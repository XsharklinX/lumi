package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Las fotos de un timelapse, guardadas una a una en una carpeta temporal mientras se hace. */
class TimelapseFrames(context: Context) {
    val folder = File(context.cacheDir, "timelapse_${System.currentTimeMillis()}").apply { mkdirs() }
    var count = 0
        private set
    var width = 0
        private set
    var height = 0
        private set

    fun add(frame: Bitmap) {
        if (count == 0) {
            width = frame.width
            height = frame.height
        }
        File(folder, "f_%05d.jpg".format(count)).outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        count++
    }

    fun clear() {
        folder.deleteRecursively()
    }
}

/**
 * Monta el vídeo acelerado: cada foto dura un fotograma, a 30 por segundo. Se guarda en
 * Movies/Lumi y se devuelve su dirección, o null si no se pudo.
 */
@androidx.annotation.OptIn(UnstableApi::class)
suspend fun makeTimelapse(context: Context, frames: TimelapseFrames, onProgress: (Int) -> Unit): Uri? = withContext(Dispatchers.Main) {
    if (frames.count < 2) return@withContext null
    val upright = frames.height >= frames.width
    val width = if (upright) 1080 else 1920
    val height = if (upright) 1920 else 1080
    val frame = Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
    val clips = (0 until frames.count).map { i ->
        val source = androidx.media3.common.MediaItem.Builder()
            .setUri(Uri.fromFile(File(frames.folder, "f_%05d.jpg".format(i)))).setImageDurationMs(34).build()
        EditedMediaItem.Builder(source).setFrameRate(30).setEffects(Effects(emptyList(), listOf(frame))).build()
    }
    val composition = Composition.Builder(listOf(EditedMediaItemSequence(clips))).build()
    val temp = File(context.cacheDir, "timelapse.mp4")
    temp.delete()
    val done = CompletableDeferred<Boolean>()
    val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                done.complete(true)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                done.complete(false)
            }
        })
        .build()
    try {
        transformer.start(composition, temp.path)
        val holder = ProgressHolder()
        while (!done.isCompleted) {
            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
            delay(250)
        }
        if (!done.await()) null else withContext(Dispatchers.IO) { publishVideo(context, temp, "Timelapse_" + System.currentTimeMillis() / 1000 + ".mp4") }
    } finally {
        if (!done.isCompleted) runCatching { transformer.cancel() }
        temp.delete()
        frames.clear()
    }
}

private fun publishVideo(context: Context, file: File, name: String): Uri? {
    if (!file.exists() || file.length() == 0L) return null
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Lumi/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) }.getOrNull() ?: return null
    val ok = runCatching {
        file.inputStream().use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it) } }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }.isSuccess
    if (!ok) runCatching { resolver.delete(target, null, null) }
    return if (ok) target else null
}

/**
 * Retrato Lumi: separa lo principal (personas, mascotas, objetos) y desenfoca el fondo con suavidad.
 * Se trabaja a 2560 de lado como mucho; null si no se encontró nada que separar.
 */
fun lumiPortrait(source: Bitmap, strength: Float = 0.55f): Bitmap? = runCatching {
    val k = minOf(1f, 2560f / maxOf(source.width, source.height))
    val photo = if (k < 1f) Bitmap.createScaledBitmap(source, (source.width * k).toInt(), (source.height * k).toInt(), true) else source
    val w = photo.width
    val h = photo.height
    // La máscara se calcula más pequeña (es lo lento) y se amplía con suavidad.
    val m = minOf(1f, 1280f / maxOf(w, h))
    val small = if (m < 1f) Bitmap.createScaledBitmap(photo, (w * m).toInt(), (h * m).toInt(), true) else photo
    val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build())
    val buffer = Tasks.await(segmenter.process(InputImage.fromBitmap(small, 0))).foregroundConfidenceMask ?: return null
    segmenter.close()
    val confidence = FloatArray(small.width * small.height)
    buffer.rewind()
    buffer.get(confidence)
    if (confidence.count { it > 0.5f } < confidence.size / 50) return null
    val maskPx = IntArray(confidence.size) { i ->
        val t = ((confidence[i] - 0.2f) / 0.6f).coerceIn(0f, 1f)
        val a = (t * t * (3 - 2 * t) * 255).toInt()
        (a shl 24) or 0xFFFFFF
    }
    val mask = Bitmap.createScaledBitmap(Bitmap.createBitmap(maskPx, small.width, small.height, Bitmap.Config.ARGB_8888), w, h, true)
    val alpha = IntArray(w * h)
    mask.getPixels(alpha, 0, w, 0, 0, w, h)
    mask.recycle()
    // Desenfoque por pasos: reducir mucho y volver a ampliar en dos saltos queda suave, sin cuadros.
    val factor = 6f + strength * 34f
    val tiny = Bitmap.createScaledBitmap(photo, (w / factor).toInt().coerceAtLeast(2), (h / factor).toInt().coerceAtLeast(2), true)
    val mid = Bitmap.createScaledBitmap(tiny, (w / 4).coerceAtLeast(2), (h / 4).coerceAtLeast(2), true)
    val blurred = Bitmap.createScaledBitmap(mid, w, h, true)
    tiny.recycle()
    mid.recycle()
    val back = IntArray(w * h)
    blurred.getPixels(back, 0, w, 0, 0, w, h)
    blurred.recycle()
    val px = IntArray(w * h)
    photo.getPixels(px, 0, w, 0, 0, w, h)
    for (i in px.indices) {
        val a = (alpha[i] ushr 24) / 255f
        val p = px[i]
        val b = back[i]
        fun mix(shift: Int) = ((p shr shift and 0xFF) * a + (b shr shift and 0xFF) * (1 - a)).toInt().coerceIn(0, 255)
        px[i] = (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
    Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}.getOrNull()
