package com.heartguard.viewmodel

import com.heartguard.utils.DebugLogger

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heartguard.R
import com.heartguard.data.local.AppDao
import com.heartguard.data.local.MEDICATION_CUSTOM_REPEAT_DEFAULT_INTERVAL_DAYS
import com.heartguard.data.local.MEDICATION_CUSTOM_REPEAT_MAX_INTERVAL_DAYS
import com.heartguard.data.local.MEDICATION_CUSTOM_REPEAT_MIN_INTERVAL_DAYS
import com.heartguard.data.local.MEDICATION_REPEAT_CUSTOM
import com.heartguard.data.local.MEDICATION_REPEAT_DAILY
import com.heartguard.data.local.MEDICATION_REPEAT_EVERY_OTHER_DAY
import com.heartguard.data.local.MEDICATION_REPEAT_ONCE
import com.heartguard.data.local.MedicationEntity
import com.heartguard.data.local.MedicationTodayProgress
import com.heartguard.data.local.normalizeMedicationRepeatIntervalDays
import com.heartguard.data.local.reminderTimes
import com.heartguard.data.local.toRecordEntity
import com.heartguard.data.remote.AiGateway
import com.heartguard.reminder.ReminderAlarmReceiver
import com.heartguard.reminder.ReminderLaunchEvent
import com.heartguard.reminder.ReminderScheduler
import com.heartguard.ui.widget.updateHeartGuardWidgets
import com.heartguard.utils.AudioEngine
import com.heartguard.utils.NativeOCRHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

data class MedicationReminderState(
    val medication: MedicationEntity,
    val matchedTime: String,
    val isDemo: Boolean = false,
)

enum class MedicationImportSource {
    Image,
    Voice,
    Text,
}

data class MedicationImportDraft(
    val name: String = "",
    val dosage: String = "",
    val perDose: String = "",
    val timesOfDay: String = "",
    val repeatRule: String = "",
    val stockCount: String = "",
)

data class MedicationImportConfirmationState(
    val source: MedicationImportSource,
    val sourceName: String,
    val drafts: List<MedicationImportDraft>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationViewModel(
    private val appDao: AppDao,
    private val aiGateway: AiGateway? = null,
    private val nativeOCRHelper: NativeOCRHelper? = null,
    private val audioEngine: AudioEngine = AudioEngine,
    enableReminderLoop: Boolean = true,
    private val appContext: Context? = null,
) : ViewModel() {
    private val todayRange = MutableStateFlow(currentDayMillisRange())
    val medications: StateFlow<List<MedicationEntity>> = todayRange
        .flatMapLatest { range ->
            appDao.observeTodayMedications(
                startOfDay = range.startOfDay,
                endOfDay = range.endOfDay,
                targetEpochDay = range.epochDay,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = emptyList(),
        )

    val medicationProgressList: StateFlow<List<MedicationTodayProgress>> = medications
        .flatMapLatest { meds ->
            flow {
                val todayEpochDay = currentDayMillisRange().epochDay
                val takenTimeMap = mutableMapOf<Long, Set<String>>()
                meds.forEach { med ->
                    if (med.scheduleId > 0L) {
                        val times = appDao.getTakenTimesForDay(
                            scheduleId = med.scheduleId,
                            takenDateEpochDay = todayEpochDay,
                        )
                        takenTimeMap[med.scheduleId] = times.toSet()
                    }
                }
                val progressList = meds.map { med ->
                    val takenTimes = takenTimeMap[med.scheduleId].orEmpty()
                    val todayTimes = med.reminderTimes()
                    val nextUntaken = todayTimes.firstOrNull { time ->
                        time !in takenTimes && "" !in takenTimes
                    }
                    MedicationTodayProgress(
                        medicationId = med.id,
                        totalCount = med.totalTimes,
                        takenCount = med.takenCount,
                        isAllTaken = med.isTaken,
                        isEnabled = med.reminderEnabled,
                        nextUnTakenTime = nextUntaken,
                        todayTimes = todayTimes,
                        takenTimes = takenTimes.filter { it.isNotEmpty() }.toSet(),
                    )
                }
                emit(progressList)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = emptyList(),
        )

    private val _statusMessage = MutableStateFlow(appString(R.string.medication_initial_status))
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _currentReminder = MutableStateFlow<MedicationReminderState?>(null)
    val currentReminder: StateFlow<MedicationReminderState?> = _currentReminder.asStateFlow()

    private val _isImageImporting = MutableStateFlow(false)
    val isImageImporting: StateFlow<Boolean> = _isImageImporting.asStateFlow()

    private val _isVoiceImportRecording = MutableStateFlow(false)
    val isVoiceImportRecording: StateFlow<Boolean> = _isVoiceImportRecording.asStateFlow()

    private val _pendingImportConfirmation = MutableStateFlow<MedicationImportConfirmationState?>(null)
    val pendingImportConfirmation: StateFlow<MedicationImportConfirmationState?> =
        _pendingImportConfirmation.asStateFlow()

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessages: SharedFlow<String> = _toastMessages.asSharedFlow()

    private val _reminderEvent = MutableSharedFlow<ReminderLaunchEvent>(extraBufferCapacity = 1)
    val reminderEvent: SharedFlow<ReminderLaunchEvent> = _reminderEvent.asSharedFlow()

    private val _manualReminderName = MutableStateFlow("")
    val manualReminderName: StateFlow<String> = _manualReminderName.asStateFlow()

    private val _manualDosageAmount = MutableStateFlow(1f)
    val manualDosageAmount: StateFlow<Float> = _manualDosageAmount.asStateFlow()

    private val _manualReminderHour = MutableStateFlow(8)
    val manualReminderHour: StateFlow<Int> = _manualReminderHour.asStateFlow()

    private val _manualReminderMinute = MutableStateFlow(0)
    val manualReminderMinute: StateFlow<Int> = _manualReminderMinute.asStateFlow()

    private val _manualReminderPeriods = MutableStateFlow(setOf(MANUAL_REMINDER_MORNING_TIME))
    val manualReminderPeriods: StateFlow<Set<String>> = _manualReminderPeriods.asStateFlow()

    private val _manualRepeatOption = MutableStateFlow(
        appString(R.string.medication_repeat_daily).ifBlank { "每天" },
    )
    val manualRepeatOption: StateFlow<String> = _manualRepeatOption.asStateFlow()

    private val _manualRepeatIntervalDays = MutableStateFlow(MEDICATION_CUSTOM_REPEAT_DEFAULT_INTERVAL_DAYS)
    val manualRepeatIntervalDays: StateFlow<Int> = _manualRepeatIntervalDays.asStateFlow()

    private val _manualReminderStyle = MutableStateFlow(
        appString(R.string.medication_style_capsule).ifBlank { "胶囊" },
    )
    val manualReminderStyle: StateFlow<String> = _manualReminderStyle.asStateFlow()

    private val _manualReminderNote = MutableStateFlow("")
    val manualReminderNote: StateFlow<String> = _manualReminderNote.asStateFlow()

    private val _manualEditingMedicationId = MutableStateFlow<Long?>(null)
    val manualEditingMedicationId: StateFlow<Long?> = _manualEditingMedicationId.asStateFlow()

    init {
        if (enableReminderLoop) {
            rescheduleAllMedicationReminders()
        }
    }

    override fun onCleared() {
        runCatching { audioEngine.stopRecording() }
        audioEngine.stopPlaying()
        nativeOCRHelper?.close()
        super.onCleared()
    }

    fun addMedication(
        name: String,
        dosage: String,
        stockCount: Int,
        timesOfDay: String,
        note: String = "",
    ) {
        val safeName = name.trim()
        val safeDosage = dosage.trim()
        val safeTimes = timesOfDay
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(",")
        if (safeName.isBlank() || safeDosage.isBlank() || safeTimes.isBlank()) {
            _statusMessage.value = appString(R.string.medication_missing_fields)
            return
        }

        viewModelScope.launch {
            try {
                val savedMedication = withContext(Dispatchers.IO) {
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
                    appDao.getMedicationById(medicationId)
                }
                savedMedication?.let { scheduleMedicationReminder(it) }
                refreshMedicationWidget()
                _statusMessage.value = appString(
                    R.string.medication_added_status,
                    safeTimes,
                    safeName,
                    safeDosage,
                )
                _toastMessages.tryEmit(appString(R.string.medication_add_success))
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to add medication", error)
                _statusMessage.value = appString(R.string.medication_save_reminder_failed)
            }
        }
    }

    fun setManualReminderName(name: String) {
        _manualReminderName.value = name
    }

    fun selectManualQuickTag(tag: String) {
        _manualReminderName.value = tag
    }

    fun increaseManualDosage() {
        _manualDosageAmount.value = (_manualDosageAmount.value + DOSAGE_STEP)
            .coerceAtMost(MAX_DOSAGE_AMOUNT)
    }

    fun decreaseManualDosage() {
        _manualDosageAmount.value = (_manualDosageAmount.value - DOSAGE_STEP)
            .coerceAtLeast(MIN_DOSAGE_AMOUNT)
    }

    fun increaseManualHour() {
        _manualReminderHour.value = (_manualReminderHour.value + 1).floorMod(24)
    }

    fun decreaseManualHour() {
        _manualReminderHour.value = (_manualReminderHour.value - 1).floorMod(24)
    }

    fun increaseManualMinute() {
        _manualReminderMinute.value = (_manualReminderMinute.value + 5).floorMod(60)
    }

    fun decreaseManualMinute() {
        _manualReminderMinute.value = (_manualReminderMinute.value - 5).floorMod(60)
    }

    fun toggleManualReminderPeriod(time: String) {
        if (time !in MANUAL_REMINDER_TIMES) {
            return
        }
        val selectedPeriods = _manualReminderPeriods.value
        _manualReminderPeriods.value = if (time in selectedPeriods) {
            (selectedPeriods - time).ifEmpty { setOf(time) }
        } else {
            selectedPeriods + time
        }
    }

    fun setManualRepeatOption(option: String) {
        _manualRepeatOption.value = option
        if (repeatTypeForManualOption(option) == MEDICATION_REPEAT_CUSTOM) {
            _manualRepeatIntervalDays.value = _manualRepeatIntervalDays.value
                .coerceIn(
                    MEDICATION_CUSTOM_REPEAT_MIN_INTERVAL_DAYS,
                    MEDICATION_CUSTOM_REPEAT_MAX_INTERVAL_DAYS,
                )
        }
    }

    fun increaseManualRepeatIntervalDays() {
        _manualRepeatIntervalDays.value = (_manualRepeatIntervalDays.value + 1)
            .coerceAtMost(MEDICATION_CUSTOM_REPEAT_MAX_INTERVAL_DAYS)
    }

    fun decreaseManualRepeatIntervalDays() {
        _manualRepeatIntervalDays.value = (_manualRepeatIntervalDays.value - 1)
            .coerceAtLeast(MEDICATION_CUSTOM_REPEAT_MIN_INTERVAL_DAYS)
    }

    fun setManualReminderStyle(style: String) {
        _manualReminderStyle.value = style
    }

    fun setManualReminderNote(note: String) {
        _manualReminderNote.value = note
    }

    fun resetManualReminderForm() {
        _manualEditingMedicationId.value = null
        _manualReminderName.value = ""
        _manualDosageAmount.value = 1f
        _manualReminderHour.value = 8
        _manualReminderMinute.value = 0
        _manualReminderPeriods.value = setOf(MANUAL_REMINDER_MORNING_TIME)
        _manualRepeatOption.value = appString(R.string.medication_repeat_daily).ifBlank { "每天" }
        _manualRepeatIntervalDays.value = MEDICATION_CUSTOM_REPEAT_DEFAULT_INTERVAL_DAYS
        _manualReminderStyle.value = appString(R.string.medication_style_capsule).ifBlank { "胶囊" }
        _manualReminderNote.value = ""
    }

    fun startEditingManualReminder(medication: MedicationEntity) {
        _manualEditingMedicationId.value = medication.id
        _manualReminderName.value = medication.name
        _manualDosageAmount.value = parseDosageAmount(medication.dosage)

        val parsedTime = parseFirstReminderTime(medication.timesOfDay)
        _manualReminderHour.value = parsedTime.first
        _manualReminderMinute.value = parsedTime.second
        _manualReminderPeriods.value = parseManualReminderPeriods(medication.timesOfDay)

        _manualRepeatOption.value = repeatOptionForMedication(medication)
        _manualRepeatIntervalDays.value = if (medication.repeatType == MEDICATION_REPEAT_CUSTOM) {
            normalizeMedicationRepeatIntervalDays(
                repeatType = medication.repeatType,
                intervalDays = medication.repeatIntervalDays,
            )
        } else {
            MEDICATION_CUSTOM_REPEAT_DEFAULT_INTERVAL_DAYS
        }
        _manualReminderStyle.value = parseReminderStyle(medication.note)
        _manualReminderNote.value = parseFreeNote(medication.note)
    }

    fun saveManualReminder(): Boolean {
        val safeName = _manualReminderName.value.trim()
        if (safeName.isBlank()) {
            val message = appString(R.string.medication_manual_name_required)
            _statusMessage.value = message
            _toastMessages.tryEmit(message)
            return false
        }

        val dosage = appString(
            R.string.medication_dosage_piece,
            formatDosageAmount(_manualDosageAmount.value),
        )
        val time = formatManualReminderTimes()
        if (time.isBlank()) {
            val message = appString(R.string.medication_manual_time_required)
            _statusMessage.value = message
            _toastMessages.tryEmit(message)
            return false
        }
        val editingId = _manualEditingMedicationId.value
        val repeatType = repeatTypeForManualOption(_manualRepeatOption.value)
        val repeatIntervalDays = repeatIntervalDaysForType(
            repeatType = repeatType,
            customIntervalDays = _manualRepeatIntervalDays.value,
        )
        val noteParts = listOf(
            appString(R.string.medication_note_repeat, _manualRepeatOption.value)
                .takeIf { it.isNotBlank() },
            appString(R.string.medication_note_repeat_interval, repeatIntervalDays)
                .takeIf { repeatType == MEDICATION_REPEAT_CUSTOM && it.isNotBlank() },
            appString(R.string.medication_note_style, _manualReminderStyle.value)
                .takeIf { it.isNotBlank() },
            _manualReminderNote.value.trim().takeIf { it.isNotBlank() },
        ).filterNotNull()

        viewModelScope.launch {
            try {
                val savedMedication = withContext(Dispatchers.IO) {
                    val updatedMedication = MedicationEntity(
                        id = editingId ?: 0L,
                        name = safeName,
                        dosage = dosage,
                        timesOfDay = time,
                        note = noteParts.joinToString("；"),
                        isTaken = false,
                        ringtoneUri = null,
                        stockCount = DEFAULT_STOCK_COUNT,
                        repeatType = repeatType,
                        repeatIntervalDays = repeatIntervalDays,
                    )
                    if (editingId == null) {
                        val medicationId = appDao.insertMedication(updatedMedication)
                        appDao.getMedicationById(medicationId)
                    } else {
                        val existing = appDao.getMedicationById(editingId)
                            ?: error("Medication not found for editing: $editingId")
                        cancelMedicationReminder(existing)
                        val mergedMedication = updatedMedication.copy(
                            isTaken = existing.isTaken,
                            ringtoneUri = existing.ringtoneUri,
                            stockCount = existing.stockCount,
                            createdAt = existing.createdAt,
                            scheduleId = existing.scheduleId,
                            reminderEnabled = existing.reminderEnabled,
                            repeatType = repeatType,
                            repeatIntervalDays = repeatIntervalDays,
                            startDateEpochDay = existing.startDateEpochDay,
                        )
                        if (
                            existing.name != mergedMedication.name ||
                                existing.dosage != mergedMedication.dosage ||
                                existing.timesOfDay != mergedMedication.timesOfDay
                        ) {
                            DebugLogger.i(
                                TAG,
                                "Medication core fields updated id=$editingId from=$existing to=$mergedMedication",
                            )
                        }
                        appDao.updateMedication(mergedMedication)
                        appDao.getMedicationById(editingId)
                    }
                }
                savedMedication?.let { scheduleMedicationReminder(it) }
                refreshMedicationWidget()
                _statusMessage.value = if (editingId == null) {
                    appString(R.string.medication_added_status, time, safeName, dosage)
                } else {
                    appString(R.string.medication_updated_status, safeName)
                }
                _toastMessages.emit(
                    if (editingId == null) {
                        appString(R.string.medication_add_success)
                    } else {
                        appString(R.string.medication_update_success)
                    }
                )
                resetManualReminderForm()
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to save manual reminder", error)
                _statusMessage.value = appString(R.string.medication_save_reminder_failed)
                _toastMessages.emit(appString(R.string.medication_save_failed))
            }
        }

        return true
    }

    fun deleteEditingManualReminder(): Boolean {
        val editingId = _manualEditingMedicationId.value ?: return false

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appDao.getMedicationById(editingId)?.let { medication ->
                        cancelMedicationReminder(medication)
                    }
                    appDao.deleteMedication(editingId)
                }
                refreshMedicationWidget()
                if (_currentReminder.value?.medication?.id == editingId) {
                    dismissReminderInternal()
                }
                _statusMessage.value = appString(R.string.medication_deleted_manual_status)
                _toastMessages.emit(appString(R.string.medication_delete_success))
                resetManualReminderForm()
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to delete manual reminder", error)
                _statusMessage.value = appString(R.string.medication_delete_failed_status)
                _toastMessages.emit(appString(R.string.medication_delete_failed))
            }
        }

        return true
    }

    fun importMedicationFromImage(uri: Uri) {
        if (_isImageImporting.value) {
            return
        }

        viewModelScope.launch {
            _isImageImporting.value = true
            _statusMessage.value = appString(R.string.medication_image_importing)
            try {
                val helper = nativeOCRHelper ?: run {
                    postImportStatus(appString(R.string.medication_ocr_unavailable))
                    return@launch
                }
                val rawText = withContext(Dispatchers.IO) {
                    helper.extractRawText(uri)
                }
                importMedicationTextInternal(
                    rawText = rawText,
                    sourceName = appString(R.string.medication_source_image),
                    source = MedicationImportSource.Image,
                )
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to import medication image", error)
                postImportStatus(appString(R.string.medication_image_recognition_failed))
            } finally {
                _isImageImporting.value = false
            }
        }
    }

    fun importMedicationFromText(rawText: String) {
        if (_isImageImporting.value) {
            return
        }

        viewModelScope.launch {
            _isImageImporting.value = true
            _statusMessage.value = appString(R.string.medication_text_importing)
            try {
                importMedicationTextInternal(
                    rawText = rawText,
                    sourceName = appString(R.string.medication_source_description),
                    source = MedicationImportSource.Text,
                )
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to import medication text", error)
                postImportStatus(appString(R.string.medication_parse_failed))
            } finally {
                _isImageImporting.value = false
            }
        }
    }

    fun addMedicationFromVoice(rawText: String) {
        if (_isImageImporting.value) {
            return
        }

        viewModelScope.launch {
            _isImageImporting.value = true
            _statusMessage.value = appString(R.string.medication_text_importing)
            try {
                importMedicationTextInternal(
                    rawText = rawText,
                    sourceName = appString(R.string.medication_source_voice),
                    source = MedicationImportSource.Voice,
                )
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to import medication voice text", error)
                postImportStatus(appString(R.string.medication_parse_failed))
            } finally {
                _isImageImporting.value = false
            }
        }
    }

    fun importMedicationsFromOcrText(rawText: String) {
        if (_isImageImporting.value) {
            return
        }

        viewModelScope.launch {
            _isImageImporting.value = true
            _statusMessage.value = appString(R.string.medication_text_importing)
            try {
                importMedicationTextInternal(
                    rawText = appString(R.string.medication_ocr_text_prefix, rawText),
                    sourceName = appString(R.string.medication_source_image),
                    source = MedicationImportSource.Image,
                )
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to import medication OCR text", error)
                postImportStatus(appString(R.string.medication_parse_failed))
            } finally {
                _isImageImporting.value = false
            }
        }
    }

    private suspend fun importMedicationTextInternal(
        rawText: String,
        sourceName: String,
        source: MedicationImportSource,
    ) {
        val recognizedText = rawText.trim()
        if (recognizedText.isBlank()) {
            postImportStatus(appString(R.string.medication_no_clear_text))
            return
        }

        val repository = aiGateway ?: run {
            postImportStatus(appString(R.string.medication_parse_service_unavailable))
            return
        }

        val rawResponse = withContext(Dispatchers.IO) {
            repository.getChatResponse(
                userText = recognizedText,
                systemPrompt = buildMedicationBatchExtractionPrompt(),
            )
        }
        if (rawResponse.isBlank() || looksLikeAiFailure(rawResponse)) {
            postImportStatus(appString(R.string.medication_ai_no_valid_info))
            return
        }

        val drafts = parseMedicationDrafts(rawResponse)
        if (drafts.isEmpty()) {
            postImportStatus(appString(R.string.medication_no_valid_info))
            return
        }

        _pendingImportConfirmation.value = MedicationImportConfirmationState(
            source = source,
            sourceName = sourceName,
            drafts = drafts,
        )
        _statusMessage.value = appString(R.string.medication_ai_confirm_ready, drafts.size)
    }

    fun updateImportDraft(
        index: Int,
        draft: MedicationImportDraft,
    ) {
        val currentState = _pendingImportConfirmation.value ?: return
        if (index !in currentState.drafts.indices) {
            return
        }
        _pendingImportConfirmation.value = currentState.copy(
            drafts = currentState.drafts.mapIndexed { draftIndex, currentDraft ->
                if (draftIndex == index) draft else currentDraft
            },
        )
    }

    fun dismissImportConfirmation() {
        _pendingImportConfirmation.value = null
    }

    fun confirmPendingImport() {
        val currentState = _pendingImportConfirmation.value ?: return
        val medications = currentState.drafts.mapNotNull { draft ->
            draft.toMedicationEntity(currentState.sourceName)
        }
        if (medications.size != currentState.drafts.size || medications.isEmpty()) {
            _statusMessage.value = appString(R.string.medication_confirm_missing_fields)
            _toastMessages.tryEmit(appString(R.string.medication_confirm_missing_fields))
            return
        }

        _pendingImportConfirmation.value = null
        viewModelScope.launch {
            try {
                val savedMedications = withContext(Dispatchers.IO) {
                    medications.mapNotNull { entity ->
                        val medicationId = appDao.insertMedication(entity)
                        appDao.getMedicationById(medicationId)
                    }
                }
                savedMedications.forEach { medication ->
                    scheduleMedicationReminder(medication)
                }
                refreshMedicationWidget()
                postImportStatus(appString(R.string.medication_imported_count, medications.size))
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to save confirmed medication import", error)
                _statusMessage.value = appString(R.string.medication_save_reminder_failed)
            }
        }
    }

    fun startVoiceImportRecording() {
        if (_isImageImporting.value || _isVoiceImportRecording.value) {
            return
        }

        val context = appContext ?: run {
            _statusMessage.value = appString(R.string.chat_recording_not_ready)
            return
        }

        try {
            _pendingImportConfirmation.value = null
            _statusMessage.value = appString(R.string.medication_voice_recording)
            _isVoiceImportRecording.value = true
            audioEngine.startRecording(context)
        } catch (error: Exception) {
            DebugLogger.e(TAG, "Failed to start medication voice import recording", error)
            _isVoiceImportRecording.value = false
            _statusMessage.value = appString(R.string.chat_start_recording_failed)
        }
    }

    fun stopVoiceImportRecordingAndRecognize() {
        if (!_isVoiceImportRecording.value) {
            return
        }

        _isVoiceImportRecording.value = false
        val audioFile = audioEngine.stopRecording() ?: run {
            _statusMessage.value = appString(R.string.chat_no_valid_voice)
            return
        }

        viewModelScope.launch {
            runVoiceMedicationImport(audioFile)
        }
    }

    fun cancelVoiceImportRecording() {
        if (!_isVoiceImportRecording.value) {
            return
        }

        _isVoiceImportRecording.value = false
        runCatching { audioEngine.stopRecording()?.delete() }
        _statusMessage.value = appString(R.string.chat_recording_cancelled)
    }

    private suspend fun runVoiceMedicationImport(audioFile: File) {
        val gateway = aiGateway ?: run {
            _statusMessage.value = appString(R.string.chat_asr_not_configured)
            return
        }

        _isImageImporting.value = true
        _statusMessage.value = appString(R.string.medication_voice_recognizing)
        try {
            val recognizedText = gateway.recognizeSpeech(audioFile).trim()
            if (recognizedText.isBlank() || isVoiceRecognitionError(recognizedText)) {
                postImportStatus(
                    recognizedText.ifBlank {
                        appString(R.string.chat_no_clear_content)
                    },
                )
                return
            }
            importMedicationTextInternal(
                rawText = recognizedText,
                sourceName = appString(R.string.medication_source_voice),
                source = MedicationImportSource.Voice,
            )
        } catch (error: Exception) {
            DebugLogger.e(TAG, "Failed to recognize medication voice import", error)
            postImportStatus(appString(R.string.chat_asr_failed))
        } finally {
            _isImageImporting.value = false
            runCatching { audioFile.delete() }
        }
    }

    private fun postImportStatus(message: String) {
        _statusMessage.value = message
        _toastMessages.tryEmit(message)
    }

    private suspend fun refreshMedicationWidget() {
        val context = appContext ?: return
        runCatching {
            updateHeartGuardWidgets(context)
        }.onFailure { error ->
            DebugLogger.e(TAG, "Failed to update HeartGuard widget.", error)
        }
    }

    private fun rescheduleAllMedicationReminders() {
        val context = appContext ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val medications = appDao.getAllScheduledMedicationsForReminder(
                    targetEpochDay = currentDayMillisRange().epochDay,
                )
                ReminderScheduler.scheduleMedications(context, medications)
            }
        }
    }

    private fun scheduleMedicationReminder(medication: MedicationEntity) {
        val context = appContext ?: return
        ReminderScheduler.scheduleMedication(context, medication)
    }

    private fun cancelMedicationReminder(medication: MedicationEntity) {
        val context = appContext ?: return
        ReminderScheduler.cancelMedication(context, medication)
    }

    fun playReminderVoice(itemName: String) {
        val safeItemName = itemName.trim().ifBlank {
            appString(R.string.medication_default_reminder_item)
        }
        val reminderText = appString(R.string.medication_reminder_voice_text, safeItemName)

        viewModelScope.launch {
            _statusMessage.value = reminderText
            try {
                speakWithAi(reminderText)
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to play reminder voice.", error)
                _toastMessages.tryEmit(appString(R.string.medication_voice_play_failed))
            }
        }
    }

    fun showDueReminder(
        medication: MedicationEntity,
        matchedTime: String,
    ) {
        if (medication.isTaken) {
            dismissReminderInternal()
            return
        }

        val safeMatchedTime = matchedTime.ifBlank {
            medication.reminderTimes().firstOrNull().orEmpty()
        }

        viewModelScope.launch {
            val todayEpochDay = currentDayMillisRange().epochDay
            val takenTimes = withContext(Dispatchers.IO) {
                appDao.getTakenTimesForDay(
                    scheduleId = medication.scheduleId,
                    takenDateEpochDay = todayEpochDay,
                )
            }.toSet()
            if (safeMatchedTime in takenTimes || "" in takenTimes) {
                dismissReminderInternal()
                return@launch
            }

            _currentReminder.value = MedicationReminderState(
                medication = medication,
                matchedTime = safeMatchedTime,
            )
            _statusMessage.value = appString(R.string.medication_reminder_due, medication.name)
        }
    }

    fun dismissCurrentReminder() {
        dismissReminderInternal()
    }

    fun markReminderTaken() {
        val reminderState = _currentReminder.value ?: return
        val reminder = reminderState.medication
        if (reminder.isTaken) {
            return
        }

        val updatedReminder = reminder.copy(
            stockCount = (reminder.stockCount - 1).coerceAtLeast(0),
        )
        viewModelScope.launch {
            try {
                val reminderForScheduling = withContext(Dispatchers.IO) {
                    cancelMedicationReminder(reminder)
                    appDao.updateMedication(updatedReminder)
                    if (reminder.scheduleId > 0L) {
                        val todayEpochDay = currentDayMillisRange().epochDay
                        appDao.markMedicationTakenForTime(
                            scheduleId = reminder.scheduleId,
                            takenDateEpochDay = todayEpochDay,
                            takenTime = reminderState.matchedTime,
                            takenAtMillis = System.currentTimeMillis(),
                        )
                        val takenTimes = appDao.getTakenTimesForDay(
                            scheduleId = reminder.scheduleId,
                            takenDateEpochDay = todayEpochDay,
                        ).toSet()
                        val allReminderTimesTaken = reminder.reminderTimes()
                            .all { reminderTime -> reminderTime in takenTimes || "" in takenTimes }
                        if (allReminderTimesTaken) {
                            appDao.setMedicationTakenForDay(
                                scheduleId = reminder.scheduleId,
                                takenDateEpochDay = todayEpochDay,
                                isTaken = true,
                            )
                        }
                        updatedReminder.copy(isTaken = allReminderTimesTaken)
                    } else {
                        updatedReminder
                    }
                }
                scheduleMedicationReminder(reminderForScheduling)
                refreshMedicationWidget()
                _statusMessage.value = appString(R.string.medication_taken_recorded, reminder.name)
                if (updatedReminder.stockCount <= 0) {
                    _toastMessages.tryEmit(appString(R.string.medication_stock_low))
                }
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to update medication taken status", error)
                _statusMessage.value = appString(R.string.medication_taken_update_failed)
            }
        }
    }

    fun markNextTimeSlotTaken(medication: MedicationEntity) {
        if (medication.isTaken || !medication.reminderEnabled) {
            return
        }

        viewModelScope.launch {
            try {
                val todayEpochDay = currentDayMillisRange().epochDay
                val scheduleId = medication.scheduleId
                if (scheduleId <= 0L) {
                    _statusMessage.value = appString(R.string.medication_taken_update_failed)
                    return@launch
                }

                val marked = withContext(Dispatchers.IO) {
                    val takenTimes = appDao.getTakenTimesForDay(
                        scheduleId = scheduleId,
                        takenDateEpochDay = todayEpochDay,
                    ).toSet()

                    val allTimes = medication.reminderTimes()
                    val nextUntakenTime = allTimes.firstOrNull { time ->
                        time !in takenTimes && "" !in takenTimes
                    } ?: return@withContext false

                    appDao.markMedicationTakenForTime(
                        scheduleId = scheduleId,
                        takenDateEpochDay = todayEpochDay,
                        takenTime = nextUntakenTime,
                        takenAtMillis = System.currentTimeMillis(),
                    )
                }

                if (marked) {
                    val allTaken = withContext(Dispatchers.IO) {
                        val takenTimes = appDao.getTakenTimesForDay(
                            scheduleId = scheduleId,
                            takenDateEpochDay = todayEpochDay,
                        ).toSet()
                        val allReminderTimesTaken = medication.reminderTimes()
                            .all { time -> time in takenTimes || "" in takenTimes }
                        if (allReminderTimesTaken) {
                            appDao.setMedicationTakenForDay(
                                scheduleId = scheduleId,
                                takenDateEpochDay = todayEpochDay,
                                isTaken = true,
                            )
                        }
                        allReminderTimesTaken
                    }

                    val updatedMedication = medication.copy(
                        stockCount = (medication.stockCount - 1).coerceAtLeast(0),
                        isTaken = allTaken,
                    )
                    withContext(Dispatchers.IO) {
                        appDao.updateMedicationRecord(updatedMedication.toRecordEntity())
                    }
                    cancelMedicationReminder(medication)
                    scheduleMedicationReminder(updatedMedication)
                    refreshMedicationWidget()
                    _statusMessage.value = appString(R.string.medication_taken_recorded, medication.name)
                    if (updatedMedication.stockCount <= 0) {
                        _toastMessages.tryEmit(appString(R.string.medication_stock_low))
                    }
                }
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to mark next time slot taken", error)
                _statusMessage.value = appString(R.string.medication_taken_update_failed)
            }
        }
    }

    fun remindLater() {
        val reminder = _currentReminder.value ?: return
        val scheduled = scheduleRemindLaterAlarm(reminder)
        _statusMessage.value = if (scheduled) {
            appString(R.string.medication_remind_later_scheduled, reminder.medication.name)
        } else {
            appString(R.string.medication_remind_later_failed)
        }
        dismissReminderInternal()
    }

    fun updateMedication(updatedMedication: MedicationEntity) {
        viewModelScope.launch {
            try {
                val savedMedication = withContext(Dispatchers.IO) {
                    appDao.getMedicationById(updatedMedication.id)?.let { existing ->
                        cancelMedicationReminder(existing)
                    }
                    appDao.updateMedication(updatedMedication)
                    appDao.getMedicationById(updatedMedication.id)
                }
                savedMedication?.let { scheduleMedicationReminder(it) }
                refreshMedicationWidget()
                if (_currentReminder.value?.medication?.id == updatedMedication.id) {
                    _currentReminder.value = _currentReminder.value?.copy(medication = updatedMedication)
                }
                _statusMessage.value = appString(R.string.medication_update_saved, updatedMedication.name)
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to update medication", error)
                _statusMessage.value = appString(R.string.medication_save_update_failed)
            }
        }
    }

    fun deleteMedication(id: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appDao.getMedicationById(id)?.let { medication ->
                        cancelMedicationReminder(medication)
                    }
                    appDao.deleteMedication(id)
                }
                refreshMedicationWidget()
                if (_currentReminder.value?.medication?.id == id) {
                    dismissReminderInternal()
                }
                _statusMessage.value = appString(R.string.medication_deleted_status)
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to delete medication", error)
                _statusMessage.value = appString(R.string.medication_delete_failed_status)
            }
        }
    }

    fun triggerReminderTest() {
        viewModelScope.launch {
            val todayEpochDay = currentDayMillisRange().epochDay
            val medications = loadTodayMedications().filter { it.reminderEnabled }

            var foundMedication: MedicationEntity? = null
            var foundTime: String? = null

            for (med in medications) {
                val scheduleId = med.scheduleId
                if (scheduleId <= 0L) continue

                val takenTimes = withContext(Dispatchers.IO) {
                    appDao.getTakenTimesForDay(scheduleId, todayEpochDay)
                }.toSet()

                if ("" in takenTimes) continue

                val untakenTime = med.reminderTimes().firstOrNull { it !in takenTimes }
                if (untakenTime != null) {
                    foundMedication = med
                    foundTime = untakenTime
                    break
                }
            }

            if (foundMedication != null && foundTime != null) {
                _currentReminder.value = MedicationReminderState(
                    medication = foundMedication,
                    matchedTime = foundTime,
                    isDemo = true,
                )
                _statusMessage.value = appString(R.string.medication_reminder_due, foundMedication.name)
                playDemoReminderVoice(foundMedication, foundTime)
            } else {
                _statusMessage.value = appString(R.string.medication_demo_all_done)
                _toastMessages.tryEmit(appString(R.string.medication_demo_all_done))
                playDemoAllDoneVoice()
            }
        }
    }

    fun markDemoReminderTaken() {
        val reminderState = _currentReminder.value ?: return
        if (!reminderState.isDemo) return
        val reminder = reminderState.medication

        viewModelScope.launch {
            try {
                val todayEpochDay = currentDayMillisRange().epochDay
                val scheduleId = reminder.scheduleId
                if (scheduleId > 0L) {
                    withContext(Dispatchers.IO) {
                        appDao.markMedicationTakenForTime(
                            scheduleId = scheduleId,
                            takenDateEpochDay = todayEpochDay,
                            takenTime = reminderState.matchedTime,
                            takenAtMillis = System.currentTimeMillis(),
                        )
                        val takenTimes = appDao.getTakenTimesForDay(
                            scheduleId = scheduleId,
                            takenDateEpochDay = todayEpochDay,
                        ).toSet()
                        val allReminderTimesTaken = reminder.reminderTimes()
                            .all { time -> time in takenTimes || "" in takenTimes }
                        if (allReminderTimesTaken) {
                            appDao.setMedicationTakenForDay(
                                scheduleId = scheduleId,
                                takenDateEpochDay = todayEpochDay,
                                isTaken = true,
                            )
                        }
                        val updatedStock = (reminder.stockCount - 1).coerceAtLeast(0)
                        appDao.updateMedicationRecord(
                            reminder.toRecordEntity().copy(stockCount = updatedStock),
                        )
                    }
                    refreshMedicationWidget()
                    _statusMessage.value = appString(R.string.medication_taken_recorded, reminder.name)
                }
            } catch (error: Exception) {
                DebugLogger.e(TAG, "Failed to mark demo reminder taken", error)
            } finally {
                dismissReminderInternal()
            }
        }
    }

    fun dismissDemoReminder() {
        dismissReminderInternal()
    }

    private suspend fun playDemoReminderVoice(medication: MedicationEntity, time: String) {
        val context = appContext ?: return
        val userName = com.heartguard.utils.SettingsManager.getUserName(context)
        val formattedTime = formatTimeForVoice(time)
        val text = if (userName.isNotBlank()) {
            appString(
                R.string.medication_demo_voice_text,
                userName,
                medication.name,
                medication.dosage,
                formattedTime,
            )
        } else {
            appString(
                R.string.medication_demo_voice_text_no_name,
                medication.name,
                medication.dosage,
                formattedTime,
            )
        }
        speakWithAi(text)
    }

    private suspend fun playDemoAllDoneVoice() {
        val text = appString(R.string.medication_demo_all_done_tts)
        speakWithAi(text)
    }

    private fun formatTimeForVoice(time: String): String {
        val parts = time.split(":")
        if (parts.size != 2) return time
        val hour = parts[0].toIntOrNull() ?: return time
        val minute = parts[1].toIntOrNull() ?: return time
        return if (minute == 0) "${hour}点" else "${hour}点${minute}分"
    }

    private suspend fun loadTodayMedications(): List<MedicationEntity> {
        val range = refreshTodayRangeIfNeeded()
        return withContext(Dispatchers.IO) {
            appDao.getTodayMedications(
                startOfDay = range.startOfDay,
                endOfDay = range.endOfDay,
                targetEpochDay = range.epochDay,
            )
        }
    }

    private fun refreshTodayRangeIfNeeded(): DayMillisRange {
        val latestRange = currentDayMillisRange()
        if (latestRange != todayRange.value) {
            todayRange.value = latestRange
        }
        return latestRange
    }

    private suspend fun emitReminderEvent(
        medication: MedicationEntity,
        matchedTime: String,
    ) {
        _currentReminder.value = MedicationReminderState(
            medication = medication,
            matchedTime = matchedTime,
        )
        _statusMessage.value = appString(R.string.medication_reminder_due, medication.name)
        _reminderEvent.emit(
            ReminderLaunchEvent(
                itemName = medication.name,
                reminderId = medication.id,
                matchedTime = matchedTime,
            )
        )
    }

    private fun dismissReminderInternal() {
        audioEngine.stopPlaying()
        _currentReminder.value = null
    }

    private fun scheduleRemindLaterAlarm(reminder: MedicationReminderState): Boolean {
        val context = appContext ?: return false
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        val triggerAtMillis = System.currentTimeMillis() + REMIND_LATER_DELAY_MILLIS
        val medication = reminder.medication
        val requestCode = remindLaterRequestCode(medication, reminder.matchedTime)
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMIND_LATER
            putExtra(ReminderAlarmReceiver.EXTRA_MEDICATION_ID, medication.id)
            putExtra(ReminderAlarmReceiver.EXTRA_MEDICATION_NAME, medication.name)
            putExtra(ReminderAlarmReceiver.EXTRA_MEDICATION_DOSAGE, medication.dosage)
            putExtra(ReminderAlarmReceiver.EXTRA_MEDICATION_TIME, reminder.matchedTime)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
            true
        } catch (error: SecurityException) {
            DebugLogger.w(TAG, "Exact alarm permission unavailable, falling back to inexact alarm", error)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            true
        } catch (error: Exception) {
            DebugLogger.e(TAG, "Failed to schedule reminder alarm", error)
            false
        }
    }

    private fun remindLaterRequestCode(
        medication: MedicationEntity,
        matchedTime: String,
    ): Int {
        var result = 17
        result = 31 * result + ReminderAlarmReceiver.ACTION_REMIND_LATER.hashCode()
        result = 31 * result + if (medication.id != 0L) {
            medication.id.hashCode()
        } else {
            medication.name.hashCode()
        }
        result = 31 * result + matchedTime.hashCode()
        return result
    }

    private suspend fun speakWithAi(text: String) {
        val repository = aiGateway ?: return
        val audioPath = repository.textToSpeech(text).trim()
        if (isPlayableLocalAudioPath(audioPath)) {
            audioEngine.playAudio(audioPath) {
                // No UI state is tied to medication reminder playback completion.
            }
        }
    }

    private fun isPlayableLocalAudioPath(audioPath: String): Boolean {
        val failedKeyword = appString(R.string.common_error_failed_keyword)
        val unavailableKeyword = appString(R.string.common_error_unavailable_keyword)
        return audioPath.isNotBlank() &&
            (failedKeyword.isBlank() || !audioPath.contains(failedKeyword)) &&
            (unavailableKeyword.isBlank() || !audioPath.contains(unavailableKeyword)) &&
            File(audioPath).exists()
    }

    private fun parseMedicationDrafts(rawResponse: String): List<MedicationImportDraft> {
        return extractMedicationObjects(rawResponse).mapNotNull { item ->
            val name = item.firstString("name", "drugName", "medicineName", "medicationName").trim()
            val dosage = item.firstString("dosage", "dose", "amount").trim()
            val perDose = item.firstString("perDose", "singleDose", "eachDose", "eachAmount").trim()
            val timesRaw = item.firstString("timesPerDay", "timesOfDay", "time", "times").trim()
            val repeatRule = item.firstString("repeatRule", "repeat", "frequency", "repeatType").trim()
            val stockCount = item.firstInt("stockCount", "stock", "count")
            val normalizedTimes = if (timesRaw.isBlank()) {
                ""
            } else {
                normalizeMedicationTimes(timesRaw)
            }

            if (
                name.isBlank() &&
                dosage.isBlank() &&
                perDose.isBlank() &&
                timesRaw.isBlank() &&
                repeatRule.isBlank()
            ) {
                null
            } else {
                MedicationImportDraft(
                    name = name,
                    dosage = dosage,
                    perDose = perDose.ifBlank { dosage },
                    timesOfDay = normalizedTimes,
                    repeatRule = repeatRule,
                    stockCount = stockCount?.coerceAtLeast(0)?.toString().orEmpty(),
                )
            }
        }
    }

    private fun extractMedicationObjects(rawResponse: String): List<JsonObject> {
        val cleanJson = cleanJsonResponse(rawResponse)
        val candidates = buildList {
            add(cleanJson)
            cleanJson.substringBetween("[", "]")?.let { add(it) }
            cleanJson.substringBetween("{", "}")?.let { add(it) }
        }.distinct()

        candidates.forEach { candidate ->
            val element = runCatching {
                medicationJson.parseToJsonElement(candidate)
            }.getOrNull() ?: return@forEach

            when (element) {
                is JsonArray -> {
                    val objects = element.mapNotNull { it as? JsonObject }
                    if (objects.isNotEmpty()) {
                        return objects
                    }
                }
                is JsonObject -> {
                    if (element.keys.any { key ->
                            key in setOf("name", "drugName", "medicineName", "medicationName")
                        }
                    ) {
                        return listOf(element)
                    }
                    val nestedObjects = element.values.firstNotNullOfOrNull { value ->
                        (value as? JsonArray)?.mapNotNull { it as? JsonObject }?.takeIf { it.isNotEmpty() }
                    }
                    if (!nestedObjects.isNullOrEmpty()) {
                        return nestedObjects
                    }
                }
                else -> Unit
            }
        }

        return emptyList()
    }

    private fun normalizeMedicationTimes(rawTimes: String): String {
        val explicitTimes = timePattern.findAll(rawTimes)
            .map { match ->
                val (hour, minute) = match.destructured
                "%02d:%02d".format(hour.toInt().coerceIn(0, 23), minute.toInt().coerceIn(0, 59))
            }
            .toList()
            .distinct()
        if (explicitTimes.isNotEmpty()) {
            return explicitTimes.joinToString(",")
        }

        val timesPerDay = rawTimes.toIntOrNull()
            ?: when {
                rawTimes.contains("四") || rawTimes.contains("4") -> 4
                rawTimes.contains("三") || rawTimes.contains("3") -> 3
                rawTimes.contains("两") || rawTimes.contains("二") || rawTimes.contains("2") -> 2
                else -> 1
            }

        return when (timesPerDay.coerceIn(1, 4)) {
            1 -> "08:00"
            2 -> "08:00,20:00"
            3 -> "08:00,12:00,18:00"
            else -> "08:00,12:00,18:00,21:00"
        }
    }

    private fun buildMedicationBatchExtractionPrompt(): String {
        return appString(R.string.medication_parse_prompt)
    }

    private fun cleanJsonResponse(rawResponse: String): String {
        return rawResponse
            .replace("```json", "")
            .replace("```", "")
            .trim()
    }

    private fun looksLikeAiFailure(text: String): Boolean {
        val failedKeyword = appString(R.string.common_error_failed_keyword)
        val unavailableKeyword = appString(R.string.common_error_unavailable_keyword)
        return text == appString(R.string.vivo_ai_missing_credentials)
            || text == appString(R.string.vivo_ai_chat_model_no_permission)
            || text == appString(R.string.vivo_ai_chat_failed)
            || text == appString(R.string.vivo_ai_chat_network_failed)
            || text == appString(R.string.vivo_ai_chat_parse_failed)
            || (unavailableKeyword.isNotBlank() && text.contains(unavailableKeyword))
            || text.contains("请稍后再试")
            || text.contains("请求失败")
            || (failedKeyword.isNotBlank() && text.contains(failedKeyword))
    }

    private fun isVoiceRecognitionError(message: String): Boolean {
        val errorKeywords = listOf(
            R.string.common_error_failed_keyword,
            R.string.common_error_unavailable_keyword,
            R.string.vivo_ai_missing_credentials,
            R.string.vivo_ai_asr_requires_pcm_wav,
            R.string.vivo_ai_asr_failed,
        ).map(::appString)
            .filter { it.isNotBlank() }
        return errorKeywords.any { keyword ->
            message == keyword || message.contains(keyword)
        }
    }

    private fun MedicationImportDraft.toMedicationEntity(sourceName: String): MedicationEntity? {
        val safeName = name.trim()
        val safeDosage = perDose.trim().ifBlank { dosage.trim() }
        val safeTimes = timesOfDay
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(",")

        if (safeName.isBlank() || safeDosage.isBlank() || safeTimes.isBlank()) {
            return null
        }

        val repeatType = repeatTypeForImportRule(repeatRule)
        return MedicationEntity(
            name = safeName,
            dosage = safeDosage,
            timesOfDay = safeTimes,
            note = appString(R.string.medication_ai_import_note, sourceName),
            isTaken = false,
            ringtoneUri = null,
            stockCount = stockCount.toIntOrNull()?.coerceAtLeast(0) ?: DEFAULT_STOCK_COUNT,
            repeatType = repeatType,
            repeatIntervalDays = normalizeMedicationRepeatIntervalDays(
                repeatType = repeatType,
                intervalDays = MEDICATION_CUSTOM_REPEAT_DEFAULT_INTERVAL_DAYS,
            ),
        )
    }

    private fun repeatTypeForImportRule(rawRule: String): String {
        val rule = rawRule.trim()
        return when {
            rule.contains(appString(R.string.medication_repeat_every_other_day)) ||
                rule.contains("隔天") ||
                rule.contains("every other", ignoreCase = true) -> MEDICATION_REPEAT_EVERY_OTHER_DAY
            rule.contains(appString(R.string.medication_repeat_once)) ||
                rule.contains("仅") ||
                rule.contains("一次") ||
                rule.contains("once", ignoreCase = true) -> MEDICATION_REPEAT_ONCE
            rule.contains(appString(R.string.medication_repeat_custom)) ||
                rule.contains("每") && rule.contains("天") && !rule.contains("每天") -> MEDICATION_REPEAT_CUSTOM
            else -> MEDICATION_REPEAT_DAILY
        }
    }

    private fun formatManualReminderTimes(): String {
        val selectedPeriods = _manualReminderPeriods.value
        return MANUAL_REMINDER_TIMES
            .filter { time -> time in selectedPeriods }
            .joinToString(",")
    }

    private fun formatDosageAmount(amount: Float): String {
        return if (amount % 1f == 0f) {
            amount.toInt().toString()
        } else {
            "%.1f".format(amount)
        }
    }

    private fun parseDosageAmount(dosage: String): Float {
        return dosageNumberPattern.find(dosage)
            ?.value
            ?.toFloatOrNull()
            ?.coerceIn(MIN_DOSAGE_AMOUNT, MAX_DOSAGE_AMOUNT)
            ?: 1f
    }

    private fun parseFirstReminderTime(timesOfDay: String): Pair<Int, Int> {
        val match = timePattern.find(timesOfDay)
        if (match != null) {
            val (hour, minute) = match.destructured
            return hour.toInt().coerceIn(0, 23) to minute.toInt().coerceIn(0, 59)
        }
        return 8 to 0
    }

    private fun parseManualReminderPeriods(timesOfDay: String): Set<String> {
        val selectedTimes = timesOfDay
            .split(",")
            .map { it.trim() }
            .filter { it in MANUAL_REMINDER_TIMES }
            .toSet()
        return selectedTimes.ifEmpty { setOf(MANUAL_REMINDER_MORNING_TIME) }
    }

    private fun parseRepeatOption(note: String): String {
        return MANUAL_REPEAT_OPTIONS.firstOrNull { option ->
            note.contains("重复：$option")
        } ?: "仅当天"
    }

    private fun repeatOptionForMedication(medication: MedicationEntity): String {
        return when (medication.repeatType) {
            MEDICATION_REPEAT_ONCE -> appString(R.string.medication_repeat_once).ifBlank { "仅当天" }
            MEDICATION_REPEAT_DAILY -> appString(R.string.medication_repeat_daily).ifBlank { "每天" }
            MEDICATION_REPEAT_EVERY_OTHER_DAY -> appString(R.string.medication_repeat_every_other_day)
                .ifBlank { "隔天" }
            MEDICATION_REPEAT_CUSTOM -> appString(R.string.medication_repeat_custom).ifBlank { "自定义" }
            else -> parseRepeatOption(medication.note)
        }
    }

    private fun repeatTypeForManualOption(option: String): String {
        return when (option) {
            appString(R.string.medication_repeat_once).ifBlank { "仅当天" },
            "仅当天" -> MEDICATION_REPEAT_ONCE

            appString(R.string.medication_repeat_daily).ifBlank { "每天" },
            "每天" -> MEDICATION_REPEAT_DAILY

            appString(R.string.medication_repeat_every_other_day).ifBlank { "隔天" },
            "隔天" -> MEDICATION_REPEAT_EVERY_OTHER_DAY

            appString(R.string.medication_repeat_custom).ifBlank { "自定义" },
            "自定义" -> MEDICATION_REPEAT_CUSTOM

            else -> MEDICATION_REPEAT_ONCE
        }
    }

    private fun repeatIntervalDaysForType(
        repeatType: String,
        customIntervalDays: Int,
    ): Int {
        return normalizeMedicationRepeatIntervalDays(
            repeatType = repeatType,
            intervalDays = customIntervalDays,
        )
    }

    private fun parseReminderStyle(note: String): String {
        return MANUAL_STYLE_OPTIONS.firstOrNull { style ->
            note.contains("样式：$style")
        } ?: "胶囊"
    }

    private fun parseFreeNote(note: String): String {
        return note
            .split("；")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot {
                it.startsWith("重复：") ||
                    it.startsWith("间隔：") ||
                    it.startsWith("样式：")
            }
            .joinToString("；")
    }

    private companion object {
        const val TAG = "MedicationViewModel"
        const val REMIND_LATER_DELAY_MILLIS = 15 * 60 * 1000L
        const val DEFAULT_STOCK_COUNT = 30
        const val DOSAGE_STEP = 0.5f
        const val MIN_DOSAGE_AMOUNT = 0.5f
        const val MAX_DOSAGE_AMOUNT = 20f
        const val MANUAL_REMINDER_MORNING_TIME = "08:00"
        const val MANUAL_REMINDER_NOON_TIME = "12:00"
        const val MANUAL_REMINDER_EVENING_TIME = "18:00"
        val MANUAL_REMINDER_TIMES = listOf(
            MANUAL_REMINDER_MORNING_TIME,
            MANUAL_REMINDER_NOON_TIME,
            MANUAL_REMINDER_EVENING_TIME,
        )
        val MANUAL_REPEAT_OPTIONS = listOf("仅当天", "每天", "隔天", "自定义")
        val MANUAL_STYLE_OPTIONS = listOf("胶囊", "圆片", "水杯")
        val medicationJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val timePattern = Regex("""(\d{1,2}):(\d{2})""")
        val dosageNumberPattern = Regex("""\d+(?:\.\d+)?""")
    }

    private fun appString(
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

private fun JsonObject.firstString(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key ->
        runCatching {
            this[key]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }.orEmpty()
}

private fun JsonObject.firstInt(vararg keys: String): Int? {
    return keys.firstNotNullOfOrNull { key ->
        runCatching {
            val primitive = this[key]?.jsonPrimitive ?: return@runCatching null
            primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
        }.getOrNull()
    }
}

private fun Int.floorMod(modulus: Int): Int {
    return ((this % modulus) + modulus) % modulus
}

private fun String.substringBetween(
    start: String,
    end: String,
): String? {
    val startIndex = indexOf(start)
    val endIndex = lastIndexOf(end)
    if (startIndex < 0 || endIndex <= startIndex) {
        return null
    }
    return substring(startIndex, endIndex + end.length)
}

private data class DayMillisRange(
    val startOfDay: Long,
    val endOfDay: Long,
    val epochDay: Long,
)

private fun currentDayMillisRange(): DayMillisRange {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val startOfDay = today
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()
    val endOfDay = today
        .plusDays(1)
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()
    return DayMillisRange(
        startOfDay = startOfDay,
        endOfDay = endOfDay,
        epochDay = today.toEpochDay(),
    )
}
