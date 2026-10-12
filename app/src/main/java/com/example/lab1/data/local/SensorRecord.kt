package com.example.lab1.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Table name shared by the entity and the DAO queries. */
const val SENSOR_RECORDS_TABLE = "sensor_records"

/**
 * A row of the sensor_records table: one reading of the light sensor.
 * The index on timestamp speeds up the "last N records" query for the chart.
 */
@Entity(tableName = SENSOR_RECORDS_TABLE, indices = [Index("timestamp")])
data class SensorRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,                // measurement time, ms since 1970
    val sensorValue: Float,             // illuminance, lux
    val actionTriggered: Boolean        // true — the alarm (actuator) fired at this moment
)
