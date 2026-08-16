package com.cctvscanner

import android.content.Context
import android.net.Network
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.UUID
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

object OnvifDiscoveryProvider {
    private const val MULTICAST_IP = "239.255.255.250"
    private const val MULTICAST_PORT = 3702
    private const val DISCOVERY_TIMEOUT_MS = 4000L

    /**
     * Performs ONVIF WS-Discovery Probe for NetworkVideoTransmitter devices.
     * Must be bound to the active Ethernet interface.
     */
    suspend fun discoverOnvifDevices(
        context: Context,
        network: NetworkInterfaceInfo,
        activeNetwork: Network?
    ): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiscoveredDevice>()
        var multicastSocket: MulticastSocket? = null
        var multicastLock: WifiManager.MulticastLock? = null

        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("CCTVScannerMulticast")?.apply {
                setReferenceCounted(false)
                acquire()
            }

            val networkInterface = NetworkInterface.getByName(network.interfaceName)
                ?: return@withContext results

            multicastSocket = MulticastSocket(MULTICAST_PORT).apply {
                setNetworkInterface(networkInterface)
                setReuseAddress(true)
                soTimeout = DISCOVERY_TIMEOUT_MS.toInt()

                // Bind socket to the active network (USB Ethernet if available)
                if (activeNetwork != null) {
                    try {
                        activeNetwork.bindSocket(this)
                    } catch (e: Exception) {
                        // Fallback if binding fails
                    }
                }
            }

            val inetGroup = InetAddress.getByName(MULTICAST_IP)
            multicastSocket.joinGroup(
                InetSocketAddress(inetGroup, MULTICAST_PORT),
                networkInterface
            )

            // Send ONVIF Probe
            val probeXml = buildOnvifProbeMessage()
            val txData = probeXml.toByteArray()
            val txPacket = DatagramPacket(txData, txData.size, inetGroup, MULTICAST_PORT)
            multicastSocket.send(txPacket)

            // Receive ProbeMatch responses
            val rxBuffer = ByteArray(8192)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS) {
                try {
                    val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
                    multicastSocket.receive(rxPacket)

                    val rawXml = String(rxPacket.data, 0, rxPacket.getLength())
                    val device = parseProbeMatchResponse(
                        rawXml,
                        rxPacket.address.hostAddress ?: "unknown"
                    )

                    if (device != null) {
                        results.add(device)
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Expected when no more responses arrive
                    break
                }
            }

            try {
                multicastSocket.leaveGroup(
                    InetSocketAddress(inetGroup, MULTICAST_PORT),
                    networkInterface
                )
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
        } catch (e: Exception) {
            // Log and continue with empty results
        } finally {
            multicastSocket?.close()
            multicastLock?.release()
        }

        results
    }

    private fun buildOnvifProbeMessage(): String {
        val msgId = UUID.randomUUID().toString()
        return """<?xml version="1.0" encoding="utf-8"?>
            |<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" 
            |    xmlns:wsa="http://www.w3.org/2005/08/addressing" 
            |    xmlns:wsd="http://schemas.xmlsoap.org/ws/2005/04/discovery">
            |    <soap:Header>
            |        <wsa:MessageID>urn:uuid:$msgId</wsa:MessageID>
            |        <wsa:To>urn:schemas-xmlsoap-org:ws:discovery:DiscoveryProxy</wsa:To>
            |        <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action>
            |    </soap:Header>
            |    <soap:Body>
            |        <wsd:Probe>
            |            <wsd:Types>dn:NetworkVideoTransmitter</wsd:Types>
            |        </wsd:Probe>
            |    </soap:Body>
            |</soap:Envelope>
        """.trimMargin()
    }

    private fun parseProbeMatchResponse(xml: String, fallbackIp: String): DiscoveredDevice? {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray()))

            // Parse WS-Discovery fields
            var uuid = ""
            var xaddrs = ""
            var scopes = ""

            // Look for EndpointReference Address
            doc.getElementsByTagNameNS("http://www.w3.org/2005/08/addressing", "Address").let {
                if (it.length > 0) uuid = it.item(0)?.textContent ?: ""
            }

            // Look for XAddrs
            doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/ws/2005/04/discovery", "XAddrs").let {
                if (it.length > 0) xaddrs = it.item(0)?.textContent ?: ""
            }

            // Look for Scopes
            doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/ws/2005/04/discovery", "Scopes").let {
                if (it.length > 0) scopes = it.item(0)?.textContent ?: ""
            }

            // Parse manufacturer and model from Scopes
            var manufacturer = "Unknown"
            var model = "ONVIF Device"

            if (scopes.isNotEmpty()) {
                scopes.split(" ").forEach { scope ->
                    when {
                        scope.contains("onvif://www.onvif.org/name/") -> {
                            manufacturer = scope.substringAfterLast("/").replace("_", " ")
                        }
                        scope.contains("onvif://www.onvif.org/hardware/") -> {
                            model = scope.substringAfterLast("/").replace("_", " ")
                        }
                    }
                }
            }

            // Extract IP from XAddrs
            var ipAddress = fallbackIp
            if (xaddrs.isNotEmpty()) {
                val pattern = Pattern.compile("http://([^:/]+)")
                val matcher = pattern.matcher(xaddrs)
                if (matcher.find()) {
                    ipAddress = matcher.group(1) ?: fallbackIp
                }
            }

            DiscoveredDevice(
                ipAddress = ipAddress,
                openPorts = listOf(80, 554), // Typical ONVIF ports
                status = "$manufacturer $model",
                manufacturer = manufacturer,
                model = model,
                onvifSupported = true,
                onvifXaddr = if (xaddrs.isNotEmpty()) xaddrs.split(" ").firstOrNull() else null,
                onvifUuid = if (uuid.isNotEmpty()) uuid else null,
                discoveryMethods = listOf("ONVIF WS-Discovery")
            )
        } catch (e: Exception) {
            null
        }
    }
}
