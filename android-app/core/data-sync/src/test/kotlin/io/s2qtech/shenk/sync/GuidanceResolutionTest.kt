package io.s2qtech.shenk.sync

import io.s2qtech.shenk.model.GuidanceSource
import io.s2qtech.shenk.model.SharedRecord
import java.time.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidanceResolutionTest {
    @Test
    fun structuredLegacyFieldsRemainMissingInsteadOfCrashingCalendar() {
        val malformedDate = buildJsonArray { add(JsonPrimitive("2026-07-18")) }
        val malformedRecords = listOf(
            SharedRecord.create(
                entity = "training_logs",
                id = "legacy-log",
                data = buildJsonObject {
                    put("date", malformedDate)
                    put("type", JsonPrimitive("strength"))
                },
            ),
            SharedRecord.create(
                entity = "daily_plan_items",
                id = "legacy-plan",
                data = buildJsonObject {
                    put("date", malformedDate)
                    put("trainingType", JsonPrimitive("strength"))
                },
            ),
            SharedRecord.create(
                entity = "plan_adjustments",
                id = "legacy-adjustment",
                data = buildJsonObject {
                    put("date", malformedDate)
                    put("trainingType", JsonPrimitive("recovery"))
                },
            ),
        )

        val resolved = GuidanceResolution.resolve(
            date = LocalDate.of(2026, 7, 18),
            logs = listOf(malformedRecords[0]),
            plans = listOf(malformedRecords[1]),
            adjustments = listOf(malformedRecords[2]),
        )

        assertEquals(GuidanceSource.LOCAL_SUGGESTION, resolved.guidance.source)
        assertEquals(emptyList<Any>(), resolved.actualLogs)
        assertNull(buildJsonObject { put("value", malformedDate) }.fieldString("value"))
        assertNull(buildJsonObject { put("value", malformedDate) }.fieldInt("value"))
        assertNull(buildJsonObject { put("value", malformedDate) }.fieldDouble("value"))
        assertNull(buildJsonObject { put("value", malformedDate) }.fieldBoolean("value"))
    }
}
