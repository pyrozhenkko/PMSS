package com.example.lab1.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lab1.AppConstants
import com.example.lab1.repository.SensorRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Analytics" tab: chart of the latest Room records and the alarm log.
 * The data is read from the local database, so the screen works without the Internet too.
 */
@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    AnalyticsContent(state = state, modifier = modifier)
}

/** A pure function of the State. */
@Composable
fun AnalyticsContent(state: AnalyticsState, modifier: Modifier = Modifier) {
    val chartRecords = state.chartRecords
    val alarmRecords = state.alarmRecords
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Illuminance in the safe, lux", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Last ${chartRecords.size} of ${state.totalRecords} records (max ${AppConstants.CHART_POINTS})",
            style = MaterialTheme.typography.bodySmall
        )
        LightLineChart(
            records = chartRecords,
            threshold = state.openThreshold,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(vertical = 8.dp)
        )

        Text(
            text = "Alarm log (${alarmRecords.size}):",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        if (alarmRecords.isEmpty()) {
            Text("No alarms yet.", color = Color.Gray)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(alarmRecords, key = { it.id }) { record -> AlarmRow(record) }
        }
    }
}

@Composable
private fun AlarmRow(record: SensorRecord) {
    val time = SimpleDateFormat(AppConstants.DATE_TIME_FORMAT, Locale.getDefault()).format(Date(record.timestamp))
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
            Text("Alarm · $time", color = MaterialTheme.colorScheme.onErrorContainer)
            Text("${record.sensorValue} lux", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalyticsScreenPreview() {
    val now = System.currentTimeMillis()
    val records = listOf(3f, 4f, 30f, 60f, 20f, 5f).mapIndexed { i, lux ->
        SensorRecord(id = i.toLong(), timestamp = now + i * 2000L, sensorValue = lux, actionTriggered = i == 2)
    }
    MaterialTheme {
        AnalyticsContent(AnalyticsState(records, records.filter { it.actionTriggered }, totalRecords = 6))
    }
}
