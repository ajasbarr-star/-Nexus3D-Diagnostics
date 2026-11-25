# Nexus3D Diagnostics

Standalone Android companion app for Nexus3D Editor diagnostics.

**Scope:** this repository builds the Diagnostics APK only. It does not modify or build the Nexus3D Editor APK.

## Foundation
- Native Android app using Kotlin and Jetpack Compose
- Room-backed offline event storage
- Separate RDS LogCat and RIT Trace categories
- Initial report dashboard and event list
- Dedicated Android CI workflow
- Future versioned app-to-app integration contract and opt-in cloud sync

## Privacy
Never commit real diagnostic reports, personal identifiers, project assets, API keys, credentials, or secrets. Cloud upload must remain opt-in. Imported diagnostic payloads are untrusted and must be validated.

## Build
CI uses Gradle and Android SDK to build a debug APK. Release signing is intentionally not configured yet; add signing secrets only when release distribution is planned.
