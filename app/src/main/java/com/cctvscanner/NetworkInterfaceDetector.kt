package com.cctvscanner

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import androidx.activity.ComponentActivity
import java.net.NetworkInterface

class NetworkInterfaceDetector(
    private val context: Context?
) {
    fun detectUsbEthernet(): NetworkInterfaceInfo? {
        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = connectivityManager.activeNetwork ?: return null
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return null

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            else -> "Unknown"
        }

        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        val interfaceName = linkProperties.interfaceName ?: return null

        return linkProperties.toNetworkInterfaceInfo(
            interfaceName = interfaceName,
            displayName = if (transport == "Ethernet") "Ethernet" else "Network",
            transportName = transport
        )
    }

    companion object {
        fun detectUsbEthernet(activity: ComponentActivity?): NetworkInterfaceInfo? {
            val context = activity ?: return null
            return NetworkInterfaceDetector(context).detectUsbEthernet()
        }
    }
}
