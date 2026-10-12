package com.example.lab1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lab1.sensor.LightSensorManager
import com.example.lab1.ui.SensorViewModel
import com.example.lab1.ui.screens.AnalyticsScreen
import com.example.lab1.ui.screens.BleScreen
import com.example.lab1.ui.screens.ControlScreen
import com.example.lab1.ui.screens.MonitorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SafeBoxGuardianScreen()
                }
            }
        }
    }
}

private val TABS = listOf("Monitor", "BLE", "Control", "Analytics")

/**
 * Root screen: owns the sensor subscription (strictly tied to the lifecycle)
 * and switches between the tabs. All state lives in SensorViewModel.
 */
@Composable
fun SafeBoxGuardianScreen(viewModel: SensorViewModel = viewModel(factory = SensorViewModel.Factory)) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bleState by viewModel.bleState.collectAsStateWithLifecycle()
    val terminalLog by viewModel.terminalLog.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val controlState by viewModel.controlState.collectAsStateWithLifecycle()
    val actuatorState by viewModel.actuatorState.collectAsStateWithLifecycle()
    val chartRecords by viewModel.chartRecords.collectAsStateWithLifecycle()
    val alarmRecords by viewModel.alarmRecords.collectAsStateWithLifecycle()
    val recordCount by viewModel.recordCount.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // One-shot messages (for example, "No Internet") are shown in a Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val sensorManager = remember { LightSensorManager(context) }

    // Manage sensor listener strictly tied to Activity lifecycle
    DisposableEffect(lifecycleOwner) {
        viewModel.onSensorAvailability(sensorManager.isAvailable)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    sensorManager.startListening { newLevel ->
                        viewModel.onLightChanged(newLevel)
                    }
                    viewModel.onSensorListening(true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    sensorManager.stopListening()
                    viewModel.onSensorListening(false)
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.stopListening()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // statusBarsPadding() to prevent cutout/camera overlap
            Column(
                modifier = Modifier.statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Safe Box Guardian",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            0 -> MonitorScreen(
                state = uiState,
                syncStatus = syncStatus,
                isOnline = isOnline,
                deviceId = viewModel.deviceId,
                aiState = aiState,
                onClearLogs = viewModel::clearLogs,
                modifier = contentModifier
            )
            1 -> BleScreen(
                state = bleState,
                terminalLog = terminalLog,
                onScan = viewModel::startScan,
                onPermissionDenied = viewModel::onBlePermissionDenied,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onCommand = viewModel::onBleCommand,
                modifier = contentModifier
            )
            2 -> ControlScreen(
                state = controlState,
                actuators = actuatorState,
                onArmedChange = viewModel::setArmed,
                onFlashlightChange = viewModel::setManualFlashlight,
                onAlarmChange = viewModel::setManualAlarm,
                onResetAlarm = viewModel::resetAlarm,
                modifier = contentModifier
            )
            else -> AnalyticsScreen(
                chartRecords = chartRecords,
                alarmRecords = alarmRecords,
                totalRecords = recordCount,
                modifier = contentModifier
            )
        }
    }
}
