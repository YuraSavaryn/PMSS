package com.lab2.ble

import java.util.UUID

/**
 * Профіль віртуального BLE-пристрою «Weather Station Hub».
 * Ці значення йдуть у таблицю «Опис протоколу» у звіті.
 */
object BleProfile {

    const val DEVICE_NAME = "WeatherHub-01"
    const val DEVICE_ADDRESS = "AA:BB:CC:DD:EE:01"

    // ---- UUID ----
    /** Сервіс метеостанції. */
    val SERVICE_UUID: UUID = UUID.fromString("7a1c0001-5b3e-4f8a-9c2d-1e6f0a4b8c10")

    /** Status: Read + Notify. Телеметрія: тиск, зміна, прогноз. */
    val CHAR_STATUS_UUID: UUID = UUID.fromString("7a1c0002-5b3e-4f8a-9c2d-1e6f0a4b8c10")

    /** Alert: Notify. Тривога при різкому падінні тиску. */
    val CHAR_ALERT_UUID: UUID = UUID.fromString("7a1c0004-5b3e-4f8a-9c2d-1e6f0a4b8c10")

    // ---- Команди (Write у Command) ----
    const val CMD_PING = "PING"
    const val CMD_GET_STATUS = "GET_STATUS"
    const val CMD_RESET_HISTORY = "RESET_HISTORY"

    // ---- Відповіді / повідомлення ----
    const val RESP_PONG = "PONG"
    const val RESP_ACK = "ACK"
    const val RESP_ERR_UNKNOWN = "ERR:UNKNOWN_CMD"

    // ---- Префікси пакетів ----
    /** Телеметрія: STATUS:P=1012.3;D=-0.40;FC=STABLE */
    const val PREFIX_STATUS = "STATUS:"

    /** Тривога: ALERT:STORM */
    const val PREFIX_ALERT = "ALERT:"
}
