package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------- Varias fotos en un PDF ----------

/** Medidas de una hoja A4 en puntos, que es la unidad de los PDF. */
private const val A4_SHORT = 595
private const val A4_LONG = 842
private const val PDF_MARGIN = 24f

/**
 * Junta [items] en un PDF, una foto por página y en ese orden, y lo guarda en Documentos/Lumi.
 * Con [a4] cada página es una hoja A4 (tumbada si la foto lo está) con un pequeño margen; sin él,
 * la página tiene la forma de la foto. Devuelve la dirección del archivo, o null si no se pudo.
 */
fun savePdf(context: Context, items: List<MediaItem>, a4: Boolean, name: String): Uri? {
    val document = PdfDocument()
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val built = runCatching {
        items.forEachIndexed { index, item ->
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // Suficiente para leer un papel impreso sin que el archivo pese decenas de megas.
                val longSide = maxOf(info.size.width, info.size.height)
                if (longSide > 1800) {
                    val k = 1800f / longSide
                    decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                }
            }
            val wide = bitmap.width > bitmap.height
            val pageW: Int
            val pageH: Int
            if (a4) {
                pageW = if (wide) A4_LONG else A4_SHORT
                pageH = if (wide) A4_SHORT else A4_LONG
            } else {
                pageW = A4_SHORT
                pageH = (A4_SHORT.toFloat() * bitmap.height / bitmap.width).toInt().coerceAtLeast(1)
            }
            val page = document.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create())
            val margin = if (a4) PDF_MARGIN else 0f
            val k = minOf((pageW - 2 * margin) / bitmap.width, (pageH - 2 * margin) / bitmap.height)
            val w = bitmap.width * k
            val h = bitmap.height * k
            page.canvas.drawBitmap(bitmap, null, RectF((pageW - w) / 2, (pageH - h) / 2, (pageW + w) / 2, (pageH + h) / 2), paint)
            document.finishPage(page)
            bitmap.recycle()
        }
    }.isSuccess
    if (!built) {
        document.close()
        return null
    }

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.pdf")
        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Lumi/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) }.getOrNull()
    val saved = target != null && runCatching {
        resolver.openOutputStream(target)!!.use { document.writeTo(it) }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    document.close()
    if (!saved && target != null) runCatching { resolver.delete(target, null, null) }
    return if (saved) target else null
}

// ---------- Vídeos más ligeros ----------

/** Una calidad a la que se puede pasar un vídeo: lado corto de la imagen y datos por segundo. */
class ShrinkOption(val label: String, val hint: String, val shortSide: Int, val bitrate: Int)

val SHRINK_OPTIONS = listOf(
    ShrinkOption("Alta", "1080p. En el móvil se ve casi igual", 1080, 8_000_000),
    ShrinkOption("Media", "720p. Buena para enviar", 720, 4_000_000),
    ShrinkOption("Ligera", "480p. La que menos ocupa", 480, 1_800_000),
)

/** Lo que pesará, más o menos, [item] al pasarlo a [option]: imagen más sonido por su duración. */
fun shrunkSize(item: MediaItem, option: ShrinkOption): Long = item.duration / 1000 * (option.bitrate + 160_000) / 8

/**
 * Vuelve a comprimir [item] a la calidad [option] y lo guarda como vídeo nuevo junto al original,
 * que no se toca. Es un trabajo largo: [onProgress] recibe el porcentaje. Si se cancela la
 * corrutina, se detiene y no deja nada a medias.
 */
@androidx.annotation.OptIn(UnstableApi::class)
suspend fun shrinkVideo(context: Context, item: MediaItem, option: ShrinkOption, onProgress: (Int) -> Unit): Boolean =
    // El motor de conversión exige arrancar y consultarse desde el hilo principal.
    withContext(Dispatchers.Main) {
        val temp = File(context.cacheDir, "ligero.mp4")
        temp.delete()
        val done = CompletableDeferred<Boolean>()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(option.bitrate).build())
                    .build(),
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    done.complete(true)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    done.complete(false)
                }
            })
            .build()
        // Se encoge por el lado corto, así vale igual para vídeos en vertical y en horizontal.
        val shortSide = minOf(item.width, item.height)
        val effects = if (shortSide > option.shortSide) {
            val k = option.shortSide.toFloat() / shortSide
            listOf(ScaleAndRotateTransformation.Builder().setScale(k, k).build())
        } else {
            emptyList()
        }
        val edited = EditedMediaItem.Builder(androidx.media3.common.MediaItem.fromUri(item.uri)).setEffects(Effects(emptyList(), effects)).build()
        try {
            transformer.start(edited, temp.path)
            val holder = ProgressHolder()
            while (!done.isCompleted) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                delay(300)
            }
            done.await() && withContext(Dispatchers.IO) {
                saveVideoFile(context, temp, item.name.substringBeforeLast('.') + "_ligero.mp4", item.path)
            }
        } finally {
            if (!done.isCompleted) runCatching { transformer.cancel() }
            temp.delete()
        }
    }

/** Copia [file] a la biblioteca como vídeo nuevo, en [path] si ahí se puede escribir. */
private fun saveVideoFile(context: Context, file: File, name: String, path: String): Boolean {
    if (!file.exists() || file.length() == 0L) return false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(path)) path else "Movies/Lumi/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) }.getOrNull()
        ?: return false
    val saved = runCatching {
        file.inputStream().use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it) } }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!saved) runCatching { resolver.delete(target, null, null) }
    return saved
}
