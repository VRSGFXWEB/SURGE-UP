package com.app.surge.up

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val askNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        UiState.uiScale = Session.uiScale
        WindowCompat.setDecorFitsSystemWindows(window, false)
        Notifs.ensureChannel(this)
        hideBars()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { SurgeApp() }

        val ctx: Context = applicationContext
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var n = 0
                while (true) {
                    if (n % 6 == 0) Moderation.refreshWords()
                    n++
                    if (Session.id.isNotEmpty()) {
                        UiState.ban = Moderation.checkBan(Session.id)
                        Social.heartbeat()
                        pollNotifs(ctx)
                    }
                    delay(10_000L)
                }
            }
        }
    }

    private suspend fun pollNotifs(ctx: Context) {
        val list = Notifs.fetch() ?: return
        if (list.isEmpty()) return
        UiState.notifs = list
        val last = Session.lastNotif
        if (last.isEmpty()) {
            // pertama kali: tandai yang terbaru, tampilkan banner kalau masih baru (< 6 jam)
            val n = list[list.size - 1]
            Session.lastNotif = n.key
            if (Fb.now() - n.t < 6L * 3600L * 1000L) UiState.banner = n
            return
        }
        val fresh = list.filter { it.key > last }
        for (n in fresh) {
            Notifs.show(ctx, n)
            UiState.banner = n
        }
        if (fresh.isNotEmpty()) Session.lastNotif = fresh[fresh.size - 1].key
    }

    override fun onStart() {
        super.onStart()
        UiState.gameOn = false
        if (UiState.lobbyActive) Music.start(applicationContext)
    }

    override fun onStop() {
        super.onStop()
        Music.pause()
    }

    override fun onDestroy() {
        Music.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun hideBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }
}
