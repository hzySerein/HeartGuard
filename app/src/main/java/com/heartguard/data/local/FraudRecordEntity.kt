package com.heartguard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fraud_records")
data class FraudRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scenarioType: String,
    val passed: Boolean,
    val timestamp: Long
)
