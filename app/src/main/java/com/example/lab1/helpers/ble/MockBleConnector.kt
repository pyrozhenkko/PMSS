package com.example.lab1.helpers.ble

import android.util.Log
import com.example.lab1.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "SafeBox/MockBleConnector"

/** Typical status code of a failed connection in the real Android BLE stack. */
private const val GATT_ERROR = "GATT status 133"

/**
 * Mock implementation of a GATT client: delays, random failures and acknowledgements (ACK)
 * reproduce the behaviour of a real BLE device.
 */
class MockBleConnector(
    private val scope: CoroutineScope,
    private val random: Random = Random.Default
) : IBleConnector {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 32)
    override val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    private var device: BleDevice? = null
    private var notifyJob: Job? = null
    private var lockStatus = BleProtocol.LOCK_CLOSED   // state of the virtual lock itself
    private val battery = 87

    // The BLE stack runs only one GATT operation at a time — the Mutex forms a command queue
    private val gattMutex = Mutex()

    override suspend fun connect(device: BleDevice) {
        repeat(AppConstants.BLE_MAX_RETRIES) { attempt ->
            _state.value = ConnectionState.Connecting
            Log.d(TAG, "connectGatt(${device.address}), attempt ${attempt + 1}")
            try {
                withTimeout(AppConstants.BLE_CONNECT_TIMEOUT_MS.milliseconds) {
                    delay(AppConstants.BLE_CONNECT_DELAY_MS.milliseconds)
                    if (random.nextFloat() < AppConstants.BLE_FAILURE_RATE) throw IOException(GATT_ERROR)
                }
                this.device = device
                _state.value = ConnectionState.Connected
                Log.d(TAG, "Connected, services discovered: ${BleUuids.SERVICE}")
                startNotifications(device)
                return
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Connection timeout")
            } catch (e: IOException) {
                Log.w(TAG, "Connection failed: ${e.message}")
            }
            // Exponential backoff: 400, 800, 1600 ms — lets the air "calm down"
            delay((AppConstants.BLE_RETRY_BASE_DELAY_MS shl attempt).milliseconds)
        }
        _state.value = ConnectionState.Error("Failed to connect to ${device.name}")
    }

    override fun disconnect() {
        notifyJob?.cancel()
        notifyJob = null
        device = null
        _state.value = ConnectionState.Disconnected
        Log.d(TAG, "Disconnected, GATT closed")
    }

    override suspend fun sendData(data: String): Boolean = gattMutex.withLock {
        val target = device ?: return@withLock false
        val uuid = BleUuids.short(BleUuids.COMMAND)
        val ack = "${BleProtocol.ACK_PREFIX}$data"
        repeat(AppConstants.BLE_MAX_RETRIES) { attempt ->
            _state.value = ConnectionState.DataTransfer
            Log.d(TAG, "TX -> ${target.name} [$uuid]: $data")
            delay(AppConstants.BLE_WRITE_DELAY_MS)
            if (device == null) return@withLock false          // disconnected during the write
            if (random.nextFloat() >= AppConstants.BLE_FAILURE_RATE) {
                applyCommand(data)
                Log.d(TAG, "RX <- ${target.name} [$uuid]: $ack")
                _incoming.emit(ack)
                _state.value = ConnectionState.Connected
                return@withLock true
            }
            Log.w(TAG, "No ACK for $data, retry ${attempt + 1}/${AppConstants.BLE_MAX_RETRIES}")
            delay(AppConstants.BLE_RETRY_BASE_DELAY_MS shl attempt)
        }
        notifyJob?.cancel()
        device = null
        _state.value = ConnectionState.Error("Device did not acknowledge $data")
        false
    }

    /** The virtual lock changes its own state depending on the command. */
    private fun applyCommand(command: String) {
        lockStatus = when (command) {
            BleProtocol.CMD_ALARM_ON -> BleProtocol.LOCK_ALARM
            BleProtocol.CMD_UNLOCK -> BleProtocol.LOCK_OPEN
            BleProtocol.CMD_ALARM_OFF, BleProtocol.CMD_LOCK -> BleProtocol.LOCK_CLOSED
            else -> lockStatus          // PING does not change the state
        }
    }

    /** After the write to CCCD the device sends Notify periodically on its own. */
    private fun startNotifications(target: BleDevice) {
        Log.d(TAG, "Write CCCD [${BleUuids.short(BleUuids.CCCD)}] = ENABLE_NOTIFICATION")
        notifyJob?.cancel()
        notifyJob = scope.launch {
            while (isActive) {
                delay(AppConstants.BLE_NOTIFY_PERIOD_MS)
                val lux = if (lockStatus == BleProtocol.LOCK_CLOSED) 2 else 60
                Log.d(TAG, "NOTIFY <- ${target.name} [${BleUuids.short(BleUuids.STATUS)}]: $lockStatus")
                _incoming.emit("${BleProtocol.STATUS_PREFIX}$lockStatus${BleProtocol.BATTERY_PREFIX}$battery%")
                _incoming.emit("${BleProtocol.LUX_PREFIX}$lux")
            }
        }
    }
}
