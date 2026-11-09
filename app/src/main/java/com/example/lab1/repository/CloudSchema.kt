package com.example.lab1.repository

/** Node and field names of the Realtime Database tree, shared by the REST and SDK access. */
object CloudSchema {
    // Branches under devices/{deviceId}
    const val DEVICES = "devices"
    const val READINGS = "readings"
    const val LATEST = "latest"
    const val CONTROL = "control"
    const val STATUS = "status"
    const val EVENTS = "events"

    // Fields of the control branch
    const val FIELD_ALARM = "alarm"
    const val FIELD_FLASHLIGHT = "flashlight"
    const val FIELD_ARMED = "armed"
    const val FIELD_LED_STATUS = "led_status"

    // Fields of the status branch (the actual state reported by the device)
    const val FIELD_SAFE_STATE = "safe_state"

    // Values of led_status
    const val LED_OFF = "off"
    const val LED_GREEN = "green"
    const val LED_RED = "red"

    // Fields of an events record
    const val FIELD_TYPE = "type"
    const val FIELD_LUX = "lux"
    const val FIELD_SOURCE = "source"
    const val FIELD_TIMESTAMP = "timestamp"

    // Event types and sources
    const val EVENT_ALARM_ON = "ALARM_ON"
    const val EVENT_ALARM_OFF = "ALARM_OFF"
    const val EVENT_AI_PREFIX = "AI_"
    const val EVENT_BLE_PREFIX = "BLE_"
    const val EVENT_SCENARIO_PREFIX = "SCENARIO: "
    const val SOURCE_AI = "AI"

    const val SENSOR_TYPE_LIGHT = "TYPE_LIGHT"

    /** Query parameter with the database name; only the local emulator needs it. */
    const val NAMESPACE_PARAM = "ns"
}
