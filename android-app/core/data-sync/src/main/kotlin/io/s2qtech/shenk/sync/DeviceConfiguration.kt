package io.s2qtech.shenk.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.shenkPreferences by preferencesDataStore(name = "shenk_device_preferences")

data class SyncEndpointSettings(
    val apiBase: String,
    val timerUrl: String,
    val deviceId: String,
)

class DevicePreferencesStore(private val context: Context) {
    private object Keys {
        val apiBase = stringPreferencesKey("sync_api_base")
        val timerUrl = stringPreferencesKey("timer_url")
        val deviceId = stringPreferencesKey("device_id")
    }

    suspend fun syncSettings(): SyncEndpointSettings {
        val preferences = context.shenkPreferences.data.first()
        val deviceId = preferences[Keys.deviceId] ?: UUID.randomUUID().toString().also { generated ->
            context.shenkPreferences.edit { it[Keys.deviceId] = generated }
        }
        return SyncEndpointSettings(
            apiBase = preferences[Keys.apiBase].orEmpty(),
            timerUrl = preferences[Keys.timerUrl].orEmpty(),
            deviceId = deviceId,
        )
    }

    suspend fun setTimerUrl(value: String) {
        val normalized = value.trim().trimEnd('/')
        require(normalized.isEmpty() || normalized.startsWith("https://")) {
            "Timer URL must use HTTPS"
        }
        context.shenkPreferences.edit { preferences ->
            if (normalized.isEmpty()) preferences.remove(Keys.timerUrl) else preferences[Keys.timerUrl] = normalized
        }
    }

    suspend fun setApiBase(value: String) {
        val normalized = value.trim().trimEnd('/')
        require(normalized.isEmpty() || normalized.startsWith("https://")) {
            "API base must use HTTPS"
        }
        context.shenkPreferences.edit { preferences ->
            if (normalized.isEmpty()) preferences.remove(Keys.apiBase) else preferences[Keys.apiBase] = normalized
        }
    }

    internal val dataStore get() = context.shenkPreferences
}

enum class SecretName(val preferenceKey: String) {
    SHENK_TOKEN("secret_shenk_token"),
    TIMER_TOKEN("secret_timer_token"),
    AI_PROVIDER_KEY("secret_ai_provider_key"),
}

interface SecretStore {
    suspend fun put(name: SecretName, value: String)
    suspend fun get(name: SecretName): String?
    suspend fun remove(name: SecretName)
}

class KeystoreSecretStore(
    private val preferences: DevicePreferencesStore,
    private val cipher: SecretCipher = AndroidKeystoreCipher(),
) : SecretStore {
    override suspend fun put(name: SecretName, value: String) {
        require(value.isNotBlank()) { "secret must not be blank" }
        preferences.dataStore.edit { it[stringPreferencesKey(name.preferenceKey)] = cipher.encrypt(value) }
    }

    override suspend fun get(name: SecretName): String? {
        val encrypted = preferences.dataStore.data.first()[stringPreferencesKey(name.preferenceKey)] ?: return null
        return cipher.decrypt(encrypted)
    }

    override suspend fun remove(name: SecretName) {
        preferences.dataStore.edit { it.remove(stringPreferencesKey(name.preferenceKey)) }
    }
}

interface SecretCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(encrypted: String): String
}

class AndroidKeystoreCipher(
    private val alias: String = "shenk_device_secrets_v1",
) : SecretCipher {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return listOf(cipher.iv, cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
            .joinToString(".") { Base64.encodeToString(it, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING) }
    }

    override fun decrypt(encrypted: String): String {
        val parts = encrypted.split('.')
        require(parts.size == 2) { "encrypted secret is malformed" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }
}
