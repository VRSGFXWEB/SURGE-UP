package com.app.surge.up

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Klien Firebase Realtime Database lewat REST (tanpa SDK). */
object Fb {
    const val GAME = "https://surgeup-server-1-default-rtdb.asia-southeast1.firebasedatabase.app"
    const val ADMIN = "https://surge-admin-default-rtdb.asia-southeast1.firebasedatabase.app"

    @Volatile
    var skew: Long = 0L

    /** Waktu server (ms), dihitung dari header Date balasan Firebase. */
    fun now(): Long = System.currentTimeMillis() + skew

    class Res(val code: Int, val body: String, val etag: String?)

    private fun call(
        base: String,
        path: String,
        method: String,
        body: String?,
        query: String,
        headers: Map<String, String>
    ): Res? {
        var conn: HttpURLConnection? = null
        return try {
            val c = URL(base + "/" + path + ".json" + query).openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = method
            c.connectTimeout = 8000
            c.readTimeout = 8000
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val d = c.getHeaderFieldDate("Date", 0L)
            if (d > 0L) skew = d - System.currentTimeMillis()
            Res(code, text, c.getHeaderField("ETag"))
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun get(base: String, path: String, query: String = ""): String? =
        withContext(Dispatchers.IO) {
            val r = call(base, path, "GET", null, query, emptyMap())
            if (r != null && r.code in 200..299) r.body else null
        }

    suspend fun put(base: String, path: String, json: String): Boolean =
        withContext(Dispatchers.IO) {
            val r = call(base, path, "PUT", json, "", emptyMap())
            r != null && r.code in 200..299
        }

    suspend fun post(base: String, path: String, json: String): Boolean =
        withContext(Dispatchers.IO) {
            val r = call(base, path, "POST", json, "", emptyMap())
            r != null && r.code in 200..299
        }

    suspend fun delete(base: String, path: String): Boolean =
        withContext(Dispatchers.IO) {
            val r = call(base, path, "DELETE", null, "", emptyMap())
            r != null && r.code in 200..299
        }

    /** ID pemain berikutnya (mulai 100000) memakai ETag supaya tidak bentrok. */
    suspend fun nextId(): Long? = withContext(Dispatchers.IO) {
        var result: Long? = null
        var tries = 0
        while (result == null && tries < 6) {
            tries++
            val g = call(GAME, "meta/lastId", "GET", null, "", mapOf("X-Firebase-ETag" to "true"))
            if (g == null || g.code !in 200..299) break
            val etag = g.etag ?: break
            val cur = g.body.trim().toLongOrNull() ?: 99999L
            val p = call(GAME, "meta/lastId", "PUT", (cur + 1).toString(), "", mapOf("if-match" to etag))
            if (p != null && p.code in 200..299) result = cur + 1
        }
        result
    }
}
