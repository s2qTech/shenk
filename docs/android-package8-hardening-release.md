# Android Package 8: Hardening and Private Release

Updated: 2026-08-22
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

### P8.3 Local reminders and HyperOS guidance - complete

- Implement local reminder scheduling without an always-running service.
- Handle notification permission and Xiaomi/HyperOS restrictions honestly.
- Keep remote push outside phase 1.

Implementation notes:

- The accepted morning, midday, evening, and weekly reminders remain unique WorkManager jobs. Daily reminders are discarded outside their configured same-day delivery window, so opening the app after midnight cannot replay a stale check-in prompt.
- The reminder sheet now reports runtime notification permission, the system-wide notification switch, and battery-optimization state separately. Missing permission can be requested explicitly; public Android notification and application-detail settings are reachable without private vendor intents.
- Xiaomi, Redmi, and Poco devices receive an honest HyperOS note explaining that background work may still be delayed and that battery/background policy labels vary by system version. The app neither claims exact delivery nor requests an unrestricted battery exemption.
- No always-running reminder service or remote push transport was added. Reminder settings remain device-local and this stage changes no training, health, plan, AI, or synchronization entity.

### P8.4 Performance and stability - complete

- Measure startup, page transitions, list scrolling, timer execution, and database/sync work.
- Remove main-thread I/O and avoid unnecessary recomposition before visual tuning.

Implementation notes and Xiaomi 14 measurements are recorded in `android-package8-performance.md`. P8.4 now uses the AndroidX system splash as a bounded readiness gate: Calendar, Today, and Training load local data and receive one hidden pre-draw before a 220 ms splash exit, then all three remain retained at either pager edge. This removes first-composition work from touch handling; timer checkpoint recovery is independent of Training composition. P8.4 also lazily initializes timer platform services only outside idle state, indexes the 13-month guidance projection once per Room emission, and routes startup synchronization through the existing unique WorkManager job. Synthetic device gates cover a 400-day/560-record projection, a full 100-item outbox batch, and a one-hour virtual timer run without touching user business data.

### P8.5 Accessibility and theme validation - complete

- Validate light/dark contrast, dynamic text, touch targets, TalkBack labels, and reduced-motion behavior.

Implementation notes:

- The launcher uses a standard adaptive-icon structure: Android draws a full-size light/dark background color and applies the user-provided new S mark as a safe-inset foreground. This avoids double-masking the rounded source image and keeps the icon's outer size aligned with other HyperOS launcher icons. Normal and round launcher declarations share the same layers, and a matching S silhouette supplies Android themed icons.
- The system splash reuses the safe-inset light/dark mark. Its content is scaled to two thirds of the source canvas to compensate for Android's measured 1.5x splash/foreground expansion without cropping the mark.
- APK resource inspection verifies both `nodpi` and `night-nodpi` variants plus matching light/dark splash backgrounds. Xiaomi 14 data-preserving installs then verified both complete, uncropped splash marks; the temporary theme override was removed and the original 23:00–06:30 custom schedule restored.
- Light and dark theme text tokens plus calendar category accents now have an automated WCAG contrast floor of 4.5:1. The light supporting-text token and both borderline tertiary/outline pairs were corrected after the test exposed their previous failures.
- Fixed control heights are minimum heights, and Today status tiles stack at font scale 1.3 or above. Xiaomi 14 accepted Today, Calendar, and Training at `font_scale=1.5` without clipped primary content or controls.
- The primary pager exposes named accessibility actions for Calendar, Today, and Training; calendar rows merge into one descriptive node; and routine deletion has a non-gesture accessibility action. The device has no TalkBack package installed, so acceptance used the Android accessibility tree and a dedicated `AccessibilityNodeInfo` instrumentation contract—the same platform surface consumed by TalkBack—without claiming spoken-output testing.
- UI hierarchy inspection at the device's 480 dpi density found no fully visible clickable target below 48 dp across Today, Calendar, and Training. A single smaller reported calendar node was the intentionally clipped portion of an off-screen row, not a complete target.
- The splash exit now skips its fade when platform animators are disabled. With all three system animation scales set to zero, cold launch and primary-page navigation remained functional; font and animation scales were restored to their original `1.0` values after acceptance.

### P8.6 Backup, migration, and security regression - complete

- Verify encrypted configuration migration, local-first recovery, outbox replay, schema compatibility, and secret redaction.

Implementation notes:

- Settings now exposes the existing Storage Access Framework business backup engine. Export includes only complete shared business envelopes; configuration, API keys, cloud tokens, migration codes, outbox rows, conflicts, and cursors remain excluded.
- Import validates the outer schema and timestamp, v1/v2 record contracts, known entities, non-negative revisions, duplicate keys, size/count limits, and normalized secret-shaped fields before opening the restore transaction.
- Restore is an additive safe merge. Missing Shenk-owned records enter the transactional outbox, missing timer facts remain local-readable without record-module upload, identical records are no-ops, and a differing existing ID is reported and skipped instead of overwriting local state.
- Encrypted migration profiles now validate the Web-compatible 16-byte salt, 12-byte IV, PBKDF2 iteration count, GCM ciphertext bounds, timestamp, HTTPS endpoints without embedded credentials, and bounded secrets. Partial local configuration replacement restores the prior complete profile; a failed rollback leaves the API marker blank rather than exposing a mixed configuration.
- Test credentials and migration access codes are generated transiently at runtime. The Keystore instrumentation test uses a random DataStore file and random alias, removes both afterward, and no longer touches production preferences.
- Xiaomi 14 accepted a same-package/same-signature data-preserving install: `firstInstallTime` remained 2026-08-12, the Room database remained present, and the cold start completed in 2035 ms. The standalone synthetic-data test package passed 41/41 tests, including Android-provider PBKDF2/AES-GCM migration, backup transaction rejection, safe merge, outbox replay, and Keystore cleanup, then was removed.

### P8.7 Full regression

- Run cross-package automated gates and Xiaomi 14 critical-path acceptance.

### P8.8 Signed release candidate

- Build a signed private RC outside CI, archive checksums and source revision, install on-device, and document rollback.

## Current Gate

`android-app/gradlew.bat package8FoundationCheck` runs the native automated suite, release configuration validation, release lint, and an unsigned release assembly. A distributable RC is deferred to P8.8 and requires external signing material.

P8.0 and P8.1 passed on 2026-08-10. P8.2 passed on 2026-08-12: the authenticated no-release Worker route is deployed; all Node and Package 8 foundation gates passed; Xiaomi 14 accepted a data-preserving same-package/same-signature update; the focused on-device test verified APK package, version, SHA-256, and signing-certificate inspection; and a six-second cold-start check showed Today with zero update prompts while no release metadata was configured. The temporary instrumentation APK was removed after the test. P8.3 passed on 2026-08-12: JVM coverage verifies delivery-status composition and Xiaomi-family detection; the Package 8 foundation gate passed; Xiaomi 14 accepted the data-preserving install and the focused device test verified real notification state plus public system-setting targets. Device diagnostics found notifications currently disabled by HyperOS (`importance=NONE` / AppOps `ignore`), which the app now exposes instead of silently implying delivery. No permission or reminder setting was changed, and the temporary instrumentation APK was removed. P8.4 passed automated and Xiaomi 14 gates on 2026-08-12. The median debug cold start improved from 579 ms to 505 ms; four primary-page transitions were 1.17% janky at p95 19 ms; 12 calendar gestures were 0.09% janky at p95 25 ms; the 400-day/560-record projection completed in 123 ms; a full 100-operation outbox batch completed in 716 ms; and one virtual timer hour completed its 14,400 ticks in 40 ms. P8.5 passed its automated and Xiaomi 14 gates on 2026-08-15: contrast, 1.5x text, 48 dp touch targets, Android accessibility-node labels/actions, and zero-animation behavior were accepted, while the absence of an installed TalkBack package is recorded above. The temporary test package was removed and all changed device settings were restored. P8.6 passed on 2026-08-22: all 61 cross-repository tests, Android JVM gates, debug/release lint and assemblies, and 41 Xiaomi 14 isolated instrumentation tests passed. The production package retained its original install identity and Room database; the temporary test package was removed.

Package 8 remains in progress and the project remains at `8 / 9` until every stage above passes its gate.
