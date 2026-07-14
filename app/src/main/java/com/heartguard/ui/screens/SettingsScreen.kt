package com.heartguard.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.heartguard.BuildConfig
import com.heartguard.R
import com.heartguard.ui.theme.BackgroundWarm
import com.heartguard.ui.theme.ConfirmGreen
import com.heartguard.ui.theme.SurfaceWarm
import com.heartguard.ui.theme.TextPrimary
import com.heartguard.ui.theme.TextSecondary
import com.heartguard.utils.SettingsManager

private val SettingAvatarBackground = Color(0xFFE7F1E7)
private val SettingChoiceBackground = Color(0xFFEDEDED)
private val SettingChoiceSelectedBackground = Color(0xFFE7F1E7)

@Composable
fun SettingsScreen(
    initialRoute: String = SettingsRoutes.MAIN,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = initialRoute,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(SettingsRoutes.MAIN) {
            SettingsMainScreen(
                onNavigate = { route ->
                    navController.navigate(route)
                },
            )
        }
        composable(SettingsRoutes.PROFILE) {
            UserProfileSettingsScreen(
                onSaved = {
                    val returnedToMain = navController.popBackStack(
                        route = SettingsRoutes.MAIN,
                        inclusive = false,
                    )
                    if (!returnedToMain) {
                        navController.navigate(SettingsRoutes.MAIN) {
                            popUpTo(SettingsRoutes.PROFILE) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable(SettingsRoutes.EMERGENCY_CONTACTS) {
            EmergencyContactsSettingsScreen()
        }
        composable(SettingsRoutes.VOICE) {
            VoiceSettingsScreen()
        }
        composable(SettingsRoutes.FONT_SIZE) {
            FontSizeSettingsScreen()
        }
        composable(SettingsRoutes.HELP) {
            HelpGuideScreen()
        }
        composable(SettingsRoutes.PRIVACY_PERMISSIONS) {
            PrivacyPermissionsScreen()
        }
        composable(SettingsRoutes.ABOUT) {
            AboutHeartGuardScreen()
        }
    }
}

@Composable
private fun SettingsMainScreen(
    onNavigate: (String) -> Unit,
) {
    val userProfile by SettingsManager.userProfile.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UserAvatar(
                nickname = userProfile.nickname,
                avatarUri = userProfile.avatarUri,
                modifier = Modifier
                    .size(96.dp),
                fallbackFontSize = 34.sp,
                onClick = {
                    onNavigate(SettingsRoutes.PROFILE)
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = userProfile.nickname,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    onNavigate(SettingsRoutes.PROFILE)
                },
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = SurfaceWarm,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                settingsMenuItems.forEachIndexed { index, item ->
                    SettingsListItem(
                        item = item,
                        onClick = {
                            onNavigate(item.route)
                        },
                    )
                    if (index < settingsMenuItems.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = TextSecondary.copy(alpha = 0.18f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserProfileSettingsScreen(
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val profile = remember(context) { SettingsManager.getUserProfile(context) }
    var nickname by remember(context) { mutableStateOf(profile.nickname) }
    var avatarUri by remember(context) { mutableStateOf(profile.avatarUri) }
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { selectedUri ->
        selectedUri ?: return@rememberLauncherForActivityResult
        persistAvatarReadPermission(context, selectedUri)
        avatarUri = selectedUri.toString()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_profile_title),
                modifier = Modifier.fillMaxWidth(),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            UserAvatar(
                nickname = nickname,
                avatarUri = avatarUri,
                modifier = Modifier.size(116.dp),
                showEditBadge = true,
                fallbackFontSize = 42.sp,
                badgeSize = 34.dp,
                onClick = {
                    avatarPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )

            Text(
                text = stringResource(R.string.settings_profile_avatar_hint),
                color = TextSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )

            OutlinedTextField(
                value = nickname,
                onValueChange = {
                    nickname = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(stringResource(R.string.settings_profile_name))
                },
                placeholder = {
                    Text(stringResource(R.string.settings_profile_name_placeholder))
                },
                singleLine = true,
            )
        }

        SaveSettingsButton(
            modifier = Modifier.align(Alignment.BottomCenter),
            labelRes = R.string.settings_profile_save,
            onClick = {
                val safeNickname = nickname.trim()
                if (safeNickname.isBlank()) {
                    Toast.makeText(
                        context,
                        R.string.settings_profile_name_required,
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    SettingsManager.saveUserProfile(
                        context = context,
                        nickname = safeNickname,
                        avatarUri = avatarUri,
                    )
                    val savedProfile = SettingsManager.getUserProfile(context)
                    nickname = savedProfile.nickname
                    avatarUri = savedProfile.avatarUri
                    Toast.makeText(context, R.string.settings_profile_saved, Toast.LENGTH_SHORT).show()
                    onSaved()
                }
            },
        )
    }
}

private fun persistAvatarReadPermission(
    context: Context,
    avatarUri: Uri,
) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            avatarUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

@Composable
private fun SettingsListItem(
    item: SettingsMenuItem,
    onClick: () -> Unit,
) {
    val title = stringResource(item.titleRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = title,
            tint = ConfirmGreen,
            modifier = Modifier.size(28.dp),
        )

        Spacer(modifier = Modifier.width(18.dp))

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.settings_enter),
            tint = TextSecondary,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun EmergencyContactsSettingsScreen() {
    val context = LocalContext.current
    var contacts by remember(context) {
        mutableStateOf(SettingsManager.getConfiguredEmergencyContacts(context))
    }

    fun updateContacts(updatedContacts: List<SettingsManager.EmergencyContact>) {
        contacts = updatedContacts
        SettingsManager.saveEmergencyContacts(context, updatedContacts)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(20.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_emergency_contacts_title),
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (contacts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.settings_no_emergency_contacts),
                        color = TextSecondary,
                        fontSize = 16.sp,
                    )
                }
            }

            itemsIndexed(contacts) { index, contact ->
                EmergencyContactEditor(
                    index = index,
                    contact = contact,
                    onNameChange = { name ->
                        updateContacts(
                            contacts.replaceContact(
                                index = index,
                                contact = contact.copy(name = name),
                            ),
                        )
                    },
                    onPhoneChange = { phone ->
                        updateContacts(
                            contacts.replaceContact(
                                index = index,
                                contact = contact.copy(phone = phone),
                            ),
                        )
                    },
                    onDelete = {
                        updateContacts(
                            contacts.filterIndexed { contactIndex, _ ->
                                contactIndex != index
                            },
                        )
                    },
                )
            }

            item {
                Button(
                    onClick = {
                        updateContacts(
                            contacts + SettingsManager.EmergencyContact(
                                name = "",
                                phone = "",
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceWarm,
                        contentColor = ConfirmGreen,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.settings_add),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_add_contact),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        SaveSettingsButton(
            modifier = Modifier.align(Alignment.BottomCenter),
            onClick = {
                SettingsManager.saveEmergencyContacts(context, contacts)
                val savedCount = contacts.count { contact ->
                    contact.name.trim().isNotBlank() && contact.phone.trim().isNotBlank()
                }
                val message = if (savedCount > 0) {
                    context.getString(R.string.settings_saved_contact_count, savedCount)
                } else {
                    context.getString(R.string.settings_cleared_contacts)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun EmergencyContactEditor(
    index: Int,
    contact: SettingsManager.EmergencyContact,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWarm,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_contact_index, index + 1),
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.settings_delete_contact),
                        tint = ConfirmGreen,
                    )
                }
            }

            OutlinedTextField(
                value = contact.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(stringResource(R.string.settings_contact_name))
                },
                singleLine = true,
            )

            OutlinedTextField(
                value = contact.phone,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(stringResource(R.string.settings_contact_phone))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun FontSizeSettingsScreen() {
    val context = LocalContext.current
    val fontSizeOptions = SettingsManager.FONT_SIZE_OPTIONS
    var sizeLabel by remember(context) { mutableStateOf(SettingsManager.getFontSize(context)) }
    val previewFontSizeTarget = when (fontSizeOptions.indexOf(sizeLabel)) {
        0 -> 80f
        1 -> 100f
        2 -> 120f
        3 -> 140f
        else -> 100f
    }
    val previewFontSize by animateFloatAsState(
        targetValue = previewFontSizeTarget,
        label = "FontSizePreview",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "A",
                color = TextPrimary,
                fontSize = previewFontSize.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (previewFontSize + 8f).sp,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                RoundAdjustButton(
                    text = "-",
                    onClick = {
                        sizeLabel = fontSizeOptions.previousOption(sizeLabel)
                    },
                )

                Text(
                    text = sizeLabel,
                    modifier = Modifier.width(96.dp),
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                RoundAdjustButton(
                    text = "+",
                    onClick = {
                        sizeLabel = fontSizeOptions.nextOption(sizeLabel)
                    },
                )
            }
        }

        SaveSettingsButton(
            modifier = Modifier.align(Alignment.BottomCenter),
            onClick = {
                SettingsManager.saveFontSize(context, sizeLabel)
                Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun VoiceSettingsScreen() {
    val context = LocalContext.current
    var volume by remember(context) { mutableStateOf(SettingsManager.getVolume(context)) }
    var speed by remember(context) { mutableStateOf(SettingsManager.getSpeed(context)) }
    var tone by remember(context) { mutableStateOf(SettingsManager.getTone(context)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            VoiceOptionSection(
                title = stringResource(R.string.settings_volume),
                options = SettingsManager.VOLUME_OPTIONS,
                selected = volume,
                onSelected = {
                    volume = it
                },
            )
            VoiceOptionSection(
                title = stringResource(R.string.settings_speed),
                options = SettingsManager.SPEED_OPTIONS,
                selected = speed,
                onSelected = {
                    speed = it
                },
            )
            VoiceOptionSection(
                title = stringResource(R.string.settings_tone),
                options = SettingsManager.TONE_OPTIONS,
                selected = tone,
                onSelected = {
                    tone = it
                },
            )
        }

        SaveSettingsButton(
            modifier = Modifier.align(Alignment.BottomCenter),
            onClick = {
                SettingsManager.saveVoiceSettings(
                    context = context,
                    volume = volume,
                    speed = speed,
                    tone = tone,
                )
                Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun VoiceOptionSection(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                SettingChoiceChip(
                    text = option,
                    selected = selected == option,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingChoiceChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) SettingChoiceSelectedBackground else SettingChoiceBackground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (selected) ConfirmGreen else TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RoundAdjustButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(58.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(2.dp, TextSecondary.copy(alpha = 0.55f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SaveSettingsButton(
    modifier: Modifier = Modifier,
    @StringRes labelRes: Int = R.string.settings_save,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ConfirmGreen,
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HelpGuideScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsSectionTitle(title = stringResource(R.string.settings_help_title))
        }

        item {
            Text(
                text = stringResource(R.string.settings_help_intro),
                color = TextSecondary,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )
        }

        item {
            SettingsSectionTitle(title = stringResource(R.string.settings_help_features))
        }

        helpFeatureItems.forEach { feature ->
            item {
                HelpFeatureCard(feature = feature)
            }
        }

        item {
            SettingsSectionTitle(title = stringResource(R.string.settings_help_steps))
        }

        helpGuideSteps.forEachIndexed { index, step ->
            item {
                GuideStepRow(
                    index = index + 1,
                    step = step,
                )
            }
        }

        item {
            SettingsSectionTitle(title = stringResource(R.string.settings_help_faq))
        }

        helpFaqItems.forEach { faq ->
            item {
                FaqExpandableItem(faq = faq)
            }
        }
    }
}

@Composable
private fun PrivacyPermissionsScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsSectionTitle(title = stringResource(R.string.settings_privacy_permissions_title))
        }

        item {
            Text(
                text = stringResource(R.string.settings_privacy_permissions_intro),
                color = TextSecondary,
                fontSize = 16.sp,
                lineHeight = 23.sp,
            )
        }

        privacyPermissionItems.forEach { permissionItem ->
            item {
                PrivacyPermissionCard(item = permissionItem)
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SettingChoiceSelectedBackground,
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = ConfirmGreen,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_demo_mode_notice),
                        modifier = Modifier.weight(1f),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutHeartGuardScreen() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWarm)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SurfaceWarm,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = stringResource(R.string.settings_app_info),
                        modifier = Modifier.size(52.dp),
                        tint = ConfirmGreen,
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        color = TextSecondary,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = stringResource(R.string.settings_developer),
                        color = TextSecondary,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        item {
            AboutTextSection(
                title = stringResource(R.string.settings_privacy_title),
                body = stringResource(R.string.settings_privacy_body),
            )
        }

        item {
            AboutTextSection(
                title = stringResource(R.string.settings_terms_title),
                body = stringResource(R.string.settings_terms_body),
            )
        }

        item {
            Button(
                onClick = {
                    Toast.makeText(
                        context,
                        "\u5F53\u524D\u7248\u672C ${BuildConfig.VERSION_NAME}\uFF0C\u8BF7\u5173\u6CE8\u5E94\u7528\u5546\u5E97\u66F4\u65B0",
                        Toast.LENGTH_LONG,
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ConfirmGreen,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.settings_check_update),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PrivacyPermissionCard(item: PrivacyPermissionItem) {
    val title = stringResource(item.titleRes)
    val description = stringResource(item.descriptionRes)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceWarm,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = SettingAvatarBackground,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = title,
                        tint = ConfirmGreen,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun HelpFeatureCard(feature: HelpFeatureItem) {
    val title = stringResource(feature.titleRes)
    val description = stringResource(feature.descriptionRes)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWarm,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = SettingAvatarBackground,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = title,
                        tint = ConfirmGreen,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun GuideStepRow(
    index: Int,
    step: HelpGuideStep,
) {
    val title = stringResource(step.titleRes)
    val description = stringResource(step.descriptionRes)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = ConfirmGreen,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = index.toString(),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = title,
                    modifier = Modifier.size(22.dp),
                    tint = ConfirmGreen,
                )
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun FaqExpandableItem(faq: FaqItem) {
    var expanded by remember { mutableStateOf(false) }
    val question = stringResource(faq.questionRes)
    val answer = stringResource(faq.answerRes)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                expanded = !expanded
            },
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWarm,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = question,
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(
                        if (expanded) {
                            R.string.settings_collapse
                        } else {
                            R.string.settings_expand
                        },
                    ),
                    color = ConfirmGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = answer,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

@Composable
private fun AboutTextSection(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWarm,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

private data class SettingsMenuItem(
    @StringRes val titleRes: Int,
    val route: String,
    val icon: ImageVector,
)

private data class HelpFeatureItem(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
)

private data class HelpGuideStep(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
)

private data class PrivacyPermissionItem(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
)

private data class FaqItem(
    @StringRes val questionRes: Int,
    @StringRes val answerRes: Int,
)

private val settingsMenuItems = listOf(
    SettingsMenuItem(
        titleRes = R.string.settings_profile_menu,
        route = SettingsRoutes.PROFILE,
        icon = Icons.Filled.Person,
    ),
    SettingsMenuItem(
        titleRes = R.string.settings_emergency_contacts_menu,
        route = SettingsRoutes.EMERGENCY_CONTACTS,
        icon = Icons.Filled.Contacts,
    ),
    SettingsMenuItem(
        titleRes = R.string.settings_voice_menu,
        route = SettingsRoutes.VOICE,
        icon = Icons.Filled.RecordVoiceOver,
    ),
    SettingsMenuItem(
        titleRes = R.string.settings_font_size_menu,
        route = SettingsRoutes.FONT_SIZE,
        icon = Icons.Filled.TextFields,
    ),
    SettingsMenuItem(
        titleRes = R.string.settings_help_title,
        route = SettingsRoutes.HELP,
        icon = Icons.Filled.Help,
    ),
    SettingsMenuItem(
        titleRes = R.string.settings_privacy_permissions_title,
        route = SettingsRoutes.PRIVACY_PERMISSIONS,
        icon = Icons.Filled.Shield,
    ),
    SettingsMenuItem(
        titleRes = R.string.settings_about_menu,
        route = SettingsRoutes.ABOUT,
        icon = Icons.Filled.Info,
    ),
)

private val helpFeatureItems = listOf(
    HelpFeatureItem(
        titleRes = R.string.help_feature_ai_title,
        descriptionRes = R.string.help_feature_ai_description,
        icon = Icons.Filled.RecordVoiceOver,
    ),
    HelpFeatureItem(
        titleRes = R.string.help_feature_fraud_title,
        descriptionRes = R.string.help_feature_fraud_description,
        icon = Icons.Filled.Shield,
    ),
    HelpFeatureItem(
        titleRes = R.string.help_feature_medication_title,
        descriptionRes = R.string.help_feature_medication_description,
        icon = Icons.Filled.Notifications,
    ),
    HelpFeatureItem(
        titleRes = R.string.help_feature_emergency_title,
        descriptionRes = R.string.help_feature_emergency_description,
        icon = Icons.Filled.Call,
    ),
)

private val helpGuideSteps = listOf(
    HelpGuideStep(
        titleRes = R.string.help_step_settings_title,
        descriptionRes = R.string.help_step_settings_description,
        icon = Icons.Filled.Contacts,
    ),
    HelpGuideStep(
        titleRes = R.string.help_step_reminder_title,
        descriptionRes = R.string.help_step_reminder_description,
        icon = Icons.Filled.Notifications,
    ),
    HelpGuideStep(
        titleRes = R.string.help_step_fraud_title,
        descriptionRes = R.string.help_step_fraud_description,
        icon = Icons.Filled.Shield,
    ),
    HelpGuideStep(
        titleRes = R.string.help_step_emergency_title,
        descriptionRes = R.string.help_step_emergency_description,
        icon = Icons.Filled.Call,
    ),
)

private val privacyPermissionItems = listOf(
    PrivacyPermissionItem(
        titleRes = R.string.permission_location_title,
        descriptionRes = R.string.permission_location_body,
        icon = Icons.Filled.LocationOn,
    ),
    PrivacyPermissionItem(
        titleRes = R.string.permission_phone_title,
        descriptionRes = R.string.permission_phone_body,
        icon = Icons.Filled.Call,
    ),
    PrivacyPermissionItem(
        titleRes = R.string.permission_sms_title,
        descriptionRes = R.string.permission_sms_body,
        icon = Icons.Filled.Sms,
    ),
    PrivacyPermissionItem(
        titleRes = R.string.permission_microphone_title,
        descriptionRes = R.string.permission_microphone_body,
        icon = Icons.Filled.Mic,
    ),
    PrivacyPermissionItem(
        titleRes = R.string.permission_camera_title,
        descriptionRes = R.string.permission_camera_body,
        icon = Icons.Filled.CameraAlt,
    ),
)

private val helpFaqItems = listOf(
    FaqItem(
        questionRes = R.string.faq_ai_doctor_question,
        answerRes = R.string.faq_ai_doctor_answer,
    ),
    FaqItem(
        questionRes = R.string.faq_contacts_question,
        answerRes = R.string.faq_contacts_answer,
    ),
    FaqItem(
        questionRes = R.string.faq_permissions_question,
        answerRes = R.string.faq_permissions_answer,
    ),
    FaqItem(
        questionRes = R.string.faq_reminder_question,
        answerRes = R.string.faq_reminder_answer,
    ),
)

private fun List<String>.previousOption(current: String): String {
    if (isEmpty()) {
        return current
    }
    val currentIndex = indexOf(current).takeIf { it >= 0 } ?: 0
    return this[(currentIndex - 1 + size) % size]
}

private fun List<String>.nextOption(current: String): String {
    if (isEmpty()) {
        return current
    }
    val currentIndex = indexOf(current).takeIf { it >= 0 } ?: 0
    return this[(currentIndex + 1) % size]
}

private fun List<SettingsManager.EmergencyContact>.replaceContact(
    index: Int,
    contact: SettingsManager.EmergencyContact,
): List<SettingsManager.EmergencyContact> {
    return mapIndexed { contactIndex, currentContact ->
        if (contactIndex == index) {
            contact
        } else {
            currentContact
        }
    }
}

internal object SettingsRoutes {
    const val MAIN = "settings_main"
    const val PROFILE = "settings_profile"
    const val EMERGENCY_CONTACTS = "settings_emergency_contacts"
    const val VOICE = "settings_voice"
    const val FONT_SIZE = "settings_font_size"
    const val HELP = "settings_help"
    const val PRIVACY_PERMISSIONS = "settings_privacy_permissions"
    const val ABOUT = "settings_about"
}
