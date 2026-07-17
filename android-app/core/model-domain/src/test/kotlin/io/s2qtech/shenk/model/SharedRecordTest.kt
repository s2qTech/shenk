package io.s2qtech.shenk.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SharedRecordTest {
    @Test
    fun additiveEnvelopeFieldsSurviveMetadataUpdate() {
        val record = SharedRecord.create(
            entity = "training_logs",
            id = "synthetic-record",
            data = buildJsonObject { put("source", JsonPrimitive("manual")) },
        )
        val withUnknown = SharedRecord(buildJsonObject {
            record.envelope.forEach { (key, value) -> put(key, value) }
            put("futureAdditiveField", JsonPrimitive("preserve-me"))
        })

        val updated = withUnknown.withSyncMetadata(revision = 4, baseRevision = 4)

        assertEquals("preserve-me", updated.envelope["futureAdditiveField"]?.toString()?.trim('"'))
        assertEquals(4, updated.revision)
        assertEquals(4, updated.baseRevision)
    }

    @Test
    fun unknownEntitiesAreNotWritable() {
        assertFalse(EntityOwnership.canWrite(SharedEntityOwner.RECORD, "unknown_entity"))
    }
}
