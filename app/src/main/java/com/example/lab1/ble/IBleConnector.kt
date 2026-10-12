package com.example.lab1.ble

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bluetooth Interface Layer: the UI and the ViewModel know only this interface,
 * so the mock can be replaced with a real BluetoothGatt without touching the rest of the code.
 */
interface IBleConnector {
    /** Current connection state — the UI subscribes to it. */
    val state: StateFlow<ConnectionState>

    /** Notify messages from the device (Status, Light Level, ACK). */
    val incoming: SharedFlow<String>

    suspend fun connect(device: BleDevice)

    fun disconnect()

    /** Write to the Command characteristic; true — the device acknowledged the write. */
    suspend fun sendData(data: String): Boolean
}
