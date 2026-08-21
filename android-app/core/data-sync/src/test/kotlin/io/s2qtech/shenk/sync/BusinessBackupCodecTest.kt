package io.s2qtech.shenk.sync

import io.s2qtech.shenk.model.SharedRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BusinessBackupCodecTest {
    private val codec = BusinessBackupCodec()

    @Test
    fun backupContainsBusinessRecordsButNoSyncInternals() {
        val record = SharedRecord.create(
            "body_metrics",
            "synthetic-metric",
            buildJsonObject { put("weightKg", JsonPrimitive(100.0)) },
        )

        val encoded = codec.encode(listOf(record))
        val decoded = codec.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals(record.data, decoded.single().data)
        assertFalse(encoded.contains("outbox"))
        assertFalse(encoded.contains("sync_conflicts"))
    }

    @Test
    fun secretShapedFieldsAreRejected() {
        val unsafe = SharedRecord.create(
            "body_metrics",
            "synthetic-unsafe",
            buildJsonObject { put("apiKey", JsonPrimitive("not-exportable")) },
        )
        assertThrows(IllegalArgumentException::class.java) { codec.encode(listOf(unsafe)) }
    }

    @Test
    fun nestedAndNormalizedSecretFieldsAreRejected() {
        listOf("access_token", "Authorization", "client-secret", "privateKey", "profileAccessKey").forEach { field ->
            val unsafe = SharedRecord.create(
                "training_logs",
                "synthetic-$field",
                buildJsonObject {
                    put("nested", buildJsonObject { put(field, JsonPrimitive("not-exportable")) })
                },
            )

            assertThrows(field, IllegalArgumentException::class.java) { codec.encode(listOf(unsafe)) }
        }
    }

    @Test
    fun unknownEntitiesAndDuplicateKeysAreRejectedBeforeRestore() {
        val unknown = SharedRecord.create("unknown_records", "synthetic-unknown", buildJsonObject {})
        val duplicate = SharedRecord.create("body_metrics", "synthetic-duplicate", buildJsonObject {})

        assertThrows(IllegalArgumentException::class.java) { codec.encode(listOf(unknown)) }
        assertThrows(IllegalArgumentException::class.java) { codec.encode(listOf(duplicate, duplicate)) }
    }

    @Test
    fun malformedExportTimeAndUnsupportedRecordContractAreRejected() {
        val valid = codec.encode(
            listOf(SharedRecord.create("body_metrics", "synthetic-valid", buildJsonObject {})),
        )
        val json = Json.parseToJsonElement(valid).let { it as JsonObject }
        val malformedTime = JsonObject(json + ("exportedAt" to JsonPrimitive("not-an-instant")))
        val invalidRecord = JsonObject(
            json + (
                "records" to JsonArray(
                    listOf(
                        buildJsonObject {
                            put("contractVersion", JsonPrimitive("3.0"))
                            put("entity", JsonPrimitive("body_metrics"))
                            put("id", JsonPrimitive("synthetic-invalid-contract"))
                            put("revision", JsonPrimitive(0))
                            put("baseRevision", JsonPrimitive(0))
                            put("data", buildJsonObject {})
                        },
                    ),
                )
                ),
        )

        assertThrows(IllegalArgumentException::class.java) { codec.decode(malformedTime.toString()) }
        assertThrows(IllegalArgumentException::class.java) { codec.decode(invalidRecord.toString()) }
    }

    @Test
    fun legacyAndPlannedRecordContractsCanCoexist() {
        val records = listOf(
            SharedRecord.create("body_metrics", "synthetic-v1", buildJsonObject {}, contractVersion = "1.0"),
            SharedRecord.create("status_checkins", "synthetic-v2", buildJsonObject {}, contractVersion = "2.0"),
        )

        assertEquals(listOf("1.0", "2.0"), codec.decode(codec.encode(records)).map { it.contractVersion })
    }
}
