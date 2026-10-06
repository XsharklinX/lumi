package com.lumi.galeria

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprueba que, con la app en inglés, no queda ningún texto en español. Lee el propio código:
 * cada texto fijo que parezca español tiene que cambiar al pasar por [tr]. Si se añade un texto
 * nuevo y se olvida su traducción en English.kt, esta prueba dice cuál es.
 */
class EnglishTest {
    private val literal = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"")

    /** Señales de que un texto está en español: tildes, eñes, signos de apertura o palabras muy comunes. */
    private val spanish = Regex(
        "[áéíóúñ¿¡«]|\\b(de|del|la|las|el|los|un|una|con|sin|que|no|se|para|por|tus|tu|esta|este|y|al|ya|más|todo|toca|usar|mover|copiar|guardar|elegir|" +
            "fotos?|carpetas?|archivos?|álbum|borrar|quitar|cambiar|aquí|hay|nada|puedes)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Textos que no se enseñan: palabras para el buscador de ajustes, nombres de archivo, consultas. */
    private fun internal(text: String, line: String): Boolean =
        "Card(\"" in line || "RELATIVE_PATH" in line || "DISPLAY_NAME" in line || "execSQL" in line || "rawQuery" in line ||
            "Thread(" in line || "File(" in line || "label = " in line || "ofPattern" in line || "setOf(" in line || "mapOf(" in line ||
            " to \"" in line || "key = " in line || "PinStep" in line || "\"borrar\" ->" in line || line.trim().startsWith("\"de\",") ||
            text.endsWith(" ") || text.startsWith("Pictures/") || text.endsWith(".mp4") || text.endsWith(".jpg")

    @Test
    fun everySpanishTextHasAnEnglishVersion() {
        Lang.apply(AppLanguage.ENGLISH)
        val root = File("src/main/java/com/lumi/galeria")
        assertTrue("No se encuentra el código en ${root.absolutePath}", root.isDirectory)
        val missing = ArrayList<String>()
        // La segunda mitad de una frase partida en dos líneas: la frase entera se prueba en English.kt.
        var continued = false
        root.walkTopDown().filter { it.extension == "kt" && it.name !in setOf("Words.kt", "English.kt", "Geo.kt") }.forEach { file ->
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                val secondHalf = continued
                continued = trimmed.endsWith("\" +")
                if (secondHalf) return@forEach
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEach
                literal.findAll(line).forEach { match ->
                    val text = match.groupValues[1]
                    // Los textos con un dato dentro se prueban aparte, con datos de ejemplo.
                    if ('$' in text || '\\' in text || text.length < 2 || text.startsWith(" ") || text.startsWith(")") || text.startsWith(".")) return@forEach
                    if (!spanish.containsMatchIn(text) || internal(text, line)) return@forEach
                    if (tr(text) == text) missing += "${file.name}: $text"
                }
            }
        }
        assertTrue("Sin traducción:\n" + missing.distinct().joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun textsWithDataInsideAreTranslated() {
        Lang.apply(AppLanguage.ENGLISH)
        val samples = mapOf(
            "Hace 3 años" to "3 years ago",
            "Mover 4 aquí" to "Move 4 here",
            "Mover 4 a la papelera · 12 MB" to "Move 4 to the trash · 12 MB",
            "Devolver 2 a la galería" to "Return 2 to the gallery",
            "Copiando 3 de 1500" to "Copying 3 of 1500",
            "Nada con «gato»" to "Nothing for “gato”",
            "Agosto de 2025" to "August 2025",
            "mayo de 2025" to "May 2025",
            "Lumi ha mirado 12 de 2,000 photos. Ya puedes buscar en esas." to "Lumi has looked at 12 of 2,000 photos. You can already search those.",
            "Mirando qué hay en cada foto: 12 de 2000" to "Looking at each photo: 12 of 2000",
            "Leyendo el texto de las fotos: faltan 40" to "Reading text in photos: 40 to go",
            "Puedes recuperar 1.2 GB. Toca para ver cómo." to "You can free up 1.2 GB. Tap to see how.",
            "Fotos 3.4 GB" to "Photos 3.4 GB",
            "Vídeos 900 MB" to "Videos 900 MB",
            "12 GB libres de 64 GB" to "12 GB free of 64 GB",
            "Restaurar 3" to "Restore 3",
            "Borrar 3" to "Delete 3",
            "12 días" to "12 days",
            "De 0:05 a 0:42" to "From 0:05 to 0:42",
            "El recorte dura 0:37 de 2:14" to "The clip lasts 0:37 of 2:14",
            "Brillo 40 %" to "Brightness 40%",
            "Volumen 80 %" to "Volume 80%",
            "Pila de 5 parecidas" to "Stack of 5 similar",
            "Velocidad 1.5×" to "Speed 1.5×",
            "Cerca de Madrid" to "Near Madrid",
            "Conservar las 4" to "Keep all 4",
            "unos 41 MB" to "about 41 MB",
            "Todo copiado en USB" to "Everything backed up to USB",
            "Convirtiendo… 40 %. No salgas de esta pantalla hasta que termine." to "Converting… 40%. Stay on this screen until it finishes.",
            "Listo · 3 chosen" to "Done · 3 chosen",
            "412 · yesterday · oculto" to "412 · yesterday · hidden",
            "17:40 · Cámara" to "17:40 · Camera",
        )
        samples.forEach { (spanish, english) -> assertEquals(english, tr(spanish)) }
        // Cantidades y fechas, que monta Format.kt.
        assertEquals("1 photo", countText(1, "foto", "fotos"))
        assertEquals("1,204 photos", countText(1204, "foto", "fotos"))
        assertEquals("3 copied to Camera", tr("${countText(3, "copiada", "copiadas")} a Cámara"))
        assertEquals("2 files not backed up", tr("${countText(2, "archivo", "archivos")} sin copiar"))
        assertEquals("Today", dayTitle(java.time.LocalDate.now()))
        assertEquals("August 2025", monthTitle(java.time.YearMonth.of(2025, 8)))
    }

    @Test
    fun spanishStaysUntouched() {
        Lang.apply(AppLanguage.SPANISH)
        assertEquals("Ajustes", tr("Ajustes"))
        assertEquals("1.204 fotos", countText(1204, "foto", "fotos"))
    }
}
