package com.lumi.galeria.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.data.Clip
import com.lumi.galeria.data.FitMode
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.data.MusicTrack
import com.lumi.galeria.data.TitleAnim
import com.lumi.galeria.data.TitleItem
import com.lumi.galeria.data.TitleSpot
import com.lumi.galeria.data.TitleLook
import com.lumi.galeria.data.ClipTransition
import com.lumi.galeria.data.VideoFormat
import com.lumi.galeria.data.VideoLook
import com.lumi.galeria.data.VideoProject
import com.lumi.galeria.data.clipEffects
import com.lumi.galeria.data.previewItems
import com.lumi.galeria.formatDuration
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class VideoTab(val label: String) {
    CUT("Cortar"), SPEED("Velocidad"), ADD("Añadir"), TEXT("Textos"), MUSIC("Música"), FORMAT("Formato"), COLOR("Color"), SOUND("Sonido"),
}

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 4f)

private fun clipOf(item: MediaItem): Clip = Clip(
    id = System.nanoTime(), uri = item.uri.toString(), name = item.name, path = item.path, photo = !item.isVideo,
    width = item.shownWidth, height = item.shownHeight,
    sourceMs = if (item.isVideo) item.duration.coerceAtLeast(1L) else 3000L,
    endMs = if (item.isVideo) item.duration.coerceAtLeast(1L) else 3000L,
)

/**
 * Editor de vídeo: una línea de tiempo con los fotogramas, donde se corta, se quita lo que sobra y se
 * juntan varios vídeos y fotos con transiciones; textos que aparecen cuando se dice, música de fondo, formato
 * para redes con fondo desenfocado, velocidad por tramos y los mismos ajustes de color que las fotos.
 * Lo que se toca se ve en el reproductor de arriba; al guardar se crea un vídeo nuevo y los originales no se tocan.
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
    val scope = rememberCoroutineScope()
    val initial = remember { VideoProject(listOf(clipOf(item))) }
    var project by remember { mutableStateOf(initial) }
    val undo = remember { mutableStateListOf<VideoProject>() }
    var selected by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(VideoTab.CUT) }
    var playing by remember { mutableStateOf(true) }
    var index by remember { mutableIntStateOf(0) }
    var inClip by remember { mutableLongStateOf(0L) }
    var picker by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf<TitleItem?>(null) }
    var titleId by remember { mutableStateOf<Long?>(null) }
    val progress = vm.videoExport

    /** Un cambio que se puede deshacer. */
    fun change(next: VideoProject) {
        undo.add(project)
        if (undo.size > 40) undo.removeAt(0)
        project = next
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
        }
    }
    val song = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release(); song.release() } }

    // Los trozos del reproductor se rehacen cuando cambia lo que hay en la línea de tiempo.
    val structure = project.clips.map { listOf(it.id, it.startMs, it.endMs, it.sourceMs, it.photo) }
    LaunchedEffect(structure) {
        val keep = selected.coerceIn(0, project.clips.lastIndex)
        player.setMediaItems(previewItems(project), keep, 0L)
        player.prepare()
        if (playing) player.play()
    }
    // Lo que se ve es lo que se guardará: mismos efectos de imagen. La velocidad y el volumen, en el reproductor.
    LaunchedEffect(project, index) {
        val i = index.coerceIn(0, project.clips.lastIndex)
        runCatching { player.setVideoEffects(clipEffects(project, i, withSpeed = false)) }
        player.setPlaybackSpeed(if (project.clips[i].photo) 1f else project.clips[i].speed)
        player.volume = if (project.mute) 0f else project.volume
    }
    // La canción de fondo suena con el reproductor.
    LaunchedEffect(project.music?.uri) {
        val m = project.music
        if (m == null) { song.stop(); song.clearMediaItems() } else {
            song.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(m.uri)))
            song.prepare()
        }
    }
    LaunchedEffect(project.music?.volume) { song.volume = project.music?.volume ?: 0f }

    fun globalMs(): Long {
        val i = index.coerceIn(0, project.clips.lastIndex)
        val c = project.clips[i]
        return project.startsMs()[i] + if (c.photo) inClip else (inClip / c.speed).toLong()
    }

    LaunchedEffect(Unit) {
        var lastIndex = -1
        while (true) {
            index = player.currentMediaItemIndex
            inClip = player.currentPosition
            if (index != lastIndex) { lastIndex = index; selected = index.coerceIn(0, max(project.clips.lastIndex, 0)) }
            val m = project.music
            if (m != null) {
                val want = m.offsetMs + globalMs()
                if (playing && !song.isPlaying && song.playbackState == Player.STATE_READY) { song.seekTo(want); song.play() }
                if (!playing && song.isPlaying) song.pause()
                if (playing && kotlin.math.abs(song.currentPosition - want) > 600) song.seekTo(want)
            }
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0, 0L)
                if (playing) player.play()
            }
            delay(60)
        }
    }

    fun seekTo(clipIndex: Int, posMs: Long) {
        player.seekTo(clipIndex, posMs)
        selected = clipIndex
    }

    val pickSong = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val info = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                val duration = runCatching {
                    retriever.setDataSource(context, uri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                }.getOrNull() ?: 0L
                runCatching { retriever.release() }
                val name = runCatching {
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull() ?: "Canción"
                name to duration
            }
            if (info.second <= 0L) vm.say("No se puede leer esa canción") else {
                change(project.copy(music = MusicTrack(uri.toString(), info.first.substringBeforeLast('.'), info.second)))
                vm.discover("musica_video")
            }
        }
    }

    val starts = project.startsMs()
    val total = project.totalMs
    val changed = project != initial

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false, enabled = progress == null)
            Text("Editar vídeo", style = HeadingStyle, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            PillButton(
                if (progress != null) "Guardando $progress %" else "Guardar vídeo",
                {
                    player.pause(); song.pause()
                    playing = false
                    vm.exportProject(project)
                },
                enabled = progress == null && changed,
            )
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().clickable {
                playing = !playing
                if (playing) player.play() else { player.pause(); song.pause() }
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
            // Los textos, como se verán: aparecen y desaparecen en su momento.
            val now = globalMs()
            project.titles.filter { now >= it.startMs && now < it.endMs && it.text.isNotBlank() }.forEach { title -> TitlePreview(title) }
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

        Text(
            "${formatDuration(globalMs())} / ${formatDuration(total)}" + if (project.clips.size > 1) " · ${project.clips.size} trozos" else "",
            style = SmallStyle, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )

        // Línea de tiempo: un bloque por trozo, con sus fotogramas. Tocar uno lo elige y lleva allí el reproductor.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val available = maxWidth - 32.dp
            val minWidth = 56.dp
            val widths: List<Dp> = project.clips.map { c -> max(minWidth.value, available.value * c.outMs / max(total, 1L)).dp }
            fun xOf(ms: Long): Dp {
                var acc = 0.dp
                project.clips.forEachIndexed { i, c ->
                    if (ms < starts[i] + c.outMs || i == project.clips.lastIndex) {
                        val f = ((ms - starts[i]).toFloat() / c.outMs).coerceIn(0f, 1f)
                        return acc + widths[i] * f
                    }
                    acc += widths[i]
                }
                return acc
            }
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        project.clips.forEachIndexed { i, c ->
                            ClipBlock(c, widths[i], i == selected) { seekTo(i, 0L) }
                        }
                    }
                    Box(Modifier.offset(x = xOf(globalMs())).fillMaxHeight().width(2.dp).background(Color.White))
                }
                // Textos y música, bajo los trozos, en su momento.
                if (project.titles.isNotEmpty() || project.music != null) {
                    Box(Modifier.width(widths.fold(0.dp) { a, b -> a + b }).height(16.dp)) {
                        project.titles.forEach { t ->
                            val x0 = xOf(t.startMs)
                            val x1 = xOf(t.endMs)
                            Box(
                                Modifier.offset(x = x0).width((x1 - x0).coerceAtLeast(10.dp)).height(14.dp).clip(RoundedCornerShape(7.dp))
                                    .background(if (t.id == titleId) Lumi.Accent else Lumi.Accent.copy(alpha = 0.55f))
                                    .clickable { titleId = t.id; tab = VideoTab.TEXT },
                            )
                        }
                    }
                }
            }
        }

        Column(Modifier.background(Lumi.Bg).padding(top = 6.dp, bottom = 10.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                VideoTab.entries.forEach { option ->
                    val on = option == tab
                    Text(
                        option.label, style = LabelStyle, color = if (on) Lumi.Accent else Lumi.Muted,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).clip(CircleShape).background(if (on) Lumi.Accent.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { tab = option }.padding(horizontal = 14.dp, vertical = 14.dp),
                    )
                }
                Text(
                    "Deshacer", style = LabelStyle, color = if (undo.isNotEmpty()) Lumi.Accent else Lumi.Muted,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = undo.isNotEmpty()) {
                        project = undo.removeAt(undo.lastIndex)
                        selected = selected.coerceIn(0, project.clips.lastIndex)
                    }.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            Column(
                Modifier.fillMaxWidth().height(176.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val sel = project.clips.getOrNull(selected)
                when (tab) {
                    VideoTab.CUT -> if (sel != null) {
                        Text(
                            (if (sel.photo) "Foto" else "Vídeo") + " ${selected + 1} de ${project.clips.size} · dura ${formatDuration(sel.outMs)}",
                            style = HeadingStyle.copy(fontSize = 17.sp),
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PhotoChip("Dividir aquí", false) {
                                // Solo en el trozo que se está viendo, y no pegado a sus extremos.
                                if (index != selected) { vm.say("Lleva el reproductor a este trozo para dividirlo"); return@PhotoChip }
                                val at = if (sel.photo) inClip else sel.startMs + inClip
                                val from = if (sel.photo) 0L else sel.startMs
                                val to = if (sel.photo) sel.sourceMs else sel.endMs
                                if (at - from < 300 || to - at < 300) { vm.say("Elige un punto más lejos de los extremos"); return@PhotoChip }
                                val a = if (sel.photo) sel.copy(sourceMs = at, endMs = at) else sel.copy(endMs = at)
                                val b = (if (sel.photo) sel.copy(sourceMs = sel.sourceMs - at, endMs = sel.sourceMs - at) else sel.copy(startMs = at))
                                    .copy(id = System.nanoTime(), transition = ClipTransition.NONE)
                                change(project.copy(clips = project.clips.take(selected) + a + b + project.clips.drop(selected + 1)))
                                vm.discover("editor_video")
                            }
                            PhotoChip("Quitar este trozo", false) {
                                if (project.clips.size <= 1) { vm.say("Tiene que quedar al menos un trozo"); return@PhotoChip }
                                change(project.copy(clips = project.clips.filterIndexed { i, _ -> i != selected }))
                                selected = selected.coerceAtMost(project.clips.lastIndex)
                            }
                            PhotoChip("← Mover", false) {
                                if (selected > 0) {
                                    val l = project.clips.toMutableList(); l.add(selected - 1, l.removeAt(selected))
                                    change(project.copy(clips = l)); selected -= 1
                                }
                            }
                            PhotoChip("Mover →", false) {
                                if (selected < project.clips.lastIndex) {
                                    val l = project.clips.toMutableList(); l.add(selected + 1, l.removeAt(selected))
                                    change(project.copy(clips = l)); selected += 1
                                }
                            }
                        }
                        if (sel.photo) {
                            Text("Dura ${sel.sourceMs / 1000f} s".replace('.', ','), style = LabelStyle)
                            Slider(
                                sel.sourceMs / 1000f, { v -> setClip(project, selected) { it.copy(sourceMs = (v * 1000).toLong(), endMs = (v * 1000).toLong()) }.let { project = it } },
                                valueRange = 1f..10f,
                                colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                            )
                        } else {
                            var range by remember(sel.id, sel.startMs, sel.endMs) { mutableStateOf(sel.startMs.toFloat()..sel.endMs.toFloat()) }
                            Text("Del ${formatDuration(range.start.toLong())} al ${formatDuration(range.endInclusive.toLong())} del vídeo original", style = SmallStyle)
                            RangeSlider(
                                value = range,
                                onValueChange = { r -> if (r.endInclusive - r.start >= 500f) range = r },
                                onValueChangeFinished = {
                                    change(setClip(project, selected) { it.copy(startMs = range.start.toLong(), endMs = range.endInclusive.toLong()) })
                                },
                                valueRange = 0f..sel.sourceMs.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                            )
                        }
                    }
                    VideoTab.SPEED -> if (sel != null) {
                        if (sel.photo) {
                            Text("Las fotos no tienen velocidad: cambia cuánto duran en «Cortar».", style = SmallStyle)
                        } else {
                            Text(
                                when {
                                    sel.speed < 1f -> "Este trozo en cámara lenta: ${speedText(sel.speed)}"
                                    sel.speed > 1f -> "Este trozo más rápido: ${speedText(sel.speed)}"
                                    else -> "Este trozo a velocidad normal"
                                },
                                style = HeadingStyle.copy(fontSize = 17.sp),
                            )
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SPEEDS.forEach { speed -> PhotoChip(speedText(speed), speed == sel.speed) { change(setClip(project, selected) { it.copy(speed = speed) }); vm.discover("velocidad_tramos") } }
                            }
                            PhotoChip("Poner esta velocidad a todos los trozos", false) {
                                change(project.copy(clips = project.clips.map { if (it.photo) it else it.copy(speed = sel.speed) }))
                            }
                            Text("Cada trozo puede tener su velocidad. El sonido se ajusta sin volverse agudo ni grave.", style = SmallStyle)
                        }
                    }
                    VideoTab.ADD -> {
                        PillButton("Añadir vídeos o fotos de la galería", { picker = true }, modifier = Modifier.fillMaxWidth())
                        if (sel != null) {
                            Text("Cómo entra el trozo ${selected + 1}", style = LabelStyle)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ClipTransition.entries.forEach { t -> PhotoChip(t.label, sel.transition == t) { change(setClip(project, selected) { it.copy(transition = t) }); vm.discover("unir_videos") } }
                                PhotoChip("Igual a todos", false) { change(project.copy(clips = project.clips.mapIndexed { i, c -> if (i == 0) c else c.copy(transition = sel.transition) })) }
                            }
                            Text("«Fundido» enciende la imagen desde negro, «Deslizar» la trae de un lado y «Acercar» la acerca. Se ven al guardar.", style = SmallStyle)
                        }
                    }
                    VideoTab.TEXT -> {
                        PillButton("Añadir un texto aquí", { writing = TitleItem(System.nanoTime(), "", globalMs(), min(globalMs() + 3000, total)) }, modifier = Modifier.fillMaxWidth())
                        val t = project.titles.firstOrNull { it.id == titleId }
                        if (project.titles.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                project.titles.forEach { x -> PhotoChip(x.text.take(14).ifBlank { "Texto" }, x.id == titleId) { titleId = x.id } }
                            }
                        }
                        if (t != null) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TitleLook.entries.forEach { s -> PhotoChip(s.label, t.style == s) { change(setTitle(project, t.id) { it.copy(style = s) }) } }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TitleAnim.entries.forEach { a -> PhotoChip(a.label, t.anim == a) { change(setTitle(project, t.id) { it.copy(anim = a) }) } }
                                TitleSpot.entries.forEach { s -> PhotoChip(s.label, t.spot == s) { change(setTitle(project, t.id) { it.copy(spot = s) }) } }
                            }
                            var span by remember(t.id, t.startMs, t.endMs) { mutableStateOf(t.startMs.toFloat()..t.endMs.toFloat()) }
                            Text("Se ve de ${formatDuration(span.start.toLong())} a ${formatDuration(span.endInclusive.toLong())}", style = SmallStyle)
                            RangeSlider(
                                value = span, onValueChange = { r -> if (r.endInclusive - r.start >= 500f) span = r },
                                onValueChangeFinished = { change(setTitle(project, t.id) { it.copy(startMs = span.start.toLong(), endMs = span.endInclusive.toLong()) }) },
                                valueRange = 0f..max(total, 1000L).toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PhotoChip("Cambiar el texto", false) { writing = t }
                                PhotoChip("Quitar", false) { change(project.copy(titles = project.titles.filter { it.id != t.id })); titleId = null }
                            }
                        } else if (project.titles.isEmpty()) {
                            Text("Lleva el reproductor al momento en que quieras el texto y pulsa «Añadir un texto aquí».", style = SmallStyle)
                        }
                    }
                    VideoTab.MUSIC -> {
                        val m = project.music
                        if (m == null) {
                            PillButton("Elegir una canción del móvil", { pickSong.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth())
                            Text("Suena de fondo junto al sonido original. Con «Sonido» bajas o quitas el original.", style = SmallStyle)
                        } else {
                            Text(m.name, style = HeadingStyle.copy(fontSize = 17.sp), maxLines = 1)
                            Text("Volumen de la canción: ${(m.volume * 100).toInt()} %", style = LabelStyle)
                            Slider(
                                m.volume, { project = project.copy(music = m.copy(volume = it)) },
                                colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                            )
                            Text("Empieza en el minuto ${formatDuration(m.offsetMs)}", style = SmallStyle)
                            var offset by remember(m.uri, m.offsetMs) { mutableStateOf(m.offsetMs.toFloat()) }
                            Slider(
                                offset, { offset = it }, onValueChangeFinished = { change(project.copy(music = m.copy(offsetMs = offset.toLong()))) },
                                valueRange = 0f..max(m.songMs - 1000L, 1L).toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PhotoChip("Se apaga poco a poco al final", m.fadeOut) { change(project.copy(music = m.copy(fadeOut = !m.fadeOut))) }
                                PhotoChip("Quitar la canción", false) { change(project.copy(music = null)) }
                            }
                        }
                    }
                    VideoTab.FORMAT -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VideoFormat.entries.forEach { f -> PhotoChip(f.label, f == project.format) { change(project.copy(format = f)); vm.discover("formato_redes") } }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FitMode.entries.forEach { f -> PhotoChip(f.label, f == project.fit) { change(project.copy(fit = f)) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PhotoChip("Girar 90°", project.quarter % 4 != 0) { change(project.copy(quarter = (project.quarter + 1) % 4)) }
                            PhotoChip("Espejo", project.mirror) { change(project.copy(mirror = !project.mirror)) }
                        }
                        Text("Si lo grabado no tiene esa forma: el fondo desenfocado rellena los lados con la misma imagen difuminada, en vez de franjas negras.", style = SmallStyle)
                    }
                    VideoTab.COLOR -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VideoLook.entries.forEach { look -> PhotoChip(look.label, look == project.look) { change(project.copy(look = look)) } }
                        }
                        var knob by remember { mutableIntStateOf(0) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("Luz", "Contraste", "Color", "Calidez", "Curva").forEachIndexed { i, name -> PhotoChip(name, knob == i) { knob = i } }
                            PhotoChip("Lumi Auto", false) {
                                scope.launch {
                                    val c = project.clips.getOrNull(selected) ?: project.clips.first()
                                    val frame = withContext(Dispatchers.IO) { frameOf(context, c) }
                                    if (frame == null) vm.say("No se pudo mirar la imagen") else {
                                        val auto = autoLevels(frame)
                                        change(project.copy(brightness = auto[0], contrast = auto[1], saturation = auto[2]))
                                        vm.discover("editor_video")
                                    }
                                }
                            }
                            PhotoChip("Quitar ajustes", false) { change(project.copy(look = VideoLook.NONE, brightness = 0f, contrast = 0f, saturation = 0f, warmth = 0f, curve = com.lumi.galeria.data.Curve())) }
                        }
                        if (knob == 4) {
                            CurveEditor(project.curve, com.lumi.galeria.data.Channel.ALL, null) { project = project.copy(curve = it) }
                        } else {
                            val value = when (knob) { 0 -> project.brightness; 1 -> project.contrast; 2 -> project.saturation; else -> project.warmth }
                            Slider(
                                value, { v ->
                                    val z = if (kotlin.math.abs(v) < 0.04f) 0f else v
                                    project = when (knob) { 0 -> project.copy(brightness = z); 1 -> project.copy(contrast = z); 2 -> project.copy(saturation = z); else -> project.copy(warmth = z) }
                                },
                                valueRange = -1f..1f,
                                colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                            )
                        }
                    }
                    VideoTab.SOUND -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PhotoChip("Con sonido", !project.mute) { change(project.copy(mute = false)) }
                            PhotoChip("Sin sonido", project.mute) { change(project.copy(mute = true)) }
                        }
                        if (!project.mute) {
                            Text("Volumen del sonido original: ${(project.volume * 100).toInt()} %", style = LabelStyle)
                            Slider(
                                project.volume, { project = project.copy(volume = it) },
                                colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                            )
                        } else {
                            Text("El vídeo nuevo se guarda sin el sonido original" + if (project.music != null) ", solo con la canción." else ".", style = SmallStyle)
                        }
                    }
                }
            }
        }
    }

    if (picker) {
        PickMedia(state, onDone = { chosen ->
            picker = false
            if (chosen.isNotEmpty()) {
                change(project.copy(clips = project.clips + chosen.map { clipOf(it) }))
                selected = project.clips.lastIndex
                vm.discover("unir_videos")
            }
        }, onDismiss = { picker = false })
    }
    writing?.let { draft ->
        var text by remember(draft.id) { mutableStateOf(draft.text) }
        AlertDialog(
            onDismissRequest = { writing = null },
            containerColor = Lumi.Surface,
            title = { Text("Texto en el vídeo", style = HeadingStyle) },
            text = { OutlinedTextField(text, { text = it.take(60) }, placeholder = { Text("Por ejemplo: Verano 2026") }, shape = RoundedCornerShape(16.dp), minLines = 1, maxLines = 3) },
            confirmButton = {
                Text(
                    "Listo", style = LabelStyle, color = if (text.isBlank()) Lumi.Muted else Lumi.Accent,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = text.isNotBlank()) {
                        val done = draft.copy(text = text.trim())
                        change(if (project.titles.any { it.id == draft.id }) setTitle(project, draft.id) { done } else project.copy(titles = project.titles + done))
                        titleId = draft.id
                        writing = null
                        vm.discover("titulos_video")
                    }.padding(12.dp),
                )
            },
            dismissButton = { Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { writing = null }.padding(12.dp)) },
        )
    }
}

private fun setClip(p: VideoProject, index: Int, f: (Clip) -> Clip): VideoProject =
    p.copy(clips = p.clips.mapIndexed { i, c -> if (i == index) f(c) else c })

private fun setTitle(p: VideoProject, id: Long, f: (TitleItem) -> TitleItem): VideoProject =
    p.copy(titles = p.titles.map { if (it.id == id) f(it) else it })

/** Un fotograma de [clip], para que Lumi Auto mire cómo está de clara. */
private fun frameOf(context: android.content.Context, clip: Clip): Bitmap? {
    if (clip.photo) {
        return decodeEditable(context, Uri.parse(clip.uri))?.let { b ->
            val k = 320f / max(b.width, b.height)
            if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
        }
    }
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, Uri.parse(clip.uri))
        retriever.getFrameAtTime((clip.startMs + clip.keptMs / 2) * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/** Un trozo de la línea de tiempo con sus fotogramas. */
@Composable
private fun ClipBlock(clip: Clip, width: Dp, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val count = (width.value / 52f).toInt().coerceIn(1, 8)
    val frames by produceState<List<Bitmap>>(emptyList(), clip.id, clip.startMs, clip.endMs, count) {
        value = withContext(Dispatchers.IO) {
            if (clip.photo) {
                listOfNotNull(frameOf(context, clip))
            } else {
                val retriever = MediaMetadataRetriever()
                val shots = runCatching {
                    retriever.setDataSource(context, Uri.parse(clip.uri))
                    (0 until count).mapNotNull { i ->
                        retriever.getScaledFrameAtTime((clip.startMs + clip.keptMs * (2 * i + 1) / (2 * count)) * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 160)
                    }
                }.getOrDefault(emptyList())
                runCatching { retriever.release() }
                shots
            }
        }
    }
    Box(
        Modifier.width(width).height(50.dp).padding(horizontal = 1.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF222226))
            .then(if (selected) Modifier.border(2.5.dp, Lumi.Accent, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxSize()) {
            frames.forEach { f -> Image(f.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop) }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (clip.speed != 1f) Text(speedText(clip.speed), style = SmallStyle.copy(fontSize = 10.sp), color = Color.White, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 5.dp))
            if (clip.transition != ClipTransition.NONE) Text("✦", style = SmallStyle.copy(fontSize = 10.sp), color = Color.White, modifier = Modifier.clip(CircleShape).background(Lumi.Accent.copy(alpha = 0.8f)).padding(horizontal = 5.dp))
        }
        Text(
            formatDuration(clip.outMs), style = SmallStyle.copy(fontSize = 10.sp), color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 5.dp),
        )
    }
}

/** El texto sobre el reproductor, parecido a como saldrá. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.TitlePreview(title: TitleItem) {
    val color = when (title.style) { TitleLook.GOLD -> Color(0xFFFFC83D); TitleLook.PINK -> Color(0xFFFF6FB5); else -> Color.White }
    val align = when (title.spot) { TitleSpot.TOP -> Alignment.TopCenter; TitleSpot.CENTER -> Alignment.Center; TitleSpot.BOTTOM -> Alignment.BottomCenter }
    Text(
        title.text, style = HeadingStyle.copy(fontSize = 22.sp), color = color,
        modifier = Modifier.align(align).padding(horizontal = 24.dp, vertical = 28.dp)
            .then(if (title.style == TitleLook.BOX) Modifier.background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 4.dp) else Modifier),
    )
}

/** Elegir vídeos y fotos de la galería para añadirlos a la película. */
@Composable
private fun PickMedia(state: UiState, onDone: (List<MediaItem>) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(emptyList<Long>()) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Cancelar", onDismiss, primary = false)
                Text(if (chosen.isEmpty()) "Elige vídeos o fotos" else "${chosen.size} elegidos", style = HeadingStyle, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
                PillButton("Añadir", { onDone(chosen.mapNotNull { id -> state.items.firstOrNull { it.id == id } }) }, enabled = chosen.isNotEmpty())
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4), modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.items.filter { !it.isExternal && !it.isGif }, key = { it.id }) { it2 ->
                    PhotoTile(
                        item = it2, px = 256, selected = it2.id in chosen,
                        modifier = Modifier.aspectRatio(1f).clickable { chosen = if (it2.id in chosen) chosen - it2.id else chosen + it2.id },
                    )
                }
            }
        }
    }
}

private fun speedText(speed: Float): String =
    (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().replace('.', ',')) + "×"
