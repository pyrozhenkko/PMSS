package com.example.lab1.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lab1.SafeBoxApp
import com.example.lab1.helpers.ActuatorState
import com.example.lab1.helpers.GuardState
import com.example.lab1.helpers.SafeGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The "Control" screen (Manual Control): manual switches over the shared safe protection. */
class ControlViewModel(private val guard: SafeGuard) : ViewModel() {

    private val _state = MutableStateFlow(toState(guard.state.value, guard.actuators.value))
    val state: StateFlow<ControlState> = _state.asStateFlow()

    init {
        // The decision and the actuators can also be changed by the sensor or the cloud, not only by this screen
        viewModelScope.launch {
            combine(guard.state, guard.actuators, ::toState).collect { _state.value = it }
        }
    }

    fun onAction(action: ControlAction) {
        when (action) {
            is ControlAction.SetArmed -> guard.setArmed(action.armed)
            is ControlAction.SetFlashlight -> guard.setManualFlashlight(action.enabled)
            is ControlAction.SetAlarm -> guard.setManualAlarm(action.enabled)
            ControlAction.ResetAlarm -> guard.resetAlarm()
        }
    }

    private fun toState(guardState: GuardState, actuators: ActuatorState) = ControlState(
        armed = guardState.armed,
        ledStatus = guardState.ledStatus,
        source = guardState.source,
        reason = guardState.reason,
        cloudControlAvailable = guard.isCloudControlAvailable,
        actuators = actuators
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ControlViewModel((this[APPLICATION_KEY] as SafeBoxApp).container.safeGuard)
            }
        }
    }
}
