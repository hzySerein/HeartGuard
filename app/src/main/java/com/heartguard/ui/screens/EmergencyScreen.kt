package com.heartguard.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.telephony.SmsManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.heartguard.R
import com.heartguard.ui.theme.BackgroundWarm
import com.heartguard.ui.theme.SurfaceWarm
import com.heartguard.ui.theme.TextPrimary
import com.heartguard.ui.theme.TextSecondary
import com.heartguard.ui.theme.WarningRed
import com.heartguard.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.util.Locale

private val EmergencyAmber = Color(0xFFED6C02)
private val EmergencyAmberBackground = Color(0xFFFFF3E0)
private val EmergencyRed = Color(0xFFB3261E)
private val CallGreen = Color(0xFF2E7D32)

@Composable
fun EmergencyScreen(
    onCallContact: (String) -> Unit,
    onAddEmergencyContact: () -> Unit = {},
    emergencyPhoneNumber: String = DEFAULT_EMERGENCY_PHONE_NUMBER,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val emergencyContacts by SettingsManager.emergencyContacts.collectAsState()
    val hasEmergencyContacts = emergencyContacts.isNotEmpty()
    val primaryEmergencyContact = emergencyContacts.firstOrNull()
    var sendLocationSms by remember { mutableStateOf(true) }
    var pendingEmergencyPermissionRequest by remember {
        mutableStateOf<EmergencyPermissionRequest?>(null)
    }

    fun sendEmergencySmsIfGranted(
        request: EmergencyPermissionRequest,
        smsPermissionGranted: Boolean,
        locationPermissionGranted: Boolean,
    ) {
        if (request.shouldSendLocationSms && smsPermissionGranted) {
            coroutineScope.launch {
                sendEmergencyLocationSms(
                    context = context,
                    contacts = request.contacts,
                    includeLocation = locationPermissionGranted,
                )
            }
        }
    }

    val emergencyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionResults ->
        val request = pendingEmergencyPermissionRequest ?: return@rememberLauncherForActivityResult
        pendingEmergencyPermissionRequest = null

        val callPermissionGranted = permissionResults.isPermissionGranted(
            context = context,
            permission = Manifest.permission.CALL_PHONE,
        )
        val smsPermissionGranted = permissionResults.isPermissionGranted(
            context = context,
            permission = Manifest.permission.SEND_SMS,
        )
        val locationPermissionGranted = permissionResults.isPermissionGranted(
            context = context,
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
        )

        sendEmergencySmsIfGranted(
            request = request,
            smsPermissionGranted = smsPermissionGranted,
            locationPermissionGranted = locationPermissionGranted,
        )

        if (callPermissionGranted) {
            startEmergencyCall(context, request.phoneNumber)
        } else {
            openEmergencyDialer(context, request.phoneNumber)
        }
    }
    val onEmergencyCall = {
        val request = EmergencyPermissionRequest(
            phoneNumber = emergencyPhoneNumber,
            contacts = emergencyContacts,
            shouldSendLocationSms = sendLocationSms && emergencyContacts.isNotEmpty(),
        )
        val missingPermissions = buildList {
            if (!hasPermission(context, Manifest.permission.CALL_PHONE)) {
                add(Manifest.permission.CALL_PHONE)
            }
            if (request.shouldSendLocationSms) {
                if (!hasPermission(context, Manifest.permission.SEND_SMS)) {
                    add(Manifest.permission.SEND_SMS)
                }
                if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }

        if (missingPermissions.isEmpty()) {
            sendEmergencySmsIfGranted(
                request = request,
                smsPermissionGranted = hasPermission(context, Manifest.permission.SEND_SMS),
                locationPermissionGranted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION),
            )
            startEmergencyCall(context, request.phoneNumber)
        } else {
            pendingEmergencyPermissionRequest = request
            emergencyPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        EmergencyWarningSection()

        Spacer(modifier = Modifier.height(16.dp))

        EmergencyActionNotice(
            phoneNumber = emergencyPhoneNumber,
            hasEmergencyContacts = hasEmergencyContacts,
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (hasEmergencyContacts) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                emergencyContacts.forEach { contact ->
                    EmergencyContactCard(
                        contact = contact,
                        onCallContact = onCallContact,
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            EmergencyContactsEmptyState(
                onAddEmergencyContact = onAddEmergencyContact,
            )
        }

        if (hasEmergencyContacts) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = sendLocationSms,
                    onCheckedChange = {
                        sendLocationSms = it
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = WarningRed,
                    ),
                )
                Text(
                    text = stringResource(R.string.emergency_send_location_sms),
                    color = TextPrimary,
                    fontSize = 16.sp,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        SlideCallButton(
            phoneNumber = emergencyPhoneNumber,
            onSlideCompleted = onEmergencyCall,
        )

        primaryEmergencyContact?.let { contact ->
            TextButton(
                onClick = {
                    onCallContact(contact.phone)
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.emergency_call_contact),
                    color = EmergencyRed,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EmergencyActionNotice(
    phoneNumber: String,
    hasEmergencyContacts: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.emergency_action_title),
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.emergency_action_call, phoneNumber),
                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            if (hasEmergencyContacts) {
                Text(
                    text = stringResource(R.string.emergency_action_sms),
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

@Composable
private fun EmergencyContactsEmptyState(
    onAddEmergencyContact: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.emergency_empty_contacts_title),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.emergency_empty_contacts_description),
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }
        }

        Button(
            onClick = onAddEmergencyContact,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmergencyAmber,
                contentColor = Color.White,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Contacts,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.emergency_add_contacts_action),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EmergencyWarningSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = EmergencyAmberBackground,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = "SOS",
                modifier = Modifier.size(72.dp),
                tint = EmergencyAmber,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.emergency_title),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.emergency_helping),
                color = TextSecondary,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun EmergencyContactCard(
    contact: SettingsManager.EmergencyContact,
    onCallContact: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = contact.name,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = maskPhoneNumber(contact.phone),
                    color = TextSecondary,
                    fontSize = 16.sp,
                )
            }

            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = CallGreen,
            ) {
                IconButton(
                    onClick = {
                        onCallContact(contact.phone)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = stringResource(R.string.emergency_dial),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideCallButton(
    phoneNumber: String,
    onSlideCompleted: () -> Unit,
) {
    val currentOnSlideCompleted by rememberUpdatedState(onSlideCompleted)
    val density = LocalDensity.current
    val knobSize = 48.dp
    val knobSizePx = with(density) { knobSize.toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var slideCompleted by remember { mutableStateOf(false) }

    val maxOffsetPx = (trackWidthPx - knobSizePx).coerceAtLeast(0f)
    val slideProgress = if (maxOffsetPx > 0f) {
        (dragOffsetPx / maxOffsetPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedOffsetPx by animateFloatAsState(
        targetValue = dragOffsetPx,
        label = "slideCallHandleOffset",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            slideCompleted -> CallGreen
            slideProgress >= 0.6f -> Color(0xFFE66A1F)
            else -> EmergencyRed
        },
        label = "slideCallContainerColor",
    )
    val promptText = when {
        slideCompleted -> stringResource(R.string.emergency_calling, phoneNumber)
        slideProgress >= SLIDE_COMPLETE_THRESHOLD -> stringResource(
            R.string.emergency_release_to_call,
            phoneNumber,
        )
        slideProgress >= 0.45f -> stringResource(R.string.emergency_keep_sliding, phoneNumber)
        else -> stringResource(R.string.emergency_slide_to_call, phoneNumber)
    }

    fun completeSlide() {
        if (slideCompleted) {
            return
        }
        slideCompleted = true
        dragOffsetPx = maxOffsetPx
        currentOnSlideCompleted()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
                .onSizeChanged { size ->
                    trackWidthPx = size.width.toFloat()
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        IntOffset(animatedOffsetPx.roundToInt(), 0)
                    }
                    .size(knobSize)
                    .pointerInput(maxOffsetPx, slideCompleted) {
                        detectDragGestures(
                            onDragEnd = {
                                val completedProgress = if (maxOffsetPx > 0f) {
                                    dragOffsetPx / maxOffsetPx
                                } else {
                                    0f
                                }
                                if (completedProgress >= SLIDE_COMPLETE_THRESHOLD) {
                                    completeSlide()
                                } else if (!slideCompleted) {
                                    dragOffsetPx = 0f
                                }
                            },
                            onDragCancel = {
                                if (!slideCompleted) {
                                    dragOffsetPx = 0f
                                }
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            if (!slideCompleted && maxOffsetPx > 0f) {
                                dragOffsetPx = (dragOffsetPx + dragAmount.x).coerceIn(0f, maxOffsetPx)
                            }
                        }
                    },
                shape = CircleShape,
                color = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.emergency_slide),
                        tint = EmergencyRed,
                    )
                }
            }

            Text(
                text = promptText,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun startEmergencyCall(
    context: Context,
    phoneNumber: String,
) {
    val normalizedNumber = phoneNumber.filterNot { it.isWhitespace() }
    if (!hasPermission(context, Manifest.permission.CALL_PHONE)) {
        openEmergencyDialer(context, normalizedNumber)
        return
    }

    val callIntent = Intent(Intent.ACTION_CALL).apply {
        data = Uri.parse("tel:$normalizedNumber")
    }

    try {
        context.startActivity(callIntent)
    } catch (error: SecurityException) {
        openEmergencyDialer(context, normalizedNumber)
    }
}

private fun openEmergencyDialer(
    context: Context,
    phoneNumber: String,
) {
    val normalizedNumber = phoneNumber.filterNot { it.isWhitespace() }
    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$normalizedNumber")
    }
    context.startActivity(dialIntent)
}

private suspend fun sendEmergencyLocationSms(
    context: Context,
    contacts: List<SettingsManager.EmergencyContact>,
    includeLocation: Boolean,
) {
    if (!hasPermission(context, Manifest.permission.SEND_SMS) || contacts.isEmpty()) {
        return
    }

    val location = if (includeLocation) {
        resolveCurrentLocation(context)
    } else {
        null
    }
    val message = buildEmergencySmsMessage(context, location)
    val smsManager = SmsManager.getDefault()
    val recipientNumbers = contacts
        .map { contact -> contact.phone.toSmsNumber() }
        .filter { phone -> phone.isNotBlank() }
        .distinct()

    withContext(Dispatchers.IO) {
        recipientNumbers.forEach { phoneNumber ->
            runCatching {
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                }
            }
        }
    }
}

private suspend fun resolveCurrentLocation(context: Context): Location? {
    if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
        return null
    }

    val locationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    return runCatching {
        val cancellationTokenSource = CancellationTokenSource()
        locationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
            .await()
    }.getOrNull() ?: runCatching {
        locationClient.lastLocation.await()
    }.getOrNull()
}

private fun buildEmergencySmsMessage(
    context: Context,
    location: Location?,
): String {
    val locationText = if (location == null) {
        context.getString(R.string.emergency_sms_location_unavailable)
    } else {
        val latitude = String.format(Locale.US, "%.6f", location.latitude)
        val longitude = String.format(Locale.US, "%.6f", location.longitude)
        context.getString(R.string.emergency_sms_location, latitude, longitude)
    }
    return context.getString(R.string.emergency_sms_message, locationText)
}

private fun hasPermission(
    context: Context,
    permission: String,
): Boolean {
    return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

private fun String.toSmsNumber(): String {
    return filter { character ->
        character.isDigit() || character == '+'
    }
}

private fun maskPhoneNumber(phone: String): String {
    val digits = phone.filter { character -> character.isDigit() }
    return when {
        digits.isBlank() -> "未设置"
        digits.length >= 7 -> "${digits.take(3)}****${digits.takeLast(4)}"
        digits.length > 2 -> "${digits.take(1)}****${digits.takeLast(1)}"
        else -> "****"
    }
}

private fun Map<String, Boolean>.isPermissionGranted(
    context: Context,
    permission: String,
): Boolean {
    return if (containsKey(permission)) {
        this[permission] == true
    } else {
        hasPermission(context, permission)
    }
}

private data class EmergencyPermissionRequest(
    val phoneNumber: String,
    val contacts: List<SettingsManager.EmergencyContact>,
    val shouldSendLocationSms: Boolean,
)

private const val DEFAULT_EMERGENCY_PHONE_NUMBER = "120"
private const val SLIDE_COMPLETE_THRESHOLD = 0.85f
