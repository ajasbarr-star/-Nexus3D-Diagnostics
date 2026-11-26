-- Explicit backend privileges for the Supabase service_role.
-- Required by server-side Edge Functions that use SUPABASE_SERVICE_ROLE_KEY.
-- This does not grant these permissions to anon or authenticated clients.
grant usage on schema public to service_role;
grant all privileges on table
  public.workspaces,
  public.devices,
  public.device_tokens,
  public.enrollment_codes,
  public.diagnostic_reports
to service_role;
grant execute on function public.consume_nexus3d_enrollment_code(text) to service_role;
grant execute on function public.archive_stale_nexus3d_devices(integer) to service_role;
