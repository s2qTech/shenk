package io.s2qtech.shenk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderSystemStatusInstrumentedTest {
    @Test
    fun xiaomiDeviceReportsPermissionAndPublicSettingsTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val status = readReminderSystemStatus(context)

        assertTrue(status.isXiaomiDevice)
        assertEquals(
            status.notificationPermissionGranted && status.notificationsEnabled,
            status.notificationsAllowed,
        )
        assertNotNull(notificationSettingsIntent(context).resolveActivity(context.packageManager))
        assertNotNull(applicationDetailsIntent(context).resolveActivity(context.packageManager))
    }
}
