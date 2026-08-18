package com.heartguard.viewmodel

import androidx.lifecycle.ViewModel
import com.heartguard.data.local.FraudDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class AntiFraudViewModel @Inject constructor(
    private val fraudDao: FraudDao,
) : ViewModel() {
    fun observeFraudRecordCount(): Flow<Int> = fraudDao.observeFraudRecordCount()

    fun observeFraudPassedRecordCount(): Flow<Int> = fraudDao.observeFraudPassedRecordCount()

    fun observeLatestFraudScenarioTypes(): Flow<List<String>> = fraudDao.observeLatestFraudScenarioTypes()
}
