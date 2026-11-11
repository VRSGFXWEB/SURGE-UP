package com.app.surge.up

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader

/** Gameplay 3D (Three.js) dari assets/index.html di dalam WebView. */
class GameActivity : ComponentActivity() {
    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideBars()

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        val w = WebView(this)
        web = w
        w.setBackgroundColor(Color.BLACK)
        w.overScrollMode = View.OVER_SCROLL_NEVER
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.setOnLongClickListener { true }
        w.addJavascriptInterface(Bridge(), "Android")
        w.webChromeClient = WebChromeClient()
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val u = request?.url ?: return null
                return loader.shouldInterceptRequest(u)
            }
        }

        val name = Uri.encode(Session.name)
        val id = Uri.encode(Session.id)
        val opp = intent.getStringExtra("opp") ?: ""
        val oppName = intent.getStringExtra("oppName") ?: ""
        val map = intent.getStringExtra("map") ?: "TRAINING"
        val oppChar = intent.getStringExtra("oppChar") ?: "PUNK"
        val base = "https://appassets.androidplatform.net/assets/index.html?name=" + name + "&id=" + id +
            "&gq=" + Session.gq + "&gs=" + (if (Session.gs) "1" else "0") + "&gf=" + (if (Session.gf) "1" else "0") + "&wp=" + Session.weapon + "&char=" + Session.char
        val url = if (opp.isNotEmpty()) {
            // 1v1 online lawan teman: pakai netcode game (me, opp, db)
            base + "&enemy=" + Uri.encode(oppName) + "&me=" + id + "&opp=" + Uri.encode(opp) + "&db=" + Uri.encode(Fb.GAME) + "&oc=" + oppChar
        } else if (map == "TRAINING") {
            base + "&enemy=BOT&mode=train"
        } else {
            base + "&enemy=BOT&mode=solo"
        }
        w.loadUrl(url)
        setContentView(w)
    }

    inner class Bridge {
        @JavascriptInterface
        fun exitToLobby() {
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun isOnline(): Boolean {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return false
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }

    private fun hideBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }
}
