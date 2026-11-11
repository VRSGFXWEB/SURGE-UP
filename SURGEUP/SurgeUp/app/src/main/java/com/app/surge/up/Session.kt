package com.app.surge.up

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object Session {
    private lateinit var prefs: SharedPreferences

    fun init(c: Context) {
        prefs = c.applicationContext.getSharedPreferences("surge_session", Context.MODE_PRIVATE)
    }

    var id: String
        get() = prefs.getString("id", "") ?: ""
        set(v) {
            prefs.edit().putString("id", v).apply()
        }

    var name: String
        get() = prefs.getString("name", "") ?: ""
        set(v) {
            prefs.edit().putString("name", v).apply()
        }

    var lastNotif: String
        get() = prefs.getString("lastNotif", "") ?: ""
        set(v) {
            prefs.edit().putString("lastNotif", v).apply()
        }

    var char: String
        get() {
            val c = prefs.getString("char", "PUNK") ?: "PUNK"
            return if (c == "JOSEPH") c else "PUNK"
        }
        set(v) {
            prefs.edit().putString("char", v).apply()
        }

    var bio: String
        get() = prefs.getString("bio", "") ?: ""
        set(v) {
            prefs.edit().putString("bio", v).apply()
        }

    var ava: String
        get() = prefs.getString("ava", "") ?: ""
        set(v) {
            prefs.edit().putString("ava", v).apply()
        }

    var weapon: String
        get() = prefs.getString("weapon", "ak") ?: "ak"
        set(v) {
            prefs.edit().putString("weapon", v).apply()
        }

    var gq: String
        get() = prefs.getString("gq", "mid") ?: "mid"
        set(v) {
            prefs.edit().putString("gq", v).apply()
        }

    var gs: Boolean
        get() = prefs.getBoolean("gs", true)
        set(v) {
            prefs.edit().putBoolean("gs", v).apply()
        }

    var gf: Boolean
        get() = prefs.getBoolean("gf", false)
        set(v) {
            prefs.edit().putBoolean("gf", v).apply()
        }

    var musicVol: Float
        get() = prefs.getFloat("mvol", 0.55f)
        set(v) {
            prefs.edit().putFloat("mvol", v).apply()
        }

    var uiScale: Float
        get() = prefs.getFloat("uis", 1f)
        set(v) {
            prefs.edit().putFloat("uis", v).apply()
        }

    var emote: Int
        get() = prefs.getInt("emote", 0)
        set(v) {
            prefs.edit().putInt("emote", v).apply()
        }

    var music: Boolean
        get() = prefs.getBoolean("music", true)
        set(v) {
            prefs.edit().putBoolean("music", v).apply()
        }

    /** Buat akun baru: ambil ID numerik, simpan ke players/{id}. */
    suspend fun register(nick: String): Boolean {
        val n = Fb.nextId() ?: return false
        var clean = nick.trim()
        if (clean.length > 14) clean = clean.substring(0, 14)
        if (clean.isEmpty()) clean = "Player"
        val body = JSONObject()
            .put("name", clean)
            .put("created", JSONObject().put(".sv", "timestamp"))
            .toString()
        if (!Fb.put(Fb.GAME, "players/" + n, body)) return false
        id = n.toString()
        name = clean
        return true
    }
}
