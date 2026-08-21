# Project State

Updated: 2026-08-22

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

`timer_session_links` is legacy compatibility for linked/converted records. Android may additionally write an `action: "ignored"` acknowledgement so an unwanted timer fact stops appearing in the local-first pending-completion queue. Formal records still use an editable draft and save `training_logs.timerSessionId` / `timerSessionIds` after user confirmation.

## Implemented Baseline

- Static Web MVP for 身刻 with desktop calendar, day detail, records, metrics, settings, feedback export, and coach patch inbox.
- IndexedDB/localStorage persistence and offline application shell.
- Cloud sync through Cloudflare Worker + D1.
- Encrypted multi-device sync profiles; one migration code derives the opaque profile ID, decrypts locally, and authorizes ciphertext download. Cloud stores ciphertext and a code hash only.
- Web, Android, Worker, and MCP use the same strict Contract v2 `coach_plan_patch` import boundary. New imports require `contractVersion: "2.0"`; missing fields and empty arrays are no-op; `replaceMode: true`, the legacy singular `planTemplate`, and legacy adjustment shorthand are rejected; deletes are explicit. Records touched by an accepted patch are persisted as v2 envelopes on both clients without rewriting unrelated legacy Web data.
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

Native Android Packages 0 through 7 are complete. Package 0 established the accepted Compose module graph and passed CI plus Xiaomi 14 installation, restart, and offline-launch checks. Package 1 added additive Contract v2 schema/OpenAPI/fixtures, Worker `1.0`/`2.0` negotiation and validation, and Web/Worker/Web timer/Android conformance checks. Package 2 added the Room local source of truth, transactional outbox, visible conflict handling, WorkManager cloud sync, DataStore/Keystore configuration, encrypted migration-profile interoperability, and SAF business backup/restore. Package 3 added the native Today surface, morning check-in, optional pre-workout delta, body measurements, explicit missing-data states, effective guidance resolution, one-handed recording controls, and configurable morning/midday reminders. Package 4 added the continuous cross-month calendar agenda, actual/plan/suggestion priority, activity icons, fast-scroll week-distance feedback, a bottom current-date anchor, date details, multiple formal logs per day, the correction window, local-first record changes, 30-day body trends, and per-date body-measurement changes against each field's previous valid value. Its calendar refinement keeps agenda rows transparent, fades the lower stream edge, and uses a lightweight native pager transition without whole-page scale/alpha layers. Package 5 adds the offline routine library, native timer state machine, platform interruption behavior, terminal timer facts, pending completion, and user-confirmed formal-log completion. Its Xiaomi 14 corrective gate also verified scrollable long cues, a persistent upcoming-action strip including rest, fixed icon navigation, portrait/landscape continuity, Chinese TTS, and music ducking. Package 6 adds the provider-neutral clipboard plan inbox, strict whole-patch validation, preview and confirmation, latest-only undo, and weekly feedback workspace. Package 7 adds the bounded daily AI review flow described below. Native Android progress is now `8 / 9`. General Web shared-record envelopes remain Contract v1-compatible during migration, while all new `coach_plan_patch` imports use Contract v2. The Worker accepts both envelope versions without a D1 table migration.

The accepted primary-space corrective pass removes duplicate top-right Calendar/Today/More shortcuts, removes the redundant standalone Records page, makes the morning record/adjust action visually dominant, changes the Calendar current-date anchor from persistent to visibility-driven, and integrates the Training scene switcher into the bottom surface instead of a floating dock. This is corrective work within accepted Packages 3-5 and does not start Package 6.

The earlier seven Web/foundation work packages remain completed historical work. Native Android delivery now uses the nine packages and `X / 9` reporting format in `docs/android-delivery-and-constraints.md`.

## Confirmed Design Follow-up

The overall Android UI still needs one coordinated visual-system adjustment. The current minimal direction is accepted as a structural base, but several surfaces feel under-designed or too close to plain text layout. Planning must begin with the Shenk logo and adaptive light/dark application icon, then cover iconography, typography, spacing, cards, colors, component states, empty states, and motion across the whole app. This is a design-audit and system-planning task before broad implementation; it must preserve the accepted Calendar–Today–Training structure, accessibility, performance, and domain semantics rather than becoming a page-by-page cosmetic rewrite. Exact delivery sequencing remains to be confirmed before implementation.

## Immediate Next Step

Package 6 passed its automated gates and Xiaomi 14 acceptance on 2026-08-08. Its accepted phase-1 advanced-AI transport is provider-neutral clipboard copy/paste: Android copies the normalized weekly review package, and accepts only a pasted `coach_plan_patch` that passes whole-patch validation, preview, and confirmation. Device acceptance covered strict add/apply, routine-only empty-array no-op, whole-patch rejection, explicit-delete confirmation, latest-only undo, offline outbox recovery, weekly feedback copy, and cleanup. Apply and undo now release their busy state before showing result snackbars. Package 7 daily AI review passed its automated gates and Xiaomi 14 acceptance on 2026-08-09. Progress is `8 / 9`; Package 8 hardening and private release work is in progress. P8.0 baseline audit and P8.1 reproducible release foundation passed on 2026-08-10, establishing centralized versioning, external release signing, and CI release validation. P8.2 authenticated foreground update passed automated, deployed-Worker, data-preserving install, APK integrity/signature, and no-release cold-start gates on Xiaomi 14 on 2026-08-12. P8.3 local reminders and HyperOS guidance passed automated and Xiaomi 14 gates on 2026-08-12; the reminder surface now exposes actual notification and battery state, public repair routes, and honest HyperOS delivery limits without a permanent service or remote push. Device diagnostics found the app notification switch disabled by HyperOS, and the app reports that state without changing it. P8.4 performance and stability passed automated and Xiaomi 14 gates on 2026-08-12, including cold start, primary transitions, calendar scrolling, one-hour virtual timer execution, a realistic 13-month projection, and a full sync batch. A 2026-08-13 interaction follow-up first retained all three primary pages, then moved their initial local-data composition and hidden pre-draw behind a bounded AndroidX system SplashScreen gate. Forty continuous Xiaomi 14 transitions completed at 1.41% janky, p95 18 ms and p99 29 ms; the focused post-splash first gesture produced no Choreographer skipped-frame burst. The debug tradeoff is an approximately 2.1-2.4 second fully prepared time-to-interactive, to be remeasured on the signed P8.8 release candidate. Timer recovery no longer depends on Training composition, calendar resolution is indexed per Room emission, and cloud synchronization is coordinated through WorkManager. P8.5 accessibility and theme validation passed on 2026-08-15. Automated contrast gates enforce 4.5:1 for light/dark text tokens and calendar accents; fixed control heights now expand with text; primary navigation and routine deletion expose accessibility actions; and the splash skips its fade when system animators are disabled. Xiaomi 14 accepted all three primary pages at 1.5x font size, fully visible 48 dp touch targets at 480 dpi, Android accessibility-node labels/actions, and zero-animation launch/navigation. The device has no TalkBack package, so spoken-output testing was not claimed; acceptance used the platform accessibility contract TalkBack consumes. Font and animation settings were restored. P8.6 backup, migration, and security regression passed on 2026-08-22. Settings now exposes secret-free SAF business backup; restore validates complete v1/v2 envelopes before a transaction and safely merges without replacing existing local state; restored Shenk-owned records replay through outbox while timer facts remain local; encrypted migration profiles enforce the Web-compatible PBKDF2/AES-GCM shape and roll back partial local replacement; and test access codes are generated transiently. All 61 repository tests, Package 8 foundation gates, and 42 isolated Xiaomi 14 tests passed, including a synthetic ContentResolver JSON round trip. The data-preserving install kept the original first-install time and Room database, the debug cold start completed in 2035 ms, and the temporary backup URI and test package were removed. P8.7 full regression passed on 2026-08-22: all 61 cross-repository tests, Android Debug/Release/lint/JVM gates, 42 isolated synthetic-data device tests, and three read-only production-package checks passed. The data-preserving install retained the original first-install identity and `shenk-native.db`; Calendar-Today-Training accessibility navigation and Settings -> Data backup reachability passed; the final cold launch was 2110 ms; ordinary UI remained portrait-locked without changing the device auto-rotate setting; system font/animation state and notification state were unchanged; and all test packages plus synthetic backup rows were removed. P8.8 signed release candidate is next.

The 2026-08-15 Package 7 reliability correction makes each daily review a Worker-authoritative idempotent job. Android no longer exposes retry during uncertain transport or server execution, and polls the same job id until the Worker records success or explicit failure. DeepSeek V4 Flash keeps reasoning enabled with a 42,066-token primary output budget derived by doubling the observed 21,033-token real-device request, plus one bounded 8,192-token non-reasoning structure repair. Worker job metadata records numeric token usage and finish reason without persisting provider credentials or health snapshots.

The 2026-08-17 Package 7 presentation correction gives the complete review a scan-first card hierarchy: conclusion, assessment, next actions, cautions, and evidence are separate top-level sections rather than one oversized nested card. Actions are numbered, cautions use a dedicated warning container, and legacy evidence is translated at display time so internal fields such as `estimatedMinutes`, `durationSec`, and pain enums never appear as raw UI copy. The Worker prompt now requires concise user-facing Chinese evidence and forbids field names, internal IDs, raw enums, and status codes. Calendar date details use separate guidance, body-data, and review cards while the canonical transparent calendar agenda rows remain unchanged. No Contract field, ownership, day priority, stored review, or formal plan is changed.

The follow-up correction on 2026-08-15 fixes the observed `ai_provider_job_expired` failure by moving execution ownership fully to Cloudflare Workflows. Android submits once, receives `RUNNING`, and only polls D1-backed state; app startup keeps existing unique polling work and cannot cancel provider execution. Provider generation and structure repair have no phone/network deadline, while the explicit connection test retains its short timeout. The provider key and normalized snapshot enter durable Workflow state only as AES-GCM ciphertext protected by a dedicated Cloudflare secret; D1 still stores only status, result, and usage metadata. Retry remains unavailable until the Workflow writes explicit `FAILED`.

The 2026-08-15 Package 5 orientation correction restores the intended inverse gate: ordinary app surfaces and timer preview are portrait-locked, only a running or paused native timer follows the user's system rotation preference, and terminal/reset transitions immediately return the Activity to portrait. Shenk never changes the device-wide auto-rotate setting.

Package 7 uses DeepSeek V4 Flash as its single phase-1 user-facing daily-review provider, with API-key-only setup in the app settings surface, Android Keystore storage, test-before-replace key handling, Worker-only provider calls, strict normalized daily-review input/output, deterministic input digests, versioned `daily_reviews`, a durable independent AI job queue, explicit incomplete-data generation, correction invalidation, offline recovery, a 23:15 unrecorded-day prompt, and generated-review notifications. The current conclusion and first two concrete actions are integrated into the primary Today guidance card; the review sheet is reserved for the complete review and visible generation progress. The underlying Worker boundary remains OpenAI Chat Completions-compatible for later adapters, but provider/model/endpoint selection is not exposed in phase 1. Daily reviews can explain, warn, and suggest only within existing strategy boundaries; neither Android nor Worker can mutate formal plans or routines through this path. The detailed implementation and acceptance matrix is recorded in `docs/android-package7-daily-review.md`.

The 2026-08-10 Package 7 corrective pass also exposes daily review from every non-future calendar date. Calendar details now combine the selected day's effective guidance, confirmed training facts, and concise review in one overview surface; a missing historical review can be generated from the 14-day snapshot ending on that date, independently of the training-edit window. No entity, ownership, priority, or Contract semantics changed.

The 2026-08-11 Packages 4/5/7 corrective pass aligns Today actions with the effective guidance. Only a timer-eligible formal plan with an explicit routine opens the native timer; rest, walking, local suggestions, and formal guidance without a timer-eligible routine open an editable same-day `training_logs` draft with the guidance type preselected. Rest saves use the existing `rested` completion status. Opening a missing daily review now immediately queues generation when provider configuration and critical inputs are ready, while incomplete inputs still require an explicit partial-generation confirmation. Queue writes remain idempotent and visibly pending through Room; no schema, Worker, ownership, or Package 8 scope changed.

The 2026-08-12 Package 7 reliability correction keeps reasoning enabled but gives the provider sufficient completion budget and requests JSON output explicitly. Android preserves the Worker's bounded error code instead of collapsing every proxy failure to HTTP 502, so retryable provider, quota, model, and malformed-response failures receive distinct user-facing guidance. Failed jobs remain manually retryable. No business entity, ownership, Contract, or AI permission changed.

The 2026-08-12 Package 3 reminder correction prevents application startup from reviving stale periodic reminders. Existing legacy work is migrated once to stable v2 unique work; ordinary startup keeps the future schedule, explicit setting changes replace it, and each daily worker validates that execution is after its configured time and inside the same day's bounded delivery window. Opening Shenk after midnight therefore cannot emit a stale morning, midday, or prior-evening prompt unless the user explicitly configured that reminder for that time. Xiaomi 14 verification at 01:06 confirmed all four legacy jobs cancelled, all v2 jobs scheduled, and zero active daily reminder notifications. No record is created or changed by this migration.

The 2026-07-27 Package 5 corrective pass makes timer completion eligibility fail closed: a session is offered as a formal-record draft only when its immutable snapshot explicitly has `countsTowardTraining: true`; missing legacy visibility fields no longer default to true. Pending completions can be acknowledged as ignored without modifying `timer_sessions`. Formal logs inherit the timer snapshot's `calendarVisible` and `countsTowardTraining`. Today exposes a timer action only when the effective formal plan explicitly references a cached `routineId`; the later 2026-08-11 correction routes non-timer guidance into the confirmed same-day record flow. Dark theme surfaces now define explicit foreground/container colors.

The 2026-08-03 Package 3 corrective pass fixes order-dependent Android wheel edits. Duration wheels now merge hour and minute changes against one current editor value, and the shared wheel listener always invokes the latest callback while synchronizing externally constrained selections. Sleep and deep-sleep duration wheels are covered by order and boundary regression tests; the single-column body-measurement wheels share the corrected listener.

The 2026-08-03 Package 5 corrective pass adds explicit routine removal to the native timer library. A user-confirmed removal writes a local-first `routine_templates` tombstone, immediately removes the routine from ordinary timer UI, and queues a cloud `delete` operation. Existing `timer_sessions` and `training_logs` remain unchanged. This is routine-library maintenance inside Package 5 and does not start Package 7.
