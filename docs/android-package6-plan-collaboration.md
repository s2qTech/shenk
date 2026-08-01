# Android Package 6: Plan Collaboration and Weekly Feedback

Updated: 2026-08-01
Status: implemented; Xiaomi 14 acceptance pending; Android delivery progress remains `6 / 9`

## Scope

Package 6 adds the native plan inbox and weekly feedback workspace to `android-app/`. It does not add daily AI review, provider configuration, automatic plan generation, or any Package 7 behavior.

## Implemented Behavior

- Android is a `text/plain` share target, so a `coach_plan_patch` can be shared from ChatGPT into the plan inbox. Manual paste remains available.
- Cloud-synchronized `coach_plan_patches` with status `pending` appear at the top of the Android draft inbox. ChatGPT submission never applies a formal plan automatically.
- A cloud draft can be loaded into the same whole-patch preview, applied, or rejected. Apply marks the exchange record `applied`; reject marks it `rejected` and leaves formal plans unchanged.
- Applying a cloud draft writes formal planning records, import history, the handled exchange record, and all outbox rows in one Room transaction.
- Patch extraction accepts a direct JSON object, a fenced JSON block, or a wrapper containing `patch` / `coach_plan_patch`.
- Validation is whole-patch and uses the same strict Contract v2 import rules in Android and Web. Invalid input produces a readable error list and writes nothing.
- Every new patch must include `contractVersion: "2.0"`. Missing or different versions, `replaceMode: true`, the legacy singular `planTemplate`, and legacy plan-adjustment shorthand are rejected as a whole.
- Missing entity arrays and empty arrays are no-op. Default behavior is merge/upsert; `replaceMode: true` is rejected.
- `plan_templates`, `routine_templates`, `daily_plan_items`, and `plan_adjustments` are upserted by ID.
- Routine changes require explicit `scene`, `role`, lifecycle, visibility booleans, training ownership, and non-empty steps. Android never infers routine authority from a title, type, ID, or exercise list.
- Preview reports additions, updates, and deletions before apply. Only explicit `operation: "delete"` or a tombstone can delete; deletion requires a second confirmation.
- Apply writes all affected records, the import batch, and their outbox rows in one Room transaction.
- The latest applied patch can be undone once. Undo is blocked if any affected record changed after apply, so later work is never silently overwritten.
- Weekly feedback summarizes the last 14 days of training logs, status check-ins, and timer facts, plus 30-day body trends and the current plan/routine context.
- Feedback is stored as `feedback_summaries`, queued through the local-first outbox, and shared to ChatGPT with the Android chooser as fallback.
- A Saturday 22:30 reminder is enabled by default and configurable with the existing reminder sheet. It generates the local summary first and opens the feedback workspace from the notification.
- Today exposes Data and plan collaboration through a fixed two-destination native bottom bar. The content body contains no web-style plan collaboration link row, and the morning measurement summary does not duplicate the Data destination.

## Data and Compatibility

- No D1 table migration is required. Shared records continue to use the existing envelope storage.
- Validated `coach_plan_patch` imports use Contract v2 in Android, Web, Worker, and MCP. Every record added, updated, or explicitly deleted by an accepted patch is persisted with a v2 envelope. General Web records outside that patch remain Contract v1-compatible during migration; that compatibility does not permit v1 plan-patch imports.
- `planning_runs` and `coach_plan_patches` are owned by the separate `planning_exchange` role. They are not formal plan entities.
- `plan_import_batches` is Android planning history used for preview audit and latest-only undo.
- `feedback_summaries` remains owned by the Shenk record role.
- No token, API key, migration code, health value, or provider secret is added to source, fixtures, logs, or share URLs.

## Automated Verification

- Patch engine tests cover empty-array no-op, authority rejection, explicit delete, unknown delete, and replace-mode rejection.
- Room instrumentation tests cover atomic routine-only apply without calendar clearing, whole-patch rejection without writes, latest-only undo, pending cloud apply, and rejection without formal-plan mutation.
- Compose instrumentation verifies the fixed Today Data/Plan destinations and that a synchronized cloud draft remains pending until previewed.
- `gradlew test lintDebug assembleDebug assembleDebugAndroidTest` passes.

## Xiaomi 14 Acceptance

1. In ChatGPT Web, call `get_planning_snapshot`, then submit one harmless patch with `submit_coach_plan_patch`. Verify ChatGPT reports a pending draft, not an applied plan.
2. In Shenk Android, synchronize cloud data, then use the fixed **计划** action at the bottom of Today and open **草案**. Verify the cloud draft appears as **待确认** and the calendar has not changed.
3. Open that draft, verify additions/updates/deletions, and apply it. Verify it disappears from pending only after confirmation and the formal calendar changes accordingly.
4. Submit a second harmless draft, synchronize, reject it in Android, and verify formal plans remain unchanged.
5. Paste a valid routine-only Contract v2 patch containing `dailyPlanItems: []`. Preview it, apply it, synchronize, and verify the existing calendar is unchanged.
6. Share the same patch from ChatGPT to Shenk and verify it opens in the inbox without applying automatically.
7. Remove required `scene` from a routine. Verify the whole patch is rejected and no routine or calendar item changes.
8. Preview an explicit delete. Verify the delete count is visible and apply requires a second confirmation.
9. Apply a harmless update, use **撤销最近一次**, and verify it can be undone only once.
10. Disable networking, apply a harmless patch, and verify the change is available locally and enters the outbox. Reconnect and verify it synchronizes once without duplicates.
11. Generate the weekly feedback package and verify recent training, status, timer facts, body trends, and current planning context are present.
12. Share the feedback. Verify ChatGPT opens when installed and the Android chooser appears as fallback.
13. Enable the weekly reminder and verify reminder permission handling and the configured Saturday time are preserved after restart.

## Risks and Rollback

- Android share payloads can contain arbitrary text; they are never applied until extraction, validation, preview, and user confirmation complete.
- Weekly reminders are best-effort under HyperOS background scheduling and may be delayed by the operating system.
- Rollback is code-only: install the last accepted Package 5 APK. Additive Package 6 records may remain in Room/D1 but Package 5 ignores them.
- Rollback must not clear Room, app data, the outbox, import history, or feedback summaries.
