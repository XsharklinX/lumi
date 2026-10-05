package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer

/** Guarda [bitmap] como foto nueva en [path] (o en Pictures/Lumi si ahí no se puede escribir). */
fun saveNewPhoto(context: Context, bitmap: Bitmap, name: String, path: String = "Pictures/Lumi/"): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(path)) path else "Pictures/Lumi/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val target = runCatching { resolver.insert(images, values) }.getOrNull() ?: return false
    val saved = runCatching {
        resolver.openOutputStream(target)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!saved) runCatching { resolver.delete(target, null, null) }
    return saved
}

/** Guarda [bitmap] como PNG en el álbum Lumi; a diferencia del JPEG, conserva las zonas transparentes. */
fun savePng(context: Context, bitmap: Bitmap, name: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Lumi/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val target = runCatching { resolver.insert(images, values) }.getOrNull() ?: return false
    val saved = runCatching {
        resolver.openOutputStream(target)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!saved) runCatching { resolver.delete(target, null, null) }
    return saved
}

/**
 * Guarda como vídeo nuevo el tramo de [startMs] a [endMs]. No vuelve a comprimir nada, así que es
 * rápido y no pierde calidad; a cambio, el corte empieza en el fotograma completo anterior al punto
 * elegido, que puede caer hasta un par de segundos antes.
 */
fun trimVideo(context: Context, video: MediaItem, startMs: Long, endMs: Long): Boolean {
    val temp = File(context.cacheDir, "recorte.mp4")
    val cut = runCatching {
        val extractor = MediaExtractor()
        context.contentResolver.openFileDescriptor(video.uri, "r")!!.use { extractor.setDataSource(it.fileDescriptor) }
        val muxer = MediaMuxer(temp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val tracks = HashMap<Int, Int>()
        var bufferSize = 2 * 1024 * 1024
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
            tracks[i] = muxer.addTrack(format)
            extractor.selectTrack(i)
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
        }
        // Sin esto, un vídeo grabado en vertical saldría tumbado.
        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(context, video.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.let(muxer::setOrientationHint)
        }
        runCatching { retriever.release() }

        extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val base = extractor.sampleTime.coerceAtLeast(0)
        val endUs = endMs * 1000
        val buffer = ByteBuffer.allocate(bufferSize)
        val info = MediaCodec.BufferInfo()
        muxer.start()
        var open = tracks.size
        while (open > 0) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val track = extractor.sampleTrackIndex
            if (extractor.sampleTime > endUs) {
                // Esta pista ya pasó el final; las demás pueden ir algo por detrás.
                extractor.unselectTrack(track)
                open--
                continue
            }
            info.set(0, size, extractor.sampleTime - base, extractor.sampleFlags)
            muxer.writeSampleData(tracks.getValue(track), buffer, info)
            extractor.advance()
        }
        muxer.stop()
        muxer.release()
        extractor.release()
    }.isSuccess
    if (!cut) {
        temp.delete()
        return false
    }

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, video.name.substringBeforeLast('.') + "_recorte.mp4")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(video.path)) video.path else "Movies/Lumi/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val videos = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val target = runCatching { resolver.insert(videos, values) }.getOrNull()
    val saved = target != null && runCatching {
        temp.inputStream().use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it) } }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!saved && target != null) runCatching { resolver.delete(target, null, null) }
    temp.delete()
    return saved
}

// ---------- Carpetas ocultas del sistema ----------

class HiddenFolder(val path: String, val name: String, val items: List<MediaItem>)

private val PHOTO_TYPES = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
private val VIDEO_TYPES = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v")

/**
 * Busca fotos y vídeos en carpetas que Android no enseña en la galería: las que empiezan por punto
 * o llevan dentro un archivo ".nomedia" (y todo lo que cuelga de ellas). Necesita el permiso de
 * acceso a todos los archivos.
 */
fun scanHidden(): List<HiddenFolder> {
    val out = ArrayList<HiddenFolder>()

    fun walk(dir: File, depth: Int, inherited: Boolean) {
        if (depth > 8) return
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return
        val hidden = inherited || dir.name.startsWith(".") || files.any { it.name == ".nomedia" }
        if (hidden) {
            val media = files.filter { it.isFile }.mapNotNull { file ->
                val type = file.extension.lowercase()
                val isVideo = type in VIDEO_TYPES
                if (!isVideo && type !in PHOTO_TYPES) return@mapNotNull null
                MediaItem(
                    // Negativo para no chocar con los identificadores de la biblioteca.
                    id = -(file.path.hashCode().toLong() and 0x7FFFFFFF) - 2,
                    isVideo = isVideo, name = file.name, date = file.lastModified(), modified = file.lastModified() / 1000,
                    size = file.length(), width = 0, height = 0, duration = 0, bucketId = dir.path.hashCode().toLong(),
                    bucket = dir.name, path = dir.path, expires = 0, external = Uri.fromFile(file),
                )
            }.sortedByDescending { it.date }
            if (media.isNotEmpty()) out += HiddenFolder(dir.path, dir.name.removePrefix("."), media)
        }
        files.filter { it.isDirectory }.forEach { walk(it, depth + 1, hidden) }
    }

    walk(Environment.getExternalStorageDirectory(), 0, false)
    return out.sortedByDescending { it.items.first().date }
}
