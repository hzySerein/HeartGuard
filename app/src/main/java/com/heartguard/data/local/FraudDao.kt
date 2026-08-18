package com.heartguard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FraudDao {
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
}
