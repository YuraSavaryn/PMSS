package com.lab2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lab2.ble.BleDevice
import com.lab2.ble.BleProfile
import com.lab2.ble.ConnectionState

/**
 * Секція керування BLE: пошук (ProgressBar), список пристроїв,
 * індикатор статусу з'єднання (Connected/Disconnected) і тестові кнопки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BleSection(
    state: BleUiState,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onScan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onDisconnect: () -> Unit,
    onSendTestPacket: () -> Unit,
    onHubCommand: (String) -> Unit,
    onSimulateLoss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val conn = state.connectionState

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("BLE-хаб (Mock)", style = MaterialTheme.typography.titleMedium)

            StatusIndicator(conn, state.connectedDevice?.name)

            // ---- Пошук ----
            if (!permissionsGranted) {
                Text(
                    "Потрібні дозволи Bluetooth для сканування",
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = onRequestPermissions) { Text("Надати дозволи") }
            } else {
                Button(
                    onClick = onScan,
                    enabled = !state.scanning && conn != ConnectionState.CONNECTING,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.scanning) "Сканування..." else "Пошук пристроїв")
                }
            }

            if (state.scanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            HorizontalDivider()

            // ---- Список знайдених пристроїв ----
            Text("Знайдені пристрої", style = MaterialTheme.typography.titleSmall)
            if (state.devices.isEmpty()) {
                Text(
                    if (state.scanning) "Пошук..." else "Поки порожньо. Натисніть «Пошук пристроїв».",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.devices.forEach { device ->
                DeviceRow(
                    device = device,
                    isCurrent = state.connectedDevice?.address == device.address,
                    connectionState = conn,
                    onConnect = { onConnect(device) },
                    onDisconnect = onDisconnect
                )
            }

            // ---- Тестові кнопки (лише при активному з'єднанні) ----
            if (conn.isLinked) {
                HorizontalDivider()
                Text("Тестування обміну", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(onClick = onSendTestPacket) { Text("Надіслати пакет") }
                    OutlinedButton(onClick = { onHubCommand(BleProfile.CMD_PING) }) { Text("Хаб: PING") }
                    OutlinedButton(onClick = { onHubCommand(BleProfile.CMD_GET_STATUS) }) { Text("Хаб: GET_STATUS") }
                    OutlinedButton(onClick = { onHubCommand(BleProfile.CMD_RESET_HISTORY) }) { Text("Хаб: RESET_HISTORY") }
                    OutlinedButton(onClick = onSimulateLoss) { Text("Імітувати обрив") }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(state: ConnectionState, deviceName: String?) {
    val color = when {
        state.isLinked -> Color(0xFF2E7D32)                                   // зелений
        state == ConnectionState.CONNECTING || state == ConnectionState.SCANNING -> Color(0xFFF9A825) // жовтий
        state == ConnectionState.ERROR -> Color(0xFFC62828)                   // червоний
        else -> Color.Gray
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(14.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        val suffix = if (state.isLinked && deviceName != null) " · $deviceName" else ""
        Text("Статус: ${state.label}$suffix", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DeviceRow(
    device: BleDevice,
    isCurrent: Boolean,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnectedHere = isCurrent && connectionState.isLinked
    val busy = connectionState == ConnectionState.CONNECTING || connectionState.isLinked

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${device.address} · ${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (isConnectedHere) {
            OutlinedButton(onClick = onDisconnect) { Text("Відключити") }
        } else {
            Button(onClick = onConnect, enabled = !busy) { Text("Підключити") }
        }
    }
}
