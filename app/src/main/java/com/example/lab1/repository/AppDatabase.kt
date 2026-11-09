package com.example.lab1.repository

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.lab1.helpers.SafeState
import kotlinx.coroutines.flow.Flow

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
    val actionTriggered: Boolean,       // true — the alarm (actuator) fired at this moment
    val state: String = SafeState.CLOSED.name,  // safe state at the time of measurement, goes to the cloud
    val synced: Boolean = false         // false — not in the cloud yet, sent when the network is back
)

/** Room verifies the SQL queries at compile time, not at run time. */
@Dao
interface SensorRecordDao {

    @Insert
    suspend fun insert(record: SensorRecord)

    /** The last N records in chronological order — for the chart. */
    @Query(
        "SELECT * FROM (SELECT * FROM $SENSOR_RECORDS_TABLE ORDER BY timestamp DESC LIMIT :n) " +
            "ORDER BY timestamp ASC"
    )
    fun observeLast(n: Int): Flow<List<SensorRecord>>

    /** Alarm log: the newest on top. */
    @Query("SELECT * FROM $SENSOR_RECORDS_TABLE WHERE actionTriggered = 1 ORDER BY timestamp DESC LIMIT :n")
    fun observeAlarms(n: Int): Flow<List<SensorRecord>>

    @Query("SELECT COUNT(*) FROM $SENSOR_RECORDS_TABLE")
    fun observeCount(): Flow<Int>

    /** Lab 7: the newest records on top — the dashboard shows the last 5. */
    @Query("SELECT * FROM $SENSOR_RECORDS_TABLE ORDER BY timestamp DESC LIMIT :n")
    fun observeRecent(n: Int): Flow<List<SensorRecord>>

    /** Lab 7: the size of the offline queue, live — the dashboard sees it fill up and drain. */
    @Query("SELECT COUNT(*) FROM $SENSOR_RECORDS_TABLE WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    /** The offline queue: the oldest first, so the cloud receives the readings in their order. */
    @Query("SELECT * FROM $SENSOR_RECORDS_TABLE WHERE synced = 0 ORDER BY timestamp ASC LIMIT :n")
    suspend fun unsynced(n: Int): List<SensorRecord>

    @Query("SELECT COUNT(*) FROM $SENSOR_RECORDS_TABLE WHERE synced = 0")
    suspend fun countUnsynced(): Int

    @Query("UPDATE $SENSOR_RECORDS_TABLE SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    /** Only synced rows: a reading that has not reached the cloud yet is never lost by the cleanup. */
    @Query("DELETE FROM $SENSOR_RECORDS_TABLE WHERE timestamp < :before AND synced = 1")
    suspend fun deleteOlderThan(before: Long): Int

    /** Lab 7: frees space when the disk is full — the oldest history that is already in the cloud goes first. */
    @Query(
        "DELETE FROM $SENSOR_RECORDS_TABLE WHERE id IN " +
            "(SELECT id FROM $SENSOR_RECORDS_TABLE WHERE synced = 1 ORDER BY timestamp ASC LIMIT :n)"
    )
    suspend fun deleteOldestSynced(n: Int): Int
}

/**
 * Version 2 adds the offline queue (state, synced). The existing rows are marked as synced,
 * otherwise the whole old history would be sent to the cloud after the update.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE $SENSOR_RECORDS_TABLE ADD COLUMN state TEXT NOT NULL DEFAULT '${SafeState.CLOSED.name}'")
        db.execSQL("ALTER TABLE $SENSOR_RECORDS_TABLE ADD COLUMN synced INTEGER NOT NULL DEFAULT 1")
    }
}

/** Version 3 (Lab 7) adds the table of the user smart scenarios. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `$SCENARIO_RULES_TABLE` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `condition` TEXT NOT NULL, " +
                "`threshold` REAL NOT NULL, `aiClass` TEXT NOT NULL, `action` TEXT NOT NULL, " +
                "`enabled` INTEGER NOT NULL)"
        )
    }
}

/**
 * Room database (an abstraction over SQLite). A singleton: several instances
 * of the same database would compete for the file and miss each other's changes.
 */
@Database(entities = [SensorRecord::class, ScenarioRule::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sensorRecordDao(): SensorRecordDao

    abstract fun scenarioRuleDao(): ScenarioRuleDao

    companion object {
        private const val DB_NAME = "safebox.db"

        // @Volatile — a write to INSTANCE is immediately visible to all threads
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                // second check inside the lock (double-checked locking)
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,     // not an Activity — that would leak memory
                    AppDatabase::class.java,
                    DB_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
