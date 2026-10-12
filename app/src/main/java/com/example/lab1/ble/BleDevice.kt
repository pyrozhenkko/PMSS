package com.example.lab1.ble

/** Scan result: name from the advertising packet, MAC address and signal level. */
data class BleDevice(val name: String, val address: String, val rssi: Int)
