-- Versioned, server-authoritative consent for the single Nexus3D Diagnostics Owner Admin.
-- Source-only migration: do not apply to production without separate approval.
create table if not exists public.nexus3d_admin_consents (
  auth_user_id uuid primary key references auth.users(id) on delete restrict,
  email text not null,
  consent_version text not null,
  accepted_at timestamptz not null,
  updated_at timestamptz not null default now()
);

alter table public.nexus3d_admin_consents enable row level security;
revoke all on public.nexus3d_admin_consents from public, anon, authenticated;
grant select, insert, update, delete on public.nexus3d_admin_consents to service_role;
