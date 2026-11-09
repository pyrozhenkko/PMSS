package com.example.lab1.scenarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lab1.AppConstants
import com.example.lab1.SafeBoxApp
import com.example.lab1.helpers.GuardSettings
import com.example.lab1.helpers.SafeGuard
import com.example.lab1.helpers.describe
import com.example.lab1.helpers.ble.ConnectionState
import com.example.lab1.helpers.ble.IBleConnector
import com.example.lab1.repository.ScenarioRepository
import com.example.lab1.repository.ScenarioRule
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lab 7: the "Scenarios" screen — the user composes "IF … THEN …" rules and sets up the main guard.
 * The ViewModel only edits the data in the repository; SafeGuard receives the same Flows and executes them.
 */
class ScenariosViewModel(
    private val repository: ScenarioRepository,
    guard: SafeGuard,
    bleConnector: IBleConnector
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScenariosState(guardThresholdText = formatLux(repository.guardSettings.value.openThreshold))
    )
    val state: StateFlow<ScenariosState> = _state.asStateFlow()

    private val _events = Channel<ScenariosEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { repository.rules.collect { rules -> _state.update { it.copy(rules = rules) } } }
        viewModelScope.launch { repository.guardSettings.collect { s -> _state.update { it.copy(guard = s) } } }
        // Which rules are firing now — the screen highlights them
        viewModelScope.launch {
            guard.state.collect { guardState ->
                _state.update { it.copy(activeRuleIds = guardState.activeScenarios.map(ScenarioRule::id).toSet()) }
            }
        }
        // Without a connected hub a BLE_ALARM rule fires, but its command goes nowhere — the card says so
        viewModelScope.launch {
            bleConnector.state.collect { connection ->
                val connected = connection is ConnectionState.Connected || connection is ConnectionState.DataTransfer
                _state.update { it.copy(hubConnected = connected) }
            }
        }
    }

    fun onAction(action: ScenariosAction) {
        when (action) {
            is ScenariosAction.SetCondition -> updateDraft { it.copy(condition = action.condition) }
            is ScenariosAction.SetThreshold -> updateDraft { it.copy(thresholdText = action.text) }
            is ScenariosAction.SetAiClass -> updateDraft { it.copy(aiClass = action.aiClass) }
            is ScenariosAction.SetAction -> updateDraft { it.copy(action = action.action) }
            ScenariosAction.AddRule -> addRule()
            is ScenariosAction.SetEnabled -> viewModelScope.launch { repository.setEnabled(action.id, action.enabled) }
            is ScenariosAction.DeleteRule -> viewModelScope.launch { repository.delete(action.id) }
            is ScenariosAction.SetGuardThreshold -> _state.update { it.copy(guardThresholdText = action.text) }
            ScenariosAction.ApplyGuardThreshold -> applyGuardThreshold()
            is ScenariosAction.SetGuardConfirm -> saveGuard { it.copy(confirmMs = action.ms) }
            is ScenariosAction.SetGuardOption -> saveGuard { setOption(it, action.option, action.enabled) }
            ScenariosAction.ResetGuard -> {
                _state.update { it.copy(guardThresholdText = formatLux(AppConstants.SAFE_BOX_OPEN_THRESHOLD_LUX)) }
                saveGuard { GuardSettings() }
            }
        }
    }

    private fun updateDraft(change: (ScenarioDraft) -> ScenarioDraft) =
        _state.update { it.copy(draft = change(it.draft)) }

    private fun addRule() {
        val draft = _state.value.draft
        if (!draft.isValid) return
        val rule = ScenarioRule(
            condition = draft.condition,
            threshold = draft.threshold ?: 0f,
            aiClass = draft.aiClass,
            action = draft.action
        )
        viewModelScope.launch {
            // Lab 7: the repository refuses the same rule, even after two quick taps
            val id = repository.add(rule)
            val text = if (id == null) "This rule already exists: ${rule.describe()}" else "Rule added: ${rule.describe()}"
            _events.send(ScenariosEvent.ShowMessage(text))
        }
    }

    private fun applyGuardThreshold() {
        val value = _state.value.guardThresholdText.replace(',', '.').toFloatOrNull()
        if (value == null || value < AppConstants.GUARD_THRESHOLD_MIN || value > AppConstants.GUARD_THRESHOLD_MAX) {
            viewModelScope.launch {
                _events.send(ScenariosEvent.ShowMessage("Enter a threshold from 1 to 1000 lux"))
            }
            return
        }
        saveGuard { it.copy(openThreshold = value) }
        val closes = formatLux(value * AppConstants.GUARD_CLOSE_RATIO)
        viewModelScope.launch {
            _events.send(ScenariosEvent.ShowMessage("Opens above ${formatLux(value)} lux, closes below $closes lux"))
        }
    }

    private fun saveGuard(change: (GuardSettings) -> GuardSettings) {
        viewModelScope.launch { repository.saveGuardSettings(change(repository.guardSettings.value)) }
    }

    private fun setOption(settings: GuardSettings, option: GuardOption, enabled: Boolean) = when (option) {
        GuardOption.AI_ALARM -> settings.copy(aiAlarm = enabled)
        GuardOption.SIREN -> settings.copy(siren = enabled)
        GuardOption.FLASHLIGHT -> settings.copy(flashlight = enabled)
        GuardOption.BLE_ALARM -> settings.copy(bleAlarm = enabled)
    }

    companion object {
        /** 15.0 → "15", 12.5 → "12.5". */
        fun formatLux(value: Float): String =
            if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SafeBoxApp).container
                ScenariosViewModel(container.scenarioRepository, container.safeGuard, container.bleConnector)
            }
        }
    }
}
