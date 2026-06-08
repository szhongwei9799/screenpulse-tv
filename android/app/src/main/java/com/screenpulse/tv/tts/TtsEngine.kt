package com.screenpulse.tv.tts

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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Core Edge TTS engine.
 *
 * Connects to Microsoft Edge's TTS service via WebSocket using OkHttp,
 * sends SSML with voice configuration, and receives MP3 audio data.
 *
 * Usage:
 *   val engine = TtsEngine(context)
 *   val result = engine.generateSpeech("Hello world", "en-US-JennyNeural", "Greeting")
 *   if (result.success) {
 *       // result.filePath contains the path to the generated MP3
 *   }
 */
class TtsEngine(private val context: Context) {

    companion object {
        private const val TAG = "TtsEngine"
        private const val WS_URL =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4&ConnectionId="
        private const val TTS_DIR = "screenpulse_tts"

        /**
         * Represents an available Edge TTS voice.
         */
        data class EdgeVoice(
            val id: String,
            val name: String
        )

        /** Predefined list of available Edge TTS voices. */
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

        /**
         * Result of a TTS generation operation.
         */
        data class TtsResult(
            val success: Boolean,
            val filePath: String? = null,
            val fileSize: Long = 0,
            val error: String? = null
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Generate speech audio from text using Edge TTS.
     *
     * @param text The text to synthesize into speech.
     * @param voiceId The Edge TTS voice identifier (e.g. "zh-CN-XiaoxiaoNeural").
     * @param title A title for the generated audio file (defaults to first 50 chars of text).
     * @return [TtsResult] containing the file path on success, or an error message on failure.
     */
    suspend fun generateSpeech(
        text: String,
        voiceId: String,
        title: String = text.take(50)
    ): TtsResult = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val requestId = UUID.randomUUID().toString()
            val audioChunks = mutableListOf<ByteArray>()
            val latch = CountDownLatch(1)
            var errorMessage: String? = null
            var success = false

            val request = Request.Builder()
                .url(WS_URL + requestId)
                .header("Origin", "chrome-extension://jdiccldimpiaibgdkfjclkfpkccgoecg")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
                )
                .header("Pragma", "no-cache")
                .build()

            val ws = client.newWebSocket(request, object : WebSocketListener() {

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected for requestId=$requestId")

                    // 1. Send the speech config message
                    val configMsg = buildConfigMessage()
                    webSocket.send(configMsg)
                    Log.d(TAG, "Sent speech config message")

                    // 2. Send the SSML synthesis message
                    val ssmlMsg = buildSsmlMessage(text, voiceId, requestId)
                    webSocket.send(ssmlMsg)
                    Log.d(TAG, "Sent SSML message (voice=$voiceId, text length=${text.length})")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received text message: ${text.take(120)}")

                    when {
                        text.contains("Path:turn.start") -> {
                            Log.d(TAG, "TTS synthesis started (turn.start)")
                        }

                        text.contains("Path:turn.end") -> {
                            Log.d(TAG, "TTS synthesis completed (turn.end)")
                            success = true
                            webSocket.close(1000, "Synthesis complete")
                            latch.countDown()
                        }

                        text.contains("Path:response") -> {
                            // Check for error responses from the service
                            if (text.contains("error") || text.contains("Error")) {
                                Log.e(TAG, "TTS error response: $text")
                                errorMessage = extractErrorMessage(text) ?: "TTS synthesis error"
                                webSocket.close(1002, "Error")
                                latch.countDown()
                            }
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    // Binary messages contain audio data with a 2-byte length prefix header
                    if (bytes.size > 2) {
                        val audioBytes = bytes.substring(2).toByteArray()
                        audioChunks.add(audioBytes)
                        Log.d(TAG, "Received audio chunk: ${audioBytes.size} bytes")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure", t)
                    errorMessage = t.message ?: "WebSocket connection failed"
                    latch.countDown()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: code=$code reason=$reason")
                    latch.countDown()
                }
            })

            // Cancel the coroutine's continuation if it is cancelled externally
            continuation.invokeOnCancellation {
                ws.close(1001, "Cancelled")
                latch.countDown()
            }

            // Wait for synthesis to complete (max 60 seconds)
            latch.await(60, TimeUnit.SECONDS)

            if (success && audioChunks.isNotEmpty()) {
                // Combine all audio chunks into one file
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

                Log.d(TAG, "Saved TTS audio: ${file.absolutePath} ($totalBytes bytes)")
                continuation.resume(
                    TtsResult(
                        success = true,
                        filePath = file.absolutePath,
                        fileSize = totalBytes
                    )
                )
            } else {
                Log.e(TAG, "TTS generation failed: success=$success, chunks=${audioChunks.size}")
                continuation.resume(
                    TtsResult(
                        success = false,
                        error = errorMessage ?: "Unknown error - no audio data received"
                    )
                )
            }
        }
    }

    /**
     * Get the directory where TTS audio files are stored.
     * Creates the directory if it does not exist.
     */
    fun getTtsDir(): File {
        val dir = File(context.filesDir, TTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Delete a TTS audio file from storage.
     */
    fun deleteFile(filePath: String): Boolean {
        return File(filePath).delete()
    }

    /**
     * Estimate the duration of an MP3 file in seconds based on file size.
     * Uses a rough estimate: 24kHz, 48kbps mono MP3 ≈ 6KB per second.
     */
    fun estimateDuration(fileSize: Long): Long {
        // ~6,000 bytes per second for audio-24khz-48kbitrate-mono-mp3
        return fileSize / 6000
    }

    /**
     * Build the WebSocket speech configuration message.
     * This sets the audio output format to 24kHz 48kbps mono MP3.
     */
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

    /**
     * Build the SSML message for speech synthesis.
     *
     * @param text The text to synthesize (will be XML-escaped).
     * @param voiceId The voice identifier.
     * @param requestId A unique request ID for tracking.
     * @return The formatted SSML WebSocket message.
     */
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

    /**
     * Extract a human-readable error message from a TTS error response.
     */
    private fun extractErrorMessage(response: String): String? {
        // Try to find a useful error description in the response text
        val patterns = listOf(
            "\"message\"\\s*:\\s*\"([^\"]+)\"",
            "\"error\"\\s*:\\s*\"([^\"]+)\"",
            "Path:error\\s*\\n\\s*(.+)"
        )
        for (pattern in patterns) {
            val match = Regex(pattern).find(response)
            if (match != null) {
                return match.groupValues.getOrNull(1)?.trim()
            }
        }
        return null
    }
}
