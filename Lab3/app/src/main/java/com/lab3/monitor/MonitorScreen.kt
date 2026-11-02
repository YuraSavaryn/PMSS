package com.lab3.monitor

import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lab3.helpers.BleDevice
import com.lab3.helpers.BleProfile
import com.lab3.helpers.ConnectionState
import com.lab3.helpers.Direction
import com.lab3.helpers.LogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Green = Color(0xFF2E7D32)
private val Yellow = Color(0xFFF9A825)
private val Red = Color(0xFFC62828)

private val TerminalBg = Color(0xFF12161B)
private val ColorTx = Color(0xFF64B5F6)
private val ColorRx = Color(0xFF81C784)
private val ColorSys = Color(0xFFB0BEC5)
private val ColorCloud = Color(0xFFFFB74D)

@Composable
fun MonitorScreen(vm: MonitorViewModel, onRequestPermissions: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Слухаємо разові Event
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is MonitorEvent.ShowMessage ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    MonitorContent(state = state, onAction = vm::onAction, onRequestPermissions = onRequestPermissions)
}

// Чиста функція: State на вході, Action на виході.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorContent(
    state: MonitorState,
    onAction: (MonitorAction) -> Unit,
    onRequestPermissions: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Weather Station Client") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CloudSection(state)
            SensorSection(state)
            BleSection(state, onAction, onRequestPermissions)
            TerminalSection(state.log, onClear = { onAction(MonitorAction.ClearLogClick) })
        }
    }
}

/** Індикатор «Хмарна синхронізація»: зелений, якщо останній запит успішний, інакше червоний. */
@Composable
private fun CloudSection(state: MonitorState) {
    val color = when (state.syncStatus) {
        SyncStatus.SYNCED -> Green
        SyncStatus.PENDING, SyncStatus.FAILED -> Red
        SyncStatus.IDLE -> Color.Gray
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Хмарна синхронізація (Firebase)", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(14.dp).background(color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(state.syncStatus.label, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Мережа: ${if (state.isOnline) "онлайн" else "офлайн"}",
                style = MaterialTheme.typography.bodySmall
            )
            val lastSync = state.lastSyncedAt?.let {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
            } ?: "—"
            Text(
                "Підтверджено записів: ${state.syncedCount} · останній о $lastSync",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Секція сенсорів з Л.Р.1: X/Y/Z, індикатор нахилу, барометр і прогноз. */
@Composable
private fun SensorSection(state: MonitorState) {
    Card(modifier = Modifier.fillMaxWidth()) {
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

/** "Бульбашковий рівень": кулька зміщується відповідно до нахилу пристрою (з Л.Р.1). */
@Composable
private fun TiltIndicator(x: Float, y: Float, modifier: Modifier = Modifier) {
    val g = SensorManager.GRAVITY_EARTH
    val ballColor = Color(0xFF1E88E5)
    val ringColor = Color.Gray

    Canvas(modifier = modifier) {
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

/**
 * Секція керування BLE: пошук (ProgressBar), список пристроїв,
 * індикатор статусу з'єднання (Connected/Disconnected) і тестові кнопки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BleSection(
    state: MonitorState,
    onAction: (MonitorAction) -> Unit,
    onRequestPermissions: () -> Unit
) {
    val conn = state.displayedConnection

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("BLE-хаб (Mock)", style = MaterialTheme.typography.titleMedium)

            BleStatusIndicator(conn, state.connectedDevice?.name)

            // ---- Пошук ----
            if (!state.permissionsGranted) {
                Text(
                    "Потрібні дозволи Bluetooth для сканування",
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = onRequestPermissions) { Text("Надати дозволи") }
            } else {
                Button(
                    onClick = { onAction(MonitorAction.ScanClick) },
                    enabled = !state.isScanning && conn != ConnectionState.CONNECTING,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isScanning) "Сканування..." else "Пошук пристроїв")
                }
            }

            if (state.isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            HorizontalDivider()

            // ---- Список знайдених пристроїв ----
            Text("Знайдені пристрої", style = MaterialTheme.typography.titleSmall)
            if (state.devices.isEmpty()) {
                Text(
                    if (state.isScanning) "Пошук..." else "Поки порожньо. Натисніть «Пошук пристроїв».",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.devices.forEach { device ->
                DeviceRow(
                    device = device,
                    isCurrent = state.connectedDevice?.address == device.address,
                    connectionState = conn,
                    onConnect = { onAction(MonitorAction.ConnectClick(device)) },
                    onDisconnect = { onAction(MonitorAction.DisconnectClick) }
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
                    OutlinedButton(onClick = { onAction(MonitorAction.SendTestPacketClick) }) { Text("Надіслати пакет") }
                    OutlinedButton(onClick = { onAction(MonitorAction.HubCommandClick(BleProfile.CMD_PING)) }) { Text("Хаб: PING") }
                    OutlinedButton(onClick = { onAction(MonitorAction.HubCommandClick(BleProfile.CMD_GET_STATUS)) }) { Text("Хаб: GET_STATUS") }
                    OutlinedButton(onClick = { onAction(MonitorAction.HubCommandClick(BleProfile.CMD_RESET_HISTORY)) }) { Text("Хаб: RESET_HISTORY") }
                    OutlinedButton(onClick = { onAction(MonitorAction.SimulateLossClick) }) { Text("Імітувати обрив") }
                }
            }
        }
    }
}

@Composable
private fun BleStatusIndicator(state: ConnectionState, deviceName: String?) {
    val color = when {
        state.isLinked -> Green
        state == ConnectionState.CONNECTING || state == ConnectionState.SCANNING -> Yellow
        state == ConnectionState.ERROR -> Red
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

/** "Термінал" з журналом обміну; автоматично прокручується до останнього запису. */
@Composable
private fun TerminalSection(entries: List<LogEntry>, onClear: () -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalBg)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Термінал обміну (BLE + Cloud)",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
                TextButton(onClick = onClear) { Text("Очистити") }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(TerminalBg)
            ) {
                items(entries) { entry ->
                    Text(
                        text = entry.toString(),
                        color = when (entry.direction) {
                            Direction.TX -> ColorTx
                            Direction.RX -> ColorRx
                            Direction.SYS -> ColorSys
                            Direction.CLOUD -> ColorCloud
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
