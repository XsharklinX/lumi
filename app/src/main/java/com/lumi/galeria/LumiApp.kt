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

/** Miniatura de un archivo de la carpeta privada; se descifra al vuelo. */
data class VaultThumb(val id: String)

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
        val bitmap = runInterruptible { LumiApp.vault.thumbnail(thumb.id) } ?: return null
        return DrawableResult(BitmapDrawable(options.context.resources, bitmap), true, DataSource.DISK)
    }
}

class LumiApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        vault = Vault(this)
        Geo.load(this)
        // Si la app se cerró con algo abierto de la carpeta privada, no debe quedar copia sin cifrar.
        vault.clearOpened()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(Keyer<Thumb> { data, _ -> "${data.uri}:${data.px}:${data.stamp}" })
            add(Fetcher.Factory<Thumb> { data, options, _ -> ThumbFetcher(data, options) })
            add(ImageDecoderDecoder.Factory())
            add(Keyer<VaultThumb> { data, _ -> "vault:${data.id}" })
            add(Fetcher.Factory<VaultThumb> { data, options, _ -> VaultThumbFetcher(data, options) })
        }
        // Pocas miniaturas a la vez: pedir decenas de golpe al desplazar deja sin aire a la pantalla.
        .fetcherDispatcher(Dispatchers.IO.limitedParallelism(4))
        .decoderDispatcher(Dispatchers.IO.limitedParallelism(2))
        .build()

    companion object {
        lateinit var vault: Vault
            private set
    }
}
