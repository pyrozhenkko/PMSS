package com.example.lab1.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database (an abstraction over SQLite). A singleton: several instances
 * of the same database would compete for the file and miss each other's changes.
 */
@Database(entities = [SensorRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sensorRecordDao(): SensorRecordDao

    companion object {
        private const val DB_NAME = "safebox.db"

        // @Volatile — a write to INSTANCE is immediately visible to all threads
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                // second check inside the lock (double-checked locking)
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,     // not an Activity — that would leak memory
                    AppDatabase::class.java,
                    DB_NAME
                ).build().also { INSTANCE = it }
            }
    }
}
