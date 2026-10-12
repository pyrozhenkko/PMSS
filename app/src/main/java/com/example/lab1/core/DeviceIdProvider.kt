package com.example.lab1.core

import android.content.Context
import java.util.UUID

/**
 * Stable installation identifier: generated once and stored in SharedPreferences,
 * so the cloud can tell devices apart without access to the IMEI.
 */
class DeviceIdProvider(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val deviceId: String by lazy {
        prefs.getString(KEY, null) ?: "$ID_PREFIX${UUID.randomUUID().toString().take(ID_SUFFIX_LENGTH)}".also {
            prefs.edit().putString(KEY, it).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "safebox_prefs"
        const val KEY = "device_id"
        const val ID_PREFIX = "safebox-"
        const val ID_SUFFIX_LENGTH = 8
    }
}
