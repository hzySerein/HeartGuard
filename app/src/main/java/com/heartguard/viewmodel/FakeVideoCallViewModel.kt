package com.heartguard.viewmodel

import com.heartguard.utils.DebugLogger

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heartguard.R
import com.heartguard.data.local.AppDao
import com.heartguard.data.local.FraudRecordEntity
import com.heartguard.data.remote.AiGateway
import com.heartguard.utils.AudioEngine
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VideoCallState {
    CALLING,
    WARNING,
    HUNG_UP,
}

class FakeVideoCallViewModel(
    private val appDao: AppDao,
    private val aiGateway: AiGateway? = null,
    private val audioEngine: AudioEngine = AudioEngine,
    private val appContext: Context? = null,
) : ViewModel() {
    private val _callState = MutableStateFlow(VideoCallState.CALLING)
    val callState: StateFlow<VideoCallState> = _callState.asStateFlow()

    private val _scamMessage = MutableStateFlow("")
    val scamMessage: StateFlow<String> = _scamMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _videoResId = MutableStateFlow(0)
    val videoResId: StateFlow<Int> = _videoResId.asStateFlow()

    private val _sessionId = MutableStateFlow(0L)
    val sessionId: StateFlow<Long> = _sessionId.asStateFlow()

    private var warningTimeoutJob: Job? = null
    private var speechJob: Job? = null
    private var fraudRecordPersisted = false
    private var currentScenarioType = DEFAULT_SCENARIO_TYPE

    init {
        selectNextVideo()
    }

    fun startNewDrill(
        scenarioId: String? = null,
        scamText: String? = null,
    ) {
        warningTimeoutJob?.cancel()
        speechJob?.cancel()
        audioEngine.stopPlaying()
        currentScenarioType = scenarioId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SCENARIO_TYPE
        fraudRecordPersisted = false
        _isLoading.value = false
        _sessionId.value = _sessionId.value + 1L
        _callState.value = VideoCallState.CALLING
        selectNextVideo()
        generateScamSpeech(
            sessionId = _sessionId.value,
            scamText = scamText,
        )
    }

    fun triggerWarning(sessionId: Long) {
        if (_sessionId.value == sessionId && _callState.value == VideoCallState.CALLING) {
            _callState.value = VideoCallState.WARNING
            startWarningTimeout(sessionId)
        }
    }

    fun hangUpCall() {
        if (_callState.value == VideoCallState.HUNG_UP) {
            return
        }
        warningTimeoutJob?.cancel()
        speechJob?.cancel()
        audioEngine.stopPlaying()
        _isLoading.value = false
        _callState.value = VideoCallState.HUNG_UP
        persistFraudRecord(passed = true)
    }

    override fun onCleared() {
        warningTimeoutJob?.cancel()
        speechJob?.cancel()
        audioEngine.stopPlaying()
        super.onCleared()
    }

    private fun generateScamSpeech(
        sessionId: Long,
        scamText: String? = null,
    ) {
        val scamSpeechText = scamText
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: appString(SCAM_MESSAGE_RES_IDS.random())
        _scamMessage.value = scamSpeechText

        val repository = aiGateway ?: return
        _isLoading.value = true
        speechJob = viewModelScope.launch {
            try {
                val audioPath = repository.textToSpeech(scamSpeechText).trim()
                if (
                    _sessionId.value == sessionId &&
                    _callState.value != VideoCallState.HUNG_UP &&
                    looksPlayable(audioPath)
                ) {
                    audioEngine.playAudio(audioPath) {
                        // Playback completion does not change the drill state.
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to play video scam speech", error)
            } finally {
                if (_sessionId.value == sessionId) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun selectNextVideo() {
        val videos = collectVideoResIds(appContext)
        if (videos.isNotEmpty()) {
            _videoResId.value = videos.first()
        }
    }

    private fun appString(@StringRes resId: Int): String {
        return appContext?.getString(resId).orEmpty()
    }

    private fun startWarningTimeout(sessionId: Long) {
        warningTimeoutJob?.cancel()
        warningTimeoutJob = viewModelScope.launch {
            delay(WARNING_TIMEOUT_MILLIS)
            if (_sessionId.value == sessionId && _callState.value == VideoCallState.WARNING) {
                speechJob?.cancel()
                audioEngine.stopPlaying()
                _isLoading.value = false
                _callState.value = VideoCallState.HUNG_UP
                persistFraudRecord(passed = false)
            }
        }
    }

    private fun persistFraudRecord(passed: Boolean) {
        if (fraudRecordPersisted) {
            return
        }
        fraudRecordPersisted = true
        val scenarioType = currentScenarioType
        viewModelScope.launch {
            withContext(Dispatchers.IO + NonCancellable) {
                try {
                    appDao.insertFraudRecord(
                        FraudRecordEntity(
                            scenarioType = scenarioType,
                            passed = passed,
                            timestamp = System.currentTimeMillis(),
                        )
                    )
                } catch (error: Exception) {
                    DebugLogger.e(TAG, "Failed to save video fraud record", error)
                }
            }
        }
    }

    private fun looksPlayable(audioPath: String): Boolean {
        return audioPath.isNotBlank() && File(audioPath).exists()
    }

    private companion object {
        const val TAG = "FakeVideoCallVM"
        const val DEFAULT_SCENARIO_TYPE = "video_fraud"
        const val WARNING_TIMEOUT_MILLIS = 12_000L

        private fun collectVideoResIds(context: Context?): List<Int> {
            val rawClassName = context?.packageName?.let { "$it.R\$raw" }
                ?: "${R::class.java.name}\$raw"
            val rawClass = runCatching {
                Class.forName(rawClassName)
            }.getOrElse { error ->
                DebugLogger.w(TAG, "No raw resources found for video drill", error)
                return emptyList()
            }

            return rawClass.declaredFields
                .filter { field -> field.name.startsWith("scammer_video") }
                .mapNotNull { field ->
                    runCatching { field.getInt(null) }
                        .onFailure { error ->
                            DebugLogger.w(TAG, "Failed to read raw video resource: ${field.name}", error)
                        }
                        .getOrNull()
                }
                .shuffled()
        }

        val SCAM_MESSAGE_RES_IDS = listOf(
            R.string.scam_video_message_anti_fraud,
        )
    }
}
