# Android Delivery Plan and Constraints

Updated: 2026-08-09
Status: confirmed planning baseline; Packages 0-7 accepted, Package 8 in progress, progress `8 / 9`

## 1. Delivery Model

The native Android effort is divided into nine work packages. Package completion is reported as `X / 9`; a package is complete only after its acceptance tests pass.

### Package 0: Decision Freeze and Native Skeleton

- Record the native Compose architecture decision.
- Freeze `mobile/` as a Capacitor prototype.
- Create `android-app/` with the agreed small module graph.
- Establish formatting, lint, unit tests, instrumentation tests, and CI.

Exit: a private debug APK opens on Xiaomi 14 and contains no production feature claims.

### Package 1: Contract v2 and Worker Compatibility

- Implement the contract plan, additive schemas, OpenAPI changes, role ownership, and sanitized fixtures.
- Add Worker validation and D1-compatible storage behavior.
- Keep v1 clients working during migration.

Exit: Web, Worker, Web timer, and Android contract fixtures all pass.

### Package 2: Native Local-First Foundation

- Room entities, repositories, outbox, conflict state, WorkManager sync.
- DataStore preferences and Keystore-backed secrets.
- Existing migration-code interoperability.
- Full JSON import/export through SAF.

Exit: offline writes survive process death, later sync, and never leak secrets.

### Package 3: Today, Check-in, and Measurements

- Morning check-in, pre-workout delta, measurements, missing-data states.
- Today effective-plan resolver and one-handed recording interactions.
- Notification settings and morning/midday prompts.

Exit: the user can complete a normal morning flow offline in under five minutes.

### Package 4: Calendar, Records, and Data

- Whole-month mobile calendar/stream.
- Actual > plan > suggestion presentation.
- Recent record correction and read-only historical window.
- One-month weight/body-fat/muscle trends and concise waist change.

Exit: month rhythm and data changes are understandable without using the Web app.

### Package 5: Native Timer and Routine Library

- Routine cache/library, explicit scene/role, preview, exercise details.
- Native timer state machine, TTS, keep-screen-on, audio ducking, phone-call pause, rotation recovery.
- Post-workout completion and pending-completion state.

Exit: a full routine completes on Xiaomi 14 through rotation, interruption, background, and offline conditions.

### Package 6: Plan Inbox and Weekly Feedback

- Provider-neutral clipboard paste into the plan inbox.
- Dormant cloud pending-draft compatibility may remain, but is not exposed or required by phase 1.
- Whole-patch validation, preview, apply, and undo-latest.
- Saturday feedback package copied to the clipboard for the established fitness-planning task.

Exit: an AI patch cannot partially apply, clear unrelated arrays, infer scenes, or bypass delete confirmation.

### Package 7: Daily AI Review

- Fixed DeepSeek V4 Flash configuration with API-key-only setup and connection test.
- Worker proxy with secret redaction.
- Durable AI job queue and daily-review versioning.
- Evening unrecorded prompt and review notification.

Exit: daily review works online and after offline recovery, and cannot mutate a formal plan.

### Package 8: Hardening and Private Release

Package 8 is implemented in the ordered stages documented in `android-package8-hardening-release.md`. Phase 1 update checks are foreground-only, authenticated, integrity-verified, user-initiated, and completed through Android's system installer. No always-running update service or remote push is introduced.

- Performance, accessibility, light/dark theme, reduced motion, font scaling.
- Backup/restore and migration rehearsal.
- Battery/background behavior on current HyperOS.
- Signed private APK and rollback instructions.

Exit: the user can rely on Android for the defined 80% daily workflow.

Phase-2 work starts only after Package 8: widget, Health Connect/Xiaomi data, and exercise animation assets.

## 2. Non-negotiable Product Boundaries

1. Android is not a scaled copy of the Web UI.
2. The app guides; the user executes and may deviate.
3. Formal AI plan > local suggestion, but actual execution/body feedback is authoritative.
4. Missing data stays missing. No automatic rest and no assumed normality for omitted changed fields.
5. Daily AI review cannot edit formal plans or routines.
6. Diet, weather planning, multi-user accounts, public distribution, widgets, wearables, and GIF assets are outside phase 1 unless this document is explicitly revised.
7. Every user-visible routine has explicit `scene` and `role`; no title/type/ID classification inference is permitted.
8. Deleted items disappear from normal UI; tombstones are an internal sync mechanism only.

## 3. Non-negotiable Technical Boundaries

1. Room is the Android business-data source of truth.
2. All business writes are local-first and transactionally enqueue an outbox item.
3. Remote data never silently overwrites a dirty local record.
4. Conflicts are visible unless changes are semantically identical and safely mergeable.
5. No WebView timer and no iframe timer inside Android.
6. Timer facts and formal training logs remain separate until user confirmation.
7. Web timer and native timer may write `timer_sessions`; record/calendar modules may only read them.
8. No secrets in URLs, logs, crash reports, Room business rows, analytics, fixtures, or JSON backups.
9. Shared contracts contain domain state only, never Android screen layout or gesture state.
10. New shared fields require schema, Worker validation, ownership, fixtures, and compatibility tests in the same package.
11. Empty patch arrays never mean replace or delete; deletion is explicit.
12. Unknown additive fields must be preserved where clients round-trip an entity.
13. Android supports the current stable platform used by the primary device; phase 1 carries no legacy Android compatibility matrix.
14. Gradle runs and compilation use the current LTS JDK, while Android bytecode level follows the newest class-file target officially supported by D8/R8.
15. Dependency upgrades select the newest stable, mutually compatible set and are verified as a set; preview versions and unsupported version mixtures are not production defaults.

## 4. Timer Safety Boundaries

1. One active session ID maps to one timer engine instance.
2. Rotation and activity recreation must not create a second clock.
3. A call interruption pauses rather than silently continuing.
4. Other audio is ducked for speech; it is not permanently stopped.
5. The app keeps the screen on only during active training.
6. Timer completion persists a fact before opening editable formal-record completion.
7. Exiting post-workout does not fabricate a formal log; it creates `pending completion`.
8. A walking plan does not auto-select an unrelated timer routine.
9. No custom lock-screen timer UI is introduced; any Android-required foreground notification is minimal and private.

## 5. UX Boundaries

1. Core morning and training flows must be comfortably operable with one hand.
2. Gestures are accelerators, not the only way to access a function.
3. Today never duplicates an active timer screen.
4. Calendar details present one effective instruction, not plan plus adjustment history as competing cards.
5. Actual execution takes visual priority over the original plan.
6. No nested cards, permanent tutorial copy, or desktop-form density in primary mobile flows.
7. Light/dark theme, font scaling, contrast, TalkBack semantics, and reduced motion are acceptance criteria, not cleanup work.
8. Animation must communicate spatial continuity and state change; decoration alone is insufficient.

## 6. AI and Security Boundaries

1. Daily AI receives the smallest normalized input needed for review.
2. Provider output is schema-validated before storage or display.
3. Provider failures leave a retryable job and never block local record saving.
4. Plaintext provider keys are transient in the Worker and must be redacted from logs and errors.
5. Advanced-AI strategy and weekly formal plan remain distinct from daily review.
6. Key missing information triggers a question; no answer keeps the original formal plan.

## 7. Test Matrix

### Contract and Migration

- v1 Web record -> v2 Android read.
- v2 additive record -> Web read without destructive round trip.
- Legacy combined body metric -> measurement plus morning check-in without duplication.
- Empty/missing patch arrays -> no-op.
- Explicit delete -> tombstone and immediate UI disappearance.
- Latest patch undo -> new revisions restore prior effective state.

### Offline and Sync

- Seven days offline with plans/routines cached.
- Offline check-in, body metrics, training log, timer session, and patch import queued.
- Process death with pending outbox.
- Reconnect, retry, identical idempotent response, stale revision conflict.
- Two-device edits: identical merge and material conflict.

### Timer

- Rotation in every state.
- Activity/background/process recreation.
- Incoming call and audio focus loss.
- TTS unavailable or voice missing.
- Routine with preparation, bilateral hold, bilateral reps, and simple steps.
- Completion fact saved before formal log.

### AI

- DeepSeek V4 Flash connection test, including unauthorized and timeout handling.
- Timeout, unauthorized, malformed JSON, partial response, and retry.
- Offline queue and later generation.
- Corrected day invalidates and regenerates latest review.
- Attempted plan mutation in AI output is rejected.

### UI and Device

- Xiaomi 14 on current HyperOS.
- Light/dark, largest practical font scale, TalkBack, reduced motion.
- Portrait and timer landscape.
- One-handed reachability and no horizontal clipping.
- Cold start and month rendering performance with realistic sanitized data volume.

## 8. Package Progress Report Format

Every package report must include:

```text
Progress: X / 9
Completed: concrete behavior and files
Verified: commands, fixtures, device tests
Compatibility: Web / Worker / Web timer / Android
Data changes: schema, migration, ownership
Known risks: unresolved items only
Next prerequisite: what must be true before the next package
```

No package may be marked complete because code compiles alone.

## 9. Change Control

- Any proposed destructive migration, implicit classification, replacement import, or ownership change must be described to the user and confirmed before implementation.
- A new phase-1 feature must identify what existing scope it replaces or delays.
- UI direction should be validated with an interactive Compose prototype on the target device before broad screen implementation.
- The first implementation step after approval is Package 0, not a feature screen.
