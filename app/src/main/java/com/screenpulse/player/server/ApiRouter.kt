package com.screenpulse.player.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.screenpulse.player.data.AppDatabase
import com.screenpulse.player.data.entity.MediaItem
import com.screenpulse.player.data.entity.MediaType
import com.screenpulse.player.data.entity.PlaylistConfig
import com.screenpulse.player.data.entity.PlaybackMode
import com.screenpulse.player.util.NetworkUtil
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

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
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val mediaItemDao = database.mediaItemDao()
    private val configDao = database.playlistConfigDao()

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
                addProperty("playbackMode", config.playbackMode.name)
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
                    val id = item.get("id")?.asLong ?: continue
                    val sortOrder = item.get("sortOrder")?.asInt ?: continue
                    updates[id] = sortOrder
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
                    playbackMode = body.get("playbackMode")?.asString?.let {
                        try { PlaybackMode.valueOf(it) } catch (_: Exception) { existingConfig.playbackMode }
                    } ?: existingConfig.playbackMode,
                    interstitialEnabled = body.get("interstitialEnabled")?.asBoolean ?: existingConfig.interstitialEnabled,
                    interstitialStartHour = body.get("interstitialStartHour")?.asInt ?: existingConfig.interstitialStartHour,
                    interstitialEndHour = body.get("interstitialEndHour")?.asInt ?: existingConfig.interstitialEndHour,
                    interstitialPlaylistName = body.get("interstitialPlaylistName")?.asString ?: existingConfig.interstitialPlaylistName,
                    volumeLevel = body.get("volumeLevel")?.asInt ?: existingConfig.volumeLevel,
                    lastUpdated = System.currentTimeMillis()
                )

                configDao.insert(updatedConfig)
                Log.d(TAG, "Updated config: ${updatedConfig.playbackMode}")
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

        // NanoHTTPD stores filenames in session.parameters when using multipart
        // Try multiple possible parameter names
        val originalName = session.parameters["file"]?.firstOrNull()
            ?: session.parameters["filename"]?.firstOrNull()
            ?: tempFile.name

        return runBlocking {
            try {
                // Sanitize filename
                val safeName = originalName.replace(Regex("[^a-zA-Z0-9._\\u4e00-\\u9fff\\u3040-\\u309f\\u30a0-\\u30ff]"), "_")
                val destFile = File(uploadDir, safeName)

                // Copy uploaded file to persistent storage
                java.io.FileInputStream(tempFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Always auto-add to playlist database
                val type = detectMediaType(safeName)
                val mediaItem = MediaItem(
                    title = safeName.substringBeforeLast("."),
                    url = destFile.absolutePath,
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
                    addProperty("filename", safeName)
                    addProperty("url", destFile.absolutePath)
                    addProperty("size", destFile.length())
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

                // Fetch all media items from database to return as files array
                val allItems = mediaItemDao.getAllItemsOnce()
                val filesArray = gson.toJsonTree(allItems).asJsonArray

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
