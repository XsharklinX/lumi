package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

// ---------- Subtítulos ----------

/** Una frase de subtítulo y cuándo se enseña, en milisegundos. */
class Cue(val start: Long, val end: Long, val text: String)

private val SRT_TIME = Regex("(\\d+):(\\d+):(\\d+)[,.](\\d+)\\s*-->\\s*(\\d+):(\\d+):(\\d+)[,.](\\d+)")
private val TAGS = Regex("<[^>]+>|\\{[^}]+\\}")

/** Lee un archivo .srt. Lo que no se entiende se salta. */
fun parseSrt(text: String): List<Cue> {
    val cues = ArrayList<Cue>()
    for (block in text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n"))) {
        val lines = block.trim().lines()
        val at = lines.indexOfFirst { SRT_TIME.containsMatchIn(it) }
        if (at < 0) continue
        val m = SRT_TIME.find(lines[at])!!.groupValues
        fun ms(h: String, mi: String, s: String, f: String) = ((h.toLong() * 60 + mi.toLong()) * 60 + s.toLong()) * 1000 + f.padEnd(3, '0').take(3).toLong()
        val body = lines.drop(at + 1).joinToString("\n").replace(TAGS, "").trim()
        if (body.isNotEmpty()) cues += Cue(ms(m[1], m[2], m[3], m[4]), ms(m[5], m[6], m[7], m[8]), body)
    }
    return cues.sortedBy { it.start }
}

/** Subtítulos de un archivo que eligió el usuario. */
fun readSubtitles(context: Context, uri: Uri): List<Cue> = runCatching {
    context.contentResolver.openInputStream(uri)!!.use { parseSrt(decodeText(it.readBytes())) }
}.getOrDefault(emptyList())

/**
 * Busca junto al vídeo un .srt con su mismo nombre. Android solo deja leerlo si lo creó Lumi o si
 * la app tiene acceso a todos los archivos; si no, devuelve null y hay que elegirlo a mano.
 */
fun findSubtitles(context: Context, video: MediaItem): List<Cue>? = runCatching {
    val name = video.name.substringBeforeLast('.') + ".srt"
    val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    context.contentResolver.query(
        files, arrayOf(MediaStore.Files.FileColumns._ID),
        "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?",
        arrayOf(name, video.path), null,
    )?.use { c ->
        if (!c.moveToFirst()) return null
        readSubtitles(context, android.content.ContentUris.withAppendedId(files, c.getLong(0))).ifEmpty { null }
    }
}.getOrNull()

/** Los .srt viejos suelen venir en Latin-1, no en UTF-8: si no es UTF-8 válido, se lee como Latin-1. */
private fun decodeText(bytes: ByteArray): String {
    val decoder = Charsets.UTF_8.newDecoder()
    return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrElse { String(bytes, Charsets.ISO_8859_1) }
        .removePrefix("﻿")
}

/** La frase que toca en [position], o null. */
fun cueAt(cues: List<Cue>, position: Long): Cue? {
    var low = 0
    var high = cues.lastIndex
    while (low <= high) {
        val mid = (low + high) / 2
        val cue = cues[mid]
        when {
            position < cue.start -> high = mid - 1
            position > cue.end -> low = mid + 1
            else -> return cue
        }
    }
    return null
}

// ---------- De vídeo a GIF ----------

/**
 * Convierte el tramo de [startMs] a [endMs] de [video] en un GIF de [width] px de ancho, a 10
 * fotogramas por segundo, y lo guarda en Pictures/Lumi. [onProgress] recibe el porcentaje.
 */
suspend fun makeGif(context: Context, video: MediaItem, startMs: Long, endMs: Long, width: Int, onProgress: (Int) -> Unit): Boolean {
    val retriever = MediaMetadataRetriever()
    val frames = ArrayList<Bitmap>()
    try {
        retriever.setDataSource(context, video.uri)
        val count = ((endMs - startMs) / FRAME_MS).toInt().coerceIn(1, 120)
        for (i in 0 until count) {
            coroutineContext.ensureActive()
            val at = (startMs + i * FRAME_MS) * 1000
            val frame = retriever.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST, width, width * 4) ?: continue
            frames += if (frame.config == Bitmap.Config.ARGB_8888) frame else frame.copy(Bitmap.Config.ARGB_8888, false)
            onProgress(i * 70 / count)
        }
    } catch (e: Exception) {
        frames.forEach { it.recycle() }
        return false
    } finally {
        runCatching { retriever.release() }
    }
    if (frames.isEmpty()) return false

    val bytes = ByteArrayOutputStream()
    runCatching {
        val gif = GifWriter(bytes, frames[0].width, frames[0].height)
        frames.forEachIndexed { i, frame ->
            coroutineContext.ensureActive()
            gif.frame(frame, (FRAME_MS / 10).toInt())
            onProgress(70 + (i + 1) * 28 / frames.size)
        }
        gif.finish()
    }.onFailure {
        frames.forEach { it.recycle() }
        return false
    }
    frames.forEach { it.recycle() }

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, video.name.substringBeforeLast('.') + "_${startMs / 1000}s.gif")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/gif")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Lumi/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) }.getOrNull() ?: return false
    val saved = runCatching {
        resolver.openOutputStream(target)!!.use { bytes.writeTo(it) }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!saved) runCatching { resolver.delete(target, null, null) }
    onProgress(100)
    return saved
}

/** Lo que tarda en llegar el siguiente fotograma del GIF: 10 por segundo. */
private const val FRAME_MS = 100L

/**
 * Escritor de GIF animado. Cada fotograma lleva su propia paleta de 256 colores, elegidos entre
 * los más frecuentes de esa imagen; cada píxel toma el más cercano. Comprime con LZW, como manda
 * el formato.
 */
private class GifWriter(output: OutputStream, private val width: Int, private val height: Int) {
    private val out = BufferedOutputStream(output)

    init {
        out.write("GIF89a".toByteArray())
        short(width)
        short(height)
        out.write(0x70) // sin paleta global; 8 bits por color
        out.write(0)
        out.write(0)
        // Repetir para siempre.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray())
        out.write(byteArrayOf(3, 1, 0, 0, 0))
    }

    private fun short(value: Int) {
        out.write(value and 0xFF)
        out.write(value shr 8 and 0xFF)
    }

    fun frame(bitmap: Bitmap, delayCs: Int) {
        val pixels = IntArray(width * height)
        val source = if (bitmap.width == width && bitmap.height == height) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        if (source !== bitmap) source.recycle()

        // Paleta: los 256 colores más frecuentes, contados con 5 bits por canal.
        val counts = IntArray(32768)
        for (p in pixels) counts[key(p)]++
        val palette = counts.indices.filter { counts[it] > 0 }.sortedByDescending { counts[it] }.take(256)
        val colors = IntArray(256)
        palette.forEachIndexed { i, k -> colors[i] = k }
        // Para cada color de 15 bits, el de la paleta más cercano; se calcula solo para los que aparecen.
        val nearest = IntArray(32768) { -1 }
        val indexes = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val k = key(pixels[i])
            var best = nearest[k]
            if (best < 0) {
                var bestDistance = Int.MAX_VALUE
                val r = k shr 10
                val g = k shr 5 and 31
                val b = k and 31
                for (j in palette.indices) {
                    val c = colors[j]
                    val dr = r - (c shr 10)
                    val dg = g - (c shr 5 and 31)
                    val db = b - (c and 31)
                    val d = dr * dr * 3 + dg * dg * 4 + db * db * 2
                    if (d < bestDistance) {
                        bestDistance = d
                        best = j
                    }
                }
                nearest[k] = best
            }
            indexes[i] = best.toByte()
        }

        // Retraso del fotograma.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0))
        short(delayCs)
        out.write(0)
        out.write(0)
        // Imagen, con su paleta local de 256 colores.
        out.write(0x2C)
        short(0)
        short(0)
        short(width)
        short(height)
        out.write(0x87)
        for (i in 0 until 256) {
            val c = colors[i]
            out.write((c shr 10) * 255 / 31)
            out.write((c shr 5 and 31) * 255 / 31)
            out.write((c and 31) * 255 / 31)
        }
        lzw(indexes)
    }

    fun finish() {
        out.write(0x3B)
        out.flush()
    }

    private fun key(p: Int) = (p shr 19 and 31 shl 10) or (p shr 11 and 31 shl 5) or (p shr 3 and 31)

    /** Compresión LZW de los índices, en bloques de 255 bytes como pide el formato. */
    private fun lzw(data: ByteArray) {
        val minCode = 8
        out.write(minCode)
        val clear = 1 shl minCode
        val end = clear + 1
        val table = HashMap<Int, Int>(5003)
        var next = end + 1
        var size = minCode + 1
        val block = ByteArray(255)
        var blockLength = 0
        var bits = 0
        var bitCount = 0

        fun emit(code: Int) {
            bits = bits or (code shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                block[blockLength++] = (bits and 0xFF).toByte()
                bits = bits ushr 8
                bitCount -= 8
                if (blockLength == 255) {
                    out.write(255)
                    out.write(block, 0, 255)
                    blockLength = 0
                }
            }
        }

        emit(clear)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val c = data[i].toInt() and 0xFF
            val k = (prefix shl 8) or c
            val found = table[k]
            if (found != null) {
                prefix = found
                continue
            }
            emit(prefix)
            if (next < 4096) {
                table[k] = next++
                if (next > (1 shl size) && size < 12) size++
            } else {
                // Tabla llena: se empieza de nuevo.
                emit(clear)
                table.clear()
                next = end + 1
                size = minCode + 1
            }
            prefix = c
        }
        emit(prefix)
        emit(end)
        if (bitCount > 0) {
            block[blockLength++] = (bits and 0xFF).toByte()
            if (blockLength == 255) {
                out.write(255)
                out.write(block, 0, 255)
                blockLength = 0
            }
        }
        if (blockLength > 0) {
            out.write(blockLength)
            out.write(block, 0, blockLength)
        }
        out.write(0)
    }
}

// ---------- Fotos seguidas a GIF ----------

/**
 * Convierte [photos] (una ráfaga o fotos parecidas, en el orden en que se hicieron) en un GIF de
 * [width] px de ancho. [delayCs] es lo que dura cada fotograma, en centésimas. Con [bounce] vuelve
 * hacia atrás al llegar al final, para que el bucle no dé un salto.
 */
suspend fun makeBurstGif(context: Context, photos: List<MediaItem>, width: Int, delayCs: Int, bounce: Boolean, onProgress: (Int) -> Unit): Boolean {
    val frames = ArrayList<Bitmap>()
    try {
        photos.forEachIndexed { i, item ->
            coroutineContext.ensureActive()
            val thumb = runCatching { context.contentResolver.loadThumbnail(item.uri, android.util.Size(width, width * 2), null) }.getOrNull() ?: return@forEachIndexed
            frames += if (thumb.config == Bitmap.Config.ARGB_8888) thumb else thumb.copy(Bitmap.Config.ARGB_8888, false)
            onProgress(i * 40 / photos.size)
        }
        if (frames.size < 2) return false
        // Todas al tamaño de la primera.
        val w = frames[0].width
        val h = frames[0].height
        val order = if (bounce) frames + frames.subList(1, frames.size - 1).asReversed() else frames
        val bytes = ByteArrayOutputStream()
        val gif = GifWriter(bytes, w, h)
        order.forEachIndexed { i, frame ->
            coroutineContext.ensureActive()
            gif.frame(frame, delayCs)
            onProgress(40 + (i + 1) * 58 / order.size)
        }
        gif.finish()
        return saveGif(context, bytes, photos.first().name.substringBeforeLast('.') + "_animada.gif").also { onProgress(100) }
    } finally {
        frames.forEach { it.recycle() }
    }
}

private fun saveGif(context: Context, bytes: ByteArrayOutputStream, name: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/gif")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Lumi/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) }.getOrNull() ?: return false
    val saved = runCatching {
        resolver.openOutputStream(target)!!.use { bytes.writeTo(it) }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!saved) runCatching { resolver.delete(target, null, null) }
    return saved
}

/** Guarda [frames] (todas del mismo tamaño) como GIF en Pictures/Lumi. [delayCs]: centésimas por fotograma. */
suspend fun saveFramesGif(context: Context, frames: List<Bitmap>, delayCs: Int, name: String, onProgress: (Int) -> Unit): Boolean {
    if (frames.size < 2) return false
    val bytes = ByteArrayOutputStream()
    val gif = GifWriter(bytes, frames[0].width, frames[0].height)
    frames.forEachIndexed { i, frame ->
        coroutineContext.ensureActive()
        gif.frame(frame, delayCs)
        onProgress((i + 1) * 95 / frames.size)
    }
    gif.finish()
    return saveGif(context, bytes, name).also { onProgress(100) }
}
