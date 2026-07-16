package io.s2qtech.shenk.sync

enum class SyncFoundationState {
    LOCAL_ONLY,
    QUEUED,
    SYNCED,
    CONFLICT,
}

data class PendingOperation(
    val entity: String,
    val recordId: String,
) {
    init {
        require(entity.isNotBlank()) { "entity must not be blank" }
        require(recordId.isNotBlank()) { "recordId must not be blank" }
    }
}

interface LocalFirstWritePort {
    suspend fun persistAndEnqueue(operation: PendingOperation): SyncFoundationState
}
