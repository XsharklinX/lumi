package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Formas de encuadre del vídeo. 0 = la suya. */
enum class VideoFrame(val label: String, val ratio: Float) {
    ORIGINAL("Original", 0f), SQUARE("1:1", 1f), PORTRAIT("9:16", 9f / 16f), WIDE("16:9", 16f / 9f), FOUR_FIVE("4:5", 4f / 5f),
}

/** Estilos de color, los mismos de un vistazo que en el editor de fotos. */
enum class VideoLook(val label: String) {
    NONE("Original"), VIVID("Vivo"), WARM("Cálido"), COOL("Frío"), MONO("Blanco y negro"), SEPIA("Sepia"), FADED("Desvaído"), DRAMA("Drama"),
}

/** Todo lo que se puede cambiar de un vídeo. Lo que vale lo mismo que al principio no se aplica. */
data class VideoEdit(
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val speed: Float = 1f,
    val frame: VideoFrame = VideoFrame.ORIGINAL,
    /** Cuartos de vuelta a la derecha. */
    val quarter: Int = 0,
    val mirror: Boolean = false,
    val look: VideoLook = VideoLook.NONE,
    /** De -1 a 1. */
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    /** De -100 a 100. */
    val saturation: Float = 0f,
    val mute: Boolean = false,
    /** De 0 a 1. */
    val volume: Float = 1f,
)

/** Efectos de imagen del vídeo, en el orden en que se aplican. Sirven igual para verlo y para guardarlo. */
@androidx.annotation.OptIn(UnstableApi::class)
fun videoEffects(edit: VideoEdit, withSpeed: Boolean): List<Effect> = buildList {
    if (withSpeed && edit.speed != 1f) add(SpeedChangeEffect(edit.speed))
    if (edit.quarter % 4 != 0 || edit.mirror) {
        add(
            ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(-90f * (edit.quarter % 4))
                .setScale(if (edit.mirror) -1f else 1f, 1f)
                .build(),
        )
    }
    if (edit.frame != VideoFrame.ORIGINAL) add(Presentation.createForAspectRatio(edit.frame.ratio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
    when (edit.look) {
        VideoLook.NONE -> Unit
        VideoLook.VIVID -> {
            add(HslAdjustment.Builder().adjustSaturation(30f).build())
            add(Contrast(0.12f))
        }
        VideoLook.WARM -> add(RgbAdjustment.Builder().setRedScale(1.1f).setGreenScale(1.02f).setBlueScale(0.88f).build())
        VideoLook.COOL -> add(RgbAdjustment.Builder().setRedScale(0.9f).setGreenScale(1f).setBlueScale(1.1f).build())
        VideoLook.MONO -> add(RgbFilter.createGrayscaleFilter())
        VideoLook.SEPIA -> add(RgbMatrix { _, _ ->
            // Por columnas, como espera el motor: cada columna dice cuánto aporta un canal a R, G y B.
            floatArrayOf(
                0.393f, 0.349f, 0.272f, 0f,
                0.769f, 0.686f, 0.534f, 0f,
                0.189f, 0.168f, 0.131f, 0f,
                0f, 0f, 0f, 1f,
            )
        })
        VideoLook.FADED -> {
            add(Contrast(-0.22f))
            add(HslAdjustment.Builder().adjustSaturation(-25f).build())
        }
        VideoLook.DRAMA -> {
            add(Contrast(0.3f))
            add(HslAdjustment.Builder().adjustSaturation(-20f).build())
        }
    }
    if (edit.brightness != 0f) add(Brightness(edit.brightness.coerceIn(-1f, 1f)))
    if (edit.contrast != 0f) add(Contrast(edit.contrast.coerceIn(-1f, 1f)))
    if (edit.saturation != 0f) add(HslAdjustment.Builder().adjustSaturation(edit.saturation.coerceIn(-100f, 100f)).build())
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun audioEffects(edit: VideoEdit): List<AudioProcessor> = buildList {
    if (edit.speed != 1f) add(SonicAudioProcessor().apply { setSpeed(edit.speed) })
    if (edit.volume < 0.999f) {
        add(ChannelMixingAudioProcessor().apply {
            for (channels in 1..2) putChannelMixingMatrix(ChannelMixingMatrix.create(channels, channels).scaleBy(edit.volume))
        })
    }
}

/**
 * Guarda [item] con los cambios de [edit] como vídeo nuevo, junto al original. Vuelve a codificar
 * el vídeo (H.264), así que tarda un poco; [onProgress] va de 0 a 100.
 */
@androidx.annotation.OptIn(UnstableApi::class)
suspend fun exportVideo(context: Context, item: MediaItem, edit: VideoEdit, onProgress: (Int) -> Unit): Boolean =
    withContext(Dispatchers.Main) {
        val temp = File(context.cacheDir, "editado.mp4")
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
        val end = if (edit.endMs in 1 until item.duration) edit.endMs else androidx.media3.common.C.TIME_END_OF_SOURCE
        val source = androidx.media3.common.MediaItem.Builder()
            .setUri(item.uri)
            .setClippingConfiguration(
                androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(edit.startMs)
                    .setEndPositionMs(end)
                    .build(),
            )
            .build()
        val edited = EditedMediaItem.Builder(source)
            .setRemoveAudio(edit.mute || edit.volume <= 0.001f)
            .setEffects(Effects(if (edit.mute) emptyList() else audioEffects(edit), videoEffects(edit, withSpeed = true)))
            .build()
        try {
            transformer.start(edited, temp.path)
            val holder = ProgressHolder()
            while (!done.isCompleted) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                delay(300)
            }
            done.await() && withContext(Dispatchers.IO) { saveEditedVideo(context, temp, item) }
        } finally {
            if (!done.isCompleted) runCatching { transformer.cancel() }
            temp.delete()
        }
    }

private fun saveEditedVideo(context: Context, file: File, item: MediaItem): Boolean {
    if (!file.exists() || file.length() == 0L) return false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, item.name.substringBeforeLast('.') + "_editado.mp4")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(item.path)) item.path else "Movies/Lumi/")
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
