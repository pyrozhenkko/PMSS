package com.example.lab1.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lab1.ml.AccessClass
import com.example.lab1.ml.Prediction
import com.example.lab1.ui.AiUiState
import kotlin.math.roundToInt

/**
 * "AI Insights" block: real-time model prediction and the probabilities of all classes.
 */
@Composable
fun AiInsightsCard(state: AiUiState, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("AI Insights", style = MaterialTheme.typography.titleSmall)
            val prediction = state.prediction
            when {
                !state.available -> Text("AI unavailable — threshold logic is active", color = Color.Gray)
                prediction == null -> Text("Collecting the data window (2 s)…", color = Color.Gray)
                else -> {
                    Text(
                        text = "State: ${prediction.accessClass.title} (${percent(prediction.confidence)}%)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = classColor(prediction.accessClass)
                    )
                    AccessClass.entries.forEachIndexed { index, accessClass ->
                        ProbabilityRow(accessClass, prediction.probabilities.getOrElse(index) { 0f })
                    }
                    Text(
                        text = "DB record every ${state.recordIntervalMs / 1000} s" +
                            (state.lastEvent?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbabilityRow(accessClass: AccessClass, probability: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(accessClass.name, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { probability.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            color = classColor(accessClass),
            drawStopIndicator = {}
        )
        Text(
            text = "${percent(probability)}%",
            modifier = Modifier.width(44.dp).padding(start = 6.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun percent(value: Float) = (value * 100).roundToInt()

private fun classColor(accessClass: AccessClass): Color = when (accessClass) {
    AccessClass.CLOSED -> Color(0xFF2E7D32)
    AccessClass.FLICKER -> Color(0xFFF9A825)
    AccessClass.OPENED -> Color(0xFFD32F2F)
    AccessClass.TAMPERING -> Color(0xFF6A1B9A)
}

@Preview(showBackground = true)
@Composable
private fun AiInsightsCardPreview() {
    MaterialTheme {
        AiInsightsCard(
            AiUiState(
                available = true,
                prediction = Prediction(AccessClass.OPENED, 0.92f, listOf(0.01f, 0.05f, 0.92f, 0.02f))
            )
        )
    }
}
