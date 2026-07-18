package io.s2qtech.shenk.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarModelsTest {
    @Test
    fun editWindowIncludesBothFourteenDayBoundaries() {
        val today = LocalDate.of(2026, 7, 18)

        assertTrue(RecordEditPolicy.canEdit(today.minusDays(14), today))
        assertTrue(RecordEditPolicy.canEdit(today.plusDays(14), today))
        assertFalse(RecordEditPolicy.canEdit(today.minusDays(15), today))
        assertFalse(RecordEditPolicy.canEdit(today.plusDays(15), today))
    }

    @Test
    fun trendsUseThirtyDayWindowAndLatestMeasurementPerDay() {
        val today = LocalDate.of(2026, 7, 18)
        val trends = MetricTrendResolver.resolve(
            metrics = listOf(
                metric("old", today.minusDays(30), "08:00", weight = 110.0),
                metric("first", today.minusDays(29), "08:00", weight = 105.0, bodyFat = 30.0),
                metric("same-day-old", today, "07:00", weight = 102.0, bodyFat = 29.0),
                metric("same-day-latest", today, "08:00", weight = 101.5, muscle = 68.0),
            ),
            today = today,
        )

        assertEquals(listOf(105.0, 101.5), trends.weight.points.map(MetricPoint::value))
        assertEquals(-3.5, trends.weight.change ?: 0.0, 0.0)
        assertEquals(68.0, trends.muscle.latest?.value ?: 0.0, 0.0)
        assertNull(trends.waist.latest)
    }

    @Test
    fun missingMetricValuesStayMissing() {
        val today = LocalDate.of(2026, 7, 18)
        val trends = MetricTrendResolver.resolve(
            listOf(metric("weight-only", today, "08:00", weight = 101.0)),
            today,
        )

        assertEquals(1, trends.weight.points.size)
        assertTrue(trends.bodyFat.points.isEmpty())
        assertNull(trends.bodyFat.change)
    }

    private fun metric(
        id: String,
        date: LocalDate,
        time: String,
        weight: Double? = null,
        bodyFat: Double? = null,
        muscle: Double? = null,
    ) = BodyMetric(
        id = id,
        date = date.toString(),
        observedAt = "${date}T${time}:00Z",
        weightKg = weight,
        bodyFatPct = bodyFat,
        muscleKg = muscle,
    )
}
