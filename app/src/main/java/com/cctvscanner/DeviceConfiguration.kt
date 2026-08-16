package com.cctvscanner

/**
 * Device network configuration model.
 * Represents current and proposed network settings.
 */
data class DeviceConfiguration(
    val ipAddress: String,
    val subnetMask: String,
    val gateway: String,
    val dns1: String? = null,
    val dns2: String? = null,
    val dhcpEnabled: Boolean = false
) {
    fun isEmpty(): Boolean = ipAddress.isEmpty() || subnetMask.isEmpty()
    
    fun isSameAs(other: DeviceConfiguration): Boolean {
        return ipAddress == other.ipAddress &&
                subnetMask == other.subnetMask &&
                gateway == other.gateway &&
                dns1 == other.dns1 &&
                dns2 == other.dns2 &&
                dhcpEnabled == other.dhcpEnabled
    }
}

/**
 * Configuration change proposal with validation and safety checks.
 */
data class ConfigurationChangeProposal(
    val currentConfig: DeviceConfiguration,
    val proposedConfig: DeviceConfiguration,
    val requiresReboot: Boolean = true,
    val mayDisconnectDevice: Boolean = false,
    val warnings: List<String> = emptyList(),
    val requiresAuthentication: Boolean = false
) {
    fun hasChanges(): Boolean = !currentConfig.isSameAs(proposedConfig)
    
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        // Validate IP address format
        if (!isValidIpAddress(proposedConfig.ipAddress)) {
            errors.add("Invalid IP address format")
        }
        
        // Validate subnet mask
        if (!isValidSubnetMask(proposedConfig.subnetMask)) {
            errors.add("Invalid subnet mask format")
        }
        
        // Validate gateway
        if (!isValidIpAddress(proposedConfig.gateway)) {
            errors.add("Invalid gateway address")
        }
        
        // DNS validation
        if (proposedConfig.dns1 != null && !isValidIpAddress(proposedConfig.dns1)) {
            errors.add("Invalid DNS1 address")
        }
        if (proposedConfig.dns2 != null && !isValidIpAddress(proposedConfig.dns2)) {
            errors.add("Invalid DNS2 address")
        }
        
        return errors
    }
    
    private fun isValidIpAddress(ip: String): Boolean {
        if (ip.isEmpty()) return false
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { 
            try {
                val num = it.toInt()
                num >= 0 && num <= 255
            } catch (_: Exception) {
                false
            }
        }
    }
    
    private fun isValidSubnetMask(mask: String): Boolean {
        if (!isValidIpAddress(mask)) return false
        // Convert to binary and check for valid subnet mask pattern
        val parts = mask.split(".").map { it.toInt() }
        val binary = parts.fold("") { acc, part ->
            acc + part.toString(2).padStart(8, '0')
        }
        // Valid subnet mask has all 1s followed by all 0s
        return !binary.contains("01")
    }
}

/**
 * Abstract provider for device configuration.
 * Implemented per-manufacturer for vendor-specific configuration methods.
 */
interface DeviceConfigurationProvider {
    suspend fun readConfiguration(device: DiscoveredDevice, username: String = "", password: String = ""): DeviceConfiguration?
    suspend fun proposeConfiguration(currentConfig: DeviceConfiguration, newIp: String, newSubnet: String, newGateway: String): ConfigurationChangeProposal
    suspend fun applyConfiguration(proposal: ConfigurationChangeProposal, device: DiscoveredDevice, username: String = "", password: String = ""): Boolean
    suspend fun verifyConfiguration(device: DiscoveredDevice, expectedConfig: DeviceConfiguration, timeoutSeconds: Int = 10): Boolean
}
