package com.app.surge.up

import org.json.JSONArray
import org.json.JSONObject

class BanInfo(val until: Long, val reason: String)

object Moderation {
    @Volatile
    var words: Set<String> = emptySet()

    /** Ambil daftar kata terlarang dari admin (config/badwords). */
    suspend fun refreshWords() {
        val raw = Fb.get(Fb.ADMIN, "config/badwords") ?: return
        val t = raw.trim()
        val out = HashSet<String>()
        try {
            if (t.startsWith("[")) {
                val a = JSONArray(t)
                for (i in 0 until a.length()) {
                    val w = a.optString(i, "").trim().lowercase()
                    if (w.isNotEmpty() && w != "null") out.add(w)
                }
            } else if (t.startsWith("{")) {
                val o = JSONObject(t)
                for (k in o.keys()) {
                    val w = o.optString(k, "").trim().lowercase()
                    if (w.isNotEmpty() && w != "null") out.add(w)
                }
            }
        } catch (e: Exception) {
            return
        }
        words = out
    }

    /** Cocok per kata utuh: "anj" kena, "panjang" tidak. */
    fun hasBad(text: String): Boolean {
        val ws = words
        if (ws.isEmpty()) return false
        val tokens = text.lowercase().split(Regex("[^a-z0-9]+"))
        for (tk in tokens) {
            if (tk.isNotEmpty() && ws.contains(tk)) return true
        }
        return false
    }

    suspend fun checkBan(id: String): BanInfo? {
        val raw = Fb.get(Fb.ADMIN, "bans/" + id) ?: return null
        val t = raw.trim()
        if (!t.startsWith("{")) return null
        return try {
            val o = JSONObject(t)
            val until = o.optLong("until", 0L)
            if (until > Fb.now()) BanInfo(until, o.optString("reason", "Pelanggaran aturan")) else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun autoBan(id: String): BanInfo {
        val until = Fb.now() + 2L * 60L * 60L * 1000L
        val reason = "Kata terlarang di chat global"
        val body = JSONObject()
            .put("until", until)
            .put("reason", reason)
            .put("by", "auto")
            .toString()
        Fb.put(Fb.ADMIN, "bans/" + id, body)
        return BanInfo(until, reason)
    }

    fun fmtLeft(until: Long): String {
        if (until >= 9_000_000_000_000L) return "Permanen"
        val s = ((until - Fb.now()) / 1000L).coerceAtLeast(0L)
        val h = s / 3600L
        val m = (s % 3600L) / 60L
        val d = s % 60L
        return if (h > 0) "${h} jam ${m} menit" else "${m} menit ${d} detik"
    }
}
