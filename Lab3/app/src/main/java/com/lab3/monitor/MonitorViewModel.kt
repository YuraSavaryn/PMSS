package com.lab3.monitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lab3.Lab3App
import com.lab3.helpers.BleDevice
import com.lab3.helpers.BleProfile
import com.lab3.helpers.ConnectionState
import com.lab3.helpers.ExchangeLog
import com.lab3.helpers.IBleConnector
import com.lab3.helpers.IBleScanner
import com.lab3.helpers.MockBleConnector
import com.lab3.helpers.MockBleScanner
import com.lab3.helpers.NetworkMonitor
import com.lab3.helpers.SensorReader
import com.lab3.repository.SensorReading
import com.lab3.repository.TYPE_ACCELEROMETER
import com.lab3.repository.TYPE_BAROMETER
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeoutException
import kotlin.math.sqrt

/**
 * Зв'язує сенсори (Л.Р.1), BLE-шар (Л.Р.2) і хмару (Л.Р.3).
 * Працює лише з помічниками та репозиторієм, екран бачить тільки State.
 */
class MonitorViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val TELEMETRY_INTERVAL_MS = 5_000L
        const val CLOUD_SYNC_INTERVAL_MS = 5_000L
        const val RECONNECT_ATTEMPTS = 3
        const val RECONNECT_DELAY_MS = 2_000L
    }

    // ---- Помічники і репозиторій ----
    private val sensorReader = SensorReader(app)
    private val networkMonitor = NetworkMonitor(app)
    private val repository = (app as Lab3App).sensorRepository
    private val scanner: IBleScanner = MockBleScanner()
    private val mockConnector = MockBleConnector(viewModelScope)
    private val connector: IBleConnector = mockConnector

    // State: приватний змінюваний + публічний тільки для читання
    private val _state = MutableStateFlow(
        MonitorState(
            accelAvailable = sensorReader.hasAccelerometer,
            baroAvailable = sensorReader.hasBarometer
        )
    )
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    // Event: Channel доставляє кожну подію рівно один раз
    private val _events = Channel<MonitorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Історія тиску: пари (час, значення)
    private val pressureHistory = ArrayDeque<Pair<Long, Float>>()
    private var lastSampleTime = 0L

    private var monitoringJob: Job? = null
    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var reconnectJob: Job? = null

    init {
        ExchangeLog.sys("Застосунок запущено. BLE-шар: Mock, хмара: Firebase Realtime Database")

        viewModelScope.launch {
            ExchangeLog.entries.collect { entries -> _state.update { it.copy(log = entries) } }
        }

        // Вхідні повідомлення від хаба (Notify / Command)
        viewModelScope.launch {
            connector.incoming.collect { handleIncoming(it) }
        }

        // Стан з'єднання і автоперепідключення після обриву зв'язку (стан ERROR)
        viewModelScope.launch {
            connector.connectionState.collect { conn ->
                _state.update { it.copy(connectionState = conn) }
                val device = _state.value.connectedDevice
                if (conn == ConnectionState.ERROR && device != null) {
                    reconnectAfterLoss(device)
                }
            }
        }

        // Тривога: прогноз змінився на STORM -> ALERT на хаб
        viewModelScope.launch {
            _state.map { it.forecast }.distinctUntilChanged().collect { forecast ->
                if (forecast == Forecast.STORM && isLinked()) {
                    connector.sendData("${BleProfile.PREFIX_ALERT}STORM")
                }
            }
        }

        // Періодична телеметрія на хаб (лише якщо є дані барометра)
        viewModelScope.launch {
            while (isActive) {
                delay(TELEMETRY_INTERVAL_MS)
                if (isLinked() && _state.value.pressureHpa != null) {
                    connector.sendData(statusPacket())
                }
            }
        }
    }

    // ЄДИНИЙ вхід у ViewModel — всі дії приходять сюди
    fun onAction(action: MonitorAction) {
        when (action) {
            MonitorAction.Start -> startMonitoring()
            MonitorAction.Stop -> stopMonitoring()
            is MonitorAction.PermissionsChanged -> _state.update { it.copy(permissionsGranted = action.granted) }
            MonitorAction.ScanClick -> startScan()
            is MonitorAction.ConnectClick -> connect(action.device)
            MonitorAction.DisconnectClick -> disconnect()
            MonitorAction.SendTestPacketClick -> viewModelScope.launch { connector.sendData(statusPacket()) }
            is MonitorAction.HubCommandClick -> mockConnector.simulateHubCommand(action.command)
            MonitorAction.SimulateLossClick -> mockConnector.simulateConnectionLoss()
            MonitorAction.ClearLogClick -> ExchangeLog.clear()
        }
    }

    // ------------------------------------------------------------
    // Сенсори, мережа і хмара: працюють, лише поки екран видно
    // ------------------------------------------------------------

    private fun startMonitoring() {
        if (monitoringJob != null) return
        monitoringJob = viewModelScope.launch {
            launch {
                sensorReader.acceleration().collect { v ->
                    _state.update { it.copy(x = v[0], y = v[1], z = v[2]) }
                }
            }
            launch { sensorReader.pressure().collect { handlePressure(it) } }
            launch { networkMonitor.isOnline().collect { onNetworkChanged(it) } }
            launch {
                while (isActive) {
                    delay(CLOUD_SYNC_INTERVAL_MS) // не перевантажуємо мережу
                    syncWithCloud()
                }
            }
        }
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel() // скасування Flow -> unregisterListener()
        monitoringJob = null
    }

    private fun handlePressure(pressure: Float) {
        val now = System.currentTimeMillis()

        // Додаємо в історію не частіше ніж раз на SAMPLE_INTERVAL_MS
        if (now - lastSampleTime >= Thresholds.SAMPLE_INTERVAL_MS) {
            pressureHistory.addLast(now to pressure)
            lastSampleTime = now
        }
        // Видаляємо застарілі записи за межами вікна
        while (pressureHistory.isNotEmpty() && now - pressureHistory.first().first > Thresholds.WINDOW_MS) {
            pressureHistory.removeFirst()
        }

        val delta = if (pressureHistory.size >= 2) pressure - pressureHistory.first().second else 0f
        val forecast = when {
            pressureHistory.size < 2 -> Forecast.UNKNOWN
            delta <= Thresholds.STORM_HPA -> Forecast.STORM
            delta <= Thresholds.FALL_HPA -> Forecast.DETERIORATING
            delta >= Thresholds.RISE_HPA -> Forecast.IMPROVING
            else -> Forecast.STABLE
        }

        _state.update { it.copy(pressureHpa = pressure, deltaHpa = delta, forecast = forecast) }
    }

    /** Скидання історії тиску (команда RESET_HISTORY від хаба). */
    private fun resetPressureHistory() {
        pressureHistory.clear()
        lastSampleTime = 0L
        _state.update { it.copy(deltaHpa = 0f, forecast = Forecast.UNKNOWN) }
    }

    private suspend fun onNetworkChanged(online: Boolean) {
        val wasOnline = _state.value.isOnline
        _state.update {
            it.copy(
                isOnline = online,
                syncStatus = if (online) it.syncStatus else SyncStatus.PENDING
            )
        }
        if (online == wasOnline) return

        val message = if (online) "Мережу відновлено, надсилаю дані" else "Немає мережі: дані чекають у черзі"
        ExchangeLog.cloud(message)
        _events.send(MonitorEvent.ShowMessage(message))
    }

    /** Знімок поточних значень сенсорів, по одному запису на сенсор. */
    private fun syncWithCloud() {
        val s = _state.value
        if (s.accelAvailable) {
            val magnitude = sqrt(s.x * s.x + s.y * s.y + s.z * s.z)
            upload(SensorReading(TYPE_ACCELEROMETER, magnitude, s.x, s.y, s.z))
        }
        s.pressureHpa?.let { upload(SensorReading(TYPE_BAROMETER, it)) }
    }

    private fun upload(reading: SensorReading) = viewModelScope.launch {
        repository.save(reading)
            .onSuccess {
                _state.update {
                    it.copy(
                        syncStatus = SyncStatus.SYNCED,
                        syncedCount = it.syncedCount + 1,
                        lastSyncedAt = reading.timestamp
                    )
                }
                ExchangeLog.cloud("OK ${reading.type} value=%.2f".format(Locale.US, reading.value))
            }
            .onFailure { error ->
                val status = if (error is TimeoutException) SyncStatus.PENDING else SyncStatus.FAILED
                val statusChanged = _state.value.syncStatus != status
                _state.update { it.copy(syncStatus = status) }
                ExchangeLog.cloud("${status.name} ${reading.type}: ${error.message}")
                // Запити йдуть кожні 5 с, тому повідомляємо лише про зміну стану
                if (statusChanged) _events.send(MonitorEvent.ShowMessage(status.label))
            }
    }

    // ------------------------------------------------------------
    // BLE
    // ------------------------------------------------------------

    private fun startScan() {
        if (scanJob?.isActive == true) return
        _state.update { it.copy(devices = emptyList(), isScanning = true) }
        scanJob = viewModelScope.launch {
            try {
                scanner.scan().collect { device ->
                    _state.update { it.copy(devices = it.devices + device) }
                }
            } finally {
                _state.update { it.copy(isScanning = false) }
            }
        }
    }

    private fun connect(device: BleDevice) {
        if (connectJob?.isActive == true || reconnectJob?.isActive == true) return
        scanJob?.cancel()
        connectJob = viewModelScope.launch {
            val ok = connector.connect(device)
            _state.update { it.copy(connectedDevice = if (ok) device else null) }
        }
    }

    private fun disconnect() {
        reconnectJob?.cancel()
        connectJob?.cancel()
        _state.update { it.copy(connectedDevice = null) }
        viewModelScope.launch { connector.disconnect() }
    }

    private fun isLinked() = connector.connectionState.value.isLinked

    /** Пакет телеметрії: STATUS:P=1012.3;D=-0.40;FC=STABLE */
    private fun statusPacket(): String {
        val s = _state.value
        val pressure = s.pressureHpa?.let { "%.1f".format(Locale.US, it) } ?: "N/A"
        val delta = "%+.2f".format(Locale.US, s.deltaHpa)
        return "${BleProfile.PREFIX_STATUS}P=$pressure;D=$delta;FC=${s.forecast.name}"
    }

    private suspend fun handleIncoming(message: String) {
        when (message) {
            BleProfile.RESP_ACK, BleProfile.RESP_PONG -> Unit // підтвердження, відповідати не треба
            BleProfile.CMD_PING -> connector.sendData(BleProfile.RESP_PONG)
            BleProfile.CMD_GET_STATUS -> connector.sendData(statusPacket())
            BleProfile.CMD_RESET_HISTORY -> {
                resetPressureHistory()
                ExchangeLog.sys("Історію тиску скинуто за командою хаба")
                connector.sendData(BleProfile.RESP_ACK)
            }
            else -> {
                ExchangeLog.sys("Невідома команда: $message")
                connector.sendData(BleProfile.RESP_ERR_UNKNOWN)
            }
        }
    }

    private fun reconnectAfterLoss(device: BleDevice) {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            for (attempt in 1..RECONNECT_ATTEMPTS) {
                ExchangeLog.sys("Автоперепідключення: спроба $attempt/$RECONNECT_ATTEMPTS")
                delay(RECONNECT_DELAY_MS)
                if (connector.connect(device)) return@launch
            }
            ExchangeLog.sys("Автоперепідключення не вдалося")
            _state.update { it.copy(connectedDevice = null) }
        }
    }
}
