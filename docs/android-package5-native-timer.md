# Android Package 5: Native Timer and Routine Library

Status: accepted on Xiaomi 14 on 2026-07-19; Android delivery progress `6 / 9`.

Updated: 2026-07-27

Package 5 acceptance also includes a corrective gate for previously delivered native surfaces: a clean install must expose the existing-data connection flow, and Today/Calendar must render as intentional native product surfaces rather than unstyled functional skeletons. This correction does not start Package 6 or change package progress.

## Scope

Package 5 adds the production native routine library and timer to `android-app/`. It does not embed or merge the Web timer, implement the plan inbox, or add AI review behavior.

## Implemented Behavior

- Reads `routine_templates` from the Room source of truth and remains usable from the local cache while offline.
- Accepts only routines with explicit valid `scene`, `role`, lifecycle, visibility, and steps. It never classifies a routine from its title, type, ID, or exercise contents.
- Preserves optional step `execution` data and expands preparation, alternating, and bilateral execution at runtime.
- Shows logical exercises in the library and preview while the running timer uses expanded execution steps.
- Supports preview, start, pause, resume, previous, next, stop, reset, portrait/landscape layouts, TTS cues, audio ducking, active-training screen-on behavior, and phone-call pause.
- The running surface keeps the immediate next runtime action visible in portrait and landscape, including during rest. Previous, next, and stop use fixed-size icon controls, while long cues and warnings scroll instead of being clipped.
- TTS queues the first non-countdown cue until initialization completes, verifies Chinese voice availability, reports an actionable error in the running surface, reads all stored action cues, and announces the upcoming action five seconds before transition.
- Checkpoints active state and rebuilds the timer engine after Activity or process recreation.
- Persists terminal `timer_sessions` locally before opening post-workout completion. The timer module is the only writer of this entity.
- Keeps `timer_sessions` separate from formal `training_logs`. Closing completion leaves a pending item; saving the editable completion creates a linked formal log through the record module.
- Today-plan entry preserves `routineId`, `dailyPlanItemId`, and `planTemplateId`. Formal strength/recovery plans may preselect an exact cached routine; walking plans open the walking scene and require a manual routine choice.
- Missing or uncached plan routines show an explicit message and never silently fall back to an old built-in flow.
- A new device can connect to the existing cloud dataset with the Web-generated migration code. The app decrypts the profile locally, stores secrets in Android Keystore, immediately synchronizes Room, and never persists the migration code.
- The Android application declares network access explicitly. Profile download, PBKDF2 decryption, and initial synchronization run off the main thread; invalid, expired, offline, malformed, and local secure-storage failures remain recoverable in the connection sheet instead of terminating the activity.
- A connected device synchronizes on process start; local saves continue to enqueue the existing WorkManager path, and manual sync remains available from Today > More > Data Sync.
- Today presents the effective instruction as the elevated primary card with one training action. Calendar presents a whole-month rhythm in weekly bands: actual facts are solid, formal plans are patterned, local suggestions remain quiet, and Today is visually elevated.

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
3. Start and complete a routine with voice cues and music ducking. Confirm the first action cue is spoken, every action includes its stored cues, and the upcoming action is announced before transition. If Chinese TTS is unavailable, confirm the screen explains how to fix it instead of failing silently.
4. Rotate between portrait and landscape without resetting or duplicating the clock.
5. Background and reopen the app without losing progress.
6. Receive a phone call while running and confirm automatic pause.
7. Finish offline and confirm a pending completion survives restart.
8. Save the post-workout form and confirm one formal log links to one timer session.
9. Reconnect and confirm the timer fact and formal log synchronize without duplicates.
10. On a clean install, use Today > Connect Existing Data with a migration code generated by Web Shenk; confirm plans, records, metrics, and routines appear without entering API addresses or tokens.
11. Restart the app and confirm the cloud connection remains available, startup sync runs, and the migration code is not requested again.
12. Open More > Data Sync and confirm manual sync reports pulled/pushed counts without changing local data on failure.
13. Review Today and Calendar in light and dark themes: the primary guidance is visibly a card, the current day is elevated, and actual/plan/suggestion remain distinguishable without losing the full month.
14. Run a long-cue action and a rest step in portrait and landscape. Confirm cues and warnings remain scrollable, the next action is always visible, and previous/next labels cannot wrap because the controls are icons.

Acceptance result: passed. The primary device completed the routine, rotation, persistence, offline/sync, completion, portrait/landscape UI, Chinese TTS, upcoming-action, and music-ducking gates without a crash or duplicate record.

## 2026-07-27 Corrective Gate

- Pending formal-record candidates fail closed. A terminal session is shown under "待补训练记录" only when its immutable snapshot explicitly contains `countsTowardTraining: true`; legacy sessions missing this field are treated as auxiliary facts.
- Dismissing a pending item writes an additive `timer_session_links` acknowledgement with `action: "ignored"`. It never edits or deletes the source `timer_sessions` fact and does not create a formal training log.
- A confirmed formal log inherits `calendarVisible` and `countsTowardTraining` from the timer-session snapshot. Auxiliary warm-up, stretch, cue-only, child, and test flows therefore remain outside the calendar and training statistics when their authoritative routine flags are false.
- Today shows "进入训练" only when the effective formal plan has an explicit `routineId`. Rest, easy-walk, quality-walk, and fallback guidance without a runnable routine remain instructions rather than timer entry points.
- The exact cached routine supplies its own explicit `scene`; Android does not infer a scene from title, training type, routine ID, or exercise content.
- Dark surfaces use explicit Material foreground/container roles. Trend values also use semantic favorable/unfavorable colors instead of relying on light-theme defaults.

Corrective verification completed with JVM tests, Kotlin compilation, lint, debug APK assembly, and installation on the connected Xiaomi 14 without clearing user data. HyperOS blocked installation of the separate instrumentation-test APK until its USB-install confirmation is granted; this does not affect the installed application or the in-memory automated test build.

## Risks and Rollback

- OEM background restrictions can affect foreground-service continuity and must be verified on current HyperOS.
- TTS voice availability depends on the installed Android speech engine; the app prefers a female voice when one is exposed and otherwise uses the system default.
- Rollback is code-only: install the previous accepted Package 4 APK. Package 5 uses additive records and does not require deleting or rewriting user data.
- A failed terminal-session upload remains in the outbox; rollback must not clear Room, app data, or pending completions.
