package com.example.data

import kotlinx.coroutines.flow.Flow

class RecoveryRepository(private val recoveryDao: RecoveryDao) {

    val allScanHistory: Flow<List<ScanHistory>> = recoveryDao.getAllScanHistory()
    val allRecoveredFiles: Flow<List<RecoveredFile>> = recoveryDao.getAllRecoveredFiles()

    suspend fun insertScanHistory(history: ScanHistory): Long {
        return recoveryDao.insertScanHistory(history)
    }

    suspend fun insertRecoveredFile(file: RecoveredFile): Long {
        return recoveryDao.insertRecoveredFile(file)
    }

    suspend fun deleteRecoveredFile(id: Long) {
        recoveryDao.deleteRecoveredFile(id)
    }

    suspend fun getLatestScanHistory(): ScanHistory? {
        return recoveryDao.getLatestScanHistory()
    }

    suspend fun clearScanHistory() {
        recoveryDao.clearScanHistory()
    }
}
