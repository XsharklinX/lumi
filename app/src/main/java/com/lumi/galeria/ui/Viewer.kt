package com.lumi.galeria.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.positionChange
import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import coil.request.ImageRequest
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.Thumb
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.data.WORDS
import com.lumi.galeria.data.cameraInfo
import com.lumi.galeria.data.nearestCity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.lumi.galeria.dayTitle
import com.lumi.galeria.formatDuration
import com.lumi.galeria.formatSize
import com.lumi.galeria.timeText
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Tint(val background: Color, val accent: Color)

// El visor es siempre oscuro, también con el tema claro: las fotos se ven mejor así.
private val DefaultTint = Tint(Color(0xFF0E0E13), Color(0xFFB9A8FF))
private val OnTint = Color(0xFF14101C)

/** Saca de la foto un fondo oscuro y un acento claro para vestir el visor con sus colores. */
private fun tintOf(context: Context, item: MediaItem): Tint {
    val bitmap = try {
        context.contentResolver.loadThumbnail(item.uri, Size(128, 128), null)
    } catch (e: Exception) {
        return DefaultTint
    }
    val software = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    val palette = Palette.from(software).maximumColorCount(12).generate()
    val dark = palette.darkVibrantSwatch ?: palette.darkMutedSwatch
    val background = when {
        dark != null -> lerp(Color(dark.rgb), Color.Black, 0.15f)
        palette.dominantSwatch != null -> lerp(Color(palette.dominantSwatch!!.rgb), Color.Black, 0.7f)
        else -> DefaultTint.background
    }
    val light = palette.lightVibrantSwatch ?: palette.vibrantSwatch ?: palette.lightMutedSwatch
    val accent = if (light != null) lerp(Color(light.rgb), Color.White, 0.2f) else DefaultTint.accent
    return Tint(background, accent)
}

@Composable
fun ViewerScreen(screen: Screen.Viewer, state: UiState, vm: LumiViewModel, actions: Actions, link: GridLink) {
    // En Favoritas la lista se congela al entrar: quitar el corazón no hace saltar la foto.
    val frozenFavorites = remember { state.favorites }
    val items = remember(state.tiles, state.items, state.stackByBest, screen.source) {
        if (screen.source == Source.Favorites) state.items.filter { it.id in frozenFavorites } else state.itemsFor(screen.source)
    }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val start = remember { items.indexOfFirst { it.id == screen.startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { items.size }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    val isFavorite = current.id in state.favorites
    val stack = if (screen.source == Source.Timeline) state.stackByBest[current.id] else null

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var tint by remember { mutableStateOf(DefaultTint) }
    LaunchedEffect(current.id) { tint = withContext(Dispatchers.IO) { tintOf(context, current) } }
    val background by animateColorAsState(tint.background, tween(400), label = "fondo")
    val accent by animateColorAsState(tint.accent, tween(400), label = "acento")

    var chrome by remember { mutableStateOf(true) }
    var more by remember { mutableStateOf(false) }
    // Texto leído en la foto, mientras se enseña para copiarlo.
    var foundText by remember { mutableStateOf<String?>(null) }
    // true = mover, false = copiar, null = cerrado
    var moving by remember { mutableStateOf<Boolean?>(null) }
    // Vídeo que se está pasando a una copia más ligera.
    var shrinking by remember { mutableStateOf<MediaItem?>(null) }

    // --- Abrir desde la miniatura, cerrar hacia ella y arrastrar hacia abajo ---
    val open = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var tile by remember { mutableStateOf(screen.origin) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(Unit) { open.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    LaunchedEffect(current.id) { link.reveal?.invoke(current.id) }

    // --- Vídeo ---
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var looping by remember { mutableStateOf(false) }
    // Aviso breve en pantalla al saltar con doble toque ("+10 s").
    var flash by remember { mutableStateOf<String?>(null) }
    // Pasar las fotos solas, bloquear los toques durante un vídeo y forzar el apaisado.
    var slideshow by remember { mutableStateOf(false) }
    var touchLock by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    val activity = context as Activity
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volume by remember {
        mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1))
    }
    LaunchedEffect(current.id) {
        player.stop()
        player.clearMediaItems()
        playing = false
        position = 0
        duration = current.duration
        if (!current.isVideo) return@LaunchedEffect
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(current.uri))
        // El vídeo arranca solo al abrirlo, como en cualquier reproductor.
        player.playWhenReady = true
        player.prepare()
        while (isActive) {
            if (player.playbackState == Player.STATE_ENDED) {
                player.pause()
                player.seekTo(0)
            }
            playing = player.isPlaying
            position = player.currentPosition
            if (player.duration > 0) duration = player.duration
            delay(200)
        }
    }

    LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(looping) { player.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(700)
            flash = null
        }
    }
    // Mientras se reproduce, los controles se retiran solos y la pantalla no se apaga.
    LaunchedEffect(playing, chrome, speed, muted, looping, current.id) {
        if (playing && chrome && current.isVideo) {
            delay(3500)
            chrome = false
        }
    }
    val view = LocalView.current
    DisposableEffect(playing, slideshow) {
        view.keepScreenOn = playing || slideshow
        onDispose { view.keepScreenOn = false }
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0, maxOf(duration, 0))
        player.seekTo(target)
        position = target
        flash = if (deltaMs > 0) "+${deltaMs / 1000} s" else "−${-deltaMs / 1000} s"
    }

    fun close() {
        if (closing) return
        closing = true
        player.pause()
        tile = link.boundsOf(current.id)
        scope.launch {
            open.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
            vm.back()
        }
    }

    /** Pasa a otra pantalla recordando la foto actual, para volver a ella. */
    fun leaveTo(next: Screen) {
        player.pause()
        vm.replaceTop(screen.copy(startId = current.id, origin = null))
        vm.open(next)
    }

    LaunchedEffect(slideshow) {
        while (slideshow) {
            delay(3000)
            if (pager.currentPage < items.lastIndex) pager.animateScrollToPage(pager.currentPage + 1) else slideshow = false
        }
    }
    LaunchedEffect(landscape) {
        activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    // Al salir del visor, la pantalla vuelve a girar sola y recupera su brillo.
    DisposableEffect(Unit) {
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = -1f }
        }
    }

    /** Deslizar por un lado del vídeo: a la izquierda cambia el brillo; a la derecha, el volumen. */
    fun adjust(left: Boolean, amount: Float) {
        if (left) {
            val now = activity.window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f
            val next = (now + amount).coerceIn(0.02f, 1f)
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = next }
            flash = "Brillo ${(next * 100).toInt()} %"
        } else {
            volume = (volume + amount).coerceIn(0f, 1f)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).roundToInt(), 0)
            flash = "Volumen ${(volume * 100).toInt()} %"
        }
    }

    BackHandler {
        when {
            touchLock -> Unit
            slideshow -> slideshow = false
            else -> close()
        }
    }

    // Solo cambia al terminar de abrirse o al empezar a moverse, no en cada paso de la animación.
    val settled by remember { derivedStateOf { open.value == 1f && dragY.value == 0f && !closing } }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                // Lo que el dedo ha subido en este gesto: hacia abajo se cierra, hacia arriba salen los detalles.
                var lifted = 0f
                detectVerticalDragGestures(
                    onDragStart = { lifted = 0f },
                    onDragEnd = {
                        when {
                            dragY.value > size.height * 0.14f -> close()
                            lifted > size.height * 0.09f && dragY.value == 0f -> {
                                more = true
                                scope.launch { dragY.animateTo(0f) }
                            }
                            else -> scope.launch { dragY.animateTo(0f) }
                        }
                    },
                    onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    lifted -= amount
                    scope.launch { dragY.snapTo((dragY.value + amount).coerceAtLeast(0f)) }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val pulled = if (box.height > 0) (abs(dragY.value) / (box.height * 0.35f)).coerceIn(0f, 1f) else 0f
                    alpha = open.value * (1f - 0.75f * pulled)
                }
                .background(Color.Black),
        )

        HorizontalPager(
            state = pager,
            key = { items.getOrNull(it)?.id ?: it },
            beyondViewportPageCount = 1,
            pageSpacing = 16.dp,
            modifier = Modifier.graphicsLayer {
                val p = open.value
                val pulled = if (box.height > 0) (abs(dragY.value) / (box.height * 0.35f)).coerceIn(0f, 1f) else 0f
                val fullScale = 1f - 0.2f * pulled
                val rect = tile
                if (rect != null && box.width > 0) {
                    // Tamaño con el que la foto se ve entera en pantalla, para casarlo con su miniatura.
                    val ratio = if (current.width > 0 && current.height > 0) current.width.toFloat() / current.height else box.width.toFloat() / box.height
                    val fitWidth = minOf(box.width.toFloat(), box.height * ratio)
                    val fitHeight = fitWidth / ratio
                    val tileScale = maxOf(rect.width / fitWidth, rect.height / fitHeight)
                    val scale = lerp(tileScale, fullScale, p)
                    scaleX = scale
                    scaleY = scale
                    translationX = lerp(rect.center.x - box.width / 2f, 0f, p)
                    translationY = lerp(rect.center.y - box.height / 2f, dragY.value, p)
                } else {
                    val scale = lerp(0.9f, fullScale, p)
                    scaleX = scale
                    scaleY = scale
                    translationY = dragY.value
                    alpha = p
                }
            },
        ) { page ->
            val item = items[page]
            val active = page == pager.currentPage
            if (item.isVideo) {
                VideoPage(
                    item = item,
                    player = if (active && settled) player else null,
                    playing = playing,
                    flash = if (active) flash else null,
                    onTap = { chrome = !chrome },
                    onToggle = { if (player.isPlaying) player.pause() else player.play() },
                    onSeekBy = ::seekBy,
                    onAdjust = ::adjust,
                )
            } else {
                ZoomablePhoto(item, active = active, onTap = { chrome = !chrome })
            }
        }

        AnimatedVisibility(chrome && settled && !state.pip && !slideshow && !touchLock, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(start = 10.dp, top = 8.dp, bottom = 28.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Volver",
                        Modifier.clip(CircleShape).clickable { close() }.padding(10.dp).size(24.dp),
                        tint = Color.White,
                    )
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to background.copy(alpha = 0.88f),
                                1f to background,
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (stack != null) {
                        Text(
                            "Pila de ${stack.members.size} parecidas",
                            style = LabelStyle,
                            color = OnTint,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(accent)
                                .clickable { leaveTo(Screen.StackView(current.id)) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (current.isExternal) {
                        Text(current.name, style = TitleStyle.copy(fontSize = 20.sp), color = Color.White, maxLines = 1)
                    } else {
                        Text(dayTitle(current.date), style = TitleStyle.copy(fontSize = 22.sp), color = Color.White)
                        Text(
                            timeText(current.date) + if (current.bucket.isNotEmpty()) " · ${current.bucket}" else "",
                            style = SmallStyle,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    if (current.isVideo) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.size(42.dp).clip(CircleShape).background(accent).clickable { if (playing) player.pause() else player.play() },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (playing) PauseGlyph(OnTint) else Icon(Icons.Filled.PlayArrow, "Reproducir", tint = OnTint)
                            }
                            Slider(
                                value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                                onValueChange = { fraction ->
                                    position = (fraction * duration).toLong()
                                    player.seekTo(position)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = accent,
                                    activeTrackColor = accent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text("${formatDuration(position)} / ${formatDuration(duration)}", style = SmallStyle, color = Color.White)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp).horizontalScroll(rememberScrollState())) {
                            PlayerChip(if (speed == 1f) "Velocidad 1×" else "Velocidad ${if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()}×", speed != 1f, accent) {
                                speed = when (speed) {
                                    1f -> 1.5f
                                    1.5f -> 2f
                                    2f -> 0.5f
                                    else -> 1f
                                }
                            }
                            PlayerChip(if (muted) "Sin sonido" else "Con sonido", muted, accent) { muted = !muted }
                            PlayerChip("Repetir", looping, accent) { looping = !looping }
                            PlayerChip("Fotograma", false, accent) { vm.saveFrame(current, position) }
                            PlayerChip("Girar", landscape, accent) { landscape = !landscape }
                            PlayerChip("Flotante", false, accent) { actions.pip() }
                            PlayerChip("Bloquear toques", false, accent) {
                                touchLock = true
                                chrome = false
                            }
                            if (!current.isExternal) PlayerChip("Recortar", false, accent) { leaveTo(Screen.Trim(current.id)) }
                        }
                    } else if (items.size > 1) {
                        Filmstrip(items, pager.currentPage.coerceIn(0, items.lastIndex)) { index ->
                            // Un salto corto se ve pasar; uno largo va directo.
                            scope.launch { if (abs(index - pager.currentPage) <= 3) pager.animateScrollToPage(index) else pager.scrollToPage(index) }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (current.isExternal) {
                            // Lo abierto desde la carpeta privada es una copia temporal: no se ofrece enviarla.
                            if (current.uri.scheme != "file") {
                                ActionChip(Icons.Filled.Share, "Enviar", Modifier.weight(1f), accent) { actions.share(listOf(current)) }
                            }
                        } else {
                            ActionChip(Icons.Filled.Share, "Enviar", Modifier.weight(1f), accent) { actions.share(listOf(current)) }
                            ActionChip(
                                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorita",
                                Modifier.weight(1f), accent, filled = isFavorite,
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.toggleFavorite(listOf(current.id))
                            }
                            if (!current.isVideo) {
                                ActionChip(Icons.Filled.Edit, "Editar", Modifier.weight(1f), accent) { leaveTo(Screen.Editor(current.id)) }
                            }
                            ActionChip(Icons.Filled.Delete, "Borrar", Modifier.weight(1f), accent) { actions.trash(listOf(current)) {} }
                            ActionChip(Icons.Filled.MoreVert, "Más", Modifier.weight(1f), accent) { more = true }
                        }
                    }
                }
            }
        }
    }

    if (touchLock) {
        // Tapa toda la pantalla y se queda con los toques: nada reacciona hasta mantener pulsado.
        var hint by remember { mutableStateOf(true) }
        LaunchedEffect(hint) {
            if (hint) {
                delay(2500)
                hint = false
            }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { hint = true }, onLongPress = { touchLock = false }) },
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (hint) {
                Text(
                    "Pantalla bloqueada. Mantén pulsado para desbloquear.",
                    style = LabelStyle,
                    color = Color.White,
                    modifier = Modifier.navigationBarsPadding().padding(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
    if (slideshow) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { slideshow = false; chrome = true }) })
    }

    foundText?.let { text ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { foundText = null },
            containerColor = Lumi.Surface,
            title = { Text("Texto de la foto", style = HeadingStyle) },
            text = {
                // Se puede seleccionar un trozo con el dedo o copiarlo todo con el botón.
                SelectionContainer {
                    Text(text, style = SmallStyle.copy(fontSize = 15.sp, color = Lumi.Ink), modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = {
                Text(
                    "Copiar todo", style = LabelStyle, color = Lumi.Accent,
                    modifier = Modifier.clip(CircleShape).clickable {
                        clipboard.setText(AnnotatedString(text))
                        foundText = null
                        vm.say("Texto copiado")
                    }.padding(12.dp),
                )
            },
            dismissButton = {
                Text("Cerrar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { foundText = null }.padding(12.dp))
            },
        )
    }

    if (more) {
        MoreSheet(
            item = current,
            place = state.index[current.id]?.takeIf { it.hasPlace }?.let { nearestCity(it.lat.toDouble(), it.lon.toDouble(), 90.0)?.name },
            things = state.index[current.id]?.labels.orEmpty().mapNotNull { WORDS[it]?.firstOrNull() }.distinct().take(6),
            onRename = { name ->
                more = false
                actions.write(listOf(current)) { vm.rename(current, name) }
            },
            onDismiss = { more = false },
            onShareClean = { more = false; actions.shareWithoutLocation(current) },
            onUseAs = { more = false; actions.useAs(current) },
            onMove = { more = false; moving = true },
            onCopy = { more = false; moving = false },
            onHide = {
                more = false
                vm.hideInVault(listOf(current)) { stored -> actions.deleteForever(stored) }
            },
            onSlideshow = {
                more = false
                chrome = false
                slideshow = true
            },
            onCover = { more = false; vm.setAlbumCover(current) },
            onMarkup = { more = false; leaveTo(Screen.Markup(current.id)) },
            onText = {
                more = false
                vm.readText(current) { foundText = it }
            },
            onCutout = { more = false; leaveTo(Screen.Cutout(current.id)) },
            onShrink = {
                more = false
                player.pause()
                shrinking = current
            },
        )
    }
    shrinking?.let { video -> ShrinkSheet(video, vm) { shrinking = null } }
    moving?.let { move ->
        val chosen = listOf(current)
        AlbumPickerSheet(
            title = if (move) "Mover a…" else "Copiar a…",
            albums = state.albums,
            onPick = { path, name ->
                moving = null
                if (move) actions.write(chosen) { vm.moveTo(chosen, path, name) } else vm.copyTo(chosen, path, name)
            },
            onDismiss = { moving = null },
        )
    }
}

/**
 * Tira de miniaturas bajo la foto: enseña las vecinas y deja saltar varias de golpe sin volver
 * a la cuadrícula. La que se está viendo queda siempre en el centro.
 */
@Composable
private fun Filmstrip(items: List<MediaItem>, page: Int, onPick: (Int) -> Unit) {
    val strip = rememberLazyListState(initialFirstVisibleItemIndex = page)
    LaunchedEffect(page) { strip.animateScrollToItem(page) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
        LazyRow(
            state = strip,
            // Con este margen, la miniatura que queda la primera cae justo en el centro.
            contentPadding = PaddingValues(horizontal = (maxWidth - 40.dp) / 2),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            items(items.size, key = { items[it].id }) { index ->
                val on = index == page
                val shape = RoundedCornerShape(8.dp)
                MediaThumb(
                    items[index], 160,
                    Modifier
                        .size(width = if (on) 40.dp else 32.dp, height = if (on) 54.dp else 44.dp)
                        .clip(shape)
                        .then(if (on) Modifier.border(2.dp, Color.White, shape) else Modifier.alpha(0.7f))
                        .clickable { onPick(index) },
                )
            }
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, modifier: Modifier, accent: Color, filled: Boolean = false, onClick: () -> Unit) {
    val content = if (filled) OnTint else accent
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (filled) accent else accent.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = content)
        Text(label, style = SmallStyle, color = content, maxLines = 1)
    }
}

@Composable
private fun ZoomablePhoto(item: MediaItem, active: Boolean, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    // Al ampliar se carga la foto a mucha más resolución; se queda cargada mientras se mira.
    var sharp by remember { mutableStateOf(false) }
    LaunchedEffect(scale > 1.4f, active) {
        if (!active) sharp = false else if (scale > 1.4f) sharp = true
    }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val tap by rememberUpdatedState(onTap)

    fun clamp(value: Offset, forScale: Float): Offset {
        val maxX = size.width * (forScale - 1) / 2
        val maxY = size.height * (forScale - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { point ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = clamp((Offset(size.width / 2f, size.height / 2f) - point) * 1.5f, 2.5f)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // Con un dedo y sin ampliar, el gesto es del visor: pasar de foto o cerrarla.
                        if (event.changes.count { it.pressed } >= 2 || scale > 1f) {
                            val next = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (next == 1f) Offset.Zero else clamp(offset + event.calculatePan(), next)
                            scale = next
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) {
        // La miniatura ya está en memoria: se ve al instante mientras llega la foto completa.
        if (!item.isExternal) {
            AsyncImage(Thumb(item.uri, 320, item.modified), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        if (sharp && !item.isGif) {
            AsyncImage(
                ImageRequest.Builder(LocalContext.current).data(item.uri).size(4096).memoryCacheKey("nitida:${item.uri}").build(),
                null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun PlayerChip(label: String, on: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        label,
        style = SmallStyle,
        color = if (on) OnTint else Color.White,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) accent else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

/**
 * [player] solo llega a la página que se está viendo y cuando el visor está quieto.
 * Un toque enseña u oculta los controles; dos toques en un lado saltan diez segundos.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPage(
    item: MediaItem,
    player: ExoPlayer?,
    playing: Boolean,
    flash: String?,
    onTap: () -> Unit,
    onToggle: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onAdjust: (left: Boolean, amount: Float) -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val seek by rememberUpdatedState(onSeekBy)
    val tune by rememberUpdatedState(onAdjust)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { point -> seek(if (point.x < size.width / 2) -10_000L else 10_000L) },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val third = size.width / 3f
                    // El centro queda libre: ahí deslizar hacia abajo sigue cerrando el vídeo.
                    if (down.position.x > third && down.position.x < third * 2) return@awaitEachGesture
                    val left = down.position.x <= third
                    val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                    verticalDrag(start.id) { change ->
                        // Subir el dedo aumenta; recorrer toda la pantalla va de nada a todo.
                        tune(left, -change.positionChange().y / size.height)
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!item.isExternal) {
            AsyncImage(Thumb(item.uri, 1024, item.modified), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (flash != null) {
            Text(
                flash,
                style = TitleStyle.copy(fontSize = 22.sp),
                color = Color.White,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        } else if (!playing) {
            Icon(
                Icons.Filled.PlayArrow, "Reproducir",
                Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onToggle).padding(18.dp).size(40.dp),
                tint = Color.White,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    item: MediaItem,
    place: String?,
    things: List<String>,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
    onShareClean: () -> Unit,
    onUseAs: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onHide: () -> Unit,
    onSlideshow: () -> Unit,
    onCover: () -> Unit,
    onMarkup: () -> Unit,
    onText: () -> Unit,
    onCutout: () -> Unit,
    onShrink: () -> Unit,
) {
    val context = LocalContext.current
    // Lo que anotó la cámara se lee al abrir los detalles, no antes.
    val camera by produceState(emptyList<Pair<String, String>>(), item.id) {
        value = withContext(Dispatchers.IO) { cameraInfo(context, item) }
    }
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(item.name.substringBeforeLast('.')) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lumi.Surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Detalles", style = HeadingStyle.copy(fontSize = 20.sp))
            DetailRow("Fecha", "${dayTitle(item.date)}, ${timeText(item.date)}")
            DetailRow("Archivo", item.name)
            DetailRow("Tamaño", formatSize(item.size))
            if (item.width > 0 && item.height > 0) {
                val megapixels = String.format(Locale("es", "ES"), "%.1f MP", item.width * item.height / 1_000_000.0)
                DetailRow("Resolución", "${item.width} × ${item.height}" + if (item.isVideo) "" else " · $megapixels")
            }
            if (item.isVideo) DetailRow("Duración", formatDuration(item.duration))
            if (item.path.isNotEmpty()) DetailRow("Carpeta", item.path.trimEnd('/'))
            if (!item.isExternal) DetailRow("Guardada en", if (item.onCard) "Tarjeta de memoria" else "Memoria del teléfono")
            if (place != null) DetailRow("Lugar", "Cerca de $place")
            camera.forEach { (label, value) -> DetailRow(label, value) }
            if (things.isNotEmpty()) DetailRow("Lumi ve", things.joinToString(", "))
            Column(Modifier.padding(top = 6.dp)) {
                SheetAction("Presentación", "Pasa las fotos solas cada tres segundos; un toque la detiene", onSlideshow)
                if (!item.isVideo) SheetAction("Dibujar o escribir encima", "Se guarda en una copia", onMarkup)
                if (!item.isVideo) SheetAction("Copiar el texto de la foto", "Lee lo que hay escrito para pegarlo donde quieras", onText)
                if (!item.isVideo) SheetAction("Quitar el fondo", "Deja solo a la persona u objeto principal", onCutout)
                if (item.isVideo && !item.isExternal) SheetAction("Reducir el peso", "Guarda una copia que ocupa menos; el original no se toca", onShrink)
                SheetAction("Usar como portada del álbum", null, onCover)
                SheetAction("Cambiar el nombre", null) { renaming = true }
                if (!item.isVideo) {
                    SheetAction("Enviar sin ubicación", "Se manda una copia sin el lugar donde se hizo", onShareClean)
                    SheetAction("Usar como…", "Fondo de pantalla, foto de contacto y otros", onUseAs)
                }
                SheetAction("Mover a un álbum", null, onMove)
                SheetAction("Copiar a un álbum", null, onCopy)
                SheetAction("Mover a la carpeta privada", "Se guarda cifrada y desaparece de la galería", onHide)
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = Lumi.Surface,
            title = { Text("Cambiar el nombre", style = HeadingStyle) },
            text = { OutlinedTextField(newName, { newName = it.take(80) }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
            confirmButton = {
                Text(
                    "Guardar", style = LabelStyle, color = Lumi.Accent,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = newName.isNotBlank()) { renaming = false; onRename(newName) }.padding(12.dp),
                )
            },
            dismissButton = {
                Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { renaming = false }.padding(12.dp))
            },
        )
    }
}

@Composable
private fun SheetAction(title: String, hint: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 11.dp)) {
        Text(title, style = LabelStyle.copy(fontSize = 15.sp), color = Lumi.Accent)
        if (hint != null) Text(hint, style = SmallStyle)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = SmallStyle)
        Text(value, style = LabelStyle.copy(fontSize = 15.sp))
    }
}

@Composable
fun StackScreen(screen: Screen.StackView, state: UiState, vm: LumiViewModel, actions: Actions) {
    val stack = state.stackByBest[screen.bestId]
    if (stack == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    var selectedId by remember { mutableStateOf(stack.best.id) }
    val selected = stack.members.firstOrNull { it.id == selectedId } ?: stack.best
    val isBest = selected.id == stack.best.id

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Pila", countText(stack.members.size, "foto parecida", "fotos parecidas"), onBack = { vm.back() })
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Lumi.Surface)
                .clickable { vm.open(Screen.Viewer(Source.Stack(stack.best.id), selected.id)) },
        ) {
            AsyncImage(model = selected.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (isBest) {
                Text(
                    "Mejor toma",
                    style = LabelStyle,
                    color = Lumi.OnAccent,
                    modifier = Modifier.padding(12.dp).clip(CircleShape).background(Lumi.Accent).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Hasta seis a la vista; el resto se recorre abriendo la foto grande.
            stack.members.take(6).forEach { member ->
                val on = member.id == selected.id
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) Lumi.Accent else Color.Transparent)
                        .clickable { selectedId = member.id }
                        .padding(2.5.dp),
                ) {
                    PhotoTile(member, 160, Modifier.fillMaxWidth().height(56.dp), corner = 10.dp)
                }
            }
        }
        Text(
            if (isBest) "Elegida por nitidez. Toca otra si prefieres quedarte con ella."
            else "Has elegido otra toma. Las demás irán a la papelera, donde siguen 30 días.",
            style = SmallStyle,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                if (isBest) "Quedarme con la mejor" else "Quedarme con esta",
                // Al vaciarse la pila, la pantalla se cierra sola.
                onClick = { actions.trash(stack.members.filter { it.id != selected.id }) {} },
                modifier = Modifier.fillMaxWidth(),
            )
            PillButton(
                "Conservar las ${stack.members.size}",
                onClick = { vm.keepStack(stack); vm.back() },
                modifier = Modifier.fillMaxWidth(),
                primary = false,
            )
        }
    }
}
