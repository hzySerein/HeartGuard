package com.heartguard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.heartguard.data.local.AppDatabase
import com.heartguard.reminder.ReminderAlarmReceiver
import com.heartguard.reminder.ReminderLaunchEvent
import com.heartguard.ui.screens.MainScreen
import com.heartguard.ui.screens.SplashScreen
import com.heartguard.ui.theme.HeartGuardTheme
import com.heartguard.utils.SettingsManager
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var pendingReminderLaunchEvent by mutableStateOf<ReminderLaunchEvent?>(null)
    private var pendingEmergencyLaunchEventId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appDao = AppDatabase.getInstance(applicationContext).appDao()
        SettingsManager.initialize(applicationContext)
        pendingReminderLaunchEvent = intent.toReminderLaunchEvent()
        pendingEmergencyLaunchEventId = intent.toEmergencyLaunchEventId()

        setContent {
            val fontSize by SettingsManager.fontSize.collectAsState()
            HeartGuardTheme(
                fontSize = fontSize,
            ) {
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(SPLASH_VISIBLE_MILLIS)
                    showSplash = false
                }

                Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                ) {
                    MainScreen(
                        appDao = appDao,
                        settingsManager = SettingsManager,
                        initialReminderLaunchEvent = pendingReminderLaunchEvent,
                        initialEmergencyLaunchEventId = pendingEmergencyLaunchEventId,
                    )

                    if (showSplash) {
                        SplashScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingReminderLaunchEvent = intent.toReminderLaunchEvent()
        pendingEmergencyLaunchEventId = intent.toEmergencyLaunchEventId()
    }

    private fun Intent?.toEmergencyLaunchEventId(): Long? {
        return if (this?.action == ACTION_OPEN_EMERGENCY) {
            System.nanoTime()
        } else {
            null
        }
    }

    private fun Intent?.toReminderLaunchEvent(): ReminderLaunchEvent? {
        val medicationId = this?.getLongExtra(ReminderAlarmReceiver.EXTRA_MEDICATION_ID, -1L) ?: -1L
        val medicationName = this?.getStringExtra(ReminderAlarmReceiver.EXTRA_MEDICATION_NAME).orEmpty()
        val medicationTime = this?.getStringExtra(ReminderAlarmReceiver.EXTRA_MEDICATION_TIME).orEmpty()
        if (medicationId <= 0L && medicationName.isBlank()) {
            return null
        }

        return ReminderLaunchEvent(
            itemName = medicationName.ifBlank {
                getString(R.string.reminder_notification_fallback_title)
            },
            reminderId = medicationId,
            matchedTime = medicationTime,
        )
    }

    companion object {
        const val ACTION_OPEN_EMERGENCY = "com.heartguard.action.OPEN_EMERGENCY"
        private const val SPLASH_VISIBLE_MILLIS = 900L
    }
}
