package com.heartguard.domain.usecase.medication

import com.heartguard.data.local.AppDao
import com.heartguard.reminder.ReminderScheduler
import javax.inject.Inject

class DeleteMedicationUseCase @Inject constructor(
    private val appDao: AppDao,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend operator fun invoke(medicationId: Long): Result<Unit> {
        return try {
            appDao.getMedicationById(medicationId)?.let { medication ->
                reminderScheduler.cancelMedication(medication)
            }
            appDao.deleteMedication(medicationId)
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
