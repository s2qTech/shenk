package io.s2qtech.shenk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ShenkApplication
        setContent {
            ShenkTheme {
                ShenkApp(
                    todayRepository = app.todayRepository,
                    calendarRepository = app.calendarRepository,
                    routineLibraryRepository = app.routineLibraryRepository,
                    timerSessionRepository = app.timerSessionRepository,
                    timerCoordinator = app.nativeTimerCoordinator,
                    reminderStore = ReminderSettingsStore(this),
                    cloudConnectionManager = app.cloudConnectionManager,
                )
            }
        }
    }
}
