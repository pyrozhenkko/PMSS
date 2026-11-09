package com.example.lab1.helpers

import com.example.lab1.AppConstants
import com.example.lab1.repository.ScenarioRule
import kotlin.math.min

/** What a smart scenario watches. */
enum class ScenarioCondition(val title: String) {
    LUX_ABOVE("lux >"),
    LUX_BELOW("lux <"),
    AI_CLASS("AI =")
}

/** What a smart scenario does while its condition holds. */
enum class ScenarioAction(val title: String) {
    VIBRATION("vibration"),
    FLASHLIGHT("flashlight"),
    BLE_ALARM("BLE hub ALARM_ON"),
    CLOUD_EVENT("event to the cloud")
}

/** Human-readable form of a rule for the UI, Logcat and the cloud log. */
fun ScenarioRule.describe(): String {
    val subject = when (condition) {
        ScenarioCondition.AI_CLASS -> "${condition.title} ${aiClass.name}"
        else -> "${condition.title} $threshold"
    }
    return "IF $subject THEN ${action.title}"
}

/** Lab 7: the same condition and action — such a rule is not added twice. The unused field is ignored. */
fun ScenarioRule.isSameAs(other: ScenarioRule): Boolean =
    condition == other.condition && action == other.action &&
        if (condition == ScenarioCondition.AI_CLASS) aiClass == other.aiClass else threshold == other.threshold

/** Result of the rules: the actions active now and the rules that have just fired. */
data class ScenarioResult(
    val actions: Set<ScenarioAction> = emptySet(),
    val activeRules: List<ScenarioRule> = emptyList(),
    val fired: List<ScenarioRule> = emptyList(),
    val isPending: Boolean = false      // a condition holds, but its confirmation time has not passed yet
)

/**
 * Evaluates the user rules. Pure Kotlin, like DecisionEngine, so it is covered by JVM unit tests.
 * Noise suppression: a lux rule fires only after its condition has held for 500 ms (debounce)
 * and is released only 2 lux past the threshold (hysteresis), so the sensor noise around
 * the threshold does not switch the action on and off. An AI rule is released only after the
 * class has been absent for 3 s, because the model can label one window as FLICKER between flashes.
 */
class ScenarioEngine(
    private val confirmMs: Long = AppConstants.SCENARIO_CONFIRM_MS,
    private val hysteresis: Float = AppConstants.SCENARIO_HYSTERESIS_LUX,
    private val aiHoldMs: Long = AppConstants.ML_ALERT_HOLD_MS
) {
    private val active = mutableSetOf<Long>()          // ids of the fired rules
    private val since = mutableMapOf<Long, Long>()     // lux condition holds since
    private val lastSeen = mutableMapOf<Long, Long>()  // AI class was seen last

    /** aiClass — the class the model reported with enough confidence, null — no confident prediction. */
    fun evaluate(rules: List<ScenarioRule>, lux: Float, aiClass: AccessClass?, nowMs: Long): ScenarioResult {
        val enabled = rules.filter { it.enabled }
        val ids = enabled.map { it.id }.toSet()
        // a deleted or disabled rule releases its action at once
        active.retainAll(ids)
        since.keys.retainAll(ids)
        lastSeen.keys.retainAll(ids)

        val fired = mutableListOf<ScenarioRule>()
        for (rule in enabled) {
            val holds = holds(rule, lux, aiClass)
            if (rule.id in active) {
                if (isReleased(rule, lux, holds, nowMs)) active.remove(rule.id)
            } else if (holds && isConfirmed(rule, nowMs)) {
                active += rule.id
                fired += rule
            } else if (!holds) {
                since.remove(rule.id)
            }
            if (holds && rule.condition == ScenarioCondition.AI_CLASS) lastSeen[rule.id] = nowMs
        }
        val activeRules = enabled.filter { it.id in active }
        return ScenarioResult(activeRules.map { it.action }.toSet(), activeRules, fired, since.isNotEmpty())
    }

    private fun holds(rule: ScenarioRule, lux: Float, aiClass: AccessClass?) = when (rule.condition) {
        ScenarioCondition.LUX_ABOVE -> lux > rule.threshold
        ScenarioCondition.LUX_BELOW -> lux < rule.threshold
        ScenarioCondition.AI_CLASS -> aiClass == rule.aiClass
    }

    /** The model already looks at a 2 s window: only lux conditions need debounce. */
    private fun isConfirmed(rule: ScenarioRule, nowMs: Long): Boolean {
        if (rule.condition == ScenarioCondition.AI_CLASS) return true
        val start = since.getOrPut(rule.id) { nowMs }
        if (nowMs - start < confirmMs) return false
        since.remove(rule.id)
        return true
    }

    private fun isReleased(rule: ScenarioRule, lux: Float, holds: Boolean, nowMs: Long) = when (rule.condition) {
        // the band is at most half of the threshold, so "lux > 1" can be released
        ScenarioCondition.LUX_ABOVE -> lux < rule.threshold - min(hysteresis, rule.threshold / 2)
        ScenarioCondition.LUX_BELOW -> lux > rule.threshold + hysteresis
        ScenarioCondition.AI_CLASS -> !holds && nowMs - (lastSeen[rule.id] ?: 0L) >= aiHoldMs
    }
}
