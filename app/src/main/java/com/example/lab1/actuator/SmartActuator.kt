package com.example.lab1.actuator

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "SafeBox/SmartActuator"

/** Actual state of the actuators — this is what the UI shows. */
data class ActuatorState(
    val alarmOn: Boolean = false,
    val flashlightOn: Boolean = false,
    val hasVibrator: Boolean = false,
    val hasFlash: Boolean = false       // false — "virtual floodlight" (typical for the emulator)
)

/**
 * Actuator service: the vibration motor + siren and the flashlight of the smartphone imitate
 * industrial actuators (the siren and the floodlight of the safe).
 */
class SmartActuator(private val context: Context) {

    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private var toneGenerator: ToneGenerator? = null

    // Check the hardware capabilities before any call
    private val hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    private val torchCameraId: String? = if (hasFlash) findTorchCamera() else null

    private val _state = MutableStateFlow(
        ActuatorState(hasVibrator = vibrator.hasVibrator(), hasFlash = torchCameraId != null)
    )
    val state: StateFlow<ActuatorState> = _state.asStateFlow()

    /** Switches the "siren" on: patterned vibration + a sound tone until stopAlarm() is called. */
    fun triggerAlarm() {
        if (_state.value.alarmOn) return
        try {
            if (vibrator.hasVibrator()) {
                // 0 ms pause, 400 ms vibration, 200 ms pause, 400 ms vibration; 0 — repeat from the start
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), 0))
            }
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME).apply {
                startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK)
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to switch the siren on", e)
        }
        _state.update { it.copy(alarmOn = true) }
        Log.d(TAG, "ALARM ON (vibrator=${vibrator.hasVibrator()})")
    }

    fun stopAlarm() {
        if (!_state.value.alarmOn) return
        vibrator.cancel()
        toneGenerator?.apply { stopTone(); release() }
        toneGenerator = null
        _state.update { it.copy(alarmOn = false) }
        Log.d(TAG, "ALARM OFF")
    }

    /** Controls the flashlight; if there is no flash, only the "virtual floodlight" state changes. */
    fun toggleFlashlight(status: Boolean) {
        val cameraId = torchCameraId
        if (cameraId == null) {
            Log.w(TAG, "FEATURE_CAMERA_FLASH is missing — virtual floodlight: $status")
        } else {
            try {
                cameraManager.setTorchMode(cameraId, status)   // the CAMERA permission is not required
                Log.d(TAG, "setTorchMode($cameraId, $status)")
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Camera unavailable: ${e.message}")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Camera $cameraId does not support the torch")
            }
        }
        _state.update { it.copy(flashlightOn = status) }
    }

    /** Called from ViewModel.onCleared(): no actuator is left switched on. */
    fun shutdown() {
        stopAlarm()
        if (_state.value.flashlightOn) toggleFlashlight(false)
    }

    private fun findTorchCamera(): String? = try {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: CameraAccessException) {
        Log.e(TAG, "Failed to get the camera list", e)
        null
    }
}
