package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Size
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.java.TfLite
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive
import org.tensorflow.lite.InterpreterApi

/**
 * Una cara encontrada en una foto. [box] va en proporción de la imagen (de 0 a 1), así vale para
 * cualquier tamaño de miniatura. [print] es su huella: 192 números de longitud 1; dos caras de la
 * misma persona tienen huellas parecidas.
 */
class Face(
    val photo: Long, val n: Int, val left: Float, val top: Float, val right: Float, val bottom: Float, val print: FloatArray,
    /** Lo abiertos que están los ojos (el menos abierto de los dos) y la sonrisa, de 0 a 1; -1 si no se sabe. */
    val eyes: Float = -1f,
    val smile: Float = -1f,
) {
    /** Alguien parpadea: los dos ojos casi cerrados en una cara de buen tamaño. */
    val blinking: Boolean get() = eyes in 0f..0.25f && right - left >= 0.06f

    val key: String get() = "$photo:$n"
}

/** Lado de la miniatura en la que se buscan las caras. */
const val FACE_THUMB = 640

private const val SIDE = 112
private const val PRINT = 192

/** Lo que se sabe de las caras, en su propia base de datos: se puede borrar entera sin tocar lo demás. */
private class FaceStore(context: Context) : SQLiteOpenHelper(context, "caras.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE mirada (id INTEGER PRIMARY KEY, modified INTEGER)")
        db.execSQL("CREATE TABLE cara (photo INTEGER, n INTEGER, l REAL, t REAL, r REAL, b REAL, huella BLOB, ojos REAL, sonrisa REAL, PRIMARY KEY (photo, n))")
    }

    /** La versión 1 no guardaba ojos ni sonrisa: se empieza de cero y se vuelve a mirar. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS cara")
        db.execSQL("DROP TABLE IF EXISTS mirada")
        onCreate(db)
    }

    fun read(seen: MutableMap<Long, Long>, faces: MutableMap<Long, List<Face>>) {
        readableDatabase.rawQuery("SELECT id, modified FROM mirada", null).use { c -> while (c.moveToNext()) seen[c.getLong(0)] = c.getLong(1) }
        val found = HashMap<Long, ArrayList<Face>>()
        readableDatabase.rawQuery("SELECT photo, n, l, t, r, b, huella, ojos, sonrisa FROM cara", null).use { c ->
            while (c.moveToNext()) {
                val bytes = c.getBlob(6)
                // Se guarda en 8 bits por número: sobra precisión y ocupa cuatro veces menos.
                val print = FloatArray(PRINT) { i -> if (i < bytes.size) bytes[i] / 127f else 0f }
                found.getOrPut(c.getLong(0)) { ArrayList() } += Face(c.getLong(0), c.getInt(1), c.getFloat(2), c.getFloat(3), c.getFloat(4), c.getFloat(5), normalized(print), c.getFloat(7), c.getFloat(8))
            }
        }
        faces.putAll(found)
    }

    fun save(batch: Map<Long, Pair<Long, List<Face>>>, gone: Collection<Long>) {
        if (batch.isEmpty() && gone.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            (batch.keys + gone).forEach { id ->
                db.delete("cara", "photo = ?", arrayOf(id.toString()))
                db.delete("mirada", "id = ?", arrayOf(id.toString()))
            }
            batch.forEach { (id, pair) ->
                db.insert("mirada", null, ContentValues().apply { put("id", id); put("modified", pair.first) })
                pair.second.forEach { face ->
                    db.insert("cara", null, ContentValues().apply {
                        put("photo", face.photo)
                        put("n", face.n)
                        put("l", face.left)
                        put("t", face.top)
                        put("r", face.right)
                        put("b", face.bottom)
                        put("ojos", face.eyes)
                        put("sonrisa", face.smile)
                        put("huella", ByteArray(PRINT) { i -> (face.print[i] * 127f).toInt().coerceIn(-127, 127).toByte() })
                    })
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clear() {
        writableDatabase.delete("cara", null, null)
        writableDatabase.delete("mirada", null, null)
    }
}

private fun normalized(v: FloatArray): FloatArray {
    var sum = 0f
    for (x in v) sum += x * x
    val k = if (sum > 0f) 1f / sqrt(sum) else 0f
    for (i in v.indices) v[i] *= k
    return v
}

fun similarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    for (i in 0 until PRINT) dot += a[i] * b[i]
    return dot
}

/**
 * Busca las caras de las fotos y saca la huella de cada una. Todo en el teléfono: el detector lo
 * descarga Google Play, el modelo de huellas va dentro de la app (assets/caras.tflite, MobileFaceNet)
 * y el motor que lo ejecuta lo pone también Google Play. Si alguno aún no está, se deja para luego.
 */
class FaceFinder(private val context: Context) {
    private val store = FaceStore(context)
    private val seen = ConcurrentHashMap<Long, Long>()
    private val faces = ConcurrentHashMap<Long, List<Face>>()
    private var loaded = false

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.08f)
                .build(),
        )
    }
    private var interpreter: InterpreterApi? = null

    @Synchronized
    private fun load() {
        if (loaded) return
        loaded = true
        runCatching { store.read(seen, faces) }
    }

    /** Todas las caras encontradas hasta ahora. */
    fun snapshot(): List<Face> {
        load()
        return faces.values.flatten()
    }

    /** Fotos que ya se miraron, tengan caras o no. */
    fun lookedAt(): Int {
        load()
        return seen.size
    }

    fun clear() {
        load()
        runCatching { store.clear() }
        seen.clear()
        faces.clear()
    }

    private fun engine(): InterpreterApi? {
        interpreter?.let { return it }
        return runCatching {
            Tasks.await(TfLite.initialize(context))
            val model = context.assets.openFd("caras.tflite").use { fd ->
                fd.createInputStream().channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
            InterpreterApi.create(model, InterpreterApi.Options().setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY))
        }.getOrNull()?.also { interpreter = it }
    }

    /**
     * Mira las fotos que faltan. Ni vídeos ni capturas de pantalla. [onProgress] avisa de vez en
     * cuando para que la pantalla enseñe a las personas que ya salen.
     */
    suspend fun scan(items: List<MediaItem>, onProgress: suspend () -> Unit) {
        load()
        val photos = items.filter { !it.isVideo && !it.isScreenshot && !it.isGif }
        val alive = photos.mapTo(HashSet()) { it.id }
        val gone = seen.keys.filter { it !in alive }
        if (gone.isNotEmpty()) {
            gone.forEach { seen.remove(it); faces.remove(it) }
            runCatching { store.save(emptyMap(), gone) }
        }
        val pending = photos.filter { seen[it.id] != it.modified }
        if (pending.isEmpty()) return
        val engine = engine() ?: return
        val batch = HashMap<Long, Pair<Long, List<Face>>>()
        var last = System.currentTimeMillis()
        var failures = 0
        for (item in pending) {
            coroutineContext.ensureActive()
            Quiet.whenIdle()
            val found = look(item, engine)
            if (found == null) {
                // El detector aún no ha llegado de Google Play: se vuelve a intentar en otra pasada.
                if (++failures >= 3) break
                continue
            }
            failures = 0
            seen[item.id] = item.modified
            if (found.isEmpty()) faces.remove(item.id) else faces[item.id] = found
            batch[item.id] = item.modified to found
            if (System.currentTimeMillis() - last > 30_000) {
                runCatching { store.save(batch, emptyList()) }
                batch.clear()
                last = System.currentTimeMillis()
                onProgress()
            }
        }
        runCatching { store.save(batch, emptyList()) }
        onProgress()
    }

    /** Caras de una foto. Lista vacía si no hay ninguna; null si el detector no respondió. */
    private fun look(item: MediaItem, engine: InterpreterApi): List<Face>? {
        val thumb = runCatching { context.contentResolver.loadThumbnail(item.uri, Size(FACE_THUMB, FACE_THUMB), null) }.getOrNull()
            ?: return emptyList()
        try {
            val detected = runCatching { Tasks.await(detector.process(InputImage.fromBitmap(thumb, 0))) }.getOrNull() ?: return null
            val usable = detected.filter { face ->
                val box = face.boundingBox
                // Caras muy pequeñas o muy de perfil dan huellas poco fiables.
                box.width() >= 36 && kotlin.math.abs(face.headEulerAngleY) <= 35f &&
                    face.getLandmark(FaceLandmark.LEFT_EYE) != null && face.getLandmark(FaceLandmark.RIGHT_EYE) != null
            }.take(12)
            if (usable.isEmpty()) return emptyList()
            val crops = usable.map { align(thumb, it) }
            val prints = embed(engine, crops)
            crops.forEach { it.recycle() }
            if (prints == null) return null
            val w = thumb.width.toFloat()
            val h = thumb.height.toFloat()
            return usable.mapIndexed { i, face ->
                val box = face.boundingBox
                Face(item.id, i, (box.left / w).coerceIn(0f, 1f), (box.top / h).coerceIn(0f, 1f), (box.right / w).coerceIn(0f, 1f), (box.bottom / h).coerceIn(0f, 1f), prints[i],
                    eyes = face.leftEyeOpenProbability?.let { l -> face.rightEyeOpenProbability?.let { r -> minOf(l, r) } } ?: -1f,
                    smile = face.smilingProbability ?: -1f,
                )
            }
        } finally {
            thumb.recycle()
        }
    }

    /** La cara enderezada y recortada como la espera el modelo: ojos en horizontal y a una altura fija. */
    private fun align(source: Bitmap, face: com.google.mlkit.vision.face.Face): Bitmap {
        val left = face.getLandmark(FaceLandmark.LEFT_EYE)!!.position
        val right = face.getLandmark(FaceLandmark.RIGHT_EYE)!!.position
        // En la imagen, el ojo «izquierdo» de la persona queda a la derecha.
        val (a, b) = if (left.x > right.x) right to left else left to right
        val angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
        val eyes = kotlin.math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat().coerceAtLeast(4f)
        // En una cara alineada de 112 x 112 los ojos quedan a unos 35 px de distancia, a la altura 51.
        val k = 35.5f / eyes
        val matrix = Matrix().apply {
            postTranslate(-(a.x + b.x) / 2, -(a.y + b.y) / 2)
            postRotate(-angle)
            postScale(k, k)
            postTranslate(SIDE / 2f, 51.5f)
        }
        val out = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Huellas de [crops], de dos en dos, que es como está hecho el modelo. */
    private fun embed(engine: InterpreterApi, crops: List<Bitmap>): List<FloatArray>? = runCatching {
        val out = ArrayList<FloatArray>(crops.size)
        val pixels = IntArray(SIDE * SIDE)
        crops.chunked(2).forEach { pair ->
            val input = ByteBuffer.allocateDirect(2 * SIDE * SIDE * 3 * 4).order(ByteOrder.nativeOrder())
            for (slot in 0 until 2) {
                val crop = pair.getOrElse(slot) { pair[0] }
                crop.getPixels(pixels, 0, SIDE, 0, 0, SIDE, SIDE)
                for (p in pixels) {
                    input.putFloat(((p shr 16 and 0xFF) - 127.5f) / 128f)
                    input.putFloat(((p shr 8 and 0xFF) - 127.5f) / 128f)
                    input.putFloat(((p and 0xFF) - 127.5f) / 128f)
                }
            }
            input.rewind()
            val result = Array(2) { FloatArray(PRINT) }
            engine.run(input, result)
            out += normalized(result[0])
            if (pair.size == 2) out += normalized(result[1])
        }
        out
    }.getOrNull()
}

/** Una persona: las caras que se parecen entre sí. [name] es null hasta que el usuario se lo pone. */
class Person(val key: String, val name: String?, val faces: List<Face>, val photos: List<Long>, val centroid: FloatArray) {
    /** La cara que la representa: la más grande y más parecida al resto. */
    val cover: Face get() = faces.maxBy { (it.right - it.left) * (it.bottom - it.top) * (0.5f + similarity(it.print, centroid)) }
}

/** Una persona con nombre: su huella media cuando se la nombró. Así se reconoce aunque cambien los grupos. */
class Named(val name: String, val centroid: FloatArray, val hidden: Boolean = false)

/** Parecido mínimo para considerar dos caras de la misma persona. */
private const val SAME = 0.6f

/**
 * Junta las caras en personas. Primero cada cara se suma al grupo más parecido (o abre uno nuevo);
 * después se juntan los grupos que se parecen entre sí. Los grupos con nombre se reconocen por la
 * huella guardada; dos grupos con el mismo nombre son la misma persona. Solo salen los que
 * aparecen en al menos [minPhotos] fotos, para no llenar la lista de desconocidos.
 */
fun groupPeople(faces: List<Face>, named: List<Named>, excluded: Set<String>, minPhotos: Int = 3): List<Person> {
    class Group(val members: ArrayList<Face>, val sum: FloatArray) {
        val centroid: FloatArray get() = normalized(sum.copyOf())
    }
    val groups = ArrayList<Group>()
    val centroids = ArrayList<FloatArray>()
    for (face in faces.sortedBy { it.photo }) {
        var best = -1
        var bestScore = SAME
        for (i in groups.indices) {
            val score = similarity(face.print, centroids[i])
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        if (best < 0) {
            groups += Group(arrayListOf(face), face.print.copyOf())
            centroids += face.print
        } else {
            val g = groups[best]
            g.members += face
            for (k in 0 until PRINT) g.sum[k] += face.print[k]
            // Se recalcula de vez en cuando: hacerlo con cada cara sería muy lento.
            if (g.members.size <= 8 || g.members.size % 8 == 0) centroids[best] = g.centroid
        }
    }
    // Segunda vuelta: grupos de la misma persona que empezaron por separado. Se compara cada par
    // una sola vez y se juntan en cadena (si A se parece a B y B a C, los tres van juntos).
    val current = groups.map { it.centroid }
    val parent = IntArray(groups.size) { it }
    fun root(i: Int): Int {
        var r = i
        while (parent[r] != r) r = parent[r]
        parent[i] = r
        return r
    }
    for (i in groups.indices) for (j in i + 1 until groups.size) {
        if (similarity(current[i], current[j]) >= SAME + 0.03f) {
            val a = root(i)
            val b = root(j)
            if (a != b) parent[b] = a
        }
    }
    val joined = groups.indices.groupBy(::root).values.map { indexes ->
        val first = groups[indexes[0]]
        indexes.drop(1).forEach { other ->
            first.members += groups[other].members
            for (k in 0 until PRINT) first.sum[k] += groups[other].sum[k]
        }
        first
    }
    groups.clear()
    groups += joined

    // Nombres: cada grupo toma el de la persona guardada más parecida, si se parece bastante.
    val byName = LinkedHashMap<String, ArrayList<Group>>()
    val unnamed = ArrayList<Group>()
    val hiddenNames = named.filter { it.hidden }.map { it.name }.toSet()
    for (g in groups) {
        val c = g.centroid
        val match = named.maxByOrNull { similarity(c, it.centroid) }?.takeIf { similarity(c, it.centroid) >= SAME - 0.05f }
        if (match != null) byName.getOrPut(match.name) { ArrayList() } += g else unnamed += g
    }
    val people = ArrayList<Person>()
    byName.forEach { (name, list) ->
        if (name in hiddenNames) return@forEach
        val members = list.flatMap { it.members }.filter { "${it.photo}:$name" !in excluded }
        if (members.isEmpty()) return@forEach
        val sum = FloatArray(PRINT)
        members.forEach { f -> for (k in 0 until PRINT) sum[k] += f.print[k] }
        people += Person(name, name, members, members.map { it.photo }.distinct(), normalized(sum))
    }
    unnamed.forEach { g ->
        val photos = g.members.map { it.photo }.distinct()
        if (photos.size >= minPhotos) {
            people += Person("?" + g.members.minOf { it.key }, null, g.members, photos, g.centroid)
        }
    }
    // Con nombre primero; después, quien más sale.
    return people.sortedWith(compareByDescending<Person> { it.name != null }.thenByDescending { it.photos.size })
}

/** Guarda y lee las personas con nombre: una por línea, nombre, si está oculta y su huella media. */
object PeopleNames {
    fun read(context: Context): List<Named> = runCatching {
        val file = java.io.File(context.filesDir, "personas.tsv")
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size != 3) return@mapNotNull null
            val bytes = android.util.Base64.decode(p[2], android.util.Base64.NO_WRAP)
            val print = FloatArray(PRINT) { i -> java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat(i * 4) }
            Named(p[0], print, p[1] == "1")
        }
    }.getOrDefault(emptyList())

    fun write(context: Context, people: List<Named>) {
        val text = people.joinToString("") { person ->
            val buffer = ByteBuffer.allocate(PRINT * 4).order(ByteOrder.LITTLE_ENDIAN)
            person.centroid.forEach { buffer.putFloat(it) }
            "${person.name.replace('\t', ' ')}\t${if (person.hidden) 1 else 0}\t${android.util.Base64.encodeToString(buffer.array(), android.util.Base64.NO_WRAP)}\n"
        }
        java.io.File(context.filesDir, "personas.tsv").writeText(text)
    }
}
