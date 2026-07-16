# ADR 0002: Android 使用 Room 本地第一写入和持久 outbox

Status: Accepted
Date: 2026-07-16

## Context

用户需要一周断网仍能查看计划、执行训练和记录数据。云端不可用不能阻塞日常使用，多设备同步也不能静默覆盖本地修改。

## Decision

- Room 是 Android 业务数据唯一 UI source of truth。
- 每次业务保存和 outbox 入队处于同一数据库事务。
- WorkManager 负责持久重试、增量拉取和 AI 待办。
- dirty 记录不被 pull 覆盖；实质冲突进入显式 conflict 状态。
- DataStore 只保存设备偏好，不保存业务事实。

## Consequences

- UI 不直接观察远程 DTO。
- 所有新实体都要定义本地表、outbox、幂等、冲突和迁移语义。
- 改成 cloud-first 或整包快照写入需要用户明确批准。
