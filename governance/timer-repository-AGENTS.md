# home-training-timer 仓库代理规则

本文件是独立 Web timer 仓库的 AI/开发入口。它必须复制为 `home-training-timer/AGENTS.md`。

## 必读

1. 本仓库 `PROJECT_BOUNDARIES.md`
2. 本仓库 `MODULE_BOUNDARIES.md`
3. 本仓库 `contracts/v1/`、`contracts/v2/` 镜像和测试
4. 身刻仓库 `AGENTS.md`
5. 身刻仓库 `governance/guardrails.json`
6. 身刻仓库 `docs/data-contract.md`
7. 身刻仓库 `docs/development-constraints.md`
8. 身刻仓库 `docs/adr/0004-timer-facts-and-formal-records.md`
9. 身刻仓库 `docs/adr/0005-explicit-routine-scene-role.md`

如果身刻仓库不在同一台机器，先从 `https://github.com/s2qTech/shenk` 读取 canonical 文档；不要凭本仓库旧代码猜测共享语义。

## 当前角色

- 本仓库是独立部署的 Web 计时器，不合并进身刻仓库。
- Android 原生计时器在身刻仓库实现，但必须与本仓库共享 contract 和行为 fixtures。
- 本仓库不承载 Android UI，也不是 Android 的 WebView runtime。

## 不可突破边界

1. Web timer 只写 `timer_sessions`，不得写 `training_logs`、身体数据或计划实体。
2. `timer_sessions` 是不可反向改写的执行事实；正式训练记录由身刻记录模块在用户确认后创建。
3. routine 从共享 `routine_templates` 读取并缓存最后一套有效数据。
4. 请求的云端 routine 不存在或读取失败时必须明确报错，不静默换成旧内置流程。
5. 内置 routine 只用于开发/应急 fallback，使用时必须有明确警告。
6. `scene` 和 `role` 是模板提供的权威字段；禁止从标题、trainingType、routineId 或动作内容推断、覆盖、清理或迁移。
7. 必须保留未知兼容字段，尤其是 cues、warnings、safetyNotes、breath、execution 和后续 mediaAssetId。
8. 准备、换侧等 runtime step 可以展开，但用户动作数量仍按原始逻辑动作计算。
9. `actualSeconds` 只计算有效执行时间；elapsed 和 paused 独立保存。
10. 完成、停止、重置、异常离开和恢复必须有明确、幂等的 session 语义。
11. token、API key、真实 session/健康数据不得进入代码、文档、URL、日志、fixture 或 Git。

## 必须先确认

以下操作必须先描述影响并取得用户明确批准：

- 批量删除、重分类、停用或覆盖云端 routine。
- 根据名称自动补 scene/role。
- 改变 session 字段含义、写入 owner 或正式记录流程。
- 修改共享 Contract、部署地址、仓库边界或 fallback 策略。

## 跨端改动顺序

共享字段变化必须先在身刻 canonical Contract 中定义 schema、owner、兼容和 fixture，再更新 Worker、本仓库和 Android。不得先在 timer 私自增加同名异义字段。

## 完成定义

- 纯 engine/session 规则有 Node 测试。
- 契约镜像与身刻 canonical fixture 一致。
- 云端失败、离线缓存、重复上报和异常退出已验证。
- 动态文本安全渲染。
- 没有真实数据和密钥进入 diff。
- 文档同步更新并推送本仓库。
