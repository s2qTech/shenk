package io.s2qtech.shenk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val requestedSpace = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIntent(intent)
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
                    planCollaborationRepository = app.planCollaborationRepository,
                    reminderStore = ReminderSettingsStore(this),
                    cloudConnectionManager = app.cloudConnectionManager,
                    requestedSpace = requestedSpace.value,
                    onExternalRequestConsumed = {
                        requestedSpace.value = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        requestedSpace.value = intent?.getStringExtra(EXTRA_OPEN_SPACE)
    }

    companion object {
        const val EXTRA_OPEN_SPACE = "io.s2qtech.shenk.OPEN_SPACE"
    }
}
