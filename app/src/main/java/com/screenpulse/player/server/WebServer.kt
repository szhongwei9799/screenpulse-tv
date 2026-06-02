package com.screenpulse.player.server

import android.content.Context
import android.util.Log
import com.screenpulse.player.data.AppDatabase
import com.screenpulse.player.data.entity.MediaType
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Embedded NanoHTTPD web server that serves a Vue3 SPA for management
 * and exposes REST API endpoints for controlling the player remotely.
 *
 * Default port: 8080
 *
 * Routes:
 *   Static files  →  / (serves from assets/web-admin)
 *   GET  /api/status         →  device status, IP, playlist info
 *   GET  /api/playlist       →  list all media items
 *   POST /api/playlist       →  add a media item
 *   PUT  /api/playlist/<id>   →  update a media item
 *   DELETE /api/playlist/<id>  →  delete a media item
 *   POST /api/playlist/reorder → reorder playlist items
 *   PUT  /api/config          →  update playback config
 *   POST /api/upload          →  upload a media file (multipart)
 *   GET  /api/scan            →  trigger a local media scan
 */
class WebServer(
    private val port: Int,
    private val context: Context
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "WebServer"
        private const val UPLOAD_DIR = "screenpulse_uploads"
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

        // Handle CORS preflight
        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "").apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
                addHeader("Access-Control-Max-Age", "86400")
            }
        }

        Log.d(TAG, "Request: ${session.method} $uri")

        return try {
            val response = when {
                uri.startsWith("/api/") -> {
                    serveApi(session)
                }
                else -> {
                    serveStaticFile(uri)
                }
            }
            // Add CORS headers to all responses
            response.addHeader("Access-Control-Allow-Origin", "*")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error serving request: $uri", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message ?: "Internal server error"}"}"""
            )
        }
    }

    /**
     * Routes API requests to [ApiRouter].
     */
    private fun serveApi(session: IHTTPSession): Response {
        // Parse multipart form data if needed
        if (session.method == Method.POST && session.headers.containsKey("content-type") &&
            session.headers["content-type"]?.contains("multipart") == true
        ) {
            session.parseBody(mapOf())
        }

        return when {
            session.method == Method.GET && session.uri == "/api/status" -> {
                apiRouter.getStatus(session)
            }
            session.method == Method.GET && session.uri == "/api/playlist" -> {
                apiRouter.getPlaylist(session)
            }
            session.method == Method.POST && session.uri == "/api/playlist" -> {
                apiRouter.addPlaylistItem(session)
            }
            session.method == Method.PUT && session.uri.matches(Regex("^/api/playlist/\\d+$")) -> {
                val id = session.uri.substringAfterLast("/").toLongOrNull() ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}"""
                )
                apiRouter.updatePlaylistItem(session, id)
            }
            session.method == Method.DELETE && session.uri.matches(Regex("^/api/playlist/\\d+$")) -> {
                val id = session.uri.substringAfterLast("/").toLongOrNull() ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid ID"}"""
                )
                apiRouter.deletePlaylistItem(session, id)
            }
            session.method == Method.POST && session.uri == "/api/playlist/reorder" -> {
                apiRouter.reorderPlaylist(session)
            }
            session.method == Method.PUT && session.uri == "/api/config" -> {
                apiRouter.updateConfig(session)
            }
            session.method == Method.POST && session.uri == "/api/upload" -> {
                apiRouter.uploadFile(session)
            }
            session.method == Method.GET && session.uri == "/api/scan" -> {
                apiRouter.triggerScan(session)
            }
            else -> {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json",
                    """{"error":"Endpoint not found: ${session.uri}"}"""
                )
            }
        }
    }

    /**
     * Serves static files from the bundled web-admin SPA (assets/web-admin).
     * Falls back to index.html for SPA routing.
     * Uses binary-safe serving (byte[]) for all file types including JS/CSS.
     */
    private fun serveStaticFile(uri: String): Response {
        return try {
            val path = if (uri == "/" || uri == "/index.html") {
                "web-admin/index.html"
            } else {
                "web-admin$uri"
            }

            val inputStream = context.assets.open(path)
            val mimeType = getMimeType(path)

            // Read full content as bytes for binary-safe serving
            val buffer = ByteArrayOutputStream()
            inputStream.copyTo(buffer)
            inputStream.close()
            val bytes = buffer.toByteArray()

            // Use byte[] response for correct Content-Length with all file types
            val response = newFixedLengthResponse(
                Response.Status.OK,
                mimeType,
                java.io.ByteArrayInputStream(bytes),
                bytes.size.toLong()
            )
            response.addHeader("Content-Type", "$mimeType; charset=utf-8")
            response
        } catch (e: Exception) {
            // SPA fallback: serve index.html for unknown routes
            try {
                val inputStream = context.assets.open("web-admin/index.html")
                val buffer = ByteArrayOutputStream()
                inputStream.copyTo(buffer)
                inputStream.close()
                val bytes = buffer.toByteArray()
                newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html",
                    java.io.ByteArrayInputStream(bytes),
                    bytes.size.toLong()
                )
            } catch (fallbackE: Exception) {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/html",
                    "<h1>ScreenPulse Admin UI not found</h1><p>Place the web-admin SPA in assets/web-admin/</p>"
                )
            }
        }
    }

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html") -> "text/html"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".ico") -> "image/x-icon"
            path.endsWith(".woff") -> "font/woff"
            path.endsWith(".woff2") -> "font/woff2"
            path.endsWith(".ttf") -> "font/ttf"
            else -> "application/octet-stream"
        }
    }
}
