package io.s2qtech.shenk

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanningPresentationTest {
    @Test
    fun `formats stored import timestamp for the local planning surface`() {
        assertEquals(
            "8月22日 00:09",
            formatPlanAppliedAt("2026-08-21T16:09:59.150166Z", ZoneId.of("Asia/Shanghai")),
        )
    }

    @Test
    fun `preserves unknown legacy timestamp text`() {
        assertEquals("较早版本", formatPlanAppliedAt("较早版本", ZoneId.of("Asia/Shanghai")))
    }
}
