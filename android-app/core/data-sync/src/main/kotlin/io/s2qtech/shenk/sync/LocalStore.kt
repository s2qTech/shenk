package io.s2qtech.shenk.sync

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "shared_records",
    primaryKeys = ["entity", "record_id"],
    indices = [
        Index(value = ["entity", "deleted_at"]),
        Index(value = ["sync_state"]),
    ],
)
data class SharedRecordEntity(
    val entity: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "contract_version") val contractVersion: String,
    val revision: Int,
    @ColumnInfo(name = "base_revision") val baseRevision: Int,
    @ColumnInfo(name = "device_id") val deviceId: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "envelope_json") val envelopeJson: String,
    @ColumnInfo(name = "sync_state") val syncState: String,
)

@Entity(
    tableName = "outbox",
    indices = [Index(value = ["next_attempt_at"]), Index(value = ["idempotency_key"], unique = true)],
)
data class OutboxEntity(
    @androidx.room.PrimaryKey @ColumnInfo(name = "record_key") val recordKey: String,
    val entity: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    val operation: String,
    @ColumnInfo(name = "base_revision") val baseRevision: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    val attempts: Int,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "sync_conflicts")
data class ConflictEntity(
    @androidx.room.PrimaryKey @ColumnInfo(name = "record_key") val recordKey: String,
    val entity: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    val reason: String,
    @ColumnInfo(name = "local_json") val localJson: String,
    @ColumnInfo(name = "remote_json") val remoteJson: String,
    @ColumnInfo(name = "base_revision") val baseRevision: Int,
    @ColumnInfo(name = "remote_revision") val remoteRevision: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @androidx.room.PrimaryKey val key: String,
    val value: String,
)

@Entity(
    tableName = "ai_review_jobs",
    indices = [Index(value = ["state", "next_attempt_at"]), Index(value = ["date", "input_digest"], unique = true)],
)
data class AiReviewJobEntity(
    @androidx.room.PrimaryKey @ColumnInfo(name = "job_id") val jobId: String,
    val date: String,
    @ColumnInfo(name = "input_digest") val inputDigest: String,
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String,
    @ColumnInfo(name = "allow_incomplete") val allowIncomplete: Boolean,
    val state: String,
    val attempts: Int,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface SharedRecordDao {
    @Query("SELECT * FROM shared_records WHERE entity = :entity AND record_id = :recordId")
    suspend fun get(entity: String, recordId: String): SharedRecordEntity?

    @Query("SELECT * FROM shared_records WHERE entity = :entity AND deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeActive(entity: String): Flow<List<SharedRecordEntity>>

    @Query("SELECT * FROM shared_records ORDER BY entity, record_id")
    suspend fun getAll(): List<SharedRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(record: SharedRecordEntity)

    @Query("SELECT COUNT(*) FROM shared_records")
    suspend fun count(): Int
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(operation: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE next_attempt_at <= :now ORDER BY created_at LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE record_key = :recordKey")
    suspend fun get(recordKey: String): OutboxEntity?

    @Query("DELETE FROM outbox WHERE record_key = :recordKey AND idempotency_key = :idempotencyKey")
    suspend fun deleteIfCurrent(recordKey: String, idempotencyKey: String): Int

    @Query("UPDATE outbox SET attempts = attempts + 1, next_attempt_at = :nextAttemptAt, last_error = :error WHERE record_key = :recordKey AND idempotency_key = :idempotencyKey")
    suspend fun markRetry(recordKey: String, idempotencyKey: String, nextAttemptAt: Long, error: String)

    @Query("DELETE FROM outbox WHERE record_key = :recordKey")
    suspend fun delete(recordKey: String)

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int
}

@Dao
interface ConflictDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(conflict: ConflictEntity)

    @Query("SELECT * FROM sync_conflicts ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE record_key = :recordKey")
    suspend fun get(recordKey: String): ConflictEntity?

    @Query("DELETE FROM sync_conflicts WHERE record_key = :recordKey")
    suspend fun delete(recordKey: String)

    @Query("SELECT COUNT(*) FROM sync_conflicts")
    suspend fun count(): Int
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT value FROM sync_metadata WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: SyncMetadataEntity)
}

@Dao
interface AiReviewJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(job: AiReviewJobEntity)

    @Query("SELECT * FROM ai_review_jobs WHERE date = :date ORDER BY updated_at DESC LIMIT 1")
    fun observeLatest(date: String): Flow<AiReviewJobEntity?>

    @Query("SELECT * FROM ai_review_jobs WHERE state IN ('PENDING', 'RETRY') AND next_attempt_at <= :now ORDER BY created_at LIMIT 1")
    suspend fun nextDue(now: Long): AiReviewJobEntity?

    @Query("SELECT MIN(next_attempt_at) FROM ai_review_jobs WHERE state IN ('PENDING', 'RETRY')")
    suspend fun nextScheduledAt(): Long?

    @Query("SELECT * FROM ai_review_jobs WHERE date = :date AND input_digest = :digest LIMIT 1")
    suspend fun find(date: String, digest: String): AiReviewJobEntity?

    @Query("UPDATE ai_review_jobs SET state = :state, attempts = :attempts, next_attempt_at = :nextAttemptAt, last_error = :lastError, updated_at = :updatedAt WHERE job_id = :jobId")
    suspend fun updateState(jobId: String, state: String, attempts: Int, nextAttemptAt: Long, lastError: String?, updatedAt: Long)

    @Query("UPDATE ai_review_jobs SET state = 'SUPERSEDED', updated_at = :updatedAt WHERE date = :date AND input_digest != :digest AND state IN ('PENDING', 'RETRY')")
    suspend fun supersedeOtherInputs(date: String, digest: String, updatedAt: Long)
}

@Database(
    entities = [SharedRecordEntity::class, OutboxEntity::class, ConflictEntity::class, SyncMetadataEntity::class, AiReviewJobEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ShenkDatabase : RoomDatabase() {
    abstract fun records(): SharedRecordDao
    abstract fun outbox(): OutboxDao
    abstract fun conflicts(): ConflictDao
    abstract fun metadata(): SyncMetadataDao
    abstract fun aiReviewJobs(): AiReviewJobDao

    companion object {
        @Volatile private var instance: ShenkDatabase? = null

        fun get(context: Context): ShenkDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ShenkDatabase::class.java,
                "shenk-native.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `ai_review_jobs` (`job_id` TEXT NOT NULL, `date` TEXT NOT NULL, `input_digest` TEXT NOT NULL, `snapshot_json` TEXT NOT NULL, `allow_incomplete` INTEGER NOT NULL, `state` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `next_attempt_at` INTEGER NOT NULL, `last_error` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`job_id`))""",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_review_jobs_state_next_attempt_at` ON `ai_review_jobs` (`state`, `next_attempt_at`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_review_jobs_date_input_digest` ON `ai_review_jobs` (`date`, `input_digest`)")
            }
        }
    }
}
