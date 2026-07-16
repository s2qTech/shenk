# System Design

更新日期：2026-07-17

> Android 生产架构已由 `android-technical-architecture.md` 取代。本文继续描述当前 Web/Worker 基线；其中“后续 Android”不再表示复用 Web runtime。

## 当前架构

```text
身刻 Web
  -> 本地 IndexedDB + outbox
  -> Cloudflare Worker API
  -> Cloudflare D1

身刻 Android（计划）
  -> Kotlin + Jetpack Compose
  -> Room + outbox + WorkManager
  -> Cloudflare Worker API
  -> Cloudflare D1

home-training-timer Web
  -> 本地 routine cache + timer state
  -> Cloudflare Worker API
  -> Cloudflare D1
```

两个应用共享数据契约和数据库，但不共享页面结构，也不跨越写入职责。

## 组件职责

### 身刻

- 管理 plan/routine 模板导入和确认。
- 保存 daily plan 快照和调整历史。
- 保存正式训练记录和身体状态。
- 提供日历、趋势、反馈和同步冲突处理。

### home-training-timer

- 从 D1 读取并缓存 `routine_templates`。
- 展开 execution 结构并执行计时状态机。
- 管理语音、提示音、常亮和暂停恢复。
- 只写 `timer_sessions`。

### Worker + D1

- 验证身份和角色写入范围。
- 校验 record envelope 和实体内容。
- 管理 revision、tombstone、分页和冲突元数据。
- 保存加密后的设备配置档案。

## 本地优先

用户操作流程：

```text
用户保存
  -> 写本地实体 store
  -> 追加 outbox
  -> 立即更新 UI
  -> 后台批量推送
  -> 拉取增量记录
  -> 合并或标记冲突
```

云端不可用时，查看、记录、建议和缓存 routine 执行仍应可用。

## 数据层级

- 模板：`plan_templates`、`routine_templates`。
- 日期指导：`daily_plan_items`、`plan_adjustments`。
- 执行事实：`timer_sessions`。
- 用户确认事实：`training_logs`、`body_metrics`。
- 派生输出：趋势、日历展示、反馈摘要。

派生输出不应反向覆盖事实记录。

## 日历决策

```text
实际 training_logs
  > 最新有效计划（daily item + adjustment）
  > 本地兜底建议
```

历史调整继续保存，但普通日期详情只显示当前有效指导。

## 技术演进

- 当前静态 Web 逐步迁移到 Vite + TypeScript。
- 不先做 UI 框架重写；优先抽离 domain、storage、sync 和 adapter。
- IndexedDB 从整包快照转为实体 store + outbox。
- Worker 从逐条无界操作转为批量、分页和实体 schema 校验。
- Android 复用领域语义、数据契约、同步协议和 fixtures，使用独立 Kotlin/Compose 实现，不复用 Web 页面或 JavaScript runtime。

## 安全

- token 和密码不进入 URL、代码、文档和 Git。
- 云端只保存配置密文，密码只在设备本地使用。
- 动态文本按纯文本渲染。
- Worker 在读取配置和业务数据前完成认证。

完整规则见 [开发约束边界](development-constraints.md)，实施顺序见 [下一阶段开发方案](next-stage-development-plan.md)。
