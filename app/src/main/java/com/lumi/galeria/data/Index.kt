package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Lo que Lumi sabe de una foto después de mirarla: todo se calcula y se guarda en el teléfono. */
class IndexEntry(
    val modified: Long,
    /** Nitidez de la zona más definida; -1 si no se mide (capturas) o aún no se ha mirado a fondo. */
    val blur: Float,
    val lat: Float,
    val lon: Float,
    /** Cosas reconocidas, en inglés tal como las nombra el modelo. */
    val labels: List<String>,
    /** Texto leído dentro de la imagen. */
    val text: String,
    /** Ya pasó la segunda vuelta: texto y nitidez. */
    val deep: Boolean,
    /** Huella visual de 64 bits: dos imágenes iguales dan la misma aunque cambie el tamaño. 0 si no hay. */
    val hash: Long,
    /** Aún no se sabe qué cosas se ven: el reconocimiento no estaba disponible. Se reintenta. */
    val pending: Boolean = false,
    /** Color medio de la foto, opaco. 0 si aún no se ha calculado; [COLOR_NONE] si no se pudo. */
    val color: Int = 0,
    /** Móvil o cámara con que se hizo, ya con un nombre legible. Vacío si no consta; null si aún no se ha mirado. */
    val camera: String? = null,
    /** [FLAG_FLASH], [FLAG_FRONT] y [FLAG_MOTION]. */
    val flags: Int = 0,
) {
    val flash: Boolean get() = flags and FLAG_FLASH != 0
    val front: Boolean get() = flags and FLAG_FRONT != 0
    val motion: Boolean get() = flags and FLAG_MOTION != 0

    val hasPlace: Boolean get() = lat < 900f

    /** El texto sin tildes ni mayúsculas, que es como se busca en él. */
    val plainText: String by lazy(LazyThreadSafetyMode.PUBLICATION) { normalize(text) }
}

/** Una línea de texto de la foto y su caja, de 0 a 1 en proporción de la imagen. */
class TextLine(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** El texto de una foto por líneas. [aspect] es el ancho entre el alto de la imagen. */
class TextPage(val lines: List<TextLine>, val aspect: Float)

/** La ubicación aún no se ha intentado leer (faltaba el permiso). */
private const val PLACE_UNTRIED = 999f
private const val PLACE_NONE = 998f

/** No se pudo sacar el color de la foto: no se vuelve a intentar. */
const val COLOR_NONE = 1

/** Saltó el flash. */
const val FLAG_FLASH = 1

/** Hecha con la cámara delantera. */
const val FLAG_FRONT = 2

/** Foto en movimiento: lleva un vídeo corto dentro. */
const val FLAG_MOTION = 4

/**
 * Donde se guarda lo que se sabe de cada foto. Es una base de datos y no un archivo de texto para
 * poder anotar solo las fotos que cambian, en vez de reescribirlo todo cada pocos segundos.
 */
private class IndexStore(private val context: Context) : SQLiteOpenHelper(context, "analisis.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE foto (id INTEGER PRIMARY KEY, modified INTEGER, blur REAL, lat REAL, lon REAL, " +
                "labels TEXT, texto TEXT, deep INTEGER, hash INTEGER, pending INTEGER, color INTEGER, camara TEXT, flags INTEGER DEFAULT 0)",
        )
        importOldFile(db)
    }

    /** Versión 2: cámara, flash, selfie y foto en movimiento. Lo ya analizado se completa en la siguiente pasada. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE foto ADD COLUMN camara TEXT")
            db.execSQL("ALTER TABLE foto ADD COLUMN flags INTEGER DEFAULT 0")
        }
    }

    /** Lo analizado por versiones anteriores estaba en un archivo de texto: se pasa aquí una vez. */
    private fun importOldFile(db: SQLiteDatabase) {
        val old = File(context.filesDir, "index4.tsv")
        if (!old.exists()) return
        runCatching {
            old.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 9) {
                    val pending = p[5] == "?"
                    write(
                        db, p[0].toLong(),
                        IndexEntry(
                            p[1].toLong(), p[2].toFloat(), p[3].toFloat(), p[4].toFloat(),
                            if (p[5].isEmpty() || pending) emptyList() else p[5].split('|'), p[6], p[7] == "1", p[8].toLong(), pending,
                        ),
                    )
                }
            }
        }
        old.delete()
    }

    private fun write(db: SQLiteDatabase, id: Long, e: IndexEntry) {
        val row = ContentValues(13)
        row.put("id", id)
        row.put("modified", e.modified)
        row.put("blur", e.blur)
        row.put("lat", e.lat)
        row.put("lon", e.lon)
        row.put("labels", e.labels.joinToString("|"))
        row.put("texto", e.text)
        row.put("deep", if (e.deep) 1 else 0)
        row.put("hash", e.hash)
        row.put("pending", if (e.pending) 1 else 0)
        row.put("color", e.color)
        row.put("camara", e.camera)
        row.put("flags", e.flags)
        db.insertWithOnConflict("foto", null, row, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun readAll(into: MutableMap<Long, IndexEntry>) {
        readableDatabase.rawQuery("SELECT id, modified, blur, lat, lon, labels, texto, deep, hash, pending, color, camara, flags FROM foto", null).use { c ->
            while (c.moveToNext()) {
                val labels = c.getString(5).orEmpty()
                into[c.getLong(0)] = IndexEntry(
                    c.getLong(1), c.getFloat(2), c.getFloat(3), c.getFloat(4),
                    if (labels.isEmpty()) emptyList() else labels.split('|'), c.getString(6).orEmpty(),
                    c.getInt(7) == 1, c.getLong(8), c.getInt(9) == 1, c.getInt(10),
                    if (c.isNull(11)) null else c.getString(11), c.getInt(12),
                )
            }
        }
    }

    /** Anota de una vez las fotos que cambiaron y quita las que ya no existen. */
    fun save(changed: Map<Long, IndexEntry>, gone: Collection<Long>) {
        if (changed.isEmpty() && gone.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            changed.forEach { (id, entry) -> write(db, id, entry) }
            gone.forEach { db.delete("foto", "id = ?", arrayOf(it.toString())) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

/**
 * Mira las fotos en dos vueltas. La primera es rápida (cosas, lugar, huella y color, sobre la
 * miniatura) y deja la búsqueda utilizable en poco tiempo. La segunda lee el texto y mide la
 * nitidez a más resolución, y puede tardar bastante en una biblioteca grande.
 *
 * Los modelos que reconocen cosas y leen texto los descarga Google Play, no van dentro de la
 * app. Hasta que llegan, las llamadas fallan: lo que no se pudo mirar se deja anotado como
 * pendiente y se reintenta en la siguiente pasada, en vez de darlo por visto y vacío.
 */
class Indexer(private val context: Context) {
    private val store = IndexStore(context)
    private val entries = ConcurrentHashMap<Long, IndexEntry>()
    private val dirty = HashSet<Long>()
    private val gone = HashSet<Long>()
    private var loaded = false

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.4f).build()) }

    /** Olvida todo lo analizado para que se vuelva a leer desde cero. */
    @Synchronized
    fun reset() {
        load()
        entries.clear()
        dirty.clear()
        gone.clear()
        runCatching { store.writableDatabase.execSQL("DELETE FROM foto") }
    }

    /** Lo que se sabe ahora mismo, incluido lo guardado de otras veces. */
    fun snapshot(): Map<Long, IndexEntry> {
        load()
        return HashMap(entries)
    }

    @Synchronized
    private fun load() {
        if (loaded) return
        loaded = true
        listOf("index.tsv", "index2.tsv", "index3.tsv", "detail.tsv", "semantic.bin").forEach { File(context.filesDir, it).delete() }
        runCatching { store.readAll(entries) }
    }

    private fun put(id: Long, entry: IndexEntry) {
        entries[id] = entry
        dirty += id
    }

    private fun save() {
        runCatching {
            store.save(dirty.mapNotNull { id -> entries[id]?.let { id to it } }.toMap(), gone)
            dirty.clear()
            gone.clear()
        }
    }

    /**
     * [deep] elige la vuelta: la rápida (cosas, lugar, huella y color) o la lenta (texto y nitidez).
     * [onProgress] avisa de vez en cuando para que la pantalla enseñe lo que ya se sabe.
     */
    suspend fun scan(items: List<MediaItem>, canReadPlace: Boolean, deep: Boolean, onProgress: suspend () -> Unit) {
        load()
        val alive = items.mapTo(HashSet()) { it.id }
        entries.keys.filterTo(gone) { it !in alive }
        entries.keys.retainAll(alive)
        var fresh = 0
        var lastReport = System.currentTimeMillis()

        suspend fun report(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (fresh == 0 || (!force && now - lastReport < 8000)) return
            lastReport = now
            fresh = 0
            save()
            onProgress()
        }
        if (gone.isNotEmpty()) save()

        // Primera vuelta: cosas, lugar, huella y color. Los vídeos también, mirando su fotograma de portada.
        // Si el reconocimiento falla una vez, no se vuelve a intentar en esta pasada.
        var labelsDown = false
        for (item in if (deep) emptyList() else items) {
            coroutineContext.ensureActive()
            val known = entries[item.id]?.takeIf { it.modified == item.modified }
            val retry = known?.pending == true && !labelsDown
            // Lo analizado por versiones anteriores no tiene color: se saca de la miniatura, sin más.
            val needColor = known != null && known.color == 0
            // Lo analizado antes de la versión 2 no sabe de qué cámara viene: se lee el EXIF, sin más.
            val needCamera = known?.camera == null
            if (known != null && !retry && !needColor && !needCamera && !(known.lat == PLACE_UNTRIED && canReadPlace)) continue
            Quiet.whenIdle()
            val wantPlace = canReadPlace && !item.isVideo && (known == null || known.lat == PLACE_UNTRIED)
            val exif = if (!item.isVideo && (needCamera || wantPlace)) readExif(item, wantPlace) else null
            val place = when {
                known != null && known.lat != PLACE_UNTRIED -> known.lat to known.lon
                canReadPlace && !item.isVideo -> exif?.place ?: (PLACE_NONE to PLACE_NONE)
                canReadPlace || item.isVideo -> PLACE_NONE to PLACE_NONE
                else -> PLACE_UNTRIED to PLACE_UNTRIED
            }
            val wantLabels = known == null || retry
            val look = if (wantLabels || needColor) quickLook(item, withLabels = wantLabels && !labelsDown) else null
            if (wantLabels && look != null && look.labels == null) labelsDown = true
            put(
                item.id,
                IndexEntry(
                    modified = item.modified,
                    blur = known?.blur ?: -1f,
                    lat = place.first,
                    lon = place.second,
                    labels = if (wantLabels) look?.labels.orEmpty() else known?.labels.orEmpty(),
                    text = known?.text.orEmpty(),
                    // En los vídeos no hay texto ni nitidez que leer: quedan listos en esta vuelta.
                    deep = known?.deep ?: item.isVideo,
                    hash = known?.hash ?: look?.hash ?: 0L,
                    pending = if (wantLabels) look?.labels == null else known?.pending ?: false,
                    color = look?.color ?: known?.color ?: COLOR_NONE,
                    camera = if (needCamera) exif?.camera.orEmpty() else known?.camera,
                    flags = if (needCamera) exif?.flags ?: 0 else known?.flags ?: 0,
                ),
            )
            fresh++
            report()
        }
        report(force = true)

        if (!deep) return

        // Segunda vuelta: texto y nitidez. Una foto cuyo texto no se pudo leer queda para la
        // próxima pasada; si fallan varias seguidas es que el lector aún no está, y se deja.
        var failures = 0
        for (item in items) {
            coroutineContext.ensureActive()
            val known = entries[item.id] ?: continue
            if (known.deep) continue
            Quiet.whenIdle()
            val (text, blur) = readDeep(item)
            if (text == null) {
                if (++failures >= 3) break
                continue
            }
            failures = 0
            put(item.id, IndexEntry(known.modified, blur, known.lat, known.lon, known.labels, text, true, known.hash, known.pending, known.color, known.camera, known.flags))
            fresh++
            report()
        }
        report(force = true)
    }

    /** Lo que sale de mirar la miniatura. [labels] es null si el reconocimiento no respondió o no se pidió. */
    private class Look(val labels: List<String>?, val hash: Long, val color: Int)

    /** Sobre la miniatura: qué cosas se ven, la huella visual y el color medio. */
    private fun quickLook(item: MediaItem, withLabels: Boolean): Look = runCatching {
        val thumb = context.contentResolver.loadThumbnail(item.uri, Size(384, 384), null)
        val labels = if (!withLabels) null else runCatching {
            Tasks.await(labeler.process(InputImage.fromBitmap(thumb, 0))).take(20).map { it.text }
        }.getOrNull()
        val hash = differenceHash(thumb)
        val color = averageColor(thumb)
        thumb.recycle()
        Look(labels, hash, color)
    }.getOrDefault(Look(emptyList(), 0L, COLOR_NONE))

    /** Color medio de la imagen, opaco. Es lo que se pinta en su hueco mientras carga la miniatura. */
    private fun averageColor(bitmap: Bitmap): Int {
        var r = 0
        var g = 0
        var b = 0
        val steps = 12
        for (y in 0 until steps) for (x in 0 until steps) {
            val pixel = bitmap.getPixel((x * 2 + 1) * bitmap.width / (steps * 2), (y * 2 + 1) * bitmap.height / (steps * 2))
            r += pixel shr 16 and 0xFF
            g += pixel shr 8 and 0xFF
            b += pixel and 0xFF
        }
        val n = steps * steps
        return (0xFF shl 24) or (r / n shl 16) or (g / n shl 8) or (b / n)
    }

    /** Texto y nitidez. El texto es null si el lector no respondió. */
    private fun readDeep(item: MediaItem): Pair<String?, Float> {
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longSide = maxOf(info.size.width, info.size.height)
                if (longSide > SIDE) {
                    val k = SIDE.toFloat() / longSide
                    decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                }
            }
        } catch (e: Throwable) {
            return "" to -1f
        }
        val text = runCatching {
            Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text.replace(Regex("\\s+"), " ").trim().take(800)
        }.getOrNull()
        val blur = if (item.isScreenshot) -1f else detailScore(bitmap)
        bitmap.recycle()
        return text to blur
    }

    /** Lo que se lee del EXIF. [place] es null si no se pidió o la foto no lo guarda. */
    private class Exif(val place: Pair<Float, Float>?, val camera: String, val flags: Int)

    /**
     * Datos guardados en la propia foto: cámara, flash, si es un selfie, si lleva vídeo dentro y,
     * con [withPlace], el lugar. Android solo entrega el lugar con el permiso de ubicación de fotos.
     */
    private fun readExif(item: MediaItem, withPlace: Boolean): Exif? = try {
        val uri = if (withPlace) MediaStore.setRequireOriginal(item.uri) else item.uri
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            var flags = 0
            if (exif.getAttributeInt(ExifInterface.TAG_FLASH, 0) and 1 != 0) flags = flags or FLAG_FLASH
            val lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL).orEmpty()
            if (lens.contains("front", ignoreCase = true) || lens.contains("selfie", ignoreCase = true)) flags = flags or FLAG_FRONT
            if (isMotionPhoto(context, item, exif)) flags = flags or FLAG_MOTION
            Exif(
                if (withPlace) exif.latLong?.let { it[0].toFloat() to it[1].toFloat() } else null,
                cameraName(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL)),
                flags,
            )
        }
    } catch (e: Exception) {
        null
    }

    /** Las líneas de texto de una foto y dónde está cada una, para marcarlas encima de la imagen. */
    fun readTextPage(item: MediaItem): TextPage? {
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longSide = maxOf(info.size.width, info.size.height)
                if (longSide > 2400) {
                    val k = 2400f / longSide
                    decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                }
            }
        } catch (e: Throwable) {
            return null
        }
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val lines = runCatching {
            Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    TextLine(line.text, box.left / w, box.top / h, box.right / w, box.bottom / h)
                }
            }
        }.getOrNull()
        bitmap.recycle()
        return lines?.let { TextPage(it, w / h) }
    }

    /** Lee ahora mismo todo el texto de una foto, a buena resolución y respetando los saltos de línea. */
    fun readTextNow(item: MediaItem): String {
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longSide = maxOf(info.size.width, info.size.height)
                if (longSide > 2400) {
                    val k = 2400f / longSide
                    decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                }
            }
        } catch (e: Throwable) {
            return ""
        }
        val text = runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text.trim() }.getOrDefault("")
        bitmap.recycle()
        return text
    }

    fun blurry(items: List<MediaItem>): List<MediaItem> = items.filter { item ->
        val e = entries[item.id]
        e != null && e.modified == item.modified && e.blur >= 0f && e.blur < BLUR_LIMIT
    }

    /**
     * Nitidez de la zona más definida de la foto. Una foto buena con mucho cielo o fondo
     * desenfocado tiene al menos una zona nítida; una movida no tiene ninguna.
     */
    private fun detailScore(source: Bitmap): Float {
        // Se mide siempre a un tamaño parecido para que el umbral valga para cualquier cámara.
        val k = DETAIL_SIDE.toFloat() / maxOf(source.width, source.height)
        val bmp = if (k < 1f) Bitmap.createScaledBitmap(source, (source.width * k).toInt(), (source.height * k).toInt(), true) else source
        val w = bmp.width
        val h = bmp.height
        if (w < 64 || h < 64) return -1f
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        if (bmp !== source) bmp.recycle()
        for (i in px.indices) px[i] = gray(px[i])
        var best = 0f
        val tileW = w / TILES
        val tileH = h / TILES
        for (ty in 0 until TILES) for (tx in 0 until TILES) {
            var sum = 0.0
            var sumSq = 0.0
            var n = 0
            for (y in maxOf(ty * tileH, 1) until minOf((ty + 1) * tileH, h - 1)) {
                for (x in maxOf(tx * tileW, 1) until minOf((tx + 1) * tileW, w - 1)) {
                    val i = y * w + x
                    val lap = (px[i - 1] + px[i + 1] + px[i - w] + px[i + w] - 4 * px[i]).toDouble()
                    sum += lap
                    sumSq += lap * lap
                    n++
                }
            }
            if (n > 0) {
                val mean = sum / n
                best = maxOf(best, (sumSq / n - mean * mean).toFloat())
            }
        }
        return best
    }

    private companion object {
        const val SIDE = 1280
        const val DETAIL_SIDE = 768
        const val TILES = 4
        const val BLUR_LIMIT = 30f
    }
}

/**
 * Grupos de archivos repetidos en toda la biblioteca. En las fotos, misma huella visual y misma
 * proporción; en los vídeos, mismo tamaño y misma duración. Dentro de cada grupo va primero el
 * que se conserva: el de más resolución y, a igualdad, el más antiguo.
 */
fun findDuplicates(items: List<MediaItem>, index: Map<Long, IndexEntry>): List<List<MediaItem>> {
    val groups = HashMap<String, ArrayList<MediaItem>>()
    for (item in items) {
        val key = if (item.isVideo) {
            if (item.size < 100_000 || item.duration <= 0) continue
            "v:${item.size}:${item.duration}"
        } else {
            val hash = index[item.id]?.hash ?: continue
            // 0 y -1 son las huellas de las imágenes lisas (todo un color): no dicen nada.
            if (hash == 0L || hash == -1L || item.width <= 0 || item.height <= 0) continue
            "p:$hash:${item.width * 20 / item.height}"
        }
        groups.getOrPut(key) { ArrayList() } += item
    }
    return groups.values.filter { it.size >= 2 }.map { group ->
        group.sortedWith(compareByDescending<MediaItem> { it.width.toLong() * it.height }.thenBy { it.date })
    }
}

/**
 * Comprueba de verdad que [a] y [b] son la misma imagen: misma forma y, reducidas a 32 x 32 en
 * grises, casi los mismos píxeles. Tolera que una sea una copia recomprimida (como las que manda
 * WhatsApp) pero no dos fotos distintas que comparten huella.
 */
fun sameImage(context: Context, a: MediaItem, b: MediaItem): Boolean = runCatching {
    if (a.isVideo || b.isVideo) return@runCatching a.size == b.size && a.duration == b.duration
    if (a.width > 0 && b.width > 0 && a.height > 0 && b.height > 0) {
        val ratioA = a.width.toFloat() / a.height
        val ratioB = b.width.toFloat() / b.height
        if (kotlin.math.abs(ratioA - ratioB) > 0.02f * ratioA) return@runCatching false
    }
    val pa = tinyGray(context, a)
    val pb = tinyGray(context, b)
    var diff = 0L
    for (i in pa.indices) diff += kotlin.math.abs(pa[i] - pb[i])
    diff.toFloat() / pa.size <= SAME_IMAGE_LIMIT
}.getOrDefault(false)

/** Diferencia media por píxel (de 0 a 255) por debajo de la cual dos imágenes son la misma. */
private const val SAME_IMAGE_LIMIT = 6f

private fun tinyGray(context: Context, item: MediaItem): IntArray {
    val thumb = context.contentResolver.loadThumbnail(item.uri, Size(128, 128), null)
    val small = Bitmap.createScaledBitmap(thumb, 32, 32, true)
    if (small !== thumb) thumb.recycle()
    val px = IntArray(32 * 32)
    small.getPixels(px, 0, 32, 0, 0, 32, 32)
    small.recycle()
    for (i in px.indices) px[i] = gray(px[i])
    return px
}
