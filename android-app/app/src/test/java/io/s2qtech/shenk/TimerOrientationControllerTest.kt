package io.s2qtech.shenk

import android.content.pm.ActivityInfo
import io.s2qtech.shenk.timer.TimerEngineState
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerOrientationControllerTest {
    @Test
    fun `only an executing timer follows the user rotation preference`() {
        listOf(TimerEngineState.RUNNING, TimerEngineState.PAUSED).forEach { state ->
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_USER,
                requestedOrientationForTimerState(state),
            )
        }

        listOf(
            TimerEngineState.IDLE,
            TimerEngineState.PREVIEW,
            TimerEngineState.COMPLETED,
            TimerEngineState.STOPPED,
        ).forEach { state ->
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                requestedOrientationForTimerState(state),
            )
        }
    }
}
