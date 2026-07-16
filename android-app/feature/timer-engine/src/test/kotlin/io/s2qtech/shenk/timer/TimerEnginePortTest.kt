package io.s2qtech.shenk.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEnginePortTest {
    @Test
    fun stateVocabularyMatchesAcceptedArchitecture() {
        assertEquals(TimerEngineState.IDLE, TimerEngineState.entries.first())
        assertTrue(TimerEngineState.COMPLETED in TimerEngineState.entries)
        assertTrue(TimerEngineState.STOPPED in TimerEngineState.entries)
    }
}
