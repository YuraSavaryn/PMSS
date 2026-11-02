package com.lab3.helpers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Абстракція BLE-з'єднання. Застосунок працює лише з цим інтерфейсом,
 * тому Mock-реалізацію можна замінити реальною (BluetoothGatt) без змін в UI.
 *
 * Усі операції асинхронні (suspend), щоб не блокувати головний потік.
 */
interface IBleConnector {

    /** Поточний стан з'єднання (StateFlow для UI). */
    val connectionState: StateFlow<ConnectionState>

    /** Вхідні повідомлення від пристрою (імітація Notify). */
    val incoming: SharedFlow<String>

    /** Підключення до пристрою. Повертає true при успіху. */
    suspend fun connect(device: BleDevice): Boolean

    /** Розрив з'єднання. */
    suspend fun disconnect()

    /** Відправка даних на пристрій (імітація Write). Повертає true при успіху. */
    suspend fun sendData(data: String): Boolean
}

/** Абстракція сканера: Flow видає знайдені пристрої по мірі виявлення. */
interface IBleScanner {
    fun scan(): Flow<BleDevice>
}
