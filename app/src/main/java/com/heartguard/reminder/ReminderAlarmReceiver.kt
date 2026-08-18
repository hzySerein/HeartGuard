package com.heartguard.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.heartguard.MainActivity
import com.heartguard.R
import com.heartguard.data.local.AppDatabase
import com.heartguard.data.local.MedicationEntity
import com.heartguard.data.local.currentMedicationEpochDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_MEDICATION_REMINDER -> handleMedicationReminder(context, intent)
            ACTION_REMIND_LATER -> handleRemindLater(context, intent)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_RESCHEDULE_MEDICATION_REMINDERS -> rescheduleMedicationReminders(context)
        }
    }

    private fun handleMedicationReminder(
        context: Context,
        intent: Intent,
    ) {
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1L)
        val reminderTime = intent.getStringExtra(EXTRA_MEDICATION_TIME).orEmpty()
        if (medicationId <= 0L || reminderTime.isBlank()) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val medication = AppDatabase.getInstance(appContext)
                    .appDao()
                    .getMedicationByIdForDay(
                        id = medicationId,
                        targetEpochDay = currentMedicationEpochDay(),
                    ) ?: return@launch

                val isReminderTimeTaken = AppDatabase.getInstance(appContext)
                    .appDao()
                    .isMedicationTakenAtTime(
                        medicationId = medication.id,
                        takenDateEpochDay = currentMedicationEpochDay(),
                        takenTime = reminderTime,
                    )
                if (medication.reminderEnabled && !isReminderTimeTaken) {
                    showMedicationReminderNotification(
                        context = appContext,
                        medication = medication,
                        reminderTime = reminderTime,
                    )
                }
                ReminderScheduler.scheduleMedicationTimeAfter(
                    context = appContext,
                    medication = medication,
                    reminderTime = reminderTime,
                    afterMillis = System.currentTimeMillis() + RESCHEDULE_AFTER_FIRE_DELAY_MILLIS,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleRemindLater(
        context: Context,
        intent: Intent,
    ) {
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1L)
        val fallbackMedicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME).orEmpty().ifBlank {
            context.getString(R.string.reminder_notification_fallback_title)
        }
        val fallbackMedicationDosage = intent.getStringExtra(EXTRA_MEDICATION_DOSAGE).orEmpty()
        val reminderTime = intent.getStringExtra(EXTRA_MEDICATION_TIME).orEmpty()

        if (medicationId <= 0L) {
            showReminderNotification(
                context = context,
                medicationId = medicationId,
                medicationName = fallbackMedicationName,
                medicationDosage = fallbackMedicationDosage,
                reminderTime = reminderTime,
                titleBuilder = { name -> context.getString(R.string.reminder_later_title, name) },
            )
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val medication = AppDatabase.getInstance(appContext)
                    .appDao()
                    .getMedicationByIdForDay(
                        id = medicationId,
                        targetEpochDay = currentMedicationEpochDay(),
                    ) ?: return@launch

                val isReminderTimeTaken = AppDatabase.getInstance(appContext)
                    .appDao()
                    .isMedicationTakenAtTime(
                        medicationId = medication.id,
                        takenDateEpochDay = currentMedicationEpochDay(),
                        takenTime = reminderTime,
                    )
                if (medication.reminderEnabled && !isReminderTimeTaken) {
                    showReminderNotification(
                        context = appContext,
                        medicationId = medication.id,
                        medicationName = medication.name,
                        medicationDosage = medication.dosage,
                        reminderTime = reminderTime,
                        titleBuilder = { name -> appContext.getString(R.string.reminder_later_title, name) },
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun rescheduleMedicationReminders(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val medications = AppDatabase.getInstance(appContext)
                    .appDao()
                    .getAllScheduledMedicationsForReminder(
                        targetEpochDay = currentMedicationEpochDay(),
                    )
                ReminderScheduler.scheduleMedications(appContext, medications)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showMedicationReminderNotification(
        context: Context,
        medication: MedicationEntity,
        reminderTime: String,
    ) {
        showReminderNotification(
            context = context,
            medicationId = medication.id,
            medicationName = medication.name,
            medicationDosage = medication.dosage,
            reminderTime = reminderTime,
            titleBuilder = { name -> context.getString(R.string.medication_reminder_due, name) },
        )
    }

    private fun showReminderNotification(
        context: Context,
        medicationId: Long,
        medicationName: String,
        medicationDosage: String,
        reminderTime: String,
        titleBuilder: (String) -> String,
    ) {
        createNotificationChannel(context)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(medicationId, medicationName, reminderTime),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_MEDICATION_ID, medicationId)
                putExtra(EXTRA_MEDICATION_NAME, medicationName)
                putExtra(EXTRA_MEDICATION_TIME, reminderTime)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationText = buildNotificationText(context, medicationDosage, reminderTime)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titleBuilder(medicationName))
            .setContentText(notificationText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(notificationText),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId(medicationId, medicationName, reminderTime), notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotificationText(
        context: Context,
        dosage: String,
        reminderTime: String,
    ): String {
        val parts = listOfNotNull(
            dosage.takeIf { it.isNotBlank() }?.let { context.getString(R.string.reminder_dosage, it) },
            reminderTime.takeIf { it.isNotBlank() }?.let { context.getString(R.string.reminder_original_time, it) },
        )
        return if (parts.isEmpty()) {
            context.getString(R.string.reminder_default_content)
        } else {
            context.getString(
                R.string.reminder_content_with_parts,
                parts.joinToString(context.getString(R.string.reminder_part_separator)),
            )
        }
    }

    private fun notificationId(
        medicationId: Long,
        medicationName: String,
        reminderTime: String,
    ): Int {
        var result = 17
        result = 31 * result + if (medicationId > 0L) medicationId.hashCode() else medicationName.hashCode()
        result = 31 * result + reminderTime.hashCode()
        return result
    }

    companion object {
        const val ACTION_MEDICATION_REMINDER = "com.heartguard.reminder.ACTION_MEDICATION_REMINDER"
        const val ACTION_REMIND_LATER = "com.heartguard.reminder.ACTION_REMIND_LATER"
        const val ACTION_RESCHEDULE_MEDICATION_REMINDERS =
            "com.heartguard.reminder.ACTION_RESCHEDULE_MEDICATION_REMINDERS"
        const val EXTRA_MEDICATION_ID = "extra_medication_id"
        const val EXTRA_MEDICATION_NAME = "extra_medication_name"
        const val EXTRA_MEDICATION_DOSAGE = "extra_medication_dosage"
        const val EXTRA_MEDICATION_TIME = "extra_medication_time"

        private const val CHANNEL_ID = "medication_reminders"
        private const val RESCHEDULE_AFTER_FIRE_DELAY_MILLIS = 60_000L
    }
}
