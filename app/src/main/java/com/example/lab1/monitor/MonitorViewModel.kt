package com.example.lab1.monitor

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lab1.AppConstants
import com.example.lab1.SafeBoxApp
import com.example.lab1.helpers.AccessClass
import com.example.lab1.helpers.GuardState
import com.example.lab1.helpers.LightSensorReader
import com.example.lab1.helpers.LightWindowBuffer
import com.example.lab1.helpers.NetworkMonitor
import com.example.lab1.helpers.Prediction
import com.example.lab1.helpers.SafeAccessClassifier
import com.example.lab1.helpers.SafeGuard
import com.example.lab1.helpers.SafeState
import com.example.lab1.helpers.ble.IBleConnector
import com.example.lab1.helpers.describe
import com.example.lab1.repository.CloudSchema
import com.example.lab1.repository.SensorReading
import com.example.lab1.repository.SensorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val TAG = "SafeBox/MonitorViewModel"
private const val OFFLINE_MESSAGE = "No Internet — data is queued (Pending)"

/**
 * The main screen (Dashboard since Lab 7): the light sensor, the safe state, saving the readings,
 * the on-device AI, and the state of the other modules (Room, cloud, BLE, actuators) in one place.
 * The sensor is listened to only between the Start and Stop actions (onResume / onPause).
 */
class MonitorViewModel(
    private val sensorReader: LightSensorReader,
    private val repository: SensorRepository,
    private val networkMonitor: NetworkMonitor,
    private val guard: SafeGuard,
    private val classifier: SafeAccessClassifier?,
    bleConnector: IBleConnector,
    deviceId: String
) : ViewModel() {

    private val _state = MutableStateFlow(
        MonitorState(
            sensorAvailable = sensorReader.isAvailable,
            isOnline = networkMonitor.isOnline.value,
            deviceId = deviceId,
            ai = AiState(available = classifier != null)
        )
    )
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    // Channel delivers every event exactly once (it is not repeated after a rotation)
    private val _events = Channel<MonitorEvent>()
    val events = _events.receiveAsFlow()

    private var sensorJob: Job? = null
    private var lastGuard: GuardState? = null
    private var offlineNotified = false

    // Lab 5: when a reading is saved (Room, then the cloud)
    private var recordIntervalMs = AppConstants.RECORD_INTERVAL_MS
    private var lastRecordAt = 0L
    private var lastRecordedLux = Float.NaN
    private var lastRecordedState = SafeState.CLOSED

    // Lab 6: classification of the light window by the TensorFlow Lite model
    private val windowBuffer = LightWindowBuffer()
    private var lastAccessClass = AccessClass.CLOSED

    init {
        viewModelScope.launch { guard.state.collect(::onGuardChanged) }
        viewModelScope.launch { networkMonitor.isOnline.collect(::onNetworkChanged) }
        // Timer-based record (heartbeat), because TYPE_LIGHT is silent under constant light
        viewModelScope.launch {
            while (isActive) {
                delay(recordIntervalMs)
                val sinceLast = SystemClock.elapsedRealtime() - lastRecordAt
                if (_state.value.isRunning && sinceLast >= recordIntervalMs) {
                    record(_state.value.lightLevel, actionTriggered = false)
                }
            }
        }
        viewModelScope.launch { repository.cleanup() }
        if (classifier != null) startInference(classifier)
        // Lab 7: the dashboard gathers the live state of all modules into one State
        viewModelScope.launch {
            combine(
                repository.recentRecords, repository.pendingRecords,
                bleConnector.state, guard.actuators, ::DashboardState
            ).collect { dashboard -> _state.update { it.copy(dashboard = dashboard) } }
        }
    }

    // The ONLY entry into the ViewModel — all actions come here
    fun onAction(action: MonitorAction) {
        when (action) {
            MonitorAction.Start -> startSensor()
            MonitorAction.Stop -> stopSensor()
            // Lab 7: the quick buttons go to the same shared guard as the Control screen
            is MonitorAction.SetArmed -> guard.setArmed(action.armed)
            is MonitorAction.SetAlarm -> guard.setManualAlarm(action.enabled)
            is MonitorAction.SetFlashlight -> guard.setManualFlashlight(action.enabled)
            MonitorAction.ResetAlarm -> guard.resetAlarm()
        }
    }

    // ---------- Sensor (Lab 1) ----------

    private fun startSensor() {
        if (sensorJob != null) return
        _state.update { it.copy(isRunning = true) }
        sensorJob = viewModelScope.launch {
            sensorReader.readings().collect(::onLightChanged)
        }
    }

    private fun stopSensor() {
        sensorJob?.cancel()     // cancelling the Flow → unregisterListener()
        sensorJob = null
        _state.update { it.copy(isRunning = false) }
    }

    private fun onLightChanged(lux: Float) {
        _state.update { it.copy(lightLevel = lux) }
        guard.onLight(lux)
        // A significant change (Δ ≥ 2 lux) is recorded at once, without waiting for the timer
        if (lastRecordedLux.isNaN() || abs(lux - lastRecordedLux) >= AppConstants.RECORD_DELTA_LUX) {
            record(lux, actionTriggered = false)
        }
    }

    /** Reaction to the decisions of the protection: the security events go to the DB and to the screen. */
    private fun onGuardChanged(guardState: GuardState) {
        val previous = lastGuard
        lastGuard = guardState
        _state.update { it.copy(isSafeBreached = guardState.safeState == SafeState.OPEN, guard = guardState) }
        if (previous == null) return        // the first value is the current state, not an event
        // The guard is shared: a stopped (hidden) screen does not log, otherwise the events would be doubled
        if (!_state.value.isRunning) return

        val lux = _state.value.lightLevel
        val opened = guardState.safeState == SafeState.OPEN && previous.safeState != SafeState.OPEN
        if (opened) {
            val time = SimpleDateFormat(AppConstants.TIME_FORMAT, Locale.getDefault()).format(Date())
            Log.w(AppConstants.LOG_TAG, "UNAUTHORIZED ACCESS ATTEMPT at $time! Light level: $lux lux")
        }
        // Lab 7: a fired user scenario switches an actuator on too, so it is a security event
        val fired = guardState.activeScenarios.filter { it !in previous.activeScenarios }
        if (fired.isNotEmpty()) {
            viewModelScope.launch {
                _events.send(MonitorEvent.ShowMessage(fired.joinToString(prefix = "Scenario: ") { it.describe() }))
            }
        }
        // A security event does not wait for the timer (unless the lux record has already saved the new state)
        when {
            (guardState.alarm && !previous.alarm) || fired.isNotEmpty() -> record(lux, actionTriggered = true)
            guardState.safeState != lastRecordedState -> record(lux, actionTriggered = false)
        }
    }

    // ---------- Local database and cloud (Labs 3, 5) ----------

    private fun record(lux: Float, actionTriggered: Boolean) {
        lastRecordAt = SystemClock.elapsedRealtime()
        lastRecordedLux = lux
        val reading = SensorReading(lux, guard.state.value.safeState, actionTriggered)
        lastRecordedState = reading.safeState
        sync { repository.save(reading) }
    }

    /** The repository decides where the data goes; here its result only becomes State and Event. */
    private fun sync(upload: suspend () -> Result<Unit>) = viewModelScope.launch {
        _state.update { it.copy(syncStatus = SyncStatus.Syncing) }
        upload()
            .onSuccess { _state.update { it.copy(syncStatus = SyncStatus.Synced(System.currentTimeMillis())) } }
            .onFailure { error ->
                val queued = repository.pendingCount()
                if (_state.value.isOnline) {
                    _state.update { it.copy(syncStatus = SyncStatus.Error("${error.message} (queued $queued)")) }
                } else {
                    _state.update { it.copy(syncStatus = SyncStatus.Pending(queued)) }
                    notifyOffline()
                }
            }
    }

    /** Offline — a warning; online — everything that has accumulated goes to the cloud. */
    private suspend fun onNetworkChanged(online: Boolean) {
        val changed = online != _state.value.isOnline
        _state.update { it.copy(isOnline = online) }
        if (!changed) return
        val queued = repository.pendingCount()
        if (online) {
            offlineNotified = false
            if (queued > 0) sync { repository.syncPending() }
        } else {
            if (queued > 0) _state.update { it.copy(syncStatus = SyncStatus.Pending(queued)) }
            notifyOffline()
        }
    }

    /** One message per offline period, not one per failed record. */
    private suspend fun notifyOffline() {
        if (offlineNotified) return
        offlineNotified = true
        _events.send(MonitorEvent.ShowMessage(OFFLINE_MESSAGE))
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
                if (!_state.value.isRunning) continue
                windowBuffer.add(_state.value.lightLevel)
                if (++samples % samplesPerInference != 0 || !windowBuffer.isFull) continue
                try {
                    val prediction = model.classify(windowBuffer.toModelInput())
                    val peakLux = windowBuffer.peak()      // read here: the buffer belongs to this coroutine
                    withContext(Dispatchers.Main) { onPrediction(prediction, peakLux) }
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Inference error", e)
                    _state.update { it.copy(ai = it.ai.copy(available = false)) }
                    return@launch
                }
            }
        }
    }

    /** Integration of the prediction with the previous labs (runs on the main thread). */
    private fun onPrediction(prediction: Prediction, peakLux: Float) {
        val accessClass = prediction.accessClass
        // Lab 7: false-alarm filter — the model was trained on light of 20–500 lux, so a jump to 11 lux
        // looked like an opening to it. A threat counts only if the window really had light above
        // the opening threshold (15 lux by default, set by the user on the Scenarios screen).
        val threatClass = accessClass == AccessClass.OPENED || accessClass == AccessClass.TAMPERING
        val threat = threatClass && peakLux > guard.settings.value.openThreshold

        // "Anomaly" → actuator (Lab 4): alarm only when the model confidence is ≥ 0.7
        val confident = prediction.confidence >= AppConstants.ML_CONFIDENCE_MIN
        // Lab 7: the confident class also goes to the AI rules of the smart scenarios
        // (an implausible threat does not)
        guard.onAiResult(threat && confident, accessClass.takeIf { confident && (threat || !threatClass) })
        // The event is ongoing → more frequent DB records (Lab 5): once per 1 s instead of 5 s
        recordIntervalMs = if (threat) AppConstants.RECORD_INTERVAL_FAST_MS else AppConstants.RECORD_INTERVAL_MS

        var event = _state.value.ai.lastEvent
        if (accessClass != lastAccessClass) {
            lastAccessClass = accessClass
            Log.i(TAG, "AI: ${accessClass.name} (${(prediction.confidence * 100).toInt()}%)")
            if (accessClass != AccessClass.CLOSED) {
                val time = SimpleDateFormat(AppConstants.TIME_FORMAT, Locale.getDefault()).format(Date())
                event = "$time ${accessClass.name}"
                // FLICKER — only a log record, no alarm (false-positive filter)
                repository.logEvent(
                    CloudSchema.EVENT_AI_PREFIX + accessClass.name,
                    _state.value.lightLevel,
                    CloudSchema.SOURCE_AI
                )
            }
        }
        _state.update {
            it.copy(ai = it.ai.copy(prediction = prediction, recordIntervalMs = recordIntervalMs, lastEvent = event))
        }
    }

    override fun onCleared() {
        guard.shutdown()        // the siren and the flashlight must not stay on
        classifier?.close()     // frees the native memory of the TFLite interpreter
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SafeBoxApp
                val container = app.container
                MonitorViewModel(
                    sensorReader = LightSensorReader(app),
                    repository = container.sensorRepository,
                    networkMonitor = container.networkMonitor,
                    guard = container.safeGuard,
                    // the model belongs to the ViewModel: created here and closed in onCleared()
                    classifier = SafeAccessClassifier.createOrNull(app),
                    bleConnector = container.bleConnector,
                    deviceId = container.deviceIdProvider.deviceId
                )
            }
        }
    }
}
