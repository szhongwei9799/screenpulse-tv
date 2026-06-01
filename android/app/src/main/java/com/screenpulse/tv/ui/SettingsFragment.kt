package com.screenpulse.tv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.screenpulse.tv.R
import com.screenpulse.tv.player.PlaylistManager
import com.screenpulse.tv.server.WebServerManager
import com.screenpulse.tv.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置片段
 *
 * 提供应用设置界面：
 * - 播放模式选择（循环/顺序/随机）
 * - 默认图片/网页显示时长
 * - Web 服务器端口
 * - 开机自启动
 * - 设备信息
 */
class SettingsFragment : Fragment() {

    companion object {
        fun newInstance(): SettingsFragment {
            return SettingsFragment()
        }
    }

    private lateinit var playlistManager: PlaylistManager
    private lateinit var serverInfoText: TextView
    private lateinit var playModeText: TextView
    private lateinit var playlistCountText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistManager = PlaylistManager(requireContext())

        // 绑定视图
        serverInfoText = view.findViewById(R.id.settings_server_info)
        playModeText = view.findViewById(R.id.settings_play_mode)
        playlistCountText = view.findViewById(R.id.settings_playlist_count)

        // 设置按钮
        view.findViewById<Button>(R.id.btn_mode_loop)?.setOnClickListener {
            setPlayMode(PlaylistManager.PlayMode.LOOP)
        }
        view.findViewById<Button>(R.id.btn_mode_sequential)?.setOnClickListener {
            setPlayMode(PlaylistManager.PlayMode.SEQUENTIAL)
        }
        view.findViewById<Button>(R.id.btn_mode_random)?.setOnClickListener {
            setPlayMode(PlaylistManager.PlayMode.RANDOM)
        }

        // 显示当前设置
        displayCurrentSettings()
    }

    /**
     * 设置播放模式
     */
    private fun setPlayMode(mode: PlaylistManager.PlayMode) {
        lifecycleScope.launch {
            playlistManager.setPlayMode(mode)
            displayCurrentSettings()
        }
    }

    /**
     * 显示当前设置
     */
    private fun displayCurrentSettings() {
        val ip = NetworkUtils.getDeviceIpAddress()
        val port = (context?.let { (it.applicationContext as? com.screenpulse.tv.ScreenPulseApp)?.webServerManager?.port }) ?: 8080

        serverInfoText.text = "Web 管理面板: http://$ip:$port"

        playModeText.text = "播放模式: ${
            when (playlistManager.playMode) {
                PlaylistManager.PlayMode.LOOP -> "循环"
                PlaylistManager.PlayMode.SEQUENTIAL -> "顺序"
                PlaylistManager.PlayMode.RANDOM -> "随机"
            }
        }"

        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                playlistManager.getActivePlaylistCount()
            }
            playlistCountText.text = "播放列表: $count 项"
        }
    }
}
