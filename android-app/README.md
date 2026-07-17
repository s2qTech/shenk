# Shenk Native Android

This is the production native Android foundation for Shenk. It uses Kotlin and Jetpack Compose and does not embed either Web application.

`mobile/` remains a frozen Capacitor validation prototype. New Android product work belongs here.

## Completed packages

- Package 0: accepted four-module build, diagnostic Compose shell, CI and debug APK.
- Package 1: additive Contract v2 models, v1/v2 compatibility fixtures and Worker validation.
- Package 2: Room local source of truth, transactional outbox, visible conflicts, WorkManager sync, DataStore preferences, Keystore secrets, encrypted migration profiles and SAF business backup.

Package 2 intentionally adds no Today, check-in, record, or timer feature screen. Those capabilities begin only in their approved packages.

## Local data boundary

- Business envelopes are stored intact in Room so additive cross-client fields survive round trips.
- Every permitted local business mutation updates the record and outbox in one transaction.
- Dirty local rows cannot be silently replaced by a cloud pull.
- `timer_sessions` remains timer-owned. Record and planning repositories cannot write it.
- Secrets are stored through Android Keystore and are excluded from Room business rows and JSON backup.

See `../docs/android-package2-local-first.md` for schema, retry, conflict, backup and rollback details.

## Prerequisites

- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- An API 34+ emulator for instrumentation tests

## Verification

```text
./gradlew package2Check
./gradlew :core:data-sync:assembleDebugAndroidTest
./gradlew --no-parallel :core:data-sync:connectedDebugAndroidTest
./gradlew --no-parallel :app:connectedDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
