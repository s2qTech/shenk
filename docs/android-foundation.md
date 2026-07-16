# Android Foundation

> Status: superseded as a production direction on 2026-07-16. The Capacitor project described below is retained only as a prototype. The confirmed native direction is defined in `android-product-blueprint.md`, `android-technical-architecture.md`, `android-contract-v2-plan.md`, and `android-delivery-and-constraints.md`.

## 目标

Android 端复用身刻的业务数据契约、离线记录原则和云端同步协议，但拥有独立的移动信息架构。它不是桌面 Web 的缩放版本。

## 工程边界

| 层 | 位置 | 责任 |
| --- | --- | --- |
| 共享契约 | `contracts/v1/` | 实体 envelope、枚举、API 请求与 fixtures |
| Web 业务核心 | `src/` | 既有桌面端的记录、建议、同步核心 |
| 移动领域核心 | `mobile/src/domain/` | 日期优先级、类型显示、趋势、Timer 启动参数 |
| 移动存储与平台适配 | `mobile/src/platform/` | IndexedDB、outbox、云同步、安全配置、触感、外部 Timer |
| 移动呈现 | `mobile/src/main.js` / `styles.css` | 今天、训练、记录、数据、设置 |
| Android 容器 | `mobile/android/` | Capacitor 生成的原生项目，不手写业务规则 |

移动层不得复制或修改 timer 的执行引擎。计时器继续作为独立项目运行，移动端只传递不含 token 的 `routineId`、`date`、`dailyPlanItemId`、`planTemplateId` 和 `source=shenk`。

## 核心信息架构

1. **今天**：当天有效指导、身体状态、正式记录、计时器事实数量和快捷入口。
2. **训练**：按当前有效 `routine_templates` 选择方案并打开独立计时器。
3. **记录**：仅显示 `training_logs`；timer session 不作为第二套正式历史。
4. **数据**：体重、腰围、体脂率、肌肉量趋势。
5. **设置**：本机云端配置与离线队列状态。

不包含：桌面七列月历、右侧抽屉、嵌套滚动表格、把 timer iframe 缩进手机页面。

## 数据与同步

- 本机先写入 entity rows/outbox，联网后再调用 `/records/upsert`。每个设备使用稳定、非敏感的本机 `deviceId` 参与同步和冲突定位。
- 拉取 `/records/query` 时，outbox 中仍脏的数据不会被云端静默覆盖。
- 当天优先级固定：正式训练记录 > 调整 > 已确认日计划 > 本地兜底建议。
- `timer_sessions` 只能读取；用户确认并补充后才生成 `training_logs`。
- 云端 revision/conflict 语义沿用 Web Contract v1；移动端必须显示冲突，不可静默覆盖。

## 配置和密钥

- API/Timer 地址是非敏感公开配置。
- 身刻 token 和 timer token 在 Android 使用 Android Keystore 加密的 Secure Storage。
- 浏览器预览不保存 token，避免插件的 Web fallback 写入 localStorage。
- 多端迁移仍沿用身刻现有的加密迁移码；移动端以后接入同一逻辑，不能生成第二套明文同步机制。

## 原生能力路线

| 能力 | 当前基础 | 设备验证后的下一实现 |
| --- | --- | --- |
| 安全存储 | Secure Storage adapter | Android Keystore 真机读写验证 |
| 触感 | Capacitor Haptics adapter | 节点切换与完成提示的节流策略 |
| Timer 打开 | Capacitor Browser adapter | 与 timer app 的回到身刻协议 |
| 前后台 | App lifecycle adapter | timer 侧 foreground service / wake lock 策略 |
| 语音 | 浏览器 fallback | timer 原生 TTS adapter |
| 导入导出 | 尚未开放 | Filesystem + Android SAF 适配 |

## 验收

1. 相同 Contract fixtures 在 Web/Android 给出同一当天有效指导。
2. 飞行模式下能查看已同步记录与已缓存 routine，并把可写实体加入本地 outbox。
3. 重新联网后 outbox 能写入且冲突可见。
4. token 不出现在 URL、日志、localStorage、IndexedDB record 或导出内容中。
5. 360px 宽度下没有横向桌面布局、嵌套抽屉或不可达底部主操作。
