package com.lab3.helpers

/** Знайдений під час сканування пристрій. */
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

/**
 * Стани підключення (State Machine для діаграми у звіті):
 * IDLE -> SCANNING -> CONNECTING -> CONNECTED <-> DATA_TRANSFER -> DISCONNECTED -> IDLE
 * З будь-якого активного стану можливий перехід у ERROR.
 */
enum class ConnectionState(val label: String) {
    IDLE("Очікування"),
    SCANNING("Сканування"),
    CONNECTING("Підключення"),
    CONNECTED("Connected"),
    DATA_TRANSFER("Передача даних"),
    DISCONNECTED("Disconnected"),
    ERROR("Помилка зв'язку");

    /** true, якщо з'єднання з пристроєм встановлене. */
    val isLinked: Boolean
        get() = this == CONNECTED || this == DATA_TRANSFER
}
