package com.cctvscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val viewModel: NetworkScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NetworkScannerScreen(viewModel)
                }
            }
        }
        viewModel.initialize(this)
    }

    override fun onDestroy() {
        viewModel.cancelScan()
        super.onDestroy()
    }
}

@Composable
fun NetworkScannerScreen(viewModel: NetworkScanViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("NETWORK SCANNER", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ethernet: ${state.interfaceLabel}")
                Text("Status: ${state.linkStatus}")
                Text("Speed: ${state.linkSpeed}")
                Text("IP: ${state.ipAddress}")
                Text("Subnet: ${state.subnetMask}")
                Text("Gateway: ${state.gateway}")
                Text("MAC: ${state.macAddress}")
            }
        }

        Button(
            onClick = { viewModel.startScan() },
            enabled = !state.isScanning && !state.isOnvifScanning,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(if (state.isScanning) "IP Scanning..." else "SCAN NETWORK (IP)")
        }

        Button(
            onClick = { viewModel.startOnvifDiscovery() },
            enabled = !state.isScanning && !state.isOnvifScanning,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(if (state.isOnvifScanning) "ONVIF Discovering..." else "ONVIF DISCOVERY")
        }

        Button(
            onClick = { viewModel.startSpecoDiscovery() },
            enabled = !state.isScanning && !state.isOnvifScanning && state.devices.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text("SPECO DISCOVERY")
        }

        Button(
            onClick = { viewModel.startCombinedScan() },
            enabled = !state.isScanning && !state.isOnvifScanning,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            val scanning = state.isScanning || state.isOnvifScanning
            Text(if (scanning) "Scanning..." else "COMBINED SCAN")
        }

        OutlinedButton(
            onClick = { viewModel.cancelScan() },
            enabled = state.isScanning || state.isOnvifScanning,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text("CANCEL")
        }

        Text("Devices Found: ${state.devices.size}", style = MaterialTheme.typography.bodyMedium)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.devices) { device ->
                DeviceCard(device)
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DiscoveredDevice) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(device.ipAddress, style = MaterialTheme.typography.titleSmall)
            Text(device.status, style = MaterialTheme.typography.bodySmall)

            if (device.macAddress != null) {
                Text("MAC: ${device.macAddress}", style = MaterialTheme.typography.labelSmall)
            }

            if (device.manufacturer != null) {
                Text("Mfg: ${device.manufacturer}", style = MaterialTheme.typography.labelSmall)
            }
            if (device.model != null) {
                Text("Model: ${device.model}", style = MaterialTheme.typography.labelSmall)
            }

            if (device.openPorts.isNotEmpty()) {
                Text("Ports: ${device.openPorts.joinToString()}", style = MaterialTheme.typography.labelSmall)
            }

            if (device.onvifSupported) {
                Text("✓ ONVIF", style = MaterialTheme.typography.labelSmall)
            }

            if (device.discoveryMethods.isNotEmpty()) {
                Text("Discovery: ${device.discoveryMethods.joinToString()}", style = MaterialTheme.typography.labelSmall)
            }

            Text("Response: ${device.responseTimeMs}ms", style = MaterialTheme.typography.labelSmall)

            // Show configuration option if supported
            if (ConfigurationProviderFactory.supportsConfiguration(device)) {
                OutlinedButton(
                    onClick = { /* TODO: Open configuration screen */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("CONFIGURE")
                }
            }
        }
    }
}
