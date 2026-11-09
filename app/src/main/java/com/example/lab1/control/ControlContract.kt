package com.example.lab1.control

import com.example.lab1.helpers.ActuatorState
import com.example.lab1.helpers.CommandSource
import com.example.lab1.helpers.DecisionReason
import com.example.lab1.repository.CloudSchema

// State — everything the "Control" screen shows.
data class ControlState(
    val armed: Boolean = true,
    val ledStatus: String = CloudSchema.LED_OFF,
    val source: CommandSource = CommandSource.AUTO,
    val reason: String = DecisionReason.CLOSED,
    val cloudControlAvailable: Boolean = false,
    val actuators: ActuatorState = ActuatorState()      // the actual state of the siren and the flashlight
)

// Action — what the user can do on this screen. No one-shot events here, so there is no ControlEvent.
sealed interface ControlAction {
    data class SetArmed(val armed: Boolean) : ControlAction
    data class SetFlashlight(val enabled: Boolean) : ControlAction
    data class SetAlarm(val enabled: Boolean) : ControlAction
    data object ResetAlarm : ControlAction
}
