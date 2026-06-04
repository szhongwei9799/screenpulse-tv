package com.screenpulse.player.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Edge TTS engine using raw Java Socket for WebSocket connection.
 * Bypasses OkHttp WebSocket to have complete control over HTTP upgrade headers.
 * Matches the rany2/edge-tts v7.2.8 Python library algorithm exactly.
 */
class TtsEngine(private val context: Context) {

    companion object {
        private const val TAG = "TtsEngine"

        private const val WSS_HOST = "speech.platform.bing.com"
        private const val WSS_PATH = "/consumer/speech/synthesize/readaloud/edge/v1"
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D4"
        private const val SEC_MS_GEC_VERSION = "1-131.0.2903.51"
        private const val TTS_DIR = "screenpulse_tts"
        private const val WIN_EPOCH = 11644473600L

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
         * Exact replica of edge-tts DRM.generate_sec_ms_gec().
         * Python: ticks = unix_ts + WIN_EPOCH; ticks -= ticks % 300; ticks *= 1e7;
         *         SHA256(f"{ticks:.0f}{TOKEN}").upper()
         */
        fun generateSecMsGec(clockSkewSeconds: Double = 0.0): String {
            var ticks = (System.currentTimeMillis() / 1000.0) + clockSkewSeconds
            ticks += WIN_EPOCH.toDouble()
            ticks -= ticks % 300.0
            ticks *= 1e7
            val strToHash = "${Math.floor(ticks).toLong()}$TRUSTED_CLIENT_TOKEN"
            Log.d(TAG, "GEC input: $strToHash")
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(strToHash.toByteArray(Charsets.US_ASCII))
            return hash.joinToString("") { "%02X".format(it) }
        }

        fun generateMuid(): String {
            val random = SecureRandom()
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02X".format(it) }
        }

        fun generateWebSocketKey(): String {
            val random = SecureRandom()
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return Base64.getEncoder().encodeToString(bytes)
        }
    }

    /**
     * Attempt TTS generation with a given clock skew.
     */
    private suspend fun attemptGenerate(
        text: String, voiceId: String, clockSkewSeconds: Double = 0.0
    ): TtsResult = withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            try {
                val connectionId = UUID.randomUUID().toString().replace("-", "")
                val secMsGec = generateSecMsGec(clockSkewSeconds)
                val muid = generateMuid()
                val wsKey = generateWebSocketKey()

                val chromiumMajor = SEC_MS_GEC_VERSION.substringAfter("1-").split(".")[0]

                // Build exact URL path with all query params (in the same order as edge-tts)
                val fullPath = "$WSS_PATH" +
                    "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
                    "&ConnectionId=$connectionId" +
                    "&Sec-MS-GEC=$secMsGec" +
                    "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

                Log.d(TAG, "=== Edge TTS Attempt ===")
                Log.d(TAG, "Host: $WSS_HOST")
                Log.d(TAG, "Path: $fullPath")
                Log.d(TAG, "WS-Key: $wsKey")
                Log.d(TAG, "MUID: $muid")

                // Build the HTTP upgrade request with EXACT headers
                val httpRequest = "GET $fullPath HTTP/1.1\r\n" +
                    "Host: $WSS_HOST\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromiumMajor.0.0.0 Safari/537.36 Edg/$chromiumMajor.0.0.0\r\n" +
                    "Origin: chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold\r\n" +
                    "Sec-WebSocket-Key: $wsKey\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" +
                    "Accept-Encoding: gzip, deflate, br\r\n" +
                    "Accept-Language: en-US,en;q=0.9\r\n" +
                    "Cookie: muid=$muid;\r\n" +
                    "\r\n"

                Log.d(TAG, "Request headers:\n$httpRequest")

                // Connect using raw SSL socket
                val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(WSS_HOST, 443) as SSLSocket
                socket.soTimeout = 30000
                socket.tcpNoDelay = true

                val outputStream = socket.outputStream
                val inputStream = socket.inputStream

                // Send HTTP upgrade request
                outputStream.write(httpRequest.toByteArray(Charsets.US_ASCII))
                outputStream.flush()

                // Read HTTP response
                val responseReader = BufferedReader(inputStream.reader())
                val statusLine = responseReader.readLine()
                Log.d(TAG, "Response status: $statusLine")

                if (statusLine == null || !statusLine.contains("101")) {
                    // Read rest of response for debugging
                    val headers = StringBuilder()
                    var line: String?
                    var serverDate: String? = null
                    while (responseReader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                        headers.append(line).append("\n")
                        if (line!!.startsWith("Date:", ignoreCase = true)) {
                            serverDate = line!!.substring(5).trim()
                        }
                    }
                    Log.e(TAG, "Full response:\n$statusLine\n$headers")
                    socket.close()

                    // If 403, try clock skew correction
                    if (statusLine != null && statusLine.contains("403")) {
                        continuation.resume(TtsResult(
                            success = false,
                            error = "HTTP 403 Forbidden",
                            // We'll handle retry in generateSpeech()
                        ))
                    } else {
                        continuation.resume(TtsResult(
                            success = false,
                            error = "WebSocket upgrade failed: $statusLine"
                        ))
                    }
                    return@suspendCoroutine
                }

                // Success - WebSocket connected
                Log.d(TAG, "WebSocket connected successfully!")

                // Skip remaining HTTP headers
                var headerLine: String?
                var serverDate: String? = null
                while (responseReader.readLine().also { headerLine = it } != null && headerLine!!.isNotEmpty()) {
                    if (headerLine!!.startsWith("Date:", ignoreCase = true)) {
                        serverDate = headerLine!!.substring(5).trim()
                    }
                }

                // Send config message
                sendWebSocketFrame(outputStream, buildConfigMessage())
                Log.d(TAG, "Sent config message")

                // Send SSML message
                val ssml = buildSsmlMessage(text, voiceId, connectionId)
                sendWebSocketFrame(outputStream, ssml)
                Log.d(TAG, "Sent SSML message")

                // Read WebSocket frames
                val audioChunks = mutableListOf<ByteArray>()
                var synthesisSuccess = false
                var errorMsg: String? = null

                // Simple frame reader (handles text frames for now)
                // For binary frames we need a byte-level reader
                val byteInput = BufferedInputStream(inputStream)
                var readCount = 0
                val maxReads = 600 // 60 seconds timeout at 100ms per read
                val startTime = System.currentTimeMillis()

                while (readCount < maxReads && !synthesisSuccess && errorMsg == null) {
                    if (System.currentTimeMillis() - startTime > 65000) {
                        errorMsg = "TTS timeout after 65 seconds"
                        break
                    }

                    // Check if there's data available
                    if (byteInput.available() > 0) {
                        val opcode = byteInput.read() and 0xFF
                        val payloadLenByte = byteInput.read() and 0xFF
                        val isMasked = (opcode and 0x80) != 0
                        val frameOpcode = opcode and 0x0F

                        var payloadLen = payloadLenByte.toLong()
                        if (payloadLenByte == 126) {
                            val b1 = (byteInput.read() and 0xFF).toLong()
                            val b2 = (byteInput.read() and 0xFF).toLong()
                            payloadLen = (b1 shl 8) or b2
                        } else if (payloadLenByte == 127) {
                            payloadLen = 0L
                            for (i in 7 downTo 0) {
                                payloadLen = (payloadLen shl 8) or (byteInput.read() and 0xFF).toLong()
                            }
                        }

                        val maskKey = if (isMasked) ByteArray(4) { byteInput.read().toByte() } else null

                        if (payloadLen > 0 && payloadLen < 10_000_000) {
                            val payload = ByteArray(payloadLen.toInt())
                            var offset = 0
                            while (offset < payload.size) {
                                val read = byteInput.read(payload, offset, payload.size - offset)
                                if (read <= 0) break
                                offset += read
                            }

                            // Unmask if needed
                            if (isMasked && maskKey != null) {
                                for (i in payload.indices) {
                                    payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                                }
                            }

                            when (frameOpcode) {
                                1 -> { // Text frame
                                    val textMsg = String(payload, Charsets.UTF_8)
                                    Log.d(TAG, "WS Text (${payload.size}B): ${textMsg.take(200)}")
                                    when {
                                        textMsg.contains("Path:turn.start") -> {
                                            Log.d(TAG, "Synthesis started")
                                        }
                                        textMsg.contains("Path:turn.end") -> {
                                            Log.d(TAG, "Synthesis completed")
                                            synthesisSuccess = true
                                        }
                                        textMsg.contains("error") || textMsg.contains("Error") -> {
                                            Log.e(TAG, "TTS Error: $textMsg")
                                            errorMsg = extractErrorMessage(textMsg) ?: "TTS synthesis error"
                                        }
                                    }
                                }
                                2 -> { // Binary frame (audio data)
                                    if (payload.size > 2) {
                                        // Skip 2-byte header, rest is audio
                                        val audioData = payload.copyOfRange(2, payload.size)
                                        audioChunks.add(audioData)
                                        Log.d(TAG, "Audio chunk: ${audioData.size} bytes")
                                    }
                                }
                                8 -> { // Close frame
                                    Log.d(TAG, "Server sent close frame")
                                    synthesisSuccess = true // treat as done
                                }
                                9 -> { // Ping - send pong
                                    sendPongFrame(outputStream, payload)
                                }
                            }
                        } else if (frameOpcode == 8) {
                            // Close frame with no payload
                            synthesisSuccess = true
                        }
                        readCount = 0
                    } else {
                        readCount++
                        Thread.sleep(100)
                    }
                }

                // Send close frame
                try {
                    sendCloseFrame(outputStream)
                    socket.close()
                } catch (_: Exception) {}

                if (synthesisSuccess && audioChunks.isNotEmpty()) {
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
                    Log.d(TAG, "Saved: ${file.absolutePath} ($totalBytes bytes, ~${estimatedDuration}s)")
                    continuation.resume(TtsResult(true, file.absolutePath, totalBytes, estimatedDuration))
                } else {
                    continuation.resume(TtsResult(false, error = errorMsg ?: "No audio data received"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "TTS generation error", e)
                continuation.resume(TtsResult(false, error = e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Generate speech with 403 retry and clock skew adjustment (matching edge-tts).
     */
    suspend fun generateSpeech(
        text: String,
        voiceId: String,
        title: String = text.take(50)
    ): TtsResult {
        // First attempt
        val result = attemptGenerate(text, voiceId)
        if (result.success) return result

        // If 403, retry with clock skew correction
        if (result.error?.contains("403") == true) {
            Log.w(TAG, "Got 403, will retry in generateSpeech after getting server time...")

            // Try to get server time via HTTP GET first
            var serverTime: Long? = null
            try {
                val serverSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(WSS_HOST, 443)
                serverSocket.soTimeout = 10000
                val out = serverSocket.outputStream
                val `in` = BufferedReader(serverSocket.inputStream.reader())
                out.write("HEAD / HTTP/1.1\r\nHost: $WSS_HOST\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()
                var line: String?
                while (`in`.readLine().also { line = it } != null) {
                    if (line!!.startsWith("Date:", ignoreCase = true)) {
                        val dateStr = line!!.substring(5).trim()
                        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                        sdf.timeZone = TimeZone.getTimeZone("GMT")
                        serverTime = sdf.parse(dateStr)?.time?.let { it / 1000 }
                        Log.d(TAG, "Server time: $dateStr -> $serverTime")
                        break
                    }
                }
                serverSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get server time", e)
            }

            if (serverTime != null) {
                val clientTime = System.currentTimeMillis() / 1000
                val clockSkew = (serverTime - clientTime).toDouble()
                Log.w(TAG, "Clock skew: server=$serverTime, client=$clientTime, diff=${clockSkew}s")

                return attemptGenerate(text, voiceId, clockSkew)
            }
        }

        return result
    }

    fun getTtsDir(): File {
        val dir = File(context.filesDir, TTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun deleteFile(filePath: String): Boolean = File(filePath).delete()

    /**
     * Send a WebSocket text frame (masked, as required by client).
     */
    private fun sendWebSocketFrame(outputStream: java.io.OutputStream, message: String) {
        val msgBytes = message.toByteArray(Charsets.UTF_8)
        val maskKey = ByteArray(4) { (Math.random() * 256).toInt().toByte() }

        val firstByte = 0x81.toByte() // FIN + opcode 1 (text)
        var secondByte: Byte

        val headerLen = if (msgBytes.size <= 125) {
            secondByte = (msgBytes.size or 0x80).toByte() // MASK bit set
            2
        } else if (msgBytes.size <= 65535) {
            secondByte = (126 or 0x80).toByte()
            4
        } else {
            secondByte = (127 or 0x80).toByte()
            10
        }

        val frame = java.io.ByteArrayOutputStream()
        frame.write(firstByte.toInt())
        frame.write(secondByte.toInt())

        if (msgBytes.size > 65535) {
            val len = msgBytes.size.toLong()
            frame.write(((len ushr 56) and 0xFF).toInt())
            frame.write(((len ushr 48) and 0xFF).toInt())
            frame.write(((len ushr 40) and 0xFF).toInt())
            frame.write(((len ushr 32) and 0xFF).toInt())
            frame.write(((len ushr 24) and 0xFF).toInt())
            frame.write(((len ushr 16) and 0xFF).toInt())
            frame.write(((len ushr 8) and 0xFF).toInt())
            frame.write((len and 0xFF).toInt())
        } else if (msgBytes.size > 125) {
            frame.write((msgBytes.size ushr 8) and 0xFF)
            frame.write(msgBytes.size and 0xFF)
        }

        frame.write(maskKey)

        // Write masked payload
        val masked = ByteArray(msgBytes.size)
        for (i in msgBytes.indices) {
            masked[i] = (msgBytes[i].toInt() xor maskKey[i % 4].toInt()).toByte()
        }
        frame.write(masked)

        outputStream.write(frame.toByteArray())
        outputStream.flush()
    }

    private fun sendPongFrame(outputStream: java.io.OutputStream, payload: ByteArray) {
        outputStream.write(byteArrayOf(0x8A.toByte(), payload.size.toByte()))
        outputStream.write(payload)
        outputStream.flush()
    }

    private fun sendCloseFrame(outputStream: java.io.OutputStream) {
        try {
            val maskKey = ByteArray(4) { (Math.random() * 256).toInt().toByte() }
            val payload = byteArrayOf(0x03.toByte(), 0xE8.toByte()) // close code 1000
            val masked = ByteArray(2)
            for (i in payload.indices) {
                masked[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
            outputStream.write(byteArrayOf(0x88.toByte(), 0x82.toByte())) // FIN + close, masked, len=2
            outputStream.write(maskKey)
            outputStream.write(masked)
            outputStream.flush()
        } catch (_: Exception) {}
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
