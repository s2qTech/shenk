# System Design

## 总体架构

推荐架构：

```text
Shared Web Frontend
  -> Local IndexedDB
  -> Optional Cloud Sync
  -> Backend Function
  -> OpenAI API
  -> Weather API

Android Shell
  -> Same Shared Web Frontend
```

第一阶段本地优先：

- IndexedDB 保存日志、体重、设置和本地截图缓存。
- JSON 导入导出。
- 不要求登录。

第二阶段同步：

- Supabase 或 Firebase 保存用户数据。
- 登录后多设备同步。
- 同一天记录按 `updatedAt` 合并。

第三阶段 AI：

- 前端上传截图到后端函数。
- 后端函数调用 OpenAI API。
- 返回结构化 JSON。
- 前端展示识别结果。
- 用户确认后保存。

## 推荐技术栈

前端：

- React 或 Svelte。
- Vite。
- Web/PWA 兼容，但不把 PWA 安装作为唯一手机入口。
- Android 壳建议使用 Capacitor。
- IndexedDB，建议 Dexie。
- CSS 变量 + 原生 CSS，保持 Scandi 风格。

后端：

- Supabase Edge Function，或 Firebase Cloud Function。
- OpenAI API Key 只放后端。
- 天气 API 可由前端直接调用，也可后端代理。

同步：

- 推荐 Supabase/Firebase。
- OneDrive JSON 同步作为后续可选项。
- JSON 导入导出必须长期保留。

移动端：

- 小米/HyperOS 上 PWA 安装可能不可用或体验不稳定，因此 v2 不依赖纯 PWA。
- 推荐用 Capacitor 打包 Android APK。
- 同一套 Web 前端同时服务电脑网页和 Android 壳。
- Android 壳只负责安装入口、权限、文件选择、相机/相册访问、常亮能力等平台能力。
- 业务逻辑、UI、数据模型和 AI 调用仍放在共享 Web 层。
- Trusted Web Activity 可作为后续备选，但它仍依赖浏览器能力，不作为第一选择。

## 数据模型

### workout_logs

```json
{
  "id": "uuid",
  "date": "2026-06-10",
  "type": "strength | easyWalk | qualityWalk | indoorCardio | recovery | rest",
  "status": "completed | short | stretchOnly | skipped",
  "source": "manual | timer | aiScreenshot | import",
  "durationSec": 3600,
  "distanceKm": 5.89,
  "avgPaceSecPerKm": 628,
  "bestPaceSecPerKm": 559,
  "avgHeartRate": 123,
  "maxHeartRate": 140,
  "steps": 6578,
  "cadence": 110,
  "strideCm": 85,
  "trainingEffect": 2.4,
  "trainingLoad": 56,
  "recoveryHours": 14,
  "lapPaces": [
    { "km": 1, "paceSec": 659 },
    { "km": 2, "paceSec": 638 }
  ],
  "fatigue": "low | normal | high | severe",
  "pain": {
    "calf": 0,
    "back": 0,
    "wrist": 0,
    "outerThigh": 0
  },
  "notes": "",
  "weatherId": "uuid",
  "bodyMetricId": "uuid",
  "createdAt": "iso",
  "updatedAt": "iso"
}
```

### body_metrics

```json
{
  "id": "uuid",
  "date": "2026-06-10",
  "weightKg": 80.0,
  "waistCm": 90.0,
  "sleepQuality": "poor | normal | good",
  "energy": 3,
  "notes": "",
  "createdAt": "iso",
  "updatedAt": "iso"
}
```

### weather_logs

```json
{
  "id": "uuid",
  "date": "2026-06-10",
  "locationName": "Beijing",
  "latitude": 39.9,
  "longitude": 116.4,
  "temperatureC": 28,
  "humidity": 65,
  "precipitationMm": 0,
  "windSpeedKmh": 12,
  "condition": "cloudy | rainy | sunny | hot | humid | windy",
  "source": "auto | manual",
  "createdAt": "iso",
  "updatedAt": "iso"
}
```

### ai_analysis

```json
{
  "id": "uuid",
  "date": "2026-06-10",
  "kind": "screenshotParse | dailyAdvice | weeklyReview",
  "inputRefs": [],
  "result": {},
  "confidence": 0.86,
  "needsReview": true,
  "createdAt": "iso"
}
```

## AI 截图识别输出格式

AI 必须返回 JSON，不返回自由散文作为主结果。

```json
{
  "detectedWorkoutType": "easyWalk | qualityWalk | recoveryWalk | indoor | strength | unknown",
  "summary": {
    "distanceKm": 5.89,
    "durationSec": 3700,
    "avgPaceSecPerKm": 628,
    "avgHeartRate": 123,
    "maxHeartRate": 140,
    "steps": 6578,
    "trainingEffect": 2.4,
    "trainingLoad": 56,
    "recoveryHours": 14
  },
  "laps": [
    { "index": 1, "paceSecPerKm": 659 },
    { "index": 2, "paceSecPerKm": 638 }
  ],
  "analysis": {
    "intensity": "moderate | quality | recovery | tooHard",
    "riskFlags": ["calfLoad", "lateWorkout"],
    "plainText": "这次属于提高走，原因是第 4 公里明显提速。"
  },
  "confidence": {
    "overall": 0.85,
    "distance": 0.98,
    "heartRate": 0.9,
    "laps": 0.75
  },
  "fieldsNeedingReview": ["laps"]
}
```

## AI 提示词约束

AI 必须知道用户长期约束：

- 41 岁。
- 身高约 184.5 cm。
- 体重约 109 kg。
- BMI 偏高。
- 腰突史。
- 手腕不适。
- 左小腿旧伤。
- 目标是减脂、心肺、体能、体态、精力。
- 不能用年轻人高冲击方案。

AI 分析必须遵守：

- 不把手环燃脂区间当唯一标准。
- 不建议连续高强度。
- 不建议疼痛硬扛。
- 不把暴汗当力量训练有效性的主要指标。
- 单次数据必须结合历史趋势。
- 输出要有明确下一步。

## 天气策略

第一阶段：

- 记录当天时获取一次天气。
- 浏览器定位授权失败时允许手动选择。
- 不补历史天气。

天气字段只用于辅助解释：

- 闷热可能影响心率和疲劳。
- 下雨影响户外走路安排。
- 风大或冷天影响体感。

天气不能作为训练建议的唯一依据。

## 同步策略

第一阶段：

- 本地 IndexedDB。
- JSON 导出/导入。

第二阶段：

- 用户登录。
- 云端保存结构化日志。
- 同一天多端冲突按 `updatedAt` 合并。
- 关键字段冲突时保留两个版本并提示用户。

云端默认不保存原始截图。

截图策略：

- 默认只用于识别。
- 用户可选择保存原图到本地。
- 云端只保存识别结果和可选缩略图。

## 安全和隐私

- OpenAI API Key 不能放前端。
- 前端不能直接调用 OpenAI API。
- 默认不上传非必要个人文件。
- 截图上传前明确告知。
- 用户可删除 AI 分析记录。
- 用户可导出全部数据。
- 用户可清空本地数据。

## 实现顺序

1. 初始化 v2 前端项目。
2. 搭建 Scandi UI 框架和布局。
3. 验证 Android 壳方案，优先 Capacitor APK。
4. 迁移 v1 计时器流程。
5. 实现本地 IndexedDB 日志。
6. 实现日历视图。
7. 实现体重和天气记录。
8. 实现 JSON 导入导出。
9. 实现建议引擎。
10. 接入云同步。
11. 接入 AI 截图识别。
12. 实现周复盘。
