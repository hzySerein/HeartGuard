package com.heartguard.viewmodel

import com.heartguard.data.local.AppDao
import com.heartguard.data.local.ChatEntity
import com.heartguard.data.local.FraudRecordEntity
import com.heartguard.data.local.MedicationEntity
import com.heartguard.data.local.MedicationRecordEntity
import com.heartguard.data.local.MedicationScheduleEntity
import com.heartguard.data.local.MedicationTakenLogEntity
import com.heartguard.data.local.MEDICATION_REPEAT_CUSTOM
import com.heartguard.data.local.MEDICATION_REPEAT_DAILY
import com.heartguard.data.local.MEDICATION_REPEAT_EVERY_OTHER_DAY
import com.heartguard.data.local.MEDICATION_REPEAT_ONCE
import com.heartguard.data.local.normalizeMedicationRepeatIntervalDays
import com.heartguard.data.local.repeatIntervalDaysFromMedicationNote
import com.heartguard.data.local.repeatTypeFromMedicationNote
import com.heartguard.data.remote.AiChatMessage
import com.heartguard.data.remote.AiGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class MedicationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun addMedicationFromVoice_savesMedication() = runTest {
        val appDao = FakeMedicationAppDao()
        val viewModel = MedicationViewModel(
            appDao = appDao,
            aiGateway = MedicationAiGateway(
                singleResponse = """{"name":"维生素","dosage":"2片","time":"08:00","note":"饭后"}""",
            ),
            enableReminderLoop = false,
        )

        advanceUntilIdle()
        viewModel.addMedicationFromVoice("早上八点吃两片维生素")
        advanceUntilIdle()

        val medications = viewModel.medications.first()
        assertTrue(medications.any { it.name == "维生素" && it.dosage == "2片" && it.timesOfDay == "08:00" })
        assertFalse(viewModel.isImageImporting.first())
    }

    @Test
    fun addMedicationFromVoice_whenJsonInvalid_updatesFailureStatus() = runTest {
        val viewModel = MedicationViewModel(
            appDao = FakeMedicationAppDao(),
            aiGateway = MedicationAiGateway(singleResponse = "not-json"),
            enableReminderLoop = false,
        )

        advanceUntilIdle()
        viewModel.addMedicationFromVoice("添加药")
        advanceUntilIdle()

        assertFalse(viewModel.isImageImporting.first())
    }

    @Test
    fun updateAndDeleteMedication_refreshesMedicationList() = runTest {
        val appDao = FakeMedicationAppDao()
        appDao.insertMedication(
            MedicationEntity(
                name = "维生素",
                dosage = "1片",
                timesOfDay = "08:00",
                note = "饭后",
                isTaken = false,
                stockCount = 30,
            )
        )
        val viewModel = MedicationViewModel(
            appDao = appDao,
            aiGateway = MedicationAiGateway(),
            enableReminderLoop = false,
        )

        advanceUntilIdle()
        val initialMedication = viewModel.medications.first().first()

        viewModel.updateMedication(
            initialMedication.copy(
                name = "钙片",
                dosage = "2片",
                timesOfDay = "08:00,12:00,18:00",
                stockCount = 20,
            )
        )
        advanceUntilIdle()

        val updatedMedication = viewModel.medications.first().first()
        assertEquals("钙片", updatedMedication.name)
        assertEquals("08:00,12:00,18:00", updatedMedication.timesOfDay)

        viewModel.deleteMedication(updatedMedication.id)
        advanceUntilIdle()

        assertTrue(viewModel.medications.first().isEmpty())
    }

    @Test
    fun markReminderTaken_updatesTakenStatusAndDecrementsStock() = runTest {
        val appDao = FakeMedicationAppDao()
        appDao.insertMedication(
            MedicationEntity(
                name = "降压药",
                dosage = "1片",
                timesOfDay = "08:00",
                note = "温水送服",
                isTaken = false,
                stockCount = 1,
            )
        )
        val viewModel = MedicationViewModel(
            appDao = appDao,
            aiGateway = MedicationAiGateway(),
            enableReminderLoop = false,
        )

        advanceUntilIdle()
        viewModel.triggerReminderTest()
        advanceUntilIdle()
        viewModel.markReminderTaken()
        advanceUntilIdle()

        val updatedMedication = viewModel.medications.first().first()
        assertTrue(updatedMedication.isTaken)
        assertEquals(0, updatedMedication.stockCount)
    }

    @Test
    fun init_whenDatabaseEmpty_doesNotInsertMockMedication() = runTest {
        val viewModel = MedicationViewModel(
            appDao = FakeMedicationAppDao(),
            aiGateway = MedicationAiGateway(),
            enableReminderLoop = false,
        )

        advanceUntilIdle()

        assertTrue(viewModel.medications.first().isEmpty())
    }

    @Test
    fun todayMedications_keepsRepeatingPreviousDayRecords() = runTest {
        val appDao = FakeMedicationAppDao()
        val yesterday = LocalDate.now()
            .minusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        appDao.insertMedication(
            MedicationEntity(
                name = "昨天的历史药",
                dosage = "1片",
                timesOfDay = "08:00",
                note = "历史记录",
                isTaken = false,
                stockCount = 30,
                createdAt = yesterday,
            )
        )
        appDao.insertMedication(
            MedicationEntity(
                name = "昨天的每日药",
                dosage = "1片",
                timesOfDay = "09:00",
                note = "重复：每天；样式：胶囊",
                isTaken = false,
                stockCount = 30,
                createdAt = yesterday,
            )
        )
        appDao.insertMedication(
            MedicationEntity(
                name = "今天的药",
                dosage = "1片",
                timesOfDay = "08:00",
                note = "今日记录",
                isTaken = false,
                stockCount = 30,
            )
        )
        val viewModel = MedicationViewModel(
            appDao = appDao,
            aiGateway = MedicationAiGateway(),
            enableReminderLoop = false,
        )

        advanceUntilIdle()

        val medications = viewModel.medications.first()
        assertTrue(medications.any { it.name == "今天的药" })
        assertTrue(medications.any { it.name == "昨天的每日药" })
        assertFalse(medications.any { it.name == "昨天的历史药" })
    }

    @Test
    fun importMedicationsFromOcrText_savesAllRecognizedMedications() = runTest {
        val appDao = FakeMedicationAppDao()
        val viewModel = MedicationViewModel(
            appDao = appDao,
            aiGateway = MedicationAiGateway(
                batchResponse = """[{"name":"降压药","dosage":"1片","timesOfDay":"08:00,20:00","note":"温水送服"},{"name":"钙片","dosage":"1片","timesOfDay":"12:00","note":"随餐"}]""",
            ),
            enableReminderLoop = false,
        )

        advanceUntilIdle()
        viewModel.importMedicationsFromOcrText("OCR 药品文字")
        advanceUntilIdle()

        val medications = viewModel.medications.first()
        assertTrue(medications.any { it.name == "降压药" && it.timesOfDay == "08:00,20:00" })
        assertTrue(medications.any { it.name == "钙片" && it.timesOfDay == "12:00" })
    }
}

private class MedicationAiGateway(
    private val singleResponse: String = "{}",
    private val batchResponse: String = "[]",
) : AiGateway {
    override suspend fun recognizeSpeech(audioFile: File): String = ""

    override suspend fun getChatResponse(
        userText: String,
        messages: List<AiChatMessage>,
        systemPrompt: String?,
    ): String {
        return if (userText.contains("OCR 文本")) batchResponse else singleResponse
    }

    override suspend fun textToSpeech(text: String): String = ""
}

private class FakeMedicationAppDao : AppDao() {
    private val medications = mutableListOf<MedicationEntity>()
    private val medicationFlow = MutableStateFlow<List<MedicationEntity>>(emptyList())
    private var observedStartOfDay = Long.MIN_VALUE
    private var observedEndOfDay = Long.MAX_VALUE
    private var observedEpochDay = Long.MIN_VALUE

    override suspend fun insertChatMessage(message: ChatEntity) = Unit

    override suspend fun getRecentChatMessages(): List<ChatEntity> = emptyList()

    override suspend fun insertFraudRecord(record: FraudRecordEntity) = Unit

    override suspend fun getFraudPassRate(): Double? = null

    override fun observeFraudRecordCountSince(startTimeMillis: Long): Flow<Int> = MutableStateFlow(0)

    override suspend fun insertMedication(medication: MedicationEntity): Long {
        val nextId = if (medication.id == 0L) medications.size.toLong() + 1L else medication.id
        check(medications.none { it.id == nextId }) { "Medication already exists: $nextId" }
        val noteRepeatType = repeatTypeFromMedicationNote(medication.note)
        val repeatType = noteRepeatType
            .takeIf { it != MEDICATION_REPEAT_ONCE }
            ?: medication.repeatType
        val repeatIntervalDays = if (noteRepeatType != MEDICATION_REPEAT_ONCE) {
            repeatIntervalDaysFromMedicationNote(medication.note)
        } else {
            medication.repeatIntervalDays
        }
        medications += medication.copy(
            id = nextId,
            scheduleId = medication.scheduleId.takeIf { it > 0L } ?: nextId,
            repeatType = repeatType,
            repeatIntervalDays = normalizeMedicationRepeatIntervalDays(
                repeatType = repeatType,
                intervalDays = repeatIntervalDays,
            ),
        )
        publishObservedMedications()
        return nextId
    }

    override suspend fun insertMedicationRecord(medication: MedicationRecordEntity): Long = medication.id

    override suspend fun insertMedicationSchedule(schedule: MedicationScheduleEntity): Long = schedule.id

    override fun observeTodayMedications(
        startOfDay: Long,
        endOfDay: Long,
        targetEpochDay: Long,
    ): Flow<List<MedicationEntity>> {
        observedStartOfDay = startOfDay
        observedEndOfDay = endOfDay
        observedEpochDay = targetEpochDay
        publishObservedMedications()
        return medicationFlow
    }

    override suspend fun getTodayMedications(
        startOfDay: Long,
        endOfDay: Long,
        targetEpochDay: Long,
    ): List<MedicationEntity> {
        return medications
            .filter { medication -> medication.isVisibleForDay(startOfDay, endOfDay, targetEpochDay) }
            .sortedBy { it.timesOfDay }
    }

    override suspend fun getTodayPendingMedicationCount(
        startOfDay: Long,
        endOfDay: Long,
        targetEpochDay: Long,
    ): Int {
        return medications.count { medication ->
            medication.isVisibleForDay(startOfDay, endOfDay, targetEpochDay) && !medication.isTaken
        }
    }

    override suspend fun getAllScheduledMedicationsForReminder(
        targetEpochDay: Long,
    ): List<MedicationEntity> = medications

    override suspend fun updateMedicationTakenStatus(id: Long, isTaken: Boolean) {
        val index = medications.indexOfFirst { it.id == id }
        if (index >= 0) {
            medications[index] = medications[index].copy(isTaken = isTaken)
            publishObservedMedications()
        }
    }

    override suspend fun setMedicationTakenForDay(
        scheduleId: Long,
        takenDateEpochDay: Long,
        isTaken: Boolean,
    ) {
        val index = medications.indexOfFirst { it.scheduleId == scheduleId }
        if (index >= 0) {
            medications[index] = medications[index].copy(isTaken = isTaken)
            publishObservedMedications()
        }
    }

    override suspend fun markMedicationTakenForDay(log: MedicationTakenLogEntity) = Unit

    override suspend fun insertMedicationTakenLog(log: MedicationTakenLogEntity): Long {
        return 1L
    }

    override suspend fun clearMedicationTakenForDay(
        scheduleId: Long,
        takenDateEpochDay: Long,
    ) = Unit

    override suspend fun updateMedication(medication: MedicationEntity) {
        val index = medications.indexOfFirst { it.id == medication.id }
        if (index >= 0) {
            val noteRepeatType = repeatTypeFromMedicationNote(medication.note)
            val repeatType = noteRepeatType
                .takeIf { it != MEDICATION_REPEAT_ONCE }
                ?: medication.repeatType
            val repeatIntervalDays = if (noteRepeatType != MEDICATION_REPEAT_ONCE) {
                repeatIntervalDaysFromMedicationNote(medication.note)
            } else {
                medication.repeatIntervalDays
            }
            medications[index] = medication.copy(
                repeatType = repeatType,
                repeatIntervalDays = normalizeMedicationRepeatIntervalDays(
                    repeatType = repeatType,
                    intervalDays = repeatIntervalDays,
                ),
            )
            publishObservedMedications()
        }
    }

    override suspend fun updateMedicationRecord(medication: MedicationRecordEntity) = Unit

    override suspend fun getPrimaryScheduleForMedication(medicationId: Long): MedicationScheduleEntity? {
        val medication = medications.firstOrNull { it.id == medicationId } ?: return null
        return MedicationScheduleEntity(
            id = medication.scheduleId,
            medicationId = medication.id,
            timesOfDay = medication.timesOfDay,
            repeatType = medication.repeatType,
            intervalDays = medication.repeatIntervalDays,
            startDateEpochDay = medication.startDateEpochDay,
        )
    }

    override suspend fun getPrimaryScheduleIdForMedication(medicationId: Long): Long? {
        return medications.firstOrNull { it.id == medicationId }?.scheduleId
    }

    override suspend fun getMedicationById(id: Long): MedicationEntity? {
        return medications.firstOrNull { it.id == id }
    }

    override suspend fun getMedicationByIdForDay(
        id: Long,
        targetEpochDay: Long,
    ): MedicationEntity? {
        return medications.firstOrNull { it.id == id }
    }

    override suspend fun deleteMedication(id: Long) {
        medications.removeAll { it.id == id }
        publishObservedMedications()
    }

    override suspend fun deleteMedicationRecord(id: Long) {
        deleteMedication(id)
    }

    private fun publishObservedMedications() {
        medicationFlow.value = medications
            .filter { medication ->
                medication.isVisibleForDay(
                    startOfDay = observedStartOfDay,
                    endOfDay = observedEndOfDay,
                    targetEpochDay = observedEpochDay,
                )
            }
            .sortedBy { it.timesOfDay }
    }
}

private fun MedicationEntity.isVisibleForDay(
    startOfDay: Long,
    endOfDay: Long,
    targetEpochDay: Long,
): Boolean {
    if (createdAt >= endOfDay || targetEpochDay < startDateEpochDay) {
        return false
    }

    return when (repeatType) {
        MEDICATION_REPEAT_DAILY -> true
        MEDICATION_REPEAT_EVERY_OTHER_DAY -> (targetEpochDay - startDateEpochDay) % 2L == 0L
        MEDICATION_REPEAT_CUSTOM -> {
            val interval = normalizeMedicationRepeatIntervalDays(
                repeatType = repeatType,
                intervalDays = repeatIntervalDays,
            ).toLong()
            (targetEpochDay - startDateEpochDay) % interval == 0L
        }
        MEDICATION_REPEAT_ONCE -> createdAt >= startOfDay || startDateEpochDay == targetEpochDay
        else -> createdAt >= startOfDay
    }
}
