package com.example.lab1.ui

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lab1.SafeBoxApp
import com.example.lab1.actuator.ActuatorState
import com.example.lab1.actuator.SmartActuator
import com.example.lab1.ble.BleDevice
import com.example.lab1.ble.BleProtocol
import com.example.lab1.ble.BleUuids
import com.example.lab1.ble.ConnectionState
import com.example.lab1.ble.IBleConnector
import com.example.lab1.ble.MockBleScanner
import com.example.lab1.core.AccessLogEntry
import com.example.lab1.core.AppConstants
import com.example.lab1.data.local.SensorRecord
import com.example.lab1.data.local.SensorRepository
import com.example.lab1.data.remote.CloudRepository
import com.example.lab1.data.remote.CloudSchema
import com.example.lab1.data.remote.ControlCommand
import com.example.lab1.data.remote.NetworkMonitor
import com.example.lab1.data.remote.RemoteControlSource
import com.example.lab1.data.remote.SensorData
import com.example.lab1.data.remote.SyncStatus
import com.example.lab1.domain.Decision
import com.example.lab1.domain.DecisionEngine
import com.example.lab1.domain.DecisionReason
import com.example.lab1.domain.SafeState
import com.example.lab1.ml.AccessClass
import com.example.lab1.ml.LightWindowBuffer
import com.example.lab1.ml.Prediction
import com.example.lab1.ml.SafeAccessClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val TAG = "SafeBox/SensorViewModel"
private const val OFFLINE_MESSAGE = "No Internet — data is queued (Pending)"

/** State of the "Monitor" tab. */
data class SafeBoxUiState(
    val lightLevel: Float = 0f,
    val isSafeBreached: Boolean = false,
    val sensorAvailable: Boolean = true,
    val accessLogs: List<AccessLogEntry> = emptyList()
)

/** State of the "BLE" tab. */
data class BleUiState(
    val devices: List<BleDevice> = emptyList(),
    val connection: ConnectionState = ConnectionState.Idle,
    val connectedDevice: BleDevice? = null
)

/** Who currently defines the state of the actuators. */
enum class CommandSource { AUTO, MANUAL, CLOUD }

/** State of the "Control" tab. */
data class ControlUiState(
    val armed: Boolean = true,
    val ledStatus: String = CloudSchema.LED_OFF,
    val source: CommandSource = CommandSource.AUTO,
    val reason: String = DecisionReason.CLOSED,
    val cloudControlAvailable: Boolean = false
)

/** State of the "AI Insights" block. */
data class AiUiState(
    val available: Boolean = false,
    val prediction: Prediction? = null,
    val recordIntervalMs: Long = AppConstants.RECORD_INTERVAL_MS,
    val lastEvent: String? = null
)

/**
 * The single source of application state: Composable functions only show the StateFlows
 * and call the ViewModel methods.
 */
class SensorViewModel(
    private val bleScanner: MockBleScanner,
    private val bleConnector: IBleConnector,
    private val cloudRepository: CloudRepository,
    private val networkMonitor: NetworkMonitor,
    private val remoteControl: RemoteControlSource,
    private val actuator: SmartActuator,
    private val sensorRepository: SensorRepository,
    private val classifier: SafeAccessClassifier?,
    val deviceId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(SafeBoxUiState())
    val uiState: StateFlow<SafeBoxUiState> = _uiState.asStateFlow()

    private val _terminalLog = MutableStateFlow<List<String>>(emptyList())
    val terminalLog: StateFlow<List<String>> = _terminalLog.asStateFlow()

    private val devices = MutableStateFlow<List<BleDevice>>(emptyList())
    private val scanning = MutableStateFlow(false)
    private val connectedDevice = MutableStateFlow<BleDevice?>(null)
    private var scanJob: Job? = null

    // Lab 3: cloud state and one-shot messages for the Snackbar
    val syncStatus: StateFlow<SyncStatus> = cloudRepository.status
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private var lastUploadAt = 0L
    private var lastUploadedLux = Float.NaN

    // Lab 4: decisions, actuators and commands
    private val decisionEngine = DecisionEngine()
    private var lastDecision = Decision()
    private var confirmJob: Job? = null
    private var manualAlarm: Boolean? = null        // null — automation is in control
    private var manualFlashlight: Boolean? = null
    private var cloudCommand = ControlCommand()
    private val _controlState = MutableStateFlow(ControlUiState(cloudControlAvailable = remoteControl.isAvailable))
    val controlState: StateFlow<ControlUiState> = _controlState.asStateFlow()
    val actuatorState: StateFlow<ActuatorState> = actuator.state

    // Lab 5: Room data for the "Analytics" screen (Flow → StateFlow)
    val chartRecords: StateFlow<List<SensorRecord>> = sensorRepository.chartRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val alarmRecords: StateFlow<List<SensorRecord>> = sensorRepository.alarmRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recordCount: StateFlow<Int> = sensorRepository.recordCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private var recordIntervalMs = AppConstants.RECORD_INTERVAL_MS
    private var lastRecordAt = 0L
    private var lastRecordedLux = Float.NaN
    private var sensorActive = false

    // Lab 6: classification of the light window by the TensorFlow Lite model
    private val windowBuffer = LightWindowBuffer()
    private val _aiState = MutableStateFlow(AiUiState(available = classifier != null))
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()
    private var mlAlertUntil = 0L
    private var lastAccessClass = AccessClass.CLOSED

    /** The scanner and the connector are different objects, so the overall BLE state is assembled here. */
    val bleState: StateFlow<BleUiState> =
        combine(devices, scanning, bleConnector.state, connectedDevice) { found, isScanning, state, device ->
            BleUiState(
                devices = found,
                connection = if (isScanning) ConnectionState.Scanning else state,
                connectedDevice = device
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BleUiState())

    init {
        // Notify from the device — into the terminal
        viewModelScope.launch {
            bleConnector.incoming.collect { appendTerminal("RX <- $it") }
        }
        viewModelScope.launch {
            bleConnector.state.collect { state ->
                appendTerminal("STATE: ${stateLabel(state)}")
                if (state is ConnectionState.Disconnected || state is ConnectionState.Error) {
                    connectedDevice.value = null
                }
            }
        }
        // Reaction to a network change: offline — a warning, online — flush the queue
        viewModelScope.launch {
            networkMonitor.isOnline.drop(1).collect { online ->
                if (online) {
                    cloudRepository.flushPending()
                } else {
                    cloudRepository.markPending()
                    _messages.emit(OFFLINE_MESSAGE)
                }
            }
        }
        // Cloud-to-Device: an event from Firebase changes the state at once, without polling
        viewModelScope.launch {
            remoteControl.observe().collect(::onCloudCommand)
        }
        // Lab 5: timer-based record (heartbeat), because TYPE_LIGHT is silent under constant light
        viewModelScope.launch {
            while (isActive) {
                delay(recordIntervalMs)
                val sinceLast = SystemClock.elapsedRealtime() - lastRecordAt
                if (sensorActive && sinceLast >= recordIntervalMs) {
                    saveRecord(_uiState.value.lightLevel, actionTriggered = false)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) { sensorRepository.cleanup() }
        if (classifier != null) startInference(classifier)
    }

    // ---------- On-device AI (Lab 6) ----------

    /**
     * TYPE_LIGHT sends events only when the value changes, while the model needs evenly spaced
     * samples. So a timer puts the last value into the ring buffer every 100 ms
     * (resampling), and every 500 ms the filled window is passed to the model.
     */
    private fun startInference(model: SafeAccessClassifier) {
        // Dispatchers.Default: inference is CPU-intensive work, not for the main thread
        viewModelScope.launch(Dispatchers.Default) {
            val samplesPerInference =
                (AppConstants.ML_INFERENCE_PERIOD_MS / AppConstants.ML_SAMPLE_PERIOD_MS).toInt()
            var samples = 0
            while (isActive) {
                delay(AppConstants.ML_SAMPLE_PERIOD_MS)
                if (!sensorActive) continue
                windowBuffer.add(_uiState.value.lightLevel)
                if (++samples % samplesPerInference != 0 || !windowBuffer.isFull) continue
                try {
                    val prediction = model.classify(windowBuffer.toModelInput())
                    withContext(Dispatchers.Main) { onPrediction(prediction) }
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Inference error", e)
                    _aiState.update { it.copy(available = false) }
                    return@launch
                }
            }
        }
    }

    /** Integration of the prediction with the previous labs (runs on the main thread). */
    private fun onPrediction(prediction: Prediction) {
        val accessClass = prediction.accessClass
        val confident = prediction.confidence >= AppConstants.ML_CONFIDENCE_MIN
        val threat = accessClass == AccessClass.OPENED || accessClass == AccessClass.TAMPERING

        // "Anomaly" → actuator (Lab 4): alarm only when the model confidence is ≥ 0.7.
        // The alarm is held for another 3 s after the last threat, otherwise the siren would "blink"
        // when the window is classified as FLICKER for a moment between flashes.
        if (threat && confident) {
            mlAlertUntil = SystemClock.elapsedRealtime() + AppConstants.ML_ALERT_HOLD_MS
        }
        // The event is ongoing → more frequent DB records (Lab 5): once per 1 s instead of 5 s
        recordIntervalMs = if (threat) AppConstants.RECORD_INTERVAL_FAST_MS else AppConstants.RECORD_INTERVAL_MS

        var event = _aiState.value.lastEvent
        if (accessClass != lastAccessClass) {
            lastAccessClass = accessClass
            val percent = (prediction.confidence * 100).toInt()
            Log.i(TAG, "AI: ${accessClass.name} ($percent%)")
            if (accessClass != AccessClass.CLOSED) {
                val time = SimpleDateFormat(AppConstants.TIME_FORMAT, Locale.getDefault()).format(Date())
                event = "$time ${accessClass.name}"
                // FLICKER — only a log record, no alarm (false-positive filter)
                remoteControl.logEvent(
                    CloudSchema.EVENT_AI_PREFIX + accessClass.name,
                    _uiState.value.lightLevel,
                    CloudSchema.SOURCE_AI
                )
            }
        }
        _aiState.update { it.copy(prediction = prediction, recordIntervalMs = recordIntervalMs, lastEvent = event) }
        evaluate()
    }

    // ---------- Sensor (Lab 1) ----------

    fun onSensorAvailability(available: Boolean) {
        _uiState.update { it.copy(sensorAvailable = available) }
    }

    /** The Activity reports whether it is listening to the sensor (ON_RESUME / ON_PAUSE). */
    fun onSensorListening(active: Boolean) {
        sensorActive = active
    }

    /** Called from the Activity on every onSensorChanged event. */
    fun onLightChanged(lux: Float) {
        _uiState.update { it.copy(lightLevel = lux) }
        evaluate()
        // A significant change (Δ ≥ 2 lux) is recorded at once, without waiting for the timer
        if (lastRecordedLux.isNaN() || abs(lux - lastRecordedLux) >= AppConstants.RECORD_DELTA_LUX) {
            saveRecord(lux, actionTriggered = false)
        }
    }

    // ---------- Local database (Lab 5) ----------

    private fun saveRecord(lux: Float, actionTriggered: Boolean) {
        lastRecordAt = SystemClock.elapsedRealtime()
        lastRecordedLux = lux
        // Dispatchers.IO: SQLite must not be accessed on the main thread
        viewModelScope.launch(Dispatchers.IO) {
            sensorRepository.save(lux, actionTriggered)
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(accessLogs = emptyList()) }
    }

    // ---------- Decisions and actuators (Lab 4) ----------

    /** Passes the current data to the DecisionEngine and applies its decision. */
    private fun evaluate() {
        val lux = _uiState.value.lightLevel
        var decision = decide(lux)
        // A manual "off" only silences the alarm that is active now. Once there is nothing left
        // to suppress, the switch returns to automation, so the next opening raises the alarm again.
        var released = false
        if (manualAlarm == false && !decision.autoAlarm && !cloudCommand.alarm) {
            manualAlarm = null
            released = true
        }
        if (manualFlashlight == false && !decision.autoAlarm && !cloudCommand.flashlight) {
            manualFlashlight = null
            released = true
        }
        if (released) decision = decide(lux)
        applyDecision(decision, lux)

        // TYPE_LIGHT sends events only when the value changes, so the opening confirmation
        // time (debounce) is counted with our own timer
        confirmJob?.cancel()
        if (decisionEngine.isOpenPending) {
            confirmJob = viewModelScope.launch {
                delay(AppConstants.OPEN_CONFIRM_MS)
                evaluate()
            }
        }
    }

    private fun decide(lux: Float): Decision = decisionEngine.evaluate(
        lux = lux,
        nowMs = SystemClock.elapsedRealtime(),
        armed = _controlState.value.armed,
        // priority: manual switch → cloud command → automation (null)
        manualAlarm = manualAlarm ?: true.takeIf { cloudCommand.alarm },
        manualFlashlight = manualFlashlight ?: true.takeIf { cloudCommand.flashlight },
        mlAlert = SystemClock.elapsedRealtime() < mlAlertUntil
    )

    private fun applyDecision(decision: Decision, lux: Float) {
        val previous = lastDecision
        lastDecision = decision
        val isOpen = decision.state == SafeState.OPEN
        val stateChanged = decision.state != previous.state
        val source = when {
            manualAlarm != null || manualFlashlight != null -> CommandSource.MANUAL
            cloudCommand.alarm || cloudCommand.flashlight -> CommandSource.CLOUD
            else -> CommandSource.AUTO
        }
        _uiState.update { it.copy(isSafeBreached = isOpen) }
        _controlState.update { it.copy(source = source, reason = decision.reason) }

        if (stateChanged && isOpen) {
            val time = SimpleDateFormat(AppConstants.TIME_FORMAT, Locale.getDefault()).format(Date())
            _uiState.update { it.copy(accessLogs = listOf(AccessLogEntry(time, lux)) + it.accessLogs) }
            Log.w(AppConstants.LOG_TAG, "UNAUTHORIZED ACCESS ATTEMPT at $time! Light level: $lux lux")
        }
        if (decision.alarm != previous.alarm) {
            if (decision.alarm) actuator.triggerAlarm() else actuator.stopAlarm()
            if (decision.alarm) saveRecord(lux, actionTriggered = true)   // the event goes to the local log
            val event = if (decision.alarm) CloudSchema.EVENT_ALARM_ON else CloudSchema.EVENT_ALARM_OFF
            remoteControl.logEvent(event, lux, source.name)       // alarm log in the cloud
            if (connectedDevice.value != null) {                  // and a command to the BLE hub (Lab 2)
                sendCommand(if (decision.alarm) BleProtocol.CMD_ALARM_ON else BleProtocol.CMD_ALARM_OFF)
            }
        }
        if (decision.flashlight != previous.flashlight) {
            actuator.toggleFlashlight(decision.flashlight)
        }
        uploadIfNeeded(lux, decision.state, stateChanged)
    }

    private fun onCloudCommand(command: ControlCommand) {
        cloudCommand = command
        _controlState.update { it.copy(armed = command.armed, ledStatus = command.ledStatus) }
        evaluate()
    }

    fun setArmed(armed: Boolean) {
        _controlState.update { it.copy(armed = armed) }
        remoteControl.setArmed(armed)      // so the cloud and the phone see the same mode
        evaluate()
    }

    fun setManualAlarm(enabled: Boolean) {
        manualAlarm = enabled
        evaluate()
    }

    fun setManualFlashlight(enabled: Boolean) {
        manualFlashlight = enabled
        evaluate()
    }

    /** "Reset alarm": removes the manual forced values and returns to AUTO mode. */
    fun resetAlarm() {
        manualAlarm = null
        manualFlashlight = null
        decisionEngine.acknowledge()
        evaluate()
    }

    // ---------- Cloud (Lab 3) ----------

    /**
     * Throttling: not every sensor event goes to the network, only if 5 s have passed,
     * or the value changed by Δ ≥ 5 lux, or the safe state changed (a security event does not wait).
     */
    private fun uploadIfNeeded(lux: Float, safeState: SafeState, stateChanged: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val intervalPassed = now - lastUploadAt >= AppConstants.UPLOAD_INTERVAL_MS
        val deltaExceeded = lastUploadedLux.isNaN() ||
            abs(lux - lastUploadedLux) >= AppConstants.UPLOAD_DELTA_LUX
        if (!intervalPassed && !deltaExceeded && !stateChanged) return
        lastUploadAt = now
        lastUploadedLux = lux

        val data = SensorData(
            deviceId = deviceId,
            value = lux,
            state = safeState.name
        )
        // viewModelScope: the request does not block the UI and is cancelled together with the ViewModel
        viewModelScope.launch {
            val wasOffline = !networkMonitor.isOnline.value
            cloudRepository.send(data)
            if (wasOffline) _messages.emit(OFFLINE_MESSAGE)
        }
    }

    // ---------- BLE (Lab 2) ----------

    fun startScan() {
        if (scanning.value) return
        devices.value = emptyList()
        scanJob = viewModelScope.launch {
            scanning.value = true
            appendTerminal("SCAN: searching for devices…")
            bleScanner.scan().collect { device ->
                devices.update { it + device }
                appendTerminal("FOUND: ${device.name} ${device.address} (${device.rssi} dBm)")
            }
            scanning.value = false
            appendTerminal("SCAN: finished, found ${devices.value.size}")
        }
    }

    fun connect(device: BleDevice) {
        viewModelScope.launch {
            appendTerminal("CONNECT -> ${device.name}")
            bleConnector.connect(device)
            if (bleConnector.state.value is ConnectionState.Connected) connectedDevice.value = device
        }
    }

    fun disconnect() = bleConnector.disconnect()

    fun sendCommand(command: String) {
        val target = connectedDevice.value ?: return
        viewModelScope.launch {
            appendTerminal("TX -> ${target.name} [${BleUuids.short(BleUuids.COMMAND)}]: $command")
            val delivered = bleConnector.sendData(command)
            if (!delivered) appendTerminal("ERR: $command not delivered")
        }
    }

    /**
     * A command pressed on the BLE tab. ALARM_ON / ALARM_OFF also drive the alarm of the phone,
     * so the button, the siren and the virtual lock stay consistent; LOCK / UNLOCK go to the cloud log.
     */
    fun onBleCommand(command: String) {
        val alarmBefore = lastDecision.alarm
        when (command) {
            BleProtocol.CMD_ALARM_ON -> setManualAlarm(true)
            BleProtocol.CMD_ALARM_OFF -> {
                resetAlarm()
                // a cloud command may still force the alarm — the manual switch has priority over it
                if (lastDecision.alarm) setManualAlarm(false)
            }
            BleProtocol.CMD_LOCK, BleProtocol.CMD_UNLOCK -> remoteControl.logEvent(
                CloudSchema.EVENT_BLE_PREFIX + command,
                _uiState.value.lightLevel,
                CommandSource.MANUAL.name
            )
        }
        // applyDecision has already sent the command to the hub if the alarm state changed
        if (lastDecision.alarm == alarmBefore) sendCommand(command)
    }

    fun onBlePermissionDenied() {
        appendTerminal("ERR: BLUETOOTH_SCAN / BLUETOOTH_CONNECT permissions are missing")
    }

    private fun appendTerminal(message: String) {
        val time = SimpleDateFormat(AppConstants.TIME_FORMAT_MILLIS, Locale.getDefault()).format(Date())
        Log.d(TAG, message)
        // takeLast bounds the memory and the number of lines the LazyColumn redraws
        _terminalLog.update { (it + "$time  $message").takeLast(AppConstants.TERMINAL_MAX_LINES) }
    }

    private fun stateLabel(state: ConnectionState): String = when (state) {
        is ConnectionState.Error -> "Error (${state.reason})"
        else -> state.toString()
    }

    override fun onCleared() {
        scanJob?.cancel()
        bleConnector.disconnect()
        actuator.shutdown()     // the siren and the flashlight must not stay on
        classifier?.close()     // frees the native memory of the TFLite interpreter
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SafeBoxApp
                val container = app.container
                SensorViewModel(
                    bleScanner = container.bleScanner,
                    bleConnector = container.bleConnector,
                    cloudRepository = container.cloudRepository,
                    networkMonitor = container.networkMonitor,
                    remoteControl = container.remoteControl,
                    actuator = container.smartActuator,
                    sensorRepository = container.sensorRepository,
                    // the model belongs to the ViewModel: created here and closed in onCleared()
                    classifier = SafeAccessClassifier.createOrNull(app),
                    deviceId = container.deviceIdProvider.deviceId
                )
            }
        }
    }
}
