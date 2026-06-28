# Timer Integration Plan

Last updated: 2026-06-28

## Goal

Integrate `home-training-timer` and `身刻` under the shared data contract without breaking either existing app.

The timer remains usable as a standalone tool while gaining the ability to:

- read routine templates from shared data
- start from a `dailyPlanItemId`
- write timer sessions
- return execution results to `身刻`

## Non-Goals for First Pass

- Do not rewrite the timer UI.
- Do not merge both codebases immediately.
- Do not require cloud sync for timer execution.
- Do not make timer generate or modify training plans.
- Do not remove the existing deployed timer until the new flow is stable.

## Current State

`home-training-timer` currently keeps routines as JavaScript arrays and action metadata inside one HTML file.

`身刻` currently has:

- calendar
- daily record editor
- body metrics
- local IndexedDB/localStorage fallback
- JSON import/export
- rolling recommendation logic

Timer code is not yet inside `身刻`.

## Target Architecture

```text
routine_templates
  -> used by timer execution UI
  -> referenced by daily_plan_items

daily_plan_items
  -> displayed by 身刻 calendar
  -> passed to timer when user starts planned session

timer_sessions
  -> written by timer
  -> read by 身刻
  -> user either ignores, marks as auxiliary, or opens a prefilled training draft in 身刻
  -> only user-saved main/recovery drafts become training_logs
```

## Routine Source

`routine_templates` in Cloudflare D1 are the normal source of executable routines.

The timer startup order is:

1. Read `routine_templates` from the cloud API.
2. Normalize and display only routines with `timerVisible: true`.
3. Save the normalized routine catalog to local browser storage.
4. If cloud read fails, use the last successful local cache.
5. If both cloud and cache are unavailable, show an explicit fallback/debug state. Built-in routines are not the normal source of truth.

When `身刻` opens the timer with a `routineId`, the timer should execute that routine from cloud/cache even if it is not visible in the standalone selector. If the routine is not found, the timer must show a clear error instead of silently falling back to an unrelated routine.

Routine grouping and ordering:

- `scene`: `home` / `walk` / `recovery` / `travel`.
- `sortOrder`: lower values appear earlier.
- `isDefault`: preferred routine for a scene.
- `timerVisible`: whether the routine appears in the timer selector.

`身刻` writes or updates routines only through user-confirmed `coach_plan_patch.routineTemplates`. Timer execution never mutates `routine_templates`.

## Timer URL Contract

The timer must support these query parameters:

```text
?routineId=routine_home_strength_standard_v3_1
&date=2026-06-19
&dailyPlanItemId=daily_2026-06-19_strength_001
&source=shenke
```

Optional:

```text
&calfCare=true
&hasBand=true
&restSeconds=20
```

If no query parameters are present, the timer behaves exactly like the current standalone timer.

## Timer Session Output

On finish, timer creates:

```json
{
  "id": "session_2026-06-19_2130_001",
  "date": "2026-06-19",
  "dailyPlanItemId": "daily_2026-06-19_strength_001",
  "routineId": "routine_home_strength_standard_v3_1",
  "routineVersion": "3.1",
  "startedAt": "2026-06-19T21:30:00+08:00",
  "endedAt": "2026-06-19T22:17:00+08:00",
  "actualSeconds": 2820,
  "completion": "completed",
  "stepResults": [],
  "notes": ""
}
```

Timer stores this locally first, then syncs when possible.

## Integration Modes

### Mode 1: Link Out

`身刻` opens timer page in a new tab/window with query params.

Pros:

- Low risk.
- Does not disturb current app shell.
- Good for early validation.

Cons:

- Returning result to `身刻` is manual or needs postMessage/opener.

### Mode 2: Iframe / Embedded Timer

`身刻` embeds timer in a panel or route.

Timer posts session result:

```js
window.parent.postMessage({
  type: "shenke.timerSession.completed",
  payload: timerSession
}, origin);
```

Pros:

- Better user experience.
- Can open a pending training draft from a completed timer session.

Cons:

- Needs origin handling.
- Needs more careful mobile layout.

### Mode 3: Native Module Merge

Move timer code into `身刻` as a module.

Pros:

- Best final experience.
- One app, one local database, no cross-window messaging.

Cons:

- Highest refactor risk.

Recommended path:

```text
Mode 1 -> Mode 2 -> Mode 3
```

Do not jump directly to Mode 3.

## 身刻 UI Changes

Daily drawer should show:

```text
Plan
  title
  type
  estimated time
  routine link
  notes

Adjustments
  original plan
  adjusted plan
  reason

Actual
  timer sessions
  confirmed training log
  body status
```

For timer-capable plan:

```text
[开始计时器]
```

If timer session exists but no confirmed training log:

```text
发现计时器完成记录
[补全训练记录] [标记为热身/拉伸] [忽略]
```

`补全训练记录` only opens an editable draft. It must not write `training_logs` until the user saves the training form.

## Timer UI Changes

Keep existing controls:

- start/pause
- previous/next
- reset
- voice
- beep
- wake lock
- calf care
- has band

Add only minimal data-driven indicators:

- routine title
- linked date
- linked plan item
- sync status

Do not add calendar or body metrics to timer UI.

## Local Storage Boundary

Both apps should eventually share the same local database name when hosted under the same origin.

During separate deployment, they cannot share IndexedDB across origins. Therefore early integration uses:

- cloud sync
- JSON handoff
- postMessage when embedded

When timer is merged into `身刻`, both modules use the same IndexedDB database directly.

## Development Steps

### Step 1: Shared Routine Data

- Create `shared/routines.json`.
- Map current timer action metadata into routine templates.
- Keep existing timer arrays until parity is verified.

### Step 2: Timer Reads Routine Data

- Add a routine loader.
- Build steps from routine data.
- Keep existing preset selector.
- Verify all current presets still work.

### Step 3: Timer Session Persistence

- Add local timer session store.
- Add session result screen.
- Add JSON copy/export for session.

### Step 4: 身刻 Plan Snapshots

- Add `daily_plan_items`.
- Add `plan_adjustments`.
- Keep existing workout records working.
- Calendar displays planned/adjusted/actual layers.

### Step 5: Link Out Integration

- Add "开始计时器" button in `身刻`.
- Open timer with `routineId`, `date`, `dailyPlanItemId`.
- Timer stores session and shows result JSON.

### Step 6: Cloud Database Access

- Deploy D1 schema.
- Add Worker API.
- Sync routine templates, daily plan items, timer sessions, training logs, body metrics.

### Step 7: Embedded Timer

- Embed timer route in `身刻`.
- Use postMessage to pass timer session result.
- Convert session into pending training log.

## Verification Checklist

- Existing `home-training-timer` still works without query params.
- Existing `身刻` calendar and editor still work.
- A planned strength day can open the standard strength routine.
- Timer completion creates a timer session.
- `身刻` can read the timer session.
- User can confirm a timer session into a training log.
- Original daily plan remains visible after adjustment and actual completion.
- JSON export includes plans, routines, sessions, logs, and body metrics.
