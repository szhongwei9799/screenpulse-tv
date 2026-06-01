# ScreenPulse TV Player — Design Document

> Version: 1.0.0 | Last Updated: 2024

This document describes the system architecture, component design, data models, API specification, and key workflows for the ScreenPulse TV Player project.

---

## Table of Contents

1. [System Architecture Overview](#1-system-architecture-overview)
2. [Android App Architecture (MVVM)](#2-android-app-architecture-mvvm)
   - 2.1 [Data Layer (Room, DAO)](#21-data-layer-room-dao)
   - 2.2 [Player Layer (ExoPlayer, MediaController)](#22-player-layer-exoplayer-mediacontroller)
   - 2.3 [Server Layer (NanoHTTPD, API)](#23-server-layer-nanohttpd-api)
   - 2.4 [Schedule Layer (WorkManager)](#24-schedule-layer-workmanager)
3. [Web Admin Architecture](#3-web-admin-architecture)
4. [Data Models](#4-data-models)
5. [API Design](#5-api-design)
6. [Playlist Playback Flow](#6-playlist-playback-flow)
7. [Interstitial Schedule Flow](#7-interstitial-schedule-flow)
8. [Security Considerations](#8-security-considerations)
9. [Performance Optimization](#9-performance-optimization)

---

## 1. System Architecture Overview

ScreenPulse TV Player follows a **self-contained embedded-server architecture**. The Android app hosts both the media playback engine and the web management interface, eliminating the need for any external server infrastructure.

### High-Level Architecture

```
┌───────────────────────────────────────────────────────────┐
│                    Android Device                          │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │                  Android Application                │  │
│  │                                                      │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │  │
│  │  │  UI Layer │  │ Player   │  │    Web Server     │  │  │
│  │  │ (Activity)│◄─┤ Service  │  │  (NanoHTTPD:8080)│  │  │
│  │  └──────────┘  └────┬─────┘  └────────┬─────────┘  │  │
│  │                     │                  │            │  │
│  │  ┌──────────────────▼──────────────────▼─────────┐  │  │
│  │  │              Data Layer (Room)                 │  │  │
│  │  │   ┌──────────────┐  ┌─────────────────────┐   │  │  │
│  │  │   │  media_items  │  │  playlist_config     │   │  │  │
│  │  │   └──────────────┘  └─────────────────────┘   │  │  │
│  │  └───────────────────────────────────────────────┘  │  │
│  │                                                      │  │
│  │  ┌──────────────────┐  ┌───────────────────────┐   │  │
│  │  │ ScheduleManager  │  │  QRCodeGenerator      │   │  │
│  │  │ (WorkManager)    │  │  (ZXing)              │   │  │
│  │  └──────────────────┘  └───────────────────────┘   │  │
│  └─────────────────────────────────────────────────────┘  │
│                         │                                 │
│                  HTTP API (:8080)                         │
│                         │                                 │
└─────────────────────────┼─────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          │               │               │
     ┌────▼────┐   ┌─────▼────┐   ┌──────▼──────┐
     │ Browser │   │ Phone    │   │ Tablet      │
     │ (Admin) │   │ (Admin)  │   │ (Admin)     │
     └─────────┘   └──────────┘   └─────────────┘
```

### Design Principles

- **Self-contained**: No external server or cloud service required
- **Zero-config**: Works out of the box on any Android TV
- **Network-first management**: All control through the web interface
- **Separation of concerns**: Player, server, data, and UI are loosely coupled
- **Robust playback**: Foreground service ensures uninterrupted media display

---

## 2. Android App Architecture (MVVM)

The Android app loosely follows the MVVM (Model-View-ViewModel) pattern, with the key distinction that the "ViewModel" is distributed across multiple manager classes rather than a single monolithic ViewModel.

### Component Dependency Graph

```
MainActivity
    ├── PlaybackService (foreground service)
    │       ├── PlaylistManager
    │       │       └── List<MediaItem> (from Room)
    │       └── MediaController
    │               └── ExoPlayer (Media3)
    ├── WebServer (NanoHTTPD)
    │       └── ApiRouter
    │               ├── MediaItemDao (Room)
    │               └── PlaylistConfigDao (Room)
    ├── ScheduleManager
    │       └── WorkManager
    └── QRCodeGenerator
            └── ZXing
```

### 2.1 Data Layer (Room, DAO)

The data layer uses AndroidX Room for persistent storage. The database holds two tables: `media_items` and `playlist_config`.

#### AppDatabase

```
AppDatabase (Singleton)
├── mediaItemDao()    → MediaItemDao
├── playlistConfigDao() → PlaylistConfigDao
└── getInstance(context) → synchronized singleton
```

The `AppDatabase` is implemented as a lazy singleton to ensure only one database instance exists throughout the app lifecycle. This prevents database locking issues when multiple components access it concurrently.

#### MediaItemDao

```kotlin
@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items WHERE enabled = 1 ORDER BY sortOrder ASC")
    fun getEnabledItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items ORDER BY sortOrder ASC")
    suspend fun getAllItemsOnce(): List<MediaItem>

    @Query("SELECT COUNT(*) FROM media_items WHERE enabled = 1")
    suspend fun getEnabledCount(): Int

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getItemById(id: Long): MediaItem?

    @Insert
    suspend fun insert(item: MediaItem): Long

    @Update
    suspend fun update(item: MediaItem)

    @Delete
    suspend fun delete(item: MediaItem)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE media_items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Transaction
    suspend fun reorderItems(updates: Map<Long, Int>)
}
```

Key design decisions:
- `getEnabledItems()` returns a `Flow` so the playback service reactively updates when items change
- `reorderItems()` is a `@Transaction` to ensure atomic batch updates
- Queries use `suspend` functions for coroutine-safe database access

#### PlaylistConfigDao

```kotlin
@Dao
interface PlaylistConfigDao {
    @Query("SELECT * FROM playlist_config WHERE id = 1")
    fun getConfig(): Flow<PlaylistConfig?>

    @Query("SELECT * FROM playlist_config WHERE id = 1")
    suspend fun getConfigOnce(): PlaylistConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: PlaylistConfig)
}
```

The config table uses a single fixed row (`id = 1`) as a key-value store for global settings. This simplifies queries — there is always at most one config row.

---

### 2.2 Player Layer (ExoPlayer, MediaController)

The player layer handles all media rendering and playback lifecycle management.

#### PlaybackService

A foreground service that ensures uninterrupted playback. It holds references to `PlaylistManager` and `MediaController`, orchestrating the playback loop.

```
PlaybackService (Lifecycle)
    onStartCommand()
        → Start foreground notification
        → Initialize PlaylistManager with current items
        → Load PlaylistConfig
        → Begin playback loop

    Playback Loop:
        1. currentItem = playlistManager.currentItem()
        2. mediaController.prepareMedia(currentItem)
        3. Wait for onPlaybackEnded / onImageDisplayComplete callback
        4. playlistManager.advanceToNext()
        5. If advanceToNext() returns true → go to step 1
        6. If advanceToNext() returns false (SEQUENTIAL mode) → stop
```

#### MediaController

Wraps ExoPlayer and provides a unified interface for all media types.

```
MediaController
    ├── ExoPlayer instance (video, IPTV, stream)
    ├── CoroutineScope (image/PPT timing)
    └── Callbacks
        ├── onPlaybackEnded        (video/stream finished)
        ├── onPlaybackError        (player error)
        └── onImageDisplayComplete (image/PPT timer expired)
```

Media type handling strategy:

| MediaType | Player Strategy | Duration Source |
|-----------|----------------|-----------------|
| `VIDEO` | ExoPlayer `setMediaItem` | Auto-detect from file |
| `IMAGE` | Coroutine delay timer | `durationSeconds` or 10s default |
| `PPT` | Coroutine delay timer | `durationSeconds` or 60s default |
| `IPTV` | ExoPlayer `HlsMediaSource` | Continuous (live stream) |
| `STREAM` | ExoPlayer `setMediaItem` | Continuous (live stream) |

#### ExoPlayer Configuration

```kotlin
ExoPlayer.Builder(context)
    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
    .setAudioAttributes(movieAudioAttributes, handleAudioFocus = true)
    .setHandleAudioBecomingNoisy(true)
    .setWakeMode(C.WAKE_MODE_LOCAL)
    .build()
```

Key settings:
- **Audio focus management**: Automatically ducks/pauses when other audio apps interrupt
- **Wake mode**: Keeps the CPU awake during playback via `WAKE_MODE_LOCAL`
- **HTTP timeouts**: 30-second connect and read timeouts for network streams
- **Cross-protocol redirects**: Enabled for servers that redirect HTTP → HTTPS
- **Custom User-Agent**: `ScreenPulsePlayer/1.0` for server-side identification

---

### 2.3 Server Layer (NanoHTTPD, API)

The web server is embedded directly in the Android app using NanoHTTPD.

#### WebServer

```
WebServer (NanoHTTPD, port 8080)
    serve(session):
        ├── URI starts with "/api/" → serveApi(session)
        └── All other URIs → serveStaticFile(session)
            ├── Try to load from assets/web-admin/{uri}
            └── Fallback → assets/web-admin/index.html (SPA routing)
```

The server has two responsibilities:
1. **Static file serving**: Serves the bundled Vue 3 SPA from `assets/web-admin/`
2. **API routing**: Delegates `/api/*` requests to `ApiRouter`

#### ApiRouter

Handles all REST API logic. Each method:
1. Parses the request body (JSON)
2. Invokes the appropriate DAO operation
3. Returns a JSON response

```
ApiRouter
    ├── getStatus()           → GET  /api/status
    ├── getPlaylist()          → GET  /api/playlist
    ├── addPlaylistItem()      → POST /api/playlist
    ├── updatePlaylistItem()   → PUT  /api/playlist/:id
    ├── deletePlaylistItem()   → DELETE /api/playlist/:id
    ├── reorderPlaylist()      → POST /api/playlist/reorder
    ├── updateConfig()         → PUT  /api/config
    ├── uploadFile()           → POST /api/upload
    └── triggerScan()          → GET  /api/scan
```

**Multipart handling**: The `/api/upload` endpoint uses NanoHTTPD's `session.parseBody()` for multipart form data parsing. Uploaded files are saved to the app's internal storage directory (`files/screenpulse_uploads/`).

**CORS**: NanoHTTPD adds permissive CORS headers to all responses to allow cross-origin requests from browsers.

---

### 2.4 Schedule Layer (WorkManager)

The schedule layer manages time-based operations using AndroidX WorkManager.

#### BootReceiver

```kotlin
class BootReceiver : BroadcastReceiver {
    onReceive(context, intent):
        if (BOOT_COMPLETED or QUICKBOOT_POWERON):
            ScheduleManager.schedule(context)
}
```

Listens for `BOOT_COMPLETED` and `QUICKBOOT_POWERON` intents to re-register the playback schedule after device reboot.

#### ScheduleCheckWorker

A periodic WorkManager task that runs every 15 minutes (minimum interval) to:
1. Check the current time against interstitial schedule
2. Trigger playback mode changes if the interstitial window starts/ends
3. Re-launch the PlaybackService if it's not running

#### ScheduleManager

Configuration wrapper that sets up the WorkManager constraints and periodic work request.

---

## 3. Web Admin Architecture

The web admin is a Vue 3 Single Page Application (SPA) built with Element Plus.

### Component Architecture

```
App.vue (Root)
├── Sidebar Navigation
└── <router-view>
    ├── Dashboard.vue
    │   ├── StatusCard × N (device info, playback state, network)
    │   └── Quick actions (scan, refresh)
    ├── Playlist.vue
    │   ├── Toolbar (add button, mode selector)
    │   ├── SortablePlaylist (drag-and-drop table)
    │   │   └── PlaylistItemRow × N
    │   │       ├── MediaTypeBadge
    │   │       ├── Enable/Disable toggle
    │   │       ├── Edit/Delete actions
    │   │       └── Duration input
    │   └── EditDialog (add/edit item form)
    ├── MediaLibrary.vue
    │   ├── Upload zone (drag-and-drop)
    │   ├── File list with thumbnails
    │   └── Scan local storage button
    └── Settings.vue
        ├── PlaybackMode radio group
        ├── Interstitial toggle + time picker
        ├── Volume slider
        └── Save button
```

### Router Configuration

```javascript
const routes = [
  { path: '/',      redirect: '/dashboard' },
  { path: '/dashboard',  component: Dashboard },
  { path: '/playlist',   component: Playlist },
  { path: '/media',      component: MediaLibrary },
  { path: '/settings',   component: Settings }
]
```

### API Integration Layer

The `src/api/index.js` module provides a centralized Axios instance with:
- **Base URL**: `window.location.origin` (resolves to the NanoHTTPD server address)
- **Timeout**: 15 seconds
- **Error interceptor**: Extracts error messages and logs them
- **Response interceptor**: Unwraps `response.data` for cleaner API usage

All API functions are imported by view components and called directly:

```javascript
// Example: fetching device status in Dashboard.vue
import { getStatus } from '@/api'
const status = await getStatus()
```

### State Management

The web admin uses **component-local state** (via Vue 3 `ref`/`reactive`) rather than a global state manager like Pinia. This is intentional — the app is relatively simple, and the API always returns fresh data from the device. There is no complex client-side caching or offline support needed.

---

## 4. Data Models

### MediaItem

Represents a single piece of media content in the playlist.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | Long | Primary Key, auto-generate | Unique identifier |
| `title` | String | Not null | Display name shown in the playlist |
| `url` | String | Not null | Local file path or network URL |
| `type` | MediaType (enum) | Not null, default VIDEO | Media type classification |
| `durationSeconds` | Int | Default 0 | Custom display duration (seconds). 0 = auto for video, 10s default for images |
| `enabled` | Boolean | Default true | Whether the item is active in playback |
| `sortOrder` | Int | Default 0 | Position index for ordering |
| `createdAt` | Long | Default `currentTimeMillis` | Creation timestamp |
| `updatedAt` | Long | Default `currentTimeMillis` | Last modified timestamp |

**MediaType enum values:**
- `VIDEO` — Standard video files (MP4, AVI, MKV, MOV, etc.)
- `IMAGE` — Static image files (JPG, PNG, GIF, etc.)
- `PPT` — Presentation files (PPT, PPTX, PDF)
- `IPTV` — HLS streaming playlists (M3U, M3U8)
- `STREAM` — Other streaming content (DASH, progressive HTTP, etc.)

### PlaylistConfig

Global playback configuration stored as a single-row table.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | Int | Primary Key, always 1 | Fixed identifier (singleton row) |
| `playbackMode` | PlaybackMode (enum) | Default LOOP | Playlist iteration strategy |
| `interstitialEnabled` | Boolean | Default false | Enable/disable interstitial scheduling |
| `interstitialStartHour` | Int | Default 12, range 0–23 | Hour when interstitial begins |
| `interstitialEndHour` | Int | Default 13, range 0–23 | Hour when interstitial ends |
| `interstitialPlaylistName` | String | Default "interstitial" | Name tag for the interstitial playlist |
| `volumeLevel` | Int | Default 80, range 0–100 | Master audio volume percentage |
| `lastUpdated` | Long | Default `currentTimeMillis` | Last configuration change timestamp |

**PlaybackMode enum values:**
- `LOOP` — After the last item, restart from the first item
- `SEQUENTIAL` — Play through once, then stop at the end
- `RANDOM` — Shuffle items; each cycle produces a different order

### Entity Relationships

```
PlaylistConfig (1 row)
        │
        │ influences
        ▼
PlaylistManager
        │
        │ references
        ▼
MediaItem (0..N rows)
        │
        │ stored in
        ▼
Room Database (SQLite)
```

---

## 5. API Design

### General Conventions

- **Base URL**: `http://<device-ip>:8080`
- **Content-Type**: `application/json` (except for file uploads)
- **Response format**: JSON object with relevant fields
- **Error format**: `{"error": "description"}` with appropriate HTTP status code

### Endpoints

#### GET /api/status

Returns current device status and configuration summary.

**Response (200 OK):**
```json
{
  "deviceName": "Xiaomi Mi Box S",
  "ipAddress": "192.168.1.105",
  "port": 8080,
  "managementUrl": "http://192.168.1.105:8080",
  "activeItems": 7,
  "playbackMode": "LOOP",
  "interstitialEnabled": true,
  "volumeLevel": 80,
  "androidVersion": "12",
  "appVersion": "1.0.0"
}
```

#### GET /api/playlist

Returns all playlist items ordered by `sortOrder`.

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "title": "Welcome Video",
    "url": "/storage/emulated/0/ScreenPulse/welcome.mp4",
    "type": "VIDEO",
    "durationSeconds": 0,
    "enabled": true,
    "sortOrder": 0,
    "createdAt": 1700000000000,
    "updatedAt": 1700000000000
  },
  {
    "id": 2,
    "title": "Promo Banner",
    "url": "/storage/emulated/0/ScreenPulse/banner.jpg",
    "type": "IMAGE",
    "durationSeconds": 15,
    "enabled": true,
    "sortOrder": 1,
    "createdAt": 1700000100000,
    "updatedAt": 1700000100000
  }
]
```

#### POST /api/playlist

Adds a new media item to the playlist.

**Request Body:**
```json
{
  "title": "Live Stream",
  "url": "https://example.com/stream/live.m3u8",
  "type": "IPTV",
  "durationSeconds": 0,
  "enabled": true,
  "sortOrder": 10
}
```

**Response (200 OK):**
```json
{
  "id": 3,
  "title": "Live Stream",
  "url": "https://example.com/stream/live.m3u8",
  "type": "IPTV",
  "durationSeconds": 0,
  "enabled": true,
  "sortOrder": 10,
  "createdAt": 1700000200000,
  "updatedAt": 1700000200000
}
```

**Error (400 Bad Request):**
```json
{
  "error": "URL is required"
}
```

#### PUT /api/playlist/:id

Updates an existing playlist item. Only the provided fields are updated (partial update).

**Request Body (partial update example):**
```json
{
  "title": "Updated Title",
  "enabled": false
}
```

**Response (200 OK):**
Returns the full updated `MediaItem` object.

**Error (404 Not Found):**
```json
{
  "error": "Item not found"
}
```

#### DELETE /api/playlist/:id

Deletes a playlist item. If the item references a locally uploaded file, the file is also deleted from storage.

**Response (200 OK):**
```json
{
  "success": true,
  "deletedId": 3
}
```

#### POST /api/playlist/reorder

Batch-updates the sort order of multiple items in a single transaction.

**Request Body:**
```json
{
  "items": [
    { "id": 1, "sortOrder": 2 },
    { "id": 2, "sortOrder": 0 },
    { "id": 5, "sortOrder": 1 }
  ]
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "updatedCount": 3
}
```

#### PUT /api/config

Updates the global playback configuration.

**Request Body:**
```json
{
  "playbackMode": "RANDOM",
  "interstitialEnabled": true,
  "interstitialStartHour": 12,
  "interstitialEndHour": 13,
  "volumeLevel": 75
}
```

**Response (200 OK):**
Returns the full updated `PlaylistConfig` object.

#### POST /api/upload

Uploads a media file using multipart form data.

**Request:** `multipart/form-data` with fields:
- `file` — the file binary
- `filename` — original filename (optional, auto-generated if missing)
- `autoAdd` — `true` to automatically add to playlist (optional, default `false`)

**Response (200 OK):**
```json
{
  "success": true,
  "filename": "video.mp4",
  "url": "/data/data/com.screenpulse.player/files/screenpulse_uploads/video.mp4",
  "size": 52428800,
  "autoAdded": true,
  "mediaItem": {
    "id": 8,
    "title": "video",
    "url": "/data/data/com.screenpulse.player/files/screenpulse_uploads/video.mp4",
    "type": "VIDEO",
    "durationSeconds": 0,
    "enabled": true,
    "sortOrder": 8,
    "createdAt": 1700000300000,
    "updatedAt": 1700000300000
  }
}
```

#### GET /api/scan

Triggers a scan of common media directories on the device. New files not already in the playlist are automatically added.

**Scanned directories:**
- `/storage/emulated/0/ScreenPulse/`
- `/storage/emulated/0/DCIM/`
- `/storage/emulated/0/Movies/`
- `/storage/emulated/0/Pictures/`
- App's internal upload directory

**Response (200 OK):**
```json
{
  "success": true,
  "scannedDirectories": "[/storage/emulated/0/ScreenPulse, ...]",
  "filesFound": 12
}
```

---

## 6. Playlist Playback Flow

The playback loop is managed by `PlaybackService` and follows this sequence:

```
┌─────────────────────────────┐
│    PlaybackService.start()   │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│  Load items from Room DB     │
│  (only enabled items)        │
│  Load PlaylistConfig         │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│  PlaylistManager initialized │
│  MediaController initialized │
│  Foreground notification     │
└──────────────┬──────────────┘
               │
               ▼
        ┌──────────────┐
        │ Playlist     │
        │ Empty?       │
        └──────┬───────┘
          Yes  │  No
          ┌────┴────┐
          ▼         ▼
    ┌──────────┐  ┌──────────────────┐
    │ Show QR  │  │ currentItem()    │
    │ Code     │  └────────┬─────────┘
    └──────────┘           │
                    ┌──────▼──────┐
                    │ In          │
                    │ Interstitial│
                    │ Window?     │
                    └──────┬──────┘
                      Yes  │  No
                      ┌────┴────┐
                      ▼         ▼
                ┌─────────┐ ┌───────────────┐
                │ Play    │ │ Play Normal   │
                │ Interst.│ │ Item          │
                │ Content │ │               │
                └────┬────┘ └───────┬───────┘
                     │              │
                     └──────┬───────┘
                            ▼
                   ┌─────────────────┐
                   │ Wait for       │
                   │ Playback End / │
                   │ Timer Expired  │
                   └────────┬────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │ advanceToNext() │
                   └────────┬────────┘
                            │
                     ┌──────┴──────┐
                     │ Returns     │
                     │ true?       │
                     └──────┬──────┘
                   Yes │       │ No (SEQUENTIAL)
                       ▼       ▼
               ┌─────────┐ ┌──────────────┐
               │ Loop    │ │ Stop & Show  │
               │ Back    │ │ QR Code      │
               │ (top)   │ │              │
               └─────────┘ └──────────────┘
```

### Item Transition Logic

When switching between items:

1. **Current item ends** (video finishes, image timer expires, etc.)
2. `PlaylistManager.advanceToNext()` is called
3. Based on `playbackMode`:
   - **LOOP**: `currentIndex = (currentIndex + 1) % items.size`
   - **SEQUENTIAL**: `currentIndex++` — returns `false` if exhausted
   - **RANDOM**: Pick a random index different from the current one
4. The new `currentItem()` is retrieved
5. `MediaController.prepareMedia()` is called with the new item
6. Playback continues

### Database Change Handling

The `MediaItemDao.getEnabledItems()` returns a `Flow<List<MediaItem>>`. The `PlaybackService` collects this flow, so any changes made through the web admin (add, delete, reorder, enable/disable) automatically trigger a `PlaylistManager.updateItems()` call.

---

## 7. Interstitial Schedule Flow

Interstitial playback allows a separate content set to play during a configured time window.

### Configuration

```json
{
  "interstitialEnabled": true,
  "interstitialStartHour": 12,
  "interstitialEndHour": 13,
  "interstitialPlaylistName": "lunch-break-ads"
}
```

### Flow Diagram

```
┌──────────────────────────────┐
│  ScheduleCheckWorker        │
│  (runs every 15 minutes)    │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  Read PlaylistConfig        │
│  Check interstitialEnabled  │
└──────────────┬───────────────┘
               │
          ┌────┴────┐
       Enabled  Disabled
          │         │
          ▼         ▼
   ┌────────────┐  ┌──────────────┐
   │ Get current│  │ Skip check   │
   │ hour from  │  │              │
   │ Calendar   │  │              │
   └─────┬──────┘  └──────────────┘
         │
    ┌────┴──────────────────┐
    │ Is currentHour within │
    │ [startHour, endHour)? │
    └────┬──────────────┬───┘
       Yes│              │No
         ▼              ▼
  ┌──────────────┐  ┌──────────────┐
  │ Enter        │  │ Exit         │
  │ Interstitial │  │ Interstitial │
  │ Mode         │  │ Mode         │
  └──────┬───────┘  └──────┬───────┘
         │                  │
         ▼                  ▼
  ┌──────────────┐  ┌──────────────┐
  │ PlaybackMgr  │  │ PlaybackMgr  │
  │ switches to  │  │ switches back│
  │ interstitial │  │ to normal    │
  │ playlist     │  │ playlist     │
  └──────────────┘  └──────────────┘
```

### Cross-Midnight Handling

If `startHour > endHour` (e.g., 22:00 to 06:00), the logic wraps around midnight:

```kotlin
return if (interstitialStartHour <= interstitialEndHour) {
    currentHour in interstitialStartHour until interstitialEndHour
} else {
    // Wraps around midnight
    currentHour >= interstitialStartHour || currentHour < interstitialEndHour
}
```

---

## 8. Security Considerations

### Current Security Posture

ScreenPulse TV Player is designed for **local network use** in controlled environments (offices, retail stores, home). The following security characteristics apply:

| Aspect | Status | Notes |
|--------|--------|-------|
| Network scope | LAN only | Web server binds to `0.0.0.0` — accessible from all network interfaces |
| Authentication | None | No login/password required |
| HTTPS/TLS | Not enabled | HTTP only (`usesCleartextTraffic = true`) |
| File access | Sandboxed | Uploaded files stored in app's private directory |
| API input validation | Basic | URL field validation, type enum validation |
| Filename sanitization | Basic | Regex-based character filtering on uploads |
| CORS | Permissive | All origins allowed for development convenience |

### Recommendations for Production

1. **Add authentication**: Implement a simple token or password-based auth for the web admin
2. **Bind to LAN interface**: Use `set InetAddress.getByName("0.0.0.0")` or bind to the specific LAN IP
3. **Enable HTTPS**: Add TLS support using NanoHTTPD's SSLSocketServerFactory
4. **Rate limiting**: Add request throttling to prevent API abuse
5. **Input validation**: Enhance URL validation to prevent SSRF or file path traversal
6. **File size limits**: Enforce maximum upload size to prevent disk exhaustion
7. **Content-Type validation**: Verify MIME type of uploaded files

---

## 9. Performance Optimization

### ExoPlayer Optimizations

| Optimization | Implementation | Impact |
|-------------|---------------|--------|
| Lazy preparation | Media is prepared only when transitioning to a new item | Reduced memory usage |
| Buffer size | 64 KB default HTTP buffer | Balanced memory/throughput |
| Wake mode | `WAKE_MODE_LOCAL` | Prevents CPU sleep during playback |
| Audio focus | Auto-managed by ExoPlayer | Proper audio ducking |
| Release on stop | `MediaController.release()` called in `onDestroy` | Prevents memory leaks |

### Database Optimizations

| Optimization | Implementation | Impact |
|-------------|---------------|--------|
| Reactive queries | `Flow`-based queries for playlist items | Real-time UI updates |
| Transactional reorder | `@Transaction` for batch sort order updates | Atomic consistency |
| Indexed fields | Primary keys and sort order indexed | Fast query execution |
| Singleton database | Single instance via `getInstance()` | Avoids connection overhead |

### Web Server Optimizations

| Optimization | Implementation | Impact |
|-------------|---------------|--------|
| Static file serving | Files loaded from APK assets | No disk I/O for web UI |
| SPA fallback | Unknown routes serve `index.html` | Clean client-side routing |
| In-memory API | NanoHTTPD runs in-process | Zero network latency within device |
| Small payload | Only essential fields returned | Fast response times |

### Memory Management

- **ExoPlayer instances are released** when the PlaybackService is destroyed
- **Room database instance** is a process-level singleton
- **Uploaded files** are stored on the app's internal file system (not in memory)
- **Coroutine scopes** are properly cancelled when components are destroyed
- **Image loading** is managed by the Activity's ImageView (no Glide/Picasso dependency)

---

## Appendix: File Format Support

### Supported Media Formats

| Category | Formats | Player Engine |
|----------|---------|---------------|
| Video | MP4, AVI, MKV, MOV, 3GP, WebM, FLV | ExoPlayer |
| Image | JPG, JPEG, PNG, GIF, BMP, WebP | ImageView + Timer |
| Presentation | PPT, PPTX, PDF | WebView / Timer |
| IPTV / HLS | M3U, M3U8 | ExoPlayer (HlsMediaSource) |
| Streaming | DASH (MPD), Progressive HTTP, RTSP | ExoPlayer |
