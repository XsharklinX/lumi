package com.lumi.galeria.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.data.motionVideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.withContext
import java.io.File

/** El vídeo que lleva dentro una foto en movimiento, sacado a un archivo temporal. null mientras se saca. */
@Composable
private fun rememberMotionFile(item: MediaItem): File? {
    val context = LocalContext.current
    val file by produceState<File?>(null, item.id, item.modified) {
        value = withContext(Dispatchers.IO) { motionVideoFile(context, item) }
    }
    return file
}

@Composable
private fun rememberLoopingPlayer(file: File?): ExoPlayer? {
    val context = LocalContext.current
    val player = remember(file) {
        file?.let {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(it)))
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                prepare()
            }
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    return player
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PlayerSurface(player: ExoPlayer, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

/** Mientras se mantiene el dedo en una foto en movimiento, se mueve encima de ella. */
@Composable
fun MotionLayer(item: MediaItem, modifier: Modifier = Modifier) {
    val player = rememberLoopingPlayer(rememberMotionFile(item)) ?: return
    PlayerSurface(player, modifier)
}

/**
 * La foto en movimiento a pantalla completa: se repite sola, se puede parar en cualquier instante
 * y guardar ese fotograma como foto, o guardar el vídeo entero aparte.
 */
@Composable
fun MotionScreen(item: MediaItem, vm: LumiViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val file = rememberMotionFile(item)
    val player = rememberLoopingPlayer(file)
    var length by remember { mutableLongStateOf(0L) }
    var at by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Larga exposición: null si no se ha hecho; mientras se calcula, [exposing].
    var exposure by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var exposing by remember { mutableStateOf(false) }
    var trails by remember { mutableStateOf(false) }

    fun expose(lights: Boolean) {
        trails = lights
        exposing = true
        player?.pause()
        scope.launch {
            val made = withContext(Dispatchers.Default) { com.lumi.galeria.data.longExposure(context, item, lights) }
            exposing = false
            if (made == null) vm.say("No se pudo hacer la larga exposición") else exposure = made
        }
    }
    LaunchedEffect(player) {
        while (player != null) {
            if (player.duration > 0) length = player.duration
            if (!dragging && length > 0) at = player.currentPosition.toFloat() / length
            delay(50)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        exposure?.let { made ->
            Column(
                Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (trails) "Estela de luces" else "Larga exposición", style = HeadingStyle, color = Color.White)
                androidx.compose.foundation.Image(
                    made.asImageBitmap(), null, Modifier.weight(1f).fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                Text(
                    if (trails) "Lo más claro de cada instante: las luces que se mueven dejan su rastro." else "Los fotogramas superpuestos: el agua y lo que se mueve se vuelven seda.",
                    style = SmallStyle, color = Color.White.copy(alpha = 0.8f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Guardar como foto", onClick = {
                        vm.savePhotoNextTo(item, made, if (trails) "_estela" else "_larga_exposicion", "Guardada como foto nueva")
                        exposure = null
                    }, modifier = Modifier.weight(1f))
                    PillButton("Volver", onClick = { exposure = null }, modifier = Modifier.weight(1f), primary = false)
                }
            }
            return@Box
        }
        if (player == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Lumi.Accent)
        } else {
            PlayerSurface(player, Modifier.fillMaxSize())
        }
        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.White) }
            Text("Foto en movimiento", style = HeadingStyle, color = Color.White, modifier = Modifier.padding(start = 10.dp))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                .navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (player?.isPlaying == false || dragging) "Elige el instante y guárdalo como foto" else "Para en el instante que quieras",
                style = SmallStyle, color = Color.White.copy(alpha = 0.85f),
            )
            Slider(
                value = at,
                onValueChange = { value ->
                    dragging = true
                    at = value
                    player?.pause()
                    player?.seekTo((value * length).toLong())
                },
                onValueChangeFinished = { dragging = false },
                enabled = player != null && length > 0,
                colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    "Guardar este instante",
                    onClick = {
                        player?.pause()
                        vm.saveMotionFrame(item, ((player?.currentPosition) ?: 0L))
                    },
                    modifier = Modifier.weight(1.3f),
                    enabled = player != null,
                )
                PillButton("Guardar el vídeo", onClick = { vm.saveMotionVideo(item) }, modifier = Modifier.weight(1f), primary = false, enabled = player != null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (exposing && !trails) "Calculando…" else "Larga exposición", onClick = { expose(false) }, modifier = Modifier.weight(1f), primary = false, enabled = player != null && !exposing)
                PillButton(if (exposing && trails) "Calculando…" else "Estela de luces", onClick = { expose(true) }, modifier = Modifier.weight(1f), primary = false, enabled = player != null && !exposing)
            }
        }
    }
}
