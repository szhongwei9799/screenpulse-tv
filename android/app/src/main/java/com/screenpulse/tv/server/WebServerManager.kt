package com.screenpulse.tv.server

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import com.screenpulse.tv.R
import com.screenpulse.tv.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Web 服务器管理器
 *
 * 基于 NanoHTTPD 的嵌入式 Web 服务器，运行在 Android TV 设备上
 * 提供 Web 管理界面和 REST API 接口，用于远程控制播放器
 *
 * 功能：
 * - 托管 Web 管理面板（单页应用 HTML）
 * - 提供完整的 REST API 接口
 * - 支持局域网内设备管理
 * - 自动生成设备管理 URL 和 QR 码
 */
class WebServerManager(private val context: Context) {

    companion object {
        private const val TAG = "WebServerManager"

        /** 默认端口 */
        const val DEFAULT_PORT = 8080

        /** 最大超时时间 */
        private const val SOCKET_TIMEOUT = 30_000 // 30 秒
    }

    /** NanoHTTPD 服务器实例 */
    private var server: NanoHTTPD? = null

    /** API 处理器 */
    private lateinit var apiHandler: ApiHandler

    /** 协程作用域 */
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 服务器是否正在运行 */
    @Volatile
    var isRunning: Boolean = false
        private set

    /** 服务器端口 */
    var port: Int = DEFAULT_PORT
        private set

    /**
     * 启动 Web 服务器
     */
    fun start(port: Int = DEFAULT_PORT) {
        if (isRunning) {
            Log.w(TAG, "Web 服务器已在运行中")
            return
        }

        this.port = port
        apiHandler = ApiHandler(context)

        try {
            server = object : NanoHTTPD(port) {
                override fun serve(session: IHTTPSession): Response {
                    return apiHandler.handleRequest(session)
                }
            }.apply {
                setAsyncRunner(null) // 使用同步模式，控制更精确
                start(SOCKET_TIMEOUT)
            }

            isRunning = true
            val ip = NetworkUtils.getDeviceIpAddress()
            Log.i(TAG, "Web 服务器已启动: http://$ip:$port")

        } catch (e: IOException) {
            Log.e(TAG, "Web 服务器启动失败", e)
            // 端口被占用，尝试下一个端口
            if (port < DEFAULT_PORT + 100) {
                Log.w(TAG, "端口 $port 被占用，尝试端口 ${port + 1}")
                start(port + 1)
            }
        }
    }

    /**
     * 停止 Web 服务器
     */
    fun stop() {
        if (!isRunning) return

        server?.stop()
        server = null
        isRunning = false
        Log.i(TAG, "Web 服务器已停止")
    }

    /**
     * 重启 Web 服务器
     */
    fun restart() {
        val currentPort = port
        stop()
        // 等待端口释放
        Thread.sleep(500)
        start(currentPort)
    }

    /**
     * 获取服务器 URL
     */
    fun getServerUrl(): String {
        val ip = NetworkUtils.getDeviceIpAddress()
        return "http://$ip:$port"
    }

    /**
     * 获取管理面板 URL
     */
    fun getManagementUrl(): String {
        return "${getServerUrl()}/admin"
    }

    /**
     * 释放资源
     */
    fun destroy() {
        stop()
        serverScope.cancel()
    }
}
