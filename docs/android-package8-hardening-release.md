# Android Package 8: Hardening and Private Release

Updated: 2026-08-10
Status: In progress
Overall delivery progress: `8 / 9`

Package 8 turns the accepted Packages 0-7 product into a reliable private release. It does not change plan ownership, timer ownership, calendar precedence, AI permissions, or Contract v1/v2 semantics.

## Delivery Stages

### P8.0 Baseline audit and release decisions - complete

- Audit build, CI, signing, versioning, backup, notification, and update boundaries.
- Record the private release and update decision in ADR 0009.
- Keep all credentials and real data outside Git.

### P8.1 Reproducible release foundation - complete

- Centralize `versionCode` and `versionName`.
- Accept signing material only through external Gradle properties or environment variables.
- Reject partial signing configuration and repository-local keystores.
- Add the `package8FoundationCheck` automated gate.

### P8.2 Authenticated foreground updater

- Check after first frame, at most once per 24 hours.
- Read authenticated metadata and stream the private APK through the Worker.
- Verify application ID, increasing version, SHA-256, and signing certificate.
- Require explicit user download and Android installation confirmation.

### P8.3 Local reminders and HyperOS guidance

- Implement local reminder scheduling without an always-running service.
- Handle notification permission and Xiaomi/HyperOS restrictions honestly.
- Keep remote push outside phase 1.

### P8.4 Performance and stability

- Measure startup, page transitions, list scrolling, timer execution, and database/sync work.
- Remove main-thread I/O and avoid unnecessary recomposition before visual tuning.

### P8.5 Accessibility and theme validation

- Validate light/dark contrast, dynamic text, touch targets, TalkBack labels, and reduced-motion behavior.

### P8.6 Backup, migration, and security regression

- Verify encrypted configuration migration, local-first recovery, outbox replay, schema compatibility, and secret redaction.

### P8.7 Full regression

- Run cross-package automated gates and Xiaomi 14 critical-path acceptance.

### P8.8 Signed release candidate

- Build a signed private RC outside CI, archive checksums and source revision, install on-device, and document rollback.

## Current Gate

`android-app/gradlew.bat package8FoundationCheck` runs the native automated suite, release configuration validation, release lint, and an unsigned release assembly. A distributable RC is deferred to P8.8 and requires external signing material.

P8.0 and P8.1 passed on 2026-08-10. The gate completed 194 Gradle tasks, including native unit tests, debug and release lint, release configuration validation, debug assembly, and unsigned release assembly. P8.2 is the next stage; updater code has not started.

Package 8 remains in progress and the project remains at `8 / 9` until every stage above passes its gate.
