package com.screenpulse.tv.util

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 网络工具类
 *
 * 获取设备 IP 地址和进行网络状态检查
 * 用于 Web 服务器绑定和 QR 码生成
 */
object NetworkUtils {

    /**
     * 获取设备的局域网 IP 地址
     * 优先获取 Wi-Fi IP，其次获取其他网络接口 IP
     *
     * @return IP 地址字符串，如 "192.168.1.100"
     */
    fun getDeviceIpAddress(): String {
        // 优先通过 NetworkInterface 获取（适用于所有 Android 版本）
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                // 跳过回环接口和未启用接口
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    // 只返回 IPv4 地址
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            // NetworkInterface 方式失败，尝试 WifiManager
        }

        return "127.0.0.1"
    }

    /**
     * 通过 WifiManager 获取 IP 地址（备用方案）
     * 注意：此方法在某些设备上可能返回 "0.0.0.0"
     */
    fun getWifiIpAddress(context: Context): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                Formatter.formatIpAddress(ip)
            } else {
                "127.0.0.1"
            }
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    /**
     * 检查设备是否连接到网络
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager
            val network = connectivityManager?.activeNetworkInfo
            network?.isConnected == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查设备是否连接到 Wi-Fi
     */
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager
            val network = connectivityManager?.activeNetworkInfo
            network?.type == android.net.ConnectivityManager.TYPE_WIFI && network.isConnected
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查是否有效的 IP 地址
     */
    fun isValidIpAddress(ip: String): Boolean {
        return ip.matches(Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"))
    }
}
