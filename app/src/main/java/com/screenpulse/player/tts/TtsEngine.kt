package com.screenpulse.player.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Core Edge TTS engine using Microsoft's free TTS service via WebSocket.
 * Matches the rany2/edge-tts v7.2.8 Python library algorithm exactly.
 */
class TtsEngine(private val context: Context) {

    companion object {
        private const val TAG = "TtsEngine"

        // Constants from edge-tts constants.py
        private const val WSS_BASE =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val SEC_MS_GEC_VERSION = "1-131.0.2903.51"
        private const val CHROMIUM_VERSION = "131.0.2903.51"
        private const val TTS_DIR = "screenpulse_tts"
        private const val WIN_EPOCH = 11644473600L // seconds between 1970-01-01 and 1601-01-01

        data class EdgeVoice(val id: String, val name: String)

        val AVAILABLE_VOICES = listOf(
            EdgeVoice("zh-CN-XiaoxiaoNeural", "晓晓 (女声，中文)"),
            EdgeVoice("zh-CN-YunxiNeural", "云希 (男声，中文)"),
            EdgeVoice("zh-CN-YunjianNeural", "云健 (男声，中文，新闻)"),
            EdgeVoice("zh-CN-XiaoyiNeural", "晓艺 (女声，中文，客服)"),
            EdgeVoice("zh-CN-YunyangNeural", "云扬 (男声，中文，助手)"),
            EdgeVoice("zh-CN-XiaohanNeural", "晓涵 (女声，中文，儿童)"),
            EdgeVoice("zh-CN-XiaomoNeural", "晓墨 (女声，中文，成人)"),
            EdgeVoice("zh-CN-XiaoruiNeural", "晓睿 (女声，中文，新闻)"),
            EdgeVoice("zh-CN-XiaoshuangNeural", "晓双 (女声，中文，童声)"),
            EdgeVoice("zh-CN-XiaoxuanNeural", "晓萱 (女声，中文，方言)"),
            EdgeVoice("zh-TW-HsiaoChenNeural", "晓辰 (女声，台湾)"),
            EdgeVoice("zh-TW-YunJheNeural", "云哲 (男声，台湾)"),
            EdgeVoice("zh-HK-HiuGaaiNeural", "曉佳 (女声，粤语)"),
            EdgeVoice("zh-HK-WanLungNeural", "雲龍 (男声，粤语)"),
            EdgeVoice("en-US-JennyNeural", "Jenny (Female, English)"),
            EdgeVoice("en-US-GuyNeural", "Guy (Male, English)"),
            EdgeVoice("en-US-AriaNeural", "Aria (Female, English)"),
            EdgeVoice("en-US-DavisNeural", "Davis (Male, English)"),
            EdgeVoice("ja-JP-NanamiNeural", "七海 (女声，日语)"),
            EdgeVoice("ja-JP-KeitaNeural", "圭太 (男声，日语)"),
            EdgeVoice("ko-KR-SunHiNeural", "선히 (女声，韩语)"),
            EdgeVoice("ko-KR-InJoonNeural", "인준 (男声，韩语)")
        )

        data class TtsResult(
            val success: Boolean,
            val filePath: String? = null,
            val fileSize: Long = 0,
            val duration: Long = 0,
            val error: String? = null
        )

        /**
         * Exact replica of edge-tts DRM.generate_sec_ms_gec() from Python source.
         *
         * Python algorithm:
         *   ticks = get_unix_timestamp()
         *   ticks += WIN_EPOCH          # 11644473600
         *   ticks -= ticks % 300        # round down to nearest 5 minutes (in seconds)
         *   ticks *= S_TO_NS / 100      # 1e9 / 100 = 1e7, convert to 100-nanosecond intervals
         *   str_to_hash = f"{ticks:.0f}{TRUSTED_CLIENT_TOKEN}"
         *   return sha256(str_to_hash).hexdigest().upper()
         */
        fun generateSecMsGec(clockSkewSeconds: Double = 0.0): String {
            var ticks = (System.currentTimeMillis() / 1000.0) + clockSkewSeconds
            ticks += WIN_EPOCH.toDouble()
            ticks -= ticks % 300.0        // round down to 5-minute boundary in seconds
            ticks *= 1e7                  // convert to 100-nanosecond intervals (FILETIME)
            val strToHash = "${ticks.toLong()}$TRUSTED_CLIENT_TOKEN"
            Log.d(TAG, "Sec-MS-GEC input: $strToHash")
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(strToHash.toByteArray(Charsets.US_ASCII))
            val result = hash.joinToString("") { "%02X".format(it) }
            Log.d(TAG, "Sec-MS-GEC token: $result")
            return result
        }

        /** Generate random MUID (32 hex chars, uppercase). */
        fun generateMuid(): String {
            val random = SecureRandom()
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02X".format(it) }
        }

        /**
         * Parse RFC 2616 date header to Unix timestamp.
         * Format: "Thu, 04 Jun 2026 06:00:00 GMT"
         */
        fun parseServerDate(dateStr: String): Long? {
            return try {
                val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("GMT")
                sdf.parse(dateStr)?.time?.let { it / 1000 }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse server date: $dateStr", e)
                null
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Internal WebSocket connection attempt.
     * Returns the error message (null on success) and optionally the server Date header.
     */
    private data class WsAttemptResult(
        val success: Boolean,
        val filePath: String? = null,
        val fileSize: Long = 0,
        val duration: Long = 0,
        val error: String? = null,
        val serverDate: String? = null
    )

    private suspend fun attemptGenerateSpeech(
        text: String,
        voiceId: String,
        clockSkewSeconds: Double = 0.0
    ): WsAttemptResult = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val connectionId = UUID.randomUUID().toString().replace("-", "")
            val secMsGec = generateSecMsGec(clockSkewSeconds)
            val muid = generateMuid()
            val audioChunks = mutableListOf<ByteArray>()
            val latch = CountDownLatch(1)
            var errorMessage: String? = null
            var success = false
            var serverDateHeader: String? = null

            // Build URL exactly like edge-tts
            val wsUrl = "$WSS_BASE" +
                "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
                "&ConnectionId=$connectionId" +
                "&Sec-MS-GEC=$secMsGec" +
                "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

            Log.d(TAG, "TTS WebSocket URL: $wsUrl")

            val chromiumMajor = CHROMIUM_VERSION.split(".")[0]
            val request = Request.Builder()
                .url(wsUrl)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("Sec-WebSocket-Version", "13")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/$chromiumMajor.0.0.0 Safari/537.36 " +
                        "Edg/$chromiumMajor.0.0.0"
                )
                .header("Cookie", "muid=$muid;")
                .build()

            Log.d(TAG, "TTS Headers: Origin=chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold, UA=Chrome/$chromiumMajor Edg/$chromiumMajor, MUID=$muid")

            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket OPEN - connected successfully")
                    webSocket.send(buildConfigMessage())
                    webSocket.send(buildSsmlMessage(text, voiceId, connectionId))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    when {
                        text.contains("Path:turn.start") -> {
                            Log.d(TAG, "TTS synthesis started")
                        }
                        text.contains("Path:turn.end") -> {
                            Log.d(TAG, "TTS synthesis completed")
                            success = true
                            webSocket.close(1000, "Synthesis complete")
                            latch.countDown()
                        }
                        text.contains("error") || text.contains("Error") -> {
                            Log.e(TAG, "TTS error response: $text")
                            errorMessage = extractErrorMessage(text) ?: "TTS synthesis error"
                            webSocket.close(1002, "Error")
                            latch.countDown()
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (bytes.size > 2) {
                        val audioBytes = bytes.substring(2).toByteArray()
                        audioChunks.add(audioBytes)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val code = response?.code ?: -1
                    Log.e(TAG, "WebSocket FAILURE: ${t.message}, HTTP code=$code")
                    if (response != null) {
                        serverDateHeader = response.header("Date")
                        Log.e(TAG, "Server Date header: $serverDateHeader")
                        Log.e(TAG, "All response headers: ${response.headers}")
                    }
                    errorMessage = t.message ?: "WebSocket connection failed"
                    if (code == 403) {
                        errorMessage += " (HTTP 403 Forbidden - Sec-MS-GEC token rejected)"
                    } else if (code > 0) {
                        errorMessage += " (HTTP $code)"
                    }
                    latch.countDown()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    latch.countDown()
                }
            })

            continuation.invokeOnCancellation {
                ws.close(1001, "Cancelled")
                latch.countDown()
            }

            latch.await(60, TimeUnit.SECONDS)

            if (success && audioChunks.isNotEmpty()) {
                val ttsDir = getTtsDir()
                val textHash = MessageDigest.getInstance("MD5")
                    .digest(text.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                    .take(8)
                val safeVoiceId = voiceId.replace("-", "_")
                val fileName = "${System.currentTimeMillis()}_${safeVoiceId}_${textHash}.mp3"
                val file = File(ttsDir, fileName)

                var totalBytes = 0L
                FileOutputStream(file).use { fos ->
                    for (chunk in audioChunks) {
                        fos.write(chunk)
                        totalBytes += chunk.size
                    }
                }

                val estimatedDuration = totalBytes / 6000
                Log.d(TAG, "Saved TTS audio: ${file.absolutePath} ($totalBytes bytes, ~${estimatedDuration}s)")
                continuation.resume(
                    WsAttemptResult(
                        success = true,
                        filePath = file.absolutePath,
                        fileSize = totalBytes,
                        duration = estimatedDuration,
                        serverDate = serverDateHeader
                    )
                )
            } else {
                continuation.resume(
                    WsAttemptResult(
                        success = false,
                        error = errorMessage ?: "No audio data received",
                        serverDate = serverDateHeader
                    )
                )
            }
        }
    }

    /**
     * Generate speech with automatic 403 retry and clock skew adjustment.
     * Matches edge-tts communicate.py retry logic exactly.
     */
    suspend fun generateSpeech(
        text: String,
        voiceId: String,
        title: String = text.take(50)
    ): TtsResult {
        // First attempt
        var result = attemptGenerateSpeech(text, voiceId)

        if (result.success) {
            return TtsResult(true, result.filePath, result.fileSize, result.duration)
        }

        // If 403 and we got a server Date header, adjust clock skew and retry once
        if (result.error?.contains("403") == true && result.serverDate != null) {
            Log.w(TAG, "Got 403, adjusting clock skew using server Date: ${result.serverDate}")

            val serverTimestamp = parseServerDate(result.serverDate!!)
            if (serverTimestamp != null) {
                val clientTimestamp = System.currentTimeMillis() / 1000
                val clockSkew = (serverTimestamp - clientTimestamp).toDouble()
                Log.w(TAG, "Clock skew: server=$serverTimestamp, client=$clientTimestamp, skew=${clockSkew}s")

                // Retry with corrected clock skew
                Log.i(TAG, "Retrying TTS with clock skew adjustment...")
                result = attemptGenerateSpeech(text, voiceId, clockSkew)

                if (result.success) {
                    return TtsResult(true, result.filePath, result.fileSize, result.duration)
                }
            }
        }

        return TtsResult(false, error = result.error)
    }

    fun getTtsDir(): File {
        val dir = File(context.filesDir, TTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun deleteFile(filePath: String): Boolean {
        return File(filePath).delete()
    }

    private fun buildConfigMessage(): String {
        val date = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US).format(Date())
        return "X-Timestamp:$date\r\n" +
                "Content-Type:application/json; charset=utf-8\r\n" +
                "Path:speech.config\r\n" +
                "\r\n" +
                "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
                "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"}," +
                "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
    }

    private fun buildSsmlMessage(text: String, voiceId: String, requestId: String): String {
        val date = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US).format(Date())
        val escapedText = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        return "X-Timestamp:$date\r\n" +
                "Content-Type:application/ssml+xml\r\n" +
                "X-RequestId:$requestId\r\n" +
                "Path:ssml\r\n" +
                "\r\n" +
                "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                "<voice name='$voiceId'>" +
                "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>" +
                "$escapedText" +
                "</prosody>" +
                "</voice>" +
                "</speak>\r\n"
    }

    private fun extractErrorMessage(response: String): String? {
        val patterns = listOf(
            "\"message\"\\s*:\\s*\"([^\"]+)\"",
            "\"error\"\\s*:\\s*\"([^\"]+)\"",
            "Path:error\\s*\\n\\s*(.+)"
        )
        for (pattern in patterns) {
            val match = Regex(pattern).find(response)
            if (match != null) return match.groupValues.getOrNull(1)?.trim()
        }
        return null
    }
}
