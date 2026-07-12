# Project State

Updated: 2026-07-11

## Product Boundary

身刻 and `home-training-timer` remain separate applications and repositories.

- 身刻 owns plans, calendar snapshots, plan adjustments, formal training logs, body metrics, trends, feedback summaries, and sync coordination.
- `home-training-timer` owns routine execution, voice, wake lock, timing state, and `timer_sessions`.
- Cloudflare Worker + D1 owns authentication, role permissions, validation, revisions, conflict metadata, and shared cloud storage.

Do not merge timer execution code into 身刻. Do not let either client write entities owned by the other client.

## Shared Data Ownership

身刻 writes:

- `plan_templates`
- `routine_templates`
- `daily_plan_items`
- `plan_adjustments`
- `training_logs`
- `body_metrics`
- `weather_logs`
- `media_assets`
- `feedback_summaries`

Timer writes:

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
- Desktop Web UI is currently the accepted product baseline.

## Current Risks

1. Contract v1 has JSON Schema, OpenAPI, versioned requests and fixtures. Worker still uses deliberate hand-written validation rather than a full JSON Schema runtime, so every new field still needs a matching Worker validation review.
2. In-progress timer sessions are persisted every 15 seconds and recovered as `stopped` using the last activity heartbeat. A process kill can still lose up to one heartbeat interval of active time.
3. Published templates are protected when explicitly marked `lifecycle: "published"`, `publishedAt`, or `immutable: true`; legacy templates without a marker remain mutable for migration compatibility.
4. Both frontends remain large single files; the first extracted timer-session core is only the start of modularization.
5. Pull pagination is available and both Web clients follow it. IndexedDB still stores a whole snapshot, so the entity-store outbox remains work package 4.

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

## Active Development Direction

The canonical next-stage documents are:

- `docs/next-stage-development-plan.md`
- `docs/development-constraints.md`
- `docs/data-contract.md`
- `docs/system-design.md`
- `docs/mobile-strategy.md`

Work proceeds in seven packages:

0. Freeze baseline and fixtures.
1. Data correctness and security.
2. Shared Contract v1 and version governance.
3. Modularization and CI.
4. IndexedDB/outbox/sync v2.
5. Web information architecture and accessibility.
6. Android foundation with shared domain logic and independent mobile UI.

Every completed package must report progress as `X / 7`, verification, compatibility, remaining risks, and next prerequisites.

## Immediate Next Step

Start work package 4: design an entity-store IndexedDB migration and durable outbox. This requires an explicit migration preview, local backup, compatibility read path, and rollback plan before any user data is changed.
