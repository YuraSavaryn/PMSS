package com.lab2.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Головний екран: сенсори, керування BLE, термінал обміну. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val sensorState by viewModel.sensorState.collectAsState()
    val bleState by viewModel.bleState.collectAsState()
    val logEntries by viewModel.log.collectAsState()

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
            SensorSection(state = sensorState)

            BleSection(
                state = bleState,
                permissionsGranted = permissionsGranted,
                onRequestPermissions = onRequestPermissions,
                onScan = viewModel::startScan,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onSendTestPacket = viewModel::sendTestPacket,
                onHubCommand = viewModel::simulateHubCommand,
                onSimulateLoss = viewModel::simulateConnectionLoss
            )

            TerminalView(
                entries = logEntries,
                onClear = viewModel::clearLog
            )
        }
    }
}
