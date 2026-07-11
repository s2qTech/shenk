# Mobile Strategy

更新日期：2026-07-11

## 结论

Android 不采用“桌面 Web 页面缩放后装进壳”的方案。

推荐结构：

```text
共享领域层 / 数据契约 / 同步协议 / timer engine
  -> Web 桌面呈现
  -> Android 独立移动呈现

Capacitor Android 壳
  -> Secure Storage
  -> TTS / Wake Lock / Foreground Service
  -> 文件导入导出 / 生命周期适配
```

共享的是业务含义和执行引擎，不是桌面页面结构。

## 为什么仍选择 Capacitor

- 小米 HyperOS 上 PWA 安装和后台能力不够稳定。
- Capacitor 能提供 APK、原生权限和安全存储。
- 领域逻辑仍可复用，避免再维护一套训练规则和同步协议。
- 后续可按需增加前台服务、通知和文件能力。

Capacitor 是平台容器，不是要求复用桌面 UI 的理由。

## 移动端核心任务

移动端首版优先完成：

1. 查看今天最终应该做什么。
2. 一步进入对应计时器流程。
3. 快速补充训练数据和身体状态。
4. 查看近期记录和趋势摘要。
5. 离线查看、记录、执行缓存流程，联网后同步。

桌面端的完整月历、宽表设置和右侧抽屉不是移动端核心结构。

## 共享与独立边界

必须共享：

- 共享实体和 JSON Schema。
- 训练类型、状态和名称映射。
- 计划归一化、日历优先级和趋势计算。
- IndexedDB repository 接口和同步协议。
- routine step 展开和 timer state machine。

必须独立：

- 导航、页面组合、移动日历、表单布局。
- 手势、返回键、底部导航和全屏训练体验。
- Android 生命周期、常亮、TTS 和前台服务 adapter。

## 数据策略

- Android 仍以本地第一写入点为原则。
- 使用与 Web 相同的 record envelope、revision、outbox 和 conflict 语义。
- 配置档案从云端读取密文，在设备本地解密。
- 解密后的 token 存入系统安全存储，不进入 URL、日志或业务实体。
- Android 与 Web 必须通过同一组 contract fixtures。

## UI 约束

- 不把 7 列桌面月历压缩到手机宽度。
- 不依赖 hover、右侧抽屉或鼠标右键。
- 不产生双层滚动的嵌入式计时器。
- 训练运行态优先显示当前动作、剩余时间和少量直接控制。
- 选择 routine、动作预览和运行时应是清晰分离的状态。
- 支持字体缩放、屏幕阅读器、减少动画和高对比度。

## 实施顺序

1. 先完成共享 Contract v1 和核心自动化测试。
2. 抽离 domain、sync 和 timer engine。
3. 建立 Capacitor 壳和原生 adapter。
4. 用脱敏数据跑通今日、训练、补录、近期记录。
5. 再进行移动 UI 视觉设计和设备测试。

详细阶段和验收门槛见 [下一阶段开发方案](next-stage-development-plan.md)。
