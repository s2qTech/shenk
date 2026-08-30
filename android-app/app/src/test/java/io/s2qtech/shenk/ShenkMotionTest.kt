package io.s2qtech.shenk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenkMotionTest {
    @Test
    fun `shared motion tokens stay inside the accepted subtle range`() {
        assertEquals(180, ShenkMotionTokens.QUICK_MILLIS)
        assertEquals(240, ShenkMotionTokens.STANDARD_MILLIS)
        assertEquals(280, ShenkMotionTokens.EMPHASIZED_MILLIS)
        listOf(
            ShenkMotionTokens.QUICK_MILLIS,
            ShenkMotionTokens.STANDARD_MILLIS,
            ShenkMotionTokens.EMPHASIZED_MILLIS,
        ).forEach { duration ->
            assertTrue(duration in 180..280)
        }
    }

    @Test
    fun `pager motion is standard for adjacent pages and emphasized across two pages`() {
        assertEquals(ShenkMotionTokens.STANDARD_MILLIS, shenkPageMotionDuration(0, 1))
        assertEquals(ShenkMotionTokens.STANDARD_MILLIS, shenkPageMotionDuration(2, 1))
        assertEquals(ShenkMotionTokens.EMPHASIZED_MILLIS, shenkPageMotionDuration(0, 2))
        assertEquals(ShenkMotionTokens.EMPHASIZED_MILLIS, shenkPageMotionDuration(2, 0))
    }
}
