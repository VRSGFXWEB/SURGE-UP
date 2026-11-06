package com.app.surge.up

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LobbyScreen(onStart: (Launch) -> Unit, onSearch: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dlg by remember { mutableStateOf("") }
    var chatTab by remember { mutableStateOf("GLOBAL") }
    var chatOpen by remember { mutableStateOf(false) }
    val room = UiState.room
    val me = Session.id
    val invite = UiState.invite
    val banner = UiState.banner

    LaunchedEffect(Unit) { Music.start(ctx) }
    LaunchedEffect(Unit) { Social.syncProfile() }
    DisposableEffect(Unit) {
        UiState.lobbyActive = true
        onDispose { UiState.lobbyActive = false }
    }

    // sinkron teman, room, undangan; mulai pertandingan saat host menekan MULAI
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            Social.pollRoom()
            Social.pollInvites()
            val st = UiState.room
            if (st != null && st.members.size == 2 && st.go.isNotEmpty() && st.go != Social.lastGo && Social.goFresh(st.go)) {
                Social.lastGo = st.go
                val opp = st.members.firstOrNull { it.id != me }
                if (opp != null) onStart(Launch(opp.id, opp.name, st.map, false, false))
            }
            if (n % 5 == 0) {
                val f = Social.loadFriends()
                if (f != null) UiState.friends = f
                val r = Social.loadReqs()
                if (r != null) UiState.reqs = r
            }
            n++
            delay(2000L)
        }
    }
    // chat global
    LaunchedEffect(Unit) {
        while (true) {
            val m = Chat.fetch()
            if (m != null) UiState.messages = m
            delay(3000L)
        }
    }
    // chat tim (di dalam room)
    LaunchedEffect(room?.code) {
        val code = room?.code
        if (code == null) {
            UiState.teamMessages = emptyList()
        } else {
            while (true) {
                val m = Chat.fetch("rooms/" + code + "/chat")
                if (m != null) UiState.teamMessages = m
                delay(3000L)
            }
        }
    }
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(5000L)
            if (UiState.banner === banner) UiState.banner = null
        }
    }
    LaunchedEffect(UiState.toast) {
        if (UiState.toast.isNotEmpty()) {
            delay(2200L)
            UiState.toast = ""
        }
    }

    val doStart: () -> Unit = {
        scope.launch {
            val st = UiState.room
            if (st == null || st.members.size < 2) {
                if (st != null && st.mode == "2v2") {
                    UiState.toast = "2v2 butuh 2 pemain. Undang teman dulu."
                } else {
                    onStart(Launch("", "", st?.map ?: Social.MAP_TRAIN))
                }
            } else if (st.host == me) {
                val other = st.members.firstOrNull { it.id != me }
                if (other == null || !other.ready) {
                    UiState.toast = "Tunggu teman siap dulu."
                } else if (st.mode == "2v2") {
                    onSearch()
                } else {
                    onStart(Launch(other.id, other.name, st.map, true, true))
                }
            } else {
                val mine = st.members.firstOrNull { it.id == me }
                Social.setReady(!(mine?.ready ?: false))
            }
        }
    }

    BackHandler(enabled = UiState.page != "home" || UiState.viewFriend != null) {
        if (UiState.viewFriend != null) UiState.viewFriend = null else UiState.page = "home"
    }
    val page = UiState.page
    val vf = UiState.viewFriend
    val glLobby = rememberGl(0)
    val mateM = room?.members?.firstOrNull { it.id != me }
    glLobby.chars = if (mateM != null) arrayOf(Session.char, mateM.ch) else arrayOf(Session.char)
    glLobby.tapEmote = true
    LaunchedEffect(UiState.hint) {
        if (UiState.hint.isNotEmpty()) {
            delay(9000L)
            UiState.hint = ""
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (vf != null) {
            ProfilePage(vf, vf.id == Session.id) { UiState.viewFriend = null }
        } else if (page == "char") {
            CharPage { UiState.page = "home" }
        } else if (page == "weapon") {
            WeaponPage { UiState.page = "home" }
        } else if (page == "friends") {
            FriendsPage { UiState.page = "home" }
        } else if (page == "settings") {
            SettingsPage { UiState.page = "home" }
        } else {
            Gl3D(glLobby, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xB3000000), Color(0x00000000), Color(0x00000000), Color(0xB3000000)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x80000000), Color.Transparent, Color(0x66000000)))))
            // nama pemain di bawah karakter
            Column(Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.2f), verticalArrangement = Arrangement.Top) {
                Row(horizontalArrangement = Arrangement.spacedBy(if (mateM != null) 90.dp else 0.dp)) {
                    NameTag(Session.name)
                    if (mateM != null) NameTag(mateM.name)
                }
            }

            TopBar(dlg = { dlg = it })

            // menu kiri
            Box(Modifier.align(Alignment.CenterStart).padding(top = 40.dp)) {
                NavColumn(chatOpen) { chatOpen = !chatOpen }
            }
            // chat global: muncul saat tombol CHAT ditekan
            if (chatOpen) {
                ChatPanel(
                    chatTab, { chatTab = it }, { chatOpen = false },
                    Modifier.align(Alignment.BottomStart).padding(start = 152.dp, bottom = 12.dp).width(300.dp).height(230.dp)
                )
            }

            if (!chatOpen) ChatPreview(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 12.dp))

            // kolom kanan: teman + squad + mulai
            Column(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(300.dp).padding(top = 64.dp, bottom = 10.dp, end = 10.dp)
            ) {
                TeamPanel(room, { UiState.page = "friends" }, Modifier.weight(1f).fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                SquadCard(room, me) { dlg = "room" }
                Spacer(Modifier.height(8.dp))
                StartButton(room, me, doStart)
            }
        }

        AnimatedVisibility(
            visible = UiState.hint.isNotEmpty() && invite == null && page == "home",
            enter = slideInVertically() + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
        ) {
            HintBanner(UiState.hint, onOpen = {
                UiState.hint = ""
                UiState.page = "settings"
            }, onClose = { UiState.hint = "" })
        }

        if (invite != null) {
            InviteBanner(
                invite,
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                onAccept = {
                    scope.launch {
                        val err = Social.acceptInvite(invite)
                        UiState.toast = if (err.isEmpty()) "Masuk ke room " + invite.fromName else err
                    }
                },
                onDecline = { scope.launch { Social.declineInvite(invite) } }
            )
        } else if (banner != null) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp).width(380.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color(0xF0140A0E))
                    .border(1.dp, CRed, RoundedCornerShape(8.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar("!", 34)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(banner.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text(banner.body, color = CSoft, fontSize = 12.sp, maxLines = 2)
                }
            }
        }

        if (UiState.toast.isNotEmpty()) {
            Text(
                UiState.toast, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0xE6000000)).padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }

    when (dlg) {
        "add" -> AddFriendDialog { dlg = "" }
        "room" -> RoomDialog { dlg = "" }
        "reqs" -> ReqsDialog { dlg = "" }
        "settings" -> SettingsDialog { dlg = "" }
        else -> {}
    }
}

// ---------- Bar atas ----------
@Composable
fun TopBar(dlg: (String) -> Unit) {
    val toastReq = UiState.reqs.size
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.clip(SlantShape(12f)).background(CPanel).border(1.dp, Color(0x33FFFFFF), SlantShape(12f))
                .clickable { UiState.viewFriend = Friend(Session.id, Session.name, true, Session.ava, Session.bio, Session.char) }
                .padding(start = 18.dp, end = 26.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(Session.name, 40, ava = Session.ava)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(Session.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
                Text("ID " + Session.id, color = CSoft, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(14.dp))
        CurrencyPill(true, "99999")
        Spacer(Modifier.width(8.dp))
        CurrencyPill(false, "99999")
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(SlantShape(12f)).background(CPanel).padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopIcon(Icons.Filled.AccountCircle, toastReq > 0) { UiState.page = "friends" }
            TopIcon(Icons.Filled.Settings, false) { UiState.page = "settings" }
        }
    }
}

@Composable
fun Pill(value: String) {
    Text(
        value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(CPanel)
            .border(1.dp, CLine, RoundedCornerShape(6.dp)).padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
fun TopIcon(icon: ImageVector, dot: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = 6.dp).clickable { onClick() }.padding(6.dp)) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        if (dot) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(CRed))
    }
}

// ---------- Menu kiri ----------
@Composable
fun NavColumn(chatOpen: Boolean, onChat: () -> Unit) {
    Column(Modifier.width(144.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NavItem("home", "BERANDA", !chatOpen) { }
        NavItem("char", "KARAKTER", false) { UiState.page = "char" }
        NavItem("gun", "SENJATA", false) { UiState.page = "weapon" }
        NavItem("shirt", "VAULT", false) { UiState.toast = "Vault segera hadir" }
        NavItem("chat", "CHAT", chatOpen) { onChat() }
    }
}

@Composable
fun NavItem(kind: String, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(SlantShape(10f))
            .background(if (on) Brush.horizontalGradient(listOf(CRed, CRedDark)) else Brush.horizontalGradient(listOf(Color(0x99000000), Color(0x00000000))))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavIcon(kind)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun ChatPanel(tab: String, onTab: (String) -> Unit, onClose: () -> Unit, modifier: Modifier) {
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val room = UiState.room
    val msgs = if (tab == "TIM") UiState.teamMessages else UiState.messages
    val listState = rememberLazyListState()
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    LaunchedEffect(msgs.size, tab) {
        if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1)
    }

    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(CPanel).border(1.dp, CLine, RoundedCornerShape(8.dp))
    ) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF0E0E13))) {
            ChatTab("GLOBAL", tab == "GLOBAL") { onTab("GLOBAL") }
            ChatTab("TIM", tab == "TIM") { onTab("TIM") }
            Box(Modifier.clickable { onClose() }.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            if (tab == "TIM" && room == null) {
                Text("Masuk room dulu untuk chat tim.", color = CSoft, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(msgs, key = { it.key }) { m ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Box(Modifier.clickable {
                                scope.launch {
                                    val pf = Social.loadProfile(m.id)
                                    if (pf != null) UiState.viewFriend = pf
                                }
                            }) { Avatar(m.name, 30, nameColor(m.id)) }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Row {
                                    Text(m.name, color = nameColor(m.id), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(fmt.format(Date(m.t)), color = CSoft, fontSize = 10.sp)
                                }
                                Text(
                                    m.text, color = Color.White, fontSize = 13.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1B1B22)).padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (status.isNotEmpty()) Text(status, color = Color(0xFFFF7A8A), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(input, { if (it.length <= 100) input = it }, "Ketik pesan...", Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.clip(SlantShape(8f)).background(if (busy) CRedDark else CRed)
                    .clickable(enabled = !busy && !(tab == "TIM" && room == null)) {
                        val clean = input.replace(Regex("\\s+"), " ").trim()
                        if (clean.isNotEmpty()) {
                            busy = true
                            status = ""
                            scope.launch {
                                val path = if (tab == "TIM" && room != null) "rooms/" + room.code + "/chat" else "chat/global"
                                val b = Moderation.checkBan(Session.id)
                                if (b != null) {
                                    UiState.ban = b
                                } else if (Moderation.hasBad(clean)) {
                                    UiState.ban = Moderation.autoBan(Session.id)
                                } else if (Chat.send(Session.id, Session.name, clean, path)) {
                                    input = ""
                                    val m = Chat.fetch(path)
                                    if (m != null) {
                                        if (tab == "TIM") UiState.teamMessages = m else UiState.messages = m
                                    }
                                } else {
                                    status = "Pesan gagal terkirim."
                                }
                                busy = false
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ChatTab(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).background(if (on) CRed else Color.Transparent).clickable { onClick() }.padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (on) Color.White else CSoft, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 12.sp)
    }
}

// ---------- Panel teman ----------
@Composable
fun TeamPanel(room: RoomState?, onAdd: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val friends = UiState.friends
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(CPanel).border(1.dp, CLine, RoundedCornerShape(8.dp)).padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TEMAN", color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text(friends.size.toString(), color = CGold, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier.fillMaxWidth().clip(SlantShape(14f)).background(CGold).clickable { onAdd() }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFF1A1205), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("TAMBAH", color = Color(0xFF1A1205), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        if (friends.isEmpty()) {
            Text("Belum ada teman. Tekan TAMBAH lalu masukkan ID teman.", color = CSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(friends, key = { it.id }) { f ->
                    val inRoom = room != null && room.members.any { it.id == f.id }
                    FriendRow(f, inRoom) {
                        scope.launch { UiState.toast = Social.inviteFriend(f) }
                    }
                }
            }
        }
    }
}

@Composable
fun FriendRow(f: Friend, inRoom: Boolean, onInvite: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clickable { UiState.viewFriend = f }) { Avatar(f.name, 42, nameColor(f.id), f.ava) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (f.online) CGreen else Color(0xFF6A6A78)))
                Spacer(Modifier.width(5.dp))
                Text(if (f.online) "Online" else "Offline", color = if (f.online) CGreen else CSoft, fontSize = 11.sp)
            }
        }
        if (inRoom) {
            Text(
                "SQUAD", color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 12.sp,
                modifier = Modifier.clip(SlantShape(8f)).background(Color(0xFF26262E)).padding(horizontal = 14.dp, vertical = 6.dp)
            )
        } else {
            Text(
                "UNDANG", color = if (f.online) Color(0xFF1A1205) else CSoft, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 12.sp,
                modifier = Modifier.clip(SlantShape(8f)).background(if (f.online) CGold else Color(0xFF1B1B22))
                    .clickable { onInvite() }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// ---------- Kartu squad + MULAI ----------
@Composable
fun SquadCard(room: RoomState?, me: String, onOpen: () -> Unit) {
    val mate = room?.members?.firstOrNull { it.id != me }
    val mateReady = mate != null && (mate.id == room?.host || mate.ready)
    val sub = if (room == null) {
        "TAP UNTUK BUAT ROOM"
    } else if (mate == null) {
        "KODE " + room.code + " \u00B7 MENUNGGU TEMAN"
    } else if (mateReady) {
        "TEMAN SIAP"
    } else {
        "MENUNGGU TEMAN SIAP"
    }
    Row(
        Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF0B0B10), Color(0xFF2E0A12))))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp)).clickable { onOpen() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3A3A44), Color(0xFF15151B)))),
            contentAlignment = Alignment.Center
        ) {
            Text(room?.map?.take(1) ?: "S", color = CRed, fontWeight = FontWeight.Black, fontSize = 24.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("SQUAD " + (room?.mode ?: "1v1").uppercase().replace("V", "VS"), color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 18.sp)
            Text(sub, color = if (sub == "TEMAN SIAP") CGreen else CSoft, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun StartButton(room: RoomState?, me: String, onClick: () -> Unit) {
    val isGuest = room != null && room.host != me && room.members.size >= 2
    val mine = room?.members?.firstOrNull { it.id == me }
    val label = if (isGuest) (if (mine?.ready == true) "BATAL SIAP" else "SIAP") else "MULAI"
    Box(
        Modifier.fillMaxWidth().height(78.dp).clip(SlantShape(22f))
            .background(Brush.horizontalGradient(listOf(CRedDark, CRed, Color(0xFFFF4A5E))))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 36.sp)
    }
}

@Composable
fun InviteBanner(inv: Invite, modifier: Modifier, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier.width(430.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xF0140A0E))
            .border(1.dp, CRed, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(inv.fromName, 40, nameColor(inv.fromId))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("UNDANGAN SQUAD", color = CGold, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 12.sp)
            Text(inv.fromName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text("mengajak masuk squad (maks 2 orang)", color = CSoft, fontSize = 11.sp)
        }
        GoldSlantButton("TERIMA", Modifier) { onAccept() }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.clip(CircleShape).background(Color(0xFF26262E)).clickable { onDecline() }.padding(8.dp)) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

// ---------- Dialog ----------
@Composable
fun Sheet(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(480.dp).heightIn(max = 330.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E0E13))
                .border(1.dp, CRed, RoundedCornerShape(10.dp)).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).clickable { onClose() }.padding(6.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        }
    }
}

@Composable
fun AddFriendDialog(onClose: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Sheet("TAMBAH TEMAN", onClose) {
        Text("Masukkan ID pemain teman kamu. ID kamu: " + Session.id, color = CSoft, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Field(id, { if (it.length <= 10) id = it.filter { c -> c.isDigit() } }, "ID teman", Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        RedButton(if (busy) "..." else "KIRIM PERMINTAAN", Modifier.fillMaxWidth(), enabled = !busy) {
            busy = true
            scope.launch {
                msg = Social.sendFriendReq(id)
                busy = false
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = CGold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun ReqsDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    Sheet("PERMINTAAN TEMAN", onClose) {
        val list = UiState.reqs
        if (list.isEmpty()) Text("Tidak ada permintaan.", color = CSoft, fontSize = 13.sp)
        for (r in list) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(r.name, 36, nameColor(r.fromId))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("ID " + r.fromId, color = CSoft, fontSize = 11.sp)
                }
                GoldSlantButton("TERIMA", Modifier) {
                    scope.launch {
                        Social.acceptReq(r.fromId)
                        UiState.reqs = UiState.reqs.filter { it.fromId != r.fromId }
                        val f = Social.loadFriends()
                        if (f != null) UiState.friends = f
                    }
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.clip(CircleShape).background(Color(0xFF26262E)).clickable {
                    scope.launch {
                        Social.declineReq(r.fromId)
                        UiState.reqs = UiState.reqs.filter { it.fromId != r.fromId }
                    }
                }.padding(8.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun MailDialog(onClose: () -> Unit) {
    Sheet("PESAN DARI ADMIN", onClose) {
        val list = UiState.notifs
        if (list.isEmpty()) Text("Belum ada pesan.", color = CSoft, fontSize = 13.sp)
        for (n in list.reversed()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(n.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(n.body, color = CSoft, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SettingsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var music by remember { mutableStateOf(Session.music) }
    Sheet("PENGATURAN", onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Musik lobby", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            RedButton(if (music) "ON" else "OFF") {
                music = !music
                Session.music = music
                if (music) Music.start(ctx) else Music.pause()
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Surge Up Beta \u00B7 ID " + Session.id, color = CSoft, fontSize = 12.sp)
    }
}

@Composable
fun RoomDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val room = UiState.room
    val me = Session.id
    var join by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var selMap by remember { mutableStateOf(Social.MAP_TRAIN) }
    var selMode by remember { mutableStateOf("1v1") }
    val map = room?.map ?: selMap
    val hasMate = room != null && room.members.size >= 2

    Sheet("ROOM", onClose) {
        Text("MAP", color = CSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            MapChip("TRAINING", "Menara Jam \u00B7 solo vs bot", map == Social.MAP_TRAIN, !hasMate) {
                if (room == null) selMap = Social.MAP_TRAIN else scope.launch { Social.setMap(Social.MAP_TRAIN) }
            }
            MapChip("ARENA", "Arena utama \u00B7 solo & online", map == Social.MAP_ARENA, true) {
                if (room == null) selMap = Social.MAP_ARENA else scope.launch { Social.setMap(Social.MAP_ARENA) }
            }
        }
        Text("MODE", color = CSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            for (md in listOf("1v1", "2v2")) {
                Chip(md.uppercase().replace("V", "VS"), (room?.mode ?: selMode) == md) {
                    if (room == null) selMode = md else scope.launch { Social.setMode(md) }
                }
            }
        }
        if ((room?.mode ?: selMode) == "2v2") {
            Text("2VS2: butuh 2 pemain di room. Pencarian lawan maksimal 40 detik dan bisa dibatalkan.", color = CGold, fontSize = 11.sp)
        }
        if (hasMate && map == Social.MAP_TRAIN) {
            Text("Map TRAINING hanya untuk solo. Pilih ARENA untuk bermain online.", color = CGold, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (room == null) {
            RedButton("BUAT ROOM", Modifier.fillMaxWidth()) {
                scope.launch {
                    val c = Social.createRoom(selMap)
                    if (c != null && selMode == "2v2") Social.setMode("2v2")
                    msg = if (c == null) "Gagal membuat room." else "Room dibuat: " + c
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("ATAU GABUNG PAKAI KODE", color = CSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(join, { if (it.length <= 5) join = it.uppercase() }, "KODE 5 HURUF", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                GoldSlantButton("GABUNG", Modifier) {
                    scope.launch { msg = Social.joinRoom(join).ifEmpty { "Berhasil masuk room." } }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("KODE ROOM", color = CSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(room.code, color = CGold, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                RedButton("SALIN") {
                    clip.setText(AnnotatedString(room.code))
                    msg = "Kode disalin."
                }
            }
            Spacer(Modifier.height(8.dp))
            for (m in room.members) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.name, 34, nameColor(m.id))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name + if (m.id == me) " (kamu)" else "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(if (m.id == room.host) "HOST" else "ANGGOTA", color = CSoft, fontSize = 11.sp)
                    }
                    Text(
                        if (m.id == room.host) "SIAP" else if (m.ready) "SIAP" else "BELUM",
                        color = if (m.id == room.host || m.ready) CGreen else CSoft, fontWeight = FontWeight.Black, fontSize = 12.sp
                    )
                }
            }
            if (room.members.size < 2) {
                Text("Undang teman dari panel TEMAN (tombol UNDANG) atau bagikan kode ini.", color = CSoft, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            Spacer(Modifier.height(6.dp))
            RedButton("KELUAR ROOM", Modifier.fillMaxWidth()) {
                scope.launch {
                    Social.leaveRoom()
                    msg = "Keluar dari room."
                }
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = CGold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun MapChip(title: String, sub: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(210.dp).clip(RoundedCornerShape(8.dp))
            .background(if (on) Color(0xFF2A0C14) else Color(0xFF15151B))
            .border(1.5.dp, if (on) CRed else CLine, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }.padding(10.dp)
    ) {
        Text(title, color = if (enabled) Color.White else CSoft, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 16.sp)
        Text(sub, color = CSoft, fontSize = 11.sp)
    }
}

@Composable
fun ChatButton(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 6.dp, top = 6.dp).width(64.dp).height(40.dp).clip(SlantShape(10f))
            .background(if (on) Brush.horizontalGradient(listOf(CRed, CRedDark)) else Brush.horizontalGradient(listOf(Color(0xCC000000), Color(0x66000000))))
            .border(1.dp, if (on) CRed else Color(0xFF55555F), SlantShape(10f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(Color.White, Offset(0f, 0f), Size(w, h * 0.72f), CornerRadius(w * 0.2f, w * 0.2f))
            val p = Path()
            p.moveTo(w * 0.22f, h * 0.68f)
            p.lineTo(w * 0.22f, h)
            p.lineTo(w * 0.52f, h * 0.70f)
            p.close()
            drawPath(p, Color.White)
            for (i in 0..2) drawCircle(if (on) CRed else Color(0xFF111116), w * 0.07f, Offset(w * (0.28f + 0.22f * i), h * 0.36f))
        }
    }
}

@Composable
fun CurrencyPill(gold: Boolean, value: String) {
    Row(
        Modifier.clip(SlantShape(10f)).background(CPanel).border(1.dp, Color(0x33FFFFFF), SlantShape(10f))
            .padding(start = 18.dp, end = 22.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            if (gold) {
                drawCircle(Color(0xFFFFC933), w * 0.5f)
                drawCircle(Color(0xFFB8860B), w * 0.32f, style = Stroke(3f))
            } else {
                val p = Path()
                p.moveTo(w * 0.5f, 0f)
                p.lineTo(w, h * 0.38f)
                p.lineTo(w * 0.5f, h)
                p.lineTo(0f, h * 0.38f)
                p.close()
                drawPath(p, Color(0xFFFF5C70))
                val q = Path()
                q.moveTo(w * 0.5f, 0f)
                q.lineTo(w * 0.72f, h * 0.38f)
                q.lineTo(w * 0.28f, h * 0.38f)
                q.close()
                drawPath(q, Color(0x88FFFFFF))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
fun NavIcon(kind: String) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val c = Color.White
        when (kind) {
            "home" -> {
                val p = Path()
                p.moveTo(w * 0.5f, h * 0.05f)
                p.lineTo(w * 0.98f, h * 0.5f)
                p.lineTo(w * 0.82f, h * 0.5f)
                p.lineTo(w * 0.82f, h * 0.95f)
                p.lineTo(w * 0.18f, h * 0.95f)
                p.lineTo(w * 0.18f, h * 0.5f)
                p.lineTo(w * 0.02f, h * 0.5f)
                p.close()
                drawPath(p, c)
            }
            "char" -> {
                drawCircle(c, w * 0.22f, Offset(w * 0.5f, h * 0.27f))
                drawRoundRect(c, Offset(w * 0.12f, h * 0.55f), Size(w * 0.76f, h * 0.4f), CornerRadius(w * 0.3f, w * 0.3f))
            }
            "gun" -> {
                drawRoundRect(c, Offset(w * 0.02f, h * 0.28f), Size(w * 0.96f, h * 0.2f), CornerRadius(3f, 3f))
                drawRect(c, Offset(w * 0.02f, h * 0.45f), Size(w * 0.3f, h * 0.2f))
                drawRoundRect(c, Offset(w * 0.34f, h * 0.45f), Size(w * 0.16f, h * 0.5f), CornerRadius(3f, 3f))
                drawRect(c, Offset(w * 0.62f, h * 0.45f), Size(w * 0.1f, h * 0.22f))
            }
            "shirt" -> {
                val p = Path()
                p.moveTo(w * 0.32f, h * 0.08f)
                p.lineTo(w * 0.68f, h * 0.08f)
                p.lineTo(w * 0.98f, h * 0.3f)
                p.lineTo(w * 0.82f, h * 0.52f)
                p.lineTo(w * 0.72f, h * 0.45f)
                p.lineTo(w * 0.72f, h * 0.95f)
                p.lineTo(w * 0.28f, h * 0.95f)
                p.lineTo(w * 0.28f, h * 0.45f)
                p.lineTo(w * 0.18f, h * 0.52f)
                p.lineTo(w * 0.02f, h * 0.3f)
                p.close()
                drawPath(p, c)
            }
            else -> {
                drawRoundRect(c, Offset(0f, 0f), Size(w, h * 0.72f), CornerRadius(w * 0.2f, w * 0.2f))
                val p = Path()
                p.moveTo(w * 0.22f, h * 0.68f)
                p.lineTo(w * 0.22f, h)
                p.lineTo(w * 0.52f, h * 0.7f)
                p.close()
                drawPath(p, c)
                for (i in 0..2) drawCircle(Color(0xFF111116), w * 0.07f, Offset(w * (0.28f + 0.22f * i), h * 0.36f))
            }
        }
    }
}

@Composable
fun NameTag(name: String) {
    Text(
        name.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 13.sp, maxLines = 1,
        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 3.dp)
    )
}

@Composable
fun ChatPreview(modifier: Modifier) {
    val last = UiState.messages.takeLast(3)
    if (last.isEmpty()) return
    Column(modifier.width(250.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(8.dp)) {
        for (m in last) {
            Row {
                Text(m.name, color = nameColor(m.id), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Text(m.text, color = Color.White, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun HintBanner(text: String, onOpen: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.width(460.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xF0140A0E))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        GoldSlantButton("ATUR", Modifier) { onOpen() }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.clip(CircleShape).clickable { onClose() }.padding(6.dp)) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
