# ScreenPulse TV — 更新日志

所有项目的重要变更均记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本管理遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [1.0.0] — 2026-06-01

### 🎉 首次正式发布

ScreenPulse TV v1.0.0 是一个功能完整的 Android TV 数字标牌轮播播放器，
包含以下核心功能：

#### ✨ 新增功能

**播放引擎**
- 基于 ExoPlayer (Media3) 的视频播放引擎
- 支持多种视频格式：MP4、MKV、AVI、WebM
- 支持 HLS (m3u8) 和 DASH (mpd) 流媒体协议
- 支持 RTSP 和 RTMP 网络流
- 图片轮播支持 JPG、PNG、GIF、WebP 格式
- 使用 Glide 高效加载图片，内置磁盘/内存缓存
- 网页/PPT 在线展示，基于 WebView
- 三种播放模式：循环、顺序、随机
- 每个播放项独立的时长和音量控制
- 播放项启用/禁用开关
- 自动播放下一项，视频播完自动切换

**Web 管理后台**
- 内置嵌入式 Web 服务器（NanoHTTPD）
- 完整的单页应用管理面板（纯 HTML/CSS/JS）
- 响应式设计，适配手机和电脑浏览器
- 设备信息实时展示
- 文件拖拽上传（最大 50MB）
- 通过 URL 添加网络媒体资源
- 播放列表可视化管理（排序、启用/禁用、删除）
- 远程播放控制（播放、暂停、上一项、下一项）
- 自动刷新（5 秒轮询）

**REST API**
- 完整的 RESTful API 接口（20 个端点）
- 设备状态查询 API
- 播放列表 CRUD API
- 媒体库管理 API（上传/URL 添加/删除）
- 定时任务管理 API
- 播放远程控制 API
- 系统设置 API
- JSON 数据格式
- Multipart 文件上传

**定时插播**
- 定时任务调度系统（AlarmManager + WorkManager）
- 支持简单的 cron-like 表达式
- 定时中断当前播放，插入特定内容
- 插播完成后自动恢复之前的播放位置
- 支持一次性任务和重复任务
- 开机后自动恢复所有定时任务

**数据管理**
- Room 数据库持久化存储
- 播放列表表 (playlist)
- 媒体库表 (media)
- 定时任务表 (schedule)
- 双重检查锁定线程安全单例
- 协程异步数据库操作

**首次启动体验**
- 启动时显示管理地址二维码
- 使用 ZXing 生成二维码
- 显示设备名称和 IP 地址
- 引导用户通过浏览器管理

**系统集成**
- 开机自启动（BroadcastReceiver）
- 前台服务保持播放不中断
- Wake Lock 防止休眠
- Android TV Leanback 启动器集成
- 多种 Intent Filter（媒体文件打开、深度链接）

#### 🏗️ 架构

- Kotlin 主开发语言
- MVVM 架构 + 分层设计
- ViewModel 生命周期感知
- Kotlin Coroutines 异步处理
- Navigation Component 页面导航
- DataStore 替代 SharedPreferences

#### 📦 依赖版本

| 依赖 | 版本 |
|------|------|
| Kotlin | 1.9.0 |
| Android Gradle Plugin | 8.2.x |
| compileSdk | 34 |
| minSdk | 24 |
| targetSdk | 34 |
| ExoPlayer (Media3) | 1.2.1 |
| Room | 2.6.1 |
| NanoHTTPD | 2.3.1 |
| Glide | 4.16.0 |
| Coroutines | 1.7.3 |
| Gson | 2.10.1 |
| OkHttp | 4.12.0 |
| ZXing | 3.5.3 |
| WorkManager | 2.9.0 |
| Navigation | 2.7.7 |
| DataStore | 1.0.0 |

#### 📱 兼容设备

- Android TV Box（如 Xiaomi Mi Box、NVIDIA Shield TV）
- Android 智能电视（内置 Android TV 系统）
- Android 迷你主机
- 任何运行 Android 7.0+ (API 24) 的电视设备

#### 📄 文档

- README.md — 项目总览与快速开始
- docs/API.md — 完整 REST API 文档
- docs/USAGE.md — 用户使用指南
- docs/DESIGN.md — 技术设计文档
- docs/CHANGELOG.md — 本文件

---

## 版本规划

### [1.1.0] — 计划中

- 🔐 可选管理密码认证
- 🔒 HTTPS 支持（自签名证书）
- 📋 IP 白名单功能
- ⏱️ API 速率限制
- 🐛 初始版本 Bug 修复

### [1.2.0] — 计划中

- 📊 多设备分组管理
- 📡 设备状态监控面板
- 📤 批量推送播放列表
- 🔄 OTA 更新机制

### [1.3.0] — 计划中

- 🖼️ 画中画 (PIP) 模式
- 📐 多分屏布局
- 💬 字幕支持 (SRT/ASS)
- 🎵 背景音乐独立播放
- 📈 播放统计与报表

### [2.0.0] — 远期规划

- ☁️ 云端管理后台（SaaS）
- 📋 行业模板系统
- 🔗 第三方集成（天气、新闻、汇率）
- 🌍 多语言支持
- 🔌 开放 API（供第三方系统对接）

---

> 📖 API 文档请参阅 [API.md](./API.md)  
> 📝 使用指南请参阅 [USAGE.md](./DESIGN.md)  
> 📐 技术设计请参阅 [DESIGN.md](./DESIGN.md)
