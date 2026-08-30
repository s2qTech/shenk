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

## 2026-08-30 planning subpage crash correction

Connected-device reproduction showed that both Plan actions terminated `MainActivity` before their content could render. The crash buffer identified `java.lang.IllegalArgumentException: Padding must be non-negative` at `PlanningSubpageHeader` in `PlanningScreen.kt`. The shared back action used `Modifier.padding(start = (-12).dp)`, so both Import and Recent Review failed through the same header. The negative modifier was replaced with legal zero horizontal `contentPadding`, and the existing Plan reachability test now renders Import, returns through the shared header, and renders Recent Review in sequence. Unit tests, Lint, Android-test compilation, and a complete credential-free debug APK assembly passed.

The signed follow-up APK matched the installed certificate and updated Xiaomi 14 through `adb install -r` while preserving the original 2026-08-12 first-install identity. Import and Recent Review then opened in sequence without an AndroidRuntime crash. Their stable initial content now raises the Plan sheet only to a content-fitting intermediate height instead of either clipping the final controls or expanding into a mostly empty pseudo-full-screen surface; larger validation previews remain scrollable. All initial controls on both subpages were visible without dragging, and the review generation action was not invoked during verification, so no business record was created or changed. The pulled verification APK and temporary signing copy were removed.

## 2026-08-30 unified bottom-sheet sizing

The Plan-specific height correction was generalized into one `ShenkModalBottomSheet` contract used by every phone bottom sheet. Short content now opens at its natural measured height with only the handle, safe area, and modest breathing space. Content taller than two thirds of the available screen opens directly at that cap and scrolls inside the sheet; Material's intermediate partial detent and page-specific height fractions are no longer used. Data, Plan, Settings, Calendar details, check-in editors, daily review, cloud/AI/backup/conflict tools, and the post-workout editor all share the same host.

Xiaomi 14 verification covered a short Plan chooser, the capped and scrollable Plan import workspace, Settings, and the capped Data trend surface. The Data title, metric selector, current value, and complete chart were visible immediately without manually lifting the sheet or exposing the previous blank tail. The installed package remained foreground, the crash buffer stayed empty, and no import, review generation, conflict resolution, or record save was invoked. The shared Compose regression asserts that a sheet never exceeds two thirds of the root height.

## 2026-08-30 Today hierarchy and Data interaction correction

The recorded Today state now gives morning measurements the same intentional hierarchy as body status: one compact outlined summary card contains the measurement identity, record count, and a two-column scan of the four values. Coach review uses one stable card anatomy for empty, running, failed, and complete states, including the replaceable provider identity, an explicit state label, supporting copy, and the state-appropriate action. No review job, measurement, or guidance semantics changed.

Data remains a capped phone-scoped sheet, but its dismissal and metric navigation are now separated. A tap on the scrim and incidental horizontal movement cannot dismiss it. A high-contrast horizontal handle stays visible as the affordance, while a deliberate downward gesture anywhere inside the sheet uses the native sheet drag so the complete surface follows the finger before settling or closing; Android system back is the non-gesture alternative. Weight, body fat, muscle, and waist are pages in one horizontal pager; direct tab selection and chart swipes share the same pager state, while the selected capsule follows the continuous page offset.

Automated verification passed JVM unit tests, Lint, debug Android-test compilation, and signed debug assembly. The same-certificate APK updated Xiaomi 14 without clearing application data. Real-device verification confirmed the integrated morning-measurement and empty-review cards, complete first-open chart visibility, the restored visible handle, ignored scrim taps, ignored short horizontal movement, linked weight/body-fat/muscle swipe transitions, continuous finger-following downward motion from sheet content, release-time dismissal, system-back dismissal, a live application process, and an empty Android crash buffer. No review was generated and no business data was saved or modified during verification.
