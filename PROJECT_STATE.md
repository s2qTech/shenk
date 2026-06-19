# Project State

Last updated: 2026-06-19

## Current Goal

Build 身刻 as a separate project from the existing home-training-timer.

Brand:

- Name: 身刻
- Slogan: 记录身体变化，掌控生活节奏
- Local brand assets live in `brand-assets/`.
- Current Web shell uses the full logo in the boot screen and the horizontal logo at the top right of each page header. The side rail is navigation-only.

The product should combine:

- Daily training log
- Calendar review
- Today recommendation
- Body weight tracking
- Weather capture for new records
- Screenshot-assisted workout data entry
- JSON import/export first
- Future cloud sync
- Future OpenAI-assisted recognition and analysis
- Future timer merge from the existing dedicated timer project

## User Context

- User uses a Xiaomi phone, so pure PWA cannot be the only mobile strategy.
- User wants both phone and desktop access.
- User prefers a calm Scandi minimal UI: white/light gray base, restrained green/blue/yellow accents, clean grouping, light shadows, no heavy gradients.
- Existing v1 timer is deployed separately and should remain stable.

## Architecture Decision

Use one shared Web core:

- Desktop: normal Web app.
- Phone: Android APK shell, preferably Capacitor.
- PWA: optional enhancement only, not the primary mobile route.

Data strategy:

- Phase 1: local IndexedDB plus JSON import/export remains required.
- Phase 2: Cloudflare Worker API + D1 is now the primary cloud sync path.
- `身刻` and `home-training-timer` must share one data model and one cloud database.
- Local IndexedDB is still the first write target; cloud sync mirrors data and must not block recording.
- AI integration must go through a backend function; never expose API keys in the frontend.

## MVP Scope

Implement first:

- Responsive app shell.
- Calendar view.
- Daily record editor.
- Today suggestion card.
- Local storage persistence.
- JSON import/export.
- Initial historical seed data from known chat records.

## Current Implementation Status

The project has been migrated into `C:\Workspace\training-assistant-v2`.

Because Node/npm are not available on PATH and the bundled runtime does not include Vite/React, the first working MVP uses dependency-free static HTML/CSS/JS:

- `index.html`
- `src/styles.css`
- `src/app.js`

Implemented now:

- Responsive app shell with calm light UI.
- Calendar-first layout with a left navigation rail and the calendar as the main workspace.
- Left navigation is navigation-only, with calendar, data, and settings tabs using the user-provided app icons. The app tab icons are still referenced from the local OneDrive image paths; brand assets are copied into `brand-assets/`.
- Date details open in a right-side sliding drawer instead of permanently taking space from the calendar.
- Dates default to view mode; today uses "编辑", past dates use "修正", and future dates show forecasts only.
- Completed days no longer auto-open the editor; the form appears only after explicit edit/correction.
- Suggestions are display-only; record creation and changes go through the explicit drawer editor and save action.
- Drawer editing now separates saves: workout changes use "保存训练", while fatigue, discomfort, sleep, energy, and weight use "保存状态".
- Calendar records use local sports icon assets from `assets/sports/`.
- Calendar-first record view with actual records, today suggestions, and future forecasts marked differently.
- Today is emphasized in the calendar with a visible badge and stronger border.
- Today recommendation using the rolling 7-day logic inside the calendar view.
- Recommendation rhythm is kept internal; the calendar main view no longer shows explanatory algorithm/status text.
- Desktop proportions use the full workspace: the calendar fills the available right-side width and the remaining viewport height.
- Data tab uses a two-column dashboard: weight and waist trends on the left, record overview and recent records on the right.
- Daily record editor separates workout data from one merged status record, so updating status cannot overwrite the workout.
- Status editing prioritizes weight, waist, sleep, and energy first; sleep, energy, and fatigue use color-coded sliders where moving right means a better state, with fatigue spanning the full row and discomfort choices grouped below.
- Completion status options are type-aware: strength/walk/cardio use completion or shortened/not-done states, recovery uses completed/stretch-only/not-done, and rest only shows as rested.
- Sleep is labeled as last night's sleep and stored on the date it affects.
- Pain/discomfort uses clear segmented levels instead of ambiguous sliders.
- IndexedDB persistence with localStorage fallback.
- JSON import/export and seed restore live in the low-frequency settings tab.
- Local data layer now writes a transition snapshot with both legacy `workouts`/`bodyMetrics` and shared `records` arrays for `plan_templates`, `routine_templates`, `daily_plan_items`, `plan_adjustments`, `timer_sessions`, `training_logs`, and `body_metrics`.
- Existing `workouts` are mapped into shared `training_logs`, and shared `training_logs` can be mapped back into legacy workout records for the current UI.
- Existing `bodyMetrics` are mapped into shared `body_metrics`, including weight, waist, body fat, muscle mass, sleep, energy, fatigue, pain, and notes.
- JSON export includes both legacy arrays and shared `records`; JSON import accepts old legacy exports and new shared-record exports.
- Repository seed data is empty by default so no personal health history is committed.
- Shared data-contract documents have been added for the upcoming two-sided refactor:
  - `docs/data-contract.md`
  - `docs/cloudflare-d1-schema.sql`
  - `docs/sync-protocol.md`
  - `docs/timer-integration-plan.md`

Timer code has been removed from this MVP for now. The dedicated timer project remains the source of truth and can be merged later when the record workflow is solid.

Current architecture decision:

- Database is mandatory, not optional.
- `身刻` and `home-training-timer` use the same D1-backed data model.
- Both sides may read all shared data.
- Write ownership is separated:
  - `身刻`: plan templates, daily plan snapshots, adjustments, training logs, body metrics, media metadata, feedback summaries.
  - `home-training-timer`: timer sessions and routine execution details.
- Plan changes must be versioned.
- Calendar days must store daily plan snapshots so old dates are not rewritten by future plan updates.
- Plan adjustments are separate records; they do not overwrite original daily plans.
- Actual completion logs are separate from planned items.

Defer:

- Cloud login/auth hardening.
- Timer integration from the dedicated timer project.
- APK build until the record-focused Web MVP is usable.
- Full screenshot OCR.
- Automatic historical weather backfill.

## Personal Health Data Policy

Do not commit real training history, body metrics, screenshots, exports, tokens, or API keys. Keep personal records in local IndexedDB, ignored export folders, or private backups outside the repository.

## Training Logic

Recommendation should not force a fixed weekly calendar. Use a rolling cycle:

- 2 strength sessions per 7-day window.
- 2 to 3 easy aerobic sessions per 7-day window.
- 1 quality walk or controlled improvement session per 7-day window.
- 1 recovery/rest/stretch day as needed.

Rules:

- No make-up punishment.
- Actual completion updates the next recommendation.
- Fatigue, poor sleep, pain, or unusual soreness should downshift the recommendation.
- Strength does not need to produce heavy sweating to be useful.
- Avoid wrist-loaded pushups/planks because user has wrist pain.
- Protect low back because of old severe lumbar disc history.
- Protect calf; downgrade if calf tightness or pain returns.

## Next Implementation Step

Continue from the static MVP with the shared data contract:

1. Update `身刻` local data layer to introduce `daily_plan_items`, `plan_adjustments`, `timer_sessions`, and `routine_templates` without breaking existing `workouts` and `bodyMetrics`.
2. Add a compatibility mapper from current `workouts` to the new `training_logs` shape.
3. Update calendar detail UI to show planned / adjusted / actual layers.
4. Extract current `home-training-timer` routines into shared routine templates.
5. Add timer session persistence to `home-training-timer`.
6. Add link-out integration from `身刻` to timer by `routineId`, `date`, and `dailyPlanItemId`.
7. Create Cloudflare D1 database and Worker API only after the local shared model works.
8. Keep JSON import/export compatible through the transition.

Current local shared data-layer items 1 and 2 are now implemented in `src/app.js`; continue with the planned / adjusted / actual calendar detail UI next.
