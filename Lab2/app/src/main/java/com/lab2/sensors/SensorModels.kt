package com.lab2.sensors

// ---------------------------------------------------------------
// Моделі даних і порогові значення (без змін відносно Л.Р.1)
// ---------------------------------------------------------------

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

data class SensorUiState(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val pressureHpa: Float? = null,
    val deltaHpa: Float = 0f,
    val forecast: Forecast = Forecast.UNKNOWN,
    val accelAvailable: Boolean = true,
    val baroAvailable: Boolean = true
)