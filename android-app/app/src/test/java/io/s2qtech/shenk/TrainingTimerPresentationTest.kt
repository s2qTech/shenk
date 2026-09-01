package io.s2qtech.shenk

import io.s2qtech.shenk.timer.RuntimePart
import io.s2qtech.shenk.timer.RuntimeStep
import io.s2qtech.shenk.timer.TimerEngineState
import io.s2qtech.shenk.timer.TimerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingTimerPresentationTest {
    @Test
    fun `upcoming action skips execution details from the current logical action`() {
        val snapshot = TimerSnapshot(
            state = TimerEngineState.RUNNING,
            steps = listOf(
                step(runtimeIndex = 0, logicalIndex = 0, name = "准备：深蹲", part = RuntimePart.PREPARE, seconds = 10),
                step(runtimeIndex = 1, logicalIndex = 0, name = "左侧：深蹲", part = RuntimePart.SIDE, seconds = 20),
                step(runtimeIndex = 2, logicalIndex = 0, name = "换侧", part = RuntimePart.SWITCH, seconds = 5),
                step(runtimeIndex = 3, logicalIndex = 1, name = "臀桥", seconds = 40),
            ),
            currentStepIndex = 0,
            currentStepRemainingMillis = 9_000,
        )

        assertEquals("臀桥", snapshot.nextLogicalStep()?.name)
    }

    @Test
    fun `topline remaining time includes the current and all future runtime steps`() {
        val snapshot = TimerSnapshot(
            state = TimerEngineState.RUNNING,
            steps = listOf(
                step(runtimeIndex = 0, logicalIndex = 0, name = "动作一", seconds = 10),
                step(runtimeIndex = 1, logicalIndex = 1, name = "动作二", seconds = 20),
                step(runtimeIndex = 2, logicalIndex = 2, name = "动作三", seconds = 30),
            ),
            currentStepIndex = 0,
            currentStepRemainingMillis = 8_001,
        )

        assertEquals(59, snapshot.totalRemainingSeconds())
    }

    @Test
    fun `activity recreation returns to training whenever a timer flow is open`() {
        assertEquals(1, initialPrimaryPageForTimerState(TimerEngineState.IDLE))
        assertEquals(2, initialPrimaryPageForTimerState(TimerEngineState.PREVIEW))
        assertEquals(2, initialPrimaryPageForTimerState(TimerEngineState.RUNNING))
        assertEquals(2, initialPrimaryPageForTimerState(TimerEngineState.PAUSED))
        assertEquals(2, initialPrimaryPageForTimerState(TimerEngineState.COMPLETED))
        assertEquals(2, initialPrimaryPageForTimerState(TimerEngineState.STOPPED))
    }

    @Test
    fun `process recovered active timer is revealed after asynchronous restore`() {
        assertTrue(
            shouldRevealRecoveredTimer(
                TimerSnapshot(
                    state = TimerEngineState.PAUSED,
                    interruptionReason = "recovered_after_process_death",
                ),
            ),
        )
        assertFalse(
            shouldRevealRecoveredTimer(
                TimerSnapshot(
                    state = TimerEngineState.PAUSED,
                    interruptionReason = "phone_call",
                ),
            ),
        )
        assertFalse(
            shouldRevealRecoveredTimer(
                TimerSnapshot(
                    state = TimerEngineState.IDLE,
                    interruptionReason = "recovered_after_process_death",
                ),
            ),
        )
    }

    private fun step(
        runtimeIndex: Int,
        logicalIndex: Int,
        name: String,
        seconds: Int,
        part: RuntimePart = RuntimePart.ACTION,
    ) = RuntimeStep(
        runtimeIndex = runtimeIndex,
        logicalIndex = logicalIndex,
        sourceStepId = "step-$logicalIndex",
        name = name,
        phase = null,
        seconds = seconds,
        part = part,
        speechText = name,
        cues = emptyList(),
        warnings = emptyList(),
        breath = null,
    )
}
