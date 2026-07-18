# Android Package 5: Native Timer and Routine Library

Updated: 2026-07-18
Status: implemented; CI and Xiaomi 14 acceptance pending

## Scope

Package 5 adds the production native routine library and timer to `android-app/`. It does not embed or merge the Web timer, implement the plan inbox, or add AI review behavior.

## Implemented Behavior

- Reads `routine_templates` from the Room source of truth and remains usable from the local cache while offline.
- Accepts only routines with explicit valid `scene`, `role`, lifecycle, visibility, and steps. It never classifies a routine from its title, type, ID, or exercise contents.
- Preserves optional step `execution` data and expands preparation, alternating, and bilateral execution at runtime.
- Shows logical exercises in the library and preview while the running timer uses expanded execution steps.
- Supports preview, start, pause, resume, previous, next, stop, reset, portrait/landscape layouts, TTS cues, audio ducking, active-training screen-on behavior, and phone-call pause.
- Checkpoints active state and rebuilds the timer engine after Activity or process recreation.
- Persists terminal `timer_sessions` locally before opening post-workout completion. The timer module is the only writer of this entity.
- Keeps `timer_sessions` separate from formal `training_logs`. Closing completion leaves a pending item; saving the editable completion creates a linked formal log through the record module.
- Today-plan entry preserves `routineId`, `dailyPlanItemId`, and `planTemplateId`. Formal strength/recovery plans may preselect an exact cached routine; walking plans open the walking scene and require a manual routine choice.
- Missing or uncached plan routines show an explicit message and never silently fall back to an old built-in flow.

## Data and Compatibility

- No D1 migration is required.
- Contract v1 remains the production default; existing Web clients are unchanged.
- Android reads additive routine fields without dropping unknown JSON fields from the shared record.
- Native sessions use stable session IDs as idempotency keys and enter the existing local-first outbox.
- Existing Web `timer_sessions` remain readable; Android does not reinterpret them as formal logs.

## Verification

Automated gates:

- Repository static Android Package 5 checks.
- Routine JSON decoding and explicit classification tests.
- Execution expansion and timer state-machine unit tests.
- Room routine cache, timer-session idempotency, pending completion, and guidance-reference instrumentation tests.
- Compose offline routine-library and preview test.
- GitHub Actions Android compile, lint, unit, instrumentation, and debug APK build.

Required Xiaomi 14 acceptance:

1. Open a cached routine with networking disabled.
2. Preview logical exercises and inspect exercise details.
3. Start and complete a routine with voice cues and music ducking.
4. Rotate between portrait and landscape without resetting or duplicating the clock.
5. Background and reopen the app without losing progress.
6. Receive a phone call while running and confirm automatic pause.
7. Finish offline and confirm a pending completion survives restart.
8. Save the post-workout form and confirm one formal log links to one timer session.
9. Reconnect and confirm the timer fact and formal log synchronize without duplicates.

## Risks and Rollback

- OEM background restrictions can affect foreground-service continuity and must be verified on current HyperOS.
- TTS voice availability depends on the installed Android speech engine; the app prefers a female voice when one is exposed and otherwise uses the system default.
- Rollback is code-only: install the previous accepted Package 4 APK. Package 5 uses additive records and does not require deleting or rewriting user data.
- A failed terminal-session upload remains in the outbox; rollback must not clear Room, app data, or pending completions.

