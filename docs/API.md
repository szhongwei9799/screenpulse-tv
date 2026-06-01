# ScreenPulse TV — REST API 文档

> **版本**: v1.0.0  
> **基础 URL**: `http://{设备IP}:8080`  
> **协议**: HTTP  
> **数据格式**: JSON

---

## 📋 目录

- [认证说明](#-认证说明)
- [通用约定](#-通用约定)
- [设备状态](#1-设备状态)
- [播放列表](#2-播放列表)
- [媒体库](#3-媒体库)
- [定时任务](#4-定时任务)
- [播放控制](#5-播放控制)
- [系统设置](#6-系统设置)
- [错误码](#-错误码)

---

## 🔐 认证说明

ScreenPulse TV 默认**不启用认证**，适用于受信任的局域网环境。

| 配置项 | 说明 |
|--------|------|
| 默认状态 | 无需认证，直接访问 |
| 密码保护 | 可通过 `POST /api/settings` 设置管理密码 |
| 传输安全 | 建议仅在局域网内使用，生产环境建议配置 HTTPS |

> ⚠️ **安全提示**：由于使用 HTTP 协议且默认无认证，请确保设备仅部署在可信的局域网中。

---

## 📐 通用约定

### 请求头

| Header | 值 | 说明 |
|--------|-----|------|
| `Content-Type` | `application/json` | JSON 请求体（文件上传除外） |
| `Accept` | `application/json` | 期望 JSON 响应 |

### 文件上传

文件上传使用 `multipart/form-data` 格式。

### 日期格式

所有时间戳使用**毫秒级 Unix 时间戳**（Long）。

### 响应格式

成功响应：
```json
{
  "success": true,
  "message": "操作成功",
  "data": { ... }
}
```

错误响应：
```json
{
  "success": false,
  "error": "错误描述",
  "code": 400
}
```

---

## 1. 设备状态

### `GET /api/status`

获取设备基本信息和当前播放状态。

#### 请求示例

```bash
curl http://192.168.1.100:8080/api/status
```

#### 响应示例

```json
{
  "deviceName": "Xiaomi Mi Box S",
  "deviceIp": "192.168.1.100",
  "port": 8080,
  "status": "online",
  "playlistCount": 5,
  "playbackState": "playing",
  "version": "1.0.0",
  "timestamp": "2026-06-01 14:30:00"
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `deviceName` | String | 设备型号名称 |
| `deviceIp` | String | 设备局域网 IP 地址 |
| `port` | Integer | Web 服务器端口 |
| `status` | String | 设备状态（`online` / `offline`） |
| `playlistCount` | Integer | 当前播放列表项目数 |
| `playbackState` | String | 播放状态（`idle` / `playing` / `paused`） |
| `version` | String | 应用版本号 |
| `timestamp` | String | 服务器当前时间 |

---

## 2. 播放列表

### `GET /api/playlist`

获取当前播放列表所有项目。

#### 请求示例

```bash
curl http://192.168.1.100:8080/api/playlist
```

#### 响应示例

```json
[
  {
    "id": 1,
    "title": "产品宣传片.mp4",
    "type": "video",
    "url": "/media/1715000000_产品宣传片.mp4",
    "duration": null,
    "enabled": true,
    "volume": 80,
    "order": 0,
    "createdAt": 1715000000000
  },
  {
    "id": 2,
    "title": "门店活动海报.jpg",
    "type": "image",
    "url": "/media/1715000100_门店活动海报.jpg",
    "duration": 10,
    "enabled": true,
    "volume": 100,
    "order": 1,
    "createdAt": 1715000100000
  },
  {
    "id": 3,
    "title": "CCTV-1 直播",
    "type": "iptv",
    "url": "http://live.example.com/cctv1.m3u8",
    "duration": 300,
    "enabled": true,
    "volume": 70,
    "order": 2,
    "createdAt": 1715000200000
  },
  {
    "id": 4,
    "title": "公司官网",
    "type": "webpage",
    "url": "https://www.example.com",
    "duration": 30,
    "enabled": false,
    "volume": 100,
    "order": 3,
    "createdAt": 1715000300000
  }
]
```

#### 播放项类型说明

| type 值 | 说明 |
|---------|------|
| `video` | 本地视频或网络视频 |
| `image` | 图片文件（JPG/PNG/GIF/WebP） |
| `iptv` | IPTV 直播流（HLS/m3u8） |
| `stream` | 网络流媒体（RTSP/RTMP） |
| `webpage` | 网页/PPT 在线展示 |

---

### `POST /api/playlist`

更新整个播放列表（批量替换）。

#### 请求示例

```bash
curl -X POST http://192.168.1.100:8080/api/playlist \
  -H "Content-Type: application/json" \
  -d '[
    {
      "title": "宣传片.mp4",
      "type": "video",
      "url": "/media/1715000000_宣传片.mp4",
      "duration": null,
      "enabled": true,
      "volume": 80
    },
    {
      "title": "促销海报.jpg",
      "type": "image",
      "url": "/media/1715000100_促销海报.jpg",
      "duration": 15,
      "enabled": true,
      "volume": 100
    }
  ]'
```

#### 请求体字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 否 | 已有项的 ID（为空则创建新项） |
| `title` | String | 是 | 显示标题 |
| `type` | String | 是 | 媒体类型（video/image/iptv/stream/webpage） |
| `url` | String | 是 | 媒体 URL 或本地路径 |
| `duration` | Long | 否 | 显示时长（秒），`null` 表示视频自动时长 |
| `enabled` | Boolean | 否 | 是否启用，默认 `true` |
| `volume` | Integer | 否 | 音量 0-100，默认 `100` |

#### 响应示例

```json
{
  "success": true,
  "message": "播放列表已更新"
}
```

---

### `PUT /api/playlist/{id}`

更新单个播放项。

#### 请求示例

```bash
curl -X PUT http://192.168.1.100:8080/api/playlist/3 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "CCTV-1 高清直播",
    "volume": 90,
    "duration": 600
  }'
```

#### 响应示例

```json
{
  "success": true,
  "message": "播放项已更新",
  "data": {
    "id": 3,
    "title": "CCTV-1 高清直播",
    "volume": 90,
    "duration": 600
  }
}
```

---

### `DELETE /api/playlist/{id}`

删除指定播放项。

#### 请求示例

```bash
curl -X DELETE http://192.168.1.100:8080/api/playlist/4
```

#### 响应示例

```json
{
  "success": true,
  "message": "已删除"
}
```

---

### `POST /api/playlist/reorder`

重新排序播放列表项目。

#### 请求示例

```bash
curl -X POST http://192.168.1.100:8080/api/playlist/reorder \
  -H "Content-Type: application/json" \
  -d '{
    "orderedIds": [3, 1, 4, 2]
  }'
```

#### 请求体字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `orderedIds` | Long[] | 是 | 按新顺序排列的 ID 数组 |

#### 响应示例

```json
{
  "success": true,
  "message": "顺序已更新"
}
```

---

## 3. 媒体库

### `GET /api/media`

获取媒体库中所有媒体资源列表。

#### 请求示例

```bash
curl http://192.168.1.100:8080/api/media
```

#### 响应示例

```json
[
  {
    "id": 1,
    "title": "1715000000_产品宣传片.mp4",
    "type": "video",
    "url": "/media/1715000000_产品宣传片.mp4",
    "fileSize": 52428800,
    "thumbnailUrl": "/media/thumbnails/1715000000_产品宣传片.jpg",
    "createdAt": 1715000000000
  },
  {
    "id": 2,
    "title": "1715000100_门店活动海报.jpg",
    "type": "image",
    "url": "/media/1715000100_门店活动海报.jpg",
    "fileSize": 2097152,
    "thumbnailUrl": null,
    "createdAt": 1715000100000
  }
]
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 媒体 ID |
| `title` | String | 文件名/标题 |
| `type` | String | 媒体类型 |
| `url` | String | 访问路径（相对路径或完整 URL） |
| `fileSize` | Long | 文件大小（字节） |
| `thumbnailUrl` | String | 缩略图路径（可能为 null） |
| `createdAt` | Long | 创建时间戳 |

---

### `POST /api/media/upload`

上传媒体文件到设备。

#### 请求格式

使用 `multipart/form-data` 格式上传。

#### 请求示例

```bash
# 上传视频文件
curl -X POST http://192.168.1.100:8080/api/media/upload \
  -F "file=@/path/to/video.mp4"

# 上传图片
curl -X POST http://192.168.1.100:8080/api/media/upload \
  -F "file=@/path/to/poster.jpg"
```

#### 限制说明

| 项目 | 限制 |
|------|------|
| 单文件最大大小 | **50 MB** |
| 支持的视频格式 | MP4, MKV, AVI |
| 支持的图片格式 | JPG, JPEG, PNG, GIF, WebP |

#### 响应示例

```json
{
  "success": true,
  "message": "上传成功",
  "data": {
    "id": 0,
    "title": "1715000000_video.mp4",
    "type": "video",
    "url": "/media/1715000000_video.mp4",
    "fileSize": 52428800
  }
}
```

> 💡 **提示**：文件上传成功后会**自动添加到播放列表**。图片默认显示 10 秒。

---

### `POST /api/media/url`

通过 URL 添加网络媒体资源。

#### 请求示例

```bash
# 添加 IPTV 直播源
curl -X POST http://192.168.1.100:8080/api/media/url \
  -H "Content-Type: application/json" \
  -d '{
    "url": "http://live.example.com/channel1.m3u8",
    "type": "iptv",
    "title": "CCTV-1"
  }'

# 添加 RTSP 流
curl -X POST http://192.168.1.100:8080/api/media/url \
  -H "Content-Type: application/json" \
  -d '{
    "url": "rtsp://192.168.1.50:8554/live",
    "type": "stream",
    "title": "监控摄像头-门口"
  }'

# 添加网页
curl -X POST http://192.168.1.100:8080/api/media/url \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.example.com",
    "type": "webpage",
    "title": "公司官网"
  }'
```

#### 请求体字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | String | ✅ | 媒体资源的完整 URL |
| `type` | String | 否 | 媒体类型（默认 `video`） |
| `title` | String | 否 | 显示标题（默认从 URL 提取） |

#### 响应示例

```json
{
  "success": true,
  "message": "添加成功",
  "data": {
    "title": "CCTV-1",
    "type": "iptv",
    "url": "http://live.example.com/channel1.m3u8"
  }
}
```

> 💡 **提示**：通过 URL 添加的媒体同样会自动添加到播放列表。图片默认 10 秒，网页默认 30 秒。

---

### `DELETE /api/media/{id}`

删除媒体库中的指定媒体资源。

#### 请求示例

```bash
curl -X DELETE http://192.168.1.100:8080/api/media/2
```

#### 响应示例

```json
{
  "success": true,
  "message": "媒体已删除"
}
```

> ⚠️ **注意**：删除本地文件媒体时，物理文件也会被一并删除。同时会从播放列表中移除。

---

## 4. 定时任务

### `GET /api/schedule`

获取所有定时任务。

#### 请求示例

```bash
curl http://192.168.1.100:8080/api/schedule
```

#### 响应示例

```json
[
  {
    "id": 1,
    "name": "早间问候",
    "cron": "08:30",
    "contentJson": "[{\"title\":\"早安视频\",\"type\":\"video\",\"url\":\"/media/morning.mp4\",\"duration\":30}]",
    "repeat": true,
    "completed": false,
    "enabled": true,
    "priority": 0,
    "lastExecutedAt": 1715000000000,
    "nextTriggerAt": 1715100000000,
    "createdAt": 1714900000000,
    "updatedAt": 1714900000000
  }
]
```

#### Cron 表达式格式

| 格式 | 示例 | 说明 |
|------|------|------|
| `HH:mm` | `"09:30"` | 每天 9:30 |
| `d HH:mm` | `"1 09:00"` | 每周第 1 天 9:00 |
| `HH:mm HH:mm` | `"09:00 18:00"` | 每天 9:00 到 18:00 范围 |
| 时间戳 | `"1715000000"` | 指定时刻（秒级） |

---

### `POST /api/schedule`

创建定时插播任务。

#### 请求示例

```bash
curl -X POST http://192.168.1.100:8080/api/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "name": "午间广告轮播",
    "cron": "12:00",
    "repeat": true,
    "priority": 1,
    "contentJson": "[{\"title\":\"午餐优惠\",\"type\":\"image\",\"url\":\"/media/lunch_ad.jpg\",\"duration\":15},{\"title\":\"饮品推荐\",\"type\":\"image\",\"url\":\"/media/drink_ad.jpg\",\"duration\":10}]"
  }'
```

#### 请求体字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | String | ✅ | 任务名称 |
| `cron` | String | ✅ | 定时表达式（支持 HH:mm、cron-like） |
| `repeat` | Boolean | 否 | 是否重复执行，默认 `true` |
| `enabled` | Boolean | 否 | 是否启用，默认 `true` |
| `priority` | Integer | 否 | 优先级（越大越高），默认 `0` |
| `contentJson` | String | 否 | 插播内容的 JSON 序列化（`List<PlaylistEntity>`） |

#### 响应示例

```json
{
  "success": true,
  "message": "定时任务已创建"
}
```

---

### `PUT /api/schedule/{id}`

更新定时任务。

#### 请求示例

```bash
curl -X PUT http://192.168.1.100:8080/api/schedule/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "早间问候（更新）",
    "cron": "09:00",
    "enabled": true
  }'
```

#### 响应示例

```json
{
  "success": true,
  "message": "定时任务已更新"
}
```

---

### `DELETE /api/schedule/{id}`

删除定时任务。

#### 请求示例

```bash
curl -X DELETE http://192.168.1.100:8080/api/schedule/1
```

#### 响应示例

```json
{
  "success": true,
  "message": "定时任务已删除"
}
```

---

## 5. 播放控制

### `POST /api/control/play`

开始/恢复播放。

#### 请求示例

```bash
curl -X POST http://192.168.1.100:8080/api/control/play
```

#### 响应示例

```json
{
  "success": true,
  "state": "playing"
}
```

---

### `POST /api/control/pause`

暂停播放。

#### 请求示例

```bash
curl -X POST http://192.168.1.100:8080/api/control/pause
```

#### 响应示例

```json
{
  "success": true,
  "state": "paused"
}
```

---

### `POST /api/control/skip`

跳到播放列表的下一项。

#### 请求示例

```bash
curl -X POST http://192.168.1.100:8080/api/control/skip
```

#### 响应示例

```json
{
  "success": true,
  "action": "skip"
}
```

---

### `POST /api/control/previous`

跳到播放列表的上一项。

#### 请求示例

```bash
curl -X POST http://192.168.1.100:8080/api/control/previous
```

#### 响应示例

```json
{
  "success": true,
  "action": "previous"
}
```

---

## 6. 系统设置

### `GET /api/settings`

获取当前系统设置。

#### 请求示例

```bash
curl http://192.168.1.100:8080/api/settings
```

#### 响应示例

```json
{
  "playMode": "loop",
  "transitionEffect": "fade",
  "defaultImageDuration": 10,
  "defaultWebpageDuration": 30,
  "volume": 100,
  "autoStartOnBoot": true,
  "serverPort": 8080
}
```

#### 设置字段说明

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `playMode` | String | `"loop"` | 播放模式：`loop`（循环）/ `sequential`（顺序）/ `random`（随机） |
| `transitionEffect` | String | `"fade"` | 切换效果：`fade`（淡入淡出）/ `none`（无） |
| `defaultImageDuration` | Integer | `10` | 图片默认显示时长（秒） |
| `defaultWebpageDuration` | Integer | `30` | 网页默认显示时长（秒） |
| `volume` | Integer | `100` | 默认音量（0-100） |
| `autoStartOnBoot` | Boolean | `true` | 开机自动启动播放 |
| `serverPort` | Integer | `8080` | Web 服务器端口 |

---

### `POST /api/settings`

更新系统设置。

#### 请求示例

```bash
curl -X POST http://192.168.1.100:8080/api/settings \
  -H "Content-Type: application/json" \
  -d '{
    "playMode": "random",
    "transitionEffect": "fade",
    "defaultImageDuration": 15,
    "volume": 80
  }'
```

#### 请求体字段

只需提交需要修改的字段，未提交的字段保持不变。

#### 响应示例

```json
{
  "success": true,
  "message": "设置已更新"
}
```

---

## ❌ 错误码

| HTTP 状态码 | 说明 |
|-------------|------|
| `200` | 请求成功 |
| `400` | 请求参数错误（缺少必填字段、格式错误等） |
| `404` | 资源不存在（端点、媒体文件等） |
| `413` | 上传文件超过大小限制（50MB） |
| `500` | 服务器内部错误 |

#### 错误响应示例

```json
{
  "success": false,
  "error": "文件大小超过限制 (50MB)",
  "code": 400
}
```

```json
{
  "success": false,
  "error": "文件不存在: missing_video.mp4",
  "code": 404
}
```

---

## 📡 Web 管理面板

除了 REST API，ScreenPulse TV 还提供了一个完整的 Web 管理面板（单页应用）：

| URL | 说明 |
|-----|------|
| `http://{IP}:8080/` | Web 管理面板 |
| `http://{IP}:8080/admin` | 管理面板（别名） |
| `http://{IP}:8080/index.html` | 管理面板（别名） |

Web 面板内置了以上所有 API 的调用，提供可视化操作界面，支持：
- 设备状态查看
- 文件拖拽上传
- 播放列表可视化管理（排序、启用/禁用、删除）
- 远程播放控制
- 自动刷新（5 秒轮询）

---

> 📖 更多使用说明请参阅 [USAGE.md](./USAGE.md)  
> 📐 技术设计请参阅 [DESIGN.md](./DESIGN.md)
