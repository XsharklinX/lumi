package com.lumi.galeria.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.data.VideoEdit
import com.lumi.galeria.data.VideoFrame
import com.lumi.galeria.data.VideoLook
import com.lumi.galeria.data.videoEffects
import com.lumi.galeria.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class VideoTab(val label: String) { TRIM("Recortar"), SPEED("Velocidad"), FRAME("Encuadre"), COLOR("Filtros"), SOUND("Sonido") }

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 4f)

/**
 * Editor de vídeo: recortar el tiempo, cámara lenta o rápida, encuadre y giro, filtros y ajustes
 * de color, y quitar o bajar el sonido. Lo que se toca se ve en el acto en el reproductor de
 * arriba; al guardar se crea un vídeo nuevo y el original no se toca.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoEditorScreen(screen: Screen.VideoEditor, state: UiState, vm: LumiViewModel) {
    val item = state.items.firstOrNull { it.id == screen.id && it.isVideo }
    if (item == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    val length = item.duration.coerceAtLeast(1L)
    var edit by remember { mutableStateOf(VideoEdit(endMs = length)) }
    var tab by remember { mutableStateOf(VideoTab.TRIM) }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    val progress = vm.videoExport

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    // Lo que se ve es lo que se guardará: mismos efectos de imagen, misma velocidad y mismo volumen.
    LaunchedEffect(edit.look, edit.brightness, edit.contrast, edit.saturation, edit.frame, edit.quarter, edit.mirror) {
        runCatching { player.setVideoEffects(videoEffects(edit, withSpeed = false)) }
    }
    LaunchedEffect(edit.speed) { player.setPlaybackSpeed(edit.speed) }
    LaunchedEffect(edit.mute, edit.volume) { player.volume = if (edit.mute) 0f else edit.volume }
    // Se repite solo el tramo elegido.
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            if (position >= edit.endMs - 50 || position < edit.startMs - 200 || player.playbackState == Player.STATE_ENDED) {
                player.seekTo(edit.startMs)
                if (playing) player.play()
            }
            delay(60)
        }
    }

    val frames by produceState<List<Bitmap>>(emptyList(), item.id) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val shots = runCatching {
                retriever.setDataSource(context, item.uri)
                (0 until 8).mapNotNull { i ->
                    retriever.getScaledFrameAtTime(length * 1000 * (2 * i + 1) / 16, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 160)
                }
            }.getOrDefault(emptyList())
            runCatching { retriever.release() }
            shots
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false, enabled = progress == null)
            Text("Editar vídeo", style = HeadingStyle, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            PillButton(
                if (progress != null) "Guardando $progress %" else "Guardar vídeo",
                {
                    player.pause()
                    playing = false
                    vm.exportVideo(item, edit)
                },
                enabled = progress == null && edit != VideoEdit(endMs = length),
            )
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().clickable {
                playing = !playing
                if (playing) player.play() else player.pause()
            },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (!playing) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, "Reproducir", Modifier.size(36.dp), tint = Color.White)
                }
            }
            if (progress != null) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Guardando el vídeo nuevo… $progress %", style = LabelStyle, color = Color.White)
                    LinearProgressIndicator(
                        progress = { progress / 100f }, color = Lumi.Accent, trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    )
                }
            }
        }

        // Tira con los fotogramas, el tramo elegido y por dónde va.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(46.dp).clip(RoundedCornerShape(10.dp))) {
            Row(Modifier.fillMaxSize()) {
                frames.forEach { frame ->
                    Image(frame.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                }
            }
            val from = edit.startMs.toFloat() / length
            val to = edit.endMs.toFloat() / length
            Box(Modifier.fillMaxHeight().width(maxWidth * from).background(Color.Black.copy(alpha = 0.6f)))
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(maxWidth * (1f - to)).background(Color.Black.copy(alpha = 0.6f)))
            Box(Modifier.offset(x = maxWidth * from).fillMaxHeight().width(maxWidth * (to - from)).border(2.5.dp, Lumi.Accent, RoundedCornerShape(10.dp)))
            Box(Modifier.offset(x = maxWidth * (position.toFloat() / length).coerceIn(0f, 1f)).fillMaxHeight().width(2.dp).background(Color.White))
        }

        Column(Modifier.background(Lumi.Bg).padding(top = 6.dp, bottom = 10.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                VideoTab.entries.forEach { option ->
                    val on = option == tab
                    Text(
                        option.label, style = LabelStyle, color = if (on) Lumi.Accent else Lumi.Muted,
                        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { tab = option }.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
            Column(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    VideoTab.TRIM -> {
                        val kept = edit.endMs - edit.startMs
                        Text("De ${formatDuration(edit.startMs)} a ${formatDuration(edit.endMs)}", style = HeadingStyle.copy(fontSize = 18.sp))
                        RangeSlider(
                            value = edit.startMs.toFloat() / length..edit.endMs.toFloat() / length,
                            onValueChange = { range ->
                                val start = (range.start * length).toLong()
                                val end = (range.endInclusive * length).toLong()
                                if (end - start >= 500) {
                                    if (start != edit.startMs) player.seekTo(start) else player.seekTo((end - 1500).coerceAtLeast(start))
                                    edit = edit.copy(startMs = start, endMs = end)
                                }
                            },
                            colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                        )
                        Text(
                            "Dura ${formatDuration((kept / edit.speed).toLong())}" + if (edit.speed != 1f) " con la velocidad elegida" else "",
                            style = SmallStyle,
                        )
                    }
                    VideoTab.SPEED -> {
                        Text(
                            when {
                                edit.speed < 1f -> "Cámara lenta: ${speedText(edit.speed)}"
                                edit.speed > 1f -> "Más rápido: ${speedText(edit.speed)}"
                                else -> "Velocidad normal"
                            },
                            style = HeadingStyle.copy(fontSize = 18.sp),
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SPEEDS.forEach { speed -> EditChip(speedText(speed), speed == edit.speed) { edit = edit.copy(speed = speed) } }
                        }
                        Text("El sonido se ajusta a la velocidad sin volverse agudo ni grave.", style = SmallStyle)
                    }
                    VideoTab.FRAME -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VideoFrame.entries.forEach { frame -> EditChip(frame.label, frame == edit.frame) { edit = edit.copy(frame = frame) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditChip("Girar 90°", edit.quarter % 4 != 0) { edit = edit.copy(quarter = (edit.quarter + 1) % 4) }
                            EditChip("Espejo", edit.mirror) { edit = edit.copy(mirror = !edit.mirror) }
                        }
                        Text("El encuadre recorta por el centro para llenar la forma elegida.", style = SmallStyle)
                    }
                    VideoTab.COLOR -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VideoLook.entries.forEach { look -> EditChip(look.label, look == edit.look) { edit = edit.copy(look = look) } }
                        }
                        var knob by remember { mutableIntStateOf(0) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("Luz", "Contraste", "Color").forEachIndexed { i, name -> EditChip(name, knob == i) { knob = i } }
                        }
                        val value = when (knob) {
                            0 -> edit.brightness / 0.5f
                            1 -> edit.contrast / 0.6f
                            else -> edit.saturation / 100f
                        }
                        Slider(
                            value, { v ->
                                edit = when (knob) {
                                    0 -> edit.copy(brightness = v * 0.5f)
                                    1 -> edit.copy(contrast = v * 0.6f)
                                    else -> edit.copy(saturation = v * 100f)
                                }
                            },
                            valueRange = -1f..1f,
                            colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                        )
                    }
                    VideoTab.SOUND -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditChip("Con sonido", !edit.mute) { edit = edit.copy(mute = false) }
                            EditChip("Sin sonido", edit.mute) { edit = edit.copy(mute = true) }
                        }
                        if (!edit.mute) {
                            Text("Volumen: ${(edit.volume * 100).toInt()} %", style = LabelStyle)
                            Slider(
                                edit.volume, { edit = edit.copy(volume = it) },
                                colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                            )
                        } else {
                            Text("El vídeo nuevo se guarda sin pista de sonido.", style = SmallStyle)
                        }
                    }
                }
            }
        }
    }
}

private fun speedText(speed: Float): String =
    (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().replace('.', ',')) + "×"

@Composable
private fun EditChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, style = LabelStyle, color = if (on) Lumi.OnAccent else Lumi.Ink,
        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Surface).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
