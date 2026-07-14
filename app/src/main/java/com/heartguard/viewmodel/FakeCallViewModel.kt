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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class FakeCallState {
    RINGING,
    ANSWERED,
    HUNG_UP,
}

class FakeCallViewModel(
    private val appDao: AppDao,
    private val aiGateway: AiGateway? = null,
    private val audioEngine: AudioEngine = AudioEngine,
    private val appContext: Context? = null,
) : ViewModel() {
    private val _callState = MutableStateFlow(FakeCallState.RINGING)
    val callState: StateFlow<FakeCallState> = _callState.asStateFlow()

    private val _scamMessage = MutableStateFlow("")
    val scamMessage: StateFlow<String> = _scamMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sessionId = MutableStateFlow(0L)
    val sessionId: StateFlow<Long> = _sessionId.asStateFlow()

    private var speechJob: Job? = null
    private var fraudRecordPersisted = false
    private var currentScenarioType = DEFAULT_SCENARIO_TYPE

    fun startNewDrill(scenarioId: String? = null) {
        speechJob?.cancel()
        audioEngine.stopPlaying()
        currentScenarioType = scenarioId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SCENARIO_TYPE
        fraudRecordPersisted = false
        _isLoading.value = false
        _scamMessage.value = ""
        _sessionId.value = _sessionId.value + 1L
        _callState.value = FakeCallState.RINGING
    }

    fun answerCall(scamText: String? = null) {
        if (_callState.value != FakeCallState.RINGING) {
            return
        }
        _callState.value = FakeCallState.ANSWERED
        generateScamSpeech(
            sessionId = _sessionId.value,
            scamText = scamText,
        )
    }

    fun hangUpCall() {
        if (_callState.value == FakeCallState.HUNG_UP) {
            return
        }
        speechJob?.cancel()
        audioEngine.stopPlaying()
        _isLoading.value = false
        _callState.value = FakeCallState.HUNG_UP
        persistFraudRecord(passed = true)
    }

    fun finishDrill() {
        speechJob?.cancel()
        audioEngine.stopPlaying()
        _isLoading.value = false
    }

    private fun generateScamSpeech(
        sessionId: Long,
        scamText: String? = null,
    ) {
        val repository = aiGateway
        val scamSpeechText = scamText
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: appString(SCAM_MESSAGE_RES_IDS.random())
        _scamMessage.value = scamSpeechText
        if (repository == null) {
            return
        }
        _isLoading.value = true
        speechJob = viewModelScope.launch {
            try {
                val audioPath = repository.textToSpeech(scamSpeechText).trim()
                if (
                    _sessionId.value == sessionId &&
                    _callState.value == FakeCallState.ANSWERED &&
                    looksPlayable(audioPath)
                ) {
                    audioEngine.playAudio(audioPath) {
                        // Playback completion does not change call state.
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to play scam speech", error)
            } finally {
                if (_sessionId.value == sessionId) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun looksPlayable(audioPath: String): Boolean {
        val failedKeyword = appString(R.string.common_error_failed_keyword)
        val unavailableKeyword = appString(R.string.common_error_unavailable_keyword)
        return audioPath.isNotBlank() &&
            (failedKeyword.isBlank() || !audioPath.contains(failedKeyword)) &&
            (unavailableKeyword.isBlank() || !audioPath.contains(unavailableKeyword)) &&
            File(audioPath).exists()
    }

    private fun appString(@StringRes resId: Int): String {
        return appContext?.getString(resId).orEmpty()
    }

    override fun onCleared() {
        speechJob?.cancel()
        audioEngine.stopPlaying()
        super.onCleared()
    }

    private fun persistFraudRecord(passed: Boolean) {
        if (fraudRecordPersisted) {
            return
        }
        fraudRecordPersisted = true
        val scenarioType = currentScenarioType
        viewModelScope.launch {
            try {
                appDao.insertFraudRecord(
                    FraudRecordEntity(
                        scenarioType = scenarioType,
                        passed = passed,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to save fraud record", error)
            }
        }
    }

    private companion object {
        const val TAG = "FakeCallViewModel"
        const val DEFAULT_SCENARIO_TYPE = "fake_call"
        val SCAM_MESSAGE_RES_IDS = listOf(
            R.string.scam_call_message_bank,
        )
    }
}
