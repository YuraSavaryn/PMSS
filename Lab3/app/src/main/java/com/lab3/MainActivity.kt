package com.lab3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.lab3.monitor.MonitorAction
import com.lab3.monitor.MonitorScreen
import com.lab3.monitor.MonitorViewModel
import com.lab3.ui.theme.Lab3Theme

class MainActivity : ComponentActivity() {

    private val vm: MonitorViewModel by viewModels()

    private val requiredPermissions =
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            vm.onAction(MonitorAction.PermissionsChanged(result.values.all { it }))
        }

    private fun hasAllPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null && !hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            Lab3Theme {
                MonitorScreen(
                    vm = vm,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Користувач міг змінити дозвіл у налаштуваннях
        vm.onAction(MonitorAction.PermissionsChanged(hasAllPermissions()))
        vm.onAction(MonitorAction.Start) // екран видно -> слухаємо сенсори і відправляємо в хмару
    }

    override fun onPause() {
        vm.onAction(MonitorAction.Stop) // екран сховано -> відписуємось, економимо батарею
        super.onPause()
    }
}
