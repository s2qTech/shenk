# Android Package 8: Hardening and Private Release

Updated: 2026-08-12
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

### P8.2 Authenticated foreground updater - complete

- Check after first frame, at most once per 24 hours.
- Read authenticated metadata and stream the private APK through the Worker.
- Verify application ID, increasing version, SHA-256, and signing certificate.
- Require explicit user download and Android installation confirmation.

Implementation notes:

- `MainActivity` schedules the check only after the first rendered frame. A device-local DataStore timestamp limits attempts to once per 24 hours; offline, unauthorized, malformed, and no-release results stay silent.
- The Worker exposes authenticated `shenk`/`admin` metadata and APK routes. The timer role is forbidden, metadata omits the private object key, and an absent release returns a successful empty result.
- The app accepts only `io.s2qtech.shenk` with a strictly increasing `versionCode`. After an explicit download it verifies byte count, SHA-256, archive package/version, and an exact match with the installed signing certificate before enabling the system installer action.
- The user confirms download in-app and confirms installation again in Android's system UI. APKs live only in the app cache and are removed on dismissal or failed verification.
- Production R2 binding and signed release metadata remain intentionally absent until the P8.8 signed RC. The deployed route therefore reports no release without affecting normal use. Operational publication steps are documented in `android-private-update-operations.md`.

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

P8.0 and P8.1 passed on 2026-08-10. P8.2 passed on 2026-08-12: the authenticated no-release Worker route is deployed; all Node and Package 8 foundation gates passed; Xiaomi 14 accepted a data-preserving same-package/same-signature update; the focused on-device test verified APK package, version, SHA-256, and signing-certificate inspection; and a six-second cold-start check showed Today with zero update prompts while no release metadata was configured. The temporary instrumentation APK was removed after the test. P8.3 local reminders and HyperOS guidance is next and has not started.

Package 8 remains in progress and the project remains at `8 / 9` until every stage above passes its gate.
