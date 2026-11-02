package com.lab3.monitor

import com.lab3.helpers.BleDevice
import com.lab3.helpers.ConnectionState
import com.lab3.helpers.LogEntry

enum class Forecast(val label: String) {
    UNKNOWN("Недостатньо даних"),
    IMPROVING("Погода покращується (тиск росте)"),
    STABLE("Погода стабільна"),
    DETERIORATING("Погіршення погоди (тиск падає)"),
    STORM("Різке падіння тиску — можливий шторм!")
}

object Thresholds {
    // Для демонстрації на емуляторі вікно коротке (60 с).
    // У реальній метеостанції: 3 години (3 * 60 * 60 * 1000L).
    const val WINDOW_MS = 60_000L
    const val SAMPLE_INTERVAL_MS = 1_000L

    // Пороги зміни тиску (гПа) за вікно (для демо масштабовано)
    const val RISE_HPA = 1.0f
    const val FALL_HPA = -1.0f
    const val STORM_HPA = -3.0f

    // Діапазон для ProgressBar
    const val P_MIN = 950f
    const val P_MAX = 1050f
}

/** Стан хмарної синхронізації: індикатор зелений лише для SYNCED, червоний для PENDING і FAILED. */
enum class SyncStatus(val label: String) {
    IDLE("Очікування першої відправки"),
    SYNCED("Синхронізовано"),
    PENDING("Pending: немає зв'язку, дані в черзі"),
    FAILED("Помилка запису в хмару")
}

// State — все, що показуємо.
data class MonitorState(
    // Сенсори
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val pressureHpa: Float? = null,
    val deltaHpa: Float = 0f,
    val forecast: Forecast = Forecast.UNKNOWN,
    val accelAvailable: Boolean = true,
    val baroAvailable: Boolean = true,
    // BLE
    val permissionsGranted: Boolean = false,
    val isScanning: Boolean = false,
    val devices: List<BleDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val connectedDevice: BleDevice? = null,
    // Хмара
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val isOnline: Boolean = true,
    val syncedCount: Int = 0,
    val lastSyncedAt: Long? = null,
    // Термінал
    val log: List<LogEntry> = emptyList()
) {
    /** Під час сканування показуємо SCANNING, але не затираємо статус активного з'єднання. */
    val displayedConnection: ConnectionState
        get() = if (isScanning && !connectionState.isLinked) ConnectionState.SCANNING else connectionState
}

// Action — усі дії користувача і події життєвого циклу цього екрана.
sealed interface MonitorAction {
    data object Start : MonitorAction
    data object Stop : MonitorAction
    data class PermissionsChanged(val granted: Boolean) : MonitorAction
    data object ScanClick : MonitorAction
    data class ConnectClick(val device: BleDevice) : MonitorAction
    data object DisconnectClick : MonitorAction
    data object SendTestPacketClick : MonitorAction
    data class HubCommandClick(val command: String) : MonitorAction
    data object SimulateLossClick : MonitorAction
    data object ClearLogClick : MonitorAction
}

// Event — разові повідомлення від ViewModel до екрана.
sealed interface MonitorEvent {
    data class ShowMessage(val text: String) : MonitorEvent
}
