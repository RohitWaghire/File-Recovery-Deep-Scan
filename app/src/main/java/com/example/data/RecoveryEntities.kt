package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanType: String, // "FULL", "PHOTOS", "VIDEOS", "AUDIO", "DOCUMENTS"
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val filesFound: Int,
    val filesRecovered: Int
)

@Entity(tableName = "recovered_files")
data class RecoveredFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileType: String, // "PHOTO", "VIDEO", "AUDIO", "DOCUMENT"
    val sizeBytes: Long,
    val originalPath: String,
    val recoveredPath: String,
    val timestamp: Long = System.currentTimeMillis()
)
