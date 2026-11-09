package com.example.lab1.analytics

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.lab1.AppConstants
import com.example.lab1.repository.SensorRecord
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ALARM_COLOR = Color.rgb(211, 47, 47)
private val LINE_COLOR = Color.rgb(25, 118, 210)
private const val DATA_SET_LABEL = "lux"

/**
 * Line chart of illuminance (MPAndroidChart inside Compose through AndroidView).
 * X axis — time, Y axis — lux; the red line is the safe opening threshold.
 */
@Composable
fun LightLineChart(records: List<SensorRecord>, threshold: Float, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // factory runs once: only the immutable settings go here
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                axisRight.isEnabled = false
                setNoDataText("No records — change the light level")
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setLabelCount(4, false)
                axisLeft.axisMinimum = 0f
            }
        },
        update = { chart ->
            // Lab 7: the threshold line follows the user setting of the main guard
            chart.axisLeft.removeAllLimitLines()
            chart.axisLeft.addLimitLine(
                LimitLine(threshold, "threshold ${threshold.toInt()} lux").apply {
                    lineColor = ALARM_COLOR
                    lineWidth = 1.5f
                    enableDashedLine(12f, 8f, 0f)
                }
            )
            // update is called on every change of records — that is, after every INSERT
            if (records.isEmpty()) {
                chart.clear()
                return@AndroidView
            }
            // A Float cannot hold Unix time in milliseconds, so X is seconds from the first point
            val start = records.first().timestamp
            val entries = records.map { Entry((it.timestamp - start) / 1000f, it.sensorValue) }
            val dataSet = LineDataSet(entries, DATA_SET_LABEL).apply {
                color = LINE_COLOR
                lineWidth = 2f
                setDrawValues(false)
                circleRadius = 3.5f
                setDrawCircleHole(false)
                // points where the alarm fired are red
                circleColors = records.map { if (it.actionTriggered) ALARM_COLOR else LINE_COLOR }
            }
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                private val format = SimpleDateFormat(AppConstants.TIME_FORMAT, Locale.getDefault())
                override fun getFormattedValue(value: Float): String =
                    format.format(Date(start + (value * 1000).toLong()))
            }
            chart.data = LineData(dataSet)
            chart.invalidate()      // redraw with the new data
        }
    )
}
