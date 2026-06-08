package com.screenpulse.tv.player

import android.content.Context
import android.util.Log
import com.screenpulse.tv.ScreenPulseApp
import com.screenpulse.tv.db.AppDatabase
import com.screenpulse.tv.db.entities.PlaylistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * 播放列表管理器
 *
 * 管理播放列表的加载、播放模式切换和播放项管理
 * 支持 LOOP（循环）、SEQUENTIAL（顺序）、RANDOM（随机）三种播放模式
 */
class PlaylistManager(private val context: Context) {

    companion object {
        private const val TAG = "PlaylistManager"
    }

    /** 数据库实例 */
    private val database: AppDatabase = AppDatabase.getInstance(context)

    /** 当前播放模式 */
    var playMode: PlayMode = PlayMode.LOOP
        private set

    /** 当前活跃的播放列表缓存 */
    private val _activePlaylist = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val activePlaylistFlow: Flow<List<PlaylistEntity>> = _activePlaylist

    /**
     * 播放模式枚举
     */
    enum class PlayMode(val value: String) {
        /** 循环播放 */
        LOOP("loop"),
        /** 顺序播放 */
        SEQUENTIAL("sequential"),
        /** 随机播放 */
        RANDOM("random");

        companion object {
            fun fromValue(value: String): PlayMode {
                return values().firstOrNull { it.value == value } ?: LOOP
            }
        }
    }

    /**
     * 设置播放模式
     */
    fun setPlayMode(mode: PlayMode) {
        playMode = mode
        Log.d(TAG, "播放模式切换为: ${mode.value}")
    }

    /**
     * 设置播放模式（字符串）
     */
    fun setPlayMode(modeStr: String) {
        setPlayMode(PlayMode.fromValue(modeStr))
    }

    /**
     * 从数据库加载活跃的播放列表
     * 只加载启用状态的播放项，按顺序排列
     */
    suspend fun getActivePlaylist(): List<PlaylistEntity> {
        return withContext(Dispatchers.IO) {
            val items = database.playlistDao().getActivePlaylistItems()
            _activePlaylist.value = items
            items
        }
    }

    /**
     * 刷新播放列表缓存
     */
    suspend fun refreshPlaylist() {
        getActivePlaylist()
    }

    /**
     * 获取活跃播放列表数量
     */
    suspend fun getActivePlaylistCount(): Int {
        return withContext(Dispatchers.IO) {
            database.playlistDao().getActivePlaylistCount()
        }
    }

    /**
     * 设置活跃播放列表（用于恢复中断前的列表）
     */
    fun setActivePlaylist(playlist: List<PlaylistEntity>) {
        _activePlaylist.value = playlist
    }

    /**
     * 添加播放项到数据库
     */
    suspend fun addPlaylistItem(item: PlaylistEntity): Long {
        return withContext(Dispatchers.IO) {
            val id = database.playlistDao().insert(item)
            refreshPlaylist()
            Log.d(TAG, "添加播放项: ${item.title} (id=$id)")
            id
        }
    }

    /**
     * 批量添加播放项
     */
    suspend fun addPlaylistItems(items: List<PlaylistEntity>) {
        withContext(Dispatchers.IO) {
            database.playlistDao().insertAll(items)
            refreshPlaylist()
            Log.d(TAG, "批量添加 ${items.size} 个播放项")
        }
    }

    /**
     * 更新播放项
     */
    suspend fun updatePlaylistItem(item: PlaylistEntity) {
        withContext(Dispatchers.IO) {
            database.playlistDao().update(item)
            refreshPlaylist()
            Log.d(TAG, "更新播放项: ${item.title}")
        }
    }

    /**
     * 删除播放项
     */
    suspend fun deletePlaylistItem(id: Long) {
        withContext(Dispatchers.IO) {
            database.playlistDao().deleteById(id)
            refreshPlaylist()
            Log.d(TAG, "删除播放项: id=$id")
        }
    }

    /**
     * 清空播放列表
     */
    suspend fun clearPlaylist() {
        withContext(Dispatchers.IO) {
            database.playlistDao().deleteAll()
            refreshPlaylist()
            Log.d(TAG, "播放列表已清空")
        }
    }

    /**
     * 重新排序播放列表
     * @param orderedIds 按新顺序排列的 ID 列表
     */
    suspend fun reorderPlaylist(orderedIds: List<Long>) {
        withContext(Dispatchers.IO) {
            orderedIds.forEachIndexed { index, id ->
                database.playlistDao().updateOrder(id, index)
            }
            refreshPlaylist()
            Log.d(TAG, "播放列表已重新排序")
        }
    }

    /**
     * 启用/禁用播放项
     */
    suspend fun togglePlaylistItem(id: Long, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            database.playlistDao().updateEnabled(id, enabled)
            refreshPlaylist()
            Log.d(TAG, "播放项 $id ${if (enabled) "启用" else "禁用"}")
        }
    }

    /**
     * 替换整个播放列表
     * 用于 Web API 批量更新
     */
    suspend fun replacePlaylist(newItems: List<PlaylistEntity>) {
        withContext(Dispatchers.IO) {
            database.playlistDao().deleteAll()
            database.playlistDao().insertAll(newItems)
            refreshPlaylist()
            Log.d(TAG, "播放列表已替换，共 ${newItems.size} 项")
        }
    }

    /**
     * 获取播放列表的 LiveData（实时更新）
     */
    fun observePlaylist(): androidx.lifecycle.LiveData<List<PlaylistEntity>> {
        return database.playlistDao().getActivePlaylistFlow()
    }
}
