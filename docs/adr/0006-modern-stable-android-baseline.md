# ADR 0006: Android 采用当前稳定单设备基线

Status: Accepted
Date: 2026-07-17

## Context

身刻 Android 在可预见阶段是私人安装、单一主设备使用的新项目，没有旧安装版本、公共商店机型矩阵或旧 Android 系统兼容责任。此前使用 `minSdk 26` 和 JDK 17 工具链是面向通用 Android 产品的保守默认值，与产品实际边界不一致。

同时，Web 身刻、Web 计时器、Worker、D1 和用户数据已经存在。它们的数据契约与同步兼容责任不能被误认为旧机型兼容而删除。

## Decision

- 正式 Android 只支持当前主设备上的稳定 Android 平台；初始基线为 Android 16 / API 36。
- `compileSdk`、`targetSdk`、`minSdk`、CI 模拟器和目标设备验证使用同一 API 36 基线。
- Gradle 运行与编译 toolchain 使用当前 LTS JDK 25。
- Android 与共享 JVM 模块暂时输出 JVM 17 字节码，直到 Android D8/R8 官方支持更高 class-file target。
- AGP、Gradle、Kotlin 和库按官方稳定兼容交集成套升级，不以“每个组件版本号最大”为目标。
- 不为假设中的旧 Android 版本增加 polyfill、desugaring、版本分支、降级 UI 或旧依赖。
- 预览版、alpha、beta 和兼容矩阵外组合只有在解决明确产品问题且具备隔离验证、回退方案时才可采用。

## Consequences

- 可以直接使用 API 36 的稳定平台能力，不维护 API 26-35 的兼容路径。
- Android 安装包不会支持 API 36 以下设备；这是产品边界而不是缺陷。
- 开发机需要 JDK 25 和 Android SDK 36；CI 使用相同基线。
- JDK 25 不意味着输出 Java 25 class files。Android 字节码目标由 D8/R8 支持能力独立决定。
- Web/Worker Contract v1/v2、现有云数据、加密档案和离线迁移仍必须兼容。
- 若未来转向公共分发或新增第二台低版本设备，必须由用户明确批准并以 superseding ADR 重新定义支持矩阵。
