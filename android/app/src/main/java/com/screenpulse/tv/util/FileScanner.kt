package com.screenpulse.tv.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.screenpulse.tv.db.entities.MediaEntity
import com.screenpulse.tv.player.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 媒体文件扫描工具
 *
 * 扫描设备本地存储中的媒体文件
 * 支持的视频格式：MP4, MKV, AVI, MOV, WMV, FLV, 3GP, TS, M3U8
 * 支持的图片格式：JPG, JPEG, PNG, GIF, WEBP, BMP
 */
object FileScanner {

    private const val TAG = "FileScanner"

    /** 支持的视频文件扩展名 */
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp",
        "ts", "m3u8", "webm", "mpg", "mpeg", "m4v", "vob"
    )

    /** 支持的图片文件扩展名 */
    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"
    )

    /** 扫描的根目录 */
    private val SCAN_DIRECTORIES = arrayOf(
        "Movies", "DCIM", "Pictures", "Download",
        "Video", "Videos", "screenpulse_media"
    )

    /**
     * 扫描设备存储中的所有媒体文件
     *
     * @return 扫描到的媒体实体列表
     */
    suspend fun scanDeviceMedia(context: Context): List<MediaEntity> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<MediaEntity>()

            try {
                // 扫描外部存储
                val externalStorage = System.getenv("EXTERNAL_STORAGE")
                val dirsToScan = mutableListOf<File>()

                // 添加外部存储根目录
                externalStorage?.let {
                    dirsToScan.add(File(it))
                }

                // 添加特定目录
                externalStorage?.let { root ->
                    SCAN_DIRECTORIES.forEach { dirName ->
                        dirsToScan.add(File(root, dirName))
                    }
                }

                // 递归扫描每个目录
                dirsToScan.forEach { dir ->
                    if (dir.exists() && dir.isDirectory) {
                        scanDirectory(dir, results)
                    }
                }

                // 同时扫描应用私有目录中的媒体
                val appMediaDir = File(context.filesDir, "screenpulse_media")
                if (appMediaDir.exists()) {
                    scanDirectory(appMediaDir, results)
                }

                Log.d(TAG, "扫描完成，找到 ${results.size} 个媒体文件")
            } catch (e: Exception) {
                Log.e(TAG, "扫描媒体文件失败", e)
            }

            results
        }
    }

    /**
     * 递归扫描目录
     */
    private fun scanDirectory(dir: File, results: MutableList<MediaEntity>) {
        if (!dir.canRead()) return

        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isHidden) continue

            if (file.isDirectory) {
                // 递归扫描子目录（限制深度）
                scanDirectory(file, results)
            } else if (file.isFile) {
                val extension = file.extension.lowercase()
                when {
                    extension in VIDEO_EXTENSIONS -> {
                        results.add(createMediaEntity(file, MediaType.VIDEO))
                    }
                    extension in IMAGE_EXTENSIONS -> {
                        results.add(createMediaEntity(file, MediaType.IMAGE))
                    }
                }
            }
        }
    }

    /**
     * 从文件创建 MediaEntity
     */
    private fun createMediaEntity(file: File, type: MediaType): MediaEntity {
        var duration: Long? = null
        var width: Int? = null
        var height: Int? = null

        // 如果是视频，提取元数据
        if (type == MediaType.VIDEO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)

                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationStr?.toLongOrNull()?.let { duration = it / 1000 } // 转为秒

                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                widthStr?.toIntOrNull()?.let { width = it }

                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                heightStr?.toIntOrNull()?.let { height = it }

                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "提取视频元数据失败: ${file.name}", e)
            }
        }

        return MediaEntity(
            title = file.name,
            type = type.value,
            url = "/media/${file.name}",
            filePath = file.absolutePath,
            fileSize = file.length(),
            duration = duration,
            width = width,
            height = height,
            createdAt = file.lastModified()
        )
    }

    /**
     * 检查文件是否为支持的媒体格式
     */
    fun isMediaFile(file: File): Boolean {
        if (!file.isFile) return false
        val extension = file.extension.lowercase()
        return extension in VIDEO_EXTENSIONS || extension in IMAGE_EXTENSIONS
    }

    /**
     * 获取文件媒体类型
     */
    fun getMediaType(file: File): MediaType? {
        val extension = file.extension.lowercase()
        return when {
            extension in VIDEO_EXTENSIONS -> MediaType.VIDEO
            extension in IMAGE_EXTENSIONS -> MediaType.IMAGE
            else -> null
        }
    }
}