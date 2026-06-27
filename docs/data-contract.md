# Shared Data Contract

Last updated: 2026-06-27

## Purpose

`身刻` and `home-training-timer` share one data model and one cloud database.

They are separate apps/modules with different presentation responsibilities:

- `身刻`: calendar, plan snapshots, actual logs, body status, trends, feedback export.
- `home-training-timer`: routine execution, voice/wake controls, step timing, timer sessions.

Both sides may read all data. Write ownership is narrower:

- `身刻` writes plan templates, daily plan snapshots, adjustments, training logs, body metrics, media metadata, feedback summaries.
- `home-training-timer` writes timer sessions and routine execution details.
- `身刻` writes timer session handling records in `timer_session_links`; it does not modify `timer_sessions`.
- Routine templates are modified only through explicit plan updates, not by timer execution.
- Plans are modified only through explicit coach/user-confirmed updates, not automatically by the app.
- `routine_templates` in Cloudflare D1 are the source of truth for timer-executable routines.
- `home-training-timer` may cache `routine_templates` locally for offline execution, but cache is only a replica.

## Core Rules

1. Plans are versioned.
2. Routines are versioned.
3. Calendar days store snapshots, not only references.
4. Adjustments never overwrite the original planned item.
5. Actual completion records never overwrite the planned item.
6. Timer sessions are raw execution records; `身刻` may summarize them into training logs.
7. Local IndexedDB remains the first write target; cloud sync mirrors data.
8. All records must be exportable as JSON.
9. Timer routine selection is driven by `routine_templates.timerVisible === true`.
10. Built-in timer routines are fallback/debug only; they must not be treated as the normal plan source.

## Naming

IDs are client-generated strings. Recommended format:

```text
plan_2026_06_base_v1
routine_home_strength_standard_v3_1
daily_2026-06-19_strength_001
adjust_2026-06-19_001
session_2026-06-19_2130_001
log_2026-06-19_001
metric_2026-06-19
```

Timestamps use ISO 8601 with timezone when generated on client:

```text
2026-06-19T21:30:00+08:00
```

Cloud storage may additionally keep UTC timestamps.

## Enums

### Training Type

```ts
type TrainingType =
  | "strength"
  | "easy_walk"
  | "quality_walk"
  | "indoor_cardio"
  | "warmup"
  | "cooldown"
  | "recovery"
  | "travel_strength"
  | "seat_recovery"
  | "stretch"
  | "rest";
```

## Routine Template Source Rules

`routine_templates` are the only normal source for executable timer routines.

`身刻` writes or updates `routine_templates` through user-confirmed plan patches. `home-training-timer` reads them, caches them locally, and executes them. Timer execution must never mutate routine templates.

Required / recommended fields for timer execution:

```json
{
  "id": "routine_recovery_low_pressure_v2",
  "title": "低压恢复",
  "version": "2.0.0",
  "trainingType": "recovery",
  "scene": "recovery",
  "estimatedMinutes": 18,
  "sortOrder": 30,
  "isDefault": false,
  "timerVisible": true,
  "needsTimer": true,
  "defaultOptions": {
    "voice": true,
    "wakeLock": true,
    "defaultRestSeconds": 20
  },
  "steps": [
    {
      "stepId": "breathingReset",
      "name": "呼吸重置",
      "phase": "恢复",
      "durationSeconds": 90,
      "dose": "鼻吸口呼，肩颈放松"
    }
  ]
}
```

Rules:

- `id` is the stable `routineId`; `daily_plan_items.routineId` references it.
- `title` is user-facing. Do not put internal IDs or version text in the title.
- `version` is kept for traceability but hidden from normal UI.
- `timerVisible: true` means the routine appears in the timer's standalone selector.
- A routine referenced by a daily plan may still be opened directly by `routineId` even if it is not visible in the standalone selector.
- `scene` controls timer grouping. Recommended values: `home`, `walk`, `recovery`, `travel`.
- `sortOrder` controls order inside a scene. Lower numbers appear first.
- `isDefault` marks the first-choice routine for a scene.
- `steps` are required for timer execution.
- If the cloud read fails, the timer uses the last successful local cache.
- If there is no cloud data and no local cache, the timer may show a fallback/debug routine set with an explicit warning.

### Completion Status

```ts
type CompletionStatus =
  | "planned"
  | "completed"
  | "short_version"
  | "stretch_only"
  | "skipped"
  | "rested"
  | "modified_by_user";
```

### Intensity

```ts
type Intensity = 1 | 2 | 3 | 4 | 5;
```

Recommended meaning:

- `1`: rest or very light recovery.
- `2`: recovery, easy stretching, light walk.
- `3`: normal strength, normal walk, indoor cardio.
- `4`: quality walk or dense indoor session.
- `5`: reserved; not used in current plan.

### Pain Level

```ts
type PainLevel = 0 | 1 | 2 | 3 | 4 | 5;
```

Recommended meaning:

- `0`: none.
- `1`: slight awareness.
- `2`: mild but noticeable.
- `3`: affects movement choice.
- `4`: stop training and recover.
- `5`: seek medical evaluation if persistent or acute.

## Plan Template

Plan templates describe a coach-approved training cycle.

```json
{
  "id": "plan_2026_06_base_v1",
  "version": "1.0.0",
  "title": "2026-06 基础减脂与体能循环",
  "status": "active",
  "createdBy": "coach",
  "createdAt": "2026-06-19T10:00:00+08:00",
  "effectiveFrom": "2026-06-19",
  "effectiveTo": null,
  "goal": ["fat_loss", "cardio", "posture", "stamina"],
  "rules": {
    "rollingWindowDays": 7,
    "strengthTarget": 2,
    "easyAerobicTarget": 2,
    "qualityWalkMax": 1,
    "recoveryAsNeeded": true,
    "noMakeupWorkout": true,
    "workloadCountsAsLoad": true
  },
  "notes": [
    "力量训练不追心率。",
    "疼痛、麻木、跛行、胸闷或头晕时停止。"
  ]
}
```

## Routine Template

Routine templates are executable timer flows.

```json
{
  "id": "routine_home_strength_standard_v3_1",
  "version": "3.1",
  "title": "居家力量标准版 3.1",
  "trainingType": "strength",
  "estimatedMinutes": 47,
  "defaultOptions": {
    "voice": true,
    "wakeLock": true,
    "calfCare": false,
    "hasBand": true,
    "defaultRestSeconds": 20
  },
  "steps": [
    {
      "stepId": "chair_sit",
      "name": "椅子坐站",
      "phase": "strength",
      "durationSeconds": 90,
      "targetReps": "10-12",
      "round": 1,
      "cues": [
        "坐椅子前半段，双脚与髋同宽。",
        "允许髋部前倾，但背保持直。"
      ],
      "warnings": [
        "不要砸到椅子上。",
        "膝盖不能内扣。"
      ],
      "breath": "下坐吸气，站起呼气。",
      "substitutions": [
        {
          "stepId": "assisted_chair_sit",
          "condition": "knee_pressure",
          "label": "扶桌椅子坐站"
        }
      ]
    }
  ],
  "createdAt": "2026-06-19T10:00:00+08:00",
  "updatedAt": "2026-06-19T10:00:00+08:00"
}
```

## Daily Plan Item

Daily plan items are calendar snapshots. They preserve what was planned for that date at that time.

```json
{
  "id": "daily_2026-06-19_strength_001",
  "date": "2026-06-19",
  "sourcePlanId": "plan_2026_06_base_v1",
  "sourcePlanVersion": "1.0.0",
  "trainingType": "strength",
  "title": "居家力量标准版 3.1",
  "goal": "全身基础力量，重点补臀髋、背部、核心和推力。",
  "estimatedMinutes": 47,
  "intensity": 3,
  "needsTimer": true,
  "routineId": "routine_home_strength_standard_v3_1",
  "routineVersion": "3.1",
  "timerOptions": {
    "voice": true,
    "wakeLock": true,
    "calfCare": false,
    "hasBand": true,
    "defaultRestSeconds": 20
  },
  "notes": [
    "力量训练不追心率。",
    "手腕疼时斜板俯卧撑退回墙面俯卧撑。"
  ],
  "snapshot": {
    "routineTitle": "居家力量标准版 3.1",
    "stepsDigest": "chair_sit,band_bridge,row,incline_push,dead_bug,bird_dog,clamshell,shadow_punch",
    "generatedAt": "2026-06-19T10:00:00+08:00"
  },
  "status": "planned",
  "createdAt": "2026-06-19T10:00:00+08:00",
  "updatedAt": "2026-06-19T10:00:00+08:00"
}
```

## Plan Adjustment

Adjustments preserve plan changes without destroying history.

```json
{
  "id": "adjust_2026-06-19_001",
  "date": "2026-06-19",
  "targetDailyPlanItemId": "daily_2026-06-19_strength_001",
  "adjustedAt": "2026-06-19T18:30:00+08:00",
  "adjustedBy": "coach",
  "reason": "出外展站了一下午，右小腿外侧和左腰背酸。",
  "fromSnapshot": {
    "trainingType": "strength",
    "title": "居家力量标准版 3.1",
    "routineId": "routine_home_strength_standard_v3_1"
  },
  "toSnapshot": {
    "trainingType": "recovery",
    "title": "状态差恢复拉伸",
    "routineId": "routine_recovery_stretch_v1"
  }
}
```

Calendar display uses:

```text
planned item -> latest adjustment, if any -> actual log, if any
```

The detail drawer must be able to show all three layers.

## Timer Session

Timer sessions are written by `home-training-timer`.

```json
{
  "id": "session_2026-06-19_2130_001",
  "dailyPlanItemId": "daily_2026-06-19_strength_001",
  "routineId": "routine_home_strength_standard_v3_1",
  "routineVersion": "3.1",
  "startedAt": "2026-06-19T21:30:00+08:00",
  "endedAt": "2026-06-19T22:17:00+08:00",
  "actualSeconds": 2820,
  "completion": "completed",
  "stepResults": [
    {
      "stepId": "incline_push",
      "status": "modified",
      "actualLabel": "墙面俯卧撑",
      "reason": "手腕不适"
    }
  ],
  "notes": "第二轮鸟狗式不稳。"
}
```

## Timer Session Link

Timer session links are written by `身刻`. They record how a timer fact was handled without mutating the original `timer_sessions` row.

```json
{
  "id": "timer_link_session_2026-06-19_2130_001",
  "timerSessionId": "session_2026-06-19_2130_001",
  "date": "2026-06-19",
  "action": "converted",
  "targetTrainingLogId": "log_2026-06-19_session_2026-06-19_2130_001",
  "role": "main",
  "note": "由计时器记录转为正式训练日志",
  "createdAt": "2026-06-19T22:20:00+08:00",
  "updatedAt": "2026-06-19T22:20:00+08:00"
}
```

Allowed `action` values:

- `linked`: associated with an existing training log or kept as an auxiliary flow.
- `converted`: converted into a formal `training_logs` record.
- `ignored`: intentionally hidden from pending timer workflows.

Allowed `role` values:

- `warmup`
- `stretch`
- `cooldown`
- `main`
- `recovery`
- `note`

## Training Log

Training logs are confirmed actual records. They may be created manually, from screenshots, or from timer sessions.

```json
{
  "id": "log_2026-06-19_001",
  "date": "2026-06-19",
  "dailyPlanItemId": "daily_2026-06-19_strength_001",
  "timerSessionId": "session_2026-06-19_2130_001",
  "type": "strength",
  "status": "completed",
  "source": "timer",
  "durationSec": 2820,
  "distanceKm": null,
  "avgHeartRate": 99,
  "maxHeartRate": 121,
  "trainingEffect": 0.9,
  "trainingLoad": 13,
  "notes": "完成标准力量。整体不累，鸟狗式第二轮平衡下降。"
}
```

## Body Metric

Body metrics are date-based status records. They are separate from training logs.

```json
{
  "id": "metric_2026-06-19",
  "date": "2026-06-19",
  "weightKg": 80.0,
  "waistCm": 90.0,
  "bodyFatPct": 24.0,
  "muscleKg": 52.0,
  "sleepQuality": "normal",
  "energy": 3,
  "fatigue": 2,
  "pain": {
    "calfRightOuter": 1,
    "hipRightOuter": 1,
    "backLeftLower": 1,
    "wrist": 0
  },
  "notes": "出外展久站后轻微酸。"
}
```

## Feedback Summary

Feedback summaries are exported from `身刻` and pasted into the planning conversation.

```json
{
  "id": "feedback_2026-06-13_2026-06-19",
  "period": {
    "from": "2026-06-13",
    "to": "2026-06-19"
  },
  "plannedSummary": {},
  "adjustmentSummary": {},
  "actualSummary": {},
  "bodyTrend": {},
  "painTrend": {},
  "openQuestions": [
    "是否调整下一周力量密度？"
  ]
}
```

## Record Envelope

All synced records use a shared envelope in local storage and API transport:

```json
{
  "entity": "daily_plan_items",
  "id": "daily_2026-06-19_strength_001",
  "revision": 3,
  "deviceId": "desktop_qi_001",
  "updatedAt": "2026-06-19T22:20:00+08:00",
  "deletedAt": null,
  "data": {}
}
```

Conflict rule for phase 1:

- Higher `revision` wins when the same `deviceId` updated the record.
- Later `updatedAt` wins for ordinary non-critical fields.
- For plan changes, body metrics, and training logs, conflicting versions are preserved and surfaced for review.
