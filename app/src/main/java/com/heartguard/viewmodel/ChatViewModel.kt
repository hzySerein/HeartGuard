package com.heartguard.viewmodel

import com.heartguard.utils.DebugLogger

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heartguard.R
import com.heartguard.data.local.AppDao
import com.heartguard.data.local.ChatEntity
import com.heartguard.data.remote.AiChatMessage
import com.heartguard.data.remote.AiGateway
import com.heartguard.utils.AudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ChatMessageType {
    TEXT,
    VOICE,
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isAi: Boolean,
    val type: ChatMessageType = ChatMessageType.TEXT,
    val voiceLabel: String? = null,
)

class ChatViewModel(
    private val appDao: AppDao,
    private val appContext: Context? = null,
    private val aiGateway: AiGateway? = null,
    private val audioEngine: AudioEngine = AudioEngine,
) : ViewModel() {
    private var currentRoleId: String = DEFAULT_ROLE_ID

    private val _messages = MutableStateFlow(seedMessages())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _processingMessage = MutableStateFlow<String?>(null)
    val processingMessage: StateFlow<String?> = _processingMessage.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private val _playbackVolume = MutableStateFlow(1.0f)
    val playbackVolume: StateFlow<Float> = _playbackVolume.asStateFlow()

    private var conversationHistory: List<AiChatMessage> = seedMessages().toAiHistory()

    private var recordingJob: Job? = null

    init {
        viewModelScope.launch {
            bootstrapMessages()
        }
    }

    fun sendMessage(text: String, isVoiceInput: Boolean = false) {
        val userMessage = text.trim()
        if (userMessage.isBlank() || _isProcessing.value) {
            return
        }

        viewModelScope.launch {
            runChatPipeline(userMessage, isVoiceInput)
        }
    }

    fun startRecording() {
        if (_isProcessing.value || _isRecording.value) {
            return
        }

        val context = appContext ?: run {
            _processingMessage.value = stringResource(R.string.chat_recording_not_ready)
            return
        }

        try {
            _processingMessage.value = null
            _isRecording.value = true
            startRecordingTimer()
            audioEngine.startRecording(context)
        } catch (error: Exception) {
            DebugLogger.e(TAG, "Failed to start recording", error)
            _isRecording.value = false
            stopRecordingTimer()
            _processingMessage.value = stringResource(R.string.chat_start_recording_failed)
        }
    }

    fun stopRecordingAndSend() {
        if (!_isRecording.value) {
            return
        }

        _isRecording.value = false
        stopRecordingTimer()
        val audioFile = audioEngine.stopRecording() ?: run {
            _processingMessage.value = stringResource(R.string.chat_no_valid_voice)
            return
        }
        viewModelScope.launch {
            runVoiceRecognition(audioFile)
        }
    }

    fun cancelRecording() {
        if (!_isRecording.value) {
            return
        }

        _isRecording.value = false
        stopRecordingTimer()
        runCatching { audioEngine.stopRecording()?.delete() }
        _processingMessage.value = stringResource(R.string.chat_recording_cancelled)
    }

    fun setPlaybackVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        _playbackVolume.value = safeVolume
        audioEngine.setPlaybackVolume(safeVolume)
    }

    fun setRole(roleId: String) {
        if (roleId.isBlank()) return
        currentRoleId = roleId
    }

    override fun onCleared() {
        stopRecordingTimer()
        runCatching { audioEngine.stopRecording() }
        runCatching { audioEngine.stopPlaying() }
        super.onCleared()
    }

    private fun startRecordingTimer() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            var duration = 0L
            while (isActive) {
                _recordingDuration.value = duration
                delay(1000)
                duration += 1000
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingJob?.cancel()
        recordingJob = null
        _recordingDuration.value = 0L
    }

    private suspend fun bootstrapMessages() {
        try {
            if (withContext(Dispatchers.IO) { appDao.getRecentChatMessages() }.isEmpty()) {
                seedMessages().forEach { message ->
                    withContext(Dispatchers.IO) {
                        appDao.insertChatMessage(
                            ChatEntity(
                                text = message.text,
                                isAi = message.isAi,
                                timestamp = System.currentTimeMillis(),
                            )
                        )
                    }
                }
            }
            refreshMessagesFromDb()
        } catch (error: Exception) {
            DebugLogger.e(TAG, "Failed to bootstrap messages", error)
        }
    }

    private suspend fun runChatPipeline(
        userMessage: String,
        isVoiceInput: Boolean,
    ) {
        _isProcessing.value = true
        try {
            _processingMessage.value = null
            val userChatMessage = ChatMessage(
                text = userMessage,
                isAi = false,
                type = if (isVoiceInput) ChatMessageType.VOICE else ChatMessageType.TEXT,
                voiceLabel = if (isVoiceInput) voiceMessagePrefix() else null,
            )
            appendAndPersistMessage(userChatMessage)
            appendToConversationHistory(userChatMessage.toAiChatMessage())

            _isThinking.value = true
            _processingMessage.value = stringResource(R.string.chat_ai_thinking)
            val aiReply = try {
                if (isIdentityQuestion(userMessage)) {
                    identityReply()
                } else {
                    aiGateway
                        ?.getChatResponse(
                            userText = userMessage,
                            messages = conversationHistory,
                            systemPrompt = chatSystemPrompt(),
                        )
                        ?.let { reply -> sanitizeAiReply(reply) }
                        ?.ifBlank { fallbackReply() }
                        ?: fallbackReply()
                }
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to request companion reply", error)
                fallbackReply()
            }

            val aiMessage = ChatMessage(text = aiReply, isAi = true)
            appendAndPersistMessage(aiMessage)
            appendToConversationHistory(aiMessage.toAiChatMessage())
            _isThinking.value = false
            _processingMessage.value = null

            if (!isChatServiceError(aiReply)) {
                playAiReply(aiMessage)
            }
        } finally {
            _isThinking.value = false
            _isProcessing.value = false
        }
    }

    private suspend fun runVoiceRecognition(audioFile: File) {
        val gateway = aiGateway ?: run {
            _processingMessage.value = stringResource(R.string.chat_asr_not_configured)
            return
        }

        _isProcessing.value = true
        try {
            _processingMessage.value = stringResource(R.string.chat_recognizing_voice)
            val recognizedText = gateway.recognizeSpeech(audioFile).trim()
            if (recognizedText.isBlank() || isVoiceRecognitionError(recognizedText)) {
                _processingMessage.value = recognizedText.ifBlank {
                    stringResource(R.string.chat_no_clear_content)
                }
                return
            }

            runChatPipeline(
                userMessage = recognizedText,
                isVoiceInput = true,
            )
        } catch (error: Exception) {
            DebugLogger.e(TAG, "AI speech recognition failed", error)
            _processingMessage.value = stringResource(R.string.chat_asr_failed)
        } finally {
            _isProcessing.value = false
            runCatching { audioFile.delete() }
        }
    }

    private suspend fun appendAndPersistMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
        persistMessage(message)
    }

    private fun appendToConversationHistory(message: AiChatMessage) {
        val content = message.content.trim()
        if (content.isBlank()) {
            return
        }
        conversationHistory = (conversationHistory + message.copy(content = content))
            .takeLast(MAX_CONVERSATION_HISTORY_MESSAGES)
    }

    private fun List<ChatMessage>.toAiHistory(): List<AiChatMessage> {
        return map { message -> message.toAiChatMessage() }
            .filter { message -> message.content.isNotBlank() }
            .takeLast(MAX_CONVERSATION_HISTORY_MESSAGES)
    }

    private fun ChatMessage.toAiChatMessage(): AiChatMessage {
        val content = historyText().trim()
        return if (isAi) {
            AiChatMessage.assistant(content)
        } else {
            AiChatMessage.user(content)
        }
    }

    private fun ChatMessage.historyText(): String {
        return if (!isAi && type == ChatMessageType.VOICE) {
            val prefix = voiceLabel ?: voiceMessagePrefix()
            text.removePrefix(prefix).removePrefix(": ").ifBlank { text }
        } else {
            text
        }
    }

    private suspend fun persistMessage(message: ChatMessage) {
        try {
            withContext(Dispatchers.IO) {
                appDao.insertChatMessage(
                    ChatEntity(
                        text = persistedText(message),
                        isAi = message.isAi,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            }
        } catch (error: Exception) {
            DebugLogger.e(TAG, "Failed to save chat message", error)
        }
    }

    private suspend fun refreshMessagesFromDb() {
        val savedMessages = withContext(Dispatchers.IO) {
            appDao.getRecentChatMessages()
        }
            .sortedBy { it.timestamp }
            .map { entity ->
                val voicePrefix = voiceMessagePrefix()
                val isVoiceMessage = !entity.isAi && entity.text.startsWith(voicePrefix)
                val displayText = if (isVoiceMessage) {
                    entity.text.removePrefix(voicePrefix).removePrefix(": ").ifBlank {
                        entity.text
                    }
                } else {
                    entity.text
                }
                ChatMessage(
                    text = displayText,
                    isAi = entity.isAi,
                    type = if (isVoiceMessage) ChatMessageType.VOICE else ChatMessageType.TEXT,
                    voiceLabel = if (isVoiceMessage) voicePrefix else null,
                )
            }

        if (savedMessages.isNotEmpty()) {
            _messages.value = savedMessages
            conversationHistory = savedMessages.toAiHistory()
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
        const val DEFAULT_ROLE_ID = "nurse"
        const val MAX_CONVERSATION_HISTORY_MESSAGES = 20
        val questionPunctuation = setOf('?', '？', '。', '！', '!', '，', ',', '.', '、', ':', '：')
        val identityQuestionTriggers = listOf(
            "你是什么模型",
            "你是哪个模型",
            "你是哪种模型",
            "你是谁",
            "你叫什么",
            "你由谁开发",
            "你是谁开发",
            "谁开发的你",
            "谁开发了你",
            "谁做的你",
            "你是谁做的",
            "你是哪个公司",
            "你由哪个公司",
            "whoareyou",
            "whatmodelareyou",
            "whichmodelareyou",
            "whodevelopedyou",
            "whomadeyou",
            "whatcompanymadeyou",
        )
        val forbiddenVendorPattern = Regex(
            listOf(
                "Doubao",
                "豆包",
                "ByteDance",
                "字节跳动",
                "ChatGPT",
                "OpenAI",
            ).joinToString("|") { term -> Regex.escape(term) },
            RegexOption.IGNORE_CASE,
        )
    }

    private fun isVoiceRecognitionError(message: String): Boolean {
        val errorKeywords = listOf(
            R.string.common_error_failed_keyword,
            R.string.common_error_unavailable_keyword,
            R.string.vivo_ai_missing_credentials,
            R.string.vivo_ai_asr_requires_pcm_wav,
            R.string.vivo_ai_asr_failed,
        ).map(::stringResource)
            .filter { it.isNotBlank() }
        return errorKeywords.any { keyword ->
            message == keyword || message.contains(keyword)
        }
    }

    private fun isChatServiceError(message: String): Boolean {
        val errorMessages = listOf(
            R.string.vivo_ai_missing_credentials,
            R.string.vivo_ai_chat_model_no_permission,
            R.string.vivo_ai_chat_network_failed,
            R.string.vivo_ai_chat_parse_failed,
            R.string.vivo_ai_chat_failed,
        ).map(::stringResource)
            .filter { it.isNotBlank() }
        return errorMessages.any { errorMessage -> message == errorMessage }
    }

    private suspend fun playAiReply(aiMessage: ChatMessage) {
        val gateway = aiGateway
        if (gateway == null) {
            _processingMessage.value = null
            return
        }

        _processingMessage.value = stringResource(R.string.chat_generating_voice)
        val audioPath = gateway.textToSpeech(aiMessage.text).trim()

        if (audioPath.isBlank() || 
            audioPath.startsWith("ERROR:") ||
            isErrorAudioPath(audioPath) ||
            !File(audioPath).exists()) {
            _processingMessage.value = stringResource(R.string.chat_voice_generation_problem)
            DebugLogger.w(TAG, "TTS did not return a playable local path: $audioPath")
            return
        }

        _processingMessage.value = stringResource(R.string.chat_playing_reply)
        _speakingMessageId.value = aiMessage.id
        audioEngine.playAudio(audioPath, _playbackVolume.value) {
            _speakingMessageId.value = null
            _processingMessage.value = null
        }
    }

    private fun isErrorAudioPath(audioPath: String): Boolean {
        return listOf(
            R.string.common_error_unavailable_keyword,
            R.string.common_error_failed_keyword,
        ).map(::stringResource)
            .filter { it.isNotBlank() }
            .any { keyword -> audioPath.contains(keyword) }
    }

    private fun persistedText(message: ChatMessage): String {
        return if (!message.isAi && message.type == ChatMessageType.VOICE) {
            val voiceLabel = message.voiceLabel ?: voiceMessagePrefix()
            if (message.text == voiceLabel) {
                voiceLabel
            } else {
                "$voiceLabel: ${message.text}"
            }
        } else {
            message.text
        }
    }

    private fun seedMessages(): List<ChatMessage> {
        return listOf(
            ChatMessage(text = stringResource(R.string.chat_seed_ai_hello), isAi = true),
            ChatMessage(text = stringResource(R.string.chat_seed_user_weather), isAi = false),
            ChatMessage(text = stringResource(R.string.chat_seed_ai_slow_chat), isAi = true),
        )
    }

    private fun fallbackReply(): String {
        return stringResource(R.string.chat_fallback_reply)
    }

    private fun chatSystemPrompt(): String {
        val basePrompt = stringResource(R.string.chat_system_prompt)
        val rolePrompt = roleSystemPrompt(currentRoleId)
        return if (rolePrompt.isNotBlank()) "$basePrompt\n$rolePrompt" else basePrompt
    }

    private fun roleSystemPrompt(roleId: String): String {
        val resId = when (roleId) {
            "tiger" -> R.string.chat_role_system_tiger
            "nurse" -> R.string.chat_role_system_nurse
            "granddaughter" -> R.string.chat_role_system_granddaughter
            "volunteer" -> R.string.chat_role_system_volunteer
            else -> return ""
        }
        return stringResource(resId)
    }

    private fun identityReply(): String {
        return stringResource(R.string.chat_identity_reply)
    }

    private fun isIdentityQuestion(message: String): Boolean {
        val compactMessage = message
            .lowercase()
            .filterNot { char -> char.isWhitespace() || char in questionPunctuation }
        return identityQuestionTriggers.any { trigger -> compactMessage.contains(trigger) } ||
            (forbiddenVendorPattern.containsMatchIn(message) && compactMessage.contains("你"))
    }

    private fun sanitizeAiReply(reply: String): String {
        val trimmedReply = reply.trim()
        if (trimmedReply.isBlank()) {
            return ""
        }
        return if (forbiddenVendorPattern.containsMatchIn(trimmedReply)) {
            identityReply()
        } else {
            trimmedReply
        }
    }

    private fun voiceMessagePrefix(): String {
        return stringResource(R.string.chat_voice_message_prefix)
    }

    private fun stringResource(
        @StringRes resId: Int,
        vararg args: Any,
    ): String {
        val context = appContext ?: return ""
        return if (args.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *args)
        }
    }
}
