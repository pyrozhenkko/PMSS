package com.example.lab1.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lab1.AppConstants
import com.example.lab1.helpers.ActuatorState
import com.example.lab1.helpers.GuardState
import com.example.lab1.helpers.ble.ConnectionState
import com.example.lab1.helpers.describe
import com.example.lab1.repository.SensorRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OkGreen = Color(0xFF2E7D32)
private val WaitYellow = Color(0xFFF9A825)

/** Lab 7: the state of the BLE mock hub (the BLE screen controls it, the dashboard only shows it). */
@Composable
fun BleStatusCard(connection: ConnectionState, modifier: Modifier = Modifier) {
    val (label, color) = when (connection) {
        ConnectionState.Connected, ConnectionState.DataTransfer ->
            "Connected to ${AppConstants.BLE_DEVICE_NAME}" to OkGreen
        ConnectionState.Scanning, ConnectionState.Connecting -> "$connection…" to WaitYellow
        is ConnectionState.Error -> "Error — ${connection.reason}" to Color.Red
        ConnectionState.Idle, ConnectionState.Disconnected -> "Not connected (BLE tab → Scan)" to Color.Gray
    }
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("BLE hub (mock)", style = MaterialTheme.typography.titleSmall)
                Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Lab 7: the quick actuator buttons — the same commands as on the Control screen, one tap away. */
@Composable
fun QuickControlsCard(guard: GuardState, actuators: ActuatorState, onAction: (MonitorAction) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quick control", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("Armed", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Switch(checked = guard.armed, onCheckedChange = { onAction(MonitorAction.SetArmed(it)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleButton("Siren", actuators.alarmOn, Modifier.weight(1f)) { onAction(MonitorAction.SetAlarm(it)) }
                ToggleButton("Light", actuators.flashlightOn, Modifier.weight(1f)) {
                    onAction(MonitorAction.SetFlashlight(it))
                }
                OutlinedButton(onClick = { onAction(MonitorAction.ResetAlarm) }, modifier = Modifier.weight(1f)) {
                    Text("Reset")
                }
            }
            val vibrating = actuators.pulseOn || actuators.alarmOn
            Text(
                text = "Source: ${guard.source} · vibration: ${if (vibrating) "ON" else "OFF"}",
                style = MaterialTheme.typography.bodySmall
            )
            guard.activeScenarios.forEach { rule ->
                Text("▶ ${rule.describe()}", style = MaterialTheme.typography.bodySmall, color = WaitYellow)
            }
        }
    }
}

/** A filled button means "on"; a tap sends the opposite value. */
@Composable
private fun ToggleButton(label: String, isOn: Boolean, modifier: Modifier, onToggle: (Boolean) -> Unit) {
    if (isOn) {
        Button(
            onClick = { onToggle(false) },
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) { Text("$label ON") }
    } else {
        OutlinedButton(onClick = { onToggle(true) }, modifier = modifier) { Text("$label OFF") }
    }
}

/** Lab 7: the last 5 rows of Room. "queued" turns into "cloud ✓" when the network is back. */
@Composable
fun RecentRecordsCard(records: List<SensorRecord>, pendingCount: Int) {
    val format = SimpleDateFormat(AppConstants.TIME_FORMAT, Locale.getDefault())
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Last ${AppConstants.DASHBOARD_RECENT_RECORDS} records (Room) · not synced: $pendingCount",
                style = MaterialTheme.typography.titleSmall
            )
            if (records.isEmpty()) Text("No records yet", color = Color.Gray)
            records.forEach { record ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${format.format(Date(record.timestamp))}  %5.1f lux  %-6s"
                            .format(record.sensorValue, record.state),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (record.actionTriggered) FontWeight.Bold else FontWeight.Normal,
                        color = if (record.actionTriggered) Color.Red else Color.Unspecified,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (record.synced) "cloud ✓" else "queued",
                        color = if (record.synced) OkGreen else WaitYellow,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
    )
}
