package io.s2qtech.shenk

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrentDateTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun returningAfterMidnightUpdatesDateWithoutRecreatingActivity() {
        var date = LocalDate.of(2099, 1, 1)
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle = registry
        }
        composeRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.activity.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                Text(rememberCurrentDate { date }.toString())
            }
        }
        composeRule.onNodeWithText("2099-01-01").assertIsDisplayed()
        composeRule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            date = date.plusDays(1)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.onNodeWithText("2099-01-02").assertIsDisplayed()
    }
}
