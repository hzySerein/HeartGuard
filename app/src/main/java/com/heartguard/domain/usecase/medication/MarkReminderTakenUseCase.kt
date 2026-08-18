package com.heartguard.domain.usecase.medication

import com.heartguard.data.local.AppDao
import com.heartguard.data.local.MedicationEntity
import com.heartguard.data.local.reminderTimes
import com.heartguard.reminder.ReminderScheduler
import javax.inject.Inject

class MarkReminderTakenUseCase @Inject constructor(
    private val appDao: AppDao,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend operator fun invoke(
        medication: MedicationEntity,
        matchedTime: String,
        todayEpochDay: Long,
    ): Result<MedicationEntity> {
        if (medication.isTaken) {
            return Result.success(medication)
        }

        return try {
            val updatedMedication = medication.copy(
                stockCount = (medication.stockCount - 1).coerceAtLeast(0),
            )

            reminderScheduler.cancelMedication(medication)
            appDao.updateMedication(updatedMedication)

            val reminderForScheduling = if (medication.scheduleId > 0L) {
                appDao.markMedicationTakenForTime(
                    scheduleId = medication.scheduleId,
                    takenDateEpochDay = todayEpochDay,
                    takenTime = matchedTime,
                    takenAtMillis = System.currentTimeMillis(),
                )
                val takenTimes = appDao.getTakenTimesForDay(
                    scheduleId = medication.scheduleId,
                    takenDateEpochDay = todayEpochDay,
                ).toSet()
                val allReminderTimesTaken = medication.reminderTimes()
                    .all { reminderTime -> reminderTime in takenTimes || "" in takenTimes }
                if (allReminderTimesTaken) {
                    appDao.setMedicationTakenForDay(
                        scheduleId = medication.scheduleId,
                        takenDateEpochDay = todayEpochDay,
                        isTaken = true,
                    )
                }
                updatedMedication.copy(isTaken = allReminderTimesTaken)
            } else {
                updatedMedication
            }

            reminderScheduler.scheduleMedication(reminderForScheduling)
            Result.success(reminderForScheduling)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
