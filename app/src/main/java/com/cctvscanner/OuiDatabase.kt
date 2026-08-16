package com.cctvscanner

/**
 * OUI (Organizationally Unique Identifier) database for manufacturer identification.
 * Maps MAC address prefixes to manufacturers.
 * Focused on security camera manufacturers.
 */
object OuiDatabase {
    private val ouiMap = mapOf(
        // Speco
        "00:08:5B" to "Speco Technologies",
        "00:50:FC" to "Speco Technologies",
        "08:5B:00" to "Speco Technologies",

        // Hikvision
        "00:0C:42" to "Hikvision Digital Technology",
        "0C:42:A1" to "Hikvision Digital Technology",
        "DC:FE:07" to "Hikvision Digital Technology",

        // Dahua
        "00:25:86" to "Dahua Technology",
        "44:29:C6" to "Dahua Technology",

        // Axis Communications
        "00:40:8C" to "Axis Communications",
        "AC:CC:8E" to "Axis Communications",

        // Hanwha (Samsung) security cameras
        "00:30:F1" to "Hanwha Techwin",
        "94:52:1F" to "Hanwha Techwin",

        // Bosch
        "00:01:32" to "Bosch Security Systems",
        "00:50:EA" to "Bosch Security Systems",

        // ONVIF generic/other manufacturers
        "00:0A:95" to "Panasonic",
        "00:E0:7C" to "Canon",
        "00:40:05" to "Vivotek",
        "00:D0:FE" to "Arecont Vision",

        // Common network equipment
        "00:1A:2B" to "Cisco Systems",
        "00:0D:BC" to "Cisco Systems",
        "00:50:F2" to "Microsoft",
        "00:0C:29" to "VMware",
        "08:00:27" to "PCS Systemtechnik",
        "52:54:00" to "QEMU",

        // Generic
        "FF:FF:FF" to "Broadcast",
        "00:00:00" to "Unknown"
    )

    /**
     * Get manufacturer from MAC address.
     * Extracts first 3 octets (OUI) and looks up manufacturer.
     */
    fun getManufacturer(macAddress: String?): String? {
        if (macAddress == null || macAddress.length < 8) {
            return null
        }

        return try {
            // Extract first 3 octets: XX:XX:XX
            val oui = macAddress.substring(0, 8).uppercase()
            ouiMap[oui]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if MAC address likely belongs to a camera manufacturer.
     */
    fun isCameraManufacturer(macAddress: String?): Boolean {
        val manufacturer = getManufacturer(macAddress) ?: return false
        return manufacturer.contains("Speco", ignoreCase = true) ||
                manufacturer.contains("Hikvision", ignoreCase = true) ||
                manufacturer.contains("Dahua", ignoreCase = true) ||
                manufacturer.contains("Axis", ignoreCase = true) ||
                manufacturer.contains("Hanwha", ignoreCase = true) ||
                manufacturer.contains("Bosch", ignoreCase = true) ||
                manufacturer.contains("Panasonic", ignoreCase = true) ||
                manufacturer.contains("Canon", ignoreCase = true) ||
                manufacturer.contains("Vivotek", ignoreCase = true) ||
                manufacturer.contains("Arecont", ignoreCase = true)
    }
}
