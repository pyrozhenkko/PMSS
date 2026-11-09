package com.example.lab1.helpers.ble

import android.util.Log
import com.example.lab1.AppConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "SafeBox/MockBleScanner"

// A foreign beacon that is always "on the air" next to our device
private const val BEACON_NAME = "Unknown Beacon"
private const val BEACON_ADDRESS = "4C:21:9F:0A:77:E2"
private const val BEACON_RSSI = -88
private const val SAFE_BOX_RSSI = -55

/** Imitates BluetoothLeScanner: devices "appear" on the air with a delay. */
class MockBleScanner {

    fun scan(): Flow<BleDevice> = flow {
        Log.d(TAG, "Scan started, filter service=${BleUuids.SERVICE}")
        // The foreign beacon answers faster — real air always has some "noise"
        delay((AppConstants.BLE_SCAN_DURATION_MS / 2).milliseconds)
        emit(BleDevice(BEACON_NAME, BEACON_ADDRESS, BEACON_RSSI))
        // Our virtual IoT device is found 2 seconds after the start
        delay((AppConstants.BLE_SCAN_DURATION_MS / 2).milliseconds)
        val safeBox = BleDevice(AppConstants.BLE_DEVICE_NAME, AppConstants.BLE_DEVICE_ADDRESS, SAFE_BOX_RSSI)
        Log.d(TAG, "Found ${safeBox.name} (${safeBox.address}) rssi=${safeBox.rssi} dBm")
        emit(safeBox)
    }
}
