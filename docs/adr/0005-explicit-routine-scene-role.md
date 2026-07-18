# ADR 0005: routine 场景和角色必须显式

Status: Accepted
Date: 2026-07-16
Clarified: 2026-07-18

## Decision

- 新增、替换或迁移 routine 必须显式包含 `scene` 和 `role`。
- `scene` 只允许 `home`、`walk`、`recovery`、`travel`，并独立决定 UI 分组。
- `role` 只允许 `main`、`warmup`、`stretch`、`cooldown`、`recovery`、`auxiliary`，描述执行用途而非分组。
- 禁止从 `title`、`trainingType`、`routineId`、版本或动作内容推断、覆盖或自动迁移分类。
- 缺失权威字段的 routine 在导入校验和计时器加载时必须报错，由计划提供方修正。
- Contract v2 lifecycle 只允许 `draft`、`published`、`archived`；旧值 `active` 只可在读取旧数据时识别，不得由新 patch 写入。

## Consequences

- 自动分类脚本、标题匹配和类型兜底都属于越界。
- 批量补分类、重分类或清理 routine 前必须展示逐条变更并取得用户确认。
- Web、Android 和计时器应共享同一套严格校验；不得在任一客户端增加静默兜底来掩盖脏数据。
- 当前可转发的完整计划端约束见 `docs/coach-routine-contract.md`。
