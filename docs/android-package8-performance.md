# Android Package 8 Performance and Stability Gate

Updated: 2026-08-13
Status: P8.4 accepted on Xiaomi 14

## Scope

P8.4 covers Android startup, primary-page navigation, calendar scrolling, timer execution, and local database/sync work. It does not change shared contracts, business entities, ownership, user confirmation, or synchronization semantics.

## Implemented Corrections

- AndroidX SplashScreen 1.2.0 now holds the system launch surface until Calendar, Today, and Training have emitted their first local data and each page has been pre-drawn once. The splash then fades out over 220 ms. First composition no longer competes with a user gesture.
- The pager retains the complete three-page set even while Calendar or Training is selected. Before the splash exits it performs hidden, non-animated Calendar → Training → Today placement to prime first-page drawing; later gestures never rebuild or first-draw an edge page.
- The startup gate is scoped to each Activity instance, has a five-second fail-open ceiling, and releases immediately for planning/feedback deep links. It is a bounded readiness gate, not an artificial fixed delay.
- Timer checkpoint recovery starts independently after the first app frame and resumes the timer ticker when required. It no longer depends on Training page composition timing.
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
| Fully prepared time-to-interactive after gesture correction, 10 cold runs | median 2,243 ms to Calendar direction; 2,406 ms to Training direction |
| First Calendar gesture after splash, 5 cold runs | median p95 38 ms; maximum p95 129 ms |
| First Training gesture after splash, 5 cold runs | median p95 61 ms; maximum p95 101 ms |
| Continuous bidirectional pager stress, 40 transitions | 1,770 frames; 1.41% janky; p95 18 ms; p99 29 ms |
| Four primary-page transitions after idle warm-up | 514 frames; 1.17% janky; p95 19 ms |
| Calendar scroll, 12 gestures | 1,152 frames; 0.09% janky; p95 25 ms |
| Training to Today, 10 isolated runs after retention correction | median per-run p95 12 ms; maximum p95 16 ms |
| Calendar to Today, 10 isolated runs after retention correction | median per-run p95 13 ms; maximum p95 20 ms |
| Three-page retained debug-process memory after final cold start | total PSS 214,924 KB; total RSS 361,584 KB |
| 400-day / 560-record Room projection | 123 ms |
| 100-operation local outbox sync | 716 ms |
| One virtual timer hour / 14,400 ticks | 40 ms on the JVM gate |

Results can vary with thermal state, refresh rate, background load, and debug runtime; regression comparisons must use the same device, build type, and command sequence. The navigation and scrolling frame windows are measured separately from the immediate post-launch `gfxinfo` snapshot so the sample sizes are explicit.

This interaction correction deliberately trades the earlier approximately 505 ms first Today frame for an approximately 2.1-2.4 second fully prepared debug experience behind the branded system splash. During a focused post-splash first-gesture run, a cleared `Choreographer` log reported no skipped-frame burst; the 30-78 frame startup stalls were observed only while the splash still covered the application. P8.8 must repeat this gate on the signed release candidate, where debug runtime and class-loading overhead do not apply.

The edge-retention and startup-slot instrumentation tests compiled as part of the Package 8 gate. HyperOS again rejected installation of the temporary app instrumentation APK with `INSTALL_FAILED_USER_RESTRICTED`, so those focused device tests are not reported as executed. The production APK installed successfully, and all launch, memory, Choreographer, and gesture measurements above ran against that installed correction.

## Stability and Data Safety

- Performance instrumentation uses only an in-memory Room database and sanitized synthetic records.
- Device navigation measurements do not open, edit, save, delete, export, or synchronize user business records.
- No timer fact or formal training record is created by the timer stress test.
- The temporary instrumentation package is removed after device measurement.

## Rollback

Reverting the P8.4 commits removes the SplashScreen readiness gate and hidden pager pre-draw, restores edge-page disposal while the opposite edge is selected, eager idle timer platform initialization, repeated per-day guidance scans, and direct startup synchronization. No database rollback, data conversion, or cleanup is required.
