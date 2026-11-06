package com.app.surge.up

import org.json.JSONObject

class Msg(val key: String, val id: String, val name: String, val text: String, val t: Long)

object Chat {
    const val MAX_AGE = 7L * 60L * 1000L // pesan hilang setelah 7 menit
    const val MAX_SHOWN = 40

    suspend fun fetch(path: String = "chat/global"): List<Msg>? {
        val raw = Fb.get(Fb.GAME, path, "?orderBy=%22%24key%22&limitToLast=60") ?: return null
        val t = raw.trim()
        if (!t.startsWith("{")) return emptyList()
        val now = Fb.now()
        val list = ArrayList<Msg>()
        try {
            val o = JSONObject(t)
            for (k in o.keys()) {
                val m = o.optJSONObject(k) ?: continue
                val ts = m.optLong("t", 0L)
                if (ts <= 0L || now - ts > MAX_AGE) continue
                list.add(Msg(k, m.optString("id"), m.optString("name"), m.optString("text"), ts))
            }
        } catch (e: Exception) {
            return null
        }
        list.sortBy { it.t }
        return if (list.size > MAX_SHOWN) list.subList(list.size - MAX_SHOWN, list.size).toList() else list
    }

    suspend fun send(id: String, name: String, text: String, path: String = "chat/global"): Boolean {
        val body = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("text", text)
            .put("t", JSONObject().put(".sv", "timestamp"))
            .toString()
        return Fb.post(Fb.GAME, path, body)
    }
}
