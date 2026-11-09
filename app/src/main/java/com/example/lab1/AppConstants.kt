package com.example.lab1

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

    // Lab 3: cloud synchronization (every saved reading goes to Room first, then to the cloud)
    const val NETWORK_TIMEOUT_MS = 10_000L
    const val PENDING_QUEUE_MAX = 200        // unsynced rows sent in one pass after the network is back

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

    // Lab 7: dashboard, smart scenarios and robustness
    const val DASHBOARD_RECENT_RECORDS = 5         // the last DB records on the dashboard
    const val SCENARIO_CONFIRM_MS = 500L           // a lux rule fires only if its condition holds this long
    const val SCENARIO_HYSTERESIS_LUX = 2f         // a lux rule is released only 2 lux past its threshold
    const val SCENARIO_VIBRATION_PULSE_MS = 150L   // the scenario vibration is a soft pulse, not the siren
    const val SCENARIO_VIBRATION_PAUSE_MS = 850L
    const val SCENARIO_DEFAULT_THRESHOLD = "10"    // the example from the task: "if X > 10, vibrate"
    const val STORAGE_FULL_TRIM = 500              // the oldest synced rows deleted when the disk is full

    // Lab 7: the user settings of the main guard (the defaults are the values of Labs 4 and 6)
    const val GUARD_CLOSE_RATIO = SAFE_BOX_CLOSE_THRESHOLD_LUX / SAFE_BOX_OPEN_THRESHOLD_LUX   // 15 → 10 lux
    const val GUARD_THRESHOLD_MIN = 1f
    const val GUARD_THRESHOLD_MAX = 1000f
    val GUARD_CONFIRM_OPTIONS_MS = listOf(200L, 500L, 1000L, 2000L)

    // Shared date/time patterns for the UI and logs
    const val TIME_FORMAT = "HH:mm:ss"
    const val TIME_FORMAT_MILLIS = "HH:mm:ss.SSS"
    const val DATE_TIME_FORMAT = "dd.MM HH:mm:ss"
}
