# Android Package 4: Calendar, Records, and Data

Updated: 2026-07-18
Status: accepted; native Android progress `5 / 9`

## Delivered

- A native whole-month calendar built from seven-column week bands.
- One effective day presentation with fixed priority: formal actual record, effective formal plan, then local fallback suggestion.
- Date details that do not expose adjustment history as a parallel instruction.
- Multiple formal `training_logs` on one date, recent correction, explicit deletion, and read-only older history.
- A records space for confirmed formal facts only.
- Thirty-day weight, body-fat, and muscle trends plus a concise waist-change summary.
- Horizontal Today/calendar continuity and explicit accessible navigation controls.

## Data Boundaries

- Package 4 reads plan, adjustment, body metric, and timer-session facts.
- Package 4 writes only user-confirmed `training_logs`.
- Saves and deletes update Room and enqueue outbox operations in the same transaction.
- Unknown JSON fields are retained when a formal record is edited.
- Records with `calendarVisible: false` remain outside calendar presentation.
- Missing status or measurement data remains missing.

## Acceptance Evidence

- Domain tests cover day priority, effective adjustments, edit windows, and calendar visibility.
- Room instrumentation tests cover local-first save/delete, outbox creation, multiple logs per date, and unknown-field retention.
- Compose instrumentation covers offline Today, morning entry, calendar, records, and data navigation.
- Android CI run `29630543898` passed source verification, unit tests, lint, APK assembly, Room instrumentation, and Compose instrumentation on API 36.
- Debug artifact: `shenk-package4-debug`.

## Compatibility and Rollback

- Contract v1 remains the production default; no D1 schema migration or destructive cloud operation was introduced.
- Web Shenk and Web timer behavior is unchanged.
- Rollback is an application-code rollback to the accepted Package 3 commit. Room data remains compatible because Package 4 uses the existing Package 2 stores and additive record content.

## Known Follow-up

- Package 5 owns native routine execution and `timer_sessions`; Package 4 must not absorb timer controls or write timer facts.
- The private APK still requires final Package 8 signing and release hardening before it is treated as a durable daily release.
