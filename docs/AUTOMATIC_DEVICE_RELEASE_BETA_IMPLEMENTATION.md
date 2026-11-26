# Nexus3D Diagnostics: automatic identity, releases and beta operations

## Confirmed product behavior
- Editor creates a persistent installation UUID and 256-bit device credential only on first app launch, never merely on install.
- The UUID is the single installation identity shared by all diagnostic sources. It survives app updates and changes after uninstall/reinstall.
- The credential is stored encrypted on Android and is never displayed as a copyable enrollment code. Only its SHA-256 hash is stored server-side.
- Registration is idempotent and uses the automatic registration Edge Function. Diagnostics Admin workspace sees the installation and privacy-safe technical metadata.
- Collect only manufacturer, model, Android version, app version/build, timestamps, and connection/registration state. Do not collect IMEI, serial, GPS, contacts, files, photos, or account data.
- Offline identity creation is local-first; registration/reporting retries when network is available. Cloud reporting remains subject to diagnostics consent.
- Admin-only release manager supports Stable, Beta, and Internal channels. Each release has app name, version, version code, build number, branch, release tag/ID, APK size, private storage path, notes, and status.
- Beta testers are explicit device assignments. Each installation report is tied to the persistent Device ID and release ID.
- Installation is not marked Success merely because a release was published or downloaded. Verified Success requires the updated Editor APK to launch and report its actual installed version/build.
- Beta and Stable channels are isolated. Beta builds are never automatically offered to Stable devices.

## Backend setup required
1. Apply migration 202611260003_release_beta_management.sql after migrations 001 and 002.
2. Set Edge Function secret NEXUS3D_AUTO_ENROLL_WORKSPACE_ID to the UUID of the Diagnostics administrator's workspace. Do not put a service-role key in either APK.
3. Deploy auto-register-device and report-installation Edge Functions.
4. Configure the app to request diagnostics consent before cloud registration/reporting. A device may generate and retain its identity locally while offline or before consent.

## Important ownership/security note
The current product model is one central Diagnostics Admin workspace for device/release operations. If the product later needs each end customer to see only their own devices in a separate Diagnostics account, add explicit account authentication/tenant assignment; an anonymous installation UUID alone cannot establish a user's account ownership.

## Implementation order
- Backend schema and device endpoints.
- Native Editor identity store and first-launch registration bridge.
- Diagnostics device inventory and privacy-safe device detail screen.
- Release manager, private APK upload, beta assignments and tester management.
- Installation verification and error/feedback correlation.
- CI builds, regression tests, and manual two-device online/offline verification.

## Device lifecycle policy (30-day archive-and-hide)

- A daily Supabase Cron job runs at 03:15 UTC and calls `archive_stale_nexus3d_devices(30)`.
- The archive function compares the newest of `last_seen_at` and `last_active_at`; ordinary foreground presence heartbeats therefore keep an installation active.
- Archiving changes only `devices.status` to `archived` and records `archived_at`. It does not delete the device row, credential, or diagnostic history.
- Active Device Inventory filters to `status=active`, so archived installations are hidden from the active list.
- When the same installation later launches and proves possession of its existing device credential, `auto-register-device` restores `status=active`, clears `archived_at`, and updates its presence timestamps. A revoked credential is never reactivated.
- A fresh install has a new installation UUID and registers as a new device. The old archived record remains available for audit/history.

Apply `202611260005_safe_device_archive_reactivation.sql` after migrations 001–004. The migration installs/replaces one named Cron job, avoiding duplicate schedules. Verify the job exists in `cron.job` and inspect executions in `cron.job_run_details` after deployment.
