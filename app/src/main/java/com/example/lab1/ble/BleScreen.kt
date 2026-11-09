package com.example.lab1.ble

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lab1.helpers.ble.BleDevice
import com.example.lab1.helpers.ble.BleProtocol
import com.example.lab1.helpers.ble.ConnectionState
import com.example.lab1.AppConstants

private val BLE_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT
)
private val ConnectedGreen = Color(0xFF2E7D32)
private val TerminalBackground = Color(0xFF101418)
private val TerminalText = Color(0xFF7CFC9A)

/**
 * "BLE" tab: scanning, device list, connection status, commands and the exchange terminal.
 */
@Composable
fun BleScreen(vm: BleViewModel, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is BleEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    BleContent(state = state, onAction = vm::onAction, modifier = modifier)
}

/** A pure function: State in, Action out. */
@Composable
fun BleContent(state: BleState, onAction: (BleAction) -> Unit, modifier: Modifier = Modifier) {
    // Runtime permissions are requested before scanning, as for real BLE (Android 12+)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        onAction(if (result.values.all { it }) BleAction.Scan else BleAction.PermissionDenied)
    }
    val isLinked = state.connectedDevice != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { permissionLauncher.launch(BLE_PERMISSIONS) }, enabled = !state.isScanning) {
                Text("Scan for devices")
            }
            Spacer(Modifier.width(12.dp))
            ConnectionStatusChip(state.status)
        }

        // The ProgressBar is visible only while scanning
        if (state.isScanning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        Text(
            text = "Found devices (${state.devices.size}):",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.devices, key = { it.address }) { device ->
                DeviceCard(
                    device = device,
                    isConnected = state.connectedDevice == device,
                    onClick = { onAction(BleAction.Connect(device)) }
                )
            }
        }

        if (isLinked) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BleProtocol.COMMANDS.forEach { command ->
                    OutlinedButton(onClick = { onAction(BleAction.SendCommand(command)) }) { Text(command) }
                }
                OutlinedButton(onClick = { onAction(BleAction.Disconnect) }) { Text("Disconnect") }
            }
        }

        Text(
            text = "Exchange terminal:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
        BleTerminal(lines = state.terminal, modifier = Modifier.weight(1f))
    }
}

/** Status indicator: Connected — green, Error — red, the rest — grey. */
@Composable
private fun ConnectionStatusChip(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.Idle -> "Idle" to Color.Gray
        ConnectionState.Scanning -> "Scanning…" to MaterialTheme.colorScheme.primary
        ConnectionState.Connecting -> "Connecting…" to MaterialTheme.colorScheme.primary
        ConnectionState.Connected -> "Connected" to ConnectedGreen
        ConnectionState.DataTransfer -> "Data Transfer" to ConnectedGreen
        ConnectionState.Disconnected -> "Disconnected" to Color.Gray
        is ConnectionState.Error -> "Error" to Color.Red
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeviceCard(device: BleDevice, isConnected: Boolean, onClick: () -> Unit) {
    val container = if (isConnected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(device.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            Text("${device.rssi} dBm")
        }
    }
}

/** Terminal: monospace font and auto-scroll to the last line. */
@Composable
private fun BleTerminal(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalBackground)
            .padding(8.dp)
    ) {
        items(lines) { line ->
            Text(text = line, color = TerminalText, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun BleScreenPreview() {
    val device = BleDevice(AppConstants.BLE_DEVICE_NAME, AppConstants.BLE_DEVICE_ADDRESS, -55)
    MaterialTheme {
        BleContent(
            state = BleState(
                devices = listOf(device),
                connection = ConnectionState.Connected,
                connectedDevice = device,
                terminal = listOf("12:00:01.120  TX -> SafeBox-Lock-14 [5afe0003]: PING")
            ),
            onAction = {}
        )
    }
}
