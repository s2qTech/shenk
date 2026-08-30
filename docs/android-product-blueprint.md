# Android Product Blueprint

Updated: 2026-08-22
Status: confirmed product direction; Packages 0-8 accepted, phase-1 progress `9 / 9`

## 1. Product Definition

Shenk is a personal-first training companion that helps the user find sustainable exercise time inside a fragmented daily life. It is not a generic fitness content library, a calorie tracker, or an autonomous coach that overrides the user.

The product loop is:

```text
morning status and measurements
  -> effective guidance for today
  -> training or rest execution
  -> actual record and daily review
  -> weekly feedback to advanced AI
  -> validated plan update for the next week
```

The Android app is the primary daily product and should eventually cover about 80% of use. The Web app remains a compatible baseline and administration fallback.

## 2. Product Principles

1. Health and sustainable execution come before short-term weight loss speed.
2. The user's actual body response and execution always win over a proposed plan.
3. An advanced AI plan has higher authority than a local fallback suggestion.
4. Missing data is shown as missing. It is never silently converted to rest or normal status.
5. Recording should take less than five minutes and support one-handed operation.
6. Offline use is a core capability, not a degraded afterthought.
7. The interface should feel refined, light, positive, and native to Android rather than like a scaled Web page.
8. Gestures may accelerate common actions, but every important action also needs a discoverable and accessible control.

## 3. Authority Model

| Source | May do | Must not do |
| --- | --- | --- |
| User | Execute, skip, rest, correct records, report discomfort, choose a shorter strength routine | Be forced to follow a plan that conflicts with actual condition |
| Advanced AI planning task | Create weekly strategy, plans, goals, routines, and plan patches from canonical project context | Write directly without validation and confirmation |
| Daily compatible AI | Review the day, warn, explain, and suggest within the latest advanced-AI strategy | Modify formal plans or routines |
| Local fallback engine | Offer generic guidance when a formal plan is unavailable or offline | Pretend to be a formal AI plan |

## 4. Phase 1 Scope

### Included

- Native Android Today experience and whole-month calendar.
- Morning check-in and optional pre-workout delta check-in.
- Weight, body-fat percentage, muscle mass, and waist recording.
- Total sleep duration, deep-sleep duration, sleep quality, fatigue, and regional pain.
- Formal plan inbox through provider-neutral clipboard paste.
- Patch validation, preview, apply, and undo-latest.
- Sunday-to-Saturday formal weekly plan; future dates beyond the plan use clearly labelled local suggestions.
- Native timer with routine selection, preview, voice, keep-screen-on, pause/resume, call interruption handling, music ducking, and post-workout completion.
- Routine library with explicit scene and role, details, active/inactive state, and enable/disable control.
- Formal training and rest records, recent-record correction, and data trends.
- Daily AI review after training or confirmed rest.
- Weekly feedback package generated Saturday evening and copied to the established fitness-planning task.
- Offline-first storage, durable outbox, encrypted configuration migration, and full JSON backup.
- Four configurable reminders: morning, midday missing data, evening unrecorded, and weekly review.

### Phase 2

- Xiaomi Band 9 Pro / Xiaomi Fitness or Health Connect integration where technically available.
- Xiaomi scale import beyond the phase-1 manual flow.
- A polished 2x2 rotating home-screen widget.
- High-quality exercise animation assets linked through `mediaAssetId`.

### Explicitly Excluded From Phase 1

- Diet and calorie recognition.
- Multi-user accounts and public distribution infrastructure.
- Weather-based plan changes.
- A local AI that autonomously edits formal plans.
- Generic social, leaderboard, or gamification features.
- A WebView wrapper of the desktop Web UI.

## 5. Primary Information Architecture

The main Android experience is a continuous horizontal space:

```text
Calendar  <-  Today  ->  Training
```

- **Today** is the default anchor.
- A leftward reveal opens the whole-month calendar and its date stream.
- A rightward reveal opens the native training/timer space.
- Returning to Today while a timer is active shows only a compact “training in progress” state. It does not duplicate timer controls.
- System back, visible navigation controls, and accessibility actions must provide alternatives to gestures.
- Calendar and Training are expanded states of the primary space, not independent exit destinations. System back from either one returns to Today first; only a back action from Today may leave the app.
- High-frequency choices and primary actions belong in the lower thumb-reach zone. The top region is reserved for context, status, and genuinely low-frequency navigation.
- The three-position primary-page indicator is shared by the pager, not duplicated inside each page. Its selected capsule follows the continuous drag offset and occupies the same compact top safety band as page context; it must not create a second status-bar inset or remain visible over timer preview/active states.
- A gesture-only action is never sufficient: every reveal, collapse, and primary command has a visible or accessibility equivalent.

Secondary spaces are reached contextually from the content that owns them:

- Data
- Plan inbox
- Routine library
- Settings and backup

Today exposes Data and Plan inbox through one fixed, native thumb-action dock. The same rounded, inset dock language is used for Training scene choices and Calendar's conditional current-date anchor: no primary canvas adds a full-width separator above its lower controls. The Today dock can accept later AI or recognition capabilities without changing the primary three-canvas model. These destinations must not be duplicated as inline title/subtitle/chevron rows or measurement-level text links inside Today content.

Formal training history is not a separate primary or secondary destination. It is read through the calendar and date details so the same facts are not duplicated into a competing Records page. Today and Training do not repeat Calendar/Today shortcuts in the top-right corner; the primary pager, system back behavior, and pager accessibility semantics own movement between the three canvases.

The final navigation control may be refined during visual prototyping, but a conventional five-tab Web layout must not be treated as the default.

## 6. Today Experience

Today is one adaptable canvas rather than a dashboard of nested cards.

### Morning State

- Missing check-in prompt, or a concise morning summary whose hierarchy is sleep/readiness/fatigue first, body discomfort second, and measurements third.
- Today’s effective plan.
- Measurement delta from yesterday or the recent baseline when useful.
- A small list of genuinely missing information, not a permanent checklist.
- The record/adjust command is the only prominent action in the morning area. Trend, pre-workout delta, and reminder actions remain visually subordinate.
- Body trends use the single fixed Data destination; the morning measurement summary does not add a second inline trend link.
- When measurements exist, they form one intentional third-level summary inside the body-status section: a labelled measurement header plus compact values. They must not fall back to an unframed concatenated text line that looks detached from the status hierarchy.
- The four-part body-status row is authoritative for normal and missing pain state, so Today does not repeat `正常`, `未记录`, or an equivalent sentence beneath it. Actual pain remains visible as a deliberate warning. The optional pre-workout update is a secondary action row attached to the morning-measurement card (or its own compact card when there are no measurements), never a floating text link between sections.
- Body status is one first-class Today card, not an uncontained page fragment. Its heading, record/modify action, four-part scan, actual-pain warning, morning measurements, and pre-workout action share one outer container and use dividers for internal hierarchy; the measurement area must not become a nested card.
- Today uses one action grammar. The day-level primary action is a full-width filled button with a trailing arrow. Every card-local action that opens an editor, detail, generation flow, or retry path uses the same compact tonal button with a trailing arrow. Bare text-plus-arrow links and arrowless pills must not be mixed into these equivalent navigation actions.

### Daytime State

- Effective plan remains visible.
- An optional one-time midday reminder can surface missing information.
- A lightweight status update can record only changed fields such as fatigue or discomfort.

### Pre-workout State

- User may add a delta check-in; omitted fields inherit the morning check-in.
- Plan and relevant safety notes remain visible.
- If the formal plan requires a timer routine, Today can enter Training with the matching routine selected.

### Completed State

- Actual execution replaces plan prominence.
- Daily review shows three fields: `today conclusion`, `key evidence`, and `attention needed`.
- The original plan is available in details but does not compete with the actual record.

### Unrecorded State

- The day remains `unrecorded` until the user confirms training, rest, or skip.
- Late reminders prompt once; they do not silently create a rest record.

## 7. Calendar Experience

The user wants the entire month visible, but not as a cramped desktop seven-column grid.

The Android month surface uses a continuous vertical date stream rather than a seven-column miniature grid:

- A compact date rail on the left and a full-width day summary on the right preserve month context and readable training information.
- The date stream is continuous across month boundaries; it must not compress titles and durations into phone-width mini tiles or require top-edge month paging controls.
- Fast scrolling reports the visible week's distance from the current week in a centered transient HUD.
- A bottom thumb-zone action anchors the current date inside the continuous stream only after the current date has left the viewport. It stays hidden while the current date is already visible. Horizontal swipe or system back returns from Calendar to the Today canvas through the shared-axis transition.
- Every day summary includes a direct, unframed activity icon alongside its title so training type remains recognizable at a glance.
- Actual records, formal plans, and local suggestions have distinct visual languages.
- Agenda summaries are spacious flat rows with a vertical activity cue rather than small rounded cards. Actual execution uses the strongest activity color, formal plans remain clean and structured, and local suggestions use a quiet gray diagonal pattern.
- Flat rows are transparent against the calendar canvas; they must not reintroduce a white card rectangle behind each date.
- When a date has body measurements, the row may show weight, body-fat percentage, muscle mass, and waist as compact inline values. Each field compares with its own previous valid measurement: lower weight/body fat/waist and higher muscle are positive green changes; the opposite direction is red; missing values remain absent.
- The lower edge of the stream uses a quiet visual fade behind the current-date anchor so partially visible rows do not look mechanically clipped.
- Formal plans describe the planned activity rather than an invented clock time or duration. Duration is shown in the stream only when it comes from an actual record.
- Local fallback suggestions omit generic explanatory copy; their lower authority is communicated by the gray patterned treatment and the `建议` source label.
- Today is always easy to return to and can expand through a container transformation rather than a hard page jump.
- The primary horizontal transition uses the pager's native translation without simultaneously scaling and fading whole Calendar/Today/Training surfaces. Pages share one continuous background with zero visual gutter, so swiping never exposes a bright seam in dark mode. The three bounded primary canvases may remain precomposed to make return-to-Today deterministic; frame stability takes priority over ornamental depth effects.
- The default month view prioritizes completion rhythm and upcoming formal plans.
- A day normally displays one effective training item. Multiple source records remain available in date details.

Display priority is:

```text
actual record > effective formal plan > local fallback suggestion
```

Plan adjustments are resolved into the effective formal plan. Adjustment history is not shown as a competing calendar layer.

## 8. Morning and Status Recording

### Measurements

- Weight, body-fat percentage, and muscle mass are primary daily trend values.
- Waist is recordable and shown as a concise change rather than a fourth equal-weight chart on Today.
- Values use wheel/stepper interactions tuned for small daily changes and one-handed input.
- Missing values may be skipped without later forcing completion.

### Sleep and Readiness

- Total sleep duration.
- Deep-sleep duration.
- Subjective sleep quality.
- Fatigue.
- Optional work-pressure prompt when context requires it.

### Pain and Discomfort

Regions:

- neck and shoulder
- wrist
- lower back
- hip and glute
- thigh and knee
- calf and ankle
- other

The flow starts with a fast `no abnormality` action. Fatigue and pain are distinct. Severity is captured only when a region is selected.

## 9. Native Training and Timer

### Entry Behavior

- Strength and recovery plans may preselect the matching routine.
- Walking plans open the walking scene; the user manually chooses warmup or stretch.
- The user may switch routines before starting.
- A cached routine is an offline executable copy, not a replacement for the AI-managed source.
- Scene switching remains anchored in the lower thumb-reach zone while each scene's routine list scrolls vertically and independently. Home, Walk, Recovery, and Travel are also four pages in one horizontal scene pager; tapping the lower label and swiping the content drive the same continuous pager state. At the Home boundary, a continued drag toward Today hands control back to the primary pager, while system back from Training returns directly to Today.
- The scene switcher is an integrated bottom control band separated by a quiet divider, not a detached floating pill or elevated dock.

### Preview

- Show logical exercises, not expanded prepare/switch implementation steps as separate exercises.
- Every exercise can be opened before training to inspect cues and warnings.
- Preparation, side switching, and bilateral execution remain visible as execution details.
- Routine preview is a nested Training state, not another primary page. While it is open, ordinary horizontal movement must not drive the outer Calendar–Today–Training pager. Android edge/system back exits preview to the Training library first; a second back from the library returns to Today. Preview back and Start are aligned in the same lower action surface rather than splitting navigation into a loose top text link and a bottom primary button.

### Active Timer

- Portrait is the app default; the timer supports portrait and landscape without losing state.
- Keep the screen on only while training is active.
- Incoming calls pause the timer.
- Voice cues temporarily duck other audio instead of stopping it.
- No vibration is required.
- Female text-to-speech is preferred when available.
- Every exercise announces its cue together with countdown behavior.
- Current action, remaining time, exercise cues, and warnings are the dominant content.
- The compact status line shows routine name, current logical-action position, and total remaining time without consuming the hero area.
- The current action uses a large tabular countdown plus a thin progress indicator. The following-action strip skips prepare, side-switch, and rest implementation fragments and names the next logical exercise.
- Portrait and landscape use the same hierarchy at different densities. Landscape is a dedicated wide-screen composition: content starts directly below a compact system-icon safety band, the main columns use the full vertical span, and controls move from the bottom edge into a right-side vertical rail. Activity recreation during a running or paused timer must reopen the Training page instead of resetting the primary pager to Today.
- Pause/resume is the dominant control; previous, next, and stop remain visibly available without becoming equal-weight primary actions. In portrait this control group stays in lower-thumb reach, while landscape prioritizes vertical space and places the group on the right edge.
- Do not create a custom lock-screen experience. If Android requires a foreground-service notification, keep it minimal and private.

### Completion

1. Persist the immutable `timer_session` fact first.
2. Open post-workout completion for average heart rate, subjective result, and notes.
3. Confirmation creates or updates the formal `training_log`.
4. Leaving before confirmation keeps the session as `pending completion`.

Walking records may come from wearable data or manual entry and do not require a timer match.

## 10. Daily AI Review

- Runs after a formal workout, confirmed rest, or explicit skip is recorded.
- Covers rest days and may point out avoidable inactivity, but remains factual and professional.
- Missing key status data prompts the user to complete it or explicitly generate from available data.
- Offline requests are queued and generated automatically when connectivity returns.
- Correcting the day regenerates the review; only the latest review is prominent.
- The review never modifies a formal plan.
- Before a review exists, Today presents a compact coach-identity state with one clear generation action. Empty, running, failed, and completed review states keep the same container anatomy so the section does not change from an orphaned text link into an unrelated card after generation.

## 11. Weekly Feedback and Plan Intake

- Saturday at 22:30, generate a copyable feedback package.
- Include 14-day details, 30-day trend summary, current plan and routine versions, unresolved discomfort, skips, short versions, and relevant reasons.
- Copy the package to the clipboard; the user pastes it into the established fitness-planning task.
- Plan patches return through clipboard paste only in phase 1.
- A patch is validated as a whole, previewed, and only then applied.
- Validation failure rejects the whole patch.
- Only the latest applied patch needs one-step undo.

## 12. Visual and Motion Direction

- Tone: a restrained professional coach and dependable companion.
- Character: refined, light, positive, and designed; not clinical, childish, or overly decorative.
- Light and dark themes follow the phone.
- Use a broader accent system than the current Web palette while keeping Shenk brand recognition.
- Avoid nested cards, oversized dashboard typography, constant explanatory copy, and conventional form-heavy screens.
- Use spatial transitions: shared-axis movement between Calendar/Today/Training and container transforms for expanding a date.
- Default motion should be subtle, approximately 180–280 ms, with reduced-motion support.
- Every gesture path must have an accessible semantic action and meet font scaling and contrast requirements.

### Confirmed visual-system follow-up

The current native UI is functionally coherent and intentionally minimal, but its visual expression is still too bare in places. A broad, planned refinement is required rather than isolated decoration work. The follow-up starts from the brand foundation—Shenk logo and adaptive light/dark application icon—and then defines one coherent system for product icons, typography hierarchy, spacing rhythm, card grouping, color roles, elevation, component states, illustration/empty states, and motion.

Before broad screen changes, complete a whole-product UI inventory and establish reusable tokens/components plus representative light/dark prototypes for Today, Calendar/date details, Training/timer, review, planning, data, and settings. Preserve the accepted information architecture, one-handed operation, accessibility, day priority, and domain ownership. Minimalism remains a direction, but must communicate intent, hierarchy, and brand character rather than appearing unfinished or relying on generic text-only layout.

Implementation began on 2026-08-27 after the representative mobile prototype was accepted. The first native slice establishes the shared light/dark color, typography, shape, page-position, card, and lower-thumb-control language on Today, Calendar, and the Training routine library. The Xiaomi 14 gate verified both themes, retained the existing application identity and Room database, and exercised Calendar distance/return controls plus the Training scene dock. This is a reusable-system rollout, not authorization to change business copy, navigation ownership, plan resolution, recording, timer facts, or sync behavior. The timer and remaining secondary surfaces follow in separate, reviewable slices.

The second native slice applies the shared system to routine preview, active timer, and completion while preserving timer/session ownership. Real-device acceptance covered dark and light portrait states plus landscape Activity recreation, retained the original application identity and database, and left no validation session or formal record behind.

The following secondary-surface slices now carry the same accepted prototype language into Today detail, daily review, planning, body trends, settings, and shared non-content states. Today keeps one compact primary plan card and a four-part body-status summary; Data, Plan, Settings, and the full review open as phone-scoped sheets rather than replacing the three-page primary canvas. Planning exposes only its two real jobs, body trends expose one selected metric at a time, settings uses a divided action list, and DeepSeek uses the accepted replaceable whale identity. Xiaomi 14 side-by-side QA against the accepted prototype is recorded in `design-qa.md`. Motion remains a separate final slice.

All phone bottom sheets follow one sizing contract. The sheet wraps its content and keeps only the system safe area plus a small visual breathing space; it must not use a page-specific fixed height or expand merely to occupy the screen. The initial sheet content is capped at two thirds of the available screen height. When content exceeds that cap, the sheet stays at the cap and the content scrolls vertically inside it. The initial state skips Material's partially expanded detent so a short sheet opens at its natural height and a long sheet opens directly at its readable capped height.

The Data sheet is a persistent inspection surface rather than a transient chooser. It does not close from outside taps, small diagonal movement, or horizontal paging. A visible handle communicates its vertical behavior, but a deliberate downward gesture anywhere inside the sheet closes it; the sheet must follow the finger continuously and settle or return through the native bottom-sheet motion instead of disappearing after a threshold. Android system back remains the non-gesture alternative. Its four metric labels and chart pages share one pager state: tapping a label animates to that page, swiping the chart moves the selected indicator continuously, and both paths retain an accessible tab alternative.

## 13. Phase 1 Success Criteria

The first release succeeds when the user can stop opening the Web app for most daily tasks:

1. Complete a morning check-in in under five minutes with one hand.
2. See the correct effective plan offline.
3. Run a complete native timer session without losing state on rotation or a phone call.
4. Save actual training and generate a daily review.
5. See one month of rhythm and one month of body trends.
6. Import and undo a validated weekly plan patch.
7. Produce and copy the weekly feedback package for the fitness-planning task.
8. Reconnect after offline use and synchronize without silent data loss.
