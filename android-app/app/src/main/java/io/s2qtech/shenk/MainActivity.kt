package io.s2qtech.shenk

import android.content.Intent
import android.os.Bundle
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val requestedSpace = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIntent(intent)
        enableEdgeToEdge()
        val app = application as ShenkApplication
        setContent {
            val updateState by app.appUpdateManager.state.collectAsState()
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
                AppUpdatePrompt(
                    state = updateState,
                    onDismiss = app.appUpdateManager::dismiss,
                    onDownload = app.appUpdateManager::download,
                    onInstall = app.appUpdateManager::openSystemInstaller,
                )
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        Choreographer.getInstance().postFrameCallback {
            (application as ShenkApplication).appUpdateManager.checkAfterFirstFrame()
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
