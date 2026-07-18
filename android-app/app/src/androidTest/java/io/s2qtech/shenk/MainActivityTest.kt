package io.s2qtech.shenk

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun todayOpensOfflineAndMorningWorkspaceIsReachable() {
        composeRule.onNodeWithTag("today-screen").assertIsDisplayed()
        composeRule.onNodeWithText("晨起状态").assertIsDisplayed()
        composeRule.onNodeWithTag("morning-action").performClick()
        composeRule.onNodeWithText("今天身体怎么样？").assertIsDisplayed()
        composeRule.onNodeWithText("保存晨起状态").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun calendarRecordsAndDataAreReachableWithoutNetwork() {
        composeRule.onNodeWithTag("today-open-calendar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("calendar-screen").assertIsDisplayed()
        composeRule.onNodeWithText("回到今天").assertIsDisplayed()

        composeRule.onNodeWithTag("calendar-open-records").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("正式训练事实").assertIsDisplayed()
        composeRule.onNodeWithTag("space-back").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("calendar-open-data").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("最近 30 天身体变化").assertIsDisplayed()
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

        composeRule.onNodeWithTag("today-open-training").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("routine-synthetic-native-timer")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("routine-synthetic-native-timer").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("timer-start").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("timer-start").assertIsDisplayed()
        composeRule.onNodeWithText("原地慢走").assertIsDisplayed()
    }
}
