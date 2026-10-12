package com.example.lab1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lab1.core.AppConstants
import com.example.lab1.data.remote.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SyncGreen = Color(0xFF2E7D32)
private val SyncYellow = Color(0xFFF9A825)

/**
 * "Cloud sync" indicator: green — the last request succeeded,
 * red — an error, yellow — data is waiting in the queue.
 */
@Composable
fun CloudSyncIndicator(
    status: SyncStatus,
    isOnline: Boolean,
    deviceId: String,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (status) {
        SyncStatus.Idle -> Color.Gray to "waiting for data"
        SyncStatus.Syncing -> SyncYellow to "sending…"
        is SyncStatus.Synced -> SyncGreen to "Synced at ${formatTime(status.at)}"
        is SyncStatus.Pending -> SyncYellow to "Pending — ${status.count} queued"
        is SyncStatus.Error -> Color.Red to "Error — ${status.message}"
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Cloud sync", style = MaterialTheme.typography.titleSmall)
                Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Network: ${if (isOnline) "online" else "offline"} · ID: $deviceId",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat(AppConstants.TIME_FORMAT, Locale.getDefault()).format(Date(millis))

@Preview(showBackground = true)
@Composable
private fun CloudSyncIndicatorPreview() {
    MaterialTheme {
        CloudSyncIndicator(SyncStatus.Pending(3), isOnline = false, deviceId = "safebox-1a2b3c4d")
    }
}
