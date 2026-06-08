package com.screenpulse.tv.player

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.screenpulse.tv.db.entities.PlaylistEntity
import com.screenpulse.tv.player.MediaType
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
        private const val CARD_WIDTH = 300
        private const val CARD_HEIGHT = 170
    }

    /** ViewHolder 模式 - 持有播放列表项视图 */
    class ViewHolder(view: View) : Presenter.ViewHolder(view) {
        val titleText: TextView = view.findViewById(android.R.id.text1)
        val subtitleText: TextView = view.findViewById(android.R.id.text2)
        val thumbnail: ImageView = view.findViewById(android.R.id.icon)
        var typeBadge: TextView? = null
        var durationText: TextView? = null
    }

    /** 创建播放列表项视图 */
    override fun onCreateViewHolder(parentViewGroup: ViewGroup): ViewHolder {
        val context = parentViewGroup.context
        
        // 创建卡片视图
        val cardView = android.widget.FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                dpToPx(context, CARD_WIDTH),
                dpToPx(context, CARD_HEIGHT)
            )
            setBackgroundResource(R.drawable.bg_gradient)
            setPadding(dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16))
        }

        // 缩略图
        val thumbnail = ImageView(context).apply {
            id = android.R.id.icon
            layoutParams = ViewGroup.LayoutParams(
                dpToPx(context, 120),
                dpToPx(context, 120)
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.DKGRAY)
        }

        // 信息容器
        val infoContainer = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(context, 16), 0, 0, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // 标题
        val titleText = TextView(context).apply {
            id = android.R.id.text1
            textSize = 18f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // 副标题 (URL 缩短)
        val subtitleText = TextView(context).apply {
            id = android.R.id.text2
            textSize = 14f
            setTextColor(Color.GRAY)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // 类型标签
        val typeBadge = TextView(context).apply {
            id = View.generateViewId()
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dpToPx(context, 8), dpToPx(context, 4), dpToPx(context, 8), dpToPx(context, 4))
            setBackgroundResource(R.drawable.badge_background)
        }

        // 时长
        val durationText = TextView(context).apply {
            id = View.generateViewId()
            textSize = 14f
            setTextColor(Color.LTGRAY)
        }

        infoContainer.addView(titleText)
        infoContainer.addView(subtitleText)
        
        // 标签行容器
        val badgeContainer = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        badgeContainer.addView(typeBadge)
        badgeContainer.addView(durationText)
        infoContainer.addView(badgeContainer)

        cardView.addView(thumbnail)
        cardView.addView(infoContainer)

        return ViewHolder(cardView).apply {
            this.typeBadge = typeBadge
            this.durationText = durationText
        }
    }

    /** 绑定数据到视图 */
    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val vh = viewHolder as ViewHolder
        val playlistItem = item as? PlaylistEntity ?: return

        val context = vh.view.context

        // 设置标题
        vh.titleText.text = playlistItem.title

        // 设置副标题 - 显示 URL 缩短版本
        vh.subtitleText.text = shortenUrl(playlistItem.url)

        // 设置类型标签和颜色
        val (typeLabel, badgeColorRes) = when (MediaType.fromValue(playlistItem.type)) {
            MediaType.VIDEO -> "视频" to R.color.badge_video
            MediaType.IMAGE -> "图片" to R.color.badge_image
            MediaType.IPTV -> "直播" to R.color.badge_iptv
            MediaType.STREAM -> "流媒体" to R.color.badge_stream
            MediaType.WEBPAGE -> "网页" to R.color.badge_webpage
            else -> "未知" to R.color.badge_video
        }
        vh.typeBadge?.text = typeLabel
        vh.typeBadge?.setBackgroundColor(ContextCompat.getColor(context, badgeColorRes))

        // 设置时长
        vh.durationText?.text = formatDuration(playlistItem.duration)

        // 设置缩略图
        loadThumbnail(vh.thumbnail, playlistItem)

        // 设置启用状态视觉反馈
        vh.view.alpha = if (playlistItem.enabled) 1.0f else 0.4f
    }

    /** 卸载视图 */
    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val vh = viewHolder as ViewHolder
        Glide.with(vh.thumbnail).clear(vh.thumbnail)
    }

    /** 加载缩略图 */
    private fun loadThumbnail(imageView: ImageView, item: PlaylistEntity) {
        val placeholderRes = when (MediaType.fromValue(item.type)) {
            MediaType.VIDEO -> R.drawable.bg_gradient
            MediaType.IMAGE -> R.drawable.bg_gradient
            MediaType.IPTV -> R.drawable.bg_gradient
            MediaType.STREAM -> R.drawable.bg_gradient
            MediaType.WEBPAGE -> R.drawable.bg_gradient
            else -> R.drawable.bg_gradient
        }

        Glide.with(imageView.context)
            .load(item.url)
            .apply(RequestOptions()
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .centerCrop())
            .into(imageView)
    }

    /** 缩短 URL 显示 */
    private fun shortenUrl(url: String): String {
        return if (url.length > 40) {
            url.substring(0, 20) + "..." + url.substring(url.length - 15)
        } else {
            url
        }
    }

    /** 格式化时长显示 */
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

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}