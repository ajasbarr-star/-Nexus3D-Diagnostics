-- Nexus3D automatic device registration, release management and beta testing.
-- Additive migration: preserves the existing enrollment flow and diagnostic history.
alter table public.devices
  add column if not exists manufacturer text,
  add column if not exists model text,
  add column if not exists android_version text,
  add column if not exists app_build_number bigint,
  add column if not exists app_version_code bigint,
  add column if not exists release_id uuid,
  add column if not exists registration_method text not null default 'enrollment'
    check (registration_method in ('enrollment','automatic'));

create table if not exists public.app_releases (
  id uuid primary key default gen_random_uuid(),
  workspace_id uuid not null references public.workspaces(id) on delete cascade,
  app_name text not null default 'Nexus3D',
  version_name text not null,
  version_code bigint not null check (version_code > 0),
  build_number bigint not null check (build_number > 0),
  branch_name text not null default 'unknown',
  release_channel text not null check (release_channel in ('stable','beta','internal')),
  release_status text not null default 'draft' check (release_status in ('draft','published','withdrawn')),
  release_tag text not null,
  file_size_bytes bigint not null default 0 check (file_size_bytes >= 0),
  storage_path text,
  download_url text,
  release_notes text not null default '',
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  published_at timestamptz,
  unique(workspace_id, release_tag),
  unique(workspace_id, release_channel, version_code)
);
create index if not exists app_releases_workspace_channel_idx
  on public.app_releases(workspace_id, release_channel, created_at desc);

create table if not exists public.beta_testers (
  id uuid primary key default gen_random_uuid(),
  workspace_id uuid not null references public.workspaces(id) on delete cascade,
  device_id uuid references public.devices(id) on delete set null,
  tester_label text not null default 'Beta Tester',
  contact_email text,
  tester_status text not null default 'active' check (tester_status in ('invited','active','paused','removed')),
  notes text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(workspace_id, device_id)
);

create table if not exists public.release_assignments (
  id uuid primary key default gen_random_uuid(),
  workspace_id uuid not null references public.workspaces(id) on delete cascade,
  release_id uuid not null references public.app_releases(id) on delete cascade,
  device_id uuid not null references public.devices(id) on delete cascade,
  assigned_at timestamptz not null default now(),
  download_status text not null default 'assigned'
    check (download_status in ('assigned','downloaded','installed','verified','failed')),
  installed_version text,
  installed_version_code bigint,
  installed_build_number bigint,
  last_reported_at timestamptz,
  failure_reason text,
  unique(release_id, device_id)
);
create index if not exists release_assignments_device_idx
  on public.release_assignments(device_id, last_reported_at desc);

create table if not exists public.installation_events (
  id uuid primary key default gen_random_uuid(),
  workspace_id uuid not null references public.workspaces(id) on delete cascade,
  device_id uuid not null references public.devices(id) on delete cascade,
  release_id uuid references public.app_releases(id) on delete set null,
  event_type text not null check (event_type in ('launched','downloaded','installed','verified','failed')),
  app_version text not null default 'unknown',
  version_code bigint,
  build_number bigint,
  details text not null default '',
  occurred_at timestamptz not null default now(),
  received_at timestamptz not null default now()
);
create index if not exists installation_events_workspace_time_idx
  on public.installation_events(workspace_id, occurred_at desc);
create index if not exists installation_events_device_time_idx
  on public.installation_events(device_id, occurred_at desc);

alter table public.app_releases enable row level security;
alter table public.beta_testers enable row level security;
alter table public.release_assignments enable row level security;
alter table public.installation_events enable row level security;

drop policy if exists "workspace owners manage releases" on public.app_releases;
create policy "workspace owners manage releases" on public.app_releases
  for all to authenticated
  using (exists (select 1 from public.workspaces w where w.id = app_releases.workspace_id and w.owner_id = (select auth.uid())))
  with check (exists (select 1 from public.workspaces w where w.id = app_releases.workspace_id and w.owner_id = (select auth.uid())));

drop policy if exists "workspace owners manage beta testers" on public.beta_testers;
create policy "workspace owners manage beta testers" on public.beta_testers
  for all to authenticated
  using (exists (select 1 from public.workspaces w where w.id = beta_testers.workspace_id and w.owner_id = (select auth.uid())))
  with check (exists (select 1 from public.workspaces w where w.id = beta_testers.workspace_id and w.owner_id = (select auth.uid())));

drop policy if exists "workspace owners manage release assignments" on public.release_assignments;
create policy "workspace owners manage release assignments" on public.release_assignments
  for all to authenticated
  using (exists (select 1 from public.workspaces w where w.id = release_assignments.workspace_id and w.owner_id = (select auth.uid())))
  with check (exists (select 1 from public.workspaces w where w.id = release_assignments.workspace_id and w.owner_id = (select auth.uid())));

drop policy if exists "workspace owners read installation events" on public.installation_events;
create policy "workspace owners read installation events" on public.installation_events
  for select to authenticated
  using (exists (select 1 from public.workspaces w where w.id = installation_events.workspace_id and w.owner_id = (select auth.uid())));

revoke all on public.app_releases, public.beta_testers, public.release_assignments, public.installation_events from anon, authenticated;
grant select, insert, update, delete on public.app_releases, public.beta_testers, public.release_assignments to authenticated;
grant select on public.installation_events to authenticated;
grant all on public.app_releases, public.beta_testers, public.release_assignments, public.installation_events to service_role;

insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values ('nexus3d-releases', 'nexus3d-releases', false, 209715200, array['application/vnd.android.package-archive','application/octet-stream'])
on conflict (id) do update set public = false, file_size_limit = 209715200;

drop policy if exists "workspace owners upload Nexus3D releases" on storage.objects;
create policy "workspace owners upload Nexus3D releases" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'nexus3d-releases'
    and exists (select 1 from public.workspaces w
      where w.id::text = (storage.foldername(name))[1] and w.owner_id = (select auth.uid()))
  );

drop policy if exists "workspace owners read Nexus3D releases" on storage.objects;
create policy "workspace owners read Nexus3D releases" on storage.objects
  for select to authenticated
  using (
    bucket_id = 'nexus3d-releases'
    and exists (select 1 from public.workspaces w
      where w.id::text = (storage.foldername(name))[1] and w.owner_id = (select auth.uid()))
  );
