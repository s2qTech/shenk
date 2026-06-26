# Codex 计划协作交接说明

更新时间：2026-06-27

## 目标

身刻负责训练计划的承载、执行入口、记录、同步和反馈摘要；Codex 对话负责根据用户状态和历史数据制定、解释、修改训练计划。

身刻不要自动扮演教练，不要擅自长期修改训练计划。身刻内部推荐算法只作为“本地兜底排程器”：在没有新计划调整时，根据最近一次已批准计划、实际训练记录和当天状态给出合理默认建议。

## 职责边界

### Codex 对话负责

- 分析用户反馈摘要、截图、体感和阶段目标。
- 制定阶段计划、滚动规则、当日调整建议。
- 输出给人看的训练说明。
- 输出可被身刻解析的结构化计划草案。
- 说明调整原因、风险点、降级条件。

### 身刻负责

- 保存计划模板、日历快照、计划调整、实际训练记录、身体状态、天气、反馈摘要。
- 展示日历、详情、趋势和执行入口。
- 提供“计划草案收件箱/导入计划草案”。
- 对计划草案做校验、预览差异、等待用户确认。
- 用户确认后，本地优先写入 IndexedDB，再同步 Cloudflare D1。
- 离线时按最近一次已批准计划继续本地排程。
- 导出最近 7/14/30 天反馈摘要给 Codex。

### home-training-timer 负责

- 执行 routine 流程。
- 提供语音、提示音、常亮、动作切换、休息等计时能力。
- 写入 `timer_sessions`。
- 不负责制定计划，不负责修改日历训练建议。

## 数据实体分工

现有 Cloudflare D1 + `cloud_records` JSON envelope 可以继续使用，不需要推倒重来。

身刻写：

- `plan_templates`
- `routine_templates`
- `daily_plan_items`
- `plan_adjustments`
- `timer_session_links`
- `training_logs`
- `body_metrics`
- `weather_logs`
- `media_assets`
- `feedback_summaries`

计时器写：

- `timer_sessions`

双方都可以读取共享数据，但不要跨越写入边界。

## 推荐工作流

### 1. 身刻导出反馈

用户在身刻点击“导出给 Codex”，生成最近 7/14 天摘要。

摘要至少包含：

- 时间范围。
- 每日实际训练：类型、时长、距离、心率、训练负荷、完成状态。
- 计时器执行记录：routine、开始结束时间、完成状态、是否已关联训练日志。
- 身体状态：体重、腰围、睡眠、精力、疲劳、疼痛部位。
- 计划偏差：跳过、缩短、只拉伸、替换。
- 用户备注。
- 当前有效计划版本。
- 身刻本地推荐结果和推荐理由。

### 2. 用户把摘要发给 Codex

Codex 根据摘要输出两部分：

- `给人看的说明`：今天/未来几天为什么这样安排。
- `结构化计划草案`：给身刻解析和预览。

### 3. 身刻接收计划草案

身刻提供一个“计划草案收件箱”：

- 粘贴 Codex 输出。
- 自动提取 JSON 代码块。
- 校验 schema。
- 展示变更预览。
- 用户确认后应用。
- 用户取消则不写库。

### 4. 身刻应用计划

应用计划时必须：

- 本地先写 IndexedDB。
- 再同步云端。
- 生成或更新 `plan_templates`。
- 生成或更新 `routine_templates`。
- 为未来日期生成 `daily_plan_items`。
- 当修改已有日期计划时写 `plan_adjustments`，不要覆盖历史。
- 已有实际记录的日期不要被未来计划重写。

## 计划草案格式

建议使用一个 `coach_plan_patch` 顶层对象。

```json
{
  "schema": "coach_plan_patch",
  "schemaVersion": "1.0",
  "generatedAt": "2026-06-27T21:00:00+08:00",
  "generatedBy": "codex",
  "reason": "根据最近 7 天训练记录和小腿/腰臀反馈调整。",
  "effectiveFrom": "2026-06-28",
  "effectiveTo": "2026-07-05",
  "planTemplate": {
    "id": "plan_2026_06_base_v2",
    "version": "2.0.0",
    "title": "基础减脂与体能滚动计划",
    "status": "active",
    "goal": ["fat_loss", "cardio", "strength", "posture", "stamina"],
    "rules": {
      "rollingWindowDays": 7,
      "strengthTarget": 2,
      "easyAerobicTarget": 2,
      "qualityWalkMax": 1,
      "recoveryAsNeeded": true,
      "noMakeupWorkout": true,
      "workloadCountsAsLoad": true
    }
  },
  "routineTemplates": [],
  "dailyPlanItems": [],
  "planAdjustments": [],
  "notes": [
    "力量训练不以心率判断有效性。",
    "小腿、腰臀牵扯时取消提高走。"
  ]
}
```

## dailyPlanItems 建议字段

```json
{
  "id": "daily_2026-06-28_recovery_001",
  "date": "2026-06-28",
  "sourcePlanId": "plan_2026_06_base_v2",
  "sourcePlanVersion": "2.0.0",
  "trainingType": "recovery",
  "title": "恢复拉伸",
  "goal": "降低小腿和腰臀负荷，保持活动连续性。",
  "estimatedMinutes": 15,
  "intensity": 2,
  "needsTimer": true,
  "routineId": "routine_recovery_stretch",
  "routineVersion": "1.0",
  "timerOptions": {
    "voice": true,
    "wakeLock": true,
    "calfCare": true,
    "defaultRestSeconds": 20
  },
  "notes": [
    "牵拉感控制在 3/10 以内。",
    "有麻、刺、电流感或放射痛时停止训练。"
  ],
  "status": "planned"
}
```

## 身刻推荐算法定位

推荐算法保留，但定位必须明确：

- 它是离线兜底排程器。
- 它基于实际记录，而不是死守原计划。
- 它不能长期覆盖 Codex 确认后的计划。
- 它不能把某一天晨起状态自动延续到多日预测。
- 它可以根据当天状态把今日建议降级。
- 它可以在没有未来计划快照时，用最近一次有效计划生成预测。

日历展示优先级：

```text
实际训练记录
  > 计划调整
  > 已确认日计划快照
  > 本地兜底预测
```

## 需要身刻端实现的开发任务

### A. 计划草案收件箱

- 新增“计划草案”入口或放在设置/数据页。
- 支持粘贴 Codex 输出。
- 从文本中提取 `coach_plan_patch` JSON。
- 校验必填字段。
- 显示变更预览：
  - 新增/更新计划模板。
  - 新增/更新 routine。
  - 新增/修改哪些日期。
  - 哪些已有实际记录不会被覆盖。
- 用户确认后应用。

### B. 反馈摘要导出

- 新增“导出给 Codex”。
- 可选时间范围：7 天、14 天、30 天。
- 输出 Markdown + JSON。
- Markdown 给人读，JSON 给 Codex 稳定解析。
- 包含身刻当前推荐结果和推荐理由。

### C. 计划版本和历史

- `plan_templates` 必须带 `version`、`effectiveFrom`、`effectiveTo`。
- 新计划生效时，旧 active 计划要关闭或标记 superseded。
- 已过去日期保留当时快照。
- 修改已有未来日期写 `plan_adjustments` 或更新未执行的 `daily_plan_items`，需要在预览里说明。

### D. 日历详情层级

日期详情显示：

- 原计划。
- 最新调整。
- 计时器执行记录。
- 正式训练日志。
- 身体状态。
- 本地推荐理由。

### E. 离线策略

- 已加载过的计划和记录可离线查看。
- 离线时允许记录训练和身体状态。
- 离线时可以用本地推荐算法继续生成建议。
- 联网后同步冲突。
- 计划草案应用也应本地优先。

### F. 安全边界

- Token 只在身刻设置里保存。
- Codex 输出不包含 token。
- 计划草案不直接携带数据库凭据。
- 应用草案前必须用户确认。

## 验收标准

1. 用户可以从身刻导出最近 14 天反馈并粘贴给 Codex。
2. Codex 可以返回 `coach_plan_patch`。
3. 身刻可以识别、校验、预览该 patch。
4. 用户确认后，身刻生成计划和未来日历快照。
5. 已有实际训练记录不会被覆盖。
6. 计时器入口仍然可用，完成后写 `timer_sessions`。
7. 身刻能读取计时器记录，但不修改 `timer_sessions`。
8. 无网络时，身刻仍可查看日历、记录当天、生成兜底建议。
9. 恢复网络后可以同步。
10. 日历上的预测和 Codex 当日建议不会出现明显冲突；如冲突，详情页必须显示推荐理由和数据来源。

## 给身刻开发对话的交接提示

可以把下面这段复制给身刻开发对话：

```text
请按 C:\Workspace\training-assistant-v2\docs\coach-planning-handoff.md 实现“Codex 计划协作”能力。

目标不是让身刻自动当教练，而是：
1. 身刻导出反馈摘要给 Codex；
2. Codex 返回结构化 coach_plan_patch；
3. 身刻校验、预览、用户确认；
4. 身刻本地优先写入 plan_templates / routine_templates / daily_plan_items / plan_adjustments，再同步云端；
5. 身刻保留离线兜底推荐，但不覆盖已批准计划。

请优先实现：
- 反馈摘要导出；
- 计划草案收件箱；
- coach_plan_patch schema 校验；
- 计划变更预览；
- 用户确认后应用；
- 日历详情中展示原计划/调整/实际/身体状态/推荐理由的层级。

不要把 home-training-timer 的执行代码合并进身刻，也不要让身刻直接修改 timer_sessions。
```
