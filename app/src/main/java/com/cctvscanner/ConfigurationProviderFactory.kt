package com.cctvscanner

/**
 * Factory for creating appropriate configuration provider based on device manufacturer.
 */
object ConfigurationProviderFactory {
    fun getProvider(device: DiscoveredDevice): DeviceConfigurationProvider? {
        return when {
            device.manufacturer?.contains("Speco", ignoreCase = true) == true -> 
                SpecoConfigurationProvider
            
            // Additional vendors can be added here
            // device.manufacturer?.contains("Hikvision", ignoreCase = true) == true ->
            //     HikvisionConfigurationProvider
            
            else -> null
        }
    }

    fun supportsConfiguration(device: DiscoveredDevice): Boolean {
        return getProvider(device) != null
    }
}
