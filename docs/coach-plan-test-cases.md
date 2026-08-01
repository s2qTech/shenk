# Coach Plan Patch Test Cases

本文件用于给“健身计划制定”对话生成和自测 `coach_plan_patch`。
目标是让计划端只负责输出结构化计划，身刻负责导入、校验、预览、确认写入和同步。

## 基本规则

- 顶层必须是 `schema: "coach_plan_patch"`。
- 顶层必须显式包含 `contractVersion: "2.0"`；缺失版本或其它版本整份拒绝。
- 默认是 merge/upsert 模式。
- 不允许 `replaceMode: true`。
- 缺省字段表示不处理该实体。
- 空数组表示不处理该实体。
- 只有 `operation: "delete"` 或 `deletedAt` 表示删除。
- 不允许用空数组清空已有计划、routine 或调整记录。
- 不要输出 token、API key、真实云端配置或用户隐私。
- `routine_templates` 是计时器可执行流程的主源。
- `daily_plan_items.routineId` 必须引用一个可执行的 `routine_templates.id`。
- UI 标题使用中文用户侧名称，不显示 routineId、英文下划线 ID、版本号。
- routine 的完整权威规则见 `docs/coach-routine-contract.md`。
- `scene`、`role` 和 `lifecycle` 必须显式填写，不得由标题、类型或 ID 推断。
- `lifecycle` 只允许 `draft` / `published` / `archived`，旧值 `active` 必须被拒绝。

## 最小 Patch 模板

```json
{
  "schema": "coach_plan_patch",
  "contractVersion": "2.0",
  "generatedBy": "coach",
  "reason": "说明本次调整原因",
  "effectiveFrom": "YYYY-MM-DD"
}
```

如果某一类实体不需要修改，推荐直接省略该字段，不要写空数组。
身刻会把空数组当作 no-op 兼容处理，但计划端默认不应输出无意义的 `planTemplates: []`、`routineTemplates: []`、`dailyPlanItems: []` 或 `planAdjustments: []`。

## routine_templates 字段要求

```json
{
  "id": "routine_stable_id_without_version_noise",
  "title": "用户看到的中文流程名",
  "variant": "可选，简版/完整版/低压版",
  "trainingType": "strength",
  "scene": "home",
  "role": "main",
  "lifecycle": "published",
  "estimatedMinutes": 45,
  "timerVisible": true,
  "calendarVisible": true,
  "countsTowardTraining": true,
  "sortOrder": 10,
  "defaultOptions": {
    "defaultRestSeconds": 20,
    "voice": true,
    "beep": true,
    "wakeLock": true,
    "calfCare": true
  },
  "steps": [
    {
      "stepId": "warmup_walk",
      "name": "原地慢走",
      "phase": "热身",
      "durationSeconds": 120,
      "dose": "2 分钟",
      "cues": ["保持鼻吸口呼", "身体放松"],
      "warnings": ["疼痛升高时停止"]
    }
  ]
}
```

`trainingType` 建议值：
- `easy_walk`
- `quality_walk`
- `indoor_cardio`
- `recovery`
- `strength`
- `warmup`
- `stretch`
- `travel_strength`
- `seat_recovery`

`scene` 建议值：
- `home`
- `walk`
- `recovery`
- `travel`

`steps[].execution` 可选。需要准备时间、左右侧或换侧提示时优先使用它，不要把准备、换侧、左侧、右侧手工拆成多个 step。

支持：
- `simple`
- `prepare_only`
- `alternating`
- `bilateral_hold`
- `bilateral_reps`

## daily_plan_items 字段要求

```json
{
  "id": "daily_YYYY-MM-DD_short_slug",
  "date": "YYYY-MM-DD",
  "trainingType": "easy_walk",
  "title": "普通走",
  "goal": "当天目标说明",
  "estimatedMinutes": 45,
  "intensity": 2,
  "needsTimer": true,
  "routineId": "routine_stable_id_without_version_noise",
  "timerOptions": {
    "defaultRestSeconds": 20,
    "voice": true,
    "beep": true,
    "wakeLock": true,
    "calfCare": true
  },
  "notes": [
    "给用户看的注意事项",
    "不要写内部实现说明"
  ],
  "status": "planned",
  "sortOrder": 10
}
```

## 必跑测试用例

### 1. routine-only patch

用途：只修改计时器流程，不改日历计划。

```json
{
  "schema": "coach_plan_patch",
  "contractVersion": "2.0",
  "generatedBy": "coach",
  "reason": "只更新恢复流程",
  "effectiveFrom": "2099-01-01",
  "routineTemplates": [
    {
      "id": "routine_recovery_low_pressure",
      "title": "低压恢复",
      "variant": "低压版",
      "trainingType": "recovery",
      "scene": "recovery",
      "role": "recovery",
      "lifecycle": "published",
      "estimatedMinutes": 18,
      "timerVisible": true,
      "calendarVisible": true,
      "countsTowardTraining": true,
      "steps": [
        {
          "stepId": "breathing",
          "name": "腹式呼吸",
          "phase": "恢复",
          "durationSeconds": 120
        }
      ]
    }
  ]
}
```

预期：
- 预览显示 routine 新增或更新 1 条。
- 日计划新增 0 条、更新 0 条、删除 0 条。
- 已有 `daily_plan_items` 不被清空。

### 2. 带 execution 的左右侧动作

用途：让计时器运行时自动展开准备、左侧、换侧、右侧。

```json
{
  "schema": "coach_plan_patch",
  "contractVersion": "2.0",
  "generatedBy": "coach",
  "reason": "更新拉伸动作执行结构",
  "effectiveFrom": "2099-01-02",
  "routineTemplates": [
    {
      "id": "routine_walk_stretch_execution",
      "title": "走后拉伸",
      "trainingType": "stretch",
      "scene": "walk",
      "role": "stretch",
      "lifecycle": "published",
      "estimatedMinutes": 8,
      "timerVisible": true,
      "calendarVisible": false,
      "countsTowardTraining": false,
      "steps": [
        {
          "stepId": "stretch_calf_straight",
          "name": "小腿直膝拉伸",
          "phase": "拉伸",
          "durationSeconds": 30,
          "dose": "每侧30秒",
          "execution": {
            "mode": "bilateral_hold",
            "prepareSeconds": 8,
            "sideSeconds": 30,
            "switchSeconds": 6,
            "sides": ["左侧", "右侧"]
          }
        }
      ]
    }
  ]
}
```

预期：
- 身刻预览显示 routine 新增或更新 1 条。
- `execution` 字段保存到 `routine_templates.steps` 中，不被序列化丢失。
- 计时器执行时展开为准备、左侧、换侧、右侧。
- 计时器预计总时长使用展开后的真实总时长。

### 3. 新增未来日计划 + routine

用途：新增未来某天计划，并能从身刻打开计时器。

```json
{
  "schema": "coach_plan_patch",
  "contractVersion": "2.0",
  "generatedBy": "coach",
  "reason": "新增未来普通走计划",
  "effectiveFrom": "2099-02-01",
  "routineTemplates": [
    {
      "id": "routine_easy_walk_park",
      "title": "公园普通走",
      "trainingType": "easy_walk",
      "scene": "walk",
      "role": "main",
      "lifecycle": "published",
      "estimatedMinutes": 45,
      "timerVisible": true,
      "calendarVisible": true,
      "countsTowardTraining": true,
      "steps": [
        {
          "stepId": "walk_easy",
          "name": "普通走",
          "phase": "主训练",
          "durationSeconds": 2700
        }
      ]
    }
  ],
  "dailyPlanItems": [
    {
      "id": "daily_2099-02-01_easy_walk",
      "date": "2099-02-01",
      "trainingType": "easy_walk",
      "title": "普通走",
      "estimatedMinutes": 45,
      "needsTimer": true,
      "routineId": "routine_easy_walk_park",
      "status": "planned"
    }
  ]
}
```

预期：
- 日历显示“计划”样式，不应像已完成记录。
- 日历详情能看到计划层。
- 打开计时器 URL 包含 `routineId`、`date`、`dailyPlanItemId`、`source=shenk`。
- URL 不包含任何 token。

### 4. 已有实际记录的日期不覆盖

用途：计划端误发已有实际记录日期时，身刻必须跳过。

```json
{
  "schema": "coach_plan_patch",
  "contractVersion": "2.0",
  "generatedBy": "coach",
  "reason": "测试已有记录保护",
  "effectiveFrom": "2099-03-01",
  "dailyPlanItems": [
    {
      "id": "daily_2099-03-01_strength",
      "date": "2099-03-01",
      "trainingType": "strength",
      "title": "力量训练",
      "estimatedMinutes": 45,
      "routineId": "routine_strength_standard",
      "status": "planned"
    }
  ]
}
```

预期：
- 如果 `2099-03-01` 已有正式训练记录，预览应显示跳过。
- 写入后不能覆盖当天正式训练记录。
- 日历详情有实际记录时不显示计划层。

### 5. 显式删除

用途：验证删除必须显式声明。

```json
{
  "schema": "coach_plan_patch",
  "contractVersion": "2.0",
  "generatedBy": "coach",
  "reason": "取消某天计划",
  "effectiveFrom": "2099-04-01",
  "dailyPlanItems": [
    {
      "id": "daily_2099-04-01_easy_walk",
      "operation": "delete"
    }
  ]
}
```

预期：
- 预览删除数量为 1。
- 删除数量非 0 时，身刻应二次确认。
- 未显式声明删除时不得清空任何记录。

### 6. 无效 routine

用途：验证计划端不能输出不可执行流程。

```json
{
  "schema": "coach_plan_patch",
  "contractVersion": "2.0",
  "generatedBy": "coach",
  "reason": "无效流程测试",
  "effectiveFrom": "2099-05-01",
  "routineTemplates": [
    {
      "title": "缺少 ID 的流程",
      "trainingType": "recovery",
      "steps": [
        {
          "stepId": "breathing",
          "durationSeconds": 60
        }
      ]
    }
  ]
}
```

预期：
- 身刻预览判定无效。
- 不允许确认写入。

## 计划端自检清单

- 每个 `routineTemplates[]` 都有稳定 `id`。
- 每个需要计时器执行的 `dailyPlanItems[]` 都有 `routineId`。
- `dailyPlanItems[].routineId` 能在本次 patch 或既有云端 routine 中找到。
- `title` 和 `variant` 是用户侧中文，不带内部版本号。
- `steps[]` 至少有一项，且每项 `durationSeconds > 0`。
- routine-only patch 不携带无意义的全量 `dailyPlanItems`。
- 修改未来某天用 upsert；取消未来某天用 `operation: "delete"`。
- 不修改过去日期，不覆盖已有正式训练记录。
- 不输出 token、云 API 凭据、浏览器本地配置。
