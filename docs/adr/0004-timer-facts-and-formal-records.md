# ADR 0004: 计时事实与正式训练记录分离

Status: Accepted
Date: 2026-07-16

## Decision

- `timer_sessions` 是计时模块产生的不可反向改写执行事实。
- `training_logs` 是用户补充心率、体感、备注并确认后的正式记录。
- Timer 完成时先持久化 session，再进入训练后补充页面。
- 中途离开保留 `pending completion`，不自动生成正式记录。
- Web timer 与 Android 原生 timer 可写 `timer_sessions`；日历/记录模块不得写。

## Consequences

- 应用边界不能简单写成“身刻不能写 timer session”，必须按模块角色授权。
- 正式记录可以引用一个或多个 session，但不能覆盖 session 原始事实。
