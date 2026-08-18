package com.heartguard.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

const val MEDICATION_REPEAT_ONCE = "ONCE"
const val MEDICATION_REPEAT_DAILY = "DAILY"
const val MEDICATION_REPEAT_EVERY_OTHER_DAY = "EVERY_OTHER_DAY"
const val MEDICATION_REPEAT_CUSTOM = "CUSTOM"
const val MEDICATION_CUSTOM_REPEAT_MIN_INTERVAL_DAYS = 2
const val MEDICATION_CUSTOM_REPEAT_DEFAULT_INTERVAL_DAYS = 3
const val MEDICATION_CUSTOM_REPEAT_MAX_INTERVAL_DAYS = 30

@Entity(tableName = "medications")
data class MedicationRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val dosage: String,
    val note: String,
    val ringtoneUri: String? = null,
    val stockCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "medication_schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicationRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["medicationId"]),
        Index(value = ["repeatType", "startDateEpochDay"]),
    ],
)
data class MedicationScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val medicationId: Long,
    val timesOfDay: String,
    val repeatType: String = MEDICATION_REPEAT_ONCE,
    val intervalDays: Int = 1,
    val startDateEpochDay: Long = currentMedicationEpochDay(),
    val enabled: Boolean = true,
)

@Entity(
    tableName = "medication_taken_logs",
    primaryKeys = ["scheduleId", "takenDateEpochDay", "takenTime"],
    foreignKeys = [
        ForeignKey(
            entity = MedicationScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["takenDateEpochDay"]),
    ],
)
data class MedicationTakenLogEntity(
    val scheduleId: Long,
    val takenDateEpochDay: Long,
    @ColumnInfo(defaultValue = "''")
    val takenTime: String = "",
    val takenAtMillis: Long = System.currentTimeMillis(),
)

data class MedicationEntity(
    val id: Long = 0L,
    val scheduleId: Long = 0L,
    val name: String,
    val dosage: String,
    val timesOfDay: String,
    val note: String,
    val isTaken: Boolean,
    val ringtoneUri: String? = null,
    val stockCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val repeatType: String = MEDICATION_REPEAT_ONCE,
    val repeatIntervalDays: Int = 1,
    val startDateEpochDay: Long = currentMedicationEpochDay(),
    val reminderEnabled: Boolean = true,
    val takenCount: Int = 0,
    val totalTimes: Int = 1,
)

data class MedicationTodayProgress(
    val medicationId: Long,
    val totalCount: Int,
    val takenCount: Int,
    val isAllTaken: Boolean,
    val isEnabled: Boolean,
    val nextUnTakenTime: String?,
    val todayTimes: List<String>,
    val takenTimes: Set<String> = emptySet(),
)

fun MedicationEntity.reminderTimes(): List<String> {
    return timesOfDay
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

fun MedicationEntity.toRecordEntity(): MedicationRecordEntity {
    return MedicationRecordEntity(
        id = id,
        name = name,
        dosage = dosage,
        note = note,
        ringtoneUri = ringtoneUri,
        stockCount = stockCount,
        createdAt = createdAt,
    )
}

fun MedicationEntity.toScheduleEntity(
    medicationId: Long = id,
): MedicationScheduleEntity {
    val noteRepeatType = repeatTypeFromMedicationNote(note)
    val sourceRepeatType = if (repeatType != MEDICATION_REPEAT_ONCE) {
        repeatType
    } else {
        noteRepeatType
    }
    val resolvedRepeatType = normalizeMedicationRepeatType(sourceRepeatType)
    val resolvedRepeatIntervalDays = when {
        repeatType != MEDICATION_REPEAT_ONCE -> normalizeMedicationRepeatIntervalDays(
            repeatType = resolvedRepeatType,
            intervalDays = repeatIntervalDays,
        )
        noteRepeatType != MEDICATION_REPEAT_ONCE -> repeatIntervalDaysFromMedicationNote(note)
        else -> normalizeMedicationRepeatIntervalDays(
            repeatType = resolvedRepeatType,
            intervalDays = repeatIntervalDays,
        )
    }

    return MedicationScheduleEntity(
        id = scheduleId,
        medicationId = medicationId,
        timesOfDay = timesOfDay,
        repeatType = resolvedRepeatType,
        intervalDays = resolvedRepeatIntervalDays,
        startDateEpochDay = startDateEpochDay,
        enabled = reminderEnabled,
    )
}

fun repeatTypeFromMedicationNote(note: String): String {
    return when {
        note.contains("重复：隔天") -> MEDICATION_REPEAT_EVERY_OTHER_DAY
        note.contains("重复：自定义") -> MEDICATION_REPEAT_CUSTOM
        note.contains("重复：每天") -> MEDICATION_REPEAT_DAILY
        else -> MEDICATION_REPEAT_ONCE
    }
}

fun repeatIntervalDaysFromMedicationNote(note: String): Int {
    return when (repeatTypeFromMedicationNote(note)) {
        MEDICATION_REPEAT_EVERY_OTHER_DAY -> 2
        MEDICATION_REPEAT_CUSTOM -> {
            val intervalDays = Regex("""间隔：\s*(\d+)\s*天""")
                .find(note)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: MEDICATION_CUSTOM_REPEAT_DEFAULT_INTERVAL_DAYS
            normalizeMedicationRepeatIntervalDays(
                repeatType = MEDICATION_REPEAT_CUSTOM,
                intervalDays = intervalDays,
            )
        }
        else -> 1
    }
}

fun normalizeMedicationRepeatType(repeatType: String): String {
    return when (repeatType) {
        MEDICATION_REPEAT_DAILY,
        MEDICATION_REPEAT_EVERY_OTHER_DAY,
        MEDICATION_REPEAT_CUSTOM,
        MEDICATION_REPEAT_ONCE -> repeatType

        else -> MEDICATION_REPEAT_ONCE
    }
}

fun normalizeMedicationRepeatIntervalDays(
    repeatType: String,
    intervalDays: Int,
): Int {
    return when (normalizeMedicationRepeatType(repeatType)) {
        MEDICATION_REPEAT_EVERY_OTHER_DAY -> 2
        MEDICATION_REPEAT_CUSTOM -> {
            if (intervalDays < MEDICATION_CUSTOM_REPEAT_MIN_INTERVAL_DAYS) {
                MEDICATION_CUSTOM_REPEAT_DEFAULT_INTERVAL_DAYS
            } else {
                intervalDays.coerceAtMost(MEDICATION_CUSTOM_REPEAT_MAX_INTERVAL_DAYS)
            }
        }
        else -> 1
    }
}

fun currentMedicationEpochDay(): Long = LocalDate.now().toEpochDay()
