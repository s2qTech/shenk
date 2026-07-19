package io.s2qtech.shenk.sync

import io.s2qtech.shenk.model.SharedRecord
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LegacyBodyMetricCompatibilityTest {
    @Test
    fun `v1 body metric remains readable without v2 measurement metadata`() {
        val record = SharedRecord(buildJsonObject {
            put("contractVersion", JsonPrimitive("1.0"))
            put("entity", JsonPrimitive("body_metrics"))
            put("id", JsonPrimitive("legacy-metric"))
            put("revision", JsonPrimitive(2))
            put("baseRevision", JsonPrimitive(2))
            put("updatedAt", JsonPrimitive("2099-01-03T13:00:00Z"))
            put("deletedAt", JsonNull)
            put("data", buildJsonObject {
                put("id", JsonPrimitive("legacy-metric"))
                put("date", JsonPrimitive("2099-01-03"))
                put("weightKg", JsonPrimitive(101.2))
                put("bodyFatPct", JsonPrimitive(28.5))
                put("muscleKg", JsonPrimitive(67.5))
                put("waistCm", JsonPrimitive(105.0))
            })
        })

        val metric = decodeBodyMetric(record)

        assertNotNull(metric)
        assertEquals("2099-01-03T13:00:00Z", metric?.observedAt)
        assertEquals("morning", metric?.context)
        assertEquals("legacy", metric?.source)
        assertEquals(101.2, metric?.weightKg ?: 0.0, 0.0)
        assertEquals(28.5, metric?.bodyFatPct ?: 0.0, 0.0)
        assertEquals(67.5, metric?.muscleKg ?: 0.0, 0.0)
        assertEquals(105.0, metric?.waistCm ?: 0.0, 0.0)
    }

    @Test
    fun `v1 body metric without timestamps uses stable date fallback`() {
        val record = SharedRecord.create(
            entity = "body_metrics",
            id = "legacy-no-time",
            contractVersion = "1.0",
            data = buildJsonObject {
                put("id", JsonPrimitive("legacy-no-time"))
                put("date", JsonPrimitive("2099-01-04"))
                put("weightKg", JsonPrimitive(100.8))
            },
        )

        val metric = decodeBodyMetric(record)

        assertNotNull(metric)
        assertEquals("2099-01-04T00:00:00Z", metric?.observedAt)
        assertEquals("morning", metric?.context)
        assertEquals("legacy", metric?.source)
    }
}
