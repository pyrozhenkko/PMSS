package com.example.lab1.helpers.ble

import java.util.UUID

/**
 * GATT profile of the virtual lock/siren of the safe.
 * The UUIDs are custom (128 bit), outside the Bluetooth SIG base range,
 * so they do not clash with the standard services.
 */
object BleUuids {
    val SERVICE: UUID = UUID.fromString("5afe0001-7c3b-4b8e-9d2a-1c0de5afe014")

    /** Read / Notify: CLOSED | OPEN | ALARM + battery level. */
    val STATUS: UUID = UUID.fromString("5afe0002-7c3b-4b8e-9d2a-1c0de5afe014")

    /** Write: LOCK, UNLOCK, ALARM_ON, ALARM_OFF, PING. */
    val COMMAND: UUID = UUID.fromString("5afe0003-7c3b-4b8e-9d2a-1c0de5afe014")

    /** Notify: light level inside the safe, lux. */
    val LIGHT_LEVEL: UUID = UUID.fromString("5afe0004-7c3b-4b8e-9d2a-1c0de5afe014")

    /** Standard CCCD descriptor — writing to it enables Notify. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Short form for logs: the first 8 characters of the UUID. */
    fun short(uuid: UUID): String = uuid.toString().substring(0, 8)
}
