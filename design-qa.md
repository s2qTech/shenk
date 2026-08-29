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

## 2026-08-29 structural follow-up

The next real-device review found four cross-surface layout problems rather than isolated decoration defects:

1. Landscape timer guidance and the upcoming action shared the narrow right column while the left task column ended early. The upcoming-action strip now stays with the current-action hero on the left; the right column is reserved for cues and warnings.
2. Today used a four-part scan row after a morning record but replaced it with an unrelated explanatory card before recording. Both states now use the same four-part anatomy and preserve explicit `未记录` values.
3. Plan used bordered action cards while Settings used transparent divided rows. Both now reuse `SecondaryActionRow`, including the same icon container, type hierarchy, outline, radius, and direction affordance.
4. Data combined a partially expanded sheet with a forced `82%` content height. The sheet now skips the partial state and its scrollable content wraps to the chart instead of filling a fixed tall region.

Automated verification passed `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:compileDebugAndroidTestKotlin`. The focused Compose checks cover the Today status skeleton, Settings action destinations, Data chart visibility on first open, and the landscape timer's left/right information grouping. Xiaomi 14 same-certificate installation preserved the original 2026-08-12 first-install identity. Real-device comparison confirmed the shared Today skeleton, matching Plan/Settings action-card anatomy, and a fully visible Data chart on first open without dragging or the previous blank tail. The device's rotation setting remained unchanged and no user business record was created, edited, or deleted. The newly packaged isolated instrumentation APK could not execute against the installed private build because the sandbox could not read the private signing certificate; the test source compiled, while the production surfaces were verified manually on the connected device.
