package io.s2qtech.shenk.sync

import android.content.ContentValues
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafBusinessBackupInstrumentedTest {
    @Test
    fun syntheticBusinessBackupRoundTripsThroughDeviceContentResolver() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceDatabase = Room.inMemoryDatabaseBuilder(context, ShenkDatabase::class.java).build()
        val targetDatabase = Room.inMemoryDatabaseBuilder(context, ShenkDatabase::class.java).build()
        val uri = requireNotNull(
            context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "shenk-p8-synthetic-${UUID.randomUUID()}.json")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                },
            ),
        )
        try {
            val sourceRepository = LocalFirstRepository(sourceDatabase, localDeviceId = "synthetic-source")
            sourceRepository.persistAndEnqueue(
                SharedRecord.create(
                    entity = "body_metrics",
                    id = "synthetic-saf-metric",
                    data = buildJsonObject { put("weightKg", JsonPrimitive(100.0)) },
                ),
                SharedEntityOwner.RECORD,
            )
            SafBusinessBackup(context.contentResolver, sourceRepository).exportTo(uri)

            val targetRepository = LocalFirstRepository(targetDatabase, localDeviceId = "synthetic-target")
            val result = SafBusinessBackup(context.contentResolver, targetRepository).restoreFrom(uri)

            assertEquals(BackupRestoreResult(restored = 1, unchanged = 0, skippedExisting = 0), result)
            assertNotNull(targetRepository.get("body_metrics", "synthetic-saf-metric"))
            assertEquals(1, targetDatabase.outbox().count())
        } finally {
            sourceDatabase.close()
            targetDatabase.close()
            context.contentResolver.delete(uri, null, null)
        }
    }
}
