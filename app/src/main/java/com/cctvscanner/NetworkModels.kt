package com.cctvscanner

import android.net.LinkProperties
import java.net.InetAddress

data class NetworkInterfaceInfo(
    val interfaceName: String,
    val displayName: String,
    val ipv4Address: String,
    val subnetMask: String,
    val gateway: String,
    val macAddress: String,
    val linkSpeedLabel: String,
    val prefixLength: Int,
    val linkStatus: String,
    val transportName: String
)

data class DiscoveredDevice(
    val ipAddress: String,
    val openPorts: List<Int> = emptyList(),
    val responseTimeMs: Long = 0L,
    val status: String = "Unknown Network Device",
    val macAddress: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val onvifSupported: Boolean = false,
    val onvifXaddr: String? = null,
    val onvifUuid: String? = null,
    val discoveryMethods: List<String> = emptyList()
)

data class NetworkUiState(
    val interfaceLabel: String = "Unknown",
    val linkStatus: String = "Disconnected",
    val linkSpeed: String = "Unknown",
    val ipAddress: String = "---",
    val subnetMask: String = "---",
    val gateway: String = "---",
    val macAddress: String = "---",
    val isScanning: Boolean = false,
    val isOnvifScanning: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList()
)

internal data class ScanRange(
    val startValue: Int,
    val endValue: Int,
    val gateway: String?
)

internal fun LinkProperties.toNetworkInterfaceInfo(
    interfaceName: String,
    displayName: String,
    transportName: String
): NetworkInterfaceInfo {
    val ipv4Address = linkAddresses
        .mapNotNull { it.address }
        .firstOrNull { it.hostAddress?.contains('.') == true && !it.isLoopbackAddress }
        ?.hostAddress
        ?: "---"

    val prefixLength = linkAddresses
        .firstOrNull { it.address.hostAddress?.contains('.') == true }
        ?.prefixLength
        ?: 24

    val subnetMask = prefixLengthToMask(prefixLength)
    val gatewayAddress = routes
        .firstOrNull { it.isDefaultRoute }
        ?.gateway
        ?.hostAddress
        ?: "---"

    val macAddress = try {
        java.net.NetworkInterface.getByName(interfaceName)?.hardwareAddress
            ?.joinToString(":") { "%02X".format(it) }
            ?: "--:--:--:--:--:--"
    } catch (_: Exception) {
        "--:--:--:--:--:--"
    }

    return NetworkInterfaceInfo(
        interfaceName = interfaceName,
        displayName = displayName,
        ipv4Address = ipv4Address,
        subnetMask = subnetMask,
        gateway = gatewayAddress,
        macAddress = macAddress,
        linkSpeedLabel = if (transportName == "Ethernet") "1 Gbps" else "Unknown",
        prefixLength = prefixLength,
        linkStatus = "Connected",
        transportName = transportName
    )
}

internal fun prefixLengthToMask(prefixLength: Int): String {
    if (prefixLength !in 0..32) return "255.255.255.0"
    val mask = if (prefixLength == 0) 0 else ((0xFFFFFFFF shl (32 - prefixLength)) and 0xFFFFFFFF)
    return listOf(
        (mask ushr 24) and 0xFF,
        (mask ushr 16) and 0xFF,
        (mask ushr 8) and 0xFF,
        mask and 0xFF
    ).joinToString(".")
}

internal fun String.toIpInteger(): Int {
    val octets = split(".")
        .map { it.toIntOrNull() ?: 0 }
        .takeIf { it.size == 4 }
        ?: return 0
    return ((octets[0] and 0xFF) shl 24) or
        ((octets[1] and 0xFF) shl 16) or
        ((octets[2] and 0xFF) shl 8) or
        (octets[3] and 0xFF)
}

internal fun Int.toIpv4String(): String {
    return listOf(
        (this ushr 24) and 0xFF,
        (this ushr 16) and 0xFF,
        (this ushr 8) and 0xFF,
        this and 0xFF
    ).joinToString(".")
}

internal fun calculateSubnetRange(ipAddress: String, prefixLength: Int): ScanRange {
    val ipInt = ipAddress.toIpInteger()
    val ipLong = ipInt.toLong() and 0xFFFFFFFFL
    val maskLong = if (prefixLength == 0) 0L else ((0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL)
    val networkLong = ipLong and maskLong
    val broadcastLong = networkLong or (0xFFFFFFFFL xor maskLong)
    val start = if (prefixLength >= 31) networkLong.toInt() else ((networkLong + 1L) and 0xFFFFFFFFL).toInt()
    val end = if (prefixLength >= 31) broadcastLong.toInt() else ((broadcastLong - 1L) and 0xFFFFFFFFL).toInt()
    return ScanRange(startValue = start, endValue = end, gateway = null)
}

internal fun IntArray.toMaskString(): String {
    return this.joinToString(".")
}

internal fun InetAddress.isUsableIpv4(): Boolean {
    return !this.isLoopbackAddress && hostAddress?.contains('.') == true
}
