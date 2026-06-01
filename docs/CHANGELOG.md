# Changelog

All notable changes to the ScreenPulse TV Player project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2024

### Added

#### Core Player
- Full-screen media playback engine using ExoPlayer (Media3)
- Video playback support for MP4, AVI, MKV, MOV, 3GP, WebM, FLV formats
- Image display support for JPG, JPEG, PNG, GIF, BMP, WebP with configurable duration
- Presentation display support for PPT, PPTX, PDF with configurable duration
- IPTV / HLS live stream playback (M3U, M3U8) via HlsMediaSource
- Generic network stream support (HTTP, HTTPS, DASH, RTSP)
- Automatic media type detection based on file extension
- Configurable playback speed (0.5x to 3.0x)
- Volume control (0–100)
- Audio focus management with automatic ducking
- Wake mode to prevent CPU sleep during playback

#### Playlist Management
- REST API for full CRUD operations on playlist items
- Drag-and-drop reordering with batch sort order updates
- Per-item enable/disable toggle for temporarily skipping content
- Per-item custom duration override for images and presentations
- Loop playback mode — continuous repeat from beginning
- Sequential playback mode — play through once and stop
- Random playback mode — shuffle items each cycle
- Real-time playlist synchronization via Room Flow observers

#### Scheduled Interstitials
- Time-windowed interstitial content scheduling
- Configurable start and end hours (0–23)
- Cross-midnight time window support (e.g., 22:00 to 06:00)
- WorkManager-based periodic schedule checking (every 15 minutes)
- Automatic switching between normal and interstitial playlists

#### Web Management Interface
- Embedded Vue 3 SPA served by NanoHTTPD on port 8080
- Element Plus UI component library with dark theme
- Dashboard page with real-time device status information
- Playlist management page with add/edit/delete/reorder controls
- Media library page with file upload (multipart form data)
- Auto-add uploaded files to playlist option
- Settings page for playback mode, interstitial config, and volume
- Responsive design for mobile, tablet, and desktop browsers
- Vue Router SPA client-side routing
- Axios-based API integration layer with error handling

#### Embedded Web Server
- NanoHTTPD HTTP server running in-process on port 8080
- Static file serving for Vue 3 SPA from APK assets
- SPA fallback routing (unknown routes serve index.html)
- RESTful API routing for all management endpoints
- Multipart form data parsing for file uploads
- Automatic MIME type detection for static files
- CORS headers for cross-origin browser requests

#### Data Persistence
- Room database with two tables: `media_items` and `playlist_config`
- MediaItemDao with reactive Flow queries and transactional batch operations
- PlaylistConfigDao for singleton configuration storage
- Automatic timestamp tracking (createdAt, updatedAt)

#### Device Integration
- QR code display on first launch with management URL
- QR code display when playlist is empty
- Auto-start on boot via BootReceiver
- Foreground service for uninterrupted playback
- Android TV Leanback launcher integration
- Automatic device IP address detection
- Local media file scanning (ScreenPulse, DCIM, Movies, Pictures directories)

#### API Endpoints
- `GET /api/status` — device status and configuration summary
- `GET /api/playlist` — list all playlist items
- `POST /api/playlist` — add a new playlist item
- `PUT /api/playlist/:id` — update an existing playlist item
- `DELETE /api/playlist/:id` — delete a playlist item and its local file
- `POST /api/playlist/reorder` — batch update sort order
- `PUT /api/config` — update playback configuration
- `POST /api/upload` — upload a media file
- `GET /api/scan` — trigger local media directory scan

#### Project Infrastructure
- Gradle-based build system with debug and release variants
- ProGuard rules for release builds
- View binding enabled
- Kotlin coroutines for asynchronous operations
- Gson for JSON serialization
- GitHub Actions CI/CD workflow
- Comprehensive project documentation (README, DESIGN, USAGE, CHANGELOG)

---

## [Unreleased]

_No unreleased changes yet._
