package com.cctvscanner

/**
 * Deduplicates and merges discovered devices from multiple discovery sources.
 * Prioritizes identification methods:
 * 1. MAC address
 * 2. ONVIF UUID
 * 3. IP address
 *
 * Combines information from:
 * - IP Scanner
 * - ONVIF Discovery
 * - MAC/OUI Database
 */
object DeviceDeduplicator {

    /**
     * Merge devices from multiple discovery sources.
     * Combines IP scan results with ONVIF results and MAC/OUI data.
     */
    fun mergeDiscoveryResults(
        ipScanDevices: List<DiscoveredDevice>,
        onvifDevices: List<DiscoveredDevice>,
        macResolutions: Map<String, String?> = emptyMap()
    ): List<DiscoveredDevice> {
        val mergedMap = mutableMapOf<String, DiscoveredDevice>()

        // Process ONVIF devices first (highest priority for deduplication key)
        onvifDevices.forEach { device ->
            val key = device.onvifUuid ?: device.macAddress ?: device.ipAddress
            mergedMap[key] = device.copy(
                discoveryMethods = listOf("ONVIF")
            )
        }

        // Process IP scan devices
        ipScanDevices.forEach { device ->
            val macAddress = macResolutions[device.ipAddress]
            val formattedMac = macAddress?.let { MacAddressResolver.formatMacAddress(it) }
            val manufacturer = macAddress?.let { OuiDatabase.getManufacturer(it) }

            // Try to find existing device to merge
            val existingKey = when {
                device.onvifUuid != null -> device.onvifUuid // ONVIF UUID is primary key
                formattedMac != null -> formattedMac // MAC is secondary
                else -> device.ipAddress // IP is fallback
            }

            val existing = mergedMap[existingKey]
            if (existing != null) {
                // Merge with existing ONVIF device
                mergedMap[existingKey] = existing.copy(
                    ipAddress = device.ipAddress, // Ensure IP is set
                    macAddress = formattedMac ?: existing.macAddress,
                    manufacturer = existing.manufacturer ?: manufacturer,
                    openPorts = if (device.openPorts.isNotEmpty()) device.openPorts else existing.openPorts,
                    responseTimeMs = if (device.responseTimeMs > 0) device.responseTimeMs else existing.responseTimeMs,
                    discoveryMethods = (existing.discoveryMethods + "IP Scan").distinct()
                )
            } else {
                // New device from IP scan
                mergedMap[existingKey] = device.copy(
                    macAddress = formattedMac,
                    manufacturer = manufacturer,
                    discoveryMethods = listOf("IP Scan") + (if (formattedMac != null) listOf("MAC/OUI") else emptyList())
                )
            }
        }

        return mergedMap.values.sortedBy { it.ipAddress }
    }

    /**
     * Enrich a device with MAC address and manufacturer information.
     */
    suspend fun enrichDevice(device: DiscoveredDevice): DiscoveredDevice {
        if (device.macAddress != null && device.manufacturer != null) {
            return device // Already enriched
        }

        val macAddress = MacAddressResolver.getMacAddressForIp(device.ipAddress)
            ?.let { MacAddressResolver.formatMacAddress(it) }
        val manufacturer = macAddress?.let { OuiDatabase.getManufacturer(it) }

        return device.copy(
            macAddress = macAddress ?: device.macAddress,
            manufacturer = manufacturer ?: device.manufacturer,
            discoveryMethods = if (macAddress != null && !device.discoveryMethods.contains("MAC/OUI")) {
                device.discoveryMethods + "MAC/OUI"
            } else {
                device.discoveryMethods
            }
        )
    }

    /**
     * Determine device status/type based on available information.
     */
    fun determineDeviceStatus(device: DiscoveredDevice): String {
        return when {
            device.status != "Unknown Network Device" -> device.status

            device.onvifSupported -> {
                when {
                    device.model?.contains("NVR", ignoreCase = true) == true -> "ONVIF NVR"
                    device.model?.contains("DVR", ignoreCase = true) == true -> "ONVIF DVR"
                    else -> "ONVIF Camera"
                }
            }

            OuiDatabase.isCameraManufacturer(device.macAddress) -> {
                when {
                    device.manufacturer?.contains("Speco", ignoreCase = true) == true -> "Speco Device"
                    device.manufacturer?.contains("Hikvision", ignoreCase = true) == true -> "Hikvision Device"
                    device.manufacturer?.contains("Dahua", ignoreCase = true) == true -> "Dahua Device"
                    else -> "Camera/NVR (${device.manufacturer ?: "Unknown"})"
                }
            }

            // Check for common camera ports
            device.openPorts.contains(554) -> "RTSP-Enabled Device" // RTSP port
            device.openPorts.contains(80) || device.openPorts.contains(443) -> "Web-Enabled Device"

            else -> "Unknown Network Device"
        }
    }

    /**
     * Sort devices by relevance for technician workflow.
     * Camera/NVR devices first, then by manufacturer, then by IP.
     */
    fun sortByRelevance(devices: List<DiscoveredDevice>): List<DiscoveredDevice> {
        return devices.sortedWith(compareBy(
            { !OuiDatabase.isCameraManufacturer(it.macAddress) }, // Camera manufacturers first
            { it.manufacturer ?: "zzz" }, // Then by manufacturer
            { it.ipAddress.toIpInteger() } // Then by IP address
        ))
    }
}
