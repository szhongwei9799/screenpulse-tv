<p align="center">
  <img src="docs/assets/banner.png" alt="ScreenPulse TV Banner" width="800"/>
</p>

<h1 align="center">📺 ScreenPulse TV</h1>

<p align="center">
  <strong>Android TV 数字标牌轮播播放器</strong><br/>
  开源、轻量、功能丰富的智能播放器解决方案
</p>

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-1.9.0-purple?logo=kotlin&logoColor=white" alt="Kotlin"/></a>
  <a href="https://developer.android.com/studio"><img src="https://img.shields.io/badge/Android%20TV-API%2024+-green?logo=android&logoColor=white" alt="Android TV"/></a>
  <a href="https://exoplayer.dev/"><img src="https://img.shields.io/badge/ExoPlayer-Media3%201.2.1-blue?logo=google&logoColor=white" alt="ExoPlayer"/></a>
  <a href="https://github.com/NanoHttpd/nanohttpd"><img src="https://img.shields.io/badge/NanoHTTPD-2.3.1-orange?logo=web&logoColor=white" alt="NanoHTTPD"/></a>
  <br/>
  <a href="https://developer.android.com/topic/libraries/architecture/room"><img src="https://img.shields.io/badge/Room-2.6.1-green" alt="Room"/></a>
  <a href="https://developer.android.com/kotlin/coroutines"><img src="https://img.shields.io/badge/Coroutines-1.7.3-blue" alt="Coroutines"/></a>
  <a href="https://github.com/nicklockwood/FX"><img src="https://img.shields.io/badge/License-MIT-yellow" alt="License"/></a>
  <a href="https://github.com/nicklockwood/FX"><img src="https://img.shields.io/badge/Version-1.0.0-brightgreen" alt="Version"/>
  <img src="https://img.shields.io/badge/minSdk-24+-informational" alt="Min SDK"/>
</p>

---
# 这是一个完全由AI人工智障生成的测试项目，请不必当真

## 📖 项目简介

**ScreenPulse TV** 是一款专为 Android TV 设计的**数字标牌轮播播放器**，适用于商场、酒店、学校、会议室、展览展示等场景。设备通过局域网内的 Web 管理后台即可完成全部配置，无需外接键盘鼠标，真正做到**零接触部署**。

只需将安卓电视盒子接上显示器或大屏电视，打开 ScreenPulse TV，扫描屏幕上的二维码即可在手机或电脑上管理播放内容。

### ✨ 核心特性

- 🎬 **多格式视频播放** — 支持 MP4、MKV、AVI、HLS (m3u8)、DASH、RTSP、RTMP 等几乎所有视频格式
- 📡 **IPTV 直播源** — 支持 M3U/M3U8 直播源列表，直接播放网络电视频道
- 🖼️ **图片轮播** — 支持 JPG/PNG/GIF/WebP，内置淡入淡出过渡动画
- 🌐 **网页/PPT 展示** — 支持在线网页和文档的实时展示，定时切换
- 🖥️ **内置 Web 管理后台** — 无需安装任何客户端，浏览器即可管理，响应式设计适配手机
- 📋 **播放列表管理** — 每项可独立设置时长、音量、启用/禁用，支持拖拽排序
- ⏰ **定时插播功能** — 在指定时间自动中断当前播放，插入紧急通知或广告，播完自动恢复
- 🔁 **多种播放模式** — 循环播放、顺序播放、随机播放
- 📱 **首次启动二维码** — 首次启动自动在屏幕上显示管理地址二维码，手机扫码即可开始
- 🚀 **开机自启动** — 设备上电即自动启动播放，适合无人值守场景

---

## 🏗️ 系统架构

```
┌───────────────────────────────────────────────────────────────────┐
│                     ScreenPulse TV 系统                          │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │                    Android TV 设备                           │  │
│  │                                                              │  │
│  │  ┌─────────────┐    ┌──────────────┐    ┌───────────────┐  │  │
│  │  │             │    │              │    │               │  │  │
│  │  │  播放引擎   │◄───┤  播放列表    │◄───┤  定时任务     │  │  │
│  │  │  Playback   │    │  Manager     │    │  Scheduler     │  │  │
│  │  │  Engine     │    │              │    │               │  │  │
│  │  │             │    └──────┬───────┘    └───────────────┘  │  │
│  │  │  ┌───────┐  │           │                                │  │
│  │  │  │ExoPlr │  │    ┌──────┴───────┐                       │  │
│  │  │  │Glide  │  │    │   Room DB     │                       │  │
│  │  │  │WebView│  │    │  (SQLite)     │                       │  │
│  │  │  └───────┘  │    │  3 Tables     │                       │  │
│  │  └─────────────┘    └──────────────┘                       │  │
│  │                                                              │  │
│  │  ┌─────────────────────────────────────────────────────┐    │  │
│  │  │              Web 服务器 (NanoHTTPD :8080)            │    │  │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌────────────┐  │    │  │
│  │  │  │  Web 管理   │  │  REST API   │  │  媒体文件   │  │    │  │
│  │  │  │  面板 HTML  │  │  端点       │  │  服务       │  │    │  │
│  │  │  └─────────────┘  └─────────────┘  └────────────┘  │    │  │
│  │  └──────────────────────┬──────────────────────────────┘    │  │
│  └─────────────────────────┼───────────────────────────────────┘  │
│                            │  HTTP (局域网)                        │
└────────────────────────────┼──────────────────────────────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
        ┌─────┴─────┐  ┌────┴────┐  ┌──────┴──────┐
        │  手机浏览器 │  │ 电脑    │  │  平板浏览器 │
        │  (扫码访问) │  │  浏览器 │  │             │
        └───────────┘  └─────────┘  └─────────────┘
```

---

## 📸 截图预览

| 首次启动（二维码引导） | Web 管理后台 | 播放列表管理 |
|:---:|:---:|:---:|
| ![](docs/assets/screenshot-qr.png) | ![](docs/assets/screenshot-web-admin.png) | ![](docs/assets/screenshot-playlist.png) |

| 视频播放 | 图片轮播 | IPTV 直播 |
|:---:|:---:|:---:|
| ![](docs/assets/screenshot-video.png) | ![](docs/assets/screenshot-image.png) | ![](docs/assets/screenshot-iptv.png) |

> 📁 截图文件请放置于 `docs/assets/` 目录下

---

## 🚀 快速开始

### 前置条件

| 环境 | 要求 |
|------|------|
| Android Studio | Flamingo (2022.2.1) 或更高版本 |
| JDK | 17 |
| Android 设备/模拟器 | Android TV (API 24+)，建议使用 Android TV Box |
| 网络 | 局域网环境（用于 Web 管理后台访问） |

### 构建步骤

```bash
# 1. 克隆项目
git clone https://github.com/yourname/screenpulse-tv.git
cd screenpulse-tv

# 2. 打开项目
# 用 Android Studio 打开 android/ 目录
open -a "Android Studio" android/

# 3. 同步 Gradle
# Android Studio 会自动提示同步，或点击 File → Sync Project with Gradle Files

# 4. 构建项目
# Build → Build Bundle(s) / APK(s) → Build APK(s)
```

### 安装到设备

```bash
# 方式一：通过 ADB 连接 Android TV 盒子安装
adb connect <tv-ip-address>        # 例如: adb connect 192.168.1.100
adb install android/app/build/outputs/apk/debug/app-debug.apk

# 方式二：通过 U 盘
# 将 APK 复制到 U 盘，在 Android TV 上使用文件管理器安装

# 方式三：通过 Android Studio 直接运行
# 选择 TV 设备，点击 Run 按钮
```

### 访问 Web 管理后台

```
1. 在 Android TV 设备上启动 ScreenPulse TV 应用
2. 屏幕上会显示管理地址和二维码
3. 在手机或电脑浏览器中输入: http://<设备IP>:8080
   或直接扫描屏幕上的二维码
4. 即可在 Web 界面管理播放内容
```

---

## 🛠️ 技术栈

| 模块 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **开发语言** | Kotlin | 1.9.0 | 主开发语言 |
| **最低 SDK** | Android API | 24 (Android 7.0) | 兼容性 |
| **目标 SDK** | Android API | 34 (Android 14) | 最新特性 |
| **视频播放** | ExoPlayer (Media3) | 1.2.1 | 视频/IPTV/流媒体播放 |
| **图片加载** | Glide | 4.16.0 | 图片加载与缓存 |
| **Web 服务器** | NanoHTTPD | 2.3.1 | 嵌入式 Web 服务 |
| **本地数据库** | Room | 2.6.1 | 播放列表/媒体库/定时任务持久化 |
| **异步框架** | Kotlin Coroutines | 1.7.3 | 异步任务处理 |
| **定时调度** | AlarmManager + WorkManager | 2.9.0 | 定时任务/开机自启动 |
| **JSON 解析** | Gson | 2.10.1 | API 数据序列化 |
| **网络客户端** | OkHttp | 4.12.0 | HTTP 请求 |
| **二维码** | ZXing | 3.5.3 | 管理地址二维码生成 |
| **导航** | Navigation Component | 2.7.7 | 页面导航 |
| **UI 框架** | Leanback + Material | 1.0.0 / 1.11.0 | Android TV UI 组件 |
| **数据存储** | DataStore | 1.0.0 | 应用设置存储 |

---

## 📁 项目结构

```
screenpulse-tv/
├── README.md                          # 项目说明文档
├── docs/
│   ├── API.md                        # REST API 文档
│   ├── USAGE.md                      # 使用指南
│   ├── DESIGN.md                     # 技术设计文档
│   ├── CHANGELOG.md                  # 更新日志
│   └── assets/
│       ├── prototype-tv.html          # TV 端原型
│       └── prototype-web.html        # Web 管理端原型
└── android/
    ├── build.gradle.kts              # 根构建配置
    ├── settings.gradle.kts            # 项目设置
    ├── gradle.properties              # Gradle 属性
    └── app/
        ├── build.gradle.kts          # 应用模块构建配置
        └── src/main/
            ├── AndroidManifest.xml   # 应用清单
            ├── java/com/screenpulse/tv/
            │   ├── MainActivity.kt              # 主 Activity
            │   ├── ScreenPulseApp.kt           # Application 类
            │   ├── player/
            │   │   ├── PlaybackEngine.kt       # 播放引擎（核心）
            │   │   ├── PlaylistManager.kt       # 播放列表管理
            │   │   ├── PlaylistItemAdapter.kt   # 列表适配器
            │   │   ├── MediaItem.kt             # 媒体类型枚举
            │   │   ├── ImageDisplayFragment.kt  # 图片展示
            │   │   └── WebPageDisplayFragment.kt# 网页展示
            │   ├── server/
            │   │   ├── WebServerManager.kt      # Web 服务器管理
            │   │   └── ApiHandler.kt           # REST API 处理器
            │   ├── db/
            │   │   ├── AppDatabase.kt           # Room 数据库
            │   │   ├── Converters.kt            # 类型转换器
            │   │   ├── MediaDao.kt              # 媒体库 DAO
            │   │   ├── PlaylistDao.kt          # 播放列表 DAO
            │   │   ├── ScheduleDao.kt           # 定时任务 DAO
            │   │   └── entities/
            │   │       ├── MediaEntity.kt       # 媒体实体
            │   │       ├── PlaylistEntity.kt    # 播放列表实体
            │   │       └── ScheduleEntity.kt     # 定时任务实体
            │   ├── schedule/
            │   │   ├── ScheduleManager.kt       # 定时任务管理器
            │   │   └── BootReceiver.kt           # 开机自启动接收器
            │   ├── ui/
            │   │   ├── PlaybackFragment.kt       # 播放界面
            │   │   ├── PlaybackViewModel.kt     # 播放视图模型
            │   │   ├── LandingFragment.kt        # 首次启动页面
            │   │   └── SettingsFragment.kt        # 设置页面
            │   └── util/
            │       ├── NetworkUtils.kt           # 网络工具类
            │       ├── FileScanner.kt            # 文件扫描
            │       └── QrCodeGenerator.kt        # 二维码生成
            └── res/
                ├── layout/                       # 布局文件
                ├── values/                       # 字符串、颜色、样式
                ├── drawable/                     # 图片资源
                └── xml/                          # 配置文件
```

---

## 📡 API 接口概览

ScreenPulse TV 内置完整的 REST API，可通过 Web 管理后台或第三方集成调用。

| 方法 | 端点 | 描述 |
|------|------|------|
| GET | `/api/status` | 获取设备状态 |
| GET | `/api/playlist` | 获取播放列表 |
| POST | `/api/playlist` | 更新播放列表 |
| PUT | `/api/playlist/{id}` | 更新播放项 |
| DELETE | `/api/playlist/{id}` | 删除播放项 |
| POST | `/api/playlist/reorder` | 重新排序播放列表 |
| GET | `/api/media` | 获取媒体库 |
| POST | `/api/media/upload` | 上传媒体文件 |
| POST | `/api/media/url` | 添加网络 URL |
| DELETE | `/api/media/{id}` | 删除媒体 |
| GET | `/api/schedule` | 获取定时任务 |
| POST | `/api/schedule` | 创建定时任务 |
| PUT | `/api/schedule/{id}` | 更新定时任务 |
| DELETE | `/api/schedule/{id}` | 删除定时任务 |
| POST | `/api/control/play` | 播放 |
| POST | `/api/control/pause` | 暂停 |
| POST | `/api/control/skip` | 跳到下一项 |
| POST | `/api/control/previous` | 跳到上一项 |
| GET | `/api/settings` | 获取设置 |
| POST | `/api/settings` | 更新设置 |

> 📖 完整 API 文档请参阅 [docs/API.md](docs/API.md)

---

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源。

```
MIT License

Copyright (c) 2026 ScreenPulse TV Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

---

## 🤝 贡献指南

我们欢迎任何形式的贡献！无论是提交 Bug、改进文档还是开发新功能。

### 贡献流程

1. **Fork** 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature-name`
3. 提交更改：`git commit -m 'feat: add your feature description'`
4. 推送分支：`git push origin feature/your-feature-name`
5. 提交 **Pull Request**

### Commit 规范

我们使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

| 类型 | 描述 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修复 Bug |
| `docs` | 文档更新 |
| `style` | 代码格式（不影响功能） |
| `refactor` | 重构（不是新功能也不是修复） |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建过程或辅助工具的变动 |

### 代码风格

- 遵循 [Kotlin 官方编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- 使用 Android Studio 内置的 Kotlin 代码格式化工具
- 添加必要的 KDoc 注释（尤其是公共 API）

---

## 📚 文档目录

| 文档 | 说明 |
|------|------|
| [README.md](README.md) | 项目总览（本文件） |
| [docs/API.md](docs/API.md) | REST API 完整接口文档 |
| [docs/USAGE.md](docs/USAGE.md) | 用户使用指南 |
| [docs/DESIGN.md](docs/DESIGN.md) | 技术设计文档 |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | 版本更新日志 |

---

## ⚠️ 常见问题

<details>
<summary><b>❓ 无法通过浏览器访问设备？</b></summary>

1. 确保手机/电脑与 Android TV 设备在同一个局域网
2. 检查设备 IP 地址是否正确（屏幕上会显示）
3. 确认防火墙未阻止 8080 端口
4. 尝试关闭 VPN 连接

</details>

<details>
<summary><b>❓ 视频播放卡顿？</b></summary>

1. 确保网络带宽足够（建议 10Mbps+ 用于 1080p 视频）
2. 对于 4K 视频，建议使用支持硬件解码的设备
3. 检查 RTSP/RTMP 流地址是否可访问
4. IPTV 源可能存在服务器端限制

</details>

<details>
<summary><b>❓ 上传文件失败？</b></summary>

1. 文件大小限制为 50MB，如需更大请通过 URL 添加
2. 确保设备存储空间充足
3. 检查网络连接是否稳定

</details>

---

<p align="center">
  Made with ❤️ by ScreenPulse TV Contributors<br/>
  <sub>让每一块屏幕都充满活力</sub>
</p>
