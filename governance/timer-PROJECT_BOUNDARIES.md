# home-training-timer Project Boundaries

Updated: 2026-07-17

Any coding model must read the repository-root `AGENTS.md` before this file.

## Role

`home-training-timer` is the Web execution engine for 身刻 routines. It remains an independent repository and deployment. Android has an independent native timer implementation, but both timer implementations conform to the same shared contract and behavior fixtures.

It owns:

- routine selection and preview;
- runtime step expansion;
- timing state, pause/resume/stop;
- voice prompts, sound cues, and wake lock;
- local routine cache for offline execution;
- creation and upload of `timer_sessions`.

It does not own:

- plans or daily calendar snapshots;
- plan adjustments;
- formal `training_logs`;
- body metrics, trends, or feedback summaries;
- sync conflict decisions for Shenk-owned entities;
- Android presentation or native platform integration.

## Shared Contract

Canonical contracts and governance live in the Shenk repository. Read these first:

- `AGENTS.md`
- `governance/guardrails.json`
- `docs/data-contract.md`
- `docs/development-constraints.md`
- `docs/android-contract-v2-plan.md`
- `docs/adr/0004-timer-facts-and-formal-records.md`
- `docs/adr/0005-explicit-routine-scene-role.md`

Contract v1 remains the production contract. Contract v2 is a plan until Worker and cross-client compatibility gates pass. This repository keeps an identical v1 test mirror at `contracts/v1/` so its offline suite can verify compatibility.

Timer changes that add or change shared fields must update the canonical contract first.

## Mandatory Rules

- Read `routine_templates` from the shared database and cache the last valid set locally. A routine is shown in the standalone selector by default; only explicit `timerVisible: false` or `needsTimer: false` hides it.
- Built-in routines are fallback/debug only and must show an explicit warning when used.
- Do not silently fall back when a requested cloud `routineId` is unavailable.
- Preserve unknown compatible fields when caching and serializing routine templates. In particular, `description`, `keyPoints`, `cues`, `warnings`, `safetyNotes`, `breath`, `execution`, and `mediaAssetId` must survive normalization.
- `scene` and `role` are explicit authoritative template fields. Never infer or rewrite them from title, training type, routine ID, or steps.
- Write only `timer_sessions` to the shared database.
- Never write `training_logs` or modify plans.
- `actualSeconds` represents active execution time and excludes pauses.
- Completion, stop, reset, and interrupted exit need explicit session semantics.
- Session upload must be idempotent.
- Cloud-controlled text must be rendered as text, not untrusted HTML.
- Tokens must not enter source code, documentation, URLs, logs, or Git.
- `calendarVisible: false` and `countsTowardTraining: false` must be preserved in session context for non-calendar routines.

## Runtime Model

`routine_templates.steps[].execution` may expand one user-facing action into preparation, left side, switch, and right side runtime steps.

The UI must:

- count the original action as one action;
- calculate planned duration from expanded runtime steps;
- speak expanded runtime labels;
- allow action guidance to be inspected before starting;
- keep selection/preview state separate from active execution state.

## Module Boundaries

The page shell owns DOM, audio, wake lock, and event wiring. Reusable pure cores are documented in `MODULE_BOUNDARIES.md`:

- `timer-execution-core.js` expands execution details;
- `timer-preview-core.js` groups expanded steps as one visible action;
- `timer-session-core.js` owns duration and interruption calculations.

The catalog loader must preserve template-provided `scene` and `role` and must not infer, overwrite, delete, or migrate routine metadata during normal loading.

## Cross-client Change Order

1. Update the canonical Shenk contract, ownership, compatibility, and sanitized fixtures.
2. Update Worker validation and role enforcement.
3. Update the Web timer and native Android timer behavior.
4. Run contract, engine, offline, interruption, idempotency, and security tests.
5. Update both repositories' boundary documents.

## Definition of Done

A timer change is complete only when behavior, compatibility, tests, documentation, offline behavior, cloud idempotency, and safe rendering have all been verified and the affected repository has been pushed.
