package io.s2qtech.shenk

import android.content.Intent
import android.os.Bundle
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestedSpace = mutableStateOf<String?>(null)
    private val primaryUiReady = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as ShenkApplication
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !primaryUiReady.get() }
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_EXIT_DURATION_MILLIS)
                .withEndAction(provider::remove)
                .start()
        }
        super.onCreate(savedInstanceState)
        val timerCoordinator = app.nativeTimerCoordinator
        val timerOrientationController = TimerOrientationController(this)
        timerOrientationController.apply(timerCoordinator.snapshot.value.state)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                timerCoordinator.snapshot
                    .map { snapshot -> snapshot.state }
                    .distinctUntilChanged()
                    .collect { state ->
                        timerOrientationController.apply(state)
                    }
            }
        }
        lifecycleScope.launch {
            delay(PRIMARY_UI_STARTUP_TIMEOUT_MILLIS)
            primaryUiReady.set(true)
        }
        consumeIntent(intent)
        enableEdgeToEdge()
        setContent {
            val updateState by app.appUpdateManager.state.collectAsState()
            ShenkTheme {
                ShenkApp(
                    todayRepository = app.todayRepository,
                    calendarRepository = app.calendarRepository,
                    routineLibraryRepository = app.routineLibraryRepository,
                    timerSessionRepository = app.timerSessionRepository,
                    timerCoordinator = { timerCoordinator },
                    planCollaborationRepository = app.planCollaborationRepository,
                    reminderStore = app.reminderSettingsStore,
                    cloudConnectionManager = app.cloudConnectionManager,
                    requestedSpace = requestedSpace.value,
                    onExternalRequestConsumed = {
                        requestedSpace.value = null
                    },
                    onPrimaryPagesReady = { primaryUiReady.set(true) },
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
            val app = application as ShenkApplication
            app.restoreTimerAfterFirstFrame()
            app.appUpdateManager.checkAfterFirstFrame()
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
        private const val SPLASH_EXIT_DURATION_MILLIS = 220L
        private const val PRIMARY_UI_STARTUP_TIMEOUT_MILLIS = 5_000L
    }
}
