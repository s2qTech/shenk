package io.s2qtech.shenk

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationWheelStateTest {
    @Test
    fun hourThenMinuteKeepsBothSelections() {
        val afterHour = durationMinutesWithHour(currentMinutes = 8 * 60, hour = 5, maximumMinutes = 16 * 60)
        val afterMinute = durationMinutesWithMinute(afterHour, minute = 30, maximumMinutes = 16 * 60)

        assertEquals(5 * 60 + 30, afterMinute)
    }

    @Test
    fun minuteThenHourKeepsBothSelections() {
        val afterMinute = durationMinutesWithMinute(currentMinutes = 8 * 60, minute = 30, maximumMinutes = 16 * 60)
        val afterHour = durationMinutesWithHour(afterMinute, hour = 5, maximumMinutes = 16 * 60)

        assertEquals(5 * 60 + 30, afterHour)
    }

    @Test
    fun deepSleepCannotExceedSleepDuration() {
        val changed = durationMinutesWithMinute(
            currentMinutes = 5 * 60,
            minute = 45,
            maximumMinutes = 5 * 60 + 30,
        )

        assertEquals(5 * 60 + 30, changed)
    }

    @Test
    fun wholeHourRemainsStableWhenMinuteIsZero() {
        val changed = durationMinutesWithMinute(currentMinutes = 60, minute = 0, maximumMinutes = 16 * 60)

        assertEquals(60, changed)
    }
}
