package com.screenpulse.tv.player

/**
 * 媒体类型枚举
 * 定义播放列表中支持的媒体类型
 */
enum class MediaType(val value: String) {
    /** 视频文件 - 本地或网络视频 */
    VIDEO("video"),

    /** 图片 - JPG, PNG, GIF 等 */
    IMAGE("image"),

    /** IPTV 直播流 - m3u8, HLS, RTSP */
    IPTV("iptv"),

    /** 网络流媒体 - YouTube, RTMP 等 */
    STREAM("stream"),

    /** 网页内容 - HTML 页面、在线文档 */
    WEBPAGE("webpage");

    companion object {
        /**
         * 从字符串解析媒体类型
         */
        fun fromValue(value: String): MediaType {
            return values().firstOrNull { it.value == value } ?: VIDEO
        }
    }
}
