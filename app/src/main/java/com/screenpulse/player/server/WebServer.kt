package com.screenpulse.player.server

import android.content.Context
import android.util.Log
import com.screenpulse.player.data.AppDatabase
import com.screenpulse.player.data.entity.MediaType
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

/**
 * Embedded NanoHTTPD web server that serves a Vue3 SPA for management
 * and exposes REST API endpoints for controlling the player remotely.
 *
 * Default port: 8080
 */
class WebServer(
    private val port: Int,
    private val context: Context
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "WebServer"
        private const val UPLOAD_DIR = "screenpulse_uploads"

        @Volatile
        var activePlaylistManager: com.screenpulse.player.player.PlaylistManager? = null
            private set

        fun setPlaylistManager(manager: com.screenpulse.player.player.PlaylistManager?) {
            activePlaylistManager = manager
        }

        @Volatile
        var activeMediaController: com.screenpulse.player.player.MediaController? = null
            private set

        fun setMediaController(controller: com.screenpulse.player.player.MediaController?) {
            activeMediaController = controller
        }

        @Volatile
        var activeBgMusicPlayer: com.screenpulse.player.player.BackgroundMusicPlayer? = null
            private set

        fun setBgMusicPlayer(player: com.screenpulse.player.player.BackgroundMusicPlayer?) {
            activeBgMusicPlayer = player
        }
    }

    private val apiRouter: ApiRouter
    private val database: AppDatabase = AppDatabase.getInstance(context)
    private val uploadDir: File = File(context.filesDir, UPLOAD_DIR)

    init {
        uploadDir.mkdirs()
        apiRouter = ApiRouter(database, uploadDir, context)
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "").apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
                addHeader("Access-Control-Max-Age", "86400")
            }
        }

        Log.d(TAG, "Request: ${session.method} $uri  [thread=${Thread.currentThread().name}]")

        return try {
            val response = when {
                uri.startsWith("/api/debug") -> serveDebugApi(session)
                uri.startsWith("/api/") -> serveApi(session)
                else -> serveStaticFile(uri)
            }
            response.addHeader("Access-Control-Allow-Origin", "*")
            response
        } catch (e: Exception) {
            Log.e(TAG, "FATAL error serving $uri", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message ?: "Internal server error}"}"""
            )
        }
    }

    // =====================================================================
    //  Debug API
    // =====================================================================

    private fun serveDebugApi(session: IHTTPSession): Response {
        return when {
            session.uri == "/api/debug/assets" -> listAssets()
            session.uri == "/api/debug/check" -> checkCriticalAssets()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"error":"Debug endpoint not found: ${session.uri}"}""")
        }
    }

    private fun listAssets(): Response {
        val sb = StringBuilder()
        sb.append("=== Web Admin Assets ===\n")
        try {
            val files = context.assets.list("web-admin")
            if (files != null) {
                for (file in files.sorted()) {
                    sb.append("  $file\n")
                    listAssetsRecursive("web-admin/$file", "  ", sb)
                }
            }
        } catch (e: Exception) {
            sb.append("  ERROR: ${e.message}\n")
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", sb.toString())
    }

    private fun listAssetsRecursive(path: String, indent: String, sb: StringBuilder) {
        try {
            val files = context.assets.list(path)
            if (files != null && files.isNotEmpty()) {
                for (file in files.sorted()) {
                    sb.append("$indent$path/$file\n")
                    listAssetsRecursive("$path/$file", "$indent  ", sb)
                }
            }
        } catch (_: Exception) {}
    }

    private fun checkCriticalAssets(): Response {
        val criticalFiles = listOf(
            "web-admin/index.html",
            "web-admin/css/index.css",
            "web-admin/js/vue.global.prod.js",
            "web-admin/js/element-plus.js",
            "web-admin/js/element-plus-icons.js"
        )

        val sb = StringBuilder()
        sb.append("{\n  \"checks\": [\n")

        criticalFiles.forEachIndexed { index, assetPath ->
            try {
                val inputStream = context.assets.open(assetPath)
                val size = inputStream.available().toLong()
                val header = ByteArray(minOf(100, size.toInt()))
                inputStream.read(header)
                inputStream.close()

                val headerStr = String(header, Charsets.US_ASCII).trim()
                val isValid = headerStr.isNotEmpty() && size > 0

                sb.append("    {")
                sb.append("\"file\": \"$assetPath\", ")
                sb.append("\"size\": $size, ")
                sb.append("\"valid\": $isValid, ")
                sb.append("\"header\": ${headerStr.take(80).replace("\"", "'").let { "\"$it\"" }}")
                sb.append("}")

                if (index < criticalFiles.lastIndex) sb.append(",")
                sb.append("\n")
            } catch (e: Exception) {
                sb.append("    {")
                sb.append("\"file\": \"$assetPath\", ")
                sb.append("\"size\": 0, ")
                sb.append("\"valid\": false, ")
                sb.append("\"error\": \"${e.javaClass.simpleName}: ${e.message}\"")
                sb.append("}")

                if (index < criticalFiles.lastIndex) sb.append(",")
                sb.append("\n")
            }
        }

        sb.append("  ],\n  \"note\": \"If 'valid' is false for any JS/CSS file, the admin page will be blank.\"\n}")
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", sb.toString())
    }

    // =====================================================================
    //  API routing
    // =====================================================================

    private fun serveApi(session: IHTTPSession): Response {
        return when {
            session.method == Method.POST && session.uri == "/api/auth/login" -> apiRouter.login(session)
            session.method == Method.POST && session.uri == "/api/auth/password" -> apiRouter.changePassword(session)
            session.method == Method.GET && session.uri == "/api/status" -> apiRouter.getStatus(session)
            session.method == Method.GET && session.uri == "/api/config" -> apiRouter.getConfig(session)
            session.method == Method.PUT && session.uri == "/api/config" -> apiRouter.updateConfig(session)
            session.method == Method.GET && session.uri == "/api/playlist" -> apiRouter.getPlaylist(session)
            session.method == Method.POST && session.uri == "/api/playlist" -> apiRouter.addPlaylistItem(session)
            session.method == Method.PUT && session.uri.matches(Regex("^/api/playlist/\\d+$")) -> {
                val id = session.uri.substringAfterLast("/").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.updatePlaylistItem(session, id)
            }
            session.method == Method.DELETE && session.uri.matches(Regex("^/api/playlist/\\d+$")) -> {
                val id = session.uri.substringAfterLast("/").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.deletePlaylistItem(session, id)
            }
            session.method == Method.POST && session.uri == "/api/playlist/reorder" -> apiRouter.reorderPlaylist(session)
            session.method == Method.GET && session.uri == "/api/media" -> apiRouter.getMediaList(session)
            session.method == Method.DELETE && session.uri.matches(Regex("^/api/media/\\d+$")) -> {
                val id = session.uri.substringAfterLast("/").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.deleteMediaItem(session, id)
            }
            session.method == Method.PUT && session.uri.matches(Regex("^/api/media/\\d+$")) -> {
                val id = session.uri.substringAfterLast("/").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.renameMediaItem(session, id)
            }
            session.method == Method.POST && session.uri == "/api/upload" -> apiRouter.uploadFile(session)
            session.method == Method.GET && session.uri == "/api/scan" -> apiRouter.triggerScan(session)
            session.method == Method.GET && session.uri == "/api/playback-stats" -> apiRouter.getPlaybackStats(session)

            // ── Groups APIs ─────────────────────────────────────────────
            session.method == Method.GET && session.uri == "/api/groups" -> apiRouter.getGroups(session)
            session.method == Method.POST && session.uri == "/api/groups" -> apiRouter.createGroup(session)
            session.method == Method.GET && session.uri.matches(Regex("^/api/groups/\\d+/items$")) -> {
                val id = session.uri.removePrefix("/api/groups/").substringBefore("/items").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.getGroupItems(session, id)
            }
            session.method == Method.POST && session.uri.matches(Regex("^/api/groups/\\d+/items$")) -> {
                val id = session.uri.removePrefix("/api/groups/").substringBefore("/items").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.addItemsToGroup(session, id)
            }
            session.method == Method.DELETE && session.uri.matches(Regex("^/api/groups/\\d+/items/\\d+$")) -> {
                val parts = session.uri.removePrefix("/api/groups/").split("/")
                val groupId = parts[0].toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                val mediaItemId = parts[2].toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.removeItemFromGroup(session, groupId, mediaItemId)
            }
            session.method == Method.PUT && session.uri.matches(Regex("^/api/groups/\\d+$")) -> {
                val id = session.uri.substringAfterLast("/").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.updateGroup(session, id)
            }
            session.method == Method.DELETE && session.uri.matches(Regex("^/api/groups/\\d+$")) -> {
                val id = session.uri.substringAfterLast("/").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.deleteGroup(session, id)
            }

            // ── Scheduled Tasks APIs ────────────────────────────────────
            session.method == Method.GET && session.uri == "/api/schedules" -> apiRouter.getSchedules(session)
            session.method == Method.POST && session.uri == "/api/schedules" -> apiRouter.createSchedule(session)
            session.method == Method.PUT && session.uri.matches(Regex("^/api/schedules/\\d+/toggle$")) -> {
                val id = session.uri.removePrefix("/api/schedules/").substringBefore("/toggle").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.toggleSchedule(session, id)
            }
            session.method == Method.PUT && session.uri.matches(Regex("^/api/schedules/\\d+$")) -> {
                val id = session.uri.substringAfterLast("/").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.updateSchedule(session, id)
            }
            session.method == Method.DELETE && session.uri.matches(Regex("^/api/schedules/\\d+$")) -> {
                val id = session.uri.substringAfterLast("/").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.deleteSchedule(session, id)
            }

            // ── Playback Control APIs ────────────────────────────────────
            session.method == Method.POST && session.uri == "/api/playback/control" -> apiRouter.playbackControl(session)
            session.method == Method.PUT && session.uri == "/api/playback/volume" -> apiRouter.setPlaybackVolume(session)
            session.method == Method.POST && session.uri == "/api/playback/interject" -> apiRouter.interjectPlayback(session)

            // ── Background Music APIs ──────────────────────────────────────
            session.method == Method.GET && session.uri == "/api/bgmusic" -> apiRouter.getBackgroundMusic(session)
            session.method == Method.POST && session.uri == "/api/bgmusic/upload" -> apiRouter.uploadBackgroundMusic(session)
            session.method == Method.PUT && session.uri == "/api/bgmusic/reorder" -> apiRouter.reorderBackgroundMusic(session)
            session.method == Method.POST && session.uri == "/api/bgmusic/control" -> apiRouter.bgMusicControl(session)
            session.method == Method.DELETE && session.uri.matches(Regex("^/api/bgmusic/\\d+$")) -> {
                val id = session.uri.substringAfterLast("/").toLongOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}""")
                apiRouter.deleteBackgroundMusic(session, id)
            }

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"error":"Endpoint not found: ${session.uri}"}""")
        }
    }

    // =====================================================================
    //  Static file serving — ALL files served as byte arrays
    // =====================================================================

    /**
     * Serves static files from the bundled web-admin SPA (assets/web-admin).
     *
     * ALL files are served as byte[] to avoid any string encoding / asset
     * decompression issues that NanoHTTPD's String-based constructor can hit
     * with large or compressed Android assets.
     */
    private fun serveStaticFile(uri: String): Response {
        val cleanUri = uri.split("?")[0].split("#")[0]

        val assetPath = if (cleanUri == "/" || cleanUri == "/index.html") {
            "web-admin/index.html"
        } else {
            "web-admin$cleanUri"
        }

        val mimeType = getMimeType(assetPath)

        return try {
            val inputStream = context.assets.open(assetPath)
            val buffer = ByteArrayOutputStream()
            inputStream.copyTo(buffer)
            inputStream.close()
            val bytes = buffer.toByteArray()

            Log.d(TAG, "SERVED $assetPath  mime=$mimeType  size=${bytes.size}B")

            val response = newFixedLengthResponse(
                Response.Status.OK,
                mimeType,
                java.io.ByteArrayInputStream(bytes),
                bytes.size.toLong()
            )
            addStaticFileHeaders(response, assetPath)
            response
        } catch (e: java.io.FileNotFoundException) {
            Log.w(TAG, "ASSET NOT FOUND: $assetPath (requested URI: $uri)")
            if (cleanUri.contains(".") && !cleanUri.endsWith(".html")) {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"error":"File not found: $cleanUri"}""")
            } else {
                serveIndexHtmlFallback()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ERROR serving $assetPath: ${e.javaClass.simpleName}: ${e.message}", e)
            if (cleanUri.contains(".") && !cleanUri.endsWith(".html")) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", """{"error":"Failed to serve $cleanUri: ${e.message}"}""")
            } else {
                serveIndexHtmlFallback()
            }
        }
    }

    private fun serveIndexHtmlFallback(): Response {
        return try {
            val inputStream = context.assets.open("web-admin/index.html")
            val buffer = ByteArrayOutputStream()
            inputStream.copyTo(buffer)
            inputStream.close()
            val bytes = buffer.toByteArray()

            newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                java.io.ByteArrayInputStream(bytes),
                bytes.size.toLong()
            )
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: index.html not found in assets!", e)
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/html; charset=utf-8",
                "<h1>ScreenPulse Admin UI not found</h1><p>Error: ${e.message ?: "unknown"}</p>"
            )
        }
    }

    private fun addStaticFileHeaders(response: Response, assetPath: String) {
        val maxAge = when {
            assetPath.contains(".js") || assetPath.contains(".css") -> "0"
            assetPath.contains(".png") || assetPath.contains(".ico") || assetPath.contains(".svg") -> "86400"
            else -> "0"
        }
        response.addHeader("Cache-Control", "public, max-age=$maxAge")
    }

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html") || path.endsWith(".htm") -> "text/html; charset=utf-8"
            path.endsWith(".css") -> "text/css; charset=utf-8"
            path.endsWith(".js") || path.endsWith(".mjs") -> "application/javascript; charset=utf-8"
            path.endsWith(".json") || path.endsWith(".map") -> "application/json; charset=utf-8"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".ico") -> "image/x-icon"
            path.endsWith(".woff") -> "font/woff"
            path.endsWith(".woff2") -> "font/woff2"
            path.endsWith(".ttf") -> "font/ttf"
            path.endsWith(".eot") -> "application/vnd.ms-fontobject"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".pdf") -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
