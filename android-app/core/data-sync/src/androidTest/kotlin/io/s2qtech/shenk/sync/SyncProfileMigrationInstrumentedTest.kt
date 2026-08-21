package io.s2qtech.shenk.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncProfileMigrationInstrumentedTest {
    @Test
    fun encryptedProfileUsesWebCompatibleContractOnAndroidCryptoProvider() {
        val crypto = SyncProfileCrypto()
        val migrationCode = crypto.generateMigrationCode()
        val payload = SyncProfilePayload(
            apiBase = "https://example.invalid/api",
            timerUrl = "https://timer.example.invalid/",
            token = UUID.randomUUID().toString(),
            timerToken = UUID.randomUUID().toString(),
        )

        val encrypted = crypto.encrypt(payload, migrationCode)

        assertEquals("shenk_sync_profile/v1", encrypted.schema)
        assertEquals("AES-GCM", encrypted.cipher)
        assertEquals("PBKDF2-SHA256", encrypted.kdf)
        assertEquals(210_000, encrypted.iterations)
        assertNotEquals(payload.token, encrypted.ciphertext)
        assertEquals(payload, crypto.decrypt(encrypted, migrationCode))
        assertThrows(Exception::class.java) {
            crypto.decrypt(encrypted, crypto.generateMigrationCode())
        }
    }
}
