# 健身计划与身刻方案契约

更新时间：2026-07-18

本文件是“健身计划制定”对话生成 `routineTemplates` 时的权威规则。它同时约束 Web 身刻、Android 身刻、Web 计时器和 Android 原生计时器。任何模型接手计划制定前都应完整读取本文件。

## 1. 责任边界

- 健身计划对话负责设计方案内容，并明确填写分类、角色、生命周期和可见性。
- 身刻负责校验、预览、用户确认、本地优先保存和云同步。
- 计时器只执行 `lifecycle: "published"`、`timerVisible: true` 且结构完整的方案。
- 客户端不得根据标题、`trainingType`、routine ID、版本号或动作内容猜测 `scene`、`role`。
- 字段缺失或取值非法时必须拒绝导入或拒绝执行，并明确列出错误；不得静默套用旧内置流程。

## 2. routine 必填字段

每条新增或替换的 routine 必须完整包含：

```json
{
  "id": "routine_example_v1",
  "title": "用户看到的中文方案名",
  "version": "1.0.0",
  "trainingType": "strength",
  "scene": "home",
  "role": "main",
  "lifecycle": "published",
  "timerVisible": true,
  "calendarVisible": true,
  "countsTowardTraining": true,
  "estimatedMinutes": 45,
  "steps": [
    {
      "stepId": "warmup_walk",
      "name": "原地慢走",
      "phase": "热身",
      "durationSeconds": 120,
      "dose": "2 分钟"
    }
  ]
}
```

以下字段不能省略，也不能由身刻补齐：`id`、`title`、`trainingType`、`scene`、`role`、`lifecycle`、`timerVisible`、`calendarVisible`、`countsTowardTraining` 和非空 `steps`。

## 3. scene：方案出现在哪个场景

`scene` 只决定计时器 UI 分组，是计划作者给出的权威事实。

| 值 | 用户侧分组 | 典型用途 |
|---|---|---|
| `home` | 居家 | 居家力量、室内有氧、孩子居家训练 |
| `walk` | 健走 | 健走主流程、走前热身、走后拉伸、孩子公园训练 |
| `recovery` | 恢复 | 独立恢复、低压恢复、恢复拉伸 |
| `travel` | 外出 | 酒店训练、座位活动、差旅辅助活动 |

禁止规则：

- `trainingType: recovery` 不代表 `scene` 必然是 `recovery`。
- `trainingType: stretch` 不代表 `scene` 必然是 `walk`。
- 标题含“座位”“公园”“恢复”“健走”也不能触发自动分类。
- 如果计划作者无法确定场景，应先向用户追问，不得让客户端猜测。

## 4. role：方案在执行链路中的用途

| 值 | 含义 |
|---|---|
| `main` | 独立主训练或可独立完成的主流程 |
| `warmup` | 主训练前辅助热身 |
| `stretch` | 主训练后拉伸 |
| `cooldown` | 主训练后冷身 |
| `recovery` | 可独立完成的恢复训练 |
| `auxiliary` | 不作为正式训练主体的辅助活动 |

`role` 不决定 UI 分组，也不替代 `trainingType`。例如座位活动可以是 `scene: "travel"`、`role: "auxiliary"`、`trainingType: "seat_recovery"`。

## 5. lifecycle 与可见性

只允许：

- `draft`：草稿，不进入计时器可执行列表。
- `published`：现行正式方案。
- `archived`：停用历史方案，不进入普通计时器列表。

旧值 `active` 已废弃，任何新 patch 使用 `active` 都应校验失败。

三个布尔字段必须显式填写：

- `timerVisible`：是否出现在计时器方案列表。
- `calendarVisible`：该方案产生的计时事实是否允许进入日历相关流程。
- `countsTowardTraining`：该方案是否可计入正式训练判断。

常见组合：

| 方案用途 | timerVisible | calendarVisible | countsTowardTraining |
|---|---:|---:|---:|
| 用户主训练 | `true` | `true` | `true` |
| 独立恢复训练 | `true` | `true` | `true` |
| 热身/拉伸辅助流程 | `true` | 按产品决定显式填写 | 通常 `false` |
| 孩子训练、测试、演示 | 按需 | `false` | `false` |

不得从 `role` 自动生成这三个值。计划作者必须逐项决定。

## 6. 发布与变更规则

- `draft` 可保持同一 `id` 修改，发布前提高 `version`。
- `published` 的标题、版本、动作、时长、动作要领、风险提示和执行结构视为不可变快照。
- 修改已发布方案内容时必须创建新 `id` 和新 `version`，保留旧方案供历史 `timer_sessions` 追溯。
- 停用旧方案通过身刻方案库执行归档，不要在计划 patch 中用空数组、覆盖集合或删除来代替。
- 只有明确 `operation: "delete"` 或 `deletedAt` 才表示删除；删除是高风险操作，必须由用户确认。
- 方案变化是否需要调整未来 `dailyPlanItems`，由高级 AI 明确判断并单独输出；更新 routine 本身不会自动改日历。

## 7. coach_plan_patch 合并规则

- 默认始终是 merge/upsert，不是全量替换。
- 只输出本次实际变化的实体数组。
- 缺失实体字段表示不处理。
- 空数组也表示不处理，建议直接省略。
- routine-only patch 不得附带无意义的 `dailyPlanItems: []`、`planAdjustments: []`。
- 同一 `id` 是更新；新 `id` 是新增。
- `routineTemplates` 只影响方案库，不直接改变日历。
- 日历变化必须由 `dailyPlanItems` 或 `planAdjustments` 明确表达。

最小 routine-only patch：

```json
{
  "schema": "coach_plan_patch",
  "contractVersion": "1.0",
  "generatedBy": "coach",
  "generatedAt": "YYYY-MM-DDTHH:mm:ss+08:00",
  "effectiveFrom": "YYYY-MM-DD",
  "reason": "说明新增或替换方案的原因",
  "routineTemplates": [
    {
      "id": "routine_example_v1",
      "title": "示例方案",
      "version": "1.0.0",
      "trainingType": "recovery",
      "scene": "recovery",
      "role": "recovery",
      "lifecycle": "published",
      "timerVisible": true,
      "calendarVisible": true,
      "countsTowardTraining": true,
      "estimatedMinutes": 18,
      "steps": [
        {
          "stepId": "breathing_reset",
          "name": "呼吸调整",
          "phase": "恢复",
          "durationSeconds": 90,
          "dose": "90 秒"
        }
      ]
    }
  ]
}
```

## 8. 动作与 execution

- 每个 step 至少包含 `stepId`、`name`、`phase`、`durationSeconds`。
- `durationSeconds` 表示动作主体时长，必须大于 0。
- 动作要领写 `cues`，风险提示写 `warnings`，呼吸提示可写 `breath`。
- 准备、左右侧和换侧应写入 `execution`，不要手工拆成多个伪动作。
- 支持 `simple`、`prepare_only`、`alternating`、`bilateral_hold`、`bilateral_reps`。
- 导入、云同步、本地缓存和计时器执行必须原样保留 `execution` 及未知扩展字段。

## 9. 当前 11 条方案的已确认权威分类

以下表格是 2026-07-18 用户明确确认的迁移结果，仅用于修复现有云数据，不构成按名称推断的新规则。

| routineId | scene | role |
|---|---|---|
| `routine_home_strength_standard_v3_1` | `home` | `main` |
| `routine_home_strength_short_v3_1` | `home` | `main` |
| `routine_indoor_cardio_v2_9` | `home` | `main` |
| `routine_kids_home_strength_v1` | `home` | `main` |
| `routine_kids_park_training_v1` | `walk` | `main` |
| `routine_walk_warmup_v1` | `walk` | `warmup` |
| `routine_walk_stretch_quick_v1` | `walk` | `stretch` |
| `routine_walk_stretch_full_v1` | `walk` | `stretch` |
| `routine_recovery_stretch_v1` | `recovery` | `recovery` |
| `routine_travel_hotel_v2_7` | `travel` | `main` |
| `routine_seat_recovery_v1` | `travel` | `auxiliary` |

## 10. 输出前自检

计划对话在输出 JSON 前必须逐条确认：

1. 是否存在全部必填字段。
2. `scene` 是否由计划意图明确给出，而非从名称猜测。
3. `role` 是否描述真实用途。
4. `lifecycle` 是否只使用 `draft/published/archived`。
5. 三个可见性布尔值是否均为真实 boolean。
6. `steps` 是否非空，时长是否大于 0。
7. 是否误改了已发布方案的内容；若是，是否创建了新 ID 和版本。
8. 是否错误附带空数组。
9. 是否包含 token、API key、真实配置或无关隐私；如有必须删除。
10. 若有删除，是否已经得到用户明确确认。
