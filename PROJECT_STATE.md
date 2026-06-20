# Project State

## Product Boundary

身刻 and `home-training-timer` remain separate applications.

- 身刻 owns calendar, training logs, body metrics, plan records, weather records, media metadata, and feedback summaries.
- `home-training-timer` owns training execution, voice prompts, wake lock, routine timing, and timer sessions.
- Cloudflare Worker + D1 owns shared cloud database access, auth, role permissions, and conflict metadata.

Do not merge timer execution code into 身刻. 身刻 can open timer by URL and read completed timer sessions from the shared database.

## Data Ownership

Both clients may read shared records. Writes are role-scoped:

- `admin`: all entities.
- `shenk`: `plan_templates`, `routine_templates`, `daily_plan_items`, `plan_adjustments`, `training_logs`, `body_metrics`, `weather_logs`, `media_assets`, `feedback_summaries`.
- `timer`: `timer_sessions`.

## Shared Entities

- `plan_templates`
- `routine_templates`
- `daily_plan_items`
- `plan_adjustments`
- `timer_sessions`
- `training_logs`
- `body_metrics`
- `weather_logs`
- `media_assets`
- `feedback_summaries`

Phase 1 stores these entities as JSON envelopes in D1 table `cloud_records`. Entity-specific analytical tables can be added later.

## Implemented Locally

- Static Web MVP for 身刻.
- IndexedDB/localStorage persistence.
- Calendar and day-detail recording UI.
- Compatibility mapper from legacy `workouts` and `bodyMetrics` to shared `training_logs` and `body_metrics`.
- Cloud database settings panel in `src/app.js`.
- Cloud sync reads all shared entities and writes only 身刻-owned entities; `timer_sessions` is read-only in 身刻.
- Day detail is layered as plan, adjustment, timer session, actual training log, and body metrics.
- Completed/stopped timer sessions can be confirmed into local `training_logs` without duplicating existing timer-linked logs.
- Timer launch URLs are generated from daily plan items without putting timer tokens in the URL.
- Cloudflare Worker API in `cloudflare/worker.js`.
- D1 migration in `cloudflare/migrations/0001_cloud_records.sql`.
- Deployment template in `wrangler.toml.example`.
- Setup guide in `docs/cloudflare-cloud-db-setup.md`.
- API contract in `docs/cloud-records-api.md`.

## Cloud Database API

Preferred endpoints:

- `GET /api/health`
- `POST /api/records/query`
- `POST /api/records/upsert`
- `POST /api/timer-sessions`
- `POST /api/training-logs`
- `POST /api/body-metrics`
- `POST /api/daily-plan-items`

Legacy `/api/sync/pull` and `/api/sync/push` are kept only for compatibility.

## Training Logic

Recommendations use a rolling cycle, not fixed weekly punishment:

- 2 strength sessions per 7-day window.
- 2 to 3 easy aerobic sessions per 7-day window.
- 1 quality walk or controlled improvement session per 7-day window.
- 1 recovery/rest/stretch day as needed.

Rules:

- No make-up punishment.
- Actual completion updates the next recommendation.
- Fatigue, poor sleep, pain, or unusual soreness should downshift the recommendation.
- Avoid wrist-loaded pushups/planks because the user has wrist pain.
- Protect low back because of old severe lumbar disc history.
- Protect calf; downgrade if calf tightness or pain returns.

## Next Steps

1. Fill 身刻 settings with the Cloudflare Worker API base and `SHENK_TOKEN` locally.
2. Pull `timer_sessions` from cloud and confirm completed/stopped sessions into training logs.
3. Push 身刻-owned records with `/api/records/upsert`.
4. Exercise conflict handling with "use cloud" and "use local override".
5. Build the later feedback-summary export for Codex planning.
