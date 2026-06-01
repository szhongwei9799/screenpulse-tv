# ScreenPulse TV — 技术设计文档

> **版本**: v1.0.0  
> **最后更新**: 2026-06-01

---

## 📋 目录

- [系统架构概述](#1-系统架构概述)
- [模块设计](#2-模块设计)
  - [播放引擎 (PlaybackEngine)](#21-播放引擎-playbackengine)
  - [Web 服务器 (WebServerManager)](#22-web-服务器-webservermanager)
  - [数据库设计 (Room)](#23-数据库设计-room)
  - [定时系统 (ScheduleManager)](#24-定时系统-schedulemanager)
  - [媒体类型支持矩阵](#25-媒体类型支持矩阵)
- [数据流图](#3-数据流图)
- [技术选型与理由](#4-技术选型与理由)
- [安全考量](#5-安全考量)
- [性能优化](#6-性能优化)
- [未来路线图](#7-未来路线图)

---

## 1. 系统架构概述

ScreenPulse TV 采用**分层架构**设计，核心分为四大子系统：

```
┌─────────────────────────────────────────────────────────────────────┐
│                        表现层 (Presentation)                        │
│  ┌────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │
│  │ PlaybackFrag  │  │  LandingFrag     │  │  SettingsFrag       │  │
│  │ (播放界面)    │  │ (首次启动/二维码)│  │  (设置界面)         │  │
│  └───────┬────────┘  └─────────────────┘  └─────────────────────┘  │
│          │                                                        │
│  ┌───────┴──────────────────────────────────────────────────────┐  │
│  │                    ViewModel (PlaybackViewModel)             │  │
│  │                    状态管理、生命周期感知                      │  │
│  └───────┬──────────────────────────────────────────────────────┘  │
├──────────┼──────────────────────────────────────────────────────────┤
│          │                    业务逻辑层                             │
│  ┌───────┴────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │ PlaybackEngine │  │PlaylistManager│  │  ScheduleManager      │  │
│  │ 播放控制核心   │  │ 播放列表管理  │  │  定时任务调度         │  │
│  └───────┬────────┘  └──────┬───────┘  └───────────┬───────────┘  │
├──────────┼──────────────────┼─────────────────────┼───────────────┤
│          │             数据持久层                    │               │
│          │        ┌────────┴─────────┐             │               │
│  ┌───────┴────────┴──┐   Room DB   ┌─┴─────────────┴───────────┐  │
│  │    AppDatabase     │  (SQLite)   │    DataStore             │  │
│  │  playlist / media   │             │    (应用设置)             │  │
│  │  / schedule tables  │             │                          │  │
│  └─────────────────────┘             └──────────────────────────┘  │
├──────────────────────────────────────────────────────────────────────┤
│                        基础设施层                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │ WebServer    │  │  ExoPlayer   │  │  Glide       │             │
│  │ (NanoHTTPD)  │  │  (Media3)    │  │  (图片加载)  │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└─────────────────────────────────────────────────────────────────────┘
```

### 架构原则

| 原则 | 实践 |
|------|------|
| **单一职责** | 每个模块只负责一个核心功能 |
| **关注点分离** | 播放、管理、调度各自独立 |
| **依赖倒置** | 上层模块依赖抽象接口（回调/LiveData） |
| **响应式** | 使用协程 + LiveData 实现异步响应 |
| **可测试性** | 核心逻辑与 Android 框架解耦 |

---

## 2. 模块设计

### 2.1 播放引擎 (PlaybackEngine)

`com.screenpulse.tv.player.PlaybackEngine`

播放引擎是整个应用的核心，负责所有类型媒体的播放控制和切换逻辑。

#### 类结构

```
PlaybackEngine
├── 属性
│   ├── exoPlayer: ExoPlayer           // 视频播放器实例
│   ├── imageView: ImageView?          // 图片显示视图
│   ├── webView: WebView?              // 网页显示视图
│   ├── containerView: ViewGroup?     // 容器视图
│   ├── mainHandler: Handler           // 主线程定时器
│   ├── engineScope: CoroutineScope    // 协程作用域
│   ├── currentState: PlaybackState   // 当前状态
│   └── currentMediaItem: PlaylistEntity?  // 当前播放项
│
├── 方法
│   ├── attachViews(container)         // 绑定视图
│   ├── start()                        // 开始播放
│   ├── playItem(item)                 // 播放指定项
│   ├── playVideo(item)                // 播放视频/直播/流
│   ├── playImage(item)               // 播放图片
│   ├── playWebpage(item)             // 播放网页
│   ├── playNext()                     // 播放下一项
│   ├── playPrevious()                 // 播放上一项
│   ├── pause() / resume()            // 暂停/恢复
│   ├── stop()                         // 停止
│   ├── jumpTo(index)                  // 跳转到指定索引
│   ├── insertScheduledContent()       // 插入定时内容
│   └── release()                       // 释放资源
│
└── 回调
    ├── onStateChanged                 // 状态变化回调
    └── onError                        // 错误回调
```

#### 状态机

```
                    ┌──────────────┐
                    │              │
        ┌──────────┤    IDLE      ├──────────┐
        │          │   (空闲)     │          │
        │          └──────┬───────┘          │
        │                 │                  │
        │          start()│                  │ stop()
        │                 ▼                  │
        │          ┌──────────────┐         │
        │  error   │              │         │
        ├──────────┤  PREPARING   ├─────────┤
        │          │  (准备中)    │         │
        │          └──────┬───────┘         │
        │                 │                  │
        │          ready()│                  │
        │                 ▼                  │
        │          ┌──────────────┐         │
        │  ┌───────┤              ├─────────┤
        │  │pause()│   PLAYING    │         │
        │  │       │  (播放中)    │         │
        │  │       └──────────────┘         │
        │  │                                 │
        │  ▼                                 │
        │  ┌──────────────┐                  │
        └──┤   PAUSED     │──────────────────┘
        resume()  (暂停)
           └──────────────┘
```

#### 播放流程

```
start()
  │
  ▼
加载播放列表 (PlaylistManager)
  │
  ▼
playItem(item)
  │
  ├── 判断 enabled 状态
  │     └── 禁用 → playNext()
  │
  ├── 取消当前定时器
  │
  ├── 隐藏所有视图
  │
  ├── 根据 MediaType 分发
  │     ├── VIDEO/IPTV/STREAM → playVideo()
  │     │     └── ExoPlayer.setMediaItem() → prepare()
  │     │
  │     ├── IMAGE → playImage()
  │     │     └── Glide.load() → 设置定时器
  │     │
  │     └── WEBPAGE → playWebpage()
  │           └── WebView.loadUrl() → 设置定时器
  │
  └── 通知状态变化 (onStateChanged)
        │
        ▼
  onItemComplete()
        │
        ▼
    playNext() (根据 PlayMode 决定下一项)
```

---

### 2.2 Web 服务器 (WebServerManager)

`com.screenpulse.tv.server.WebServerManager`

基于 NanoHTTPD 的嵌入式 Web 服务器，运行在 Android TV 设备上。

#### 架构设计

```
WebServerManager (管理器)
  │
  ├── server: NanoHTTPD           // HTTP 服务器实例
  ├── apiHandler: ApiHandler      // API 处理器
  ├── serverScope: CoroutineScope // 异步操作协程
  │
  ├── start(port)                  // 启动服务器
  ├── stop()                       // 停止服务器
  ├── restart()                    // 重启服务器
  ├── getServerUrl()              // 获取服务器 URL
  └── getManagementUrl()          // 获取管理面板 URL

ApiHandler (请求路由器)
  │
  ├── handleRequest(session)      // 路由分发
  │
  ├── Web 面板路由
  │     ├── GET /                  → serveAdminPanel()
  │     └── GET /admin             → serveAdminPanel()
  │
  ├── 状态 API
  │     └── GET /api/status        → getStatus()
  │
  ├── 播放列表 API
  │     ├── GET  /api/playlist     → getPlaylist()
  │     ├── POST /api/playlist     → updatePlaylist()
  │     ├── POST /api/playlist/reorder → reorderPlaylist()
  │     └── DELETE /api/playlist/{id} → deletePlaylistItem()
  │
  ├── 媒体库 API
  │     ├── GET  /api/media        → getMediaLibrary()
  │     ├── POST /api/media/upload → handleFileUpload()
  │     ├── POST /api/media/url    → addMediaUrl()
  │     └── DELETE /api/media/{id} → deleteMedia()
  │
  ├── 定时任务 API
  │     ├── GET  /api/schedule     → getSchedules()
  │     ├── POST /api/schedule     → createSchedule()
  │     └── DELETE /api/schedule/{id} → deleteSchedule()
  │
  ├── 播放控制 API
  │     ├── POST /api/control/play     → controlPlay()
  │     ├── POST /api/control/pause    → controlPause()
  │     ├── POST /api/control/skip     → controlSkip()
  │     └── POST /api/control/previous  → controlPrevious()
  │
  ├── 设置 API
  │     ├── GET  /api/settings    → getSettings()
  │     └── POST /api/settings    → updateSettings()
  │
  └── 静态文件
        └── GET /media/{filename}  → serveMediaFile()
```

#### 请求处理流程

```
客户端请求
    │
    ▼
NanoHTTPD.serve(session)
    │
    ▼
ApiHandler.handleRequest(session)
    │
    ├── 添加 CORS 头
    │
    ├── URL 匹配 & 方法检查
    │
    ├── 解析请求体 (JSON / Multipart)
    │
    ├── 业务逻辑处理
    │     ├── 读取/写入 Room 数据库
    │     ├── 调用 PlaylistManager
    │     ├── 调用 ScheduleManager
    │     └── 触发 PlaybackEngine 回调
    │
    └── 返回 JSON 响应
```

---

### 2.3 数据库设计 (Room)

`com.screenpulse.tv.db.AppDatabase`

使用 Room ORM 管理 SQLite 数据库，版本 1，包含 3 张表。

#### ER 图

```
┌────────────────────────┐       ┌────────────────────────┐
│       media            │       │      playlist           │
│────────────────────────│       │────────────────────────│
│ id: Long (PK, AUTO)    │       │ id: Long (PK, AUTO)    │
│ title: String          │◄──┐   │ title: String          │
│ type: String          │   │   │ type: String           │
│ url: String           │   │   │ url: String            │
│ file_path: String?    │   │   │ duration: Long?         │
│ file_size: Long       │   │   │ enabled: Boolean       │
│ mime_type: String?     │   │   │ volume: Int            │
│ thumbnail_url: String? │   │   │ play_order: Int        │
│ thumbnail_path: String?│   │   │ created_at: Long       │
│ width: Int?           │   │   └───────────┬────────────┘
│ height: Int?          │   │               │
│ duration: Long?       │   │   ┌───────────┴────────────┐
│ created_at: Long      │   │   │      schedule          │
└────────────────────────┘   │   │────────────────────────│
                             │   │ id: Long (PK, AUTO)    │
                             │   │ name: String           │
                             │   │ cron: String           │
                             └───│ content_json: String?  │
                                 │ repeat: Boolean        │
                                 │ completed: Boolean     │
                                 │ enabled: Boolean       │
                                 │ priority: Int          │
                                 │ last_executed_at: Long?│
                                 │ next_trigger_at: Long? │
                                 │ created_at: Long       │
                                 │ updated_at: Long       │
                                 └────────────────────────┘
```

#### 表结构详情

##### `playlist` 表 — 播放列表

存储当前播放列表中的每一项，包括播放参数。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, AUTO INCREMENT | 主键 |
| `title` | String | NOT NULL | 媒体标题 |
| `type` | String | NOT NULL | 媒体类型 (video/image/iptv/stream/webpage) |
| `url` | String | NOT NULL | 媒体 URL 或本地路径 |
| `duration` | Long | NULLABLE | 显示时长（秒），null=自动 |
| `enabled` | Boolean | DEFAULT true | 是否启用 |
| `volume` | Int | DEFAULT 100 | 音量 0-100 |
| `play_order` | Int | DEFAULT 0 | 播放顺序 |
| `created_at` | Long | NOT NULL | 创建时间戳 |

**索引**: `play_order` (ASC) — 用于播放顺序查询

##### `media` 表 — 媒体库

存储所有可用的媒体资源，包括上传的本地文件和添加的网络 URL。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, AUTO INCREMENT | 主键 |
| `title` | String | NOT NULL | 文件名/标题 |
| `type` | String | NOT NULL | 媒体类型 |
| `url` | String | NOT NULL | 访问路径 |
| `file_path` | String | NULLABLE | 本地文件绝对路径 |
| `file_size` | Long | DEFAULT 0 | 文件大小（字节） |
| `mime_type` | String | NULLABLE | MIME 类型 |
| `thumbnail_url` | String | NULLABLE | 缩略图 URL |
| `thumbnail_path` | String | NULLABLE | 缩略图本地路径 |
| `width` | Int | NULLABLE | 宽度（像素） |
| `height` | Int | NULLABLE | 高度（像素） |
| `duration` | Long | NULLABLE | 时长（秒，仅视频） |
| `created_at` | Long | NOT NULL | 创建时间戳 |

##### `schedule` 表 — 定时任务

存储定时播放任务的配置。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, AUTO INCREMENT | 主键 |
| `name` | String | NOT NULL | 任务名称 |
| `cron` | String | NOT NULL | 定时表达式 |
| `content_json` | String | NULLABLE | 插播内容 JSON |
| `repeat` | Boolean | DEFAULT true | 是否重复 |
| `completed` | Boolean | DEFAULT false | 是否已完成 |
| `enabled` | Boolean | DEFAULT true | 是否启用 |
| `priority` | Int | DEFAULT 0 | 优先级 |
| `last_executed_at` | Long | NULLABLE | 最后执行时间 |
| `next_trigger_at` | Long | NULLABLE | 下次触发时间 |
| `created_at` | Long | NOT NULL | 创建时间戳 |
| `updated_at` | Long | NOT NULL | 更新时间戳 |

#### DAO 接口

```
PlaylistDao
├── getActivePlaylistItems(): List<PlaylistEntity>  // 获取启用的播放项（按顺序）
├── getActivePlaylistCount(): Int                   // 启用项计数
├── getAll(): List<PlaylistEntity>                   // 获取所有项
├── insert(item): Long                               // 插入
├── update(item)                                     // 更新
├── deleteById(id)                                  // 按 ID 删除
├── deleteAll()                                     // 清空
└── updateOrder(id, order)                          // 更新排序

MediaDao
├── getAll(): List<MediaEntity>                      // 获取所有媒体
├── getById(id): MediaEntity?                        // 按 ID 查询
├── insert(media): Long                              // 插入
├── deleteById(id)                                   // 按 ID 删除

ScheduleDao
├── getAll(): List<ScheduleEntity>                    // 获取所有任务
├── getActive(): List<ScheduleEntity>                 // 获取启用任务
├── getById(id): ScheduleEntity?                     // 按 ID 查询
├── insert(schedule): Long                           // 插入
├── update(schedule)                                  // 更新
├── deleteById(id)                                    // 按 ID 删除
└── updateCompleted(id, completed)                    // 更新完成状态
```

---

### 2.4 定时系统 (ScheduleManager)

`com.screenpulse.tv.schedule.ScheduleManager`

定时系统负责在指定时间触发内容插播。

#### 调度架构

```
┌─────────────────────────────────────────────────────┐
│                  ScheduleManager                    │
│                                                      │
│  ┌───────────────┐   ┌─────────────────────────┐   │
│  │  AlarmManager │   │  WorkManager              │   │
│  │  (精确定时)    │   │  (后台保活/可靠调度)      │   │
│  └───────┬───────┘   └──────────┬──────────────┘   │
│          │                       │                   │
│          └───────────┬───────────┘                   │
│                      │                               │
│              triggerSchedule()                        │
│                      │                               │
│                      ▼                               │
│          ┌───────────────────────┐                   │
│          │  解析 contentJson     │                   │
│          │  → List<PlaylistEntity>                  │
│          └───────────┬───────────┘                   │
│                      │                               │
│                      ▼                               │
│          ┌───────────────────────┐                   │
│          │ PlaybackEngine        │                   │
│          │ .insertScheduledContent│                   │
│          └───────────────────────┘                   │
│                                                      │
└─────────────────────────────────────────────────────┘
```

#### Cron 表达式解析

```
输入表达式 → 类型判断 → 计算下次触发时间

┌───────────────────────────────────────────┐
│         Cron 表达式类型判断                 │
├───────────────────────────────────────────┤
│                                           │
│  ┌─────────┐  "1715000000"               │
│  │ 绝对时间 │  (10-13位数字)               │
│  └─────────┘  → Unix 时间戳 × 1000       │
│                                           │
│  ┌─────────┐  "HH:mm"                     │
│  │ 每天    │  如 "09:30"                  │
│  └─────────┘  → 今天/明天 HH:mm          │
│                                           │
│  ┌─────────┐  "d HH:mm"                   │
│  │ 每周    │  如 "1 09:00"               │
│  └─────────┘  → 下个目标日 HH:mm          │
│                                           │
│  ┌─────────┐  "HH:mm HH:mm"              │
│  │ 时间范围 │  如 "09:00 18:00"           │
│  └─────────┘  → 开始时间 (每天)            │
│                                           │
└───────────────────────────────────────────┘
```

#### 插播流程

```
定时触发
    │
    ▼
保存当前播放状态
(currentIndex, currentPlaylist)
    │
    ▼
播放定时内容列表
    │
    ├── playScheduledItems(items, 0)
    │     │
    │     ├── playItem(items[0])
    │     │     └── playVideo / playImage / playWebpage
    │     │
    │     ├── 定时器到期 → playScheduledItems(items, 1)
    │     │     ├── playItem(items[1])
    │     │     └── ...
    │     │
    │     └── index >= items.length
    │           │
    │           ▼
    │     恢复之前播放状态
    │     (preInterruptPlaylist, preInterruptIndex)
    │
    └── 完成
```

---

### 2.5 媒体类型支持矩阵

| 类型 | 枚举值 | 播放器 | 支持格式 | 时长控制 |
|------|--------|--------|----------|----------|
| 视频 | `VIDEO` | ExoPlayer | MP4, MKV, AVI, WebM | 自动（视频时长）或手动设定 |
| IPTV 直播 | `IPTV` | ExoPlayer | HLS (m3u8), DASH (mpd) | 需手动设定（建议 300s） |
| 网络流 | `STREAM` | ExoPlayer | RTSP, RTMP | 需手动设定 |
| 图片 | `IMAGE` | Glide + ImageView | JPG, PNG, GIF, WebP | 默认 10 秒 |
| 网页 | `WEBPAGE` | WebView | HTML, HTTPS URL | 默认 30 秒 |

```
┌─────────────────────────────────────────────────────────┐
│              媒体类型 → 播放器映射                          │
│                                                          │
│  VIDEO    ──┐                                            │
│  IPTV     ──┼──► ExoPlayer (MediaItem.fromUri)          │
│  STREAM   ──┘                                            │
│                                                          │
│  IMAGE    ─────► Glide → ImageView                       │
│                                                          │
│  WEBPAGE  ─────► WebView.loadUrl()                      │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 数据流图

### 完整数据流

```
管理员操作                    ScreenPulse TV 系统
──────────                    ─────────────────

[上传文件] ──POST /api/media/upload──► ApiHandler
                                          │
[添加 URL] ──POST /api/media/url────►    │ 解析请求
                                          ▼
[更新列表] ──POST /api/playlist──────► Room Database
                                          │
[设置定时] ──POST /api/schedule──────►    │ 写入数据
                                          ▼
[播放控制] ──POST /api/control/play──► PlaybackEngine
                                          │
[更新设置] ──POST /api/settings──────►    │ 状态驱动
                                          ▼
                                      ┌───────────────┐
                                      │  视频播放      │
                                      │  ExoPlayer     │
                                      │  PlayerView    │
                                      ├───────────────┤
                                      │  图片展示      │
                                      │  Glide         │
                                      │  ImageView     │
                                      ├───────────────┤
                                      │  网页展示      │
                                      │  WebView       │
                                      └───────────────┘
                                           │
                                           ▼
                                      HDMI 输出到大屏
```

### 媒体文件上传流程

```
客户端                         Android TV 设备
──────                         ──────────────

浏览器选择文件
       │
       ▼
POST /api/media/upload
(multipart/form-data)
       │
       └──────────────────────────► NanoHTTPD 接收
                                       │
                                       ▼
                                  parseBody() 解析
                                       │
                                       ▼
                                  检查文件大小 ≤ 50MB
                                       │
                                       ▼
                                  保存到 filesDir/screenpulse_media/
                                       │
                                       ▼
                                  写入 media 表
                                       │
                                       ▼
                                  自动添加到 playlist 表
                                       │
                                       ▼
                                  返回成功响应 ◄───────
       │
       ▼
  Web 面板刷新播放列表
```

---

## 4. 技术选型与理由

| 技术 | 选择 | 理由 |
|------|------|------|
| **视频播放** | ExoPlayer (Media3) | Google 官方推荐，支持 HLS/DASH/RTSP，硬件加速，扩展性强，社区活跃 |
| **图片加载** | Glide | 内存缓存 + 磁盘缓存，GIF 支持，生命周期感知，性能优秀 |
| **Web 服务器** | NanoHTTPD | 轻量级（~100KB），无需额外依赖，适合嵌入式场景，API 简洁 |
| **数据库** | Room | Google 官方 ORM，编译时 SQL 验证，协程支持，迁移管理完善 |
| **异步** | Kotlin Coroutines | 官方推荐，结构化并发，取消支持，比 RxJava 更轻量 |
| **定时** | AlarmManager + WorkManager | AlarmManager 提供精确定时，WorkManager 保证可靠执行和开机恢复 |
| **JSON** | Gson | 轻量级，API 简洁，Google 维护，性能足够 |
| **网络** | OkHttp | 高性能 HTTP 客户端，连接池复用，拦截器机制灵活 |
| **二维码** | ZXing | 成熟稳定的条码/二维码库，支持多种格式 |
| **设置存储** | DataStore | SharedPreferences 的现代替代，协程支持，类型安全 |
| **导航** | Navigation Component | 官方推荐，Safe Args 类型安全传参，与 ViewModel 集成好 |

### 未选择的方案及原因

| 方案 | 未选择原因 |
|------|-----------|
| VLC for Android | 体积过大（~30MB），集成复杂，过于重量级 |
| IjkPlayer | B站开源但维护不活跃，兼容性问题多 |
| Ktor Server | 依赖 Kotlinx.coroutines 和多平台，增加包体积 |
| Retrofit | 对于嵌入式服务器场景，直接 OkHttp 更灵活 |
| SQLDelight | 学习曲线较陡，Room 与 Android 生态集成更好 |
| RxJava | Coroutines 已足够，避免引入额外复杂度 |

---

## 5. 安全考量

### 当前安全措施

| 措施 | 说明 |
|------|------|
| 网络安全配置 | `network_security_config.xml` 配置明文流量策略 |
| 文件上传限制 | 单文件最大 50MB，防止存储耗尽 |
| 线程安全 | 数据库单例 + 双重检查锁定，Volatile 变量 |
| 输入验证 | URL、ID 参数有效性校验 |

### 安全风险与建议

#### 🔴 高风险

| 风险 | 说明 | 建议 |
|------|------|------|
| 无认证 | 任何人可访问管理后台 | 添加可选密码认证（Token/Basic Auth） |
| HTTP 明文传输 | 密码和数据未加密 | 局域网内可接受；生产环境建议 HTTPS |
| 文件上传漏洞 | 可上传任意文件类型 | 添加文件类型白名单和内容校验 |

#### 🟡 中等风险

| 风险 | 说明 | 建议 |
|------|------|------|
| CORS 未限制 | 当前未限制来源 | 添加 Origin 白名单 |
| SSRF 风险 | URL 添加功能可访问内网 | 添加 URL 域名/协议白名单 |
| 路径遍历 | 媒体文件服务可能被利用 | 校验文件名，禁止 `..` 路径 |

#### 🟢 低风险

| 风险 | 说明 | 建议 |
|------|------|------|
| 本地数据 | 数据库文件可被 root 用户读取 | 可加密敏感字段 |
| 日志泄露 | 日志中可能包含敏感信息 | 生产版本关闭详细日志 |

### 未来安全增强

- [ ] 可选管理密码认证（Digest Auth）
- [ ] HTTPS 支持（自签名证书）
- [ ] API Token 机制
- [ ] IP 白名单
- [ ] 速率限制（防暴力操作）
- [ ] 文件上传 MIME 类型校验

---

## 6. 性能优化

### 播放性能

| 优化项 | 实现方式 |
|--------|----------|
| 硬件加速 | ExoPlayer 默认使用硬件解码 |
| 预加载 | 视频播放前 ExoPlayer 自动缓冲 |
| 内存缓存 | Glide 磁盘缓存 + 内存缓存图片 |
| 视图复用 | PlayerView / ImageView / WebView 常驻，切换时仅控制 visibility |
| 避免重复创建 | ExoPlayer 使用 lazy 延迟初始化单例 |

### 网络性能

| 优化项 | 实现方式 |
|--------|----------|
| 连接复用 | OkHttp 连接池 |
| 响应压缩 | NanoHTTPD 可配置 GZIP |
| 静态文件缓存 | 媒体文件通过 MIME 正确响应头缓存 |
| 大文件分块 | NanoHTTPD 使用 Chunked Response |

### 数据库性能

| 优化项 | 实现方式 |
|--------|----------|
| 单例模式 | AppDatabase 双重检查锁定单例 |
| 索引 | playlist 表 play_order 索引 |
| 异步 IO | 所有数据库操作在 IO 协程中执行 |
| 批量操作 | replacePlaylist() 使用事务批量写入 |

### 内存管理

| 优化项 | 实现方式 |
|--------|----------|
| 视图释放 | release() 方法清理所有引用 |
| 协程取消 | engineScope.cancel() 取消所有协程 |
| 图片回收 | Glide 生命周期感知自动回收 |
| 限制上传 | 50MB 文件大小限制防止 OOM |

---

## 7. 未来路线图

### v1.1.0 — 安全增强

- [ ] 管理密码认证
- [ ] HTTPS 支持
- [ ] IP 白名单
- [ ] API 速率限制

### v1.2.0 — 多设备管理

- [ ] 多设备分组管理
- [ ] 设备状态监控面板
- [ ] 批量推送播放列表
- [ ] OTA 更新机制

### v1.3.0 — 高级播放功能

- [ ] 画中画 (PIP) 模式
- [ ] 多分屏布局
- [ ] 字幕支持 (SRT/ASS)
- [ ] 背景音乐独立播放
- [ ] 播放统计与报表

### v2.0.0 — 平台化

- [ ] 云端管理后台（SaaS）
- [ ] 模板系统（行业模板一键应用）
- [ ] 第三方集成（天气、新闻、汇率）
- [ ] 多语言支持
- [ ] 开放 API（供第三方系统对接）

---

> 📖 API 文档请参阅 [API.md](./API.md)  
> 📝 使用指南请参阅 [USAGE.md](./USAGE.md)  
> 📋 更新日志请参阅 [CHANGELOG.md](./CHANGELOG.md)
