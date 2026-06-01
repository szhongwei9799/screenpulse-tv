# 🖥️ ScreenPulse TV Player

> An open-source Android TV digital signage player with a built-in web-based management backend. Manage your playlists, upload media, and control playback — all from your browser.

![Android](https://img.shields.io/badge/Platform-Android%20TV-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Vue3](https://img.shields.io/badge/Frontend-Vue%203-4FC08D?logo=vue.js&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg)
![Version](https://img.shields.io/badge/Version-1.0.0-green.svg)
![Min SDK](https://img.shields.io/badge/Min%20SDK-21%20(Lollipop)-orange.svg)

---

## 📖 Overview

**ScreenPulse TV Player** turns any Android TV or Android box into a professional digital signage display. It supports full-screen rotation of videos, photos, presentations, IPTV streams, and network media resources. The embedded web management interface (powered by Vue 3 + Element Plus) lets you control everything from any device on the same network — no separate server required.

### How It Works

The Android app bundles a lightweight HTTP server (NanoHTTPD) that serves a Vue 3 Single Page Application. When the app launches on your TV, it displays a QR code and management URL. Scan the QR code or open the URL from any device to access the web admin panel, where you can:

- Upload and manage media files
- Build and reorder playlists
- Configure playback modes and interstitial schedules
- Monitor device status in real time

---

## ✨ Features

### Media Playback
- **Full-screen video playback** — MP4, AVI, MKV, MOV, 3GP, WebM, FLV
- **Photo slideshow** — JPG, JPEG, PNG, GIF, BMP, WebP with configurable display duration
- **Presentation display** — PPT, PPTX, PDF with timed transitions
- **IPTV / HLS streams** — M3U / M3U8 playlists with native HLS support
- **Network media** — HTTP, HTTPS, RTSP, DASH, and generic streaming URLs
- **Automatic media type detection** based on file extension

### Playlist Management
- Add, edit, delete, and reorder playlist items via drag-and-drop
- Per-item **enable/disable** toggle — temporarily skip items without deleting them
- Per-item **duration override** — set custom display time for images and presentations
- **Loop mode** — repeat the playlist indefinitely
- **Sequential mode** — play through once and stop
- **Random mode** — shuffle playback order each cycle

### Scheduled Interstitials
- Configure time-windowed interstitial playlists (e.g., lunch-break ads from 12:00–13:00)
- Automatic insertion and removal of interstitial content based on the system clock
- Cross-midnight support (e.g., 22:00 to 06:00)

### Web Admin Panel
- **Built-in Vue 3 SPA** — no external server or cloud dependency
- **Dashboard** — real-time device status (IP, playback state, active items, volume)
- **Playlist editor** — full CRUD with drag-and-drop reordering
- **Media library** — file upload with auto-playlist add option
- **Settings** — playback mode, interstitial schedule, volume control
- **Dark theme UI** — optimized for low-light environments
- **Responsive design** — works on phones, tablets, and desktops

### Device Integration
- **QR code on first launch** — instantly connect to the management interface
- **QR code on empty playlist** — reminds you to add content when the playlist is empty
- **Auto-start on boot** — the player launches automatically after device reboot
- **Foreground service** — uninterrupted playback even when the app is backgrounded
- **Android TV Leanback** compatible — appears in the Android TV launcher

---

## 📸 Screenshots

| Dashboard | Playlist Management | Media Upload |
|-----------|-------------------|--------------|
| _Coming soon_ | _Coming soon_ | _Coming soon_ |

| Settings | QR Code | Playback |
|----------|---------|----------|
| _Coming soon_ | _Coming soon_ | _Coming soon_ |

---

## 🏗️ Tech Stack

### Android App
| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 1.9+ |
| Min SDK | Android 5.0 (Lollipop) | API 21 |
| Target SDK | Android 14 | API 34 |
| Media Player | ExoPlayer (Media3) | 1.2.0 |
| Database | Room | 2.6.1 |
| Web Server | NanoHTTPD | 2.3.1 |
| QR Code | ZXing Core | 3.5.2 |
| Scheduling | WorkManager | 2.9.0 |
| Serialization | Gson | 2.10.1 |
| Concurrency | Kotlin Coroutines | 1.7.3 |
| Build System | Gradle (AGP) | 8.x |

### Web Admin Panel
| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Vue 3 (Composition API) | 3.4+ |
| UI Library | Element Plus | 2.9+ |
| Routing | Vue Router | 4.3+ |
| HTTP Client | Axios | 1.7+ |
| Build Tool | Vite | 5.4+ |
| Icons | Element Plus Icons | 2.3+ |

---

## 🧩 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    ScreenPulse TV Player                         │
│                                                                   │
│  ┌──────────────────────┐    ┌─────────────────────────────────┐ │
│  │   Android TV App     │    │       Web Admin (Vue 3 SPA)      │ │
│  │                      │    │                                   │ │
│  │  ┌────────────────┐  │    │  ┌──────────┐ ┌───────────────┐  │ │
│  │  │  MainActivity  │  │    │  │Dashboard │ │  Playlist     │  │ │
│  │  └───────┬────────┘  │    │  └──────────┘ └───────────────┘  │ │
│  │          │           │    │  ┌──────────┐ ┌───────────────┐  │ │
│  │  ┌───────▼────────┐  │    │  │  Media   │ │   Settings    │  │ │
│  │  │ PlaybackService│  │    │  │ Library  │ │               │  │ │
│  │  └───────┬────────┘  │    │  └──────────┘ └───────────────┘  │ │
│  │          │           │    └──────────────┬──────────────────┘ │
│  │  ┌───────▼────────┐  │                   │ Axios HTTP          │
│  │  │PlaylistManager │  │                   │ Requests           │
│  │  └───────┬────────┘  │                   │                    │
│  │  ┌───────▼────────┐  │    ┌──────────────▼──────────────────┐ │
│  │  │MediaController │◄─┼────│  NanoHTTPD Web Server :8080    │ │
│  │  │  (ExoPlayer)    │  │    │                                  │ │
│  │  └────────────────┘  │    │  ┌──────────┐  ┌────────────┐  │ │
│  │                      │    │  │Static SPA │  │  REST API   │  │ │
│  │  ┌────────────────┐  │    │  │  Files    │  │  Endpoints  │  │ │
│  │  │ ScheduleManager│  │    │  └──────────┘  └──────┬─────┘  │ │
│  │  │ (WorkManager)  │  │    └───────────────────────┼────────┘ │
│  │  └────────────────┘  │                            │           │
│  │                      │    ┌───────────────────────▼────────┐ │
│  │  ┌────────────────┐  │    │      Room Database (SQLite)     │ │
│  │  │ QRCodeGenerator│  │    │  ┌──────────┐  ┌────────────┐  │ │
│  │  └────────────────┘  │    │  │media_items│  │playlist    │  │ │
│  └──────────────────────┘    │  │  table    │  │_config tbl │  │ │
│                                │  └──────────┘  └────────────┘  │ │
│                                └─────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** — Hedgehog (2023.1.1) or newer
- **JDK 17** — required for Gradle compilation
- **Android TV device or emulator** — API 21+ for testing
- **Node.js 18+** — only needed if you want to modify the web admin UI
- **Git** — for cloning the repository

### Clone the Repository

```bash
git clone https://github.com/szhongwei9799/ScreenPulse-TV-Player.git
cd ScreenPulse-TV-Player
```

### Build the APK

#### Option A: Android Studio
1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**
4. The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

#### Option B: Command Line
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# For release build:
./gradlew assembleRelease
```

#### Option C: GitHub Actions
The project includes a GitHub Actions workflow (`.github/workflows/build.yml`). Pushing to the `main` branch will automatically build and upload the APK as an artifact.

### Install on Android TV

1. **Via ADB** (recommended for development):
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Via USB drive**:
   - Copy the APK to a USB drive
   - Plug into your Android TV
   - Use a file manager app to install the APK
   - Enable "Install from unknown sources" in Settings > Security

3. **Via network**:
   - Host the APK on a local web server
   - Open the URL in the Android TV browser to download

### Access the Web Admin

1. Launch ScreenPulse TV Player on your Android TV
2. The screen will display a **QR code** and **management URL** (e.g., `http://192.168.1.100:8080`)
3. From any device on the **same network**, open that URL in a browser
4. The web admin dashboard will load — start managing your playlists!

> **Note:** Both devices must be on the same local network. The web server runs on port **8080** by default.

### Build the Web Admin (Optional)

If you want to modify the web admin UI:

```bash
cd web-admin
npm install
npm run dev        # Development server at http://localhost:5173
npm run build      # Production build → dist/
```

After building, copy the `dist/` contents to `app/src/main/assets/web-admin/`:

```bash
rm -rf ../app/src/main/assets/web-admin/*
cp -r dist/* ../app/src/main/assets/web-admin/
```

---

## 📁 Project Structure

```
ScreenPulse-TV-Player/
├── .github/
│   └── workflows/
│       └── build.yml                    # GitHub Actions CI/CD
├── app/
│   ├── build.gradle                     # App-level Gradle config
│   ├── proguard-rules.pro              # ProGuard rules
│   └── src/main/
│       ├── AndroidManifest.xml          # App manifest (permissions, activities)
│       ├── res/
│       │   ├── layout/
│       │   │   └── activity_main.xml    # Main activity layout
│       │   ├── values/
│       │   │   ├── strings.xml          # String resources
│       │   │   ├── colors.xml          # Color definitions
│       │   │   └── styles.xml          # Theme styles
│       │   └── xml/
│       │       └── file_paths.xml       # FileProvider paths
│       ├── assets/
│       │   └── web-admin/
│       │       └── index.html           # Bundled Vue 3 SPA
│       └── java/com/screenpulse/player/
│           ├── ScreenPulseApp.kt       # Application class
│           ├── MainActivity.kt         # Main launcher activity
│           ├── data/
│           │   ├── AppDatabase.kt      # Room database singleton
│           │   ├── dao/
│           │   │   ├── MediaItemDao.kt # Media item DAO
│           │   │   └── PlaylistConfigDao.kt
│           │   └── entity/
│           │       ├── MediaItem.kt     # MediaItem entity + MediaType enum
│           │       └── PlaylistConfig.kt # PlaylistConfig entity + PlaybackMode enum
│           ├── player/
│           │   ├── PlaybackService.kt  # Foreground service for playback
│           │   ├── PlaylistManager.kt  # Playlist iteration & interstitial logic
│           │   └── MediaController.kt  # ExoPlayer wrapper
│           ├── server/
│           │   ├── WebServer.kt        # NanoHTTPD server
│           │   └── ApiRouter.kt        # REST API route handler
│           ├── schedule/
│           │   ├── BootReceiver.kt     # BOOT_COMPLETED receiver
│           │   ├── ScheduleManager.kt  # Schedule configuration
│           │   └── ScheduleCheckWorker.kt # WorkManager worker
│           ├── qrcode/
│           │   └── QRCodeGenerator.kt  # QR code generation
│           └── util/
│               └── NetworkUtil.kt      # Network utility (IP detection)
├── web-admin/                          # Vue 3 web admin (separate project)
│   ├── package.json                     # Node.js dependencies
│   ├── vite.config.js                  # Vite configuration
│   ├── index.html                      # Entry HTML
│   └── src/
│       ├── main.js                     # Vue app entry point
│       ├── App.vue                     # Root component
│       ├── api/
│       │   └── index.js                # Axios API client
│       ├── router/
│       │   └── index.js                # Vue Router configuration
│       ├── views/
│       │   ├── Dashboard.vue           # Device status dashboard
│       │   ├── Playlist.vue           # Playlist management
│       │   ├── MediaLibrary.vue        # Media file management
│       │   └── Settings.vue            # App settings
│       ├── components/
│       │   ├── MediaTypeBadge.vue      # Media type indicator
│       │   └── StatusCard.vue          # Status card widget
│       └── assets/
│           └── styles.css              # Global styles
├── build.gradle                         # Root Gradle config
├── settings.gradle                      # Gradle settings
├── gradle.properties                    # Gradle properties
├── gradle/wrapper/
│   └── gradle-wrapper.properties       # Gradle wrapper version
├── docs/
│   ├── DESIGN.md                       # Architecture & design document
│   ├── USAGE.md                        # User guide (Chinese)
│   └── CHANGELOG.md                    # Version changelog
└── README.md                            # This file
```

---

## 📡 API Reference

All API endpoints are served by the embedded NanoHTTPD server on port **8080**.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/status` | Get device status, IP address, active item count, playback mode, volume |
| `GET` | `/api/playlist` | Get all playlist items |
| `POST` | `/api/playlist` | Add a new media item to the playlist |
| `PUT` | `/api/playlist/:id` | Update an existing playlist item |
| `DELETE` | `/api/playlist/:id` | Delete a playlist item (and its local file if applicable) |
| `POST` | `/api/playlist/reorder` | Reorder playlist items (batch update sort orders) |
| `PUT` | `/api/config` | Update playback configuration (mode, interstitial, volume) |
| `POST` | `/api/upload` | Upload a media file (multipart form data) |
| `GET` | `/api/scan` | Trigger a scan of local media directories |

### Example Requests

#### Get Device Status
```bash
curl http://192.168.1.100:8080/api/status
```

```json
{
  "deviceName": "Android TV Box",
  "ipAddress": "192.168.1.100",
  "port": 8080,
  "managementUrl": "http://192.168.1.100:8080",
  "activeItems": 5,
  "playbackMode": "LOOP",
  "interstitialEnabled": false,
  "volumeLevel": 80,
  "androidVersion": "14",
  "appVersion": "1.0.0"
}
```

#### Add Playlist Item
```bash
curl -X POST http://192.168.1.100:8080/api/playlist \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Promo Video",
    "url": "/storage/emulated/0/ScreenPulse/promo.mp4",
    "type": "VIDEO",
    "durationSeconds": 0,
    "enabled": true,
    "sortOrder": 0
  }'
```

#### Upload a File
```bash
curl -X POST http://192.168.1.100:8080/api/upload \
  -F "file=@/path/to/video.mp4" \
  -F "filename=video.mp4" \
  -F "autoAdd=true"
```

#### Update Configuration
```bash
curl -X PUT http://192.168.1.100:8080/api/config \
  -H "Content-Type: application/json" \
  -d '{
    "playbackMode": "LOOP",
    "interstitialEnabled": true,
    "interstitialStartHour": 12,
    "interstitialEndHour": 13,
    "volumeLevel": 75
  }'
```

---

## ⚙️ Configuration

### Playlist Config Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `playbackMode` | enum | `LOOP` | `LOOP`, `SEQUENTIAL`, or `RANDOM` |
| `interstitialEnabled` | boolean | `false` | Enable time-windowed interstitial playback |
| `interstitialStartHour` | int | `12` | Interstitial start hour (0–23) |
| `interstitialEndHour` | int | `13` | Interstitial end hour (0–23) |
| `interstitialPlaylistName` | string | `"interstitial"` | Name of the interstitial playlist |
| `volumeLevel` | int | `80` | Master volume (0–100) |

### Media Item Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `title` | string | required | Display name of the media item |
| `url` | string | required | File path or network URL |
| `type` | enum | `VIDEO` | `VIDEO`, `IMAGE`, `PPT`, `IPTV`, `STREAM` |
| `durationSeconds` | int | `0` | Custom duration (0 = auto-detect for video, 10s default for images) |
| `enabled` | boolean | `true` | Whether the item is active in the playlist |
| `sortOrder` | int | auto | Position in the playlist (0 = first) |

### Environment / Build Config

| Property | Value | Description |
|----------|-------|-------------|
| `applicationId` | `com.screenpulse.player` | Unique app identifier |
| `minSdk` | `21` | Android 5.0 Lollipop |
| `targetSdk` | `34` | Android 14 |
| `versionCode` | `1` | Incremental version code |
| `versionName` | `"1.0.0"` | Human-readable version |
| Web server port | `8080` | Embedded HTTP server port |
| Upload directory | `files/screenpulse_uploads/` | Local file storage for uploaded media |

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. **Fork** the repository
2. **Create a feature branch**: `git checkout -b feature/your-feature-name`
3. **Commit** your changes: `git commit -m 'Add: your feature description'`
4. **Push** to your branch: `git push origin feature/your-feature-name`
5. Open a **Pull Request** with a clear description of your changes

### Commit Message Convention

We follow conventional commits:

- `feat:` — New features
- `fix:` — Bug fixes
- `docs:` — Documentation changes
- `style:` — Code style changes (formatting)
- `refactor:` — Code refactoring
- `perf:` — Performance improvements
- `test:` — Test additions or modifications
- `chore:` — Build system or tooling changes

### Development Guidelines

- **Kotlin**: Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Vue 3**: Use the Composition API with `<script setup>`
- **API**: Keep REST endpoints consistent and backward-compatible
- **Database**: Use Room migrations for schema changes
- **Testing**: Write unit tests for core logic (PlaylistManager, ApiRouter)

---

## 📄 License

This project is licensed under the **MIT License**. You are free to use, modify, and distribute this software.

```
MIT License

Copyright (c) 2024 szhongwei9799

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
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 👤 Author

**szhongwei9799**

- GitHub: [@szhongwei9799](https://github.com/szhongwei9799)

---

## 🙏 Acknowledgments

- [ExoPlayer / Media3](https://github.com/google/ExoPlayer) by Google — powerful media playback
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) — lightweight embedded HTTP server
- [Room](https://developer.android.com/jetpack/androidx/releases/room) — Android persistence library
- [Vue.js](https://vuejs.org/) — progressive JavaScript framework
- [Element Plus](https://element-plus.org/) — Vue 3 UI component library
- [ZXing](https://github.com/zxing/zxing) — QR code generation

---

<p align="center">
  <sub>Built with ❤️ for digital signage</sub>
</p>
