package com.screenpulse.player.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.screenpulse.player.data.AppDatabase
import com.screenpulse.player.data.entity.MediaGroup
import com.screenpulse.player.data.entity.MediaGroupItem
import com.screenpulse.player.data.entity.MediaItem
import com.screenpulse.player.data.entity.MediaType
import com.screenpulse.player.data.entity.PlaylistConfig
import com.screenpulse.player.data.entity.BackgroundMusic
import com.screenpulse.player.data.entity.ScheduledTask
import com.screenpulse.player.util.NetworkUtil
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Handles REST API routing logic for the embedded web server.
 * Each method parses the request body (JSON), invokes the appropriate
 * DAO operation, and returns a JSON response.
 */
class ApiRouter(
    private val database: AppDatabase,
    private val uploadDir: File,
    private val context: Context
) {

    companion object {
        private const val TAG = "ApiRouter"
        private val activeTokens = mutableSetOf<String>()
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val mediaItemDao = database.mediaItemDao()
    private val configDao = database.playlistConfigDao()
    private val mediaGroupDao = database.mediaGroupDao()
    private val scheduledTaskDao = database.scheduledTaskDao()
    private val bgMusicDao = database.backgroundMusicDao()
    private val bgMusicDir = File(uploadDir, "background_music")

    init {
        bgMusicDir.mkdirs()
    }

    // Password stored in a hidden file in the app's internal storage
    private val passwordFile = File(context.filesDir, ".admin_password")

    private fun getStoredPassword(): String {
        return if (passwordFile.exists()) {
            try { passwordFile.readText().trim() } catch (_: Exception) { "admin" }
        } else {
            "admin"
        }
    }

    private fun setStoredPassword(pwd: String) {
        try {
            passwordFile.writeText(pwd)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write password file", e)
        }
    }

    /**
     * Check if the request has a valid auth token.
     * Tokens are checked from Authorization header or cookie.
     * If no tokens exist at all (fresh start), allows through.
     */
    fun isAuthenticated(session: NanoHTTPD.IHTTPSession): Boolean {
        // If no tokens have ever been issued, allow all requests
        // (this handles the first-time setup where no password has been set)
        synchronized(activeTokens) {
            if (activeTokens.isEmpty()) return true
        }
        val authHeader = session.headers["authorization"]
        val token = authHeader?.removePrefix("Bearer ")?.trim() ?: ""
        return synchronized(activeTokens) { activeTokens.contains(token) }
    }

    // =====================================================================
    //  POST /api/auth/login
    // =====================================================================

    fun login(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val password = body.get("password")?.asString ?: ""

                val storedPassword = getStoredPassword()

                if (password == storedPassword) {
                    val token = UUID.randomUUID().toString()
                    synchronized(activeTokens) { activeTokens.add(token) }
                    Log.d(TAG, "Login successful, token issued")
                    val result = JsonObject().apply {
                        addProperty("success", true)
                        addProperty("token", token)
                    }
                    jsonResponse(result)
                } else {
                    jsonResponseError("密码错误", NanoHTTPD.Response.Status.UNAUTHORIZED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                jsonResponseError("登录失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  POST /api/auth/password
    // =====================================================================

    fun changePassword(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val oldPassword = body.get("oldPassword")?.asString ?: ""
                val newPassword = body.get("newPassword")?.asString ?: ""

                val storedPassword = getStoredPassword()

                if (oldPassword != storedPassword) {
                    return@runBlocking jsonResponseError("原密码错误", NanoHTTPD.Response.Status.BAD_REQUEST)
                }
                if (newPassword.length < 4) {
                    return@runBlocking jsonResponseError("新密码至少4位", NanoHTTPD.Response.Status.BAD_REQUEST)
                }

                setStoredPassword(newPassword)
                Log.d(TAG, "Password changed successfully")
                jsonResponse(JsonObject().apply { addProperty("success", true) })
            } catch (e: Exception) {
                Log.e(TAG, "Change password failed", e)
                jsonResponseError("修改密码失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  GET /api/status
    // =====================================================================

    fun getStatus(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            val itemCount = mediaItemDao.getEnabledCount()
            val totalCount = mediaItemDao.getTotalCount()
            val config = configDao.getConfigOnce() ?: PlaylistConfig()
            val ipAddress = NetworkUtil.getDeviceIpAddress(context) ?: "unknown"

            // Calculate uptime
            val uptimeMs = System.currentTimeMillis() - android.os.Build.TIME
            val uptimeSecs = uptimeMs / 1000
            val hours = uptimeSecs / 3600
            val minutes = (uptimeSecs % 3600) / 60
            val uptimeStr = "${hours}h ${minutes}m"

            // Calculate storage used
            val uploadDirSize = uploadDir.listFiles()?.sumOf { it.length() } ?: 0
            val storageUsedStr = if (uploadDirSize > 1073741824) {
                String.format("%.1f GB", uploadDirSize / 1073741824.0)
            } else if (uploadDirSize > 1048576) {
                String.format("%.1f MB", uploadDirSize / 1048576.0)
            } else {
                uploadDirSize.toString() + " B"
            }

            // Get MAC address
            val macAddress = try { NetworkUtil.getMacAddress(context) } catch (_: Exception) { null }

            val status = JsonObject().apply {
                addProperty("online", true)
                addProperty("deviceName", android.os.Build.MODEL)
                addProperty("model", android.os.Build.MODEL)
                addProperty("ip", ipAddress)
                addProperty("ipAddress", ipAddress)
                addProperty("port", 8080)
                addProperty("managementUrl", "http://$ipAddress:8080")
                addProperty("uptime", uptimeStr)
                addProperty("mediaCount", totalCount)
                addProperty("activeItems", itemCount)
                addProperty("storageUsed", storageUsedStr)
                addProperty("playbackMode", config.orderMode)
                addProperty("orderMode", config.orderMode)
                addProperty("repeatMode", config.repeatMode)
                addProperty("interstitialEnabled", config.interstitialEnabled)
                addProperty("volumeLevel", config.volumeLevel)
                addProperty("volume", config.volumeLevel)
                addProperty("androidVersion", android.os.Build.VERSION.RELEASE)
                addProperty("appVersion", "1.0.0")
                if (macAddress != null) addProperty("mac", macAddress)
            }

            jsonResponse(status)
        }
    }

    // =====================================================================
    //  GET /api/playlist
    // =====================================================================

    fun getPlaylist(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            val items = mediaItemDao.getAllItemsOnce()
            val json = gson.toJson(items)
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", json)
        }
    }

    // =====================================================================
    //  POST /api/playlist
    // =====================================================================

    fun addPlaylistItem(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val title = body.get("title")?.asString ?: "Untitled"
                val url = body.get("url")?.asString ?: ""
                val typeName = body.get("type")?.asString ?: MediaType.VIDEO.name
                val durationSeconds = body.get("durationSeconds")?.asInt ?: 0
                val enabled = body.get("enabled")?.asBoolean ?: true
                val sortOrder = body.get("sortOrder")?.asInt ?: mediaItemDao.getTotalCount()

                if (url.isBlank()) {
                    return@runBlocking jsonResponseError("URL is required", NanoHTTPD.Response.Status.BAD_REQUEST)
                }

                val mediaItem = MediaItem(
                    title = title,
                    url = url,
                    type = try { MediaType.valueOf(typeName) } catch (_: Exception) { MediaType.VIDEO },
                    durationSeconds = durationSeconds,
                    enabled = enabled,
                    sortOrder = sortOrder,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = mediaItemDao.insert(mediaItem)
                mediaItem.id = id

                Log.d(TAG, "Added media item: $title (id=$id)")
                jsonResponse(gson.toJsonTree(mediaItem))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add playlist item", e)
                jsonResponseError("Failed to add item: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  PUT /api/playlist/:id
    // =====================================================================

    fun updatePlaylistItem(session: NanoHTTPD.IHTTPSession, id: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                val existing = mediaItemDao.getItemById(id)
                if (existing == null) {
                    return@runBlocking jsonResponseError("Item not found", NanoHTTPD.Response.Status.NOT_FOUND)
                }

                val body = parseBody(session)
                val updated = existing.copy(
                    title = body.get("title")?.asString ?: existing.title,
                    url = body.get("url")?.asString ?: existing.url,
                    type = body.get("type")?.asString?.let {
                        try { MediaType.valueOf(it) } catch (_: Exception) { existing.type }
                    } ?: existing.type,
                    durationSeconds = body.get("durationSeconds")?.asInt ?: existing.durationSeconds,
                    enabled = body.get("enabled")?.asBoolean ?: existing.enabled,
                    sortOrder = body.get("sortOrder")?.asInt ?: existing.sortOrder,
                    updatedAt = System.currentTimeMillis()
                )

                mediaItemDao.update(updated)
                Log.d(TAG, "Updated media item: $id")
                jsonResponse(gson.toJsonTree(updated))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update playlist item: $id", e)
                jsonResponseError("Failed to update: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  DELETE /api/playlist/:id
    // =====================================================================

    fun deletePlaylistItem(session: NanoHTTPD.IHTTPSession, id: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                val existing = mediaItemDao.getItemById(id)
                if (existing == null) {
                    return@runBlocking jsonResponseError("Item not found", NanoHTTPD.Response.Status.NOT_FOUND)
                }

                mediaItemDao.deleteById(id)

                // Also delete the uploaded file if it's a local file
                if (existing.url.startsWith(uploadDir.absolutePath)) {
                    File(existing.url).delete()
                }

                Log.d(TAG, "Deleted media item: $id")
                val result = JsonObject().apply {
                    addProperty("success", true)
                    addProperty("deletedId", id)
                }
                jsonResponse(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete playlist item: $id", e)
                jsonResponseError("Failed to delete: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  POST /api/playlist/reorder
    // =====================================================================

    fun reorderPlaylist(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val items = body.getAsJsonArray("items") ?: return@runBlocking jsonResponseError(
                    "items array required",
                    NanoHTTPD.Response.Status.BAD_REQUEST
                )

                val updates = mutableMapOf<Long, Int>()
                for (element in items) {
                    val item = element.asJsonObject
                    val itemId = item.get("id")?.asLong ?: continue
                    val sortOrder = item.get("sortOrder")?.asInt ?: continue
                    updates[itemId] = sortOrder
                }

                if (updates.isNotEmpty()) {
                    mediaItemDao.reorderItems(updates)
                }

                Log.d(TAG, "Reordered ${updates.size} items")
                val result = JsonObject().apply {
                    addProperty("success", true)
                    addProperty("updatedCount", updates.size)
                }
                jsonResponse(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reorder playlist", e)
                jsonResponseError("Failed to reorder: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  GET /api/config
    // =====================================================================

    fun getConfig(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val config = configDao.getConfigOnce() ?: PlaylistConfig()
                val ipAddress = NetworkUtil.getDeviceIpAddress(context) ?: "unknown"
                val netInfo = JsonObject().apply {
                    addProperty("ip", ipAddress)
                    addProperty("mac", NetworkUtil.getMacAddress(context) ?: "--")
                }

                val json = gson.toJson(config)
                val result = JsonParser.parseString(json).asJsonObject
                result.add("network", netInfo)

                jsonResponse(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get config", e)
                jsonResponseError("Failed to get config: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  PUT /api/config
    // =====================================================================

    fun updateConfig(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val existingConfig = configDao.getConfigOnce() ?: PlaylistConfig()

                val updatedConfig = existingConfig.copy(
                    orderMode = body.get("orderMode")?.asString ?: existingConfig.orderMode,
                    repeatMode = body.get("repeatMode")?.asString ?: existingConfig.repeatMode,
                    repeatCount = body.get("repeatCount")?.asInt ?: existingConfig.repeatCount,
                    volumeLevel = body.get("volumeLevel")?.asInt ?: body.get("volume")?.asInt ?: existingConfig.volumeLevel,
                    imageDuration = body.get("imageDuration")?.asInt ?: existingConfig.imageDuration,
                    deviceName = body.get("deviceName")?.asString ?: existingConfig.deviceName,
                    orientation = body.get("orientation")?.asString ?: existingConfig.orientation,
                    idleTimeout = body.get("idleTimeout")?.asInt ?: existingConfig.idleTimeout,
                    interstitialEnabled = body.get("interstitialEnabled")?.asBoolean ?: existingConfig.interstitialEnabled,
                    interstitialStartHour = body.get("interstitialStartHour")?.asInt ?: existingConfig.interstitialStartHour,
                    interstitialEndHour = body.get("interstitialEndHour")?.asInt ?: existingConfig.interstitialEndHour,
                    interstitialPlaylistName = body.get("interstitialPlaylistName")?.asString ?: existingConfig.interstitialPlaylistName,
                    offScreenMessage = body.get("offScreenMessage")?.asString ?: existingConfig.offScreenMessage,
                    bgMusicEnabled = body.get("bgMusicEnabled")?.asBoolean ?: existingConfig.bgMusicEnabled,
                    bgMusicVolume = body.get("bgMusicVolume")?.asInt ?: existingConfig.bgMusicVolume,
                    bgMusicLoop = body.get("bgMusicLoop")?.asBoolean ?: existingConfig.bgMusicLoop,
                    bgMusicShuffle = body.get("bgMusicShuffle")?.asBoolean ?: existingConfig.bgMusicShuffle,
                    lastUpdated = System.currentTimeMillis()
                )

                configDao.insert(updatedConfig)
                Log.d(TAG, "Updated config: orderMode=${updatedConfig.orderMode}, repeatMode=${updatedConfig.repeatMode}")
                jsonResponse(gson.toJsonTree(updatedConfig))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update config", e)
                jsonResponseError("Failed to update config: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  POST /api/upload
    // =====================================================================

    fun uploadFile(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val files = mutableMapOf<String, String>()
        try {
            // Set temp dir for NanoHTTPD to use app's cache directory
            // Android TV's default java.io.tmpdir may not be writable
            System.setProperty("java.io.tmpdir", File(context.cacheDir, "nanohttpd_tmp").also { it.mkdirs() }.absolutePath)
            session.parseBody(files)
        } catch (e: Exception) {
            Log.e(TAG, "parseBody failed: ${e.message}", e)
            return jsonResponseError("文件上传解析失败: ${e.message}", NanoHTTPD.Response.Status.BAD_REQUEST)
        }
        val tempFilePath = files["file"] ?: return jsonResponseError("未找到上传文件", NanoHTTPD.Response.Status.BAD_REQUEST)
        val tempFile = File(tempFilePath)
        if (!tempFile.exists()) {
            return jsonResponseError("临时文件不存在: ${tempFile.absolutePath}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
        }

        // Extract original filename from multipart Content-Disposition header
        // NanoHTTPD stores the filename in session.parameters[fieldName]
        val originalName = session.parameters["file"]?.firstOrNull()?.let {
            // Strip any directory path (browser may include full path)
            it.substringAfterLast('/')
        }
        val fallbackName = tempFile.name
        val nameToUse = if (!originalName.isNullOrBlank() && originalName.contains('.')) {
            originalName
        } else {
            fallbackName
        }
        Log.d(TAG, "Upload original filename: $originalName, using: $nameToUse")

        return runBlocking {
            try {
                // Keep original filename, only strip path separators and null bytes
                val safeName = nameToUse
                    .replace("\\0", "")
                    .replace("..", "_")
                val destFile = File(uploadDir, safeName)

                // If filename conflicts, append a number
                var finalFile = destFile
                var counter = 1
                while (finalFile.exists() && finalFile != tempFile) {
                    val base = nameToUse.substringBeforeLast('.')
                    val ext = nameToUse.substringAfterLast('.')
                    finalFile = File(uploadDir, "${base}_${counter}.${ext}")
                    counter++
                }

                // Copy uploaded file to persistent storage
                java.io.FileInputStream(tempFile).use { input ->
                    FileOutputStream(finalFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Always auto-add to playlist database
                val type = detectMediaType(finalFile.name)
                // Use original filename (before any sanitization) as display title
                val displayTitle = (originalName ?: nameToUse).substringBeforeLast(".").take(200)
                val mediaItem = MediaItem(
                    title = displayTitle,
                    url = finalFile.absolutePath,
                    type = type,
                    durationSeconds = if (type == MediaType.IMAGE) 10 else 0,
                    sortOrder = mediaItemDao.getTotalCount()
                )
                val id = mediaItemDao.insert(mediaItem)
                mediaItem.id = id

                // Clean up temp file
                try { tempFile.delete() } catch (_: Exception) {}

                val result = JsonObject().apply {
                    addProperty("success", true)
                    addProperty("filename", finalFile.name)
                    addProperty("url", finalFile.absolutePath)
                    addProperty("size", finalFile.length())
                    add("mediaItem", gson.toJsonTree(mediaItem))
                }

                jsonResponse(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload file", e)
                jsonResponseError("上传失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  GET /api/scan
    // =====================================================================

    fun triggerScan(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        // Trigger a scan of common media directories
        return runBlocking {
            try {
                var foundCount = 0
                val scanDirs = listOf(
                    File(android.os.Environment.getExternalStorageDirectory(), "ScreenPulse"),
                    File(android.os.Environment.getExternalStorageDirectory(), "DCIM"),
                    File(android.os.Environment.getExternalStorageDirectory(), "Movies"),
                    File(android.os.Environment.getExternalStorageDirectory(), "Pictures"),
                    uploadDir
                )

                for (dir in scanDirs) {
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.forEach { file ->
                            if (file.isFile && file.canRead()) {
                                // Check if already in playlist
                                val existing = mediaItemDao.getAllItemsOnce()
                                val alreadyExists = existing.any { it.url == file.absolutePath }
                                if (!alreadyExists) {
                                    val type = detectMediaType(file.name)
                                    val item = MediaItem(
                                        title = file.nameWithoutExtension,
                                        url = file.absolutePath,
                                        type = type,
                                        durationSeconds = if (type == MediaType.IMAGE) 10 else 0,
                                        sortOrder = mediaItemDao.getTotalCount()
                                    )
                                    mediaItemDao.insert(item)
                                    foundCount++
                                }
                            }
                        }
                    }
                }

                // Fetch all media items from database and enrich with file system metadata
                val allItems = mediaItemDao.getAllItemsOnce()
                val filesArray = com.google.gson.JsonArray()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                for (item in allItems) {
                    val file = File(item.url)
                    val obj = com.google.gson.JsonObject()
                    obj.addProperty("id", item.id)
                    obj.addProperty("name", file.name)
                    obj.addProperty("title", item.title)
                    obj.addProperty("path", item.url)
                    obj.addProperty("type", item.type.name)
                    obj.addProperty("size", if (file.exists()) file.length() else 0)
                    obj.addProperty("date", if (file.exists()) dateFormat.format(Date(file.lastModified())) else "")
                    filesArray.add(obj)
                }

                val result = JsonObject().apply {
                    addProperty("success", true)
                    addProperty("filesFound", foundCount)
                    add("files", filesArray)
                }
                jsonResponse(result)
            } catch (e: Exception) {
                Log.e(TAG, "Media scan failed", e)
                jsonResponseError("Scan failed: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  GET /api/media - list all media items (lightweight, no scan)
    // =====================================================================

    fun getMediaList(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val allItems = mediaItemDao.getAllItemsOnce()
                val filesArray = com.google.gson.JsonArray()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                for (item in allItems) {
                    val file = File(item.url)
                    val obj = com.google.gson.JsonObject()
                    obj.addProperty("id", item.id)
                    obj.addProperty("name", file.name)
                    obj.addProperty("title", item.title)
                    obj.addProperty("path", item.url)
                    obj.addProperty("type", item.type.name)
                    obj.addProperty("size", if (file.exists()) file.length() else 0)
                    obj.addProperty("date", if (file.exists()) dateFormat.format(Date(file.lastModified())) else "")
                    filesArray.add(obj)
                }
                val result = JsonObject().apply {
                    addProperty("success", true)
                    add("files", filesArray)
                }
                jsonResponse(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get media list", e)
                jsonResponseError("获取媒体列表失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  GET /api/playback-stats
    // =====================================================================

    fun getPlaybackStats(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return try {
            val manager = WebServer.activePlaylistManager
            if (manager == null) {
                val empty = JsonObject().apply {
                    addProperty("currentPlayingTitle", "")
                    addProperty("currentPlayingType", "")
                    addProperty("currentIndex", 0)
                    addProperty("totalPlayCount", 0)
                    addProperty("loopCount", 0)
                    add("itemStats", com.google.gson.JsonArray())
                    addProperty("playbackMode", "LOOP")
                    addProperty("totalItems", 0)
                }
                jsonResponse(empty)
            } else {
                val stats = manager.getPlaybackStats()
                val json = gson.toJsonTree(stats).asJsonObject
                jsonResponse(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get playback stats", e)
            jsonResponseError("获取播放统计失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
        }
    }

    // =====================================================================
    //  DELETE /api/media/:id
    // =====================================================================

    fun deleteMediaItem(session: NanoHTTPD.IHTTPSession, id: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                val existing = mediaItemDao.getItemById(id)
                if (existing == null) {
                    return@runBlocking jsonResponseError("文件未找到", NanoHTTPD.Response.Status.NOT_FOUND)
                }

                // Delete file from disk
                val file = File(existing.url)
                if (file.exists()) file.delete()

                // Delete from database
                mediaItemDao.deleteById(id)

                Log.d(TAG, "Deleted media file: ${file.name} (id=$id)")
                val result = JsonObject().apply {
                    addProperty("success", true)
                    addProperty("deletedId", id)
                }
                jsonResponse(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete media file: $id", e)
                jsonResponseError("删除失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  PUT /api/media/:id  (rename)
    // =====================================================================

    fun renameMediaItem(session: NanoHTTPD.IHTTPSession, id: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                val existing = mediaItemDao.getItemById(id)
                if (existing == null) {
                    return@runBlocking jsonResponseError("文件未找到", NanoHTTPD.Response.Status.NOT_FOUND)
                }

                val body = parseBody(session)
                val newTitle = body.get("title")?.asString
                if (newTitle.isNullOrBlank()) {
                    return@runBlocking jsonResponseError("文件名不能为空", NanoHTTPD.Response.Status.BAD_REQUEST)
                }

                val oldFile = File(existing.url)
                val ext = oldFile.extension
                val newFileName = if (ext.isNotEmpty()) "$newTitle.$ext" else newTitle
                val newFile = File(oldFile.parentFile, newFileName)

                // Rename file on disk
                if (oldFile.exists() && !newFile.exists()) {
                    oldFile.renameTo(newFile)
                }

                // Update database
                val updated = existing.copy(
                    title = newTitle,
                    url = newFile.absolutePath,
                    updatedAt = System.currentTimeMillis()
                )
                mediaItemDao.update(updated)

                Log.d(TAG, "Renamed media: ${existing.title} -> $newTitle (id=$id)")
                val result = com.google.gson.JsonObject()
                result.addProperty("id", updated.id)
                result.addProperty("name", newFile.name)
                result.addProperty("title", newTitle)
                result.addProperty("path", newFile.absolutePath)
                result.addProperty("type", updated.type.name)
                result.addProperty("success", true)
                jsonResponse(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename media file: $id", e)
                jsonResponseError("重命名失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  Groups APIs
    // =====================================================================

    // GET /api/groups
    fun getGroups(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val groups = mediaGroupDao.getAllGroups()
                val groupsArray = com.google.gson.JsonArray()
                for (group in groups) {
                    val itemCount = mediaGroupDao.getGroupItemCount(group.id)
                    val obj = com.google.gson.JsonObject()
                    obj.addProperty("id", group.id)
                    obj.addProperty("name", group.name)
                    obj.addProperty("description", group.description)
                    obj.addProperty("color", group.color)
                    obj.addProperty("itemCount", itemCount)
                    obj.addProperty("createdAt", group.createdAt)
                    obj.addProperty("updatedAt", group.updatedAt)
                    groupsArray.add(obj)
                }
                val result = JsonObject().apply {
                    addProperty("success", true)
                    add("groups", groupsArray)
                }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("获取分组失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // POST /api/groups
    fun createGroup(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val name = body.get("name")?.asString ?: ""
                val description = body.get("description")?.asString ?: ""
                val color = body.get("color")?.asString ?: "#409EFF"
                if (name.isBlank()) return@runBlocking jsonResponseError("分组名称不能为空", NanoHTTPD.Response.Status.BAD_REQUEST)

                val group = MediaGroup(name = name, description = description, color = color)
                val id = mediaGroupDao.insert(group)
                group.id = id

                val result = JsonObject().apply {
                    addProperty("success", true)
                    add("group", gson.toJsonTree(group))
                }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("创建分组失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // PUT /api/groups/:id
    fun updateGroup(session: NanoHTTPD.IHTTPSession, id: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                val existing = mediaGroupDao.getGroupById(id) ?: return@runBlocking jsonResponseError("分组未找到", NanoHTTPD.Response.Status.NOT_FOUND)
                val body = parseBody(session)
                val updated = existing.copy(
                    name = body.get("name")?.asString ?: existing.name,
                    description = body.get("description")?.asString ?: existing.description,
                    color = body.get("color")?.asString ?: existing.color,
                    updatedAt = System.currentTimeMillis()
                )
                mediaGroupDao.update(updated)
                val result = JsonObject().apply { addProperty("success", true) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("更新分组失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // DELETE /api/groups/:id
    fun deleteGroup(session: NanoHTTPD.IHTTPSession, id: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                if (id == 1L) {
                    return@runBlocking jsonResponseError("默认分组不可删除", NanoHTTPD.Response.Status.FORBIDDEN)
                }
                mediaGroupDao.clearGroupItems(id)
                mediaGroupDao.deleteById(id)
                val result = JsonObject().apply { addProperty("success", true) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("删除分组失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // POST /api/groups/:id/items - add media items to a group
    fun addItemsToGroup(session: NanoHTTPD.IHTTPSession, groupId: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                val group = mediaGroupDao.getGroupById(groupId) ?: return@runBlocking jsonResponseError("分组未找到", NanoHTTPD.Response.Status.NOT_FOUND)
                val body = parseBody(session)
                val mediaItemIds = body.getAsJsonArray("mediaItemIds") ?: return@runBlocking jsonResponseError("mediaItemIds required", NanoHTTPD.Response.Status.BAD_REQUEST)
                val items = mediaItemIds.map {
                    MediaGroupItem(groupId = groupId, mediaItemId = it.asLong)
                }
                mediaGroupDao.addGroupItems(items)
                val result = JsonObject().apply { addProperty("success", true); addProperty("addedCount", items.size) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("添加项目到分组失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // DELETE /api/groups/:groupId/items/:mediaItemId
    fun removeItemFromGroup(session: NanoHTTPD.IHTTPSession, groupId: Long, mediaItemId: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                mediaGroupDao.removeGroupItem(groupId, mediaItemId)
                val result = JsonObject().apply { addProperty("success", true) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("移除项目失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // GET /api/groups/:id/items - get all items in a group
    fun getGroupItems(session: NanoHTTPD.IHTTPSession, groupId: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                val mediaIds = mediaGroupDao.getGroupMediaIds(groupId)
                val itemsArray = com.google.gson.JsonArray()
                for (mediaId in mediaIds) {
                    val item = mediaItemDao.getItemById(mediaId)
                    if (item != null) {
                        val obj = gson.toJsonTree(item).asJsonObject
                        val file = File(item.url)
                        obj.addProperty("name", file.name)
                        obj.addProperty("size", if (file.exists()) file.length() else 0)
                        itemsArray.add(obj)
                    }
                }
                val result = JsonObject().apply { addProperty("success", true); add("items", itemsArray) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("获取分组项目失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  Scheduled Tasks APIs
    // =====================================================================

    // GET /api/schedules
    fun getSchedules(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val tasks = scheduledTaskDao.getAllTasks()
                val tasksArray = com.google.gson.JsonArray()
                for (task in tasks) {
                    tasksArray.add(gson.toJsonTree(task))
                }
                val result = JsonObject().apply { addProperty("success", true); add("tasks", tasksArray) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("获取定时任务失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // POST /api/schedules
    fun createSchedule(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val task = ScheduledTask(
                    name = body.get("name")?.asString ?: "未命名任务",
                    enabled = body.get("enabled")?.asBoolean ?: true,
                    taskType = body.get("taskType")?.asString ?: "SCHEDULED_PLAYBACK",
                    scheduleMode = body.get("scheduleMode")?.asString ?: "ALWAYS",
                    startTime = body.get("startTime")?.asString ?: "",
                    endTime = body.get("endTime")?.asString ?: "",
                    daysOfWeek = body.get("daysOfWeek")?.asString ?: "",
                    orderMode = body.get("orderMode")?.asString ?: "SEQUENTIAL",
                    repeatMode = body.get("repeatMode")?.asString ?: "LOOP",
                    repeatCount = body.get("repeatCount")?.asInt ?: 0,
                    groupIds = body.get("groupIds")?.asString ?: "[]",
                    priority = body.get("priority")?.asInt ?: 0
                )
                val id = scheduledTaskDao.insert(task)
                task.id = id
                val result = JsonObject().apply { addProperty("success", true); add("task", gson.toJsonTree(task)) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("创建定时任务失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // PUT /api/schedules/:id
    fun updateSchedule(session: NanoHTTPD.IHTTPSession, id: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                val existing = scheduledTaskDao.getTaskById(id) ?: return@runBlocking jsonResponseError("任务未找到", NanoHTTPD.Response.Status.NOT_FOUND)
                val body = parseBody(session)
                val updated = existing.copy(
                    name = body.get("name")?.asString ?: existing.name,
                    enabled = body.get("enabled")?.asBoolean ?: existing.enabled,
                    taskType = body.get("taskType")?.asString ?: existing.taskType,
                    scheduleMode = body.get("scheduleMode")?.asString ?: existing.scheduleMode,
                    startTime = body.get("startTime")?.asString ?: existing.startTime,
                    endTime = body.get("endTime")?.asString ?: existing.endTime,
                    daysOfWeek = body.get("daysOfWeek")?.asString ?: existing.daysOfWeek,
                    orderMode = body.get("orderMode")?.asString ?: existing.orderMode,
                    repeatMode = body.get("repeatMode")?.asString ?: existing.repeatMode,
                    repeatCount = body.get("repeatCount")?.asInt ?: existing.repeatCount,
                    groupIds = body.get("groupIds")?.asString ?: existing.groupIds,
                    priority = body.get("priority")?.asInt ?: existing.priority,
                    updatedAt = System.currentTimeMillis()
                )
                scheduledTaskDao.update(updated)
                val result = JsonObject().apply { addProperty("success", true) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("更新定时任务失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // DELETE /api/schedules/:id
    fun deleteSchedule(session: NanoHTTPD.IHTTPSession, id: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                scheduledTaskDao.deleteById(id)
                val result = JsonObject().apply { addProperty("success", true) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("删除定时任务失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // PUT /api/schedules/:id/toggle
    fun toggleSchedule(session: NanoHTTPD.IHTTPSession, id: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                val task = scheduledTaskDao.getTaskById(id) ?: return@runBlocking jsonResponseError("任务未找到", NanoHTTPD.Response.Status.NOT_FOUND)
                scheduledTaskDao.setEnabled(id, !task.enabled)
                val result = JsonObject().apply { addProperty("success", true); addProperty("enabled", !task.enabled) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("切换任务状态失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  Playback Control APIs
    // =====================================================================

    // POST /api/playback/control
    fun playbackControl(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val action = body.get("action")?.asString ?: ""
                val pm = WebServer.activePlaylistManager
                val mc = WebServer.activeMediaController

                when (action) {
                    "play" -> { mc?.play(); Log.d(TAG, "Playback: play") }
                    "pause" -> { mc?.pause(); Log.d(TAG, "Playback: pause") }
                    "stop" -> { pm?.stop(); mc?.stop(); Log.d(TAG, "Playback: stop") }
                    else -> return@runBlocking jsonResponseError("未知操作: $action", NanoHTTPD.Response.Status.BAD_REQUEST)
                }

                val result = JsonObject().apply { addProperty("success", true); addProperty("action", action) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("播放控制失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // PUT /api/playback/volume
    fun setPlaybackVolume(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val volume = body.get("volume")?.asInt ?: return@runBlocking jsonResponseError("volume required", NanoHTTPD.Response.Status.BAD_REQUEST)
                val clamped = volume.coerceIn(0, 100)

                // Save to config
                val config = configDao.getConfigOnce() ?: PlaylistConfig()
                val updated = config.copy(volumeLevel = clamped, lastUpdated = System.currentTimeMillis())
                configDao.insert(updated)

                // Apply to media controller
                WebServer.activeMediaController?.setVolume(clamped)

                val result = JsonObject().apply { addProperty("success", true); addProperty("volume", clamped) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("设置音量失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // POST /api/playback/interject
    fun interjectPlayback(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val groupIdsStr = body.get("groupIds")?.asString ?: "[]"
                val groupIds = try {
                    com.google.gson.JsonParser.parseString(groupIdsStr).asJsonArray.map { it.asLong }
                } catch (_: Exception) { emptyList() }

                // Collect all media items from the specified groups
                val allMediaIds = mutableListOf<Long>()
                for (gid in groupIds) {
                    allMediaIds.addAll(mediaGroupDao.getGroupMediaIds(gid))
                }

                val items = allMediaIds.mapNotNull { mediaItemDao.getItemById(it) }
                val pm = WebServer.activePlaylistManager

                if (items.isNotEmpty() && pm != null) {
                    // Set interstitial mode with these items
                    pm.updateItems(items)
                    pm.setInterstitialConfig(true, 0, 24) // Force interstitial
                    Log.d(TAG, "Interjection: ${items.size} items from ${groupIds.size} groups")
                }

                val result = JsonObject().apply {
                    addProperty("success", true)
                    addProperty("itemCount", items.size)
                }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("插播失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  Background Music APIs
    // =====================================================================

    // GET /api/bgmusic
    fun getBackgroundMusic(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val musicList = bgMusicDao.getAllMusic()
                val array = com.google.gson.JsonArray()
                for (m in musicList) {
                    val file = File(m.filePath)
                    val obj = com.google.gson.JsonObject()
                    obj.addProperty("id", m.id)
                    obj.addProperty("title", m.title)
                    obj.addProperty("filePath", m.filePath)
                    obj.addProperty("fileSize", if (file.exists()) file.length() else m.fileSize)
                    obj.addProperty("durationSec", m.durationSec)
                    obj.addProperty("sortOrder", m.sortOrder)
                    obj.addProperty("createdAt", m.createdAt)
                    array.add(obj)
                }
                val result = JsonObject().apply { addProperty("success", true); add("music", array) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("获取背景音乐失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // POST /api/bgmusic/upload
    fun uploadBackgroundMusic(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val files = mutableMapOf<String, String>()
        try {
            System.setProperty("java.io.tmpdir", File(context.cacheDir, "nanohttpd_tmp").also { it.mkdirs() }.absolutePath)
            session.parseBody(files)
        } catch (e: Exception) {
            return jsonResponseError("文件上传解析失败: ${e.message}", NanoHTTPD.Response.Status.BAD_REQUEST)
        }
        val tempFilePath = files["file"] ?: return jsonResponseError("未找到上传文件", NanoHTTPD.Response.Status.BAD_REQUEST)
        val tempFile = File(tempFilePath)
        if (!tempFile.exists()) return jsonResponseError("临时文件不存在", NanoHTTPD.Response.Status.INTERNAL_ERROR)

        val originalName = session.parameters["file"]?.firstOrNull()?.let { it.substringAfterLast('/') } ?: tempFile.name

        return runBlocking {
            try {
                val safeName = originalName.replace("\\0", "").replace("..", "_")
                val destFile = File(bgMusicDir, safeName)
                var finalFile = destFile
                var counter = 1
                while (finalFile.exists() && finalFile != tempFile) {
                    val base = originalName.substringBeforeLast('.')
                    val ext = originalName.substringAfterLast('.')
                    finalFile = File(bgMusicDir, "${base}_${counter}.${ext}")
                    counter++
                }
                java.io.FileInputStream(tempFile).use { input ->
                    java.io.FileOutputStream(finalFile).use { output -> input.copyTo(output) }
                }
                val displayTitle = originalName.substringBeforeLast(".")
                val music = BackgroundMusic(
                    title = displayTitle,
                    filePath = finalFile.absolutePath,
                    fileSize = finalFile.length(),
                    sortOrder = bgMusicDao.getAllMusic().size
                )
                val id = bgMusicDao.insert(music)
                music.id = id
                try { tempFile.delete() } catch (_: Exception) {}
                val result = JsonObject().apply {
                    addProperty("success", true)
                    addProperty("filename", finalFile.name)
                    add("music", gson.toJsonTree(music))
                }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("上传失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // DELETE /api/bgmusic/:id
    fun deleteBackgroundMusic(session: NanoHTTPD.IHTTPSession, id: Long): NanoHTTPD.Response {
        return runBlocking {
            try {
                val music = bgMusicDao.getMusicById(id) ?: return@runBlocking jsonResponseError("音乐未找到", NanoHTTPD.Response.Status.NOT_FOUND)
                File(music.filePath).let { if (it.exists()) it.delete() }
                bgMusicDao.deleteById(id)
                val result = JsonObject().apply { addProperty("success", true) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("删除失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // PUT /api/bgmusic/reorder
    fun reorderBackgroundMusic(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val items = body.getAsJsonArray("items") ?: return@runBlocking jsonResponseError("items required", NanoHTTPD.Response.Status.BAD_REQUEST)
                for (element in items) {
                    val item = element.asJsonObject
                    val musicId = item.get("id")?.asLong ?: continue
                    val sortOrder = item.get("sortOrder")?.asInt ?: continue
                    val music = bgMusicDao.getMusicById(musicId) ?: continue
                    bgMusicDao.update(music.copy(sortOrder = sortOrder))
                }
                val result = JsonObject().apply { addProperty("success", true) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("排序失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // POST /api/bgmusic/control
    fun bgMusicControl(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return runBlocking {
            try {
                val body = parseBody(session)
                val action = body.get("action")?.asString ?: ""
                when (action) {
                    "enable" -> WebServer.activeBgMusicPlayer?.enable()
                    "disable" -> WebServer.activeBgMusicPlayer?.disable()
                    else -> return@runBlocking jsonResponseError("未知操作: $action", NanoHTTPD.Response.Status.BAD_REQUEST)
                }
                val result = JsonObject().apply { addProperty("success", true); addProperty("action", action) }
                jsonResponse(result)
            } catch (e: Exception) {
                jsonResponseError("背景音乐控制失败: ${e.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
            }
        }
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private fun parseBody(session: NanoHTTPD.IHTTPSession): JsonObject {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength <= 0) return JsonObject()

        val buf = ByteArray(contentLength)
        session.inputStream.read(buf, 0, contentLength)
        return JsonParser.parseString(String(buf)).asJsonObject
    }

    private fun detectMediaType(filename: String): MediaType {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when {
            ext in listOf("mp4", "avi", "mkv", "mov", "3gp", "webm", "flv") -> MediaType.VIDEO
            ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp") -> MediaType.IMAGE
            ext in listOf("ppt", "pptx", "pdf") -> MediaType.PPT
            ext in listOf("m3u", "m3u8") -> MediaType.IPTV
            ext in listOf("mpd", "mp4", "ts") -> MediaType.STREAM
            else -> MediaType.VIDEO
        }
    }

    private fun jsonResponse(json: JsonObject): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json",
            gson.toJson(json)
        )
    }

    private fun jsonResponse(json: com.google.gson.JsonElement): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json",
            gson.toJson(json)
        )
    }

    private fun jsonResponseError(message: String, status: NanoHTTPD.Response.Status): NanoHTTPD.Response {
        val error = JsonObject().apply {
            addProperty("error", message)
        }
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", gson.toJson(error))
    }
}
