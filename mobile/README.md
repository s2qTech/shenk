# 身刻移动端基础工程

`mobile/` 是身刻的独立移动呈现层。它不复用桌面七列日历、日详情抽屉或桌面设置布局。

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

## Android 原生职责

这一阶段已建立 Capacitor 适配边界：安全存储、触感反馈、外部计时器打开、应用前后台事件和浏览器语音。后台保活、原生 TTS、前台服务和文件导入导出将在真实设备验证后以原生插件完成，不能在 Web 壳中假定已具备。
