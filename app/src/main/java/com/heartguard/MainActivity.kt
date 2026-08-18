package com.heartguard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.heartguard.reminder.ReminderAlarmReceiver
import com.heartguard.reminder.ReminderLaunchEvent
import com.heartguard.ui.screens.MainScreen
import com.heartguard.ui.screens.SplashScreen
import com.heartguard.ui.theme.HeartGuardTheme
import com.heartguard.utils.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsManager: SettingsManager

    private var pendingReminderLaunchEvent by mutableStateOf<ReminderLaunchEvent?>(null)
    private var pendingEmergencyLaunchEventId by mutableStateOf<Long?>(null)
    private val showSplash = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        try {
            pendingReminderLaunchEvent = intent.toReminderLaunchEvent()
            pendingEmergencyLaunchEventId = intent.toEmergencyLaunchEventId()
            Log.d(TAG, "Intent processing completed")

            // Use View.postDelayed to guarantee splash disappears regardless of Compose state.
            // This avoids the original bug where a MainScreen composition failure prevented
            // the LaunchedEffect from executing, leaving the splash stuck forever.
            window.decorView.postDelayed({
                showSplash.value = false
                Log.d(TAG, "Splash hidden by postDelayed")
            }, SPLASH_VISIBLE_MILLIS)

            setContent {
                Log.d(TAG, "setContent started")
                val fontSize by settingsManager.fontSize.collectAsState()
                Log.d(TAG, "fontSize collected: $fontSize")
                val splashVisible by showSplash

                HeartGuardTheme(
                    fontSize = fontSize,
                ) {
                    Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    ) {
                        Log.d(TAG, "About to create MainScreen")
                        MainScreen(
                            settingsManager = settingsManager,
                            initialReminderLaunchEvent = pendingReminderLaunchEvent,
                            initialEmergencyLaunchEventId = pendingEmergencyLaunchEventId,
                        )
                        Log.d(TAG, "MainScreen created")

                        if (splashVisible) {
                            SplashScreen()
                        }
                    }
                }
            }
            Log.d(TAG, "setContent completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            throw e
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
        private const val TAG = "MainActivity"
        const val ACTION_OPEN_EMERGENCY = "com.heartguard.action.OPEN_EMERGENCY"
        private const val SPLASH_VISIBLE_MILLIS = 900L
    }
}
