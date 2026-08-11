package io.s2qtech.shenk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyReviewSheetTest {
    @Test
    fun readyCompleteInputStartsFromTheFirstVisibleGenerateAction() {
        assertTrue(
            shouldAutoStartDailyReview(
                preparationLoaded = true,
                providerReady = true,
                missing = emptyList(),
                reviewPresent = false,
                jobState = null,
                attempted = false,
            ),
        )
    }

    @Test
    fun missingInputStillRequiresExplicitPartialGeneration() {
        assertFalse(
            shouldAutoStartDailyReview(
                preparationLoaded = true,
                providerReady = true,
                missing = listOf("疲劳"),
                reviewPresent = false,
                jobState = null,
                attempted = false,
            ),
        )
    }

    @Test
    fun existingOrRunningReviewIsNeverAutoQueuedAgain() {
        assertFalse(
            shouldAutoStartDailyReview(true, true, emptyList(), reviewPresent = true, jobState = null, attempted = false),
        )
        assertFalse(
            shouldAutoStartDailyReview(true, true, emptyList(), reviewPresent = false, jobState = "PENDING", attempted = false),
        )
    }
}
