package com.example.lab1.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lab1.AppConstants
import com.example.lab1.helpers.AccessClass
import com.example.lab1.helpers.GuardState
import com.example.lab1.helpers.Prediction
import com.example.lab1.helpers.SafeState
import com.example.lab1.helpers.ble.ConnectionState
import com.example.lab1.repository.SensorRecord

/**
 * "Dashboard" tab (the main screen): the state of the safe and of every module of the system in real time —
 * the sensor, the AI, the cloud, the BLE hub, the quick actuator buttons and the last database records.
 */
@Composable
fun MonitorScreen(vm: MonitorViewModel, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()

    // One-shot events (for example, "No Internet") are shown in a Snackbar
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MonitorEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    MonitorContent(state = state, onAction = vm::onAction, modifier = modifier)
}

/** A pure function: State in, Action out. Easy to preview. */
@Composable
fun MonitorContent(state: MonitorState, onAction: (MonitorAction) -> Unit, modifier: Modifier = Modifier) {
    val dashboard = state.dashboard
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!state.sensorAvailable) {
            Text("The device has no light sensor (TYPE_LIGHT)", color = MaterialTheme.colorScheme.error)
        }
        SafeStatusCard(state.isSafeBreached, state.lightLevel)
        AiInsightsCard(state.ai)
        CloudSyncIndicator(status = state.syncStatus, isOnline = state.isOnline, deviceId = state.deviceId)
        BleStatusCard(dashboard.bleConnection)
        QuickControlsCard(state.guard, dashboard.actuators, onAction)
        RecentRecordsCard(dashboard.recentRecords, dashboard.pendingCount)
    }
}

/** The safe state and the light level (Lab 1) — the first thing the owner looks at. */
@Composable
private fun SafeStatusCard(isSafeBreached: Boolean, lightLevel: Float) {
    val statusText = if (isSafeBreached) "WARNING: Safe Opened! (Light Detected)" else "Safe Closed (Darkness)"
    val statusColor = if (isSafeBreached) Color.Red else Color(0xFF2E7D32)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(statusText, color = statusColor, style = MaterialTheme.typography.titleLarge)
            Text("Illuminance (TYPE_LIGHT): $lightLevel lux", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { (lightLevel / AppConstants.MAX_PROGRESS_LUX).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(10.dp),
                color = statusColor
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun MonitorScreenPreview() {
    val now = System.currentTimeMillis()
    val records = listOf(60f, 60f, 3f).mapIndexed { i, lux ->
        SensorRecord(
            id = i.toLong(), timestamp = now - i * 5000L, sensorValue = lux, actionTriggered = i == 1,
            state = if (lux > 15f) SafeState.OPEN.name else SafeState.CLOSED.name, synced = i > 0
        )
    }
    MaterialTheme {
        MonitorContent(
            state = MonitorState(
                lightLevel = 60f,
                isSafeBreached = true,
                syncStatus = SyncStatus.Synced(now),
                deviceId = "safebox-1a2b3c4d",
                ai = AiState(
                    available = true,
                    prediction = Prediction(AccessClass.OPENED, 0.92f, listOf(0.01f, 0.05f, 0.92f, 0.02f))
                ),
                guard = GuardState(safeState = SafeState.OPEN, alarm = true, flashlight = true),
                dashboard = DashboardState(records, pendingCount = 1, bleConnection = ConnectionState.Connected)
            ),
            onAction = {}
        )
    }
}
