package io.s2qtech.shenk.sync

import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncProfileCryptoTest {
    @Test
    fun webCompatibleProfileShapeRoundTripsWithoutPlaintext() {
        val crypto = SyncProfileCrypto()
        val code = crypto.generateMigrationCode()
        val payload = SyncProfilePayload(
            apiBase = "https://example.invalid/api",
            timerUrl = "https://example.invalid/timer/",
            token = UUID.randomUUID().toString(),
            timerToken = UUID.randomUUID().toString(),
        )

        val profile = crypto.encrypt(payload, code)

        assertEquals("shenk_sync_profile/v1", profile.schema)
        assertEquals("AES-GCM", profile.cipher)
        assertEquals("PBKDF2-SHA256", profile.kdf)
        assertEquals(210_000, profile.iterations)
        assertNotEquals(payload.token, profile.ciphertext)
        assertEquals(payload, crypto.decrypt(profile, code))
        assertEquals(crypto.deriveProfileId(code), crypto.deriveProfileId(code))
        assertThrows(Exception::class.java) { crypto.decrypt(profile, crypto.generateMigrationCode()) }
    }

    @Test
    fun gatewayUsesMigrationCodeForWebCompatibleCloudProfile() = runBlocking {
        val crypto = SyncProfileCrypto()
        val migrationCode = crypto.generateMigrationCode()
        val payload = payload()
        val api = FakeProfileApi()
        val gateway = SyncProfileGateway(api, crypto)

        val profileId = gateway.save(migrationCode, UUID.randomUUID().toString(), "synthetic-device", payload)
        api.getResponse = JsonObject(mapOf("profile" to requireNotNull(api.savedBody?.get("profile")).jsonObject))

        assertEquals(crypto.deriveProfileId(migrationCode), profileId)
        assertEquals(payload, gateway.load(migrationCode))
        assertEquals(migrationCode, api.lastProfileAccessKey)
    }

    private fun payload() = SyncProfilePayload(
        apiBase = "https://example.invalid/api",
        timerUrl = "https://example.invalid/timer/",
        token = UUID.randomUUID().toString(),
        timerToken = UUID.randomUUID().toString(),
    )

    private class FakeProfileApi : SyncProfileRemoteApi {
        var savedBody: JsonObject? = null
        var getResponse: JsonObject = JsonObject(emptyMap())
        var lastProfileAccessKey: String? = null

        override suspend fun get(profileId: String, profileAccessKey: String): JsonObject {
            lastProfileAccessKey = profileAccessKey
            return getResponse
        }

        override suspend fun put(profileId: String, authorization: String, body: JsonObject): JsonObject {
            savedBody = body
            return JsonObject(emptyMap())
        }
    }
}
