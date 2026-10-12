package com.example.lab1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lab1.core.AccessLogEntry
import com.example.lab1.core.AppConstants
import com.example.lab1.data.remote.SyncStatus
import com.example.lab1.ui.AiUiState
import com.example.lab1.ui.SafeBoxUiState
import com.example.lab1.ui.components.AiInsightsCard
import com.example.lab1.ui.components.CloudSyncIndicator

/**
 * "Monitor" tab: current sensor value, safe state and the log of access attempts (Lab 1).
 */
@Composable
fun MonitorScreen(
    state: SafeBoxUiState,
    syncStatus: SyncStatus,
    isOnline: Boolean,
    deviceId: String,
    aiState: AiUiState,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!state.sensorAvailable) {
            Text(
                text = "The device has no light sensor (TYPE_LIGHT)",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        SensorCoordinatesPanel(state.lightLevel)

        Spacer(modifier = Modifier.height(16.dp))

        SecurityStatusIndicator(state.isSafeBreached, state.lightLevel)

        Spacer(modifier = Modifier.height(16.dp))

        CloudSyncIndicator(status = syncStatus, isOnline = isOnline, deviceId = deviceId)

        Spacer(modifier = Modifier.height(12.dp))

        AiInsightsCard(aiState)

        Spacer(modifier = Modifier.height(12.dp))

        // Log history UI component
        AccessLogsSection(
            accessLogs = state.accessLogs,
            onClearLogs = onClearLogs
        )
    }
}

/**
 * Extracted composable for rendering the sensor coordinates.
 * Fulfills the specific requirement to display X, Y, Z axes.
 */
@Composable
private fun SensorCoordinatesPanel(lightLevel: Float) {
    Text(
        text = "Sensor Axes (TYPE_LIGHT):",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Text(text = "X (Illuminance): $lightLevel lux")
    Text(text = "Y: 0.0 (N/A for light sensor)")
    Text(text = "Z: 0.0 (N/A for light sensor)")
}

/**
 * Extracted composable for rendering the security status and progress bar.
 */
@Composable
private fun SecurityStatusIndicator(isSafeBreached: Boolean, lightLevel: Float) {
    val statusText = if (isSafeBreached) "WARNING: Safe Opened! (Light Detected)" else "Safe Closed (Darkness)"
    val statusColor = if (isSafeBreached) Color.Red else Color.Green

    Text(
        text = statusText,
        color = statusColor,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    val progressValue = (lightLevel / AppConstants.MAX_PROGRESS_LUX).coerceIn(0f, 1f)

    LinearProgressIndicator(
        progress = { progressValue },
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
        color = statusColor,
    )
}

/**
 * Extracted composable for rendering the access attempt logs history.
 */
@Composable
private fun AccessLogsSection(
    accessLogs: List<AccessLogEntry>,
    onClearLogs: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Access Attempt Logs (${accessLogs.size}):",
            style = MaterialTheme.typography.titleMedium
        )
        if (accessLogs.isNotEmpty()) {
            TextButton(onClick = onClearLogs) {
                Text("Clear Logs")
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (accessLogs.isEmpty()) {
        Text(
            text = "No breach attempts logged yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(top = 12.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accessLogs) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Breach at ${entry.timestamp}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "${entry.lightLevel} lux",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonitorScreenPreview() {
    MaterialTheme {
        MonitorScreen(
            state = SafeBoxUiState(
                lightLevel = 27f,
                isSafeBreached = true,
                accessLogs = listOf(AccessLogEntry("12:30:05", 27f))
            ),
            syncStatus = SyncStatus.Synced(0L),
            isOnline = true,
            deviceId = "safebox-1a2b3c4d",
            aiState = AiUiState(),
            onClearLogs = {}
        )
    }
}
