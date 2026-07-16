# Shenk Native Android

This is the production native Android foundation for Shenk. It uses Kotlin and Jetpack Compose and does not embed either Web application.

`mobile/` remains a frozen Capacitor validation prototype. New Android product work belongs here.

## Package 0 scope

- Four-module build boundary: app, model/domain, data/sync, and timer engine.
- Pinned stable Android toolchain and Compose BOM.
- A diagnostic-only Compose launch surface with no production feature claims.
- Unit, lint, instrumentation, formatting, and APK build gates.
- A sanitized Contract v1 conformance fixture shared from `../contracts/conformance/`.

No Room schema, cloud API, timer runtime, or feature screen is implemented in Package 0.

## Prerequisites

- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- An API 35+ device or emulator for instrumentation tests

## Verification

```text
./gradlew package0Check
./gradlew connectedDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
