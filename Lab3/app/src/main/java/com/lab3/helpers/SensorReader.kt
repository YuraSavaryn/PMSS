package com.lab3.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/**
 * Перетворює SensorManager на Flow (акселерометр + барометр).
 * Скасування Flow викликає unregisterListener, тому зупинка в onPause звільняє сенсори.
 */
class SensorReader(context: Context) {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val barometer = manager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val hasAccelerometer: Boolean get() = accelerometer != null
    val hasBarometer: Boolean get() = barometer != null

    /** Значення [x, y, z] у м/с². */
    fun acceleration(): Flow<FloatArray> = readings(accelerometer, SensorManager.SENSOR_DELAY_UI)

    /** Тиск у гПа. */
    fun pressure(): Flow<Float> =
        readings(barometer, SensorManager.SENSOR_DELAY_NORMAL).map { it[0] }

    private fun readings(sensor: Sensor?, delay: Int): Flow<FloatArray> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                trySend(e.values.clone())
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, delay)
        awaitClose { manager.unregisterListener(listener) }
    }
}
