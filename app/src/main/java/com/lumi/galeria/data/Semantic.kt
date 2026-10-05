package com.lumi.galeria.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/**
 * Búsqueda por significado. Un modelo (MobileCLIP S0, incluido en la app) convierte cada foto en
 * 512 números, y cualquier frase en otros 512; cuanto más se parecen los dos, mejor describe la
 * frase a la foto. Así se puede buscar "león" o "perro en la playa" sin una lista fija de cosas.
 * Todo se calcula y se guarda en el teléfono.
 */
class Semantic(private val context: Context) {
    private class Entry(val modified: Long, val vector: ByteArray)

    private val file = File(context.filesDir, "semantic.bin")
    private val entries = ConcurrentHashMap<Long, Entry>()
    private var loaded = false

    private val env by lazy { OrtEnvironment.getEnvironment() }
    private val options by lazy {
        // Dos hilos como mucho: analizar no debe dejar al teléfono sin aire.
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
    }
    private val vision by lazy { env.createSession(context.assets.open("clip/vision.onnx").use { it.readBytes() }, options) }
    private val text by lazy { env.createSession(context.assets.open("clip/text.onnx").use { it.readBytes() }, options) }

    private val vocab = HashMap<String, Int>()
    private val ranks = HashMap<String, Int>()

    val size: Int get() = entries.size

    fun has(id: Long): Boolean = entries.containsKey(id)

    @Synchronized
    private fun load() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                repeat(input.readInt()) {
                    val id = input.readLong()
                    val modified = input.readLong()
                    val vector = ByteArray(DIM).also(input::readFully)
                    entries[id] = Entry(modified, vector)
                }
            }
        }
    }

    private fun save() {
        runCatching {
            DataOutputStream(file.outputStream().buffered()).use { out ->
                val all = entries.entries.toList()
                out.writeInt(all.size)
                all.forEach { (id, e) ->
                    out.writeLong(id)
                    out.writeLong(e.modified)
                    out.write(e.vector)
                }
            }
        }
    }

    /** Cuántos elementos de [items] faltan por mirar. */
    fun pending(items: List<MediaItem>): Int {
        load()
        return items.count { entries[it.id]?.modified != it.modified }
    }

    /** Mira lo que falta, despacio y parando mientras se usa la app. */
    suspend fun scan(items: List<MediaItem>, onProgress: suspend () -> Unit) {
        load()
        entries.keys.retainAll(items.mapTo(HashSet()) { it.id })
        var fresh = 0
        var lastReport = System.currentTimeMillis()
        for (item in items) {
            coroutineContext.ensureActive()
            if (entries[item.id]?.modified == item.modified) continue
            Quiet.whenIdle()
            // Si una imagen no se deja leer se guarda vacía, para no intentarlo en cada arranque.
            entries[item.id] = Entry(item.modified, runCatching { embedImage(item) }.getOrNull() ?: ByteArray(DIM))
            fresh++
            val now = System.currentTimeMillis()
            if (now - lastReport > 10_000) {
                lastReport = now
                save()
                onProgress()
            }
        }
        if (fresh > 0) {
            save()
            onProgress()
        }
    }

    private fun embedImage(item: MediaItem): ByteArray {
        val loaded = context.contentResolver.loadThumbnail(item.uri, Size(384, 384), null)
        val thumb = if (loaded.config == Bitmap.Config.HARDWARE) loaded.copy(Bitmap.Config.ARGB_8888, false) else loaded
        // El modelo espera un cuadrado de 256: se ajusta el lado corto y se recorta el centro.
        val k = SIDE.toFloat() / minOf(thumb.width, thumb.height)
        val scaled = Bitmap.createScaledBitmap(thumb, maxOf(SIDE, (thumb.width * k).roundToInt()), maxOf(SIDE, (thumb.height * k).roundToInt()), true)
        val pixels = IntArray(SIDE * SIDE)
        scaled.getPixels(pixels, 0, SIDE, (scaled.width - SIDE) / 2, (scaled.height - SIDE) / 2, SIDE, SIDE)
        if (scaled !== thumb) scaled.recycle()
        thumb.recycle()
        // Tres planos seguidos (rojo, verde, azul), con valores de 0 a 1.
        val input = FloatBuffer.allocate(3 * SIDE * SIDE)
        val plane = SIDE * SIDE
        for (i in 0 until plane) {
            val p = pixels[i]
            input.put(i, (p shr 16 and 0xFF) / 255f)
            input.put(plane + i, (p shr 8 and 0xFF) / 255f)
            input.put(2 * plane + i, (p and 0xFF) / 255f)
        }
        val vector = OnnxTensor.createTensor(env, input, longArrayOf(1, 3, SIDE.toLong(), SIDE.toLong())).use { tensor ->
            vision.run(mapOf("pixel_values" to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                (result[0].value as Array<FloatArray>)[0]
            }
        }
        return pack(vector)
    }

    /** Cada número se guarda en un byte: ocupa una cuarta parte y la comparación casi no lo nota. */
    private fun pack(vector: FloatArray): ByteArray {
        val length = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
        return ByteArray(DIM) { i -> (vector[i] / length * 127f).roundToInt().coerceIn(-127, 127).toByte() }
    }

    private fun loadTokenizer() {
        if (vocab.isNotEmpty()) return
        val json = JSONObject(context.assets.open("clip/tokenizer.json").use { it.readBytes().toString(Charsets.UTF_8) })
        val words = json.getJSONObject("vocab")
        words.keys().forEach { vocab[it] = words.getInt(it) }
        val merges = json.getJSONArray("merges")
        for (i in 0 until merges.length()) ranks[merges.getString(i)] = i
    }

    /** Trocea una palabra en las piezas que conoce el modelo, uniendo primero los pares más frecuentes. */
    private fun pieces(word: String): List<String> {
        var parts = word.mapIndexed { i, c -> if (i == word.lastIndex) "$c</w>" else c.toString() }
        while (parts.size > 1) {
            var best = -1
            var bestRank = Int.MAX_VALUE
            for (i in 0 until parts.size - 1) {
                val rank = ranks["${parts[i]} ${parts[i + 1]}"] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    best = i
                }
            }
            if (best < 0) break
            val first = parts[best]
            val second = parts[best + 1]
            val merged = ArrayList<String>(parts.size)
            var i = 0
            while (i < parts.size) {
                if (i < parts.size - 1 && parts[i] == first && parts[i + 1] == second) {
                    merged += first + second
                    i += 2
                } else {
                    merged += parts[i]
                    i++
                }
            }
            parts = merged
        }
        return parts
    }

    private fun embedText(phrase: String): FloatArray {
        loadTokenizer()
        val ids = ArrayList<Long>()
        ids += START
        // La frase llega en minúsculas y sin tildes, así que basta con letras, cifras y signos sueltos.
        WORD.findAll(phrase).forEach { match -> pieces(match.value).forEach { ids += (vocab[it] ?: END.toInt()).toLong() } }
        while (ids.size > LENGTH - 1) ids.removeAt(ids.lastIndex)
        ids += END
        while (ids.size < LENGTH) ids += 0L
        val vector = OnnxTensor.createTensor(env, LongBuffer.wrap(ids.toLongArray()), longArrayOf(1, LENGTH.toLong())).use { tensor ->
            text.run(mapOf("input_ids" to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                (result[0].value as Array<FloatArray>)[0]
            }
        }
        val length = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
        return FloatArray(DIM) { vector[it] / length }
    }

    /**
     * Parecido de cada foto con [phrase] (de 0 a 1; por encima de 0,16 suele haber relación).
     * Si se da además la frase en inglés, se mezclan las dos: el modelo aprendió sobre todo en
     * inglés y así acierta más, sin dejar de entender el español.
     */
    fun scores(phrase: String, english: String?): Map<Long, Float> {
        load()
        if (phrase.isBlank() || entries.isEmpty()) return emptyMap()
        val query = runCatching {
            val own = embedText(phrase)
            val other = english?.let { embedText("a photo of $it") }
            if (other == null) own else FloatArray(DIM) { (own[it] + other[it]) / 2f }
        }.getOrNull() ?: return emptyMap()
        val length = sqrt(query.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
        val out = HashMap<Long, Float>(entries.size)
        for ((id, entry) in entries) {
            var dot = 0f
            val v = entry.vector
            for (i in 0 until DIM) dot += v[i] * query[i]
            out[id] = dot / 127f / length
        }
        return out
    }

    private val conceptVectors = ConcurrentHashMap<String, FloatArray>()

    /** Por cada tema de [concepts] que aparece en la biblioteca, sus fotos, de la que mejor encaja a la que menos. */
    private fun groups(items: List<MediaItem>, concepts: List<Pair<String, String>>): List<Pair<String, List<MediaItem>>> {
        load()
        if (entries.isEmpty()) return emptyList()
        val byId = items.associateBy { it.id }
        val found = ArrayList<Pair<String, List<MediaItem>>>()
        for ((word, english) in concepts) {
            val query = conceptVectors[word] ?: runCatching { embedText("a photo of $english") }.getOrNull()?.also { conceptVectors[word] = it } ?: continue
            val matches = ArrayList<Pair<MediaItem, Float>>()
            for ((id, entry) in entries) {
                val item = byId[id] ?: continue
                var dot = 0f
                val v = entry.vector
                for (i in 0 until DIM) dot += v[i] * query[i]
                val score = dot / 127f
                if (score >= TOPIC_LIKENESS) matches += item to score
            }
            if (matches.size < 2) continue
            matches.sortByDescending { it.second }
            found += word to matches.map { it.first }
        }
        return found.sortedByDescending { it.second.size }
    }

    /**
     * Los temas que de verdad aparecen en la biblioteca, con cuántas fotos y la que mejor los
     * representa. Sirve para proponer por dónde empezar a buscar sin tener que escribir.
     */
    fun topics(items: List<MediaItem>, concepts: List<Pair<String, String>>, limit: Int = 12): List<Topic> {
        val used = HashSet<Long>()
        return groups(items, concepts).take(limit).map { (word, photos) ->
            // Cada tarjeta con una foto distinta, para que no parezcan todas la misma.
            val cover = photos.firstOrNull { it.id !in used } ?: photos[0]
            used += cover.id
            Topic(word, photos.size, cover)
        }
    }

    /** Los mismos temas como álbumes automáticos, con sus fotos de más reciente a más antigua. */
    fun albums(items: List<MediaItem>, concepts: List<Pair<String, String>>): List<AutoAlbum> =
        groups(items, concepts).filter { it.second.size >= 3 }.map { (word, photos) ->
            AutoAlbum("tema:$word", word.replaceFirstChar { it.uppercase() }, "", photos.sortedByDescending { it.date }, false)
        }

    private companion object {
        /** Más exigente que la búsqueda: un tema solo se propone si las fotos encajan con claridad. */
        const val TOPIC_LIKENESS = 0.2f
        const val DIM = 512
        const val SIDE = 256
        const val LENGTH = 77
        const val START = 49406L
        const val END = 49407L
        val WORD = Regex("[a-z]+|[0-9]|[^\\sa-z0-9]+")
    }
}
