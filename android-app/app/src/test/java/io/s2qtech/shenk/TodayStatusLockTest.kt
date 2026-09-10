package io.s2qtech.shenk

import io.s2qtech.shenk.model.GuidanceSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayStatusLockTest {
    @Test
    fun confirmedActualRecordLocksStatusEditing() {
        assertTrue(isTodayStatusLocked(GuidanceSource.ACTUAL))
    }

    @Test
    fun unconfirmedDayKeepsStatusEditingAvailable() {
        assertFalse(isTodayStatusLocked(null))
        assertFalse(isTodayStatusLocked(GuidanceSource.FORMAL_PLAN))
        assertFalse(isTodayStatusLocked(GuidanceSource.LOCAL_SUGGESTION))
    }
}
