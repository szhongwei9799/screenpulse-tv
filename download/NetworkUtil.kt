package com.screenpulse.player.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Network utility class for retrieving device IP addresses and
 * checking connectivity status.
 *
 * Used primarily to display the management URL in the QR code splash screen.
 */
object NetworkUtil {

    private const val TAG = "NetworkUtil"

    /**
     * Returns the device's local IP address (e.g., "192.168.1.100").
     * Prefers Wi-Fi address when available, falls back to any network interface.
     *
     * @return The IP address as a string, or null if no address could be determined.
     */
    fun getDeviceIpAddress(context: Context): String? {
        return getWifiIpAddress(context) ?: getNetworkIpAddress()
    }

    /**
     * Gets the IP address from the Wi-Fi connection.
     */
    private fun getWifiIpAddress(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.connectionInfo?.let { wifiInfo ->
                val ip = wifiInfo.ipAddress
                if (ip != 0) {
                    ipToString(ip)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Wi-Fi IP address", e)
            null
        }
    }

    /**
     * Gets the IP address by enumerating network interfaces.
     */
    private fun getNetworkIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces?.hasMoreElements() == true) {
                val networkInterface = interfaces.nextElement()
                // Skip loopback and down interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    // Skip IPv6 and loopback
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val hostAddress = address.hostAddress ?: continue
                        // Prefer IPv4 addresses
                        if (hostAddress.contains(".")) {
                            return hostAddress
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enumerate network interfaces", e)
            null
        }
    }

    /**
     * Converts an integer IP address to a dotted-decimal string.
     * Handles both IPv4 and IPv6 (Wi-Fi returns int for IPv4).
     */
    private fun ipToString(ip: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            (ip and 0xFF),
            (ip shr 8 and 0xFF),
            (ip shr 16 and 0xFF),
            (ip shr 24 and 0xFF)
        )
    }

    /**
     * Returns the device's MAC address.
     * Tries Wi-Fi first (requires ACCESS_FINE_LOCATION on newer Android),
     * then falls back to enumerating network interfaces.
     */
    fun getMacAddress(context: Context): String? {
        // Try Wi-Fi MAC first
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            if (wifiInfo != null) {
                val mac = wifiInfo.macAddress
                if (mac != null && mac != "02:00:00:00:00:00") {
                    return mac
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Wi-Fi MAC address", e)
        }

        // Fallback: enumerate network interfaces
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces?.hasMoreElements() == true) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp) continue
                val mac = ni.hardwareAddress
                if (mac != null && mac.isNotEmpty()) {
                    return mac.joinToString(":") { "%02X".format(it) }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enumerate MAC addresses", e)
            null
        }
    }

    /**
     * Checks if the device currently has network connectivity.
     *
     * @return true if the device has an active network connection.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking network connectivity", e)
            false
        }
    }

    /**
     * Checks if the device is connected to Wi-Fi specifically.
     *
     * @return true if connected to a Wi-Fi network.
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Wi-Fi connectivity", e)
            false
        }
    }

    /**
     * Checks if the device is connected to Ethernet.
     *
     * @return true if connected via Ethernet (common for Android TV boxes).
     */
    fun isEthernetConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Ethernet connectivity", e)
            false
        }
    }
}
