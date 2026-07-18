package io.s2qtech.shenk.timer

enum class TimerEngineState {
    IDLE,
    PREVIEW,
    RUNNING,
    PAUSED,
    COMPLETED,
    STOPPED,
}

interface TimerEnginePort {
    val snapshot: TimerSnapshot

    fun preview(request: TimerPreviewRequest): TimerSnapshot
    fun start(nowEpochMillis: Long): TimerSnapshot
    fun tick(nowEpochMillis: Long): TimerSnapshot
    fun pause(nowEpochMillis: Long, reason: String? = null): TimerSnapshot
    fun resume(nowEpochMillis: Long): TimerSnapshot
    fun next(nowEpochMillis: Long): TimerSnapshot
    fun previous(nowEpochMillis: Long): TimerSnapshot
    fun stop(nowEpochMillis: Long, reason: String? = null): TimerSnapshot
    fun reset(): TimerSnapshot
}
