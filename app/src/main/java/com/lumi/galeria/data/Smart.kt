package com.lumi.galeria.data

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ---------- Búsqueda ----------

private val STOP = setOf(
    "de", "del", "en", "la", "el", "los", "las", "un", "una", "con", "y", "al", "a", "mi", "mis", "que", "por",
    "the", "of", "in", "on", "at", "an", "and", "with", "my", "from",
)

/** Palabras que acotan una búsqueda, escritas en inglés -> la que entiende el buscador. */
private val FROM_ENGLISH = mapOf(
    "january" to "enero", "february" to "febrero", "march" to "marzo", "april" to "abril", "may" to "mayo", "june" to "junio",
    "july" to "julio", "august" to "agosto", "september" to "septiembre", "october" to "octubre", "november" to "noviembre",
    "december" to "diciembre", "today" to "hoy", "yesterday" to "ayer", "text" to "texto",
    "photo" to "foto", "photos" to "foto", "picture" to "foto", "pictures" to "foto",
    "favorite" to "favorita", "favorites" to "favorita", "favourite" to "favorita", "favourites" to "favorita",
    "screenshot" to "captura", "screenshots" to "captura",
    "selfies" to "selfie", "portrait" to "vertical", "landscape" to "horizontal",
)
private val MONTHS = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)
private val ACCENTS = Regex("\\p{Mn}+")
private val NOT_WORD = Regex("[^a-z0-9]+")

/** Minúsculas y sin tildes, para que "montaña" y "montana" o "Cámara" y "camara" sean lo mismo. */
fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(ACCENTS, "")

/** "coches" encuentra "coche" y "flores" encuentra "flor", sin que "mar" encuentre "marco". */
private fun sameWord(a: String, b: String): Boolean {
    if (a == b) return true
    val short = if (a.length <= b.length) a else b
    val long = if (a.length <= b.length) b else a
    return short.length >= 4 && long.length - short.length <= 2 && long.startsWith(short)
}

/** Cosas del modelo a las que se refiere una palabra escrita por el usuario. */
private fun labelsFor(token: String): Set<String> {
    val exact = PLAIN_WORDS.filter { (label, words) -> normalize(label) == token || words.any { sameWord(it, token) } }.keys
    return exact + broadFor(token)
}

/** Familias con las que se aproxima una palabra que el modelo no conoce. */
private fun broadFor(token: String): List<String> =
    BROAD[token] ?: BROAD.entries.firstOrNull { sameWord(it.key, token) }?.value.orEmpty()

private fun knows(token: String): Boolean =
    PLAIN_WORDS.any { (label, words) -> normalize(label) == token || words.any { sameWord(it, token) } }

/** Palabras de la búsqueda que Lumi solo puede aproximar, para decírselo al usuario. */
fun approximated(query: String): List<String> =
    normalize(query).split(NOT_WORD).filter { it.length >= 2 && it !in STOP && !knows(it) && broadFor(it).isNotEmpty() }

/** Palabras que Lumi sí entiende y se parecen a lo escrito, para proponerlas cuando no sale nada. */
fun similarWords(query: String, limit: Int = 8): List<String> {
    val tokens = normalize(query).split(NOT_WORD).filter { it.length >= 3 && it !in STOP }
    if (tokens.isEmpty()) return emptyList()
    val known = WORDS.values.flatten().distinct()
    return known.filter { word ->
        val plain = normalize(word)
        tokens.any { token -> plain.startsWith(token.take(3)) || (token.length >= 4 && plain.contains(token.take(4))) }
    }.take(limit)
}

/** Algo por lo que se puede buscar con un toque: una cosa o un lugar que aparece en la biblioteca. */
class Topic(val word: String, val count: Int, val cover: MediaItem)

/** Cosas que el modelo dice de casi cualquier foto y que no ayudan a encontrar nada. */
private val VAGUE = setOf(
    "Vacation", "Fun", "Flesh", "Skin", "Cool", "Leisure", "Event", "Product", "Standing", "Sitting", "Interaction",
    "Pattern", "Monochrome", "Community", "Competition", "Model", "Human", "Dude", "Youth", "Love", "Musical", "Metal",
    "Space", "Class", "Icon", "Net", "Flap", "Strap", "Sky", "Plant", "Flora", "Hair", "Hand", "Ear", "Mouth", "Muscle",
    "Fur", "Textile", "Wall", "Room", "Tile", "Roof", "Soil", "Wing", "Wheel", "Steaming", "Balance", "Aggression",
)

/** Las cosas más frecuentes en las fotos, con una foto de muestra, para explorar sin escribir. */
fun topThings(items: List<MediaItem>, index: Map<Long, IndexEntry>, limit: Int = 12): List<Topic> {
    val groups = LinkedHashMap<String, ArrayList<MediaItem>>()
    for (item in items) {
        val labels = index[item.id]?.labels ?: continue
        // Varias etiquetas pueden llevar a la misma palabra; cada foto cuenta una vez por palabra.
        labels.filter { it !in VAGUE }.mapNotNullTo(HashSet()) { WORDS[it]?.firstOrNull() }
            .forEach { word -> groups.getOrPut(word) { ArrayList() } += item }
    }
    val used = HashSet<Long>()
    return groups.entries.filter { it.value.size >= 2 }.sortedByDescending { it.value.size }.take(limit).map { (word, found) ->
        // Cada tarjeta con una foto distinta, para que no parezcan todas la misma.
        val cover = found.firstOrNull { it.id !in used } ?: found.first()
        used += cover.id
        Topic(word, found.size, cover)
    }
}

/** Ciudades cerca de las que hay fotos. */
fun topPlaces(items: List<MediaItem>, names: Map<Long, String>, limit: Int = 8): List<Topic> {
    val groups = HashMap<String, ArrayList<MediaItem>>()
    for (item in items) groups.getOrPut(names[item.id] ?: continue) { ArrayList() } += item
    return groups.entries.sortedByDescending { it.value.size }.take(limit).map { Topic(it.key, it.value.size, it.value.first()) }
}

/** Palabras que no describen la foto sino que acotan: fechas y tipos de archivo. */
private fun isFilterWord(token: String): Boolean =
    token in MONTHS || (token.length == 4 && token.all(Char::isDigit) && token.toInt() in 1990..2100) ||
        token == "hoy" || token == "ayer" || token == "texto" ||
        sameWord(token, "video") || sameWord(token, "foto") || sameWord(token, "favorita") || sameWord(token, "captura") ||
        sameWord(token, "selfie") || token == "flash" || sameWord(token, "vertical") || sameWord(token, "horizontal") ||
        token == "verticales" || token == "horizontales"

/**
 * Busca por lo que se ve en la foto, por el texto que contiene, por lugar ("madrid"), por fecha
 * ("agosto", "2025", "ayer"), por álbum, por nombre de archivo y por tipo ("vídeos", "capturas",
 * "favoritas", "texto"). Todas las palabras tienen que cumplirse.
 */
fun search(
    query: String,
    items: List<MediaItem>,
    index: Map<Long, IndexEntry>,
    favorites: Set<Long>,
    people: Map<Long, List<String>> = emptyMap(),
): List<MediaItem> {
    var plain = " " + normalize(query).replace(NOT_WORD, " ").trim() + " "
    if (plain.isBlank()) return emptyList()

    // Los nombres de ciudad pueden tener varias palabras: se buscan primero y se quitan de la frase.
    val cities = ArrayList<City>()
    val placed by lazy(LazyThreadSafetyMode.NONE) { index.values.filter { it.hasPlace } }
    for (city in CITIES) {
        if (cities.size >= 2) break
        val name = " " + normalize(city.name) + " "
        // Muchas ciudades se llaman como cosas corrientes (León, Victoria): solo cuentan como
        // lugar si de verdad hay fotos hechas allí.
        if (plain.contains(name) && placed.any { km(it.lat.toDouble(), it.lon.toDouble(), city.lat, city.lon) <= NEAR_CITY_KM }) {
            cities += city
            plain = plain.replace(name, " ")
        }
    }
    val tokens = plain.trim().split(' ').filter { it.length >= 2 && it !in STOP }.map { FROM_ENGLISH[it] ?: it }
    if (tokens.isEmpty() && cities.isEmpty()) return emptyList()
    val filters = tokens.filter(::isFilterWord)
    val words = tokens - filters.toSet()

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val labelSets = words.associateWith(::labelsFor)
    val albumNames = HashMap<Long, String>()

    fun literal(item: MediaItem, entry: IndexEntry?, token: String): Boolean {
        val wanted = labelSets.getValue(token)
        return entry?.plainText?.contains(token) == true ||
            (wanted.isNotEmpty() && entry?.labels?.any { it in wanted } == true) ||
            // Carpeta, ruta y nombre del archivo: así se llega a lo que viene de una app
            // ("tiktok", "whatsapp", "instagram") aunque se escriba en plural.
            albumNames.getOrPut(item.bucketId) { normalize(albumName(item.bucket) + " " + item.path) }.let { where ->
                where.contains(token) || (token.length >= 5 && token.endsWith("s") && where.contains(token.dropLast(1)))
            } ||
            // Quien sale en la foto, por su nombre.
            people[item.id]?.any { normalize(it).split(' ').any { part -> part == token || (token.length >= 4 && part.startsWith(token)) } } == true ||
            // El móvil o la cámara: «pixel», «canon», «iphone».
            (token.length >= 3 && entry?.camera?.let { normalize(it).contains(token) } == true) ||
            (token.length >= 3 && item.name.lowercase(Locale.ROOT).let { name ->
                name.contains(token) || (token.length >= 5 && token.endsWith("s") && name.contains(token.dropLast(1)))
            })
    }

    val hits = items.filter { item ->
        val entry = index[item.id]
        if (cities.isNotEmpty()) {
            if (entry == null || !entry.hasPlace) return@filter false
            if (cities.any { km(entry.lat.toDouble(), entry.lon.toDouble(), it.lat, it.lon) > NEAR_CITY_KM }) return@filter false
        }
        val date by lazy(LazyThreadSafetyMode.NONE) { Instant.ofEpochMilli(item.date).atZone(zone).toLocalDate() }
        val passes = filters.all { token ->
            when {
                token in MONTHS -> date.monthValue == MONTHS.indexOf(token) + 1
                token == "hoy" -> date == today
                token == "ayer" -> date == today.minusDays(1)
                token == "texto" -> (entry?.text?.length ?: 0) >= 40
                sameWord(token, "video") -> item.isVideo
                sameWord(token, "foto") -> !item.isVideo
                sameWord(token, "favorita") -> item.id in favorites
                sameWord(token, "captura") -> item.isScreenshot
                sameWord(token, "selfie") -> entry?.front == true
                token == "flash" -> entry?.flash == true
                sameWord(token, "vertical") || token == "verticales" -> item.shownHeight > item.shownWidth
                sameWord(token, "horizontal") || token == "horizontales" -> item.shownWidth > item.shownHeight
                else -> date.year == token.toInt()
            }
        }
        passes && words.all { literal(item, entry, it) }
    }
    return hits
}

// ---------- Álbumes automáticos ----------

class AutoAlbum(val key: String, val title: String, val subtitle: String, val items: List<MediaItem>, val isTrip: Boolean)

private class Thing(val key: String, val title: String, vararg val labels: String)

private val THINGS = listOf(
    Thing(
        "comida", "Comida", "Food", "Cuisine", "Meal", "Fast food", "Pizza", "Cake", "Vegetable", "Fruit", "Lunch", "Supper",
        "Bread", "Cheeseburger", "Sushi", "Cookie", "Pie", "Gelato", "Hot dog", "Bento", "Couscous", "Pho", "Pasteles",
    ),
    Thing(
        "mascotas", "Mascotas", "Dog", "Cat", "Pet", "Dalmatian", "Boxer", "Basset hound", "Cairn terrier", "Shetland sheepdog",
        "Shikoku", "Cavalier", "Ragdoll", "Sphynx", "Himalayan", "Pixie-bob",
    ),
    Thing("playa", "Playa y mar", "Beach", "Sand", "Surfing", "Sailboat", "Boat", "Pier", "Lighthouse", "Reef", "Snorkeling", "Underwater"),
    Thing("montana", "Montaña y nieve", "Mountain", "Cliff", "Canyon", "Glacier", "Volcano", "Skiing", "Snowboarding", "Ice"),
    Thing("naturaleza", "Naturaleza", "Forest", "Jungle", "Waterfall", "River", "Lake", "Garden", "Flower", "Prairie", "Field"),
    Thing("atardeceres", "Atardeceres", "Sunset"),
    Thing("ciudad", "Ciudad", "Building", "Skyscraper", "Skyline", "Tower", "Bridge", "Church", "Cathedral", "Castle", "Palace", "Monument"),
    Thing("gente", "Gente", "Selfie", "Smile", "Crowd", "Baby", "Child", "Team", "Bride", "Groom", "Grandparent", "Laugh"),
    Thing("vehiculos", "Coches y motos", "Car", "Motorcycle", "Bicycle", "Van"),
    Thing("celebraciones", "Celebraciones", "Christmas", "Party", "Balloon", "Marriage", "Graduation", "Concert", "Fireworks", "Carnival"),
    Thing("documentos", "Papeles", "Receipt", "Paper", "Newspaper", "Menu", "Passport", "Whiteboard", "Blackboard"),
)

fun buildThings(items: List<MediaItem>, index: Map<Long, IndexEntry>): List<AutoAlbum> {
    val albums = THINGS.mapNotNull { thing ->
        val wanted = thing.labels.toSet()
        val found = items.filter { item -> index[item.id]?.labels?.any { it in wanted } == true }
        if (found.size < 3) null else AutoAlbum("cosa:${thing.key}", thing.title, "", found, false)
    }
    // Fotos con bastante texto que no son capturas de pantalla: carteles, apuntes, pizarras.
    val written = items.filter { !it.isScreenshot && (index[it.id]?.text?.length ?: 0) >= 60 }
    return if (written.size < 3) albums else albums + AutoAlbum("cosa:texto", "Con texto", "", written, false)
}

private val CITIES: List<City> get() = Geo.cities

private const val NEAR_CITY_KM = 60.0

private fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 6371 * 2 * asin(sqrt(a))
}

fun nearestCity(lat: Double, lon: Double, withinKm: Double): City? {
    // Un grado de latitud son unos 111 km: lo que queda claramente lejos se descarta sin hacer cuentas.
    val reach = withinKm / 111.0 + 0.1
    var best: City? = null
    var bestKm = withinKm
    for (city in CITIES) {
        if (kotlin.math.abs(city.lat - lat) > reach) continue
        val d = km(lat, lon, city.lat, city.lon)
        if (d <= bestKm) {
            bestKm = d
            best = city
        }
    }
    return best
}

private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("es", "ES"))

/**
 * Un viaje es una racha de fotos hechas lejos de casa. "Casa" es la zona donde hay más fotos
 * con ubicación. Hacen falta al menos diez fotos con ubicación para fiarse de eso.
 */
fun buildTrips(items: List<MediaItem>, index: Map<Long, IndexEntry>): List<AutoAlbum> {
    val placed = items.mapNotNull { item -> index[item.id]?.takeIf { it.hasPlace }?.let { item to it } }.sortedBy { it.first.date }
    if (placed.size < 10) return emptyList()
    val homeCell = placed.groupBy { (Math.round(it.second.lat * 2f) to Math.round(it.second.lon * 2f)) }.maxBy { it.value.size }.value
    val homeLat = homeCell.map { it.second.lat.toDouble() }.average()
    val homeLon = homeCell.map { it.second.lon.toDouble() }.average()
    val zone = ZoneId.systemDefault()
    val trips = ArrayList<AutoAlbum>()
    val run = ArrayList<Pair<MediaItem, IndexEntry>>()

    fun close() {
        if (run.size >= 4) {
            val lat = run.map { it.second.lat.toDouble() }.average()
            val lon = run.map { it.second.lon.toDouble() }.average()
            val start = Instant.ofEpochMilli(run.first().first.date).atZone(zone).toLocalDate()
            trips += AutoAlbum(
                key = "viaje:${run.first().first.id}",
                title = nearestCity(lat, lon, 150.0)?.name ?: "Viaje",
                subtitle = MONTH_YEAR.format(start),
                items = run.map { it.first }.sortedByDescending { it.date },
                isTrip = true,
            )
        }
        run.clear()
    }

    for (pair in placed) {
        val away = km(homeLat, homeLon, pair.second.lat.toDouble(), pair.second.lon.toDouble()) > 80
        val gapDays = run.lastOrNull()?.let { (pair.first.date - it.first.date) / 86_400_000.0 } ?: 0.0
        if (!away || gapDays > 4) close()
        if (away) run += pair
    }
    close()
    return trips.sortedByDescending { it.items.first().date }
}

// ---------- Recuerdos ----------

class Memory(val title: String, val items: List<MediaItem>)

/** Fotos de estos mismos días en años anteriores; gana el año más cercano que tenga alguna. */
fun buildMemory(items: List<MediaItem>): Memory? {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    // Día (contado desde 1970) -> cuántos años hace. Así cada foto se comprueba con una sola cuenta.
    val yearsByDay = HashMap<Long, Int>()
    for (years in 1..15) {
        val then = today.minusYears(years.toLong()).toEpochDay()
        for (day in then - 1..then + 1) yearsByDay.putIfAbsent(day, years)
    }
    val offset = zone.rules.getOffset(Instant.now()).totalSeconds * 1000L
    val found = HashMap<Int, ArrayList<MediaItem>>()
    for (item in items) {
        val years = yearsByDay[Math.floorDiv(item.date + offset, 86_400_000L)] ?: continue
        found.getOrPut(years) { ArrayList() } += item
    }
    val years = found.keys.minOrNull() ?: return null
    return Memory(if (years == 1) "Hace un año" else "Hace $years años", found.getValue(years))
}

// ---------- Filtros del buscador ----------

/** Tipos de archivo por los que se puede acotar una búsqueda. Se pueden sumar: "vídeos" y "favoritas". */
enum class Kind(val label: String) {
    PHOTOS("Fotos"), VIDEOS("Vídeos"), FAVORITES("Favoritas"), SCREENSHOTS("Capturas"), TEXT("Con texto"), GIFS("GIF"),
    MOTION("En movimiento"), SELFIES("Selfies"), FLASH("Con flash"),
    HORIZONTAL("Horizontales"), VERTICAL("Verticales"), HIGH_RES("Alta resolución"),
}

/** Tramos de peso del archivo. */
enum class SizeRange(val label: String, val range: LongRange) {
    SMALL("Menos de 1 MB", 0L until MB),
    MEDIUM("De 1 a 10 MB", MB until 10 * MB),
    LARGE("De 10 a 100 MB", 10 * MB until 100 * MB),
    HUGE("Más de 100 MB", 100 * MB..Long.MAX_VALUE),
}

private const val MB = 1024L * 1024

/** Lo que hay elegido en el buscador además de lo escrito. Todo lo elegido tiene que cumplirse. */
data class Filters(
    val kinds: Set<Kind> = emptySet(),
    val year: Int? = null,
    /** De 1 a 12; solo se usa con un año elegido. */
    val month: Int? = null,
    val place: String? = null,
    val album: Long? = null,
    val thing: String? = null,
    /** Móvil o cámara con que se hizo. */
    val camera: String? = null,
    val size: SizeRange? = null,
    /** JPG, HEIC, PNG, DNG, MP4… */
    val format: String? = null,
    /** Nombre de una persona. */
    val person: String? = null,
) {
    val isEmpty: Boolean
        get() = kinds.isEmpty() && year == null && month == null && place == null && album == null && thing == null &&
            camera == null && size == null && format == null && person == null
}

/** Una opción de un filtro, con las fotos que quedarían al elegirla. */
class Option<T>(val value: T, val label: String, val count: Int)

/**
 * Resultado de una búsqueda y las opciones que tiene sentido ofrecer a continuación. Solo salen
 * opciones con alguna foto, así que elegir una nunca deja la pantalla vacía.
 */
class Found(
    val results: List<MediaItem>,
    val kinds: List<Option<Kind>> = emptyList(),
    val years: List<Option<Int>> = emptyList(),
    val months: List<Option<Int>> = emptyList(),
    val places: List<Option<String>> = emptyList(),
    val albums: List<Option<Long>> = emptyList(),
    val things: List<Option<String>> = emptyList(),
    val cameras: List<Option<String>> = emptyList(),
    val sizes: List<Option<SizeRange>> = emptyList(),
    val formats: List<Option<String>> = emptyList(),
    val people: List<Option<String>> = emptyList(),
)

/** Las cosas que se ven en una foto, con la palabra que se enseña al usuario. */
fun thingWords(entry: IndexEntry?): Set<String> {
    val labels = entry?.labels ?: return emptySet()
    if (labels.isEmpty()) return emptySet()
    return labels.filter { it !in VAGUE }.mapNotNullTo(HashSet()) { WORDS[it]?.firstOrNull() }
}

private val cityNames = java.util.concurrent.ConcurrentHashMap<Long, String>()

/** Ciudad cercana a cada foto que guarda su lugar. Las que no tienen ninguna cerca no aparecen. */
fun placeNames(items: List<MediaItem>, index: Map<Long, IndexEntry>): Map<Long, String> {
    val names = HashMap<Long, String>()
    for (item in items) {
        val entry = index[item.id]?.takeIf { it.hasPlace } ?: continue
        // Buscar la ciudad más cercana es lento y las fotos de un mismo sitio se repiten mucho:
        // se recuerda el resultado por cada kilómetro de mapa, más o menos.
        val cell = (Math.round(entry.lat * 100.0) shl 32) xor (Math.round(entry.lon * 100.0) and 0xFFFFFFFFL)
        val name = cityNames.getOrPut(cell) { nearestCity(entry.lat.toDouble(), entry.lon.toDouble(), NEAR_CITY_KM)?.name.orEmpty() }
        if (name.isNotEmpty()) names[item.id] = name
    }
    return names
}

private fun hasKind(kind: Kind, item: MediaItem, entry: IndexEntry?, favorites: Set<Long>): Boolean = when (kind) {
    Kind.PHOTOS -> !item.isVideo && !item.isScreenshot
    Kind.VIDEOS -> item.isVideo
    Kind.FAVORITES -> item.id in favorites
    Kind.SCREENSHOTS -> item.isScreenshot
    Kind.TEXT -> (entry?.text?.length ?: 0) >= 40
    Kind.GIFS -> item.isGif
    Kind.MOTION -> entry?.motion == true
    Kind.SELFIES -> entry?.front == true
    Kind.FLASH -> entry?.flash == true
    Kind.HORIZONTAL -> item.shownWidth > item.shownHeight
    Kind.VERTICAL -> item.shownHeight > item.shownWidth
    // Fotos de 12 megapíxeles o más; vídeos 4K.
    Kind.HIGH_RES -> if (item.isVideo) maxOf(item.width, item.height) >= 3840 else item.width.toLong() * item.height >= 12_000_000L
}

private fun sizeOf(item: MediaItem): SizeRange? = SizeRange.entries.firstOrNull { item.size in it.range }

/**
 * Busca [query] (si hay algo escrito) y aplica [filters]. Para cada filtro cuenta sus opciones
 * sobre lo que queda al aplicar todos los demás, de modo que se puede cambiar de año o de lugar
 * sin tener que quitar antes el que estaba puesto.
 */
fun find(
    query: String,
    filters: Filters,
    items: List<MediaItem>,
    index: Map<Long, IndexEntry>,
    favorites: Set<Long>,
    places: Map<Long, String>,
    /** Foto -> nombres de las personas que salen en ella. */
    people: Map<Long, List<String>> = emptyMap(),
): Found {
    val base = if (query.isBlank()) items else search(query, items, index, favorites, people)
    // Sin nada escrito ni elegido no hay resultados que enseñar, pero sí opciones que ofrecer.
    val idle = query.isBlank() && filters.isEmpty

    val zone = ZoneId.systemDefault()
    val results = ArrayList<MediaItem>()
    val kinds = IntArray(Kind.entries.size)
    val years = HashMap<Int, Int>()
    val months = IntArray(13)
    val placeCount = HashMap<String, Int>()
    val albumCount = HashMap<Long, Int>()
    val albumNames = HashMap<Long, String>()
    val thingCount = HashMap<String, Int>()
    val cameraCount = HashMap<String, Int>()
    val sizeCount = IntArray(SizeRange.entries.size)
    val formatCount = HashMap<String, Int>()
    val personCount = HashMap<String, Int>()

    for (item in base) {
        val entry = index[item.id]
        val date = Instant.ofEpochMilli(item.date).atZone(zone)
        val place = places[item.id]
        val things = thingWords(entry)

        val okKind = filters.kinds.all { hasKind(it, item, entry, favorites) }
        val okYear = filters.year == null || date.year == filters.year
        val okMonth = filters.month == null || date.monthValue == filters.month
        val okPlace = filters.place == null || place == filters.place
        val okAlbum = filters.album == null || item.bucketId == filters.album
        val okThing = filters.thing == null || filters.thing in things
        val camera = entry?.camera.orEmpty()
        val size = sizeOf(item)
        val format = item.format
        val okCamera = filters.camera == null || camera == filters.camera
        val okSize = filters.size == null || size == filters.size
        val okFormat = filters.format == null || format == filters.format
        val names = people[item.id].orEmpty()
        val okPerson = filters.person == null || filters.person in names
        val failed = (if (okKind) 0 else 1) + (if (okYear) 0 else 1) + (if (okMonth) 0 else 1) +
            (if (okPlace) 0 else 1) + (if (okAlbum) 0 else 1) + (if (okThing) 0 else 1) +
            (if (okCamera) 0 else 1) + (if (okSize) 0 else 1) + (if (okFormat) 0 else 1) + (if (okPerson) 0 else 1)
        if (failed > 1) continue
        if (failed == 0) results += item

        // Cada filtro cuenta lo que pasa todos los demás.
        if (failed == 0) Kind.entries.forEach { if (hasKind(it, item, entry, favorites)) kinds[it.ordinal]++ }
        if (failed == 0 || !okYear) years.merge(date.year, 1, Int::plus)
        if ((failed == 0 || !okMonth) && filters.year != null) months[date.monthValue]++
        if ((failed == 0 || !okPlace) && place != null) placeCount.merge(place, 1, Int::plus)
        if (failed == 0 || !okAlbum) {
            albumCount.merge(item.bucketId, 1, Int::plus)
            albumNames.getOrPut(item.bucketId) { albumName(item.bucket) }
        }
        if (failed == 0 || !okThing) things.forEach { thingCount.merge(it, 1, Int::plus) }
        if ((failed == 0 || !okCamera) && camera.isNotEmpty()) cameraCount.merge(camera, 1, Int::plus)
        if ((failed == 0 || !okSize) && size != null) sizeCount[size.ordinal]++
        if ((failed == 0 || !okFormat) && format.isNotEmpty()) formatCount.merge(format, 1, Int::plus)
        if (failed == 0 || !okPerson) names.forEach { personCount.merge(it, 1, Int::plus) }
    }

    return Found(
        results = if (idle) emptyList() else results,
        kinds = Kind.entries.filter { kinds[it.ordinal] > 0 || it in filters.kinds }.map { Option(it, it.label, kinds[it.ordinal]) },
        years = years.entries.sortedByDescending { it.key }.map { Option(it.key, it.key.toString(), it.value) },
        months = (1..12).filter { months[it] > 0 }.map { Option(it, MONTHS[it - 1].replaceFirstChar(Char::uppercase), months[it]) },
        places = placeCount.entries.sortedByDescending { it.value }.take(40).map { Option(it.key, it.key, it.value) },
        albums = albumCount.entries.sortedByDescending { it.value }.map { Option(it.key, albumNames[it.key].orEmpty(), it.value) },
        things = thingCount.entries.sortedByDescending { it.value }.take(40).map { Option(it.key, it.key.replaceFirstChar(Char::uppercase), it.value) },
        cameras = cameraCount.entries.sortedByDescending { it.value }.take(30).map { Option(it.key, it.key, it.value) },
        sizes = SizeRange.entries.filter { sizeCount[it.ordinal] > 0 }.map { Option(it, it.label, sizeCount[it.ordinal]) },
        formats = formatCount.entries.sortedByDescending { it.value }.map { Option(it.key, it.key, it.value) },
        people = personCount.entries.sortedByDescending { it.value }.map { Option(it.key, it.key, it.value) },
    )
}
