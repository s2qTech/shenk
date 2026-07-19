# Project State

Updated: 2026-07-19

## Android Platform Baseline

- Android is a private, single-primary-device product with no legacy device or OS compatibility requirement.
- The accepted initial production baseline is Android 16 / API 36, JDK 25, AGP 9.2.1, Gradle 9.4.1, and Kotlin 2.3.21.
- Android/JVM modules compile with JDK 25 but emit JVM 17 bytecode until D8/R8 officially supports a newer target.
- Tooling and libraries advance as one stable, officially compatible set. Existing Web/Worker data-contract compatibility remains mandatory.

## Product Boundary

身刻 Web and `home-training-timer` remain separate applications and repositories. The future Android app contains a native timer module but does not merge or embed the Web timer source.

- Shenk planning/record modules own plans, calendar snapshots, plan adjustments, formal training logs, body metrics, trends, feedback summaries, and sync coordination.
- The timer role owns routine execution, voice, wake behavior, timing state, and `timer_sessions`; the role is implemented by independent Web timer and future native Android timer modules.
- Cloudflare Worker + D1 owns authentication, role permissions, validation, revisions, conflict metadata, and shared cloud storage.

Do not merge or embed the Web timer code into Shenk. Enforce writes by module role: planning/record UI cannot write timer facts, and timer modules cannot write formal records or plans.

## Shared Data Ownership

Shenk planning/record modules write:

- `plan_templates`
- `routine_templates`
- `daily_plan_items`
- `plan_adjustments`
- `training_logs`
- `body_metrics`
- `weather_logs`
- `media_assets`
- `feedback_summaries`

Timer modules write:

- `timer_sessions`

`timer_session_links` is legacy compatibility only. New flows use an editable training draft and save `training_logs.timerSessionId` / `timerSessionIds` after user confirmation.

## Implemented Baseline

- Static Web MVP for 身刻 with desktop calendar, day detail, records, metrics, settings, feedback export, and coach patch inbox.
- IndexedDB/localStorage persistence and offline application shell.
- Cloud sync through Cloudflare Worker + D1.
- Encrypted multi-device sync profiles; one migration code derives the opaque profile ID, decrypts locally, and authorizes ciphertext download. Cloud stores ciphertext and a code hash only.
- Merge/upsert `coach_plan_patch` import; missing fields and empty arrays are no-op; deletes are explicit.
- Dynamic `routine_templates` source of truth with timer local cache and fallback warning.
- Timer step `execution` expansion for preparation and bilateral actions.
- `timer_sessions` read in 身刻 and used only to prefill editable formal-record drafts.
- Routine `calendarVisible` and `countsTowardTraining` boundaries.
- Canonical coach routine rules in `docs/coach-routine-contract.md`; new patches must provide explicit `scene`, `role`, lifecycle, visibility booleans, and non-empty steps. Web and Android reject incomplete routines instead of inferring classification or silently running a built-in fallback.
- Desktop Web UI is currently the accepted product baseline.

## Current Risks

1. Contract v1 has JSON Schema, OpenAPI, versioned requests and fixtures. Worker still uses deliberate hand-written validation rather than a full JSON Schema runtime, so every new field still needs a matching Worker validation review.
2. In-progress timer sessions are persisted every 15 seconds and recovered as `stopped` using the last activity heartbeat. A process kill can still lose up to one heartbeat interval of active time.
3. Published templates are protected when explicitly marked `lifecycle: "published"`, `publishedAt`, or `immutable: true`; legacy templates without a marker remain mutable for migration compatibility.
4. Both frontends remain large single files; the first extracted timer-session core is only the start of modularization.
5. Pull pagination is available and both Web clients follow it. IndexedDB still stores a whole snapshot, so the entity-store outbox remains work package 4.
6. The 11 legacy cloud `routine_templates` still require the user-approved one-time authority migration recorded in `docs/coach-routine-contract.md`. The prepared migration updates only explicit `scene`, `role`, and legacy `active` to `published`; it deletes nothing and preserves steps and visibility fields. Until that migration is applied and synchronized, Android correctly excludes those routines as invalid.

## Work Package Progress

### Work Package 0: baseline and fixtures - completed

- 身刻：coach patch、integration flow、recommendation engine、Worker security tests are runnable with Node.
- 计时器：session timing core and page contract tests are runnable with Node.
- Both repositories run the same checks on push and pull request through GitHub Actions.
- Broader end-to-end and fixture coverage remains part of later package-specific work.

### Work Package 1: data correctness and security - completed

- Cloud pulls preserve local dirty records and surface a conflict instead of replacing them.
- Worker rejects stale blind writes, while identical retries remain idempotent.
- Sync profile reads require authentication and an allowed role.
- Timer cue/warning/plan text is rendered as text nodes instead of untrusted HTML.
- Timer records active, elapsed and paused durations separately; reset, routine changes and page exit finalize active sessions as `stopped`.
- A 15-second local heartbeat recovers unexpected browser/process exits as one `stopped` session and retries cloud upload without generating a formal training log.

### Work Package 2: shared Contract v1 and version governance - completed

- Canonical schema, API description and sanitized fixtures live in `contracts/v1/`; timer keeps a matching schema/fixture test mirror.
- New 身刻 and timer requests declare `contractVersion: "1.0"`; old clients that omit it remain compatible, while explicit unknown versions receive `unsupported_contract_version`.
- Worker query responses include `contractVersion` and optional cursor pagination. 身刻 and timer follow `nextCursor` with the original `since` value.
- Explicitly published plan/routine templates are immutable locally and in Worker. Daily plan snapshots stay independent and same-day adjustments now receive stable content IDs instead of a fixed date ID.

### Work Package 3: modularization and CI - completed

- Shenk now separates recommendation, encrypted sync-profile, and snapshot-storage cores from the Web UI shell.
- Timer separates runtime execution expansion, preview grouping/action progress, and session timing from its page shell.
- The extracted cores have direct Node tests; existing plan import, integration, sync-transfer, contract, and security tests continue to run against the composed page application.
- Both apps retain static deployment, existing browser storage, cloud ownership boundaries, and accepted UI behavior.
- Module boundaries are documented in `docs/module-boundaries.md` and `PROJECT_BOUNDARIES.md`.

### Work Package 4: IndexedDB/outbox/sync v2 - completed

- IndexedDB v2 adds additive `records`, `outbox`, and `meta` stores while retaining the v1 `kv/snapshot` store.
- First open backs up the normalized local record set before copying it into entity rows and outbox entries.
- Normal saves now use entity rows and outbox keyed diff writes. The old snapshot is retained as a migration, background, and timed compatibility checkpoint; IndexedDB failure still falls back to the old snapshot path.
- Outbox failure count, error detail, and scheduled retry time are durable across a refresh.
- No cloud migration, D1 change, or physical record deletion is part of this stage.
- Migration and rollback details are documented in `docs/entity-store-migration.md`.

### Work Package 5: Web information architecture and accessibility - completed

- The Records entry now contains only confirmed formal training logs. Timer sessions remain source facts in date details and can prefill, but never compete as a second history page.
- Date details use dialog semantics, a focus trap, Escape-to-close, focus restoration, and a background scrim. Calendar dates support arrow keys, Home/End, and Page Up/Down navigation.
- Settings separates routine daily sync from optional connection editing and device migration. Trend panels compact when a metric has no usable series and explain when a second value is needed.
- The document root no longer announces every rendered page change through a global live region, and reduced-motion preferences suppress nonessential animation.

### Historical Work Package 6: Capacitor Android prototype - completed

- `mobile/` is an independent Vite + Capacitor 8 project with a generated Android container and Android CI workflow. It does not reuse the desktop seven-column calendar or date-detail drawer.
- The mobile presentation is limited to Today, Training, Records, Data, and Settings. It reuses Contract v1 envelopes and the shared cloud ownership model, while keeping its mobile presentation logic separate.
- Mobile domain tests cover effective-day priority, timer-session/formal-log separation, user-facing type labels, and timer URLs without tokens.
- The mobile repository stores writable Shenk entities in its own IndexedDB rows/outbox and rejects writes to `timer_sessions`; cloud pull protects unsynced outbox rows from a silent overwrite.
- Mobile config stores public API/Timer addresses locally but rejects secret persistence in a browser preview. Native Android uses the Secure Storage adapter backed by Android Keystore.
- Capacitor adapters exist for Secure Storage, Haptics, Browser launch, and App lifecycle. Native foreground service, native TTS, Android file import/export, and real-device timer lifecycle verification intentionally remain the first follow-up stage rather than being claimed by the Web shell.

## Active Development Direction

The previously generated Capacitor `mobile/` project is now a frozen validation prototype, not the production Android base. Product discovery completed on 2026-07-16 and selected a native Kotlin + Jetpack Compose implementation with a native timer.

The canonical Android planning documents are:

- `docs/android-product-blueprint.md`
- `docs/android-technical-architecture.md`
- `docs/android-contract-v2-plan.md`
- `docs/android-delivery-and-constraints.md`

Repository governance is enforced through:

- `AGENTS.md` as the mandatory entry point for any coding model;
- `governance/guardrails.json` as a machine-readable invariant summary;
- `docs/domain-glossary.md` for stable terminology;
- accepted decisions in `docs/adr/`;
- `tests/governance.test.js` in the normal CI test suite.

Native Android Packages 0 through 5 are complete. Package 0 established the accepted Compose module graph and passed CI plus Xiaomi 14 installation, restart, and offline-launch checks. Package 1 added additive Contract v2 schema/OpenAPI/fixtures, Worker `1.0`/`2.0` negotiation and validation, and Web/Worker/Web timer/Android conformance checks. Package 2 added the Room local source of truth, transactional outbox, visible conflict handling, WorkManager cloud sync, DataStore/Keystore configuration, encrypted migration-profile interoperability, and SAF business backup/restore. Package 3 added the native Today surface, morning check-in, optional pre-workout delta, body measurements, explicit missing-data states, effective guidance resolution, one-handed recording controls, and configurable morning/midday reminders. Package 4 added the continuous cross-month calendar agenda, actual/plan/suggestion priority, activity icons, fast-scroll week-distance feedback, bottom Today return, date details, multiple formal logs per day, the correction window, local-first record changes, and 30-day body trends. Package 5 adds the offline routine library, native timer state machine, platform interruption behavior, terminal timer facts, pending completion, and user-confirmed formal-log completion. Its Xiaomi 14 corrective gate also verified scrollable long cues, a persistent upcoming-action strip including rest, fixed icon navigation, portrait/landscape continuity, Chinese TTS, and music ducking. Native Android progress is now `6 / 9`. Contract v1 remains the production default during migration; the Worker accepts v2 without a D1 table migration.

The earlier seven Web/foundation work packages remain completed historical work. Native Android delivery now uses the nine packages and `X / 9` reporting format in `docs/android-delivery-and-constraints.md`.

## Immediate Next Step

Package 5 passed its automated gates and Xiaomi 14 acceptance on 2026-07-19, including audible TTS and music ducking. Its implementation and acceptance paths are recorded in `docs/android-package5-native-timer.md`. Package 6 is the next eligible package, but it must not start without a new explicit user instruction.
