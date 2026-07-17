package io.s2qtech.shenk.sync

import android.content.ContentResolver
import android.net.Uri
import io.s2qtech.shenk.model.ContractVersion
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
        records.forEach { assertNoSecretFields(it.envelope) }
        return json.encodeToString(
            BusinessBackup.serializer(),
            BusinessBackup(
                exportedAt = Instant.now().toString(),
                records = records.map(SharedRecord::envelope),
            ),
        )
    }

    fun decode(value: String): List<SharedRecord> {
        require(value.toByteArray().size <= MAX_BACKUP_BYTES) { "backup is too large" }
        val backup = json.decodeFromString(BusinessBackup.serializer(), value)
        require(backup.schema == BusinessBackup.SCHEMA) { "unsupported backup schema" }
        require(backup.contractVersion in setOf(ContractVersion.ACTIVE, ContractVersion.PLANNED)) {
            "unsupported backup contract"
        }
        backup.records.forEach(::assertNoSecretFields)
        return backup.records.map(::SharedRecord)
    }

    private fun assertNoSecretFields(element: JsonElement) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                require(!SECRET_FIELD.matches(key)) { "backup contains forbidden configuration field" }
                assertNoSecretFields(value)
            }
            is JsonArray -> element.forEach(::assertNoSecretFields)
            else -> Unit
        }
    }

    companion object {
        const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
        private val SECRET_FIELD = Regex(
            "^(token|.*Token|apiKey|.*ApiKey|password|secret|migrationCode|profileAccessKey)$",
            RegexOption.IGNORE_CASE,
        )
    }
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

    suspend fun restoreFrom(uri: Uri): Int {
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
        repository.restoreBackup(records)
        return records.size
    }
}
