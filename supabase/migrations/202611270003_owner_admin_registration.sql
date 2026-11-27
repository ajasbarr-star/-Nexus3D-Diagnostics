-- Nexus3D Diagnostics: single, permanent Owner Admin registration.
-- This migration is additive and is not applied to production by this source change.
create table if not exists public.nexus3d_admin_accounts (
  id uuid primary key default gen_random_uuid(),
  auth_user_id uuid not null unique references auth.users(id) on delete restrict,
  email text not null,
  role text not null default 'owner_admin' check (role = 'owner_admin'),
  status text not null default 'active' check (status in ('active', 'locked')),
  created_at timestamptz not null default now(),
  locked_at timestamptz
);

-- A locked Owner Admin still occupies the permanent owner slot.
create unique index if not exists nexus3d_admin_accounts_single_owner_uidx
  on public.nexus3d_admin_accounts(role)
  where role = 'owner_admin';

alter table public.nexus3d_admin_accounts enable row level security;
revoke all on public.nexus3d_admin_accounts from public, anon, authenticated;
grant select, insert, update, delete on public.nexus3d_admin_accounts to service_role;

create or replace function public.claim_nexus3d_owner_admin(
  p_auth_user_id uuid,
  p_email text
)
returns table(admin_id uuid, admin_email text, admin_status text)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_verified_email text;
  v_existing public.nexus3d_admin_accounts%rowtype;
  v_created public.nexus3d_admin_accounts%rowtype;
begin
  select lower(u.email)
    into v_verified_email
    from auth.users u
   where u.id = p_auth_user_id
     and u.email_confirmed_at is not null;

  if v_verified_email is null or v_verified_email <> lower(trim(p_email)) then
    raise exception 'owner_admin_email_not_verified' using errcode = 'P0001';
  end if;

  select *
    into v_existing
    from public.nexus3d_admin_accounts
   where auth_user_id = p_auth_user_id;

  if found then
    return query select v_existing.id, v_existing.email, v_existing.status;
    return;
  end if;

  if exists (
    select 1 from public.nexus3d_admin_accounts
     where role = 'owner_admin'
  ) then
    raise exception 'owner_admin_already_claimed' using errcode = 'P0001';
  end if;

  insert into public.nexus3d_admin_accounts(auth_user_id, email, role, status)
  values (p_auth_user_id, v_verified_email, 'owner_admin', 'active')
  returning * into v_created;

  return query select v_created.id, v_created.email, v_created.status;
exception
  when unique_violation then
    select *
      into v_existing
      from public.nexus3d_admin_accounts
     where auth_user_id = p_auth_user_id;

    if found then
      return query select v_existing.id, v_existing.email, v_existing.status;
      return;
    end if;

    raise exception 'owner_admin_already_claimed' using errcode = 'P0001';
end;
$$;

revoke all on function public.claim_nexus3d_owner_admin(uuid, text) from public, anon, authenticated;
grant execute on function public.claim_nexus3d_owner_admin(uuid, text) to service_role;
