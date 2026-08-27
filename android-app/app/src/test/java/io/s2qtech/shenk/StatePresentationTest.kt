package io.s2qtech.shenk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatePresentationTest {
    @Test
    fun `manual retry is unavailable while server retry remains authoritative`() {
        assertFalse(dailyReviewAllowsManualRetry("PENDING"))
        assertFalse(dailyReviewAllowsManualRetry("RUNNING"))
        assertFalse(dailyReviewAllowsManualRetry("AWAITING_SERVER"))
        assertFalse(dailyReviewAllowsManualRetry("RETRY"))
        assertTrue(dailyReviewAllowsManualRetry("FAILED"))
    }

    @Test
    fun `conflicts use user-facing entity names instead of internal identifiers`() {
        assertEquals("训练记录", syncConflictEntityLabel("training_logs"))
        assertEquals("身体测量", syncConflictEntityLabel("body_metrics"))
        assertEquals("正式计划", syncConflictEntityLabel("plan_adjustments"))
        assertEquals("同步记录", syncConflictEntityLabel("future_entity"))
    }
}
