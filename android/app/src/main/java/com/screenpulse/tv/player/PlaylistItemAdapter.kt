package com.screenpulse.tv.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.screenpulse.tv.R

/**
 * 播放列表项适配器
 *
 * 适配 Android TV Leanback 框架的 Presenter 模式
 * 用于在 BrowseFragment 或相关 Leanback 组件中显示播放列表项
 */
class PlaylistItemAdapter : Presenter() {

    companion object {
        private const val TAG = "PlaylistItemAdapter"
    }

    /**
     * ViewHolder 模式 - 持有播放列表项视图
     */
    class ViewHolder(
        val view: View,
        val titleText: TextView,
        val subtitleText: TextView,
        val thumbnail: ImageView,
        val typeBadge: TextView,
        val durationText: TextView
    ) : Presenter.ViewHolder(view)

    /**
     * 创建播放列表项视图
     */
    override fun onCreateViewHolder(parentViewGroup: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parentViewGroup.context)
            .inflate(R.layout.item_playlist_card, parentViewGroup, false)

        return ViewHolder(
            view = view,
            titleText = view.findViewById(R.id.item_title),
            subtitleText = view.findViewById(R.id.item_subtitle),
            thumbnail = view.findViewById(R.id.item_thumbnail),
            typeBadge = view.findViewById(R.id.item_type_badge),
            durationText = view.findViewById(R.id.item_duration)
        )
    }

    /**
     * 绑定数据到视图
     */
    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is MediaItem) return

        val context = viewHolder.view.context

        // 设置标题
        viewHolder.titleText.text = item.title

        // 设置副标题 - 显示 URL 缩短版本
        viewHolder.subtitleText.text = shortenUrl(item.url)

        // 设置类型标签
        val typeLabel = when (item.type) {
            MediaType.VIDEO -> "视频"
            MediaType.IMAGE -> "图片"
            MediaType.IPTV -> "直播"
            MediaType.STREAM -> "流媒体"
            MediaType.WEBPAGE -> "网页"
        }
        viewHolder.typeBadge.text = typeLabel

        // 设置类型标签颜色
        val badgeColor = when (item.type) {
            MediaType.VIDEO -> R.color.badge_video
            MediaType.IMAGE -> R.color.badge_image
            MediaType.IPTV -> R.color.badge_iptv
            MediaType.STREAM -> R.color.badge_stream
            MediaType.WEBPAGE -> R.color.badge_webpage
        }
        viewHolder.typeBadge.setBackgroundColor(
            ContextCompat.getColor(context, badgeColor)
        )

        // 设置时长
        viewHolder.durationText.text = formatDuration(item.duration)

        // 设置缩略图
        loadThumbnail(viewHolder.thumbnail, item)

        // 设置启用状态视觉反馈
        viewHolder.view.alpha = if (item.enabled) 1.0f else 0.4f
    }

    /**
     * 卸载视图
     */
    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        Glide.with(viewHolder.thumbnail).clear(viewHolder.thumbnail)
    }

    /**
     * 加载缩略图
     * 视频显示第一帧/海报，图片直接显示，网页显示截图
     */
    private fun loadThumbnail(imageView: ImageView, item: MediaItem) {
        val placeholderRes = when (item.type) {
            MediaType.VIDEO -> R.drawable.bg_gradient
            MediaType.IMAGE -> R.drawable.bg_gradient
            MediaType.IPTV -> R.drawable.bg_gradient
            MediaType.STREAM -> R.drawable.bg_gradient
            MediaType.WEBPAGE -> R.drawable.bg_gradient
        }

        Glide.with(imageView.context)
            .load(item.url)
            .placeholder(placeholderRes)
            .error(placeholderRes)
            .centerCrop()
            .into(imageView)
    }

    /**
     * 缩短 URL 显示
     */
    private fun shortenUrl(url: String): String {
        return if (url.length > 40) {
            url.substring(0, 20) + "..." + url.substring(url.length - 15)
        } else {
            url
        }
    }

    /**
     * 格式化时长显示
     */
    private fun formatDuration(duration: Long?): String {
        if (duration == null || duration <= 0) return "自动"
        val minutes = duration / 60
        val seconds = duration % 60
        return if (minutes > 0) {
            "${minutes}分${seconds}秒"
        } else {
            "${seconds}秒"
        }
    }
}

/**
 * 需要添加的布局文件 item_playlist_card.xml 的说明：
 * - CardView 布局，包含缩略图、标题、副标题、类型标签、时长标签
 * - 适配 TV 遥控器焦点导航
 * - 足够大的触摸/焦点目标区域
 */
