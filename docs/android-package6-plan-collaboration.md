# Android Package 6: Plan Collaboration and Weekly Feedback

Updated: 2026-08-02
Status: implemented; Xiaomi 14 acceptance pending; Android delivery progress remains `6 / 9`

## Scope

Package 6 adds the native plan inbox and weekly feedback workspace to `android-app/`. Phase 1 advanced-AI exchange is deliberately provider-neutral and clipboard-only. It does not add daily AI review, provider configuration, automatic plan generation, API-based plan mutation, or any Package 7 behavior.

## Implemented Behavior

- A user copies a `coach_plan_patch` from an established advanced-AI or Codex planning task, then pastes it into the Android plan inbox.
- Android is not a `text/plain` share target and does not invoke the system share sheet for plan exchange or weekly feedback.
- Cloud planning-exchange records and MCP/OAuth code may remain as dormant future infrastructure, but they are not visible in the Phase 1 user flow and are not acceptance dependencies.
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
- Feedback is stored as `feedback_summaries`, queued through the local-first outbox, and copied to the system clipboard for the user to paste into the planning task they choose.
- A Saturday 22:30 reminder is enabled by default and configurable with the existing reminder sheet. It generates the local summary first and opens the feedback workspace from the notification.
- Today exposes Data and plan collaboration through a fixed two-destination native bottom bar. The content body contains no web-style plan collaboration link row, and the morning measurement summary does not duplicate the Data destination.

## Transport Boundary

- Phase 1 transport is exactly two user-controlled actions: copy normalized feedback out, and paste a strict `coach_plan_patch` back in.
- The app must not name or require a particular AI provider in this path.
- A future API, MCP, Skill, or agent integration may replace the transport only. It must reuse the same normalized planning snapshot, `coach_plan_patch`, validation, authority checks, preview, user confirmation, audit, undo, and local-first synchronization semantics.
- No future transport may directly mutate formal planning entities.

## Data and Compatibility

- No D1 table migration is required. Shared records continue to use the existing envelope storage.
- Validated `coach_plan_patch` imports use Contract v2 in Android, Web, Worker, and dormant MCP infrastructure. Every record added, updated, or explicitly deleted by an accepted patch is persisted with a v2 envelope. General Web records outside that patch remain Contract v1-compatible during migration; that compatibility does not permit v1 plan-patch imports.
- `planning_runs` and `coach_plan_patches` remain owned by the separate `planning_exchange` role for future transport work. They are not formal plan entities and are not surfaced by the Phase 1 Android inbox.
- `plan_import_batches` is Android planning history used for preview audit and latest-only undo.
- `feedback_summaries` remains owned by the Shenk record role.
- No token, API key, migration code, health value, or provider secret is added to source, fixtures, logs, URLs, or clipboard instructions.

## Automated Verification

- Patch engine tests cover empty-array no-op, authority rejection, explicit delete, unknown delete, and replace-mode rejection.
- Room instrumentation tests cover atomic routine-only apply without calendar clearing, whole-patch rejection without writes, latest-only undo, and dormant planning-exchange record compatibility.
- Compose instrumentation verifies the fixed Today Data/Plan destinations, the paste-based inbox, and that dormant cloud drafts are not shown in the Phase 1 flow.
- Repository governance tests reject Android system sharing and visible Web ChatGPT/MCP setup in the Phase 1 UI.
- `gradlew test lintDebug assembleDebug assembleDebugAndroidTest` must pass.

## Xiaomi 14 Acceptance

1. In the established Codex fitness-planning task, create one harmless Contract v2 patch and copy it.
2. In Shenk Android, open **计划协作 > 草案**, paste the patch, and confirm the calendar remains unchanged before preview and apply.
3. Preview the patch and verify additions, updates, deletions, and validation messages are readable.
4. Apply it and verify the formal calendar changes only after confirmation.
5. Paste a second harmless patch, then leave or clear the inbox without applying it. Verify the formal plan remains unchanged.
6. Paste a valid routine-only Contract v2 patch containing `dailyPlanItems: []`. Apply it and verify the existing calendar remains unchanged.
7. Remove required `scene` from a routine. Verify the whole patch is rejected and no routine or calendar item changes.
8. Preview an explicit delete. Verify the delete count is visible and apply requires a second confirmation.
9. Apply a harmless update, use **撤销最近一次**, and verify it can be undone only once.
10. Disable networking, apply a harmless patch, and verify the change is available locally and enters the outbox. Reconnect and verify it synchronizes once without duplicates.
11. Generate the weekly feedback package and verify recent training, status, timer facts, body trends, and current planning context are present.
12. Tap **复制复盘资料**, paste into a neutral text field or the established Codex planning task, and verify the copied content is complete. No external app or share chooser should open.
13. Verify Shenk is absent from Android's generic `text/plain` share-target list.
14. Enable the weekly reminder and verify reminder permission handling and the configured Saturday time are preserved after restart.

## Risks and Rollback

- Clipboard content can be read by the user-selected destination and may be visible to the operating system for a limited time. The app never chooses the AI destination on the user's behalf.
- Weekly reminders are best-effort under HyperOS background scheduling and may be delayed by the operating system.
- Rollback is code-only: install the last accepted Package 5 APK. Additive Package 6 records may remain in Room/D1 but Package 5 ignores them.
- Rollback must not clear Room, app data, the outbox, import history, or feedback summaries.
