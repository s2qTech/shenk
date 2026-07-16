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
    val state: TimerEngineState

    fun preview(routineId: String)
    fun start()
    fun pause()
    fun resume()
    fun stop()
}
