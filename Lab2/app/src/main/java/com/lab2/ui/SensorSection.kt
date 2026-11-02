package com.lab2.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lab2.sensors.SensorUiState
import com.lab2.sensors.Thresholds

/** Секція сенсорів з Л.Р.1: X/Y/Z, індикатор нахилу, барометр і прогноз. */
@Composable
fun SensorSection(state: SensorUiState, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Акселерометр (м/с²)", style = MaterialTheme.typography.titleMedium)
            if (!state.accelAvailable) {
                Text("Акселерометр недоступний", color = MaterialTheme.colorScheme.error)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text("X: %.2f".format(state.x))
                Text("Y: %.2f".format(state.y))
                Text("Z: %.2f".format(state.z))
            }

            TiltIndicator(x = state.x, y = state.y, modifier = Modifier.size(140.dp))

            HorizontalDivider()

            Text("Барометр", style = MaterialTheme.typography.titleMedium)
            if (!state.baroAvailable) {
                Text("Барометр недоступний на цьому пристрої", color = MaterialTheme.colorScheme.error)
            } else {
                val p = state.pressureHpa
                Text(if (p != null) "Тиск: %.1f гПа".format(p) else "Очікування даних...")
                if (p != null) {
                    val progress = ((p - Thresholds.P_MIN) / (Thresholds.P_MAX - Thresholds.P_MIN))
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("Зміна за вікно: %+.2f гПа".format(state.deltaHpa))
                Text("Прогноз: ${state.forecast.label}", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
