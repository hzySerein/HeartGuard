package com.heartguard.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heartguard.R
import com.heartguard.ui.theme.AccentOrange
import com.heartguard.ui.theme.BackgroundWarm
import com.heartguard.ui.theme.ConfirmGreen
import com.heartguard.ui.theme.SurfaceWarm
import com.heartguard.ui.theme.TextPrimary
import com.heartguard.ui.theme.TextSecondary
import com.heartguard.utils.SettingsManager
import com.heartguard.viewmodel.ChatMessage
import com.heartguard.viewmodel.ChatMessageType
import com.heartguard.viewmodel.ChatViewModel

private val SosRed = Color(0xFFB3261E)
private val UserBubbleGreen = Color(0xFFDDEFD8)
private val RoleSelectedBackground = Color(0xFFE7F1E7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChatViewModel,
    onSosClick: () -> Unit,
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingDuration by viewModel.recordingDuration.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val userProfile by SettingsManager.userProfile.collectAsStateWithLifecycle()
    var showRoleSheet by remember { mutableStateOf(false) }
    var selectedRoleId by rememberSaveable { mutableStateOf(DEFAULT_ROLE_ID) }

    LaunchedEffect(selectedRoleId) {
        viewModel.setRole(selectedRoleId)
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                R.string.runtime_permission_required,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
        ) {
            HomeTopSection(
                nickname = userProfile.nickname,
                avatarUri = userProfile.avatarUri,
                onAvatarClick = {
                    showRoleSheet = true
                },
                onSosClick = onSosClick,
            )

            Spacer(modifier = Modifier.height(8.dp))

            ChatSection(
                messages = messages,
                isThinking = isThinking,
                modifier = Modifier.weight(1f),
                isRecording = isRecording,
                recordingDuration = recordingDuration,
                isProcessing = isProcessing,
                onSendText = { text ->
                    viewModel.sendMessage(text)
                },
                onPressStart = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.startRecording()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onPressEnd = viewModel::stopRecordingAndSend,
                onPressCancel = viewModel::cancelRecording,
            )
        }

        if (showRoleSheet) {
            RoleSelectionBottomSheet(
                selectedRoleId = selectedRoleId,
                onDismiss = {
                    showRoleSheet = false
                },
                onRoleSelected = { role ->
                    selectedRoleId = role.id
                    showRoleSheet = false
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.role_switch_success,
                            context.getString(role.nameRes),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }
}

@Composable
private fun HomeTopSection(
    nickname: String,
    avatarUri: String,
    onAvatarClick: () -> Unit,
    onSosClick: () -> Unit,
) {
    val emergencyDescription = stringResource(R.string.home_sos)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UserAvatar(
                nickname = nickname,
                avatarUri = avatarUri,
                modifier = Modifier.size(48.dp),
                fallbackFontSize = 20.sp,
                onClick = onAvatarClick,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = nickname,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FeatureTag(
                    text = stringResource(R.string.home_tag_ai_companion),
                    modifier = Modifier.weight(1f),
                )
                FeatureTag(
                    text = stringResource(R.string.home_tag_anti_fraud),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FeatureTag(
                    text = stringResource(R.string.home_tag_medication),
                    modifier = Modifier.weight(1f),
                )
                FeatureTag(
                    text = stringResource(R.string.home_tag_emergency),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Button(
            onClick = onSosClick,
            modifier = Modifier
                .size(54.dp)
                .semantics {
                    contentDescription = emergencyDescription
                },
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SosRed.copy(alpha = 0.92f),
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "SOS",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun ChatSection(
    messages: List<ChatMessage>,
    isThinking: Boolean,
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    recordingDuration: Long,
    isProcessing: Boolean,
    onSendText: (String) -> Unit,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onPressCancel: () -> Unit,
) {
    var isVoiceMode by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()
    val inputEnabled = !isRecording && !isProcessing
    val lastMessageId = messages.lastOrNull()?.id
    val visibleChatItemCount = messages.size + if (isThinking) 1 else 0
    val sendCurrentText = {
        val message = inputText.trim()
        if (message.isNotBlank()) {
            onSendText(message)
            inputText = ""
        }
    }

    LaunchedEffect(lastMessageId, isThinking, visibleChatItemCount) {
        if (visibleChatItemCount > 0) {
            chatListState.animateScrollToItem(visibleChatItemCount - 1)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn(
            state = chatListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = messages,
                key = { message -> message.id },
            ) { message ->
                ChatBubble(message = message)
            }

            if (isThinking) {
                item(key = "thinking") {
                    ThinkingBubble()
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        QuickQuestionChips(
            enabled = inputEnabled,
            onQuestionClick = { question ->
                onSendText(question)
            },
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = SurfaceWarm,
                tonalElevation = 2.dp,
            ) {
                IconButton(
                    onClick = {
                        isVoiceMode = !isVoiceMode
                    },
                    enabled = !isRecording,
                ) {
                    Icon(
                        imageVector = if (isVoiceMode) Icons.Filled.Keyboard else Icons.Filled.Mic,
                        contentDescription = if (isVoiceMode) {
                            stringResource(R.string.home_switch_text_input)
                        } else {
                            stringResource(R.string.home_switch_voice_input)
                        },
                        tint = TextPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isVoiceMode) {
                VoiceHoldButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = !isProcessing,
                    isRecording = isRecording,
                    isProcessing = isProcessing,
                    recordingDuration = recordingDuration,
                    onPressStart = onPressStart,
                    onPressEnd = onPressEnd,
                    onPressCancel = onPressCancel,
                )
            } else {
                TextMessageInput(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = inputEnabled,
                    onSend = sendCurrentText,
                )
            }
        }
    }
}

@Composable
private fun TextMessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = value.isNotBlank() && enabled

    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = Color.Black,
                shape = RoundedCornerShape(18.dp),
            )
            .alpha(if (enabled) 1f else 0.65f),
        color = SurfaceWarm,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSend) {
                            onSend()
                        }
                    },
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = stringResource(R.string.home_text_input_placeholder),
                                color = TextSecondary,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (canSend) ConfirmGreen else TextSecondary.copy(alpha = 0.16f),
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = stringResource(R.string.home_send),
                    tint = if (canSend) Color.White else TextSecondary.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun QuickQuestionChips(
    enabled: Boolean,
    onQuestionClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.home_quick_questions_scroll_hint),
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(quickQuestionOptions) { option ->
                val question = stringResource(option.textRes)
                QuickQuestionChip(
                    text = question,
                    enabled = enabled,
                    onClick = {
                        onQuestionClick(question)
                    },
                )
            }
        }
    }
}

@Composable
private fun QuickQuestionChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.55f)
            .border(
                width = 1.dp,
                color = Color.Black,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VoiceHoldButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    isRecording: Boolean,
    isProcessing: Boolean,
    recordingDuration: Long,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onPressCancel: () -> Unit,
) {
    val currentIsRecording by rememberUpdatedState(isRecording)
    val currentOnPressStart by rememberUpdatedState(onPressStart)
    val currentOnPressEnd by rememberUpdatedState(onPressEnd)
    val currentOnPressCancel by rememberUpdatedState(onPressCancel)
    val buttonColor = when {
        isProcessing -> SurfaceWarm.copy(alpha = 0.6f)
        isRecording -> ConfirmGreen.copy(alpha = 0.85f)
        enabled -> SurfaceWarm
        else -> SurfaceWarm.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = Color.Black,
                shape = RoundedCornerShape(18.dp),
            )
            .alpha(if (enabled || isRecording || isProcessing) 1f else 0.65f)
            .testTag("voice_button")
            .pointerInput(enabled, isProcessing) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!enabled || isProcessing) {
                        return@awaitEachGesture
                    }
                    if (currentIsRecording) {
                        currentOnPressCancel()
                        return@awaitEachGesture
                    }

                    currentOnPressStart()

                    var canceled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.id != down.id && it.pressed }) {
                            canceled = true
                            currentOnPressCancel()
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        if (!change.position.isInside(size)) {
                            canceled = true
                            currentOnPressCancel()
                            break
                        }
                        if (change.changedToUp() || !change.pressed) {
                            break
                        }
                    }

                    if (!canceled) {
                        currentOnPressEnd()
                    }
                }
            },
        color = buttonColor,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                isProcessing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = TextSecondary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.home_recognizing),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                }

                isRecording -> {
                    RecordingWaveform()
                    Text(
                        text = stringResource(R.string.home_release_to_send),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = formatRecordingDuration(recordingDuration),
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.home_hold_to_talk),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingWaveform() {
    val transition = rememberInfiniteTransition(label = "recordingWaveform")
    val barScale1 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = waveformAnimation(delayMillis = 0),
        label = "waveformBar1",
    )
    val barScale2 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = waveformAnimation(delayMillis = 90),
        label = "waveformBar2",
    )
    val barScale3 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = waveformAnimation(delayMillis = 180),
        label = "waveformBar3",
    )
    val barScale4 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = waveformAnimation(delayMillis = 270),
        label = "waveformBar4",
    )

    Row(
        modifier = Modifier.height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(barScale1, barScale2, barScale3, barScale4).forEach { scale ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((16f * scale).dp)
                    .background(TextPrimary, RoundedCornerShape(999.dp))
            )
        }
    }
}

private fun waveformAnimation(delayMillis: Int) = infiniteRepeatable<Float>(
    animation = tween(
        durationMillis = 420,
        delayMillis = delayMillis,
        easing = LinearEasing,
    ),
    repeatMode = RepeatMode.Reverse,
)

private fun Offset.isInside(size: IntSize): Boolean {
    return x >= 0f && x <= size.width.toFloat() && y >= 0f && y <= size.height.toFloat()
}

private fun formatRecordingDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
) {
    val isUser = !message.isAi
    val displayText = if (isUser && message.type == ChatMessageType.VOICE) {
        stringResource(R.string.home_voice_message, message.text)
    } else {
        message.text
    }
    val bubbleWidth = if (isUser) 0.82f else 0.84f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(bubbleWidth),
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp,
                bottomStart = if (isUser) 20.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp,
            ),
            color = if (isUser) UserBubbleGreen else Color.White,
            tonalElevation = 1.dp,
        ) {
            Text(
                text = displayText,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                color = TextPrimary,
                fontSize = 18.sp,
                lineHeight = 29.sp,
                fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 1.dp,
        ) {
            Text(
                text = stringResource(R.string.chat_ai_thinking),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                color = TextSecondary,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleSelectionBottomSheet(
    selectedRoleId: String,
    onDismiss: () -> Unit,
    onRoleSelected: (RoleOption) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundWarm,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_role_selection),
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(roleOptions) { role ->
                    RoleCard(
                        role = role,
                        selected = role.id == selectedRoleId,
                        onClick = {
                            onRoleSelected(role)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleCard(
    role: RoleOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val roleName = stringResource(role.nameRes)
    val roleDescription = stringResource(role.descriptionRes)
    val avatarText = stringResource(role.avatarTextRes)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ConfirmGreen else TextSecondary.copy(alpha = 0.18f),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) RoleSelectedBackground else SurfaceWarm,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(role.avatarColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = avatarText,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = roleName,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = roleDescription,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    role.tagRes.forEach { tagRes ->
                        RoleTag(text = stringResource(tagRes))
                    }
                }
            }

            if (selected) {
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = ConfirmGreen,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFF0EBE0),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun RoleTag(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.82f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private data class RoleOption(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val avatarTextRes: Int,
    val tagRes: List<Int>,
    val avatarColor: Color,
)

private data class QuickQuestionOption(
    @StringRes val textRes: Int,
)

private val quickQuestionOptions = listOf(
    QuickQuestionOption(R.string.home_quick_medication_today),
    QuickQuestionOption(R.string.home_quick_fraud_check),
    QuickQuestionOption(R.string.home_quick_healthy_exercise),
)

private const val DEFAULT_ROLE_ID = "nurse"

private val roleOptions = listOf(
    RoleOption(
        id = "tiger",
        nameRes = R.string.role_tiger_name,
        descriptionRes = R.string.role_tiger_description,
        avatarTextRes = R.string.role_tiger_avatar,
        tagRes = listOf(
            R.string.role_tag_companion,
            R.string.role_tag_simple_qa,
        ),
        avatarColor = AccentOrange,
    ),
    RoleOption(
        id = DEFAULT_ROLE_ID,
        nameRes = R.string.role_nurse_name,
        descriptionRes = R.string.role_nurse_description,
        avatarTextRes = R.string.role_nurse_avatar,
        tagRes = listOf(
            R.string.role_tag_medication,
            R.string.role_tag_care,
        ),
        avatarColor = ConfirmGreen,
    ),
    RoleOption(
        id = "granddaughter",
        nameRes = R.string.role_granddaughter_name,
        descriptionRes = R.string.role_granddaughter_description,
        avatarTextRes = R.string.role_granddaughter_avatar,
        tagRes = listOf(
            R.string.role_tag_family_chat,
            R.string.role_tag_lively,
        ),
        avatarColor = Color(0xFF80A7C8),
    ),
    RoleOption(
        id = "volunteer",
        nameRes = R.string.role_volunteer_name,
        descriptionRes = R.string.role_volunteer_description,
        avatarTextRes = R.string.role_volunteer_avatar,
        tagRes = listOf(
            R.string.role_tag_assist,
            R.string.role_tag_reminder,
        ),
        avatarColor = Color(0xFF9E8BC0),
    ),
)
