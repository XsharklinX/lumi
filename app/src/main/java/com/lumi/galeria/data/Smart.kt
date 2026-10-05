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

private val STOP = setOf("de", "del", "en", "la", "el", "los", "las", "un", "una", "con", "y", "al", "a", "mi", "mis", "que", "por")
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

/** Temas que se proponen en el buscador: la palabra que se enseña y cómo se le describe al modelo. */
val CONCEPTS: List<Pair<String, String>> = listOf(
    "perros" to "a dog", "gatos" to "a cat", "pájaros" to "a bird", "caballos" to "a horse",
    "playa" to "a beach", "mar" to "the sea", "montaña" to "mountains", "nieve" to "snow", "bosque" to "a forest",
    "cascadas" to "a waterfall", "lagos y ríos" to "a lake or a river", "atardeceres" to "a sunset", "noche" to "the city at night",
    "flores" to "flowers", "comida" to "a plate of food", "café" to "a cup of coffee", "fruta" to "fruit",
    "ciudad" to "city buildings and streets", "coches" to "a car", "retratos" to "a portrait of a person",
    "grupos" to "a group of people", "bebés" to "a baby", "selfies" to "a selfie", "bodas" to "a wedding",
    "fiestas" to "a party", "documentos" to "a document with text", "dibujos" to "a drawing or a cartoon", "anime" to "anime",
    "memes" to "a meme", "deporte" to "people playing sports",
)

/** Ciudades cerca de las que hay fotos. */
fun topPlaces(items: List<MediaItem>, index: Map<Long, IndexEntry>, limit: Int = 8): List<Topic> {
    val groups = HashMap<String, ArrayList<MediaItem>>()
    for (item in items) {
        val entry = index[item.id]?.takeIf { it.hasPlace } ?: continue
        val city = nearestCity(entry.lat.toDouble(), entry.lon.toDouble(), NEAR_CITY_KM) ?: continue
        groups.getOrPut(city.name) { ArrayList() } += item
    }
    return groups.entries.sortedByDescending { it.value.size }.take(limit).map { Topic(it.key, it.value.size, it.value.first()) }
}

/** Palabras que no describen la foto sino que acotan: fechas y tipos de archivo. */
private fun isFilterWord(token: String): Boolean =
    token in MONTHS || (token.length == 4 && token.all(Char::isDigit) && token.toInt() in 1990..2100) ||
        token == "hoy" || token == "ayer" || token == "texto" ||
        sameWord(token, "video") || sameWord(token, "foto") || sameWord(token, "favorita") || sameWord(token, "captura")

/** Lo que queda de la búsqueda al quitar fechas y tipos: la frase que se compara con las fotos. */
fun semanticPhrase(query: String): String =
    normalize(query).replace(NOT_WORD, " ").trim().split(' ').filter { it.isNotEmpty() && !isFilterWord(it) }.joinToString(" ")

/** La misma frase en inglés, si todas sus palabras tienen traducción conocida; si no, null. */
fun toEnglish(phrase: String): String? {
    val words = phrase.split(' ').filter { it.length >= 2 && it !in STOP }
    if (words.isEmpty()) return null
    return words.map { token ->
        // Varias cosas comparten palabra ("perro" es Dog, pero también cada raza): vale la más corta.
        PLAIN_WORDS.entries.filter { (_, list) -> sameWord(list.first(), token) }.minByOrNull { it.key.length }?.key?.lowercase() ?: return null
    }.joinToString(" ")
}

/**
 * Busca por lo que se ve en la foto, por el texto que contiene, por lugar ("madrid"), por fecha
 * ("agosto", "2025", "ayer"), por álbum, por nombre de archivo y por tipo ("vídeos", "capturas",
 * "favoritas", "texto").
 *
 * [semantic] es el parecido de cada foto con la frase, calculado por el modelo. Con él, una foto
 * entra si se parece lo bastante a la frase o si las palabras aparecen literalmente (en su texto,
 * su álbum o su nombre); y salen primero las que más se parecen. Sin él, se usa la lista fija de
 * cosas que reconoce el modelo antiguo.
 */
fun search(
    query: String,
    items: List<MediaItem>,
    index: Map<Long, IndexEntry>,
    favorites: Set<Long>,
    semantic: Map<Long, Float>? = null,
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
    val tokens = plain.trim().split(' ').filter { it.length >= 2 && it !in STOP }
    if (tokens.isEmpty() && cities.isEmpty()) return emptyList()
    val filters = tokens.filter(::isFilterWord)
    val words = tokens - filters.toSet()

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val labelSets = words.associateWith(::labelsFor)
    val albumNames = HashMap<Long, String>()

    val scores = semantic?.takeIf { words.isNotEmpty() && it.isNotEmpty() }
    // Lo que se parece de verdad queda cerca de la mejor; lo demás, claramente por debajo.
    val floor = maxOf(MIN_LIKENESS, (scores?.values?.maxOrNull() ?: 0f) * 0.8f)

    fun literal(item: MediaItem, entry: IndexEntry?, token: String): Boolean {
        val wanted = labelSets.getValue(token)
        return entry?.plainText?.contains(token) == true ||
            // La lista fija de cosas solo se usa con lo que el modelo nuevo aún no ha mirado.
            (wanted.isNotEmpty() && scores?.containsKey(item.id) != true && entry?.labels?.any { it in wanted } == true) ||
            // Carpeta, ruta y nombre del archivo: así se llega a lo que viene de una app
            // ("tiktok", "whatsapp", "instagram") aunque se escriba en plural.
            albumNames.getOrPut(item.bucketId) { normalize(albumName(item.bucket) + " " + item.path) }.let { where ->
                where.contains(token) || (token.length >= 5 && token.endsWith("s") && where.contains(token.dropLast(1)))
            } ||
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
                else -> date.year == token.toInt()
            }
        }
        passes && (words.isEmpty() || (scores?.get(item.id) ?: 0f) >= floor || words.all { literal(item, entry, it) })
    }
    return if (scores == null) hits else hits.sortedByDescending { scores[it.id] ?: 0f }
}

/** Parecido mínimo para dar una foto por buena; sale de probar el modelo con fotos y frases reales. */
private const val MIN_LIKENESS = 0.16f

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
