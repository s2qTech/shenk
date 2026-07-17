# Android Contract v2 Plan

Updated: 2026-07-17
Status: Package 1 compatibility gate passed; Worker accepts `1.0` and `2.0`, while `1.0` remains the default production contract

## 1. Purpose

Contract v1 is sufficient for the current Web baseline but does not fully represent morning status, pre-workout changes, native timer durability, daily AI review, plan rollback, or future wearable imports.

Contract v2 must remain presentation-neutral so Android can use a new UI while Web keeps its existing interface.

Package 1 added the canonical v2 schema, OpenAPI description, sanitized fixtures, Worker validation, and cross-client compatibility checks. The D1 record table remains unchanged because shared records are stored as generic JSON envelopes. Rollback is therefore non-destructive: clients stop sending `2.0` and continue using the still-supported `1.0` contract. Package 2 may build native local storage against v2, but existing Web clients continue to default to v1 during the migration window.

## 2. Compatibility Rules

1. Keep the existing record envelope semantics: entity, ID, revision, device ID, timestamps, deletion marker, and data.
2. Add fields and entities before removing old ones.
3. Old Web `workouts` and combined `body_metrics` remain readable through mappers.
4. Unknown fields must survive read-modify-write flows.
5. Empty arrays and omitted fields in a coach patch are no-op; explicit delete is required.
6. New Android writes use v2 only after Worker validation and Web compatibility tests pass.
7. Contract v1 remains accepted during a migration window.

## 3. Ownership by Role

| Entity | Writer |
| --- | --- |
| `plan_templates`, `daily_plan_items`, `plan_adjustments`, `routine_templates` | Shenk planning module after validated AI patch or explicit user action |
| `timer_sessions` | Timer module, including Web timer and native Android timer |
| `training_logs`, `body_metrics`, `status_checkins` | Shenk record module |
| `daily_reviews` | Shenk AI review module |
| `plan_import_batches`, `goal_sets`, `coach_strategies` | Shenk planning module |
| `media_assets` | Shenk asset module |

Ownership is enforced by API role/token, not by which repository sends the request.

## 4. New Entity: `status_checkins`

Represents subjective state independently from body measurements.

Required or recommended fields:

```json
{
  "id": "status_checkin:2026-07-16:morning",
  "date": "2026-07-16",
  "kind": "morning",
  "observedAt": "2026-07-16T08:36:00+08:00",
  "sleepDurationMinutes": 390,
  "deepSleepMinutes": 82,
  "sleepQuality": 3,
  "energy": 4,
  "fatigue": 2,
  "workPressure": 3,
  "pain": [
    { "region": "calf_ankle", "severity": 1, "side": "right" }
  ],
  "note": "optional"
}
```

`kind` values:

- `morning`
- `pre_workout`

A pre-workout check-in may contain only changed fields and reference `baseCheckinId`. The effective-state resolver overlays the delta on the latest morning check-in. Omitted fields mean unchanged, not normal.

Pain region enum:

- `neck_shoulder`
- `wrist`
- `lower_back`
- `hip_glute`
- `thigh_knee`
- `calf_ankle`
- `other`

## 5. Evolve `body_metrics`

Keep the existing numeric fields and add measurement context:

```json
{
  "id": "body_metric:2026-07-16:morning",
  "date": "2026-07-16",
  "observedAt": "2026-07-16T08:35:00+08:00",
  "context": "morning",
  "source": "manual",
  "sourceRecordId": null,
  "weightKg": 100.2,
  "bodyFatPct": 28.4,
  "muscleKg": 67.8,
  "waistCm": 104.0
}
```

Source values should support:

- `manual`
- `health_connect`
- `xiaomi_import`
- `scale_import`
- `legacy`

Legacy sleep, energy, fatigue, and pain fields remain readable but are normalized into `status_checkins`. Android must not indefinitely dual-write both representations.

## 6. Evolve `routine_templates`

Make these fields required for new v2 routines:

```json
{
  "scene": "home",
  "role": "main",
  "lifecycle": "published",
  "timerVisible": true,
  "calendarVisible": true,
  "countsTowardTraining": true
}
```

`scene` values remain:

- `home`
- `walk`
- `recovery`
- `travel`

`role` values:

- `main`
- `warmup`
- `stretch`
- `cooldown`
- `recovery`
- `auxiliary`

Rules:

- UI groups only by explicit `scene`.
- Title, training type, and routine ID must never infer scene.
- `role` controls execution/record semantics, not grouping.
- Add optional `mediaAssetId` to a logical step for future animation.
- Preserve `execution` exactly through imports, cache, sync, and export.
- A deleted routine is absent from normal UI; a tombstone remains only for sync retention.

## 7. Evolve `timer_sessions`

Add enough fact data to recover and audit native execution:

- `routineVersion`
- `routineDigest`
- `routineSnapshot` or a bounded snapshot reference
- `dailyPlanItemId`
- `planTemplateId`
- `calendarVisible`
- `countsTowardTraining`
- `activeSeconds`
- `elapsedSeconds`
- `pausedSeconds`
- `interruptionReason`
- `stepResults`
- `devicePlatform`
- `idempotencyKey`

The timer session remains an execution fact. It is never edited to add average heart rate or subjective evaluation.

## 8. Evolve `training_logs`

Recommended additions:

- `timerSessionId`
- `timerSessionIds`
- `startedAt`, `endedAt`
- `durationSec`
- `distanceKm`
- `averageHeartRate`
- `perceivedEffort`
- `subjectiveResult`
- `notes`
- `sourceRecordId`
- `calendarVisible`
- `countsTowardTraining`
- `rawSource` for bounded imported source facts

Source values should include wearable/manual/native timer imports without treating timer facts as formal logs before confirmation.

Phase-2 wearable workouts may become formal records directly when their stable `sourceRecordId` and source timestamps match. Small duplicate differences may merge automatically; materially different records require review.

## 9. New Entity: `daily_reviews`

```json
{
  "id": "daily_review:2026-07-16:3",
  "date": "2026-07-16",
  "version": 3,
  "status": "generated",
  "conclusion": "...",
  "evidence": ["..."],
  "cautions": ["..."],
  "inputDigest": "sha256:...",
  "provider": "deepseek",
  "model": "...",
  "generatedAt": "..."
}
```

Provider metadata never includes a key. A changed input digest invalidates the visible review and schedules regeneration.

## 10. New Entity: `plan_import_batches`

Supports validation audit and one-step undo:

- patch ID/schema/version
- received and applied timestamps
- generated-by and reason
- affected entity IDs
- normalized add/update/delete counts
- bounded before snapshots or rollback references
- status: `previewed`, `applied`, `undone`, `rejected`

The latest applied batch may be undone. Undo creates new revisions; it does not erase sync history.

## 11. New Entities: `goal_sets` and `coach_strategies`

`goal_sets` stores versioned goals with effective dates and source/rationale.

`coach_strategies` stores the advanced AI boundaries that daily review may use. It is not a daily plan and cannot be silently edited by the daily AI provider.

Both use immutable published versions plus explicit effective ranges.

## 12. Effective Day Resolver

The domain resolver produces one user-facing day state:

```text
formal actual record
  else effective formal plan (latest valid adjustment resolved over daily snapshot)
  else local fallback suggestion
```

Supporting facts such as timer sessions, body metrics, and status check-ins are attached to the day but do not compete as top-level calendar layers.

If an actual record exists, the formal plan is retained for analysis but hidden from the primary date-detail presentation.

## 13. Deletion and Edit Window

- User-facing deletion removes an item from normal UI immediately.
- Cloud synchronization uses a tombstone until all active clients can observe the deletion.
- The normal UI never presents a permanent “deleted” routine list.
- Records may be corrected within two weeks before or after today; older records are read-only.
- Recent deletions support a short undo action. After that, restoration is an explicit new edit.

## 14. Configuration Is Not a Business Entity

API base, Shenk token, timer token, compatible-AI provider URL/key/model, and migration data remain in the encrypted sync-profile subsystem. They must not be stored in shared business entities or exported with health/training backup data.

Reminder schedules are per-device DataStore preferences unless a later explicit requirement makes them shared.

## 15. Contract v2 Release Gate

Contract v2 is ready only when:

1. Schema, OpenAPI, and ownership tables agree.
2. Worker validates every new entity and enforces roles.
3. Web and Web timer pass additive-field fixtures.
4. Android and Web pass encrypted-profile interoperability fixtures.
5. Empty-array patch behavior, explicit delete, rollback, tombstone, and conflict fixtures pass.
6. A migration test converts legacy body metrics without duplicating measurements or losing status.
