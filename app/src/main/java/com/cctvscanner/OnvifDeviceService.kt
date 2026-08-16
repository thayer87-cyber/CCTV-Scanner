package com.cctvscanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLConnection
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Queries ONVIF devices for detailed information.
 * Handles device service discovery and information retrieval.
 */
object OnvifDeviceService {
    data class DeviceInfo(
        val manufacturer: String? = null,
        val model: String? = null,
        val firmware: String? = null,
        val serialNumber: String? = null,
        val hardwareId: String? = null
    )

    /**
     * Retrieves device information from an ONVIF device.
     * Attempts to query the device service endpoint.
     */
    suspend fun getDeviceInfo(
        xaddr: String,
        timeoutMs: Int = 3000
    ): DeviceInfo = withContext(Dispatchers.IO) {
        try {
            // xaddr typically contains multiple URLs separated by spaces
            val deviceUrl = if (xaddr.contains(" ")) {
                xaddr.split(" ").firstOrNull { it.startsWith("http") } ?: return@withContext DeviceInfo()
            } else {
                xaddr
            }

            // Try to get device information from device service
            val url = URL("$deviceUrl/onvif/device_service")
            val connection = url.openConnection() as URLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs

            // Send GetDeviceInformation request
            val soapRequest = buildGetDeviceInfoRequest()
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
            connection.getOutputStream().use { it.write(soapRequest.toByteArray()) }

            // Parse response
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            parseDeviceInfoResponse(response)
        } catch (e: Exception) {
            DeviceInfo()
        }
    }

    private fun buildGetDeviceInfoRequest(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
            |<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" 
            |    xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
            |    <soap:Body>
            |        <tds:GetDeviceInformation/>
            |    </soap:Body>
            |</soap:Envelope>
        """.trimMargin()
    }

    private fun parseDeviceInfoResponse(xml: String): DeviceInfo {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray()))

            var manufacturer: String? = null
            var model: String? = null
            var firmware: String? = null
            var serialNumber: String? = null
            var hardwareId: String? = null

            // Parse GetDeviceInformationResponse
            doc.getElementsByTagNameNS("http://www.onvif.org/ver10/device/wsdl", "Manufacturer").let {
                if (it.length > 0) manufacturer = it.item(0)?.textContent
            }

            doc.getElementsByTagNameNS("http://www.onvif.org/ver10/device/wsdl", "Model").let {
                if (it.length > 0) model = it.item(0)?.textContent
            }

            doc.getElementsByTagNameNS("http://www.onvif.org/ver10/device/wsdl", "FirmwareVersion").let {
                if (it.length > 0) firmware = it.item(0)?.textContent
            }

            doc.getElementsByTagNameNS("http://www.onvif.org/ver10/device/wsdl", "SerialNumber").let {
                if (it.length > 0) serialNumber = it.item(0)?.textContent
            }

            doc.getElementsByTagNameNS("http://www.onvif.org/ver10/device/wsdl", "HardwareId").let {
                if (it.length > 0) hardwareId = it.item(0)?.textContent
            }

            DeviceInfo(
                manufacturer = manufacturer,
                model = model,
                firmware = firmware,
                serialNumber = serialNumber,
                hardwareId = hardwareId
            )
        } catch (e: Exception) {
            DeviceInfo()
        }
    }
}
