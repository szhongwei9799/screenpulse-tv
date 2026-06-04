---
Task ID: 1
Agent: Main Agent
Task: ScreenPulse TV v1.1.0 - 移除TTS，新增仪表盘，修复API

Work Log:
- 克隆仓库，分析代码结构（双模块 app/ + android/，用户使用 app/ 模块）
- 发现仓库已提交所有文件（eaa91aa merge commit），无未提交变更
- 全面分析 app/ 模块：WebServer.kt, ApiRouter.kt, web-admin/index.html
- 确认前端已有：分组标签、在线资源、定时播放、背景音乐、设置页面
- 用户要求"先取消这个功能"，确认为移除 TTS
- 移除前端 TTS 卡片和 JS 代码（约 50 行 HTML + 70 行 JS）
- 移除后端 TTS API（4个路由方法 + TtsEngine/TtsAudioDao 引用）
- 新增仪表盘页面（设备信息+播放统计 grid layout）
- 新增 Schedule CRUD API（SharedPreferences 临时存储）
- 修复 getMediaList/triggerScan 返回 groupIds
- 更新版本 v1.1.0 (versionCode 8)
- 提交并推送到 GitHub

Stage Summary:
- Commit 4b04a04 pushed to main
- 4 files changed: 144 insertions, 164 deletions
- TTS 完全移除，仪表盘已添加，Schedule API 已修复
- 前端侧边栏：仪表盘 → 播放列表 → 媒体库 → 定时播放 → 背景音乐 → 设置
