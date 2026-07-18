package io.s2qtech.shenk

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun todayOpensOfflineAndMorningWorkspaceIsReachable() {
        composeRule.onNodeWithText("今天").assertIsDisplayed()
        composeRule.onNodeWithText("晨起状态").assertIsDisplayed()
        composeRule.onNodeWithTag("morning-action").performClick()
        composeRule.onNodeWithText("今天身体怎么样？").assertIsDisplayed()
        composeRule.onNodeWithText("保存晨起状态").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun calendarRecordsAndDataAreReachableWithoutNetwork() {
        composeRule.onNodeWithTag("today-open-calendar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("月历").assertIsDisplayed()
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
}
