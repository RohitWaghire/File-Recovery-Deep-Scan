package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.RecoveredFile
import com.example.data.ScanHistory
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ScanState
import com.example.viewmodel.ScannedFile

// ---------------------------------------------------------------------------
// Design-time previews. Open this file in Android Studio and use the Split or
// Design view to see each screen rendered without running the app.
// ---------------------------------------------------------------------------

private fun readableSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val group = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return String.format("%.1f %s", bytes / Math.pow(1024.0, group.toDouble()), units[group])
}

private val sampleFiles = listOf(
    ScannedFile(
        name = "IMG_20260514_184233.jpg",
        path = "/storage/emulated/0/DCIM/Camera/IMG_20260514_184233.jpg",
        sizeBytes = 4_318_208,
        type = "PHOTO",
        recoverability = 0.98f,
        magicHeader = "JPEG Image (0xFFD8FF)",
        isReal = true,
        sectorAddress = "Clust:0x4A3F91C2"
    ),
    ScannedFile(
        name = "VID_birthday_party.mp4",
        path = "/storage/emulated/0/DCIM/Camera/VID_birthday_party.mp4",
        sizeBytes = 182_452_224,
        type = "VIDEO",
        recoverability = 0.72f,
        magicHeader = "MP4 Container",
        isReal = true,
        sectorAddress = "Clust:0x7B12E04D"
    ),
    ScannedFile(
        name = "voice_memo_003.m4a",
        path = "/storage/emulated/0/Recordings/voice_memo_003.m4a",
        sizeBytes = 2_097_152,
        type = "AUDIO",
        recoverability = 0.98f,
        magicHeader = "AAC Audio (M4A)",
        isReal = true,
        sectorAddress = "Clust:0x1C88A3F0"
    ),
    ScannedFile(
        name = "tax_return_2025.pdf",
        path = "/storage/emulated/0/Download/tax_return_2025.pdf",
        sizeBytes = 812_044,
        type = "DOCUMENT",
        recoverability = 0.72f,
        magicHeader = "PDF Document (0x25504446)",
        isReal = true,
        sectorAddress = "Clust:0x9D54B7E1"
    )
)

private val sampleRecovered = listOf(
    RecoveredFile(
        id = 1,
        fileName = "Recovered_IMG_20260514_184233.jpg",
        fileType = "PHOTO",
        sizeBytes = 4_318_208,
        originalPath = "/storage/emulated/0/DCIM/Camera/IMG_20260514_184233.jpg",
        recoveredPath = "/storage/emulated/0/Android/data/app/files/RecoveredFiles/Recovered_IMG.jpg"
    ),
    RecoveredFile(
        id = 2,
        fileName = "Recovered_tax_return_2025.pdf",
        fileType = "DOCUMENT",
        sizeBytes = 812_044,
        originalPath = "/storage/emulated/0/Download/tax_return_2025.pdf",
        recoveredPath = "/storage/emulated/0/Android/data/app/files/RecoveredFiles/Recovered_tax.pdf"
    )
)

private val sampleHistory = listOf(
    ScanHistory(id = 1, scanType = "FULL", durationMs = 42_300, filesFound = 318, filesRecovered = 12),
    ScanHistory(id = 2, scanType = "PHOTOS", durationMs = 12_800, filesFound = 154, filesRecovered = 3)
)

@Preview(name = "Scanner - Idle (home)", showBackground = true, backgroundColor = 0xFF111318, widthDp = 412, heightDp = 915)
@Composable
fun PreviewScannerIdle() {
    MyApplicationTheme {
        ScannerScreen(
            scanState = ScanState.IDLE,
            scanningPath = "",
            foundFiles = emptyList(),
            selectedIds = emptySet(),
            totalBytesScanned = 0L,
            isRecovering = false,
            selectedFilter = "ALL",
            onFilterChange = {},
            onStartScan = {},
            onFileSelected = {},
            onSelectAll = {},
            onClearSelection = {},
            onRecover = {},
            onRecoverSingle = {},
            onCancelScan = {},
            getReadableSize = ::readableSize
        )
    }
}

@Preview(name = "Scanner - Scanning", showBackground = true, backgroundColor = 0xFF111318, widthDp = 412, heightDp = 915)
@Composable
fun PreviewScannerScanning() {
    MyApplicationTheme {
        ScannerScreen(
            scanState = ScanState.SCANNING,
            scanningPath = "/storage/emulated/0/DCIM/Camera/IMG_20260301_092811.jpg",
            foundFiles = sampleFiles.take(2),
            selectedIds = emptySet(),
            totalBytesScanned = 186_770_432L,
            isRecovering = false,
            selectedFilter = "ALL",
            onFilterChange = {},
            onStartScan = {},
            onFileSelected = {},
            onSelectAll = {},
            onClearSelection = {},
            onRecover = {},
            onRecoverSingle = {},
            onCancelScan = {},
            getReadableSize = ::readableSize
        )
    }
}

@Preview(name = "Scanner - Results & selection", showBackground = true, backgroundColor = 0xFF111318, widthDp = 412, heightDp = 915)
@Composable
fun PreviewScannerResults() {
    MyApplicationTheme {
        ScannerScreen(
            scanState = ScanState.FINISHED,
            scanningPath = "Finished indexing storage clusters.",
            foundFiles = sampleFiles,
            selectedIds = setOf(sampleFiles[0].id, sampleFiles[3].id),
            totalBytesScanned = 189_679_628L,
            isRecovering = false,
            selectedFilter = "ALL",
            onFilterChange = {},
            onStartScan = {},
            onFileSelected = {},
            onSelectAll = {},
            onClearSelection = {},
            onRecover = {},
            onRecoverSingle = {},
            onCancelScan = {},
            getReadableSize = ::readableSize
        )
    }
}

@Preview(name = "Vault - Recovered files & history", showBackground = true, backgroundColor = 0xFF111318, widthDp = 412, heightDp = 915)
@Composable
fun PreviewVault() {
    MyApplicationTheme {
        VaultScreen(
            recoveredFilesLog = sampleRecovered,
            scanHistory = sampleHistory,
            onShareFile = {},
            onDeleteFile = { _, _ -> },
            onClearHistory = {},
            getReadableSize = ::readableSize
        )
    }
}

@Preview(name = "Vault - Empty state", showBackground = true, backgroundColor = 0xFF111318, widthDp = 412, heightDp = 600)
@Composable
fun PreviewEmptyVault() {
    MyApplicationTheme {
        EmptyVaultState()
    }
}

@Preview(name = "Guide screen", showBackground = true, backgroundColor = 0xFF111318, widthDp = 412, heightDp = 915)
@Composable
fun PreviewGuide() {
    MyApplicationTheme {
        GuideScreen()
    }
}

@Preview(name = "Component - File card", showBackground = true, backgroundColor = 0xFF111318, widthDp = 412)
@Composable
fun PreviewFileCard() {
    MyApplicationTheme {
        Column(
            modifier = Modifier
                .background(Color(0xFF111318))
                .padding(16.dp)
        ) {
            FileCard(
                scannedFile = sampleFiles[0],
                isSelected = true,
                onSelect = {},
                getReadableSize = ::readableSize,
                onRecoverSingle = {}
            )
        }
    }
}

@Preview(name = "Component - Scan button", showBackground = true, backgroundColor = 0xFF111318, widthDp = 220)
@Composable
fun PreviewScanButton() {
    MyApplicationTheme {
        Column(
            modifier = Modifier
                .background(Color(0xFF111318))
                .padding(16.dp)
        ) {
            ScanButton(
                label = "Photos",
                icon = Icons.Filled.Favorite,
                color = Color(0xFFA8C7FA),
                onClick = {}
            )
        }
    }
}
