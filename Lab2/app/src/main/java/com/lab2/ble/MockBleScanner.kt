package com.lab2.ble

import com.lab2.log.ExchangeLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Імітація BLE-сканера.
 * Через 2 секунди "знаходить" віртуальну метеостанцію, трохи пізніше ще один
 * сторонній пристрій (SmartRelay-02). До нього підключення навмисно завершується
 * помилкою, бо на ньому немає потрібного сервісу. Це зручно для демонстрації
 * стану ERROR у звіті.
 */
class MockBleScanner : IBleScanner {

    companion object {
        const val FIRST_DEVICE_DELAY_MS = 2_000L
        const val SECOND_DEVICE_DELAY_MS = 600L
        const val FINISH_DELAY_MS = 400L

        const val DECOY_NAME = "SmartRelay-02"
        const val DECOY_ADDRESS = "AA:BB:CC:DD:EE:02"
    }

    override fun scan(): Flow<BleDevice> = flow {
        ExchangeLog.sys("Сканування розпочато...")

        delay(FIRST_DEVICE_DELAY_MS)
        val hub = BleDevice(BleProfile.DEVICE_NAME, BleProfile.DEVICE_ADDRESS, rssi = -58)
        ExchangeLog.sys("Знайдено: ${hub.name} (${hub.address}), RSSI ${hub.rssi} dBm")
        emit(hub)

        delay(SECOND_DEVICE_DELAY_MS)
        val decoy = BleDevice(DECOY_NAME, DECOY_ADDRESS, rssi = -74)
        ExchangeLog.sys("Знайдено: ${decoy.name} (${decoy.address}), RSSI ${decoy.rssi} dBm")
        emit(decoy)

        delay(FINISH_DELAY_MS)
        ExchangeLog.sys("Сканування завершено")
    }
}
