# 身刻 Capacitor 验证原型（冻结）

`mobile/` 只保留为历史验证原型，不是正式 Android 基础，不再接收生产功能。正式 Android 工程位于 `android-app/`，使用 Kotlin + Jetpack Compose。

本目录仅用于回归既有 Capacitor 行为。不要从这里复制页面结构、存储实现或计时器运行时到正式 Android；跨端只共享契约、脱敏 fixtures 和可验证的领域语义。

## 运行

```powershell
cd mobile
pnpm install
pnpm dev
pnpm run test
pnpm run android:sync
pnpm run android:open
```

首次生成 Android 项目：

```powershell
pnpm exec cap add android
pnpm run android:sync
```

本机已配置 Android SDK 后，可运行 `pnpm run android:debug` 生成 debug APK。

## 数据与安全边界

- 使用与 Web 相同的 Contract v1 record envelope、实体名和云端 API。
- `shenk-mobile` IndexedDB 是移动 Web 壳的离线记录层；Android WebView 同样使用本机缓存。
- `timer_sessions` 只读；身刻只能写自己的实体。
- API 地址、Timer 地址可存公开本地配置；`SHENK_TOKEN` 和 `TIMER_TOKEN` 仅在原生环境写入 Android Keystore 的 Secure Storage。
- 浏览器预览中拒绝保存 token，避免把密钥落到 localStorage。
- 计时器 URL 只带 routine/date/plan 上下文，绝不携带 token。
- 本机 `deviceId` 只用于同步身份和冲突定位，保存为非敏感本机标识；不会出现在计划、训练记录或导出数据中。

## 历史原生适配职责

本原型曾建立 Capacitor 适配边界：安全存储、触感反馈、外部计时器打开、应用前后台事件和浏览器语音。这些内容不再继续扩展；正式原生能力按 `docs/android-delivery-and-constraints.md` 在 `android-app/` 分包实现。
