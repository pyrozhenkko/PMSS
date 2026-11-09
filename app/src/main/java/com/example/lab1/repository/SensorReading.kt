package com.example.lab1.repository

import com.example.lab1.helpers.SafeState

/** One light reading as the ViewModel sees it; the repository turns it into a Room row and JSON. */
data class SensorReading(
    val lux: Float,
    val safeState: SafeState,
    val actionTriggered: Boolean,       // true — the alarm fired at this moment
    val timestamp: Long = System.currentTimeMillis()
)

/** A new row is not synced yet: it goes to the cloud right after the INSERT or when the network is back. */
fun SensorReading.toRecord() = SensorRecord(
    timestamp = timestamp,
    sensorValue = lux,
    actionTriggered = actionTriggered,
    state = safeState.name,
    synced = false
)

fun SensorRecord.toPayload(deviceId: String) = SensorData(
    deviceId = deviceId,
    timestamp = timestamp,
    value = sensorValue,
    state = state
)
