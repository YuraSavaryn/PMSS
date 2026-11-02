package com.myapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------------------------------------------------------------
// 1. Моделі даних і порогові значення (Threshold)
// ---------------------------------------------------------------

enum class Forecast(val label: String) {
    UNKNOWN("Недостатньо даних"),
    IMPROVING("Погода покращується (тиск росте)"),
    STABLE("Погода стабільна"),
    DETERIORATING("Погіршення погоди (тиск падає)"),
    STORM("Різке падіння тиску — можливий шторм!")
}

object Thresholds {
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

// ---------------------------------------------------------------
// 2. Клас обробки сенсорів
// ---------------------------------------------------------------

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

// ---------------------------------------------------------------
// 3. Activity з керуванням життєвим циклом
// ---------------------------------------------------------------

class MainActivity : ComponentActivity() {

    private lateinit var sensorHandler: SensorHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorHandler = SensorHandler(this)
        setContent {
            MaterialTheme {
                val state by sensorHandler.state.collectAsState()
                WeatherScreen(state)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorHandler.start()
    }

    override fun onPause() {
        super.onPause()
        sensorHandler.stop()
    }
}

// ---------------------------------------------------------------
// 4. Інтерфейс (Jetpack Compose)
// ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(state: SensorUiState) {
    Scaffold(topBar = { TopAppBar(title = { Text("Weather Station Client") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Акселерометр: координати ---
            Text("Акселерометр (м/с²)", style = MaterialTheme.typography.titleMedium)
            if (!state.accelAvailable) {
                Text("Акселерометр недоступний", color = MaterialTheme.colorScheme.error)
            }
            Text("X: %.2f".format(state.x))
            Text("Y: %.2f".format(state.y))
            Text("Z: %.2f".format(state.z))

            // --- Графічний індикатор положення ---
            TiltIndicator(x = state.x, y = state.y)

            HorizontalDivider()

            // --- Барометр ---
            Text("Барометр", style = MaterialTheme.typography.titleMedium)
            if (!state.baroAvailable) {
                Text("Барометр недоступний на цьому пристрої", color = MaterialTheme.colorScheme.error)
            } else {
                val p = state.pressureHpa
                Text(if (p != null) "Тиск: %.1f гПа".format(p) else "Очікування даних...")
                if (p != null) {
                    val progress =
                        ((p - Thresholds.P_MIN) / (Thresholds.P_MAX - Thresholds.P_MIN))
                            .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("Зміна за вікно: %+.2f гПа".format(state.deltaHpa))
                Text(
                    "Прогноз: ${state.forecast.label}",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

/** "Бульбашковий рівень": кулька зміщується відповідно до нахилу пристрою. */
@Composable
fun TiltIndicator(x: Float, y: Float) {
    val g = SensorManager.GRAVITY_EARTH
    val ballColor = Color(0xFF1E88E5)
    val ringColor = Color.Gray

    Canvas(modifier = Modifier.size(200.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2
        val ballR = radius * 0.12f

        // Нормалізація: ±g -> ±(radius - ballR)
        val maxShift = radius - ballR
        val dx = (-x / g).coerceIn(-1f, 1f) * maxShift
        val dy = (y / g).coerceIn(-1f, 1f) * maxShift

        drawCircle(ringColor, radius, center, style = Stroke(width = 4f))
        drawLine(ringColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 2f)
        drawLine(ringColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 2f)
        drawCircle(ballColor, ballR, Offset(center.x + dx, center.y + dy))
    }
}