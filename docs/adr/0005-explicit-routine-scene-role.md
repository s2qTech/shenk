# ADR 0005: routine 场景和角色必须显式

Status: Accepted
Date: 2026-07-16

## Decision

- 新 routine 必须显式包含 `scene` 和 `role`。
- `scene` 只允许 home、walk、recovery、travel，并决定 UI 分组。
- `role` 描述 main、warmup、stretch、cooldown、recovery、auxiliary 等执行用途。
- 禁止从 title、trainingType、routineId、版本或动作内容推断、覆盖或自动迁移分类。
- 缺失权威字段的 routine 在导入校验中报错，由计划提供方修正。

## Consequences

- 自动分类脚本和兜底标题匹配都属于越界。
- 批量补分类或清理旧 routine 前必须展示具体变更并取得用户确认。
