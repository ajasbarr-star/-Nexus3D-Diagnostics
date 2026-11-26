-- Nexus3D Diagnostics multi-device backend foundation.
-- Historical reports are intentionally retained when a device is archived.
create extension if not exists pgcrypto;

create table if not exists public.workspaces (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null unique references auth.users(id) on delete cascade,
    name text not null default 'Nexus3D Workspace',
    created_at timestamptz not null default now()
);

create table if not exists public.devices (
    id uuid primary key default gen_random_uuid(),
    installation_id uuid not null unique,
    workspace_id uuid not null references public.workspaces(id) on delete cascade,
    device_name text not null default 'Nexus3D device',
    platform text not null default 'android',
    app_version text not null default 'unknown',
    status text not null default 'active'
        check (status in ('active', 'archived', 'revoked')),
    created_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    archived_at timestamptz,
    constraint devices_installation_id_not_nil check (installation_id <> '00000000-0000-0000-0000-000000000000')
);

create index if not exists devices_workspace_status_idx
    on public.devices(workspace_id, status, last_seen_at desc);

create table if not exists public.device_tokens (
    device_id uuid primary key references public.devices(id) on delete cascade,
    token_hash text not null unique,
    created_at timestamptz not null default now(),
    revoked_at timestamptz
);

create table if not exists public.enrollment_codes (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references public.workspaces(id) on delete cascade,
    code_hash text not null unique,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists enrollment_codes_expiry_idx
    on public.enrollment_codes(expires_at) where consumed_at is null;

create table if not exists public.diagnostic_reports (
    id uuid primary key default gen_random_uuid(),
    device_id uuid not null references public.devices(id) on delete restrict,
    event_id uuid not null,
    session_id uuid not null,
    sequence bigint not null check (sequence >= 0),
    schema_version integer not null check (schema_version >= 1),
    source text not null check (source in ('RDS_LOGCAT', 'RIT_TRACE', 'RUNTIME')),
    severity text not null check (severity in ('VERBOSE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL')),
    title text not null check (char_length(title) between 1 and 240),
    details text not null default '' check (char_length(details) <= 24000),
    occurred_at timestamptz not null,
    received_at timestamptz not null default now(),
    unique (device_id, event_id)
);

create index if not exists diagnostic_reports_device_time_idx
    on public.diagnostic_reports(device_id, occurred_at desc);
create index if not exists diagnostic_reports_source_severity_idx
    on public.diagnostic_reports(source, severity, occurred_at desc);

-- Create one private workspace automatically for each new authenticated owner.
create or replace function public.create_nexus3d_workspace()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.workspaces(owner_id, name)
    values (new.id, 'Nexus3D Workspace')
    on conflict (owner_id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created_nexus3d_workspace on auth.users;
create trigger on_auth_user_created_nexus3d_workspace
after insert on auth.users
for each row execute function public.create_nexus3d_workspace();

-- Also provision workspaces for accounts that existed before this migration.
insert into public.workspaces(owner_id)
select id from auth.users
on conflict (owner_id) do nothing;

-- Atomic one-time enrollment-code consumption; only service-role Edge Functions call this.
create or replace function public.consume_nexus3d_enrollment_code(p_code_hash text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_workspace_id uuid;
begin
    update public.enrollment_codes
       set consumed_at = now()
     where code_hash = p_code_hash
       and consumed_at is null
       and expires_at > now()
    returning workspace_id into v_workspace_id;
    return v_workspace_id;
end;
$$;

revoke all on function public.consume_nexus3d_enrollment_code(text) from public, anon, authenticated;
grant execute on function public.consume_nexus3d_enrollment_code(text) to service_role;

-- Archive inactive installations after the configured grace period.
-- Reports remain untouched; archived credentials are rejected by ingest-events.
create or replace function public.archive_stale_nexus3d_devices(p_inactive_days integer default 30)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_archived_count integer;
begin
    if p_inactive_days < 7 or p_inactive_days > 365 then
        raise exception 'p_inactive_days must be between 7 and 365';
    end if;

    update public.devices
       set status = 'archived',
           archived_at = now()
     where status = 'active'
       and last_seen_at < now() - make_interval(days => p_inactive_days);

    get diagnostics v_archived_count = row_count;
    return v_archived_count;
end;
$$;

revoke all on function public.archive_stale_nexus3d_devices(integer) from public, anon, authenticated;
grant execute on function public.archive_stale_nexus3d_devices(integer) to service_role;

alter table public.workspaces enable row level security;
alter table public.devices enable row level security;
alter table public.device_tokens enable row level security;
alter table public.enrollment_codes enable row level security;
alter table public.diagnostic_reports enable row level security;

drop policy if exists "owners read own workspace" on public.workspaces;
create policy "owners read own workspace" on public.workspaces
    for select to authenticated using (owner_id = (select auth.uid()));

drop policy if exists "owners read own devices" on public.devices;
create policy "owners read own devices" on public.devices
    for select to authenticated using (
        exists (select 1 from public.workspaces w
                where w.id = devices.workspace_id and w.owner_id = (select auth.uid()))
    );

drop policy if exists "owners read own reports" on public.diagnostic_reports;
create policy "owners read own reports" on public.diagnostic_reports
    for select to authenticated using (
        exists (
            select 1 from public.devices d
            join public.workspaces w on w.id = d.workspace_id
            where d.id = diagnostic_reports.device_id
              and w.owner_id = (select auth.uid())
        )
    );

-- No direct client writes or reads of device credentials/enrollment secrets.
revoke all on public.workspaces, public.devices, public.device_tokens,
    public.enrollment_codes, public.diagnostic_reports from anon, authenticated;
grant select on public.workspaces, public.devices, public.diagnostic_reports to authenticated;
