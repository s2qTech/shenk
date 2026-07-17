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
                TodayRoute(
                    repository = app.todayRepository,
                    reminderStore = ReminderSettingsStore(this),
                )
            }
        }
    }
}
