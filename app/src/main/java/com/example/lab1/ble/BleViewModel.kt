package com.example.lab1.ble

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lab1.AppConstants
import com.example.lab1.SafeBoxApp
import com.example.lab1.helpers.CommandSource
import com.example.lab1.helpers.SafeGuard
import com.example.lab1.helpers.ble.BleDevice
import com.example.lab1.helpers.ble.BleProtocol
import com.example.lab1.helpers.ble.BleUuids
import com.example.lab1.helpers.ble.ConnectionState
import com.example.lab1.helpers.ble.IBleConnector
import com.example.lab1.helpers.ble.MockBleScanner
import com.example.lab1.repository.CloudSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "SafeBox/BleViewModel"
private const val PERMISSION_MESSAGE = "Bluetooth permissions are required for scanning"

/** The "BLE" screen: scanning, connection to the virtual lock, commands and the exchange terminal. */
class BleViewModel(
    private val scanner: MockBleScanner,
    private val connector: IBleConnector,
    private val guard: SafeGuard
) : ViewModel() {

    private val _state = MutableStateFlow(BleState())
    val state: StateFlow<BleState> = _state.asStateFlow()

    private val _events = Channel<BleEvent>()
    val events = _events.receiveAsFlow()

    private var scanJob: Job? = null

    init {
        // Notify from the device — into the terminal
        viewModelScope.launch {
            connector.incoming.collect { appendTerminal("RX <- $it") }
        }
        viewModelScope.launch {
            connector.state.collect { connection ->
                appendTerminal("STATE: ${stateLabel(connection)}")
                val lost = connection is ConnectionState.Disconnected || connection is ConnectionState.Error
                _state.update {
                    it.copy(connection = connection, connectedDevice = if (lost) null else it.connectedDevice)
                }
            }
        }
        // The alarm of the safe also goes to the hub (Lab 2)
        viewModelScope.launch {
            guard.bleCommands.collect(::send)
        }
    }

    fun onAction(action: BleAction) {
        when (action) {
            BleAction.Scan -> startScan()
            BleAction.PermissionDenied -> {
                appendTerminal("ERR: BLUETOOTH_SCAN / BLUETOOTH_CONNECT permissions are missing")
                viewModelScope.launch { _events.send(BleEvent.ShowMessage(PERMISSION_MESSAGE)) }
            }
            is BleAction.Connect -> connect(action.device)
            BleAction.Disconnect -> connector.disconnect()
            is BleAction.SendCommand -> onCommand(action.command)
        }
    }

    private fun startScan() {
        if (_state.value.isScanning) return
        scanJob = viewModelScope.launch {
            _state.update { it.copy(devices = emptyList(), isScanning = true) }
            appendTerminal("SCAN: searching for devices…")
            scanner.scan().collect { device ->
                _state.update { it.copy(devices = it.devices + device) }
                appendTerminal("FOUND: ${device.name} ${device.address} (${device.rssi} dBm)")
            }
            _state.update { it.copy(isScanning = false) }
            appendTerminal("SCAN: finished, found ${_state.value.devices.size}")
        }
    }

    private fun connect(device: BleDevice) {
        viewModelScope.launch {
            appendTerminal("CONNECT -> ${device.name}")
            connector.connect(device)
            if (connector.state.value is ConnectionState.Connected) {
                _state.update { it.copy(connectedDevice = device) }
                // Lab 7: a hub connected during an alarm gets ALARM_ON too, not only at the next change
                if (guard.state.value.hubAlarm) send(BleProtocol.CMD_ALARM_ON)
            }
        }
    }

    /**
     * ALARM_ON / ALARM_OFF also drive the alarm of the phone, so the button, the siren
     * and the virtual lock stay consistent; LOCK / UNLOCK go to the cloud log.
     */
    private fun onCommand(command: String) {
        val alarmBefore = guard.state.value.alarm
        when (command) {
            BleProtocol.CMD_ALARM_ON -> guard.setManualAlarm(true)
            BleProtocol.CMD_ALARM_OFF -> {
                guard.resetAlarm()
                // a cloud command may still force the alarm — the manual switch has priority over it
                if (guard.state.value.alarm) guard.setManualAlarm(false)
            }
            BleProtocol.CMD_LOCK, BleProtocol.CMD_UNLOCK ->
                guard.logEvent(CloudSchema.EVENT_BLE_PREFIX + command, CommandSource.MANUAL.name)
        }
        // If the alarm state changed, the guard has already sent the command to the hub through bleCommands
        if (guard.state.value.alarm == alarmBefore) send(command)
    }

    private fun send(command: String) {
        val target = _state.value.connectedDevice
        if (target == null) {
            // Lab 7: a scenario command without a hub must not vanish silently
            appendTerminal("ERR: $command not sent — no hub connected")
            return
        }
        viewModelScope.launch {
            appendTerminal("TX -> ${target.name} [${BleUuids.short(BleUuids.COMMAND)}]: $command")
            val delivered = connector.sendData(command)
            if (!delivered) appendTerminal("ERR: $command not delivered")
        }
    }

    private fun appendTerminal(message: String) {
        val time = SimpleDateFormat(AppConstants.TIME_FORMAT_MILLIS, Locale.getDefault()).format(Date())
        Log.d(TAG, message)
        // takeLast bounds the memory and the number of lines the LazyColumn redraws
        _state.update { it.copy(terminal = (it.terminal + "$time  $message").takeLast(AppConstants.TERMINAL_MAX_LINES)) }
    }

    private fun stateLabel(state: ConnectionState): String = when (state) {
        is ConnectionState.Error -> "Error (${state.reason})"
        else -> state.toString()
    }

    override fun onCleared() {
        scanJob?.cancel()
        connector.disconnect()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SafeBoxApp).container
                BleViewModel(
                    scanner = container.bleScanner,
                    connector = container.bleConnector,
                    guard = container.safeGuard
                )
            }
        }
    }
}
