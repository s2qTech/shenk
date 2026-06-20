# Cloud Records API

This API is the shared cloud database contract for 身刻 and `home-training-timer`.

It is not a timer implementation and not an app-to-app sync layer. Both clients use the same Cloudflare Worker + D1 database, while write ownership is enforced by role tokens.

## Base

```text
https://<worker>.workers.dev/api
```

## Auth

```text
Authorization: Bearer <token>
X-Shenke-Device-Id: <stable-device-id>
```

Roles:

- `ADMIN_TOKEN`: read/write all records.
- `SHENK_TOKEN`: write 身刻-owned records, including `timer_session_links`.
- `TIMER_TOKEN`: write timer-owned records.

## Entity Envelope

```json
{
  "entity": "training_logs",
  "id": "log_2026-06-20_001",
  "revision": 1,
  "deviceId": "shenke_web_...",
  "createdAt": "2026-06-20T12:00:00.000Z",
  "updatedAt": "2026-06-20T12:00:00.000Z",
  "deletedAt": null,
  "data": {}
}
```

The application-specific payload lives in `data`.

## Entities

- `plan_templates`
- `routine_templates`
- `daily_plan_items`
- `plan_adjustments`
- `timer_sessions`
- `timer_session_links`
- `training_logs`
- `body_metrics`
- `weather_logs`
- `media_assets`
- `feedback_summaries`

## Query

```text
POST /api/records/query
```

Request:

```json
{
  "deviceId": "shenke_web_...",
  "since": "2026-06-20T00:00:00.000Z",
  "entities": ["training_logs", "body_metrics", "timer_sessions"]
}
```

Response:

```json
{
  "ok": true,
  "serverTime": "2026-06-20T12:00:00.000Z",
  "records": []
}
```

The Worker filters readable entities by token role.

## Upsert

```text
POST /api/records/upsert
```

Request:

```json
{
  "deviceId": "shenke_web_...",
  "records": [
    {
      "entity": "training_logs",
      "id": "log_2026-06-20_001",
      "baseRevision": 1,
      "data": {},
      "createdAt": "2026-06-20T12:00:00.000Z",
      "updatedAt": "2026-06-20T12:10:00.000Z",
      "deletedAt": null
    }
  ]
}
```

Response:

```json
{
  "ok": true,
  "serverTime": "2026-06-20T12:10:00.000Z",
  "accepted": [
    { "entity": "training_logs", "id": "log_2026-06-20_001", "revision": 2, "updatedAt": "2026-06-20T12:10:00.000Z" }
  ],
  "conflicts": []
}
```

The Worker rejects writes outside the caller role.

## Convenience Endpoints

These wrap `records/upsert` and validate ownership:

```text
POST /api/timer-sessions
POST /api/training-logs
POST /api/body-metrics
POST /api/daily-plan-items
```

## Local-First Rule

Clients should write locally first, then write to cloud. Cloud failure must not block recording or timer completion. Failed cloud writes should remain retryable.
