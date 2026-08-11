package io.s2qtech.shenk

import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSettingsTest {
    private val settings = ReminderSettings()

    @Test
    fun openingAfterMidnightDoesNotDeliverStaleDailyReminders() {
        val afterMidnight = LocalDateTime.of(2026, 8, 13, 0, 34)

        assertFalse(isDailyReminderInDeliveryWindow("morning", afterMidnight, settings))
        assertFalse(isDailyReminderInDeliveryWindow("midday", afterMidnight, settings))
        assertFalse(isDailyReminderInDeliveryWindow("evening", afterMidnight, settings))
    }

    @Test
    fun remindersDeliverOnlyInsideTheirSameDayWindow() {
        assertTrue(isDailyReminderInDeliveryWindow("morning", LocalDateTime.of(2026, 8, 13, 8, 45), settings))
        assertTrue(isDailyReminderInDeliveryWindow("midday", LocalDateTime.of(2026, 8, 13, 13, 0), settings))
        assertTrue(isDailyReminderInDeliveryWindow("evening", LocalDateTime.of(2026, 8, 13, 23, 30), settings))
        assertFalse(isDailyReminderInDeliveryWindow("morning", LocalDateTime.of(2026, 8, 13, 11, 45), settings))
    }

    @Test
    fun anExplicitMidnightReminderStillWorks() {
        val midnightMorning = settings.copy(morningHour = 0, morningMinute = 30)

        assertTrue(
            isDailyReminderInDeliveryWindow(
                "morning",
                LocalDateTime.of(2026, 8, 13, 0, 34),
                midnightMorning,
            ),
        )
    }
}
