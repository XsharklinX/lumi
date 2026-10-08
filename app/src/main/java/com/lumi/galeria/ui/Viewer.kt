package com.lumi.galeria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.drawscope.Stroke
import android.widget.Toast
import com.lumi.galeria.data.Filters
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import com.lumi.galeria.data.findContacts
import com.lumi.galeria.data.ContactKind
import com.lumi.galeria.data.Contact
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Email
import android.content.Intent
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.combinedClickable
import com.lumi.galeria.tr
import com.lumi.galeria.data.albumName
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import com.lumi.galeria.data.readSubtitles
import com.lumi.galeria.data.findSubtitles
import com.lumi.galeria.data.cueAt
import com.lumi.galeria.data.Cue
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Equalizer
import android.media.MediaMetadataRetriever
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // Fotos Ultra HDR: la pantalla pasa a HDR para enseñar su brillo real (Android 14 o posterior).
    LaunchedEffect(current.id) {
        if (android.os.Build.VERSION.SDK_INT < 34) return@LaunchedEffect
        val hdr = !current.isVideo && withContext(Dispatchers.IO) { com.lumi.galeria.data.isUltraHdr(context, current.uri) }
        (context as? android.app.Activity)?.window?.colorMode =
            if (hdr) android.content.pm.ActivityInfo.COLOR_MODE_HDR else android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
    }
    DisposableEffect(Unit) {
        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= 34) (context as? android.app.Activity)?.window?.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
        }
    }
    val background by animateColorAsState(tint.background, tween(400), label = "fondo")
    val accent by animateColorAsState(tint.accent, tween(400), label = "acento")

    var chrome by remember { mutableStateOf(!screen.slideshow) }
    var more by remember { mutableStateOf(false) }
    // Texto leído en la foto, mientras se enseña para copiarlo.
    var foundText by remember { mutableStateOf<String?>(null) }
    // true = mover, false = copiar, null = cerrado
    var moving by remember { mutableStateOf<Boolean?>(null) }
    val scanDocument = rememberDocumentScanner(vm)
    // Hoja con todas las opciones del reproductor.
    var videoOptions by remember { mutableStateOf(false) }
    // Cambiar el nombre, desde el menú o manteniendo pulsado el título.
    var renaming by remember { mutableStateOf(false) }
    // Panel de información: la foto sube y debajo aparecen sus datos.
    var info by remember { mutableStateOf(false) }
    val infoShift by androidx.compose.animation.core.animateFloatAsState(if (info) 1f else 0f, tween(280), label = "info")
    // Vídeo que se está pasando a una copia más ligera.
    var shrinking by remember { mutableStateOf<MediaItem?>(null) }
    // Foto en movimiento: se mueve mientras se mantiene el dedo, o se abre a pantalla completa.
    val isMotion = !current.isVideo && state.index[current.id]?.motion == true
    // En tableta o pantalla dividida, mantener la foto la arrastra a la otra app.
    val dragOut = canDragOut()
    val dragThumb = rememberDragThumb(if (dragOut) current else null)
    val dragView = androidx.compose.ui.platform.LocalView.current
    var motionHold by remember { mutableStateOf(false) }
    var motionOpen by remember { mutableStateOf(false) }
    // Texto de la foto marcado encima de ella, para tocarlo; null si no se está viendo.
    var textPage by remember { mutableStateOf<com.lumi.galeria.data.TextPage?>(null) }
    // Caras de la foto: quién es cada una. Con [faceTags], sus nombres se ven encima de la foto.
    var faceTags by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<com.lumi.galeria.data.Face?>(null) }
    // Códigos leídos de la foto con «Leer el código QR».
    var qrCodes by remember { mutableStateOf<List<com.google.mlkit.vision.barcode.common.Barcode>>(emptyList()) }
    // Cara mantenida pulsada en «En esta foto»: se puede decir que no es esa persona.
    var faceMenu by remember { mutableStateOf<Pair<com.lumi.galeria.data.Face, com.lumi.galeria.data.Person?>?>(null) }
    val photoFaces by produceState(emptyList<Pair<com.lumi.galeria.data.Face, com.lumi.galeria.data.Person?>>(), current.id, state.people) {
        value = withContext(Dispatchers.Default) {
            val byGroup = state.people.associateBy { it.group.id }
            vm.facesOf(current.id).sortedBy { it.left }.map { it to byGroup[it.group] }
        }
    }
    val hasText = !current.isVideo && (state.index[current.id]?.text?.length ?: 0) >= 20
    fun showText() {
        vm.readTextPage(current) { page ->
            textPage = page
            chrome = false
            info = false
        }
    }
    LaunchedEffect(current.id) {
        motionHold = false
        motionOpen = false
        textPage = null
        faceTags = false
    }

    // --- Abrir desde la miniatura, cerrar hacia ella y arrastrar hacia abajo ---
    val open = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var tile by remember { mutableStateOf(screen.origin) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(Unit) { open.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    LaunchedEffect(current.id) { link.reveal?.invoke(current.id) }

    // --- Vídeo ---
    val player = remember {
        // Con su propia sesión de sonido, para poder colgarle el volumen extra y el realce de voces.
        ExoPlayer.Builder(context).build().also { p ->
            runCatching { p.audioSessionId = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId() }
        }
    }
    val sound = remember { SoundEffects(player.audioSessionId) }
    DisposableEffect(Unit) {
        onDispose {
            sound.release()
            player.release()
        }
    }
    // Al arrastrar la barra: por dónde va, de 0 a 1. Null si no se arrastra.
    var scrub by remember { mutableStateOf<Float?>(null) }
    // Mantener el dedo en el vídeo lo pone al doble mientras dure.
    var holdFast by remember { mutableStateOf(false) }
    var boost by remember { mutableIntStateOf(100) }
    var voice by remember { mutableStateOf(false) }
    // Subtítulos: las frases, si se ven, su tamaño (0 a 2) y cuánto se adelantan o retrasan.
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var subsOn by remember { mutableStateOf(true) }
    var subSize by remember { mutableIntStateOf(1) }
    var subShift by remember { mutableLongStateOf(0L) }
    val pickSubs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val found = withContext(Dispatchers.IO) { readSubtitles(context, uri) }
            if (found.isEmpty()) vm.say("Ese archivo no tiene subtítulos que Lumi entienda") else {
                cues = found
                subsOn = true
            }
        }
    }
    // Dónde se retomó el vídeo, para ofrecer volver al principio durante unos segundos.
    var resumedAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(resumedAt) {
        if (resumedAt != null) {
            delay(6000)
            resumedAt = null
        }
    }
    LaunchedEffect(boost, voice) { sound.set(boost, voice) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var looping by remember { mutableStateOf(false) }
    // Aviso breve en pantalla al saltar con doble toque ("+10 s").
    var flash by remember { mutableStateOf<String?>(null) }
    // Pasar las fotos solas, bloquear los toques durante un vídeo y forzar el apaisado.
    var slideshow by remember { mutableStateOf(screen.slideshow) }
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
        cues = emptyList()
        subShift = 0L
        resumedAt = null
        if (!current.isVideo) return@LaunchedEffect
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(current.uri))
        // Un vídeo largo sigue donde se dejó.
        val saved = if (current.isExternal) 0L else vm.resumePosition(current)
        if (saved > 0) {
            player.seekTo(saved)
            resumedAt = saved
        }
        // El vídeo arranca solo al abrirlo, como en cualquier reproductor.
        player.playWhenReady = true
        player.prepare()
        if (!current.isExternal) launch { withContext(Dispatchers.IO) { findSubtitles(context, current) }?.let { cues = it } }
        var lastSave = 0L
        try {
            while (isActive) {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.pause()
                    player.seekTo(0)
                }
                playing = player.isPlaying
                position = player.currentPosition
                if (player.duration > 0) duration = player.duration
                if (position - lastSave > 5000 || lastSave - position > 5000) {
                    lastSave = position
                    if (!current.isExternal) vm.saveResume(current, position)
                }
                delay(200)
            }
        } finally {
            if (!current.isExternal) vm.saveResume(current, player.currentPosition)
        }
    }

    LaunchedEffect(speed, holdFast) { player.setPlaybackSpeed(if (holdFast) 2f else speed) }
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
            info -> info = false
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
                            // Con la información abierta, bajar la cierra en vez de cerrar la foto.
                            info && dragY.value > 0f -> {
                                info = false
                                scope.launch { dragY.animateTo(0f) }
                            }
                            dragY.value > size.height * 0.14f -> close()
                            lifted > size.height * 0.09f && dragY.value == 0f -> {
                                info = true
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
            userScrollEnabled = textPage == null,
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
                    translationY = lerp(rect.center.y - box.height / 2f, dragY.value, p) - infoShift * box.height * 0.3f
                } else {
                    val scale = lerp(0.9f, fullScale, p)
                    scaleX = scale
                    scaleY = scale
                    translationY = dragY.value - infoShift * box.height * 0.3f
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
                    onTap = { if (info) info = false else chrome = !chrome },
                    onToggle = { if (player.isPlaying) player.pause() else player.play() },
                    onSeekBy = ::seekBy,
                    onAdjust = ::adjust,
                    fast = active && holdFast,
                    onHold = { holdFast = it },
                )
            } else {
                val moves = !item.isExternal && state.index[item.id]?.motion == true
                Box(Modifier.fillMaxSize()) {
                    if (!item.isExternal) Backdrop(item)
                    ZoomablePhoto(
                        item, active = active, locked = active && textPage != null, onTap = { if (info) info = false else chrome = !chrome },
                        onHold = when {
                            moves -> ({ held -> motionHold = held })
                            dragOut && !item.isExternal -> ({ held -> if (held) startDragOut(dragView, context, listOf(item), dragThumb) })
                            else -> null
                        },
                    )
                    if (active && moves && motionHold) MotionLayer(item, Modifier.fillMaxSize())
                }
            }
        }

        // Subtítulos: por encima de la imagen, y más arriba cuando están los controles.
        textPage?.let { page -> LiveText(page) { textPage = null } }
        if (faceTags && photoFaces.isNotEmpty()) {
            FaceTags(current, photoFaces, onClose = { faceTags = false }) { face, person ->
                if (person?.name != null) leaveTo(Screen.Person(person.key)) else naming = face
            }
        }

        if (current.isVideo && subsOn && cues.isNotEmpty() && settled) {
            cueAt(cues, position - subShift)?.let { cue ->
                Text(
                    cue.text,
                    style = LabelStyle.copy(fontSize = listOf(15, 19, 24)[subSize].sp, lineHeight = listOf(20, 25, 31)[subSize].sp),
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = if (chrome) 260.dp else 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        resumedAt?.let { at ->
            Text(
                "Sigues en ${formatDuration(at)} · Desde el principio",
                style = LabelStyle,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable {
                        player.seekTo(0)
                        resumedAt = null
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        AnimatedVisibility(
            info && settled,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Las demás fotos de ese mismo día, de las que se están viendo.
            val sameDay = remember(current.id, items) {
                val day = dayTitle(current.date)
                items.filter { !it.isExternal && dayTitle(it.date) == day }
            }
            // Más como esta: lo que comparte con otras fotos. Se calcula aparte para no frenar el visor.
            val related by produceState(emptyList<Pair<String, List<MediaItem>>>(), current.id, state.index, state.people) {
                value = withContext(Dispatchers.Default) { relatedTo(current, state) }
            }
            InfoPanel(
                faces = photoFaces,
                onFace = { face, person -> if (person?.name != null) leaveTo(Screen.Person(person.key)) else naming = face },
                onFaceLong = { face, person -> faceMenu = face to person },
                onFaceTags = {
                    info = false
                    chrome = false
                    faceTags = true
                },
                related = related,
                onRelated = { list, target -> vm.open(Screen.Viewer(Source.Ids(list.mapTo(HashSet()) { it.id }), target.id)) },
                item = current,
                place = state.places[current.id] ?: state.index[current.id]?.takeIf { it.hasPlace }?.let { nearestCity(it.lat.toDouble(), it.lon.toDouble(), 90.0)?.name },
                things = state.index[current.id]?.labels.orEmpty().mapNotNull { WORDS[it]?.firstOrNull() }.distinct().take(6),
                sameDay = sameDay,
                onJump = { target ->
                    val at = items.indexOfFirst { it.id == target.id }
                    if (at >= 0) scope.launch { pager.scrollToPage(at) }
                },
                onPlace = { name ->
                    vm.searchQuery = ""
                    vm.searchFilters = Filters(place = name)
                    vm.open(Screen.Search)
                },
                onThing = { word ->
                    vm.searchQuery = ""
                    vm.searchFilters = Filters(thing = word)
                    vm.open(Screen.Search)
                },
                onClose = { info = false },
            )
        }
        AnimatedVisibility(chrome && settled && !info && !state.pip && !slideshow && !touchLock, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                // Arriba: qué es y cuándo se hizo, y lo que se usa sin pensar (favorita y más opciones).
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BarIcon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", { close() }, Color.White)
                    // Mantener pulsado el título deja cambiar el nombre del archivo.
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (!current.isExternal) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    renaming = true
                                }
                            },
                        ).padding(start = 2.dp, top = 2.dp, bottom = 2.dp),
                    ) {
                        Text(
                            if (current.isExternal) current.name else dayTitle(current.date),
                            style = HeadingStyle.copy(fontSize = 16.sp),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!current.isExternal) {
                            Text(
                                timeText(current.date) + (if (current.bucket.isNotEmpty()) " · ${albumName(current.bucket)}" else "") +
                                    if (current.isVideo && duration > 0) " · ${formatDuration(duration)}" else "",
                                style = SmallStyle,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (hasText && textPage == null) {
                        Row(
                            Modifier
                                .padding(end = 4.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable { showText() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(TextIcon, null, Modifier.size(14.dp), tint = Color.White)
                            Text("Texto", style = LabelStyle, color = Color.White)
                        }
                    }
                    if (isMotion) {
                        Row(
                            Modifier
                                .padding(end = 4.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable { motionOpen = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                            Text("Movimiento", style = LabelStyle, color = Color.White)
                        }
                    }
                    if (stack != null) {
                        Text(
                            "${stack.members.size} parecidas",
                            style = LabelStyle,
                            color = OnTint,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .clickable { leaveTo(Screen.StackView(current.id)) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    if (!current.isExternal) {
                        BarIcon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (isFavorite) "Quitar de favoritas" else "Favorita",
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.toggleFavorite(listOf(current.id))
                            },
                            if (isFavorite) Color(0xFFFF4D6D) else Color.White,
                        )
                        BarIcon(Icons.Filled.MoreVert, "Más", { more = true }, Color.White)
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.5f to background.copy(alpha = 0.85f),
                                1f to background,
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 56.dp, bottom = 8.dp),
                ) {
                    if (current.isVideo && items.size > 1 && scrub == null) {
                        Filmstrip(items, pager.currentPage.coerceIn(0, items.lastIndex)) { index ->
                            scope.launch { if (abs(index - pager.currentPage) <= 3) pager.animateScrollToPage(index) else pager.scrollToPage(index) }
                        }
                    }
                    if (current.isVideo) {
                        scrub?.let { at -> SeekPreview(current, (at * duration).toLong(), at) }
                        Slider(
                            value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            onValueChange = { fraction ->
                                scrub = fraction
                                position = (fraction * duration).toLong()
                                player.seekTo(position)
                            },
                            onValueChangeFinished = { scrub = null },
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                            ),
                            modifier = Modifier.fillMaxWidth().height(28.dp),
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                            Text(formatDuration(position), style = SmallStyle, color = Color.White)
                            Spacer(Modifier.weight(1f))
                            Text(formatDuration(duration), style = SmallStyle, color = Color.White.copy(alpha = 0.75f))
                        }
                        // Una sola fila de mandos: velocidad, saltos, reproducir, sonido y el resto de opciones.
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            RoundControl(if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×", "Velocidad", speed != 1f || holdFast, accent) {
                                speed = when (speed) {
                                    1f -> 1.5f
                                    1.5f -> 2f
                                    2f -> 0.5f
                                    else -> 1f
                                }
                            }
                            RoundControl("−10", "Atrás 10 segundos", false, accent) { seekBy(-10_000) }
                            Box(
                                Modifier.size(60.dp).clip(CircleShape).background(accent).clickable { if (playing) player.pause() else player.play() },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (playing) PauseGlyph(OnTint, Modifier.size(24.dp)) else Icon(Icons.Filled.PlayArrow, "Reproducir", Modifier.size(32.dp), tint = OnTint)
                            }
                            RoundControl("+10", "Adelante 10 segundos", false, accent) { seekBy(10_000) }
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).clickable { videoOptions = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Settings, "Opciones del vídeo", Modifier.size(24.dp), tint = Color.White)
                                // Un punto avisa de que hay algo cambiado dentro.
                                if (muted || looping || boost > 100 || voice || (cues.isNotEmpty() && subsOn)) {
                                    Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(7.dp).clip(CircleShape).background(accent))
                                }
                            }
                        }
                    } else if (items.size > 1) {
                        Filmstrip(items, pager.currentPage.coerceIn(0, items.lastIndex)) { index ->
                            // Un salto corto se ve pasar; uno largo va directo.
                            scope.launch { if (abs(index - pager.currentPage) <= 3) pager.animateScrollToPage(index) else pager.scrollToPage(index) }
                        }
                    }
                    // Teléfonos, correos y webs escritos en la foto, con su botón.
                    if (!current.isVideo) {
                        val text = state.index[current.id]?.text.orEmpty()
                        val contacts = remember(current.id, text) { findContacts(text) }
                        if (contacts.isNotEmpty()) ContactRow(contacts)
                    }
                    // Acciones: pocas y sin cajas, para que la foto siga siendo lo importante.
                    Row(Modifier.fillMaxWidth()) {
                        if (current.isExternal) {
                            // Lo abierto desde la carpeta privada es una copia temporal: no se ofrece enviarla.
                            if (current.uri.scheme != "file") {
                                ToolButton(Icons.Filled.Share, "Enviar", Modifier.weight(1f)) { actions.share(listOf(current)) }
                            }
                        } else {
                            ToolButton(Icons.Filled.Share, "Enviar", Modifier.weight(1f)) { actions.share(listOf(current)) }
                            if (current.isVideo) {
                                ToolButton(Icons.Filled.Edit, "Editar", Modifier.weight(1f)) { leaveTo(Screen.VideoEditor(current.id)) }
                            } else {
                                ToolButton(Icons.Filled.Edit, "Editar", Modifier.weight(1f)) { leaveTo(Screen.Editor(current.id)) }
                            }
                            ToolButton(Icons.Filled.Delete, "Borrar", Modifier.weight(1f)) { actions.trash(listOf(current)) {} }
                        }
                    }
                }
            }
        }
    }

    // Todo lo demás del reproductor, ordenado por grupos en vez de en una fila sin fin.
    if (videoOptions && current.isVideo) {
        ModalBottomSheet(onDismissRequest = { videoOptions = false }, containerColor = Lumi.Surface) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Opciones del vídeo", style = HeadingStyle.copy(fontSize = 20.sp), modifier = Modifier.padding(bottom = 6.dp))
                OptionGroup("Reproducción")
                OptionRow("Velocidad", if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×") {
                    speed = when (speed) {
                        1f -> 1.5f
                        1.5f -> 2f
                        2f -> 0.5f
                        else -> 1f
                    }
                }
                OptionRow("Repetir", if (looping) "Sí" else "No") { looping = !looping }
                OptionRow("Girar la pantalla", if (landscape) "Sí" else "No") { landscape = !landscape }
                OptionRow("Ventana flotante", null, "Sigue viéndose encima de otras apps") { videoOptions = false; actions.pip() }
                OptionRow("Bloquear la pantalla", null, "Para que un toque sin querer no lo pare") {
                    videoOptions = false
                    touchLock = true
                    chrome = false
                }
                OptionGroup("Sonido")
                OptionRow("Sonido", if (muted) "Silenciado" else "Activado") { muted = !muted }
                OptionRow("Volumen extra", "$boost %", "Para vídeos grabados muy bajos") { boost = if (boost >= 200) 100 else boost + 50 }
                OptionRow("Realzar voces", if (voice) "Sí" else "No", "Se entiende mejor a quien habla") { voice = !voice }
                OptionGroup("Subtítulos")
                if (cues.isEmpty()) {
                    OptionRow("Cargar subtítulos", null, "Elige un archivo .srt del teléfono") {
                        pickSubs.launch(arrayOf("application/x-subrip", "text/plain", "application/octet-stream", "*/*"))
                    }
                } else {
                    OptionRow("Mostrar", if (subsOn) "Sí" else "No") { subsOn = !subsOn }
                    OptionRow("Tamaño", listOf("Pequeño", "Mediano", "Grande")[subSize]) { subSize = (subSize + 1) % 3 }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Sincronizar", style = LabelStyle.copy(fontSize = 15.sp))
                            Text(if (subShift == 0L) "Sin cambios" else String.format(java.util.Locale.US, "%+.1f s", subShift / 1000.0), style = SmallStyle)
                        }
                        PillButton("−0,5 s", { subShift -= 500 }, primary = false)
                        PillButton("+0,5 s", { subShift += 500 }, primary = false)
                    }
                    OptionRow("Otro archivo", null) { pickSubs.launch(arrayOf("*/*")) }
                }
                if (!current.isExternal) {
                    OptionGroup("Crear")
                    OptionRow("Guardar fotograma", null, "El momento que se ve, como foto") { videoOptions = false; vm.saveFrame(current, position) }
                    OptionRow("Hacer un GIF", null, "Un trozo de hasta 8 segundos") { videoOptions = false; leaveTo(Screen.Gif(current.id)) }
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
            onInfo = { more = false; info = true },
            motion = isMotion,
            onMotion = { more = false; motionOpen = true },
            landscape = landscape,
            onRename = { more = false; renaming = true },
            onSetAs = { more = false; leaveTo(Screen.Wallpaper(current.id)) },
            onRotate = { more = false; leaveTo(Screen.Rotate(current.id)) },
            onScreen = { more = false; landscape = !landscape },
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
                showText()
            },
            onCutout = { more = false; leaveTo(Screen.Cutout(current.id)) },
            onPortrait = { more = false; leaveTo(Screen.Portrait(current.id)) },
            onQr = {
                more = false
                scope.launch {
                    val found = readCodes(context, current.uri)
                    if (found.isEmpty()) vm.say("No se ve ningún código en esta foto") else qrCodes = found
                }
            },
            onSign = { more = false; leaveTo(Screen.SignDoc(current.uri.toString(), pdf = false, photoId = current.id)) },
            onScan = { more = false; scanDocument() },
            onCopyImage = {
                more = false
                // Se copia como imagen: en un chat o un documento basta con mantener pulsado y pegar.
                runCatching {
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newUri(context.contentResolver, "Lumi", current.uri))
                }
                vm.say("Copiada. Mantén pulsado en un chat para pegarla.")
            },
            onShrink = {
                more = false
                player.pause()
                shrinking = current
            },
        )
    }
    shrinking?.let { video -> ShrinkSheet(video, vm) { shrinking = null } }
    if (motionOpen && isMotion) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { motionOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) { MotionScreen(current, vm) { motionOpen = false } }
    }
    if (qrCodes.isNotEmpty()) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { qrCodes = emptyList() }, containerColor = Lumi.Bg) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                qrCodes.forEach { QrCard(it) }
            }
        }
    }
    faceMenu?.let { (face, person) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { faceMenu = null },
            containerColor = Lumi.Surface,
            title = { Text(person?.name ?: "¿Quién es?", style = HeadingStyle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (person?.name != null) {
                        Text("No es ${person.name}", style = LabelStyle.copy(fontSize = 15.sp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            faceMenu = null
                            vm.answerDoubt(com.lumi.galeria.data.Doubt(face, person, true), yes = false)
                        }.padding(12.dp))
                    }
                    Text("Es otra persona…", style = LabelStyle.copy(fontSize = 15.sp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        faceMenu = null
                        naming = face
                    }.padding(12.dp))
                }
            },
            confirmButton = {},
        )
    }
    naming?.let { face ->
        NameDialog("", state.people.mapNotNull { it.name }.distinct(), { naming = null }) { name -> vm.faceIsNew(face, name) }
    }
    if (renaming) {
        RenameDialog(
            current,
            onDone = { name ->
                renaming = false
                actions.write(listOf(current)) { vm.rename(current, name) }
            },
            onDismiss = { renaming = false },
        )
    }
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

/** Botones para llamar, escribir o abrir lo que aparece escrito en la foto. Lumi no se conecta: abre otra app. */
@Composable
private fun ContactRow(contacts: List<Contact>) {
    val context = LocalContext.current
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        contacts.forEach { contact ->
            val (icon, intent) = when (contact.kind) {
                ContactKind.PHONE -> Icons.Filled.Phone to Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + contact.value.filter { it.isDigit() || it == '+' }))
                ContactKind.EMAIL -> Icons.Filled.Email to Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:" + contact.value))
                ContactKind.WEB -> Icons.AutoMirrored.Filled.ExitToApp to Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(if (contact.value.startsWith("http")) contact.value else "https://" + contact.value),
                )
            }
            Row(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                    .clickable { runCatching { context.startActivity(intent) } }
                    .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, Modifier.size(16.dp), tint = Color.White)
                Text(contact.value, style = LabelStyle, color = Color.White, maxLines = 1)
            }
        }
    }
}

/**
 * El texto de la foto marcado encima de ella. Tocar una línea la elige; arrastrar elige varias
 * seguidas. Abajo, copiar lo elegido (o todo) y los teléfonos, correos y webs que haya, a un toque.
 */
@Composable
private fun LiveText(page: com.lumi.galeria.data.TextPage, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var chosen by remember(page) { mutableStateOf(emptySet<Int>()) }
    var anchor by remember(page) { mutableStateOf(-1) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val lines = page.lines
    val picked = if (chosen.isEmpty()) lines.map { it.text } else chosen.sorted().map { lines[it].text }
    val text = picked.joinToString("\n")
    val contacts = remember(text) { findContacts(text) }
    val linked = remember(page) { lines.indices.filter { findContacts(lines[it].text).isNotEmpty() }.toSet() }

    /** Dónde se dibuja la foto, ajustada a la pantalla sin deformarla. */
    fun frame(): androidx.compose.ui.geometry.Rect {
        val w = area.width.toFloat()
        val h = area.height.toFloat()
        val fw = minOf(w, h * page.aspect)
        val fh = fw / page.aspect
        return androidx.compose.ui.geometry.Rect((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2)
    }

    fun lineAt(point: Offset): Int {
        val f = frame()
        val x = (point.x - f.left) / f.width
        val y = (point.y - f.top) / f.height
        val slack = 0.012f
        return lines.indexOfFirst { x in it.left - slack..it.right + slack && y in it.top - slack..it.bottom + slack }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { area = it }
                .pointerInput(page) {
                    detectTapGestures { point ->
                        val hit = lineAt(point)
                        if (hit < 0) {
                            chosen = emptySet()
                        } else {
                            chosen = if (hit in chosen) chosen - hit else chosen + hit
                            anchor = hit
                        }
                    }
                }
                .pointerInput(page) {
                    detectDragGestures(
                        onDragStart = { start ->
                            anchor = lineAt(start)
                            if (anchor >= 0) chosen = setOf(anchor)
                        },
                    ) { change, _ ->
                        val hit = lineAt(change.position)
                        if (anchor >= 0 && hit >= 0) chosen = (minOf(anchor, hit)..maxOf(anchor, hit)).toSet()
                        change.consume()
                    }
                },
        ) {
            drawRect(Color.Black.copy(alpha = 0.3f))
            val f = frame()
            lines.forEachIndexed { i, line ->
                val topLeft = Offset(f.left + line.left * f.width, f.top + line.top * f.height)
                val size = androidx.compose.ui.geometry.Size((line.right - line.left) * f.width, (line.bottom - line.top) * f.height)
                val tone = when {
                    i in chosen -> Color(0xFFB9A8FF).copy(alpha = 0.5f)
                    i in linked -> Color(0xFF4F8DF7).copy(alpha = 0.35f)
                    else -> Color(0xFFFFD43B).copy(alpha = 0.22f)
                }
                drawRoundRect(tone, topLeft, size, androidx.compose.ui.geometry.CornerRadius(6f))
                drawRoundRect(
                    if (i in chosen) Color(0xFFB9A8FF) else Color.White.copy(alpha = 0.55f), topLeft, size,
                    androidx.compose.ui.geometry.CornerRadius(6f), style = Stroke(1.5.dp.toPx()),
                )
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.78f)).navigationBarsPadding().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (chosen.isEmpty()) "Toca o arrastra sobre el texto para elegir un trozo" else countText(chosen.size, "línea elegida", "líneas elegidas"),
                style = SmallStyle, color = Color.White.copy(alpha = 0.8f),
            )
            if (chosen.isNotEmpty()) Text(text, style = LabelStyle, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (contacts.isNotEmpty()) ContactRow(contacts)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (chosen.isEmpty()) "Copiar todo" else "Copiar", onClick = {
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, tr("Texto copiado"), Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f))
                PillButton("Compartir", onClick = {
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                    }
                }, modifier = Modifier.weight(1f), primary = false)
                PillButton("Cerrar", onClick = onClose, modifier = Modifier.weight(1f), primary = false)
            }
        }
    }
}

/** El estado de la app, para piezas pequeñas (como las caras) que lo necesitan sin pasarlo por todas partes. */
val LocalState = androidx.compose.runtime.staticCompositionLocalOf { UiState() }

/**
 * Los nombres de quien sale, encima de cada cara de la foto. Tocar un nombre abre a esa persona;
 * «¿Quién es?» deja ponérselo. Tocar fuera lo cierra.
 */
@Composable
private fun FaceTags(
    item: MediaItem,
    faces: List<Pair<com.lumi.galeria.data.Face, com.lumi.galeria.data.Person?>>,
    onClose: () -> Unit,
    onTap: (com.lumi.galeria.data.Face, com.lumi.galeria.data.Person?) -> Unit,
) {
    BackHandler(onBack = onClose)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().clickable(onClick = onClose)) {
        val aspect = item.shownWidth.toFloat() / item.shownHeight.coerceAtLeast(1)
        val boxW = maxWidth
        val boxH = maxHeight
        val fitW = if (boxW / boxH > aspect) boxH * aspect else boxW
        val fitH = fitW / aspect
        val left = (boxW - fitW) / 2
        val top = (boxH - fitH) / 2
        faces.forEach { (face, person) ->
            val x = left + fitW * face.left
            val y = top + fitH * face.top
            val w = fitW * (face.right - face.left)
            val h = fitH * (face.bottom - face.top)
            Box(
                Modifier.offset(x, y).size(w, h).border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(10.dp)),
            )
            Text(
                person?.name ?: "¿Quién es?",
                style = LabelStyle, color = if (person?.name != null) Lumi.OnAccent else Color.White,
                modifier = Modifier.offset(x, y + h + 4.dp).clip(CircleShape)
                    .background(if (person?.name != null) Lumi.Accent else Color.Black.copy(alpha = 0.7f))
                    .clickable { onTap(face, person) }.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** Mando redondo de la fila del reproductor: un texto corto, encendido si cambia algo. */
@Composable
private fun RoundControl(label: String, description: String, on: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (on) accent else Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick)
            .semantics { contentDescription = tr(description) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = if (on) OnTint else Color.White, maxLines = 1)
    }
}

/** Acción de abajo del visor: icono con su nombre, sin caja alrededor. */
@Composable
private fun ToolButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, Modifier.size(23.dp), tint = Color.White)
        Text(label, style = SmallStyle, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
    }
}

@Composable
private fun OptionGroup(title: String) {
    Text(title, style = SmallStyle.copy(fontSize = 13.sp), color = Lumi.Accent, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
}

/** Una opción de la hoja: nombre, explicación corta y, a la derecha, cómo está ahora. */
@Composable
private fun OptionRow(title: String, value: String?, hint: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LabelStyle.copy(fontSize = 15.sp))
            if (hint != null) Text(hint, style = SmallStyle)
        }
        if (value != null) Text(value, style = LabelStyle, color = Lumi.Accent)
    }
}

/** El fotograma de [atMs] mientras se arrastra la barra, a la altura de [fraction]. */
@Composable
private fun SeekPreview(item: MediaItem, atMs: Long, fraction: Float) {
    val context = LocalContext.current
    val retriever = remember(item.id) { MediaMetadataRetriever() }
    DisposableEffect(retriever) { onDispose { runCatching { retriever.release() } } }
    var ready by remember(item.id) { mutableStateOf(false) }
    // Un fotograma cada medio segundo de vídeo basta: pedir uno por cada píxel del dedo atasca.
    val frame by produceState<Bitmap?>(null, item.id, atMs / 500) {
        val shot = withContext(Dispatchers.IO) {
            synchronized(retriever) {
                runCatching {
                    if (!ready) {
                        retriever.setDataSource(context, item.uri)
                        ready = true
                    }
                    retriever.getScaledFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 360)
                }.getOrNull()
            }
        }
        if (shot != null) value = shot
    }
    // Con el dedo quieto, el fotograma exacto (no el más cercano que se carga rápido).
    val exact by produceState<Bitmap?>(null, item.id, atMs) {
        delay(220)
        value = withContext(Dispatchers.IO) {
            synchronized(retriever) {
                runCatching {
                    if (!ready) {
                        retriever.setDataSource(context, item.uri)
                        ready = true
                    }
                    retriever.getScaledFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 360, 360)
                }.getOrNull()
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        val width = 150.dp
        Column(
            Modifier.padding(start = (maxWidth - width) * fraction.coerceIn(0f, 1f)).width(width),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(10.dp)).background(Color.Black).border(2.dp, Color.White, RoundedCornerShape(10.dp))) {
                (exact ?: frame)?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High) }
            }
            Text(formatDuration(atMs), style = LabelStyle, color = Color.White, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Volumen por encima del máximo y realce de voces, colgados de la sesión de sonido del vídeo.
 * Si el teléfono no los admite, no hacen nada.
 */
private class SoundEffects(private val session: Int) {
    private var loudness: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null

    fun set(volume: Int, voice: Boolean) {
        runCatching {
            if (volume > 100 || loudness != null) {
                val effect = loudness ?: LoudnessEnhancer(session).also { loudness = it }
                // 150 % son unos 3,5 dB más; 200 %, unos 6 dB.
                effect.setTargetGain(((volume - 100) * 12).coerceAtLeast(0))
                effect.enabled = volume > 100
            }
        }
        runCatching {
            if (voice || equalizer != null) {
                val eq = equalizer ?: Equalizer(0, session).also { equalizer = it }
                val (low, high) = eq.bandLevelRange.let { it[0] to it[1] }
                for (band in 0 until eq.numberOfBands) {
                    val hz = eq.getCenterFreq(band.toShort()) / 1000
                    // Las voces viven entre 1 y 4 kHz: se suben; los graves que las tapan, se bajan.
                    val level = when {
                        hz in 1000..4000 -> 600
                        hz < 250 -> -400
                        else -> 0
                    }
                    eq.setBandLevel(band.toShort(), level.coerceIn(low.toInt(), high.toInt()).toShort())
                }
                eq.enabled = voice
            }
        }
    }

    fun release() {
        runCatching { loudness?.release() }
        runCatching { equalizer?.release() }
    }
}

/**
 * Lo que rodea a una foto que no llena la pantalla: la misma foto, muy desenfocada y oscura, en
 * vez de bandas negras. Se parte de una miniatura diminuta, que al ampliarse ya sale borrosa.
 */
@Composable
private fun Backdrop(item: MediaItem) {
    AsyncImage(
        Thumb(item.uri, 64, item.modified), null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(40.dp) else Modifier),
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
}

@Composable
private fun ZoomablePhoto(item: MediaItem, active: Boolean, onTap: () -> Unit, onHold: ((Boolean) -> Unit)? = null, locked: Boolean = false) {
    var scale by remember { mutableFloatStateOf(1f) }
    // Al ampliar se carga la foto a mucha más resolución; se queda cargada mientras se mira.
    var sharp by remember { mutableStateOf(false) }
    LaunchedEffect(scale > 1.4f, active) {
        if (!active) sharp = false else if (scale > 1.4f) sharp = true
    }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)

    fun clamp(value: Offset, forScale: Float): Offset {
        val maxX = size.width * (forScale - 1) / 2
        val maxY = size.height * (forScale - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    LaunchedEffect(active, locked) {
        // Con el texto marcado encima, la foto se queda entera y quieta para que las marcas coincidan.
        if (!active || locked) {
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
                    // Foto en movimiento: se mueve mientras el dedo sigue encima.
                    onPress = {
                        tryAwaitRelease()
                        hold?.invoke(false)
                    },
                    onLongPress = { if (scale == 1f) hold?.invoke(true) },
                    onDoubleTap = { point ->
                        val next = when {
                            scale < 2f -> 2.5f
                            scale < 4.5f -> 5f
                            else -> 1f
                        }
                        if (next == 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            // El punto tocado se queda bajo el dedo al ampliar.
                            val touched = point - Offset(size.width / 2f, size.height / 2f)
                            offset = clamp(touched - (touched - offset) * (next / scale), next)
                            scale = next
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
        // Ultra HDR: la foto con su mapa de ganancia, que la pantalla enseña con más brillo en las luces.
        val context = LocalContext.current
        val hdr by produceState<Bitmap?>(null, item.uri) {
            if (android.os.Build.VERSION.SDK_INT >= 34 && !item.isGif && !item.isExternal) {
                value = withContext(Dispatchers.IO) { com.lumi.galeria.data.decodeUltraHdr(context, item.uri) }
            }
        }
        if (sharp && !item.isGif && hdr == null) {
            AsyncImage(
                ImageRequest.Builder(LocalContext.current).data(item.uri).size(4096).memoryCacheKey("nitida:${item.uri}").build(),
                null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
            )
        }
        hdr?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.High) }
    }
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
    fast: Boolean,
    onHold: (Boolean) -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
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
                // Mantener el dedo quieto: el doble de rápido hasta soltarlo. El toque de soltar se
                // lo queda este gesto, para que no enseñe ni esconda los controles.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    hold(true)
                    try {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == press.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                        }
                    } finally {
                        hold(false)
                    }
                }
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
        if (fast) {
            Text(
                "⏩ 2×",
                style = HeadingStyle,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 16.dp, vertical = 8.dp),
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
    onInfo: () -> Unit,
    motion: Boolean,
    onMotion: () -> Unit,
    landscape: Boolean,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
    onShareClean: () -> Unit,
    onSetAs: () -> Unit,
    onUseAs: () -> Unit,
    onRotate: () -> Unit,
    onScreen: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onHide: () -> Unit,
    onSlideshow: () -> Unit,
    onCover: () -> Unit,
    onMarkup: () -> Unit,
    onText: () -> Unit,
    onCutout: () -> Unit,
    onPortrait: () -> Unit,
    onQr: () -> Unit,
    onSign: () -> Unit,
    onShrink: () -> Unit,
    onScan: () -> Unit,
    onCopyImage: () -> Unit,
) {
    // Lo más usado, en botones redondos; según sea foto o vídeo.
    val quick: List<Triple<ImageVector, String, () -> Unit>> = if (item.isVideo) listOf(
        Triple(InfoIcon, "Información", onInfo),
        Triple(ShrinkIcon, "Reducir", onShrink),
        Triple(LandscapeIcon, if (landscape) "Vertical" else "Horizontal", onScreen),
        Triple(CopyIcon, "Copiar", onCopyImage),
        Triple(AlbumIcon, "Álbum", onMove),
        Triple(Icons.Filled.Lock, "Ocultar", onHide),
        Triple(SlidesIcon, "Presentación", onSlideshow),
        Triple(PenIcon, "Nombre", onRename),
    ) else listOf(
        Triple(PictureIcon, "Fondo", onSetAs),
        Triple(RotateIcon, "Girar", onRotate),
        Triple(TextIcon, "Texto", onText),
        Triple(PersonIcon, "Sin fondo", onCutout),
        Triple(CopyIcon, "Copiar", onCopyImage),
        Triple(AlbumIcon, "Álbum", onMove),
        Triple(Icons.Filled.Lock, "Ocultar", onHide),
        Triple(PenIcon, "Dibujar", onMarkup),
    )
    // El resto, en una lista corta con iconos.
    val rows: List<Triple<ImageVector, String, () -> Unit>> = buildList {
        if (motion) add(Triple(SlidesIcon, "Foto en movimiento: guardar el vídeo o un instante", onMotion))
        if (!item.isVideo) {
            add(Triple(PersonIcon, "Modo retrato: desenfocar el fondo", onPortrait))
            add(Triple(ScanIcon, "Leer el código QR", onQr))
            add(Triple(PenIcon, "Firmar", onSign))
            add(Triple(InfoIcon, "Información", onInfo))
            add(Triple(PenIcon, "Cambiar el nombre", onRename))
            add(Triple(LandscapeIcon, if (landscape) "Volver a vertical" else "Ver en horizontal", onScreen))
            add(Triple(ScanIcon, "Escanear documento", onScan))
            add(Triple(SlidesIcon, "Presentación", onSlideshow))
        }
        add(Triple(AlbumIcon, "Copiar a un álbum", onCopy))
        add(Triple(CoverIcon, "Usar como portada del álbum", onCover))
        if (!item.isVideo) {
            add(Triple(NoPlaceIcon, "Enviar sin ubicación", onShareClean))
            add(Triple(OpenWithIcon, "Usar en otra app", onUseAs))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lumi.Surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Qué archivo es: miniatura, nombre y peso.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MediaThumb(item, 160, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = HeadingStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        formatSize(item.size) + if (item.width > 0) " · ${item.width} × ${item.height}" else "",
                        style = SmallStyle,
                    )
                }
            }
            quick.chunked(4).forEach { line ->
                Row(Modifier.fillMaxWidth()) {
                    line.forEach { (icon, label, act) -> QuickButton(icon, label, Modifier.weight(1f), act) }
                    repeat(4 - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Bg)) {
                rows.forEachIndexed { i, (icon, title, act) ->
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = act).padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(icon, null, Modifier.size(20.dp), tint = Lumi.Accent)
                        Text(title, style = LabelStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = Lumi.Muted)
                    }
                    if (i < rows.lastIndex) HorizontalDivider(Modifier.padding(start = 50.dp), color = Lumi.Surface, thickness = 1.dp)
                }
            }
        }
    }
}

/** Botón redondo del menú «Más»: icono en un círculo con su nombre debajo. */
@Composable
private fun QuickButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Lumi.Accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = Lumi.Accent)
        }
        Text(label, style = SmallStyle.copy(fontSize = 12.sp), color = Lumi.Ink, maxLines = 1)
    }
}

/**
 * Los datos de la foto o el vídeo, en un panel bajo la imagen, que sube para dejarle sitio. El
 * lugar, la cámara y lo que Lumi reconoce van como etiquetas; el lugar y las cosas buscan fotos
 * parecidas. Una tira enseña las demás fotos de ese día. Se cierra deslizando hacia abajo,
 * tocando la foto o con «atrás».
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun InfoPanel(
    faces: List<Pair<com.lumi.galeria.data.Face, com.lumi.galeria.data.Person?>>,
    onFace: (com.lumi.galeria.data.Face, com.lumi.galeria.data.Person?) -> Unit,
    onFaceLong: (com.lumi.galeria.data.Face, com.lumi.galeria.data.Person?) -> Unit,
    onFaceTags: () -> Unit,
    related: List<Pair<String, List<MediaItem>>>,
    onRelated: (List<MediaItem>, MediaItem) -> Unit,
    item: MediaItem,
    place: String?,
    things: List<String>,
    sameDay: List<MediaItem>,
    onJump: (MediaItem) -> Unit,
    onPlace: (String) -> Unit,
    onThing: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    // Lo que anotó la cámara se lee al abrir el panel, no antes.
    val camera by produceState(emptyList<Pair<String, String>>(), item.id) {
        value = withContext(Dispatchers.IO) { cameraInfo(context, item) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Lumi.Surface)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Lumi.Line))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dayTitle(item.date), style = HeadingStyle.copy(fontSize = 20.sp))
                Text(
                    timeText(item.date) + " · " + item.name + " · " + formatSize(item.size),
                    style = SmallStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            BarIcon(Icons.Filled.Close, "Cerrar", onClose)
        }
        // Lugar y cámara, como etiquetas.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (place != null) InfoChip(place, Icons.Filled.Place) { onPlace(place) }
            camera.forEach { (label, value) -> InfoChip(if (label == "Flash") "Flash" else value) }
            if (item.width > 0 && !item.isVideo) {
                InfoChip(String.format(Locale.US, "%.1f MP", item.width * item.height / 1_000_000.0))
            }
            if (item.isVideo) InfoChip(formatDuration(item.duration))
        }
        if (sameDay.size > 1) {
            Text(countText(sameDay.size, "foto ese día", "fotos ese día"), style = LabelStyle)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(sameDay, key = { it.id }) { other ->
                    val here = other.id == item.id
                    MediaThumb(
                        other, 160,
                        Modifier.size(58.dp).clip(RoundedCornerShape(10.dp))
                            .then(if (here) Modifier.border(2.dp, Lumi.Accent, RoundedCornerShape(10.dp)) else Modifier)
                            .clickable { onJump(other) },
                    )
                }
            }
        }
        if (things.isNotEmpty()) {
            Text("Lumi ve", style = LabelStyle)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                things.forEach { thing -> InfoChip(thing.replaceFirstChar { it.uppercase() }, Icons.Filled.Search) { onThing(thing) } }
            }
        }
        if (faces.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("En esta foto", style = LabelStyle)
                    Text("Mantén pulsada una cara si no es quien dice", style = SmallStyle)
                }
                Text(
                    "Ver nombres en la foto", style = LabelStyle, color = Lumi.Accent,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onFaceTags).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                faces.forEach { (face, person) ->
                    Column(
                        Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { onFace(face, person) }, onLongClick = { onFaceLong(face, person) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        FaceCircle(face, LocalState.current, 54.dp)
                        Text(
                            person?.name ?: "¿Quién es?", style = SmallStyle.copy(fontSize = 11.sp),
                            color = if (person?.name == null) Lumi.Accent else Lumi.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (related.isNotEmpty()) {
            var chosen by remember(item.id) { mutableStateOf(0) }
            Text("Más como esta", style = LabelStyle)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                related.forEachIndexed { i, (label, list) ->
                    val on = i == chosen.coerceAtMost(related.lastIndex)
                    Text(
                        "$label · ${list.size}", style = LabelStyle, color = if (on) Lumi.OnAccent else Lumi.Ink,
                        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Bg).clickable { chosen = i }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            val list = related[chosen.coerceAtMost(related.lastIndex)].second
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list.take(40), key = { it.id }) { other ->
                    MediaThumb(other, 160, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).clickable { onRelated(list, other) })
                }
            }
        }
        if (item.path.isNotEmpty()) DetailRow("Carpeta", item.path.trimEnd('/'))
        if (!item.isExternal) DetailRow("Guardada en", if (item.onCard) "Tarjeta de memoria" else "Memoria del teléfono")
    }
}

/**
 * Hilos que salen de [item]: las mismas cosas, las mismas personas, el mismo sitio y el mismo día
 * de otros años. Cada uno con sus fotos, de la más reciente a la más antigua y sin la propia.
 */
private fun relatedTo(item: MediaItem, state: UiState): List<Pair<String, List<MediaItem>>> {
    if (item.isExternal) return emptyList()
    val out = ArrayList<Pair<String, List<MediaItem>>>()
    val others = state.items.filter { it.id != item.id && !it.isScreenshot }
    state.people.filter { it.name != null && item.id in it.photos }.forEach { person ->
        val ids = person.photos.toHashSet()
        others.filter { it.id in ids }.takeIf { it.isNotEmpty() }?.let { out += person.name!! to it }
    }
    com.lumi.galeria.data.thingWords(state.index[item.id]).take(3).forEach { word ->
        others.filter { word in com.lumi.galeria.data.thingWords(state.index[it.id]) }.takeIf { it.size >= 2 }?.let {
            out += word.replaceFirstChar(Char::uppercase) to it
        }
    }
    state.places[item.id]?.let { place ->
        others.filter { state.places[it.id] == place }.takeIf { it.isNotEmpty() }?.let { out += place to it }
    }
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(item.date).atZone(zone).toLocalDate()
    others.filter {
        val d = java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        d.year != day.year && d.monthValue == day.monthValue && kotlin.math.abs(d.dayOfMonth - day.dayOfMonth) <= 1
    }.takeIf { it.isNotEmpty() }?.let { out += "Este día, otros años" to it }
    return out
}

/** Etiqueta del panel de información. Con [onClick], lleva a buscar. */
@Composable
private fun InfoChip(label: String, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.clip(CircleShape).background(Lumi.Bg).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = Lumi.Accent)
        Text(label, style = LabelStyle, maxLines = 1)
    }
}

@Composable
private fun MenuGroup(title: String) {
    Text(title, style = SmallStyle.copy(fontSize = 13.sp), color = Lumi.Accent, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
}

@Composable
private fun MenuItem(title: String, hint: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 9.dp)) {
        Text(title, style = LabelStyle.copy(fontSize = 15.sp))
        if (hint != null) Text(hint, style = SmallStyle)
    }
}

/** Cambiar el nombre del archivo, sin la extensión, que no se toca. */
@Composable
private fun RenameDialog(item: MediaItem, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name.substringBeforeLast('.')) }
    val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text("Cambiar el nombre", style = HeadingStyle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Text("." + item.name.substringAfterLast('.', ""), style = SmallStyle)
            }
        },
        confirmButton = {
            Text(
                "Guardar", style = LabelStyle, color = if (clean.isEmpty()) Lumi.Muted else Lumi.Accent,
                modifier = Modifier.clip(CircleShape).clickable(enabled = clean.isNotEmpty()) { onDone(clean) }.padding(12.dp),
            )
        },
        dismissButton = {
            Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(12.dp))
        },
    )
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
            if (isBest) "Elegida por nitidez y, si sale gente, con todos los ojos abiertos. Toca otra si prefieres quedarte con ella."
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    "Conservar las ${stack.members.size}",
                    onClick = { vm.keepStack(stack); vm.back() },
                    modifier = Modifier.weight(1f),
                    primary = false,
                )
                PillButton(
                    "Animar",
                    onClick = { vm.open(Screen.Animate(stack.members.filter { !it.isVideo }.map { it.id })) },
                    modifier = Modifier.weight(1f),
                    primary = false,
                )
            }
        }
    }
}
