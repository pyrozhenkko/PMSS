package com.example.lab1.scenarios

import com.example.lab1.AppConstants
import com.example.lab1.helpers.AccessClass
import com.example.lab1.helpers.GuardSettings
import com.example.lab1.helpers.ScenarioAction
import com.example.lab1.helpers.ScenarioCondition
import com.example.lab1.repository.ScenarioRule

/** The rule the user is composing; the threshold stays text until the rule is added. */
data class ScenarioDraft(
    val condition: ScenarioCondition = ScenarioCondition.LUX_ABOVE,
    val thresholdText: String = AppConstants.SCENARIO_DEFAULT_THRESHOLD,
    val aiClass: AccessClass = AccessClass.TAMPERING,
    val action: ScenarioAction = ScenarioAction.VIBRATION
) {
    /** null — not a number or negative, the "Add" button is disabled. */
    val threshold: Float? get() = thresholdText.replace(',', '.').toFloatOrNull()?.takeIf { it >= 0f }

    val isValid: Boolean get() = condition == ScenarioCondition.AI_CLASS || threshold != null
}

/** Lab 7: the switches of the main guard on the Scenarios screen. */
enum class GuardOption(val title: String) {
    AI_ALARM("AI can raise the alarm"),
    SIREN("Siren and vibration"),
    FLASHLIGHT("Flashlight"),
    BLE_ALARM("BLE hub ALARM_ON")
}

// State — everything the "Scenarios" screen shows.
data class ScenariosState(
    val rules: List<ScenarioRule> = emptyList(),
    val activeRuleIds: Set<Long> = emptySet(),      // the rules whose condition holds right now
    val hubConnected: Boolean = false,              // a BLE_ALARM rule reaches only a connected hub
    val draft: ScenarioDraft = ScenarioDraft(),
    val guard: GuardSettings = GuardSettings(),     // Lab 7: the saved settings of the main guard
    val guardThresholdText: String = AppConstants.SAFE_BOX_OPEN_THRESHOLD_LUX.toInt().toString()
)

// Action — what the user can do on this screen.
sealed interface ScenariosAction {
    data class SetCondition(val condition: ScenarioCondition) : ScenariosAction
    data class SetThreshold(val text: String) : ScenariosAction
    data class SetAiClass(val aiClass: AccessClass) : ScenariosAction
    data class SetAction(val action: ScenarioAction) : ScenariosAction
    data object AddRule : ScenariosAction
    data class SetEnabled(val id: Long, val enabled: Boolean) : ScenariosAction
    data class DeleteRule(val id: Long) : ScenariosAction

    // Lab 7: the main guard
    data class SetGuardThreshold(val text: String) : ScenariosAction
    data object ApplyGuardThreshold : ScenariosAction
    data class SetGuardConfirm(val ms: Long) : ScenariosAction
    data class SetGuardOption(val option: GuardOption, val enabled: Boolean) : ScenariosAction
    data object ResetGuard : ScenariosAction
}

// Event — one-shot messages from the ViewModel to the screen.
sealed interface ScenariosEvent {
    data class ShowMessage(val text: String) : ScenariosEvent
}
