package com.heartguard.data.remote

import android.content.Context
import android.util.Base64
import com.heartguard.BuildConfig
import com.heartguard.R
import com.heartguard.utils.DebugLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class VivoAiRepository(
    context: Context,
    private val client: OkHttpClient = createClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : AiGateway {
    private val appContext = context.applicationContext
    private val cacheDir = appContext.cacheDir
    private val sessionId = UUID.randomUUID().toString()

    override suspend fun recognizeSpeech(audioFile: File): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext appContext.getString(R.string.vivo_ai_missing_credentials)
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext appContext.getString(R.string.vivo_ai_invalid_recording)
        }

        val wavAudio = runCatching { audioFile.readVivoAsrWav() }
            .getOrElse { error ->
                DebugLogger.e(TAG, "Invalid ASR recording format", error)
                return@withContext appContext.getString(R.string.vivo_ai_asr_requires_pcm_wav)
            }

        var webSocket: WebSocket? = null
        val result = CompletableDeferred<String>()
        val request = websocketRequest(
            path = ASR_PATH,
            queryParams = asrQueryParams(),
        )

        try {
            webSocket = client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        webSocket.send(asrStartMessage())
                        wavAudio.pcmData.sendInChunks(webSocket, ASR_CHUNK_SIZE_BYTES)
                        webSocket.send(ASR_END_MARKER.encodeUtf8())
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        handleAsrMessage(
                            message = text,
                            result = result,
                            webSocket = webSocket,
                        )
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        bytes: ByteString,
                    ) {
                        handleAsrMessage(
                            message = bytes.utf8(),
                            result = result,
                            webSocket = webSocket,
                        )
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        completeExceptionally(result, t)
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        completeIfNeeded(result, "")
                    }
                },
            )

            withTimeout(ASR_TIMEOUT_MILLIS) {
                result.await()
            }.ifBlank {
                appContext.getString(R.string.vivo_ai_asr_no_clear_voice)
            }
        } catch (error: Exception) {
            DebugLogger.e(TAG, "Vivo ASR failed", error)
            appContext.getString(R.string.vivo_ai_asr_failed)
        } finally {
            webSocket?.close(NORMAL_CLOSURE_STATUS, null)
        }
    }

    override suspend fun getChatResponse(
        userText: String,
        messages: List<AiChatMessage>,
        systemPrompt: String?,
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext appContext.getString(R.string.vivo_ai_missing_credentials)
        }

        try {
            val queryParams = sortedMapOf(
                "requestId" to UUID.randomUUID().toString(),
            )
            val requestBody = buildJsonObject {
                put("model", CHAT_MODEL)
                put("stream", false)
                put(
                    "messages",
                    buildJsonArray {
                        systemPrompt
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { prompt ->
                                add(
                                    buildJsonObject {
                                        put("role", ROLE_SYSTEM)
                                        put("content", prompt)
                                    },
                                )
                            }
                        normalizedChatHistory(userText, messages).forEach { message ->
                            add(
                                buildJsonObject {
                                    put("role", message.role)
                                    put("content", message.content)
                                },
                            )
                        }
                    },
                )
            }
            val request = Request.Builder()
                .url(httpUrl(CHAT_PATH, queryParams))
                .header("Authorization", "Bearer ${BuildConfig.VIVO_APP_KEY}")
                .header("Content-Type", JSON_MEDIA_TYPE.toString())
                .post(json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    DebugLogger.e(TAG, "Vivo chat HTTP ${response.code}: $bodyText")
                    return@withContext friendlyHttpError(bodyText)
                }

                extractChatContent(bodyText).ifBlank {
                    appContext.getString(R.string.vivo_ai_empty_reply)
                }
            }
        } catch (error: IOException) {
            DebugLogger.e(TAG, "Vivo chat network failed", error)
            appContext.getString(R.string.vivo_ai_chat_network_failed)
        } catch (error: SerializationException) {
            DebugLogger.e(TAG, "Vivo chat parse failed", error)
            appContext.getString(R.string.vivo_ai_chat_parse_failed)
        } catch (error: Exception) {
            DebugLogger.e(TAG, "Vivo chat failed", error)
            appContext.getString(R.string.vivo_ai_chat_failed)
        }
    }

    override suspend fun textToSpeech(text: String): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext appContext.getString(R.string.vivo_ai_missing_credentials)
        }
        val safeText = text.trim()
        if (safeText.isBlank()) {
            return@withContext appContext.getString(R.string.vivo_ai_tts_no_audio)
        }

        val result = CompletableDeferred<TtsAudioPayload>()
        var webSocket: WebSocket? = null
        val request = websocketRequest(
            path = TTS_PATH,
            queryParams = ttsQueryParams(),
        )

        try {
            webSocket = client.newWebSocket(
                request,
                object : WebSocketListener() {
                    private val pcmBuffer = ByteArrayOutputStream()

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        webSocket.send(ttsRequestMessage(safeText))
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        handleTtsMessage(
                            message = text,
                            pcmBuffer = pcmBuffer,
                            result = result,
                        )
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        bytes: ByteString,
                    ) {
                        handleTtsBinaryMessage(
                            bytes = bytes,
                            pcmBuffer = pcmBuffer,
                            result = result,
                        )
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        completeExceptionally(result, t)
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (!result.isCompleted) {
                            completeIfNeeded(result, TtsAudioPayload.AudioBytes(pcmBuffer.toByteArray()))
                        }
                    }
                },
            )

            val payload = withTimeout(TTS_TIMEOUT_MILLIS) {
                result.await()
            }
            saveTtsPayloadToLocalFile(payload)
        } catch (error: Exception) {
            DebugLogger.e(TAG, "Vivo TTS failed", error)
            appContext.getString(R.string.vivo_ai_tts_failed)
        } finally {
            webSocket?.close(NORMAL_CLOSURE_STATUS, null)
        }
    }

    private fun handleAsrMessage(
        message: String,
        result: CompletableDeferred<String>,
        webSocket: WebSocket,
    ) {
        val root = runCatching { json.parseToJsonElement(message).asObjectOrNull() }
            .getOrNull()
            ?: return
        val code = root.intValue("code") ?: root.intValue("error_code")
        if (code != null && code != 0 && code != 8 && code != 9) {
            val errorMessage = root.stringValue("desc")
                ?: root.stringValue("error_msg")
                ?: root.stringValue("msg")
                ?: appContext.getString(R.string.vivo_ai_asr_failed)
            completeExceptionally(result, IllegalStateException(errorMessage))
            webSocket.close(NORMAL_CLOSURE_STATUS, null)
            return
        }

        val data = root["data"]?.asObjectOrNull()
        val text = data?.stringValue("text")
            ?: data?.stringValue("onebest")
            ?: root.stringValue("text")
        val isLast = data?.booleanValue("is_last") == true ||
            root.stringValue("action") == "result" && data?.booleanValue("is_last") == true ||
            code == 9
        if (!text.isNullOrBlank()) {
            if (isLast) {
                completeIfNeeded(result, text)
                webSocket.send(ASR_CLOSE_MARKER.encodeUtf8())
                webSocket.close(NORMAL_CLOSURE_STATUS, null)
            }
        } else if (isLast) {
            completeIfNeeded(result, "")
            webSocket.close(NORMAL_CLOSURE_STATUS, null)
        }
    }

    private fun handleTtsMessage(
        message: String,
        pcmBuffer: ByteArrayOutputStream,
        result: CompletableDeferred<TtsAudioPayload>,
    ) {
        val root = runCatching { json.parseToJsonElement(message).asObjectOrNull() }
            .getOrNull()
            ?: return
        val errorCode = root.intValue("error_code") ?: 0
        if (errorCode != 0) {
            val errorMessage = root.stringValue("error_msg")
                ?: root.stringValue("msg")
                ?: appContext.getString(R.string.vivo_ai_tts_failed)
            completeExceptionally(result, IllegalStateException(errorMessage))
            return
        }

        val data = root["data"]?.asObjectOrNull()
        val audioUrl = data.firstNonBlankString("audio_url", "audioUrl", "url")
            ?: root.firstNonBlankString("audio_url", "audioUrl", "url")
        if (!audioUrl.isNullOrBlank()) {
            completeIfNeeded(result, TtsAudioPayload.RemoteUrl(audioUrl))
            return
        }

        val audio = data?.stringValue("audio")
        if (!audio.isNullOrBlank()) {
            if (audio.startsWith("http://") || audio.startsWith("https://")) {
                completeIfNeeded(result, TtsAudioPayload.RemoteUrl(audio))
                return
            }
            val audioBytes = runCatching {
                audio.decodeBase64Audio()
            }.getOrElse { error ->
                completeExceptionally(result, error)
                return
            }
            synchronized(pcmBuffer) {
                pcmBuffer.write(audioBytes)
            }
        }
        if (data?.intValue("status") == TTS_STATUS_FINISHED) {
            completeIfNeeded(result, TtsAudioPayload.AudioBytes(pcmBuffer.toByteArray()))
        }
    }

    private fun handleTtsBinaryMessage(
        bytes: ByteString,
        pcmBuffer: ByteArrayOutputStream,
        result: CompletableDeferred<TtsAudioPayload>,
    ) {
        val rawBytes = bytes.toByteArray()
        val text = runCatching { bytes.utf8() }.getOrNull()
        if (!text.isNullOrBlank() && text.trimStart().startsWith("{")) {
            handleTtsMessage(
                message = text,
                pcmBuffer = pcmBuffer,
                result = result,
            )
            return
        }

        synchronized(pcmBuffer) {
            pcmBuffer.write(rawBytes)
        }
    }

    private fun saveTtsPayloadToLocalFile(payload: TtsAudioPayload): String {
        return when (payload) {
            is TtsAudioPayload.AudioBytes -> saveTtsBytesToLocalFile(payload.bytes)
            is TtsAudioPayload.RemoteUrl -> downloadTtsAudioToLocalFile(payload.url)
        }
    }

    private fun saveTtsBytesToLocalFile(audioBytes: ByteArray): String {
        if (audioBytes.isEmpty()) {
            return appContext.getString(R.string.vivo_ai_tts_no_audio)
        }

        val fileType = audioBytes.detectAudioFileType() ?: TtsFileType.WAV
        val outputBytes = if (fileType == TtsFileType.WAV && !audioBytes.isWavBytes()) {
            audioBytes.toWavBytes(sampleRate = TTS_SAMPLE_RATE)
        } else {
            audioBytes
        }
        val outputFile = File(cacheDir, "$TTS_OUTPUT_FILE_PREFIX.${fileType.extension}")
        outputFile.writeBytes(outputBytes)
        return outputFile.absolutePath
    }

    private fun downloadTtsAudioToLocalFile(url: String): String {
        if (url.isBlank()) {
            return appContext.getString(R.string.vivo_ai_tts_no_audio)
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                DebugLogger.e(TAG, "Vivo TTS audio download HTTP ${response.code}")
                return appContext.getString(R.string.vivo_ai_tts_failed)
            }
            val body = response.body ?: return appContext.getString(R.string.vivo_ai_tts_no_audio)
            val contentType = body.contentType()?.subtype.orEmpty()
            val bytes = body.bytes()
            val fileType = bytes.detectAudioFileType()
                ?: contentType.toTtsFileType()
                ?: url.toTtsFileType()
                ?: TtsFileType.WAV
            val outputBytes = if (fileType == TtsFileType.WAV && !bytes.isWavBytes()) {
                bytes.toWavBytes(sampleRate = TTS_SAMPLE_RATE)
            } else {
                bytes
            }
            val outputFile = File(cacheDir, "$TTS_OUTPUT_FILE_PREFIX.${fileType.extension}")
            outputFile.writeBytes(outputBytes)
            return outputFile.absolutePath
        }
    }

    private fun asrStartMessage(): String {
        val payload = buildJsonObject {
            put("type", "started")
            put("request_id", UUID.randomUUID().toString())
            put(
                "asr_info",
                buildJsonObject {
                    put("end_vad_time", 100)
                    put("audio_type", "pcm")
                    put("punctuation", 1)
                    put("chinese2digital", 0)
                },
            )
        }
        return json.encodeToString(payload)
    }

    private fun ttsRequestMessage(text: String): String {
        val payload = buildJsonObject {
            put("aue", TTS_AUE_PCM)
            put("auf", "audio/L16;rate=$TTS_SAMPLE_RATE")
            put("vcn", TTS_VOICE)
            put("reqId", TTS_REQUEST_ID)
            put("text", Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            put("encoding", "utf8")
        }
        return json.encodeToString(payload)
    }

    private fun normalizedChatHistory(
        userText: String,
        messages: List<AiChatMessage>,
    ): List<AiChatMessage> {
        val history = messages.mapNotNull { message ->
            val role = when (message.role.lowercase()) {
                ROLE_USER -> ROLE_USER
                ROLE_SYSTEM -> ROLE_SYSTEM
                else -> null
            }
            val content = message.content.trim()
            if (role == null || content.isBlank()) {
                null
            } else {
                AiChatMessage(role, content)
            }
        }.takeLast(MAX_CHAT_HISTORY_MESSAGES)

        return history.ifEmpty {
            listOf(AiChatMessage.user(userText.trim()))
        }
    }

    private fun extractChatContent(bodyText: String): String {
        val root = json.parseToJsonElement(bodyText).asObjectOrNull() ?: return ""
        root["error"]?.asObjectOrNull()?.let { error ->
            val message = error.stringValue("message")
                ?: error.stringValue("msg")
                ?: error.stringValue("code")
                ?: return appContext.getString(R.string.vivo_ai_chat_failed)
            DebugLogger.w(TAG, "Vivo chat error: $message")
            return friendlyChatError(message)
        }
        val code = root.intValue("code") ?: 0
        if (code != 0) {
            val message = root.stringValue("msg").orEmpty()
            DebugLogger.w(TAG, "Vivo chat business error code=$code msg=$message")
            return friendlyChatError(message)
        }
        return root["choices"]
            ?.asArrayOrNull()
            ?.firstOrNull()
            ?.asObjectOrNull()
            ?.get("message")
            ?.asObjectOrNull()
            ?.stringValue("content")
            .orEmpty()
            .ifBlank {
                root["data"]
                    ?.asObjectOrNull()
                    ?.stringValue("content")
                    .orEmpty()
            }
    }

    private fun friendlyHttpError(bodyText: String): String {
        val root = runCatching { json.parseToJsonElement(bodyText).asObjectOrNull() }.getOrNull()
        val message = root?.get("error")
            ?.asObjectOrNull()
            ?.stringValue("message")
            ?: root?.stringValue("message")
            ?: root?.stringValue("msg")
            ?: appContext.getString(R.string.vivo_ai_chat_failed)
        return friendlyChatError(message)
    }

    private fun friendlyChatError(message: String): String {
        return if (message.contains(MODEL_ACCESS_PERMISSION_ERROR, ignoreCase = true)) {
            appContext.getString(R.string.vivo_ai_chat_model_no_permission)
        } else {
            message.ifBlank { appContext.getString(R.string.vivo_ai_chat_failed) }
        }
    }

    private fun websocketRequest(
        path: String,
        queryParams: Map<String, String>,
    ): Request {
        return Request.Builder()
            .url(websocketUrl(path, queryParams))
            .addVivoGatewayHeaders(
                method = HTTP_GET,
                path = path,
                queryParams = queryParams,
                includeJsonContentType = false,
            )
            .build()
    }

    private fun Request.Builder.addVivoGatewayHeaders(
        method: String,
        path: String,
        queryParams: Map<String, String>,
        includeJsonContentType: Boolean,
    ): Request.Builder {
        val timestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toString()
        val nonce = randomNonce()
        val query = sortedEncodedQuery(queryParams)
        val signingString = buildString {
            append(method)
            append('\n')
            append(path)
            append('\n')
            append(query)
            append('\n')
            append(BuildConfig.VIVO_APP_ID)
            append('\n')
            append(timestamp)
            append('\n')
            append("x-ai-gateway-app-id:${BuildConfig.VIVO_APP_ID}")
            append('\n')
            append("x-ai-gateway-timestamp:$timestamp")
            append('\n')
            append("x-ai-gateway-nonce:$nonce")
        }

        header("X-AI-GATEWAY-APP-ID", BuildConfig.VIVO_APP_ID)
        header("X-AI-GATEWAY-TIMESTAMP", timestamp)
        header("X-AI-GATEWAY-NONCE", nonce)
        header("X-AI-GATEWAY-SIGNED-HEADERS", SIGNED_HEADERS)
        header("X-AI-GATEWAY-SIGNATURE", signingString.hmacSha256Base64(BuildConfig.VIVO_APP_KEY))
        header("Authorization", "Bearer ${BuildConfig.VIVO_APP_KEY}")
        if (includeJsonContentType) {
            header("Content-Type", JSON_MEDIA_TYPE.toString())
        }
        return this
    }

    private fun httpUrl(
        path: String,
        queryParams: Map<String, String>,
    ): String {
        val builder = "$HTTPS_BASE_URL$path".toHttpUrl().newBuilder()
        queryParams.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun websocketUrl(
        path: String,
        queryParams: Map<String, String>,
    ): String {
        val query = sortedEncodedQuery(queryParams)
        return "$WSS_BASE_URL$path?$query"
    }

    private fun asrQueryParams(): Map<String, String> = sortedMapOf(
        "android_version" to "9",
        "client_version" to "0",
        "engineid" to ASR_ENGINE_ID,
        "model" to "modelX",
        "net_type" to "0",
        "package" to appContext.packageName,
        "sdk_version" to "0",
        "system_time" to TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toString(),
        "system_version" to "0",
        "user_id" to VIVO_USER_ID,
    )

    private fun ttsQueryParams(): Map<String, String> = sortedMapOf(
        "android_version" to "9",
        "client_version" to "0",
        "engineid" to TTS_ENGINE_ID,
        "model" to "modelX",
        "package" to appContext.packageName,
        "product" to "productX",
        "sdk_version" to "0",
        "system_time" to TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toString(),
        "system_version" to "0",
        "user_id" to VIVO_USER_ID,
    )

    private fun isConfigured(): Boolean {
        return BuildConfig.VIVO_APP_ID.isNotBlank() && BuildConfig.VIVO_APP_KEY.isNotBlank()
    }

    private fun ByteArray.sendInChunks(
        webSocket: WebSocket,
        chunkSize: Int,
    ) {
        var offset = 0
        while (offset < size) {
            val byteCount = minOf(chunkSize, size - offset)
            webSocket.send(this.toByteString(offset, byteCount))
            offset += byteCount
        }
    }

    private fun File.readVivoAsrWav(): WavAudio {
        val bytes = readBytes()
        require(bytes.size > WAV_HEADER_SIZE) { "WAV file is too short" }
        require(bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 4) == "WAVE") {
            "Not a RIFF/WAVE file"
        }

        var offset = 12
        var audioFormat: Int? = null
        var channels: Int? = null
        var sampleRate: Int? = null
        var bitsPerSample: Int? = null
        var pcmData: ByteArray? = null

        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.ascii(offset, 4)
            val chunkSize = bytes.leInt(offset + 4)
            val dataOffset = offset + 8
            if (chunkSize < 0 || dataOffset + chunkSize > bytes.size) {
                break
            }

            when (chunkId) {
                "fmt " -> {
                    require(chunkSize >= 16) { "Invalid fmt chunk" }
                    audioFormat = bytes.leShort(dataOffset)
                    channels = bytes.leShort(dataOffset + 2)
                    sampleRate = bytes.leInt(dataOffset + 4)
                    bitsPerSample = bytes.leShort(dataOffset + 14)
                }

                "data" -> {
                    pcmData = bytes.copyOfRange(dataOffset, dataOffset + chunkSize)
                }
            }

            offset = dataOffset + chunkSize + (chunkSize and 1)
        }

        require(audioFormat == WAV_PCM_FORMAT) { "Only PCM WAV is supported" }
        require(channels == ASR_CHANNELS) { "ASR requires mono audio" }
        require(sampleRate == ASR_SAMPLE_RATE) { "ASR requires 16k sample rate" }
        require(bitsPerSample == ASR_BITS_PER_SAMPLE) { "ASR requires 16-bit audio" }
        val data = requireNotNull(pcmData) { "WAV data chunk not found" }
        require(data.isNotEmpty()) { "WAV data is empty" }
        return WavAudio(data)
    }

    private fun ByteArray.toWavBytes(
        sampleRate: Int,
        channels: Int = 1,
        bitsPerSample: Int = 16,
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = size
        val output = ByteArrayOutputStream(WAV_HEADER_SIZE + dataSize)
        output.writeAscii("RIFF")
        output.writeIntLE(dataSize + 36)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeIntLE(16)
        output.writeShortLE(WAV_PCM_FORMAT)
        output.writeShortLE(channels)
        output.writeIntLE(sampleRate)
        output.writeIntLE(byteRate)
        output.writeShortLE(blockAlign)
        output.writeShortLE(bitsPerSample)
        output.writeAscii("data")
        output.writeIntLE(dataSize)
        output.write(this)
        return output.toByteArray()
    }

    private fun ByteArray.detectAudioFileType(): TtsFileType? {
        return when {
            isWavBytes() -> TtsFileType.WAV
            isAacBytes() -> TtsFileType.AAC
            isMp3Bytes() -> TtsFileType.MP3
            isM4aBytes() -> TtsFileType.M4A
            else -> null
        }
    }

    private fun ByteArray.isWavBytes(): Boolean {
        return size >= 12 && ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE"
    }

    private fun ByteArray.isMp3Bytes(): Boolean {
        if (size >= 3 && ascii(0, 3) == "ID3") {
            return true
        }
        return size >= 2 &&
            (this[0].toInt() and 0xff) == 0xff &&
            (this[1].toInt() and 0xe0) == 0xe0 &&
            (this[1].toInt() and 0x06) != 0
    }

    private fun ByteArray.isAacBytes(): Boolean {
        return size >= 2 &&
            (this[0].toInt() and 0xff) == 0xff &&
            (this[1].toInt() and 0xf6) == 0xf0
    }

    private fun ByteArray.isM4aBytes(): Boolean {
        return size >= 12 && ascii(4, 4) == "ftyp"
    }

    private fun String.toTtsFileType(): TtsFileType? {
        val value = lowercase()
        return when {
            "wav" in value || "x-wav" in value -> TtsFileType.WAV
            "mpeg" in value || "mp3" in value -> TtsFileType.MP3
            "mp4" in value || "m4a" in value -> TtsFileType.M4A
            "aac" in value -> TtsFileType.AAC
            else -> null
        }
    }

    private fun String.decodeBase64Audio(): ByteArray {
        val base64Value = substringAfter("base64,", this).trim()
        return Base64.decode(base64Value, Base64.DEFAULT)
    }

    private fun String.hmacSha256Base64(key: String): String {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), HMAC_SHA256))
        return Base64.encodeToString(mac.doFinal(toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun sortedEncodedQuery(queryParams: Map<String, String>): String {
        return TreeMap(queryParams).entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
    }

    private fun String.urlEncode(): String {
        return java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(NONCE_LENGTH)
        secureRandom.nextBytes(bytes)
        return buildString(NONCE_LENGTH) {
            bytes.forEach { raw ->
                append(NONCE_CHARS[(raw.toInt() and 0xff) % NONCE_CHARS.length])
            }
        }
    }

    private fun ByteArray.ascii(
        offset: Int,
        length: Int,
    ): String = String(this, offset, length, Charsets.US_ASCII)

    private fun ByteArray.leInt(offset: Int): Int {
        return (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun ByteArray.leShort(offset: Int): Int {
        return (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeIntLE(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
        write((value shr 16) and 0xff)
        write((value shr 24) and 0xff)
    }

    private fun ByteArrayOutputStream.writeShortLE(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonObject.stringValue(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }

    private fun JsonObject.intValue(key: String): Int? {
        return this[key]?.jsonPrimitive?.intOrNull
    }

    private fun JsonObject.booleanValue(key: String): Boolean? {
        return this[key]?.jsonPrimitive?.booleanOrNull
    }

    private fun JsonObject?.firstNonBlankString(vararg keys: String): String? {
        if (this == null) {
            return null
        }
        return keys.firstNotNullOfOrNull { key ->
            stringValue(key)?.takeIf { it.isNotBlank() }
        }
    }

    private fun <T> completeIfNeeded(
        result: CompletableDeferred<T>,
        value: T,
    ) {
        if (!result.isCompleted) {
            result.complete(value)
        }
    }

    private fun <T> completeExceptionally(
        result: CompletableDeferred<T>,
        error: Throwable,
    ) {
        if (!result.isCompleted) {
            result.completeExceptionally(error)
        }
    }

    private data class WavAudio(
        val pcmData: ByteArray,
    )

    private sealed class TtsAudioPayload {
        data class AudioBytes(
            val bytes: ByteArray,
        ) : TtsAudioPayload()

        data class RemoteUrl(
            val url: String,
        ) : TtsAudioPayload()
    }

    private enum class TtsFileType(
        val extension: String,
    ) {
        WAV("wav"),
        MP3("mp3"),
        M4A("m4a"),
        AAC("aac"),
    }

    private companion object {
        private const val TAG = "VivoAiRepository"
        private const val HTTPS_BASE_URL = "https://api-ai.vivo.com.cn"
        private const val WSS_BASE_URL = "wss://api-ai.vivo.com.cn"
        private const val CHAT_PATH = "/v1/chat/completions"
        private const val ASR_PATH = "/asr/v2"
        private const val TTS_PATH = "/tts"
        private val CHAT_MODEL = BuildConfig.VIVO_CHAT_MODEL.ifBlank { "Doubao-Seed-2.0-mini" }
        private const val MODEL_ACCESS_PERMISSION_ERROR = "no model access permission"
        private const val ASR_ENGINE_ID = "shortasrinput"
        private const val TTS_ENGINE_ID = "short_audio_synthesis_jovi"
        private const val TTS_VOICE = "xiaofu"
        private const val TTS_OUTPUT_FILE_PREFIX = "vivo_ai_reply"
        private const val TTS_REQUEST_ID = 513722013
        private const val TTS_AUE_PCM = 0
        private const val TTS_STATUS_FINISHED = 2
        private const val TTS_SAMPLE_RATE = 24_000
        private const val ASR_SAMPLE_RATE = 16_000
        private const val ASR_BITS_PER_SAMPLE = 16
        private const val ASR_CHANNELS = 1
        private const val ASR_CHUNK_SIZE_BYTES = 1280
        private const val ASR_TIMEOUT_MILLIS = 45_000L
        private const val TTS_TIMEOUT_MILLIS = 45_000L
        private const val NORMAL_CLOSURE_STATUS = 1000
        private const val WAV_HEADER_SIZE = 44
        private const val WAV_PCM_FORMAT = 1
        private const val NONCE_LENGTH = 8
        private const val VIVO_USER_ID = "heartguard-user"
        private const val HTTP_GET = "GET"
        private const val HTTP_POST = "POST"
        private const val HMAC_SHA256 = "HmacSHA256"
        private const val ROLE_USER = "user"
        private const val ROLE_SYSTEM = "system"
        private const val MAX_CHAT_HISTORY_MESSAGES = 20
        private const val ASR_END_MARKER = "--end--"
        private const val ASR_CLOSE_MARKER = "--close--"
        private const val SIGNED_HEADERS =
            "x-ai-gateway-app-id;x-ai-gateway-timestamp;x-ai-gateway-nonce"
        private const val NONCE_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val secureRandom = SecureRandom()

        private fun createClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
