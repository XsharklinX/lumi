package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import androidx.media3.common.Effect
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Cuánto dura cada foto en el vídeo de recuerdos. */
enum class MemoryPace(val label: String, val seconds: Float) { CALM("Tranquilo", 3.5f), NORMAL("Normal", 2.5f), QUICK("Rápido", 1.6f) }

/** Las fotos que mejor cuentan algo: hasta [max], repartidas a lo largo del tiempo, en orden. */
fun pickForMemory(photos: List<MediaItem>, favorites: Set<Long>, index: Map<Long, IndexEntry>, max: Int = 30): List<MediaItem> {
    val candidates = photos.filter { !it.isVideo && !it.isScreenshot && !it.isGif }.sortedBy { it.date }
    if (candidates.size <= max) return candidates
    // Se parte el tiempo en tramos iguales y de cada uno se coge la mejor: favorita y, si no, la más nítida.
    val chunk = candidates.size.toFloat() / max
    return (0 until max).map { i ->
        candidates.subList((i * chunk).toInt(), ((i + 1) * chunk).toInt().coerceAtMost(candidates.size).coerceAtLeast((i * chunk).toInt() + 1))
            .maxBy { (if (it.id in favorites) 1000f else 0f) + (index[it.id]?.blur ?: 0f) }
    }
}

/**
 * Monta un vídeo con [photos]: cada una con un acercamiento suave, el título sobre la primera y,
 * si hay [music], esa canción de fondo (se repite si hace falta y se corta al acabar las fotos).
 * Vertical u horizontal según cómo sean la mayoría. Se guarda en Movies/Lumi.
 */
@androidx.annotation.OptIn(UnstableApi::class)
suspend fun makeMemoryVideo(
    context: Context,
    photos: List<MediaItem>,
    music: Uri?,
    title: String,
    pace: MemoryPace,
    onProgress: (Int) -> Unit,
): Boolean = withContext(Dispatchers.Main) {
    if (photos.isEmpty()) return@withContext false
    val upright = photos.count { it.shownHeight > it.shownWidth } * 2 >= photos.size
    val width = if (upright) 1080 else 1920
    val height = if (upright) 1920 else 1080
    val durationMs = (pace.seconds * 1000).toLong()
    val frame = Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)

    val clips = photos.mapIndexed { i, photo ->
        // Un acercamiento lento, alternando si entra o sale, para que no se haga monótono.
        val zoomIn = i % 2 == 0
        val zoom = MatrixTransformation { timeUs ->
            val t = (timeUs / 1000f / durationMs).coerceIn(0f, 1f)
            val s = if (zoomIn) 1f + 0.08f * t else 1.08f - 0.08f * t
            Matrix().apply { postScale(s, s) }
        }
        val effects = ArrayList<Effect>()
        effects += frame
        effects += zoom
        if (i == 0 && title.isNotBlank()) {
            val text = SpannableString(title).apply {
                setSpan(AbsoluteSizeSpan(if (upright) 84 else 72), 0, length, 0)
                setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, length, 0)
            }
            val settings = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, -0.72f).build()
            effects += OverlayEffect(ImmutableList.of(TextOverlay.createStaticTextOverlay(text, settings)))
        }
        val source = androidx.media3.common.MediaItem.Builder().setUri(photo.uri).setImageDurationMs(durationMs).build()
        EditedMediaItem.Builder(source).setFrameRate(30).setEffects(Effects(emptyList(), effects)).build()
    }
    val sequences = ArrayList<EditedMediaItemSequence>()
    sequences += EditedMediaItemSequence(clips)
    if (music != null) {
        val song = EditedMediaItem.Builder(androidx.media3.common.MediaItem.fromUri(music)).setRemoveVideo(true).build()
        sequences += EditedMediaItemSequence(ImmutableList.of(song), true)
    }
    val composition = Composition.Builder(sequences).apply { if (music != null) experimentalSetForceAudioTrack(true) }.build()

    val temp = File(context.cacheDir, "recuerdo.mp4")
    temp.delete()
    val done = CompletableDeferred<Boolean>()
    val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
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
            delay(300)
        }
        done.await() && withContext(Dispatchers.IO) { saveMemoryVideo(context, temp, title) }
    } finally {
        if (!done.isCompleted) runCatching { transformer.cancel() }
        temp.delete()
    }
}

private fun saveMemoryVideo(context: Context, file: File, title: String): Boolean {
    if (!file.exists() || file.length() == 0L) return false
    val resolver = context.contentResolver
    val name = safeFolder(title).replace(' ', '_').ifEmpty { "Recuerdo" } + "_" + System.currentTimeMillis() / 1000 + ".mp4"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Lumi/")
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
