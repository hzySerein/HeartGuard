package com.heartguard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertChatMessage(message: ChatEntity)

    @Query(
        """
        SELECT * FROM chat_messages
        ORDER BY timestamp DESC
        LIMIT 50
        """
    )
    abstract suspend fun getRecentChatMessages(): List<ChatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFraudRecord(record: FraudRecordEntity)

    @Query(
        """
        SELECT AVG(CASE WHEN passed THEN 1.0 ELSE 0.0 END)
        FROM fraud_records
        """
    )
    abstract suspend fun getFraudPassRate(): Double?

    @Query(
        """
        SELECT COUNT(*) FROM fraud_records
        WHERE timestamp >= :startTimeMillis
        """
    )
    abstract fun observeFraudRecordCountSince(startTimeMillis: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM fraud_records
        """
    )
    abstract fun observeFraudRecordCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM fraud_records
        WHERE passed = 1
        """
    )
    abstract fun observeFraudPassedRecordCount(): Flow<Int>

    @Query(
        """
        SELECT scenarioType FROM fraud_records
        ORDER BY timestamp DESC
        LIMIT 1
        """
    )
    abstract fun observeLatestFraudScenarioTypes(): Flow<List<String>>

    @Transaction
    open suspend fun insertMedication(medication: MedicationEntity): Long {
        val medicationId = insertMedicationRecord(medication.toRecordEntity())
        val scheduleId = insertMedicationSchedule(
            medication.toScheduleEntity(medicationId = medicationId),
        )
        if (medication.isTaken) {
            markMedicationTakenForDay(
                scheduleId = scheduleId,
                takenDateEpochDay = currentMedicationEpochDay(),
                takenAtMillis = System.currentTimeMillis(),
            )
        }
        return medicationId
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMedicationRecord(medication: MedicationRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMedicationSchedule(schedule: MedicationScheduleEntity): Long

    @Query(
        """
        SELECT
            m.id AS id,
            s.id AS scheduleId,
            m.name AS name,
            m.dosage AS dosage,
            s.timesOfDay AS timesOfDay,
            m.note AS note,
            CASE WHEN (SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime = '') > 0
                 OR COALESCE((SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime != ''), 0)
                    >= (LENGTH(s.timesOfDay) - LENGTH(REPLACE(s.timesOfDay, ',', '')) + 1)
                 THEN 1 ELSE 0 END AS isTaken,
            m.ringtoneUri AS ringtoneUri,
            m.stockCount AS stockCount,
            m.createdAt AS createdAt,
            s.repeatType AS repeatType,
            s.intervalDays AS repeatIntervalDays,
            s.startDateEpochDay AS startDateEpochDay,
            s.enabled AS reminderEnabled,
            COALESCE((SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime != ''), 0) AS takenCount,
            (LENGTH(s.timesOfDay) - LENGTH(REPLACE(s.timesOfDay, ',', '')) + 1) AS totalTimes
        FROM medications AS m
        INNER JOIN medication_schedules AS s ON s.medicationId = m.id
        WHERE m.createdAt < :endOfDay
            AND :targetEpochDay >= s.startDateEpochDay
            AND (
                s.repeatType = 'DAILY'
                OR (
                    s.repeatType = 'EVERY_OTHER_DAY'
                    AND ((:targetEpochDay - s.startDateEpochDay) % 2) = 0
                )
                OR (
                    s.repeatType = 'CUSTOM'
                    AND ((:targetEpochDay - s.startDateEpochDay) % CASE WHEN s.intervalDays < 2 THEN 3 WHEN s.intervalDays > 30 THEN 30 ELSE s.intervalDays END) = 0
                )
                OR (
                    s.repeatType = 'ONCE'
                    AND (
                        s.startDateEpochDay = :targetEpochDay
                        OR m.createdAt >= :startOfDay
                    )
                )
            )
        ORDER BY s.timesOfDay ASC, m.id ASC
        """
    )
    abstract fun observeTodayMedications(
        startOfDay: Long,
        endOfDay: Long,
        targetEpochDay: Long,
    ): Flow<List<MedicationEntity>>

    @Query(
        """
        SELECT
            m.id AS id,
            s.id AS scheduleId,
            m.name AS name,
            m.dosage AS dosage,
            s.timesOfDay AS timesOfDay,
            m.note AS note,
            CASE WHEN (SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime = '') > 0
                 OR COALESCE((SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime != ''), 0)
                    >= (LENGTH(s.timesOfDay) - LENGTH(REPLACE(s.timesOfDay, ',', '')) + 1)
                 THEN 1 ELSE 0 END AS isTaken,
            m.ringtoneUri AS ringtoneUri,
            m.stockCount AS stockCount,
            m.createdAt AS createdAt,
            s.repeatType AS repeatType,
            s.intervalDays AS repeatIntervalDays,
            s.startDateEpochDay AS startDateEpochDay,
            s.enabled AS reminderEnabled,
            COALESCE((SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime != ''), 0) AS takenCount,
            (LENGTH(s.timesOfDay) - LENGTH(REPLACE(s.timesOfDay, ',', '')) + 1) AS totalTimes
        FROM medications AS m
        INNER JOIN medication_schedules AS s ON s.medicationId = m.id
        WHERE s.enabled = 1
            AND m.createdAt < :endOfDay
            AND :targetEpochDay >= s.startDateEpochDay
            AND (
                s.repeatType = 'DAILY'
                OR (
                    s.repeatType = 'EVERY_OTHER_DAY'
                    AND ((:targetEpochDay - s.startDateEpochDay) % 2) = 0
                )
                OR (
                    s.repeatType = 'CUSTOM'
                    AND ((:targetEpochDay - s.startDateEpochDay) % CASE WHEN s.intervalDays < 2 THEN 3 WHEN s.intervalDays > 30 THEN 30 ELSE s.intervalDays END) = 0
                )
                OR (
                    s.repeatType = 'ONCE'
                    AND (
                        s.startDateEpochDay = :targetEpochDay
                        OR m.createdAt >= :startOfDay
                    )
                )
            )
        ORDER BY s.timesOfDay ASC, m.id ASC
        """
    )
    abstract suspend fun getTodayMedications(
        startOfDay: Long,
        endOfDay: Long,
        targetEpochDay: Long,
    ): List<MedicationEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM medications AS m
        INNER JOIN medication_schedules AS s ON s.medicationId = m.id
        WHERE s.enabled = 1
            AND m.createdAt < :endOfDay
            AND :targetEpochDay >= s.startDateEpochDay
            AND NOT EXISTS (
                SELECT 1 FROM medication_taken_logs
                WHERE scheduleId = s.id
                    AND takenDateEpochDay = :targetEpochDay
                    AND takenTime = ''
            )
            AND COALESCE((SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime != ''), 0)
                < (LENGTH(s.timesOfDay) - LENGTH(REPLACE(s.timesOfDay, ',', '')) + 1)
            AND (
                s.repeatType = 'DAILY'
                OR (
                    s.repeatType = 'EVERY_OTHER_DAY'
                    AND ((:targetEpochDay - s.startDateEpochDay) % 2) = 0
                )
                OR (
                    s.repeatType = 'CUSTOM'
                    AND ((:targetEpochDay - s.startDateEpochDay) % CASE WHEN s.intervalDays < 2 THEN 3 WHEN s.intervalDays > 30 THEN 30 ELSE s.intervalDays END) = 0
                )
                OR (
                    s.repeatType = 'ONCE'
                    AND (
                        s.startDateEpochDay = :targetEpochDay
                        OR m.createdAt >= :startOfDay
                    )
                )
            )
        """
    )
    abstract suspend fun getTodayPendingMedicationCount(
        startOfDay: Long,
        endOfDay: Long,
        targetEpochDay: Long,
    ): Int

    @Query(
        """
        SELECT
            m.id AS id,
            s.id AS scheduleId,
            m.name AS name,
            m.dosage AS dosage,
            s.timesOfDay AS timesOfDay,
            m.note AS note,
            CASE WHEN (SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime = '') > 0
                 OR COALESCE((SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime != ''), 0)
                    >= (LENGTH(s.timesOfDay) - LENGTH(REPLACE(s.timesOfDay, ',', '')) + 1)
                 THEN 1 ELSE 0 END AS isTaken,
            m.ringtoneUri AS ringtoneUri,
            m.stockCount AS stockCount,
            m.createdAt AS createdAt,
            s.repeatType AS repeatType,
            s.intervalDays AS repeatIntervalDays,
            s.startDateEpochDay AS startDateEpochDay,
            s.enabled AS reminderEnabled,
            COALESCE((SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime != ''), 0) AS takenCount,
            (LENGTH(s.timesOfDay) - LENGTH(REPLACE(s.timesOfDay, ',', '')) + 1) AS totalTimes
        FROM medications AS m
        INNER JOIN medication_schedules AS s ON s.medicationId = m.id
        WHERE s.enabled = 1
        ORDER BY m.id ASC, s.id ASC
        """
    )
    abstract suspend fun getAllScheduledMedicationsForReminder(
        targetEpochDay: Long,
    ): List<MedicationEntity>

    @Transaction
    open suspend fun updateMedicationTakenStatus(id: Long, isTaken: Boolean) {
        val scheduleId = getPrimaryScheduleIdForMedication(id) ?: return
        setMedicationTakenForDay(
            scheduleId = scheduleId,
            takenDateEpochDay = currentMedicationEpochDay(),
            isTaken = isTaken,
        )
    }

    @Transaction
    open suspend fun setMedicationTakenForDay(
        scheduleId: Long,
        takenDateEpochDay: Long,
        isTaken: Boolean,
    ) {
        if (isTaken) {
            markMedicationTakenForDay(
                scheduleId = scheduleId,
                takenDateEpochDay = takenDateEpochDay,
                takenAtMillis = System.currentTimeMillis(),
            )
        } else {
            clearMedicationTakenForDay(scheduleId, takenDateEpochDay)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun markMedicationTakenForDay(log: MedicationTakenLogEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMedicationTakenLog(log: MedicationTakenLogEntity): Long

    open suspend fun markMedicationTakenForDay(
        scheduleId: Long,
        takenDateEpochDay: Long,
        takenAtMillis: Long,
    ) {
        markMedicationTakenForDay(
            MedicationTakenLogEntity(
                scheduleId = scheduleId,
                takenDateEpochDay = takenDateEpochDay,
                takenAtMillis = takenAtMillis,
            ),
        )
    }

    @Query(
        """
        DELETE FROM medication_taken_logs
        WHERE scheduleId = :scheduleId
            AND takenDateEpochDay = :takenDateEpochDay
            AND takenTime = ''
        """
    )
    abstract suspend fun clearMedicationTakenForDay(
        scheduleId: Long,
        takenDateEpochDay: Long,
    )

    open suspend fun markMedicationTakenForTime(
        scheduleId: Long,
        takenDateEpochDay: Long,
        takenTime: String,
        takenAtMillis: Long,
    ): Boolean {
        val result = insertMedicationTakenLog(
            MedicationTakenLogEntity(
                scheduleId = scheduleId,
                takenDateEpochDay = takenDateEpochDay,
                takenTime = takenTime,
                takenAtMillis = takenAtMillis,
            ),
        )
        return result != -1L
    }

    @Query(
        """
        SELECT takenTime FROM medication_taken_logs
        WHERE scheduleId = :scheduleId
            AND takenDateEpochDay = :takenDateEpochDay
        """
    )
    abstract suspend fun getTakenTimesForDay(
        scheduleId: Long,
        takenDateEpochDay: Long,
    ): List<String>

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM medication_schedules AS s
        INNER JOIN medication_taken_logs AS l
            ON l.scheduleId = s.id
            AND l.takenDateEpochDay = :takenDateEpochDay
            AND (l.takenTime = :takenTime OR l.takenTime = '')
        WHERE s.medicationId = :medicationId
        """
    )
    abstract suspend fun isMedicationTakenAtTime(
        medicationId: Long,
        takenDateEpochDay: Long,
        takenTime: String,
    ): Boolean

    @Transaction
    open suspend fun updateMedication(medication: MedicationEntity) {
        updateMedicationRecord(medication.toRecordEntity())
        val currentSchedule = getPrimaryScheduleForMedication(medication.id)
        val nextSchedule = medication.toScheduleEntity(
            medicationId = medication.id,
        )
        val repeatRuleChanged = currentSchedule != null &&
            (
                currentSchedule.repeatType != nextSchedule.repeatType ||
                    currentSchedule.intervalDays != nextSchedule.intervalDays
            )
        val schedule = nextSchedule.copy(
            id = medication.scheduleId.takeIf { it > 0L }
                ?: currentSchedule?.id
                ?: 0L,
            startDateEpochDay = if (repeatRuleChanged) {
                currentMedicationEpochDay()
            } else {
                currentSchedule?.startDateEpochDay ?: medication.startDateEpochDay
            },
        )
        insertMedicationSchedule(schedule)
        val scheduleId = schedule.id.takeIf { it > 0L } ?: getPrimaryScheduleIdForMedication(medication.id)
        if (scheduleId != null) {
            setMedicationTakenForDay(
                scheduleId = scheduleId,
                takenDateEpochDay = currentMedicationEpochDay(),
                isTaken = medication.isTaken,
            )
        }
    }

    @Update
    abstract suspend fun updateMedicationRecord(medication: MedicationRecordEntity)

    @Query(
        """
        SELECT * FROM medication_schedules
        WHERE medicationId = :medicationId
        ORDER BY id ASC
        LIMIT 1
        """
    )
    abstract suspend fun getPrimaryScheduleForMedication(medicationId: Long): MedicationScheduleEntity?

    @Query(
        """
        SELECT id FROM medication_schedules
        WHERE medicationId = :medicationId
        ORDER BY id ASC
        LIMIT 1
        """
    )
    abstract suspend fun getPrimaryScheduleIdForMedication(medicationId: Long): Long?

    open suspend fun getMedicationById(id: Long): MedicationEntity? {
        return getMedicationByIdForDay(
            id = id,
            targetEpochDay = currentMedicationEpochDay(),
        )
    }

    @Query(
        """
        SELECT
            m.id AS id,
            s.id AS scheduleId,
            m.name AS name,
            m.dosage AS dosage,
            s.timesOfDay AS timesOfDay,
            m.note AS note,
            CASE WHEN (SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime = '') > 0
                 OR COALESCE((SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime != ''), 0)
                    >= (LENGTH(s.timesOfDay) - LENGTH(REPLACE(s.timesOfDay, ',', '')) + 1)
                 THEN 1 ELSE 0 END AS isTaken,
            m.ringtoneUri AS ringtoneUri,
            m.stockCount AS stockCount,
            m.createdAt AS createdAt,
            s.repeatType AS repeatType,
            s.intervalDays AS repeatIntervalDays,
            s.startDateEpochDay AS startDateEpochDay,
            s.enabled AS reminderEnabled,
            COALESCE((SELECT COUNT(*) FROM medication_taken_logs WHERE scheduleId = s.id AND takenDateEpochDay = :targetEpochDay AND takenTime != ''), 0) AS takenCount,
            (LENGTH(s.timesOfDay) - LENGTH(REPLACE(s.timesOfDay, ',', '')) + 1) AS totalTimes
        FROM medications AS m
        INNER JOIN medication_schedules AS s ON s.medicationId = m.id
        WHERE m.id = :id
        ORDER BY s.id ASC
        LIMIT 1
        """
    )
    abstract suspend fun getMedicationByIdForDay(
        id: Long,
        targetEpochDay: Long,
    ): MedicationEntity?

    @Transaction
    open suspend fun deleteMedication(id: Long) {
        deleteMedicationRecord(id)
    }

    @Query(
        """
        DELETE FROM medications
        WHERE id = :id
        """
    )
    abstract suspend fun deleteMedicationRecord(id: Long)
}
