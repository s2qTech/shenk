# 身刻仓库代理规则

本文件是任何 AI 编码代理、自动化工具和新开发者进入仓库后的第一入口。开始分析、设计或修改前必须完整阅读本文件。

## 1. 当前状态

- 当前日期基线：2026-07-18。
- Web 身刻和 Web `home-training-timer` 是可用基线，保持独立仓库和部署。
- 正式 Android 产品已完成需求收束；Packages 0-5 已通过门禁，进度为 `6 / 9`。Package 5 原生方案库与计时器已通过 CI 和小米 14 真机门禁；开始 Package 6 前仍须收到用户明确指令。
- `mobile/` 是冻结的 Capacitor 验证原型，不是正式 Android 基础，不得继续向其中添加生产功能。
- 正式 Android 技术路线是 Kotlin + Jetpack Compose，生产工程位于 `android-app/`。
- 正式 Android 是面向当前自用主设备的现代原生产品：只支持当前稳定 Android 平台，默认采用官方兼容交集内最新稳定工具链，不为假设中的旧机型、旧系统或旧 Java 环境保留兼容分支。
- 当前生产默认数据契约仍是 Contract v1；Worker 已兼容 Contract v2。Android 仅可在对应工作包内显式使用 v2，Web 旧客户端继续使用 v1。

## 2. 必读顺序

每次开始新任务时按顺序读取：

1. `PROJECT_STATE.md`
2. `governance/guardrails.json`
3. `docs/android-product-blueprint.md`
4. `docs/android-technical-architecture.md`
5. `docs/android-contract-v2-plan.md`
6. `docs/android-delivery-and-constraints.md`
7. `docs/development-constraints.md`
8. `docs/domain-glossary.md`
9. 与任务相关的 `docs/adr/` 决策记录
10. 与改动相关的当前 schema、OpenAPI、fixtures 和测试

`docs/mobile-strategy.md`、`docs/android-foundation.md`、`docs/next-stage-development-plan.md` 和旧版 `docs/product-constraints.md` 只保留历史背景，不得作为新 Android 实现依据。

## 3. 冲突时的优先级

```text
用户最新且明确批准的决定
  > 本文件与 governance/guardrails.json
  > PROJECT_STATE.md
  > 当前 Android 四份规范与 development-constraints.md
  > 已接受 ADR
  > 当前 Contract/OpenAPI/fixtures
  > 实现代码与历史文档
```

如果高低层材料冲突，停止实现，先更新高层约束和受影响文档，再修改代码。不得自行选择“看起来更合理”的旧规则。

## 4. 不可突破的核心语义

1. 日历主显示优先级固定为：正式实际记录 > 当前有效正式计划 > 本地兜底建议。
2. 计划调整解析为当天唯一有效计划；普通 UI 不并列展示计划和调整历史。
3. 缺失数据保持缺失，不自动记为休息，也不把未填写字段推断为正常。
4. 高级 AI 计划高于本地建议；用户实际执行和身体反馈是最终事实。
5. 日常兼容 AI 只能点评、预警和在策略边界内建议，不得修改正式计划或 routine。
6. `timer_sessions` 是计时执行事实；`training_logs` 是用户确认后的正式训练记录，两者不得合并成一个实体。
7. 只有计时模块可写 `timer_sessions`。Web 计时器和 Android 原生计时器都属于计时模块；日历/记录模块只能读取。
8. routine 必须显式提供 `scene` 和 `role`。禁止依据标题、trainingType、routineId 或动作内容推断、覆盖或迁移分类。
9. 所有业务保存本地优先，并在同一事务中进入 outbox。云端失败不得阻止记录。
10. 本地脏数据不得被云端静默覆盖；实质冲突必须可见。
11. patch 缺失字段和空数组都是 no-op。只有显式 delete/tombstone 才能删除。
12. 删除项立即从普通 UI 消失；tombstone 只是同步实现细节。
13. Android 平台与构建基线遵循“当前稳定平台 + 当前 LTS JDK + 官方验证组合”；预览版、alpha 和未经完整兼容验证的版本不得仅因版本号更大而混入生产基线。

## 5. 必须先征得用户确认的改动

以下改动不得凭推断直接实施：

- 覆盖、重分类、批量停用、清空或物理删除计划/routine/健康/训练云数据。
- 修改实体所有权、日历优先级、AI 权限或正式记录确认流程。
- 用标题、类型或 ID 自动补齐 `scene`、`role` 等权威字段。
- 全量 replace 导入、不可逆数据库迁移、仓库合并或部署地址变更。
- 把 phase 2 或明确排除项带入 phase 1。
- 新增账号、多用户、饮食、天气决策、社交或游戏化系统。
- 更换原生 Android 技术路线或重新启用 Capacitor 作为正式基础。
- 降低 Android 当前稳定平台基线，或新增仅服务于旧机型、旧系统的兼容层。

提问时必须说明：要改什么、为什么、影响哪些历史数据、是否可回退、建议默认选项。

## 6. 跨端数据改动顺序

1. 更新领域定义、owner 和 Contract 计划。
2. 更新 JSON Schema、OpenAPI 和脱敏 fixtures。
3. 定义 v1/v2 兼容、迁移、回滚、删除和冲突语义。
4. Worker 先兼容并执行服务端权限。
5. 再更新 Android、Web 身刻和 Web timer 中受影响的客户端。
6. 运行跨端契约、离线、冲突和安全测试。
7. 更新状态文档和工作包进度。
8. 最后才清理旧兼容路径。

不得先在某一端添加同名但含义不同的字段。

## 7. 安全规则

- token、API key、密码、迁移码和真实健康数据不得进入代码、文档、URL、日志、测试 fixture、Git 或普通 JSON 备份。
- Android 密钥使用 Keystore；跨设备配置使用既有加密档案协议。
- AI provider 密钥只可在 TLS 请求中瞬时经过 Worker，Worker 不记录、不持久化明文。
- 动态文本默认纯文本渲染。
- 测试、截图和导出只使用脱敏数据，并放在 `.gitignore` 排除目录。

## 8. 实施流程

- 未收到“开始 Package 0”或等价明确指令前，不创建正式 Android 功能代码。
- 每次只推进一个已确认工作包，不得顺手实现后续包。
- 开始前列出受影响边界、数据实体、兼容风险和验收条件。
- 完成时按 `docs/android-delivery-and-constraints.md` 的 `X / 9` 模板汇报。
- 功能完成必须包含实现、自动化测试、手工关键路径、兼容、安全、文档和回退方案。
- 修改完成后按用户既有要求分别提交并推送受影响仓库；不得提交用户无关改动或真实数据。

## 9. 停止条件

出现以下情况立即停止编码并向用户说明：

- 需求需要越过本文件的确认门槛。
- canonical docs 互相冲突。
- 无法判断数据是否真实、是否可删除或由谁拥有。
- 需要把密钥写入不安全位置才能继续。
- 兼容迁移或回退路径尚未定义。
- 测试只能通过修改/删除用户现有数据。
