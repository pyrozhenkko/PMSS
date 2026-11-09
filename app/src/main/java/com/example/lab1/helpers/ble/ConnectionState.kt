package com.example.lab1.helpers.ble

/** States of the BLE connection lifecycle (nodes of the state diagram). */
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Scanning : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data object DataTransfer : ConnectionState
    data object Disconnected : ConnectionState
    data class Error(val reason: String) : ConnectionState
}
