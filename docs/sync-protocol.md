# Sync Protocol

Last updated: 2026-06-19

## Decision

`身刻` and `home-training-timer` use:

```text
Local IndexedDB as first write target
Cloudflare Worker API as sync gateway
Cloudflare D1 as shared cloud database
Cloudflare R2 later for optional screenshots
```

The app must remain usable when Cloudflare is unreachable. Sync failure must never block local recording or timer execution.

## Sync Phases

### Phase 1: Record Sync

Sync each shared entity table directly:

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

This is preferred over a single snapshot because both `身刻` and timer need to read/write different records independently.

### Phase 2: Media Sync

Add R2 for screenshots:

- upload file to R2
- save metadata and recognition result in `media_assets`
- do not require original screenshot upload by default

### Phase 3: AI and Weather Backend

Use Worker API for:

- weather capture
- screenshot parsing
- coach feedback packaging
- OpenAI API proxy

API keys must never be placed in frontend code.

## Local Record Envelope

In IndexedDB, each entity record should include sync metadata:

```json
{
  "id": "log_2026-06-19_001",
  "entity": "training_logs",
  "data": {},
  "revision": 1,
  "deviceId": "desktop_qi_001",
  "createdAt": "2026-06-19T21:30:00+08:00",
  "updatedAt": "2026-06-19T21:30:00+08:00",
  "deletedAt": null,
  "syncState": "dirty",
  "lastSyncedAt": null,
  "conflict": null
}
```

`syncState` values:

```ts
type SyncState = "clean" | "dirty" | "syncing" | "conflict" | "error";
```

## API Endpoints

All endpoints are under:

```text
https://<worker-host>/api
```

### GET /api/health

Returns service status.

```json
{
  "ok": true,
  "service": "shenke-sync",
  "schemaVersion": "2026-06-19-001",
  "time": "2026-06-19T10:00:00Z"
}
```

### POST /api/bootstrap

Returns initial data needed by both apps.

Request:

```json
{
  "deviceId": "desktop_qi_001",
  "clientSchemaVersion": "2026-06-19-001"
}
```

Response:

```json
{
  "ok": true,
  "serverTime": "2026-06-19T10:00:00Z",
  "schemaVersion": "2026-06-19-001",
  "records": {
    "plan_templates": [],
    "routine_templates": [],
    "daily_plan_items": []
  }
}
```

### POST /api/sync/pull

Pull changed records since a timestamp or event cursor.

Request:

```json
{
  "deviceId": "desktop_qi_001",
  "since": "2026-06-19T00:00:00Z",
  "entities": [
    "daily_plan_items",
    "training_logs",
    "body_metrics",
    "timer_sessions"
  ]
}
```

Response:

```json
{
  "ok": true,
  "serverTime": "2026-06-19T10:05:00Z",
  "records": [
    {
      "entity": "training_logs",
      "id": "log_2026-06-19_001",
      "revision": 2,
      "updatedAt": "2026-06-19T10:04:00Z",
      "deletedAt": null,
      "data": {}
    }
  ]
}
```

### POST /api/sync/push

Push local dirty records.

Request:

```json
{
  "deviceId": "desktop_qi_001",
  "records": [
    {
      "entity": "timer_sessions",
      "id": "session_2026-06-19_2130_001",
      "baseRevision": 0,
      "data": {}
    }
  ]
}
```

Response:

```json
{
  "ok": true,
  "accepted": [
    {
      "entity": "timer_sessions",
      "id": "session_2026-06-19_2130_001",
      "revision": 1,
      "updatedAt": "2026-06-19T13:30:00Z"
    }
  ],
  "conflicts": []
}
```

Conflict response:

```json
{
  "ok": true,
  "accepted": [],
  "conflicts": [
    {
      "entity": "body_metrics",
      "id": "metric_2026-06-19",
      "clientRecord": {},
      "serverRecord": {},
      "reason": "server_revision_newer"
    }
  ]
}
```

### POST /api/timer-sessions

Convenience endpoint for timer app. Internally same as `sync/push`, but validates timer session shape.

### POST /api/training-logs

Convenience endpoint for `身刻`. Internally same as `sync/push`, but validates training log shape.

## Conflict Strategy

### Safe Auto-Merge

Allowed when fields are independent:

- `training_logs.notes` updated on one device, `avgHeartRate` updated on another.
- `body_metrics.weightKg` updated on one device, `waistCm` updated on another.

### Preserve Both and Ask

Required for:

- `daily_plan_items`
- `plan_adjustments`
- `routine_templates`
- same metric field edited differently on two devices
- same training log completion status edited differently

### Never Auto-Overwrite

Do not silently overwrite:

- coach plan updates
- routine step definitions
- timer session raw step results
- media recognition results after user confirmation

## Device IDs

Device ID is generated once and stored locally.

Examples:

```text
desktop_qi_001
android_xiaomi_001
timer_web_chrome_001
```

It is not a security credential. It is only for sync diagnostics and conflict handling.

## Authentication

Phase 1 personal use:

- Worker accepts a simple bearer token stored in app settings.
- Token is user-managed and can be rotated.

Phase 2:

- Add Cloudflare Access, passkey, or custom login.
- Do not block local use when not logged in.

## Offline Behavior

When offline or proxy unavailable:

1. Save locally.
2. Mark record `dirty`.
3. Show small sync state indicator.
4. Retry on next app open and when network returns.

The UI should not show training as failed just because sync failed.

## Deletion

Use soft delete:

```json
{
  "deletedAt": "2026-06-19T22:00:00+08:00"
}
```

Hard delete is reserved for:

- full local reset
- explicit cloud purge
- media file cleanup after metadata deletion

## JSON Export

Export must include both raw app data and sync metadata:

```json
{
  "schemaVersion": "2026-06-19-001",
  "exportedAt": "2026-06-19T22:00:00+08:00",
  "records": {
    "plan_templates": [],
    "routine_templates": [],
    "daily_plan_items": [],
    "plan_adjustments": [],
    "timer_sessions": [],
    "training_logs": [],
    "body_metrics": []
  }
}
```

JSON import must not blindly replace local data. It should import as incoming records and run the same conflict logic as cloud pull.
