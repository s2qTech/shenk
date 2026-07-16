# 开发约束边界

更新日期：2026-07-17

本文是身刻、`home-training-timer`、Cloudflare Worker 和原生 Android 客户端的强制约束。仓库入口和优先级见根目录 `AGENTS.md`，机器可读摘要见 `governance/guardrails.json`。除非用户明确批准变更，并同步更新数据契约、ADR、迁移和测试，否则不得绕过。

## 1. 产品职责

### 身刻必须负责

- 计划模板、日计划快照、计划调整。
- 正式训练记录、身体状态、趋势和反馈摘要。
- 本地优先存储、同步协调和冲突处理。
- 从日计划打开计时器，并读取计时事实。

### 计时模块必须负责

- routine 选择、预览、运行时 step 展开和状态机。
- 语音、提示音、常亮、暂停、恢复和停止。
- 生成并写入不可反向修改的 `timer_sessions` 执行事实。
- 缓存云端 `routine_templates` 供离线执行。

### Worker 必须负责

- 身份验证、角色授权、实体校验、revision 和冲突元数据。
- D1 迁移、批量读写、分页和幂等。
- 加密配置档案只保存密文；不得持久化迁移码、配置密码或明文密钥。

### 禁止跨界

- 不强行合并两个前端仓库。
- 计时器不得写 `training_logs`、`body_metrics` 或计划实体。
- Web/Android 的日历、计划和记录模块不得写、改写或删除 `timer_sessions`；Web 计时器和 Android 原生计时器作为计时模块可写入。
- Worker 不得替用户自动改变训练计划或正式训练记录。

## 2. 数据所有权

- `plan_templates`、`routine_templates`、`daily_plan_items`、`plan_adjustments`、`training_logs`、`body_metrics`、`status_checkins`、`daily_reviews`、`media_assets`、`feedback_summaries` 由对应的身刻领域模块写。
- `timer_sessions` 只由计时模块写；计时模块可位于独立 Web timer 或 Android 身刻应用内。
- 双方可读共享实体，但只可写自己拥有的实体。
- `timer_session_links` 只保留旧数据兼容；新流程不再创建纯关联处理记录。
- 新增实体前必须同时定义 owner、schema、同步策略、迁移和测试。

## 3. 计划与 routine

- 已发布的 plan/routine 必须不可变。
- 内容变化必须生成新版本；不得只修改 `version` 字段或原地覆盖旧内容。
- `daily_plan_items` 是日期快照，不是对活动模板的动态视图。
- 模板更新不得回写历史日期。
- `plan_adjustments` 必须追加保存，不能用日期固定 ID 覆盖历史。
- 日期有效指导是“原计划 + 最新有效调整”的结果；普通 UI 不重复展示调整历史。
- patch 缺失字段或空数组必须是 no-op；只有显式 delete/tombstone 才可删除。
- 删除预览非零时必须二次确认。
- 新 routine 必须显式包含 `scene` 和 `role`；禁止根据标题、trainingType、routineId 或动作内容推断、覆盖或批量迁移分类。
- 高级 AI patch 可更新正式计划和 routine；日常兼容 AI 不得写计划、调整、目标、策略或 routine。

## 4. 日历与正式记录

- 日历显示优先级必须是：实际记录 > 当前有效计划 > 本地建议。
- 当天已有正式记录时，主日历不再同时突出显示计划。
- 一天允许多条 `training_logs`，ID 不得仅由日期决定。
- 正式记录必须由用户确认保存；计时器事实只可预填草稿。
- `calendarVisible: false` 的 routine/session 不得进入日历。
- `countsTowardTraining: false` 的 session 不得成为正式训练候选。
- 儿童、测试、提示和辅助流程默认同时设置上述两个字段为 false。

## 5. 计时事实

- `timer_sessions` 是追加型事实，不是可编辑计划。
- `actualSeconds` 必须表示有效执行时间，不得包含暂停时间。
- 如需保留总历时，应使用独立字段，例如 `elapsedSeconds` 和 `pausedSeconds`。
- 完成、停止、重置和异常退出必须有明确 completion 语义。
- 同一会话重试上报必须幂等，不得生成重复 session。
- routine 的准备、换侧等 runtime step 可展开执行，但 UI 动作数量仍按原始动作计数。

## 6. 本地优先与同步

- 用户保存操作必须先写本地，再进入 outbox；云端失败不得阻止保存。
- 本地 dirty 记录不得被拉取结果静默覆盖。
- 冲突必须保留双方候选并提示处理，不得只按 `updatedAt` 静默决胜关键实体。
- 所有上行操作必须带 baseRevision 或等价前置条件。
- 同步、重试和重复请求必须幂等。
- 删除使用 tombstone，并有明确保留期；不得立即物理删除。
- 查询必须分页或使用增量游标，不得长期无限量返回全部历史。
- 普通保存不得序列化和重写整个数据库快照。
- Android 使用 Room 作为业务数据 source of truth；DataStore 只保存设备偏好。

## 7. 安全与隐私

- token、API key、密码和真实健康数据不得写入代码、文档、URL、日志或 Git。
- token 只保存在设备本地安全存储；Web 至少隔离于可分享 URL。
- 多设备配置只在云端保存密文；迁移码只在设备本地用于加密、解密和密文读取授权。
- 动态文本默认按纯文本渲染；未经净化不得写入 `innerHTML`。
- Worker 必须认证业务数据访问。加密同步档案密文可由身刻/admin token 或迁移码验证后的访问密钥读取；迁移码永远不写入 URL、Git、日志或云端明文。
- 角色权限必须由服务端执行，不能只依赖前端隐藏按钮。
- 导出文件默认保存在被 `.gitignore` 排除的位置。
- 兼容 AI 的 API key 只可在 TLS 请求中瞬时经过 Worker；不得写入 Worker 日志、D1 业务实体或普通备份。

## 8. 共享 Contract

- 共享数据模型必须与 UI 无关。
- 禁止在共享记录中存 CSS class、卡片样式、导航页签、Android screen 名或临时展开状态。
- 所有共享实体必须有 JSON Schema；所有 API 必须有 OpenAPI 约定。
- 客户端必须声明支持的 `contractVersion`。
- 枚举变化必须向后兼容或提供显式迁移。
- 内部 ID、版本号和下划线枚举不直接展示给用户；展示名通过映射或 title 产生。
- 未知字段在兼容读取和重新序列化时不得无故丢失。

## 9. Web UI

- 已确认的桌面日历是基线，不因代码重构随意换布局、配色或交互。
- 一个用户任务只保留一个主要入口；不得产生“正式记录”和“计时记录”两套相互竞争的历史系统。
- 嵌入计时器应明确区分选择/预览态与执行态。
- 设置中的日常操作、设备迁移和危险操作必须分级。
- 保存训练和保存状态是独立命令，只关闭对应编辑区。
- 桌面抽屉不得成为移动端的默认布局模板。

## 10. Android UI

- 正式 Android 使用 Kotlin + Jetpack Compose；`mobile/` Capacitor 工程只是冻结原型。
- Android 共用领域语义、schema、同步协议和 timer 行为 fixtures，不共用 Web 页面源码。
- 禁止把桌面 7 列月历等比例压缩到手机。
- 禁止依赖右侧抽屉和鼠标 hover 才能完成核心任务。
- 移动端主空间采用 Calendar <- Today -> Training 的连续模型，同时为手势提供可见替代入口。
- Room 是本地业务 source of truth；所有保存先本地后 outbox。
- 计时器必须原生实现，不通过 iframe、外部浏览器或 WebView 作为正式运行时。
- 常亮、TTS、音频 duck、来电暂停、前台服务、旋转和进程恢复必须通过可测试 adapter 隔离。
- 不做自定义锁屏计时界面；平台强制通知必须最小化并使用私密锁屏可见性。

## 11. 可访问性

- 所有核心操作必须可通过键盘完成。
- 弹层使用 dialog 语义、焦点锁定、Escape 关闭和关闭后焦点归还。
- 不得把整个应用根节点设为持续 `aria-live`。
- focus 状态必须清晰可见。
- 动画必须尊重 `prefers-reduced-motion`。
- 文字和状态颜色必须满足可读性，不只依赖颜色表达状态。

## 12. 工程与依赖

- 先拆领域逻辑和 adapter，再考虑 UI 框架变化。
- 不因“现代化”重写已经稳定的产品页面。
- 新依赖必须说明解决的问题、包体成本、维护状态和替代方案。
- 核心规则必须是可测试纯函数，不得隐藏在 DOM 事件中。
- 两个仓库都必须有 CI；Worker、同步和 timer state machine 必须有自动化测试。
- 生成文件、临时截图、真实导出和依赖目录不得提交。
- 任何代理开始任务前必须阅读 `AGENTS.md`；违反 `governance/guardrails.json` 的改动不得通过评审或 CI。

## 13. 变更流程

任何跨端数据变化必须按以下顺序：

1. 更新数据契约和 schema。
2. 定义兼容范围和迁移。
3. 更新 Worker，使其先兼容新旧客户端。
4. 更新身刻和计时器。
5. 运行契约、集成和离线测试。
6. 更新文档并发布。
7. 最后才清理旧兼容路径。

不得先在某一端私自增加含义不同的同名字段。

## 14. 完成定义

一项改造只有同时满足以下条件才算完成：

- 功能实现。
- 旧数据兼容或迁移完成。
- 自动化测试通过。
- 手工关键场景验证通过。
- 安全和权限检查通过。
- 文档同步更新。
- 有可执行回退方案。
- 分别提交并推送到受影响的仓库。
