package com.app.surge.up

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class AdminNotif(val key: String, val title: String, val body: String, val t: Long)

object Notifs {
    const val CHANNEL = "admin_notif"

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Info dari admin", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    suspend fun fetch(): List<AdminNotif>? {
        val raw = Fb.get(Fb.ADMIN, "notifs", "?orderBy=%22%24key%22&limitToLast=5") ?: return null
        val t = raw.trim()
        if (!t.startsWith("{")) return emptyList()
        val out = ArrayList<AdminNotif>()
        try {
            val o = JSONObject(t)
            for (k in o.keys()) {
                val m = o.optJSONObject(k) ?: continue
                out.add(AdminNotif(k, m.optString("title"), m.optString("body"), m.optLong("t", 0L)))
            }
        } catch (e: Exception) {
            return null
        }
        out.sortBy { it.key }
        return out
    }

    fun show(c: Context, n: AdminNotif) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            val open = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(
                c, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notif = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(n.title)
                .setContentText(n.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            NotificationManagerCompat.from(c).notify(n.key.hashCode() and 0x7fffffff, notif)
        } catch (e: SecurityException) {
            // izin belum diberikan
        }
    }
}
