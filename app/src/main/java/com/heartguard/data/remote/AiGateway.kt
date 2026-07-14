package com.heartguard.data.remote

import java.io.File

data class AiChatMessage(
    val role: String,
    val content: String,
) {
    companion object {
        fun user(content: String) = AiChatMessage("user", content)

        fun assistant(content: String) = AiChatMessage("assistant", content)
    }
}

interface AiGateway {
    suspend fun recognizeSpeech(audioFile: File): String

    suspend fun getChatResponse(
        userText: String,
        messages: List<AiChatMessage> = listOf(AiChatMessage.user(userText)),
        systemPrompt: String? = null,
    ): String

    /**
     * Returns a local playable audio file path, or a user-facing error message.
     */
    suspend fun textToSpeech(text: String): String
}
