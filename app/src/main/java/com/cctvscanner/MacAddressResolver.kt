package com.cctvscanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Resolves MAC addresses and manufacturer information for discovered IP addresses.
 * Uses ARP tables and OUI database for identification.
 */
object MacAddressResolver {

    /**
     * Attempt to resolve MAC address for a given IP address.
     * Uses system ARP table via /proc/net/arp on Linux/Android.
     */
    suspend fun getMacAddressForIp(ipAddress: String): String? = withContext(Dispatchers.IO) {
        try {
            // First try direct InetAddress lookup (limited permissions on Android)
            val inetAddr = InetAddress.getByName(ipAddress)
            return@withContext try {
                val networkInterface = NetworkInterface.getByInetAddress(inetAddr)
                networkInterface?.hardwareAddress?.let {
                    it.joinToString(":") { b -> "%02X".format(b) }
                }
            } catch (e: Exception) {
                null
            }
        } catch (e: Exception) {
            // Fallback: try to read ARP table from /proc/net/arp
            readArpTable(ipAddress)
        }
    }

    /**
     * Read system ARP table to resolve MAC for IP address.
     * Works on Linux/Android with appropriate permissions.
     */
    private fun readArpTable(targetIp: String): String? {
        return try {
            val arpFile = java.io.File("/proc/net/arp")
            if (!arpFile.exists()) return null

            arpFile.bufferedReader().use { reader ->
                reader.lineSequence()
                    .drop(1) // Skip header
                    .forEach { line ->
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 4 && parts[0] == targetIp) {
                            val macAddress = parts[3]
                            // Validate MAC format
                            if (macAddress.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}"))) {
                                return macAddress
                            }
                        }
                    }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get manufacturer information for an IP address.
     * Attempts MAC resolution first, then OUI lookup.
     */
    suspend fun getManufacturerForIp(ipAddress: String): String? = withContext(Dispatchers.IO) {
        val macAddress = getMacAddressForIp(ipAddress) ?: return@withContext null
        OuiDatabase.getManufacturer(macAddress)
    }

    /**
     * Format MAC address to standardized format (XX:XX:XX:XX:XX:XX).
     */
    fun formatMacAddress(mac: String?): String? {
        if (mac == null) return null
        return try {
            when {
                mac.contains(":") && mac.length == 17 -> mac.uppercase()
                mac.contains("-") && mac.length == 17 -> mac.replace("-", ":").uppercase()
                mac.length == 12 && !mac.contains(Regex("[^0-9A-Fa-f]")) -> {
                    // Convert 12-char hex string to XX:XX:XX:XX:XX:XX
                    mac.chunked(2).joinToString(":").uppercase()
                }
                else -> mac.uppercase()
            }
        } catch (e: Exception) {
            mac.uppercase()
        }
    }

    /**
     * Validate MAC address format.
     */
    fun isValidMacAddress(mac: String?): Boolean {
        if (mac == null) return false
        return mac.matches(Regex("([0-9A-Fa-f]{2}[:-]?){5}([0-9A-Fa-f]{2})"))
    }
}
