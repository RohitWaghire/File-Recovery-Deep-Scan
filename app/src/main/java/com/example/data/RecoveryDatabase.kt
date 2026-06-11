package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScanHistory::class, RecoveredFile::class], version = 1, exportSchema = false)
abstract class RecoveryDatabase : RoomDatabase() {
    abstract fun recoveryDao(): RecoveryDao

    companion object {
        @Volatile
        private var INSTANCE: RecoveryDatabase? = null

        fun getDatabase(context: Context): RecoveryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecoveryDatabase::class.java,
                    "recovery_database"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
