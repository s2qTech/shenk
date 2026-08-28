# Android visual implementation QA

Date: 2026-08-28

Target: Xiaomi 14, Android 16, 1200 × 2670 px at 480 dpi, font scale 1.0

Source of truth: `outputs/shenk-mobile-ui-prototype/AGENTS.md`, `outputs/shenk-mobile-ui-prototype/src/Prototype.tsx`, and `outputs/shenk-mobile-ui-prototype/src/prototype.css`

## Required fidelity surfaces

- Today: compact plan card, source artwork, four-part body status, coach summary, and lower Data / Plan / Settings dock.
- Data: phone-scoped bottom sheet, four body-metric tabs, current value and one readable trend chart.
- Plan: phone-scoped bottom sheet with exactly the two accepted jobs: import a specified plan and generate recent review material.
- Settings: low-elevation list of real actionable destinations rather than a nested dashboard.
- Daily review: replaceable provider identity, scan-first conclusion, analysis, numbered actions, cautions, and evidence.

## Same-input comparisons

Reference appears on the left and native Android implementation on the right. The prototype uses representative data and iPhone chrome; Android uses the real device's current date and local data, so QA compares component anatomy, hierarchy, spacing, color, typography roles, and visible interaction state rather than literal copy.

| Surface | Comparison |
| --- | --- |
| Today | `outputs/design-audit-2026-08-28/comparison-today.png` |
| Data | `outputs/design-audit-2026-08-28/comparison-data.png` |
| Plan | `outputs/design-audit-2026-08-28/comparison-plan.png` |
| Settings | `outputs/design-audit-2026-08-28/comparison-settings.png` |
| Daily review | `outputs/design-audit-2026-08-28/comparison-review.png` |

## Comparison history

1. The first Today implementation let the decorative plan artwork participate in measurement, which made the card substantially taller than the reference. The artwork now uses `matchParentSize()`, and `native-today-pass2.png` confirms the compact card height.
2. The completed-review state exposed an invalid negative Compose padding that was not reachable in loading/empty screenshots. It was replaced with zero horizontal content padding, a short-conclusion regression case was added, and the real completed review was reopened and scrolled without a new crash.
3. Plan and Settings retain only real Android actions. The prototype's descriptive display row is intentionally absent because Android already follows the system theme and has no separate display destination; a dead row would violate the accepted interaction model.

## Verification

- `:app:testDebugUnitTest`: passed.
- `:app:lintDebug`: passed.
- `:app:compileDebugAndroidTestKotlin`: passed.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed.
- The focused completed-review instrumentation test compiled. HyperOS cancelled installation of the test APK, so the same state was also exercised manually with the existing real completed review.
- Same-certificate `adb install -r` succeeded and preserved the original first-install identity from 2026-08-12.
- Post-fix Activity remained foreground and targeted AndroidRuntime logcat contained no new crash.
- Temporary signing-key read access was removed after each build. No application data was cleared or replaced.

## Final result

passed
