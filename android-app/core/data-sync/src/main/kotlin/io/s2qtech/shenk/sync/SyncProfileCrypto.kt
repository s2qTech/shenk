package io.s2qtech.shenk.sync

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.net.URI
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class EncryptedSyncProfile(
    val schema: String = SyncProfileCrypto.PROFILE_SCHEMA,
    val cipher: String = "AES-GCM",
    val kdf: String = "PBKDF2-SHA256",
    val iterations: Int = SyncProfileCrypto.ITERATIONS,
    val salt: String,
    val iv: String,
    val ciphertext: String,
    val updatedAt: String,
)

@Serializable
data class SyncProfilePayload(
    val apiBase: String,
    val timerUrl: String,
    val token: String,
    val timerToken: String,
)

class SyncProfileCrypto(
    private val random: SecureRandom = SecureRandom(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun generateMigrationCode(): String = ByteArray(32).also(random::nextBytes).base64Url()

    fun deriveProfileId(migrationCode: String): String {
        val code = validateMigrationCode(migrationCode)
        val digest = MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8))
        return "profile_${digest.base64Url().take(48)}"
    }

    fun encrypt(payload: SyncProfilePayload, migrationCode: String): EncryptedSyncProfile {
        validatePayload(payload)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(validateMigrationCode(migrationCode), salt), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(json.encodeToString(SyncProfilePayload.serializer(), payload).toByteArray())
        return EncryptedSyncProfile(
            salt = salt.base64Url(),
            iv = iv.base64Url(),
            ciphertext = ciphertext.base64Url(),
            updatedAt = Instant.now().toString(),
        )
    }

    fun decrypt(profile: EncryptedSyncProfile, migrationCode: String): SyncProfilePayload {
        require(profile.schema == PROFILE_SCHEMA && profile.cipher == "AES-GCM" && profile.kdf == "PBKDF2-SHA256") {
            "unsupported sync profile"
        }
        require(profile.iterations == ITERATIONS) { "unsupported sync profile iterations" }
        runCatching { Instant.parse(profile.updatedAt) }
            .getOrElse { throw IllegalArgumentException("sync profile timestamp is invalid", it) }
        val salt = profile.salt.base64UrlBytes()
        val iv = profile.iv.base64UrlBytes()
        val ciphertext = profile.ciphertext.base64UrlBytes()
        require(salt.size == SALT_BYTES) { "sync profile salt is invalid" }
        require(iv.size == IV_BYTES) { "sync profile IV is invalid" }
        require(ciphertext.size in GCM_TAG_BYTES..MAX_CIPHERTEXT_BYTES) { "sync profile ciphertext is invalid" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(validateMigrationCode(migrationCode), salt),
            GCMParameterSpec(128, iv),
        )
        val payload = json.decodeFromString(
            SyncProfilePayload.serializer(),
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8),
        )
        validatePayload(payload)
        return payload
    }

    private fun deriveKey(code: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun validatePayload(payload: SyncProfilePayload) {
        requireValidHttpsUrl(payload.apiBase, "API base")
        requireValidHttpsUrl(payload.timerUrl, "timer URL")
        require(payload.token.isNotBlank() && payload.timerToken.isNotBlank()) { "sync profile is incomplete" }
        require(payload.token.length <= MAX_SECRET_CHARS && payload.timerToken.length <= MAX_SECRET_CHARS) {
            "sync profile secret is too large"
        }
    }

    private fun requireValidHttpsUrl(value: String, label: String) {
        val uri = runCatching { URI(value) }
            .getOrElse { throw IllegalArgumentException("sync profile $label is invalid", it) }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "sync profile $label must use HTTPS without embedded credentials"
        }
    }

    companion object {
        const val PROFILE_SCHEMA = "shenk_sync_profile/v1"
        const val ITERATIONS = 210_000
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val GCM_TAG_BYTES = 16
        const val MAX_CIPHERTEXT_BYTES = 64 * 1024
        const val MAX_SECRET_CHARS = 8 * 1024

        fun validateMigrationCode(value: String): String = value.trim().also {
            require(it.matches(Regex("^[A-Za-z0-9_-]{20,200}$"))) { "invalid migration code" }
        }
    }
}

interface SyncProfileRemoteApi {
    @GET("sync-profiles/{profileId}")
    suspend fun get(
        @Path("profileId") profileId: String,
        @Header("X-Shenke-Profile-Key") profileAccessKey: String,
    ): JsonObject

    @PUT("sync-profiles/{profileId}")
    suspend fun put(
        @Path("profileId") profileId: String,
        @Header("Authorization") authorization: String,
        @Body body: JsonObject,
    ): JsonObject
}

class SyncProfileGateway(
    private val api: SyncProfileRemoteApi,
    private val crypto: SyncProfileCrypto = SyncProfileCrypto(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun save(
        migrationCode: String,
        shenkToken: String,
        deviceId: String,
        payload: SyncProfilePayload,
    ): String {
        require(shenkToken.isNotBlank()) { "Shenk token is required" }
        val profileId = crypto.deriveProfileId(migrationCode)
        val encrypted = crypto.encrypt(payload, migrationCode)
        api.put(
            profileId = profileId,
            authorization = "Bearer $shenkToken",
            body = buildJsonObject {
                put("deviceId", JsonPrimitive(deviceId))
                put("profileAccessKey", JsonPrimitive(migrationCode))
                put("profile", json.encodeToJsonElement(EncryptedSyncProfile.serializer(), encrypted))
            },
        )
        return profileId
    }

    suspend fun load(migrationCode: String): SyncProfilePayload {
        val profileId = crypto.deriveProfileId(migrationCode)
        val response = api.get(profileId, migrationCode)
        val encrypted = json.decodeFromJsonElement(
            EncryptedSyncProfile.serializer(),
            requireNotNull(response["profile"]) { "sync profile payload is missing" }.jsonObject,
        )
        return crypto.decrypt(encrypted, migrationCode)
    }
}

object SyncProfileRemoteApiFactory {
    fun create(apiBase: String, json: Json = Json { ignoreUnknownKeys = true }): SyncProfileRemoteApi {
        require(apiBase.startsWith("https://")) { "cloud API must use HTTPS" }
        return Retrofit.Builder()
            .baseUrl("${apiBase.trimEnd('/')}/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SyncProfileRemoteApi::class.java)
    }
}

private fun ByteArray.base64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
private fun String.base64UrlBytes(): ByteArray = Base64.getUrlDecoder().decode(this)
