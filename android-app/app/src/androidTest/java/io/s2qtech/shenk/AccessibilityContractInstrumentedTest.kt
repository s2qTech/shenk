package io.s2qtech.shenk

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityContractInstrumentedTest {
    @Test
    fun primaryPagerOffersNamedAccessibilityNavigationActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n io.s2qtech.shenk/.MainActivity",
        ).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText()
        }
        val root = waitForAppRoot(instrumentation)
        val labels = root.descendants()
            .flatMap { node -> node.actionList.mapNotNull { action -> action.label?.toString() } }
            .toSet()

        assertTrue("Missing calendar accessibility action: $labels", "转到日历" in labels)
        assertTrue("Missing training accessibility action: $labels", "转到训练" in labels)

        performPagerAction(instrumentation, "转到日历")
        waitForPagerState(instrumentation, "日历，第 1 页，共 3 页")
        performPagerAction(instrumentation, "转到今天")
        waitForPagerState(instrumentation, "今天，第 2 页，共 3 页")

        performPagerAction(instrumentation, "转到训练")
        waitForPagerState(instrumentation, "训练，第 3 页，共 3 页")
        performPagerAction(instrumentation, "转到今天")
        waitForPagerState(instrumentation, "今天，第 2 页，共 3 页")

        clickNodeWithText(instrumentation, "设置")
        waitForText(instrumentation, "数据备份")
        clickNodeWithText(instrumentation, "数据备份")
        waitForText(instrumentation, "导出 JSON 备份")
        waitForText(instrumentation, "从 JSON 安全恢复")
    }

    private fun waitForAppRoot(instrumentation: android.app.Instrumentation): AccessibilityNodeInfo {
        repeat(100) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root?.packageName?.toString() == "io.s2qtech.shenk") return root
            Thread.sleep(100)
        }
        error("Shenk did not become the active accessibility window")
    }

    private fun performPagerAction(instrumentation: android.app.Instrumentation, label: String) {
        val root = waitForAppRoot(instrumentation)
        val pager = root.descendants().firstOrNull { node ->
            node.actionList.any { action -> action.label?.toString() == label }
        } ?: error("Pager action was not available: $label")
        val action = pager.actionList.single { it.label?.toString() == label }
        assertTrue("Pager action failed: $label", pager.performAction(action.id))
    }

    private fun waitForPagerState(
        instrumentation: android.app.Instrumentation,
        expected: String,
    ) {
        repeat(100) {
            val root = waitForAppRoot(instrumentation)
            if (root.descendants().any { it.stateDescription?.toString() == expected }) return
            Thread.sleep(50)
        }
        error("Pager did not reach accessibility state: $expected")
    }

    private fun clickNodeWithText(instrumentation: android.app.Instrumentation, text: String) {
        repeat(100) {
            val textNode = waitForAppRoot(instrumentation).descendants()
                .firstOrNull { it.text?.toString() == text }
            var clickable = textNode
            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
            if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            Thread.sleep(50)
        }
        error("Clickable text was not available: $text")
    }

    private fun waitForText(instrumentation: android.app.Instrumentation, text: String) {
        repeat(100) {
            if (waitForAppRoot(instrumentation).descendants().any { it.text?.toString() == text }) return
            Thread.sleep(50)
        }
        error("Text was not available: $text")
    }

    private fun AccessibilityNodeInfo.descendants(): Sequence<AccessibilityNodeInfo> = sequence {
        yield(this@descendants)
        for (index in 0 until childCount) {
            getChild(index)?.let { child -> yieldAll(child.descendants()) }
        }
    }
}
