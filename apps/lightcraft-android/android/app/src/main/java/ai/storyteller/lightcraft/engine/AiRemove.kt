package ai.storyteller.lightcraft.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.security.MessageDigest

/**
 * AI Remove (LISTD build): the LaMa inpainting model (Apache-2.0), converted to ONNX by the
 * repository's "LISTD model" workflow and published as its `lama-model` release. Downloaded once
 * into the app's own files, then run on the phone with ONNX Runtime; nothing leaves the phone.
 */
class AiRemove(context: Context) {
    companion object {
        private const val RELEASE = "https://github.com/Earlybird69/lightcraft-android/releases/download/lama-model"
        const val MODEL_URL = "$RELEASE/lama.onnx"
        const val SHA_URL = "$RELEASE/lama.onnx.sha256"

        /** About how big the download is, for the question before it. */
        const val SIZE_MB = 206
    }

    private val dir = File(context.filesDir, "models")
    private val model = File(dir, "lama.onnx")
    private val part = File(dir, "lama.onnx.part")

    /** The model is downloaded and checked. */
    val ready: Boolean
        get() = model.isFile && model.length() > 1_000_000

    /**
     * Download the model (picking up a download cut off before), check it against its published
     * SHA-256 and keep it. [progress] gets the bytes so far and the total (−1: unknown).
     * Blocking; throws when it fails.
     */
    fun download(progress: (Long, Long) -> Unit) {
        dir.mkdirs()
        val expected = String(fetch(SHA_URL, 0).use { (c, _) -> c.inputStream.readBytes() })
            .trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.takeIf { it.length == 64 }
            ?: throw IOException("couldn't read the model's checksum")
        var have = if (part.isFile) part.length() else 0L
        val (conn, code) = fetch(MODEL_URL, have)
        conn.use {
            if (code == 416) {
                // asked past the end: the partial file is already whole (or wrong), checked below
            } else {
                if (code != 206) have = 0L
                val total = conn.getHeaderFieldLong("Content-Length", -1L).let { if (it < 0) -1L else it + have }
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(have)
                    out.seek(have)
                    val buf = ByteArray(1 shl 16)
                    conn.inputStream.use { input ->
                        var lastReport = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            have += n
                            if (have - lastReport >= (1 shl 20)) {
                                lastReport = have
                                progress(have, total)
                            }
                        }
                    }
                }
                progress(have, total)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        part.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val got = digest.digest().joinToString("") { "%02x".format(it) }
        if (got != expected) {
            part.delete()
            throw IOException("the download was damaged; try again")
        }
        if (!part.renameTo(model)) throw IOException("couldn't keep the model")
    }

    /** GET [url] from byte [from] on, following GitHub's redirects ourselves so the range survives them. */
    private fun fetch(url: String, from: Long): Pair<HttpURLConnection, Int> {
        var at = URL(url)
        repeat(6) {
            val c = at.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
            val code = c.responseCode
            if (code in 300..399) {
                val next = c.getHeaderField("Location") ?: throw IOException("download: redirect without a location")
                c.disconnect()
                at = URL(at, next)
            } else {
                if (code !in 200..299 && code != 416) {
                    c.disconnect()
                    throw IOException(if (code == 404) "the model isn't published yet (run the LISTD model workflow)" else "download failed (HTTP $code)")
                }
                return c to code
            }
        }
        throw IOException("download: too many redirects")
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T = try {
        block(this)
    } finally {
        disconnect()
    }

    private inline fun <T> Pair<HttpURLConnection, Int>.use(block: (Pair<HttpURLConnection, Int>) -> T): T = try {
        block(this)
    } finally {
        first.disconnect()
    }

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    @Synchronized
    private fun session(): Pair<OrtEnvironment, OrtSession> {
        val s = session
        val e = env
        if (s != null && e != null) return e to s
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        val created = environment.createSession(model.absolutePath, options)
        env = environment
        session = created
        return environment to created
    }

    /** Let go of the loaded model (a few hundred MB): leaving the editor. */
    @Synchronized
    fun release() {
        session?.close()
        session = null
    }

    /**
     * Fill the masked part of an image: [image] holds RGB8 rows ([width] × [height], both
     * multiples of 8), [mask] one byte a pixel (255: fill). Writes the result's RGB8 rows to
     * [out]. Blocking (seconds); run it off the main thread.
     */
    @Synchronized
    fun fill(image: File, mask: File, width: Int, height: Int, out: File) {
        val rgb = image.readBytes()
        val m = mask.readBytes()
        val plane = width * height
        require(width > 0 && height > 0 && rgb.size == plane * 3 && m.size == plane) { "AI Remove: the input doesn't match its size" }
        val img = FloatBuffer.allocate(plane * 3)
        val msk = FloatBuffer.allocate(plane)
        for (i in 0 until plane) {
            for (c in 0..2) img.put(c * plane + i, (rgb[i * 3 + c].toInt() and 0xFF) / 255f)
            msk.put(i, if ((m[i].toInt() and 0xFF) > 127) 1f else 0f)
        }
        val (environment, s) = session()
        val shape = { ch: Long -> longArrayOf(1, ch, height.toLong(), width.toLong()) }
        OnnxTensor.createTensor(environment, img, shape(3)).use { ti ->
            OnnxTensor.createTensor(environment, msk, shape(1)).use { tm ->
                s.run(mapOf("image" to ti, "mask" to tm)).use { result ->
                    val o = (result.get(0) as OnnxTensor).floatBuffer
                    val bytes = ByteArray(plane * 3)
                    for (i in 0 until plane) {
                        for (c in 0..2) bytes[i * 3 + c] = (o.get(c * plane + i) * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
                    }
                    out.writeBytes(bytes)
                }
            }
        }
    }
}
