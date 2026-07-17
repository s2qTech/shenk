# Android Technical Architecture

Updated: 2026-07-17
Status: architecture decision for the native implementation

## 1. Architecture Decision

The production Android app will use **Kotlin + Jetpack Compose**. The current `mobile/` Capacitor project is retained as a frozen prototype until the native app reaches feature parity; it is not the production base.

Reasons:

- The native timer is a core feature, not an embedded Web surface.
- Rotation, process recreation, call interruption, audio focus, text-to-speech, screen-on behavior, notifications, and later widgets/Health Connect are platform responsibilities.
- The product explicitly requires a native, high-quality mobile experience and strong Android performance.
- Continuing with Capacitor would put plugin adapters on every critical path and preserve WebView layout constraints.

This is a presentation/runtime decision. It does not discard the existing cloud contract, offline principles, timer behavior, or Web compatibility.

## 2. Repository Strategy

Keep the two existing repositories separate:

- `training-assistant-v2`: Web Shenk, Cloudflare Worker, shared contracts, and the new native Android app.
- `home-training-timer`: independent Web timer and its browser runtime.

Add the native project under a clear path such as `android-app/` in the Shenk repository. Do not merge the Web timer repository into Shenk.

Share behavior through:

- JSON Schema and OpenAPI contracts.
- Sanitized fixtures.
- Timer execution/state-machine conformance fixtures.
- Documented ownership and resolver rules.

Do not attempt to share Web UI source or run the Web timer in a WebView.

## 3. Recommended Module Shape

Start with a deliberately small module graph:

```text
:app
:core:model-domain
:core:data-sync
:feature:timer-engine
```

Responsibilities:

| Module | Responsibility |
| --- | --- |
| `:app` | Compose UI, navigation, Android lifecycle, notifications, share targets, settings |
| `:core:model-domain` | Contract models, effective-day resolver, plan validation, trend and review inputs |
| `:core:data-sync` | Room, repositories, outbox, Worker API, encrypted configuration, backup |
| `:feature:timer-engine` | Routine expansion, timer state machine, persistence, audio/TTS/call handling |

Feature modules may be split later only when build time or ownership justifies it. Avoid a module per screen in the personal-product phase.

## 4. Technology Selection

### 4.1 Supported platform and build baseline

This is a private, single-primary-device product with no legacy Android install base. The Android production baseline therefore follows these rules:

- Support the current stable Android platform only. The initial baseline is Android 16 / API 36 for compile, target, minimum SDK, emulator, and target-device verification.
- Run Gradle and compile with the current LTS JDK. The initial baseline is JDK 25.
- Emit JVM 17 bytecode for Android and shared JVM modules until the Android D8/R8 toolchain officially supports a newer class-file target. This is an Android build-format boundary, not support for old devices or old Java installations.
- Prefer the newest stable versions inside one officially compatible toolchain set. Do not combine unrelated maximum version numbers when their published compatibility ranges do not overlap.
- Stable platform/library releases are adopted at a package boundary after build, lint, unit, instrumentation, offline, and rollback verification. Preview, alpha, beta, or RC releases require a concrete product need and an isolated rollback path.
- Do not add desugaring, polyfills, alternate implementations, version checks, or UI compromises solely for hypothetical old Android versions.

Initial accepted set:

| Layer | Baseline | Reason |
| --- | --- | --- |
| Android platform | API 36 only | Current stable target and current primary-device baseline |
| Build runtime/toolchain | JDK 25 | Current LTS; supported by the selected Gradle line |
| Android Gradle Plugin | 9.2.1 | Current stable AGP patch |
| Gradle wrapper | 9.4.1 | AGP 9.2 documented default and supported with JDK 25 |
| Kotlin | 2.3.21 | Current validated project pairing with AGP 9.2; upgrade when the full published compatibility set covers the selected AGP/Gradle line |
| JVM class-file target | 17 | Current documented Android language/class-file boundary |

This policy does not remove compatibility obligations for existing user data, Web clients, Worker APIs, encrypted profiles, or Contract v1/v2. Those are live product state rather than obsolete device support.

| Area | Selection | Notes |
| --- | --- | --- |
| Language/UI | Kotlin, Jetpack Compose, Material 3 foundation | Custom Shenk tokens and components; no desktop UI reuse |
| State | ViewModel, `StateFlow`, immutable UI state | Saved-state identifiers only; durable state belongs in Room |
| Local database | Room | Local source of truth for business records, outbox, AI jobs, timer checkpoints |
| Preferences | DataStore | Theme, reminders, device-level preferences; never business records |
| Secrets | Android Keystore + AES-GCM | API keys and decrypted configuration only |
| Network | OkHttp + Retrofit | Small Android-only API surface; Kotlin serialization converter |
| Serialization | Kotlinx Serialization | Preserve unknown JSON fields where round-trip compatibility requires it |
| Background work | WorkManager | Durable sync, pending AI review, scheduled weekly package, retry |
| Timer lifecycle | Foreground service only during active/background training | Visible notification required by Android |
| Voice | Android `TextToSpeech` | Select preferred Chinese female voice when available; safe fallback |
| Audio | Android audio focus with transient ducking | Other audio is lowered for cues, not stopped |
| Import/export | Storage Access Framework | Full JSON backup and plan-patch import |
| DI | Manual application container initially | Add Hilt only if test wiring or graph complexity warrants it |
| Charts | Compose Canvas first | Reassess a maintained chart library after visual prototype |
| Widget | Glance in phase 2 | 2x2 rotating content, deep-link into Shenk records |
| Health data | Health Connect in phase 2 | Feature-detect; Xiaomi-specific fallback remains research work |

## 5. Runtime Layers

```text
Compose UI
  -> use cases / effective-day resolver / timer commands
  -> repositories
  -> Room local source of truth
  -> durable outbox and job queue
  -> Cloudflare Worker API
  -> D1 and optional compatible-AI proxy
```

UI never reads remote DTOs directly. Remote updates are normalized into Room, and Compose observes Room-backed flows.

## 6. Offline-First Data Flow

### Write

1. Validate and normalize input on device.
2. In one Room transaction, update the entity row and enqueue an outbox operation.
3. Render success from local state immediately.
4. WorkManager retries cloud upsert with idempotency metadata.
5. A stale base revision becomes a visible conflict; it is not silently overwritten.

### Read

1. Render cached Room data immediately.
2. Pull cloud changes when online.
3. Do not replace a locally dirty entity with a cloud copy.
4. Merge identical changes automatically; expose materially different conflicts.

### Retention

- Cache at least the current formal week, nearby daily records, active routines, and recent trends.
- The app remains useful for a full week without network access.
- Server-side deletion uses tombstones for synchronization, while deleted items disappear from normal UI immediately.

Reference: [Android offline-first guidance](https://developer.android.com/topic/architecture/data-layer/offline-first) and [data-layer guidance](https://developer.android.com/topic/architecture/data-layer).

## 7. Native Timer Architecture

The Android timer uses the same behavioral contract as the Web timer but is implemented natively.

### State Machine

```text
idle -> preview -> running <-> paused -> completed
                         -> stopped
```

Durable state contains:

- session ID and routine snapshot/digest
- logical and expanded step indexes
- active, elapsed, and paused time
- last monotonic checkpoint and wall-clock timestamp
- completion and interruption reason

Persist a checkpoint on meaningful transitions and a bounded heartbeat. Reconstruct from elapsed monotonic time where possible after process recreation.

### Android Integration

- Use `FLAG_KEEP_SCREEN_ON` only in the active timer surface.
- Use a foreground service if training continues when the activity leaves the foreground.
- Keep any platform-required foreground-service notification minimal and use private lock-screen visibility; do not build lock-screen controls as a product surface.
- A call-state interruption pauses once and records the reason.
- Audio cues request transient audio focus with ducking.
- Rotation recreates UI from the same durable session, not a second timer.
- Post-workout formal-log editing is separate from timer fact persistence.

References: [keep the screen on](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on), [audio focus](https://developer.android.com/media/optimize/audio-focus), [foreground services](https://developer.android.com/develop/background-work/services), and [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech).

## 8. AI Architecture

### Provider Contract

Phase 1 supports OpenAI Chat Completions-compatible providers through configurable templates:

- DeepSeek
- Alibaba Bailian / Qwen
- Zhipu GLM
- SiliconFlow
- custom compatible endpoint

Phone configuration contains provider base URL, API key, and model. It can be encrypted into the existing migration profile.

### Request Path

```text
Android device
  -> Cloudflare Worker AI proxy
  -> configured compatible provider
```

- The phone sends the provider secret only over TLS for the request.
- The Worker must not persist or log plaintext provider keys.
- Provider metadata stored with a review excludes secrets.
- Input is a normalized review payload, not arbitrary database dumps.
- Output must satisfy a strict daily-review schema before it is stored.

### Daily Review Queue

1. A confirmed day mutation invalidates the current review input digest.
2. If required information is missing, prompt once for completion or explicit partial generation.
3. Online: enqueue immediate generation.
4. Offline: persist an AI job and retry through WorkManager.
5. Correction creates a new version; only the latest is prominent.

Daily AI cannot write formal plans, plan adjustments, or routines.

## 9. Notifications

Use exact alarms only if Android policy and precision truly require them; ordinary WorkManager or AlarmManager scheduling is sufficient for user-adjustable reminders.

Defaults:

| Reminder | Default | Rule |
| --- | --- | --- |
| Morning check-in | 08:45 daily | Maximum once per day |
| Missing data | 12:30 daily | Only if meaningful morning fields remain missing |
| Unrecorded day | 23:15 daily | Do not auto-create rest |
| Weekly review | Saturday 22:30 | Generate/share package when data is ready |

Each reminder is independently adjustable and disableable. Notification content should deep-link to the relevant native state.

## 10. Security and Configuration Migration

Reuse the established profile algorithm for interoperability:

- profile ID: SHA-256 of the migration code, Base64URL-derived opaque ID
- key derivation: PBKDF2-HMAC-SHA256, 210,000 iterations, 16-byte salt
- encryption: AES-256-GCM, 12-byte IV
- cloud stores ciphertext and authorization hash, not plaintext secrets

Implement cross-platform fixtures before Android onboarding is considered complete. Verify Web encrypt -> Android decrypt and Android encrypt -> Web decrypt.

Secrets must not enter:

- URLs
- Room business entities
- logs or crash reports
- analytics
- JSON backup
- screenshots or test fixtures

## 11. Web and Worker Compatibility

- Web remains readable and writable under the same entity envelopes.
- Android-specific presentation state is local and must not pollute shared records.
- New contract fields are additive first; old clients must preserve unknown fields when round-tripping.
- The Worker receives matching validation and role-ownership updates before Android writes new entities.
- Timer ownership is defined by the timer module, not by repository: Web timer and native Android timer may write `timer_sessions`; the record/calendar module may not.

## 12. Process and Quality Gates

No Android feature package starts until:

1. Its contract fields and ownership are defined.
2. Sanitized fixtures exist for Web, Worker, Web timer, and Android.
3. Offline, conflict, deletion, and backward compatibility behavior is specified.
4. UI acceptance criteria include one-handed use, font scaling, dark theme, and reduced motion.

Compose implementation should follow [Compose layering guidance](https://developer.android.com/develop/ui/compose/layering). Phase-2 capabilities should follow [Health Connect availability](https://developer.android.com/health-and-fitness/health-connect/availability) and [Glance widget guidance](https://developer.android.com/develop/ui/compose/glance).
