package com.example.lab1.helpers

import com.example.lab1.AppConstants

enum class SafeState { CLOSED, OPEN }

/** User-facing explanations of the current decision. */
object DecisionReason {
    const val CLOSED = "Safe closed"
    const val MANUAL = "Manual control has priority"
    const val AI_THREAT = "AI detected a threat — alarm"
    const val OPEN_MUTED = "Safe open, alarm reset"
    const val OPEN_DISARMED = "Safe open, guard disarmed"}

/** Decision result: the safe state and which actuators must be switched on. */
data class Decision(
    val state: SafeState = SafeState.CLOSED,
    val alarm: Boolean = false,
    val flashlight: Boolean = false,
    val reason: String = DecisionReason.CLOSED,
    val autoAlarm: Boolean = false      // what automation alone would do, ignoring the manual overrides
)

/**
 * Decision-making logic block. Pure Kotlin without Android dependencies,
 * so it is checked with ordinary unit tests.
 */
class DecisionEngine(
    private var openThreshold: Float = AppConstants.SAFE_BOX_OPEN_THRESHOLD_LUX,
    private var closeThreshold: Float = AppConstants.SAFE_BOX_CLOSE_THRESHOLD_LUX,
    private var confirmMs: Long = AppConstants.OPEN_CONFIRM_MS,
    private val muteGraceMs: Long = AppConstants.MUTE_GRACE_MS
) {
    private var state = SafeState.CLOSED
    private var aboveSince: Long? = null    // since when lux has stayed above the threshold
    private var muted = false               // the user has reset the alarm
    private var mutedAt: Long? = null       // start of the grace period of the reset; null — not stamped yet

    /** true — the threshold is exceeded, but the confirmation time has not passed yet. */
    val isOpenPending: Boolean get() = state == SafeState.CLOSED && aboveSince != null

    /** Lab 7: the thresholds come from the user settings; the current state (OPEN / CLOSED) is kept. */
    fun configure(openThreshold: Float, closeThreshold: Float, confirmMs: Long) {
        this.openThreshold = openThreshold
        this.closeThreshold = closeThreshold
        this.confirmMs = confirmMs
    }

    /** "Reset alarm": silences the current alarm only; the next opening or threat raises it again. */
    fun acknowledge() {
        muted = true
        mutedAt = null
    }

    /**
     * manualAlarm / manualFlashlight: null — automation, otherwise a forced value.
     * mlAlert — the model (Lab 6) confidently recognized an opening or a tampering attempt.
     */
    fun evaluate(
        lux: Float, nowMs: Long, armed: Boolean,
        manualAlarm: Boolean? = null, manualFlashlight: Boolean? = null,
        mlAlert: Boolean = false
    ): Decision {
        when (state) {
            SafeState.CLOSED ->
                if (lux > openThreshold) {
                    // Debounce: a short flash (< 500 ms) is not treated as an opening
                    val since = aboveSince ?: nowMs.also { aboveSince = it }
                    if (nowMs - since >= confirmMs) {
                        state = SafeState.OPEN
                        aboveSince = null
                        muted = false       // a new opening is a new alarm, the old reset no longer applies
                    }
                } else {
                    aboveSince = null
                }
            // Hysteresis: back to CLOSED only below 10 lux so the state does not "flicker" around 15
            SafeState.OPEN -> if (lux < closeThreshold) {
                state = SafeState.CLOSED
                mutedAt = nowMs     // the grace period is counted from the moment of closing
            }
        }
        // After closing, the model still holds its alert for the opening that was just reset, so the reset
        // stays in force for a short grace period — otherwise the siren would come back for a few seconds.
        // It never lasts longer than that, so a real threat after the reset is not suppressed.
        if (muted && state == SafeState.CLOSED) {
            val since = mutedAt ?: nowMs.also { mutedAt = it }
            if (!mlAlert || nowMs - since >= muteGraceMs) muted = false
        }
        // ML complements the threshold: flickering (tampering) does not pass the debounce, but the model sees it
        val autoAlarm = armed && !muted && (state == SafeState.OPEN || mlAlert)
        val reason = when {
            manualAlarm != null || manualFlashlight != null -> DecisionReason.MANUAL
            autoAlarm && state == SafeState.CLOSED -> DecisionReason.AI_THREAT
            autoAlarm -> "lux > $openThreshold and guard armed — alarm"
            state == SafeState.OPEN && muted -> DecisionReason.OPEN_MUTED
            state == SafeState.OPEN -> DecisionReason.OPEN_DISARMED
            else -> DecisionReason.CLOSED
        }
        // Manual control has priority over automation
        return Decision(state, manualAlarm ?: autoAlarm, manualFlashlight ?: autoAlarm, reason, autoAlarm)
    }
}
