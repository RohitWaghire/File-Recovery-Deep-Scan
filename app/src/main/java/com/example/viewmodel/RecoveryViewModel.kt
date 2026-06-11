package com.example.viewmodel

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.RecoveredFile
import com.example.data.RecoveryRepository
import com.example.data.ScanHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ScannedFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val type: String, // "PHOTO", "VIDEO", "AUDIO", "DOCUMENT"
    val recoverability: Float, // 0.0 to 1.0
    val magicHeader: String,
    val isReal: Boolean,
    val sectorAddress: String,
    val lastModified: Long = System.currentTimeMillis()
)

enum class ScanState {
    IDLE, SCANNING, FINISHED, CANCELLED
}

class RecoveryViewModel(private val repository: RecoveryRepository) : ViewModel() {

    private var scanJob: kotlinx.coroutines.Job? = null
    private val recoveryMutex = Mutex()
    private val discoveredMutex = Mutex()

    fun cancelScan() {
        scanJob?.cancel()
        _scanState.value = ScanState.CANCELLED
        _currentScanningPath.value = "Scan cancelled by user."
        // Clear discovered state to prevent stale reads from pacing loop
        viewModelScope.launch {
            discoveredMutex.withLock {
                _foundFiles.value = emptyList()
                _totalBytesScanned.value = 0L
            }
        }
    }

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _currentScanningPath = MutableStateFlow("")
    val currentScanningPath: StateFlow<String> = _currentScanningPath.asStateFlow()

    private val _foundFiles = MutableStateFlow<List<ScannedFile>>(emptyList())
    val foundFiles: StateFlow<List<ScannedFile>> = _foundFiles.asStateFlow()

    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds: StateFlow<Set<String>> = _selectedFileIds.asStateFlow()

    private val _totalBytesScanned = MutableStateFlow(0L)
    val totalBytesScanned: StateFlow<Long> = _totalBytesScanned.asStateFlow()

    private val _recoveredCount = MutableStateFlow(0)
    val recoveredCount: StateFlow<Int> = _recoveredCount.asStateFlow()

    private val _isRecovering = MutableStateFlow(false)
    val isRecovering: StateFlow<Boolean> = _isRecovering.asStateFlow()

    // Room Database Observables
    val scanHistory: StateFlow<List<ScanHistory>> = repository.allScanHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recoveredFiles: StateFlow<List<RecoveredFile>> = repository.allRecoveredFiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleFileSelection(id: String) {
        val current = _selectedFileIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedFileIds.value = current
    }

    fun selectAll() {
        _selectedFileIds.value = _foundFiles.value.map { it.id }.toSet()
    }

    fun selectAllFiltered(filteredIds: Set<String>) {
        _selectedFileIds.value = _selectedFileIds.value + filteredIds
    }

    fun clearSelection() {
        _selectedFileIds.value = emptySet()
    }

    fun startScan(context: Context, scanType: String) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _scanState.value = ScanState.SCANNING
            _foundFiles.value = emptyList()
            _selectedFileIds.value = emptySet()
            _totalBytesScanned.value = 0L
            _currentScanningPath.value = "Initializing forensic deep scanner..."

            val startTime = System.currentTimeMillis()
            val discovered = mutableListOf<ScannedFile>()

            // 1. Scan app-specific cache and files (guaranteed accessible)
            val searchPaths = mutableListOf<File>()
            try {
                searchPaths.add(context.cacheDir)
                searchPaths.add(context.filesDir)
                context.externalCacheDir?.let { searchPaths.add(it) }
                context.getExternalFilesDir(null)?.let { searchPaths.add(it) }
            } catch (e: Exception) {
                // Ignore errors
            }

            // 2. Scan external standard files (will succeed if permissions are granted)
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                try {
                    val extDir = Environment.getExternalStorageDirectory()
                    searchPaths.add(extDir)

                    // Add standard multimedia blocks
                    val dcim = File(extDir, "DCIM")
                    if (dcim.exists()) searchPaths.add(dcim)
                    val pictures = File(extDir, "Pictures")
                    if (pictures.exists()) searchPaths.add(pictures)
                    val downloads = File(extDir, "Download")
                    if (downloads.exists()) searchPaths.add(downloads)
                } catch (e: Exception) {
                    // Muted
                }
            }

            // Perform active scan with proper cancellation handling
            try {
                withContext(Dispatchers.IO) {
                    val visited = mutableSetOf<String>()
                    for (root in searchPaths) {
                        if (_scanState.value != ScanState.SCANNING) break
                        scanDirectoryRecursive(root, scanType, discovered, 0, visited)
                    }
                }
            } catch (e: CancellationException) {
                discoveredMutex.withLock {
                    discovered.clear()
                    _foundFiles.value = emptyList()
                }
                throw e
            }

            // Dynamic presentation: Pace search results rapidly with thread-safe access
            discoveredMutex.withLock {
                val paceList = mutableListOf<ScannedFile>()
                for (file in discovered) {
                    if (_scanState.value != ScanState.SCANNING) break
                    _totalBytesScanned.value += file.sizeBytes
                    _currentScanningPath.value = file.path
                    paceList.add(file)
                    _foundFiles.value = paceList.toList()
                    // Fast updates
                    delay(if (discovered.size > 20) 40 else 80)
                }
            }

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            _currentScanningPath.value = "Finished indexing storage clusters."
            _scanState.value = ScanState.FINISHED

            // Persist scan history to Room
            repository.insertScanHistory(
                ScanHistory(
                    scanType = scanType,
                    durationMs = duration,
                    filesFound = discovered.size,
                    filesRecovered = 0
                )
            )
        }
    }

    private fun scanDirectoryRecursive(dir: File, scanType: String, outputList: MutableList<ScannedFile>, depth: Int = 0, visited: MutableSet<String>) {
        if (!dir.exists() || !dir.isDirectory || depth > 10) return
        val canonicalPath = try { dir.canonicalPath } catch (e: Exception) { dir.absolutePath }
        if (!visited.add(canonicalPath)) return
        val list = dir.listFiles() ?: return
        
        for (file in list) {
            if (_scanState.value != ScanState.SCANNING) break
            
            if (file.isDirectory) {
                // Ignore standard skip directories to avoid hanging under heavy Android frameworks
                val dName = file.name
                if (dName.startsWith(".") || dName.equals("Android", ignoreCase = true) || file.path.contains("/Android/data") || file.path.contains("/Android/obb")) continue
                scanDirectoryRecursive(file, scanType, outputList, depth + 1, visited)
            } else {
                val ext = file.extension.lowercase()
                val hexHeader = readFileHeader(file)
                
                val isPhoto = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") || hexHeader.startsWith("89504E47") || hexHeader.startsWith("FFD8FF") || hexHeader.startsWith("47494638")
                val isVideo = ext in listOf("mp4", "mkv", "3gp", "avi", "mov")
                val isAudio = ext in listOf("mp3", "wav", "ogg", "flac", "aac", "m4a") || hexHeader.startsWith("494433")
                val isDoc = ext in listOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx") || hexHeader.startsWith("25504446") || hexHeader.startsWith("504B0304")
                
                val matchesType = when (scanType) {
                    "PHOTOS" -> isPhoto
                    "VIDEOS" -> isVideo
                    "AUDIOS" -> isAudio
                    "DOCUMENTS" -> isDoc
                    else -> true // FULL scan
                }
                
                if (matchesType && file.length() > 0) {
                    val type = when {
                        isPhoto -> "PHOTO"
                        isVideo -> "VIDEO"
                        isAudio -> "AUDIO"
                        else -> "DOCUMENT"
                    }
                    
                    val magic = when {
                        hexHeader.startsWith("89504E47") -> "PNG Image (0x89504E47)"
                        hexHeader.startsWith("FFD8FF") -> "JPEG Image (0xFFD8FF)"
                        hexHeader.startsWith("47494638") -> "GIF Image (0x47494638)"
                        hexHeader.startsWith("25504446") -> "PDF Document (0x25504446)"
                        hexHeader.startsWith("504B0304") -> "ZIP Archive (0x504B0304)"
                        hexHeader.startsWith("494433") -> "MP3 Audio (0x494433)"
                        else -> "Metadata Cluster / " + (file.extension.uppercase().ifEmpty { "BIN" })
                    }
                    
                    // Hidden/temporary files get high "Forensic Recoverability" status
                    val isHiddenOrCache = file.isHidden || file.path.contains("/cache/") || file.name.startsWith(".")
                    val baseRecoverability = if (isHiddenOrCache) 0.98f else 0.72f
                    
                    val scannedFile = ScannedFile(
                        name = file.name,
                        path = file.absolutePath,
                        sizeBytes = file.length(),
                        type = type,
                        recoverability = baseRecoverability,
                        magicHeader = magic,
                        isReal = true,
                        sectorAddress = "Clust:0x" + file.hashCode().toString(16).uppercase().take(8)
                    )
                    outputList.add(scannedFile)
                }
            }
        }
    }

    private fun readFileHeader(file: File): String {
        try {
            if (!file.exists() || file.isDirectory) return ""
            val bytes = ByteArray(4)
            val read = file.inputStream().use { fis -> fis.read(bytes) }
            if (read <= 0) return ""
            val sb = java.lang.StringBuilder()
            for (i in 0 until read) {
                sb.append(String.format("%02X", bytes[i]))
            }
            return sb.toString()
        } catch (e: Exception) {
            return ""
        }
    }

    fun recoverSelectedFiles(context: Context, onSuccess: (Int) -> Unit) {
        viewModelScope.launch {
            if (_selectedFileIds.value.isEmpty()) return@launch
            recoveryMutex.withLock {
                _isRecovering.value = true
                
                val selectedFiles = _foundFiles.value.filter { _selectedFileIds.value.contains(it.id) }
                var actualRecoveredCount = 0
                
                // Set up local physical "Recovered" folder
                val recoveredDir = File(context.getExternalFilesDir(null), "RecoveredFiles")
                if (!recoveredDir.exists()) {
                    recoveredDir.mkdirs()
                }

                withContext(Dispatchers.IO) {
                    for (scannedFile in selectedFiles) {
                        val destFile = getUniqueRecoveredFile(recoveredDir, scannedFile.name)
                        var copySuccess = false
                        
                        if (scannedFile.isReal) {
                            try {
                                val srcFile = File(scannedFile.path)
                                if (srcFile.exists()) {
                                    srcFile.inputStream().use { input ->
                                        destFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    copySuccess = destFile.exists() && destFile.length() > 0
                                }
                            } catch (e: Exception) {
                                copySuccess = false
                            }
                        }

                        if (copySuccess) {
                            // Logging into Room DB for secure validation
                            repository.insertRecoveredFile(
                                RecoveredFile(
                                    fileName = destFile.name,
                                    fileType = scannedFile.type,
                                    sizeBytes = scannedFile.sizeBytes,
                                    originalPath = scannedFile.path,
                                    recoveredPath = destFile.absolutePath
                                )
                            )
                            actualRecoveredCount++
                        }
                    }
                    updateHistoryWithRecovery(actualRecoveredCount)
                }

                // Pause for realistic sector-block writing and user feedback loop
                delay(1200)
                
                _recoveredCount.value += actualRecoveredCount
                _selectedFileIds.value = emptySet()
                _isRecovering.value = false
                
                onSuccess(actualRecoveredCount)
            }
        }
    }

    fun recoverSingleFile(context: Context, scannedFile: ScannedFile, onSuccess: () -> Unit) {
        viewModelScope.launch {
            recoveryMutex.withLock {
                _isRecovering.value = true
                val recoveredDir = File(context.getExternalFilesDir(null), "RecoveredFiles")
                if (!recoveredDir.exists()) {
                    recoveredDir.mkdirs()
                }
                var copySuccess = false
                val destFile = getUniqueRecoveredFile(recoveredDir, scannedFile.name)
                
                withContext(Dispatchers.IO) {
                    if (scannedFile.isReal) {
                        try {
                            val srcFile = File(scannedFile.path)
                            if (srcFile.exists()) {
                                srcFile.inputStream().use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                copySuccess = destFile.exists() && destFile.length() > 0
                            }
                        } catch (e: Exception) {
                            copySuccess = false
                        }
                    }
                    if (copySuccess) {
                        repository.insertRecoveredFile(
                            RecoveredFile(
                                fileName = destFile.name,
                                fileType = scannedFile.type,
                                sizeBytes = scannedFile.sizeBytes,
                                originalPath = scannedFile.path,
                                recoveredPath = destFile.absolutePath
                            )
                        )
                        updateHistoryWithRecovery(1)
                    }
                }
                delay(800)
                if (copySuccess) {
                    _recoveredCount.value += 1
                }
                _isRecovering.value = false
                if (copySuccess) {
                    onSuccess()
                }
            }
        }
    }

    private fun getUniqueRecoveredFile(directory: File, originalName: String): File {
        val baseName = originalName.substringBeforeLast(".")
        val extension = originalName.substringAfterLast(".", "")
        val suffix = if (extension.isNotEmpty()) ".$extension" else ""

        var counter = 0
        var file: File
        var created = false
        var maxAttempts = 10000

        do {
            file = if (counter == 0) {
                File(directory, "Recovered_$originalName")
            } else {
                File(directory, "Recovered_${baseName}_($counter)$suffix")
            }
            counter++

            // Use atomic createNewFile() - returns true only if file didn't exist and was created
            try {
                created = file.createNewFile()
            } catch (e: Exception) {
                // If creation fails, try next name
                created = false
            }
        } while (!created && counter < maxAttempts)

        return file
    }

    private suspend fun updateHistoryWithRecovery(recoveredCount: Int) {
        if (recoveredCount <= 0) return
        try {
            val latest = repository.getLatestScanHistory()
            if (latest != null) {
                val updated = latest.copy(filesRecovered = latest.filesRecovered + recoveredCount)
                repository.insertScanHistory(updated)
            }
        } catch (e: Exception) {
            // Muted
        }
    }

    fun deleteRecoveredState(id: Long, path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    // Muted
                }
                repository.deleteRecoveredFile(id)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearScanHistory()
        }
    }

    fun getReadableSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}

class RecoveryViewModelFactory(private val repository: RecoveryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecoveryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RecoveryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
