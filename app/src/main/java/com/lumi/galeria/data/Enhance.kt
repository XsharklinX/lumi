package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Lado más largo con el que se procesan las fotos: de sobra para ver e imprimir y sin agotar la memoria. */
const val PROCESS_SIDE = 4096

// ---------- Lumi Auto ----------

/**
 * Mejora automática: estira los niveles (que los negros sean negros y los blancos, blancos), aclara
 * lo oscuro sin quemar lo claro, sube el contraste local (lo que quita lo «lavado»), aviva un poco
 * el color si está apagado y da un toque de nitidez. Con [strength] de 0 a 1 se modera todo.
 */
fun lumiAuto(source: Bitmap, strength: Float = 1f, night: Boolean = false): Bitmap {
    val w = source.width
    val h = source.height
    val px = IntArray(w * h)
    source.getPixels(px, 0, w, 0, 0, w, h)

    // Cómo es la luz de la foto: histograma de luminancia muestreado.
    val hist = IntArray(256)
    var satSum = 0f
    var samples = 0
    val step = max(1, (w * h) / 60000)
    var i = 0
    while (i < px.size) {
        val p = px[i]
        val r = p shr 16 and 0xFF
        val g = p shr 8 and 0xFF
        val b = p and 0xFF
        hist[(r * 299 + g * 587 + b * 114) / 1000]++
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        if (mx > 0) satSum += (mx - mn).toFloat() / mx
        samples++
        i += step
    }
    fun percentile(q: Float): Int {
        val target = samples * q
        var acc = 0
        for (v in 0..255) {
            acc += hist[v]
            if (acc >= target) return v
        }
        return 255
    }
    val low = percentile(0.005f)
    val high = percentile(0.995f).coerceAtLeast(low + 40)
    var mean = 0f
    for (v in 0..255) mean += v * hist[v]
    mean /= samples * 255f
    val saturation = satSum / samples
    val clipped = (250..255).sumOf { hist[it] }.toFloat() / samples

    val k = strength.coerceIn(0f, 1f)
    // Niveles: solo se estira, nunca se comprime, y con freno.
    val black = low * 0.8f * k / 255f
    val white = 1f - (255 - high) * 0.8f * k / 255f
    val range = (white - black).coerceAtLeast(0.3f)
    // Curva de medios: si la foto es oscura, se levantan los medios (gamma < 1).
    val target = if (night) 0.47f else 0.46f
    val gamma = if (mean < target) (0.55f + 0.45f * (mean / target)).coerceIn(0.55f, 1f).let { 1f - (1f - it) * k } else 1f
    val shadowLift = (if (night) 0.18f else 0.1f) * k * ((target - mean) / target).coerceIn(0f, 1f)
    val highlightPull = 0.15f * k * (clipped * 20f).coerceIn(0f, 1f)
    val satBoost = 1f + k * (if (saturation < 0.25f) 0.18f else if (saturation < 0.4f) 0.1f else 0.03f)

    // Tabla de tono: niveles, gamma, sombras y luces en un solo paso.
    val lut = IntArray(256) { v ->
        var x = ((v / 255f) - black) / range
        x = x.coerceIn(0f, 1f).pow(gamma)
        x += shadowLift * (1f - x) * (1f - x) * x * 4f
        x -= highlightPull * x * x * x
        (x.coerceIn(0f, 1f) * 255f).toInt()
    }

    // Contraste local: diferencia con una versión muy borrosa de la foto (claridad).
    val blurW = max(2, w / 24)
    val blurH = max(2, h / 24)
    val tiny = Bitmap.createScaledBitmap(source, blurW, blurH, true)
    val soft = Bitmap.createScaledBitmap(tiny, w, h, true)
    tiny.recycle()
    val blur = IntArray(w * h)
    soft.getPixels(blur, 0, w, 0, 0, w, h)
    soft.recycle()
    val clarity = 0.22f * k

    for (j in px.indices) {
        val p = px[j]
        var r = lut[p shr 16 and 0xFF].toFloat()
        var g = lut[p shr 8 and 0xFF].toFloat()
        var b = lut[p and 0xFF].toFloat()
        val l = 0.299f * r + 0.587f * g + 0.114f * b
        val q = blur[j]
        val bl = lut[q shr 16 and 0xFF] * 0.299f + lut[q shr 8 and 0xFF] * 0.587f + lut[q and 0xFF] * 0.114f
        // Claridad suave en los medios; en sombras y luces extremas, menos (para no crear halos).
        val mid = 1f - abs(l / 127.5f - 1f)
        val add = (l - bl) * clarity * mid
        r += add
        g += add
        b += add
        // Color: alrededor de la luminancia.
        val nl = 0.299f * r + 0.587f * g + 0.114f * b
        r = nl + (r - nl) * satBoost
        g = nl + (g - nl) * satBoost
        b = nl + (b - nl) * satBoost
        px[j] = (p and 0xFF000000.toInt()) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
    }
    val toned = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    // Nitidez y, de noche, quitar algo de grano, con el revelado del editor.
    return develop(toned, Develop(sharpen = 0.22f * k, denoise = if (night) 0.35f * k else 0.08f * k))
}

/** Cómo de mejorable es una foto por su color medio: oscura si su luminancia queda por debajo de esto. */
fun isDark(color: Int): Boolean {
    if (color ushr 24 != 0xFF) return false
    val l = ((color shr 16 and 0xFF) * 299 + (color shr 8 and 0xFF) * 587 + (color and 0xFF) * 114) / 1000
    return l < 72
}

// ---------- Varias fotos en una: noche y HDR ----------

private fun grayScaled(px: IntArray, w: Int, h: Int, k: Int): IntArray {
    val sw = w / k
    val sh = h / k
    return IntArray(sw * sh) { i ->
        val p = px[(i / sw) * k * w + (i % sw) * k]
        ((p shr 16 and 0xFF) * 3 + (p shr 8 and 0xFF) * 4 + (p and 0xFF)) shr 3
    }
}

/** Desplazamiento (en píxeles de [b]) con el que [b] encaja mejor con [a], buscando cerca del centro. */
private fun shift(a: IntArray, b: IntArray, w: Int, h: Int, range: Int, normalize: Boolean): Pair<Int, Int> {
    var best = 0 to 0
    var bestSum = Long.MAX_VALUE
    // En HDR las fotos tienen distinta luz: se compara cada una contra su propia media.
    val ma = if (normalize) a.average().toInt() else 0
    val mb = if (normalize) b.average().toInt() else 0
    for (dy in -range..range) for (dx in -range..range) {
        var sum = 0L
        var y = h / 6
        while (y < h * 5 / 6) {
            var x = w / 6
            while (x < w * 5 / 6) {
                sum += abs((a[y * w + x] - ma) - (b[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)] - mb))
                x += 2
            }
            y += 2
        }
        if (sum < bestSum) {
            bestSum = sum
            best = dx to dy
        }
    }
    return best
}

/** Cómo encaja [frame] con [reference]: primero a lo grueso y luego afinando. */
private fun align(reference: IntArray, frame: IntArray, w: Int, h: Int, normalize: Boolean): Pair<Int, Int> {
    val coarse = shift(grayScaled(reference, w, h, 16), grayScaled(frame, w, h, 16), w / 16, h / 16, 4, normalize)
    val sw = w / 4
    val sh = h / 4
    val ga = grayScaled(reference, w, h, 4)
    val gb = grayScaled(frame, w, h, 4)
    // Afinado alrededor de lo grueso, a un cuarto de tamaño.
    val base = coarse.first * 4 to coarse.second * 4
    var best = base
    var bestSum = Long.MAX_VALUE
    for (dy in -4..4) for (dx in -4..4) {
        val sx = base.first + dx
        val sy = base.second + dy
        var sum = 0L
        var y = sh / 6
        while (y < sh * 5 / 6) {
            var x = sw / 6
            while (x < sw * 5 / 6) {
                sum += abs(ga[y * sw + x] - gb[(y + sy).coerceIn(0, sh - 1) * sw + (x + sx).coerceIn(0, sw - 1)])
                x += 2
            }
            y += 2
        }
        if (sum < bestSum) {
            bestSum = sum
            best = sx to sy
        }
    }
    return best.first * 4 to best.second * 4
}

/**
 * Noche Lumi: suma [frames] alineados con el primero. El ruido de cada foto es distinto y al
 * sumarlas se anula; la luz se queda. Después se aclara con Lumi Auto en modo noche.
 * Las fotos se reciben de una en una ([next]) para no tenerlas todas en memoria.
 */
class NightStack(first: Bitmap) {
    private val w = first.width
    private val h = first.height
    private val reference = IntArray(w * h).also { first.getPixels(it, 0, w, 0, 0, w, h) }
    private val r = FloatArray(w * h)
    private val g = FloatArray(w * h)
    private val b = FloatArray(w * h)
    private var count = 0

    init {
        add(reference, 0, 0)
    }

    private fun add(px: IntArray, dx: Int, dy: Int) {
        for (y in 0 until h) {
            val sy = (y + dy).coerceIn(0, h - 1) * w
            for (x in 0 until w) {
                val p = px[sy + (x + dx).coerceIn(0, w - 1)]
                val i = y * w + x
                r[i] += (p shr 16 and 0xFF).toFloat()
                g[i] += (p shr 8 and 0xFF).toFloat()
                b[i] += (p and 0xFF).toFloat()
            }
        }
        count++
    }

    fun next(frame: Bitmap) {
        if (frame.width != w || frame.height != h) return
        val px = IntArray(w * h)
        frame.getPixels(px, 0, w, 0, 0, w, h)
        val (dx, dy) = align(reference, px, w, h, normalize = false)
        add(px, dx, dy)
    }

    fun result(): Bitmap {
        val k = 1f / count
        val out = IntArray(w * h) { i ->
            (0xFF shl 24) or ((r[i] * k).toInt().coerceIn(0, 255) shl 16) or ((g[i] * k).toInt().coerceIn(0, 255) shl 8) or (b[i] * k).toInt().coerceIn(0, 255)
        }
        val stacked = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        return lumiAuto(stacked, 1f, night = true).also { if (it !== stacked) stacked.recycle() }
    }
}

/**
 * HDR Lumi: mezcla una foto oscura, una normal y una clara. Cada píxel toma más de la foto en la
 * que está mejor expuesto (ni negro ni quemado) y con más color. Los pesos se suavizan para que
 * no aparezcan bordes, y las fotos se alinean antes por si la mano se movió.
 */
fun hdrMerge(frames: List<Bitmap>): Bitmap {
    val w = frames[0].width
    val h = frames[0].height
    val all = frames.filter { it.width == w && it.height == h }.map { f -> IntArray(w * h).also { f.getPixels(it, 0, w, 0, 0, w, h) } }
    val middle = all[all.size / 2]
    val shifts = all.map { if (it === middle) 0 to 0 else align(middle, it, w, h, normalize = true) }
    // Peso de cada foto, calculado a un octavo de tamaño y ampliado: así es suave.
    val sw = max(2, w / 8)
    val sh = max(2, h / 8)
    val weights = all.mapIndexed { n, px ->
        val (dx, dy) = shifts[n]
        FloatArray(sw * sh) { i ->
            val x = (i % sw) * 8
            val y = (i / sw) * 8
            val p = px[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
            val rr = (p shr 16 and 0xFF) / 255f
            val gg = (p shr 8 and 0xFF) / 255f
            val bb = (p and 0xFF) / 255f
            val l = 0.299f * rr + 0.587f * gg + 0.114f * bb
            val exposed = exp(-((l - 0.5f) * (l - 0.5f)) / (2 * 0.2f * 0.2f))
            val sat = max(rr, max(gg, bb)) - min(rr, min(gg, bb))
            exposed * (0.4f + sat) + 1e-4f
        }
    }
    // Suavizado de los pesos (media 3 x 3, dos veces).
    fun smooth(v: FloatArray): FloatArray {
        var cur = v
        repeat(2) {
            val out = FloatArray(cur.size)
            for (y in 0 until sh) for (x in 0 until sw) {
                var s = 0f
                for (yy in -1..1) for (xx in -1..1) s += cur[(y + yy).coerceIn(0, sh - 1) * sw + (x + xx).coerceIn(0, sw - 1)]
                out[y * sw + x] = s / 9f
            }
            cur = out
        }
        return cur
    }
    val smooth = weights.map(::smooth)
    val out = IntArray(w * h)
    for (y in 0 until h) {
        val fy = (y / 8f).coerceAtMost(sh - 1f)
        val y0 = fy.toInt()
        val y1 = (y0 + 1).coerceAtMost(sh - 1)
        val ty = fy - y0
        for (x in 0 until w) {
            val fx = (x / 8f).coerceAtMost(sw - 1f)
            val x0 = fx.toInt()
            val x1 = (x0 + 1).coerceAtMost(sw - 1)
            val tx = fx - x0
            var rs = 0f
            var gs = 0f
            var bs = 0f
            var ws = 0f
            for (n in all.indices) {
                val m = smooth[n]
                val wgt = (m[y0 * sw + x0] * (1 - tx) + m[y0 * sw + x1] * tx) * (1 - ty) + (m[y1 * sw + x0] * (1 - tx) + m[y1 * sw + x1] * tx) * ty
                val (dx, dy) = shifts[n]
                val p = all[n][(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
                rs += (p shr 16 and 0xFF) * wgt
                gs += (p shr 8 and 0xFF) * wgt
                bs += (p and 0xFF) * wgt
                ws += wgt
            }
            out[y * w + x] = (0xFF shl 24) or ((rs / ws).toInt().coerceIn(0, 255) shl 16) or ((gs / ws).toInt().coerceIn(0, 255) shl 8) or (bs / ws).toInt().coerceIn(0, 255)
        }
    }
    val merged = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    return lumiAuto(merged, 0.6f).also { if (it !== merged) merged.recycle() }
}

// ---------- Retoques al guardar ----------

/** Filtros de la Cámara Lumi: los mismos en el visor y en la foto guardada. */
enum class CameraLook(val label: String) {
    NONE("Original"), WARM("Cálido"), COOL("Frío"), VIVID("Vivo"), MONO("B y N"), SEPIA("Sepia"), SOFT("Suave"), DRAMA("Drama");

    fun matrix(): ColorMatrix = ColorMatrix().apply {
        when (this@CameraLook) {
            NONE -> Unit
            WARM -> set(floatArrayOf(1.08f, 0f, 0f, 0f, 8f, 0f, 1.02f, 0f, 0f, 2f, 0f, 0f, 0.9f, 0f, -6f, 0f, 0f, 0f, 1f, 0f))
            COOL -> set(floatArrayOf(0.92f, 0f, 0f, 0f, -4f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.1f, 0f, 8f, 0f, 0f, 0f, 1f, 0f))
            VIVID -> setSaturation(1.35f)
            MONO -> setSaturation(0f)
            SEPIA -> set(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
            SOFT -> {
                setSaturation(0.85f)
                postConcat(ColorMatrix(floatArrayOf(0.88f, 0f, 0f, 0f, 22f, 0f, 0.88f, 0f, 0f, 22f, 0f, 0f, 0.88f, 0f, 22f, 0f, 0f, 0f, 1f, 0f)))
            }
            DRAMA -> {
                setSaturation(0.8f)
                postConcat(ColorMatrix(floatArrayOf(1.25f, 0f, 0f, 0f, -30f, 0f, 1.25f, 0f, 0f, -30f, 0f, 0f, 1.25f, 0f, -30f, 0f, 0f, 0f, 1f, 0f)))
            }
        }
    }
}

/** Lo que se le hace a una foto de la Cámara Lumi antes de guardarla. */
data class ShotEdit(
    val auto: Boolean = true,
    val look: CameraLook = CameraLook.NONE,
    /** Grados que está torcida la foto; se endereza si es poco (hasta 8°). */
    val tilt: Float = 0f,
    val mirror: Boolean = false,
    /** Proporción (lado largo entre lado corto) a la que se recorta, centrada; 0 deja la foto como sale. */
    val crop: Float = 0f,
    /** Guardar en HEIC, que ocupa más o menos la mitad que JPEG. */
    val heic: Boolean = false,
    /** Retrato Lumi: desenfocar el fondo detrás de lo principal. */
    val portrait: Boolean = false,
)

/** Recorta [source] por el centro hasta que su lado largo entre el corto sea [ratio]. */
fun cropToRatio(source: Bitmap, ratio: Float): Bitmap {
    if (ratio <= 0f) return source
    val w = source.width
    val h = source.height
    val long = max(w, h)
    val short = minOf(w, h)
    val now = long.toFloat() / short
    if (abs(now - ratio) < 0.01f) return source
    val newLong = if (now > ratio) (short * ratio).toInt() else long
    val newShort = if (now > ratio) short else (long / ratio).toInt()
    val cw = if (w >= h) newLong else newShort
    val ch = if (w >= h) newShort else newLong
    return Bitmap.createBitmap(source, (w - cw) / 2, (h - ch) / 2, cw.coerceAtMost(w), ch.coerceAtMost(h))
}

/** Endereza [source] [degrees] grados y recorta lo justo para que no queden esquinas vacías. */
fun straighten(source: Bitmap, degrees: Float): Bitmap {
    if (abs(degrees) < 0.6f || abs(degrees) > 8f) return source
    val w = source.width
    val h = source.height
    val a = Math.toRadians(abs(degrees).toDouble())
    val scale = (cos(a) + max(w.toFloat() / h, h.toFloat() / w) * sin(a)).toFloat()
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val m = Matrix().apply {
        postTranslate(-w / 2f, -h / 2f)
        postRotate(-degrees)
        postScale(scale, scale)
        postTranslate(w / 2f, h / 2f)
    }
    Canvas(out).drawBitmap(source, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
}

/** Aplica [edit] a [source]: espejo, horizonte, Lumi Auto y filtro, en ese orden. */
fun applyShotEdit(source: Bitmap, edit: ShotEdit): Bitmap {
    var bmp = source
    if (edit.mirror) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postScale(-1f, 1f) }, true)
    bmp = straighten(bmp, edit.tilt)
    bmp = cropToRatio(bmp, edit.crop)
    if (edit.portrait) lumiPortrait(bmp)?.let { bmp = it }
    if (edit.auto) bmp = lumiAuto(bmp)
    if (edit.look != CameraLook.NONE) {
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bmp, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(edit.look.matrix()) })
        bmp = out
    }
    return bmp
}

/** Decodifica una foto (de archivo o de la biblioteca) girada como se ve, a [PROCESS_SIDE] como mucho. */
fun decodeShot(context: Context, uri: Uri): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
        val big = max(info.size.width, info.size.height)
        if (big > PROCESS_SIDE) {
            val k = PROCESS_SIDE.toFloat() / big
            decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
        }
    }
}.getOrNull()

/**
 * Guarda [bitmap] como foto nueva en [path] con el nombre [name] (sin extensión). Copia de [exifFrom]
 * la fecha y los datos de la cámara (la orientación ya va aplicada en la imagen).
 */
fun saveProcessed(context: Context, bitmap: Bitmap, name: String, path: String, exifFrom: File?, heic: Boolean = false): Uri? {
    if (heic) saveHeic(context, bitmap, name, path)?.let { return it }
    val resolver = context.contentResolver
    val temp = File(context.cacheDir, "procesada_${System.nanoTime()}.jpg")
    runCatching {
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (exifFrom != null && exifFrom.exists()) {
            val source = ExifInterface(exifFrom.path)
            val target = ExifInterface(temp.path)
            listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FLASH,
            ).forEach { tag -> source.getAttribute(tag)?.let { target.setAttribute(tag, it) } }
            target.saveAttributes()
        }
    }.onFailure { return null }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(path)) path else "DCIM/Camera/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) }.getOrNull() ?: return null
    val ok = runCatching {
        resolver.openOutputStream(target)!!.use { out -> temp.inputStream().use { it.copyTo(out) } }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    temp.delete()
    if (!ok) runCatching { resolver.delete(target, null, null) }
    return if (ok) target else null
}

/**
 * Guarda [bitmap] en HEIC con el codificador del propio teléfono. Si el teléfono no sabe, devuelve
 * null y la foto se guarda en JPEG.
 */
private fun saveHeic(context: Context, bitmap: Bitmap, name: String, path: String): Uri? {
    val temp = File(context.cacheDir, "procesada_${System.nanoTime()}.heic")
    val encoded = runCatching {
        val writer = androidx.heifwriter.HeifWriter.Builder(temp.path, bitmap.width, bitmap.height, androidx.heifwriter.HeifWriter.INPUT_MODE_BITMAP)
            .setQuality(90).build()
        writer.start()
        writer.addBitmap(bitmap)
        writer.stop(10_000)
        writer.close()
        temp.length() > 0
    }.getOrDefault(false)
    if (!encoded) {
        temp.delete()
        return null
    }
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.heic")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/heif")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(path)) path else "DCIM/Camera/")
        put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) }.getOrNull()
    val ok = target != null && runCatching {
        resolver.openOutputStream(target)!!.use { out -> temp.inputStream().use { it.copyTo(out) } }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    temp.delete()
    if (!ok && target != null) runCatching { resolver.delete(target, null, null) }
    return if (ok) target else null
}

/** Nitidez de una imagen pequeña (varianza del laplaciano): para elegir la mejor de una ráfaga. */
fun burstSharpness(bitmap: Bitmap): Float {
    val small = Bitmap.createScaledBitmap(bitmap, 320, (320f * bitmap.height / bitmap.width).toInt().coerceAtLeast(2), true)
    val w = small.width
    val h = small.height
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    if (small !== bitmap) small.recycle()
    for (i in px.indices) px[i] = ((px[i] shr 16 and 0xFF) * 3 + (px[i] shr 8 and 0xFF) * 4 + (px[i] and 0xFF)) shr 3
    var sum = 0.0
    var sq = 0.0
    var n = 0
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val i = y * w + x
        val lap = (px[i - 1] + px[i + 1] + px[i - w] + px[i + w] - 4 * px[i]).toDouble()
        sum += lap
        sq += lap * lap
        n++
    }
    val mean = sum / n
    return (sq / n - mean * mean).toFloat()
}
