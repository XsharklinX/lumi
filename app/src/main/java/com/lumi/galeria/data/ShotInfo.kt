package com.lumi.galeria.data

import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import java.util.Locale

/** Los datos de la toma, tal como los anotó la cámara. Lo que no consta es null. */
class ShotInfo(
    val camera: String,
    val lens: String,
    val aperture: String?,
    val shutter: String?,
    val iso: Int?,
    val focal: String?,
    val flash: Boolean,
)

/** Lee los datos de la toma de [item]; null si el archivo no los tiene. */
fun readShotInfo(context: Context, item: MediaItem): ShotInfo? = runCatching {
    context.contentResolver.openInputStream(item.uri)!!.use { stream ->
        val exif = ExifInterface(stream)
        val maker = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty().trim()
        val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty().trim()
        val camera = if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
        val lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL).orEmpty().trim()
        val aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { "f/%.1f".format(Locale.US, it) }
        val shutter = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
            if (it >= 1) "%.1f s".format(Locale.US, it) else "1/${Math.round(1 / it)} s"
        }
        val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }
        val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { "%.0f mm".format(Locale.US, it) }
        val flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, 0) and 1 == 1
        if (camera.isEmpty() && lens.isEmpty() && aperture == null && shutter == null && iso == null && focal == null) null
        else ShotInfo(camera, lens, aperture, shutter, iso, focal, flash)
    }
}.getOrNull()

/** Cómo se reparte la luz en cada canal de color, de 0 (negro) a 1 (blanco), en [bins] tramos; valores de 0 a 1. */
fun colorHistogram(bitmap: Bitmap, bins: Int = 48): Array<FloatArray> {
    val out = Array(3) { FloatArray(bins) }
    val step = maxOf(1, maxOf(bitmap.width, bitmap.height) / 160)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val p = bitmap.getPixel(x, y)
            out[0][(p shr 16 and 0xFF) * bins / 256]++
            out[1][(p shr 8 and 0xFF) * bins / 256]++
            out[2][(p and 0xFF) * bins / 256]++
            x += step
        }
        y += step
    }
    val top = out.maxOf { it.max() }.coerceAtLeast(1f)
    out.forEach { channel -> for (i in channel.indices) channel[i] /= top }
    return out
}

/** Si [item] es una foto RAW (DNG). */
val MediaItem.isRaw: Boolean get() = !isVideo && format == "DNG"

/** La foto JPEG (o HEIC) que la cámara guardó junto al RAW, con el mismo nombre. */
fun rawTwin(item: MediaItem, all: List<MediaItem>): MediaItem? {
    if (!item.isRaw) return all.firstOrNull { it.isRaw && it.bucketId == item.bucketId && it.name.substringBeforeLast('.').equals(item.name.substringBeforeLast('.'), true) }
    val base = item.name.substringBeforeLast('.')
    return all.firstOrNull { it.id != item.id && it.bucketId == item.bucketId && !it.isRaw && it.name.substringBeforeLast('.').equals(base, true) }
}
