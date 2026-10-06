package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Un archivo de la cámara, la tarjeta o la memoria USB. [known]: ya está en el teléfono. */
class Importable(
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
    /** Fecha del archivo en milisegundos; las cámaras la ponen al hacer la foto. */
    val date: Long,
    val known: Boolean,
) {
    val isVideo: Boolean get() = mime.startsWith("video/")
}

private val PHOTO_TYPES = mapOf(
    "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "heic" to "image/heic", "heif" to "image/heif",
    "webp" to "image/webp", "gif" to "image/gif", "dng" to "image/x-adobe-dng", "arw" to "image/x-sony-arw",
    "cr2" to "image/x-canon-cr2", "cr3" to "image/x-canon-cr3", "nef" to "image/x-nikon-nef", "orf" to "image/x-olympus-orf",
    "rw2" to "image/x-panasonic-rw2", "raf" to "image/x-fuji-raf",
)
private val VIDEO_TYPES = mapOf(
    "mp4" to "video/mp4", "mov" to "video/quicktime", "m4v" to "video/mp4", "3gp" to "video/3gpp", "mkv" to "video/x-matroska",
    "webm" to "video/webm", "mts" to "video/mp2t", "avi" to "video/avi",
)

/**
 * Recorre la carpeta [tree] (y sus subcarpetas) y devuelve las fotos y vídeos que hay, de la más
 * reciente a la más antigua. Un archivo con el mismo nombre y peso que otro de la biblioteca se
 * marca como ya importado: es lo que pasa al volver a conectar la misma cámara.
 */
suspend fun scanImport(context: Context, tree: Uri, library: List<MediaItem>, onFound: (Int) -> Unit): List<Importable> {
    val have = library.mapTo(HashSet()) { it.name.lowercase() + ":" + it.size }
    val out = ArrayList<Importable>()
    val resolver = context.contentResolver
    val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )
    val pending = ArrayDeque<String>()
    pending += DocumentsContract.getTreeDocumentId(tree)
    var folders = 0
    while (pending.isNotEmpty() && folders < 2000) {
        coroutineContext.ensureActive()
        folders++
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, pending.removeFirst())
        runCatching {
            resolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1).orEmpty()
                    val mime = c.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        // Las miniaturas y papeleras que crean las cámaras y los sistemas no interesan.
                        if (!name.startsWith(".") && !name.equals("thumbnails", ignoreCase = true)) pending += id
                        continue
                    }
                    if (name.startsWith(".")) continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val type = PHOTO_TYPES[ext] ?: VIDEO_TYPES[ext] ?: continue
                    val size = c.getLong(3)
                    out += Importable(
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                        name = name,
                        mime = type,
                        size = size,
                        date = c.getLong(4),
                        known = name.lowercase() + ":" + size in have,
                    )
                    if (out.size % 50 == 0) onFound(out.size)
                }
            }
        }
    }
    out.sortByDescending { it.date }
    return out
}

/** Nombre de la carpeta elegida, para proponerlo como álbum. */
fun importSourceName(context: Context, tree: Uri): String = runCatching {
    val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull().orEmpty()

/**
 * Copia [file] a la biblioteca, en DCIM/[album]. Conserva su nombre y su fecha. Devuelve false si
 * no se pudo; lo copiado a medias se borra.
 */
fun importOne(context: Context, file: Importable, album: String): Boolean {
    val resolver = context.contentResolver
    val collection = if (file.isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        put(MediaStore.MediaColumns.MIME_TYPE, file.mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/${safeFolder(album)}/")
        // Si el archivo no lleva fecha dentro (EXIF), que no aparezca como hecho hoy.
        if (file.date > 0) put(MediaStore.MediaColumns.DATE_TAKEN, file.date)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
    val ok = runCatching {
        resolver.openInputStream(file.uri)!!.use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it, 1 shl 16) } }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!ok) runCatching { resolver.delete(target, null, null) }
    return ok
}

/** Un nombre de álbum que sirve como carpeta: sin barras ni caracteres que Android rechaza. */
fun safeFolder(name: String): String =
    name.trim().replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().take(60).ifEmpty { "Importadas" }
