package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Patterns

// ---------- Tarjeta de memoria ----------

/** Nombre con el que Android conoce la tarjeta de memoria, o null si no hay ninguna puesta. */
fun cardVolume(context: Context): String? =
    runCatching { MediaStore.getExternalVolumeNames(context).firstOrNull { it != MediaStore.VOLUME_EXTERNAL_PRIMARY } }.getOrNull()

/** Espacio total y libre (en bytes) de la memoria del teléfono y de la tarjeta. */
class StorageSpace(val phoneTotal: Long, val phoneFree: Long, val cardTotal: Long, val cardFree: Long)

fun storageSpace(context: Context): StorageSpace? = runCatching {
    val phone = StatFs(Environment.getExternalStorageDirectory().path)
    val card = context.getSystemService(StorageManager::class.java).storageVolumes
        .firstOrNull { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }?.directory?.let { StatFs(it.path) }
    StorageSpace(phone.totalBytes, phone.availableBytes, card?.totalBytes ?: 0L, card?.availableBytes ?: 0L)
}.getOrNull()

/**
 * Copia [items] a la tarjeta [volume], en la misma carpeta que tenían en el teléfono. Devuelve los
 * originales que se copiaron bien: esos son los que después se pueden borrar del teléfono.
 * [onProgress] recibe cuántos lleva.
 */
fun copyToCard(context: Context, items: List<MediaItem>, volume: String, onProgress: (Int) -> Unit): List<MediaItem> {
    val resolver = context.contentResolver
    val copied = ArrayList<MediaItem>()
    items.forEachIndexed { index, item ->
        val collection = if (item.isVideo) MediaStore.Video.Media.getContentUri(volume) else MediaStore.Images.Media.getContentUri(volume)
        // Android solo deja escribir en ciertas carpetas; lo que viene de otras va a Pictures/<álbum>.
        val folder = if (isWritableAlbumPath(item.path)) item.path else "Pictures/${albumName(item.bucket).ifEmpty { "Lumi" }}/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, resolver.getType(item.uri) ?: if (item.isVideo) "video/mp4" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.DATE_TAKEN, item.date)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = runCatching { resolver.insert(collection, values) }.getOrNull()
        if (target != null) {
            val ok = runCatching {
                resolver.openInputStream(item.uri)!!.use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it) } }
                resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }.isSuccess
            if (ok) copied += item else runCatching { resolver.delete(target, null, null) }
        }
        onProgress(index + 1)
    }
    return copied
}

// ---------- Contactos dentro del texto de una foto ----------

enum class ContactKind { PHONE, EMAIL, WEB }

/** Un teléfono, un correo o una página web que aparece escrita en una foto. */
class Contact(val kind: ContactKind, val value: String)

private val PHONE = Regex("(?<![\\w])(?:\\+\\d{1,3}[\\s.-]?)?(?:\\(?\\d{2,4}\\)?[\\s.-]?){2,5}\\d{2,4}(?![\\w])")

/** Busca en [text] teléfonos, correos y páginas web. Como mucho cuatro, sin repetir. */
fun findContacts(text: String): List<Contact> {
    if (text.isBlank()) return emptyList()
    val out = LinkedHashMap<String, Contact>()
    val emails = HashSet<String>()
    val email = Patterns.EMAIL_ADDRESS.matcher(text)
    while (email.find()) {
        val value = email.group().trimEnd('.', ',')
        emails += value.substringAfter('@').lowercase()
        out.putIfAbsent(value.lowercase(), Contact(ContactKind.EMAIL, value))
    }
    val web = Patterns.WEB_URL.matcher(text)
    while (web.find()) {
        val value = web.group().trimEnd('.', ',', ')')
        val plain = value.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
        // El dominio de un correo no cuenta como página, ni lo que no parece una web (números con puntos).
        if (plain.substringBefore('/') in emails || '@' in value || plain.none { it.isLetter() } || '.' !in plain) continue
        out.putIfAbsent(plain, Contact(ContactKind.WEB, value))
    }
    for (match in PHONE.findAll(text)) {
        val digits = match.value.count { it.isDigit() }
        if (digits !in 9..15) continue
        val value = match.value.trim()
        out.putIfAbsent(value.filter { it.isDigit() || it == '+' }, Contact(ContactKind.PHONE, value))
    }
    return out.values.take(4)
}
