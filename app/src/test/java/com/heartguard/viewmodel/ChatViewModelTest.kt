package com.heartguard.viewmodel

import com.heartguard.data.local.AppDao
import com.heartguard.data.local.ChatEntity
import com.heartguard.data.local.FraudRecordEntity
import com.heartguard.data.local.MedicationEntity
import com.heartguard.data.local.MedicationRecordEntity
import com.heartguard.data.local.MedicationScheduleEntity
import com.heartguard.data.local.MedicationTakenLogEntity
import com.heartguard.data.remote.AiChatMessage
import com.heartguard.data.remote.AiGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun sendMessage_appendsReply() = runTest {
        val viewModel = ChatViewModel(
            appDao = FakeAppDao(),
            aiGateway = SuccessfulAiGateway("陪你慢慢聊"),
        )

        viewModel.sendMessage("你好")
        advanceUntilIdle()

        val messages = viewModel.messages.first()
        assertEquals("你好", messages[messages.lastIndex - 1].text)
        assertEquals("陪你慢慢聊", messages.last().text)
        assertTrue(messages.last().isAi)
        assertFalse(viewModel.isProcessing.first())
        assertEquals(null, viewModel.speakingMessageId.first())
    }

    @Test
    fun sendMessage_whenRepositoryFails_usesFallbackReply() = runTest {
        val viewModel = ChatViewModel(
            appDao = FakeAppDao(),
            aiGateway = FailingAiGateway(),
        )

        viewModel.sendMessage("在吗")
        advanceUntilIdle()

        val messages = viewModel.messages.first()
        assertEquals("我在呢，你慢慢说。", messages.last().text)
        assertFalse(viewModel.isProcessing.first())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeAppDao : AppDao() {
    private val chatMessages = mutableListOf<ChatEntity>()

    override suspend fun insertChatMessage(message: ChatEntity) {
        chatMessages += message
    }

    override suspend fun getRecentChatMessages(): List<ChatEntity> = chatMessages

    override suspend fun insertFraudRecord(record: FraudRecordEntity) = Unit

    override suspend fun getFraudPassRate(): Double? = null

    override fun observeFraudRecordCountSince(startTimeMillis: Long): Flow<Int> = flowOf(0)

    override suspend fun insertMedication(medication: MedicationEntity): Long = 0L

    override suspend fun insertMedicationRecord(medication: MedicationRecordEntity): Long = 0L

    override suspend fun insertMedicationSchedule(schedule: MedicationScheduleEntity): Long = 0L

    override fun observeTodayMedications(
        startOfDay: Long,
        endOfDay: Long,
        targetEpochDay: Long,
    ): Flow<List<MedicationEntity>> = flowOf(emptyList())

    override suspend fun getTodayMedications(
        startOfDay: Long,
        endOfDay: Long,
        targetEpochDay: Long,
    ): List<MedicationEntity> = emptyList()

    override suspend fun getTodayPendingMedicationCount(
        startOfDay: Long,
        endOfDay: Long,
        targetEpochDay: Long,
    ): Int = 0

    override suspend fun getAllScheduledMedicationsForReminder(
        targetEpochDay: Long,
    ): List<MedicationEntity> = emptyList()

    override suspend fun updateMedicationTakenStatus(id: Long, isTaken: Boolean) = Unit

    override suspend fun updateMedication(medication: MedicationEntity) = Unit

    override suspend fun setMedicationTakenForDay(
        scheduleId: Long,
        takenDateEpochDay: Long,
        isTaken: Boolean,
    ) = Unit

    override suspend fun markMedicationTakenForDay(log: MedicationTakenLogEntity) = Unit

    override suspend fun insertMedicationTakenLog(log: MedicationTakenLogEntity): Long = 1L

    override suspend fun clearMedicationTakenForDay(
        scheduleId: Long,
        takenDateEpochDay: Long,
    ) = Unit

    override suspend fun updateMedicationRecord(medication: MedicationRecordEntity) = Unit

    override suspend fun getPrimaryScheduleForMedication(medicationId: Long): MedicationScheduleEntity? = null

    override suspend fun getPrimaryScheduleIdForMedication(medicationId: Long): Long? = null

    override suspend fun getMedicationById(id: Long): MedicationEntity? = null

    override suspend fun getMedicationByIdForDay(
        id: Long,
        targetEpochDay: Long,
    ): MedicationEntity? = null

    override suspend fun deleteMedication(id: Long) = Unit

    override suspend fun deleteMedicationRecord(id: Long) = Unit
}

private class SuccessfulAiGateway(
    private val reply: String,
) : AiGateway {
    override suspend fun recognizeSpeech(audioFile: File): String = ""

    override suspend fun getChatResponse(
        userText: String,
        messages: List<AiChatMessage>,
        systemPrompt: String?,
    ): String = reply

    override suspend fun textToSpeech(text: String): String = ""
}

private class FailingAiGateway : AiGateway {
    override suspend fun recognizeSpeech(audioFile: File): String = ""

    override suspend fun getChatResponse(
        userText: String,
        messages: List<AiChatMessage>,
        systemPrompt: String?,
    ): String {
        throw IllegalStateException("network down")
    }

    override suspend fun textToSpeech(text: String): String = ""
}
