# ADR 0001: 正式 Android 使用 Kotlin + Jetpack Compose

Status: Accepted
Date: 2026-07-16

## Context

Android 将承担约 80% 的日常使用，并需要原生计时、横竖屏连续状态、来电暂停、音频 duck、TTS、后台队列、通知，以及后续 Widget 和 Health Connect。

## Decision

- 正式 Android 在 `android-app/` 使用 Kotlin + Jetpack Compose。
- `mobile/` Capacitor 工程冻结为原型，不继续承载生产功能。
- Web UI 不嵌入 Android，Web timer 不通过 WebView 作为正式计时器。
- 跨端只共享数据契约、fixtures 和可验证行为，不共享页面源码。

## Consequences

- 需要原生重建 UI、Room 数据层和 timer runtime。
- 可直接使用 Android 生命周期、音频、TTS、前台服务和后续健康能力。
- 改回 Capacitor 或其他正式技术栈需要用户明确批准和 superseding ADR。
