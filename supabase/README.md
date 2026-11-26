# Nexus3D Diagnostics — Supabase backend

This folder contains the first multi-device backend foundation. It is separate from the Android APK source and does not contain project secrets.

## Security model

- A Diagnostics owner signs in with Supabase Auth.
- The authenticated owner requests a short-lived, single-use enrollment code.
- A Nexus3D installation registers using that code and its locally persisted installation UUID.
- Registration returns a random per-installation bearer token. Only its SHA-256 hash is stored in the database.
- Nexus3D sends reports over HTTPS with that device token. The service-role key is used only inside Edge Functions and must never be shipped in either APK.
- The Diagnostics app reads only the authenticated owner's workspace, devices, and reports through Row Level Security.

## Deploy

1. Create a Supabase project.
2. Link this repository with the Supabase CLI: `supabase link --project-ref YOUR_PROJECT_REF`.
3. Apply the schema: `supabase db push`.
4. Deploy functions: `supabase functions deploy create-enrollment-code`, `supabase functions deploy register-device`, and `supabase functions deploy ingest-events`.
5. Configure the project URL, anon key, and function endpoints in the Android apps. Never place the service-role key in an APK.
6. Enable an owner sign-in method in Supabase Auth before generating enrollment codes.

## Device lifecycle

- Installation IDs are random UUIDs stored in app-private persistent storage; ordinary app updates retain them. Uninstall/reinstall creates a new ID.
- Device tokens are installation-specific and revocable.
- Archiving must not delete reports. The migration provides `archive_stale_nexus3d_devices(30)` with a 30-day default grace period and a 7–365 day safety bound. Schedule it with Supabase Cron after deployment; offline devices are not archived immediately.
- Android cannot reliably notify the backend at the exact instant an app is uninstalled. Archive status is inferred from last-seen after the configured grace period.

## Report contract (schema version 1)

Each event contains `eventId`, `sessionId`, `sequence`, `schemaVersion`, `source`, `severity`, `title`, `details`, and `timestamp`. Event IDs make retries idempotent. Sender-side durable outbox, Android-side device-token storage, cloud report retrieval, and owner sign-in UI remain the next Android integration phase. The current APK build only adds local event metadata and the Room migration; it does not yet upload or download reports.

## Important

The backend is not live until a Supabase project is created and the migration/functions above are deployed. Do not enable cloud sync in production APKs until configuration and end-to-end tests pass.
