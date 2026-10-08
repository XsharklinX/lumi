package com.lumi.galeria.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/** Lo ya comprobado: foto -> si es Ultra HDR. */
private val known = ConcurrentHashMap<String, Boolean>()

/**
 * Si la foto es Ultra HDR (JPEG con mapa de ganancia, como las de los móviles recientes). Se mira la
 * cabecera: estas fotos lo declaran con «hdrgm» en sus metadatos.
 */
fun isUltraHdr(context: Context, uri: Uri): Boolean {
    if (Build.VERSION.SDK_INT < 34) return false
    known[uri.toString()]?.let { return it }
    val found = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(256 * 1024)
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n <= 0) break
                read += n
            }
            String(head, 0, read, Charsets.ISO_8859_1).let { it.contains("hdrgm:") || it.contains("hdr-gain-map") }
        } ?: false
    }.getOrDefault(false)
    known[uri.toString()] = found
    return found
}

/**
 * La foto Ultra HDR con su mapa de ganancia, para que la pantalla la enseñe con su brillo real.
 * Solo en Android 14 o posterior; si no, o si la foto no lo es, null.
 */
fun decodeUltraHdr(context: Context, uri: Uri, maxSide: Int = 4096): Bitmap? {
    if (Build.VERSION.SDK_INT < 34 || !isUltraHdr(context, uri)) return null
    return runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val big = maxOf(info.size.width, info.size.height)
            if (big > maxSide) {
                val k = maxSide.toFloat() / big
                decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
            }
        }
    }.getOrNull()?.takeIf { it.hasGainmap() }
}
