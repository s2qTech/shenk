package io.s2qtech.shenk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val incomingPlanPatch = mutableStateOf<String?>(null)
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
                    incomingPlanPatch = incomingPlanPatch.value,
                    requestedSpace = requestedSpace.value,
                    onExternalRequestConsumed = {
                        incomingPlanPatch.value = null
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
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            incomingPlanPatch.value = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            requestedSpace.value = "plan"
            return
        }
        requestedSpace.value = intent?.getStringExtra(EXTRA_OPEN_SPACE)
    }

    companion object {
        const val EXTRA_OPEN_SPACE = "io.s2qtech.shenk.OPEN_SPACE"
    }
}
