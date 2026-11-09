package com.example.lab1.monitor

import com.example.lab1.AppConstants
import com.example.lab1.helpers.ActuatorState
import com.example.lab1.helpers.GuardState
import com.example.lab1.helpers.Prediction
import com.example.lab1.helpers.ble.ConnectionState
import com.example.lab1.repository.SensorRecord

/** Cloud synchronization state for the on-screen indicator. */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long) : SyncStatus
    data class Pending(val count: Int) : SyncStatus
    data class Error(val message: String) : SyncStatus
}

/** State of the "AI Insights" block. */
data class AiState(
    val available: Boolean = false,
    val prediction: Prediction? = null,
    val recordIntervalMs: Long = AppConstants.RECORD_INTERVAL_MS,
    val lastEvent: String? = null
)

/** Lab 7: what the dashboard gathers from the other modules. */
data class DashboardState(
    val recentRecords: List<SensorRecord> = emptyList(),  // the last 5 DB rows
    val pendingCount: Int = 0,                   // rows waiting for the network
    val bleConnection: ConnectionState = ConnectionState.Idle,
    // the actual siren, flashlight and vibration
    val actuators: ActuatorState = ActuatorState()
)

// State — everything the main screen (Dashboard) shows.
data class MonitorState(
    val lightLevel: Float = 0f,
    val isRunning: Boolean = false,
    val sensorAvailable: Boolean = true,
    val isSafeBreached: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val isOnline: Boolean = true,
    val deviceId: String = "",
    val ai: AiState = AiState(),
    val guard: GuardState = GuardState(),  // armed, alarm, command source, scenarios
    val dashboard: DashboardState = DashboardState()
)

// Action — what the user (and MainActivity on onResume/onPause) can do here.
sealed interface MonitorAction {
    data object Start : MonitorAction
    data object Stop : MonitorAction

    // Lab 7: quick actuator control from the dashboard
    data class SetArmed(val armed: Boolean) : MonitorAction
    data class SetAlarm(val enabled: Boolean) : MonitorAction
    data class SetFlashlight(val enabled: Boolean) : MonitorAction
    data object ResetAlarm : MonitorAction
}

// Event — one-shot messages from the ViewModel to the screen.
sealed interface MonitorEvent {
    data class ShowMessage(val text: String) : MonitorEvent
}
