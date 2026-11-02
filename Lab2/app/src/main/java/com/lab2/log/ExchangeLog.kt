package com.lab2.log

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Напрямок повідомлення в терміналі. */
enum class Direction(val tag: String) {
    TX("TX →"),   // відправлено на пристрій
    RX("RX ←"),   // отримано від пристрою
    SYS("SYS")    // системна подія (стан, сканування тощо)
}

data class LogEntry(
    val time: String,
    val direction: Direction,
    val message: String
) {
    override fun toString() = "$time  ${direction.tag}  $message"
}

/**
 * Журнал обміну: кожен запис іде і в Logcat (тег BLE_MOCK),
 * і в StateFlow для відображення в терміналі на екрані.
 */
object ExchangeLog {

    const val TAG = "BLE_MOCK"
    private const val MAX_ENTRIES = 200

    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun tx(message: String) = add(Direction.TX, message)
    fun rx(message: String) = add(Direction.RX, message)
    fun sys(message: String) = add(Direction.SYS, message)

    fun clear() {
        _entries.value = emptyList()
    }

    private fun add(direction: Direction, message: String) {
        val entry = LogEntry(formatter.format(Date()), direction, message)
        Log.d(TAG, "${direction.tag} $message")
        // update{} безпечний при зверненнях з різних потоків
        _entries.update { (it + entry).takeLast(MAX_ENTRIES) }
    }
}
