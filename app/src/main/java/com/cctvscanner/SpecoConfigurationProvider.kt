package com.cctvscanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLConnection
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Speco-specific device configuration provider.
 * Handles IP configuration for Speco cameras, NVRs, and DVRs.
 */
object SpecoConfigurationProvider : DeviceConfigurationProvider {

    override suspend fun readConfiguration(
        device: DiscoveredDevice,
        username: String,
        password: String
    ): DeviceConfiguration? = withContext(Dispatchers.IO) {
        try {
            val endpoint = "http://${device.ipAddress}:${getSpecoPort(device)}/cgi-bin/api/network/config"
            val response = querySpecoApi(endpoint, username, password)
            
            parseSpecoNetworkConfig(response)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun proposeConfiguration(
        currentConfig: DeviceConfiguration,
        newIp: String,
        newSubnet: String,
        newGateway: String
    ): ConfigurationChangeProposal = withContext(Dispatchers.IO) {
        val proposed = DeviceConfiguration(
            ipAddress = newIp,
            subnetMask = newSubnet,
            gateway = newGateway,
            dhcpEnabled = false
        )

        val warnings = mutableListOf<String>()
        var mayDisconnect = false

        // Check if IP change will disconnect device
        if (currentConfig.ipAddress != newIp) {
            warnings.add("Device IP will change from ${currentConfig.ipAddress} to $newIp")
            mayDisconnect = true
        }

        // Check if subnet change
        if (currentConfig.subnetMask != newSubnet) {
            warnings.add("Device subnet will change from ${currentConfig.subnetMask} to $newSubnet")
        }

        // Check if gateway change
        if (currentConfig.gateway != newGateway) {
            warnings.add("Device gateway will change from ${currentConfig.gateway} to $newGateway")
        }

        return@withContext ConfigurationChangeProposal(
            currentConfig = currentConfig,
            proposedConfig = proposed,
            requiresReboot = true,
            mayDisconnectDevice = mayDisconnect,
            warnings = warnings,
            requiresAuthentication = true
        )
    }

    override suspend fun applyConfiguration(
        proposal: ConfigurationChangeProposal,
        device: DiscoveredDevice,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!proposal.hasChanges()) return@withContext false

        // Validate the proposal
        val errors = proposal.validate()
        if (errors.isNotEmpty()) {
            return@withContext false
        }

        try {
            val endpoint = "http://${device.ipAddress}:${getSpecoPort(device)}/cgi-bin/api/network/config"
            val payload = buildSpecoConfigPayload(proposal.proposedConfig)
            
            postSpecoApi(endpoint, payload, username, password)
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun verifyConfiguration(
        device: DiscoveredDevice,
        expectedConfig: DeviceConfiguration,
        timeoutSeconds: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < (timeoutSeconds * 1000)) {
            try {
                delay(2000) // Wait for device to apply config
                
                val currentConfig = readConfiguration(device) ?: return@withContext false
                
                if (currentConfig.ipAddress == expectedConfig.ipAddress &&
                    currentConfig.subnetMask == expectedConfig.subnetMask &&
                    currentConfig.gateway == expectedConfig.gateway) {
                    return@withContext true
                }
            } catch (_: Exception) {
                // Continue retrying
            }
        }
        
        false
    }

    private suspend fun querySpecoApi(
        endpoint: String,
        username: String = "",
        password: String = ""
    ): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL(endpoint)
            val connection = url.openConnection() as URLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            // Add authentication if provided
            if (username.isNotEmpty() && password.isNotEmpty()) {
                val auth = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                connection.setRequestProperty("Authorization", "Basic $auth")
            }

            connection.getInputStream().bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun postSpecoApi(
        endpoint: String,
        payload: String,
        username: String = "",
        password: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL(endpoint)
            val connection = url.openConnection() as URLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            // Add authentication if provided
            if (username.isNotEmpty() && password.isNotEmpty()) {
                val auth = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                connection.setRequestProperty("Authorization", "Basic $auth")
            }

            connection.getOutputStream().bufferedWriter().use {
                it.write(payload)
            }

            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            response.contains("success", ignoreCase = true) || response.isEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun parseSpecoNetworkConfig(response: String?): DeviceConfiguration? {
        if (response == null) return null

        return try {
            when {
                response.trim().startsWith("{") -> parseSpecoJsonConfig(response)
                response.trim().startsWith("<") -> parseSpecoXmlConfig(response)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSpecoJsonConfig(json: String): DeviceConfiguration? {
        return try {
            var ip = ""
            var subnet = ""
            var gateway = ""
            var dhcp = false

            if (json.contains("\"ipaddr")) {
                ip = extractJsonField(json, "ipaddr") ?: ""
            }
            if (json.contains("\"netmask")) {
                subnet = extractJsonField(json, "netmask") ?: ""
            }
            if (json.contains("\"gateway")) {
                gateway = extractJsonField(json, "gateway") ?: ""
            }
            if (json.contains("\"dhcp")) {
                dhcp = extractJsonField(json, "dhcp")?.toIntOrNull() == 1
            }

            if (ip.isNotEmpty() && subnet.isNotEmpty()) {
                DeviceConfiguration(
                    ipAddress = ip,
                    subnetMask = subnet,
                    gateway = gateway,
                    dhcpEnabled = dhcp
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSpecoXmlConfig(xml: String): DeviceConfiguration? {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray()))

            var ip = ""
            var subnet = ""
            var gateway = ""

            doc.getElementsByTagName("IPAddress").let {
                if (it.length > 0) ip = it.item(0)?.textContent ?: ""
            }
            doc.getElementsByTagName("SubnetMask").let {
                if (it.length > 0) subnet = it.item(0)?.textContent ?: ""
            }
            doc.getElementsByTagName("Gateway").let {
                if (it.length > 0) gateway = it.item(0)?.textContent ?: ""
            }

            if (ip.isNotEmpty() && subnet.isNotEmpty()) {
                DeviceConfiguration(
                    ipAddress = ip,
                    subnetMask = subnet,
                    gateway = gateway
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSpecoConfigPayload(config: DeviceConfiguration): String {
        return listOf(
            "ipaddr=${config.ipAddress}",
            "netmask=${config.subnetMask}",
            "gateway=${config.gateway}",
            "dhcp=${if (config.dhcpEnabled) 1 else 0}"
        ).joinToString("&")
    }

    private fun extractJsonField(json: String, fieldName: String): String? {
        val pattern = "\"$fieldName\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }

    private fun getSpecoPort(device: DiscoveredDevice): Int {
        // Try to determine port from open ports
        return when {
            device.openPorts.contains(443) -> 443
            device.openPorts.contains(8443) -> 8443
            device.openPorts.contains(8080) -> 8080
            device.openPorts.contains(80) -> 80
            else -> 80 // Default to HTTP
        }
    }
}
