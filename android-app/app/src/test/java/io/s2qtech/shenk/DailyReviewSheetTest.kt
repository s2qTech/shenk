package io.s2qtech.shenk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun providerFailureExplainsTheActionInsteadOfShowingGeneric502() {
        assertTrue(dailyReviewFailureMessage("ai_provider_http_402", retrying = false).contains("余额"))
        assertTrue(dailyReviewFailureMessage("ai_provider_review_invalid", retrying = true).contains("不完整"))
        assertTrue(dailyReviewFailureMessage("ai_provider_job_expired", retrying = false).contains("连接意外中断"))
        assertTrue(dailyReviewFailureMessage("ai_provider_job_abandoned", retrying = false).contains("可以重新尝试"))
    }

    @Test
    fun legacyEvidenceIsPresentedAsUserFacingChinese() {
        assertEquals(
            "计划时长为 25 分钟，实际记录时长为 1 小时。",
            humanizeDailyReviewEvidence("计划 estimatedMinutes 25，实际训练日志 durationSec=3600。"),
        )
        assertEquals(
            "8月17日状态记录：左侧小腿与踝部不适，程度 1/5。",
            humanizeDailyReviewEvidence("status_checkin 2026-08-17 记录 calf_ankle left severity 1。"),
        )
        assertEquals("8月13日轻松走4.21 公里。", humanizeDailyReviewEvidence("8月13日轻松走4.21km。"))
    }
}
