package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Nombre legible de la cámara: «Pixel 8», «Samsung SM-S911B», «Canon EOS R6». Vacío si no consta. */
fun cameraName(make: String?, model: String?): String {
    val m = model?.trim().orEmpty()
    val brand = make?.trim().orEmpty()
        .replace(Regex("(?i)\\s*(corporation|corp\\.?|company|co\\.?,? ?ltd\\.?|imaging|electronics|inc\\.?)\\b"), "")
        .trim()
    if (m.isEmpty()) return brand.replaceFirstChar { it.uppercase() }
    // El modelo ya suele llevar la marca delante («Canon EOS R6», «Pixel» de Google no).
    if (brand.isEmpty() || m.startsWith(brand, ignoreCase = true) || brand.equals("google", ignoreCase = true)) return m
    return brand.lowercase().replaceFirstChar { it.uppercase() } + " " + m
}

/**
 * Foto en movimiento: un JPEG (o HEIC) con un vídeo MP4 corto pegado al final. La hacen los Pixel
 * («Movimiento»), los Samsung («Foto en movimiento») y otros móviles con el formato de Google.
 * Aquí solo se averigua dónde empieza y cuánto mide ese vídeo dentro del archivo.
 */
fun isMotionPhoto(context: Context, item: MediaItem, exif: ExifInterface? = null): Boolean =
    !item.isVideo && motionRange(context, item, exif) != null

/** Bytes del vídeo dentro del archivo: desde dónde y cuántos. null si no lleva vídeo. */
fun motionRange(context: Context, item: MediaItem, known: ExifInterface? = null): LongRange? = runCatching {
    if (item.isVideo || item.isGif) return null
    val format = item.format
    if (format != "JPG" && format != "HEIC") return null
    context.contentResolver.openFileDescriptor(item.uri, "r")?.use { pfd ->
        FileInputStream(pfd.fileDescriptor).channel.use { channel ->
            val size = channel.size()
            fun read(at: Long, count: Int): ByteBuffer? {
                if (at < 0 || count <= 0 || at + count > size) return null
                val buffer = ByteBuffer.allocate(count).order(ByteOrder.LITTLE_ENDIAN)
                var pos = at
                while (buffer.hasRemaining()) {
                    val n = channel.read(buffer, pos)
                    if (n <= 0) return null
                    pos += n
                }
                buffer.flip()
                return buffer
            }
            // Un MP4 empieza con 4 bytes de tamaño y la palabra «ftyp».
            fun isMp4(at: Long) = read(at + 4, 4)?.let { String(it.array(), Charsets.US_ASCII) == "ftyp" } == true
            fun around(at: Long): Long? {
                if (isMp4(at)) return at
                // Algunos móviles dejan unos bytes de relleno: se busca el inicio cerca.
                val window = 4096
                val start = (at - window).coerceAtLeast(0)
                val chunk = read(start, (minOf(size, at + window) - start).toInt()) ?: return null
                val bytes = chunk.array()
                for (i in 4 until bytes.size - 4) {
                    if (bytes[i] == 'f'.code.toByte() && bytes[i + 1] == 't'.code.toByte() && bytes[i + 2] == 'y'.code.toByte() && bytes[i + 3] == 'p'.code.toByte()) {
                        return start + i - 4
                    }
                }
                return null
            }

            // 1. Formato de Google: la XMP dice cuánto mide el vídeo, que va al final del archivo.
            val exif = known ?: context.contentResolver.openInputStream(item.uri)?.use { ExifInterface(it) }
            val xmp = exif?.getAttribute(ExifInterface.TAG_XMP).orEmpty()
            if (xmp.isNotEmpty()) {
                val length = Regex("""MicroVideoOffset="(\d+)"""").find(xmp)?.groupValues?.get(1)?.toLongOrNull()
                    ?: Regex("""Semantic="MotionPhoto"[^>]*?Length="(\d+)"""").find(xmp)?.groupValues?.get(1)?.toLongOrNull()
                    ?: Regex("""Length="(\d+)"[^>]*?Semantic="MotionPhoto"""").find(xmp)?.groupValues?.get(1)?.toLongOrNull()
                if (length != null && length in 1 until size) {
                    around(size - length)?.let { return@runCatching it until size }
                }
            }

            // 2. Formato de Samsung: un índice al final del archivo («SEFT») con bloques con nombre;
            // el del vídeo se llama MotionPhoto_Data.
            val tail = read(size - 8, 8) ?: return null
            val dirSize = tail.getInt(0).toLong()
            if (String(tail.array(), 4, 4, Charsets.US_ASCII) != "SEFT" || dirSize <= 12 || dirSize > 64 * 1024) return null
            val dirStart = size - 8 - dirSize
            val dir = read(dirStart, dirSize.toInt()) ?: return null
            if (String(dir.array(), 0, 4, Charsets.US_ASCII) != "SEFH") return null
            val count = dir.getInt(8)
            for (i in 0 until count.coerceAtMost(64)) {
                val entry = 12 + i * 12
                if (entry + 12 > dirSize) break
                val offset = dir.getInt(entry + 4).toLong()
                val length = dir.getInt(entry + 8).toLong()
                val blockStart = dirStart - offset
                val head = read(blockStart, 8) ?: continue
                val nameLength = head.getInt(4)
                if (nameLength !in 1..64) continue
                val name = read(blockStart + 8, nameLength)?.let { String(it.array(), Charsets.US_ASCII) } ?: continue
                if (name == "MotionPhoto_Data") {
                    val video = blockStart + 8 + nameLength
                    val end = blockStart + length
                    if (isMp4(video) && end > video) return@runCatching video until end
                }
            }
            null
        }
    }
}.getOrNull()

/** Copia el vídeo de una foto en movimiento a un archivo temporal, para verlo. */
fun motionVideoFile(context: Context, item: MediaItem): File? = runCatching {
    val range = motionRange(context, item) ?: return null
    val out = File(File(context.cacheDir, "movimiento").apply { mkdirs() }, "${item.id}_${item.modified}.mp4")
    if (out.exists() && out.length() == range.last - range.first + 1) return out
    context.contentResolver.openFileDescriptor(item.uri, "r")!!.use { pfd ->
        FileInputStream(pfd.fileDescriptor).channel.use { channel ->
            out.outputStream().channel.use { target -> channel.transferTo(range.first, range.last - range.first + 1, target) }
        }
    }
    out
}.getOrNull()

/** Guarda el vídeo de una foto en movimiento como vídeo aparte, en el mismo álbum si se puede. */
fun saveMotionVideo(context: Context, item: MediaItem): Boolean = runCatching {
    val file = motionVideoFile(context, item) ?: return false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, item.name.substringBeforeLast('.') + "_movimiento.mp4")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(item.path)) item.path else "Movies/Lumi/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
    val ok = runCatching {
        resolver.openOutputStream(target)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!ok) runCatching { resolver.delete(target, null, null) }
    ok
}.getOrDefault(false)

/**
 * Larga exposición a partir del vídeo de una foto en movimiento: se superponen sus fotogramas.
 * Con [trails] se queda en cada punto el más claro (estelas de luces); si no, la media (agua sedosa).
 * Antes de sumar, cada fotograma se alinea con el primero para que el pulso no lo emborrone todo.
 */
fun longExposure(context: Context, item: MediaItem, trails: Boolean): android.graphics.Bitmap? = runCatching {
    val file = motionVideoFile(context, item) ?: return null
    val retriever = android.media.MediaMetadataRetriever()
    val frames = ArrayList<android.graphics.Bitmap>()
    try {
        retriever.setDataSource(file.path)
        val length = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
        val count = (length / 60).toInt().coerceIn(6, 30)
        for (i in 0 until count) {
            val at = length * 1000 * i / count
            retriever.getScaledFrameAtTime(at, android.media.MediaMetadataRetriever.OPTION_CLOSEST, 1280, 1280)?.let { frames += it }
        }
    } finally {
        runCatching { retriever.release() }
    }
    if (frames.size < 3) return null
    val w = frames[0].width
    val h = frames[0].height
    val base = IntArray(w * h)
    frames[0].getPixels(base, 0, w, 0, 0, w, h)
    val small = shrinkGray(base, w, h, 4)
    val sw = w / 4
    val sh = h / 4
    val r = FloatArray(w * h)
    val g = FloatArray(w * h)
    val b = FloatArray(w * h)
    val px = IntArray(w * h)
    var used = 0
    for (frame in frames) {
        if (frame.width != w || frame.height != h) continue
        frame.getPixels(px, 0, w, 0, 0, w, h)
        // Cuánto se ha movido el teléfono respecto al primero, buscado a un cuarto de tamaño.
        val (dx, dy) = if (used == 0) 0 to 0 else bestShift(small, shrinkGray(px, w, h, 4), sw, sh, 6).let { it.first * 4 to it.second * 4 }
        for (y in 0 until h) {
            val sy = (y + dy).coerceIn(0, h - 1)
            for (x in 0 until w) {
                val p = px[sy * w + (x + dx).coerceIn(0, w - 1)]
                val i = y * w + x
                val pr = (p shr 16 and 0xFF).toFloat()
                val pg = (p shr 8 and 0xFF).toFloat()
                val pb = (p and 0xFF).toFloat()
                if (trails) {
                    if (pr + pg + pb > r[i] + g[i] + b[i]) { r[i] = pr; g[i] = pg; b[i] = pb }
                } else {
                    r[i] += pr; g[i] += pg; b[i] += pb
                }
            }
        }
        used++
    }
    frames.forEach { it.recycle() }
    val k = if (trails) 1f else 1f / used
    val out = IntArray(w * h) { i ->
        (0xFF shl 24) or ((r[i] * k).toInt().coerceIn(0, 255) shl 16) or ((g[i] * k).toInt().coerceIn(0, 255) shl 8) or (b[i] * k).toInt().coerceIn(0, 255)
    }
    android.graphics.Bitmap.createBitmap(out, w, h, android.graphics.Bitmap.Config.ARGB_8888)
}.getOrNull()

private fun shrinkGray(px: IntArray, w: Int, h: Int, k: Int): IntArray {
    val sw = w / k
    val sh = h / k
    return IntArray(sw * sh) { i ->
        val p = px[(i / sw) * k * w + (i % sw) * k]
        ((p shr 16 and 0xFF) * 3 + (p shr 8 and 0xFF) * 4 + (p and 0xFF)) shr 3
    }
}

/** El desplazamiento (dentro de ±[range]) con el que [b] se parece más a [a], mirando el centro. */
private fun bestShift(a: IntArray, b: IntArray, w: Int, h: Int, range: Int): Pair<Int, Int> {
    var best = 0 to 0
    var bestSum = Long.MAX_VALUE
    val x0 = w / 5
    val x1 = w * 4 / 5
    val y0 = h / 5
    val y1 = h * 4 / 5
    for (dy in -range..range) for (dx in -range..range) {
        var sum = 0L
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                sum += kotlin.math.abs(a[y * w + x] - b[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)])
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
