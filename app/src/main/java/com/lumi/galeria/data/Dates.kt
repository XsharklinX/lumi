package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** Una foto cuya fecha parece equivocada, y la que le toca según el nombre del archivo. */
class DateFix(val item: MediaItem, val proposed: Long, val origin: String)

private val NAME_DATE = Regex("(?<!\\d)((?:19|20)\\d{2})[-_.]?(0[1-9]|1[0-2])[-_.]?(0[1-9]|[12]\\d|3[01])(?!\\d)")

/** La fecha que lleva escrita el nombre de un archivo («IMG-20190714-WA0012»), al mediodía; null si no lleva ninguna. */
fun dateFromName(name: String): Long? {
    val m = NAME_DATE.find(name) ?: return null
    val (y, mo, d) = m.destructured
    val date = runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull() ?: return null
    val today = LocalDate.now()
    if (date.isAfter(today.plusDays(1)) || date.year < 1995) return null
    return date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** De dónde viene un archivo, por su nombre, para decírselo al usuario. */
private fun originOf(name: String): String = when {
    name.contains("-WA", ignoreCase = true) || name.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
    name.startsWith("Screenshot", ignoreCase = true) -> "Captura"
    name.startsWith("signal", ignoreCase = true) -> "Signal"
    name.startsWith("telegram", ignoreCase = true) -> "Telegram"
    else -> "Descarga"
}

private const val DAY = 86_400_000L

/** Las fotos y vídeos cuya fecha está a más de dos días de la que dice su nombre. */
fun findDateFixes(items: List<MediaItem>): List<DateFix> = items.mapNotNull { item ->
    if (item.isExternal) return@mapNotNull null
    val named = dateFromName(item.name) ?: return@mapNotNull null
    if (kotlin.math.abs(item.date - named) <= 2 * DAY) null else DateFix(item, named, originOf(item.name))
}

/**
 * Pone la fecha [taken] a [item]: en la biblioteca del teléfono y, en un JPEG, también en sus datos
 * EXIF para que no se pierda al copiar la foto a otro sitio. Hay que tener ya el permiso de escritura.
 */
fun setTakenDate(context: Context, item: MediaItem, taken: Long): Boolean {
    val resolver = context.contentResolver
    val ok = runCatching {
        resolver.update(item.uri, ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, taken) }, null, null) > 0
    }.getOrDefault(false)
    if (ok && !item.isVideo && (item.format == "JPG")) {
        runCatching {
            resolver.openFileDescriptor(item.uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                val text = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(taken))
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, text)
                exif.setAttribute(ExifInterface.TAG_DATETIME, text)
                exif.saveAttributes()
            }
        }
    }
    return ok
}
