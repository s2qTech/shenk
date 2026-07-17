package io.s2qtech.shenk

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        composeRule.onNodeWithText("记录").performClick()
        composeRule.onNodeWithText("今天身体怎么样？").assertIsDisplayed()
        composeRule.onNodeWithText("保存晨起状态").performScrollTo().assertIsDisplayed()
    }
}
