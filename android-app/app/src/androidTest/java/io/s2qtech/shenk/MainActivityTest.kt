package io.s2qtech.shenk

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun packageZeroDiagnosticSurfaceOpens() {
        composeRule.onNodeWithText("身刻").assertIsDisplayed()
        composeRule.onNodeWithText("原生 Android 基座").assertIsDisplayed()
        composeRule.onNodeWithText("Package 0 · 仅用于验证原生工程").assertIsDisplayed()
    }
}
