package com.example.lab1.analytics

import com.example.lab1.AppConstants
import com.example.lab1.repository.SensorRecord

// State — everything the "Analytics" screen shows. The screen only reads the data,
// so it has neither actions nor events.
data class AnalyticsState(
    val chartRecords: List<SensorRecord> = emptyList(),
    val alarmRecords: List<SensorRecord> = emptyList(),
    val totalRecords: Int = 0,
    val openThreshold: Float = AppConstants.SAFE_BOX_OPEN_THRESHOLD_LUX     // Lab 7: set by the user
)
