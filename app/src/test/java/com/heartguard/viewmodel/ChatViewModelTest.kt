package com.heartguard.viewmodel

import com.heartguard.data.local.ChatDao
import com.heartguard.data.local.ChatEntity
import com.heartguard.data.remote.AiChatMessage
import com.heartguard.data.remote.AiGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
            chatDao = FakeChatDao(),
            appContext = TODO("Provide test context"),
            aiGateway = SuccessfulAiGateway("陪你慢慢聊"),
            audioEngine = TODO("Provide test AudioEngine"),
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
            chatDao = FakeChatDao(),
            appContext = TODO("Provide test context"),
            aiGateway = FailingAiGateway(),
            audioEngine = TODO("Provide test AudioEngine"),
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

private class FakeChatDao : ChatDao() {
    private val chatMessages = mutableListOf<ChatEntity>()

    override suspend fun insertChatMessage(message: ChatEntity) {
        chatMessages += message
    }

    override suspend fun getRecentChatMessages(): List<ChatEntity> = chatMessages
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
