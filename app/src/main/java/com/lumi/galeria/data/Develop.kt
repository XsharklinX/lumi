package com.lumi.galeria.data

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Canales de las curvas: todos a la vez o uno solo. */
enum class Channel(val label: String) { ALL("Todo"), RED("Rojo"), GREEN("Verde"), BLUE("Azul") }

/** Gamas de color que se pueden tocar por separado; [center] es su tono en grados. */
enum class Band(val label: String, val center: Float, val swatch: Int) {
    RED("Rojos", 0f, 0xFFE5484D.toInt()), ORANGE("Naranjas", 30f, 0xFFF7883B.toInt()), YELLOW("Amarillos", 58f, 0xFFF5D547.toInt()),
    GREEN("Verdes", 120f, 0xFF46B562.toInt()), AQUA("Turquesas", 180f, 0xFF3CC4C4.toInt()), BLUE("Azules", 225f, 0xFF3F7BF0.toInt()),
    PURPLE("Morados", 275f, 0xFF8E5BFF.toInt()), MAGENTA("Rosas", 320f, 0xFFE85AC3.toInt()),
}

/** Ajuste de una gama: giro de tono, saturación y luminosidad, de -1 a 1. */
data class BandShift(val hue: Float = 0f, val saturation: Float = 0f, val lightness: Float = 0f) {
    val isZero: Boolean get() = hue == 0f && saturation == 0f && lightness == 0f
}

/** Una curva: puntos de 0 a 1, ordenados, siempre con los extremos. */
data class Curve(val points: List<Pair<Float, Float>> = listOf(0f to 0f, 1f to 1f)) {
    val isIdentity: Boolean get() = points.all { abs(it.first - it.second) < 0.004f }

    /** Tabla de 256 valores, con una curva suave que pasa por los puntos sin dar saltos. */
    fun table(): IntArray {
        val p = points.sortedBy { it.first }
        val n = p.size
        val xs = FloatArray(n) { p[it].first }
        val ys = FloatArray(n) { p[it].second }
        // Pendientes monótonas (Fritsch–Carlson): la curva nunca se dobla hacia atrás.
        val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / max(xs[it + 1] - xs[it], 1e-4f) }
        val m = FloatArray(n) { i ->
            when (i) {
                0 -> d[0]
                n - 1 -> d[n - 2]
                else -> if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2
            }
        }
        for (i in 0 until n - 1) {
            if (d[i] == 0f) {
                m[i] = 0f
                m[i + 1] = 0f
            } else {
                val a = m[i] / d[i]
                val b = m[i + 1] / d[i]
                val s = a * a + b * b
                if (s > 9f) {
                    val t = 3f / kotlin.math.sqrt(s)
                    m[i] = t * a * d[i]
                    m[i + 1] = t * b * d[i]
                }
            }
        }
        return IntArray(256) { v ->
            val x = v / 255f
            var k = 0
            while (k < n - 2 && x > xs[k + 1]) k++
            val h = max(xs[k + 1] - xs[k], 1e-4f)
            val t = ((x - xs[k]) / h).coerceIn(0f, 1f)
            val t2 = t * t
            val t3 = t2 * t
            val y = (2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * h * m[k] + (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * h * m[k + 1]
            (y * 255f).toInt().coerceIn(0, 255)
        }
    }
}

/** Todo lo que el editor hace píxel a píxel; lo demás (luz, color, filtros) va con una matriz de color. */
data class Develop(
    val curves: Map<Channel, Curve> = emptyMap(),
    val bands: Map<Band, BandShift> = emptyMap(),
    /** De -1 a 1: más de 0 aclara lo oscuro / oscurece lo claro. */
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    /** De 0 a 1. */
    val sharpen: Float = 0f,
    val denoise: Float = 0f,
    val grain: Float = 0f,
) {
    val isIdentity: Boolean
        get() = curves.values.all { it.isIdentity } && bands.values.all { it.isZero } && shadows == 0f && highlights == 0f &&
            sharpen == 0f && denoise == 0f && grain == 0f

    fun curve(channel: Channel): Curve = curves[channel] ?: Curve()
}

/** Aplica [d] a [source] y devuelve una imagen nueva del mismo tamaño. */
fun develop(source: Bitmap, d: Develop): Bitmap {
    if (d.isIdentity) return source
    val w = source.width
    val h = source.height
    var px = IntArray(w * h)
    source.getPixels(px, 0, w, 0, 0, w, h)

    if (d.denoise > 0f) px = denoise(px, w, h, d.denoise)

    val all = d.curve(Channel.ALL).table()
    val red = d.curve(Channel.RED).table()
    val green = d.curve(Channel.GREEN).table()
    val blue = d.curve(Channel.BLUE).table()
    val curved = d.curves.values.any { !it.isIdentity }
    val bands = d.bands.filterValues { !it.isZero }
    val tones = d.shadows != 0f || d.highlights != 0f
    val hsl = FloatArray(3)

    for (i in px.indices) {
        val p = px[i]
        var r = (p shr 16 and 0xFF) / 255f
        var g = (p shr 8 and 0xFF) / 255f
        var b = (p and 0xFF) / 255f
        if (tones) {
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            // Sombras: pesa más cuanto más oscuro; luces: cuanto más claro. Así no se toca el medio.
            val lift = d.shadows * 0.45f * (1 - l) * (1 - l) * (1 - l) * 2f - d.highlights * 0.45f * l * l * l * 2f
            val target = (l + lift).coerceIn(0f, 1f)
            val k = if (l > 0.002f) target / l else 1f
            r = (r * k).coerceIn(0f, 1f)
            g = (g * k).coerceIn(0f, 1f)
            b = (b * k).coerceIn(0f, 1f)
        }
        if (bands.isNotEmpty()) {
            toHsl(r, g, b, hsl)
            if (hsl[1] > 0.04f) {
                var dh = 0f
                var ds = 0f
                var dl = 0f
                for ((band, shift) in bands) {
                    // Cada gama influye en los tonos cercanos y se apaga suavemente a 45° de distancia.
                    var dist = abs(hsl[0] - band.center)
                    if (dist > 180f) dist = 360f - dist
                    val weight = (1f - dist / 45f).coerceIn(0f, 1f)
                    if (weight == 0f) continue
                    dh += shift.hue * 30f * weight
                    ds += shift.saturation * weight
                    dl += shift.lightness * 0.25f * weight * hsl[1]
                }
                hsl[0] = (hsl[0] + dh + 360f) % 360f
                hsl[1] = (hsl[1] * (1f + ds)).coerceIn(0f, 1f)
                hsl[2] = (hsl[2] + dl).coerceIn(0f, 1f)
                val back = fromHsl(hsl[0], hsl[1], hsl[2])
                r = (back shr 16 and 0xFF) / 255f
                g = (back shr 8 and 0xFF) / 255f
                b = (back and 0xFF) / 255f
            }
        }
        var ri = (r * 255f).toInt().coerceIn(0, 255)
        var gi = (g * 255f).toInt().coerceIn(0, 255)
        var bi = (b * 255f).toInt().coerceIn(0, 255)
        if (curved) {
            ri = red[all[ri]]
            gi = green[all[gi]]
            bi = blue[all[bi]]
        }
        px[i] = (p and 0xFF000000.toInt()) or (ri shl 16) or (gi shl 8) or bi
    }

    if (d.sharpen > 0f) px = sharpen(px, w, h, d.sharpen)
    if (d.grain > 0f) {
        // Grano fijo (misma semilla): la vista previa y la copia guardada se parecen.
        val random = java.util.Random(7)
        val amount = d.grain * 38f
        for (i in px.indices) {
            val n = (random.nextGaussian() * amount).toInt()
            val p = px[i]
            px[i] = (p and 0xFF000000.toInt()) or
                (((p shr 16 and 0xFF) + n).coerceIn(0, 255) shl 16) or
                (((p shr 8 and 0xFF) + n).coerceIn(0, 255) shl 8) or
                ((p and 0xFF) + n).coerceIn(0, 255)
        }
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/** Media de 3 x 3, por canal. */
private fun boxBlur(px: IntArray, w: Int, h: Int): IntArray {
    val out = IntArray(px.size)
    for (y in 0 until h) for (x in 0 until w) {
        var r = 0
        var g = 0
        var b = 0
        for (dy in -1..1) {
            val yy = (y + dy).coerceIn(0, h - 1) * w
            for (dx in -1..1) {
                val p = px[yy + (x + dx).coerceIn(0, w - 1)]
                r += p shr 16 and 0xFF
                g += p shr 8 and 0xFF
                b += p and 0xFF
            }
        }
        out[y * w + x] = (px[y * w + x] and 0xFF000000.toInt()) or (r / 9 shl 16) or (g / 9 shl 8) or (b / 9)
    }
    return out
}

/** Máscara de enfoque: refuerza la diferencia de cada píxel con su entorno. */
private fun sharpen(px: IntArray, w: Int, h: Int, amount: Float): IntArray {
    val blur = boxBlur(px, w, h)
    val k = amount * 1.6f
    return IntArray(px.size) { i ->
        val p = px[i]
        val q = blur[i]
        fun ch(s: Int) = ((p shr s and 0xFF) + ((p shr s and 0xFF) - (q shr s and 0xFF)) * k).toInt().coerceIn(0, 255)
        (p and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/** Suaviza solo donde los vecinos se parecen: quita el grano de las zonas lisas sin borrar los bordes. */
private fun denoise(px: IntArray, w: Int, h: Int, amount: Float): IntArray {
    val blur = boxBlur(px, w, h)
    return IntArray(px.size) { i ->
        val p = px[i]
        val q = blur[i]
        val diff = abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)) + abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)) + abs((p and 0xFF) - (q and 0xFF))
        val t = amount * (1f - (diff / 60f)).coerceIn(0f, 1f)
        fun ch(s: Int) = ((p shr s and 0xFF) * (1 - t) + (q shr s and 0xFF) * t).toInt()
        (p and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

private fun toHsl(r: Float, g: Float, b: Float, out: FloatArray) {
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2
    val d = mx - mn
    if (d < 1e-5f) {
        out[0] = 0f
        out[1] = 0f
        out[2] = l
        return
    }
    val s = if (l > 0.5f) d / (2 - mx - mn) else d / (mx + mn)
    val hue = when (mx) {
        r -> ((g - b) / d + (if (g < b) 6 else 0)) * 60f
        g -> ((b - r) / d + 2) * 60f
        else -> ((r - g) / d + 4) * 60f
    }
    out[0] = hue
    out[1] = s
    out[2] = l
}

private fun fromHsl(h: Float, s: Float, l: Float): Int {
    val c = (1 - abs(2 * l - 1)) * s
    val x = c * (1 - abs((h / 60f) % 2 - 1))
    val m = l - c / 2
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return (((r + m) * 255).toInt().coerceIn(0, 255) shl 16) or (((g + m) * 255).toInt().coerceIn(0, 255) shl 8) or ((b + m) * 255).toInt().coerceIn(0, 255)
}

/** Reparto de la luz de [bitmap] en 64 tramos, para dibujar el histograma detrás de las curvas. */
fun histogram(bitmap: Bitmap): FloatArray {
    val bins = IntArray(64)
    val steps = 80
    for (y in 0 until steps) for (x in 0 until steps) {
        val p = bitmap.getPixel((x * 2 + 1) * bitmap.width / (steps * 2), (y * 2 + 1) * bitmap.height / (steps * 2))
        bins[((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000 / 4]++
    }
    val top = bins.max().coerceAtLeast(1)
    return FloatArray(64) { bins[it].toFloat() / top }
}

// ---------- Filtros propios ----------

/** Un filtro guardado por el usuario: los ajustes de color tal como estaban, con un nombre. */
class SavedLook(val name: String, val values: org.json.JSONObject)
