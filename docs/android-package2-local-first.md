# Android Package 2: Local-First Foundation

Status: implemented; Package 3 may start only after CI and emulator gates pass

## Scope

Package 2 establishes native persistence and synchronization infrastructure. It does not implement Today, check-in, training, calendar, timer, AI, or settings feature UI.

## Room schema v1

| Table | Purpose | Important behavior |
| --- | --- | --- |
| `shared_records` | Complete v1/v2 shared record envelopes | Unknown additive fields remain intact; tombstones remain queryable internally |
| `outbox` | One latest pending mutation per entity and record ID | A later offline edit replaces the pending payload but keeps the same cloud base revision |
| `sync_conflicts` | Material local/cloud divergence | Ordinary sync stops retrying the conflicted record until an explicit resolution |
| `sync_metadata` | Incremental pull cursor metadata | Final `serverTime` is saved only after every pull page succeeds |

The exported Room schema is committed under `android-app/core/data-sync/schemas/`. Future schema changes require an explicit Room migration and migration test. Destructive fallback is forbidden.

## Write and ownership rules

1. A caller supplies the authoritative entity owner: planning, record, timer, AI review, or asset.
2. The repository rejects a mismatched owner before opening the mutation transaction.
3. The record row and latest outbox operation are written in one Room transaction.
4. A first write fills `deviceId`, `createdAt`, `updatedAt`, revision and base revision metadata.
5. Timer-owned backup records may be restored for local reading, but the Shenk record repository never queues them for cloud write.

## Sync and conflict rules

- WorkManager starts only with network connectivity and uses exponential retry.
- Mixed outbox batches are partitioned by authoritative owner before upload: Shenk-owned entities use the Shenk credential and `timer_sessions` use the timer credential. A missing role credential leaves that operation retryable; it is never sent under a different role.
- Upserts are batched to the Worker limit and retain an idempotency key until acknowledged.
- An omitted record in a successful Worker response is not treated as success; it remains retryable.
- A pulled record at or below the dirty row's base revision is stale and ignored.
- Semantically identical local and cloud business payloads merge automatically.
- A materially different newer cloud record creates `sync_conflicts`, removes that row from normal outbox retry and preserves both envelopes.
- `use remote` replaces the local row and clears the conflict. `use local` creates a new outbox operation based on the remote revision.
- No logs include record payloads, tokens, migration codes, or health values.

## Device configuration

- Non-secret endpoint settings and the stable random device ID use Preferences DataStore.
- Shenk, timer and future compatible-AI keys use AES-GCM keys held by Android Keystore; only ciphertext is stored in DataStore.
- Migration-profile crypto matches Web `shenk_sync_profile/v1`: SHA-256 profile ID, PBKDF2-HMAC-SHA256 with 210,000 iterations, 16-byte salt, AES-256-GCM and 12-byte IV.
- The migration code is supplied transiently to save or read a cloud profile. It is not put in a URL, business row, backup, log, screenshot or fixture.

## Business backup

The Storage Access Framework exports `shenk_business_backup/v1` JSON containing complete shared business envelopes only. It excludes preferences, secrets, outbox, conflicts and sync cursors. Import validates schema and timestamp, contract version, maximum size/count, entity names, duplicate record keys and revisions, and recursively rejects normalized secret-shaped fields before a transaction begins. Package 8 exposes this engine from Settings through the system document picker.

Restore is additive and never an implicit replace. Missing Shenk-owned records are queued for synchronization; missing timer-owned facts remain readable locally and are not written by the record module. Identical existing records are left unchanged, while a differing existing ID is reported and skipped so queued edits, conflicts, and newer local facts cannot be silently overwritten.

## Rollback

Package 2 does not migrate production Web or D1 storage. Rolling Android back before feature data exists is an APK rollback only. After native business data exists, users must export a business backup before any rollback. A future Room version must provide a forward migration; deleting the database or enabling destructive migration is not an accepted rollback path.

## Verification gates

- JVM: domain ownership, additive-field preservation, profile crypto/gateway and backup rejection tests.
- Android instrumentation: transaction atomicity, owner refusal, dirty-pull protection, stale-pull handling, required metadata, process-death database reopen, safe backup merge, restored outbox replay, accepted/unacknowledged sync behavior, Android-provider migration crypto and isolated Keystore recreation.
- Build: KSP Room generation, lint, unit tests, Android-test APK compilation and debug APK assembly.
- CI: the same build gate plus API 36 emulator execution on the current stable Android baseline.
