package com.app.surge.up

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Base64
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// =================== MODEL GLB / GLTF ===================
private fun fbuf(n: Int): FloatBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

class Prim(
    val pos: FloatArray, val nor: FloatArray, val uv: FloatArray, val idx: ShortArray,
    val joints: IntArray?, val weights: FloatArray?, val color: FloatArray,
    val img: Int, val node: Int, val skin: Int, val mask: Boolean
) {
    val outPos: FloatArray = pos.copyOf()
    val outNor: FloatArray = nor.copyOf()
    val bPos: FloatBuffer = fbuf(pos.size)
    val bNor: FloatBuffer = fbuf(nor.size)
    val bUv: FloatBuffer = fbuf(uv.size)
    val bIdx: ShortBuffer = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
    var uploaded = false

    init {
        bUv.put(uv)
        bUv.position(0)
        bIdx.put(idx)
        bIdx.position(0)
    }
}

class Chan(val node: Int, val path: Int, val times: FloatArray, val vals: FloatArray)
class Anim(val dur: Float, val chans: List<Chan>)

class GlbModel(
    val n: Int, val parent: IntArray, val names: Array<String>,
    val t: FloatArray, val r: FloatArray, val s: FloatArray,
    val prims: List<Prim>, val skinJoints: List<IntArray>, val skinIbm: List<FloatArray>,
    val images: Array<Bitmap?>, val anims: Map<String, Anim>
) {
    val t0: FloatArray = t.copyOf()
    val r0: FloatArray = r.copyOf()
    val s0: FloatArray = s.copyOf()
    val world = FloatArray(n * 16)
    var world0 = FloatArray(n * 16)
    val nameIdx = HashMap<String, Int>()
    val texIds = IntArray(images.size)
    private val tmp = FloatArray(16)

    init {
        for (i in 0 until n) nameIdx[names[i]] = i
        updateWorld()
        world0 = world.copyOf()
    }

    fun resetPose() {
        System.arraycopy(t0, 0, t, 0, t.size)
        System.arraycopy(r0, 0, r, 0, r.size)
        System.arraycopy(s0, 0, s, 0, s.size)
    }

    private fun local(i: Int, m: FloatArray) {
        val x = r[i * 4]
        val y = r[i * 4 + 1]
        val z = r[i * 4 + 2]
        val w = r[i * 4 + 3]
        val sx = s[i * 3]
        val sy = s[i * 3 + 1]
        val sz = s[i * 3 + 2]
        m[0] = (1 - 2 * (y * y + z * z)) * sx
        m[1] = (2 * (x * y + z * w)) * sx
        m[2] = (2 * (x * z - y * w)) * sx
        m[3] = 0f
        m[4] = (2 * (x * y - z * w)) * sy
        m[5] = (1 - 2 * (x * x + z * z)) * sy
        m[6] = (2 * (y * z + x * w)) * sy
        m[7] = 0f
        m[8] = (2 * (x * z + y * w)) * sz
        m[9] = (2 * (y * z - x * w)) * sz
        m[10] = (1 - 2 * (x * x + y * y)) * sz
        m[11] = 0f
        m[12] = t[i * 3]
        m[13] = t[i * 3 + 1]
        m[14] = t[i * 3 + 2]
        m[15] = 1f
    }

    fun updateWorld() {
        val done = BooleanArray(n)
        fun go(i: Int) {
            if (done[i]) return
            local(i, tmp)
            val p = parent[i]
            if (p >= 0) {
                go(p)
                Matrix.multiplyMM(world, i * 16, world, p * 16, tmp, 0)
            } else {
                System.arraycopy(tmp, 0, world, i * 16, 16)
            }
            done[i] = true
        }
        for (i in 0 until n) go(i)
    }

    /** Skinning di CPU: hasil ke outPos/outNor tiap primitif. */
    fun skin() {
        val jms = ArrayList<FloatArray>()
        for (k in skinJoints.indices) {
            val j = skinJoints[k]
            val ibm = skinIbm[k]
            val m = FloatArray(j.size * 16)
            for (q in j.indices) Matrix.multiplyMM(m, q * 16, world, j[q] * 16, ibm, q * 16)
            jms.add(m)
        }
        for (p in prims) {
            val jw = p.joints
            val ww = p.weights
            if (p.skin < 0 || jw == null || ww == null) continue
            val m = jms[p.skin]
            val nv = p.pos.size / 3
            for (v in 0 until nv) {
                val x = p.pos[v * 3]
                val y = p.pos[v * 3 + 1]
                val z = p.pos[v * 3 + 2]
                val ax = p.nor[v * 3]
                val ay = p.nor[v * 3 + 1]
                val az = p.nor[v * 3 + 2]
                var ox = 0f
                var oy = 0f
                var oz = 0f
                var nx = 0f
                var ny = 0f
                var nz = 0f
                for (k in 0 until 4) {
                    val w = ww[v * 4 + k]
                    if (w <= 0f) continue
                    val o = jw[v * 4 + k] * 16
                    ox += w * (m[o] * x + m[o + 4] * y + m[o + 8] * z + m[o + 12])
                    oy += w * (m[o + 1] * x + m[o + 5] * y + m[o + 9] * z + m[o + 13])
                    oz += w * (m[o + 2] * x + m[o + 6] * y + m[o + 10] * z + m[o + 14])
                    nx += w * (m[o] * ax + m[o + 4] * ay + m[o + 8] * az)
                    ny += w * (m[o + 1] * ax + m[o + 5] * ay + m[o + 9] * az)
                    nz += w * (m[o + 2] * ax + m[o + 6] * ay + m[o + 10] * az)
                }
                p.outPos[v * 3] = ox
                p.outPos[v * 3 + 1] = oy
                p.outPos[v * 3 + 2] = oz
                p.outNor[v * 3] = nx
                p.outNor[v * 3 + 1] = ny
                p.outNor[v * 3 + 2] = nz
            }
        }
    }

    /** Kotak pembatas [minX,minY,minZ,maxX,maxY,maxZ] dalam ruang model. */
    fun bounds(): FloatArray {
        val b = floatArrayOf(1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f)
        for (p in prims) {
            val nv = p.pos.size / 3
            val wo = p.node * 16
            for (v in 0 until nv) {
                var x: Float
                var y: Float
                var z: Float
                if (p.skin >= 0) {
                    x = p.outPos[v * 3]
                    y = p.outPos[v * 3 + 1]
                    z = p.outPos[v * 3 + 2]
                } else {
                    val px = p.pos[v * 3]
                    val py = p.pos[v * 3 + 1]
                    val pz = p.pos[v * 3 + 2]
                    x = world[wo] * px + world[wo + 4] * py + world[wo + 8] * pz + world[wo + 12]
                    y = world[wo + 1] * px + world[wo + 5] * py + world[wo + 9] * pz + world[wo + 13]
                    z = world[wo + 2] * px + world[wo + 6] * py + world[wo + 10] * pz + world[wo + 14]
                }
                if (x < b[0]) b[0] = x
                if (y < b[1]) b[1] = y
                if (z < b[2]) b[2] = z
                if (x > b[3]) b[3] = x
                if (y > b[4]) b[4] = y
                if (z > b[5]) b[5] = z
            }
        }
        return b
    }

    fun applyAnim(a: Anim, time: Float) {
        val tt = if (a.dur > 0f) time % a.dur else 0f
        for (c in a.chans) {
            val nk = c.times.size
            if (nk == 0) continue
            var k = 0
            while (k < nk - 2 && tt >= c.times[k + 1]) k++
            val t0k = c.times[k]
            val t1k = c.times[min(k + 1, nk - 1)]
            val f = if (t1k > t0k) ((tt - t0k) / (t1k - t0k)).coerceIn(0f, 1f) else 0f
            val k1 = min(k + 1, nk - 1)
            if (c.path == 1) {
                var qx = c.vals[k * 4]
                var qy = c.vals[k * 4 + 1]
                var qz = c.vals[k * 4 + 2]
                var qw = c.vals[k * 4 + 3]
                var bx = c.vals[k1 * 4]
                var by = c.vals[k1 * 4 + 1]
                var bz = c.vals[k1 * 4 + 2]
                var bw = c.vals[k1 * 4 + 3]
                if (qx * bx + qy * by + qz * bz + qw * bw < 0f) {
                    bx = -bx
                    by = -by
                    bz = -bz
                    bw = -bw
                }
                var rx = qx + (bx - qx) * f
                var ry = qy + (by - qy) * f
                var rz = qz + (bz - qz) * f
                var rw = qw + (bw - qw) * f
                val len = sqrt(rx * rx + ry * ry + rz * rz + rw * rw)
                if (len > 0f) {
                    rx /= len
                    ry /= len
                    rz /= len
                    rw /= len
                }
                r[c.node * 4] = rx
                r[c.node * 4 + 1] = ry
                r[c.node * 4 + 2] = rz
                r[c.node * 4 + 3] = rw
                qx = 0f
                qy = 0f
                qz = 0f
                qw = 0f
            } else {
                val dst = if (c.path == 0) t else s
                for (q in 0 until 3) {
                    val a0 = c.vals[k * 3 + q]
                    val a1 = c.vals[k1 * 3 + q]
                    dst[c.node * 3 + q] = a0 + (a1 - a0) * f
                }
            }
        }
    }

    // ---- rotasi sumbu dunia pada bone (untuk gerakan prosedural Joseph) ----
    fun rotWorld(name: String, ax: Float, ay: Float, az: Float, ang: Float) {
        val i = nameIdx[name] ?: return
        val o = i * 16
        val w = FloatArray(9)
        for (c in 0 until 3) {
            val a = world0[o + c * 4]
            val b = world0[o + c * 4 + 1]
            val d = world0[o + c * 4 + 2]
            val len = sqrt(a * a + b * b + d * d).let { if (it > 1e-6f) it else 1f }
            w[0 * 3 + c] = a / len
            w[1 * 3 + c] = b / len
            w[2 * 3 + c] = d / len
        }
        val al = sqrt(ax * ax + ay * ay + az * az)
        val x = ax / al
        val y = ay / al
        val z = az / al
        val c = cos(ang)
        val sn = sin(ang)
        val cc = 1 - c
        val ra = floatArrayOf(
            c + x * x * cc, x * y * cc - z * sn, x * z * cc + y * sn,
            y * x * cc + z * sn, c + y * y * cc, y * z * cc - x * sn,
            z * x * cc - y * sn, z * y * cc + x * sn, c + z * z * cc
        )
        val wt = FloatArray(9)
        for (a in 0 until 3) for (b in 0 until 3) wt[a * 3 + b] = w[b * 3 + a]
        val t1 = mul3(ra, w)
        val dl = mul3(wt, t1)
        val q = quatFrom3(dl)
        val k = i * 4
        val x1 = r[k]
        val y1 = r[k + 1]
        val z1 = r[k + 2]
        val w1 = r[k + 3]
        r[k] = w1 * q[0] + x1 * q[3] + y1 * q[2] - z1 * q[1]
        r[k + 1] = w1 * q[1] - x1 * q[2] + y1 * q[3] + z1 * q[0]
        r[k + 2] = w1 * q[2] + x1 * q[1] - y1 * q[0] + z1 * q[3]
        r[k + 3] = w1 * q[3] - x1 * q[0] - y1 * q[1] - z1 * q[2]
    }

    private fun mul3(a: FloatArray, b: FloatArray): FloatArray {
        val o = FloatArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            var sum = 0f
            for (k in 0 until 3) sum += a[i * 3 + k] * b[k * 3 + j]
            o[i * 3 + j] = sum
        }
        return o
    }

    private fun quatFrom3(m: FloatArray): FloatArray {
        val tr = m[0] + m[4] + m[8]
        val x: Float
        val y: Float
        val z: Float
        val w: Float
        if (tr > 0f) {
            val s2 = sqrt(tr + 1f) * 2f
            w = s2 / 4f
            x = (m[7] - m[5]) / s2
            y = (m[2] - m[6]) / s2
            z = (m[3] - m[1]) / s2
        } else if (m[0] > m[4] && m[0] > m[8]) {
            val s2 = sqrt(1f + m[0] - m[4] - m[8]) * 2f
            w = (m[7] - m[5]) / s2
            x = s2 / 4f
            y = (m[1] + m[3]) / s2
            z = (m[2] + m[6]) / s2
        } else if (m[4] > m[8]) {
            val s2 = sqrt(1f + m[4] - m[0] - m[8]) * 2f
            w = (m[2] - m[6]) / s2
            x = (m[1] + m[3]) / s2
            y = s2 / 4f
            z = (m[5] + m[7]) / s2
        } else {
            val s2 = sqrt(1f + m[8] - m[0] - m[4]) * 2f
            w = (m[3] - m[1]) / s2
            x = (m[2] + m[6]) / s2
            y = (m[5] + m[7]) / s2
            z = s2 / 4f
        }
        val len = sqrt(x * x + y * y + z * z + w * w)
        return floatArrayOf(x / len, y / len, z / len, w / len)
    }

    companion object {
        fun load(ctx: Context, asset: String, maxTex: Int): GlbModel? {
            return try {
                parse(ctx, asset, maxTex)
            } catch (e: Throwable) {
                null
            }
        }

        private fun parse(ctx: Context, asset: String, maxTex: Int): GlbModel {
            val bytes = ctx.assets.open(asset).use { it.readBytes() }
            val json: JSONObject
            val bin: ByteArray
            if (asset.endsWith(".glb")) {
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val jl = bb.getInt(12)
                json = JSONObject(String(bytes, 20, jl, Charsets.UTF_8))
                val off = 20 + jl + 8
                val bl = bb.getInt(20 + jl)
                bin = bytes.copyOfRange(off, off + bl)
            } else {
                json = JSONObject(String(bytes, Charsets.UTF_8))
                val uri = json.getJSONArray("buffers").getJSONObject(0).getString("uri")
                bin = Base64.decode(uri.substring(uri.indexOf(',') + 1), Base64.DEFAULT)
            }
            val bb2 = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
            val accs = json.getJSONArray("accessors")
            val bvs = json.getJSONArray("bufferViews")

            fun readF(i: Int): FloatArray {
                val a = accs.getJSONObject(i)
                val bv = bvs.getJSONObject(a.getInt("bufferView"))
                val ct = a.getInt("componentType")
                val cnt = a.getInt("count")
                val nc = when (a.getString("type")) {
                    "SCALAR" -> 1
                    "VEC2" -> 2
                    "VEC3" -> 3
                    "VEC4" -> 4
                    "MAT4" -> 16
                    else -> 1
                }
                val sz = when (ct) {
                    5126, 5125 -> 4
                    5123, 5122 -> 2
                    else -> 1
                }
                val off = bv.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
                val st = bv.optInt("byteStride", 0)
                val stride = if (st == 0) sz * nc else st
                val norm = a.optBoolean("normalized", false)
                val out = FloatArray(cnt * nc)
                for (e in 0 until cnt) for (c in 0 until nc) {
                    val p = off + e * stride + c * sz
                    var v: Float = when (ct) {
                        5126 -> bb2.getFloat(p)
                        5125 -> (bb2.getInt(p).toLong() and 0xffffffffL).toFloat()
                        5123 -> (bb2.getShort(p).toInt() and 0xffff).toFloat()
                        5122 -> bb2.getShort(p).toFloat()
                        5121 -> (bb2.get(p).toInt() and 0xff).toFloat()
                        else -> bb2.get(p).toFloat()
                    }
                    if (norm) {
                        v /= when (ct) {
                            5123 -> 65535f
                            5121 -> 255f
                            5122 -> 32767f
                            else -> 127f
                        }
                    }
                    out[e * nc + c] = v
                }
                return out
            }

            // node
            val nj = json.getJSONArray("nodes")
            val n = nj.length()
            val parent = IntArray(n) { -1 }
            val names = Array(n) { "n$it" }
            val t = FloatArray(n * 3)
            val r = FloatArray(n * 4)
            val s = FloatArray(n * 3)
            for (i in 0 until n) {
                val nd = nj.getJSONObject(i)
                names[i] = nd.optString("name", "n$i")
                val ch = nd.optJSONArray("children")
                if (ch != null) for (k in 0 until ch.length()) parent[ch.getInt(k)] = i
                val tr = nd.optJSONArray("translation")
                val ro = nd.optJSONArray("rotation")
                val sc = nd.optJSONArray("scale")
                for (q in 0 until 3) {
                    t[i * 3 + q] = tr?.getDouble(q)?.toFloat() ?: 0f
                    s[i * 3 + q] = sc?.getDouble(q)?.toFloat() ?: 1f
                }
                for (q in 0 until 4) r[i * 4 + q] = ro?.getDouble(q)?.toFloat() ?: (if (q == 3) 1f else 0f)
            }

            // gambar
            val imgsJ = json.optJSONArray("images")
            val images = arrayOfNulls<Bitmap>(imgsJ?.length() ?: 0)
            val texJ = json.optJSONArray("textures")
            fun decodeImg(i: Int): Bitmap? {
                val im = imgsJ!!.getJSONObject(i)
                val data: ByteArray = if (im.has("bufferView")) {
                    val bv = bvs.getJSONObject(im.getInt("bufferView"))
                    val o = bv.optInt("byteOffset", 0)
                    bin.copyOfRange(o, o + bv.getInt("byteLength"))
                } else {
                    val uri = im.getString("uri")
                    Base64.decode(uri.substring(uri.indexOf(',') + 1), Base64.DEFAULT)
                }
                val o1 = BitmapFactory.Options()
                o1.inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(data, 0, data.size, o1)
                var ss = 1
                while (max(o1.outWidth, o1.outHeight) / ss > maxTex) ss *= 2
                val o2 = BitmapFactory.Options()
                o2.inSampleSize = ss
                return BitmapFactory.decodeByteArray(data, 0, data.size, o2)
            }

            // skin
            val skJ = json.optJSONArray("skins")
            val skinJoints = ArrayList<IntArray>()
            val skinIbm = ArrayList<FloatArray>()
            if (skJ != null) {
                for (k in 0 until skJ.length()) {
                    val sk = skJ.getJSONObject(k)
                    val ja = sk.getJSONArray("joints")
                    val jn = IntArray(ja.length()) { ja.getInt(it) }
                    skinJoints.add(jn)
                    if (sk.has("inverseBindMatrices")) {
                        skinIbm.add(readF(sk.getInt("inverseBindMatrices")))
                    } else {
                        val id = FloatArray(jn.size * 16)
                        for (q in jn.indices) {
                            id[q * 16] = 1f
                            id[q * 16 + 5] = 1f
                            id[q * 16 + 10] = 1f
                            id[q * 16 + 15] = 1f
                        }
                        skinIbm.add(id)
                    }
                }
            }

            // mesh
            val mats = json.optJSONArray("materials")
            val meshes = json.getJSONArray("meshes")
            val prims = ArrayList<Prim>()
            val usedImg = HashSet<Int>()
            for (i in 0 until n) {
                val nd = nj.getJSONObject(i)
                if (!nd.has("mesh")) continue
                val mesh = meshes.getJSONObject(nd.getInt("mesh"))
                val pa = mesh.getJSONArray("primitives")
                for (pi in 0 until pa.length()) {
                    val pr = pa.getJSONObject(pi)
                    val at = pr.getJSONObject("attributes")
                    val pos = readF(at.getInt("POSITION"))
                    val nv = pos.size / 3
                    if (nv > 65535 || !pr.has("indices")) continue
                    val nor = if (at.has("NORMAL")) readF(at.getInt("NORMAL")) else FloatArray(nv * 3)
                    val uv = if (at.has("TEXCOORD_0")) readF(at.getInt("TEXCOORD_0")) else FloatArray(nv * 2)
                    val fi = readF(pr.getInt("indices"))
                    val idx = ShortArray(fi.size) { fi[it].toInt().toShort() }
                    var jo: IntArray? = null
                    var we: FloatArray? = null
                    val skinIdx = nd.optInt("skin", -1)
                    if (skinIdx >= 0 && at.has("JOINTS_0") && at.has("WEIGHTS_0")) {
                        val jf = readF(at.getInt("JOINTS_0"))
                        jo = IntArray(jf.size) { jf[it].toInt() }
                        we = readF(at.getInt("WEIGHTS_0"))
                    }
                    var color = floatArrayOf(1f, 1f, 1f, 1f)
                    var img = -1
                    var mask = false
                    if (pr.has("material") && mats != null) {
                        val mt = mats.getJSONObject(pr.getInt("material"))
                        val pb = mt.optJSONObject("pbrMetallicRoughness")
                        val bc = pb?.optJSONArray("baseColorFactor")
                        if (bc != null) color = floatArrayOf(bc.getDouble(0).toFloat(), bc.getDouble(1).toFloat(), bc.getDouble(2).toFloat(), bc.getDouble(3).toFloat())
                        val bt = pb?.optJSONObject("baseColorTexture")
                        if (bt != null && texJ != null && images.isNotEmpty()) {
                            img = texJ.getJSONObject(bt.getInt("index")).optInt("source", -1)
                            if (img >= 0) usedImg.add(img)
                        }
                        mask = mt.optString("alphaMode", "OPAQUE") != "OPAQUE"
                    }
                    prims.add(Prim(pos, nor, uv, idx, jo, we, color, img, i, if (jo != null) skinIdx else -1, mask))
                }
            }
            for (k in usedImg) images[k] = decodeImg(k)

            // animasi
            val anims = HashMap<String, Anim>()
            val aj = json.optJSONArray("animations")
            if (aj != null) {
                for (k in 0 until aj.length()) {
                    val a = aj.getJSONObject(k)
                    val sm = a.getJSONArray("samplers")
                    val chj = a.getJSONArray("channels")
                    val chans = ArrayList<Chan>()
                    var dur = 0f
                    for (q in 0 until chj.length()) {
                        val ch = chj.getJSONObject(q)
                        val tg = ch.getJSONObject("target")
                        val path = when (tg.optString("path")) {
                            "translation" -> 0
                            "rotation" -> 1
                            "scale" -> 2
                            else -> -1
                        }
                        if (path < 0 || !tg.has("node")) continue
                        val smp = sm.getJSONObject(ch.getInt("sampler"))
                        val times = readF(smp.getInt("input"))
                        var vals = readF(smp.getInt("output"))
                        if (smp.optString("interpolation") == "CUBICSPLINE") {
                            val comp = if (path == 1) 4 else 3
                            val mid = FloatArray(times.size * comp)
                            for (e in times.indices) for (c in 0 until comp) mid[e * comp + c] = vals[(e * 3 + 1) * comp + c]
                            vals = mid
                        }
                        if (times.isNotEmpty()) dur = max(dur, times[times.size - 1])
                        chans.add(Chan(tg.getInt("node"), path, times, vals))
                    }
                    anims[a.optString("name", "a$k")] = Anim(dur, chans)
                }
            }
            return GlbModel(n, parent, names, t, r, s, prims, skinJoints, skinIbm, images, anims)
        }
    }
}

// =================== INSTANCE KARAKTER / SENJATA ===================
class Inst(val key: String, val model: GlbModel) {
    var sc = 1f
    var cx = 0f
    var cy = 0f
    var cz = 0f
    val isChar: Boolean get() = !key.startsWith("W:")
    val isJoseph: Boolean get() = key == "JOSEPH"

    init {
        pose(0f, -1, 0f)
        val b = model.bounds()
        if (isChar) {
            sc = 1.85f / max(0.01f, b[4] - b[1])
            cx = (b[0] + b[3]) / 2f
            cy = b[1]
            cz = (b[2] + b[5]) / 2f
        } else {
            val ext = max(b[3] - b[0], max(b[4] - b[1], b[5] - b[2]))
            sc = 2.7f / max(0.01f, ext)
            cx = (b[0] + b[3]) / 2f
            cy = (b[1] + b[4]) / 2f
            cz = (b[2] + b[5]) / 2f
        }
    }

    /** emote: -1 = idle. et = detik sejak emote mulai. */
    fun pose(time: Float, emote: Int, et: Float) {
        val m = model
        m.resetPose()
        if (isJoseph) {
            joseph(time, emote, et)
        } else if (isChar) {
            val a = if (emote >= 0) m.anims[PUNK_EMOTES[emote % PUNK_EMOTES.size]] else m.anims["Idle"]
            if (a != null) m.applyAnim(a, if (emote >= 0) et else time)
        }
        m.updateWorld()
        m.skin()
    }

    private fun joseph(time: Float, emote: Int, et: Float) {
        val m = model
        val d = Math.PI.toFloat() / 180f
        val s = sin(time * 2.2f)
        val s2 = sin(time * 2.2f + 1.3f)
        var env = 0f
        if (emote >= 0) {
            val up = (et / 0.35f).coerceIn(0f, 1f)
            val dn = ((EMOTE_DUR - et) / 0.35f).coerceIn(0f, 1f)
            env = min(up, dn)
        }
        var lArm = -16f + 1.6f * s
        var rArm = 16f - 1.6f * s
        var lFore = -4f
        var rFore = 4f
        var hipsY = 0f
        var spineZ = 0f
        when (emote) {
            0 -> { // lambai
                rArm = 16f + (-116f) * env
                rFore = 4f + 28f * sin(et * 9f) * env
            }
            1 -> { // joget
                val w = sin(et * 6f)
                hipsY = 20f * w * env
                spineZ = 7f * sin(et * 6f + 1f) * env
                lArm = -16f + (60f * (0.5f + 0.5f * w)) * env
                rArm = 16f - (60f * (0.5f - 0.5f * w)) * env
            }
            2 -> { // hormat
                rArm = 16f + (-70f) * env
                rFore = 4f + 70f * env
            }
            3 -> { // otot
                lArm = -16f + 86f * env
                rArm = 16f - 86f * env
                lFore = -4f + 70f * env
                rFore = 4f - 70f * env
            }
        }
        m.rotWorld("Hips", 0f, 1f, 0f, hipsY * d)
        m.rotWorld("Spine2", 0f, 0f, 1f, spineZ * d)
        m.rotWorld("Spine2", 1f, 0f, 0f, 1.3f * s * d)
        m.rotWorld("LeftArm", 0f, 0f, 1f, lArm * d)
        m.rotWorld("RightArm", 0f, 0f, 1f, rArm * d)
        m.rotWorld("LeftForeArm", 0f, 0f, 1f, lFore * d)
        m.rotWorld("RightForeArm", 0f, 0f, 1f, rFore * d)
        m.rotWorld("Head", 0f, 1f, 0f, 3.5f * s2 * d)
    }

    companion object {
        const val EMOTE_DUR = 2.8f
        val PUNK_EMOTES = arrayOf("Wave", "Run", "Interact", "Punch_Right")

        fun create(ctx: Context, key: String): Inst? {
            val asset = when (key) {
                "PUNK" -> "Punk.gltf"
                "JOSEPH" -> "joseph.glb"
                "W:ak" -> "AK47-V2.glb"
                "W:sg" -> "M1887.glb"
                else -> return null
            }
            val tex = if (key == "JOSEPH") 1024 else 512
            val m = GlbModel.load(ctx, asset, tex) ?: return null
            return try {
                Inst(key, m)
            } catch (e: Throwable) {
                null
            }
        }
    }
}

// =================== RENDERER ===================
class Gl3DRenderer(private val ctx: Context, val mode: Int) : GLSurfaceView.Renderer {
    // mode 0 = lobby (latar foto bergerak + karakter), 1 = viewer karakter, 2 = viewer senjata
    @Volatile var chars: Array<String> = arrayOf("PUNK")
    @Volatile var weapon: String = "ak"
    @Volatile var yawUser = 0f
    @Volatile var emoteId = -1
    @Volatile var emoteT0 = 0L
    @Volatile var tapEmote = false

    private val slots = arrayOfNulls<Inst>(2)
    private val ready = arrayOfNulls<Inst>(2)
    private val wantKey = arrayOf("", "")
    private var prog = 0
    private var uMVP = 0
    private var uModel = 0
    private var uColor = 0
    private var uTex = 0
    private var uUseTex = 0
    private var uLight = 0
    private var uCut = 0
    private var uUvS = 0
    private var uUvO = 0
    private var bgTex = 0
    private var shTex = 0
    private var bgW = 1
    private var bgH = 1
    private var sw = 1
    private var sh = 1
    private var t = 0f
    private var last = 0L
    private var yawAuto = 0f
    private var dragging = false
    private var lx = 0f
    private var downX = 0f
    private var moved = false
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val place = FloatArray(16)
    private val modelM = FloatArray(16)
    private val quadPos = fbuf(12)
    private val quadNor = fbuf(12)
    private val quadUv = fbuf(8)
    private val quadIdx: ShortBuffer = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).asShortBuffer()
    private val shPos = fbuf(12)
    private val shUv = fbuf(8)

    fun playEmote(id: Int) {
        emoteId = id
        emoteT0 = SystemClock.uptimeMillis()
    }

    fun touch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                lx = e.x
                downX = e.x
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lx
                lx = e.x
                if (abs(e.x - downX) > 18f) moved = true
                yawUser += dx * 0.45f
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                if (!moved && tapEmote) playEmote((Math.random() * 4.0).toInt())
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vs = """
            attribute vec3 aPos; attribute vec3 aNor; attribute vec2 aUv;
            uniform mat4 uMVP; uniform mat4 uModel; uniform vec2 uUvS; uniform vec2 uUvO;
            varying vec3 vNor; varying vec2 vUv;
            void main(){ gl_Position = uMVP * vec4(aPos,1.0); vNor = (uModel * vec4(aNor,0.0)).xyz; vUv = aUv * uUvS + uUvO; }
        """.trimIndent()
        val fs = """
            precision mediump float;
            uniform vec4 uColor; uniform sampler2D uTex; uniform float uUseTex; uniform float uLight; uniform float uCut;
            varying vec3 vNor; varying vec2 vUv;
            void main(){
                vec4 c = uColor;
                if (uUseTex > 0.5) { c = c * texture2D(uTex, vUv); }
                if (c.a < uCut) discard;
                float d = max(dot(normalize(vNor), normalize(vec3(0.35,0.65,0.7))), 0.0);
                float l = mix(1.0, 0.52 + 0.62 * d, uLight);
                gl_FragColor = vec4(c.rgb * l, c.a);
            }
        """.trimIndent()
        prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(prog, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glBindAttribLocation(prog, 0, "aPos")
        GLES20.glBindAttribLocation(prog, 1, "aNor")
        GLES20.glBindAttribLocation(prog, 2, "aUv")
        GLES20.glLinkProgram(prog)
        uMVP = GLES20.glGetUniformLocation(prog, "uMVP")
        uModel = GLES20.glGetUniformLocation(prog, "uModel")
        uColor = GLES20.glGetUniformLocation(prog, "uColor")
        uTex = GLES20.glGetUniformLocation(prog, "uTex")
        uUseTex = GLES20.glGetUniformLocation(prog, "uUseTex")
        uLight = GLES20.glGetUniformLocation(prog, "uLight")
        uCut = GLES20.glGetUniformLocation(prog, "uCut")
        uUvS = GLES20.glGetUniformLocation(prog, "uUvS")
        uUvO = GLES20.glGetUniformLocation(prog, "uUvO")
        GLES20.glClearColor(0.05f, 0.03f, 0.06f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        quadPos.put(floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f, -1f, 1f, 0f)).position(0)
        quadNor.put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)).position(0)
        quadUv.put(floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f)).position(0)
        quadIdx.put(shortArrayOf(0, 1, 2, 0, 2, 3)).position(0)
        shPos.put(floatArrayOf(-0.75f, 0.01f, 0.75f, 0.75f, 0.01f, 0.75f, 0.75f, 0.01f, -0.75f, -0.75f, 0.01f, -0.75f)).position(0)
        shUv.put(floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f)).position(0)
        // tekstur ulang setelah konteks GL dibuat ulang
        for (i in 0..1) {
            slots[i]?.model?.texIds?.fill(0)
            slots[i]?.model?.prims?.forEach { it.uploaded = false }
        }
        run {
            val o = BitmapFactory.Options()
            o.inScaled = false
            val bm = BitmapFactory.decodeResource(ctx.resources, R.drawable.lobby_scene, o)
            if (bm != null) {
                bgW = bm.width
                bgH = bm.height
                bgTex = makeTex(bm)
                bm.recycle()
            }
        }
        val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val pt = Paint()
        pt.shader = RadialGradient(32f, 32f, 32f, intArrayOf(0xAA000000.toInt(), 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, 64f, 64f, pt)
        shTex = makeTex(b)
        b.recycle()
        last = SystemClock.uptimeMillis()
    }

    private fun isPot(v: Int) = v > 0 && (v and (v - 1)) == 0

    private fun makeTex(bmp: Bitmap): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        val pot = isPot(bmp.width) && isPot(bmp.height)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, if (pot) GLES20.GL_LINEAR_MIPMAP_LINEAR else GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        val wrap = if (pot) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        if (pot) GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        return ids[0]
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        sw = max(1, width)
        sh = max(1, height)
        GLES20.glViewport(0, 0, sw, sh)
    }

    private fun startLoad(i: Int, key: String) {
        Thread {
            val inst = Inst.create(ctx, key)
            if (inst != null) ready[i] = inst
        }.start()
    }

    private fun drawArrays(pos: FloatBuffer, nor: FloatBuffer, uv: FloatBuffer, idx: ShortBuffer, count: Int) {
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, pos)
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, 0, nor)
        GLES20.glVertexAttribPointer(2, 2, GLES20.GL_FLOAT, false, 0, uv)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_SHORT, idx)
    }

    private fun drawBg() {
        if (bgTex == 0) return
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        val sa = sw.toFloat() / sh.toFloat()
        val ia = bgW.toFloat() / bgH.toFloat()
        var us = 1f
        var vs = 1f
        if (sa > ia) vs = ia / sa else us = sa / ia
        val pan = (sin(t * 0.16f) * 0.5f + 0.5f)
        var uo = (1f - us) * pan
        val vo = (1f - vs) * 0.85f
        Matrix.setIdentityM(mvp, 0)
        Matrix.scaleM(mvp, 0, 1.02f, 1.02f, 1f)
        Matrix.setIdentityM(modelM, 0)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelM, 0)
        val dim = if (mode == 0) 1f else 0.36f
        GLES20.glUniform4f(uColor, dim, dim, dim, 1f)
        GLES20.glUniform1f(uUseTex, 1f)
        GLES20.glUniform1f(uLight, 0f)
        GLES20.glUniform1f(uCut, 0f)
        GLES20.glUniform2f(uUvS, us, vs)
        GLES20.glUniform2f(uUvO, uo, vo)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTex)
        GLES20.glUniform1i(uTex, 0)
        drawArrays(quadPos, quadNor, quadUv, quadIdx, 6)
        GLES20.glUniform2f(uUvS, 1f, 1f)
        GLES20.glUniform2f(uUvO, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        uo = 0f
    }

    private fun drawShadow(x: Float) {
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, x, 0f, 0f)
        Matrix.multiplyMM(mvp, 0, vp, 0, modelM, 0)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelM, 0)
        GLES20.glUniform4f(uColor, 1f, 1f, 1f, 1f)
        GLES20.glUniform1f(uUseTex, 1f)
        GLES20.glUniform1f(uLight, 0f)
        GLES20.glUniform1f(uCut, 0f)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, shTex)
        GLES20.glDepthMask(false)
        drawArrays(shPos, quadNor, shUv, quadIdx, 6)
        GLES20.glDepthMask(true)
    }

    private fun drawInst(inst: Inst, x: Float, yaw: Float) {
        val m = inst.model
        Matrix.setIdentityM(place, 0)
        Matrix.translateM(place, 0, x, if (inst.isChar) 0f else 1.1f, 0f)
        Matrix.rotateM(place, 0, yaw, 0f, 1f, 0f)
        Matrix.scaleM(place, 0, inst.sc, inst.sc, inst.sc)
        Matrix.translateM(place, 0, -inst.cx, -inst.cy, -inst.cz)
        for (p in m.prims) {
            if (p.skin >= 0) {
                System.arraycopy(place, 0, modelM, 0, 16)
            } else {
                Matrix.multiplyMM(modelM, 0, place, 0, m.world, p.node * 16)
            }
            Matrix.multiplyMM(mvp, 0, vp, 0, modelM, 0)
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(uModel, 1, false, modelM, 0)
            var useTex = 0f
            if (p.img >= 0 && p.img < m.images.size) {
                if (m.texIds[p.img] == 0) {
                    val bm = m.images[p.img]
                    if (bm != null) m.texIds[p.img] = makeTex(bm)
                }
                if (m.texIds[p.img] != 0) {
                    useTex = 1f
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m.texIds[p.img])
                    GLES20.glUniform1i(uTex, 0)
                }
            }
            GLES20.glUniform4f(uColor, p.color[0], p.color[1], p.color[2], p.color[3])
            GLES20.glUniform1f(uUseTex, useTex)
            GLES20.glUniform1f(uLight, 1f)
            GLES20.glUniform1f(uCut, if (p.mask) 0.5f else 0f)
            if (p.skin >= 0) {
                p.bPos.position(0)
                p.bPos.put(p.outPos)
                p.bPos.position(0)
                p.bNor.position(0)
                p.bNor.put(p.outNor)
                p.bNor.position(0)
            } else if (!p.uploaded) {
                p.bPos.put(p.pos)
                p.bPos.position(0)
                p.bNor.put(p.nor)
                p.bNor.position(0)
                p.uploaded = true
            }
            p.bIdx.position(0)
            drawArrays(p.bPos, p.bNor, p.bUv, p.bIdx, p.idx.size)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.uptimeMillis()
        val dt = min(0.05f, (now - last) / 1000f)
        last = now
        t += dt
        if (!dragging) yawAuto += dt * (if (mode == 0) 0f else 16f)

        val targets: List<String> = if (mode == 2) listOf("W:" + weapon) else chars.toList()
        for (i in 0..1) {
            val tgt = targets.getOrNull(i) ?: ""
            if (tgt.isEmpty()) {
                slots[i] = null
                wantKey[i] = ""
                continue
            }
            if (wantKey[i] != tgt) {
                wantKey[i] = tgt
                slots[i] = null
                ready[i] = null
                startLoad(i, tgt)
            }
            val rd = ready[i]
            if (rd != null && rd.key == tgt) {
                slots[i] = rd
                ready[i] = null
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(prog)
        GLES20.glUniform2f(uUvS, 1f, 1f)
        GLES20.glUniform2f(uUvO, 0f, 0f)
        val aspect = sw.toFloat() / sh.toFloat()
        when (mode) {
            0 -> {
                Matrix.perspectiveM(proj, 0, 32f, aspect, 0.1f, 60f)
                Matrix.setLookAtM(view, 0, 0f, 1.5f, 6.0f, 0f, 1.05f, 0f, 0f, 1f, 0f)
            }
            1 -> {
                Matrix.perspectiveM(proj, 0, 30f, aspect, 0.1f, 60f)
                Matrix.setLookAtM(view, 0, 0f, 1.05f, 5.0f, 0f, 0.95f, 0f, 0f, 1f, 0f)
            }
            else -> {
                Matrix.perspectiveM(proj, 0, 30f, aspect, 0.1f, 60f)
                Matrix.setLookAtM(view, 0, 0f, 0.4f, 6.2f, 0f, 1.1f, 0f, 0f, 1f, 0f)
            }
        }
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        drawBg()

        var et = (now - emoteT0) / 1000f
        var em = emoteId
        if (em >= 0 && et > Inst.EMOTE_DUR) {
            emoteId = -1
            em = -1
        }
        if (em < 0) et = 0f

        val yaw = if (mode == 0) yawUser + sin(t * 0.5f) * 6f else yawUser + yawAuto
        val count = slots.count { it != null }
        var idx = 0
        for (i in 0..1) {
            val inst = slots[i] ?: continue
            val x = if (mode == 0 && count == 2) (if (idx == 0) -1.0f else 1.0f) else 0f
            if (inst.isChar) {
                inst.pose(t, em, et)
                if (mode == 0) drawShadow(x)
            }
            drawInst(inst, x, if (inst.isChar && mode == 0 && idx == 1) -yaw else yaw)
            idx++
        }
    }
}

// =================== COMPOSABLE ===================
@Composable
fun rememberGl(mode: Int): Gl3DRenderer {
    val ctx = LocalContext.current
    return remember(mode) { Gl3DRenderer(ctx.applicationContext, mode) }
}

@Composable
fun Gl3D(r: Gl3DRenderer, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    val holder = remember { arrayOfNulls<GLSurfaceView>(1) }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            val v = GLSurfaceView(c)
            v.setEGLContextClientVersion(2)
            v.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            v.setRenderer(r)
            v.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            v.setOnTouchListener { _, e ->
                r.touch(e)
                true
            }
            holder[0] = v
            v
        }
    )
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) holder[0]?.onResume()
            else if (ev == Lifecycle.Event.ON_PAUSE) holder[0]?.onPause()
        }
        owner.lifecycle.addObserver(ob)
        onDispose {
            owner.lifecycle.removeObserver(ob)
            holder[0]?.onPause()
        }
    }
}
