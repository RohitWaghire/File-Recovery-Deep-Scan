package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.RecoveryViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.data.RecoveryRepository
import com.example.data.ScanHistory
import com.example.data.RecoveredFile
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import com.example.data.RecoveryDao

class FakeRecoveryDao : RecoveryDao {
    private val scanHistoryList = mutableListOf<ScanHistory>()
    private val recoveredFilesList = mutableListOf<RecoveredFile>()

    private val scanHistoryFlow = MutableStateFlow<List<ScanHistory>>(emptyList())
    private val recoveredFilesFlow = MutableStateFlow<List<RecoveredFile>>(emptyList())

    override fun getAllScanHistory(): Flow<List<ScanHistory>> = scanHistoryFlow
    override fun getAllRecoveredFiles(): Flow<List<RecoveredFile>> = recoveredFilesFlow

    override suspend fun insertScanHistory(history: ScanHistory): Long {
        val id = if (history.id == 0L) (scanHistoryList.size + 1).toLong() else history.id
        scanHistoryList.removeAll { it.id == id }
        scanHistoryList.add(history.copy(id = id))
        scanHistoryFlow.value = scanHistoryList.toList()
        return id
    }

    override suspend fun updateScanHistory(history: ScanHistory) {
        // Mirror Room @Update: replace the row with the matching primary key
        scanHistoryList.removeAll { it.id == history.id }
        scanHistoryList.add(history)
        scanHistoryFlow.value = scanHistoryList.toList()
    }

    override suspend fun insertRecoveredFile(file: RecoveredFile): Long {
        val id = if (file.id == 0L) (recoveredFilesList.size + 1).toLong() else file.id
        recoveredFilesList.removeAll { it.id == id }
        recoveredFilesList.add(file.copy(id = id))
        recoveredFilesFlow.value = recoveredFilesList.toList()
        return id
    }

    override suspend fun deleteRecoveredFile(id: Long) {
        recoveredFilesList.removeAll { it.id == id }
        recoveredFilesFlow.value = recoveredFilesList.toList()
    }

    override suspend fun getLatestScanHistory(): ScanHistory? {
        return scanHistoryList.maxByOrNull { it.timestamp }
    }

    override suspend fun clearScanHistory() {
        scanHistoryList.clear()
        scanHistoryFlow.value = emptyList()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // SDK 34 runs on Java 17; SDK 36 would require Java 21
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("File Recovery & Deep Scan", appName)
  }

  @Test
  fun `RecoveryViewModel getReadableSize computes correctly`() {
    val repository = RecoveryRepository(FakeRecoveryDao())
    val viewModel = RecoveryViewModel(repository)
    assertEquals("0 B", viewModel.getReadableSize(0))
    assertEquals("1.0 KB", viewModel.getReadableSize(1024))
    assertEquals("1.0 MB", viewModel.getReadableSize(1024 * 1024))
    assertEquals("1.0 GB", viewModel.getReadableSize(1024 * 1024 * 1024))
  }

  @Test
  fun `RecoveryViewModel startScan and cancelScan behaves correctly`() {
    val repository = RecoveryRepository(FakeRecoveryDao())
    val viewModel = RecoveryViewModel(repository)
    
    assertEquals(com.example.viewmodel.ScanState.IDLE, viewModel.scanState.value)
    
    // Simulate canceling a scan
    viewModel.cancelScan()
    assertEquals(com.example.viewmodel.ScanState.CANCELLED, viewModel.scanState.value)
    assertEquals("Scan cancelled by user.", viewModel.currentScanningPath.value)
  }
}
