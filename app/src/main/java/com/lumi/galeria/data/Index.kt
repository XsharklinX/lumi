package com.lumi.galeria.data

import android.content.Context
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
) {
    val hasPlace: Boolean get() = lat < 900f

    /** El texto sin tildes ni mayúsculas, que es como se busca en él. */
    val plainText: String by lazy(LazyThreadSafetyMode.PUBLICATION) { normalize(text) }
}

/** La ubicación aún no se ha intentado leer (faltaba el permiso). */
private const val PLACE_UNTRIED = 999f
private const val PLACE_NONE = 998f

/**
 * Mira las fotos en dos vueltas. La primera es rápida (cosas, lugar y huella, sobre la miniatura)
 * y deja la búsqueda utilizable en poco tiempo. La segunda lee el texto y mide la nitidez a más
 * resolución, y puede tardar bastante en una biblioteca grande.
 */
class Indexer(private val context: Context) {
    private val file = File(context.filesDir, "index4.tsv")
    private val entries = ConcurrentHashMap<Long, IndexEntry>()
    private var loaded = false

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.4f).build()) }

    /** Lo que se sabe ahora mismo, incluido lo guardado de otras veces. */
    fun snapshot(): Map<Long, IndexEntry> {
        load()
        return HashMap(entries)
    }

    @Synchronized
    private fun load() {
        if (loaded) return
        loaded = true
        listOf("index.tsv", "index2.tsv", "index3.tsv", "detail.tsv").forEach { File(context.filesDir, it).delete() }
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 9) {
                    entries[p[0].toLong()] = IndexEntry(
                        p[1].toLong(), p[2].toFloat(), p[3].toFloat(), p[4].toFloat(),
                        if (p[5].isEmpty()) emptyList() else p[5].split('|'), p[6], p[7] == "1", p[8].toLong(),
                    )
                }
            }
        }
    }

    private fun save() {
        runCatching {
            file.bufferedWriter().use { w ->
                entries.forEach { (id, e) ->
                    w.write("$id\t${e.modified}\t${e.blur}\t${e.lat}\t${e.lon}\t${e.labels.joinToString("|")}\t${e.text}\t${if (e.deep) 1 else 0}\t${e.hash}\n")
                }
            }
        }
    }

    /**
     * [deep] elige la vuelta: la rápida (cosas, lugar y huella) o la lenta (texto y nitidez).
     * [onProgress] avisa de vez en cuando para que la pantalla enseñe lo que ya se sabe.
     */
    suspend fun scan(items: List<MediaItem>, canReadPlace: Boolean, deep: Boolean, onProgress: suspend () -> Unit) {
        load()
        entries.keys.retainAll(items.mapTo(HashSet()) { it.id })
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

        // Primera vuelta: cosas, lugar y huella. Los vídeos también, mirando su fotograma de portada.
        for (item in if (deep) emptyList() else items) {
            coroutineContext.ensureActive()
            val known = entries[item.id]?.takeIf { it.modified == item.modified }
            if (known != null && !(known.lat == PLACE_UNTRIED && canReadPlace)) continue
            Quiet.whenIdle()
            val place = if (canReadPlace && !item.isVideo) readPlace(item) else null
            val none = if (canReadPlace || item.isVideo) PLACE_NONE else PLACE_UNTRIED
            val quick = if (known == null) quickLook(item) else null
            entries[item.id] = IndexEntry(
                modified = item.modified,
                blur = known?.blur ?: -1f,
                lat = place?.first ?: none,
                lon = place?.second ?: none,
                labels = known?.labels ?: quick?.first.orEmpty(),
                text = known?.text.orEmpty(),
                // En los vídeos no hay texto ni nitidez que leer: quedan listos en esta vuelta.
                deep = known?.deep ?: item.isVideo,
                hash = known?.hash ?: quick?.second ?: 0L,
            )
            fresh++
            report()
        }
        report(force = true)

        if (!deep) return

        // Segunda vuelta: texto y nitidez.
        for (item in items) {
            coroutineContext.ensureActive()
            val known = entries[item.id] ?: continue
            if (known.deep) continue
            Quiet.whenIdle()
            val (text, blur) = readDeep(item)
            entries[item.id] = IndexEntry(known.modified, blur, known.lat, known.lon, known.labels, text, true, known.hash)
            fresh++
            report()
        }
        report(force = true)
    }

    /** Sobre la miniatura: qué cosas se ven y la huella visual. */
    private fun quickLook(item: MediaItem): Pair<List<String>, Long> = runCatching {
        val thumb = context.contentResolver.loadThumbnail(item.uri, Size(384, 384), null)
        val labels = runCatching { Tasks.await(labeler.process(InputImage.fromBitmap(thumb, 0))).take(20).map { it.text } }.getOrDefault(emptyList())
        val hash = differenceHash(thumb)
        thumb.recycle()
        labels to hash
    }.getOrDefault(emptyList<String>() to 0L)

    private fun readDeep(item: MediaItem): Pair<String, Float> {
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
        }.getOrDefault("")
        val blur = if (item.isScreenshot) -1f else detailScore(bitmap)
        bitmap.recycle()
        return text to blur
    }

    /** Lugar guardado en la propia foto. Android solo lo entrega con el permiso de ubicación de fotos. */
    private fun readPlace(item: MediaItem): Pair<Float, Float>? = try {
        context.contentResolver.openInputStream(MediaStore.setRequireOriginal(item.uri))?.use { stream ->
            ExifInterface(stream).latLong?.let { it[0].toFloat() to it[1].toFloat() }
        }
    } catch (e: Exception) {
        null
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
