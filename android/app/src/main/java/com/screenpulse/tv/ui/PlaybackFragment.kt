package com.screenpulse.tv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.screenpulse.tv.R

/**
 * 全屏播放片段
 *
 * 负责全屏显示播放内容，包括视频、图片和网页
 * 与 PlaybackViewModel 和 PlaybackEngine 协作：
 * - 提供播放视图容器
 * - 接收播放状态更新
 * - 处理遥控器按键事件
 */
class PlaybackFragment : Fragment() {

    companion object {
        fun newInstance(): PlaybackFragment {
            return PlaybackFragment()
        }
    }

    private lateinit var viewModel: PlaybackViewModel
    private var playbackContainer: FrameLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_playback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playbackContainer = view.findViewById(R.id.playback_container)

        viewModel = ViewModelProvider(requireActivity())[PlaybackViewModel::class.java]

        // 绑定播放引擎到容器
        viewModel.attachToContainer(playbackContainer!!)

        // 观察播放状态
        viewModel.playbackState.observe(viewLifecycleOwner) { state ->
            // 更新 UI 状态指示器（如果有）
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumePlayback()
    }

    override fun onPause() {
        super.onPause()
        // 不暂停播放 - 保持后台播放
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playbackContainer = null
        viewModel.detachFromContainer()
    }
}
