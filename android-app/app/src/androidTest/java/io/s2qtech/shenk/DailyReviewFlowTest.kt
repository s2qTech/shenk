package io.s2qtech.shenk

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.s2qtech.shenk.model.TrainingLog
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.DailyReview
import io.s2qtech.shenk.sync.DailyReviewRepository
import io.s2qtech.shenk.sync.DailyReviewState
import io.s2qtech.shenk.sync.DevicePreferencesStore
import io.s2qtech.shenk.sync.LocalFirstRepository
import io.s2qtech.shenk.sync.SecretName
import io.s2qtech.shenk.sync.SecretStore
import io.s2qtech.shenk.sync.ShenkDatabase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyReviewFlowTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private lateinit var database: ShenkDatabase
    private lateinit var records: LocalFirstRepository
    private lateinit var repository: DailyReviewRepository
    private val date = LocalDate.of(2100, 1, 1)

    @Before
    fun setUp() {
        val context = composeRule.activity
        database = Room.inMemoryDatabaseBuilder(context, ShenkDatabase::class.java).build()
        records = LocalFirstRepository(database, localDeviceId = "synthetic-review-device")
        val secrets = object : SecretStore {
            override suspend fun get(name: SecretName): String? =
                if (name == SecretName.AI_PROVIDER_KEY) "synthetic-key" else null
            override suspend fun put(name: SecretName, value: String) = Unit
            override suspend fun remove(name: SecretName) = Unit
        }
        repository = DailyReviewRepository(database, records, DevicePreferencesStore(context), secrets)
    }

    @After fun tearDown() = database.close()

    @Test
    fun unrecordedDayShowsRequirementInsteadOfPartialGeneration() {
        show(DailyReviewState())
        composeRule.onNodeWithTag("daily-review-day-unrecorded").assertIsDisplayed()
        composeRule.onNodeWithText("按现有事实生成当日简评").assertDoesNotExist()
    }

    @Test
    fun completedReviewCanRegenerateWithExistingFacts() {
        runBlocking {
            CalendarRecordRepository(records).saveTrainingLog(
                TrainingLog("synthetic-rest", date.toString(), "rest", "rested", "manual"),
            )
        }
        show(DailyReviewState(review = review(), jobState = "COMPLETED"))
        composeRule.onNodeWithText("按现有事实重新生成简评").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(runBlocking { database.aiReviewJobs().findActive(date.toString()) != null })
    }

    @Test
    fun replacementProgressAndFailureRemainVisibleAlongsideOldReview() {
        runBlocking {
            CalendarRecordRepository(records).saveTrainingLog(
                TrainingLog("synthetic-rest", date.toString(), "rest", "rested", "manual"),
            )
        }
        show(DailyReviewState(review = review(), jobState = "AWAITING_SERVER"))
        composeRule.onNodeWithTag("daily-review-generating").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("按现有事实重新生成简评").assertDoesNotExist()
        show(DailyReviewState(review = review(), jobState = "FAILED"))
        composeRule.onNodeWithTag("daily-review-failed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("立即重试").assertIsDisplayed()
    }

    private fun show(state: DailyReviewState) {
        composeRule.activity.setContent {
            ShenkTheme {
                DailyReviewSheet(date, repository, state, onQueued = {}, onOpenAiSettings = {}, onMessage = {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun review() = DailyReview(
        "synthetic-review", date.toString(), 1, "generated", "当天已确认休息", "",
        emptyList(), emptyList(), emptyList(), null, "synthetic-digest", "deepseek", "deepseek-v4-flash", "2100-01-01T00:00:00Z",
    )
}
