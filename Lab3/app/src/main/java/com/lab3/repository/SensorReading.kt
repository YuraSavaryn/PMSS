package com.lab3.repository

const val TYPE_ACCELEROMETER = "ACCELEROMETER"
const val TYPE_BAROMETER = "BAROMETER"

/**
 * Модель, з якою працює ViewModel.
 *
 * @param type назва сенсора
 * @param value основне значення: модуль прискорення (м/с²) або тиск (гПа)
 * @param x,y,z координати акселерометра (для барометра нулі)
 * @param timestamp час вимірювання, мс
 */
data class SensorReading(
    val type: String,
    val value: Float,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

fun SensorReading.toPayload(deviceId: String) = SensorPayload(
    deviceId = deviceId,
    sensorType = type,
    value = value,
    x = x,
    y = y,
    z = z,
    timestamp = timestamp
)
