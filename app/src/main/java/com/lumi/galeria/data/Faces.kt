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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi

/**
 * Una cara encontrada en una foto. La caja va en proporción de la imagen (de 0 a 1), así vale para
 * cualquier tamaño. [print] es su huella: 192 números de longitud 1; dos caras de la misma persona
 * tienen huellas parecidas. [group] es la persona a la que pertenece (-1 si aún no tiene).
 */
class Face(
    val photo: Long, val n: Int, val left: Float, val top: Float, val right: Float, val bottom: Float, val print: FloatArray,
    /** Lo abiertos que están los ojos (el menos abierto de los dos) y la sonrisa, de 0 a 1; -1 si no se sabe. */
    val eyes: Float = -1f,
    val smile: Float = -1f,
    var group: Long = -1,
    /** Personas a las que el usuario dijo que esta cara no pertenece. */
    var refused: Set<Long> = emptySet(),
    /** El usuario confirmó a quién pertenece: no se vuelve a preguntar. */
    var sure: Boolean = false,
) {
    /** Alguien parpadea: los dos ojos casi cerrados en una cara de buen tamaño. */
    val blinking: Boolean get() = eyes in 0f..0.25f && right - left >= 0.06f

    val key: String get() = "$photo:$n"
}

/** Una persona guardada: su nombre y lo que el usuario eligió para ella. */
class Group(
    val id: Long,
    var name: String? = null,
    var hidden: Boolean = false,
    var pinned: Boolean = false,
    /** Cara elegida para su círculo («foto:n»); null para que la elija Lumi. */
    var cover: String? = null,
) {
    /** Suma de las huellas de sus caras: normalizada, es la huella media de la persona. */
    val sum = FloatArray(PRINT)
    var count = 0
    fun centroid(): FloatArray = normalized(sum.copyOf())
}

/** Lado de la miniatura en la que se buscan las caras. */
const val FACE_THUMB = 640

private const val SIDE = 112
private const val PRINT = 192

/** Parecido mínimo para considerar dos caras de la misma persona. */
private const val SAME = 0.6f

/** Parecido a partir del cual una cara suelta es, sin ninguna duda, de una persona con nombre: va con ella sin preguntar. */
private const val CERTAIN = 0.72f

/** Etiquetas del análisis que dicen que en la foto sale gente: esas se miran primero. */
private val PEOPLE_LABELS = setOf(
    "Person", "Human", "Smile", "Selfie", "Dude", "Youth", "Team", "Crowd", "Child", "Baby", "Grandparent", "Bride", "Groom",
    "Beard", "Hair", "Eyelash", "Skin", "Muscle", "Tattoo", "Event", "Party", "Fun", "Wedding", "Gown", "Suit", "Sitting", "Standing",
)

/** Lo que se sabe de las caras, en su propia base de datos: se puede borrar entera sin tocar lo demás. */
private class FaceStore(context: Context) : SQLiteOpenHelper(context, "caras.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE mirada (id INTEGER PRIMARY KEY, modified INTEGER)")
        db.execSQL(
            "CREATE TABLE cara (photo INTEGER, n INTEGER, l REAL, t REAL, r REAL, b REAL, huella BLOB, ojos REAL, sonrisa REAL, " +
                "grupo INTEGER DEFAULT -1, fuera TEXT DEFAULT '', seguro INTEGER DEFAULT 0, PRIMARY KEY (photo, n))",
        )
        createGroups(db)
    }

    private fun createGroups(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS grupo (id INTEGER PRIMARY KEY, nombre TEXT, oculto INTEGER DEFAULT 0, fijado INTEGER DEFAULT 0, portada TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS no_juntar (a INTEGER, b INTEGER, PRIMARY KEY (a, b))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // La versión 1 no guardaba ojos ni sonrisa: se empieza de cero y se vuelve a mirar.
            db.execSQL("DROP TABLE IF EXISTS cara")
            db.execSQL("DROP TABLE IF EXISTS mirada")
            onCreate(db)
            return
        }
        // Versión 3: cada cara guarda su persona. Lo ya mirado se conserva y se reparte al abrir.
        db.execSQL("ALTER TABLE cara ADD COLUMN grupo INTEGER DEFAULT -1")
        db.execSQL("ALTER TABLE cara ADD COLUMN fuera TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE cara ADD COLUMN seguro INTEGER DEFAULT 0")
        createGroups(db)
    }

    fun read(seen: MutableMap<Long, Long>, faces: MutableMap<Long, List<Face>>, groups: MutableMap<Long, Group>, apart: MutableSet<Pair<Long, Long>>) {
        val db = readableDatabase
        db.rawQuery("SELECT id, modified FROM mirada", null).use { c -> while (c.moveToNext()) seen[c.getLong(0)] = c.getLong(1) }
        db.rawQuery("SELECT id, nombre, oculto, fijado, portada FROM grupo", null).use { c ->
            while (c.moveToNext()) {
                groups[c.getLong(0)] = Group(c.getLong(0), c.getString(1), c.getInt(2) == 1, c.getInt(3) == 1, c.getString(4))
            }
        }
        db.rawQuery("SELECT a, b FROM no_juntar", null).use { c -> while (c.moveToNext()) apart += c.getLong(0) to c.getLong(1) }
        val found = HashMap<Long, ArrayList<Face>>()
        db.rawQuery("SELECT photo, n, l, t, r, b, huella, ojos, sonrisa, grupo, fuera, seguro FROM cara", null).use { c ->
            while (c.moveToNext()) {
                val bytes = c.getBlob(6)
                // Se guarda en 8 bits por número: sobra precisión y ocupa cuatro veces menos.
                val print = FloatArray(PRINT) { i -> if (i < bytes.size) bytes[i] / 127f else 0f }
                val refused = c.getString(10).orEmpty().split(',').mapNotNullTo(HashSet()) { it.toLongOrNull() }
                found.getOrPut(c.getLong(0)) { ArrayList() } += Face(
                    c.getLong(0), c.getInt(1), c.getFloat(2), c.getFloat(3), c.getFloat(4), c.getFloat(5), normalized(print),
                    c.getFloat(7), c.getFloat(8), c.getLong(9), refused, c.getInt(11) == 1,
                )
            }
        }
        faces.putAll(found)
    }

    /** Anota una foto ya mirada con sus caras, de una vez: si se corta, no queda nada a medias. */
    fun savePhoto(id: Long, modified: Long, found: List<Face>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cara", "photo = ?", arrayOf(id.toString()))
            db.insertWithOnConflict("mirada", null, ContentValues().apply { put("id", id); put("modified", modified) }, SQLiteDatabase.CONFLICT_REPLACE)
            found.forEach { insertFace(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertFace(db: SQLiteDatabase, face: Face) {
        db.insertWithOnConflict("cara", null, ContentValues().apply {
            put("photo", face.photo)
            put("n", face.n)
            put("l", face.left)
            put("t", face.top)
            put("r", face.right)
            put("b", face.bottom)
            put("ojos", face.eyes)
            put("sonrisa", face.smile)
            put("grupo", face.group)
            put("fuera", face.refused.joinToString(","))
            put("seguro", if (face.sure) 1 else 0)
            put("huella", ByteArray(PRINT) { i -> (face.print[i] * 127f).toInt().coerceIn(-127, 127).toByte() })
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Guarda a qué persona va cada cara de [changed]. */
    fun saveFaces(changed: Collection<Face>) {
        if (changed.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            changed.forEach { face ->
                db.update("cara", ContentValues().apply {
                    put("grupo", face.group)
                    put("fuera", face.refused.joinToString(","))
                    put("seguro", if (face.sure) 1 else 0)
                }, "photo = ? AND n = ?", arrayOf(face.photo.toString(), face.n.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun saveGroup(group: Group) {
        writableDatabase.insertWithOnConflict("grupo", null, ContentValues().apply {
            put("id", group.id)
            put("nombre", group.name)
            put("oculto", if (group.hidden) 1 else 0)
            put("fijado", if (group.pinned) 1 else 0)
            put("portada", group.cover)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteGroups(ids: Collection<Long>) {
        ids.forEach { writableDatabase.delete("grupo", "id = ?", arrayOf(it.toString())) }
    }

    fun keepApart(a: Long, b: Long) {
        writableDatabase.insertWithOnConflict("no_juntar", null, ContentValues().apply { put("a", minOf(a, b)); put("b", maxOf(a, b)) }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun forget(photos: Collection<Long>) {
        if (photos.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            photos.forEach { id ->
                db.delete("cara", "photo = ?", arrayOf(id.toString()))
                db.delete("mirada", "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clear() {
        listOf("cara", "mirada", "grupo", "no_juntar").forEach { writableDatabase.delete(it, null, null) }
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

/** Una persona tal como se enseña: su grupo, sus caras y sus fotos. */
class Person(val group: Group, val faces: List<Face>, val photos: List<Long>, val centroid: FloatArray, private val avoid: Set<Long> = emptySet()) {
    val key: String get() = "g${group.id}"
    val name: String? get() = group.name
    val pinned: Boolean get() = group.pinned

    /**
     * La cara de su círculo: la elegida o, si no, una grande, nítida y parecida al resto, mejor
     * sonriendo y con los ojos abiertos, y nunca la de un documento o una captura.
     */
    val cover: Face by lazy(LazyThreadSafetyMode.PUBLICATION) {
        group.cover?.let { wanted -> faces.firstOrNull { it.key == wanted } }
            ?: (faces.filter { it.photo !in avoid }.ifEmpty { faces }).maxBy { face ->
                val area = (face.right - face.left) * (face.bottom - face.top)
                val mood = (if (face.smile >= 0f) 0.8f + face.smile * 0.4f else 1f) * (if (face.eyes in 0f..0.3f) 0.5f else 1f)
                area * (0.5f + similarity(face.print, centroid)) * mood
            }
    }
}

/** Una cara que Lumi no tiene clara: «¿Es [person]?». [inside]: ya está con ella y se duda de que deba estar. */
class Doubt(val face: Face, val person: Person, val inside: Boolean)

/**
 * Busca las caras de las fotos, saca la huella de cada una y las reparte en personas. Todo en el
 * teléfono: el detector lo descarga Google Play, el modelo de huellas va dentro de la app
 * (assets/caras.tflite, MobileFaceNet) y el motor que lo ejecuta lo pone también Google Play.
 *
 * Las personas se guardan: una cara nueva se suma a la persona que más se parece, o abre otra.
 * Así no cambian de un día para otro ni hay que agruparlo todo cada vez.
 */
class FaceFinder(private val context: Context) {
    private val store = FaceStore(context)
    private val seen = ConcurrentHashMap<Long, Long>()
    private val faces = ConcurrentHashMap<Long, List<Face>>()
    private val groups = ConcurrentHashMap<Long, Group>()
    /** Parejas de personas que el usuario dijo que no son la misma. */
    private val apart = java.util.Collections.newSetFromMap(ConcurrentHashMap<Pair<Long, Long>, Boolean>())
    private var loaded = false
    private var nextId = 1L

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
        runCatching { store.read(seen, faces, groups, apart) }
        nextId = (groups.keys.maxOrNull() ?: 0L) + 1
        faces.values.flatten().forEach { face -> groups[face.group]?.let { add(it, face) } }
        // Lo mirado por la versión anterior no tenía persona: se reparte ahora, una vez.
        val loose = faces.values.flatten().filter { !groups.containsKey(it.group) }
        if (loose.isNotEmpty()) {
            loose.sortedBy { it.photo }.forEach { place(it) }
            consolidate()
            runCatching { store.saveFaces(loose) }
            migrateNames()
        }
    }

    private fun add(group: Group, face: Face) {
        for (i in 0 until PRINT) group.sum[i] += face.print[i]
        group.count++
    }

    private fun remove(group: Group, face: Face) {
        for (i in 0 until PRINT) group.sum[i] -= face.print[i]
        group.count--
    }

    private fun newGroup(): Group = Group(nextId++).also {
        groups[it.id] = it
        runCatching { store.saveGroup(it) }
    }

    /** Pone [face] con la persona más parecida (que no haya rechazado) o le abre una nueva. */
    @Synchronized
    private fun place(face: Face) {
        var best: Group? = null
        var bestScore = SAME
        for (g in groups.values) {
            if (g.count == 0 || g.id in face.refused) continue
            val score = similarity(face.print, g.centroid())
            if (score > bestScore) {
                bestScore = score
                best = g
            }
        }
        val target = best ?: newGroup()
        face.group = target.id
        add(target, face)
    }

    /**
     * Junta personas que claramente son la misma (a veces una persona empieza en dos grupos).
     * Nunca junta dos con nombres distintos ni las que el usuario separó.
     */
    @Synchronized
    fun consolidate() {
        val live = groups.values.filter { it.count > 0 && !it.hidden }
        val centroids = live.associate { it.id to it.centroid() }
        val parent = HashMap<Long, Long>()
        fun root(id: Long): Long {
            var r = id
            while (parent[r] != null && parent[r] != r) r = parent[r]!!
            return r
        }
        for (i in live.indices) for (j in i + 1 until live.size) {
            val a = live[i]
            val b = live[j]
            if (a.name != null && b.name != null && a.name != b.name) continue
            if ((minOf(a.id, b.id) to maxOf(a.id, b.id)) in apart) continue
            if (similarity(centroids.getValue(a.id), centroids.getValue(b.id)) < SAME + 0.03f) continue
            val ra = root(a.id)
            val rb = root(b.id)
            if (ra != rb) parent[rb] = ra
        }
        val changed = ArrayList<Face>()
        for (g in live) {
            val r = root(g.id)
            if (r != g.id) changed += moveAll(g.id, r)
        }
        // Caras sueltas casi idénticas a alguien con nombre: van con esa persona sin preguntar.
        val named = groups.values.filter { it.name != null && !it.hidden && it.count > 0 }.associateWith { it.centroid() }
        if (named.isNotEmpty()) {
            for (face in faces.values.flatten()) {
                val current = groups[face.group]
                if (current?.name != null) continue
                val (best, centroid) = named.maxBy { similarity(face.print, it.value) }
                if (best.id in face.refused || similarity(face.print, centroid) < CERTAIN) continue
                current?.let { remove(it, face) }
                face.group = best.id
                add(best, face)
                changed += face
            }
        }
        runCatching { store.saveFaces(changed) }
    }

    /** Pasa todas las caras de [from] a [to] y borra [from]. Devuelve las caras movidas. */
    private fun moveAll(from: Long, to: Long): List<Face> {
        val source = groups[from] ?: return emptyList()
        val target = groups[to] ?: return emptyList()
        val moved = faces.values.flatten().filter { it.group == from }
        moved.forEach { face ->
            face.group = to
            add(target, face)
        }
        if (target.name == null) target.name = source.name
        target.pinned = target.pinned || source.pinned
        groups.remove(from)
        runCatching {
            store.deleteGroups(listOf(from))
            store.saveGroup(target)
        }
        return moved
    }

    /** Los nombres que se pusieron con la versión anterior (personas.tsv) pasan a sus personas. */
    private fun migrateNames() {
        val file = java.io.File(context.filesDir, "personas.tsv")
        if (!file.exists()) return
        runCatching {
            file.readLines().forEach { line ->
                val p = line.split('\t')
                if (p.size != 3) return@forEach
                val bytes = android.util.Base64.decode(p[2], android.util.Base64.NO_WRAP)
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val centroid = FloatArray(PRINT) { buffer.getFloat(it * 4) }
                val match = groups.values.filter { it.count > 0 }.maxByOrNull { similarity(it.centroid(), centroid) }
                if (match != null && similarity(match.centroid(), centroid) >= SAME - 0.05f) {
                    if (!p[0].startsWith("~")) match.name = p[0]
                    match.hidden = p[1] == "1"
                    store.saveGroup(match)
                }
            }
        }
        file.delete()
    }

    /** Todas las caras encontradas hasta ahora. */
    fun snapshot(): List<Face> {
        load()
        return faces.values.flatten()
    }

    fun facesOf(photo: Long): List<Face> {
        load()
        return faces[photo].orEmpty()
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
        groups.clear()
        apart.clear()
    }

    /**
     * Las personas: las que tienen nombre y las que salen en al menos [minPhotos] fotos. Fuera las
     * ocultas y las fotos que ya no se ven ([alive]).
     */
    fun people(alive: Set<Long>, minPhotos: Int = 3, avoid: Set<Long> = emptySet()): List<Person> {
        load()
        val byGroup = faces.values.flatten().filter { it.photo in alive }.groupBy { it.group }
        return byGroup.mapNotNull { (id, list) ->
            val group = groups[id] ?: return@mapNotNull null
            if (group.hidden) return@mapNotNull null
            val photos = list.map { it.photo }.distinct()
            if (group.name == null && photos.size < minPhotos) return@mapNotNull null
            Person(group, list, photos, group.centroid(), avoid)
        }
    }

    fun hiddenCount(): Int = groups.values.count { it.hidden && it.count > 0 }

    /** Parejas de personas que se parecen sin llegar a ser seguro que son la misma. */
    fun mergeSuggestions(people: List<Person>): List<Pair<Person, Person>> {
        val out = ArrayList<Triple<Person, Person, Float>>()
        for (i in people.indices) for (j in i + 1 until people.size) {
            val a = people[i]
            val b = people[j]
            if (a.name != null && b.name != null) continue
            if ((minOf(a.group.id, b.group.id) to maxOf(a.group.id, b.group.id)) in apart) continue
            val score = similarity(a.centroid, b.centroid)
            if (score >= 0.45f) out += Triple(a, b, score)
        }
        return out.sortedByDescending { it.third }.take(20).map { it.first to it.second }
    }

    /**
     * Caras dudosas de [person]: las suyas que se parecen poco al resto y las de otros grupos
     * sin nombre que se le parecen bastante. Las ya confirmadas no salen.
     */
    fun doubts(person: Person): List<Doubt> {
        load()
        val inside = person.faces.filter { !it.sure && similarity(it.print, person.centroid) < 0.66f }.map { Doubt(it, person, true) }
        // Las que se parecen muchísimo ya se asignan solas: aquí solo las dudosas de verdad.
        val outside = faces.values.flatten().filter {
            it.group != person.group.id && person.group.id !in it.refused && groups[it.group]?.name == null &&
                similarity(it.print, person.centroid) in 0.5f..CERTAIN
        }.sortedByDescending { similarity(it.print, person.centroid) }.take(30).map { Doubt(it, person, false) }
        return (inside + outside).take(40)
    }

    // ---------- Lo que decide el usuario ----------

    @Synchronized
    fun rename(groupId: Long, name: String?) {
        val g = groups[groupId] ?: return
        g.name = name
        runCatching { store.saveGroup(g) }
    }

    /** Junta [from] dentro de [into]. */
    @Synchronized
    fun merge(into: Long, from: Long) {
        if (into == from) return
        runCatching { store.saveFaces(moveAll(from, into)) }
    }

    @Synchronized
    fun keepApart(a: Long, b: Long) {
        apart += minOf(a, b) to maxOf(a, b)
        runCatching { store.keepApart(a, b) }
    }

    @Synchronized
    fun setHidden(groupId: Long, hidden: Boolean) {
        val g = groups[groupId] ?: return
        g.hidden = hidden
        runCatching { store.saveGroup(g) }
    }

    @Synchronized
    fun showAllHidden() {
        groups.values.filter { it.hidden }.forEach {
            it.hidden = false
            runCatching { store.saveGroup(it) }
        }
    }

    @Synchronized
    fun setPinned(groupId: Long, pinned: Boolean) {
        val g = groups[groupId] ?: return
        g.pinned = pinned
        runCatching { store.saveGroup(g) }
    }

    @Synchronized
    fun setCover(groupId: Long, face: String?) {
        val g = groups[groupId] ?: return
        g.cover = face
        runCatching { store.saveGroup(g) }
    }

    /** La cara [face] es de [groupId] (lo confirma el usuario). */
    @Synchronized
    fun confirm(face: Face, groupId: Long) {
        val target = groups[groupId] ?: return
        if (face.group != groupId) {
            groups[face.group]?.let { remove(it, face) }
            face.group = groupId
            add(target, face)
        }
        face.refused = face.refused - groupId
        face.sure = true
        runCatching { store.saveFaces(listOf(face)) }
    }

    /** La cara [face] no es de [groupId]: va a la siguiente persona más parecida, o a una nueva. */
    @Synchronized
    fun reject(face: Face, groupId: Long) {
        face.refused = face.refused + groupId
        if (face.group == groupId) {
            groups[groupId]?.let { remove(it, face) }
            place(face)
        }
        face.sure = false
        runCatching { store.saveFaces(listOf(face)) }
    }

    /** Las fotos [photos] no son de [groupId]: cada cara suya en ellas se va. */
    fun rejectPhotos(groupId: Long, photos: Collection<Long>) {
        photos.forEach { id -> faces[id].orEmpty().filter { it.group == groupId }.forEach { reject(it, groupId) } }
    }

    /** Una persona nueva con solo [face] y el nombre [name]. */
    @Synchronized
    fun startPerson(face: Face, name: String): Long {
        groups[face.group]?.let { remove(it, face) }
        val g = newGroup()
        g.name = name
        runCatching { store.saveGroup(g) }
        face.group = g.id
        face.sure = true
        add(g, face)
        runCatching { store.saveFaces(listOf(face)) }
        return g.id
    }

    // ---------- Buscar caras ----------

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

    /** Fotos que aún faltan por mirar: primero las que tienen gente según el análisis; las demás, después. */
    fun pending(items: List<MediaItem>, index: Map<Long, IndexEntry>): Pair<List<MediaItem>, List<MediaItem>> {
        load()
        val photos = items.filter { !it.isVideo && !it.isScreenshot && !it.isGif && seen[it.id] != it.modified }
        val (people, rest) = photos.partition { item -> index[item.id]?.labels?.any { it in PEOPLE_LABELS } == true }
        // Las más recientes primero: son las que más se miran.
        return people.sortedByDescending { it.date } to rest.sortedByDescending { it.date }
    }

    /** Fotos con gente según el análisis: es el total que se enseña mientras se buscan caras. */
    fun withPeople(items: List<MediaItem>, index: Map<Long, IndexEntry>): Int =
        items.count { item -> !item.isVideo && !item.isScreenshot && !item.isGif && index[item.id]?.labels?.any { it in PEOPLE_LABELS } == true }

    /** De las fotos con gente, cuántas están ya miradas. */
    fun lookedWithPeople(items: List<MediaItem>, index: Map<Long, IndexEntry>): Int {
        load()
        return items.count { item ->
            !item.isVideo && !item.isScreenshot && !item.isGif && seen[item.id] == item.modified &&
                index[item.id]?.labels?.any { it in PEOPLE_LABELS } == true
        }
    }

    /** Quita lo de las fotos que ya no existen. */
    fun prune(items: List<MediaItem>) {
        load()
        val alive = items.mapTo(HashSet()) { it.id }
        val gone = seen.keys.filter { it !in alive }
        if (gone.isEmpty()) return
        gone.forEach { id ->
            seen.remove(id)
            faces.remove(id)?.forEach { face -> groups[face.group]?.let { remove(it, face) } }
        }
        runCatching { store.forget(gone) }
    }

    /**
     * Mira [photos] de dos en dos. Cada foto se guarda en cuanto se termina, así que si se corta
     * (se cierra la app, cambia la biblioteca) se sigue después por la siguiente.
     * [onProgress] avisa de vez en cuando para que la pantalla enseñe a las personas que ya salen.
     */
    suspend fun scan(photos: List<MediaItem>, active: () -> Boolean = { true }, onProgress: suspend () -> Unit) {
        load()
        if (photos.isEmpty()) return
        val engine = engine() ?: return
        var last = System.currentTimeMillis()
        var failures = 0
        for (pair in photos.chunked(2)) {
            coroutineContext.ensureActive()
            // En pausa: lo mirado hasta aquí ya está guardado.
            if (!active()) break
            Quiet.whenIdle()
            // Detectar las caras es lo lento y se puede hacer a la vez; la huella va de una en una.
            val looked = coroutineScope { pair.map { item -> async(Dispatchers.Default) { item to detect(item) } }.awaitAll() }
            for ((item, detected) in looked) {
                if (detected == null) {
                    // El detector aún no ha llegado de Google Play: se vuelve a intentar en otra pasada.
                    failures++
                    continue
                }
                failures = 0
                val found = withContext(Dispatchers.Default) { finish(item, detected, engine) } ?: continue
                synchronized(this) {
                    faces[item.id]?.forEach { old -> groups[old.group]?.let { remove(it, old) } }
                    found.forEach { place(it) }
                    seen[item.id] = item.modified
                    if (found.isEmpty()) faces.remove(item.id) else faces[item.id] = found
                }
                runCatching { store.savePhoto(item.id, item.modified, found) }
            }
            if (failures >= 4) break
            if (System.currentTimeMillis() - last > 8000) {
                last = System.currentTimeMillis()
                onProgress()
            }
        }
        consolidate()
        onProgress()
    }

    /** Lo que encontró el detector en una foto, con la miniatura donde se encontró. */
    private class Detected(val thumb: Bitmap?, val faces: List<com.google.mlkit.vision.face.Face>)

    /** Caras de una foto. Sin caras si no hay ninguna; null si el detector no respondió. */
    private fun detect(item: MediaItem): Detected? {
        val thumb = runCatching { context.contentResolver.loadThumbnail(item.uri, Size(FACE_THUMB, FACE_THUMB), null) }.getOrNull()
            ?: return Detected(null, emptyList())
        val detected = runCatching { Tasks.await(detector.process(InputImage.fromBitmap(thumb, 0))) }.getOrNull()
        if (detected == null) {
            thumb.recycle()
            return null
        }
        val usable = detected.filter { face ->
            val box = face.boundingBox
            // Caras muy pequeñas o muy de perfil dan huellas poco fiables.
            box.width() >= 36 && kotlin.math.abs(face.headEulerAngleY) <= 35f &&
                face.getLandmark(FaceLandmark.LEFT_EYE) != null && face.getLandmark(FaceLandmark.RIGHT_EYE) != null
        }.take(12)
        return Detected(thumb, usable)
    }

    /** Huella de cada cara encontrada. null si el motor de huellas falló. */
    private fun finish(item: MediaItem, detected: Detected, engine: InterpreterApi): List<Face>? {
        val thumb = detected.thumb ?: return emptyList()
        try {
            if (detected.faces.isEmpty()) return emptyList()
            val crops = detected.faces.map { align(thumb, it) }
            val prints = synchronized(engine) { embed(engine, crops) }
            crops.forEach { it.recycle() }
            if (prints == null) return null
            val w = thumb.width.toFloat()
            val h = thumb.height.toFloat()
            return detected.faces.mapIndexed { i, face ->
                val box = face.boundingBox
                Face(
                    item.id, i, (box.left / w).coerceIn(0f, 1f), (box.top / h).coerceIn(0f, 1f), (box.right / w).coerceIn(0f, 1f), (box.bottom / h).coerceIn(0f, 1f), prints[i],
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
