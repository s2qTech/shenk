package io.s2qtech.shenk.sync

import androidx.room.withTransaction
import io.s2qtech.shenk.model.EntityOwnership
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.model.SharedRecordKey
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

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

interface TimeSource {
    fun epochMillis(): Long
    fun isoInstant(): String
}

object SystemTimeSource : TimeSource {
    override fun epochMillis(): Long = System.currentTimeMillis()
    override fun isoInstant(): String = Instant.now().toString()
}

class LocalFirstRepository(
    private val database: ShenkDatabase,
    private val localDeviceId: String? = null,
    private val timeSource: TimeSource = SystemTimeSource,
    private val nextId: () -> String = { UUID.randomUUID().toString() },
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    fun observeActive(entity: String): Flow<List<SharedRecord>> =
        database.records().observeActive(entity).map { rows -> rows.map(::decode) }

    fun observeConflicts(): Flow<List<ConflictEntity>> = database.conflicts().observeAll()

    suspend fun get(entity: String, id: String): SharedRecord? =
        database.records().get(entity, id)?.let(::decode)

    suspend fun persistAndEnqueue(
        record: SharedRecord,
        writer: SharedEntityOwner,
    ): SyncFoundationState {
        require(EntityOwnership.canWrite(writer, record.entity)) {
            "$writer cannot write ${record.entity}"
        }
        database.withTransaction {
            queueInTransaction(record)
        }
        return SyncFoundationState.QUEUED
    }

    suspend fun persistBatchAndEnqueue(
        records: List<SharedRecord>,
        writer: SharedEntityOwner,
    ): SyncFoundationState {
        require(records.isNotEmpty()) { "records must not be empty" }
        records.forEach { record ->
            require(EntityOwnership.canWrite(writer, record.entity)) {
                "$writer cannot write ${record.entity}"
            }
        }
        database.withTransaction {
            records.forEach { queueInTransaction(it) }
        }
        return SyncFoundationState.QUEUED
    }

    suspend fun persistOwnedBatchAndEnqueue(
        records: List<Pair<SharedRecord, SharedEntityOwner>>,
    ): SyncFoundationState {
        require(records.isNotEmpty()) { "records must not be empty" }
        records.forEach { (record, writer) ->
            require(EntityOwnership.canWrite(writer, record.entity)) {
                "$writer cannot write ${record.entity}"
            }
        }
        database.withTransaction {
            records.forEach { (record, _) -> queueInTransaction(record) }
        }
        return SyncFoundationState.QUEUED
    }

    suspend fun applyRemote(remote: SharedRecord) {
        database.withTransaction {
            val existing = database.records().get(remote.entity, remote.id)
            if (existing == null || existing.syncState == SyncFoundationState.SYNCED.name) {
                database.records().put(remote.toEntity(SyncFoundationState.SYNCED))
                return@withTransaction
            }
            val local = decode(existing)
            if (remote.revision <= local.baseRevision) return@withTransaction
            if (sameBusinessPayload(local, remote)) {
                database.records().put(remote.toEntity(SyncFoundationState.SYNCED))
                database.outbox().delete(remote.key.storageKey)
                database.conflicts().delete(remote.key.storageKey)
            } else {
                persistConflict(local, remote, "remote_changed_while_local_dirty")
            }
        }
    }

    suspend fun markAccepted(
        key: SharedRecordKey,
        idempotencyKey: String,
        revision: Int,
        updatedAt: String?,
    ) {
        database.withTransaction {
            val pending = database.outbox().get(key.storageKey)
            if (pending?.idempotencyKey != idempotencyKey) return@withTransaction
            val current = database.records().get(key.entity, key.id) ?: return@withTransaction
            val synced = decode(current).withSyncMetadata(
                revision = revision,
                baseRevision = revision,
                updatedAt = updatedAt,
            )
            database.records().put(synced.toEntity(SyncFoundationState.SYNCED))
            database.outbox().deleteIfCurrent(key.storageKey, idempotencyKey)
            database.conflicts().delete(key.storageKey)
        }
    }

    suspend fun markConflict(
        operation: OutboxEntity,
        reason: String,
        remoteEnvelope: JsonObject,
    ) {
        database.withTransaction {
            val pending = database.outbox().get(operation.recordKey)
            if (pending?.idempotencyKey != operation.idempotencyKey) return@withTransaction
            val localEntity = database.records().get(operation.entity, operation.recordId) ?: return@withTransaction
            persistConflict(decode(localEntity), SharedRecord(remoteEnvelope), reason)
        }
    }

    suspend fun resolveWithRemote(key: SharedRecordKey) {
        database.withTransaction {
            val conflict = database.conflicts().get(key.storageKey) ?: return@withTransaction
            val remote = SharedRecord(json.parseToJsonElement(conflict.remoteJson).jsonObject)
            database.records().put(remote.toEntity(SyncFoundationState.SYNCED))
            database.outbox().delete(key.storageKey)
            database.conflicts().delete(key.storageKey)
        }
    }

    suspend fun resolveWithLocal(key: SharedRecordKey, writer: SharedEntityOwner) {
        require(EntityOwnership.canWrite(writer, key.entity)) { "$writer cannot write ${key.entity}" }
        database.withTransaction {
            val conflict = database.conflicts().get(key.storageKey) ?: return@withTransaction
            val local = SharedRecord(json.parseToJsonElement(conflict.localJson).jsonObject)
            queueInTransaction(local, forcedBaseRevision = conflict.remoteRevision)
        }
    }

    suspend fun allRecords(): List<SharedRecord> = database.records().getAll().map(::decode)

    suspend fun markRetry(operation: OutboxEntity, nextAttemptAt: Long, error: String) {
        database.outbox().markRetry(
            operation.recordKey,
            operation.idempotencyKey,
            nextAttemptAt,
            error.take(80),
        )
    }

    suspend fun restoreBackup(records: List<SharedRecord>): BackupRestoreResult {
        records.forEach { require(it.entity in EntityOwnership.knownEntities) { "unknown entity ${it.entity}" } }
        require(records.map { it.key.storageKey }.distinct().size == records.size) {
            "backup contains duplicate records"
        }
        var restored = 0
        var unchanged = 0
        var skippedExisting = 0
        database.withTransaction {
            records.forEach { record ->
                val existingEntity = database.records().get(record.entity, record.id)
                if (existingEntity != null) {
                    val existing = decode(existingEntity)
                    if (sameBusinessPayload(existing, record)) {
                        unchanged += 1
                    } else {
                        // Backup restore is a merge, never an implicit replace. This preserves
                        // queued edits, conflicts, and newer local facts until the user resolves them.
                        skippedExisting += 1
                    }
                } else {
                    if (EntityOwnership.ownerOf(record.entity) == SharedEntityOwner.TIMER) {
                        database.records().put(record.toEntity(SyncFoundationState.SYNCED))
                    } else {
                        queueInTransaction(record)
                    }
                    restored += 1
                }
            }
        }
        return BackupRestoreResult(restored, unchanged, skippedExisting)
    }

    private suspend fun queueInTransaction(record: SharedRecord, forcedBaseRevision: Int? = null) {
        val existing = database.records().get(record.entity, record.id)
        val baseRevision = forcedBaseRevision ?: existing?.revision ?: 0
        val now = timeSource.isoInstant()
        val outgoing = record.withSyncMetadata(
            revision = maxOf(1, baseRevision),
            baseRevision = baseRevision,
            deviceId = record.deviceId ?: localDeviceId,
            createdAt = record.createdAt ?: now,
            updatedAt = now,
        )
        val payload = json.encodeToString(JsonObject.serializer(), outgoing.envelope)
        database.records().put(
            outgoing.toEntity(
                revision = baseRevision,
                baseRevision = baseRevision,
                state = SyncFoundationState.QUEUED,
                envelopeJson = payload,
            ),
        )
        database.outbox().put(
            OutboxEntity(
                recordKey = record.key.storageKey,
                entity = record.entity,
                recordId = record.id,
                operation = if (record.deletedAt == null) "upsert" else "delete",
                baseRevision = baseRevision,
                payloadJson = payload,
                idempotencyKey = nextId(),
                attempts = 0,
                nextAttemptAt = timeSource.epochMillis(),
                lastError = null,
                createdAt = timeSource.epochMillis(),
            ),
        )
        database.conflicts().delete(record.key.storageKey)
    }

    private suspend fun persistConflict(local: SharedRecord, remote: SharedRecord, reason: String) {
        val key = local.key.storageKey
        database.records().put(
            database.records().get(local.entity, local.id)!!.copy(syncState = SyncFoundationState.CONFLICT.name),
        )
        database.conflicts().put(
            ConflictEntity(
                recordKey = key,
                entity = local.entity,
                recordId = local.id,
                reason = reason,
                localJson = json.encodeToString(JsonObject.serializer(), local.envelope),
                remoteJson = json.encodeToString(JsonObject.serializer(), remote.envelope),
                baseRevision = local.baseRevision,
                remoteRevision = remote.revision,
                createdAt = timeSource.epochMillis(),
            ),
        )
        database.outbox().delete(key)
    }

    private fun decode(entity: SharedRecordEntity): SharedRecord =
        SharedRecord(json.parseToJsonElement(entity.envelopeJson).jsonObject)
}

internal fun SharedRecord.toEntity(
    state: SyncFoundationState,
    revision: Int = this.revision,
    baseRevision: Int = this.baseRevision,
    envelopeJson: String = Json.encodeToString(JsonObject.serializer(), envelope),
): SharedRecordEntity = SharedRecordEntity(
    entity = entity,
    recordId = id,
    contractVersion = contractVersion,
    revision = revision,
    baseRevision = baseRevision,
    deviceId = deviceId,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    envelopeJson = envelopeJson,
    syncState = state.name,
)

private fun sameBusinessPayload(left: SharedRecord, right: SharedRecord): Boolean =
    left.data == right.data && left.deletedAt == right.deletedAt
