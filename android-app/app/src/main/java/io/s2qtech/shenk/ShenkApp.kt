package io.s2qtech.shenk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.TodayRecordRepository
import kotlinx.coroutines.launch

private enum class SecondarySpace { RECORDS, DATA }

@Composable
fun ShenkApp(
    todayRepository: TodayRecordRepository,
    calendarRepository: CalendarRecordRepository,
    reminderStore: ReminderSettingsStore,
) {
    val pager = rememberPagerState(initialPage = 1, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    var secondary by remember { mutableStateOf<SecondarySpace?>(null) }

    Box(Modifier.fillMaxSize()) {
        when (secondary) {
            SecondarySpace.RECORDS -> RecordsScreen(
                repository = calendarRepository,
                onBack = { secondary = null },
            )
            SecondarySpace.DATA -> DataScreen(
                repository = calendarRepository,
                onBack = { secondary = null },
            )
            null -> HorizontalPager(state = pager, beyondViewportPageCount = 1) { page ->
                when (page) {
                    0 -> CalendarScreen(
                        repository = calendarRepository,
                        onToday = { scope.launch { pager.animateScrollToPage(1) } },
                        onRecords = { secondary = SecondarySpace.RECORDS },
                        onData = { secondary = SecondarySpace.DATA },
                    )
                    else -> TodayRoute(
                        repository = todayRepository,
                        reminderStore = reminderStore,
                        onCalendar = { scope.launch { pager.animateScrollToPage(0) } },
                        onRecords = { secondary = SecondarySpace.RECORDS },
                        onData = { secondary = SecondarySpace.DATA },
                    )
                }
            }
        }
    }
}
