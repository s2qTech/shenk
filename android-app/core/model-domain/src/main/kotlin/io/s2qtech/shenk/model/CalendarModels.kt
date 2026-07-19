package io.s2qtech.shenk.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class TrainingLog(
    val id: String,
    val date: String,
    val type: String,
    val status: String,
    val source: String,
    val title: String? = null,
    val durationSec: Int? = null,
    val distanceKm: Double? = null,
    val averageHeartRate: Int? = null,
    val perceivedEffort: Int? = null,
    val subjectiveResult: String? = null,
    val notes: String? = null,
    val timerSessionId: String? = null,
    val timerSessionIds: List<String> = emptyList(),
    val calendarVisible: Boolean = true,
    val countsTowardTraining: Boolean = true,
    val updatedAt: String? = null,
) {
    init {
        require(date.isNotBlank())
        durationSec?.let { require(it >= 0) }
        distanceKm?.let { require(it >= 0.0) }
        averageHeartRate?.let { require(it in 0..300) }
        perceivedEffort?.let { require(it in 1..10) }
    }

    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: trainingTypeTitle(type)
    val durationMinutes: Int? get() = durationSec?.div(60)
}

data class CalendarDay(
    val date: LocalDate,
    val guidance: TodayGuidance,
    val actualLogs: List<TrainingLog>,
    val isInMonth: Boolean,
    val bodyMetrics: List<DailyMetric> = emptyList(),
)

data class CalendarMonth(
    val month: YearMonth,
    val weeks: List<List<CalendarDay?>>,
)

enum class MetricKind(val displayName: String, val unit: String) {
    WEIGHT("体重", "kg"),
    BODY_FAT("体脂率", "%"),
    MUSCLE("肌肉量", "kg"),
    WAIST("腰围", "cm"),
}

data class MetricPoint(
    val date: LocalDate,
    val value: Double,
)

enum class MetricChangeDirection {
    INCREASED,
    DECREASED,
    UNCHANGED,
}

data class DailyMetric(
    val kind: MetricKind,
    val value: Double,
    val changeDirection: MetricChangeDirection? = null,
)

data class MetricTrend(
    val kind: MetricKind,
    val points: List<MetricPoint>,
) {
    val latest: MetricPoint? get() = points.lastOrNull()
    val change: Double? get() = if (points.size >= 2) points.last().value - points.first().value else null
}

data class BodyTrends(
    val weight: MetricTrend,
    val bodyFat: MetricTrend,
    val muscle: MetricTrend,
    val waist: MetricTrend,
)

object MetricTrendResolver {
    fun resolve(metrics: List<BodyMetric>, today: LocalDate): BodyTrends {
        val firstDate = today.minusDays(29)
        val inRange = metrics
            .mapNotNull { metric -> runCatching { LocalDate.parse(metric.date) to metric }.getOrNull() }
            .filter { (date) -> date in firstDate..today }

        fun trend(kind: MetricKind, value: (BodyMetric) -> Double?) = MetricTrend(
            kind = kind,
            points = inRange
                .mapNotNull { (date, metric) -> value(metric)?.let { Triple(date, metric.observedAt, it) } }
                .groupBy { it.first }
                .map { (date, values) -> MetricPoint(date, values.maxBy { it.second }.third) }
                .sortedBy(MetricPoint::date),
        )

        return BodyTrends(
            weight = trend(MetricKind.WEIGHT, BodyMetric::weightKg),
            bodyFat = trend(MetricKind.BODY_FAT, BodyMetric::bodyFatPct),
            muscle = trend(MetricKind.MUSCLE, BodyMetric::muscleKg),
            waist = trend(MetricKind.WAIST, BodyMetric::waistCm),
        )
    }
}

object DailyMetricResolver {
    fun resolve(metrics: List<BodyMetric>): Map<LocalDate, List<DailyMetric>> {
        val dated = metrics.mapNotNull { metric ->
            runCatching { LocalDate.parse(metric.date) to metric }.getOrNull()
        }
        val result = mutableMapOf<LocalDate, MutableList<DailyMetric>>()

        fun add(kind: MetricKind, value: (BodyMetric) -> Double?) {
            var previous: Double? = null
            dated
                .mapNotNull { (date, metric) -> value(metric)?.let { Triple(date, metric.observedAt, it) } }
                .groupBy { it.first }
                .map { (date, values) -> date to values.maxBy { it.second }.third }
                .sortedBy { it.first }
                .forEach { (date, current) ->
                    val direction = previous?.let { prior ->
                        when {
                            current > prior -> MetricChangeDirection.INCREASED
                            current < prior -> MetricChangeDirection.DECREASED
                            else -> MetricChangeDirection.UNCHANGED
                        }
                    }
                    result.getOrPut(date) { mutableListOf() }
                        .add(DailyMetric(kind = kind, value = current, changeDirection = direction))
                    previous = current
                }
        }

        add(MetricKind.WEIGHT, BodyMetric::weightKg)
        add(MetricKind.BODY_FAT, BodyMetric::bodyFatPct)
        add(MetricKind.MUSCLE, BodyMetric::muscleKg)
        add(MetricKind.WAIST, BodyMetric::waistCm)
        return result
    }
}

object RecordEditPolicy {
    const val EDIT_WINDOW_DAYS = 14L

    fun canEdit(recordDate: LocalDate, today: LocalDate): Boolean =
        kotlin.math.abs(ChronoUnit.DAYS.between(today, recordDate)) <= EDIT_WINDOW_DAYS
}

object DefaultSuggestionResolver {
    fun resolve(date: LocalDate): TodayGuidance = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> suggestion("普通走", "easy_walk", 35)
        DayOfWeek.TUESDAY -> suggestion("力量训练", "strength", 45)
        DayOfWeek.WEDNESDAY -> suggestion("普通走", "easy_walk", 35)
        DayOfWeek.THURSDAY -> suggestion("提高走", "quality_walk", 45)
        DayOfWeek.FRIDAY -> suggestion("普通走", "easy_walk", 35)
        DayOfWeek.SATURDAY -> suggestion("力量训练", "strength", 45)
        DayOfWeek.SUNDAY -> suggestion("恢复活动", "recovery", 15)
    }

    private fun suggestion(title: String, type: String, minutes: Int) = TodayGuidance(
        source = GuidanceSource.LOCAL_SUGGESTION,
        title = title,
        trainingType = type,
        estimatedMinutes = minutes,
        note = "当前没有正式计划，这是离线兜底建议。",
    )
}

fun trainingTypeTitle(type: String?): String = when (type) {
    "strength", "travel_strength" -> "力量训练"
    "quality_walk" -> "提高走"
    "easy_walk" -> "普通走"
    "indoor_cardio" -> "室内有氧"
    "warmup" -> "热身"
    "cooldown" -> "冷身"
    "recovery" -> "恢复活动"
    "seat_recovery" -> "座位活动"
    "stretch" -> "拉伸"
    "rest" -> "休息"
    else -> "训练记录"
}
