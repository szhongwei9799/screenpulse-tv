package com.screenpulse.tv.server

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.screenpulse.tv.player.PlaybackEngine
import com.screenpulse.tv.player.PlaylistManager
import com.screenpulse.tv.player.MediaType
import com.screenpulse.tv.db.AppDatabase
import com.screenpulse.tv.db.entities.PlaylistEntity
import com.screenpulse.tv.db.entities.MediaEntity
import com.screenpulse.tv.schedule.ScheduleManager
import com.screenpulse.tv.util.NetworkUtils
import com.screenpulse.tv.tts.TtsEngine
import com.screenpulse.tv.tts.TtsAudioEntity
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * REST API 处理器
 *
 * 处理所有 HTTP 请求，包括：
 * - Web 管理面板的 HTML 页面（4个Tab：播放列表、媒体库、背景音乐/TTS、设置）
 * - RESTful API 端点（播放列表、媒体库、定时任务、播放控制、设置、TTS）
 * - 文件上传处理
 * - TTS 语音合成与管理
 * - 背景音乐控制
 *
 * API 端点列表：
 * GET  /                          - Web 管理面板
 * GET  /admin                     - Web 管理面板（别名）
 * GET  /api/status                - 设备状态
 * GET  /api/playlist              - 获取播放列表
 * POST /api/playlist              - 更新播放列表
 * POST /api/playlist/reorder      - 重新排序
 * DELETE /api/playlist/{id}       - 删除播放项
 * GET  /api/media                 - 获取媒体库列表
 * POST /api/media/upload          - 上传媒体文件
 * POST /api/media/url             - 添加 URL
 * DELETE /api/media/{id}          - 删除媒体
 * GET  /api/schedule              - 获取定时任务
 * POST /api/schedule              - 创建定时任务
 * DELETE /api/schedule/{id}       - 删除定时任务
 * POST /api/control/play          - 播放
 * POST /api/control/pause         - 暂停
 * POST /api/control/skip          - 跳到下一项
 * POST /api/control/previous      - 跳到上一项
 * GET  /api/settings              - 获取设置
 * POST /api/settings              - 更新设置
 * GET  /api/tts/voices            - 获取可用的 TTS 语音列表
 * POST /api/tts/generate          - 生成 TTS 语音
 * GET  /api/tts                   - 获取所有 TTS 音频文件
 * DELETE /api/tts/{id}            - 删除 TTS 音频
 * POST /api/tts/{id}/volume       - 设置 TTS 音量
 * POST /api/tts/{id}/toggle       - 启用/禁用 TTS 音频
 * POST /api/tts/{id}/play         - 播放指定 TTS 音频作为背景音乐
 * POST /api/tts/stop              - 停止背景音乐播放
 * GET  /api/tts/playing           - 获取当前 TTS 播放状态
 * POST /api/tts/queue             - 设置 TTS 播放队列
 * /tts/…                          - TTS 静态文件服务
 * /media/…                        - 媒体静态文件服务
 */
class ApiHandler(private val context: Context) {

    companion object {
        private const val TAG = "ApiHandler"

        /** 媒体文件存储目录 */
        private const val MEDIA_DIR = "screenpulse_media"

        /** TTS文件存储目录 */
        private const val TTS_DIR = "screenpulse_tts"

        /** 上传文件最大大小 (50MB) */
        private const val MAX_UPLOAD_SIZE = 50 * 1024 * 1024L
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        .create()

    private val database: AppDatabase = AppDatabase.getInstance(context)
    private val playlistManager: PlaylistManager = PlaylistManager(context)
    private val scheduleManager: ScheduleManager = ScheduleManager(context)
    private val ttsEngine: TtsEngine = TtsEngine(context)
    private val apiScope = CoroutineScope(Dispatchers.IO)

    // 播放控制回调 - 由 MainActivity/ViewModel 设置
    var onPlayRequest: (() -> Unit)? = null
    var onPauseRequest: (() -> Unit)? = null
    var onSkipRequest: (() -> Unit)? = null
    var onPreviousRequest: (() -> Unit)? = null

    /** PlaybackEngine 引用 - 由外部注入 */
    var playbackEngine: PlaybackEngine? = null

    // ==================== Settings storage ====================
    /** Local tracking for background music playing state */
    @Volatile private var isBgMusicPlaying: Boolean = false
    @Volatile private var currentPlayingTtsTitle: String = ""

    @Volatile private var settingsTransitionEffect: String = "random"
    @Volatile private var settingsTransitionDuration: Int = 600
    @Volatile private var settingsBgMusicVolume: Int = 80
    @Volatile private var settingsVideoMuted: Boolean = false

    /**
     * 处理 HTTP 请求
     * 路由到对应的处理方法
     */
    fun handleRequest(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val uri = session.uri
        val method = session.method

        // 处理 CORS 预检请求
        if (method == NanoHTTPD.Method.OPTIONS) {
            return handleCorsPreflight()
        }

        Log.d(TAG, "请求: $method $uri")

        try {
            return when {
                // ========== Web 管理面板 ==========
                uri == "/" || uri == "/admin" || uri == "/index.html" -> {
                    serveAdminPanel()
                }

                // ========== 设备状态 API ==========
                uri == "/api/status" && method == NanoHTTPD.Method.GET -> {
                    getStatus()
                }

                // ========== 播放列表 API ==========
                uri == "/api/playlist" && method == NanoHTTPD.Method.GET -> {
                    getPlaylist()
                }
                uri == "/api/playlist" && method == NanoHTTPD.Method.POST -> {
                    parsePostBody(session)
                    updatePlaylist(session)
                }
                uri == "/api/playlist/reorder" && method == NanoHTTPD.Method.POST -> {
                    parsePostBody(session)
                    reorderPlaylist(session)
                }
                uri.startsWith("/api/playlist/") && method == NanoHTTPD.Method.DELETE -> {
                    val id = uri.removePrefix("/api/playlist/").toLongOrNull()
                    if (id != null) deletePlaylistItem(id) else badRequest("无效的 ID")
                }

                // ========== 媒体库 API ==========
                uri == "/api/media" && method == NanoHTTPD.Method.GET -> {
                    getMediaLibrary()
                }
                uri == "/api/media/upload" && method == NanoHTTPD.Method.POST -> {
                    handleFileUpload(session)
                }
                uri == "/api/media/url" && method == NanoHTTPD.Method.POST -> {
                    parsePostBody(session)
                    addMediaUrl(session)
                }
                uri.startsWith("/api/media/") && method == NanoHTTPD.Method.DELETE -> {
                    val id = uri.removePrefix("/api/media/").toLongOrNull()
                    if (id != null) deleteMedia(id) else badRequest("无效的 ID")
                }

                // ========== 定时任务 API ==========
                uri == "/api/schedule" && method == NanoHTTPD.Method.GET -> {
                    getSchedules()
                }
                uri == "/api/schedule" && method == NanoHTTPD.Method.POST -> {
                    parsePostBody(session)
                    createSchedule(session)
                }
                uri.startsWith("/api/schedule/") && method == NanoHTTPD.Method.DELETE -> {
                    val id = uri.removePrefix("/api/schedule/").toLongOrNull()
                    if (id != null) deleteSchedule(id) else badRequest("无效的 ID")
                }

                // ========== 播放控制 API ==========
                uri == "/api/control/play" && method == NanoHTTPD.Method.POST -> {
                    controlPlay()
                }
                uri == "/api/control/pause" && method == NanoHTTPD.Method.POST -> {
                    controlPause()
                }
                uri == "/api/control/skip" && method == NanoHTTPD.Method.POST -> {
                    controlSkip()
                }
                uri == "/api/control/previous" && method == NanoHTTPD.Method.POST -> {
                    controlPrevious()
                }

                // ========== 设置 API ==========
                uri == "/api/settings" && method == NanoHTTPD.Method.GET -> {
                    getSettings()
                }
                uri == "/api/settings" && method == NanoHTTPD.Method.POST -> {
                    parsePostBody(session)
                    updateSettings(session)
                }

                // ========== TTS API ==========
                uri == "/api/tts/voices" && method == NanoHTTPD.Method.GET -> {
                    getTtsVoices()
                }
                uri == "/api/tts/generate" && method == NanoHTTPD.Method.POST -> {
                    parsePostBody(session)
                    generateTts(session)
                }
                uri == "/api/tts" && method == NanoHTTPD.Method.GET -> {
                    getTtsLibrary()
                }
                uri == "/api/tts/stop" && method == NanoHTTPD.Method.POST -> {
                    stopBgMusic()
                }
                uri == "/api/tts/playing" && method == NanoHTTPD.Method.GET -> {
                    getTtsPlayingStatus()
                }
                uri == "/api/tts/queue" && method == NanoHTTPD.Method.POST -> {
                    parsePostBody(session)
                    setTtsQueue(session)
                }
                // /api/tts/{id}/volume, /api/tts/{id}/toggle, /api/tts/{id}/play
                uri.startsWith("/api/tts/") && uri.endsWith("/volume") && method == NanoHTTPD.Method.POST -> {
                    val id = uri.removePrefix("/api/tts/").removeSuffix("/volume").toLongOrNull()
                    if (id != null) { parsePostBody(session); setTtsVolume(id, session) }
                    else badRequest("无效的 ID")
                }
                uri.startsWith("/api/tts/") && uri.endsWith("/toggle") && method == NanoHTTPD.Method.POST -> {
                    val id = uri.removePrefix("/api/tts/").removeSuffix("/toggle").toLongOrNull()
                    if (id != null) { parsePostBody(session); toggleTtsEnabled(id, session) }
                    else badRequest("无效的 ID")
                }
                uri.startsWith("/api/tts/") && uri.endsWith("/play") && method == NanoHTTPD.Method.POST -> {
                    val id = uri.removePrefix("/api/tts/").removeSuffix("/play").toLongOrNull()
                    if (id != null) playTtsAudio(id)
                    else badRequest("无效的 ID")
                }
                uri.startsWith("/api/tts/") && method == NanoHTTPD.Method.DELETE -> {
                    val id = uri.removePrefix("/api/tts/").toLongOrNull()
                    if (id != null) deleteTtsAudio(id) else badRequest("无效的 ID")
                }

                // ========== 静态文件（TTS 音频） ==========
                uri.startsWith("/tts/") -> {
                    serveTtsFile(uri.removePrefix("/tts/"))
                }

                // ========== 静态文件（上传的媒体） ==========
                uri.startsWith("/media/") -> {
                    serveMediaFile(uri.removePrefix("/media/"))
                }

                // ========== SPA fallback ==========
                !uri.startsWith("/api/") && !uri.startsWith("/media/") && !uri.startsWith("/tts/") -> {
                    serveAdminPanel()
                }

                else -> {
                    notFound("未找到: $uri")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理请求异常", e)
            serverError(e.message ?: "服务器内部错误")
        }
    }

    // ==================== Web 管理面板 ====================

    /** 提供 Web 管理面板 HTML - 从 assets 读取 */
    private fun serveAdminPanel(): NanoHTTPD.Response {
        val html = readAssetFile("admin_panel.html")
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "text/html; charset=UTF-8",
            html
        ).apply {
            addHeader("Content-Type", "text/html; charset=UTF-8")
            addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        }
    }

    /** 从 assets 读取文件内容 */
    private fun readAssetFile(fileName: String): String {
        return try {
            val inputStream = context.assets.open(fileName)
            BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "读取 assets/$fileName 失败", e)
            "<html><body><h1>管理面板加载失败</h1><p>${e.message}</p></body></html>"
        }
    } // readAssetFile 结束

    // ==================== 设备状态 API ====================

    /** GET /api/status - 获取设备状态 */
    private fun getStatus(): NanoHTTPD.Response {
         val ip = NetworkUtils.getDeviceIpAddress()
         val port = (context as? com.screenpulse.tv.ScreenPulseApp)?.webServerManager?.port ?: 8080

         val playlistCount = runCatching {
             kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                 database.playlistDao().getActivePlaylistCount()
             }
         }.getOrDefault(0)

         val status = mapOf(
             "deviceName" to android.os.Build.MODEL,
             "deviceIp" to ip,
             "ip" to ip,
             "port" to port,
             "status" to "online",
             "playlistCount" to playlistCount,
             "playbackState" to "idle",
             "version" to "1.0.0",
             "timestamp" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
         )

         return okResponse(gson.toJson(status))
     }

    // ==================== 播放列表 API ====================

    /**
     * GET /api/playlist - 获取播放列表
     /** GET /api/playlist - 获取播放列表 */
     private fun getPlaylist(): NanoHTTPD.Response {
         val items = runCatching {
             kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                 database.playlistDao().getActivePlaylistItems()
             }
         }.getOrDefault(emptyList())

         val response = items.map { it.toMap() }
         return okResponse(gson.toJson(response))
     }

    /**
     * POST /api/playlist - 更新整个播放列表
     * 请求体: JSON 数组
     */
    private fun updatePlaylist(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parms
        val body = params["postData"] ?: params["body"] ?: return badRequest("请求体为空")

        return try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val items = gson.fromJson<List<Map<String, Any>>>(body, type)

            apiScope.launch {
                val entities = items.mapIndexed { index, map ->
                    PlaylistEntity(
                        id = (map["id"] as? Double)?.toLong() ?: 0,
                        title = map["title"] as? String ?: "",
                        type = map["type"] as? String ?: MediaType.VIDEO.value,
                        url = map["url"] as? String ?: "",
                        duration = (map["duration"] as? Double)?.toLong(),
                        enabled = map["enabled"] as? Boolean ?: true,
                        volume = (map["volume"] as? Double)?.toInt() ?: 100,
                        order = index,
                        createdAt = (map["createdAt"] as? Double)?.toLong() ?: System.currentTimeMillis()
                    )
                }
                playlistManager.replacePlaylist(entities)
            }

            okResponse(gson.toJson(mapOf("success" to true, "message" to "播放列表已更新")))
        } catch (e: Exception) {
            Log.e(TAG, "更新播放列表失败", e)
            serverError("解析 JSON 失败: ${e.message}")
        }
    }

    /**
     * POST /api/playlist/reorder - 重新排序
     * 请求体: { "orderedIds": [1, 3, 2, ...] }
     */
    private fun reorderPlaylist(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parms
        val body = params["postData"] ?: params["body"] ?: return badRequest("请求体为空")

        return try {
            val type = object : TypeToken<Map<String, List<Long>>>() {}.type
            val data = gson.fromJson<Map<String, List<Long>>>(body, type)
            val orderedIds = data["orderedIds"] ?: return badRequest("缺少 orderedIds")

            apiScope.launch {
                playlistManager.reorderPlaylist(orderedIds)
            }

            okResponse(gson.toJson(mapOf("success" to true, "message" to "顺序已更新")))
        } catch (e: Exception) {
            serverError("排序失败: ${e.message}")
        }
    }

    /**
     * DELETE /api/playlist/{id} - 删除播放项
     */
    private fun deletePlaylistItem(id: Long): NanoHTTPD.Response {
        apiScope.launch {
            playlistManager.deletePlaylistItem(id)
        }
        return okResponse(gson.toJson(mapOf("success" to true, "message" to "已删除")))
    }

    // ==================== 媒体库 API ====================

    /** GET /api/media - 获取媒体库列表 */
    private fun getMediaLibrary(): NanoHTTPD.Response {
        val items = runCatching {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                database.mediaDao().getAll()
            }
        }.getOrDefault(emptyList())

        val response = items.map { media ->
            mapOf(
                "id" to media.id,
                "title" to media.title,
                "type" to media.type,
                "url" to media.url,
                "fileSize" to media.fileSize,
                "thumbnailUrl" to media.thumbnailUrl,
                "createdAt" to media.createdAt
            )
        }

        return okResponse(gson.toJson(response))
    }

    /**
     * POST /api/media/upload - 上传媒体文件
     * 使用 multipart/form-data 格式
     */
    private fun handleFileUpload(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val tempDir = File(context.cacheDir, "uploads")
        if (!tempDir.exists()) tempDir.mkdirs()

        try {
            // 解析 multipart 请求
            val files = mutableMapOf<String, String>()
            session.parseBody(files)

            // 获取上传的文件路径
            val tempFilePath = files["file"] ?: return badRequest("未找到上传文件")
            val tempFile = File(tempFilePath)

            if (tempFile.length() > MAX_UPLOAD_SIZE) {
                tempFile.delete()
                return badRequest("文件大小超过限制 (50MB)")
            }

            // 移动到媒体存储目录
            val mediaDir = File(context.filesDir, MEDIA_DIR)
            if (!mediaDir.exists()) mediaDir.mkdirs()

            val fileName = "${System.currentTimeMillis()}_${tempFile.name}"
            val destFile = File(mediaDir, fileName)
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()

            // 确定媒体类型
            val mediaType = when {
                fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) ||
                fileName.endsWith(".png", true) || fileName.endsWith(".gif", true) ||
                fileName.endsWith(".webp", true) -> MediaType.IMAGE.value
                else -> MediaType.VIDEO.value
            }

            // 保存到媒体库
            val mediaUrl = "/media/$fileName"
            val mediaEntity = MediaEntity(
                title = fileName,
                type = mediaType,
                url = mediaUrl,
                filePath = destFile.absolutePath,
                fileSize = destFile.length(),
                createdAt = System.currentTimeMillis()
            )

            apiScope.launch {
                database.mediaDao().insert(mediaEntity)

                // 同时添加到播放列表
                val playlistItem = PlaylistEntity(
                    title = fileName,
                    type = mediaType,
                    url = mediaUrl,
                    duration = if (mediaType == MediaType.IMAGE.value) 10 else null,
                    enabled = true,
                    volume = 100,
                    order = database.playlistDao().getActivePlaylistCount(),
                    createdAt = System.currentTimeMillis()
                )
                playlistManager.addPlaylistItem(playlistItem)
            }

            return okResponse(gson.toJson(mapOf(
                "success" to true,
                "message" to "上传成功",
                "data" to mapOf(
                    "id" to 0,
                    "title" to fileName,
                    "type" to mediaType,
                    "url" to mediaUrl,
                    "fileSize" to destFile.length()
                )
            )))
        } catch (e: Exception) {
            Log.e(TAG, "文件上传失败", e)
            return serverError("上传失败: ${e.message}")
        }
    }

    /**
     * POST /api/media/url - 通过 URL 添加媒体
     * 请求体: { "url": "...", "type": "video|image|iptv|stream|webpage", "title": "..." }
     */
    private fun addMediaUrl(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parms
        val body = params["postData"] ?: params["body"] ?: return badRequest("请求体为空")

        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val data = gson.fromJson<Map<String, String>>(body, type)

            val url = data["url"] ?: return badRequest("缺少 url")
            val mediaType = data["type"] ?: MediaType.VIDEO.value
            val title = data["title"] ?: url.substringAfterLast("/").substringBefore("?")

            // 保存到媒体库
            val mediaEntity = MediaEntity(
                title = title,
                type = mediaType,
                url = url,
                createdAt = System.currentTimeMillis()
            )

            apiScope.launch {
                database.mediaDao().insert(mediaEntity)

                // 添加到播放列表
                val duration = when (MediaType.fromValue(mediaType)) {
                    MediaType.IMAGE -> 10L
                    MediaType.WEBPAGE -> 30L
                    else -> null
                }

                val playlistItem = PlaylistEntity(
                    title = title,
                    type = mediaType,
                    url = url,
                    duration = duration,
                    enabled = true,
                    volume = 100,
                    order = database.playlistDao().getActivePlaylistCount(),
                    createdAt = System.currentTimeMillis()
                )
                playlistManager.addPlaylistItem(playlistItem)
            }

            okResponse(gson.toJson(mapOf(
                "success" to true,
                "message" to "添加成功",
                "data" to mapOf("title" to title, "type" to mediaType, "url" to url)
            )))
        } catch (e: Exception) {
            serverError("添加失败: ${e.message}")
        }
    }

    /**
     * DELETE /api/media/{id} - 删除媒体
     */
    private fun deleteMedia(id: Long): NanoHTTPD.Response {
        apiScope.launch {
            val media = database.mediaDao().getById(id)
            if (media != null) {
                // 删除本地文件
                media.filePath?.let { File(it).delete() }
                database.mediaDao().deleteById(id)
            }
        }
        return okResponse(gson.toJson(mapOf("success" to true, "message" to "媒体已删除")))
    }

    // ==================== 定时任务 API ====================

    /**
     * GET /api/schedule - 获取定时任务列表
     */
    private fun getSchedules(): NanoHTTPD.Response {
        val schedules = try {
            database.scheduleDao().getAll()
        } catch (e: Exception) {
            emptyList()
        }

        return okResponse(gson.toJson(schedules))
    }

    /**
     * POST /api/schedule - 创建定时任务
     * 请求体: { "cron": "0 9 * * 1-5", "playlistItems": [...], "name": "..." }
     */
    private fun createSchedule(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parms
        val body = params["postData"] ?: params["body"] ?: return badRequest("请求体为空")

        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data = gson.fromJson<Map<String, Any>>(body, type)

            // TODO: 实现定时任务创建逻辑
            okResponse(gson.toJson(mapOf("success" to true, "message" to "定时任务已创建")))
        } catch (e: Exception) {
            serverError("创建定时任务失败: ${e.message}")
        }
    }

    /**
     * DELETE /api/schedule/{id} - 删除定时任务
     */
    private fun deleteSchedule(id: Long): NanoHTTPD.Response {
        // TODO: 实现定时任务删除逻辑
        return okResponse(gson.toJson(mapOf("success" to true, "message" to "定时任务已删除")))
    }

    // ==================== 播放控制 API ====================

    private fun controlPlay(): NanoHTTPD.Response {
        onPlayRequest?.invoke()
        return okResponse(gson.toJson(mapOf("success" to true, "state" to "playing")))
    }

    private fun controlPause(): NanoHTTPD.Response {
        onPauseRequest?.invoke()
        return okResponse(gson.toJson(mapOf("success" to true, "state" to "paused")))
    }

    private fun controlSkip(): NanoHTTPD.Response {
        onSkipRequest?.invoke()
        return okResponse(gson.toJson(mapOf("success" to true, "action" to "skip")))
    }

    private fun controlPrevious(): NanoHTTPD.Response {
        onPreviousRequest?.invoke()
        return okResponse(gson.toJson(mapOf("success" to true, "action" to "previous")))
    }

    // ==================== 设置 API ====================

    /**
     * GET /api/settings - 获取设置
     * 包含播放模式、转场动画、背景音乐音量等
     */
    private fun getSettings(): NanoHTTPD.Response {
        val settings = mapOf(
            "playMode" to (playlistManager.playMode.value),
            "transitionEffect" to settingsTransitionEffect,
            "transitionDuration" to settingsTransitionDuration,
            "defaultImageDuration" to 10,
            "defaultWebpageDuration" to 30,
            "volume" to 100,
            "bgMusicVolume" to settingsBgMusicVolume,
            "videoMuted" to settingsVideoMuted,
            "autoStartOnBoot" to true,
            "serverPort" to 8080
        )
        return okResponse(gson.toJson(settings))
    }

    /**
     * POST /api/settings - 更新设置
     * 支持更新: playMode, transitionEffect, transitionDuration,
     * defaultImageDuration, defaultWebpageDuration, bgMusicVolume, videoMuted
     */
    private fun updateSettings(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parms
        val body = params["postData"] ?: params["body"] ?: return badRequest("请求体为空")

        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data = gson.fromJson<Map<String, Any>>(body, type)

            // 更新播放模式
            val playMode = data["playMode"] as? String
            playMode?.let { playlistManager.setPlayMode(it) }

            // 更新转场效果
            val transitionEffect = data["transitionEffect"] as? String
            transitionEffect?.let {
                settingsTransitionEffect = it
                playbackEngine?.setTransitionEffect(parseTransitionEffect(it))
            }

            // 更新转场时长
            val transitionDuration = data["transitionDuration"] as? Double
            transitionDuration?.let {
                settingsTransitionDuration = it.toInt()
                playbackEngine?.transitionDuration = it.toLong()
            }

            // 更新默认图片时长
            // (stored in-memory, could be persisted to SharedPreferences in future)

            // 更新背景音乐音量
            val bgMusicVolume = data["bgMusicVolume"] as? Double
            bgMusicVolume?.let {
                settingsBgMusicVolume = it.toInt()
                playbackEngine?.setBgMusicVolume(it.toFloat() / 100f)
            }

            // 更新视频静音设置
            val videoMuted = data["videoMuted"] as? Boolean
            videoMuted?.let {
                settingsVideoMuted = it
                playbackEngine?.setVideoMuted(it)
            }

            okResponse(gson.toJson(mapOf("success" to true, "message" to "设置已更新")))
        } catch (e: Exception) {
            serverError("更新设置失败: ${e.message}")
        }
    }

    /**
     * Parse transition effect string to PlaybackEngine.TransitionEffect
     */
    private fun parseTransitionEffect(value: String): PlaybackEngine.TransitionEffect {
        return when (value) {
            "none" -> PlaybackEngine.TransitionEffect.NONE
            "fade" -> PlaybackEngine.TransitionEffect.FADE
            "zoom" -> PlaybackEngine.TransitionEffect.ZOOM
            "slide_left" -> PlaybackEngine.TransitionEffect.SLIDE_LEFT
            "slide_right" -> PlaybackEngine.TransitionEffect.SLIDE_RIGHT
            "dissolve" -> PlaybackEngine.TransitionEffect.DISSOLVE
            else -> PlaybackEngine.TransitionEffect.RANDOM
        }
    }

    // ==================== TTS API ====================

    /**
     * GET /api/tts/voices - 获取可用的TTS语音列表
     */
    private fun getTtsVoices(): NanoHTTPD.Response {
        val voices = TtsEngine.AVAILABLE_VOICES.map { voice ->
            mapOf("id" to voice.id, "name" to voice.name)
        }
        return okResponse(gson.toJson(voices))
    }

    /**
     * POST /api/tts/generate - 生成TTS语音
     * 请求体: { "text": "...", "voiceId": "zh-CN-XiaoxiaoNeural", "title": "..." }
     */
    private fun generateTts(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parms
        val body = params["postData"] ?: params["body"] ?: return badRequest("请求体为空")

        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val data = gson.fromJson<Map<String, String>>(body, type)

            val text = data["text"] ?: return badRequest("缺少 text")
            val voiceId = data["voiceId"] ?: return badRequest("缺少 voiceId")
            val title = data["title"] ?: text.take(50)

            if (text.isBlank()) {
                return badRequest("文本内容不能为空")
            }

            // Generate TTS in coroutine (this is a synchronous handler, so we return immediately
            // and run generation in background)
            apiScope.launch {
                try {
                    val result = ttsEngine.generateSpeech(text, voiceId, title)
                    if (result.success && result.filePath != null) {
                        val file = File(result.filePath)
                        val fileName = file.name
                        val fileUrl = "/tts/$fileName"
                        val estimatedDuration = ttsEngine.estimateDuration(result.fileSize)

                        val entity = TtsAudioEntity(
                            title = title,
                            text = text,
                            voice = voiceId,
                            filePath = result.filePath,
                            fileUrl = fileUrl,
                            fileSize = result.fileSize,
                            duration = estimatedDuration,
                            volume = 80,
                            enabled = true
                        )
                        database.ttsAudioDao().insert(entity)
                        Log.d(TAG, "TTS音频生成成功: $title -> $fileUrl")
                    } else {
                        Log.e(TAG, "TTS音频生成失败: ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TTS音频生成异常", e)
                }
            }

            okResponse(gson.toJson(mapOf(
                "success" to true,
                "message" to "语音生成已启动"
            )))
        } catch (e: Exception) {
            serverError("生成TTS失败: ${e.message}")
        }
    }

    /** GET /api/tts - 获取所有TTS音频文件列表 */
    private fun getTtsLibrary(): NanoHTTPD.Response {
        val items = runCatching {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                database.ttsAudioDao().getAll()
            }
        }.getOrDefault(emptyList())

        val response = items.map { tts ->
            mapOf(
                "id" to tts.id,
                "title" to tts.title,
                "text" to tts.text,
                "voice" to tts.voice,
                "fileUrl" to tts.fileUrl,
                "fileSize" to tts.fileSize,
                "duration" to tts.duration,
                "volume" to tts.volume,
                "enabled" to tts.enabled,
                "createdAt" to tts.createdAt
            )
        }

        return okResponse(gson.toJson(response))
    }
            )
        }

        return okResponse(gson.toJson(response))
    }

    /**
     * DELETE /api/tts/{id} - 删除TTS音频
     */
    private fun deleteTtsAudio(id: Long): NanoHTTPD.Response {
        apiScope.launch {
            try {
                val tts = database.ttsAudioDao().getById(id)
                if (tts != null) {
                    ttsEngine.deleteFile(tts.filePath)
                    database.ttsAudioDao().deleteById(id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除TTS音频失败", e)
            }
        }
        return okResponse(gson.toJson(mapOf("success" to true, "message" to "TTS音频已删除")))
    }

    /**
     * POST /api/tts/{id}/volume - 设置TTS音量
     * 请求体: { "volume": 80 }
     */
    private fun setTtsVolume(id: Long, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parms
        val body = params["postData"] ?: params["body"] ?: return badRequest("请求体为空")

        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data = gson.fromJson<Map<String, Any>>(body, type)
            val volume = (data["volume"] as? Double)?.toInt() ?: return badRequest("缺少 volume")

            apiScope.launch {
                database.ttsAudioDao().updateVolume(id, volume.coerceIn(0, 100))
            }
            okResponse(gson.toJson(mapOf("success" to true, "message" to "音量已更新")))
        } catch (e: Exception) {
            serverError("更新音量失败: ${e.message}")
        }
    }

    /**
     * POST /api/tts/{id}/toggle - 启用/禁用TTS音频
     * 请求体: { "enabled": true }
     */
    private fun toggleTtsEnabled(id: Long, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parms
        val body = params["postData"] ?: params["body"] ?: return badRequest("请求体为空")

        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data = gson.fromJson<Map<String, Any>>(body, type)
            val enabled = data["enabled"] as? Boolean ?: return badRequest("缺少 enabled")

            apiScope.launch {
                database.ttsAudioDao().updateEnabled(id, enabled)
            }
            okResponse(gson.toJson(mapOf("success" to true, "message" to "状态已更新")))
        } catch (e: Exception) {
            serverError("更新状态失败: ${e.message}")
        }
    }

    /**
     * POST /api/tts/{id}/play - 播放指定TTS音频作为背景音乐
     */
    private fun playTtsAudio(id: Long): NanoHTTPD.Response {
        apiScope.launch {
            try {
                val tts = database.ttsAudioDao().getById(id)
                if (tts != null && tts.enabled) {
                    val file = File(tts.filePath)
                    if (file.exists()) {
                        playbackEngine?.playBackgroundMusic(tts.filePath)
                        isBgMusicPlaying = true
                        currentPlayingTtsTitle = tts.title
                        Log.d(TAG, "开始播放TTS音频: ${tts.title}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "播放TTS音频失败", e)
            }
        }
        return okResponse(gson.toJson(mapOf("success" to true, "message" to "开始播放")))
    }

    /**
     * POST /api/tts/stop - 停止背景音乐播放
     */
    private fun stopBgMusic(): NanoHTTPD.Response {
        playbackEngine?.stopBackgroundMusic()
        isBgMusicPlaying = false
        currentPlayingTtsTitle = ""
        return okResponse(gson.toJson(mapOf("success" to true, "message" to "背景音乐已停止")))
    }

    /**
     * GET /api/tts/playing - 获取当前TTS播放状态
     */
    private fun getTtsPlayingStatus(): NanoHTTPD.Response {
        val status = mapOf(
            "playing" to isBgMusicPlaying,
            "title" to currentPlayingTtsTitle
        )
        return okResponse(gson.toJson(status))
    }

    /**
     * POST /api/tts/queue - 设置TTS播放队列
     * 请求体: { "audioIds": [1, 2, 3], "startIndex": 0 }
     */
    private fun setTtsQueue(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parms
        val body = params["postData"] ?: params["body"] ?: return badRequest("请求体为空")

        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data = gson.fromJson<Map<String, Any>>(body, type)

            @Suppress("UNCHECKED_CAST")
            val audioIds = (data["audioIds"] as? List<Double>)?.map { it.toLong() }
                ?: return badRequest("缺少 audioIds")
            val startIndex = (data["startIndex"] as? Double)?.toInt() ?: 0

            apiScope.launch {
                try {
                    val audioPaths = mutableListOf<String>()
                    for (id in audioIds) {
                        val tts = database.ttsAudioDao().getById(id)
                        if (tts != null && tts.enabled && File(tts.filePath).exists()) {
                            audioPaths.add(tts.filePath)
                        }
                    }
                    if (audioPaths.isNotEmpty()) {
                        playbackEngine?.playTtsAudioList(audioPaths, startIndex)
                        isBgMusicPlaying = true
                        currentPlayingTtsTitle = "播放队列 (${audioPaths.size}首)"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "设置TTS队列失败", e)
                }
            }

            okResponse(gson.toJson(mapOf(
                "success" to true,
                "message" to "播放队列已设置"
            )))
        } catch (e: Exception) {
            serverError("设置队列失败: ${e.message}")
        }
    }

    // ==================== 静态文件服务 ====================

    /**
     * 提供媒体文件下载
     */
    private fun serveMediaFile(fileName: String): NanoHTTPD.Response {
        val file = File(context.filesDir, "$MEDIA_DIR/$fileName")
        return if (file.exists()) {
            val mimeType = when {
                fileName.endsWith(".mp4", true) -> "video/mp4"
                fileName.endsWith(".mkv", true) -> "video/x-matroska"
                fileName.endsWith(".avi", true) -> "video/x-msvideo"
                fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
                fileName.endsWith(".png", true) -> "image/png"
                fileName.endsWith(".gif", true) -> "image/gif"
                fileName.endsWith(".webp", true) -> "image/webp"
                else -> "application/octet-stream"
            }
            NanoHTTPD.newChunkedResponse(
                NanoHTTPD.Response.Status.OK,
                mimeType,
                file.inputStream()
            )
        } else {
            notFound("文件不存在: $fileName")
        }
    }

    /**
     * 提供TTS音频文件下载
     */
    private fun serveTtsFile(fileName: String): NanoHTTPD.Response {
        val file = File(context.filesDir, "$TTS_DIR/$fileName")
        return if (file.exists()) {
            val mimeType = when {
                fileName.endsWith(".mp3", true) -> "audio/mpeg"
                fileName.endsWith(".wav", true) -> "audio/wav"
                fileName.endsWith(".ogg", true) -> "audio/ogg"
                else -> "application/octet-stream"
            }
            NanoHTTPD.newChunkedResponse(
                NanoHTTPD.Response.Status.OK,
                mimeType,
                file.inputStream()
            )
        } else {
            notFound("TTS文件不存在: $fileName")
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析 POST 请求体
     */
    private fun parsePostBody(session: NanoHTTPD.IHTTPSession) {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength > 0 && contentLength < 10 * 1024 * 1024) {
            try {
                val buffer = ByteArray(contentLength)
                session.inputStream.read(buffer, 0, contentLength)
                val body = String(buffer)
                session.parms["postData"] = body
            } catch (e: Exception) {
                Log.w(TAG, "解析 POST 请求体失败", e)
            }
        }
    }

    /**
     * 处理 CORS 预检请求
     */
    private fun handleCorsPreflight(): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "text/plain",
            ""
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
            addHeader("Access-Control-Max-Age", "86400")
        }
    }

    /**
     * 成功响应
     */
    private fun okResponse(body: String): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json; charset=UTF-8",
            body
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
            addHeader("Access-Control-Max-Age", "86400")
        }
    }

    /**
     * 400 错误
     */
    private fun badRequest(message: String): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.BAD_REQUEST,
            "application/json; charset=UTF-8",
            gson.toJson(mapOf("success" to false, "error" to message))
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    /**
     * 404 错误
     */
    private fun notFound(message: String): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "application/json; charset=UTF-8",
            gson.toJson(mapOf("success" to false, "error" to message))
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    /**
     * 500 错误
     */
    private fun serverError(message: String): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json; charset=UTF-8",
            gson.toJson(mapOf("success" to false, "error" to message))
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }
}
private fun PlaylistEntity.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "title" to title,
    "type" to type,
    "url" to url,
    "duration" to (duration ?: 0),
    "enabled" to enabled,
    "volume" to volume,
    "order" to order,
    "createdAt" to createdAt
)
