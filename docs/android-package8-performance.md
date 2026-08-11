# Android Package 8 Performance and Stability Gate

Updated: 2026-08-12
Status: P8.4 accepted on Xiaomi 14

## Scope

P8.4 covers Android startup, primary-page navigation, calendar scrolling, timer execution, and local database/sync work. It does not change shared contracts, business entities, ownership, user confirmation, or synchronization semantics.

## Implemented Corrections

- The first Today frame no longer composes both adjacent primary pages. Calendar and Training are composed after the first completed primary-page navigation or after a two-second idle warm-up, then the bounded three-page space is retained. The idle fallback preserves process-recreation timer recovery without competing with the first frame.
- The native timer coordinator remains lazy until Training is first composed. TTS and phone-call monitoring remain completely uninitialized while the timer is idle and are released again after reset.
- The 13-month calendar builds one immutable guidance index per Room emission. Training logs, plans, adjustments, and generated review suggestions are decoded/grouped once, then resolved by date, instead of scanning all records once for every displayed day.
- Application startup enqueues the existing unique WorkManager sync rather than executing an uncoordinated direct cloud synchronization beside first-screen Room queries. Local-first writes and all sync semantics are unchanged.
- One application-scoped reminder preference store replaces repeated wrapper allocation from composition and startup code.

## Reproducible Gates

`android-app/ci/measure-device-performance.ps1` measures repeated cold starts through Android's `am start -W` and reports the current `gfxinfo` summary. It requires exactly one authorized device and writes no files or business data.

Automated regression coverage also includes:

- a 400-day indexed guidance projection;
- a one-hour virtual timer run at 250 ms ticks;
- a 400-day in-memory Room projection with 560 sanitized records;
- a full 100-operation outbox push through an in-memory database and fake Worker API.

The time budgets are deliberately generous regression ceilings, not public performance claims. The signed P8.8 release candidate must be remeasured because debug APK timing includes debugger/runtime overhead and no release shrinking.

## Xiaomi 14 Results

Device: Xiaomi 14 (`23127PN0CC`), Android 16 / API 36. Build: data-preserving debug APK installed over the existing app.

| Path | Result |
| --- | --- |
| Cold start baseline, 5 runs | 607, 560, 579, 558, 638 ms; median 579 ms |
| Cold start after P8.4, 5 runs | 585, 556, 500, 460, 505 ms; median 505 ms |
| Four primary-page transitions after idle warm-up | 514 frames; 1.17% janky; p95 19 ms |
| Calendar scroll, 12 gestures | 1,152 frames; 0.09% janky; p95 25 ms |
| 400-day / 560-record Room projection | 123 ms |
| 100-operation local outbox sync | 716 ms |
| One virtual timer hour / 14,400 ticks | 40 ms on the JVM gate |

Results can vary with thermal state, refresh rate, background load, and debug runtime; regression comparisons must use the same device, build type, and command sequence. The navigation and scrolling frame windows are measured separately from the immediate post-launch `gfxinfo` snapshot so the sample sizes are explicit.

## Stability and Data Safety

- Performance instrumentation uses only an in-memory Room database and sanitized synthetic records.
- Device navigation measurements do not open, edit, save, delete, export, or synchronize user business records.
- No timer fact or formal training record is created by the timer stress test.
- The temporary instrumentation package is removed after device measurement.

## Rollback

Reverting the P8.4 commit restores eager adjacent-page composition, eager idle timer platform initialization, repeated per-day guidance scans, and direct startup synchronization. No database rollback, data conversion, or cleanup is required.
