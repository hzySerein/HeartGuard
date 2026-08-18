package com.heartguard.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val _fontSize = MutableStateFlow(DEFAULT_FONT_SIZE)
    val fontSize: StateFlow<String> = _fontSize.asStateFlow()

    private val _voiceSettings = MutableStateFlow(
        VoiceSettings(
            volume = DEFAULT_VOLUME,
            speed = DEFAULT_SPEED,
            tone = DEFAULT_TONE,
        )
    )
    val voiceSettings: StateFlow<VoiceSettings> = _voiceSettings.asStateFlow()

    private val _emergencyContacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContact>> = _emergencyContacts.asStateFlow()

    private val _userProfile = MutableStateFlow(
        UserProfile(
            nickname = DEFAULT_USER_NAME,
            avatarUri = "",
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    init {
        _fontSize.value = getFontSize()
        _voiceSettings.value = getVoiceSettings()
        refreshUserProfile()
        refreshEmergencyContacts()
    }

    fun getFontSize(): String {
        return normalizeFontSize(
            prefs.getString(KEY_FONT_SIZE, DEFAULT_FONT_SIZE),
        )
    }

    fun saveFontSize(fontSize: String) {
        val safeFontSize = normalizeFontSize(fontSize)
        prefs.edit()
            .putString(KEY_FONT_SIZE, safeFontSize)
            .apply()
        _fontSize.value = safeFontSize
    }

    fun getVolume(): String {
        return normalizeOption(
            value = prefs.getString(KEY_VOLUME, DEFAULT_VOLUME),
            options = VOLUME_OPTIONS,
            default = DEFAULT_VOLUME,
        )
    }

    fun getSpeed(): String {
        return normalizeOption(
            value = prefs.getString(KEY_SPEED, DEFAULT_SPEED),
            options = SPEED_OPTIONS,
            default = DEFAULT_SPEED,
        )
    }

    fun getTone(): String {
        return normalizeOption(
            value = prefs.getString(KEY_TONE, DEFAULT_TONE),
            options = TONE_OPTIONS,
            default = DEFAULT_TONE,
        )
    }

    fun getVoiceSettings(): VoiceSettings {
        return VoiceSettings(
            volume = getVolume(),
            speed = getSpeed(),
            tone = getTone(),
        )
    }

    fun saveVoiceSettings(
        volume: String,
        speed: String,
        tone: String,
    ) {
        val safeSettings = VoiceSettings(
            volume = normalizeOption(volume, VOLUME_OPTIONS, DEFAULT_VOLUME),
            speed = normalizeOption(speed, SPEED_OPTIONS, DEFAULT_SPEED),
            tone = normalizeOption(tone, TONE_OPTIONS, DEFAULT_TONE),
        )
        prefs.edit()
            .putString(KEY_VOLUME, safeSettings.volume)
            .putString(KEY_SPEED, safeSettings.speed)
            .putString(KEY_TONE, safeSettings.tone)
            .apply()
        _voiceSettings.value = safeSettings
    }

    fun getTtsVoiceSettings(): TtsVoiceSettings {
        return getVoiceSettings().toTtsVoiceSettings()
    }

    fun currentTtsVoiceSettings(): TtsVoiceSettings {
        return _voiceSettings.value.toTtsVoiceSettings()
    }

    fun getUserName(): String {
        return getUserProfile().nickname
    }

    fun getUserAvatarText(): String {
        return getUserProfile().avatarText
    }

    fun getUserAvatarUri(): String {
        return getUserProfile().avatarUri
    }

    fun getUserProfile(): UserProfile {
        val nickname = prefs
            .getString(KEY_USER_NICKNAME, null)
            ?: prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME)
        val safeNickname = nickname
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_USER_NAME
        val avatarUri = prefs
            .getString(KEY_USER_AVATAR_URI, null)
            ?.trim()
            .orEmpty()
        return UserProfile(
            nickname = safeNickname,
            avatarUri = avatarUri,
        )
    }

    fun saveUserName(userName: String) {
        val safeName = userName.trim().ifBlank { DEFAULT_USER_NAME }
        val savedAvatarUri = getUserProfile().avatarUri
        saveUserProfile(
            nickname = safeName,
            avatarUri = savedAvatarUri,
        )
    }

    fun saveUserProfile(
        nickname: String,
        avatarUri: String,
    ) {
        val safeNickname = nickname.trim().ifBlank { DEFAULT_USER_NAME }
        val safeAvatarUri = avatarUri.trim()
        prefs.edit()
            .putString(KEY_USER_NICKNAME, safeNickname)
            .putString(KEY_USER_NAME, safeNickname)
            .putString(KEY_USER_AVATAR_URI, safeAvatarUri)
            .remove(KEY_USER_AVATAR_TEXT)
            .apply()
        _userProfile.value = UserProfile(
            nickname = safeNickname,
            avatarUri = safeAvatarUri,
        )
    }

    fun getEmergencyContacts(): List<EmergencyContact> {
        return getConfiguredEmergencyContacts()
    }

    fun getConfiguredEmergencyContacts(): List<EmergencyContact> {
        return prefs
            .getString(KEY_EMERGENCY_CONTACTS, null)
            ?.toEmergencyContacts()
            .orEmpty()
            .sanitizeContacts()
    }

    fun hasSavedEmergencyContacts(): Boolean {
        return getConfiguredEmergencyContacts().isNotEmpty()
    }

    fun saveEmergencyContacts(contacts: List<EmergencyContact>) {
        val safeContacts = contacts.sanitizeContacts()
        val editor = prefs.edit()
        if (safeContacts.isEmpty()) {
            editor.remove(KEY_EMERGENCY_CONTACTS)
        } else {
            editor.putString(KEY_EMERGENCY_CONTACTS, json.encodeToString(safeContacts))
        }
        editor.apply()
        refreshEmergencyContacts()
    }

    private fun refreshEmergencyContacts() {
        val savedContacts = getConfiguredEmergencyContacts()
        _emergencyContacts.value = savedContacts
    }

    private fun refreshUserProfile() {
        _userProfile.value = getUserProfile()
    }

    private fun normalizeFontSize(value: String?): String {
        return normalizeOption(value, FONT_SIZE_OPTIONS, DEFAULT_FONT_SIZE)
    }

    private fun normalizeOption(
        value: String?,
        options: List<String>,
        default: String,
    ): String {
        return value?.takeIf { it in options } ?: default
    }

    private fun VoiceSettings.toTtsVoiceSettings(): TtsVoiceSettings {
        return TtsVoiceSettings(
            volume = when (volume) {
                "小" -> 0.7
                "大" -> 1.3
                else -> 1.0
            },
            speed = when (speed) {
                "慢" -> 0.75
                "快" -> 1.1
                else -> 0.9
            },
            pitch = when (tone) {
                "稳重" -> -2
                "亲切" -> 2
                else -> 0
            },
        )
    }

    private fun String.toEmergencyContacts(): List<EmergencyContact> {
        val rawValue = trim()
        if (rawValue.isBlank()) {
            return emptyList()
        }

        if (rawValue.startsWith("[")) {
            return runCatching {
                json.decodeFromString<List<EmergencyContact>>(rawValue)
            }.getOrElse {
                emptyList()
            }
        }

        return lines()
            .mapNotNull { row ->
                val parts = row.split(CONTACT_FIELD_SEPARATOR, limit = 2)
                val name = parts.getOrNull(0)?.trim().orEmpty()
                val phone = parts.getOrNull(1)?.trim().orEmpty()
                if (name.isBlank() || phone.isBlank()) {
                    null
                } else {
                    EmergencyContact(name = name, phone = phone)
                }
            }
    }

    private fun List<EmergencyContact>.sanitizeContacts(): List<EmergencyContact> {
        return mapNotNull { contact ->
            val safeName = contact.name.trim()
            val safePhone = contact.phone.trim()
            if (safeName.isBlank() || safePhone.isBlank()) {
                null
            } else {
                EmergencyContact(name = safeName, phone = safePhone)
            }
        }
    }

    data class VoiceSettings(
        val volume: String,
        val speed: String,
        val tone: String,
    )

    data class TtsVoiceSettings(
        val volume: Double,
        val speed: Double,
        val pitch: Int,
    )

    data class UserProfile(
        val nickname: String,
        val avatarUri: String,
    ) {
        val name: String
            get() = nickname

        val avatarText: String
            get() = nickname.trim().take(1).ifBlank { DEFAULT_USER_AVATAR_TEXT }
    }

    @Serializable
    data class EmergencyContact(
        val name: String,
        val phone: String,
    )

    companion object {
        private const val PREFS_NAME = "heart_guard_settings"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_VOLUME = "volume"
        private const val KEY_SPEED = "speed"
        private const val KEY_TONE = "tone"
        private const val KEY_USER_NICKNAME = "nickname"
        private const val KEY_USER_AVATAR_URI = "avatar_uri"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AVATAR_TEXT = "user_avatar_text"
        private const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"
        private const val CONTACT_FIELD_SEPARATOR = "|"

        const val DEFAULT_FONT_SIZE = "大"
        const val DEFAULT_VOLUME = "中"
        const val DEFAULT_SPEED = "中"
        const val DEFAULT_TONE = "亲切"
        const val DEFAULT_USER_NAME = "张阿姨"

        val FONT_SIZE_OPTIONS = listOf("小", "中", "大", "特大")
        val VOLUME_OPTIONS = listOf("小", "中", "大")
        val SPEED_OPTIONS = listOf("慢", "中", "快")
        val TONE_OPTIONS = listOf("稳重", "日常", "亲切")
        const val DEFAULT_USER_AVATAR_TEXT = "张"

        fun fontScale(fontSize: String): Float {
            return when (fontSize) {
                "小" -> 0.88f
                "中" -> 0.94f
                "特大" -> 1.16f
                else -> 1.0f
            }
        }
    }
}
