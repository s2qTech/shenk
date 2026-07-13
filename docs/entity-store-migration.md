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

## Entity-Primary Write Window

Normal saves persist entity rows and outbox entries first. They do not rewrite
the complete legacy snapshot on every edit.

The `kv/snapshot` store remains a compatibility checkpoint. A checkpoint is
rebuilt from the entity records after the first v2 migration, at most once every
15 minutes, and when the app moves to the background or starts to unload. If
IndexedDB is unavailable, the app falls back to the legacy snapshot write path
for every save.

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

The original snapshot is never deleted by this migration. A previous Web build
can read the latest compatibility checkpoint from `kv/snapshot`; the v2 stores
are additive and ignored by that build. For a rollback immediately after a
series of edits, first allow the current app to move to the background so it
writes a checkpoint, then deploy the prior build.

## Entity-Primary Cutover Checks

Verify the following after the legacy snapshot stops receiving normal writes:

- a browser with an existing v1 snapshot migrates without losing records;
- a clean browser starts with empty entity stores and remains usable offline;
- dirty save, failed push, reload, and retry preserve the same outbox item;
- a scheduled retry retains its attempt metadata and next retry time after a
  browser refresh;
- cloud pull, conflict resolution, and tombstone sync preserve local state;
- a compatibility checkpoint remains readable through the v1 snapshot path.
