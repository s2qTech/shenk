# 领域术语表

更新日期：2026-07-17

本文件统一产品文案、数据模型和开发讨论中的核心词义。不要用相近词随意替换这些概念。

| 中文名称 | 内部概念 | 定义 |
| --- | --- | --- |
| 正式计划 | formal plan | 由高级 AI 制定、经校验和用户确认后生效的日期安排 |
| 原计划快照 | `daily_plan_items` | 计划确认时保存的日期快照，模板更新不能改写历史 |
| 计划调整 | `plan_adjustments` | 对日期快照的追加调整历史；解析后形成唯一有效计划 |
| 当前有效计划 | effective formal plan | 原计划快照叠加最新有效调整后的结果，UI 通常只显示这一份 |
| 建议 | local fallback suggestion | 没有正式计划时由本地规则产生的兜底方向，权威性低于正式计划 |
| 实际记录 | `training_logs` | 用户确认后的正式训练、恢复、休息或跳过事实 |
| 计时事实 | `timer_sessions` | 计时模块生成的执行事实，不能替代用户确认后的实际记录 |
| 待补记录 | pending completion | 已有计时事实，且方案快照显式允许计入训练，但尚未补充心率、体感或确认正式记录；可忽略，忽略不修改计时事实 |
| 方案 / 流程 | `routine_templates` | 可由计时器执行的版本化动作流程 |
| 场景 | routine `scene` | 方案在 UI 中的明确分组：home、walk、recovery、travel；只读显式字段 |
| 角色 | routine `role` | 方案在执行/记录中的用途：main、warmup、stretch、cooldown、recovery、auxiliary |
| 晨起状态 | morning check-in | 当日早晨记录的睡眠、疲劳、疼痛等主状态 |
| 训练前补充 | pre-workout delta | 只记录相对晨起状态发生变化的字段，未填字段表示继承而非正常 |
| 身体测量 | `body_metrics` | 体重、体脂率、肌肉量、腰围等客观测量事件 |
| 每日简评 | `daily_reviews` | 每日训练或休息事实完成后由兼容 AI 生成的点评，不具备改计划权限 |
| 周复盘包 | weekly feedback package | 分享给高级 AI 的 14 天明细、30 天趋势和当前策略/方案摘要 |
| 高级 AI | advanced AI | 当前主要是固定 ChatGPT 对话，负责目标、策略、计划和方案设计 |
| 日常 AI | compatible daily AI | 可配置的 OpenAI 兼容接口，负责每日点评、预警和策略内建议 |
| 本地第一写入 | local-first write | 用户操作先进入本地数据库和 outbox，再异步同步云端 |
| 冲突 | conflict | 本地和云端对同一基础 revision 有实质不同修改，不能静默覆盖 |
| 删除标记 | tombstone | 用于多端同步删除的内部记录；普通 UI 不显示已删除项 |

## 容易混淆但必须分开的概念

- `timer_sessions` 不等于 `training_logs`。
- 正式计划不等于本地建议。
- 计划调整历史不等于第二份并列计划。
- `scene` 不等于 `trainingType`，`role` 也不等于 `scene`。
- 疲劳不等于疼痛；缺失不等于正常；未记录不等于休息。
- Android 内置计时器属于计时模块，但 Android 日历/记录页面不因此获得写 `timer_sessions` 的权限。
