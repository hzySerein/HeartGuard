package com.heartguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatEntity::class,
        FraudRecordEntity::class,
        MedicationRecordEntity::class,
        MedicationScheduleEntity::class,
        MedicationTakenLogEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "heart_guard.db",
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_1_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { database ->
                        INSTANCE = database
                    }
            }
        }

        fun getDatabase(context: Context): AppDatabase = getInstance(context)

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createFraudRecordsTable(db)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createMedicationsTableBeforeCreatedAt(db)
            }
        }

        private val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createFraudRecordsTable(db)
                createMedicationsTableBeforeCreatedAt(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE medications SET createdAt = ? WHERE createdAt = 0",
                    arrayOf(System.currentTimeMillis()),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val todayEpochDay = currentMedicationEpochDay()
                val nowMillis = System.currentTimeMillis()

                db.execSQL("ALTER TABLE medications RENAME TO medications_legacy")
                createStructuredMedicationTables(db)
                db.execSQL(
                    """
                    INSERT INTO medications (id, name, dosage, note, ringtoneUri, stockCount, createdAt)
                    SELECT id, name, dosage, note, ringtoneUri, stockCount, createdAt
                    FROM medications_legacy
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO medication_schedules (
                        id,
                        medicationId,
                        timesOfDay,
                        repeatType,
                        intervalDays,
                        startDateEpochDay,
                        enabled
                    )
                    SELECT
                        id,
                        id,
                        timesOfDay,
                        CASE
                            WHEN note LIKE '%重复：每天%' THEN 'DAILY'
                            WHEN note LIKE '%重复：隔天%' THEN 'EVERY_OTHER_DAY'
                            WHEN note LIKE '%重复：自定义%' THEN 'CUSTOM'
                            ELSE 'ONCE'
                        END,
                        CASE
                            WHEN note LIKE '%重复：隔天%' THEN 2
                            WHEN note LIKE '%重复：自定义%' THEN 3
                            ELSE 1
                        END,
                        CASE
                            WHEN createdAt > 0 THEN CAST(
                                julianday(date(createdAt / 1000, 'unixepoch', 'localtime')) -
                                julianday('1970-01-01') AS INTEGER
                            )
                            ELSE ?
                        END,
                        1
                    FROM medications_legacy
                    """.trimIndent(),
                    arrayOf(todayEpochDay),
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO medication_taken_logs (
                        scheduleId,
                        takenDateEpochDay,
                        takenAtMillis
                    )
                    SELECT id, ?, ?
                    FROM medications_legacy
                    WHERE isTaken = 1
                    """.trimIndent(),
                    arrayOf(todayEpochDay, nowMillis),
                )
                db.execSQL("DROP TABLE medications_legacy")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medication_taken_logs RENAME TO medication_taken_logs_legacy")
                createMedicationTakenLogsTable(db)
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO medication_taken_logs (
                        scheduleId,
                        takenDateEpochDay,
                        takenTime,
                        takenAtMillis
                    )
                    SELECT scheduleId, takenDateEpochDay, '', takenAtMillis
                    FROM medication_taken_logs_legacy
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE medication_taken_logs_legacy")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_taken_logs_takenDateEpochDay` ON `medication_taken_logs` (`takenDateEpochDay`)")
            }
        }

        private fun createFraudRecordsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `fraud_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scenarioType` TEXT NOT NULL, `passed` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)",
            )
        }

        private fun createMedicationsTableBeforeCreatedAt(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dosage` TEXT NOT NULL, `timesOfDay` TEXT NOT NULL, `note` TEXT NOT NULL, `isTaken` INTEGER NOT NULL, `ringtoneUri` TEXT, `stockCount` INTEGER NOT NULL)",
            )
        }

        private fun createStructuredMedicationTables(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dosage` TEXT NOT NULL, `note` TEXT NOT NULL, `ringtoneUri` TEXT, `stockCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL DEFAULT 0)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `medication_schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicationId` INTEGER NOT NULL, `timesOfDay` TEXT NOT NULL, `repeatType` TEXT NOT NULL, `intervalDays` INTEGER NOT NULL, `startDateEpochDay` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_schedules_medicationId` ON `medication_schedules` (`medicationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_schedules_repeatType_startDateEpochDay` ON `medication_schedules` (`repeatType`, `startDateEpochDay`)")
            createMedicationTakenLogsTable(db)
        }

        private fun createMedicationTakenLogsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `medication_taken_logs` (`scheduleId` INTEGER NOT NULL, `takenDateEpochDay` INTEGER NOT NULL, `takenTime` TEXT NOT NULL DEFAULT '', `takenAtMillis` INTEGER NOT NULL, PRIMARY KEY(`scheduleId`, `takenDateEpochDay`, `takenTime`), FOREIGN KEY(`scheduleId`) REFERENCES `medication_schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_taken_logs_takenDateEpochDay` ON `medication_taken_logs` (`takenDateEpochDay`)")
        }
    }
}
