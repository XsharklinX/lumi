package com.lumi.galeria.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Copia de seguridad a una carpeta que elige el usuario: tarjeta SD, memoria USB o cualquier
 * otra que el teléfono ofrezca. Solo copia lo que aún no se había copiado y lo ordena por año y mes.
 */
class Backup(private val context: Context) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val record = File(context.filesDir, "backup.tsv")
    private val copied = HashSet<String>()
    private var loaded = false

    var destination: Uri?
        get() = prefs.getString("uri", null)?.let(Uri::parse)
        set(value) {
            prefs.edit().putString("uri", value?.toString()).putLong("last", 0).apply()
            // Otro destino empieza de cero: allí no hay nada copiado todavía.
            copied.clear()
            record.delete()
            loaded = true
        }

    val lastRun: Long get() = prefs.getLong("last", 0)

    fun destinationName(): String? = destination?.let { uri ->
        runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull() ?: "Carpeta elegida"
    }

    private fun load() {
        if (loaded) return
        loaded = true
        if (record.exists()) runCatching { copied += record.readLines() }
    }

    private fun mark(item: MediaItem) = "${item.id}:${item.modified}"

    fun pending(items: List<MediaItem>): List<MediaItem> {
        load()
        return items.filter { mark(it) !in copied }
    }

    /** Devuelve cuántos archivos se copiaron en esta pasada. */
    suspend fun run(items: List<MediaItem>, onProgress: (done: Int, total: Int) -> Unit): Int {
        val root = destination?.let { DocumentFile.fromTreeUri(context, it) } ?: return 0
        val todo = pending(items)
        val zone = ZoneId.systemDefault()
        val folders = HashMap<String, DocumentFile?>()

        fun child(parent: DocumentFile, name: String): DocumentFile? = parent.findFile(name) ?: parent.createDirectory(name)

        fun folderFor(item: MediaItem): DocumentFile? {
            val day = Instant.ofEpochMilli(item.date).atZone(zone).toLocalDate()
            val key = "%04d/%02d".format(day.year, day.monthValue)
            return folders.getOrPut(key) {
                child(root, "Lumi")?.let { child(it, "%04d".format(day.year)) }?.let { child(it, "%02d".format(day.monthValue)) }
            }
        }

        var done = 0
        var ok = 0
        for (item in todo) {
            coroutineContext.ensureActive()
            val success = runCatching {
                val folder = folderFor(item)!!
                val existing = folder.findFile(item.name)
                // Mismo nombre y mismo tamaño: ya estaba de una copia anterior.
                if (existing == null || existing.length() != item.size) {
                    val mime = context.contentResolver.getType(item.uri) ?: if (item.isVideo) "video/mp4" else "image/jpeg"
                    val target = folder.createFile(mime, item.name)!!
                    context.contentResolver.openInputStream(item.uri)!!.use { input ->
                        context.contentResolver.openOutputStream(target.uri)!!.use { input.copyTo(it) }
                    }
                }
            }.isSuccess
            if (success) {
                copied += mark(item)
                ok++
            }
            done++
            if (done % 10 == 0) runCatching { record.writeText(copied.joinToString("\n")) }
            onProgress(done, todo.size)
        }
        runCatching { record.writeText(copied.joinToString("\n")) }
        prefs.edit().putLong("last", System.currentTimeMillis()).apply()
        return ok
    }
}
