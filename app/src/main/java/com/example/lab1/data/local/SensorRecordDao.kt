package com.example.lab1.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room verifies the SQL queries at compile time, not at run time. */
@Dao
interface SensorRecordDao {

    @Insert
    suspend fun insert(record: SensorRecord)

    /** The last N records in chronological order — for the chart. */
    @Query(
        "SELECT * FROM (SELECT * FROM $SENSOR_RECORDS_TABLE ORDER BY timestamp DESC LIMIT :n) " +
            "ORDER BY timestamp ASC"
    )
    fun observeLast(n: Int): Flow<List<SensorRecord>>

    /** Alarm log: the newest on top. */
    @Query("SELECT * FROM $SENSOR_RECORDS_TABLE WHERE actionTriggered = 1 ORDER BY timestamp DESC LIMIT :n")
    fun observeAlarms(n: Int): Flow<List<SensorRecord>>

    @Query("SELECT COUNT(*) FROM $SENSOR_RECORDS_TABLE")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM $SENSOR_RECORDS_TABLE WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
