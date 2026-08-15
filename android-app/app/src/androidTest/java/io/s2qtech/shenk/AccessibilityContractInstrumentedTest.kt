package io.s2qtech.shenk

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityContractInstrumentedTest {
    @Test
    fun primaryPagerOffersNamedAccessibilityNavigationActions() {
        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(7_000)
            val root = requireNotNull(
                InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow,
            )
            val labels = root.descendants()
                .flatMap { node -> node.actionList.mapNotNull { action -> action.label?.toString() } }
                .toSet()

            assertTrue("Missing calendar accessibility action: $labels", "转到日历" in labels)
            assertTrue("Missing training accessibility action: $labels", "转到训练" in labels)
        }
    }

    private fun AccessibilityNodeInfo.descendants(): Sequence<AccessibilityNodeInfo> = sequence {
        yield(this@descendants)
        for (index in 0 until childCount) {
            getChild(index)?.let { child -> yieldAll(child.descendants()) }
        }
    }
}
