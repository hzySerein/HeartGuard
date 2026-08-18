package com.heartguard.domain.usecase.medication

import com.heartguard.data.local.AppDao
import com.heartguard.data.local.currentMedicationEpochDay
import com.heartguard.reminder.ReminderScheduler
import javax.inject.Inject

class ScheduleRemindersUseCase @Inject constructor(
    private val appDao: AppDao,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend operator fun invoke(): Result<Int> {
        return try {
            val todayEpochDay = currentMedicationEpochDay()
            val medications = appDao.getAllScheduledMedicationsForReminder(todayEpochDay)
            reminderScheduler.scheduleMedications(medications)
            Result.success(medications.size)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
