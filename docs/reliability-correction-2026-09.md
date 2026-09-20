# September 2026 reliability correction

Scope: the user-approved follow-up to the whole-project review on 2026-09-20.
Phase-1 progress stays **9 / 9**. No new phase-2 feature, entity ownership,
formal-record confirmation rule, shared record field, or Room/D1 schema is added.

## Changes and compatibility

- Worker record writes use database revision preconditions; record and audit event
  commit in one D1 batch transaction. Identical concurrent retries stay idempotent.
- The database assigns a timestamp after all prior committed record timestamps.
  Queries return the last visible committed timestamp instead of response time;
  an empty page keeps the original cursor. v1/v2 callers retain their existing
  request/response shapes. The separate Web timer needs no source change for this
  corrected server behavior.
- Updated Android and Web reconcile once using a new local cursor key. Remote
  records cannot downgrade a newer synced Android revision or overwrite dirty
  local facts. Android queues follow-up work when operations remain and preserves
  save triggers that arrive while synchronization is running.
- Web writes changed records, outbox and pull cursor atomically, rejects stale-tab
  edits, avoids a full record-store scan/legacy snapshot on each ordinary save,
  and updates only requested outbox retry keys. Its offline cache is refreshed.
- Today, Calendar and Data update their date on foreground return and system
  date/time/timezone changes. Today closes any editor belonging to the old day.
- Compose retains query Flow instances across recomposition. Unchanged Room rows
  no longer trigger repeated JSON decoding; heavier projections run off the UI
  dispatcher. No new state-management framework or module graph is introduced.
- AI snapshots include the effective published goal and strategy. Receiving a
  result rechecks current input and confirmed-day eligibility transactionally,
  so superseded or deleted input cannot replace a newer review. Job completion
  commits with the new review. Superseded tasks do not obscure current progress.
- AI creation/status HTTP calls have a 30-second bound, and persistent status
  waiting has a five-minute deadline. A final status check can still retrieve an
  already completed result after an offline period. Uncertain failures expose
  retry using the same job ID. High reasoning, 42,066 primary tokens and the
  existing structure-repair ceiling remain unchanged. The HTTP client is reused
  while its endpoint and authorization configuration remain unchanged.
- Native timer commands and tick publication serialize on the coordinator.
  Active/paused durations use monotonic elapsed time while fact timestamps keep
  wall time. Completing an old persistence request cannot clear a newer session's
  recovery checkpoint. Existing checkpoints remain readable.

## Verification

- Node: 67 tests passed, including real SQLite execution of Worker CAS, concurrent
  create/retry, event-failure rollback and paging/watermark scenarios.
- Chromium: isolated real IndexedDB smoke passed for record/outbox/cursor commit
  and complete rollback of a stale-tab save.
- Android nativeCheck passed: 90 JVM tests, debug Lint and debug assembly. Isolated
  UI instrumentation and data instrumentation packages compile.
- Xiaomi 14: the final 40 synthetic data tests passed in 4.454 seconds. Coverage includes
  current policies in AI input, stale-result exclusion, deletion during generation,
  bounded status wait, same-ID retry, offline completed-result recovery, safe
  reconciliation, records/outbox, and timer ownership.
- On-device synthetic performance smoke: 400-day/560-record calendar projection
  86 ms; simulated-network 100-record sync 603 ms. These are smoke observations,
  not a measured production latency improvement or real provider benchmark.
- Isolated Compose: all four tests passed, covering foreground date refresh,
  unrecorded-day gating, explicit regeneration with unchanged facts, and visible
  retry/progress alongside the previous review. HyperOS blocked the test runner's
  automatic activity launch; explicitly launching the same standard MAIN/LAUNCHER
  intent allowed execution. Earlier interrupted runs are not counted as passes.
  Tests never write synthetic health records into the production app.

## Deployment and device closure (2026-09-20)

- With explicit user approval and renewed Cloudflare authorization, the existing
  `shenke-cloud-db` Worker was deployed as version
  `d92b15e5-b4e5-478f-be12-d4a92f9e4254`. `/api/health` returned `ok: true`
  with Contract v1/v2 support. No production business records were created for tests.
- `package8ReleaseCandidateCheck` passed from committed source `524a7601d921`.
  The ignored private archive under `outputs/code-review-2026-09-20/` contains
  the signed APK, checksum, certificate digest, exact source and rollback revisions.
  APK SHA-256: `950110826db818f47d092d694553ae1e97cccc20fa010be050bddca93ac690bf`.
- Xiaomi 14 accepted `adb install -r` for the existing `io.s2qtech.shenk`
  `1.0.0-rc.2` / versionCode 12. Its first-install time remained
  `2026-08-12 02:29:56`; last-update time became `2026-09-20 15:35:58`.
  Production cold launch succeeded (ADB WaitTime 404 ms), and Training/Plan
  navigation intents were delivered successfully. No new application crash was
  found in the crash buffer. These are launch smoke checks, not an end-to-end
  production AI-provider latency measurement or inspection of private health data.
- This run's three synthetic test packages were removed. Production storage and
  device settings were not cleared. The pre-existing unrelated test package and
  untracked `.pnpm-store/` were left untouched.
- Web source is included in the repository change; the public Web site's deployment
  is separate from this Worker deployment and phone installation.

## Release and rollback

Deploy Worker first, then publish updated Web/Android clients. No cloud data
rewrite or database migration is required. Keep the existing endpoints and signing
identity. APK updates must use `install -r`, without uninstalling/clearing production.

Rollback source is `86f6b26`. Restore the previous Worker version and install a
same-certificate Android build with a versionCode no lower than the installed
one. Do not delete business records or credentials. For Web, retain its current
entity stores and compatibility checkpoint. After returning to corrected code,
reset only the local pull-cursor metadata so a protected reconciliation repairs
any writes made under old timestamp semantics. The old bugs return with old code;
prefer a forward fix where practical.
