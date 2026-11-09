package com.example.lab1

import com.example.lab1.helpers.DecisionEngine
import com.example.lab1.helpers.SafeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DecisionEngine does not depend on Android, so it is tested on the JVM. */
class DecisionEngineTest {

    @Test
    fun shortFlash_isNotTreatedAsOpening() {
        val engine = DecisionEngine()
        assertEquals(SafeState.CLOSED, engine.evaluate(60f, 0, armed = true).state)
        assertTrue(engine.isOpenPending)
        // the flash lasted only 200 ms
        assertEquals(SafeState.CLOSED, engine.evaluate(3f, 200, armed = true).state)
        assertFalse(engine.isOpenPending)
    }

    @Test
    fun lightHeldForConfirmTime_opensAndTriggersAlarm() {
        val engine = DecisionEngine()
        engine.evaluate(60f, 0, armed = true)
        val decision = engine.evaluate(60f, 500, armed = true)
        assertEquals(SafeState.OPEN, decision.state)
        assertTrue(decision.alarm)
        assertTrue(decision.flashlight)
    }

    @Test
    fun hysteresis_keepsOpenBetweenThresholds() {
        val engine = DecisionEngine()
        engine.evaluate(60f, 0, armed = true)
        engine.evaluate(60f, 500, armed = true)
        // 12 lux: below the opening threshold (15) but above the closing threshold (10)
        assertEquals(SafeState.OPEN, engine.evaluate(12f, 600, armed = true).state)
        assertEquals(SafeState.CLOSED, engine.evaluate(9f, 700, armed = true).state)
    }

    @Test
    fun disarmed_opensWithoutAlarm() {
        val engine = DecisionEngine()
        engine.evaluate(60f, 0, armed = false)
        val decision = engine.evaluate(60f, 500, armed = false)
        assertEquals(SafeState.OPEN, decision.state)
        assertFalse(decision.alarm)
    }

    @Test
    fun manualOverride_hasPriorityOverAutomation() {
        val engine = DecisionEngine()
        val forcedOn = engine.evaluate(3f, 0, armed = true, manualAlarm = true)
        assertTrue(forcedOn.alarm)
        engine.evaluate(60f, 100, armed = true)
        val forcedOff = engine.evaluate(60f, 700, armed = true, manualAlarm = false)
        assertEquals(SafeState.OPEN, forcedOff.state)
        assertFalse(forcedOff.alarm)
    }

    @Test
    fun mlAlert_triggersAlarmEvenBelowDebounce() {
        val engine = DecisionEngine()
        // flickering: the threshold is not confirmed, but the model recognized TAMPERING
        val decision = engine.evaluate(3f, 0, armed = true, mlAlert = true)
        assertEquals(SafeState.CLOSED, decision.state)
        assertTrue(decision.alarm)
        assertFalse(engine.evaluate(3f, 100, armed = false, mlAlert = true).alarm)
    }

    @Test
    fun acknowledge_staysMutedWhileModelStillAlertsAfterClosing() {
        val engine = DecisionEngine()
        engine.evaluate(60f, 0, armed = true)
        engine.evaluate(60f, 500, armed = true, mlAlert = true)
        engine.acknowledge()
        // closed, but the 2 s window of the model still contains the opening
        assertFalse(engine.evaluate(3f, 600, armed = true, mlAlert = true).alarm)
        assertFalse(engine.evaluate(3f, 3600, armed = true, mlAlert = false).alarm)
        // a new threat after the hold has expired raises the alarm again
        assertTrue(engine.evaluate(3f, 4000, armed = true, mlAlert = true).alarm)
    }

    @Test
    fun acknowledge_doesNotMuteQuickReopening() {
        val engine = DecisionEngine()
        engine.evaluate(60f, 0, armed = true)
        engine.evaluate(60f, 500, armed = true, mlAlert = true)
        engine.acknowledge()
        engine.evaluate(3f, 600, armed = true, mlAlert = true)      // closed
        // reopened while the model still holds the previous alert
        engine.evaluate(60f, 700, armed = true, mlAlert = true)
        val decision = engine.evaluate(60f, 1200, armed = true, mlAlert = true)
        assertEquals(SafeState.OPEN, decision.state)
        assertTrue(decision.alarm)
    }

    @Test
    fun acknowledge_doesNotMuteOngoingTamperingForever() {
        val engine = DecisionEngine(muteGraceMs = 5000)
        engine.evaluate(60f, 0, armed = true)
        engine.evaluate(60f, 500, armed = true, mlAlert = true)
        engine.acknowledge()
        assertFalse(engine.evaluate(3f, 600, armed = true, mlAlert = true).alarm)
        assertFalse(engine.evaluate(3f, 5000, armed = true, mlAlert = true).alarm)
        // the model keeps alerting after the grace period — this is a new threat
        assertTrue(engine.evaluate(3f, 5600, armed = true, mlAlert = true).alarm)
    }

    @Test
    fun autoAlarm_isReportedSeparatelyFromManualOverride() {
        val engine = DecisionEngine()
        engine.evaluate(60f, 0, armed = true)
        val forcedOff = engine.evaluate(60f, 500, armed = true, manualAlarm = false)
        assertFalse(forcedOff.alarm)
        assertTrue(forcedOff.autoAlarm)
        val closed = engine.evaluate(3f, 600, armed = true, manualAlarm = false)
        assertFalse(closed.autoAlarm)
    }

    @Test
    fun acknowledge_mutesAlarmUntilNextOpening() {
        val engine = DecisionEngine()
        engine.evaluate(60f, 0, armed = true)
        engine.evaluate(60f, 500, armed = true)
        engine.acknowledge()
        assertFalse(engine.evaluate(60f, 600, armed = true).alarm)
        engine.evaluate(3f, 700, armed = true)          // closed
        engine.evaluate(60f, 800, armed = true)
        assertTrue(engine.evaluate(60f, 1300, armed = true).alarm)
    }
}
