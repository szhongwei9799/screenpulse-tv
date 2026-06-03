---
Task ID: 1
Agent: main
Task: 仪表盘改为播放统计 + 修复上传失败

Work Log:
- 阅读完整项目代码：ApiRouter.kt, WebServer.kt, PlaylistManager.kt, PlaybackService.kt, MediaItem.kt, index.html
- 诊断上传失败根因：NanoHTTPD parseBody() 使用的 java.io.tmpdir 在 Android TV 上可能不可写
- 修复上传：动态设置 System.setProperty("java.io.tmpdir", app内部缓存目录)
- 修复文件名提取：支持多种参数名、中文文件名保留、上传后清理临时文件
- 在 PlaylistManager 中添加播放统计：playCounts（按媒体）、loopCount（循环次数）、totalPlayCount
- 添加 PlaybackStats 数据类
- 在 ApiRouter 中添加 GET /api/playback-stats 端点
- 在 WebServer 中注册新路由，添加 PlaylistManager 单例桥接
- 前端仪表盘：移除"设备状态 在线/离线"，替换为"当前播放"、"今日播放次数"、"循环次数"
- 添加"今日播放统计"面板（按媒体显示播放次数）
- 前端每5秒自动轮询播放统计
- 第一次推送编译失败：parseBody(files, tempDir) 多参数不兼容 + getPlaybackStats 缺少 return
- 修复后重新编译成功

Stage Summary:
- 修改文件：ApiRouter.kt, WebServer.kt, PlaylistManager.kt, index.html (assets + web-admin)
- GitHub Actions 编译成功：run 26871867024
- APK 下载：https://github.com/szhongwei9799/screenpulse-tv/actions/runs/26871867024
