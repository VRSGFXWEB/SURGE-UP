package com.app.surge.up

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object UiState {
    var banner by mutableStateOf<AdminNotif?>(null)
    var ban by mutableStateOf<BanInfo?>(null)
    var messages by mutableStateOf<List<Msg>>(emptyList())
    var teamMessages by mutableStateOf<List<Msg>>(emptyList())
    var friends by mutableStateOf<List<Friend>>(emptyList())
    var reqs by mutableStateOf<List<FriendReq>>(emptyList())
    var room by mutableStateOf<RoomState?>(null)
    var roomCode by mutableStateOf("")
    var invite by mutableStateOf<Invite?>(null)
    var toast by mutableStateOf("")
    var gameOn by mutableStateOf(false)
    var page by mutableStateOf("home")
    var uiScale by mutableStateOf(1f)
    var hint by mutableStateOf("")
    var viewFriend by mutableStateOf<Friend?>(null)
    var lobbyActive by mutableStateOf(false)
    var notifs by mutableStateOf<List<AdminNotif>>(emptyList())
}
