package com.lumi.galeria

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Size
import coil.ImageLoader
import android.media.ThumbnailUtils
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import com.lumi.galeria.data.Geo
import com.lumi.galeria.data.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Miniatura que guarda el propio sistema: mucho más rápida que decodificar la foto entera. */
data class Thumb(val uri: Uri, val px: Int, val stamp: Long)

/** Miniatura de un archivo de una cámara, tarjeta o memoria USB, para elegir qué importar. */
data class DocThumb(val uri: Uri)

private class DocThumbFetcher(private val thumb: DocThumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bitmap = runInterruptible {
            val resolver = options.context.contentResolver
            // Las cámaras y memorias suelen dar una miniatura hecha; si no, se decodifica la foto a poco tamaño.
            runCatching { android.provider.DocumentsContract.getDocumentThumbnail(resolver, thumb.uri, android.graphics.Point(256, 256), null) }.getOrNull()
                ?: runCatching {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(resolver, thumb.uri)) { decoder, info, _ ->
                        val k = 256f / maxOf(info.size.width, info.size.height)
                        if (k < 1f) decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                    }
                }.getOrNull()
        } ?: return null
        return DrawableResult(BitmapDrawable(options.context.resources, bitmap), true, DataSource.DISK)
    }
}

/** La cara de una persona, recortada de la miniatura en la que se encontró. [left]… van de 0 a 1. */
data class FaceThumb(val uri: Uri, val stamp: Long, val left: Float, val top: Float, val right: Float, val bottom: Float)

private class FaceThumbFetcher(private val face: FaceThumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bitmap = runInterruptible {
            val thumb = sharpThumb(options.context, face.uri, 1400) ?: options.context.contentResolver.loadThumbnail(
                face.uri, Size(com.lumi.galeria.data.FACE_THUMB, com.lumi.galeria.data.FACE_THUMB), null,
            )
            // Un cuadrado algo mayor que la cara, para que se vea entera con algo de pelo y cuello.
            val w = thumb.width
            val h = thumb.height
            val cx = (face.left + face.right) / 2 * w
            val cy = (face.top + face.bottom) / 2 * h
            val side = (maxOf((face.right - face.left) * w, (face.bottom - face.top) * h) * 1.5f).coerceIn(16f, minOf(w, h).toFloat())
            val x = (cx - side / 2).coerceIn(0f, w - side).toInt()
            val y = (cy - side / 2).coerceIn(0f, h - side).toInt()
            val crop = Bitmap.createBitmap(thumb, x, y, side.toInt().coerceAtMost(w - x), side.toInt().coerceAtMost(h - y))
            val out = Bitmap.createScaledBitmap(crop, 320, 320, true)
            if (crop !== out) crop.recycle()
            if (thumb !== crop) thumb.recycle()
            out
        }
        return DrawableResult(BitmapDrawable(options.context.resources, bitmap), true, DataSource.DISK)
    }
}

/** Una página de un PDF como imagen ([page] desde 0), a [px] de ancho. */
data class PdfThumb(val uri: Uri, val stamp: Long, val page: Int = 0, val px: Int = 400)

private class PdfThumbFetcher(private val thumb: PdfThumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bitmap = runInterruptible { com.lumi.galeria.data.renderPage(options.context, thumb.uri, thumb.page, thumb.px) } ?: return null
        return DrawableResult(BitmapDrawable(options.context.resources, bitmap), true, DataSource.DISK)
    }
}

/** Miniatura de un archivo de la carpeta privada; se descifra al vuelo. */
data class VaultThumb(val id: String)

/** Lo más que da la miniatura guardada por Android. Por encima, se ve pixelada si se estira. */
private const val SYSTEM_THUMB = 512

/**
 * Imagen a buena resolución para las miniaturas grandes (tarjetas, recuerdos, historias, la foto
 * estrella): se decodifica la propia foto reducida para que su lado corto mida al menos [px]. En
 * los vídeos, un fotograma a ese tamaño. null si no se puede, y entonces vale la del sistema.
 */
internal fun sharpThumb(context: android.content.Context, uri: Uri, px: Int): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val type = resolver.getType(uri).orEmpty()
    if (type.startsWith("video/")) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getScaledFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, px * 2, px * 2)
        } finally {
            runCatching { retriever.release() }
        }
    } else {
        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            val short = minOf(info.size.width, info.size.height)
            // Lado corto de al menos [px], sin pasar de 2560 por el largo para no gastar memoria de más.
            var k = px.toFloat() / short
            k = minOf(k, 2560f / maxOf(info.size.width, info.size.height))
            if (k < 1f) decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
        }
    }
}.getOrNull()

private class ThumbFetcher(private val thumb: Thumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap = runInterruptible {
            val size = Size(thumb.px, thumb.px)
            // Los archivos de carpetas ocultas no están en la biblioteca: su miniatura se saca del propio archivo.
            val loaded = if (thumb.uri.scheme == "file") {
                val file = java.io.File(thumb.uri.path!!)
                val type = file.extension.lowercase()
                if (type in setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v")) ThumbnailUtils.createVideoThumbnail(file, size, null)
                else ThumbnailUtils.createImageThumbnail(file, size, null)
            } else if (thumb.px > SYSTEM_THUMB) {
                sharpThumb(options.context, thumb.uri, thumb.px) ?: options.context.contentResolver.loadThumbnail(thumb.uri, size, null)
            } else {
                options.context.contentResolver.loadThumbnail(thumb.uri, size, null)
            }
            // Subida ya a la memoria gráfica: al desplazar no hay que hacerlo en el momento de pintar.
            loaded.copy(Bitmap.Config.HARDWARE, false)?.also { loaded.recycle() } ?: loaded
        }
        return DrawableResult(BitmapDrawable(options.context.resources, bitmap), true, DataSource.DISK)
    }
}

private class VaultThumbFetcher(private val thumb: VaultThumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bitmap = runInterruptible { LumiApp.vault.thumbnail(thumb.id) ?: LumiApp.decoy.thumbnail(thumb.id) } ?: return null
        return DrawableResult(BitmapDrawable(options.context.resources, bitmap), true, DataSource.DISK)
    }
}

class LumiApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        vault = Vault(this)
        decoy = Vault(this, "vault_decoy")
        Geo.load(this)
        // Si la app se cerró con algo abierto de la carpeta privada, no debe quedar copia sin cifrar.
        vault.clearOpened()
        decoy.clearOpened()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(Keyer<Thumb> { data, _ -> "${data.uri}:${data.px}:${data.stamp}" })
            add(Fetcher.Factory<Thumb> { data, options, _ -> ThumbFetcher(data, options) })
            add(ImageDecoderDecoder.Factory())
            add(Keyer<VaultThumb> { data, _ -> "vault:${data.id}" })
            add(Keyer<DocThumb> { data, _ -> "doc:${data.uri}" })
            add(Keyer<PdfThumb> { data, _ -> "pdf:${data.uri}:${data.stamp}:${data.page}:${data.px}" })
            add(Fetcher.Factory<PdfThumb> { data, options, _ -> PdfThumbFetcher(data, options) })
            add(Keyer<FaceThumb> { data, _ -> "cara:${data.uri}:${data.stamp}:${data.left}:${data.top}" })
            add(Fetcher.Factory<FaceThumb> { data, options, _ -> FaceThumbFetcher(data, options) })
            add(Fetcher.Factory<DocThumb> { data, options, _ -> DocThumbFetcher(data, options) })
            add(Fetcher.Factory<VaultThumb> { data, options, _ -> VaultThumbFetcher(data, options) })
        }
        // Las miniaturas aparecen con un fundido corto en vez de saltar de golpe.
        .crossfade(160)
        // Pocas miniaturas a la vez: pedir decenas de golpe al desplazar deja sin aire a la pantalla.
        .fetcherDispatcher(Dispatchers.IO.limitedParallelism(4))
        .decoderDispatcher(Dispatchers.IO.limitedParallelism(2))
        .build()

    companion object {
        lateinit var vault: Vault
            private set

        /** La carpeta que se abre con el PIN señuelo. */
        lateinit var decoy: Vault
            private set
    }
}
