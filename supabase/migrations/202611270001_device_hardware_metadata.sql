-- Additive hardware metadata for the Device Inventory DEVICE INFO section.
-- Existing installation IDs and historical diagnostic/version records are untouched.
alter table public.devices
  add column if not exists cpu_info text,
  add column if not exists gpu_info text,
  add column if not exists ram_bytes bigint,
  add column if not exists rom_bytes bigint;
