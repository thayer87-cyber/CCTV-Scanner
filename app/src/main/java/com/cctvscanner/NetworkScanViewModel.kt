package com.cctvscanner

import android.content.Context
import android.net.ConnectivityManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkScanViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NetworkUiState())
    val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var lastNetworkData: NetworkInterfaceInfo? = null
    private var lastContext: Context? = null
    private var lastConnectivityManager: ConnectivityManager? = null

    fun initialize(activity: ComponentActivity) {
        val networkInfo = NetworkInterfaceDetector.detectUsbEthernet(activity)
        lastNetworkData = networkInfo
        lastContext = activity.applicationContext
        lastConnectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        _uiState.value = _uiState.value.copy(
            interfaceLabel = networkInfo?.displayName ?: "No USB Ethernet",
            linkStatus = networkInfo?.linkStatus ?: "Disconnected",
            linkSpeed = networkInfo?.linkSpeedLabel ?: "Unknown",
            ipAddress = networkInfo?.ipv4Address ?: "---",
            subnetMask = networkInfo?.subnetMask ?: "---",
            gateway = networkInfo?.gateway ?: "---",
            macAddress = networkInfo?.macAddress ?: "---"
        )
    }

    fun startScan() {
        val activeInterface = lastNetworkData ?: NetworkInterfaceDetector.detectUsbEthernet(null)
        if (activeInterface == null) {
            _uiState.value = _uiState.value.copy(devices = emptyList(), isScanning = false)
            return
        }

        cancelScan()
        scanJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, isOnvifScanning = false)
            val ipScanResults = NetworkScanner.scanSubnet(activeInterface)
            
            // Enrich with MAC address and OUI information
            val enrichedDevices = ipScanResults.map { device ->
                DeviceDeduplicator.enrichDevice(device)
            }
            
            val sortedDevices = DeviceDeduplicator.sortByRelevance(enrichedDevices)
            _uiState.value = _uiState.value.copy(
                devices = sortedDevices,
                isScanning = false
            )
        }
    }

    fun startOnvifDiscovery() {
        val activeInterface = lastNetworkData ?: NetworkInterfaceDetector.detectUsbEthernet(null)
        val context = lastContext
        if (activeInterface == null || context == null) {
            _uiState.value = _uiState.value.copy(isOnvifScanning = false)
            return
        }

        cancelScan()
        scanJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOnvifScanning = true, isScanning = false)

            val activeNetwork = lastConnectivityManager?.activeNetwork
            val onvifResults = OnvifDiscoveryProvider.discoverOnvifDevices(
                context,
                activeInterface,
                activeNetwork
            )

            // Merge with existing IP scan devices
            val currentDevices = _uiState.value.devices
            val mergedDevices = DeviceDeduplicator.mergeDiscoveryResults(
                currentDevices,
                onvifResults
            )
            
            // Enrich with MAC/OUI
            val enrichedDevices = mergedDevices.map { device ->
                DeviceDeduplicator.enrichDevice(device)
            }
            
            val sortedDevices = DeviceDeduplicator.sortByRelevance(enrichedDevices)

            _uiState.value = _uiState.value.copy(
                devices = sortedDevices,
                isOnvifScanning = false
            )
        }
    }

    fun startCombinedScan() {
        val activeInterface = lastNetworkData ?: NetworkInterfaceDetector.detectUsbEthernet(null)
        val context = lastContext
        if (activeInterface == null || context == null) {
            _uiState.value = _uiState.value.copy(devices = emptyList(), isScanning = false, isOnvifScanning = false)
            return
        }

        cancelScan()
        scanJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, isOnvifScanning = true)

            // Run IP scan
            val ipScanResults = NetworkScanner.scanSubnet(activeInterface)

            // Run ONVIF discovery in parallel
            val activeNetwork = lastConnectivityManager?.activeNetwork
            val onvifResults = OnvifDiscoveryProvider.discoverOnvifDevices(
                context,
                activeInterface,
                activeNetwork
            )

            // Merge results using deduplicator
            val mergedDevices = DeviceDeduplicator.mergeDiscoveryResults(
                ipScanResults,
                onvifResults
            )
            
            // Enrich with MAC/OUI information
            val enrichedDevices = mergedDevices.map { device ->
                DeviceDeduplicator.enrichDevice(device)
            }
            
            // Apply intelligent status determination
            val finalDevices = enrichedDevices.map { device ->
                device.copy(status = DeviceDeduplicator.determineDeviceStatus(device))
            }
            
            val sortedDevices = DeviceDeduplicator.sortByRelevance(finalDevices)

            _uiState.value = _uiState.value.copy(
                devices = sortedDevices,
                isScanning = false,
                isOnvifScanning = false
            )
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.value = _uiState.value.copy(isScanning = false, isOnvifScanning = false)
    }

    fun startSpecoDiscovery() {
        val ipScanResults = _uiState.value.devices
        if (ipScanResults.isEmpty()) {
            return
        }

        cancelScan()
        scanJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            
            val ipAddresses = ipScanResults.map { it.ipAddress }
            val specoResults = SpecoDiscoveryProvider.discoverSpecoDevices(ipAddresses)
            
            // Merge Speco results with existing devices
            val mergedDevices = DeviceDeduplicator.mergeDiscoveryResults(
                ipScanResults,
                specoResults
            )
            
            val enrichedDevices = mergedDevices.map { device ->
                DeviceDeduplicator.enrichDevice(device)
            }
            
            val finalDevices = enrichedDevices.map { device ->
                device.copy(status = DeviceDeduplicator.determineDeviceStatus(device))
            }
            
            val sortedDevices = DeviceDeduplicator.sortByRelevance(finalDevices)

            _uiState.value = _uiState.value.copy(
                devices = sortedDevices,
                isScanning = false
            )
        }
    }

    override fun onCleared() {
        cancelScan()
        super.onCleared()
    }
}
