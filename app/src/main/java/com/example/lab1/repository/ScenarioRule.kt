package com.example.lab1.repository

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.lab1.helpers.AccessClass
import com.example.lab1.helpers.ScenarioAction
import com.example.lab1.helpers.ScenarioCondition
import kotlinx.coroutines.flow.Flow

/** Table name shared by the entity, the DAO queries and the migration. */
const val SCENARIO_RULES_TABLE = "scenario_rules"

/**
 * A user smart scenario: "IF <condition> THEN <action>", for example "IF lux > 10 THEN vibration".
 * Room stores the enums as their names (TEXT), so the table stays readable in the Database Inspector.
 */
@Entity(tableName = SCENARIO_RULES_TABLE)
data class ScenarioRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val condition: ScenarioCondition,        // LUX_ABOVE | LUX_BELOW | AI_CLASS
    val threshold: Float = 0f,               // lux; used by LUX_ABOVE / LUX_BELOW
    val aiClass: AccessClass = AccessClass.TAMPERING,   // used by AI_CLASS
    val action: ScenarioAction,  // VIBRATION | FLASHLIGHT | BLE_ALARM | CLOUD_EVENT
    val enabled: Boolean = true
)

@Dao
interface ScenarioRuleDao {

    @Insert
    suspend fun insert(rule: ScenarioRule): Long

    /** Room re-emits the list after every change: a new rule applies at once. */
    @Query("SELECT * FROM $SCENARIO_RULES_TABLE ORDER BY id ASC")
    fun observeAll(): Flow<List<ScenarioRule>>

    @Query("SELECT * FROM $SCENARIO_RULES_TABLE")
    suspend fun getAll(): List<ScenarioRule>

    @Query("UPDATE $SCENARIO_RULES_TABLE SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM $SCENARIO_RULES_TABLE WHERE id = :id")
    suspend fun delete(id: Long)
}
