package io.s2qtech.shenk

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.s2qtech.shenk.model.RoutineScene
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.sync.DailyReview
import io.s2qtech.shenk.sync.DailyReviewState
import io.s2qtech.shenk.timer.TimerEngineState
import io.s2qtech.shenk.timer.RuntimePart
import io.s2qtech.shenk.timer.RuntimeStep
import io.s2qtech.shenk.timer.TimerSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun completedCoachReviewWithShortConclusionRendersWithoutInvalidPadding() {
        val review = DailyReview(
            id = "review-test",
            date = "2026-08-28",
            version = 1,
            status = "completed",
            conclusion = "今天恢复良好。",
            assessment = "训练执行稳定。",
            actions = listOf("保持当前节奏"),
            evidence = listOf("晨起状态完整"),
            cautions = emptyList(),
            localSuggestion = null,
            inputDigest = "test",
            provider = "deepseek",
            model = "deepseek-v4-flash",
            generatedAt = "2026-08-28T12:00:00Z",
        )

        composeRule.activity.setContent {
            ShenkTheme {
                CoachReviewSection(
                    state = DailyReviewState(review = review),
                    onOpen = {},
                )
            }
        }

        composeRule.onNodeWithText("今天恢复良好。").assertIsDisplayed()
        composeRule.onNodeWithText("查看完整简评").assertIsDisplayed()
    }

    @Test
    fun todayOpensOfflineAndMorningWorkspaceIsReachable() {
        composeRule.onNodeWithTag("today-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("cloud-setup-prompt").assertIsDisplayed()
        composeRule.onNodeWithText("身体状态").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("morning-status-values").assertIsDisplayed()
        composeRule.onNodeWithTag("morning-action").performClick()
        composeRule.onNodeWithText("今天身体怎么样？").assertIsDisplayed()
        composeRule.onNodeWithText("保存晨起状态").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fallbackSuggestionWithoutRoutineOpensDirectRecordInsteadOfTimer() {
        composeRule.onNodeWithTag("today-screen").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("today-open-training").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("today-record-day").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("记录今日情况").assertIsDisplayed()
    }

    @Test
    fun calendarIsReachableByGestureAndTodayAnchorAppearsOnlyWhenNeeded() {
        composeRule.onNodeWithTag("primary-pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("calendar-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-agenda").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("calendar-return-today").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("calendar-agenda").performScrollToIndex(0)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("calendar-return-today").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("calendar-return-today").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("calendar-return-today").fetchSemanticsNodes().isEmpty()
        }

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today-screen").assertIsDisplayed()
    }

    @Test
    fun systemBackCollapsesCalendarToToday() {
        composeRule.onNodeWithTag("primary-pager").performTouchInput { swipeRight() }
        composeRule.onNodeWithTag("calendar-screen").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today-screen").assertIsDisplayed()
    }

    @Test
    fun systemBackCollapsesTrainingToToday() {
        composeRule.onNodeWithTag("primary-pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("training-screen").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today-screen").assertIsDisplayed()
    }

    @Test
    fun allPrimaryPagesStayComposedAtBothPagerEdgesAfterWarmup() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("calendar-screen").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("training-screen").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("primary-pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("training-screen").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("calendar-screen").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithTag("primary-pager").performTouchInput { swipeRight() }
        composeRule.onNodeWithTag("primary-pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("calendar-screen").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("training-screen").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun allPrimaryPageSlotsExistWhenStartupGateOpens() {
        assertTrue(composeRule.onAllNodesWithTag("primary-page-slot-0").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag("primary-page-slot-1").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag("primary-page-slot-2").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun trainingSceneSwitcherStaysInLowerThumbZone() {
        composeRule.onNodeWithTag("primary-pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("training-screen").assertIsDisplayed()
        val screenBounds = composeRule.onNodeWithTag("training-screen").getUnclippedBoundsInRoot()
        val dockBounds = composeRule.onNodeWithTag("training-scene-dock").getUnclippedBoundsInRoot()

        assertTrue((dockBounds.top + dockBounds.bottom) > (screenBounds.top + screenBounds.bottom))
        RoutineScene.entries.forEach { scene ->
            composeRule.onNodeWithTag("scene-${scene.name.lowercase()}").assertIsDisplayed()
        }
        val sceneHeights = RoutineScene.entries.map { scene ->
            val bounds = composeRule.onNodeWithTag("scene-${scene.name.lowercase()}")
                .getUnclippedBoundsInRoot()
            bounds.bottom - bounds.top
        }
        sceneHeights.forEach { height ->
            assertEquals(sceneHeights.first().value, height.value, 0.5f)
            assertTrue(height.value >= 48f)
        }
    }

    @Test
    fun todaySecondaryDestinationsUseTheSameLowerThumbZone() {
        val screenBounds = composeRule.onNodeWithTag("today-screen").getUnclippedBoundsInRoot()
        val dockBounds = composeRule.onNodeWithTag("today-destination-bar").getUnclippedBoundsInRoot()

        assertTrue((dockBounds.top + dockBounds.bottom) > (screenBounds.top + screenBounds.bottom))
        val dataBounds = composeRule.onNodeWithTag("today-open-data").getUnclippedBoundsInRoot()
        val planningBounds = composeRule.onNodeWithTag("today-open-planning").getUnclippedBoundsInRoot()
        val dataHeight = dataBounds.bottom - dataBounds.top
        val planningHeight = planningBounds.bottom - planningBounds.top
        assertEquals(dataHeight.value, planningHeight.value, 0.5f)
        assertTrue(dataHeight.value >= 48f)
        assertTrue(planningHeight.value >= 48f)
    }

    @Test
    fun cloudConnectionIsDiscoverableFromToday() {
        composeRule.onNodeWithTag("cloud-setup-prompt").assertIsDisplayed()
        composeRule.onNodeWithTag("cloud-connect-action").performClick()
        composeRule.onNodeWithTag("migration-code-input").assertIsDisplayed()
        composeRule.onNodeWithTag("connect-cloud-data").assertIsDisplayed()
        composeRule.onNodeWithTag("migration-code-input").performTextInput("invalid migration code!")
        composeRule.onNodeWithTag("connect-cloud-data").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("cloud-connection-error").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cloud-connection-error").assertIsDisplayed()
        composeRule.onNodeWithTag("today-screen").assertIsDisplayed()
    }

    @Test
    fun planCollaborationIsReachableFromToday() {
        composeRule.onNodeWithTag("today-destination-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("today-open-planning").assertIsDisplayed().performClick()
        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        val sheetBounds = composeRule.onNodeWithTag("shenk-sheet-content").getUnclippedBoundsInRoot()
        val rootHeight = rootBounds.bottom - rootBounds.top
        val sheetHeight = sheetBounds.bottom - sheetBounds.top
        assertTrue(sheetHeight <= rootHeight * (2f / 3f) + 1.dp)
        composeRule.onNodeWithTag("planning-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("open-plan-feedback").assertIsDisplayed()
        composeRule.onNodeWithTag("open-plan-import").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("plan-patch-input").assertIsDisplayed()
        composeRule.onNodeWithTag("planning-subpage-back").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("open-plan-feedback").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("generate-weekly-feedback").assertIsDisplayed()
    }

    @Test
    fun dataHasOneFixedNativeEntryOnToday() {
        composeRule.onNodeWithTag("today-destination-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("today-open-data").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("data-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("data-metric-weight").assertIsDisplayed()
        composeRule.onNodeWithTag("data-metric-body_fat").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("body-metric-chart").assertIsDisplayed()
        val screenBottom = composeRule.onRoot().getUnclippedBoundsInRoot().bottom
        val chartBottom = composeRule.onNodeWithTag("body-metric-chart").getUnclippedBoundsInRoot().bottom
        assertTrue(chartBottom <= screenBottom)
    }

    @Test
    fun settingsAndPlanningUseTheSameSecondaryActionAnatomy() {
        composeRule.onNodeWithTag("today-open-settings").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("settings-reminders").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-ai").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-backup").assertIsDisplayed()
    }

    @Test
    fun landscapeTimerKeepsUpcomingActionWithTheLeftTaskColumn() {
        val snapshot = TimerSnapshot(
            state = TimerEngineState.RUNNING,
            steps = listOf(
                RuntimeStep(
                    runtimeIndex = 0,
                    logicalIndex = 0,
                    sourceStepId = "current",
                    name = "原地慢走",
                    phase = null,
                    seconds = 120,
                    part = RuntimePart.ACTION,
                    speechText = "原地慢走",
                    cues = listOf("小步轻落地，手臂自然摆。", "身体慢慢热起来，不追出汗。"),
                    warnings = listOf("不要高抬腿。", "小腿不适时更要轻。"),
                    breath = "自然呼吸，能轻松说话。",
                ),
                RuntimeStep(
                    runtimeIndex = 1,
                    logicalIndex = 1,
                    sourceStepId = "next",
                    name = "肩膀绕圈",
                    phase = null,
                    seconds = 50,
                    part = RuntimePart.ACTION,
                    speechText = "肩膀绕圈",
                    cues = emptyList(),
                    warnings = emptyList(),
                    breath = null,
                ),
            ),
            currentStepRemainingMillis = 95_000,
        )
        composeRule.activity.setContent {
            ShenkTheme {
                LandscapeActiveTimerScreen(
                    snapshot = snapshot,
                    progress = 0.2f,
                    step = snapshot.currentStep,
                    nextLogicalStep = snapshot.nextLogicalStep(),
                    voiceNotice = null,
                    onPause = {},
                    onPrevious = {},
                    onNext = {},
                    onStop = {},
                    onFinish = {},
                )
            }
        }
        val hero = composeRule.onNodeWithTag("timer-hero").getUnclippedBoundsInRoot()
        val upcoming = composeRule.onNodeWithTag("timer-next-action").getUnclippedBoundsInRoot()
        val details = composeRule.onNodeWithTag("timer-details").getUnclippedBoundsInRoot()
        assertTrue(upcoming.left < details.left)
        assertTrue(upcoming.right <= details.left)
        assertEquals(hero.left.value, upcoming.left.value, 1f)
    }

    @Test
    fun cloudCoachPatchDoesNotEnterPhaseOneClipboardInbox() {
        val app = composeRule.activity.application as ShenkApplication
        runBlocking {
            app.localFirstRepository.persistAndEnqueue(
                SharedRecord.create(
                    entity = "coach_plan_patches",
                    id = "synthetic-pending-patch",
                    data = buildJsonObject {
                        put("id", JsonPrimitive("synthetic-pending-patch"))
                        put("runId", JsonPrimitive("synthetic-run"))
                        put("status", JsonPrimitive("pending"))
                        put("receivedAt", JsonPrimitive("2100-01-01T00:00:00Z"))
                        put("snapshotDigest", JsonPrimitive("synthetic-digest"))
                        put("patch", Json.parseToJsonElement(validPendingPatch()))
                    },
                    contractVersion = "2.0",
                ),
                SharedEntityOwner.PLANNING_EXCHANGE,
            )
        }

        composeRule.onNodeWithTag("today-open-planning").performClick()
        composeRule.onNodeWithTag("open-plan-import").performClick()
        composeRule.onNodeWithTag("plan-patch-input").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("pending-plan-synthetic-pending-patch")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        assertTrue(runBlocking { app.localFirstRepository.get("daily_plan_items", "synthetic-plan") } == null)
    }

    @Test
    fun cachedRoutineOpensNativePreviewWithoutNetwork() {
        val app = composeRule.activity.application as ShenkApplication
        runBlocking {
            app.localFirstRepository.persistAndEnqueue(
                SharedRecord.create(
                    entity = "routine_templates",
                    id = "synthetic-native-timer",
                    data = buildJsonObject {
                        put("id", JsonPrimitive("synthetic-native-timer"))
                        put("title", JsonPrimitive("离线恢复流程"))
                        put("version", JsonPrimitive("1"))
                        put("trainingType", JsonPrimitive("recovery"))
                        put("scene", JsonPrimitive("recovery"))
                        put("role", JsonPrimitive("recovery"))
                        put("lifecycle", JsonPrimitive("published"))
                        put("timerVisible", JsonPrimitive(true))
                        put("calendarVisible", JsonPrimitive(true))
                        put("countsTowardTraining", JsonPrimitive(true))
                        put("steps", buildJsonArray {
                            add(buildJsonObject {
                                put("stepId", JsonPrimitive("slow-march"))
                                put("name", JsonPrimitive("原地慢走"))
                                put("durationSeconds", JsonPrimitive(60))
                            })
                        })
                    },
                    contractVersion = "2.0",
                ),
                SharedEntityOwner.PLANNING,
            )
        }

        composeRule.onNodeWithTag("primary-pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("training-screen").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("routine-synthetic-native-timer")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val routineActions = composeRule.onNodeWithTag("routine-synthetic-native-timer")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        assertTrue(routineActions.any { it.label == "删除离线恢复流程" })
        val routine = runBlocking {
            app.routineLibraryRepository.observeLibrary().first().routines
                .single { it.id == "synthetic-native-timer" }
        }
        composeRule.runOnIdle {
            app.nativeTimerCoordinator.select(routine)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            app.nativeTimerCoordinator.snapshot.value.state == TimerEngineState.PREVIEW
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timer-start").assertExists()
        composeRule.onNodeWithText("原地慢走").assertExists()
    }

    private fun validPendingPatch() = """
        {
          "schema":"coach_plan_patch",
          "contractVersion":"2.0",
          "effectiveFrom":"2100-01-01",
          "reason":"云端待确认测试",
          "dailyPlanItems":[{
            "id":"synthetic-plan",
            "date":"2100-01-01",
            "title":"普通走",
            "trainingType":"easy_walk",
            "estimatedMinutes":35,
            "status":"planned"
          }]
        }
    """.trimIndent()
}
