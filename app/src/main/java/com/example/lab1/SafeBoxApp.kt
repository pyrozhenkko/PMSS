package com.example.lab1

import android.app.Application
import android.content.Context
import com.example.lab1.core.DeviceIdProvider
import com.example.lab1.data.local.AppDatabase
import com.example.lab1.data.local.SensorRepository
import com.example.lab1.data.remote.CloudApi
import com.example.lab1.data.remote.CloudRepository
import com.example.lab1.data.remote.NetworkMonitor
import com.example.lab1.data.remote.RemoteControlSource
import com.example.lab1.actuator.SmartActuator
import com.example.lab1.ble.IBleConnector
import com.example.lab1.ble.MockBleConnector
import com.example.lab1.ble.MockBleScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Retrofit requires a valid base URL even when FIREBASE_DB_URL is not configured. */
private const val FALLBACK_BASE_URL = "http://localhost/"

/**
 * Manual dependency injection (without Hilt): all services are created once
 * and live as long as the application process.
 */
class AppContainer(context: Context) {
    // SupervisorJob — a failure of one background task does not cancel the others
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val bleScanner = MockBleScanner()

    // The type is the interface: the ViewModel does not know a mock is behind it
    val bleConnector: IBleConnector = MockBleConnector(appScope)

    // Lab 3: the database URL comes from local.properties through BuildConfig, not from the code
    private val dbUrl = BuildConfig.FIREBASE_DB_URL
    val deviceIdProvider = DeviceIdProvider(context)
    val networkMonitor = NetworkMonitor(context, appScope)
    val cloudRepository = CloudRepository(
        api = CloudApi.create(dbUrl.ifBlank { FALLBACK_BASE_URL }),
        networkMonitor = networkMonitor,
        namespace = BuildConfig.FIREBASE_DB_NS.ifBlank { null },
        isConfigured = dbUrl.isNotBlank()
    )

    // Lab 4: actuators and the channel of control commands from the cloud
    val smartActuator = SmartActuator(context)
    val remoteControl = RemoteControlSource(
        context = context,
        dbUrl = dbUrl,
        namespace = BuildConfig.FIREBASE_DB_NS.ifBlank { null },
        deviceId = deviceIdProvider.deviceId
    )

    // Lab 5: local Room database
    val sensorRepository = SensorRepository(AppDatabase.getInstance(context).sensorRecordDao())
}

class SafeBoxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
