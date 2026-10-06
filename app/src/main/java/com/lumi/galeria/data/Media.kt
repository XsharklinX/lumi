package com.lumi.galeria.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns

data class MediaItem(
    val id: Long,
    val isVideo: Boolean,
    val name: String,
    /** Fecha de captura en milisegundos. */
    val date: Long,
    val modified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val duration: Long,
    val bucketId: Long,
    val bucket: String,
    val path: String,
    /** Segundos desde 1970 en que el sistema vacía este elemento de la papelera; 0 si no está en ella. */
    val expires: Long,
    /** Dirección de un archivo que llega de otra app y no está en la biblioteca. */
    val external: Uri? = null,
    /** Está en la tarjeta de memoria y no en la memoria interna del teléfono. */
    val onCard: Boolean = false,
    /** Grados que hay que girar la imagen guardada para verla derecha (0, 90, 180 o 270). */
    val rotation: Int = 0,
) {
    /** Ancho y alto tal como se ve, ya girada. */
    val shownWidth: Int get() = if (rotation % 180 == 0) width else height
    val shownHeight: Int get() = if (rotation % 180 == 0) height else width

    /** Formato según la extensión, en mayúsculas: JPG, HEIC, PNG, DNG, MP4… */
    val format: String
        get() = when (val ext = name.substringAfterLast('.', "").uppercase()) {
            "JPEG" -> "JPG"
            "HEIF" -> "HEIC"
            else -> ext
        }

    val uri: Uri
        get() = external ?: ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id,
        )

    val isExternal: Boolean get() = external != null

    val isGif: Boolean get() = name.endsWith(".gif", ignoreCase = true)

    val isScreenshot: Boolean
        get() = !isVideo && (path.contains("screenshot", ignoreCase = true) || name.startsWith("Screenshot", ignoreCase = true))
}

fun externalItem(uri: Uri, isVideo: Boolean) = MediaItem(
    id = -1, isVideo = isVideo, name = uri.lastPathSegment ?: "", date = System.currentTimeMillis(), modified = 0,
    size = 0, width = 0, height = 0, duration = 0, bucketId = 0, bucket = "", path = "", expires = 0, external = uri,
)

/** Identificador en la biblioteca si [uri] apunta a ella (content://media/.../123). */
fun mediaIdOf(uri: Uri): Long? =
    if (uri.authority == MediaStore.AUTHORITY) uri.lastPathSegment?.toLongOrNull() else null

class Album(
    val bucketId: Long,
    val name: String,
    val path: String,
    val cover: MediaItem,
    val count: Int,
    /** Con candado: no enseña portada ni contenido hasta desbloquear. */
    val locked: Boolean = false,
    /** Oculto: no aparece salvo que se pida ver los ocultos. */
    val hidden: Boolean = false,
    /** Fijado: va siempre al principio de la lista. */
    val pinned: Boolean = false,
    /** Cuántos de sus elementos están en la tarjeta de memoria. */
    val onCard: Int = 0,
)

enum class ItemSort(val label: String) {
    NEWEST("Más recientes"), OLDEST("Más antiguas"), NAME("Nombre"), LARGEST("Tamaño"),
}

enum class AlbumSort(val label: String) {
    RECENT("Más recientes"), NAME("Nombre"), COUNT("Cantidad de fotos"), MANUAL("A mano"),
}

fun sortItems(items: List<MediaItem>, sort: ItemSort): List<MediaItem> = when (sort) {
    ItemSort.NEWEST -> items
    ItemSort.OLDEST -> items.asReversed()
    ItemSort.NAME -> items.sortedBy { it.name.lowercase() }
    ItemSort.LARGEST -> items.sortedByDescending { it.size }
}

/** Nombre que se enseña para las carpetas habituales del teléfono. */
fun albumName(bucket: String): String = when (bucket.lowercase()) {
    "camera" -> "Cámara"
    "screenshots" -> "Capturas"
    "download", "downloads" -> "Descargas"
    "whatsapp images" -> "WhatsApp"
    "whatsapp video" -> "Vídeos de WhatsApp"
    "" -> "Sin carpeta"
    else -> bucket
}

/** Android solo deja guardar fotos y vídeos de terceros dentro de estas carpetas. */
fun isWritableAlbumPath(path: String): Boolean = path.startsWith("DCIM/") || path.startsWith("Pictures/")

fun buildAlbums(
    items: List<MediaItem>,
    locked: Set<Long>,
    hidden: Set<Long>,
    sort: AlbumSort,
    pinned: Set<Long> = emptySet(),
    covers: Map<Long, Long> = emptyMap(),
    /** Orden elegido a mano: los álbumes que no están, al final. */
    order: List<Long> = emptyList(),
): List<Album> {
    val albums = items.groupBy { it.bucketId }.map { (id, list) ->
        // La portada es la foto que eligió el usuario, si sigue en el álbum; si no, la más reciente.
        val cover = covers[id]?.let { wanted -> list.firstOrNull { it.id == wanted } } ?: list[0]
        Album(id, albumName(list[0].bucket), list[0].path, cover, list.size, id in locked, id in hidden, id in pinned, list.count { it.onCard })
    }
    val sorted = when (sort) {
        // La cámara siempre va primero en el orden por defecto.
        AlbumSort.RECENT -> albums.sortedWith(
            compareByDescending<Album> { it.cover.bucket.equals("Camera", ignoreCase = true) }.thenByDescending { it.cover.date },
        )
        AlbumSort.NAME -> albums.sortedBy { normalize(it.name) }
        AlbumSort.COUNT -> albums.sortedByDescending { it.count }
        AlbumSort.MANUAL -> {
            val place = order.withIndex().associate { it.value to it.index }
            albums.sortedWith(compareBy<Album> { place[it.bucketId] ?: Int.MAX_VALUE }.thenByDescending { it.cover.date })
        }
    }
    return sorted.sortedByDescending { it.pinned }
}

class Library(val active: List<MediaItem>, val trashed: List<MediaItem>)

val FILES_URI: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

fun loadLibrary(context: Context): Library {
    val columns = arrayOf(
        FileColumns._ID,
        FileColumns.MEDIA_TYPE,
        FileColumns.DISPLAY_NAME,
        FileColumns.DATE_TAKEN,
        FileColumns.DATE_MODIFIED,
        FileColumns.SIZE,
        FileColumns.WIDTH,
        FileColumns.HEIGHT,
        FileColumns.DURATION,
        FileColumns.BUCKET_DISPLAY_NAME,
        FileColumns.RELATIVE_PATH,
        FileColumns.IS_TRASHED,
        FileColumns.DATE_EXPIRES,
        FileColumns.BUCKET_ID,
        FileColumns.VOLUME_NAME,
        FileColumns.ORIENTATION,
    )
    val args = Bundle().apply {
        putString(
            ContentResolver.QUERY_ARG_SQL_SELECTION,
            "${FileColumns.MEDIA_TYPE} IN (${FileColumns.MEDIA_TYPE_IMAGE},${FileColumns.MEDIA_TYPE_VIDEO})",
        )
        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
    }
    val active = ArrayList<MediaItem>()
    val trashed = ArrayList<MediaItem>()
    context.contentResolver.query(FILES_URI, columns, args, null)?.use { c ->
        while (c.moveToNext()) {
            val modified = c.getLong(4)
            val taken = c.getLong(3)
            val isTrashed = c.getInt(11) != 0
            val item = MediaItem(
                id = c.getLong(0),
                isVideo = c.getInt(1) == FileColumns.MEDIA_TYPE_VIDEO,
                name = c.getString(2) ?: "",
                date = if (taken > 0) taken else modified * 1000,
                modified = modified,
                size = c.getLong(5),
                width = c.getInt(6),
                height = c.getInt(7),
                duration = c.getLong(8),
                bucketId = c.getLong(13),
                bucket = c.getString(9) ?: "",
                path = c.getString(10) ?: "",
                expires = if (isTrashed) c.getLong(12) else 0,
                // El almacenamiento interno se llama siempre igual; cualquier otro nombre es una tarjeta.
                onCard = c.getString(14)?.let { it != MediaStore.VOLUME_EXTERNAL_PRIMARY } ?: false,
                rotation = c.getInt(15),
            )
            if (isTrashed) trashed += item else active += item
        }
    }
    active.sortWith(compareByDescending<MediaItem> { it.date }.thenByDescending { it.id })
    trashed.sortBy { it.expires }
    return Library(active, trashed)
}

/** El sistema enseña un único diálogo de confirmación para todo el lote. */
fun trashRequest(context: Context, items: List<MediaItem>, trash: Boolean): IntentSender =
    MediaStore.createTrashRequest(context.contentResolver, items.map { it.uri }, trash).intentSender

fun deleteRequest(context: Context, items: List<MediaItem>): IntentSender =
    MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri }).intentSender

/** Permiso para modificar archivos que creó otra app; hace falta para moverlos de álbum. */
fun writeRequest(context: Context, items: List<MediaItem>): IntentSender =
    MediaStore.createWriteRequest(context.contentResolver, items.map { it.uri }).intentSender

// ---------- Lo último que se vio ----------

private const val SNAPSHOT_VERSION = 2

/**
 * Guarda la lista de fotos tal como quedó, para pintarla nada más abrir la app la próxima vez,
 * antes de preguntarle al teléfono qué hay. Solo datos de la lista (nombre, fecha, tamaño…);
 * las imágenes no se copian.
 */
fun saveSnapshot(context: Context, items: List<MediaItem>) {
    val temp = java.io.File(context.filesDir, "biblioteca.tmp")
    runCatching {
        java.io.DataOutputStream(temp.outputStream().buffered(1 shl 16)).use { out ->
            out.writeInt(SNAPSHOT_VERSION)
            out.writeInt(items.size)
            for (item in items) {
                out.writeLong(item.id)
                out.writeBoolean(item.isVideo)
                out.writeUTF(item.name.take(200))
                out.writeLong(item.date)
                out.writeLong(item.modified)
                out.writeLong(item.size)
                out.writeInt(item.width)
                out.writeInt(item.height)
                out.writeLong(item.duration)
                out.writeLong(item.bucketId)
                out.writeUTF(item.bucket.take(200))
                out.writeUTF(item.path.take(400))
                out.writeBoolean(item.onCard)
                out.writeShort(item.rotation)
            }
        }
        temp.renameTo(java.io.File(context.filesDir, "biblioteca.bin"))
    }
}

/** La lista guardada por [saveSnapshot]; null si no hay o no se puede leer. */
fun readSnapshot(context: Context): List<MediaItem>? = runCatching {
    val file = java.io.File(context.filesDir, "biblioteca.bin")
    if (!file.exists()) return null
    java.io.DataInputStream(file.inputStream().buffered(1 shl 16)).use { input ->
        if (input.readInt() != SNAPSHOT_VERSION) return null
        val count = input.readInt()
        val items = ArrayList<MediaItem>(count)
        repeat(count) {
            items += MediaItem(
                id = input.readLong(), isVideo = input.readBoolean(), name = input.readUTF(), date = input.readLong(),
                modified = input.readLong(), size = input.readLong(), width = input.readInt(), height = input.readInt(),
                duration = input.readLong(), bucketId = input.readLong(), bucket = input.readUTF(), path = input.readUTF(),
                expires = 0, onCard = input.readBoolean(), rotation = input.readShort().toInt(),
            )
        }
        items
    }
}.getOrNull()
