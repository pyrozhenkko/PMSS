package com.example.lab1

import android.content.Context
import android.database.sqlite.SQLiteFullException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lab1.helpers.NetworkMonitor
import com.example.lab1.helpers.SafeState
import com.example.lab1.repository.AppDatabase
import com.example.lab1.repository.CloudApi
import com.example.lab1.repository.RemoteControlSource
import com.example.lab1.repository.SensorReading
import com.example.lab1.repository.SensorRecord
import com.example.lab1.repository.SensorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Lab 7: "what happens if the phone runs out of space for the database?" — checked on a real SQLite. */
@RunWith(AndroidJUnit4::class)
class StorageFullTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        // A tiny size limit of the database imitates a phone without free space
        db.openHelper.writableDatabase.query("PRAGMA max_page_count = 16").use { it.moveToFirst() }
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun fullStorage_trimsSyncedHistoryAndKeepsTheNewEvent() = runBlocking {
        val dao = db.sensorRecordDao()
        var stored = 0
        try {
            while (true) {
                dao.insert(SensorRecord(timestamp = stored.toLong(), sensorValue = 3f, actionTriggered = false, synced = true))
                stored++
            }
        } catch (e: SQLiteFullException) {
            // the disk is full: the history fills the whole limit
        }
        val repository = SensorRepository(
            dao = dao,
            api = CloudApi.create("http://localhost/"),
            remote = RemoteControlSource(context, "", null, "test"),
            networkMonitor = NetworkMonitor(context, scope),
            deviceId = "test",
            namespace = null,
            isConfigured = false
        )

        // The alarm must be saved anyway, and the app must not crash
        repository.save(SensorReading(60f, SafeState.OPEN, actionTriggered = true))

        assertEquals(1, dao.countUnsynced())
        assertTrue(dao.observeCount().first() < stored)
        println("StorageFullTest: filled $stored rows, after the trim ${dao.observeCount().first()}")
    }
}
