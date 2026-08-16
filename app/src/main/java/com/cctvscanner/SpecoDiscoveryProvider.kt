package com.cctvscanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLConnection
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Speco-specific device discovery and information retrieval.
 * Discovers Speco cameras, NVRs, and DVRs via HTTP endpoints and APIs.
 */
object SpecoDiscoveryProvider {
    
    data class SpecoDeviceInfo(
        val ipAddress: String,
        val model: String? = null,
        val serialNumber: String? = null,
        val firmware: String? = null,
        val macAddress: String? = null,
        val deviceType: String? = null // "Camera", "NVR", "DVR"
    )

    private val specoCommonPorts = listOf(80, 443, 8080, 8443, 8888, 9000)
    private val specoHttpTimeout = 2000

    /**
     * Discover Speco devices on the network via direct HTTP queries.
     * Queries device info endpoints on known Speco ports.
     */
    suspend fun discoverSpecoDevices(
        ipAddresses: List<String>
    ): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiscoveredDevice>()

        ipAddresses.forEach { ip ->
            specoCommonPorts.forEach { port ->
                try {
                    val deviceInfo = querySpecoDeviceInfo(ip, port)
                    if (deviceInfo != null) {
                        results.add(
                            DiscoveredDevice(
                                ipAddress = ip,
                                macAddress = deviceInfo.macAddress,
                                manufacturer = "Speco",
                                model = deviceInfo.model,
                                status = determineSpecoDeviceType(deviceInfo),
                                onvifSupported = querySpecoOnvifSupport(ip, port),
                                discoveryMethods = listOf("Speco HTTP Discovery")
                            )
                        )
                    }
                } catch (_: Exception) {
                    // Continue to next port
                }
            }
        }

        results
    }

    /**
     * Query Speco device via HTTP API endpoint.
     * Attempts to retrieve device information from known Speco endpoints.
     */
    private suspend fun querySpecoDeviceInfo(
        ipAddress: String,
        port: Int
    ): SpecoDeviceInfo? = withContext(Dispatchers.IO) {
        return@withContext try {
            // Try Speco's /cgi-bin/api endpoints
            listOf(
                "http://$ipAddress:$port/cgi-bin/api/info/device",
                "http://$ipAddress:$port/api/device/info",
                "http://$ipAddress:$port/device/info"
            ).forEach { endpoint ->
                val result = tryQuerySpecoEndpoint(endpoint)
                if (result != null) return@withContext result
            }

            // Fallback: try basic HTTP GET
            val url = URL("http://$ipAddress:$port/")
            val connection = url.openConnection() as URLConnection
            connection.connectTimeout = specoHttpTimeout
            connection.readTimeout = specoHttpTimeout

            try {
                val response = connection.getInputStream().bufferedReader().use { it.readText() }
                if (response.lowercase().contains("speco") || response.lowercase().contains("o4d")) {
                    SpecoDeviceInfo(ipAddress = ipAddress)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Try specific Speco API endpoint.
     */
    private fun tryQuerySpecoEndpoint(endpoint: String): SpecoDeviceInfo? {
        return try {
            val url = URL(endpoint)
            val connection = url.openConnection() as URLConnection
            connection.connectTimeout = specoHttpTimeout
            connection.readTimeout = specoHttpTimeout
            connection.setRequestProperty("Accept", "application/json, application/xml")

            val response = connection.getInputStream().bufferedReader().use { it.readText() }

            // Parse Speco JSON/XML response
            parseSpecoDeviceResponse(response)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse Speco device information response.
     * Handles both JSON and XML formats.
     */
    private fun parseSpecoDeviceResponse(response: String): SpecoDeviceInfo? {
        return try {
            when {
                response.trim().startsWith("{") -> parseSpecoJsonResponse(response)
                response.trim().startsWith("<") -> parseSpecoXmlResponse(response)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse Speco JSON response.
     */
    private fun parseSpecoJsonResponse(json: String): SpecoDeviceInfo? {
        return try {
            // Basic JSON parsing for Speco format
            var model: String? = null
            var serial: String? = null
            var firmware: String? = null
            var mac: String? = null

            if (json.contains("\"model\"")) {
                model = extractJsonField(json, "model")
            }
            if (json.contains("\"serial")) {
                serial = extractJsonField(json, "serial")
            }
            if (json.contains("\"firmware")) {
                firmware = extractJsonField(json, "firmware")
            }
            if (json.contains("\"mac")) {
                mac = extractJsonField(json, "mac")
            }

            if (model != null) {
                SpecoDeviceInfo(
                    ipAddress = "",
                    model = model,
                    serialNumber = serial,
                    firmware = firmware,
                    macAddress = mac
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse Speco XML response.
     */
    private fun parseSpecoXmlResponse(xml: String): SpecoDeviceInfo? {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray()))

            var model: String? = null
            var serial: String? = null
            var firmware: String? = null

            doc.getElementsByTagName("Model").let {
                if (it.length > 0) model = it.item(0)?.textContent
            }
            doc.getElementsByTagName("SerialNumber").let {
                if (it.length > 0) serial = it.item(0)?.textContent
            }
            doc.getElementsByTagName("FirmwareVersion").let {
                if (it.length > 0) firmware = it.item(0)?.textContent
            }

            if (model != null) {
                SpecoDeviceInfo(
                    ipAddress = "",
                    model = model,
                    serialNumber = serial,
                    firmware = firmware
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract JSON field value.
     */
    private fun extractJsonField(json: String, fieldName: String): String? {
        val pattern = "\"$fieldName\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }

    /**
     * Check if Speco device supports ONVIF.
     */
    private suspend fun querySpecoOnvifSupport(
        ipAddress: String,
        port: Int
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL("http://$ipAddress:$port/onvif/device_service")
            val connection = url.openConnection() as URLConnection
            connection.connectTimeout = 1000
            connection.readTimeout = 1000

            connection.getInputStream().use { true }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Determine Speco device type from model information.
     */
    private fun determineSpecoDeviceType(info: SpecoDeviceInfo): String {
        val model = info.model?.lowercase() ?: return "Unknown Device"
        return when {
            model.contains("nvr") -> "Speco NVR"
            model.contains("dvr") -> "Speco DVR"
            model.contains("recorder") -> "Speco Recorder"
            model.contains("camera") || model.contains("o4") || model.contains("o8") -> "Speco Camera"
            model.startsWith("n") -> "Speco NVR"
            else -> "Speco Device"
        }
    }
}
