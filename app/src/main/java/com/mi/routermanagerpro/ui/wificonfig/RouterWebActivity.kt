package com.mi.routermanagerpro.ui.wificonfig

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.mi.routermanagerpro.databinding.ActivityRouterWebBinding

class RouterWebActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouterWebBinding

    companion object {
        private const val EXTRA_IP = "extra_router_ip"

        fun newIntent(context: Context, routerIp: String): Intent {
            return Intent(context, RouterWebActivity::class.java).apply {
                putExtra(EXTRA_IP, routerIp)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouterWebBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val routerIp = intent.getStringExtra(EXTRA_IP) ?: "192.168.100.1"
        binding.toolbar.title = routerIp
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupWebView(routerIp)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView(routerIp: String) {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = android.view.View.GONE
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility =
                    if (newProgress >= 100) android.view.View.GONE else android.view.View.VISIBLE
            }
        }

        val url = if (routerIp.startsWith("http")) routerIp else "http://$routerIp"
        binding.webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
