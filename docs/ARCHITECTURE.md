# Architecture

## Current foundation
- Android application: Kotlin + Jetpack Compose.
- Persistence: Room database on-device.
- Diagnostic sources are kept distinct: `RDS_LOGCAT`, `RIT_TRACE`, and `SYSTEM`.
- UI observes the local database; no network upload occurs.

## Planned integration
A future versioned IPC contract will allow the Editor APK to send validated diagnostic batches to this app. The preferred design is an explicit, permission-controlled Android app-to-app interface with bounded payload sizes, caller validation, event timestamps, session IDs, and schema versioning. The exact IPC mechanism must be validated against the Editor APK's Capacitor/native architecture before implementation.

## Planned phases
1. Local event intake and storage.
2. RDS LogCat and RIT Trace import, viewing, filtering, and export.
3. Editor-to-Diagnostics IPC adapter.
4. Opt-in cloud sync with retry queue and encryption in transit.
5. Multi-device dashboard and retention controls.
