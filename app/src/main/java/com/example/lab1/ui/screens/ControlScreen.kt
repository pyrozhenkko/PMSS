package com.example.lab1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lab1.actuator.ActuatorState
import com.example.lab1.data.remote.CloudSchema
import com.example.lab1.ui.CommandSource
import com.example.lab1.ui.ControlUiState

/**
 * "Control" tab (Manual Control): actuator switches, the virtual lamp
 * controlled by the cloud, and the explanation of the current DecisionEngine decision.
 */
@Composable
fun ControlScreen(
    state: ControlUiState,
    actuators: ActuatorState,
    onArmedChange: (Boolean) -> Unit,
    onFlashlightChange: (Boolean) -> Unit,
    onAlarmChange: (Boolean) -> Unit,
    onResetAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Manual Control", style = MaterialTheme.typography.titleLarge)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ControlSwitch("Guard (Armed)", state.armed, onArmedChange)
                ControlSwitch(
                    label = if (actuators.hasFlash) "Flashlight (floodlight)" else "Flashlight (virtual floodlight)",
                    checked = actuators.flashlightOn,
                    onCheckedChange = onFlashlightChange
                )
                ControlSwitch(
                    label = if (actuators.hasVibrator) "Siren / vibration" else "Siren (no vibration motor)",
                    checked = actuators.alarmOn,
                    onCheckedChange = onAlarmChange
                )
            }
        }

        Button(onClick = onResetAlarm, modifier = Modifier.fillMaxWidth()) {
            Text("Reset alarm (AUTO mode)")
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                VirtualLamp(state.ledStatus)
                Spacer(Modifier.size(16.dp))
                Column {
                    Text("Virtual lamp", style = MaterialTheme.typography.titleMedium)
                    Text("control/led_status = \"${state.ledStatus}\"")
                    Text(
                        text = if (state.cloudControlAvailable) "Cloud control: connected"
                        else "Cloud control: unavailable",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Command source: ${state.source}", fontWeight = FontWeight.Bold)
                Text("Decision: ${state.reason}")
                Spacer(Modifier.height(4.dp))
                Text("Siren: ${onOff(actuators.alarmOn)} · Floodlight: ${onOff(actuators.flashlightOn)}")
            }
        }
    }
}

@Composable
private fun ControlSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A circle whose colour is set by the led_status value in Firebase. */
@Composable
private fun VirtualLamp(ledStatus: String) {
    val color = when (ledStatus) {
        CloudSchema.LED_GREEN -> Color(0xFF2E7D32)
        CloudSchema.LED_RED -> Color.Red
        else -> Color.LightGray        // "off" or an unknown value
    }
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, Color.DarkGray, CircleShape)
    )
}

private fun onOff(value: Boolean) = if (value) "ON" else "OFF"

@Preview(showBackground = true)
@Composable
private fun ControlScreenPreview() {
    MaterialTheme {
        ControlScreen(
            state = ControlUiState(ledStatus = CloudSchema.LED_RED, source = CommandSource.CLOUD),
            actuators = ActuatorState(alarmOn = true, hasVibrator = true),
            onArmedChange = {}, onFlashlightChange = {}, onAlarmChange = {}, onResetAlarm = {}
        )
    }
}
