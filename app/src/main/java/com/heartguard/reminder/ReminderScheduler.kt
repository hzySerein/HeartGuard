package com.heartguard.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.heartguard.data.local.MEDICATION_REPEAT_CUSTOM
import com.heartguard.data.local.MEDICATION_REPEAT_DAILY
import com.heartguard.data.local.MEDICATION_REPEAT_EVERY_OTHER_DAY
import com.heartguard.data.local.MEDICATION_REPEAT_ONCE
import com.heartguard.data.local.MedicationEntity
import com.heartguard.data.local.normalizeMedicationRepeatIntervalDays
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {
    private const val LOOKAHEAD_DAYS = 370

    fun scheduleMedications(
        context: Context,
        medications: List<MedicationEntity>,
    ) {
        medications.forEach { medication ->
            scheduleMedication(context, medication)
        }
    }

    fun scheduleMedication(
        context: Context,
        medication: MedicationEntity,
    ) {
        if (!medication.reminderEnabled) {
            return
        }
        medication.reminderTimes().forEach { reminderTime ->
            scheduleMedicationTimeAfter(
                context = context,
                medication = medication,
                reminderTime = reminderTime,
                afterMillis = System.currentTimeMillis(),
            )
        }
    }

    fun scheduleMedicationTimeAfter(
        context: Context,
        medication: MedicationEntity,
        reminderTime: String,
        afterMillis: Long,
    ) {
        val triggerAtMillis = nextTriggerAtMillis(
            medication = medication,
            reminderTime = reminderTime,
            afterMillis = afterMillis,
        ) ?: return

        val pendingIntent = medicationPendingIntent(
            context = context,
            medicationId = medication.id,
            medicationName = medication.name,
            medicationDosage = medication.dosage,
            reminderTime = reminderTime,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        scheduleAlarm(context, triggerAtMillis, pendingIntent)
    }

    fun cancelMedication(
        context: Context,
        medication: MedicationEntity,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        medication.reminderTimes().forEach { reminderTime ->
            val pendingIntent = medicationPendingIntent(
                context = context,
                medicationId = medication.id,
                medicationName = medication.name,
                medicationDosage = medication.dosage,
                reminderTime = reminderTime,
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return@forEach
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(
        context: Context,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun medicationPendingIntent(
        context: Context,
        medicationId: Long,
        medicationName: String,
        medicationDosage: String,
        reminderTime: String,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_MEDICATION_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_MEDICATION_ID, medicationId)
            putExtra(ReminderAlarmReceiver.EXTRA_MEDICATION_NAME, medicationName)
            putExtra(ReminderAlarmReceiver.EXTRA_MEDICATION_DOSAGE, medicationDosage)
            putExtra(ReminderAlarmReceiver.EXTRA_MEDICATION_TIME, reminderTime)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(medicationId, reminderTime),
            intent,
            flags,
        )
    }

    private fun nextTriggerAtMillis(
        medication: MedicationEntity,
        reminderTime: String,
        afterMillis: Long,
    ): Long? {
        if (medication.id <= 0L) {
            return null
        }
        val time = parseReminderTime(reminderTime) ?: return null
        val zoneId = ZoneId.systemDefault()
        val afterDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(afterMillis),
            zoneId,
        )
        val todayEpochDay = LocalDate.now(zoneId).toEpochDay()

        repeat(LOOKAHEAD_DAYS) { dayOffset ->
            val date = afterDateTime.toLocalDate().plusDays(dayOffset.toLong())
            val dateEpochDay = date.toEpochDay()
            if (!medication.isDueOn(dateEpochDay)) {
                return@repeat
            }
            if (medication.isTaken && dateEpochDay == todayEpochDay) {
                return@repeat
            }

            val triggerDateTime = date.atTime(time)
            if (triggerDateTime.isAfter(afterDateTime)) {
                return triggerDateTime
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
            }
        }
        return null
    }

    private fun MedicationEntity.isDueOn(epochDay: Long): Boolean {
        if (epochDay < startDateEpochDay) {
            return false
        }
        val daysSinceStart = epochDay - startDateEpochDay
        return when (repeatType) {
            MEDICATION_REPEAT_DAILY -> true
            MEDICATION_REPEAT_EVERY_OTHER_DAY -> daysSinceStart % 2L == 0L
            MEDICATION_REPEAT_CUSTOM -> {
                val interval = normalizeMedicationRepeatIntervalDays(
                    repeatType = repeatType,
                    intervalDays = repeatIntervalDays,
                ).toLong()
                daysSinceStart % interval == 0L
            }
            MEDICATION_REPEAT_ONCE -> epochDay == startDateEpochDay
            else -> epochDay == startDateEpochDay
        }
    }

    private fun MedicationEntity.reminderTimes(): List<String> {
        return timesOfDay
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun parseReminderTime(text: String): LocalTime? {
        val parts = text.trim().split(":")
        if (parts.size != 2) {
            return null
        }
        val hour = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: return null
        val minute = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: return null
        return LocalTime.of(hour, minute)
    }

    private fun requestCode(
        medicationId: Long,
        reminderTime: String,
    ): Int {
        var result = 17
        result = 31 * result + medicationId.hashCode()
        result = 31 * result + reminderTime.hashCode()
        result = 31 * result + ReminderAlarmReceiver.ACTION_MEDICATION_REMINDER.hashCode()
        return result
    }
}
