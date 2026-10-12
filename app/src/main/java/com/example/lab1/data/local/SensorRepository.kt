package com.example.lab1.data.local

import android.util.Log
import com.example.lab1.core.AppConstants
import kotlinx.coroutines.flow.Flow

private const val TAG = "SafeBox/SensorRepository"

/**
 * Repository layer of MVVM: the ViewModel knows nothing about Room and SQL,
 * it works only with these methods and Flows.
 */
class SensorRepository(private val dao: SensorRecordDao) {

    /** The Room Flow emits a new list after every INSERT by itself — the chart updates live. */
    val chartRecords: Flow<List<SensorRecord>> = dao.observeLast(AppConstants.CHART_POINTS)

    val alarmRecords: Flow<List<SensorRecord>> = dao.observeAlarms(AppConstants.ALARM_LOG_SIZE)

    val recordCount: Flow<Int> = dao.observeCount()

    suspend fun save(lux: Float, actionTriggered: Boolean) {
        dao.insert(
            SensorRecord(
                timestamp = System.currentTimeMillis(),
                sensorValue = lux,
                actionTriggered = actionTriggered
            )
        )
        Log.d(TAG, "INSERT $SENSOR_RECORDS_TABLE: $lux lux, actionTriggered=$actionTriggered")
    }

    /** Deletes records older than a day so the database does not grow without bound. */
    suspend fun cleanup() {
        val deleted = dao.deleteOlderThan(System.currentTimeMillis() - AppConstants.RECORD_RETENTION_MS)
        if (deleted > 0) Log.d(TAG, "Deleted outdated records: $deleted")
    }
}
