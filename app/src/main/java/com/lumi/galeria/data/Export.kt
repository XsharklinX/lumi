package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File

enum class ExportFormat(val label: String, val mime: String, val ext: String) {
    JPG("JPG", "image/jpeg", "jpg"), PNG("PNG", "image/png", "png"), WEBP("WebP", "image/webp", "webp"),
}

enum class Corner(val label: String) { BOTTOM_RIGHT("Abajo a la derecha"), BOTTOM_LEFT("Abajo a la izquierda"), CENTER("En el centro") }

/** Cómo sacar las fotos. [side] = 0 deja el tamaño original. */
data class ExportOptions(
    val side: Int = 2048,
    val format: ExportFormat = ExportFormat.JPG,
    val quality: Int = 90,
    val mark: String = "",
    val corner: Corner = Corner.BOTTOM_RIGHT,
    val dropPlace: Boolean = true,
    val dropCamera: Boolean = false,
)

/** Lo que más o menos ocupará [item] exportada así. Es una estimación para enseñar antes de empezar. */
fun estimateExport(item: MediaItem, options: ExportOptions): Long {
    val longSide = maxOf(item.width, item.height).coerceAtLeast(1)
    val k = if (options.side == 0 || longSide <= options.side) 1f else options.side.toFloat() / longSide
    val pixels = item.width * k * item.height * k
    val perPixel = when (options.format) {
        ExportFormat.JPG -> 0.12f + options.quality / 100f * 0.35f
        ExportFormat.WEBP -> 0.1f + options.quality / 100f * 0.25f
        ExportFormat.PNG -> 1.8f
    }
    return (pixels * perPixel).toLong().coerceAtLeast(10_000)
}

/**
 * Saca una copia de [item] con [options] en Pictures/Lumi Exportadas. Se dibuja de nuevo, así
 * que la copia nunca lleva los datos de la original; se le vuelven a poner la fecha y, si no se
 * quitan, el lugar y la cámara. Devuelve la dirección de la copia o null.
 */
fun exportPhoto(context: Context, item: MediaItem, options: ExportOptions): Uri? = runCatching {
    val resolver = context.contentResolver
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, item.uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
        val longSide = maxOf(info.size.width, info.size.height)
        if (options.side in 1 until longSide) {
            val k = options.side.toFloat() / longSide
            decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
        }
    }
    if (options.mark.isNotBlank()) watermark(bitmap, options.mark.trim(), options.corner)

    // Primero a un archivo temporal, para poder escribir en él los datos que se conservan.
    val temp = File(context.cacheDir, "exportar.${options.format.ext}")
    temp.outputStream().use { out ->
        val format = when (options.format) {
            ExportFormat.JPG -> Bitmap.CompressFormat.JPEG
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
        }
        bitmap.compress(format, options.quality, out)
    }
    bitmap.recycle()
    if (options.format == ExportFormat.JPG) copyDetails(context, item, temp, options)

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, item.name.substringBeforeLast('.') + "." + options.format.ext)
        put(MediaStore.MediaColumns.MIME_TYPE, options.format.mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Lumi Exportadas/")
        put(MediaStore.MediaColumns.DATE_TAKEN, item.date)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
    val ok = runCatching {
        resolver.openOutputStream(target)!!.use { out -> temp.inputStream().use { it.copyTo(out) } }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    temp.delete()
    if (!ok) {
        runCatching { resolver.delete(target, null, null) }
        null
    } else {
        target
    }
}.getOrNull()

/** Texto en una esquina, blanco con sombra, a un tamaño que se lee en cualquier foto. */
private fun watermark(bitmap: Bitmap, text: String, corner: Corner) {
    val canvas = Canvas(bitmap)
    val size = minOf(bitmap.width, bitmap.height) * 0.045f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        alpha = 220
        textSize = size
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(size * 0.25f, 0f, size * 0.05f, android.graphics.Color.argb(150, 0, 0, 0))
    }
    val margin = size * 0.9f
    val width = paint.measureText(text)
    val (x, y) = when (corner) {
        Corner.BOTTOM_RIGHT -> bitmap.width - margin - width to bitmap.height - margin
        Corner.BOTTOM_LEFT -> margin to bitmap.height - margin
        Corner.CENTER -> (bitmap.width - width) / 2 to bitmap.height / 2f
    }
    canvas.drawText(text, x, y, paint)
}

/** Fecha siempre; lugar y cámara según lo elegido. La orientación ya va aplicada en la imagen. */
private fun copyDetails(context: Context, item: MediaItem, file: File, options: ExportOptions) {
    runCatching {
        val uri = if (options.dropPlace) item.uri else MediaStore.setRequireOriginal(item.uri)
        val source = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } ?: return
        val target = ExifInterface(file.path)
        val keep = buildList {
            addAll(listOf(ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
            if (!options.dropCamera) addAll(listOf(ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH))
            if (!options.dropPlace) addAll(listOf(ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF))
        }
        keep.forEach { tag -> source.getAttribute(tag)?.let { target.setAttribute(tag, it) } }
        target.saveAttributes()
    }
}
