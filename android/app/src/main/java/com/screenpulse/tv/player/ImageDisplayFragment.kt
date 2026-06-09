package com.screenpulse.tv.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.screenpulse.tv.R

/**
 * 图片显示片段
 *
 * 专门用于在播放引擎中显示图片内容
 * 支持以下转场效果：
 * - 淡入淡出
 * - 缩放
 * - 滑动
 * 配合 Glide 的 crossFade 效果实现平滑切换
 */
class ImageDisplayFragment : Fragment() {

    companion object {
        private const val ARG_IMAGE_URL = "image_url"
        private const val ARG_DURATION = "duration"
        private const val ARG_TRANSITION = "transition"

        /** 转场效果类型 */
        enum class TransitionType {
            FADE,        // 淡入淡出
            ZOOM,        // 缩放
            SLIDE_LEFT,  // 左滑
            SLIDE_RIGHT, // 右滑
            NONE         // 无动画
        }

        fun newInstance(
            imageUrl: String,
            duration: Long = 10,
            transition: TransitionType = TransitionType.FADE
        ): ImageDisplayFragment {
            return ImageDisplayFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_URL, imageUrl)
                    putLong(ARG_DURATION, duration)
                    putString(ARG_TRANSITION, transition.name)
                }
            }
        }
    }

    private var imageView: ImageView? = null
    private var onDisplayComplete: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 全屏 ImageView
        return ImageView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageView = this
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return
        val imageUrl = args.getString(ARG_IMAGE_URL) ?: return
        val duration = args.getLong(ARG_DURATION, 10)
        val transitionType = try {
            TransitionType.valueOf(args.getString(ARG_TRANSITION, "FADE"))
        } catch (e: Exception) {
            TransitionType.FADE
        }

        loadImage(imageUrl, transitionType, duration)
    }

    /**
     * 使用 Glide 加载图片并应用转场效果
     */
    private fun loadImage(url: String, transitionType: TransitionType, duration: Long) {
        val imgView = imageView ?: return

        // 先隐藏视图
        imgView.alpha = 0f

        Glide.with(this)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.bg_gradient)
            .error(R.drawable.bg_gradient)
            .crossFade()
            .into(imgView)

        // 根据转场类型应用不同的进入动画
        when (transitionType) {
            TransitionType.FADE -> applyFadeIn(imgView)
            TransitionType.ZOOM -> applyZoomIn(imgView)
            TransitionType.SLIDE_LEFT -> applySlideIn(imgView, fromLeft = true)
            TransitionType.SLIDE_RIGHT -> applySlideIn(imgView, fromLeft = false)
            TransitionType.NONE -> imgView.alpha = 1f
        }
    }

    /**
     * 淡入效果
     */
    private fun applyFadeIn(view: View) {
        view.animate()
            .alpha(1f)
            .setDuration(600)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(null)
            .start()
    }

    /**
     * 缩放效果
     */
    private fun applyZoomIn(view: View) {
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    /**
     * 滑动效果
     */
    private fun applySlideIn(view: View, fromLeft: Boolean) {
        val translationX = if (fromLeft) -view.width.toFloat() else view.width.toFloat()
        view.translationX = translationX
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(700)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    /**
     * 应用退出动画
     */
    fun applyExitAnimation(onComplete: () -> Unit) {
        val view = view ?: run {
            onComplete()
            return
        }

        view.animate()
            .alpha(0f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onComplete()
                }
            })
            .start()
    }

    /**
     * 设置显示完成回调
     */
    fun setOnDisplayComplete(listener: (() -> Unit)?) {
        onDisplayComplete = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val imgView = imageView
        imageView = null
        if (imgView != null) {
            Glide.with(this).clear(imgView)
        }
    }
}
