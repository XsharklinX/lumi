package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun collection(isVideo: Boolean): Uri =
    if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

/** Copia cada elemento a [relativePath]. Devuelve las copias creadas, para poder deshacerlo. */
fun copyItems(context: Context, items: List<MediaItem>, relativePath: String): List<Uri> {
    val resolver = context.contentResolver
    val done = ArrayList<Uri>()
    for (item in items) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, resolver.getType(item.uri) ?: if (item.isVideo) "video/mp4" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = runCatching { resolver.insert(collection(item.isVideo), values) }.getOrNull() ?: continue
        val copied = runCatching {
            resolver.openInputStream(item.uri)!!.use { input ->
                resolver.openOutputStream(target)!!.use { output -> input.copyTo(output) }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }.isSuccess
        if (copied) done += target else runCatching { resolver.delete(target, null, null) }
    }
    return done
}

/** Devuelve cada elemento a la carpeta que tenía: deshace un [moveItems]. */
fun restorePaths(context: Context, items: List<MediaItem>) {
    items.forEach { item ->
        runCatching { context.contentResolver.update(item.uri, ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, item.path) }, null, null) }
    }
}

/** Vuelve a poner a [item] el nombre que tenía: deshace un [renameItem]. */
fun restoreName(context: Context, item: MediaItem) {
    runCatching { context.contentResolver.update(item.uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, item.name) }, null, null) }
}

/** Mueve cada elemento a [relativePath]. Antes hay que tener concedido [writeRequest]. */
fun moveItems(context: Context, items: List<MediaItem>, relativePath: String): Int {
    val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }
    return items.count { item -> runCatching { context.contentResolver.update(item.uri, values, null, null) }.getOrDefault(0) > 0 }
}

/** Guarda [bitmap] como foto nueva junto a [original], que no se toca. */
fun saveEdited(context: Context, original: MediaItem, bitmap: Bitmap): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, original.name.substringBeforeLast('.') + "_editada.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(original.path)) original.path else "Pictures/Lumi/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(collection(false), values) }.getOrNull() ?: return false
    val saved = runCatching {
        resolver.openOutputStream(target)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        // Con la fecha original, la copia aparece al lado de la foto de la que sale.
        runCatching {
            resolver.openFileDescriptor(target, "rw")!!.use { fd ->
                ExifInterface(fd.fileDescriptor).apply {
                    setAttribute(
                        ExifInterface.TAG_DATETIME_ORIGINAL,
                        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(original.date + 1000)),
                    )
                    saveAttributes()
                }
            }
        }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!saved) runCatching { resolver.delete(target, null, null) }
    return saved
}

private val GPS_TAGS = arrayOf(
    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
    ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE,
)

/** Copia temporal de la foto sin datos de ubicación, lista para enviarla a otra app. */
fun copyWithoutLocation(context: Context, item: MediaItem): Uri? = runCatching {
    val dir = File(context.cacheDir, "shared").apply {
        mkdirs()
        listFiles()?.forEach { it.delete() }
    }
    val file = File(dir, item.name.ifEmpty { "foto.jpg" })
    context.contentResolver.openInputStream(item.uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    runCatching {
        ExifInterface(file).apply {
            GPS_TAGS.forEach { setAttribute(it, null) }
            saveAttributes()
        }
    }
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()

/** Sustituye el contenido de [original] por [bitmap]. Antes hay que tener concedido [writeRequest]. */
fun replaceOriginal(context: Context, original: MediaItem, bitmap: Bitmap): Boolean = runCatching {
    context.contentResolver.openOutputStream(original.uri, "wt")!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
}.isSuccess

/** Cambia el nombre del archivo conservando su extensión. Antes hay que tener concedido [writeRequest]. */
fun renameItem(context: Context, item: MediaItem, newName: String): Boolean {
    val extension = item.name.substringAfterLast('.', "")
    val clean = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "").substringBeforeLast(".$extension")
    if (clean.isEmpty()) return false
    val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, if (extension.isEmpty()) clean else "$clean.$extension") }
    return runCatching { context.contentResolver.update(item.uri, values, null, null) }.getOrDefault(0) > 0
}

/** Lo que la cámara anotó en la foto: aparato, apertura, velocidad, ISO y distancia focal. */
fun cameraInfo(context: Context, item: MediaItem): List<Pair<String, String>> = runCatching {
    context.contentResolver.openInputStream(item.uri)!!.use { stream ->
        val exif = ExifInterface(stream)
        val out = ArrayList<Pair<String, String>>()
        val maker = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty().trim()
        val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty().trim()
        val camera = if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
        if (camera.isNotEmpty()) out += "Cámara" to camera
        val settings = ArrayList<String>()
        exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { settings += "f/%.1f".format(Locale.US, it) }
        exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
            settings += if (it >= 1) "%.1f s".format(Locale.US, it) else "1/${Math.round(1 / it)} s"
        }
        exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }?.let { settings += "ISO $it" }
        exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { settings += "%.0f mm".format(Locale.US, it) }
        if (settings.isNotEmpty()) out += "Disparo" to settings.joinToString(" · ")
        if (exif.getAttributeInt(ExifInterface.TAG_FLASH, 0) and 1 == 1) out += "Flash" to "Disparado"
        out
    }
}.getOrDefault(emptyList())

/** Saca el fotograma de [positionMs] de un vídeo y lo guarda como foto en su misma carpeta. */
fun saveVideoFrame(context: Context, video: MediaItem, positionMs: Long): Boolean {
    val retriever = android.media.MediaMetadataRetriever()
    val frame = runCatching {
        retriever.setDataSource(context, video.uri)
        retriever.getFrameAtTime(positionMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
    }.getOrNull()
    runCatching { retriever.release() }
    if (frame == null) return false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, video.name.substringBeforeLast('.') + "_fotograma_${positionMs / 1000}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(video.path)) video.path else "Pictures/Lumi/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(collection(false), values) }.getOrNull() ?: return false
    val saved = runCatching {
        resolver.openOutputStream(target)!!.use { frame.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!saved) runCatching { resolver.delete(target, null, null) }
    return saved
}

/**
 * Copia a la galería lo que devolvió el escáner: las páginas como fotos en Pictures/Documentos y
 * el PDF en Documents/Lumi. Devuelve cuántas cosas se guardaron.
 */
fun saveScanned(context: Context, pages: List<Uri>, pdf: Uri?): Int {
    val resolver = context.contentResolver
    val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(Date())
    var saved = 0

    fun copy(from: Uri, collection: Uri, name: String, mime: String, folder: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return
        val ok = runCatching {
            resolver.openInputStream(from)!!.use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it) } }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }.isSuccess
        if (ok) saved++ else runCatching { resolver.delete(target, null, null) }
    }

    pages.forEachIndexed { i, page ->
        copy(page, MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), "Documento $stamp (${i + 1}).jpg", "image/jpeg", "Pictures/Documentos/")
    }
    if (pdf != null) copy(pdf, MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), "Documento $stamp.pdf", "application/pdf", "Documents/Lumi/")
    return saved
}
