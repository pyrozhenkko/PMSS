package com.example.lab1.repository

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

private const val TAG = "SafeBox/RemoteControl"

// Placeholder credentials for manual Firebase initialization against the local emulator
private const val EMULATOR_APP_ID = "1:1:android:safeboxguardian"
private const val EMULATOR_API_KEY = "local-emulator"

/** Control command from the devices/{deviceId}/control branch. */
data class ControlCommand(
    val alarm: Boolean = false,
    val flashlight: Boolean = false,
    val armed: Boolean = true,
    val ledStatus: String = CloudSchema.LED_OFF       // green | red | off
)

/** Actual state of the device for the devices/{deviceId}/status branch. */
data class DeviceStatus(
    val alarm: Boolean,
    val flashlight: Boolean,
    val safeState: String,      // CLOSED | OPEN
    val source: String          // AUTO | MANUAL | CLOUD
)

/**
 * Cloud-to-Device: listens to the control branch through the Firebase SDK (WebSocket),
 * so a command from the console arrives instantly, without polling the server.
 */
class RemoteControlSource(context: Context, dbUrl: String, namespace: String?, deviceId: String) {

    private val deviceRef: DatabaseReference? = try {
        // The local emulator receives the database name in the ns parameter
        val url = if (namespace == null) dbUrl else "$dbUrl?${CloudSchema.NAMESPACE_PARAM}=$namespace"
        val app = FirebaseApp.getApps(context).firstOrNull()      // present if google-services.json was added
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(EMULATOR_APP_ID)
                    .setApiKey(EMULATOR_API_KEY)
                    .setDatabaseUrl(url)
                    .build()
            )
        FirebaseDatabase.getInstance(app, url).getReference(CloudSchema.DEVICES).child(deviceId)
    } catch (e: RuntimeException) {
        Log.e(TAG, "Firebase unavailable: ${e.message}")
        null
    }

    val isAvailable: Boolean get() = deviceRef != null

    fun observe(): Flow<ControlCommand> {
        val ref = deviceRef?.child(CloudSchema.CONTROL) ?: return emptyFlow()
        return callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        // First launch: create the branch so it is visible in the console
                        ref.setValue(defaultControl())
                        return
                    }
                    val command = ControlCommand(
                        alarm = snapshot.child(CloudSchema.FIELD_ALARM).getValue(Boolean::class.java) ?: false,
                        flashlight = snapshot.child(CloudSchema.FIELD_FLASHLIGHT).getValue(Boolean::class.java) ?: false,
                        armed = snapshot.child(CloudSchema.FIELD_ARMED).getValue(Boolean::class.java) ?: true,
                        ledStatus = snapshot.child(CloudSchema.FIELD_LED_STATUS).getValue(String::class.java)
                            ?: CloudSchema.LED_OFF
                    )
                    Log.d(TAG, "control changed: $command")
                    trySend(command)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Listener cancelled: ${error.message}")
                }
            }
            ref.addValueEventListener(listener)
            // Unsubscribe when the collector is cancelled — otherwise the listener would live forever
            awaitClose { ref.removeEventListener(listener) }
        }
    }

    /** Alarm log in the events branch (a security event with its time and source). */
    fun logEvent(type: String, lux: Float, source: String) {
        deviceRef?.child(CloudSchema.EVENTS)?.push()?.setValue(
            mapOf(
                CloudSchema.FIELD_TYPE to type,
                CloudSchema.FIELD_LUX to lux,
                CloudSchema.FIELD_SOURCE to source,
                CloudSchema.FIELD_TIMESTAMP to System.currentTimeMillis()
            )
        )
    }

    /**
     * Device-to-Cloud: the actual state of the siren and the floodlight. It goes to its own branch,
     * not to control — otherwise the app would read its own report back as a command and the alarm would stick.
     */
    fun reportStatus(status: DeviceStatus) {
        deviceRef?.child(CloudSchema.STATUS)?.setValue(
            mapOf(
                CloudSchema.FIELD_ALARM to status.alarm,
                CloudSchema.FIELD_FLASHLIGHT to status.flashlight,
                CloudSchema.FIELD_SAFE_STATE to status.safeState,
                CloudSchema.FIELD_SOURCE to status.source,
                CloudSchema.FIELD_TIMESTAMP to System.currentTimeMillis()
            )
        )
    }

    fun setArmed(armed: Boolean) {
        deviceRef?.child(CloudSchema.CONTROL)?.child(CloudSchema.FIELD_ARMED)?.setValue(armed)
    }

    private fun defaultControl() = mapOf(
        CloudSchema.FIELD_ALARM to false,
        CloudSchema.FIELD_FLASHLIGHT to false,
        CloudSchema.FIELD_ARMED to true,
        CloudSchema.FIELD_LED_STATUS to CloudSchema.LED_OFF
    )
}
