package com.app.surge.up

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class Launch(
    val oppId: String,
    val oppName: String,
    val map: String,
    val host: Boolean = false,
    val cancelable: Boolean = true,
    val oppChar: String = "PUNK"
)

fun startGame(ctx: Context, l: Launch) {
    UiState.gameOn = true
    val i = Intent(ctx, GameActivity::class.java)
    i.putExtra("opp", l.oppId)
    i.putExtra("oppName", l.oppName)
    i.putExtra("map", l.map)
    i.putExtra("oppChar", l.oppChar)
    ctx.startActivity(i)
}

@Composable
fun SurgeApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * UiState.uiScale, base.fontScale * UiState.uiScale)) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var stage by remember { mutableStateOf("loading") }
        var pending by remember { mutableStateOf<Launch?>(null) }
        val ban = UiState.ban
        Box(Modifier.fillMaxSize().background(CBg)) {
            if (ban != null) {
                BanScreen(ban)
            } else {
                when (stage) {
                    "loading" -> LoadingScreen("Memuat game...", 2600L) {
                        stage = if (Session.id.isEmpty()) "register" else "lobby"
                    }
                    "register" -> RegisterScreen {
                        UiState.hint = "Sebelum mulai, atur kontrol dan tampilan dulu di Pengaturan."
                        stage = "lobby"
                    }
                    "lobby" -> LobbyScreen(
                        onStart = { l ->
                            pending = l
                            stage = "launching"
                        },
                        onSearch = { stage = "search" }
                    )
                    "launching" -> {
                        val l = pending
                        val cancel: (() -> Unit)? = if (l != null && l.cancelable) ({ stage = "lobby" }) else null
                        LoadingScreen("Menyiapkan pertandingan...", 1300L, cancel) {
                            if (l == null) {
                                stage = "lobby"
                            } else if (l.host) {
                                scope.launch {
                                    if (Social.hostStart()) {
                                        startGame(ctx, l)
                                    } else {
                                        UiState.toast = "Gagal memulai. Coba lagi."
                                    }
                                    stage = "lobby"
                                }
                            } else {
                                startGame(ctx, l)
                                stage = "lobby"
                            }
                        }
                    }
                    "search" -> SearchScreen { stage = "lobby" }
                    else -> {}
                }
            }
        }
    }
    }
}

private val TIPS = listOf(
    "Undang teman lewat panel TEMAN, lalu tekan UNDANG.",
    "Buka kartu SQUAD untuk bikin room dan salin kodenya.",
    "Chat global hilang setelah 7 menit.",
    "Masuk zona aman lebih awal, jangan jadi sasaran empuk.",
    "Pakai musik? Atur di menu pengaturan."
)

// ---------- Loading screen ----------
@Composable
fun LoadingScreen(text: String, ms: Long, onCancel: (() -> Unit)? = null, onDone: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    val tip = remember { TIPS[(Math.random() * TIPS.size).toInt()] }
    val inf = rememberInfiniteTransition(label = "load")
    val zoom by inf.animateFloat(
        initialValue = 1.04f, targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "zoom"
    )
    val sweep by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        // kerja nyata saat loading: ambil kata terlarang dan cek ban (maks 4 detik)
        withTimeoutOrNull(4000L) {
            Moderation.refreshWords()
            if (Session.id.isNotEmpty()) UiState.ban = Moderation.checkBan(Session.id)
        }
        while (true) {
            val el = System.currentTimeMillis() - start
            progress = (el.toFloat() / ms.toFloat()).coerceAtMost(1f)
            if (el >= ms) break
            delay(40L)
        }
        onDone()
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.loading_art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom
                scaleY = zoom
            }
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xEE000000)))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xAA000000), Color.Transparent, Color(0x66000000)))))
        Column(
            Modifier.align(Alignment.Center).width(460.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("SURGE", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                Text("UP", color = CRed, fontSize = 40.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            }
            Spacer(Modifier.height(10.dp))
            Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(tip, color = CSoft, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x99000000))) {
                Box(
                    Modifier.fillMaxWidth(progress).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(CRedDark, CRed, Color(0xFFFF6B7A))))
                )
            }
            Spacer(Modifier.height(4.dp))
            Text((progress * 100f).toInt().toString() + "%", color = CSoft, fontSize = 11.sp)
        }
        if (onCancel != null) {
            Text(
                "BATAL", color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 30.dp)
                    .clip(SlantShape(10f)).background(Color(0xCC000000)).border(1.dp, CRed, SlantShape(10f))
                    .clickable { onCancel() }.padding(horizontal = 30.dp, vertical = 10.dp)
            )
        }
    }
}

// ---------- Daftar ----------
@Composable
fun RegisterScreen(onDone: () -> Unit) {
    var nick by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.loading_art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)))
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                Text("SURGE", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                Text("UP", color = CRed, fontSize = 36.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            }
            Text("Buat nama pemain", color = CSoft, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Field(nick, { if (it.length <= 14) nick = it }, "Nama kamu", Modifier.width(280.dp))
            Spacer(Modifier.height(12.dp))
            RedButton(if (busy) "..." else "MASUK", Modifier.width(280.dp), enabled = !busy) {
                if (nick.trim().length < 3) {
                    msg = "Minimal 3 huruf."
                } else {
                    busy = true
                    msg = ""
                    scope.launch {
                        val ok = Session.register(nick)
                        busy = false
                        if (ok) onDone() else msg = "Gagal terhubung ke server. Cek internet."
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(msg, color = Color(0xFFFF7A8A), fontSize = 13.sp)
        }
    }
}

// ---------- Ban ----------
@Composable
fun BanScreen(b: BanInfo) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            tick++
        }
    }
    val left = if (tick >= 0) Moderation.fmtLeft(b.until) else ""
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Akun kamu di-ban", color = CRed, fontSize = 28.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        Spacer(Modifier.height(10.dp))
        Text("Alasan: " + b.reason, color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text("Sisa waktu: " + left, color = CSoft, fontSize = 15.sp)
    }
}

// ---------- Cari lawan 2v2 (maks 40 detik, bisa dibatalkan) ----------
@Composable
fun SearchScreen(onBack: () -> Unit) {
    val room = UiState.room
    val code = room?.code
    var left by remember { mutableStateOf(40) }
    var state by remember { mutableStateOf("search") }
    var found by remember { mutableStateOf("") }
    var attempt by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            if (code != null) CoroutineScope(Dispatchers.IO).launch { Social.queueRemove(code) }
        }
    }
    LaunchedEffect(attempt) {
        state = "search"
        left = 40
        if (room == null || code == null) {
            state = "none"
            return@LaunchedEffect
        }
        val names = room.members.joinToString(" & ") { it.name }
        val start = System.currentTimeMillis()
        while (state == "search") {
            val el = ((System.currentTimeMillis() - start) / 1000L).toInt()
            left = 40 - el
            if (left <= 0) {
                state = "none"
                break
            }
            Social.queuePut(code, names)
            val other = Social.queueFind(code)
            if (other != null) {
                found = other
                state = "found"
                break
            }
            delay(1500L)
        }
        Social.queueRemove(code)
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.lobby_scene),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)))
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state == "search") {
                Text(left.toString() + "s", color = CRed, fontSize = 64.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                Text("MENCARI LAWAN 2VS2...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                Text("Butuh 2 tim (4 pemain). Pencarian berhenti otomatis setelah 40 detik.", color = CSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(18.dp))
                RedButton("BATAL", Modifier.width(220.dp)) { onBack() }
            } else if (state == "found") {
                Text("LAWAN DITEMUKAN", color = CGreen, fontSize = 26.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                Text(found, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "Pertandingan 2vs2 belum bisa dimulai: game saat ini baru mendukung 1vs1 online.",
                    color = CSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(18.dp))
                RedButton("KEMBALI", Modifier.width(220.dp)) { onBack() }
            } else {
                Text("LAWAN TIDAK DITEMUKAN", color = CRed, fontSize = 26.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                Text("Tidak ada tim lain yang mencari 2vs2 dalam 40 detik.", color = CSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RedButton("COBA LAGI", Modifier.width(160.dp)) { attempt++ }
                    RedButton("KEMBALI", Modifier.width(160.dp)) { onBack() }
                }
            }
        }
    }
}
