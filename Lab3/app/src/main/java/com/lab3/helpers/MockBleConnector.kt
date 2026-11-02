package com.lab3.helpers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Mock-реалізація BLE-з'єднання з віртуальною метеостанцією-хабом.
 *
 * Що імітується:
 *  - асинхронне підключення із затримкою (CONNECTING -> CONNECTED);
 *  - Write: запис даних із затримкою, серіалізований через Mutex
 *    (BLE-стек дозволяє лише одну операцію запису одночасно);
 *  - перешкоди в ефірі: пакет втрачається з ймовірністю [packetLossProbability],
 *    застосунок повторює відправку до [maxRetries] разів;
 *  - Notify: відповіді хаба (ACK) і власні команди хаба (PING кожні 20 с);
 *  - обрив зв'язку (для тестування стійкості).
 *
 * @param scope область корутин для фонових подій (зазвичай viewModelScope)
 */
class MockBleConnector(
    private val scope: CoroutineScope,
    private val packetLossProbability: Double = 0.1,
    private val maxRetries: Int = 3
) : IBleConnector {

    companion object {
        const val CONNECT_DELAY_MS = 1_500L
        const val DISCONNECT_DELAY_MS = 300L
        const val WRITE_LATENCY_MS = 200L
        const val RETRY_BACKOFF_MS = 300L
        const val ACK_DELAY_MS = 100L
        const val HUB_HEARTBEAT_MS = 20_000L

        private const val CH_STATUS = "Status"
        private const val CH_ALERT = "Alert"
        private const val CH_COMMAND = "Command"
    }

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    private val writeMutex = Mutex()
    private var heartbeatJob: Job? = null

    // ------------------------------------------------------------
    // connect / disconnect
    // ------------------------------------------------------------

    override suspend fun connect(device: BleDevice): Boolean {
        if (_connectionState.value.isLinked) {
            ExchangeLog.sys("Вже підключено до ${device.name}")
            return true
        }

        _connectionState.value = ConnectionState.CONNECTING
        ExchangeLog.sys("Підключення до ${device.name} (${device.address})...")

        try {
            delay(CONNECT_DELAY_MS) // імітація встановлення GATT-з'єднання
        } catch (e: CancellationException) {
            _connectionState.value = ConnectionState.DISCONNECTED
            ExchangeLog.sys("Підключення скасовано")
            throw e
        }

        if (device.address != BleProfile.DEVICE_ADDRESS) {
            _connectionState.value = ConnectionState.ERROR
            ExchangeLog.sys("Помилка: на ${device.name} немає сервісу ${BleProfile.SERVICE_UUID}")
            return false
        }

        _connectionState.value = ConnectionState.CONNECTED
        ExchangeLog.sys("GATT підключено. Сервіс ${BleProfile.SERVICE_UUID}")
        ExchangeLog.sys("Notify увімкнено: Status (${BleProfile.CHAR_STATUS_UUID})")
        ExchangeLog.sys("Notify увімкнено: Alert (${BleProfile.CHAR_ALERT_UUID})")
        startHeartbeat()
        return true
    }

    override suspend fun disconnect() {
        stopHeartbeat()
        val current = _connectionState.value
        if (current == ConnectionState.IDLE || current == ConnectionState.DISCONNECTED) return

        delay(DISCONNECT_DELAY_MS)
        _connectionState.value = ConnectionState.DISCONNECTED
        ExchangeLog.sys("Відключено від пристрою")
    }

    // ------------------------------------------------------------
    // Write (застосунок -> хаб)
    // ------------------------------------------------------------

    override suspend fun sendData(data: String): Boolean = writeMutex.withLock {
        if (!_connectionState.value.isLinked) {
            ExchangeLog.sys("Запис відхилено: немає з'єднання")
            return@withLock false
        }

        _connectionState.value = ConnectionState.DATA_TRANSFER
        val channel = if (data.startsWith(BleProfile.PREFIX_ALERT)) CH_ALERT else CH_STATUS

        var attempt = 1
        while (attempt <= maxRetries) {
            val suffix = if (attempt > 1) "  (спроба $attempt/$maxRetries)" else ""
            ExchangeLog.tx("[$channel] $data$suffix")

            delay(WRITE_LATENCY_MS) // час передачі пакета в ефірі

            if (!_connectionState.value.isLinked) {
                ExchangeLog.sys("Зв'язок втрачено під час передачі")
                return@withLock false
            }

            val lost = Random.nextDouble() < packetLossProbability
            if (!lost) {
                _connectionState.compareAndSet(ConnectionState.DATA_TRANSFER, ConnectionState.CONNECTED)
                ExchangeLog.sys("Пакет доставлено (${data.length} байт)")
                scope.launch {
                    delay(ACK_DELAY_MS)
                    emitIncoming(CH_STATUS, BleProfile.RESP_ACK)
                }
                return@withLock true
            }

            ExchangeLog.sys("Пакет втрачено (перешкоди), повтор через $RETRY_BACKOFF_MS мс")
            delay(RETRY_BACKOFF_MS)
            attempt++
        }

        _connectionState.compareAndSet(ConnectionState.DATA_TRANSFER, ConnectionState.CONNECTED)
        ExchangeLog.sys("Не вдалося доставити пакет після $maxRetries спроб")
        false
    }

    // ------------------------------------------------------------
    // Notify (хаб -> застосунок) і тестові функції
    // ------------------------------------------------------------

    /** Імітує запис команди хаба в характеристику Command (для кнопок тестування в UI). */
    fun simulateHubCommand(command: String) {
        if (!_connectionState.value.isLinked) {
            ExchangeLog.sys("Команду хаба не відправлено: немає з'єднання")
            return
        }
        scope.launch { emitIncoming(CH_COMMAND, command) }
    }

    /** Імітує раптовий обрив зв'язку (вихід за зону дії, перешкоди). */
    fun simulateConnectionLoss() {
        stopHeartbeat()
        if (_connectionState.value.isLinked) {
            _connectionState.value = ConnectionState.ERROR
            ExchangeLog.sys("Зв'язок втрачено (імітація обриву)")
        }
    }

    private suspend fun emitIncoming(channel: String, text: String) {
        ExchangeLog.rx("[$channel] $text")
        _incoming.emit(text)
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HUB_HEARTBEAT_MS)
                if (_connectionState.value.isLinked) {
                    emitIncoming(CH_COMMAND, BleProfile.CMD_PING)
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
