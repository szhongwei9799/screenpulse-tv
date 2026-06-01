package com.screenpulse.tv.ui

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.screenpulse.tv.MainActivity
import com.screenpulse.tv.R
import com.screenpulse.tv.db.AppDatabase
import com.screenpulse.tv.util.NetworkUtils
import com.screenpulse.tv.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首次运行着陆页
 *
 * 当播放列表为空时显示此页面
 * 包含：
 * - 设备 IP 地址显示
 * - Web 管理面板 URL
 * - 二维码（扫码快速访问管理面板）
 * - 动态背景动画
 * - 自动检测播放列表变化并切换到播放页面
 */
class LandingFragment : Fragment() {

    companion object {
        /** Fragment 工厂方法 */
        fun newInstance(): LandingFragment {
            return LandingFragment()
        }
    }

    private lateinit var ipAddressText: TextView
    private lateinit var manageUrlText: TextView
    private lateinit var qrCodeImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var statusText: TextView

    // 自动刷新检查间隔（毫秒）
    private val refreshInterval = 3000L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_landing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 绑定视图
        titleText = view.findViewById(R.id.landing_title)
        subtitleText = view.findViewById(R.id.landing_subtitle)
        ipAddressText = view.findViewById(R.id.landing_ip_address)
        manageUrlText = view.findViewById(R.id.landing_manage_url)
        qrCodeImage = view.findViewById(R.id.landing_qr_code)
        statusText = view.findViewById(R.id.landing_status)

        // 显示设备信息
        displayDeviceInfo()

        // 启动背景动画
        startBackgroundAnimation(view)

        // 开始自动检测播放列表
        startPlaylistPolling()
    }

    override fun onResume() {
        super.onResume()
        displayDeviceInfo()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 停止轮询（由 lifecycleScope 自动取消）
    }

    /**
     * 显示设备信息
     * 包括 IP 地址、管理 URL 和 QR 码
     */
    private fun displayDeviceInfo() {
        val ip = NetworkUtils.getDeviceIpAddress()
        val port = 8080
        val manageUrl = "http://$ip:$port"

        // 设置 IP 地址
        ipAddressText.text = "IP: $ip"

        // 设置管理 URL
        manageUrlText.text = "管理地址: $manageUrl"

        // 生成并显示 QR 码
        lifecycleScope.launch {
            val qrBitmap = withContext(Dispatchers.IO) {
                QrCodeGenerator.generate(manageUrl, 400, 400)
            }
            qrBitmap?.let { qrCodeImage.setImageBitmap(it) }

            statusText.text = "✓ Web 管理面板就绪"
            statusText.setTextColor(resources.getColor(R.color.success_green, null))
        }
    }

    /**
     * 启动背景动画
     * 渐变动画效果
     */
    private fun startBackgroundAnimation(view: View) {
        val background = view.findViewById<View>(R.id.landing_background) ?: view

        // 渐变透明度动画
        ObjectAnimator.ofFloat(background, "alpha", 0.7f, 1.0f).apply {
            duration = 2000
            interpolator = AccelerateDecelerateInterpolator()
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    /**
     * 启动播放列表轮询
     * 定时检查是否有新的播放项被添加
     */
    private fun startPlaylistPolling() {
        lifecycleScope.launch {
            while (true) {
                try {
                    val count = withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(requireContext())
                            .playlistDao()
                            .getActivePlaylistCount()
                    }

                    if (count > 0) {
                        // 播放列表有内容了，通知 Activity 切换
                        statusText.text = "检测到播放列表，即将开始播放..."
                        activity?.let { act ->
                            if (act is MainActivity) {
                                // Activity 的 observePlaylistChanges 会自动处理切换
                            }
                        }
                        break
                    }
                } catch (e: Exception) {
                    // 静默处理，继续轮询
                }
                kotlinx.coroutines.delay(refreshInterval)
            }
        }
    }
}
