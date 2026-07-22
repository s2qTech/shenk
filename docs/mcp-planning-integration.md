# ChatGPT MCP planning integration

## Purpose

This integration lets a scheduled ChatGPT task read a bounded Shenk planning snapshot and submit one pending `coach_plan_patch`. It does not replace Shenk's plan inbox or confirmation flow.

## Public endpoints

- MCP: `https://shenke-cloud-db.sq-muyi.workers.dev/mcp`
- Protected-resource metadata: `/.well-known/oauth-protected-resource/mcp`
- Authorization-server metadata: `/.well-known/oauth-authorization-server`
- Dynamic client registration: `/oauth/register`
- Authorization: `/oauth/authorize`
- Token: `/oauth/token`

## Connection flow

1. Generate a one-time pairing code from an authenticated Shenk client. It expires after ten minutes.
2. In ChatGPT Plugins, create a custom MCP plugin with the `/mcp` URL and OAuth authentication.
3. Complete authorization by entering the pairing code in the Worker authorization page.
4. The pairing code is consumed once and is never stored in plaintext.

## Tools

### `get_planning_snapshot`

Inputs:

- `historyDays`: 7-30, default 14.
- `trendDays`: 14-90, default 30.
- `futureDays`: 7-28, default 14.

The result contains sanitized planning records, the requested period, and `snapshotDigest`. Credential-shaped fields, raw source blobs, and deleted records are excluded.

Formal plan interpretation is explicit:

- `planning.effectiveDailyPlans` is the only authoritative formal-plan view for each date.
- The latest valid `plan_adjustment` for a date replaces that date's daily plan snapshot.
- If no adjustment exists, the latest `daily_plan_item` snapshot applies.
- `records.daily_plan_items` and `records.plan_adjustments` remain available only as audit inputs.
- A daily plan snapshot and its resolved adjustment are not a conflict and must never be presented as parallel instructions.
- Actual execution remains separate: `training_logs` are facts and do not rewrite the formal plan history.

### `submit_coach_plan_patch`

Inputs:

- `snapshotDigest` returned by the snapshot tool;
- `period` returned by the snapshot tool;
- a Contract v2 `coach_plan_patch`.

Rules:

- `schema` is `coach_plan_patch` and `contractVersion` is `2.0`;
- `replaceMode` is absent or false;
- at least one supported entity array contains a record;
- missing arrays and empty arrays are no-op;
- explicit deletes, tombstones, and delete operations are rejected;
- accepted drafts are stored with status `pending`;
- repeated submission of the same snapshot and patch is idempotent.

## Ownership

MCP reads only the entities needed to construct a planning snapshot. MCP writes only:

- `planning_runs`
- `coach_plan_patches`

Shenk validates and confirms a pending patch before any formal plan entity changes. Calendar priority and adjustment semantics are unchanged.

## Security and rollback

- Never put application tokens, provider keys, pairing codes, or migration codes in a URL.
- OAuth codes, pairing codes, and tokens are stored only as SHA-256 hashes.
- Access tokens are short-lived and refresh tokens rotate.
- Disable the integration by revoking OAuth token rows or removing the MCP routes.
- Pending exchange records can be removed without changing formal plans.
