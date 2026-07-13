# Entity Store Migration v2

Updated: 2026-07-13

## Scope

This is a local browser-storage migration only. It does not call the cloud API,
modify D1, change a plan, or alter a training record.

The IndexedDB database remains `training-assistant-v2`. Version 2 adds:

- `records`: one shared-record envelope per entity and ID;
- `outbox`: one pending cloud operation per entity and ID;
- `meta`: migration marker and local migration backup.

The existing `kv/snapshot` store is retained unchanged.

## First Open

1. The existing snapshot is read through the v1-compatible path.
2. A complete legacy snapshot backup is written locally before any entity copy.
3. Its normalized shared records are copied into `records`.
4. Dirty Shenk-owned records are copied into `outbox`.
5. The backup is written to `meta` and a localStorage key beginning with
   `training-assistant-v2:migration-backup:v2:`.
6. A migration marker is written only after those writes succeed.

If IndexedDB is unavailable or migration fails, the application continues using
the existing snapshot/localStorage fallback. No cloud write is attempted by the
migration itself.

## Dual Write Window

During the first v2 release, saves still update the legacy snapshot and also
persist entity rows/outbox entries. This keeps older Web builds readable while
the entity-store path is verified in normal use.

`records` and `outbox` use keyed diff writes. Unchanged rows are not rewritten.
Deleted records remain tombstones in the store so another device cannot restore
them accidentally.

Outbound sync reads persisted outbox entries first. The in-memory dirty-record
scan remains only as a compatibility fallback while the dual-write window is open.
Accepted and conflicted records leave the outbox on the next local save; failed
attempts retain a local error, attempt count, and next retry time for retry
diagnostics. Those fields are stored in the outbox row, so a refresh does not
turn a scheduled retry into an untracked in-memory operation.

## Rollback

Rollback is operationally simple: deploy the previous Web build. It continues to
read `kv/snapshot`; v2 stores are additive and ignored by the previous build.
The original snapshot is never deleted by this migration.

## Exit Criteria For Full Cutover

Before the legacy snapshot stops receiving normal writes, verify:

- a browser with an existing v1 snapshot migrates without losing records;
- a clean browser starts with empty entity stores and remains usable offline;
- dirty save, failed push, reload, and retry preserve the same outbox item;
- a scheduled retry retains its attempt metadata and next retry time after a
  browser refresh;
- cloud pull, conflict resolution, and tombstone sync preserve local state;
- a previous Web build can still read the legacy snapshot during the dual-write window.
