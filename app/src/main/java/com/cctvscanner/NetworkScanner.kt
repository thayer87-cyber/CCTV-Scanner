package com.cctvscanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object NetworkScanner {
    private val commonPorts = listOf(80, 443, 554, 1025, 1080, 3389, 37777, 5000, 8000, 8080, 9000, 9100, 6036)

    suspend fun scanSubnet(network: NetworkInterfaceInfo): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val scanRange = calculateSubnetRange(network.ipv4Address, network.prefixLength)
        val results = mutableListOf<DiscoveredDevice>()
        val startAddress = scanRange.startValue
        val endAddress = scanRange.endValue

        for (value in startAddress..endAddress) {
            val ip = value.toIpv4String()
            val portMatches = mutableListOf<Int>()
            val startTime = System.nanoTime()

            for (port in commonPorts) {
                if (tryConnect(ip, port)) {
                    portMatches.add(port)
                }
            }

            if (portMatches.isNotEmpty()) {
                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
                results.add(
                    DiscoveredDevice(
                        ipAddress = ip,
                        openPorts = portMatches,
                        responseTimeMs = elapsedMs,
                        status = "Unknown Network Device"
                    )
                )
            }
        }

        results
    }

    private fun tryConnect(host: String, port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.soTimeout = 350
            socket.connect(InetSocketAddress(host, port), 350)
            true
        }
    }.getOrDefault(false)
}
