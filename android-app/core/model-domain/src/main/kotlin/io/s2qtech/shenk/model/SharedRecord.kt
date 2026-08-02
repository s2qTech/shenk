package io.s2qtech.shenk.model

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SharedRecordKey(
    val entity: String,
    val id: String,
) {
    init {
        require(entity.isNotBlank()) { "entity must not be blank" }
        require(id.isNotBlank()) { "id must not be blank" }
    }

    val storageKey: String = "$entity:$id"
}

/**
 * Presentation-neutral shared record. The complete envelope remains a JsonObject so an
 * Android read-modify-write cycle cannot discard additive fields introduced by another client.
 */
data class SharedRecord(
    val envelope: JsonObject,
) {
    val contractVersion: String = envelope.string("contractVersion") ?: ContractVersion.PLANNED
    val entity: String = requireNotNull(envelope.string("entity")) { "record entity is required" }
    val id: String = requireNotNull(envelope.string("id")) { "record id is required" }
    val revision: Int = envelope.int("revision") ?: 0
    val baseRevision: Int = envelope.int("baseRevision") ?: revision
    val deviceId: String? = envelope.string("deviceId")
    val createdAt: String? = envelope.string("createdAt")
    val updatedAt: String? = envelope.string("updatedAt")
    val deletedAt: String? = envelope.string("deletedAt")
    val data: JsonObject = envelope["data"]?.jsonObject
        ?: throw IllegalArgumentException("record data is required")
    val key: SharedRecordKey = SharedRecordKey(entity, id)

    fun withSyncMetadata(
        revision: Int = this.revision,
        baseRevision: Int = this.baseRevision,
        deviceId: String? = this.deviceId,
        createdAt: String? = this.createdAt,
        updatedAt: String? = this.updatedAt,
    ): SharedRecord = SharedRecord(buildJsonObject {
        envelope.forEach { (key, value) -> put(key, value) }
        put("contractVersion", JsonPrimitive(contractVersion))
        put("entity", JsonPrimitive(entity))
        put("id", JsonPrimitive(id))
        put("revision", JsonPrimitive(revision))
        put("baseRevision", JsonPrimitive(baseRevision))
        deviceId?.let { put("deviceId", JsonPrimitive(it)) }
        createdAt?.let { put("createdAt", JsonPrimitive(it)) }
        updatedAt?.let { put("updatedAt", JsonPrimitive(it)) }
        if (deletedAt == null) put("deletedAt", JsonNull)
        put("data", data)
    })

    fun withDeletedAt(deletedAt: String): SharedRecord = SharedRecord(buildJsonObject {
        envelope.forEach { (key, value) -> put(key, value) }
        put("deletedAt", JsonPrimitive(deletedAt))
    })

    companion object {
        fun create(
            entity: String,
            id: String,
            data: JsonObject,
            contractVersion: String = ContractVersion.PLANNED,
            deletedAt: String? = null,
        ): SharedRecord = SharedRecord(buildJsonObject {
            put("contractVersion", JsonPrimitive(contractVersion))
            put("entity", JsonPrimitive(entity))
            put("id", JsonPrimitive(id))
            put("revision", JsonPrimitive(0))
            put("baseRevision", JsonPrimitive(0))
            put("deletedAt", deletedAt?.let(::JsonPrimitive) ?: JsonNull)
            put("data", data)
        })
    }
}

private fun JsonObject.string(key: String): String? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

private fun JsonObject.int(key: String): Int? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
