package com.example.lab1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lab1.analytics.AnalyticsScreen
import com.example.lab1.analytics.AnalyticsViewModel
import com.example.lab1.ble.BleScreen
import com.example.lab1.ble.BleViewModel
import com.example.lab1.control.ControlScreen
import com.example.lab1.control.ControlViewModel
import com.example.lab1.monitor.MonitorAction
import com.example.lab1.monitor.MonitorScreen
import com.example.lab1.monitor.MonitorViewModel
import com.example.lab1.scenarios.ScenariosScreen
import com.example.lab1.scenarios.ScenariosViewModel

class MainActivity : ComponentActivity() {
    // The same ViewModels survive a screen rotation
    private val monitorVm: MonitorViewModel by viewModels { MonitorViewModel.Factory }
    private val bleVm: BleViewModel by viewModels { BleViewModel.Factory }
    private val controlVm: ControlViewModel by viewModels { ControlViewModel.Factory }
    private val analyticsVm: AnalyticsViewModel by viewModels { AnalyticsViewModel.Factory }
    private val scenariosVm: ScenariosViewModel by viewModels { ScenariosViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SafeBoxTabs(monitorVm, bleVm, controlVm, analyticsVm, scenariosVm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        monitorVm.onAction(MonitorAction.Start)    // the screen is visible → listen to the sensor
    }

    override fun onPause() {
        monitorVm.onAction(MonitorAction.Stop)     // the screen is hidden → unregister, save the battery
        super.onPause()
    }
}

// Lab 7: the main screen became the Dashboard, the user rules got their own tab
private val TABS = listOf("Dashboard", "BLE", "Control", "Analytics", "Scenarios")

/** Switches between the screens; every screen takes its State from its own ViewModel. */
@Composable
private fun SafeBoxTabs(
    monitorVm: MonitorViewModel,
    bleVm: BleViewModel,
    controlVm: ControlViewModel,
    analyticsVm: AnalyticsViewModel,
    scenariosVm: ScenariosViewModel
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                // Five tabs do not fit the width of a phone, so the row scrolls
                PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
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
            0 -> MonitorScreen(monitorVm, snackbarHostState, contentModifier)
            1 -> BleScreen(bleVm, snackbarHostState, contentModifier)
            2 -> ControlScreen(controlVm, contentModifier)
            3 -> AnalyticsScreen(analyticsVm, contentModifier)
            else -> ScenariosScreen(scenariosVm, snackbarHostState, contentModifier)
        }
    }
}
