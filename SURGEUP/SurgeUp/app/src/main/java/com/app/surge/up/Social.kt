package com.app.surge.up

import org.json.JSONObject

class Friend(val id: String, val name: String, val online: Boolean, val ava: String = "", val bio: String = "", val ch: String = "PUNK")
class FriendReq(val fromId: String, val name: String)
class RoomMember(val id: String, val name: String, val ready: Boolean, val ch: String = "PUNK")
class RoomState(
    val code: String,
    val host: String,
    val map: String,
    val members: List<RoomMember>,
    val go: String,
    val t: Long,
    val mode: String = "1v1"
)
class Invite(val code: String, val fromId: String, val fromName: String, val t: Long, val map: String)

/** Teman, undangan squad (mabar) dan room. Semua lewat Firebase REST di database game. */
object Social {
    const val MAP_ARENA = "ARENA"
    const val MAP_TRAIN = "TRAINING"
    private const val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val inviteSentAt = HashMap<String, Long>()
    private val dismissed = HashSet<String>()

    /** Token "go" terakhir yang sudah diproses di perangkat ini. */
    @Volatile
    var lastGo: String = ""

    private fun ts(): JSONObject = JSONObject().put(".sv", "timestamp")

    private fun newCode(): String {
        val sb = StringBuilder()
        for (i in 0 until 5) sb.append(LETTERS[(Math.random() * LETTERS.length).toInt()])
        return sb.toString()
    }

    // ---------- online ----------
    suspend fun heartbeat() {
        val id = Session.id
        if (id.isEmpty()) return
        Fb.put(Fb.GAME, "players/" + id + "/seen", ts().toString())
    }

    /** Simpan profil (karakter, bio, foto) ke server supaya teman dan anggota room bisa melihatnya. */
    suspend fun syncProfile() {
        val id = Session.id
        if (id.isEmpty()) return
        Fb.put(Fb.GAME, "players/" + id + "/char", JSONObject.quote(Session.char))
        Fb.put(Fb.GAME, "players/" + id + "/bio", JSONObject.quote(Session.bio))
        Fb.put(Fb.GAME, "players/" + id + "/ava", JSONObject.quote(Session.ava))
        val code = UiState.roomCode
        if (code.isNotEmpty()) Fb.put(Fb.GAME, "rooms/" + code + "/m/" + id + "/c", JSONObject.quote(Session.char))
    }

    // ---------- teman ----------
    suspend fun loadFriends(): List<Friend>? {
        val raw = Fb.get(Fb.GAME, "friends/" + Session.id) ?: return null
        val t = raw.trim()
        if (!t.startsWith("{")) return emptyList()
        val ids = ArrayList<String>()
        try {
            val o = JSONObject(t)
            for (k in o.keys()) ids.add(k)
        } catch (e: Exception) {
            return null
        }
        val out = ArrayList<Friend>()
        val now = Fb.now()
        for (fid in ids) {
            val p = Fb.get(Fb.GAME, "players/" + fid) ?: continue
            val pt = p.trim()
            if (!pt.startsWith("{")) continue
            try {
                val po = JSONObject(pt)
                val seen = po.optLong("seen", 0L)
                out.add(Friend(fid, po.optString("name", fid), seen > 0L && now - seen < 35000L, po.optString("ava", ""), po.optString("bio", ""), po.optString("char", "PUNK")))
            } catch (e: Exception) {
                // lewati data rusak
            }
        }
        out.sortWith(compareByDescending<Friend> { it.online }.thenBy { it.name.lowercase() })
        return out
    }

    suspend fun sendFriendReq(target: String): String {
        val t = target.trim()
        if (!Regex("^[0-9]{4,10}$").matches(t)) return "ID tidak valid."
        if (t == Session.id) return "Itu ID kamu sendiri."
        val p = Fb.get(Fb.GAME, "players/" + t) ?: return "Gagal terhubung ke server."
        if (!p.trim().startsWith("{")) return "ID tidak ditemukan."
        val f = Fb.get(Fb.GAME, "friends/" + Session.id + "/" + t) ?: return "Gagal terhubung ke server."
        if (f.trim() == "true") return "Kalian sudah berteman."
        val body = JSONObject().put("n", Session.name).put("t", ts()).toString()
        return if (Fb.put(Fb.GAME, "freq/" + t + "/" + Session.id, body)) "Permintaan teman terkirim." else "Gagal mengirim."
    }

    suspend fun loadReqs(): List<FriendReq>? {
        val raw = Fb.get(Fb.GAME, "freq/" + Session.id) ?: return null
        val t = raw.trim()
        if (!t.startsWith("{")) return emptyList()
        val out = ArrayList<FriendReq>()
        try {
            val o = JSONObject(t)
            for (k in o.keys()) {
                val m = o.optJSONObject(k)
                out.add(FriendReq(k, m?.optString("n", k) ?: k))
            }
        } catch (e: Exception) {
            return null
        }
        return out
    }

    suspend fun acceptReq(from: String) {
        val me = Session.id
        Fb.put(Fb.GAME, "friends/" + me + "/" + from, "true")
        Fb.put(Fb.GAME, "friends/" + from + "/" + me, "true")
        Fb.delete(Fb.GAME, "freq/" + me + "/" + from)
    }

    suspend fun declineReq(from: String) {
        Fb.delete(Fb.GAME, "freq/" + Session.id + "/" + from)
    }

    // ---------- room ----------
    private fun parseRoom(code: String, t: String): RoomState? {
        return try {
            val o = JSONObject(t)
            val host = o.optString("host")
            val ms = ArrayList<RoomMember>()
            val mo = o.optJSONObject("m")
            if (mo != null) {
                for (k in mo.keys()) {
                    val m = mo.optJSONObject(k)
                    if (m != null) ms.add(RoomMember(k, m.optString("n", k), m.optBoolean("r", false), m.optString("c", "PUNK")))
                }
            }
            ms.sortBy { if (it.id == host) 0 else 1 }
            RoomState(code, host, o.optString("map", MAP_ARENA), ms, o.optString("go", ""), o.optLong("t", 0L), o.optString("mode", "1v1"))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createRoom(map: String): String? {
        leaveRoom()
        val code = newCode()
        val me = JSONObject().put("n", Session.name).put("r", false).put("c", Session.char)
        val body = JSONObject()
            .put("host", Session.id)
            .put("map", map)
            .put("mode", "1v1")
            .put("t", ts())
            .put("m", JSONObject().put(Session.id, me))
            .toString()
        if (!Fb.put(Fb.GAME, "rooms/" + code, body)) return null
        UiState.roomCode = code
        lastGo = ""
        pollRoom()
        return code
    }

    /** Mengembalikan pesan error, atau "" kalau berhasil. */
    suspend fun joinRoom(codeIn: String): String {
        val code = codeIn.trim().uppercase()
        if (!Regex("^[A-Z]{5}$").matches(code)) return "Kode room terdiri dari 5 huruf."
        if (UiState.roomCode == code) return "Kamu sudah ada di room ini."
        val raw = Fb.get(Fb.GAME, "rooms/" + code) ?: return "Gagal terhubung ke server."
        val t = raw.trim()
        if (!t.startsWith("{")) return "Room tidak ditemukan."
        val st = parseRoom(code, t) ?: return "Room rusak."
        if (Fb.now() - st.t > 2L * 60L * 60L * 1000L) return "Room sudah kedaluwarsa."
        if (st.members.size >= 2) return "Room sudah penuh."
        leaveRoom()
        val me = JSONObject().put("n", Session.name).put("r", false).put("c", Session.char).toString()
        if (!Fb.put(Fb.GAME, "rooms/" + code + "/m/" + Session.id, me)) return "Gagal masuk room."
        UiState.roomCode = code
        lastGo = st.go
        pollRoom()
        return ""
    }

    suspend fun leaveRoom() {
        val code = UiState.roomCode
        if (code.isEmpty()) return
        val st = UiState.room
        UiState.roomCode = ""
        UiState.room = null
        if (st != null && st.host == Session.id) {
            Fb.delete(Fb.GAME, "rooms/" + code)
        } else {
            Fb.delete(Fb.GAME, "rooms/" + code + "/m/" + Session.id)
        }
    }

    suspend fun pollRoom() {
        val code = UiState.roomCode
        if (code.isEmpty()) {
            if (UiState.room != null) UiState.room = null
            return
        }
        val raw = Fb.get(Fb.GAME, "rooms/" + code) ?: return
        val t = raw.trim()
        if (!t.startsWith("{")) {
            UiState.roomCode = ""
            UiState.room = null
            UiState.toast = "Room ditutup."
            return
        }
        val st = parseRoom(code, t) ?: return
        if (st.members.none { it.id == Session.id }) {
            UiState.roomCode = ""
            UiState.room = null
            UiState.toast = "Kamu keluar dari room."
            return
        }
        UiState.room = st
    }

    suspend fun setReady(r: Boolean) {
        val code = UiState.roomCode
        if (code.isEmpty()) return
        Fb.put(Fb.GAME, "rooms/" + code + "/m/" + Session.id + "/r", r.toString())
        pollRoom()
    }

    suspend fun setMap(map: String) {
        val code = UiState.roomCode
        if (code.isEmpty()) return
        val st = UiState.room
        if (st == null || st.host != Session.id) return
        Fb.put(Fb.GAME, "rooms/" + code + "/map", "\"" + map + "\"")
        pollRoom()
    }

    suspend fun setMode(mode: String) {
        val code = UiState.roomCode
        if (code.isEmpty()) return
        val st = UiState.room
        if (st == null || st.host != Session.id) return
        Fb.put(Fb.GAME, "rooms/" + code + "/mode", "\"" + mode + "\"")
        pollRoom()
    }

    /** Profil pemain lain (untuk popup profil). */
    suspend fun loadProfile(id: String): Friend? {
        val p = Fb.get(Fb.GAME, "players/" + id) ?: return null
        val t = p.trim()
        if (!t.startsWith("{")) return null
        return try {
            val po = JSONObject(t)
            val seen = po.optLong("seen", 0L)
            Friend(id, po.optString("name", id), seen > 0L && Fb.now() - seen < 35000L, po.optString("ava", ""), po.optString("bio", ""), po.optString("char", "PUNK"))
        } catch (e: Exception) {
            null
        }
    }

    // ---------- antrean 2v2 ----------
    suspend fun queuePut(code: String, names: String) {
        val body = JSONObject().put("n", names).put("t", ts()).toString()
        Fb.put(Fb.GAME, "mm2/" + code, body)
    }

    suspend fun queueFind(own: String): String? {
        val raw = Fb.get(Fb.GAME, "mm2") ?: return null
        val t = raw.trim()
        if (!t.startsWith("{")) return null
        val now = Fb.now()
        try {
            val o = JSONObject(t)
            for (k in o.keys()) {
                if (k == own) continue
                val m = o.optJSONObject(k) ?: continue
                val tt = m.optLong("t", 0L)
                if (tt > 0L && now - tt < 12000L) return m.optString("n", k)
            }
        } catch (e: Exception) {
            return null
        }
        return null
    }

    suspend fun queueRemove(code: String) {
        Fb.delete(Fb.GAME, "mm2/" + code)
    }

    /** Host memulai pertandingan. Mengembalikan true kalau token go berhasil ditulis. */
    suspend fun hostStart(): Boolean {
        val st = UiState.room ?: return false
        if (st.host != Session.id) return false
        val token = "g" + Fb.now().toString()
        lastGo = token
        return Fb.put(Fb.GAME, "rooms/" + st.code + "/go", "\"" + token + "\"")
    }

    fun goFresh(go: String): Boolean {
        val ms = go.removePrefix("g").toLongOrNull() ?: return false
        return Fb.now() - ms < 45000L
    }

    // ---------- undangan ----------
    suspend fun inviteFriend(f: Friend): String {
        val last = inviteSentAt[f.id]
        if (last != null && Fb.now() - last < 15000L) return "Undangan sudah dikirim, tunggu sebentar."
        if (!f.online) return f.name + " sedang offline."
        var st = UiState.room
        if (st != null && st.members.any { it.id == f.id }) return f.name + " sudah ada di room."
        if (st != null && st.members.size >= 2) return "Room sudah penuh (maks 2 orang)."
        var code = UiState.roomCode
        if (code.isEmpty()) {
            code = createRoom(MAP_ARENA) ?: return "Gagal membuat room."
            st = UiState.room
        }
        inviteSentAt[f.id] = Fb.now()
        val body = JSONObject()
            .put("f", Session.id)
            .put("n", Session.name)
            .put("t", ts())
            .put("map", st?.map ?: MAP_ARENA)
            .toString()
        return if (Fb.put(Fb.GAME, "inv/" + f.id + "/" + code, body)) "Undangan dikirim ke " + f.name + "." else "Gagal mengirim undangan."
    }

    suspend fun pollInvites() {
        val raw = Fb.get(Fb.GAME, "inv/" + Session.id) ?: return
        val t = raw.trim()
        if (!t.startsWith("{")) {
            if (UiState.invite != null) UiState.invite = null
            return
        }
        val now = Fb.now()
        var best: Invite? = null
        try {
            val o = JSONObject(t)
            for (code in o.keys()) {
                val m = o.optJSONObject(code) ?: continue
                val tt = m.optLong("t", 0L)
                if (tt > 0L && now - tt > 3L * 60L * 1000L) {
                    Fb.delete(Fb.GAME, "inv/" + Session.id + "/" + code)
                    continue
                }
                if (dismissed.contains(code + tt.toString())) continue
                val inv = Invite(code, m.optString("f"), m.optString("n", "Teman"), tt, m.optString("map", MAP_ARENA))
                val b = best
                if (b == null || inv.t > b.t) best = inv
            }
        } catch (e: Exception) {
            return
        }
        val cur = UiState.invite
        val b = best
        if (b == null) {
            if (cur != null) UiState.invite = null
        } else if (cur == null || cur.code != b.code || cur.t != b.t) {
            UiState.invite = b
        }
    }

    suspend fun acceptInvite(inv: Invite): String {
        dismissed.add(inv.code + inv.t.toString())
        UiState.invite = null
        Fb.delete(Fb.GAME, "inv/" + Session.id + "/" + inv.code)
        return joinRoom(inv.code)
    }

    suspend fun declineInvite(inv: Invite) {
        dismissed.add(inv.code + inv.t.toString())
        UiState.invite = null
        Fb.delete(Fb.GAME, "inv/" + Session.id + "/" + inv.code)
    }
}
