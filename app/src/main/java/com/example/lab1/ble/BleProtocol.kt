package com.example.lab1.ble

/** Text protocol of the virtual lock: commands, lock statuses and message prefixes. */
object BleProtocol {
    // Commands written to the Command characteristic
    const val CMD_PING = "PING"
    const val CMD_LOCK = "LOCK"
    const val CMD_UNLOCK = "UNLOCK"
    const val CMD_ALARM_ON = "ALARM_ON"
    const val CMD_ALARM_OFF = "ALARM_OFF"
    val COMMANDS = listOf(CMD_PING, CMD_LOCK, CMD_UNLOCK, CMD_ALARM_ON, CMD_ALARM_OFF)

    // Lock statuses reported through the Status characteristic
    const val LOCK_CLOSED = "CLOSED"
    const val LOCK_OPEN = "OPEN"
    const val LOCK_ALARM = "ALARM"

    // Prefixes of the messages sent by the device
    const val ACK_PREFIX = "ACK:"
    const val STATUS_PREFIX = "STATUS:"
    const val BATTERY_PREFIX = ";BAT="
    const val LUX_PREFIX = "LUX:"
}
