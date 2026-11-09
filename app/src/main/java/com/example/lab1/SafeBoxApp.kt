package com.example.lab1

import android.app.Application
import android.content.Context
import com.example.lab1.helpers.DeviceIdProvider
import com.example.lab1.helpers.NetworkMonitor
import com.example.lab1.helpers.SafeGuard
import com.example.lab1.helpers.SmartActuator
import com.example.lab1.helpers.ble.IBleConnector
import com.example.lab1.helpers.ble.MockBleConnector
import com.example.lab1.helpers.ble.MockBleScanner
import com.example.lab1.repository.AppDatabase
import com.example.lab1.repository.CloudApi
import com.example.lab1.repository.GUARD_PREFS
import com.example.lab1.repository.RemoteControlSource
import com.example.lab1.repository.ScenarioRepository
import com.example.lab1.repository.SensorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Retrofit requires a valid base URL even when FIREBASE_DB_URL is not configured. */
private const val FALLBACK_BASE_URL = "http://localhost/"

/**
 * Manual dependency injection (without Hilt): the repository and the shared helpers are created once
 * and live as long as the application process, so every screen works with the same data.
 * This class is the only place that knows the concrete implementations; the ViewModels receive
 * ready objects through their constructors (Factory), so a mock can be swapped in one line.
 */
class AppContainer(context: Context) {
    // SupervisorJob — a failure of one background task does not cancel the others
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val bleScanner = MockBleScanner()

    // The type is the interface: the ViewModel does not know a mock is behind it
    val bleConnector: IBleConnector = MockBleConnector(appScope)

    // Lab 3: the database URL comes from local.properties through BuildConfig, not from the code
    private val dbUrl = BuildConfig.FIREBASE_DB_URL
    private val namespace = BuildConfig.FIREBASE_DB_NS.ifBlank { null }
    val deviceIdProvider = DeviceIdProvider(context)
    val networkMonitor = NetworkMonitor(context, appScope)
    private val database = AppDatabase.getInstance(context)

    // The single repository: Room (Lab 5) + REST (Lab 3) + Firebase commands (Lab 4)
    val sensorRepository = SensorRepository(
        dao = database.sensorRecordDao(),
        api = CloudApi.create(dbUrl.ifBlank { FALLBACK_BASE_URL }),
        remote = RemoteControlSource(context, dbUrl, namespace, deviceIdProvider.deviceId),
        networkMonitor = networkMonitor,
        deviceId = deviceIdProvider.deviceId,
        namespace = namespace,
        isConfigured = dbUrl.isNotBlank()
    )

    // Lab 7: the user smart scenarios (Room, the same database) and the settings of the main guard
    val scenarioRepository = ScenarioRepository(
        dao = database.scenarioRuleDao(),
        prefs = context.getSharedPreferences(GUARD_PREFS, Context.MODE_PRIVATE)
    )

    // Lab 4: the safe protection shared by the Monitor, Control and BLE screens; executes the scenarios too
    val safeGuard = SafeGuard(SmartActuator(context), sensorRepository, scenarioRepository)
}

class SafeBoxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
