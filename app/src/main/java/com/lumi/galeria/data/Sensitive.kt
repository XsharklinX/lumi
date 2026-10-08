package com.lumi.galeria.data

import java.text.Normalizer
import java.util.Locale

/** Fotos con datos personales que conviene guardar bajo llave. */
enum class Sensitive(val label: String, val hint: String) {
    IDENTITY("Documentos de identidad", "DNI, cédula, pasaporte o carné de conducir"),
    CARD("Tarjetas bancarias", "Se ve el número de la tarjeta"),
    PASSWORD("Contraseñas y códigos", "Claves, PIN o códigos de seguridad a la vista"),
}

/** Lo que el reconocimiento de cosas llama papeles. */
private val PAPER_LABELS = setOf("Receipt", "Paper", "Newspaper", "Menu", "Passport", "Whiteboard", "Blackboard", "Poster")

private val MARKS = Regex("\\p{Mn}+")

private fun plain(text: String): String = MARKS.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")

private val ID_WORDS = Regex(
    "\\b(dni|documento nacional de identidad|pasaporte|passport|cedula( de identidad)?|junta central electoral|identidad y electoral|" +
        "permiso de conducir|licencia de conducir|carne de conducir|carnet de identidad|identity card|national id|driver'?s? licen[cs]e|" +
        "driving licence|numero de soporte|fecha de caducidad|lugar de nacimiento|nacionalidad|tarjeta de residencia|nie)\\b",
)
private val CARD_WORDS = Regex("\\b(visa|mastercard|master card|american express|amex|maestro|debito|debit|credito|credit|valid thru|valida hasta|vence|expira|cvv|cvc)\\b")
private val CARD_NUMBER = Regex("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)")
private val FOUR_GROUPS = Regex("(?<!\\d)\\d{4}[ -]\\d{4}[ -]\\d{4}[ -]\\d{1,4}(?!\\d)")
private val PASSWORD_WORDS = Regex(
    "\\b(contrasena|password|passwd|pwd|clave( de acceso| wifi)?|pin|codigo de (seguridad|verificacion|acceso)|security code|verification code|" +
        "frase de recuperacion|recovery phrase|seed phrase|palabras de recuperacion)\\b\\s*[:=]",
)
private val RECOVERY_WORDS = Regex("\\b(frase de recuperacion|recovery phrase|seed phrase|codigos de respaldo|backup codes)\\b")

/** Números de tarjeta de verdad: pasan la comprobación de Luhn que usan todos los bancos. */
private fun luhn(digits: String): Boolean {
    var sum = 0
    var double = false
    for (i in digits.indices.reversed()) {
        var d = digits[i] - '0'
        if (double) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
        double = !double
    }
    return sum % 10 == 0
}

/** Qué dato personal se ve en la foto, por el texto que Lumi leyó en ella; null si ninguno. */
fun sensitiveKind(entry: IndexEntry?): Sensitive? {
    val raw = entry?.text.orEmpty()
    if (raw.length < 8) return if (entry?.labels?.contains("Passport") == true) Sensitive.IDENTITY else null
    val text = plain(raw)
    if (entry?.labels?.contains("Passport") == true || (ID_WORDS.findAll(text).count() >= 2 && raw.length < 2500)) return Sensitive.IDENTITY
    val numbers = CARD_NUMBER.findAll(text).map { it.value.filter(Char::isDigit) }.filter { it.length in 13..19 && luhn(it) }.toList()
    if (numbers.isNotEmpty() && (CARD_WORDS.containsMatchIn(text) || FOUR_GROUPS.containsMatchIn(text))) return Sensitive.CARD
    if (PASSWORD_WORDS.containsMatchIn(text) || RECOVERY_WORDS.containsMatchIn(text)) return Sensitive.PASSWORD
    return null
}

/**
 * Lo que no se luce: capturas, papeles, fotos con mucho texto y cualquier dato personal. No sale
 * en recuerdos, portadas, widgets, fondos de pantalla ni avisos. En la galería sigue igual.
 */
fun keepOutOfShowcase(item: MediaItem, entry: IndexEntry?): Boolean {
    if (item.isScreenshot) return true
    if (item.path.startsWith("Documents/") || item.path.contains("/Lumi Documentos")) return true
    if (entry == null) return false
    if (entry.labels.any { it in PAPER_LABELS }) return true
    if (entry.text.length >= 180) return true
    return sensitiveKind(entry) != null
}

/**
 * Lo bien que queda una foto de portada: favorita, con caras sonriendo y los ojos abiertos,
 * nítida, bien expuesta y grande. Lo que no se luce no compite.
 */
fun coverScore(item: MediaItem, favorites: Set<Long>, index: Map<Long, IndexEntry>, faces: Map<Long, Float>, out: Set<Long>): Float {
    if (item.id in out || item.isScreenshot) return -20f
    if (item.isGif) return -8f
    var score = 0f
    if (item.isVideo) score -= 1.5f
    if (item.id in favorites) score += 3f
    faces[item.id]?.let { score += 1f + it * 1.5f }
    val entry = index[item.id]
    val blur = entry?.blur ?: -1f
    if (blur > 0f) score += (kotlin.math.ln(blur + 1f) / 3f).coerceAtMost(2f)
    entry?.color?.takeIf { it ushr 24 == 0xFF }?.let { color ->
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val light = 0.299f * r + 0.587f * g + 0.114f * b
        if (light < 45f) score -= 2f else if (light < 70f) score -= 0.8f
        if (light > 230f) score -= 1f
    }
    if ((entry?.text?.length ?: 0) >= 40) score -= 1f
    if (item.width.toLong() * item.height >= 6_000_000) score += 0.3f
    return score
}

/** La mejor portada entre las primeras [look] de [items], que van de la más reciente a la más antigua. */
fun pickCover(items: List<MediaItem>, favorites: Set<Long>, index: Map<Long, IndexEntry>, faces: Map<Long, Float>, out: Set<Long>, look: Int = 150): MediaItem {
    var best = items[0]
    var bestScore = Float.NEGATIVE_INFINITY
    val n = minOf(items.size, look)
    for (i in 0 until n) {
        val item = items[i]
        // A igualdad, la más reciente: así la portada se parece a lo que hay dentro ahora.
        val score = coverScore(item, favorites, index, faces, out) - i * 0.004f
        if (score > bestScore) {
            bestScore = score
            best = item
        }
    }
    return best
}

private const val OUT_FILE = "escaparate_fuera.txt"

/** Lo que no se luce, para quien lo lee fuera de la app: widgets, fondo de pantalla y avisos. */
fun writeShowcaseOut(context: android.content.Context, ids: Set<Long>) {
    runCatching { java.io.File(context.filesDir, OUT_FILE).writeText(ids.joinToString(",")) }
}

fun readShowcaseOut(context: android.content.Context): Set<Long> = runCatching {
    java.io.File(context.filesDir, OUT_FILE).takeIf { it.exists() }?.readText().orEmpty()
        .split(',').mapNotNullTo(HashSet()) { it.toLongOrNull() }
}.getOrDefault(emptySet())
