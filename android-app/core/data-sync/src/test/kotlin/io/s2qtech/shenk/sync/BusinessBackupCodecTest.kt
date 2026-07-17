package io.s2qtech.shenk.sync

import io.s2qtech.shenk.model.SharedRecord
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
}
