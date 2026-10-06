package com.lumi.galeria.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Fotos casi iguales tomadas seguidas. [best] es la más nítida. */
class PhotoStack(val members: List<MediaItem>, val best: MediaItem)

private class Analysis(val modified: Long, val hash: Long, val sharp: Float)

/**
 * Busca fotos repetidas sin salir del teléfono: compara una huella visual de 64 bits
 * entre fotos tomadas con menos de un minuto de diferencia y puntúa la nitidez de cada una.
 */
class Analyzer(private val context: Context) {
    private val file = File(context.filesDir, "analysis.tsv")
    private val cache = HashMap<Long, Analysis>()
    private var loaded = false
    private var dirty = false

    private fun load() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 4) cache[p[0].toLong()] = Analysis(p[1].toLong(), p[2].toLong(), p[3].toFloat())
            }
        }
    }

    private fun save(alive: Set<Long>) {
        if (!dirty) return
        dirty = false
        cache.keys.retainAll(alive)
        runCatching {
            file.bufferedWriter().use { w ->
                cache.forEach { (id, a) -> w.write("$id\t${a.modified}\t${a.hash}\t${a.sharp}\n") }
            }
        }
    }

    private fun analyze(item: MediaItem): Analysis? {
        cache[item.id]?.let { if (it.modified == item.modified) return it }
        val bmp = try {
            context.contentResolver.loadThumbnail(item.uri, Size(256, 256), null)
        } catch (e: Exception) {
            return null
        }
        val result = Analysis(item.modified, differenceHash(bmp), sharpness(bmp))
        bmp.recycle()
        cache[item.id] = result
        dirty = true
        return result
    }

    suspend fun findStacks(items: List<MediaItem>, kept: Set<Long>): List<PhotoStack> {
        load()
        val photos = items.filter { !it.isVideo && it.id !in kept }
        val stacks = ArrayList<PhotoStack>()
        val group = ArrayList<Pair<MediaItem, Analysis>>()

        fun close() {
            if (group.size >= 2) {
                val best = group.maxBy { it.second.sharp }.first
                stacks += PhotoStack(group.map { it.first }, best)
            }
            group.clear()
        }

        for (i in photos.indices) {
            coroutineContext.ensureActive()
            val photo = photos[i]
            val prevNear = i > 0 && photos[i - 1].date - photo.date <= NEAR_MS
            val nextNear = i < photos.lastIndex && photo.date - photos[i + 1].date <= NEAR_MS
            if (!prevNear) close()
            // Solo se analiza lo que tiene una vecina cercana en el tiempo.
            if (!prevNear && !nextNear) continue
            if (cache[photo.id]?.modified != photo.modified) Quiet.whenIdle()
            val a = analyze(photo)
            if (a == null) {
                close()
                continue
            }
            // Basta con parecerse a cualquiera de la pila: una toma movida en medio no la parte en dos.
            if (group.isNotEmpty() && group.none { java.lang.Long.bitCount(it.second.hash xor a.hash) <= MAX_DISTANCE }) close()
            group += photo to a
        }
        close()
        save(items.mapTo(HashSet()) { it.id })
        return stacks
    }

    private companion object {
        const val NEAR_MS = 30_000L
        const val MAX_DISTANCE = 10
    }
}

internal fun gray(pixel: Int): Int =
    ((pixel shr 16 and 0xFF) * 299 + (pixel shr 8 and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000

/** dHash: cada bit dice si un píxel es más claro que su vecino en una miniatura de 9x8. */
internal fun differenceHash(source: Bitmap): Long {
    val small = Bitmap.createScaledBitmap(source.asSoftware(), 9, 8, true)
    val px = IntArray(72)
    small.getPixels(px, 0, 9, 0, 0, 9, 8)
    var hash = 0L
    for (y in 0 until 8) for (x in 0 until 8) {
        hash = hash shl 1
        if (gray(px[y * 9 + x]) > gray(px[y * 9 + x + 1])) hash = hash or 1
    }
    return hash
}

/** Varianza del laplaciano: cuanto más alta, más bordes definidos tiene la imagen. */
private fun sharpness(source: Bitmap): Float {
    val bmp = source.asSoftware()
    val w = bmp.width
    val h = bmp.height
    if (w < 3 || h < 3) return 0f
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    for (i in px.indices) px[i] = gray(px[i])
    var sum = 0.0
    var sumSq = 0.0
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val i = y * w + x
        val lap = (px[i - 1] + px[i + 1] + px[i - w] + px[i + w] - 4 * px[i]).toDouble()
        sum += lap
        sumSq += lap * lap
    }
    val n = (w - 2) * (h - 2)
    val mean = sum / n
    return (sumSq / n - mean * mean).toFloat()
}

private fun Bitmap.asSoftware(): Bitmap =
    if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
