package com.screenpulse.tv.server

import android.content.Context
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
 * GET  /api/tts/voices            - 获取可用的TTS语音列表
 * POST /api/tts/generate          - 生成TTS语音
 * GET  /api/tts                   - 获取所有TTS音频文件
 * DELETE /api/tts/{id}            - 删除TTS音频
 * POST /api/tts/{id}/volume       - 设置TTS音量
 * POST /api/tts/{id}/toggle       - 启用/禁用TTS音频
 * POST /api/tts/{id}/play         - 播放指定TTS音频作为背景音乐
 * POST /api/tts/stop              - 停止背景音乐播放
 * GET  /api/tts/playing           - 获取当前TTS播放状态
 * POST /api/tts/queue             - 设置TTS播放队列
 * /tts/*                          - TTS静态文件服务
 * /media/*                        - 媒体静态文件服务
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

    /**
     * 提供 Web 管理面板 HTML
     * 返回一个完整的单页应用 HTML，包含播放列表管理、上传、控制等功能
     */
    private fun serveAdminPanel(): NanoHTTPD.Response {
        val html = buildAdminPanelHtml()
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

    /**
     * 构建 Web 管理面板 HTML
     * 包含完整的 CSS 和 JavaScript 的单页应用
     * 4个Tab: 播放列表、媒体库、背景音乐/TTS、设置
     */
    private fun buildAdminPanelHtml(): String {
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ScreenPulse TV - 管理面板</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif; background: #f0f2f5; color: #333; min-height: 100vh; }

        /* Header */
        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #fff; padding: 16px 24px; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 2px 8px rgba(0,0,0,0.15); }
        .header h1 { font-size: 20px; font-weight: 600; }
        .header .device-info { font-size: 13px; opacity: 0.9; }

        /* Tab Navigation */
        .tabs { display: flex; background: #fff; border-bottom: 2px solid #e8e8e8; padding: 0 24px; }
        .tab { padding: 12px 24px; cursor: pointer; font-size: 14px; font-weight: 500; color: #666; border-bottom: 3px solid transparent; transition: all 0.2s; }
        .tab:hover { color: #667eea; }
        .tab.active { color: #667eea; border-bottom-color: #667eea; }

        /* Content */
        .content { max-width: 1200px; margin: 0 auto; padding: 24px; }
        .tab-content { display: none; }
        .tab-content.active { display: block; }

        /* Cards */
        .card { background: #fff; border-radius: 12px; padding: 20px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .card h2 { font-size: 16px; margin-bottom: 16px; color: #333; padding-bottom: 12px; border-bottom: 1px solid #f0f0f0; }

        /* Playback Controls Bar */
        .controls-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
        .btn { padding: 8px 16px; border: none; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; transition: all 0.15s; }
        .btn:hover { opacity: 0.85; }
        .btn-primary { background: #667eea; color: #fff; }
        .btn-success { background: #52c41a; color: #fff; }
        .btn-warning { background: #faad14; color: #fff; }
        .btn-danger { background: #ff4d4f; color: #fff; }
        .btn-default { background: #fff; color: #333; border: 1px solid #d9d9d9; }
        .btn-sm { padding: 4px 10px; font-size: 12px; }

        /* Table */
        .data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
        .data-table th, .data-table td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #f0f0f0; }
        .data-table th { background: #fafafa; color: #666; font-weight: 600; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; }
        .data-table tr:hover { background: #f5f7ff; }
        .data-table .actions { display: flex; gap: 6px; }

        /* Badge */
        .badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
        .badge-video { background: #fff1f0; color: #cf1322; }
        .badge-image { background: #e6f7ff; color: #1890ff; }
        .badge-iptv { background: #fff7e6; color: #d46b08; }
        .badge-stream { background: #f9f0ff; color: #722ed1; }
        .badge-webpage { background: #f6ffed; color: #389e0d; }
        .badge-tts { background: #e6fffb; color: #13c2c2; }
        .badge-audio { background: #fff0f6; color: #eb2f96; }

        /* Upload Area */
        .upload-area { border: 2px dashed #d9d9d9; border-radius: 8px; padding: 30px; text-align: center; cursor: pointer; transition: all 0.2s; }
        .upload-area:hover { border-color: #667eea; background: #f5f7ff; }
        .upload-area p { margin-bottom: 8px; color: #666; }
        .upload-area .hint { font-size: 12px; color: #999; }

        /* Form */
        .form-row { display: flex; gap: 12px; margin-bottom: 16px; align-items: flex-end; }
        .form-group { flex: 1; }
        .form-group label { display: block; margin-bottom: 6px; font-size: 13px; color: #666; font-weight: 500; }
        .form-group input, .form-group select, .form-group textarea {
            width: 100%; padding: 8px 12px; border: 1px solid #d9d9d9; border-radius: 6px;
            font-size: 13px; transition: border-color 0.2s;
        }
        .form-group input:focus, .form-group select:focus, .form-group textarea:focus {
            outline: none; border-color: #667eea; box-shadow: 0 0 0 2px rgba(102,126,234,0.2);
        }
        .form-group textarea { resize: vertical; min-height: 80px; }

        /* Toast */
        .toast { position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%); background: rgba(0,0,0,0.75); color: #fff; padding: 10px 20px; border-radius: 8px; font-size: 13px; z-index: 1000; opacity: 0; transition: opacity 0.3s; pointer-events: none; }
        .toast.show { opacity: 1; }

        /* Empty State */
        .empty-state { text-align: center; padding: 40px; color: #999; }
        .empty-state .icon { font-size: 40px; margin-bottom: 12px; }
        .empty-state h3 { font-size: 16px; color: #666; margin-bottom: 6px; }

        /* Settings Grid */
        .settings-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; }
        .setting-item { padding: 16px; background: #fafafa; border-radius: 8px; border: 1px solid #f0f0f0; }
        .setting-item label { display: block; font-size: 13px; color: #666; margin-bottom: 8px; }
        .setting-item select, .setting-item input { width: 100%; padding: 8px; border: 1px solid #d9d9d9; border-radius: 6px; font-size: 13px; }

        /* TTS Section */
        .tts-section { margin-bottom: 20px; }
        .tts-status { display: flex; align-items: center; gap: 12px; padding: 12px; background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 8px; margin-bottom: 16px; }
        .tts-status .dot { width: 8px; height: 8px; border-radius: 50%; background: #52c41a; }
        .tts-status .dot.stopped { background: #d9d9d9; }
        .tts-status .dot.playing { background: #52c41a; animation: pulse 1s infinite; }

        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
    </style>
</head>
<body>
    <div class="header">
        <h1>ScreenPulse TV 管理面板</h1>
        <div class="device-info" id="header-info">加载中...</div>
    </div>

    <div class="tabs">
        <div class="tab active" onclick="switchTab('playlist')">播放列表</div>
        <div class="tab" onclick="switchTab('media')">媒体库</div>
        <div class="tab" onclick="switchTab('tts')">背景音乐 / TTS</div>
        <div class="tab" onclick="switchTab('settings')">设置</div>
    </div>

    <div class="content">
        <!-- Tab 1: Playlist -->
        <div id="tab-playlist" class="tab-content active">
            <!-- Playback controls -->
            <div class="card">
                <h2>播放控制</h2>
                <div class="controls-bar">
                    <button class="btn btn-success" onclick="apiPost('/api/control/play')">&#9654; 播放</button>
                    <button class="btn btn-warning" onclick="apiPost('/api/control/pause')">&#9208; 暂停</button>
                    <button class="btn btn-default" onclick="apiPost('/api/control/skip')">&#9197; 下一项</button>
                    <button class="btn btn-default" onclick="apiPost('/api/control/previous')">&#9196; 上一项</button>
                    <button class="btn btn-primary" onclick="refreshAll()">&#8635; 刷新</button>
                </div>
            </div>

            <!-- Upload -->
            <div class="card">
                <h2>添加媒体</h2>
                <div class="upload-area" onclick="document.getElementById('file-input').click()">
                    <p>点击或拖拽文件上传</p>
                    <p class="hint">支持: MP4, MKV, AVI, JPG, PNG, GIF (最大 50MB)</p>
                    <input type="file" id="file-input" style="display:none" accept="video/*,image/*,audio/*" onchange="uploadFile(this.files[0])">
                </div>
                <div style="margin-top: 12px;">
                    <div class="form-row">
                        <div class="form-group">
                            <input type="text" id="media-url" placeholder="输入网络地址 URL">
                        </div>
                        <div class="form-group" style="flex:0 0 120px;">
                            <select id="media-type">
                                <option value="video">视频</option>
                                <option value="iptv">IPTV</option>
                                <option value="stream">网络流</option>
                                <option value="webpage">网页</option>
                            </select>
                        </div>
                        <div class="form-group" style="flex:0 0 80px;">
                            <button class="btn btn-primary" onclick="addMediaUrl()">添加</button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Playlist table -->
            <div class="card">
                <h2>播放列表</h2>
                <div id="playlist-container">
                    <div class="empty-state"><div class="icon">&#128203;</div><h3>播放列表为空</h3></div>
                </div>
            </div>
        </div>

        <!-- Tab 2: Media Library -->
        <div id="tab-media" class="tab-content">
            <div class="card">
                <h2>媒体库</h2>
                <div id="media-container">
                    <div class="empty-state"><div class="icon">&#128193;</div><h3>媒体库为空</h3></div>
                </div>
            </div>
        </div>

        <!-- Tab 3: TTS / Background Music -->
        <div id="tab-tts" class="tab-content">
            <!-- TTS Status -->
            <div class="card">
                <h2>背景音乐状态</h2>
                <div class="tts-status" id="bg-music-status">
                    <div class="dot stopped"></div>
                    <span>未播放</span>
                    <div style="margin-left:auto;">
                        <button class="btn btn-sm btn-danger" onclick="stopBgMusic()" style="display:none;" id="btn-stop-bg">停止播放</button>
                    </div>
                </div>
            </div>

            <!-- TTS Generate -->
            <div class="card">
                <h2>文字转语音 (Edge TTS)</h2>
                <div class="form-row">
                    <div class="form-group" style="flex:2;">
                        <label>输入文字内容</label>
                        <textarea id="tts-text" placeholder="请输入要转换的文本内容..."></textarea>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group" style="flex:0 0 200px;">
                        <label>发音人</label>
                        <select id="tts-voice"></select>
                    </div>
                    <div class="form-group" style="flex:0 0 150px;">
                        <label>标题（可选）</label>
                        <input type="text" id="tts-title" placeholder="自动生成">
                    </div>
                    <div class="form-group" style="flex:0 0 100px; display:flex; align-items:flex-end;">
                        <button class="btn btn-primary" onclick="generateTts()">生成语音</button>
                    </div>
                </div>
                <div id="tts-generating" style="display:none; color:#667eea; font-size:13px; padding:8px 0;">&#9203; 正在生成语音，请稍候...</div>
            </div>

            <!-- TTS Audio Library -->
            <div class="card">
                <h2>语音库 / 背景音乐</h2>
                <div style="margin-bottom:12px;">
                    <button class="btn btn-sm btn-success" onclick="playAllTts()">&#9654; 全部播放</button>
                    <button class="btn btn-sm btn-danger" onclick="stopBgMusic()">&#9209; 停止</button>
                    <span style="margin-left:12px; font-size:12px; color:#999;">可多选后添加到播放队列</span>
                </div>
                <div id="tts-container">
                    <div class="empty-state"><div class="icon">&#127925;</div><h3>语音库为空</h3><p>使用上方功能生成语音</p></div>
                </div>
            </div>
        </div>

        <!-- Tab 4: Settings -->
        <div id="tab-settings" class="tab-content">
            <div class="card">
                <h2>播放设置</h2>
                <div class="settings-grid">
                    <div class="setting-item">
                        <label>播放模式</label>
                        <select id="set-play-mode">
                            <option value="loop">循环播放</option>
                            <option value="sequential">顺序播放</option>
                            <option value="random">随机播放</option>
                        </select>
                    </div>
                    <div class="setting-item">
                        <label>过场动画</label>
                        <select id="set-transition">
                            <option value="random">随机</option>
                            <option value="none">无</option>
                            <option value="fade">淡入淡出</option>
                            <option value="zoom">缩放</option>
                            <option value="slide_left">左滑</option>
                            <option value="slide_right">右滑</option>
                            <option value="dissolve">溶解</option>
                        </select>
                    </div>
                    <div class="setting-item">
                        <label>过场时长 (毫秒)</label>
                        <input type="number" id="set-transition-duration" value="600" min="200" max="2000" step="100">
                    </div>
                    <div class="setting-item">
                        <label>默认图片时长 (秒)</label>
                        <input type="number" id="set-image-duration" value="10" min="1" max="300">
                    </div>
                    <div class="setting-item">
                        <label>默认网页时长 (秒)</label>
                        <input type="number" id="set-webpage-duration" value="30" min="5" max="600">
                    </div>
                    <div class="setting-item">
                        <label>背景音乐音量 (%)</label>
                        <input type="number" id="set-bg-volume" value="80" min="0" max="100">
                    </div>
                </div>
                <div style="margin-top:16px;">
                    <button class="btn btn-primary" onclick="saveSettings()">保存设置</button>
                </div>
            </div>

            <div class="card">
                <h2>视频音频</h2>
                <div class="setting-item" style="max-width:300px;">
                    <label>播放时禁用视频原声（使用背景音乐代替）</label>
                    <select id="set-video-muted">
                        <option value="false">正常播放视频声音</option>
                        <option value="true">静音视频，仅播放背景音乐</option>
                    </select>
                    <button class="btn btn-primary" style="margin-top:8px;" onclick="saveVideoMute()">保存</button>
                </div>
            </div>
        </div>
    </div>

    <div id="toast" class="toast"></div>

    <script>
    const API = '';

    // ===== Tab Switching =====
    function switchTab(name) {
        document.querySelectorAll('.tab').forEach(function(t) { t.classList.remove('active'); });
        document.querySelectorAll('.tab-content').forEach(function(c) { c.classList.remove('active'); });
        document.getElementById('tab-' + name) && document.getElementById('tab-' + name).classList.add('active');
        var tabs = document.querySelectorAll('.tab');
        var tabMap = { playlist: 0, media: 1, tts: 2, settings: 3 };
        if (tabMap[name] !== undefined && tabs[tabMap[name]]) tabs[tabMap[name]].classList.add('active');
        if (name === 'tts') { loadTtsVoices(); loadTtsLibrary(); loadBgMusicStatus(); }
        if (name === 'media') loadMediaLibrary();
        if (name === 'settings') loadSettings();
    }

    // ===== Utility =====
    async function apiGet(url) { var r = await fetch(API + url); return r.json(); }
    async function apiPost(url, data) {
        var opts = data ? { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data) } : { method:'POST' };
        return (await fetch(API + url, opts)).json();
    }
    async function apiDelete(url) { return (await fetch(API + url, {method:'DELETE'})).json(); }

    function showToast(msg) {
        var t = document.getElementById('toast');
        t.textContent = msg; t.classList.add('show');
        setTimeout(function() { t.classList.remove('show'); }, 2500);
    }
    function esc(s) { var d = document.createElement('div'); d.textContent = s; return d.innerHTML; }
    function fmtSize(b) { if (b < 1024) return b + ' B'; if (b < 1048576) return (b/1024).toFixed(1) + ' KB'; return (b/1048576).toFixed(1) + ' MB'; }
    function fmtDate(ts) { return new Date(ts).toLocaleString('zh-CN'); }

    // ===== Status =====
    async function loadStatus() {
        try {
            var d = await apiGet('/api/status');
            document.getElementById('header-info').textContent = d.deviceName + ' | ' + d.ip + ':' + d.port;
        } catch(e) {}
    }

    // ===== Playlist =====
    async function loadPlaylist() {
        try {
            var items = await apiGet('/api/playlist');
            var c = document.getElementById('playlist-container');
            if (!items || items.length === 0) { c.innerHTML = '<div class="empty-state"><div class="icon">&#128203;</div><h3>播放列表为空</h3></div>'; return; }
            var h = '<table class="data-table"><thead><tr><th>#</th><th>标题</th><th>类型</th><th>时长</th><th>音量</th><th>操作</th></tr></thead><tbody>';
            items.forEach(function(item, i) {
                h += '<tr><td>' + (i+1) + '</td><td>' + esc(item.title||'未命名') + '</td>';
                h += '<td><span class="badge badge-' + (item.type||'video') + '">' + (item.type||'video') + '</span></td>';
                h += '<td>' + (item.duration ? item.duration + '秒' : '自动') + '</td>';
                h += '<td>' + item.volume + '%</td>';
                h += '<td class="actions">';
                h += '<button class="btn btn-sm btn-default" onclick="toggleItem(' + item.id + ',' + !item.enabled + ')" title="启用/禁用">' + (item.enabled ? '&#10003;' : '&#10007;') + '</button>';
                h += '<button class="btn btn-sm btn-danger" onclick="deleteItem(' + item.id + ')" title="删除">&#10005;</button>';
                h += '</td></tr>';
            });
            h += '</tbody></table>';
            c.innerHTML = h;
        } catch(e) { console.error(e); }
    }

    async function uploadFile(file) {
        if (!file) return;
        if (file.size > 50*1024*1024) { showToast('文件超过50MB'); return; }
        showToast('上传中...');
        var fd = new FormData(); fd.append('file', file);
        try {
            var r = await fetch(API + '/api/media/upload', { method:'POST', body:fd });
            if (r.ok) { showToast('上传成功'); loadPlaylist(); loadMediaLibrary(); }
            else showToast('上传失败');
        } catch(e) { showToast('上传失败: ' + e.message); }
    }

    async function addMediaUrl() {
        var url = document.getElementById('media-url').value.trim();
        var type = document.getElementById('media-type').value;
        if (!url) { showToast('请输入URL'); return; }
        try {
            await apiPost('/api/media/url', { url: url, type: type, title: url.split('/').pop() || '未命名' });
            showToast('添加成功');
            document.getElementById('media-url').value = '';
            loadPlaylist(); loadMediaLibrary();
        } catch(e) { showToast('添加失败'); }
    }

    async function toggleItem(id, enabled) {
        try { await apiPost('/api/playlist/' + id + '/toggle', { enabled: enabled }); loadPlaylist(); } catch(e) {}
    }

    async function deleteItem(id) {
        if (!confirm('确定删除?')) return;
        try { await apiDelete('/api/playlist/' + id); showToast('已删除'); loadPlaylist(); } catch(e) {}
    }

    // ===== Media Library =====
    async function loadMediaLibrary() {
        try {
            var items = await apiGet('/api/media');
            var c = document.getElementById('media-container');
            if (!items || items.length === 0) { c.innerHTML = '<div class="empty-state"><div class="icon">&#128193;</div><h3>媒体库为空</h3></div>'; return; }
            var h = '<table class="data-table"><thead><tr><th>标题</th><th>类型</th><th>大小</th><th>创建时间</th><th>操作</th></tr></thead><tbody>';
            items.forEach(function(item) {
                h += '<tr><td>' + esc(item.title||'未命名') + '</td>';
                h += '<td><span class="badge badge-' + (item.type||'video') + '">' + (item.type||'video') + '</span></td>';
                h += '<td>' + fmtSize(item.fileSize||0) + '</td>';
                h += '<td>' + fmtDate(item.createdAt) + '</td>';
                h += '<td class="actions">';
                h += '<button class="btn btn-sm btn-danger" onclick="deleteMedia(' + item.id + ')">删除</button>';
                h += '</td></tr>';
            });
            h += '</tbody></table>';
            c.innerHTML = h;
        } catch(e) {}
    }

    async function deleteMedia(id) {
        if (!confirm('确定删除此媒体?')) return;
        try { await apiDelete('/api/media/' + id); showToast('已删除'); loadMediaLibrary(); } catch(e) {}
    }

    // ===== TTS =====
    async function loadTtsVoices() {
        try {
            var voices = await apiGet('/api/tts/voices');
            var sel = document.getElementById('tts-voice');
            sel.innerHTML = '';
            voices.forEach(function(v) {
                var opt = document.createElement('option');
                opt.value = v.id;
                opt.textContent = v.name;
                sel.appendChild(opt);
            });
        } catch(e) {
            var sel = document.getElementById('tts-voice');
            if (sel.options.length === 0) {
                var defaults = [
                    {id:'zh-CN-XiaoxiaoNeural', name:'晓晓 (女声，中文)'},
                    {id:'zh-CN-YunxiNeural', name:'云希 (男声，中文)'},
                    {id:'zh-CN-YunjianNeural', name:'云健 (男声，新闻)'},
                    {id:'en-US-JennyNeural', name:'Jenny (Female, English)'},
                    {id:'ja-JP-NanamiNeural', name:'七海 (女声，日语)'}
                ];
                sel.innerHTML = defaults.map(function(v) { return '<option value="' + v.id + '">' + v.name + '</option>'; }).join('');
            }
        }
    }

    async function generateTts() {
        var text = document.getElementById('tts-text').value.trim();
        var voiceId = document.getElementById('tts-voice').value;
        var title = document.getElementById('tts-title').value.trim() || text.substring(0, 50);
        if (!text) { showToast('请输入文字内容'); return; }

        document.getElementById('tts-generating').style.display = 'block';
        try {
            var r = await apiPost('/api/tts/generate', { text: text, voiceId: voiceId, title: title });
            if (r.success) {
                showToast('语音生成成功！');
                document.getElementById('tts-text').value = '';
                document.getElementById('tts-title').value = '';
                loadTtsLibrary();
            } else {
                showToast('生成失败: ' + (r.error || '未知错误'));
            }
        } catch(e) {
            showToast('生成失败: ' + e.message);
        }
        document.getElementById('tts-generating').style.display = 'none';
    }

    async function loadTtsLibrary() {
        try {
            var items = await apiGet('/api/tts');
            var c = document.getElementById('tts-container');
            if (!items || items.length === 0) { c.innerHTML = '<div class="empty-state"><div class="icon">&#127925;</div><h3>语音库为空</h3><p>使用上方功能生成语音</p></div>'; return; }
            var h = '<table class="data-table"><thead><tr><th>标题</th><th>发音人</th><th>内容预览</th><th>大小</th><th>音量</th><th>状态</th><th>操作</th></tr></thead><tbody>';
            items.forEach(function(item) {
                h += '<tr>';
                h += '<td>' + esc(item.title||'未命名') + '</td>';
                h += '<td><span class="badge badge-tts">' + esc(item.voice||'') + '</span></td>';
                h += '<td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="' + esc(item.text||'') + '">' + esc(item.text||'').substring(0, 60) + '</td>';
                h += '<td>' + fmtSize(item.fileSize||0) + '</td>';
                h += '<td><input type="number" min="0" max="100" value="' + (item.volume||80) + '" style="width:50px;padding:2px 4px;" onchange="setTtsVolume(' + item.id + ', this.value)"></td>';
                h += '<td>' + (item.enabled ? '&#10003; 启用' : '&#10007; 禁用') + '</td>';
                h += '<td class="actions">';
                h += '<button class="btn btn-sm btn-success" onclick="playTts(' + item.id + ')" title="播放">&#9654;</button>';
                h += '<button class="btn btn-sm btn-default" onclick="toggleTts(' + item.id + ',' + !item.enabled + ')" title="启用/禁用">' + (item.enabled ? '禁' : '启') + '</button>';
                h += '<button class="btn btn-sm btn-danger" onclick="deleteTts(' + item.id + ')" title="删除">&#10005;</button>';
                h += '</td></tr>';
            });
            h += '</tbody></table>';
            c.innerHTML = h;
        } catch(e) {}
    }

    async function playTts(id) {
        try { await apiPost('/api/tts/' + id + '/play'); showToast('开始播放'); loadBgMusicStatus(); } catch(e) { showToast('播放失败'); }
    }

    async function playAllTts() {
        try {
            var items = await apiGet('/api/tts');
            var ids = (items||[]).filter(function(i) { return i.enabled; }).map(function(i) { return i.id; });
            if (ids.length === 0) { showToast('没有可播放的语音'); return; }
            await apiPost('/api/tts/queue', { audioIds: ids });
            showToast('已添加 ' + ids.length + ' 个语音到播放队列');
            loadBgMusicStatus();
        } catch(e) { showToast('操作失败'); }
    }

    async function stopBgMusic() {
        try { await apiPost('/api/tts/stop'); showToast('已停止'); loadBgMusicStatus(); } catch(e) {}
    }

    async function loadBgMusicStatus() {
        try {
            var s = await apiGet('/api/tts/playing');
            var el = document.getElementById('bg-music-status');
            var dot = el.querySelector('.dot');
            var btn = document.getElementById('btn-stop-bg');
            if (s.playing) {
                dot.className = 'dot playing';
                el.querySelector('span').textContent = '正在播放: ' + (s.title || '未知');
                btn.style.display = 'inline-block';
            } else {
                dot.className = 'dot stopped';
                el.querySelector('span').textContent = '未播放';
                btn.style.display = 'none';
            }
        } catch(e) {}
    }

    async function setTtsVolume(id, vol) {
        try { await apiPost('/api/tts/' + id + '/volume', { volume: parseInt(vol) }); } catch(e) {}
    }

    async function toggleTts(id, enabled) {
        try { await apiPost('/api/tts/' + id + '/toggle', { enabled: enabled }); loadTtsLibrary(); } catch(e) {}
    }

    async function deleteTts(id) {
        if (!confirm('确定删除此语音?')) return;
        try { await apiDelete('/api/tts/' + id); showToast('已删除'); loadTtsLibrary(); } catch(e) {}
    }

    // ===== Settings =====
    async function loadSettings() {
        try {
            var s = await apiGet('/api/settings');
            document.getElementById('set-play-mode').value = s.playMode || 'loop';
            document.getElementById('set-transition').value = s.transitionEffect || 'random';
            document.getElementById('set-transition-duration').value = s.transitionDuration || 600;
            document.getElementById('set-image-duration').value = s.defaultImageDuration || 10;
            document.getElementById('set-webpage-duration').value = s.defaultWebpageDuration || 30;
            document.getElementById('set-bg-volume').value = s.bgMusicVolume || 80;
            document.getElementById('set-video-muted').value = s.videoMuted ? 'true' : 'false';
        } catch(e) {}
    }

    async function saveSettings() {
        try {
            await apiPost('/api/settings', {
                playMode: document.getElementById('set-play-mode').value,
                transitionEffect: document.getElementById('set-transition').value,
                transitionDuration: parseInt(document.getElementById('set-transition-duration').value),
                defaultImageDuration: parseInt(document.getElementById('set-image-duration').value),
                defaultWebpageDuration: parseInt(document.getElementById('set-webpage-duration').value),
                bgMusicVolume: parseInt(document.getElementById('set-bg-volume').value)
            });
            showToast('设置已保存');
        } catch(e) { showToast('保存失败'); }
    }

    async function saveVideoMute() {
        try {
            await apiPost('/api/settings', { videoMuted: document.getElementById('set-video-muted').value === 'true' });
            showToast('已保存');
        } catch(e) { showToast('保存失败'); }
    }

    // ===== Refresh =====
    function refreshAll() { loadStatus(); loadPlaylist(); }
    setInterval(refreshAll, 10000);
    loadStatus(); loadPlaylist();
    </script>
</body>
</html>""".trimIndent()
    }

    // ==================== 设备状态 API ====================

    /**
     * GET /api/status - 获取设备状态
     */
    private fun getStatus(): NanoHTTPD.Response {
        val ip = NetworkUtils.getDeviceIpAddress()
        val port = (context as? com.screenpulse.tv.ScreenPulseApp)?.webServerManager?.port ?: 8080

        val playlistCount = try {
            database.playlistDao().getActivePlaylistCount()
        } catch (e: Exception) {
            0
        }

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
     */
    private fun getPlaylist(): NanoHTTPD.Response {
        val items = try {
            database.playlistDao().getActivePlaylistItems()
        } catch (e: Exception) {
            emptyList()
        }

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

    /**
     * GET /api/media - 获取媒体库列表
     */
    private fun getMediaLibrary(): NanoHTTPD.Response {
        val items = try {
            database.mediaDao().getAll()
        } catch (e: Exception) {
            emptyList()
        }

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

    /**
     * GET /api/tts - 获取所有TTS音频文件列表
     */
    private fun getTtsLibrary(): NanoHTTPD.Response {
        val items = try {
            database.ttsAudioDao().getAll()
        } catch (e: Exception) {
            emptyList()
        }

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

/**
 * PlaylistEntity 转换为 Map（用于 JSON 序列化）
 */
private fun PlaylistEntity.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "title" to title,
    "type" to type,
    "url" to url,
    "duration" to (duration ?: 0),
    "enabled" to enabled,
    "volume" to volume,
    "order" to playOrder,
    "createdAt" to createdAt
)
