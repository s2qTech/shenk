# Cloudflare D1 cloud database setup

身刻和 home-training-timer 使用同一套 Cloudflare Worker + D1 数据库。这里的云端能力不是让两个前端互相同步代码或职责，而是让它们通过同一份数据库读写各自负责的数据。

## Ownership

- 身刻负责：计划、日历记录、身体指标、状态、天气、媒体元数据、反馈摘要。
- home-training-timer 负责：计时器执行会话和动作执行细节。
- Worker 负责：认证、角色权限、D1 读写、冲突元数据。

前端不能直接访问 D1，必须通过 Worker API。

## Files

- `cloudflare/worker.js`: Worker API.
- `cloudflare/migrations/`: D1 migrations for shared records and encrypted sync profiles.
- `wrangler.toml.example`: local deployment template.

Do not commit a real `wrangler.toml`, API tokens, personal exports, screenshots, or health data.

## Create D1

```powershell
npx wrangler d1 create shenk
```

Copy `wrangler.toml.example` to `wrangler.toml`, then fill the returned `database_id`.

## Apply Migration

```powershell
npx wrangler d1 migrations apply shenk --remote
```

## Configure Secrets

Use three separate secrets so each app can only write its own area.

```powershell
npx wrangler secret put ADMIN_TOKEN
npx wrangler secret put SHENK_TOKEN
npx wrangler secret put TIMER_TOKEN
```

- `ADMIN_TOKEN`: full read/write, for maintenance only.
- `SHENK_TOKEN`: 身刻 can write plans, logs, metrics, weather, media, feedback.
- `TIMER_TOKEN`: timer can write timer sessions.

`SYNC_TOKEN` is still accepted as a backward-compatible admin token, but new deployments should use the role tokens above.

## Deploy

```powershell
npx wrangler deploy
```

The API base is:

```text
https://<worker-name>.<account-subdomain>.workers.dev/api
```

Put this API base into 身刻 settings. Put the same API base plus `TIMER_TOKEN` into home-training-timer settings.

## API

Health check:

```text
GET /api/health
```

Generic query:

```text
POST /api/records/query
```

Generic upsert:

```text
POST /api/records/upsert
```

Convenience writes:

```text
POST /api/timer-sessions
POST /api/training-logs
POST /api/body-metrics
POST /api/daily-plan-items
```

Required auth header:

```text
Authorization: Bearer <token>
```

Optional device header:

```text
X-Shenke-Device-Id: <device-id>
```

## Data Model

Phase 1 stores every shared object as a JSON envelope in `cloud_records`:

```json
{
  "entity": "timer_sessions",
  "id": "session_...",
  "revision": 1,
  "deviceId": "timer_web_...",
  "createdAt": "2026-06-20T00:00:00.000Z",
  "updatedAt": "2026-06-20T00:00:00.000Z",
  "deletedAt": null,
  "data": {}
}
```

This keeps the schema stable while the app model is still changing. Analytical tables can be added later without breaking the frontends.
