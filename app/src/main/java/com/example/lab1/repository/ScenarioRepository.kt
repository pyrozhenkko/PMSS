package com.example.lab1.repository

import android.content.SharedPreferences
import android.util.Log
import com.example.lab1.helpers.GuardSettings
import com.example.lab1.helpers.describe
import com.example.lab1.helpers.isSameAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "SafeBox/ScenarioRepository"

/** The file of the main guard settings (SharedPreferences: a few values, no table needed). */
const val GUARD_PREFS = "guard_settings"
private const val KEY_OPEN = "open_threshold"
private const val KEY_CONFIRM = "confirm_ms"
private const val KEY_AI = "ai_alarm"
private const val KEY_SIREN = "siren"
private const val KEY_FLASHLIGHT = "flashlight"
private const val KEY_BLE = "ble_alarm"

/**
 * Lab 7: what the user configures — the smart scenarios and the settings of the main guard.
 * The rules live in Room, so they survive a restart and work without the Internet. One instance per app
 * (AppContainer): the Scenarios screen edits the data and SafeGuard executes it, both through the same Flows.
 */
class ScenarioRepository(
    private val dao: ScenarioRuleDao,
    private val prefs: SharedPreferences
) {
    // Two quick taps on "Add rule" must not insert the same rule twice
    private val addMutex = Mutex()

    val rules: Flow<List<ScenarioRule>> = dao.observeAll()

    /** null — the same rule already exists, nothing is added. */
    suspend fun add(rule: ScenarioRule): Long? = withContext(Dispatchers.IO) {
        addMutex.withLock {
            if (dao.getAll().any { it.isSameAs(rule) }) {
                Log.d(TAG, "Duplicate ignored: ${rule.describe()}")
                return@withLock null
            }
            dao.insert(rule).also { Log.d(TAG, "INSERT $SCENARIO_RULES_TABLE #$it: ${rule.describe()}") }
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setEnabled(id, enabled)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }

    // ---------- Main guard settings ----------

    private val _guardSettings = MutableStateFlow(loadGuardSettings())
    val guardSettings: StateFlow<GuardSettings> = _guardSettings.asStateFlow()

    suspend fun saveGuardSettings(settings: GuardSettings) = withContext(Dispatchers.IO) {
        _guardSettings.value = settings         // SafeGuard applies the new values at once
        prefs.edit()
            .putFloat(KEY_OPEN, settings.openThreshold)
            .putLong(KEY_CONFIRM, settings.confirmMs)
            .putBoolean(KEY_AI, settings.aiAlarm)
            .putBoolean(KEY_SIREN, settings.siren)
            .putBoolean(KEY_FLASHLIGHT, settings.flashlight)
            .putBoolean(KEY_BLE, settings.bleAlarm)
            .apply()
        Log.d(TAG, "Guard settings saved: $settings")
    }

    private fun loadGuardSettings(): GuardSettings {
        val default = GuardSettings()
        return GuardSettings(
            openThreshold = prefs.getFloat(KEY_OPEN, default.openThreshold),
            confirmMs = prefs.getLong(KEY_CONFIRM, default.confirmMs),
            aiAlarm = prefs.getBoolean(KEY_AI, default.aiAlarm),
            siren = prefs.getBoolean(KEY_SIREN, default.siren),
            flashlight = prefs.getBoolean(KEY_FLASHLIGHT, default.flashlight),
            bleAlarm = prefs.getBoolean(KEY_BLE, default.bleAlarm)
        )
    }
}
