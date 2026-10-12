package com.example.lab1.data.remote

import com.example.lab1.domain.SafeState

/**
 * One sensor reading that is sent to the cloud as JSON.
 * Gson / Firebase need the default values for deserialization.
 */
data class SensorData(
    val deviceId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val value: Float = 0f,
    val type: String = CloudSchema.SENSOR_TYPE_LIGHT,
    val state: String = SafeState.CLOSED.name       // CLOSED | OPEN — safe state at the time of measurement
)

/** Realtime Database response to POST: the generated push id of the new record. */
data class PushResponse(val name: String = "")
