-- Restrict direct execution of the workspace provisioning trigger function.
-- The existing auth.users trigger remains installed; trigger execution uses the
-- function owner privileges. No workspace rows or existing accounts are changed.
begin;

revoke all on function public.create_nexus3d_workspace()
  from public, anon, authenticated, service_role;

commit;
