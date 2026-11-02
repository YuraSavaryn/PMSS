package com.lab2.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lab2.ble.BleDevice
import com.lab2.ble.BleProfile
import com.lab2.ble.ConnectionState
import com.lab2.ble.IBleConnector
import com.lab2.ble.IBleScanner
import com.lab2.ble.MockBleConnector
import com.lab2.ble.MockBleScanner
import com.lab2.log.ExchangeLog
import com.lab2.sensors.Forecast
import com.lab2.sensors.SensorHandler
import com.lab2.sensors.SensorUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/** Об'єднаний стан BLE-частини екрана. */
data class BleUiState(
    val scanning: Boolean = false,
    val devices: List<BleDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val connectedDevice: BleDevice? = null
)

/**
 * Зв'язує сенсори (Л.Р.1), BLE-шар (Л.Р.2) і журнал обміну.
 * Працює лише з інтерфейсами IBleScanner / IBleConnector.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val TELEMETRY_INTERVAL_MS = 5_000L
        const val RECONNECT_ATTEMPTS = 3
        const val RECONNECT_DELAY_MS = 2_000L
    }

    // ---- Сенсори ----
    private val sensorHandler = SensorHandler(application)
    val sensorState: StateFlow<SensorUiState> = sensorHandler.state

    // ---- BLE ----
    private val scanner: IBleScanner = MockBleScanner()
    private val mockConnector = MockBleConnector(viewModelScope)
    private val connector: IBleConnector = mockConnector

    private val _scanning = MutableStateFlow(false)
    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)

    val bleState: StateFlow<BleUiState> = combine(
        _scanning, _devices, connector.connectionState, _connectedDevice
    ) { scanning, devices, conn, device ->
        BleUiState(
            scanning = scanning,
            devices = devices,
            // під час сканування показуємо SCANNING, але не затираємо статус активного з'єднання
            connectionState = if (scanning && !conn.isLinked) ConnectionState.SCANNING else conn,
            connectedDevice = device
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BleUiState())

    // ---- Журнал ----
    val log = ExchangeLog.entries

    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var reconnectJob: Job? = null

    init {
        ExchangeLog.sys("Застосунок запущено. BLE-шар: Mock (віртуальний хаб)")

        // 1. Вхідні повідомлення від хаба (Notify / Command)
        viewModelScope.launch {
            connector.incoming.collect { handleIncoming(it) }
        }

        // 2. Тривога: прогноз змінився на STORM -> ALERT на хаб
        viewModelScope.launch {
            sensorHandler.state.map { it.forecast }.distinctUntilChanged().collect { forecast ->
                if (forecast == Forecast.STORM && isLinked()) {
                    connector.sendData("${BleProfile.PREFIX_ALERT}STORM")
                }
            }
        }

        // 3. Періодична телеметрія (лише якщо є дані барометра)
        viewModelScope.launch {
            while (isActive) {
                delay(TELEMETRY_INTERVAL_MS)
                if (isLinked() && sensorHandler.state.value.pressureHpa != null) {
                    connector.sendData(statusPacket())
                }
            }
        }

        // 4. Автоперепідключення після обриву зв'язку (стан ERROR)
        viewModelScope.launch {
            connector.connectionState.collect { state ->
                val device = _connectedDevice.value
                if (state == ConnectionState.ERROR && device != null) {
                    reconnectAfterLoss(device)
                }
            }
        }
    }

    // ------------------------------------------------------------
    // Життєвий цикл сенсорів (викликається з Activity)
    // ------------------------------------------------------------

    fun startSensors() = sensorHandler.start()
    fun stopSensors() = sensorHandler.stop()

    override fun onCleared() {
        sensorHandler.stop()
        super.onCleared()
    }

    // ------------------------------------------------------------
    // Дії користувача
    // ------------------------------------------------------------

    fun startScan() {
        if (scanJob?.isActive == true) return
        _devices.value = emptyList()
        _scanning.value = true
        scanJob = viewModelScope.launch {
            try {
                scanner.scan().collect { device -> _devices.update { it + device } }
            } finally {
                _scanning.value = false
            }
        }
    }

    fun connect(device: BleDevice) {
        if (connectJob?.isActive == true || reconnectJob?.isActive == true) return
        scanJob?.cancel()
        connectJob = viewModelScope.launch {
            val ok = connector.connect(device)
            _connectedDevice.value = if (ok) device else null
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        connectJob?.cancel()
        _connectedDevice.value = null
        viewModelScope.launch { connector.disconnect() }
    }

    /** Ручна відправка пакета телеметрії (для тестування й скриншотів). */
    fun sendTestPacket() {
        viewModelScope.launch { connector.sendData(statusPacket()) }
    }

    /** Імітація команди, яку хаб записує в характеристику Command. */
    fun simulateHubCommand(command: String) = mockConnector.simulateHubCommand(command)

    /** Імітація обриву зв'язку. */
    fun simulateConnectionLoss() = mockConnector.simulateConnectionLoss()

    fun clearLog() = ExchangeLog.clear()

    // ------------------------------------------------------------
    // Внутрішня логіка
    // ------------------------------------------------------------

    private fun isLinked() = connector.connectionState.value.isLinked

    /** Пакет телеметрії: STATUS:P=1012.3;D=-0.40;FC=STABLE */
    private fun statusPacket(): String {
        val s = sensorHandler.state.value
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
                sensorHandler.resetHistory()
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
            _connectedDevice.value = null
        }
    }
}
