package com.lumi.galeria.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Una pegatina guardada: su archivo (512×512, WebP) y los emojis con que WhatsApp la sugiere. */
data class Sticker(val file: String, val emoji: String)

/** Datos del paquete que WhatsApp lee. */
object StickerPack {
    const val ID = "lumi_stickers"
    const val NAME = "Mis pegatinas de Lumi"
    const val PUBLISHER = "Lumi Gallery"
    const val TRAY = "tray.png"
    const val MIN = 3
    const val MAX = 30
    fun authority(context: Context) = context.packageName + ".stickercontentprovider"
}

/** Las pegatinas del usuario, en la carpeta privada de Lumi. */
class StickerStore(private val context: Context) {
    private val dir = File(context.filesDir, "pegatinas").apply { mkdirs() }
    private val index = File(dir, "pegatinas.json")

    fun dir(): File = dir

    fun list(): List<Sticker> = runCatching {
        val a = JSONArray(index.readText())
        (0 until a.length()).map { val o = a.getJSONObject(it); Sticker(o.getString("f"), o.optString("e", "😀")) }
            .filter { File(dir, it.file).exists() }
    }.getOrDefault(emptyList())

    private fun save(list: List<Sticker>) {
        index.writeText(JSONArray().also { a -> list.forEach { a.put(JSONObject().put("f", it.file).put("e", it.emoji)) } }.toString())
        // La versión cambia con cada cambio: así WhatsApp vuelve a leer el paquete.
        File(dir, "version").writeText(System.currentTimeMillis().toString())
        list.firstOrNull()?.let { makeTray(File(dir, it.file)) }
    }

    fun version(): String = runCatching { File(dir, "version").readText() }.getOrDefault("1")

    /** Guarda una pegatina ya preparada (512×512 con transparencia). Devuelve falso si el paquete está lleno. */
    fun add(bitmap: Bitmap, emoji: String): Boolean {
        val current = list()
        if (current.size >= StickerPack.MAX) return false
        val name = "p${System.currentTimeMillis()}.webp"
        File(dir, name).writeBytes(encodeSticker(bitmap))
        save(current + Sticker(name, emoji))
        return true
    }

    fun remove(sticker: Sticker) {
        File(dir, sticker.file).delete()
        save(list().filter { it.file != sticker.file })
    }

    fun setEmoji(sticker: Sticker, emoji: String) = save(list().map { if (it.file == sticker.file) it.copy(emoji = emoji) else it })

    /** El icono del paquete: la primera pegatina en 96×96. */
    private fun makeTray(sticker: File) {
        runCatching {
            val bmp = android.graphics.BitmapFactory.decodeFile(sticker.path) ?: return
            val tray = Bitmap.createScaledBitmap(bmp, 96, 96, true)
            File(dir, StickerPack.TRAY).outputStream().use { tray.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

/** WebP con transparencia por debajo de 100 KB, que es lo que pide WhatsApp. */
fun encodeSticker(bitmap: Bitmap): ByteArray {
    var quality = 90
    while (true) {
        val out = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        bitmap.compress(format, quality, out)
        if (out.size() <= 100 * 1024 || quality <= 20) return out.toByteArray()
        quality -= 10
    }
}

/**
 * De un recorte (con transparencia) hace una pegatina: se ajusta al contenido, se centra en un lienzo
 * de 512×512 con margen y, si se pide, lleva el borde blanco típico de las pegatinas.
 */
fun buildSticker(cutout: Bitmap, outline: Boolean): Bitmap {
    val size = 512
    val margin = if (outline) 28 else 16
    val box = size - margin * 2
    val k = minOf(box.toFloat() / cutout.width, box.toFloat() / cutout.height)
    val w = (cutout.width * k).toInt().coerceAtLeast(1)
    val h = (cutout.height * k).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(cutout, w, h, true)
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val left = (size - w) / 2f
    val top = (size - h) / 2f
    if (outline) {
        // El borde: la silueta teñida de blanco, dibujada varias veces alrededor.
        val silhouette = scaled.extractAlpha()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val radius = 9f
        for (i in 0 until 24) {
            val angle = Math.PI * 2 * i / 24
            canvas.drawBitmap(silhouette, left + (Math.cos(angle) * radius).toFloat(), top + (Math.sin(angle) * radius).toFloat(), paint)
        }
        silhouette.recycle()
    }
    canvas.drawBitmap(scaled, left, top, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
}

/** Recorta un bitmap con transparencia a la caja que ocupa lo visible. */
fun trimTransparent(bitmap: Bitmap): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val px = IntArray(w * h)
    bitmap.getPixels(px, 0, w, 0, 0, w, h)
    var left = w; var top = h; var right = -1; var bottom = -1
    for (i in px.indices) if ((px[i] ushr 24) > 25) {
        val x = i % w; val y = i / w
        if (x < left) left = x
        if (x > right) right = x
        if (y < top) top = y
        if (y > bottom) bottom = y
    }
    if (right < left || bottom < top) return bitmap
    return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
}

/** El proveedor que enseña a WhatsApp el paquete de pegatinas, tal como pide su formato. */
class StickerProvider : ContentProvider() {
    private companion object {
        const val METADATA = "metadata"
        const val STICKERS = "stickers"
        const val ASSETS = "stickers_asset"
    }

    override fun onCreate(): Boolean = true

    private fun store() = StickerStore(context!!)

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val segments = uri.pathSegments
        return when (segments.firstOrNull()) {
            METADATA -> metadata()
            STICKERS -> stickers()
            else -> null
        }
    }

    private fun metadata(): Cursor {
        val c = MatrixCursor(
            arrayOf(
                "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon", "android_play_store_link",
                "ios_app_download_link", "sticker_pack_publisher_email", "sticker_pack_publisher_website", "sticker_pack_privacy_policy_website",
                "sticker_pack_license_agreement_website", "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack",
            ),
        )
        val store = store()
        if (store.list().size >= 1) {
            c.addRow(
                arrayOf<Any>(
                    StickerPack.ID, StickerPack.NAME, StickerPack.PUBLISHER, StickerPack.TRAY, "", "", "contactosharklin@gmail.com", "", "", "",
                    store.version(), 0, 0,
                ),
            )
        }
        return c
    }

    private fun stickers(): Cursor {
        val c = MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text"))
        store().list().forEach { c.addRow(arrayOf<Any>(it.file, it.emoji, "Pegatina de Lumi")) }
        return c
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val segments = uri.pathSegments
        if (segments.size < 3 || segments[0] != ASSETS) return null
        val name = segments[2]
        // Solo archivos del paquete: nada de rutas que salgan de la carpeta.
        if (name.contains('/') || name.contains("..")) return null
        val file = File(store().dir(), name)
        if (!file.exists()) return null
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String? = when (uri.pathSegments.firstOrNull()) {
        METADATA -> "vnd.android.cursor.dir/vnd.${StickerPack.authority(context!!)}.$METADATA"
        STICKERS -> "vnd.android.cursor.dir/vnd.${StickerPack.authority(context!!)}.$STICKERS"
        ASSETS -> if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Solo lectura")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = throw UnsupportedOperationException("Solo lectura")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = throw UnsupportedOperationException("Solo lectura")
}
