package com.screenpulse.tv.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.fragment.app.Fragment
import com.screenpulse.tv.R

/**
 * 网页显示片段
 *
 * 使用 WebView 显示网页内容，支持：
 * - 在线演示文稿 (PPT)
 * - HTML 页面
 * - Web 应用
 * - 带有 JavaScript 的交互内容
 *
 * 针对 Android TV 优化：
 * - 禁用触控缩放（TV 无触摸屏）
 * - 启用硬件加速
 * - 优化内存使用
 */
class WebPageDisplayFragment : Fragment() {

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_DURATION = "duration"
        private const val ARG_ENABLE_JS = "enable_js"

        fun newInstance(
            url: String,
            duration: Long = 30,
            enableJavaScript: Boolean = true
        ): WebPageDisplayFragment {
            return WebPageDisplayFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                    putLong(ARG_DURATION, duration)
                    putBoolean(ARG_ENABLE_JS, enableJavaScript)
                }
            }
        }
    }

    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null
    private var onPageLoaded: (() -> Unit)? = null
    private var onDisplayComplete: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 创建 RelativeLayout 作为容器（WebView + ProgressBar）
        val rootLayout = RelativeLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 加载进度条
        progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                8
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
            }
            isIndeterminate = false
            max = 100
        }
        rootLayout.addView(progressBar)

        // WebView
        webView = createWebView()
        rootLayout.addView(webView, 0)

        return rootLayout
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return
        val url = args.getString(ARG_URL) ?: return
        val enableJs = args.getBoolean(ARG_ENABLE_JS, true)

        configureWebView(webView!!, enableJs)
        webView?.loadUrl(url)
    }

    /**
     * 创建并配置 WebView
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        return WebView(requireContext()).apply {
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // 启用硬件加速
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // WebView 客户端 - 处理页面加载
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // 拦截外部链接，在新窗口中打开
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        view?.loadUrl(url)
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar?.visibility = View.VISIBLE
                    progressBar?.progress = 0
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar?.visibility = View.GONE
                    onPageLoaded?.invoke()
                }
            }

            // Chrome 客户端 - 进度回调
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    progressBar?.progress = newProgress
                    if (newProgress >= 100) {
                        progressBar?.visibility = View.GONE
                    }
                }
            }
        }
    }

    /**
     * 配置 WebView 设置
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView, enableJs: Boolean) {
        webView.settings.apply {
            // JavaScript 支持
            javaScriptEnabled = enableJs

            // DOM 存储（网页应用可能需要）
            domStorageEnabled = true

            // 数据库存储
            databaseEnabled = true

            // 缩放设置 - TV 不需要触控缩放
            setSupportZoom(true)
            builtInZoomControls = false  // 隐藏缩放按钮
            displayZoomControls = false

            // 视口设置
            loadWithOverviewMode = true
            useWideViewPort = true

            // 自适应屏幕
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

            // 文件访问
            allowFileAccess = true
            allowContentAccess = true

            // 自动加载图片
            loadsImagesAutomatically = true

            // 缓存模式 - 优先使用缓存
            cacheMode = WebSettings.LOAD_DEFAULT

            // 混合内容 - 允许 HTTPS 页面加载 HTTP 资源
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // 媒体自动播放
            mediaPlaybackRequiresUserGesture = false

            // User-Agent 添加 TV 标识
            userAgentString = userAgentString + " ScreenPulseTV/1.0"
        }
    }

    /**
     * 刷新当前页面
     */
    fun refreshPage() {
        webView?.reload()
    }

    /**
     * 页面加载完成回调
     */
    fun setOnPageLoaded(listener: (() -> Unit)?) {
        onPageLoaded = listener
    }

    /**
     * 设置显示完成回调
     */
    fun setOnDisplayComplete(listener: (() -> Unit)?) {
        onDisplayComplete = listener
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView?.apply {
            stopLoading()
            settings.javaScriptEnabled = false
            removeJavascriptInterface("Android")
            loadUrl("about:blank")
            destroy()
        }
        webView = null
        progressBar = null
    }
}
