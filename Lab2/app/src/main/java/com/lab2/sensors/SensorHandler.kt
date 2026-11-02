package com.lab2.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Клас обробки сенсорів (акселерометр + барометр).
 * Логіка з Л.Р.1 не змінена; тепер він є джерелом даних для BLE-шару.
 */
class SensorHandler(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val _state = MutableStateFlow(
        SensorUiState(
            accelAvailable = accelerometer != null,
            baroAvailable = barometer != null
        )
    )
    val state: StateFlow<SensorUiState> = _state.asStateFlow()

    // Історія тиску: пари (час, значення)
    private val history = ArrayDeque<Pair<Long, Float>>()
    private var lastSampleTime = 0L

    /** Реєстрація слухача — викликати в onResume. */
    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        barometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /** Зняття слухача — викликати в onPause (енергозбереження). */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Скидання історії тиску (потрібно для команди RESET_HISTORY від хаба). */
    fun resetHistory() {
        history.clear()
        lastSampleTime = 0L
        _state.value = _state.value.copy(deltaHpa = 0f, forecast = Forecast.UNKNOWN)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                _state.value = _state.value.copy(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2]
                )
            }
            Sensor.TYPE_PRESSURE -> handlePressure(event.values[0])
        }
    }

    private fun handlePressure(pressure: Float) {
        val now = System.currentTimeMillis()

        // Додаємо в історію не частіше ніж раз на SAMPLE_INTERVAL_MS
        if (now - lastSampleTime >= Thresholds.SAMPLE_INTERVAL_MS) {
            history.addLast(now to pressure)
            lastSampleTime = now
        }
        // Видаляємо застарілі записи за межами вікна
        while (history.isNotEmpty() && now - history.first().first > Thresholds.WINDOW_MS) {
            history.removeFirst()
        }

        val delta = if (history.size >= 2) pressure - history.first().second else 0f
        val forecast = when {
            history.size < 2 -> Forecast.UNKNOWN
            delta <= Thresholds.STORM_HPA -> Forecast.STORM
            delta <= Thresholds.FALL_HPA -> Forecast.DETERIORATING
            delta >= Thresholds.RISE_HPA -> Forecast.IMPROVING
            else -> Forecast.STABLE
        }

        _state.value = _state.value.copy(
            pressureHpa = pressure,
            deltaHpa = delta,
            forecast = forecast
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* не використовується */ }
}