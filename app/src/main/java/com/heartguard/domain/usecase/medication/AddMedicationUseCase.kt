package com.heartguard.domain.usecase.medication

import com.heartguard.data.local.AppDao
import com.heartguard.data.local.MEDICATION_REPEAT_DAILY
import com.heartguard.data.local.MedicationEntity
import com.heartguard.reminder.ReminderScheduler
import javax.inject.Inject

class AddMedicationUseCase @Inject constructor(
    private val appDao: AppDao,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend operator fun invoke(
        name: String,
        dosage: String,
        stockCount: Int,
        timesOfDay: String,
        note: String = "",
    ): Result<MedicationEntity> {
        val safeName = name.trim()
        val safeDosage = dosage.trim()
        val safeTimes = timesOfDay
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(",")

        if (safeName.isBlank() || safeDosage.isBlank() || safeTimes.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing required fields"))
        }

        return try {
            val medication = MedicationEntity(
                name = safeName,
                dosage = safeDosage,
                timesOfDay = safeTimes,
                note = note.trim(),
                isTaken = false,
                ringtoneUri = null,
                stockCount = stockCount,
                repeatType = MEDICATION_REPEAT_DAILY,
                repeatIntervalDays = 1,
            )
            val medicationId = appDao.insertMedication(medication)
            val savedMedication = appDao.getMedicationById(medicationId)
                ?: return Result.failure(IllegalStateException("Failed to retrieve saved medication"))
            reminderScheduler.scheduleMedication(savedMedication)
            Result.success(savedMedication)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
