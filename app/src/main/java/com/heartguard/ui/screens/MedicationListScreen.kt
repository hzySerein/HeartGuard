package com.heartguard.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heartguard.R
import com.heartguard.data.local.MEDICATION_REPEAT_CUSTOM
import com.heartguard.data.local.MEDICATION_REPEAT_DAILY
import com.heartguard.data.local.MEDICATION_REPEAT_EVERY_OTHER_DAY
import com.heartguard.data.local.MEDICATION_REPEAT_ONCE
import com.heartguard.data.local.MedicationEntity
import com.heartguard.data.local.MedicationTodayProgress
import com.heartguard.data.local.normalizeMedicationRepeatIntervalDays
import com.heartguard.reminder.ReminderLaunchEvent
import com.heartguard.ui.theme.BackgroundWarm
import com.heartguard.ui.theme.ConfirmGreen
import com.heartguard.ui.theme.SurfaceWarm
import com.heartguard.ui.theme.TextPrimary
import com.heartguard.ui.theme.TextSecondary
import com.heartguard.viewmodel.MedicationImportConfirmationState
import com.heartguard.viewmodel.MedicationImportDraft
import com.heartguard.viewmodel.MedicationImportSource
import com.heartguard.viewmodel.MedicationViewModel
import java.time.LocalTime

private val ReminderIconBackground = Color(0xFFFFE8D6)
private val ReminderSelectedBackground = Color(0xFFE7F1E7)
private val ReminderUnselectedBackground = Color(0xFFEDEDED)
private val ReminderGreen = Color(0xFF5E8F61)
private val ReminderOverviewBackground = Color(0xFFFFF5EC)
private val ReminderStatusOnBackground = Color(0xFFEAF4EA)
private val ReminderStatusOffBackground = Color(0xFFF1F1F1)

private enum class NotificationPermissionAction {
    OpenManualForm,
    LaunchImageImport,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    medicationViewModel: MedicationViewModel,
    reminderLaunchEvent: ReminderLaunchEvent? = null,
    onReminderEventHandled: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel = medicationViewModel
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val medicationProgressList by viewModel.medicationProgressList.collectAsStateWithLifecycle()
    val progressMap = remember(medicationProgressList) {
        medicationProgressList.associateBy { it.medicationId }
    }
    val currentReminder by viewModel.currentReminder.collectAsStateWithLifecycle()
    val isImageImporting by viewModel.isImageImporting.collectAsStateWithLifecycle()
    val isVoiceImportRecording by viewModel.isVoiceImportRecording.collectAsStateWithLifecycle()
    val pendingImportConfirmation by viewModel.pendingImportConfirmation.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var pendingReminderEvent by remember { mutableStateOf<ReminderLaunchEvent?>(null) }
    var pendingNotificationPermissionAction by remember {
        mutableStateOf<NotificationPermissionAction?>(null)
    }
    val imageImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(viewModel::importMedicationFromImage)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        when (pendingNotificationPermissionAction) {
            NotificationPermissionAction.OpenManualForm -> {
                viewModel.resetManualReminderForm()
                showAddSheet = true
            }

            NotificationPermissionAction.LaunchImageImport -> {
                imageImportLauncher.launch("image/*")
            }

            null -> Unit
        }
        pendingNotificationPermissionAction = null
    }
    val voicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startVoiceImportRecording()
        } else {
            Toast.makeText(context, R.string.runtime_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    fun needsNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    }

    fun requestNotificationPermissionThen(action: NotificationPermissionAction) {
        if (needsNotificationPermission()) {
            pendingNotificationPermissionAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        when (action) {
            NotificationPermissionAction.OpenManualForm -> {
                viewModel.resetManualReminderForm()
                showAddSheet = true
            }

            NotificationPermissionAction.LaunchImageImport -> {
                imageImportLauncher.launch("image/*")
            }
        }
    }

    fun startVoiceImportWithPermission() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startVoiceImportRecording()
        } else {
            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun retryImport(state: MedicationImportConfirmationState) {
        viewModel.dismissImportConfirmation()
        when (state.source) {
            MedicationImportSource.Image -> {
                requestNotificationPermissionThen(NotificationPermissionAction.LaunchImageImport)
            }

            MedicationImportSource.Voice -> {
                startVoiceImportWithPermission()
            }

            MedicationImportSource.Text -> Unit
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.toastMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(reminderLaunchEvent?.eventId) {
        val event = reminderLaunchEvent ?: return@LaunchedEffect
        pendingReminderEvent = event
        onReminderEventHandled(event.eventId)
    }

    LaunchedEffect(pendingReminderEvent?.eventId, medications) {
        val event = pendingReminderEvent ?: return@LaunchedEffect
        val medication = medications.firstOrNull { medication ->
            event.reminderId > 0L && medication.id == event.reminderId
        } ?: medications.firstOrNull { medication ->
            event.itemName.isNotBlank() && medication.name == event.itemName
        }

        if (medication != null) {
            if (!medication.reminderEnabled || medication.isTaken) {
                pendingReminderEvent = null
                return@LaunchedEffect
            }

            val matchedTime = event.matchedTime.ifBlank {
                medication.timesOfDay
                    .split(",")
                    .firstOrNull()
                    .orEmpty()
            }
            viewModel.showDueReminder(medication, matchedTime)
            viewModel.playReminderVoice(medication.name)
            pendingReminderEvent = null
        } else if (event.reminderId <= 0L) {
            pendingReminderEvent = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.medication_list_title),
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = { viewModel.triggerReminderTest() },
                ) {
                    Text(
                        text = stringResource(R.string.medication_demo_reminder),
                        color = ConfirmGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.medication_count, medications.size),
                color = TextSecondary,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(18.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                item {
                    TodayMedicationOverviewCard(
                        progressList = medicationProgressList,
                    )
                }

                if (medications.isEmpty()) {
                    item {
                        EmptyReminderCard()
                    }
                } else {
                    items(medications, key = { it.id }) { medication ->
                        ReminderCard(
                            medication = medication,
                            progress = progressMap[medication.id],
                            onClick = {
                                viewModel.startEditingManualReminder(medication)
                                showAddSheet = true
                            },
                            onEnabledChange = { checked ->
                                if (checked && needsNotificationPermission()) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                viewModel.updateMedication(
                                    medication.copy(reminderEnabled = checked),
                                )
                            },
                            onMarkTaken = {
                                viewModel.markNextTimeSlotTaken(medication)
                            },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(BackgroundWarm)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    requestNotificationPermissionThen(NotificationPermissionAction.OpenManualForm)
                },
                enabled = !isImageImporting && !isVoiceImportRecording,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReminderUnselectedBackground,
                    contentColor = TextPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.medication_manual_import),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            Button(
                onClick = {
                    requestNotificationPermissionThen(NotificationPermissionAction.LaunchImageImport)
                },
                enabled = !isImageImporting && !isVoiceImportRecording,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ConfirmGreen,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.medication_photo_import),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            Button(
                onClick = {
                    if (isVoiceImportRecording) {
                        viewModel.stopVoiceImportRecordingAndRecognize()
                    } else {
                        startVoiceImportWithPermission()
                    }
                },
                enabled = !isImageImporting,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVoiceImportRecording) ConfirmGreen else ReminderUnselectedBackground,
                    contentColor = if (isVoiceImportRecording) Color.White else TextPrimary,
                ),
            ) {
                Text(
                    text = if (isVoiceImportRecording) {
                        stringResource(R.string.medication_voice_stop)
                    } else {
                        stringResource(R.string.medication_voice_import)
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        if (showAddSheet) {
            AddReminderBottomSheet(
                viewModel = viewModel,
                onDismiss = {
                    showAddSheet = false
                },
            )
        }

        if (isImageImporting) {
            MedicationImportLoadingDialog(
                text = statusMessage.ifBlank { stringResource(R.string.medication_image_importing) },
            )
        }

        pendingImportConfirmation?.let { confirmationState ->
            MedicationImportConfirmSheet(
                state = confirmationState,
                onDismiss = viewModel::dismissImportConfirmation,
                onRetry = {
                    retryImport(confirmationState)
                },
                onConfirm = viewModel::confirmPendingImport,
                onDraftChange = viewModel::updateImportDraft,
            )
        }

        currentReminder?.let { reminder ->
            val isDemo = reminder.isDemo
            val progress = progressMap[reminder.medication.id]
            ReminderArrivedDialog(
                medication = reminder.medication,
                matchedTime = reminder.matchedTime,
                isDemo = isDemo,
                takenCount = progress?.takenCount ?: 0,
                totalCount = progress?.totalCount ?: reminder.medication.totalTimes,
                onTaken = {
                    if (isDemo) {
                        viewModel.markDemoReminderTaken()
                    } else {
                        viewModel.markReminderTaken()
                        viewModel.dismissCurrentReminder()
                    }
                },
                onDismiss = {
                    if (isDemo) {
                        viewModel.dismissDemoReminder()
                    } else {
                        viewModel.dismissCurrentReminder()
                    }
                },
                onRemindLater = {
                    if (isDemo) {
                        viewModel.dismissDemoReminder()
                    } else {
                        viewModel.remindLater()
                    }
                },
                onEdit = {
                    if (isDemo) {
                        viewModel.dismissDemoReminder()
                    } else {
                        viewModel.startEditingManualReminder(reminder.medication)
                        viewModel.dismissCurrentReminder()
                        showAddSheet = true
                    }
                },
            )
        }
    }
}

@Composable
private fun ReminderArrivedDialog(
    medication: MedicationEntity,
    matchedTime: String,
    isDemo: Boolean = false,
    takenCount: Int = 0,
    totalCount: Int = 1,
    onTaken: () -> Unit,
    onDismiss: () -> Unit,
    onRemindLater: () -> Unit,
    onEdit: () -> Unit,
) {
    val titleRes = if (isDemo) R.string.medication_demo_title else R.string.medication_time_due_title

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWarm,
        title = {
            Text(
                text = stringResource(titleRes),
                color = ConfirmGreen,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = medication.name,
                    color = TextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = listOf(medication.dosage, formatMedicationTimeLabel(matchedTime))
                        .filter { it.isNotBlank() }
                        .joinToString("  "),
                    color = TextSecondary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isDemo && totalCount > 1) {
                    Text(
                        text = stringResource(
                            R.string.medication_taken_progress,
                            takenCount,
                            totalCount,
                        ),
                        color = ReminderGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onTaken,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ConfirmGreen,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.medication_mark_taken),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = if (isDemo) {
            {
                TextButton(
                    onClick = onRemindLater,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.medication_demo_remind_later),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.medication_dismiss_reminder),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(
                        onClick = onRemindLater,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.medication_remind_later_action),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.medication_edit_reminder),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun MedicationImportLoadingDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = SurfaceWarm,
        title = {
            Text(
                text = stringResource(R.string.medication_importing_dialog),
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(
                    color = ConfirmGreen,
                )
                Text(
                    text = text,
                    color = TextSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationImportConfirmSheet(
    state: MedicationImportConfirmationState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onConfirm: () -> Unit,
    onDraftChange: (Int, MedicationImportDraft) -> Unit,
) {
    val canConfirm = state.drafts.isNotEmpty() && state.drafts.all(::isImportDraftReady)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundWarm,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.medication_ai_confirm_title),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.medication_ai_confirm_subtitle),
                color = TextSecondary,
                fontSize = 16.sp,
            )

            state.drafts.forEachIndexed { index, draft ->
                AiRecognizedMedicationCard(
                    title = stringResource(R.string.medication_ai_confirm_item_title, index + 1),
                    draft = draft,
                    onDraftChange = { updatedDraft ->
                        onDraftChange(index, updatedDraft)
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReminderUnselectedBackground,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.medication_ai_confirm_retry),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = onConfirm,
                    enabled = canConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConfirmGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.medication_ai_confirm_save),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AiRecognizedMedicationCard(
    title: String,
    draft: MedicationImportDraft,
    onDraftChange: (MedicationImportDraft) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWarm,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            ImportDraftField(
                label = stringResource(R.string.medication_ai_field_name),
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
            )
            ImportDraftField(
                label = stringResource(R.string.medication_ai_field_dosage),
                value = draft.dosage,
                onValueChange = { onDraftChange(draft.copy(dosage = it)) },
            )
            ImportDraftField(
                label = stringResource(R.string.medication_ai_field_per_dose),
                value = draft.perDose,
                onValueChange = { onDraftChange(draft.copy(perDose = it)) },
            )
            ImportDraftField(
                label = stringResource(R.string.medication_ai_field_times),
                value = draft.timesOfDay,
                onValueChange = { onDraftChange(draft.copy(timesOfDay = it)) },
            )
            ImportDraftField(
                label = stringResource(R.string.medication_ai_field_repeat),
                value = draft.repeatRule,
                onValueChange = { onDraftChange(draft.copy(repeatRule = it)) },
            )
            ImportDraftField(
                label = stringResource(R.string.medication_ai_field_stock),
                value = draft.stockCount,
                onValueChange = { rawValue ->
                    onDraftChange(draft.copy(stockCount = rawValue.filter { char -> char.isDigit() }))
                },
            )
        }
    }
}

@Composable
private fun ImportDraftField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = {
            Text(text = label)
        },
        placeholder = {
            Text(text = stringResource(R.string.medication_ai_field_missing))
        },
    )
}

private fun isImportDraftReady(draft: MedicationImportDraft): Boolean {
    return draft.name.isNotBlank() &&
        draft.timesOfDay.isNotBlank() &&
        draft.perDose.ifBlank { draft.dosage }.isNotBlank()
}

@Composable
private fun TodayMedicationOverviewCard(
    progressList: List<MedicationTodayProgress>,
) {
    val enabledProgress = progressList.filter { it.isEnabled }
    val totalSlots = enabledProgress.sumOf { it.totalCount }
    val completedSlots = enabledProgress.sumOf { it.takenCount }
    val pendingCount = (totalSlots - completedSlots).coerceAtLeast(0)
    val completedCount = completedSlots
    val nextReminder = nextReminderTime(progressList)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = ReminderOverviewBackground,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.medication_today_overview_title),
                    color = TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.medication_today_overview_subtitle),
                    color = TextSecondary,
                    fontSize = 15.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OverviewMetric(
                    label = stringResource(R.string.medication_today_pending),
                    value = stringResource(R.string.medication_overview_count, pendingCount),
                    modifier = Modifier.weight(1f),
                )
                OverviewMetric(
                    label = stringResource(R.string.medication_today_completed),
                    value = stringResource(R.string.medication_overview_count, completedCount),
                    modifier = Modifier.weight(1f),
                )
                OverviewMetric(
                    label = stringResource(R.string.medication_next_reminder),
                    value = nextReminder.ifBlank { stringResource(R.string.medication_next_none) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReminderCard(
    medication: MedicationEntity,
    progress: MedicationTodayProgress?,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMarkTaken: () -> Unit,
) {
    val enabled = medication.reminderEnabled
    val statusText = if (enabled) {
        stringResource(R.string.medication_reminder_on)
    } else {
        stringResource(R.string.medication_reminder_off)
    }
    val statusBackground = if (enabled) ReminderStatusOnBackground else ReminderStatusOffBackground
    val statusColor = if (enabled) ReminderGreen else TextSecondary
    val repeatLabel = repeatLabelForMedication(
        medication = medication,
        onceLabel = stringResource(R.string.medication_repeat_once),
        dailyLabel = stringResource(R.string.medication_repeat_daily),
        everyOtherDayLabel = stringResource(R.string.medication_repeat_every_other_day),
        customLabel = stringResource(R.string.medication_repeat_custom),
        customIntervalLabel = stringResource(
            R.string.medication_repeat_every_n_days,
            normalizeMedicationRepeatIntervalDays(
                repeatType = medication.repeatType,
                intervalDays = medication.repeatIntervalDays,
            ),
        ),
    )
    val allTaken = progress?.isAllTaken == true
    val takenCount = progress?.takenCount ?: 0
    val totalCount = progress?.totalCount ?: medication.totalTimes

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWarm,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(ReminderIconBackground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = styleIconForMedication(medication),
                        contentDescription = stringResource(R.string.medication_bottle),
                        tint = ConfirmGreen,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = medication.name,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = medication.dosage,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatMedicationTimes(medication.timesOfDay)} $repeatLabel",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ReminderGreen,
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusBackground,
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = statusColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (allTaken) {
                    Text(
                        text = stringResource(R.string.medication_taken_done),
                        color = ReminderGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (takenCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.medication_taken_progress,
                                    takenCount,
                                    totalCount,
                                ),
                                color = ReminderGreen,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Button(
                            onClick = onMarkTaken,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ConfirmGreen,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.medication_mark_taken),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderBottomSheet(
    viewModel: MedicationViewModel,
    onDismiss: () -> Unit,
) {
    val reminderName by viewModel.manualReminderName.collectAsStateWithLifecycle()
    val dosageAmount by viewModel.manualDosageAmount.collectAsStateWithLifecycle()
    val selectedReminderPeriods by viewModel.manualReminderPeriods.collectAsStateWithLifecycle()
    val repeatOption by viewModel.manualRepeatOption.collectAsStateWithLifecycle()
    val repeatIntervalDays by viewModel.manualRepeatIntervalDays.collectAsStateWithLifecycle()
    val selectedStyle by viewModel.manualReminderStyle.collectAsStateWithLifecycle()
    val note by viewModel.manualReminderNote.collectAsStateWithLifecycle()
    val editingMedicationId by viewModel.manualEditingMedicationId.collectAsStateWithLifecycle()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val quickTags = listOf(
        stringResource(R.string.quick_medication_hypertension),
        stringResource(R.string.quick_medication_cold),
        stringResource(R.string.quick_medication_calcium),
        stringResource(R.string.quick_medication_water),
    )
    val repeatOptions = listOf(
        stringResource(R.string.medication_repeat_once),
        stringResource(R.string.medication_repeat_daily),
        stringResource(R.string.medication_repeat_every_other_day),
        stringResource(R.string.medication_repeat_custom),
    )
    val customRepeatOption = stringResource(R.string.medication_repeat_custom)
    val reminderStyles = listOf(
        ReminderStyle(
            label = stringResource(R.string.medication_style_capsule),
            icon = Icons.Filled.LocalPharmacy,
        ),
        ReminderStyle(
            label = stringResource(R.string.medication_style_tablet),
            icon = Icons.Filled.RadioButtonChecked,
        ),
        ReminderStyle(
            label = stringResource(R.string.medication_style_water),
            icon = Icons.Filled.LocalDrink,
        ),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundWarm,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = if (editingMedicationId == null) {
                    stringResource(R.string.medication_add_new_reminder)
                } else {
                    stringResource(R.string.medication_edit_reminder)
                },
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = reminderName,
                        onValueChange = viewModel::setManualReminderName,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(text = stringResource(R.string.medication_item_name))
                        },
                        singleLine = true,
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(quickTags) { tag ->
                            LargeChoiceChip(
                                text = tag,
                                selected = reminderName == tag,
                                onClick = {
                                    viewModel.selectManualQuickTag(tag)
                                },
                            )
                        }
                    }
                }

                DosageStepper(
                    dosageAmount = dosageAmount,
                    onDecrease = viewModel::decreaseManualDosage,
                    onIncrease = viewModel::increaseManualDosage,
                )

                ReminderPeriodSelector(
                    selectedTimes = selectedReminderPeriods,
                    onToggleTime = viewModel::toggleManualReminderPeriod,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.medication_repeat_frequency),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeatOptions.forEach { option ->
                            SegmentedChoice(
                                text = option,
                                selected = repeatOption == option,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.setManualRepeatOption(option)
                                },
                            )
                        }
                    }
                    if (repeatOption == customRepeatOption) {
                        RepeatIntervalStepper(
                            intervalDays = repeatIntervalDays,
                            onDecrease = viewModel::decreaseManualRepeatIntervalDays,
                            onIncrease = viewModel::increaseManualRepeatIntervalDays,
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.medication_style_note),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        reminderStyles.forEach { style ->
                            ReminderStyleIcon(
                                style = style,
                                selected = selectedStyle == style.label,
                                onClick = {
                                    viewModel.setManualReminderStyle(style.label)
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = viewModel::setManualReminderNote,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(text = stringResource(R.string.medication_note_optional))
                        },
                        minLines = 2,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        if (editingMedicationId == null) {
                            viewModel.resetManualReminderForm()
                            onDismiss()
                        } else {
                            showDeleteConfirmDialog = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReminderUnselectedBackground,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Text(
                        text = if (editingMedicationId == null) {
                            stringResource(R.string.medication_cancel)
                        } else {
                            stringResource(R.string.medication_delete_reminder)
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(
                    onClick = {
                        if (viewModel.saveManualReminder()) {
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConfirmGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.medication_save_reminder),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
            },
            containerColor = SurfaceWarm,
            title = {
                Text(
                    text = stringResource(R.string.medication_delete_confirm_title),
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.medication_delete_confirm_body),
                    color = TextSecondary,
                    fontSize = 16.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        if (viewModel.deleteEditingManualReminder()) {
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConfirmGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(text = stringResource(R.string.medication_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.medication_think_again),
                        color = TextPrimary,
                    )
                }
            },
        )
    }
}

@Composable
private fun DosageStepper(
    dosageAmount: Float,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.medication_dosage_title),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepButton(text = "-", onClick = onDecrease)
            Text(
                text = stringResource(
                    R.string.medication_dosage_display,
                    formatDosageAmount(dosageAmount),
                ),
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            StepButton(text = "+", onClick = onIncrease)
        }
    }
}

@Composable
private fun ReminderPeriodSelector(
    selectedTimes: Set<String>,
    onToggleTime: (String) -> Unit,
) {
    val periods = listOf(
        ReminderPeriodOption(
            label = stringResource(R.string.medication_period_morning),
            time = "08:00",
        ),
        ReminderPeriodOption(
            label = stringResource(R.string.medication_period_noon),
            time = "12:00",
        ),
        ReminderPeriodOption(
            label = stringResource(R.string.medication_period_evening),
            time = "18:00",
        ),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.medication_period_title),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            periods.forEach { period ->
                MedicationPeriodChip(
                    period = period,
                    selected = period.time in selectedTimes,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onToggleTime(period.time)
                    },
                )
            }
        }
    }
}

@Composable
private fun MedicationPeriodChip(
    period: ReminderPeriodOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) ReminderSelectedBackground else ReminderUnselectedBackground,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = period.label,
                color = if (selected) ConfirmGreen else TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = period.time,
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReminderTimePicker(
    hour: Int,
    minute: Int,
    onHourDecrease: () -> Unit,
    onHourIncrease: () -> Unit,
    onMinuteDecrease: () -> Unit,
    onMinuteIncrease: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.medication_time_title),
            modifier = Modifier.fillMaxWidth(),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimeNumberStepper(
                value = "%02d".format(hour),
                onDecrease = onHourDecrease,
                onIncrease = onHourIncrease,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = ":",
                color = TextSecondary,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
            )
            TimeNumberStepper(
                value = "%02d".format(minute),
                onDecrease = onMinuteDecrease,
                onIncrease = onMinuteIncrease,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RepeatIntervalStepper(
    intervalDays: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepButton(text = "-", onClick = onDecrease)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.medication_repeat_interval_title),
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.medication_repeat_every_n_days, intervalDays),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        StepButton(text = "+", onClick = onIncrease)
    }
}

@Composable
private fun TimeNumberStepper(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StepButton(text = "+", onClick = onIncrease)
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
        )
        StepButton(text = "-", onClick = onDecrease)
    }
}

@Composable
private fun StepButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ReminderUnselectedBackground,
            contentColor = TextPrimary,
        ),
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LargeChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) ReminderSelectedBackground else ReminderUnselectedBackground,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (selected) ConfirmGreen else TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SegmentedChoice(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) ReminderSelectedBackground else ReminderUnselectedBackground,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (selected) ConfirmGreen else TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReminderStyleIcon(
    style: ReminderStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(56.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) ReminderSelectedBackground else ReminderUnselectedBackground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = style.icon,
                contentDescription = style.label,
                tint = if (selected) ConfirmGreen else TextSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun EmptyReminderCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWarm,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = stringResource(R.string.medication_empty),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
            color = TextSecondary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun styleIconForMedication(medication: MedicationEntity): ImageVector {
    return when {
        medication.note.contains("水杯") -> Icons.Filled.LocalDrink
        medication.note.contains("圆片") -> Icons.Filled.RadioButtonChecked
        else -> Icons.Filled.LocalPharmacy
    }
}

private fun repeatLabelForMedication(
    medication: MedicationEntity,
    onceLabel: String,
    dailyLabel: String,
    everyOtherDayLabel: String,
    customLabel: String,
    customIntervalLabel: String,
): String {
    return when (medication.repeatType) {
        MEDICATION_REPEAT_ONCE -> onceLabel
        MEDICATION_REPEAT_DAILY -> dailyLabel
        MEDICATION_REPEAT_EVERY_OTHER_DAY -> everyOtherDayLabel
        MEDICATION_REPEAT_CUSTOM -> customIntervalLabel.ifBlank { customLabel }
        else -> when {
            medication.note.contains("重复：每天") -> dailyLabel
            medication.note.contains("重复：隔天") -> everyOtherDayLabel
            medication.note.contains("重复：自定义") -> customLabel
            else -> onceLabel
        }
    }
}

private fun nextReminderTime(progressList: List<MedicationTodayProgress>): String {
    val reminderTimes = progressList
        .asSequence()
        .filter { it.isEnabled && !it.isAllTaken }
        .flatMap { progress ->
            progress.todayTimes
                .filter { it !in progress.takenTimes }
                .asSequence()
        }
        .mapNotNull(::parseReminderTime)
        .sorted()
        .toList()

    if (reminderTimes.isEmpty()) {
        return ""
    }

    val now = LocalTime.now()
    return (reminderTimes.firstOrNull { reminderTime -> !reminderTime.isBefore(now) }
        ?: reminderTimes.first())
        .formatReminderTime()
}

private fun parseReminderTime(rawTime: String): LocalTime? {
    val parts = rawTime.split(":")
    if (parts.size != 2) {
        return null
    }
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return runCatching {
        LocalTime.of(hour, minute)
    }.getOrNull()
}

private fun formatMedicationTimes(timesOfDay: String): String {
    return timesOfDay
        .split(",")
        .map { time -> formatMedicationTimeLabel(time.trim()) }
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

private fun formatMedicationTimeLabel(time: String): String {
    return when (time) {
        "08:00" -> "早晨 08:00"
        "12:00" -> "中午 12:00"
        "18:00" -> "晚上 18:00"
        else -> time
    }
}

private fun LocalTime.formatReminderTime(): String {
    return "%02d:%02d".format(hour, minute)
}

private fun formatDosageAmount(amount: Float): String {
    return if (amount % 1f == 0f) {
        amount.toInt().toString()
    } else {
        "%.1f".format(amount)
    }
}

private data class ReminderStyle(
    val label: String,
    val icon: ImageVector,
)

private data class ReminderPeriodOption(
    val label: String,
    val time: String,
)

