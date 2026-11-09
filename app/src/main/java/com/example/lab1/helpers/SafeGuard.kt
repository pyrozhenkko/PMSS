package com.example.lab1.helpers

import android.os.SystemClock
import android.util.Log
import com.example.lab1.AppConstants
import com.example.lab1.helpers.ble.BleProtocol
import com.example.lab1.repository.CloudSchema
import com.example.lab1.repository.ControlCommand
import com.example.lab1.repository.DeviceStatus
import com.example.lab1.repository.ScenarioRepository
import com.example.lab1.repository.ScenarioRule
import com.example.lab1.repository.SensorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SafeBox/SafeGuard"

/** Who currently defines the state of the actuators. */
enum class CommandSource { AUTO, MANUAL, CLOUD, SCENARIO }

/** State of the safe protection shared by the Monitor, Control and BLE screens. */
data class GuardState(
    val safeState: SafeState = SafeState.CLOSED,
    val alarm: Boolean = false,
    val flashlight: Boolean = false,
    val armed: Boolean = true,
    val ledStatus: String = CloudSchema.LED_OFF,
    val source: CommandSource = CommandSource.AUTO,
    val reason: String = DecisionReason.CLOSED,
    val activeScenarios: List<ScenarioRule> = emptyList(),     // Lab 7: the user rules that hold now
    val hubAlarm: Boolean = false                              // Lab 7: what the BLE hub has been told
)

/**
 * Protection of the safe: DecisionEngine + actuators + commands from the cloud. Several screens drive it
 * (Monitor gives lux, Control and BLE give manual commands), so it is created once in AppContainer,
 * like the repository — otherwise each screen would have its own siren and the states would diverge.
 * All calls come from the main thread.
 */
class SafeGuard(
    private val actuator: SmartActuator,
    private val repository: SensorRepository,
    private val scenarios: ScenarioRepository
) {
    // Main: the timers and the cloud commands touch the same fields as the calls from the ViewModels
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(GuardState())
    val state: StateFlow<GuardState> = _state.asStateFlow()
    val actuators: StateFlow<ActuatorState> = actuator.state
    val isCloudControlAvailable: Boolean get() = repository.isCloudControlAvailable

    /** Lab 7: the user settings of the main guard (threshold, debounce, what the alarm switches on). */
    val settings: StateFlow<GuardSettings> = scenarios.guardSettings

    /** ALARM_ON / ALARM_OFF for the BLE hub; the BLE screen sends them if a device is connected. */
    private val _bleCommands = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val bleCommands: SharedFlow<String> = _bleCommands.asSharedFlow()

    private var decisionEngine = newDecisionEngine()
    private var lastDecision = Decision()
    private var lux = 0f
    private var confirmJob: Job? = null
    private var manualAlarm: Boolean? = null        // null — automation is in control
    private var manualFlashlight: Boolean? = null
    private var cloudCommand = ControlCommand()
    private var reportedStatus: DeviceStatus? = null   // null — nothing reported yet, the first decision is sent
    private var mlAlertUntil = 0L

    // Lab 7: the user smart scenarios run next to the DecisionEngine and share its actuators
    private var scenarioEngine = ScenarioEngine()
    private var rules: List<ScenarioRule> = emptyList()
    private var scenario = ScenarioResult()
    private var aiClass: AccessClass? = null
    private var hubAlarm = false            // what the BLE hub has been told last

    init {
        // Cloud-to-Device: an event from Firebase changes the state at once, without polling
        scope.launch { repository.commands().collect(::onCloudCommand) }
        // A rule added or switched off on the Scenarios screen applies at once
        scope.launch {
            scenarios.rules.collect {
                rules = it
                evaluate()
            }
        }
        // Lab 7: new guard settings apply at once, the current state of the safe is kept
        scope.launch {
            settings.collect {
                decisionEngine.configure(it.openThreshold, it.closeThreshold, it.confirmMs)
                evaluate()
            }
        }
    }

    fun onLight(lux: Float) {
        this.lux = lux
        evaluate()
    }

    /**
     * Result of the model (Lab 6). The alarm is held for another 3 s after the last threat, otherwise
     * the siren would "blink" when the window is classified as FLICKER for a moment between flashes.
     */
    fun onAiResult(confidentThreat: Boolean, confidentClass: AccessClass? = null) {
        if (confidentThreat) mlAlertUntil = SystemClock.elapsedRealtime() + AppConstants.ML_ALERT_HOLD_MS
        aiClass = confidentClass        // Lab 7: for the AI rules of the scenarios ("IF AI = TAMPERING")
        evaluate()
    }

    fun setArmed(armed: Boolean) {
        _state.update { it.copy(armed = armed) }
        repository.setArmed(armed)      // so the cloud and the phone see the same mode
        evaluate()
    }

    fun setManualAlarm(enabled: Boolean) {
        manualAlarm = enabled
        evaluate()
    }

    fun setManualFlashlight(enabled: Boolean) {
        manualFlashlight = enabled
        evaluate()
    }

    /** "Reset alarm": removes the manual forced values and returns to AUTO mode. */
    fun resetAlarm() {
        manualAlarm = null
        manualFlashlight = null
        decisionEngine.acknowledge()
        evaluate()
    }

    /** A security event in the cloud log with the current light level. */
    fun logEvent(type: String, source: String) = repository.logEvent(type, lux, source)

    /** The app is closed: the siren and the flashlight must not stay on, the next start begins from scratch. */
    fun shutdown() {
        confirmJob?.cancel()
        actuator.shutdown()
        decisionEngine = newDecisionEngine()
        lastDecision = Decision()
        manualAlarm = null
        manualFlashlight = null
        reportedStatus = null
        mlAlertUntil = 0L
        scenarioEngine = ScenarioEngine()
        scenario = ScenarioResult()
        aiClass = null
        hubAlarm = false
        _state.update { GuardState(armed = it.armed, ledStatus = it.ledStatus) }
    }

    /** Passes the current data to the DecisionEngine and applies its decision. */
    private fun evaluate() {
        scenario = scenarioEngine.evaluate(rules, lux, aiClass, SystemClock.elapsedRealtime())
        var decision = decide()
        // A manual "off" only silences the alarm that is active now. Once there is nothing left
        // to suppress, the switch returns to automation, so the next opening raises the alarm again.
        var released = false
        if (manualAlarm == false && !decision.autoAlarm && !cloudCommand.alarm) {
            manualAlarm = null
            released = true
        }
        if (manualFlashlight == false && !decision.autoAlarm && !cloudCommand.flashlight && !scenarioFlashlight) {
            manualFlashlight = null
            released = true
        }
        if (released) decision = decide()
        applyDecision(decision)

        // TYPE_LIGHT sends events only when the value changes, so the opening confirmation
        // time (debounce) is counted with our own timer
        confirmJob?.cancel()
        if (decisionEngine.isOpenPending || scenario.isPending) {
            confirmJob = scope.launch {
                delay(minOf(settings.value.confirmMs, AppConstants.SCENARIO_CONFIRM_MS))
                evaluate()
            }
        }
    }

    private fun newDecisionEngine() = settings.value.let {
        DecisionEngine(openThreshold = it.openThreshold, closeThreshold = it.closeThreshold, confirmMs = it.confirmMs)
    }

    private fun decide(): Decision {
        // priority: manual switch → cloud command → automation (null)
        val flashlightOverride = manualFlashlight ?: true.takeIf { cloudCommand.flashlight || scenarioFlashlight }
        val decision = decisionEngine.evaluate(
            lux = lux,
            nowMs = SystemClock.elapsedRealtime(),
            armed = _state.value.armed,
            manualAlarm = manualAlarm ?: true.takeIf { cloudCommand.alarm },
            manualFlashlight = flashlightOverride,
            // Lab 7: the user decides whether the model may raise the alarm
            mlAlert = settings.value.aiAlarm && SystemClock.elapsedRealtime() < mlAlertUntil
        )
        // Lab 7: the floodlight is an alarm output only if the user left it switched on
        return if (flashlightOverride == null && !settings.value.flashlight) decision.copy(flashlight = false) else decision
    }

    private val scenarioFlashlight: Boolean get() = ScenarioAction.FLASHLIGHT in scenario.actions

    private fun applyDecision(decision: Decision) {
        val previous = lastDecision
        lastDecision = decision
        val source = when {
            manualAlarm != null || manualFlashlight != null -> CommandSource.MANUAL
            cloudCommand.alarm || cloudCommand.flashlight -> CommandSource.CLOUD
            scenario.actions.isNotEmpty() && !decision.autoAlarm -> CommandSource.SCENARIO
            else -> CommandSource.AUTO
        }
        val reason = if (source == CommandSource.SCENARIO) {
            "Scenario: " + scenario.activeRules.joinToString { it.describe() }
        } else {
            decision.reason
        }
        // Lab 7: what the automatic alarm switches on is set by the user; an explicit command
        // (the Siren button, the cloud) always works
        val explicitAlarm = manualAlarm == true || cloudCommand.alarm
        val siren = decision.alarm && (explicitAlarm || settings.value.siren)
        // The BLE hub sounds both for the safe alarm and for a scenario with the BLE_ALARM action
        val hub = (decision.alarm && (explicitAlarm || settings.value.bleAlarm)) ||
            ScenarioAction.BLE_ALARM in scenario.actions
        _state.update {
            it.copy(
                safeState = decision.state,
                alarm = decision.alarm,
                flashlight = decision.flashlight,
                source = source,
                reason = reason,
                activeScenarios = scenario.activeRules,
                hubAlarm = hub
            )
        }
        if (decision.alarm != previous.alarm) {
            val event = if (decision.alarm) CloudSchema.EVENT_ALARM_ON else CloudSchema.EVENT_ALARM_OFF
            repository.logEvent(event, lux, source.name)      // alarm log in the cloud
        }
        if (siren) actuator.triggerAlarm() else actuator.stopAlarm()      // both do nothing if already so
        if (hub != hubAlarm) {
            hubAlarm = hub
            _bleCommands.tryEmit(if (hub) BleProtocol.CMD_ALARM_ON else BleProtocol.CMD_ALARM_OFF)
        }
        if (decision.flashlight != previous.flashlight) {
            actuator.toggleFlashlight(decision.flashlight)
        }
        actuator.setPulseVibration(ScenarioAction.VIBRATION in scenario.actions)
        scenario.fired.forEach(::onScenarioFired)
        // The cloud sees the actual state; written only when something has changed
        val status = DeviceStatus(decision.alarm, decision.flashlight, decision.state.name, source.name)
        if (status != reportedStatus) {
            reportedStatus = status
            repository.reportStatus(status)
        }
    }

    /** A rule has just fired: Logcat, and CLOUD_EVENT also goes to the cloud log. */
    private fun onScenarioFired(rule: ScenarioRule) {
        Log.i(TAG, "SCENARIO fired: ${rule.describe()} (lux = $lux)")
        if (rule.action == ScenarioAction.CLOUD_EVENT) {
            repository.logEvent(CloudSchema.EVENT_SCENARIO_PREFIX + rule.describe(), lux, CommandSource.SCENARIO.name)
        }
    }

    private fun onCloudCommand(command: ControlCommand) {
        cloudCommand = command
        _state.update { it.copy(armed = command.armed, ledStatus = command.ledStatus) }
        evaluate()
    }
}
