package io.s2qtech.shenk.sync

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
}
