package io.s2qtech.shenk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSystemStatusTest {
    @Test
    fun notificationDeliveryRequiresPermissionAndSystemNotifications() {
        val base = ReminderSystemStatus(
            notificationPermissionGranted = true,
            notificationsEnabled = true,
            batteryOptimizationExempt = false,
            isXiaomiDevice = true,
        )

        assertTrue(base.notificationsAllowed)
        assertFalse(base.copy(notificationPermissionGranted = false).notificationsAllowed)
        assertFalse(base.copy(notificationsEnabled = false).notificationsAllowed)
    }

    @Test
    fun recognizesXiaomiHyperOsBrandsWithoutCaseSensitivity() {
        assertTrue(isXiaomiDevice("Xiaomi", "Xiaomi"))
        assertTrue(isXiaomiDevice("unknown", "REDMI"))
        assertTrue(isXiaomiDevice(null, "poco"))
    }

    @Test
    fun doesNotShowHyperOsGuidanceOnOtherBrands() {
        assertFalse(isXiaomiDevice("Google", "google"))
        assertFalse(isXiaomiDevice("Samsung", null))
    }
}
