package com.lumi.galeria.data

import android.content.Context
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Lo que el usuario escribió de una foto: una nota libre y sus etiquetas. */
class Note(val text: String = "", val tags: List<String> = emptyList()) {
    val isEmpty: Boolean get() = text.isBlank() && tags.isEmpty()
}

private val TAG_CHARS = Regex("[^\\p{L}\\p{N}_-]")

/** Una etiqueta limpia: sin «#», sin espacios (van con guion), en minúsculas y sin símbolos. */
fun cleanTag(raw: String): String =
    TAG_CHARS.replace(raw.trim().removePrefix("#").lowercase(Locale.ROOT).replace(Regex("\\s+"), "-"), "").take(24)

/** Notas y etiquetas de todas las fotos. Se guardan en un archivo del teléfono, no dentro de las fotos. */
class NotesStore(context: Context) {
    private val file = File(context.filesDir, "notas.json")

    fun load(): Map<Long, Note> = runCatching {
        val root = JSONObject(file.readText())
        val out = HashMap<Long, Note>()
        root.keys().forEach { key ->
            val o = root.getJSONObject(key)
            val tags = o.optJSONArray("g")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            val note = Note(o.optString("t"), tags)
            if (!note.isEmpty) out[key.toLong()] = note
        }
        out
    }.getOrDefault(emptyMap())

    fun save(notes: Map<Long, Note>) {
        runCatching {
            val root = JSONObject()
            notes.forEach { (id, note) ->
                if (!note.isEmpty) root.put(id.toString(), JSONObject().put("t", note.text).put("g", JSONArray(note.tags)))
            }
            file.writeText(root.toString())
        }
    }
}

/** Una búsqueda guardada como álbum: se vuelve a hacer cada vez, así que se llena sola. */
data class SmartAlbum(val id: Long, val name: String, val query: String, val filters: Filters)

fun filtersToJson(f: Filters): JSONObject = JSONObject().apply {
    put("kinds", JSONArray(f.kinds.map { it.name }))
    f.year?.let { put("year", it) }
    f.month?.let { put("month", it) }
    f.place?.let { put("place", it) }
    f.album?.let { put("album", it) }
    f.thing?.let { put("thing", it) }
    f.camera?.let { put("camera", it) }
    f.size?.let { put("size", it.name) }
    f.format?.let { put("format", it) }
    put("people", JSONArray(f.people.toList()))
}

fun filtersFromJson(o: JSONObject): Filters = Filters(
    kinds = o.optJSONArray("kinds")?.let { a -> (0 until a.length()).mapNotNull { runCatching { Kind.valueOf(a.getString(it)) }.getOrNull() }.toSet() }.orEmpty(),
    year = if (o.has("year")) o.getInt("year") else null,
    month = if (o.has("month")) o.getInt("month") else null,
    place = o.optString("place").ifEmpty { null },
    album = if (o.has("album")) o.getLong("album") else null,
    thing = o.optString("thing").ifEmpty { null },
    camera = o.optString("camera").ifEmpty { null },
    size = o.optString("size").takeIf { it.isNotEmpty() }?.let { runCatching { SizeRange.valueOf(it) }.getOrNull() },
    format = o.optString("format").ifEmpty { null },
    people = o.optJSONArray("people")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty(),
)

fun smartAlbumsToJson(list: List<SmartAlbum>): String = JSONArray().also { array ->
    list.forEach { array.put(JSONObject().put("id", it.id).put("n", it.name).put("q", it.query).put("f", filtersToJson(it.filters))) }
}.toString()

fun smartAlbumsFromJson(text: String?): List<SmartAlbum> = runCatching {
    val a = JSONArray(text ?: "[]")
    (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        SmartAlbum(o.getLong("id"), o.getString("n"), o.optString("q"), filtersFromJson(o.optJSONObject("f") ?: JSONObject()))
    }
}.getOrDefault(emptyList())

/** Un nombre para una búsqueda guardada: lo escrito y, si hay, lo elegido («Lucía en la playa»). */
fun suggestAlbumName(query: String, filters: Filters): String {
    val parts = ArrayList<String>()
    if (query.isNotBlank()) parts += query.trim().replaceFirstChar { it.uppercase() }
    filters.people.forEach { parts += it }
    filters.thing?.let { parts += it.replaceFirstChar(Char::uppercase) }
    filters.place?.let { parts += it }
    filters.year?.let { parts += it.toString() }
    filters.kinds.forEach { parts += it.label }
    return parts.distinct().joinToString(" · ").take(40).ifBlank { "Búsqueda guardada" }
}

/** Manda [items] a la papelera sin preguntar. Solo funciona con la gestión multimedia concedida (Android 12 o posterior). */
fun trashSilently(context: Context, items: List<MediaItem>): Int {
    var done = 0
    items.forEach { item ->
        val ok = runCatching {
            context.contentResolver.update(
                item.uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null,
            ) > 0
        }.getOrDefault(false)
        if (ok) done++
    }
    return done
}
