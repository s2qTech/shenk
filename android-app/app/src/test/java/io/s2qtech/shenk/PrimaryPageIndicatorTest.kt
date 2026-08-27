package io.s2qtech.shenk

import org.junit.Assert.assertEquals
import org.junit.Test

class PrimaryPageIndicatorTest {
    @Test
    fun `indicator follows the pager continuously and clamps at both ends`() {
        assertEquals(0f, primaryPageIndicatorTravelDp(-0.4f), 0.001f)
        assertEquals(7.5f, primaryPageIndicatorTravelDp(0.5f), 0.001f)
        assertEquals(15f, primaryPageIndicatorTravelDp(1f), 0.001f)
        assertEquals(22.5f, primaryPageIndicatorTravelDp(1.5f), 0.001f)
        assertEquals(30f, primaryPageIndicatorTravelDp(2.4f), 0.001f)
    }
}
