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
 * - Web 管理面板的 HTML 页面
 * - RESTful API 端点
 * - 文件上传处理
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
 */
class ApiHandler(private val context: Context) {

    companion object {
        private const val TAG = "ApiHandler"

        /** 媒体文件存储目录 */
        private const val MEDIA_DIR = "screenpulse_media"

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
    private val apiScope = CoroutineScope(Dispatchers.IO)

    // 播放控制回调 - 由 MainActivity/ViewModel 设置
    var onPlayRequest: (() -> Unit)? = null
    var onPauseRequest: (() -> Unit)? = null
    var onSkipRequest: (() -> Unit)? = null
    var onPreviousRequest: (() -> Unit)? = null

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

                // ========== 静态文件（上传的媒体） ==========
                uri.startsWith("/media/") -> {
                    serveMediaFile(uri.removePrefix("/media/"))
                }

                // ========== SPA fallback ==========
                !uri.startsWith("/api/") && !uri.startsWith("/media/") -> {
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
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            color: #e0e0e0;
            min-height: 100vh;
        }
        .header {
            background: rgba(0,0,0,0.3);
            padding: 20px 30px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid rgba(255,255,255,0.1);
        }
        .header h1 { font-size: 24px; color: #00d4ff; }
        .header .status { font-size: 14px; color: #aaa; }
        .header .status .online { color: #4caf50; font-weight: bold; }
        .container { max-width: 1200px; margin: 0 auto; padding: 30px; }
        .card {
            background: rgba(255,255,255,0.05);
            border-radius: 12px;
            padding: 24px;
            margin-bottom: 20px;
            border: 1px solid rgba(255,255,255,0.1);
        }
        .card h2 { font-size: 18px; margin-bottom: 16px; color: #00d4ff; }
        .device-info { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; }
        .info-item { background: rgba(0,0,0,0.2); padding: 16px; border-radius: 8px; }
        .info-item .label { font-size: 12px; color: #888; margin-bottom: 4px; }
        .info-item .value { font-size: 16px; color: #fff; font-weight: bold; }
        .controls { display: flex; gap: 12px; flex-wrap: wrap; }
        .btn {
            padding: 10px 20px;
            border: none;
            border-radius: 8px;
            font-size: 14px;
            font-weight: bold;
            cursor: pointer;
            transition: all 0.2s;
        }
        .btn:hover { transform: translateY(-1px); }
        .btn-primary { background: #00d4ff; color: #000; }
        .btn-success { background: #4caf50; color: #fff; }
        .btn-warning { background: #ff9800; color: #fff; }
        .btn-danger { background: #f44336; color: #fff; }
        .btn-secondary { background: rgba(255,255,255,0.1); color: #fff; }
        .playlist-table { width: 100%; border-collapse: collapse; }
        .playlist-table th, .playlist-table td {
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid rgba(255,255,255,0.1);
        }
        .playlist-table th { color: #888; font-size: 12px; text-transform: uppercase; }
        .playlist-table tr:hover { background: rgba(255,255,255,0.05); }
        .badge {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 12px;
            font-weight: bold;
        }
        .badge-video { background: #e91e63; }
        .badge-image { background: #2196f3; }
        .badge-iptv { background: #ff5722; }
        .badge-stream { background: #9c27b0; }
        .badge-webpage { background: #4caf50; }
        .upload-area {
            border: 2px dashed rgba(255,255,255,0.2);
            border-radius: 12px;
            padding: 40px;
            text-align: center;
            cursor: pointer;
            transition: all 0.2s;
        }
        .upload-area:hover { border-color: #00d4ff; background: rgba(0,212,255,0.05); }
        .upload-area p { margin-bottom: 16px; }
        .add-url-form { display: flex; gap: 12px; }
        .add-url-form input {
            flex: 1;
            padding: 10px 16px;
            border: 1px solid rgba(255,255,255,0.2);
            border-radius: 8px;
            background: rgba(0,0,0,0.3);
            color: #fff;
            font-size: 14px;
        }
        .add-url-form select {
            padding: 10px 16px;
            border: 1px solid rgba(255,255,255,0.2);
            border-radius: 8px;
            background: rgba(0,0,0,0.3);
            color: #fff;
            font-size: 14px;
        }
        .actions-cell { display: flex; gap: 8px; }
        .icon-btn {
            width: 32px; height: 32px;
            border: none; border-radius: 6px;
            background: rgba(255,255,255,0.1);
            color: #fff; cursor: pointer;
            display: flex; align-items: center; justify-content: center;
            font-size: 16px;
        }
        .icon-btn:hover { background: rgba(255,255,255,0.2); }
        .icon-btn.delete:hover { background: rgba(244,67,54,0.8); }
        .empty-state { text-align: center; padding: 60px 20px; color: #888; }
        .empty-state .icon { font-size: 48px; margin-bottom: 16px; }
        .empty-state h3 { font-size: 20px; margin-bottom: 8px; color: #ccc; }
        .toast {
            position: fixed; bottom: 30px; left: 50%; transform: translateX(-50%);
            background: #333; color: #fff; padding: 12px 24px; border-radius: 8px;
            font-size: 14px; z-index: 1000; opacity: 0; transition: opacity 0.3s;
        }
        .toast.show { opacity: 1; }
        .modal-overlay {
            display: none; position: fixed; top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.5); z-index: 100;
            justify-content: center; align-items: center;
        }
        .modal-overlay.show { display: flex; }
        .modal {
            background: #1a1a2e; border-radius: 12px; padding: 24px;
            width: 90%; max-width: 500px; border: 1px solid rgba(255,255,255,0.1);
        }
        .modal h3 { margin-bottom: 20px; }
        .form-group { margin-bottom: 16px; }
        .form-group label { display: block; margin-bottom: 6px; font-size: 14px; color: #888; }
        .form-group input, .form-group select {
            width: 100%; padding: 10px; border-radius: 8px;
            border: 1px solid rgba(255,255,255,0.2);
            background: rgba(0,0,0,0.3); color: #fff; font-size: 14px;
        }
        .modal-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 20px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>📺 ScreenPulse TV</h1>
        <div class="status">
            <span class="online">● 在线</span> | <span id="device-ip">加载中...</span>
        </div>
    </div>

    <div class="container">
        <!-- 设备信息 -->
        <div class="card">
            <h2>设备信息</h2>
            <div class="device-info">
                <div class="info-item">
                    <div class="label">设备名称</div>
                    <div class="value" id="device-name">-</div>
                </div>
                <div class="info-item">
                    <div class="label">IP 地址</div>
                    <div class="value" id="device-ip-detail">-</div>
                </div>
                <div class="info-item">
                    <div class="label">当前状态</div>
                    <div class="value" id="playback-status">空闲</div>
                </div>
                <div class="info-item">
                    <div class="label">播放列表项数</div>
                    <div class="value" id="playlist-count">0</div>
                </div>
            </div>
        </div>

        <!-- 播放控制 -->
        <div class="card">
            <h2>播放控制</h2>
            <div class="controls">
                <button class="btn btn-success" onclick="controlPlay()">▶ 播放</button>
                <button class="btn btn-warning" onclick="controlPause()">⏸ 暂停</button>
                <button class="btn btn-secondary" onclick="controlSkip()">⏭ 下一项</button>
                <button class="btn btn-secondary" onclick="controlPrevious()">⏮ 上一项</button>
                <button class="btn btn-primary" onclick="refreshAll()">🔄 刷新</button>
            </div>
        </div>

        <!-- 添加媒体 -->
        <div class="card">
            <h2>添加媒体</h2>
            <div class="upload-area" onclick="document.getElementById('file-input').click()">
                <p>📁 点击或拖拽文件到此处上传</p>
                <p style="font-size:12px; color:#888;">支持: MP4, MKV, AVI, JPG, PNG, GIF (最大 50MB)</p>
                <input type="file" id="file-input" style="display:none" accept="video/*,image/*" onchange="uploadFile(this.files[0])">
            </div>
            <div style="margin-top: 16px;">
                <h3 style="margin-bottom: 12px; font-size: 14px; color: #aaa;">或添加网络地址</h3>
                <div class="add-url-form">
                    <input type="text" id="media-url" placeholder="输入 URL（视频流、图片、网页地址）">
                    <select id="media-type">
                        <option value="video">视频</option>
                        <option value="iptv">IPTV 直播</option>
                        <option value="stream">网络流</option>
                        <option value="webpage">网页</option>
                    </select>
                    <button class="btn btn-primary" onclick="addUrl()">+ 添加</button>
                </div>
            </div>
        </div>

        <!-- 播放列表 -->
        <div class="card">
            <h2>播放列表 <span id="playlist-count-inline"></span></h2>
            <div id="playlist-container">
                <div class="empty-state">
                    <div class="icon">📋</div>
                    <h3>播放列表为空</h3>
                    <p>上传媒体文件或添加 URL 来创建播放列表</p>
                </div>
            </div>
        </div>
    </div>

    <div id="toast" class="toast"></div>

    <script>
        const API_BASE = '';

        // 加载设备状态
        async function loadStatus() {
            try {
                const res = await fetch(API_BASE + '/api/status');
                const data = await res.json();
                document.getElementById('device-name').textContent = data.deviceName || 'ScreenPulse TV';
                document.getElementById('device-ip').textContent = data.ip + ':' + data.port;
                document.getElementById('device-ip-detail').textContent = data.ip + ':' + data.port;
                document.getElementById('playback-status').textContent = data.playbackState || '空闲';
                document.getElementById('playlist-count').textContent = data.playlistCount || 0;
            } catch(e) { console.error('加载状态失败', e); }
        }

        // 加载播放列表
        async function loadPlaylist() {
            try {
                const res = await fetch(API_BASE + '/api/playlist');
                const data = await res.json();
                renderPlaylist(data);
            } catch(e) { console.error('加载播放列表失败', e); }
        }

        // 渲染播放列表
        function renderPlaylist(items) {
            const container = document.getElementById('playlist-container');
            if (!items || items.length === 0) {
                container.innerHTML = '<div class="empty-state"><div class="icon">📋</div><h3>播放列表为空</h3></div>';
                return;
            }
            let html = '<table class="playlist-table"><thead><tr><th>序号</th><th>标题</th><th>类型</th><th>时长</th><th>音量</th><th>操作</th></tr></thead><tbody>';
            items.forEach((item, i) => {
                html += '<tr>';
                html += '<td>' + (i+1) + '</td>';
                html += '<td>' + escapeHtml(item.title || '未命名') + '</td>';
                html += '<td><span class="badge badge-' + item.type + '">' + item.type + '</span></td>';
                html += '<td>' + (item.duration ? item.duration + '秒' : '自动') + '</td>';
                html += '<td>' + item.volume + '%</td>';
                html += '<td class="actions-cell">';
                html += '<button class="icon-btn" onclick="toggleItem(' + item.id + ', ' + !item.enabled + ')" title="' + (item.enabled ? '禁用' : '启用') + '">' + (item.enabled ? '✓' : '✗') + '</button>';
                html += '<button class="icon-btn" onclick="moveItem(' + item.id + ', -1)" title="上移">↑</button>';
                html += '<button class="icon-btn" onclick="moveItem(' + item.id + ', 1)" title="下移">↓</button>';
                html += '<button class="icon-btn delete" onclick="deleteItem(' + item.id + ')" title="删除">✕</button>';
                html += '</td></tr>';
            });
            html += '</tbody></table>';
            container.innerHTML = html;
        }

        // 上传文件
        async function uploadFile(file) {
            if (!file) return;
            if (file.size > 50 * 1024 * 1024) { showToast('文件大小超过 50MB 限制'); return; }
            const formData = new FormData();
            formData.append('file', file);
            try {
                showToast('上传中...');
                const res = await fetch(API_BASE + '/api/media/upload', { method: 'POST', body: formData });
                if (res.ok) { showToast('上传成功！'); loadPlaylist(); loadStatus(); }
                else { showToast('上传失败: ' + res.statusText); }
            } catch(e) { showToast('上传失败: ' + e.message); }
        }

        // 添加 URL
        async function addUrl() {
            const url = document.getElementById('media-url').value.trim();
            const type = document.getElementById('media-type').value;
            if (!url) { showToast('请输入 URL'); return; }
            try {
                const res = await fetch(API_BASE + '/api/media/url', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ url, type, title: url.split('/').pop() || '未命名' })
                });
                if (res.ok) { showToast('添加成功！'); document.getElementById('media-url').value = ''; loadPlaylist(); loadStatus(); }
            } catch(e) { showToast('添加失败: ' + e.message); }
        }

        // 删除播放项
        async function deleteItem(id) {
            if (!confirm('确定删除此项？')) return;
            try {
                const res = await fetch(API_BASE + '/api/playlist/' + id, { method: 'DELETE' });
                if (res.ok) { showToast('已删除'); loadPlaylist(); loadStatus(); }
            } catch(e) { showToast('删除失败'); }
        }

        // 启用/禁用播放项
        async function toggleItem(id, enabled) {
            try {
                const res = await fetch(API_BASE + '/api/playlist/' + id + '/toggle', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ enabled: enabled })
                });
                if (res.ok) { showToast(enabled ? '已启用' : '已禁用'); loadPlaylist(); }
            } catch(e) { showToast('操作失败'); }
        }

        // 移动播放项
        async function moveItem(id, direction) {
            try {
                const res = await fetch(API_BASE + '/api/playlist/' + id + '/move', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ direction: direction })
                });
                if (res.ok) { loadPlaylist(); }
            } catch(e) { showToast('操作失败'); }
        }

        // 播放控制
        async function controlPlay() { await fetch(API_BASE + '/api/control/play', { method: 'POST' }); showToast('播放'); loadStatus(); }
        async function controlPause() { await fetch(API_BASE + '/api/control/pause', { method: 'POST' }); showToast('暂停'); loadStatus(); }
        async function controlSkip() { await fetch(API_BASE + '/api/control/skip', { method: 'POST' }); showToast('跳到下一项'); }
        async function controlPrevious() { await fetch(API_BASE + '/api/control/previous', { method: 'POST' }); showToast('跳到上一项'); }

        // Toast 提示
        function showToast(msg) {
            const toast = document.getElementById('toast');
            toast.textContent = msg;
            toast.classList.add('show');
            setTimeout(() => toast.classList.remove('show'), 2000);
        }

        function escapeHtml(str) {
            const div = document.createElement('div');
            div.textContent = str;
            return div.innerHTML;
        }

        // 刷新所有数据
        function refreshAll() { loadStatus(); loadPlaylist(); }

        // 自动刷新（每 5 秒）
        setInterval(refreshAll, 5000);

        // 初始加载
        loadStatus();
        loadPlaylist();
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
     */
    private fun getSettings(): NanoHTTPD.Response {
        val settings = mapOf(
            "playMode" to "loop",
            "transitionEffect" to "fade",
            "defaultImageDuration" to 10,
            "defaultWebpageDuration" to 30,
            "volume" to 100,
            "autoStartOnBoot" to true,
            "serverPort" to 8080
        )
        return okResponse(gson.toJson(settings))
    }

    /**
     * POST /api/settings - 更新设置
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

            okResponse(gson.toJson(mapOf("success" to true, "message" to "设置已更新")))
        } catch (e: Exception) {
            serverError("更新设置失败: ${e.message}")
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
