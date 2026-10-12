package com.example.lab1.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Separated business logic class responsible ONLY for sensor data acquisition.
 * Follows the Single Responsibility Principle.
 */
class LightSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private var onLightLevelChanged: ((Float) -> Unit)? = null

    /** false — the device has no light sensor, the UI must report it. */
    val isAvailable: Boolean get() = lightSensor != null

    /**
     * Registers the sensor listener to start receiving updates.
     */
    fun startListening(listener: (Float) -> Unit) {
        onLightLevelChanged = listener
        lightSensor?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    /**
     * Unregisters the sensor listener to prevent battery drain.
     */
    fun stopListening() {
        sensorManager.unregisterListener(this)
        onLightLevelChanged = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val currentLightLevel = event.values[0]
            onLightLevelChanged?.invoke(currentLightLevel)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used in this implementation, but required by the interface
    }
}
