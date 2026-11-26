-- Additive ONLINE/OFFLINE presence tracking for Nexus3D Editor installations.
-- Registration status remains independent and is never overwritten by presence.
alter table public.devices
  add column if not exists presence_state text not null default 'offline'
    check (presence_state in ('online','offline')),
  add column if not exists last_active_at timestamptz,
  add column if not exists presence_updated_at timestamptz;

create index if not exists devices_workspace_presence_idx
  on public.devices(workspace_id, presence_state, last_active_at desc);
