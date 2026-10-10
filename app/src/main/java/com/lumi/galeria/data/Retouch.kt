package com.lumi.galeria.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.util.Half
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Retoque local del editor de fotos: borrar con el dedo, ajustes por zonas, caras, marcos y revelado RAW.
// Todas las coordenadas van de 0 a 1 sobre la foto sin girar ni recortar.

data class P(val x: Float, val y: Float)

/** Un trazo del dedo. [radius] es proporcional al lado largo de la foto. */
data class Stroke(val points: List<P>, val radius: Float)

enum class ZoneKind(val label: String) { GRADIENT("Degradado"), CIRCLE("Círculo"), BRUSH("Pincel") }

/** Una zona con sus ajustes, cada uno de -1 a 1. */
data class Zone(
    val kind: ZoneKind,
    val a: P = P(0.5f, 0.25f),
    val b: P = P(0.5f, 0.6f),
    val strokes: List<Stroke> = emptyList(),
    val light: Float = 0f,
    val contrast: Float = 0f,
    val color: Float = 0f,
    val warmth: Float = 0f,
) {
    val isIdentity: Boolean get() = light == 0f && contrast == 0f && color == 0f && warmth == 0f
    val placed: Boolean get() = kind != ZoneKind.BRUSH || strokes.isNotEmpty()
}

enum class FrameKind(val label: String) {
    NONE("Sin marco"), WHITE("Blanco"), BLACK("Negro"), POLAROID("Polaroid"), BLUR_SQUARE("Desenfocado 1:1"), BLUR_PORTRAIT("Desenfocado 4:5"),
}

data class Frame(val kind: FrameKind = FrameKind.NONE, val size: Float = 0.5f)

/** Retoque de caras con un solo control: [amount] suaviza la piel e ilumina los ojos. */
data class FaceFix(val amount: Float = 0f, val redEye: Boolean = false) {
    val isIdentity: Boolean get() = amount == 0f && !redEye
}

data class RawParams(val ev: Float = 0f, val highlights: Float = 0f, val shadows: Float = 0f, val warmth: Float = 0f) {
    val isZero: Boolean get() = ev == 0f && highlights == 0f && shadows == 0f && warmth == 0f
}

/** Todo el retoque de una foto, para guardarlo en el historial y en el borrador. */
data class Retouch(
    val erase: List<Stroke> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val faces: FaceFix = FaceFix(),
    val frame: Frame = Frame(),
    val raw: RawParams = RawParams(),
) {
    val editsPixels: Boolean get() = erase.isNotEmpty() || zones.any { it.placed && !it.isIdentity } || !faces.isIdentity
}

// ---------- JSON ----------

private fun strokeToJson(s: Stroke) = JSONObject().put("r", s.radius.toDouble()).put("p", JSONArray().also { a -> s.points.forEach { a.put(it.x.toDouble()); a.put(it.y.toDouble()) } })

private fun strokeFromJson(o: JSONObject): Stroke {
    val a = o.getJSONArray("p")
    return Stroke((0 until a.length() / 2).map { P(a.getDouble(it * 2).toFloat(), a.getDouble(it * 2 + 1).toFloat()) }, o.optDouble("r", 0.02).toFloat())
}

fun retouchToJson(r: Retouch): JSONObject = JSONObject().apply {
    put("erase", JSONArray().also { a -> r.erase.forEach { a.put(strokeToJson(it)) } })
    put("zonas", JSONArray().also { a ->
        r.zones.forEach { z ->
            a.put(
                JSONObject().put("k", z.kind.name).put("ax", z.a.x.toDouble()).put("ay", z.a.y.toDouble()).put("bx", z.b.x.toDouble()).put("by", z.b.y.toDouble())
                    .put("l", z.light.toDouble()).put("c", z.contrast.toDouble()).put("s", z.color.toDouble()).put("w", z.warmth.toDouble())
                    .put("t", JSONArray().also { t -> z.strokes.forEach { t.put(strokeToJson(it)) } }),
            )
        }
    })
    put("caras", JSONObject().put("a", r.faces.amount.toDouble()).put("o", r.faces.redEye))
    put("marco", JSONObject().put("k", r.frame.kind.name).put("s", r.frame.size.toDouble()))
    put("raw", JSONObject().put("ev", r.raw.ev.toDouble()).put("h", r.raw.highlights.toDouble()).put("s", r.raw.shadows.toDouble()).put("w", r.raw.warmth.toDouble()))
}

fun retouchFromJson(o: JSONObject?): Retouch {
    if (o == null) return Retouch()
    return runCatching {
        val erase = o.optJSONArray("erase")?.let { a -> (0 until a.length()).map { strokeFromJson(a.getJSONObject(it)) } }.orEmpty()
        val zones = o.optJSONArray("zonas")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val z = a.getJSONObject(i)
                val kind = runCatching { ZoneKind.valueOf(z.getString("k")) }.getOrNull() ?: return@mapNotNull null
                Zone(
                    kind, P(z.optDouble("ax", 0.5).toFloat(), z.optDouble("ay", 0.25).toFloat()), P(z.optDouble("bx", 0.5).toFloat(), z.optDouble("by", 0.6).toFloat()),
                    z.optJSONArray("t")?.let { t -> (0 until t.length()).map { strokeFromJson(t.getJSONObject(it)) } }.orEmpty(),
                    z.optDouble("l", 0.0).toFloat(), z.optDouble("c", 0.0).toFloat(), z.optDouble("s", 0.0).toFloat(), z.optDouble("w", 0.0).toFloat(),
                )
            }
        }.orEmpty()
        val faces = o.optJSONObject("caras")?.let { FaceFix(it.optDouble("a", 0.0).toFloat(), it.optBoolean("o", false)) } ?: FaceFix()
        val frame = o.optJSONObject("marco")?.let { Frame(runCatching { FrameKind.valueOf(it.getString("k")) }.getOrDefault(FrameKind.NONE), it.optDouble("s", 0.5).toFloat()) } ?: Frame()
        val raw = o.optJSONObject("raw")?.let { RawParams(it.optDouble("ev", 0.0).toFloat(), it.optDouble("h", 0.0).toFloat(), it.optDouble("s", 0.0).toFloat(), it.optDouble("w", 0.0).toFloat()) } ?: RawParams()
        Retouch(erase, zones, faces, frame, raw)
    }.getOrDefault(Retouch())
}

// ---------- Máscaras ----------

private fun strokePath(s: Stroke, w: Int, h: Int): Path = Path().apply {
    val first = s.points.firstOrNull() ?: return@apply
    moveTo(first.x * w, first.y * h)
    if (s.points.size == 1) lineTo(first.x * w + 0.1f, first.y * h)
    s.points.drop(1).forEach { lineTo(it.x * w, it.y * h) }
}

private fun alphaOf(bitmap: Bitmap): ByteArray {
    val buffer = ByteBuffer.allocate(bitmap.byteCount)
    bitmap.copyPixelsToBuffer(buffer)
    val out = ByteArray(bitmap.width * bitmap.height)
    val rowBytes = bitmap.rowBytes
    val raw = buffer.array()
    for (y in 0 until bitmap.height) System.arraycopy(raw, y * rowBytes, out, y * bitmap.width, bitmap.width)
    return out
}

/** Un lienzo de solo transparencia donde dibujar una máscara. */
private fun maskCanvas(w: Int, h: Int): Pair<Bitmap, Canvas> {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
    return bmp to Canvas(bmp)
}

private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

// ---------- Borrar ----------

/**
 * Borra lo pintado: rellena el hueco desde el borde hacia dentro con el color de alrededor, lo alisa y le
 * devuelve el grano que tenía la zona. Va bien con motas, cables o manchas pequeñas; no inventa detalles.
 */
fun applyErase(src: Bitmap, strokes: List<Stroke>): Bitmap {
    if (strokes.isEmpty()) return src
    val w = src.width
    val h = src.height
    val long = max(w, h)
    val (maskBmp, canvas) = maskCanvas(w, h)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = 0xFFFFFFFF.toInt() }
    strokes.forEach { s ->
        paint.strokeWidth = (s.radius * long * 2).coerceAtLeast(2f)
        canvas.drawPath(strokePath(s, w, h), paint)
    }
    val alpha = alphaOf(maskBmp)
    maskBmp.recycle()
    val n = w * h
    val hole = BooleanArray(n) { alpha[it].toInt() and 0xFF >= 110 }
    // Un píxel de más alrededor, para que no queden bordes del objeto.
    val grown = hole.copyOf()
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val i = y * w + x
        if (!hole[i] && (hole[i - 1] || hole[i + 1] || hole[i - w] || hole[i + w])) grown[i] = true
    }
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val px = IntArray(n)
    out.getPixels(px, 0, w, 0, 0, w, h)
    val known = BooleanArray(n) { !grown[it] }
    val order = ArrayList<Int>()
    var layer = ArrayList<Int>()
    fun neighbors(i: Int, block: (Int) -> Unit) {
        val x = i % w
        val y = i / w
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until w && ny in 0 until h) block(ny * w + nx)
        }
    }
    val queued = BooleanArray(n)
    for (i in 0 until n) if (grown[i]) {
        var edge = false
        neighbors(i) { if (known[it]) edge = true }
        if (edge) { layer.add(i); queued[i] = true }
    }
    if (layer.isEmpty()) return src
    val firstLayer = ArrayList(layer)
    while (layer.isNotEmpty()) {
        val next = ArrayList<Int>()
        val colors = IntArray(layer.size)
        layer.forEachIndexed { k, i ->
            var r = 0; var g = 0; var b = 0; var c = 0
            neighbors(i) { j -> if (known[j]) { val p = px[j]; r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF; c++ } }
            colors[k] = if (c == 0) px[i] else (0xFF shl 24) or ((r / c) shl 16) or ((g / c) shl 8) or (b / c)
        }
        layer.forEachIndexed { k, i ->
            px[i] = colors[k]
            known[i] = true
            order.add(i)
            neighbors(i) { j -> if (grown[j] && !queued[j]) { queued[j] = true; next.add(j) } }
        }
        layer = next
    }
    // Alisar: cada píxel del hueco se acerca a la media de sus cuatro vecinos.
    val rounds = if (order.size > 60_000) 12 else 36
    repeat(rounds) {
        for (i in order) {
            val x = i % w
            val y = i / w
            var r = 0; var g = 0; var b = 0; var c = 0
            if (x > 0) { val p = px[i - 1]; r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF; c++ }
            if (x < w - 1) { val p = px[i + 1]; r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF; c++ }
            if (y > 0) { val p = px[i - w]; r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF; c++ }
            if (y < h - 1) { val p = px[i + w]; r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF; c++ }
            px[i] = (0xFF shl 24) or ((r / c) shl 16) or ((g / c) shl 8) or (b / c)
        }
    }
    // El grano de alrededor: cuánto se desvía cada píxel conocido de sus vecinos, justo al borde del hueco.
    var sum = 0.0
    var count = 0
    for (i in firstLayer) neighbors(i) { j ->
        if (!grown[j]) {
            val x = j % w
            val y = j / w
            if (x in 1 until w - 1 && y in 1 until h - 1) {
                fun lum(p: Int) = ((p shr 16 and 0xFF) * 3 + (p shr 8 and 0xFF) * 6 + (p and 0xFF)) / 10f
                val around = (lum(px[j - 1]) + lum(px[j + 1]) + lum(px[j - w]) + lum(px[j + w])) / 4f
                val d = lum(px[j]) - around
                sum += d * d
                count++
            }
        }
    }
    val grain = if (count > 0) kotlin.math.sqrt(sum / count).toFloat().coerceIn(0f, 14f) else 0f
    val random = java.util.Random(7)
    if (grain > 0.6f) for (i in order) {
        val d = (random.nextGaussian() * grain * 0.8).toInt()
        val p = px[i]
        val r = ((p shr 16 and 0xFF) + d).coerceIn(0, 255)
        val g = ((p shr 8 and 0xFF) + d).coerceIn(0, 255)
        val b = ((p and 0xFF) + d).coerceIn(0, 255)
        px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
}

// ---------- Zonas ----------

private fun zoneMask(zone: Zone, w: Int, h: Int): FloatArray {
    val mask = FloatArray(w * h)
    when (zone.kind) {
        ZoneKind.GRADIENT -> {
            val ax = zone.a.x * w; val ay = zone.a.y * h
            val dx = zone.b.x * w - ax; val dy = zone.b.y * h - ay
            val len2 = (dx * dx + dy * dy).coerceAtLeast(1f)
            for (y in 0 until h) for (x in 0 until w) {
                val t = ((x - ax) * dx + (y - ay) * dy) / len2
                mask[y * w + x] = 1f - smooth(0f, 1f, t)
            }
        }
        ZoneKind.CIRCLE -> {
            val cx = zone.a.x * w; val cy = zone.a.y * h
            val r = hypot(zone.b.x * w - cx, zone.b.y * h - cy).coerceAtLeast(2f)
            for (y in 0 until h) for (x in 0 until w) mask[y * w + x] = 1f - smooth(r * 0.45f, r, hypot(x - cx, y - cy))
        }
        ZoneKind.BRUSH -> {
            val (bmp, canvas) = maskCanvas(w, h)
            val long = max(w, h)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = 0xFFFFFFFF.toInt() }
            zone.strokes.forEach { s ->
                paint.strokeWidth = (s.radius * long * 2).coerceAtLeast(2f)
                paint.maskFilter = BlurMaskFilter((s.radius * long * 0.7f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                canvas.drawPath(strokePath(s, w, h), paint)
            }
            val a = alphaOf(bmp)
            bmp.recycle()
            for (i in mask.indices) mask[i] = (a[i].toInt() and 0xFF) / 255f
        }
    }
    return mask
}

/** Aplica los ajustes de cada zona solo donde su máscara dice. */
fun applyZones(src: Bitmap, zones: List<Zone>): Bitmap {
    val active = zones.filter { it.placed && !it.isIdentity }
    if (active.isEmpty()) return src
    val w = src.width
    val h = src.height
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val px = IntArray(w * h)
    out.getPixels(px, 0, w, 0, 0, w, h)
    for (zone in active) {
        val mask = zoneMask(zone, w, h)
        for (i in px.indices) {
            val m = mask[i]
            if (m <= 0.002f) continue
            val p = px[i]
            var r = (p shr 16 and 0xFF).toFloat()
            var g = (p shr 8 and 0xFF).toFloat()
            var b = (p and 0xFF).toFloat()
            if (zone.light != 0f) {
                val f = 1f + zone.light * m * (if (zone.light > 0) 0.9f else 0.8f)
                r *= f; g *= f; b *= f
            }
            if (zone.contrast != 0f) {
                val k = 1f + zone.contrast * 0.6f * m
                r = (r - 128f) * k + 128f; g = (g - 128f) * k + 128f; b = (b - 128f) * k + 128f
            }
            if (zone.color != 0f) {
                val gray = r * 0.299f + g * 0.587f + b * 0.114f
                val k = 1f + zone.color * m
                r = gray + (r - gray) * k; g = gray + (g - gray) * k; b = gray + (b - gray) * k
            }
            if (zone.warmth != 0f) {
                r *= 1f + zone.warmth * 0.16f * m
                b *= 1f - zone.warmth * 0.16f * m
            }
            px[i] = (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
    }
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
}

// ---------- Caras ----------

class FaceShape(val oval: List<P>, val eyes: List<List<P>>, val keepOut: List<List<P>>)

/** Busca las caras y sus contornos (óvalo, ojos, cejas y labios). Tarda un poco: no va en el hilo de la pantalla. */
suspend fun detectFaceShapes(bitmap: Bitmap): List<FaceShape> = withContext(Dispatchers.Default) {
    runCatching {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build(),
        )
        try {
            val faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            fun pts(face: com.google.mlkit.vision.face.Face, type: Int) = face.getContour(type)?.points?.map { P(it.x / w, it.y / h) }.orEmpty()
            faces.mapNotNull { f ->
                val oval = pts(f, FaceContour.FACE)
                if (oval.size < 6) return@mapNotNull null
                val eyes = listOf(pts(f, FaceContour.LEFT_EYE), pts(f, FaceContour.RIGHT_EYE)).filter { it.size >= 4 }
                val mouth = pts(f, FaceContour.UPPER_LIP_TOP) + pts(f, FaceContour.LOWER_LIP_BOTTOM).reversed()
                val brows = listOf(FaceContour.LEFT_EYEBROW_TOP, FaceContour.RIGHT_EYEBROW_TOP).map { pts(f, it) + pts(f, if (it == FaceContour.LEFT_EYEBROW_TOP) FaceContour.LEFT_EYEBROW_BOTTOM else FaceContour.RIGHT_EYEBROW_BOTTOM).reversed() }
                FaceShape(oval, eyes, (eyes + listOf(mouth) + brows).filter { it.size >= 4 })
            }
        } finally {
            detector.close()
        }
    }.getOrDefault(emptyList())
}

private fun polygon(points: List<P>, w: Int, h: Int, dx: Float, dy: Float): Path = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * w - dx, p.y * h - dy) else lineTo(p.x * w - dx, p.y * h - dy) }
    close()
}

/** Desenfoque de caja rápido, por canales y en dos pasadas. */
private fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int): IntArray {
    if (radius < 1) return px.copyOf()
    fun pass(input: IntArray, horizontal: Boolean): IntArray {
        val out = IntArray(input.size)
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        for (line in 0 until lines) {
            var r = 0; var g = 0; var b = 0
            fun at(k: Int) = if (horizontal) input[line * w + k.coerceIn(0, length - 1)] else input[k.coerceIn(0, length - 1) * w + line]
            for (k in -radius..radius) { val p = at(k); r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF }
            val size = radius * 2 + 1
            for (k in 0 until length) {
                val v = (0xFF shl 24) or ((r / size) shl 16) or ((g / size) shl 8) or (b / size)
                if (horizontal) out[line * w + k] = v else out[k * w + line] = v
                val add = at(k + radius + 1)
                val sub = at(k - radius)
                r += (add shr 16 and 0xFF) - (sub shr 16 and 0xFF)
                g += (add shr 8 and 0xFF) - (sub shr 8 and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
        }
        return out
    }
    return pass(pass(px, true), false)
}

/** Suaviza la piel conservando los bordes, ilumina los ojos y quita el rojo de los ojos. */
fun applyFaces(src: Bitmap, shapes: List<FaceShape>, fix: FaceFix): Bitmap {
    if (fix.isIdentity || shapes.isEmpty()) return src
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val fw = out.width
    val fh = out.height
    for (shape in shapes) {
        val xs = shape.oval.map { it.x * fw }
        val ys = shape.oval.map { it.y * fh }
        val faceW = xs.max() - xs.min()
        val margin = (faceW * 0.12f).toInt() + 2
        val x0 = (xs.min().toInt() - margin).coerceIn(0, fw - 1)
        val y0 = (ys.min().toInt() - margin).coerceIn(0, fh - 1)
        val x1 = (xs.max().toInt() + margin).coerceIn(x0 + 1, fw)
        val y1 = (ys.max().toInt() + margin).coerceIn(y0 + 1, fh)
        val rw = x1 - x0
        val rh = y1 - y0
        val px = IntArray(rw * rh)
        out.getPixels(px, 0, rw, x0, y0, rw, rh)

        // Máscara de piel: el óvalo menos ojos, boca y cejas, con borde suave.
        val (skinBmp, skinCanvas) = maskCanvas(rw, rh)
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); maskFilter = BlurMaskFilter((faceW * 0.03f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL) }
        skinCanvas.drawPath(polygon(shape.oval, fw, fh, x0.toFloat(), y0.toFloat()), soft)
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR); maskFilter = BlurMaskFilter((faceW * 0.012f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL) }
        shape.keepOut.forEach { skinCanvas.drawPath(polygon(it, fw, fh, x0.toFloat(), y0.toFloat()), clear) }
        val skin = alphaOf(skinBmp)
        skinBmp.recycle()

        // Máscara de ojos.
        val (eyeBmp, eyeCanvas) = maskCanvas(rw, rh)
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); maskFilter = BlurMaskFilter((faceW * 0.008f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL) }
        shape.eyes.forEach { eyeCanvas.drawPath(polygon(it, fw, fh, x0.toFloat(), y0.toFloat()), eyePaint) }
        val eyes = alphaOf(eyeBmp)
        eyeBmp.recycle()

        if (fix.amount > 0f) {
            val blurred = boxBlur(px, rw, rh, (faceW / 55f).toInt().coerceIn(1, 14))
            for (i in px.indices) {
                val m = (skin[i].toInt() and 0xFF) / 255f * fix.amount
                if (m <= 0.01f) continue
                val p = px[i]
                val q = blurred[i]
                fun lum(c: Int) = (c shr 16 and 0xFF) * 0.299f + (c shr 8 and 0xFF) * 0.587f + (c and 0xFF) * 0.114f
                // Donde hay un borde de verdad (ojos, pelo, sombras duras) se suaviza menos.
                val keep = 1f - smooth(6f, 34f, abs(lum(p) - lum(q)))
                val k = m * keep * 0.9f
                val r = (p shr 16 and 0xFF) + ((q shr 16 and 0xFF) - (p shr 16 and 0xFF)) * k
                val g = (p shr 8 and 0xFF) + ((q shr 8 and 0xFF) - (p shr 8 and 0xFF)) * k
                val b = (p and 0xFF) + ((q and 0xFF) - (p and 0xFF)) * k
                px[i] = (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
            }
        }
        for (i in px.indices) {
            val m = (eyes[i].toInt() and 0xFF) / 255f
            if (m <= 0.01f) continue
            val p = px[i]
            var r = (p shr 16 and 0xFF).toFloat()
            var g = (p shr 8 and 0xFF).toFloat()
            var b = (p and 0xFF).toFloat()
            if (fix.redEye && r > 60f && r > g * 1.45f && r > b * 1.45f) {
                val target = (g + b) / 2f * 0.9f
                r += (target - r) * m
            }
            if (fix.amount > 0f) {
                val lift = fix.amount * 0.22f * m
                r += (255f - r) * lift; g += (255f - g) * lift; b += (255f - b) * lift
            }
            px[i] = (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
        out.setPixels(px, 0, rw, x0, y0, rw, rh)
    }
    return out
}

// ---------- Marcos ----------

/** Pone el marco a una foto ya recortada y con color. */
fun applyFrame(src: Bitmap, frame: Frame): Bitmap {
    if (frame.kind == FrameKind.NONE) return src
    val w = src.width
    val h = src.height
    val long = max(w, h)
    val t = 0.25f + frame.size.coerceIn(0f, 1f) * 0.75f
    fun draw(outW: Int, outH: Int, left: Float, top: Float, background: (Canvas) -> Unit): Bitmap {
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        background(canvas)
        canvas.drawBitmap(src, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
    return when (frame.kind) {
        FrameKind.WHITE, FrameKind.BLACK -> {
            val b = (long * 0.045f * t * 2).toInt().coerceAtLeast(4)
            val color = if (frame.kind == FrameKind.WHITE) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            draw(w + b * 2, h + b * 2, b.toFloat(), b.toFloat()) { it.drawColor(color) }
        }
        FrameKind.POLAROID -> {
            val b = (long * 0.04f * t * 2).toInt().coerceAtLeast(4)
            draw(w + b * 2, h + b + b * 4, b.toFloat(), b.toFloat()) { it.drawColor(0xFFF8F5EE.toInt()) }
        }
        else -> {
            val ratio = if (frame.kind == FrameKind.BLUR_SQUARE) 1f else 4f / 5f
            val margin = (long * 0.04f * t * 2).toInt()
            // El lienzo: el más pequeño con la proporción pedida donde cabe la foto con su margen.
            val needW = w + margin * 2
            val needH = h + margin * 2
            val canvasW: Int
            val canvasH: Int
            if (needW / needH.toFloat() > ratio) { canvasW = needW; canvasH = (needW / ratio).toInt() } else { canvasH = needH; canvasW = (needH * ratio).toInt() }
            val tiny = Bitmap.createScaledBitmap(src, (canvasW / 26).coerceAtLeast(2), (canvasH / 26).coerceAtLeast(2), true)
            val out = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            // Fondo: la misma foto, ampliada hasta cubrir y muy desenfocada, algo más oscura.
            val cover = max(canvasW / src.width.toFloat(), canvasH / src.height.toFloat())
            val blurred = Bitmap.createScaledBitmap(tiny, (src.width * cover).toInt().coerceAtLeast(1), (src.height * cover).toInt().coerceAtLeast(1), true)
            canvas.drawBitmap(blurred, (canvasW - blurred.width) / 2f, (canvasH - blurred.height) / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawColor(0x40000000)
            canvas.drawBitmap(src, (canvasW - w) / 2f, (canvasH - h) / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
            tiny.recycle()
            blurred.recycle()
            out
        }
    }
}

// ---------- RAW ----------

/**
 * Abre un DNG pidiendo el espacio de color lineal ampliado: si el sistema lo da en coma flotante de 16 bits,
 * conserva luces y sombras que un JPEG ya habría perdido. Si no, queda con lo mismo que ve el resto de la app.
 */
fun decodeRaw(context: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB))
        val long = max(info.size.width, info.size.height)
        if (long > maxSide) {
            val k = maxSide.toFloat() / long
            decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
        }
    }
}.getOrNull()

/** Si la imagen tiene margen de verdad (coma flotante) y no solo 8 bits. */
fun hasRawHeadroom(bitmap: Bitmap): Boolean = bitmap.config == Bitmap.Config.RGBA_F16

private val OETF: ByteArray by lazy {
    ByteArray(4097) { i ->
        val v = i / 4096f
        val s = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
        (s * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
    }
}

private fun toSrgb8(linear: Float): Int = OETF[(linear.coerceIn(0f, 1f) * 4096f).toInt()].toInt() and 0xFF

/** Revela la imagen en luz lineal: exposición, recuperar luces, abrir sombras y calidez. */
fun developRaw(raw: Bitmap, params: RawParams): Bitmap {
    val w = raw.width
    val h = raw.height
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val result = IntArray(w * h)
    val gain = 2f.pow(params.ev * 2f)
    val recover = max(0f, -params.highlights) * 3f
    val push = max(0f, params.highlights) * 0.4f
    fun shade(rIn: Float, gIn: Float, bIn: Float, i: Int) {
        var r = rIn * gain * (1f + params.warmth * 0.15f)
        var g = gIn * gain
        var b = bIn * gain * (1f - params.warmth * 0.15f)
        fun tone(x: Float): Float {
            var v = x
            if (v > 0.8f) v = 0.8f + (v - 0.8f) / (1f + (v - 0.8f) * recover) * (1f + push)
            if (params.shadows != 0f) v *= 1f + params.shadows * (1f - smooth(0f, 0.3f, v)) * 2f
            return v
        }
        r = tone(r); g = tone(g); b = tone(b)
        result[i] = (0xFF shl 24) or (toSrgb8(r) shl 16) or (toSrgb8(g) shl 8) or toSrgb8(b)
    }
    if (raw.config == Bitmap.Config.RGBA_F16) {
        val buffer = ByteBuffer.allocateDirect(raw.byteCount).order(ByteOrder.nativeOrder())
        raw.copyPixelsToBuffer(buffer)
        buffer.rewind()
        val shorts = buffer.asShortBuffer()
        val perRow = raw.rowBytes / 2
        for (y in 0 until h) for (x in 0 until w) {
            val base = y * perRow + x * 4
            shade(Half.toFloat(shorts.get(base)), Half.toFloat(shorts.get(base + 1)), Half.toFloat(shorts.get(base + 2)), y * w + x)
        }
    } else {
        val srgbToLinear = FloatArray(256) { i -> val v = i / 255f; if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f) }
        val px = IntArray(w * h)
        raw.copy(Bitmap.Config.ARGB_8888, false).getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val p = px[i]
            shade(srgbToLinear[p shr 16 and 0xFF], srgbToLinear[p shr 8 and 0xFF], srgbToLinear[p and 0xFF], i)
        }
    }
    out.setPixels(result, 0, w, 0, 0, w, h)
    return out
}
