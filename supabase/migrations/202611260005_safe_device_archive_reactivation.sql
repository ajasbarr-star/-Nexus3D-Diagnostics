-- Nexus3D automatic stale-device archive and safe reactivation.
-- Additive and reversible in behavior: keeps device rows, credentials and reports.
-- Presence columns are introduced by 202611260004_device_presence.sql.

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
       and greatest(
           coalesce(last_seen_at, '-infinity'::timestamptz),
           coalesce(last_active_at, '-infinity'::timestamptz)
       ) < now() - make_interval(days => p_inactive_days);

    get diagnostics v_archived_count = row_count;
    return v_archived_count;
end;
$$;

revoke all on function public.archive_stale_nexus3d_devices(integer) from public, anon, authenticated;
grant execute on function public.archive_stale_nexus3d_devices(integer) to service_role;

-- Run the archive check daily at 03:15 UTC. Re-running this migration replaces
-- the named job instead of creating duplicate schedules.
create extension if not exists pg_cron;

do $$
declare
    v_job_id bigint;
begin
    for v_job_id in
        select jobid from cron.job where jobname = 'nexus3d-archive-stale-devices'
    loop
        perform cron.unschedule(v_job_id);
    end loop;

    perform cron.schedule(
        'nexus3d-archive-stale-devices',
        '15 3 * * *',
        'select public.archive_stale_nexus3d_devices(30);'
    );
end;
$$;

comment on function public.archive_stale_nexus3d_devices(integer) is
'Archives installations only after the inactivity grace period, using the newest last_seen_at or last_active_at timestamp. Device rows, credentials, and historical reports are retained.';
