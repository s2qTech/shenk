package io.s2qtech.shenk

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun foregroundChecksAreLimitedToOncePer24Hours() {
        val now = 2_000_000_000L
        assertTrue(shouldCheckForUpdate(null, now))
        assertFalse(shouldCheckForUpdate(now - TimeUnit.HOURS.toMillis(23), now))
        assertTrue(shouldCheckForUpdate(now - TimeUnit.HOURS.toMillis(24), now))
        assertTrue(shouldCheckForUpdate(now + 1, now))
    }

    @Test
    fun metadataIsStrictAndObjectStorageKeyIsNotRequiredOnDevice() {
        val release = parseAppUpdateMetadata(
            """{"ok":true,"release":{"applicationId":"io.s2qtech.shenk","versionCode":11,"versionName":"0.8.2-package8-p8.2","sha256":"${"a".repeat(64)}","sizeBytes":1234,"publishedAt":"2099-01-01T00:00:00Z"}}""",
        )
        assertEquals(11L, release?.versionCode)
        assertNull(parseAppUpdateMetadata("""{"ok":true,"release":null}"""))
        assertNull(parseAppUpdateMetadata("""{"release":{"applicationId":"other","versionCode":11}}"""))
    }

    @Test
    fun onlyIncreasingVersionForThisApplicationIsEligible() {
        val release = fixtureRelease(versionCode = 11)
        assertTrue(isEligibleUpdate(release, "io.s2qtech.shenk", 10))
        assertFalse(isEligibleUpdate(release, "io.s2qtech.shenk", 11))
        assertFalse(isEligibleUpdate(release, "other.application", 10))
    }

    @Test
    fun fileHashUsesSha256() {
        val file = File.createTempFile("shenk-update-fixture", ".apk")
        try {
            file.writeText("synthetic apk")
            assertEquals("11593c8d29c008fd058137096b3b1f6e469466dfc0bfaf51dbc4a06216cb3bf1", sha256(file))
        } finally {
            file.delete()
        }
    }

    private fun fixtureRelease(versionCode: Long) = AppUpdateRelease(
        applicationId = "io.s2qtech.shenk",
        versionCode = versionCode,
        versionName = "fixture",
        sha256 = "a".repeat(64),
        sizeBytes = 1,
        publishedAt = "2099-01-01T00:00:00Z",
    )
}
