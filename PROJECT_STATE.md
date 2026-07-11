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
- Encrypted multi-device sync profiles; cloud stores ciphertext, not plaintext tokens.
- Merge/upsert `coach_plan_patch` import; missing fields and empty arrays are no-op; deletes are explicit.
- Dynamic `routine_templates` source of truth with timer local cache and fallback warning.
- Timer step `execution` expansion for preparation and bilateral actions.
- `timer_sessions` read in 身刻 and used only to prefill editable formal-record drafts.
- Routine `calendarVisible` and `countsTowardTraining` boundaries.
- Desktop Web UI is currently the accepted product baseline.

## Current Risks

1. Worker entity validation covers the P0 shape and duration rules, but Contract v1 schema validation is still incomplete.
2. Interrupted sessions are persisted as `stopped` on reset, configuration reset, and page exit; browser/process crashes still rely on local recovery and retry.
3. Published template immutability is documented but not yet enforced end-to-end.
4. Both frontends remain large single files; the first extracted timer-session core is only the start of modularization.
5. CI, pagination, incremental sync and entity-store outbox remain unfinished.

## Work Package Progress

### Work Package 0: baseline and fixtures - completed

- 身刻：coach patch、integration flow、recommendation engine、Worker security tests are runnable with Node.
- 计时器：session timing core and page contract tests are runnable with Node.
- Both repositories run the same checks on push and pull request through GitHub Actions.
- Broader end-to-end and fixture coverage remains part of later package-specific work.

### Work Package 1: data correctness and security - in progress

- Cloud pulls preserve local dirty records and surface a conflict instead of replacing them.
- Worker rejects stale blind writes, while identical retries remain idempotent.
- Sync profile reads require authentication and an allowed role.
- Timer cue/warning/plan text is rendered as text nodes instead of untrusted HTML.
- Timer records active, elapsed and paused durations separately; reset, routine changes and page exit finalize active sessions as `stopped`.
- Remaining: complete entity-level Contract v1 validation, expand recovery behavior after browser/process crash, and add CI coverage.

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

Start work package 0, then work package 1. Do not begin Android business implementation before Contract v1 is frozen.
