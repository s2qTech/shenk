package io.s2qtech.shenk.sync

import android.content.ContentResolver
import android.net.Uri
import io.s2qtech.shenk.model.ContractVersion
import io.s2qtech.shenk.model.EntityOwnership
import io.s2qtech.shenk.model.SharedRecord
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class BusinessBackup(
    val schema: String = SCHEMA,
    val contractVersion: String = ContractVersion.PLANNED,
    val exportedAt: String,
    val records: List<JsonObject>,
) {
    companion object {
        const val SCHEMA = "shenk_business_backup/v1"
    }
}

class BusinessBackupCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        prettyPrint = true
    },
) {
    fun encode(records: List<SharedRecord>): String {
        validateRecords(records)
        return json.encodeToString(
            BusinessBackup.serializer(),
            BusinessBackup(
                exportedAt = Instant.now().toString(),
                records = records.map(SharedRecord::envelope),
            ),
        )
    }

    fun decode(value: String): List<SharedRecord> {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "backup is too large" }
        val backup = json.decodeFromString(BusinessBackup.serializer(), value)
        require(backup.schema == BusinessBackup.SCHEMA) { "unsupported backup schema" }
        require(backup.contractVersion in setOf(ContractVersion.ACTIVE, ContractVersion.PLANNED)) {
            "unsupported backup contract"
        }
        runCatching { Instant.parse(backup.exportedAt) }
            .getOrElse { throw IllegalArgumentException("backup export time is invalid", it) }
        return backup.records.map(::SharedRecord).also(::validateRecords)
    }

    private fun validateRecords(records: List<SharedRecord>) {
        require(records.size <= MAX_RECORDS) { "backup contains too many records" }
        val keys = mutableSetOf<String>()
        records.forEach { record ->
            assertNoSecretFields(record.envelope)
            require(record.contractVersion in setOf(ContractVersion.ACTIVE, ContractVersion.PLANNED)) {
                "unsupported record contract"
            }
            require(record.entity in EntityOwnership.knownEntities) { "unknown backup entity ${record.entity}" }
            require(record.revision >= 0 && record.baseRevision >= 0) { "backup revision is invalid" }
            require(keys.add(record.key.storageKey)) { "backup contains duplicate record ${record.key.storageKey}" }
        }
    }

    private fun assertNoSecretFields(element: JsonElement) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                require(!isSecretField(key)) { "backup contains forbidden configuration field" }
                assertNoSecretFields(value)
            }
            is JsonArray -> element.forEach(::assertNoSecretFields)
            else -> Unit
        }
    }

    companion object {
        const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
        const val MAX_RECORDS = 100_000

        private val EXACT_SECRET_FIELDS = setOf(
            "authorization",
            "cookie",
            "migrationcode",
            "profileaccesskey",
        )

        private fun isSecretField(key: String): Boolean {
            val normalized = key.filter(Char::isLetterOrDigit).lowercase()
            return normalized in EXACT_SECRET_FIELDS ||
                normalized == "token" || normalized.endsWith("token") ||
                normalized == "apikey" || normalized.endsWith("apikey") ||
                normalized == "password" || normalized.endsWith("password") ||
                normalized == "secret" || normalized.endsWith("secret") ||
                normalized == "privatekey" || normalized.endsWith("privatekey")
        }
    }
}

data class BackupRestoreResult(
    val restored: Int,
    val unchanged: Int,
    val skippedExisting: Int,
) {
    val total: Int get() = restored + unchanged + skippedExisting
}

class SafBusinessBackup(
    private val contentResolver: ContentResolver,
    private val repository: LocalFirstRepository,
    private val codec: BusinessBackupCodec = BusinessBackupCodec(),
) {
    suspend fun exportTo(uri: Uri) {
        val text = codec.encode(repository.allRecords())
        contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(text)
        } ?: error("unable to open backup destination")
    }

    suspend fun restoreFrom(uri: Uri): BackupRestoreResult {
        val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            val buffer = CharArray(8192)
            val output = StringBuilder()
            var totalBytes = 0
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                totalBytes += String(buffer, 0, count).toByteArray(Charsets.UTF_8).size
                require(totalBytes <= BusinessBackupCodec.MAX_BACKUP_BYTES) { "backup is too large" }
                output.append(buffer, 0, count)
            }
            output.toString()
        } ?: error("unable to open backup source")
        val records = codec.decode(text)
        return repository.restoreBackup(records)
    }
}
