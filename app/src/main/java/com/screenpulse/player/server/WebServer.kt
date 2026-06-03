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
 *   GET  /api/debug/assets    →  list all assets in web-admin for debugging
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

        Log.d(TAG, "Request: ${session.method} $uri  [thread=${Thread.currentThread().name}]")

        return try {
            val response = when {
                uri.startsWith("/api/debug") -> {
                    serveDebugApi(session)
                }
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
            Log.e(TAG, "FATAL error serving $uri", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message ?: "Internal server error"}"}"""
            )
        }
    }

    // =====================================================================
    //  Debug API (helps diagnose blank page issues)
    // =====================================================================

    private fun serveDebugApi(session: IHTTPSession): Response {
        return when {
            session.uri == "/api/debug/assets" -> {
                listAssets()
            }
            session.uri == "/api/debug/check" -> {
                checkCriticalAssets()
            }
            else -> {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json",
                    """{"error":"Debug endpoint not found: ${session.uri}"}"""
                )
            }
        }
    }

    /**
     * Lists all files in assets/web-admin/ with their sizes.
     * Useful for verifying files are present in the APK.
     */
    private fun listAssets(): Response {
        val sb = StringBuilder()
        sb.append("=== Web Admin Assets ===\n")
        sb.append("Listing: web-admin/\n\n")
        try {
            val files = context.assets.list("web-admin")
            if (files != null) {
                for (file in files.sorted()) {
                    sb.append("  $file\n")
                    listAssetsRecursive("web-admin/$file", "  ", sb)
                }
            } else {
                sb.append("  (empty or not found)\n")
            }
        } catch (e: Exception) {
            sb.append("  ERROR: ${e.message}\n")
        }
        sb.append("\n=== Done ===")
        Log.d(TAG, sb.toString())
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
        } catch (_: Exception) {
            // Not a directory, that's fine
        }
    }

    /**
     * Checks that critical JS/CSS files exist and returns their sizes.
     * This is the key diagnostic: if a file is missing or empty, it explains the blank page.
     */
    private fun checkCriticalAssets(): Response {
        val criticalFiles = listOf(
            "web-admin/index.html",
            "web-admin/css/index.css",
            "web-admin/css/dark.css",
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
                // Read first 100 bytes to verify file is valid
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

                Log.d(TAG, "Asset check OK: $assetPath (${size} bytes, starts with: ${headerStr.take(50)})")
            } catch (e: Exception) {
                sb.append("    {")
                sb.append("\"file\": \"$assetPath\", ")
                sb.append("\"size\": 0, ")
                sb.append("\"valid\": false, ")
                sb.append("\"error\": \"${e.javaClass.simpleName}: ${e.message}\"")
                sb.append("}")

                if (index < criticalFiles.lastIndex) sb.append(",")
                sb.append("\n")

                Log.e(TAG, "Asset check FAILED: $assetPath - ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        sb.append("  ],\n")
        sb.append("  \"note\": \"If 'valid' is false for any JS/CSS file, the admin page will be blank. ")
        sb.append("element-plus.js must be the IIFE browser build (index.full.min.js), NOT the CJS/UMD build. ")
        sb.append("Check that it starts with 'var ElementPlus' or contains 'global.ElementPlus'.\"\n")
        sb.append("}")

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            sb.toString()
        )
    }

    // =====================================================================
    //  API routing
    // =====================================================================

    /**
     * Routes API requests to [ApiRouter].
     */
    private fun serveApi(session: IHTTPSession): Response {
        // NOTE: Do NOT call session.parseBody() here!
        // Each handler (e.g. uploadFile) calls parseBody() itself.
        // Calling parseBody() twice on the same session causes the second
        // call to fail because the input stream is already consumed.

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
            session.method == Method.GET && session.uri == "/api/config" -> {
                apiRouter.getConfig(session)
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

    // =====================================================================
    //  Static file serving
    // =====================================================================

    /**
     * Serves static files from the bundled web-admin SPA (assets/web-admin).
     *
     * Key design decisions:
     * - Text files (HTML, CSS, JS) are read as Strings and served via
     *   `newFixedLengthResponse(status, mimeType, bodyString)`. This is the
     *   most battle-tested NanoHTTPD code path and avoids all issues with
     *   InputStream constructors, Content-Length mismatches, and duplicate
     *   Content-Type headers.
     * - Binary files (images, fonts) use the byte[] constructor.
     * - NO duplicate Content-Type header is added (NanoHTTPD sets it from the
     *   mimeType parameter automatically).
     * - Missing asset files return a proper 404 instead of silently falling
     *   back to index.html (prevents returning HTML with a JS MIME type).
     * - Cache-Control headers are set for static assets.
     */
    private fun serveStaticFile(uri: String): Response {
        // Strip query string if present (NanoHTTPD should do this, but be safe)
        val cleanUri = uri.split("?")[0].split("#")[0]

        val assetPath = if (cleanUri == "/" || cleanUri == "/index.html") {
            "web-admin/index.html"
        } else {
            "web-admin$cleanUri"
        }

        val mimeType = getMimeType(assetPath)

        return try {
            val inputStream = context.assets.open(assetPath)

            if (isTextType(mimeType)) {
                // ── Text files: String-based response (most reliable) ──
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val content = reader.readText()
                reader.close()

                Log.d(TAG, "SERVED TEXT $assetPath  mime=$mimeType  size=${content.length}B")

                val response = newFixedLengthResponse(Response.Status.OK, mimeType, content)
                addStaticFileHeaders(response, assetPath)
                response
            } else {
                // ── Binary files: InputStream response with explicit content-length ──
                val buffer = ByteArrayOutputStream()
                inputStream.copyTo(buffer)
                inputStream.close()
                val bytes = buffer.toByteArray()

                Log.d(TAG, "SERVED BINARY $assetPath  mime=$mimeType  size=${bytes.size}B")

                val response = newFixedLengthResponse(
                    Response.Status.OK,
                    mimeType,
                    java.io.ByteArrayInputStream(bytes),
                    bytes.size.toLong()
                )
                addStaticFileHeaders(response, assetPath)
                response
            }
        } catch (e: java.io.FileNotFoundException) {
            // ── File genuinely not found ──
            Log.w(TAG, "ASSET NOT FOUND: $assetPath (requested URI: $uri)")

            // For known static file extensions, return 404 (NOT index.html fallback)
            if (cleanUri.contains(".") && !cleanUri.endsWith(".html")) {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json",
                    """{"error":"File not found: $cleanUri"}"""
                )
            } else {
                // HTML files and unknown routes: SPA fallback
                serveIndexHtmlFallback()
            }
        } catch (e: Exception) {
            // ── Unexpected error reading asset ──
            Log.e(TAG, "ERROR serving $assetPath: ${e.javaClass.simpleName}: ${e.message}", e)

            if (cleanUri.contains(".") && !cleanUri.endsWith(".html")) {
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    """{"error":"Failed to serve $cleanUri: ${e.message}"}"""
                )
            } else {
                serveIndexHtmlFallback()
            }
        }
    }

    /**
     * SPA fallback: serves index.html for client-side routing.
     */
    private fun serveIndexHtmlFallback(): Response {
        return try {
            val inputStream = context.assets.open("web-admin/index.html")
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val content = reader.readText()
            reader.close()

            Log.d(TAG, "SPA FALLBACK → index.html  size=${content.length}B")

            newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", content)
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: index.html not found in assets!", e)
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/html; charset=utf-8",
                "<h1>ScreenPulse Admin UI not found</h1>" +
                "<p>Place the web-admin SPA in assets/web-admin/</p>" +
                "<p>Error: ${e.message ?: "unknown"}</p>"
            )
        }
    }

    /**
     * Adds standard static file headers (Cache-Control, etc.).
     * Does NOT add Content-Type (NanoHTTPD handles it from mimeType).
     */
    private fun addStaticFileHeaders(response: Response, assetPath: String) {
        // Cache immutable assets (JS, CSS with hash) aggressively
        // For non-hashed assets, use shorter cache
        val maxAge = when {
            assetPath.contains(".js") || assetPath.contains(".css") -> "3600" // 1 hour
            assetPath.contains(".png") || assetPath.contains(".ico") || assetPath.contains(".svg") -> "86400" // 1 day
            else -> "0" // no cache for HTML
        }
        response.addHeader("Cache-Control", "public, max-age=$maxAge")
    }

    /**
     * Determines if a MIME type represents text content.
     * Text types are served as Strings (most reliable NanoHTTPD path).
     * Binary types are served as byte[].
     */
    private fun isTextType(mimeType: String): Boolean {
        // Strip charset suffix (e.g., "application/javascript; charset=utf-8" → "application/javascript")
        val baseType = mimeType.split(";")[0].trim()
        return baseType.startsWith("text/") ||
               baseType == "application/javascript" ||
               baseType == "application/json" ||
               baseType == "application/xml"
    }

    // =====================================================================
    //  MIME type detection
    // =====================================================================

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html") || path.endsWith(".htm") -> "text/html; charset=utf-8"
            path.endsWith(".css") -> "text/css; charset=utf-8"
            path.endsWith(".js") || path.endsWith(".mjs") -> "application/javascript; charset=utf-8"
            path.endsWith(".json") -> "application/json; charset=utf-8"
            path.endsWith(".map") -> "application/json"
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
