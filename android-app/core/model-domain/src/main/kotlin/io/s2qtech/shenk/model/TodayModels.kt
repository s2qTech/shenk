package io.s2qtech.shenk.model

enum class CheckinKind(val wireValue: String) {
    MORNING("morning"),
    PRE_WORKOUT("pre_workout"),
}

enum class PainRegion(val wireValue: String, val displayName: String) {
    NECK_SHOULDER("neck_shoulder", "颈肩"),
    WRIST("wrist", "手腕"),
    LOWER_BACK("lower_back", "腰背"),
    HIP_GLUTE("hip_glute", "髋臀"),
    THIGH_KNEE("thigh_knee", "大腿与膝"),
    CALF_ANKLE("calf_ankle", "小腿与踝"),
    OTHER("other", "其他"),
}

enum class PainSide(val wireValue: String, val displayName: String) {
    LEFT("left", "左侧"),
    RIGHT("right", "右侧"),
    BILATERAL("bilateral", "双侧"),
    UNSPECIFIED("unspecified", "未区分"),
}

data class PainEntry(
    val region: PainRegion,
    val severity: Int,
    val side: PainSide = PainSide.UNSPECIFIED,
) {
    init {
        require(severity in 0..5) { "pain severity must be between 0 and 5" }
    }
}

data class StatusCheckin(
    val id: String,
    val date: String,
    val kind: CheckinKind,
    val observedAt: String,
    val baseCheckinId: String? = null,
    val sleepDurationMinutes: Int? = null,
    val deepSleepMinutes: Int? = null,
    val sleepQuality: Int? = null,
    val energy: Int? = null,
    val fatigue: Int? = null,
    val workPressure: Int? = null,
    val pain: List<PainEntry>? = null,
    val note: String? = null,
) {
    init {
        sleepDurationMinutes?.let { require(it in 0..1440) }
        deepSleepMinutes?.let { require(it in 0..1440) }
        sleepQuality?.let { require(it in 1..5) }
        energy?.let { require(it in 1..5) }
        fatigue?.let { require(it in 0..5) }
        workPressure?.let { require(it in 0..5) }
        require(deepSleepMinutes == null || sleepDurationMinutes == null || deepSleepMinutes <= sleepDurationMinutes) {
            "deep sleep cannot exceed total sleep"
        }
    }

    val hasSubjectiveData: Boolean
        get() = sleepDurationMinutes != null ||
            deepSleepMinutes != null ||
            sleepQuality != null ||
            energy != null ||
            fatigue != null ||
            workPressure != null ||
            pain != null ||
            !note.isNullOrBlank()
}

data class BodyMetric(
    val id: String,
    val date: String,
    val observedAt: String,
    val context: String = "morning",
    val source: String = "manual",
    val sourceRecordId: String? = null,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val muscleKg: Double? = null,
    val waistCm: Double? = null,
) {
    init {
        weightKg?.let { require(it in 0.0..500.0) }
        bodyFatPct?.let { require(it in 0.0..100.0) }
        muscleKg?.let { require(it in 0.0..300.0) }
        waistCm?.let { require(it in 0.0..500.0) }
    }

    val hasMeasurements: Boolean
        get() = weightKg != null || bodyFatPct != null || muscleKg != null || waistCm != null
}

data class EffectiveStatus(
    val sleepDurationMinutes: Int?,
    val deepSleepMinutes: Int?,
    val sleepQuality: Int?,
    val energy: Int?,
    val fatigue: Int?,
    val workPressure: Int?,
    val pain: List<PainEntry>?,
    val note: String?,
)

object EffectiveStatusResolver {
    fun resolve(morning: StatusCheckin?, preWorkout: StatusCheckin?): EffectiveStatus = EffectiveStatus(
        sleepDurationMinutes = preWorkout?.sleepDurationMinutes ?: morning?.sleepDurationMinutes,
        deepSleepMinutes = preWorkout?.deepSleepMinutes ?: morning?.deepSleepMinutes,
        sleepQuality = preWorkout?.sleepQuality ?: morning?.sleepQuality,
        energy = preWorkout?.energy ?: morning?.energy,
        fatigue = preWorkout?.fatigue ?: morning?.fatigue,
        workPressure = preWorkout?.workPressure ?: morning?.workPressure,
        pain = preWorkout?.pain ?: morning?.pain,
        note = preWorkout?.note ?: morning?.note,
    )
}

enum class GuidanceSource {
    ACTUAL,
    FORMAL_PLAN,
    LOCAL_SUGGESTION,
}

data class TodayGuidance(
    val source: GuidanceSource,
    val title: String,
    val trainingType: String,
    val estimatedMinutes: Int? = null,
    val note: String? = null,
    val routineId: String? = null,
    val dailyPlanItemId: String? = null,
    val planTemplateId: String? = null,
)

object TodayGuidanceResolver {
    fun resolve(
        actual: TodayGuidance?,
        effectivePlan: TodayGuidance?,
        fallback: TodayGuidance,
    ): TodayGuidance = actual ?: effectivePlan ?: fallback
}
