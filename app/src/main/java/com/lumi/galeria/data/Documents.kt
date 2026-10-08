package com.lumi.galeria.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tipos de documento, según lo que dice su texto. */
enum class DocKind(val label: String) {
    INVOICE("Facturas"), RECEIPT("Recibos y tickets"), IDENTITY("Identidad"), CONTRACT("Contratos"), BANK("Banco"),
    HEALTH("Salud"), NOTES("Apuntes"), OTHER("Otros"),
}

/** Un PDF guardado por Lumi en Documents/Lumi. [text] es lo que se lee en él (para buscar y clasificar). */
class Doc(val uri: Uri, val id: Long, val name: String, val date: Long, val size: Long, val pages: Int, val kind: DocKind, val text: String)

/** Una página para el PDF: la imagen y, si se sabe, el texto que contiene con su sitio. */
class DocPage(val bitmap: Bitmap, val lines: List<TextLine>)

private const val FOLDER = "Documents/Lumi/"

/** Lo que se sabe de cada PDF (texto, tipo, páginas), para no volver a leerlo cada vez. */
private class DocIndex(context: Context) {
    private val file = File(context.filesDir, "documentos.json")
    private val data: org.json.JSONObject = runCatching { org.json.JSONObject(file.readText()) }.getOrDefault(org.json.JSONObject())

    @Synchronized
    fun get(id: Long, modified: Long): Triple<String, DocKind, Int>? {
        val o = data.optJSONObject(id.toString()) ?: return null
        if (o.optLong("m") != modified) return null
        return Triple(o.optString("t"), runCatching { DocKind.valueOf(o.optString("k")) }.getOrDefault(DocKind.OTHER), o.optInt("p"))
    }

    @Synchronized
    fun put(id: Long, modified: Long, text: String, kind: DocKind, pages: Int) {
        data.put(id.toString(), org.json.JSONObject().put("m", modified).put("t", text.take(6000)).put("k", kind.name).put("p", pages))
        runCatching { file.writeText(data.toString()) }
    }
}

private var docIndex: DocIndex? = null
private fun index(context: Context): DocIndex = docIndex ?: DocIndex(context.applicationContext).also { docIndex = it }

private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

/** Líneas de texto de [bitmap] con su caja, de 0 a 1. Lista vacía si el lector no está o no hay texto. */
fun readLines(bitmap: Bitmap): List<TextLine> = runCatching {
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            TextLine(line.text, box.left / w, box.top / h, box.right / w, box.bottom / h)
        }
    }
}.getOrDefault(emptyList())

/** Qué tipo de documento es, por las palabras que contiene. */
fun docKind(text: String): DocKind {
    val t = normalize(text)
    fun any(vararg words: String) = words.count { t.contains(it) }
    return when {
        any("documento nacional de identidad", "dni", "pasaporte", "passport", "permiso de conducir", "nie ", "identity card", "carnet") >= 1 && t.length < 1500 -> DocKind.IDENTITY
        any("factura", "invoice", "nif", "cif", "base imponible", "iva", "importe total", "fecha de emision") >= 2 -> DocKind.INVOICE
        any("contrato", "arrendador", "arrendatario", "clausula", "las partes", "firmado", "contract", "agreement") >= 2 -> DocKind.CONTRACT
        any("iban", "extracto", "saldo", "transferencia", "cuenta", "bank", "tarjeta de credito", "movimientos") >= 2 -> DocKind.BANK
        any("receta", "paciente", "diagnostico", "hospital", "medico", "analisis", "informe clinico", "farmacia", "cita") >= 2 -> DocKind.HEALTH
        any("ticket", "total", "efectivo", "cambio", "gracias por su compra", "recibo", "tarjeta", "pagado", "receipt") >= 2 -> DocKind.RECEIPT
        any("tema", "ejercicio", "apuntes", "capitulo", "leccion", "definicion", "examen", "chapter") >= 1 -> DocKind.NOTES
        else -> DocKind.OTHER
    }
}

private val MONTHS_ES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
private val DATE = Regex("""\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})\b""")

/**
 * Un nombre que diga qué es: «Factura Endesa marzo 2025». Toma el tipo, la primera línea con
 * letras que parezca un nombre (empresa o título) y la fecha que aparezca en el texto, o la de hoy.
 */
fun suggestDocName(lines: List<TextLine>, kind: DocKind, english: Boolean = false): String {
    val text = lines.joinToString("\n") { it.text }
    val type = when (kind) {
        DocKind.INVOICE -> if (english) "Invoice" else "Factura"
        DocKind.RECEIPT -> if (english) "Receipt" else "Recibo"
        DocKind.IDENTITY -> if (english) "ID" else "Documento de identidad"
        DocKind.CONTRACT -> if (english) "Contract" else "Contrato"
        DocKind.BANK -> if (english) "Bank" else "Banco"
        DocKind.HEALTH -> if (english) "Health" else "Salud"
        DocKind.NOTES -> if (english) "Notes" else "Apuntes"
        DocKind.OTHER -> if (english) "Document" else "Documento"
    }
    // La línea más grande de la mitad de arriba suele ser el nombre de la empresa o el título.
    val top = lines.filter { it.top < 0.45f }
        .filter { l -> l.text.count { it.isLetter() } >= 3 && l.text.length in 3..32 && !l.text.contains(Regex("\\d{4,}")) }
        .maxByOrNull { it.bottom - it.top }?.text?.trim()
        ?.replace(Regex("[^\\p{L}\\p{N} &.'-]"), "")
        ?.lowercase()?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    val month = DATE.find(text)?.let { m ->
        val mm = m.groupValues[2].toIntOrNull()
        var yy = m.groupValues[3].toIntOrNull()
        if (yy != null && yy < 100) yy += 2000
        if (mm != null && mm in 1..12 && yy != null && yy in 1990..2100) {
            if (english) java.time.Month.of(mm).name.lowercase().replaceFirstChar { it.uppercase() } + " $yy" else "${MONTHS_ES[mm - 1]} $yy"
        } else null
    } ?: run {
        val now = java.time.LocalDate.now()
        if (english) now.month.name.lowercase().replaceFirstChar { it.uppercase() } + " ${now.year}" else "${MONTHS_ES[now.monthValue - 1]} ${now.year}"
    }
    return listOfNotNull(type, top?.takeIf { !it.equals(type, ignoreCase = true) }, month).joinToString(" ").take(80)
}

// ---------- Leer y dibujar PDF ----------

/** Abre un PDF para leerlo; null si no se puede. Quien lo pide lo cierra. */
fun openPdf(context: Context, uri: Uri): Pair<ParcelFileDescriptor, PdfRenderer>? = runCatching {
    val fd = context.contentResolver.openFileDescriptor(uri, "r")!!
    fd to PdfRenderer(fd)
}.getOrNull()

fun pageCount(context: Context, uri: Uri): Int = openPdf(context, uri)?.let { (fd, pdf) ->
    val n = pdf.pageCount
    pdf.close()
    fd.close()
    n
} ?: 0

/** La página [index] de un PDF como imagen de [width] px de ancho, sobre blanco. */
fun renderPage(context: Context, uri: Uri, index: Int, width: Int): Bitmap? {
    val (fd, pdf) = openPdf(context, uri) ?: return null
    return try {
        if (index !in 0 until pdf.pageCount) return null
        pdf.openPage(index).use { page ->
            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            out.eraseColor(Color.WHITE)
            page.render(out, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            out
        }
    } catch (e: Exception) {
        null
    } finally {
        runCatching { pdf.close() }
        runCatching { fd.close() }
    }
}

/**
 * Escribe un PDF con [pages]. Cada página lleva su imagen y, encima, su texto invisible en el
 * mismo sitio: en cualquier lector se puede buscar, seleccionar y copiar.
 */
fun writePdf(file: File, pages: List<DocPage>) {
    val document = PdfDocument()
    try {
        pages.forEachIndexed { i, page ->
            // Ancho de un A4 (595 puntos); el alto, el que pida la imagen.
            val w = 595
            val h = (w.toFloat() * page.bitmap.height / page.bitmap.width).toInt().coerceAtLeast(1)
            val p = document.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
            val canvas = p.canvas
            canvas.drawBitmap(page.bitmap, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            // Texto casi transparente (no del todo: algunos programas descartan lo que no se ve en absoluto).
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(2, 0, 0, 0)
                typeface = Typeface.SANS_SERIF
            }
            page.lines.forEach { line ->
                val boxW = (line.right - line.left) * w
                val boxH = (line.bottom - line.top) * h
                if (boxW <= 1f || boxH <= 1f || line.text.isBlank()) return@forEach
                paint.textSize = boxH * 0.85f
                paint.textScaleX = 1f
                val natural = paint.measureText(line.text).coerceAtLeast(1f)
                paint.textScaleX = (boxW / natural).coerceIn(0.3f, 3f)
                canvas.drawText(line.text, line.left * w, line.bottom * h - boxH * 0.18f, paint)
            }
            document.finishPage(p)
        }
        file.outputStream().use { document.writeTo(it) }
    } finally {
        document.close()
    }
}

/** Guarda [file] como PDF de Lumi con el nombre [name] (sin «.pdf»). Devuelve su dirección. */
fun publishPdf(context: Context, file: File, name: String): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, safeFolder(name) + ".pdf")
        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
        put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) }.getOrNull() ?: return null
    val ok = runCatching {
        resolver.openOutputStream(target)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!ok) runCatching { resolver.delete(target, null, null) }
    return if (ok) target else null
}

/** Sustituye el contenido de un PDF de Lumi por [file] (al editarlo). */
fun replacePdf(context: Context, uri: Uri, file: File): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    true
}.getOrDefault(false)

fun renamePdf(context: Context, uri: Uri, name: String): Boolean = runCatching {
    context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, safeFolder(name) + ".pdf") }, null, null) > 0
}.getOrDefault(false)

fun deletePdf(context: Context, uri: Uri): Boolean = runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)

/** Decodifica una imagen (de la cámara, del escáner o de la galería) a un tamaño razonable para un PDF. */
fun decodeForPdf(context: Context, uri: Uri, longSide: Int = 2000): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val big = maxOf(info.size.width, info.size.height)
        if (big > longSide) {
            val k = longSide.toFloat() / big
            decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
        }
    }
}.getOrNull()

/** Gira [bitmap] [quarter] cuartos de vuelta a la derecha. */
fun turned(bitmap: Bitmap, quarter: Int): Bitmap {
    if (quarter % 4 == 0) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(90f * quarter) }, true)
}

// ---------- La lista de documentos ----------

/**
 * Los PDF de Lumi (Documents/Lumi), del más reciente al más antiguo. Lo que aún no se ha leído
 * se lee aquí (las tres primeras páginas): conviene llamarlo fuera del hilo de la pantalla.
 */
fun loadDocs(context: Context, readMissing: Boolean): List<Doc> {
    val resolver = context.contentResolver
    val out = ArrayList<Doc>()
    val columns = arrayOf(
        MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.SIZE,
    )
    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    runCatching {
        resolver.query(
            collection, columns,
            "${MediaStore.Files.FileColumns.MIME_TYPE} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("application/pdf", "Documents/%"),
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val uri = ContentUris.withAppendedId(collection, id)
                val modified = c.getLong(2)
                var known = index(context).get(id, modified)
                if (known == null && readMissing) {
                    val pages = pageCount(context, uri)
                    val text = (0 until minOf(pages, 3)).joinToString("\n") { i ->
                        renderPage(context, uri, i, 1240)?.let { bmp -> readLines(bmp).joinToString("\n") { it.text }.also { bmp.recycle() } }.orEmpty()
                    }
                    known = Triple(text, docKind(text), pages)
                    index(context).put(id, modified, text, known.second, pages)
                }
                out += Doc(
                    uri, id, c.getString(1).orEmpty().removeSuffix(".pdf"), modified * 1000, c.getLong(3),
                    known?.third ?: 0, known?.second ?: DocKind.OTHER, known?.first.orEmpty(),
                )
            }
        }
    }
    return out
}

/** Anota de antemano lo que se sabe de un PDF recién creado, para no tener que leerlo luego. */
fun rememberDoc(context: Context, uri: Uri, text: String, kind: DocKind, pages: Int) {
    runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATE_MODIFIED), null, null, null)?.use { c ->
            if (c.moveToFirst()) index(context).put(c.getLong(0), c.getLong(1), text, kind, pages)
        }
    }
}

/** Copia un PDF de fuera (elegido por el usuario) a Documents/Lumi. */
fun importPdf(context: Context, from: Uri): Uri? = runCatching {
    val name = context.contentResolver.query(from, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }?.removeSuffix(".pdf") ?: "Documento"
    val temp = File(context.cacheDir, "importado.pdf")
    context.contentResolver.openInputStream(from)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
    publishPdf(context, temp, name).also { temp.delete() }
}.getOrNull()

// ---------- DNI y tarjetas ----------

/**
 * Las dos caras de una tarjeta, a tamaño real (85,6 x 54 mm) en una página A4, como una fotocopia.
 * Con [mark], esa frase cruza la página en diagonal para que la copia no sirva para otra cosa.
 */
fun idCardPage(front: Bitmap, back: Bitmap?, mark: String): Bitmap {
    // A4 a 200 ppp.
    val w = 1654
    val h = 2339
    val page = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(page)
    canvas.drawColor(Color.WHITE)
    val cardW = 85.6f / 210f * w
    val cardH = 54f / 85.6f * cardW
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    listOfNotNull(front, back).forEachIndexed { i, card ->
        // Siempre en horizontal, como una tarjeta.
        val upright = if (card.height > card.width) turned(card, 1) else card
        val top = h * (if (back == null) 0.38f else if (i == 0) 0.2f else 0.55f)
        canvas.drawBitmap(upright, null, RectF((w - cardW) / 2, top, (w + cardW) / 2, top + cardH), paint)
    }
    if (mark.isNotBlank()) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 200, 30, 30)
            textSize = w * 0.035f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.save()
        canvas.rotate(-30f, w / 2f, h / 2f)
        var y = -h * 0.2f
        while (y < h * 1.2f) {
            canvas.drawText("$mark   ·   $mark   ·   $mark", -w * 0.2f, y, text)
            y += h * 0.12f
        }
        canvas.restore()
    }
    return page
}

/** Fecha de hoy para firmar: «12/03/2025». */
fun todayText(): String = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT).format(Instant.now().atZone(ZoneId.systemDefault()))
