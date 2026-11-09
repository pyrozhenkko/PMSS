package com.example.lab1.repository

import android.database.sqlite.SQLiteFullException
import android.util.Log
import com.example.lab1.AppConstants
import com.example.lab1.helpers.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "SafeBox/SensorRepository"

/**
 * The single place for the app data: the ViewModels do not know about Room, Retrofit or Firebase,
 * they only call these methods. One instance per app (AppContainer), so all screens see the same data.
 */
class SensorRepository(
    private val dao: SensorRecordDao,
    private val api: CloudApi,
    private val remote: RemoteControlSource,
    private val networkMonitor: NetworkMonitor,
    private val deviceId: String,
    private val namespace: String?,
    private val isConfigured: Boolean
) {
    private val uploadMutex = Mutex()     // one upload at a time: the order is kept and no row is sent twice

    // ---------- History (Room) ----------

    /** The Room Flow emits a new list after every INSERT by itself — the chart updates live. */
    val chartRecords: Flow<List<SensorRecord>> = dao.observeLast(AppConstants.CHART_POINTS)

    val alarmRecords: Flow<List<SensorRecord>> = dao.observeAlarms(AppConstants.ALARM_LOG_SIZE)

    val recordCount: Flow<Int> = dao.observeCount()

    /** Lab 7: the dashboard widgets — the last records and the size of the offline queue. */
    val recentRecords: Flow<List<SensorRecord>> = dao.observeRecent(AppConstants.DASHBOARD_RECENT_RECORDS)

    val pendingRecords: Flow<Int> = dao.observeUnsyncedCount()

    /** 1. locally first, 2. then to the cloud. Without the network the row stays with synced = false. */
    suspend fun save(reading: SensorReading): Result<Unit> = withContext(Dispatchers.IO) {
        val record = reading.toRecord()
        if (!insertSafely(record)) return@withContext uploadDirectly(record)
        Log.d(TAG, "INSERT $SENSOR_RECORDS_TABLE: ${reading.lux} lux, actionTriggered=${reading.actionTriggered}")
        syncPending()
    }

    /**
     * Lab 7: the phone has run out of space. A security event matters more than old history,
     * so the oldest rows that are already in the cloud are deleted and the insert is retried.
     */
    private suspend fun insertSafely(record: SensorRecord): Boolean = try {
        dao.insert(record)
        true
    } catch (e: SQLiteFullException) {
        val freed = dao.deleteOldestSynced(AppConstants.STORAGE_FULL_TRIM)
        Log.w(TAG, "Storage is full, deleted $freed oldest synced records")
        try {
            dao.insert(record)
            true
        } catch (e: SQLiteFullException) {
            Log.e(TAG, "Storage is still full, the reading is not saved locally")
            false
        }
    }

    /** The last resort: the record could not be stored, it goes to the cloud. */
    private suspend fun uploadDirectly(record: SensorRecord): Result<Unit> = runCatching {
        check(isConfigured && networkMonitor.isOnline.value) { "Storage is full and there is no network" }
        withTimeout(AppConstants.NETWORK_TIMEOUT_MS.milliseconds) {
            api.postReading(deviceId, record.toPayload(deviceId), namespace)
        }
        Unit
    }.onFailure { Log.e(TAG, "Reading lost: ${it.message}") }

    /** Sends everything that has not reached the cloud yet (also called when the network is back). */
    suspend fun syncPending(): Result<Unit> = withContext(Dispatchers.IO) {
        uploadMutex.withLock {
            runCatching {
                check(isConfigured) { "FIREBASE_DB_URL is not set in local.properties" }
                if (!networkMonitor.isOnline.value) throw IOException("No Internet")
                // Lab 7: after a long offline period the queue is drained in batches, not only the first one
                do {
                    val batch = dao.unsynced(AppConstants.PENDING_QUEUE_MAX)
                    batch.forEach { record -> upload(record) }
                } while (batch.size == AppConstants.PENDING_QUEUE_MAX)
            }.onFailure { Log.w(TAG, "Upload failed: ${it.message}") }
        }
    }

    private suspend fun upload(record: SensorRecord) {
        val data = record.toPayload(deviceId)
        withTimeout(AppConstants.NETWORK_TIMEOUT_MS.milliseconds) {
            val response = api.postReading(deviceId, data, namespace)
            api.putLatest(deviceId, data, namespace)
            Log.d(TAG, "POST readings -> ${response.name}: ${data.value} lux, ${data.state}")
        }
        dao.markSynced(record.id)      // marked only after the server has confirmed
    }

    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) { dao.countUnsynced() }

    /** Deletes records older than a day so the database does not grow without bound. */
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        val deleted = dao.deleteOlderThan(System.currentTimeMillis() - AppConstants.RECORD_RETENTION_MS)
        if (deleted > 0) Log.d(TAG, "Deleted outdated records: $deleted")
    }

    // ---------- Commands and events (Firebase SDK) ----------

    val isCloudControlAvailable: Boolean get() = remote.isAvailable

    fun commands(): Flow<ControlCommand> = remote.observe()

    fun logEvent(type: String, lux: Float, source: String) = remote.logEvent(type, lux, source)

    fun reportStatus(status: DeviceStatus) = remote.reportStatus(status)

    fun setArmed(armed: Boolean) = remote.setArmed(armed)
}
