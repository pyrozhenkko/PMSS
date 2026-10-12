package com.example.lab1.data.remote

import android.util.Log
import com.example.lab1.core.AppConstants
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException

private const val TAG = "SafeBox/CloudRepository"

/**
 * Network logic: sends the readings to Firebase Realtime Database over REST.
 * If there is no network or the request failed, the record stays in the queue (Pending).
 */
class CloudRepository(
    private val api: CloudApi,
    private val networkMonitor: NetworkMonitor,
    private val namespace: String?,
    private val isConfigured: Boolean
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val pending = ArrayDeque<SensorData>()
    private val mutex = Mutex()     // requests go one by one, the order of records is preserved

    /** Retrofit suspend functions run the request on an OkHttp background thread by themselves. */
    suspend fun send(data: SensorData) = mutex.withLock {
        if (!isConfigured) {
            _status.value = SyncStatus.Error("FIREBASE_DB_URL is not set in local.properties")
            return@withLock
        }
        enqueue(data)
        if (networkMonitor.isOnline.value) uploadQueue() else markPending()
    }

    /** Called when the network is back: sends everything that has accumulated. */
    suspend fun flushPending() = mutex.withLock {
        if (isConfigured && pending.isNotEmpty()) uploadQueue()
    }

    fun markPending() {
        if (pending.isNotEmpty()) _status.value = SyncStatus.Pending(pending.size)
    }

    private fun enqueue(data: SensorData) {
        // The oldest records are evicted so the queue does not grow without bound
        if (pending.size >= AppConstants.PENDING_QUEUE_MAX) pending.removeFirst()
        pending.addLast(data)
    }

    private suspend fun uploadQueue() {
        _status.value = SyncStatus.Syncing
        try {
            while (pending.isNotEmpty()) {
                val data = pending.first()
                withTimeout(AppConstants.NETWORK_TIMEOUT_MS) {
                    val response = api.postReading(data.deviceId, data, namespace)
                    api.putLatest(data.deviceId, data, namespace)
                    Log.d(TAG, "POST readings -> ${response.name}: ${data.value} lux, ${data.state}")
                }
                pending.removeFirst()      // removed only after the server has confirmed
            }
            _status.value = SyncStatus.Synced(System.currentTimeMillis())
        } catch (e: TimeoutCancellationException) {
            fail("Request timeout")
        } catch (e: IOException) {
            fail("Network: ${e.message}")
        } catch (e: retrofit2.HttpException) {
            fail("HTTP ${e.code()}")
        }
    }

    private fun fail(message: String) {
        Log.w(TAG, "$message; queued ${pending.size}")
        _status.value = SyncStatus.Error("$message (queued ${pending.size})")
    }
}
