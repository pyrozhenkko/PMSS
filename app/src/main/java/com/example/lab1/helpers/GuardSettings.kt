package com.example.lab1.helpers

import com.example.lab1.AppConstants

/**
 * Lab 7: the user settings of the main guard. The defaults are the values of Labs 4 and 6,
 * so until the user changes something the guard behaves exactly as before.
 */
data class GuardSettings(
    val openThreshold: Float = AppConstants.SAFE_BOX_OPEN_THRESHOLD_LUX,   // lux
    val confirmMs: Long = AppConstants.OPEN_CONFIRM_MS,                     // opening debounce
    val aiAlarm: Boolean = true,        // the model (Lab 6) may raise the alarm
    val siren: Boolean = true,          // what the alarm switches on
    val flashlight: Boolean = true,
    val bleAlarm: Boolean = true
) {
    /** Hysteresis: the safe closes at 2/3 of the opening threshold (15 → 10 lux, as in Lab 4). */
    val closeThreshold: Float get() = openThreshold * AppConstants.GUARD_CLOSE_RATIO
}
