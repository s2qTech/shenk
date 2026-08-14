package io.s2qtech.shenk.sync

import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyReviewErrorTest {
    @Test
    fun workerErrorCodeIsPreservedWithoutResponseDetails() {
        assertEquals(
            "ai_provider_review_invalid",
            parseWorkerErrorCode("""{"ok":false,"error":"ai_provider_review_invalid"}"""),
        )
        assertNull(parseWorkerErrorCode("""{"error":"unsafe error text"}"""))
        assertNull(parseWorkerErrorCode("not-json"))
    }

    @Test
    fun generationTimeoutsUseAStableRetryableErrorCode() {
        assertEquals("generation_timeout", dailyReviewTransportErrorCode(SocketTimeoutException("timeout")))
        assertEquals("generation_timeout", dailyReviewTransportErrorCode(InterruptedIOException("call timeout")))
    }

    @Test
    fun connectionTestKeepsItsShortReadDeadline() {
        assertEquals(20L, AI_CONNECTION_TEST_READ_TIMEOUT_SECONDS)
    }
}
