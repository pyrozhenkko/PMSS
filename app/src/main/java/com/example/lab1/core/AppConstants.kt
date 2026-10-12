package com.example.lab1.core

/**
 * Constants for domain logic and system logging.
 */
object AppConstants {
    // Lab 1: safe opening threshold and indicator scale
    const val SAFE_BOX_OPEN_THRESHOLD_LUX = 15f
    const val MAX_PROGRESS_LUX = 40f
    const val LOG_TAG = "SafeBoxGuardian"

    // Lab 2: parameters of the virtual BLE device
    const val BLE_DEVICE_NAME = "SafeBox-Lock-14"
    const val BLE_DEVICE_ADDRESS = "AA:BB:CC:14:00:01"
    const val BLE_SCAN_DURATION_MS = 2000L
    const val BLE_CONNECT_DELAY_MS = 800L
    const val BLE_CONNECT_TIMEOUT_MS = 3000L
    const val BLE_WRITE_DELAY_MS = 300L
    const val BLE_MAX_RETRIES = 3
    const val BLE_RETRY_BASE_DELAY_MS = 400L
    const val BLE_FAILURE_RATE = 0.1f
    const val BLE_NOTIFY_PERIOD_MS = 5000L
    const val TERMINAL_MAX_LINES = 100

    // Lab 3: cloud synchronization
    const val UPLOAD_INTERVAL_MS = 5000L     // no more often than once per 5 s…
    const val UPLOAD_DELTA_LUX = 5f          // …or immediately on a change of Δ
    const val NETWORK_TIMEOUT_MS = 10_000L
    const val PENDING_QUEUE_MAX = 200

    // Lab 4: decision making
    const val SAFE_BOX_CLOSE_THRESHOLD_LUX = 10f   // hysteresis: closes below 10 lux
    const val OPEN_CONFIRM_MS = 500L               // opening debounce

    // Lab 5: local database and chart
    const val RECORD_DELTA_LUX = 2f                // significant change — record immediately
    const val RECORD_INTERVAL_MS = 5000L           // otherwise record on a timer (heartbeat)
    const val CHART_POINTS = 50                    // last N points on the chart
    const val ALARM_LOG_SIZE = 20
    const val RECORD_RETENTION_MS = 24 * 60 * 60 * 1000L

    // Lab 6: on-device AI (same values as in ml_training/train_model.py)
    const val ML_WINDOW = 20                       // samples in the window
    const val ML_SAMPLE_PERIOD_MS = 100L           // 10 Hz → 2 s window
    const val ML_INFERENCE_PERIOD_MS = 500L        // prediction at most twice per second
    const val ML_LUX_MAX = 1000f                   // upper bound of normalization
    const val ML_CONFIDENCE_MIN = 0.7f             // minimum confidence to raise an alarm
    const val RECORD_INTERVAL_FAST_MS = 1000L      // more frequent DB records during an event
    const val ML_ALERT_HOLD_MS = 3000L             // AI alarm is held for another 3 s
    // how long the AI alert of a closed opening can last: the window (2 s) + the hold (3 s)
    const val MUTE_GRACE_MS = ML_WINDOW * ML_SAMPLE_PERIOD_MS + ML_ALERT_HOLD_MS

    // Shared date/time patterns for the UI and logs
    const val TIME_FORMAT = "HH:mm:ss"
    const val TIME_FORMAT_MILLIS = "HH:mm:ss.SSS"
    const val DATE_TIME_FORMAT = "dd.MM HH:mm:ss"
}
