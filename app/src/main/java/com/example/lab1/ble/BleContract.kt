package com.example.lab1.ble

import com.example.lab1.helpers.ble.BleDevice
import com.example.lab1.helpers.ble.ConnectionState

// State — everything the "BLE" screen shows.
data class BleState(
    val devices: List<BleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val connection: ConnectionState = ConnectionState.Idle,
    val connectedDevice: BleDevice? = null,
    val terminal: List<String> = emptyList()
) {
    /** The scanner and the connector are different objects, so the status chip combines them. */
    val status: ConnectionState get() = if (isScanning) ConnectionState.Scanning else connection
}

// Action — what the user can do on this screen.
sealed interface BleAction {
    data object Scan : BleAction
    data object PermissionDenied : BleAction
    data class Connect(val device: BleDevice) : BleAction
    data object Disconnect : BleAction
    data class SendCommand(val command: String) : BleAction
}

// Event — one-shot messages from the ViewModel to the screen.
sealed interface BleEvent {
    data class ShowMessage(val text: String) : BleEvent
}
