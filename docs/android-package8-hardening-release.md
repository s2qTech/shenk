# Android Package 8: Hardening and Private Release

Updated: 2026-08-14
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

### P8.5 Accessibility and theme validation

- Validate light/dark contrast, dynamic text, touch targets, TalkBack labels, and reduced-motion behavior.

Implementation notes:

- The launcher and system splash use the user-provided Shenk artwork through one adaptive-icon resource with `drawable-nodpi` light and `drawable-night-nodpi` dark variants. Android selects the variant from the system night-mode configuration; normal and round launcher declarations share the same source, and a matching new S silhouette supplies Android themed icons.
- The supplied opaque black corner canvas is removed from the packaged launcher bitmaps before Android applies the device launcher mask. The source artwork is otherwise only resized to the 432 px adaptive-icon asset.
- APK resource inspection verifies both `nodpi` and `night-nodpi` variants plus matching light/dark splash backgrounds. Full P8.5 device acceptance remains pending together with contrast, dynamic text, touch-target, TalkBack, and reduced-motion validation.

### P8.6 Backup, migration, and security regression

- Verify encrypted configuration migration, local-first recovery, outbox replay, schema compatibility, and secret redaction.

### P8.7 Full regression

- Run cross-package automated gates and Xiaomi 14 critical-path acceptance.

### P8.8 Signed release candidate

- Build a signed private RC outside CI, archive checksums and source revision, install on-device, and document rollback.

## Current Gate

`android-app/gradlew.bat package8FoundationCheck` runs the native automated suite, release configuration validation, release lint, and an unsigned release assembly. A distributable RC is deferred to P8.8 and requires external signing material.

P8.0 and P8.1 passed on 2026-08-10. P8.2 passed on 2026-08-12: the authenticated no-release Worker route is deployed; all Node and Package 8 foundation gates passed; Xiaomi 14 accepted a data-preserving same-package/same-signature update; the focused on-device test verified APK package, version, SHA-256, and signing-certificate inspection; and a six-second cold-start check showed Today with zero update prompts while no release metadata was configured. The temporary instrumentation APK was removed after the test. P8.3 passed on 2026-08-12: JVM coverage verifies delivery-status composition and Xiaomi-family detection; the Package 8 foundation gate passed; Xiaomi 14 accepted the data-preserving install and the focused device test verified real notification state plus public system-setting targets. Device diagnostics found notifications currently disabled by HyperOS (`importance=NONE` / AppOps `ignore`), which the app now exposes instead of silently implying delivery. No permission or reminder setting was changed, and the temporary instrumentation APK was removed. P8.4 passed automated and Xiaomi 14 gates on 2026-08-12. The median debug cold start improved from 579 ms to 505 ms; four primary-page transitions were 1.17% janky at p95 19 ms; 12 calendar gestures were 0.09% janky at p95 25 ms; the 400-day/560-record projection completed in 123 ms; a full 100-operation outbox batch completed in 716 ms; and one virtual timer hour completed its 14,400 ticks in 40 ms. P8.5 accessibility and theme validation started on 2026-08-14 with the theme-responsive launcher and splash artwork; its full Xiaomi 14 acceptance remains pending.

Package 8 remains in progress and the project remains at `8 / 9` until every stage above passes its gate.
