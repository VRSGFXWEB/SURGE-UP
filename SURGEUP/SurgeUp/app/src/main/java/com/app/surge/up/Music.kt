package com.app.surge.up

import android.content.Context
import android.media.MediaPlayer

/** Musik lobby (loop). Berhenti otomatis saat app ke belakang atau masuk game. */
object Music {
    private var mp: MediaPlayer? = null

    fun start(c: Context) {
        if (!Session.music) return
        try {
            if (mp == null) {
                val p = MediaPlayer.create(c.applicationContext, R.raw.lobby_music)
                if (p != null) {
                    p.isLooping = true
                    p.setVolume(Session.musicVol, Session.musicVol)
                }
                mp = p
            }
            val p = mp
            if (p != null && !p.isPlaying) p.start()
        } catch (e: Exception) {
            // abaikan kalau perangkat gagal memutar
        }
    }

    fun setVolume(v: Float) {
        try {
            mp?.setVolume(v, v)
        } catch (e: Exception) {
        }
    }

    fun pause() {
        try {
            val p = mp
            if (p != null && p.isPlaying) p.pause()
        } catch (e: Exception) {
        }
    }

    fun release() {
        try {
            mp?.release()
        } catch (e: Exception) {
        }
        mp = null
    }
}
