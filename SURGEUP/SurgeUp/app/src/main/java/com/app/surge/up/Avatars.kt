package com.app.surge.up

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/** Foto profil kecil (96x96 JPEG, base64) supaya bisa disimpan di database dan dilihat teman. */
object Avatars {
    fun fromUri(ctx: Context, uri: Uri): String? {
        return try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / s > 600 || o.outHeight / s > 600) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
            val side = Math.min(bmp.width, bmp.height)
            val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val sm = Bitmap.createScaledBitmap(sq, 96, 96, true)
            val bos = ByteArrayOutputStream()
            sm.compress(Bitmap.CompressFormat.JPEG, 78, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun decode(b64: String): ImageBitmap? {
        if (b64.isEmpty()) return null
        return try {
            val b = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}
