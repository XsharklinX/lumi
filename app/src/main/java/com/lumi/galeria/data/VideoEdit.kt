package com.lumi.galeria.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlurWithFrameOverlaid
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SingleColorLut
import androidx.media3.effect.SpeedChangeEffect
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
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Forma de la imagen del vídeo final. */
enum class VideoFormat(val label: String, val ratio: Float) {
    ORIGINAL("Original", 0f), VERTICAL("9:16 vertical", 9f / 16f), SQUARE("1:1 cuadrado", 1f), FOUR_FIVE("4:5", 4f / 5f), WIDE("16:9 horizontal", 16f / 9f),
}

/** Qué hacer cuando lo grabado no tiene la forma elegida. */
enum class FitMode(val label: String) { BLUR("Fondo desenfocado"), CROP("Recortar al centro"), BARS("Franjas negras") }

/** Estilos de color. */
enum class VideoLook(val label: String) {
    NONE("Original"), VIVID("Vivo"), WARM("Cálido"), COOL("Frío"), MONO("Blanco y negro"), SEPIA("Sepia"), FADED("Desvaído"), DRAMA("Drama"),
}

/** Cómo entra un trozo después del anterior. Son entradas del propio trozo, no un cruce entre los dos. */
enum class ClipTransition(val label: String) { NONE("Corte"), FADE("Fundido"), SLIDE("Deslizar"), ZOOM("Acercar") }

enum class TitleLook(val label: String) { SIMPLE("Sencillo"), BOX("Con caja"), GOLD("Dorado"), PINK("Rosa") }

enum class TitleAnim(val label: String) { FADE("Aparece"), SLIDE("Sube"), POP("Rebote"), NONE("Fijo") }

enum class TitleSpot(val label: String) { TOP("Arriba"), CENTER("Centro"), BOTTOM("Abajo") }

/** Un trozo de la película: un tramo de un vídeo o una foto con su duración. */
data class Clip(
    val id: Long,
    val uri: String,
    val name: String,
    val path: String,
    val photo: Boolean,
    /** Tamaño tal como se ve, ya girado. */
    val width: Int,
    val height: Int,
    /** Duración total del vídeo de origen; en una foto, lo que dura. */
    val sourceMs: Long,
    val startMs: Long = 0L,
    val endMs: Long = sourceMs,
    val speed: Float = 1f,
    /** Cómo entra este trozo. */
    val transition: ClipTransition = ClipTransition.NONE,
) {
    val keptMs: Long get() = (endMs - startMs).coerceAtLeast(1L)

    /** Lo que dura en la película, con su velocidad. */
    val outMs: Long get() = (keptMs / speed).toLong().coerceAtLeast(1L)
}

/** Un texto que aparece entre dos momentos de la película. */
data class TitleItem(
    val id: Long,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val style: TitleLook = TitleLook.SIMPLE,
    val anim: TitleAnim = TitleAnim.FADE,
    val spot: TitleSpot = TitleSpot.BOTTOM,
)

/** Canción de fondo, de un archivo del teléfono. */
data class MusicTrack(
    val uri: String,
    val name: String,
    val songMs: Long,
    /** Desde dónde empieza a sonar la canción. */
    val offsetMs: Long = 0L,
    val volume: Float = 0.7f,
    val fadeOut: Boolean = true,
)

/** Todo lo que se puede hacer a una película. Lo que vale lo mismo que al principio no se aplica. */
data class VideoProject(
    val clips: List<Clip>,
    val titles: List<TitleItem> = emptyList(),
    val music: MusicTrack? = null,
    val format: VideoFormat = VideoFormat.ORIGINAL,
    val fit: FitMode = FitMode.BLUR,
    val look: VideoLook = VideoLook.NONE,
    /** Mismos ajustes que el editor de fotos: de -1 a 1. */
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val curve: Curve = Curve(),
    /** Cuartos de vuelta a la derecha. */
    val quarter: Int = 0,
    val mirror: Boolean = false,
    val mute: Boolean = false,
    /** Volumen del sonido original, de 0 a 1. */
    val volume: Float = 1f,
) {
    val totalMs: Long get() = clips.sumOf { it.outMs }
    val hasAdjust: Boolean get() = brightness != 0f || contrast != 0f || saturation != 0f || warmth != 0f || !curve.isIdentity
    val hasPhotos: Boolean get() = clips.any { it.photo }

    /** Cuándo empieza cada trozo en la película, en milisegundos. */
    fun startsMs(): List<Long> {
        var at = 0L
        return clips.map { val s = at; at += it.outMs; s }
    }
}

/** Tamaño del vídeo final (siempre par). */
fun outputSize(p: VideoProject): Pair<Int, Int> {
    val first = p.clips.firstOrNull()
    val big = p.clips.any { max(it.width, it.height) >= 1500 }
    fun even(v: Float) = (v.toInt() / 2 * 2).coerceAtLeast(2)
    return when (p.format) {
        VideoFormat.VERTICAL -> if (big) 1080 to 1920 else 720 to 1280
        VideoFormat.SQUARE -> if (big) 1080 to 1080 else 720 to 720
        VideoFormat.FOUR_FIVE -> if (big) 1080 to 1350 else 720 to 900
        VideoFormat.WIDE -> if (big) 1920 to 1080 else 1280 to 720
        VideoFormat.ORIGINAL -> {
            var w = (first?.width ?: 1280).toFloat()
            var h = (first?.height ?: 720).toFloat()
            if (p.quarter % 2 != 0) { val t = w; w = h; h = t }
            val k = min(1f, 1920f / max(w, h))
            even(w * k) to even(h * k)
        }
    }
}

// ---------- Efectos de imagen ----------

@androidx.annotation.OptIn(UnstableApi::class)
private fun lookEffects(look: VideoLook): List<Effect> = buildList {
    when (look) {
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
}

/**
 * Los ajustes del editor de fotos (luz, contraste, color, calidez y curva) como una tabla de color 3D, que el
 * vídeo aplica a cada fotograma: así se ven igual que en una foto.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun adjustLut(p: VideoProject): Effect? {
    if (!p.hasAdjust) return null
    val n = 33
    val m = com.lumi.galeria.ui.toneMatrix(p.brightness, p.contrast, p.saturation, p.warmth, com.lumi.galeria.ui.Look.NONE, 0f, 0f)
    val table = p.curve.table()
    val cube = Array(n) { r -> Array(n) { g -> IntArray(n) { b ->
        val rr = r * 255f / (n - 1)
        val gg = g * 255f / (n - 1)
        val bb = b * 255f / (n - 1)
        fun channel(row: Int): Int {
            val v = m[row * 5] * rr + m[row * 5 + 1] * gg + m[row * 5 + 2] * bb + m[row * 5 + 4]
            return table[v.toInt().coerceIn(0, 255)].coerceIn(0, 255)
        }
        Color.rgb(channel(0), channel(1), channel(2))
    } } }
    return SingleColorLut.createFromCube(cube)
}

private fun ramp(us: Long, startUs: Long, lengthUs: Long): Float {
    // Si el reloj ya cuenta desde el principio de la película se resta lo que dura lo anterior; si no, se usa tal cual.
    val local = if (us >= startUs) us - startUs else us
    return (local.toFloat() / lengthUs).coerceIn(0f, 1f)
}

private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)

@androidx.annotation.OptIn(UnstableApi::class)
private fun dim(factor: (Long) -> Float) = RgbMatrix { us, _ ->
    val k = factor(us)
    floatArrayOf(k, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, 1f)
}

/** Cómo entra un trozo: se enciende, llega deslizándose o se acerca. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun entranceEffects(t: ClipTransition, startUs: Long): List<Effect> {
    val length = 500_000L
    return when (t) {
        ClipTransition.NONE -> emptyList()
        ClipTransition.FADE -> listOf(dim { ramp(it, startUs, length) })
        ClipTransition.SLIDE -> listOf(MatrixTransformation { us ->
            Matrix().apply { postTranslate((1f - easeOut(ramp(us, startUs, length))) * 2f, 0f) }
        })
        ClipTransition.ZOOM -> listOf(
            MatrixTransformation { us ->
                val s = 1.45f - 0.45f * easeOut(ramp(us, startUs, length))
                Matrix().apply { postScale(s, s) }
            },
            dim { ramp(it, startUs, length) },
        )
    }
}

/**
 * Los efectos de imagen de un trozo, en el orden en que se aplican: velocidad, giro, forma de la imagen (con el
 * fondo desenfocado si toca), color, ajustes y cómo entra. Sirven igual para verlo y para guardarlo.
 */
@androidx.annotation.OptIn(UnstableApi::class)
fun clipEffects(p: VideoProject, index: Int, withSpeed: Boolean): List<Effect> = buildList {
    val clip = p.clips[index]
    val (outW, outH) = outputSize(p)
    if (withSpeed && !clip.photo && clip.speed != 1f) add(SpeedChangeEffect(clip.speed))
    if (p.quarter % 4 != 0 || p.mirror) {
        add(
            ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(-90f * (p.quarter % 4))
                .setScale(if (p.mirror) -1f else 1f, 1f)
                .build(),
        )
    }
    // Forma: cómo se ajusta la imagen de este trozo al tamaño del vídeo final.
    val w = if (p.quarter % 2 == 0) clip.width else clip.height
    val h = if (p.quarter % 2 == 0) clip.height else clip.width
    if (w > 0 && h > 0) {
        val a = w.toFloat() / h
        val t = outW.toFloat() / outH
        when {
            kotlin.math.abs(a - t) < 0.01f -> add(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT))
            p.fit == FitMode.BLUR -> {
                add(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_STRETCH_TO_FIT))
                // Detrás, la imagen estirada y muy desenfocada; delante, la imagen con su forma.
                val sx = if (a > t) 1f else a / t
                val sy = if (a > t) t / a else 1f
                add(GaussianBlurWithFrameOverlaid(outW * 0.02f, sx, sy))
            }
            p.fit == FitMode.CROP -> add(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            else -> add(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT))
        }
    }
    addAll(lookEffects(p.look))
    adjustLut(p)?.let { add(it) }
    val start = p.startsMs()[index] * 1000L
    addAll(entranceEffects(clip.transition, start))
    // Si el siguiente entra con un fundido, este se apaga al terminar.
    if (p.clips.getOrNull(index + 1)?.transition == ClipTransition.FADE) {
        val end = start + clip.outMs * 1000L
        add(dim { us ->
            val local = if (us >= start) us else us + start
            ((end - local).toFloat() / 500_000f).coerceIn(0f, 1f)
        })
    }
}

// ---------- Textos ----------

@androidx.annotation.OptIn(UnstableApi::class)
private class TitleOverlay(val title: TitleItem, val outH: Int) : TextOverlay() {
    private val wrapped: String = wrap(title.text)

    override fun getText(presentationTimeUs: Long): SpannableString {
        val s = SpannableString(wrapped)
        val px = (outH * 0.062f).toInt().coerceAtLeast(18)
        val color = when (title.style) {
            TitleLook.GOLD -> 0xFFFFC83D.toInt()
            TitleLook.PINK -> 0xFFFF6FB5.toInt()
            else -> Color.WHITE
        }
        s.setSpan(AbsoluteSizeSpan(px), 0, s.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        s.setSpan(ForegroundColorSpan(color), 0, s.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        s.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, s.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        if (title.style == TitleLook.BOX) s.setSpan(BackgroundColorSpan(0xB3000000.toInt()), 0, s.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        return s
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val ms = presentationTimeUs / 1000
        val builder = OverlaySettings.Builder()
        if (ms < title.startMs || ms >= title.endMs) return builder.setAlphaScale(0f).build()
        val into = ((ms - title.startMs) / 350f).coerceIn(0f, 1f)
        val outOf = ((title.endMs - ms) / 350f).coerceIn(0f, 1f)
        val baseY = when (title.spot) { TitleSpot.TOP -> 0.74f; TitleSpot.CENTER -> 0f; TitleSpot.BOTTOM -> -0.72f }
        var y = baseY
        var scale = 1f
        var alpha = 1f
        when (title.anim) {
            TitleAnim.NONE -> Unit
            TitleAnim.FADE -> alpha = min(into, outOf)
            TitleAnim.SLIDE -> { alpha = min(into, outOf); y = baseY - (1f - easeOut(into)) * 0.12f + (1f - outOf) * 0.0f }
            TitleAnim.POP -> {
                val over = if (into < 1f) 0.55f + 0.5f * easeOut(into) else 1f
                scale = if (outOf < 1f) 0.55f + 0.45f * outOf else min(over, 1.04f).coerceAtLeast(0.55f)
                alpha = min(into * 1.5f, outOf).coerceIn(0f, 1f)
            }
        }
        return builder.setBackgroundFrameAnchor(0f, y).setOverlayFrameAnchor(0f, 0f).setScale(scale, scale).setAlphaScale(alpha).build()
    }

    private fun wrap(text: String): String {
        val out = StringBuilder()
        var line = 0
        for (word in text.trim().split(Regex("\\s+"))) {
            if (line + word.length > 22 && line > 0) { out.append('\n'); line = 0 } else if (line > 0) { out.append(' '); line++ }
            out.append(word)
            line += word.length
        }
        return out.toString()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun titleEffect(p: VideoProject): Effect? {
    if (p.titles.isEmpty()) return null
    val (_, outH) = outputSize(p)
    return OverlayEffect(ImmutableList.copyOf(p.titles.filter { it.text.isNotBlank() }.map { TitleOverlay(it, outH) }))
}

// ---------- Sonido ----------

@androidx.annotation.OptIn(UnstableApi::class)
private fun volumeProcessor(volume: Float): AudioProcessor = ChannelMixingAudioProcessor().apply {
    for (channels in 1..2) putChannelMixingMatrix(ChannelMixingMatrix.create(channels, channels).scaleBy(volume))
}

/** Baja el sonido hasta el silencio durante los últimos [fadeLenUs] antes del final. */
@androidx.annotation.OptIn(UnstableApi::class)
private class FadeOutProcessor(private val fadeStartUs: Long, private val fadeLenUs: Long) : BaseAudioProcessor() {
    private var frames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate
        val out = replaceOutputBuffer(inputBuffer.remaining())
        while (inputBuffer.remaining() >= 2 * channels) {
            val us = frames * 1_000_000L / rate
            val gain = if (us < fadeStartUs) 1f else max(0f, 1f - (us - fadeStartUs).toFloat() / fadeLenUs)
            repeat(channels) { out.putShort((inputBuffer.short * gain).toInt().coerceIn(-32768, 32767).toShort()) }
            frames++
        }
        out.flip()
    }

    override fun onFlush() { frames = 0L }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun clipAudio(p: VideoProject, clip: Clip): List<AudioProcessor> = buildList {
    if (!clip.photo && clip.speed != 1f) add(SonicAudioProcessor().apply { setSpeed(clip.speed) })
    if (p.volume < 0.999f) add(volumeProcessor(p.volume))
}

// ---------- Guardar ----------

private fun mediaItemOf(clip: Clip): androidx.media3.common.MediaItem {
    val builder = androidx.media3.common.MediaItem.Builder().setUri(Uri.parse(clip.uri))
    if (clip.photo) {
        builder.setImageDurationMs(clip.sourceMs)
    } else {
        builder.setClippingConfiguration(
            androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.startMs)
                .setEndPositionMs(if (clip.endMs in 1 until clip.sourceMs) clip.endMs else C.TIME_END_OF_SOURCE)
                .build(),
        )
    }
    return builder.build()
}

/** Los trozos tal como los lee el reproductor de la vista previa. */
fun previewItems(p: VideoProject): List<androidx.media3.common.MediaItem> = p.clips.map { mediaItemOf(it) }

/**
 * Guarda la película como vídeo nuevo, junto al primer trozo. Vuelve a codificar todo (H.264), así que
 * tarda un poco; [onProgress] va de 0 a 100.
 */
@androidx.annotation.OptIn(UnstableApi::class)
suspend fun exportProject(context: Context, p: VideoProject, onProgress: (Int) -> Unit): Boolean =
    withContext(Dispatchers.Main) {
        if (p.clips.isEmpty()) return@withContext false
        val temp = File(context.cacheDir, "editado.mp4")
        temp.delete()
        val done = CompletableDeferred<Boolean>()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { done.complete(true) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { done.complete(false) }
            })
            .build()
        val items = p.clips.mapIndexed { i, clip ->
            val builder = EditedMediaItem.Builder(mediaItemOf(clip))
                .setRemoveAudio(p.mute)
                .setEffects(Effects(if (p.mute) emptyList() else clipAudio(p, clip), clipEffects(p, i, withSpeed = true)))
            if (clip.photo) builder.setFrameRate(30)
            builder.build()
        }
        val sequences = ArrayList<EditedMediaItemSequence>()
        sequences += EditedMediaItemSequence(items)
        p.music?.let { music ->
            val totalMs = p.totalMs
            val available = (music.songMs - music.offsetMs).coerceAtLeast(1000L)
            val clipped = androidx.media3.common.MediaItem.Builder().setUri(Uri.parse(music.uri))
                .setClippingConfiguration(
                    androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(music.offsetMs)
                        .setEndPositionMs(music.offsetMs + min(available, totalMs))
                        .build(),
                ).build()
            val processors = ArrayList<AudioProcessor>()
            if (music.volume < 0.999f) processors += volumeProcessor(music.volume)
            // El fundido final solo si la canción llega hasta el final; si es corta, se repite sin fundido.
            if (music.fadeOut && available >= totalMs) {
                val fade = min(2500L, totalMs / 2)
                processors += FadeOutProcessor((totalMs - fade) * 1000L, fade * 1000L)
            }
            val track = EditedMediaItem.Builder(clipped).setRemoveVideo(true).setEffects(Effects(processors, emptyList())).build()
            sequences += EditedMediaItemSequence(listOf(track), available < totalMs)
        }
        val global = titleEffect(p)?.let { listOf(it) } ?: emptyList()
        val composition = Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), global))
            .experimentalSetForceAudioTrack((p.hasPhotos || p.music != null) && !(p.mute && p.music == null))
            .build()
        try {
            transformer.start(composition, temp.path)
            val holder = ProgressHolder()
            while (!done.isCompleted) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                delay(300)
            }
            done.await() && withContext(Dispatchers.IO) { saveEditedVideo(context, temp, p.clips.first()) }
        } finally {
            if (!done.isCompleted) runCatching { transformer.cancel() }
            temp.delete()
        }
    }

private fun saveEditedVideo(context: Context, file: File, first: Clip): Boolean {
    if (!file.exists() || file.length() == 0L) return false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, first.name.substringBeforeLast('.') + "_editado.mp4")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(first.path)) first.path else "Movies/Lumi/")
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
