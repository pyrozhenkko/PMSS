package com.example.lab1.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Helper over the Android sensor API: turns the TYPE_LIGHT listener into a Flow of lux values.
 * The ViewModel only collects the Flow; cancelling the collection unregisters the listener.
 */
class LightSensorReader(context: Context) {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_LIGHT)

    /** false — the device has no light sensor, the UI must report it. */
    val isAvailable: Boolean get() = sensor != null

    fun readings(): Flow<Float> {
        val light = sensor ?: return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    trySend(event.values[0])
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, light, SensorManager.SENSOR_DELAY_UI)
            // Called on cancel() — the sensor does not drain the battery after onPause
            awaitClose { manager.unregisterListener(listener) }
        }
    }
}
