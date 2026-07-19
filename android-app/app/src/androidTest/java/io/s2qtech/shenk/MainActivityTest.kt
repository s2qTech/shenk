package io.s2qtech.shenk

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.s2qtech.shenk.model.RoutineScene
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.timer.TimerEngineState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun todayOpensOfflineAndMorningWorkspaceIsReachable() {
        composeRule.onNodeWithTag("today-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("cloud-setup-prompt").assertIsDisplayed()
        composeRule.onNodeWithText("晨起状态").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("morning-action").performClick()
        composeRule.onNodeWithText("今天身体怎么样？").assertIsDisplayed()
        composeRule.onNodeWithText("保存晨起状态").performScrollTo().assertIsDisplayed()
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
    fun systemBackCollapsesCalendarAndTrainingToToday() {
        composeRule.onNodeWithTag("primary-pager").performTouchInput { swipeRight() }
        composeRule.onNodeWithTag("calendar-screen").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today-screen").assertIsDisplayed()

        composeRule.onNodeWithTag("today-open-training").performClick()
        composeRule.onNodeWithTag("training-screen").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today-screen").assertIsDisplayed()
    }

    @Test
    fun trainingSceneSwitcherStaysInLowerThumbZone() {
        composeRule.onNodeWithTag("today-open-training").performClick()
        composeRule.onNodeWithTag("training-screen").assertIsDisplayed()
        val screenBounds = composeRule.onNodeWithTag("training-screen").getUnclippedBoundsInRoot()
        val dockBounds = composeRule.onNodeWithTag("training-scene-dock").getUnclippedBoundsInRoot()

        assertTrue((dockBounds.top + dockBounds.bottom) > (screenBounds.top + screenBounds.bottom))
        RoutineScene.entries.forEach { scene ->
            composeRule.onNodeWithTag("scene-${scene.name.lowercase()}").assertIsDisplayed()
        }
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
}
