package com.example.lab1

import com.example.lab1.helpers.AccessClass
import com.example.lab1.helpers.ScenarioAction
import com.example.lab1.helpers.ScenarioCondition
import com.example.lab1.helpers.ScenarioEngine
import com.example.lab1.repository.ScenarioRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ScenarioEngine is pure Kotlin, so the smart scenarios are tested on the JVM, like DecisionEngine. */
class ScenarioEngineTest {

    private val vibrateAbove10 = ScenarioRule(
        id = 1, condition = ScenarioCondition.LUX_ABOVE, threshold = 10f, action = ScenarioAction.VIBRATION
    )

    @Test
    fun luxRule_firesOnlyAfterConfirmTime() {
        val engine = ScenarioEngine()
        val first = engine.evaluate(listOf(vibrateAbove10), 12f, null, 0)
        assertTrue(first.actions.isEmpty())
        assertTrue(first.isPending)
        val confirmed = engine.evaluate(listOf(vibrateAbove10), 12f, null, 500)
        assertEquals(setOf(ScenarioAction.VIBRATION), confirmed.actions)
        assertEquals(listOf(vibrateAbove10), confirmed.fired)
        // already active — it is not reported as fired a second time
        assertTrue(engine.evaluate(listOf(vibrateAbove10), 12f, null, 600).fired.isEmpty())
    }

    @Test
    fun shortSpike_isFilteredOut() {
        val engine = ScenarioEngine()
        engine.evaluate(listOf(vibrateAbove10), 40f, null, 0)
        // the spike lasted 200 ms — noise, not a reason to vibrate
        val result = engine.evaluate(listOf(vibrateAbove10), 3f, null, 200)
        assertTrue(result.actions.isEmpty())
        assertFalse(result.isPending)
        assertTrue(engine.evaluate(listOf(vibrateAbove10), 3f, null, 900).actions.isEmpty())
    }

    @Test
    fun hysteresis_keepsActionWhileLuxJittersAroundThreshold() {
        val engine = ScenarioEngine()
        engine.evaluate(listOf(vibrateAbove10), 11f, null, 0)
        engine.evaluate(listOf(vibrateAbove10), 11f, null, 500)
        // 9.5 lux is below the threshold but inside the 2 lux band
        assertTrue(ScenarioAction.VIBRATION in engine.evaluate(listOf(vibrateAbove10), 9.5f, null, 600).actions)
        assertTrue(engine.evaluate(listOf(vibrateAbove10), 7.9f, null, 700).actions.isEmpty())
    }

    @Test
    fun belowRule_firesInDarkness() {
        val rule = ScenarioRule(id = 2, condition = ScenarioCondition.LUX_BELOW, threshold = 5f, action = ScenarioAction.CLOUD_EVENT)
        val engine = ScenarioEngine()
        engine.evaluate(listOf(rule), 2f, null, 0)
        assertEquals(setOf(ScenarioAction.CLOUD_EVENT), engine.evaluate(listOf(rule), 2f, null, 500).actions)
        assertTrue(engine.evaluate(listOf(rule), 8f, null, 600).actions.isEmpty())
    }

    @Test
    fun disabledOrDeletedRule_releasesItsAction() {
        val engine = ScenarioEngine()
        engine.evaluate(listOf(vibrateAbove10), 20f, null, 0)
        assertTrue(engine.evaluate(listOf(vibrateAbove10), 20f, null, 500).actions.isNotEmpty())
        assertTrue(engine.evaluate(listOf(vibrateAbove10.copy(enabled = false)), 20f, null, 600).actions.isEmpty())
        assertTrue(engine.evaluate(emptyList(), 20f, null, 700).actions.isEmpty())
    }

    @Test
    fun aiRule_firesAtOnceAndIsHeldBetweenFlashes() {
        val rule = ScenarioRule(
            id = 3, condition = ScenarioCondition.AI_CLASS, aiClass = AccessClass.TAMPERING, action = ScenarioAction.BLE_ALARM
        )
        val engine = ScenarioEngine(aiHoldMs = 3000)
        assertEquals(listOf(rule), engine.evaluate(listOf(rule), 3f, AccessClass.TAMPERING, 0).fired)
        // one window labelled FLICKER between the flashes does not switch the hub off
        assertTrue(engine.evaluate(listOf(rule), 3f, AccessClass.FLICKER, 500).actions.isNotEmpty())
        assertTrue(engine.evaluate(listOf(rule), 3f, AccessClass.CLOSED, 3000).actions.isEmpty())
    }
}
