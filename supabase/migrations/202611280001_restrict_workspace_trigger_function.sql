-- Restrict direct execution of the workspace provisioning trigger function.
-- The existing auth.users trigger remains installed; trigger execution uses the
-- function owner privileges. No workspace rows or existing accounts are changed.
begin;

revoke all on function public.create_nexus3d_workspace()
  from public, anon, authenticated, service_role;

do $$
begin
  if has_function_privilege('anon', 'public.create_nexus3d_workspace()', 'EXECUTE')
     or has_function_privilege('authenticated', 'public.create_nexus3d_workspace()', 'EXECUTE')
     or has_function_privilege('service_role', 'public.create_nexus3d_workspace()', 'EXECUTE') then
    raise exception 'create_nexus3d_workspace remains directly executable by a client/service role';
  end if;

  if not exists (
    select 1
      from pg_trigger t
      join pg_class c on c.oid = t.tgrelid
      join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'auth'
       and c.relname = 'users'
       and t.tgname = 'on_auth_user_created_nexus3d_workspace'
       and not t.tgisinternal
       and t.tgenabled <> 'D'
  ) then
    raise exception 'Expected auth.users workspace provisioning trigger is missing or disabled';
  end if;
end;
$$;

commit;
