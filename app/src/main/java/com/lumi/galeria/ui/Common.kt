package com.lumi.galeria.ui

import com.lumi.galeria.data.cardVolume
import androidx.compose.foundation.gestures.calculateZoom
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumi.galeria.tr
import com.lumi.galeria.dayTitle
import kotlin.math.abs
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.PinStep
import com.lumi.galeria.Screen
import com.lumi.galeria.Thumb
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.formatSize
import com.lumi.galeria.data.Album
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.data.isWritableAlbumPath
import com.lumi.galeria.formatDuration
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Acciones que necesitan a la actividad: diálogos del sistema y otras apps. */
class Actions(
    val trash: (items: List<MediaItem>, onDone: () -> Unit) -> Unit,
    val restore: (items: List<MediaItem>) -> Unit,
    val deleteForever: (items: List<MediaItem>) -> Unit,
    /** Pide al sistema permiso para modificar [items] y, si lo da, ejecuta [onGranted]. */
    val write: (items: List<MediaItem>, onGranted: () -> Unit) -> Unit,
    val share: (items: List<MediaItem>) -> Unit,
    val shareWithoutLocation: (item: MediaItem) -> Unit,
    val useAs: (item: MediaItem) -> Unit,
    val requestAccess: () -> Unit,
    val openSettings: () -> Unit,
    /** Devuelve lo elegido a la app que pidió una foto. */
    val pickResult: (items: List<MediaItem>) -> Unit,
    /** Pide la huella o el bloqueo del teléfono y, si se supera, ejecuta [onOk]. */
    val unlock: (onOk: () -> Unit) -> Unit,
    /** Hay algún modo de comprobar quién eres: bloqueo del teléfono o PIN propio. */
    val canUnlock: () -> Boolean,
    /** Abre los ajustes del sistema donde se concede el acceso a todos los archivos. */
    val allFiles: () -> Unit,
    /** Deja el vídeo en una ventana flotante sobre las demás apps. */
    val pip: () -> Unit,
    /** Abre la cámara del teléfono. */
    val camera: () -> Unit,
    /** Comparte archivos recién creados (por ejemplo, fotos exportadas). */
    val shareUris: (uris: List<android.net.Uri>) -> Unit = {},
)

/**
 * Une el visor con la cuadrícula que tiene debajo: dónde está cada miniatura en pantalla
 * (para que la foto crezca desde ella y vuelva a ella) y cómo llevar la cuadrícula a una foto.
 */
class GridLink {
    var boundsOf: (id: Long) -> Rect? = { null }
    var reveal: (suspend (id: Long) -> Unit)? = null
}

/**
 * Hueco que ocupa en pantalla el elemento [key] de una cuadrícula. Se calcula solo cuando hace
 * falta, a partir de lo que la cuadrícula ya sabe; así las miniaturas no informan de su posición
 * en cada fotograma mientras se desplaza.
 */
fun tileBounds(grid: LazyGridState, container: LayoutCoordinates?, key: Any): Rect? {
    val box = container?.takeIf { it.isAttached } ?: return null
    val info = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return null
    val origin = box.boundsInRoot().topLeft
    val left = origin.x + info.offset.x
    val top = origin.y + info.offset.y - grid.layoutInfo.viewportStartOffset
    return Rect(left, top, left + info.size.width, top + info.size.height)
}

/** [tone] es el color medio de la foto: se pinta en su hueco mientras llega la miniatura. */
@Composable
fun MediaThumb(item: MediaItem, px: Int, modifier: Modifier = Modifier, tone: Int = 0) {
    AsyncImage(
        model = Thumb(item.uri, px, item.modified),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // Solo vale un color opaco: 0 y COLOR_NONE significan que no se sabe.
        modifier = modifier.background(if (tone ushr 24 == 0xFF) Color(tone) else Lumi.Surface),
    )
}

@Composable
private fun Badge(modifier: Modifier = Modifier, color: Color = Color.Black.copy(alpha = 0.6f), content: @Composable () -> Unit) {
    Row(
        modifier
            .padding(5.dp)
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * Una foto de la cuadrícula. [selected] es null fuera del modo selección.
 * Las insignias se ocultan en miniaturas muy pequeñas para no taparlas.
 */
@Composable
fun PhotoTile(
    item: MediaItem,
    px: Int,
    modifier: Modifier = Modifier,
    stackSize: Int = 0,
    favorite: Boolean = false,
    selected: Boolean? = null,
    badges: Boolean = true,
    corner: Dp = 4.dp,
    caption: String? = null,
    tone: Int = 0,
    /** La leyenda avisa de algo que corre prisa: va en rojo. */
    urgent: Boolean = false,
    /** Parte del vídeo que ya se vio, de 0 a 1. */
    watched: Float = 0f,
    /** Mientras se mantiene el dedo encima de un vídeo, se reproduce aquí mismo. */
    preview: Boolean = false,
    /** Foto en movimiento: lleva un vídeo corto dentro. */
    motion: Boolean = false,
) {
    Box(
        modifier.clip(RoundedCornerShape(corner)).semantics {
            contentDescription = tr(if (item.isVideo) "Vídeo" else "Foto") + ", " + dayTitle(item.date) +
                (if (favorite) ", " + tr("favorita") else "")
            if (selected != null) this.selected = selected
        },
    ) {
        MediaThumb(item, px, Modifier.fillMaxSize(), tone)
        if (preview && item.isVideo) VideoPreview(item, Modifier.fillMaxSize())
        if (badges) {
            if (stackSize > 1) {
                Badge(Modifier.align(Alignment.TopEnd)) {
                    Text("$stackSize", style = SmallStyle, color = Color.White)
                }
            }
            if (item.isVideo) {
                Badge(Modifier.align(Alignment.BottomStart)) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(12.dp), tint = Color.White)
                    Text(formatDuration(item.duration), style = SmallStyle, color = Color.White)
                }
            } else if (caption != null) {
                Badge(Modifier.align(Alignment.BottomStart), if (urgent) Color(0xFFD93025) else Color.Black.copy(alpha = 0.6f)) {
                    Text(caption, style = SmallStyle, color = Color.White)
                }
            } else if (item.isGif) {
                Badge(Modifier.align(Alignment.BottomStart)) { Text("GIF", style = SmallStyle, color = Color.White) }
            } else if (motion) {
                // Un punto con su aro, como el botón de grabar: se mueve.
                Box(
                    Modifier.align(Alignment.BottomStart).padding(6.dp).size(14.dp).shadow(3.dp, CircleShape).clip(CircleShape)
                        .border(1.5.dp, Color.White, CircleShape).semantics { contentDescription = tr("Foto en movimiento") },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White)) }
            }
            if (item.onCard && selected == null) {
                // Pequeño y sin fondo: una sombra suave basta para que se lea sobre cualquier foto.
                Icon(
                    SdCardIcon, "En la tarjeta de memoria",
                    Modifier.align(Alignment.TopStart).padding(6.dp).size(15.dp).shadow(3.dp, CircleShape),
                    tint = Color.White,
                )
            }
        }
        if (watched > 0f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.5f))) {
                Box(Modifier.fillMaxWidth(watched.coerceIn(0f, 1f)).height(3.dp).background(Lumi.Accent))
            }
        }
        if (favorite) {
            // En las miniaturas muy pequeñas (vista por años) el corazón se encoge para no taparlas.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (badges) 5.dp else 2.dp)
                    .size(if (badges) 22.dp else 13.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, "Favorita", Modifier.size(if (badges) 14.dp else 9.dp), tint = Color(0xFFFF4D6D))
            }
        }
        if (selected != null) {
            if (selected) Box(Modifier.fillMaxSize().background(Lumi.Accent.copy(alpha = 0.28f)))
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Lumi.Accent else Color.Black.copy(alpha = 0.35f))
                    .border(1.5.dp, if (selected) Lumi.Accent else Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Lumi.OnAccent)
            }
        }
    }
}

@Composable
fun Dock(current: Screen, onSelect: (Screen) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Lumi.Surface.copy(alpha = 0.96f))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(Screen.Timeline to "Fotos", Screen.Albums to "Álbumes").forEach { (screen, label) ->
            val on = screen == current
            Text(
                label,
                style = LabelStyle,
                color = if (on) Lumi.OnAccent else Lumi.Muted,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) Lumi.Accent else Color.Transparent)
                    .semantics {
                        role = Role.Tab
                        selected = on
                    }
                    .clickable { onSelect(screen) }
                    .padding(horizontal = 26.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val background = when {
        !enabled -> Lumi.Surface
        primary -> Lumi.Accent
        else -> Lumi.Surface
    }
    val color = when {
        !enabled -> Lumi.Muted
        danger -> Lumi.Danger
        primary -> Lumi.OnAccent
        else -> Lumi.Ink
    }
    Text(
        text,
        style = LabelStyle.copy(fontWeight = FontWeight.Bold),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
    )
}

@Composable
fun BarIcon(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Lumi.Ink) {
    // Al menos 48 dp para el dedo, aunque el icono se vea más pequeño.
    Icon(icon, label, Modifier.minimumInteractiveComponentSize().clip(CircleShape).clickable(onClick = onClick).padding(10.dp).size(22.dp), tint = tint)
}

@Composable
fun ScreenHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Volver",
                Modifier.clip(CircleShape).clickable(onClick = onBack).padding(6.dp).size(24.dp),
                tint = Lumi.Ink,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = TitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = SmallStyle)
        }
        trailing?.invoke()
    }
}

/** Botón "Ordenar" con sus opciones; la elegida lleva una marca. */
@Composable
fun <T> SortMenu(options: List<T>, current: T, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "Ordenar",
            style = LabelStyle,
            color = Lumi.Accent,
            modifier = Modifier.clip(CircleShape).clickable { open = true }.padding(horizontal = 12.dp, vertical = 9.dp),
        )
        DropdownMenu(open, { open = false }, containerColor = Lumi.Surface) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), color = if (option == current) Lumi.Accent else Lumi.Ink) },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                    trailingIcon = { if (option == current) Icon(Icons.Filled.Check, null, tint = Lumi.Accent) },
                )
            }
        }
    }
}

/** Teclado del PIN propio de Lumi: para pedirlo o para crear uno nuevo. Tapa toda la pantalla. */
@Composable
fun PinOverlay(state: UiState, vm: LumiViewModel, onBiometric: (() -> Unit)?) {
    val step = state.pinStep ?: return
    var pin by remember(step) { mutableStateOf("") }
    BackHandler { vm.cancelPin() }

    Column(
        Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Text(
            when (step) {
                PinStep.ASK -> "Escribe tu PIN"
                PinStep.CREATE -> "Elige un PIN"
                PinStep.REPEAT, PinStep.DECOY_REPEAT -> "Escríbelo otra vez"
                PinStep.DECOY_CREATE -> "Elige el PIN señuelo"
            },
            style = TitleStyle.copy(fontSize = 26.sp),
        )
        Text(
            state.pinError ?: when (step) {
                PinStep.ASK -> "El PIN de Lumi, no el del teléfono"
                PinStep.DECOY_CREATE -> "Distinto de tu PIN. Abrirá otra carpeta privada."
                else -> "De 4 a 8 cifras"
            },
            style = SmallStyle.copy(fontSize = 14.sp), color = if (state.pinError != null) Lumi.Danger else Lumi.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(18.dp)) {
            repeat(maxOf(pin.length, 4)) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) Lumi.Accent else Lumi.Line))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(if (step == PinStep.ASK && onBiometric != null) "huella" else "", "0", "borrar")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEach { key ->
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (key.isEmpty()) Color.Transparent else Lumi.Surface)
                                .clickable(enabled = key.isNotEmpty()) {
                                    when (key) {
                                        "borrar" -> pin = pin.dropLast(1)
                                        "huella" -> onBiometric?.invoke()
                                        else -> if (pin.length < 8) pin += key
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (key) {
                                "borrar" -> Icon(Icons.AutoMirrored.Filled.ArrowBack, "Borrar", tint = Lumi.Ink)
                                "huella" -> Text("Huella", style = LabelStyle, color = Lumi.Accent)
                                else -> Text(key, style = TitleStyle.copy(fontSize = 26.sp))
                            }
                        }
                    }
                }
            }
        }
        PillButton("Aceptar", { vm.pinEntered(pin); pin = "" }, Modifier.fillMaxWidth(), enabled = pin.length >= 4)
        Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { vm.cancelPin() }.padding(10.dp))
    }
}

@Composable
fun EmptyMessage(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = HeadingStyle, textAlign = TextAlign.Center)
        Spacer(Modifier.size(8.dp))
        Text(text, style = SmallStyle.copy(fontSize = 14.sp), textAlign = TextAlign.Center)
    }
}

/**
 * Selección arrastrando. Sin nada elegido, empieza con una pulsación larga. Con algo ya elegido,
 * basta con arrastrar de lado (o dejar el dedo quieto un momento): todo lo que queda entre la
 * primera foto y el dedo se marca. Arrastrar en vertical sigue desplazando la cuadrícula.
 * [idAt] da la foto de cada posición (null en los títulos). [onHold] avisa de qué foto tiene el
 * dedo encima mientras no se mueve, y de null al moverlo o soltarlo.
 *
 * Con un vídeo y nada elegido, mantener el dedo solo enseña la vista previa: el vídeo no se
 * marca hasta que el dedo se mueve. [isVideo] dice cuáles son vídeos.
 */
fun Modifier.dragSelect(
    grid: LazyGridState,
    idAt: (index: Int) -> Long?,
    selection: () -> Set<Long>,
    onChange: (Set<Long>) -> Unit,
    onHold: (Long?) -> Unit = {},
    isVideo: (Long) -> Boolean = { false },
): Modifier = pointerInput(grid) {
    var start = -1
    var base = emptySet<Long>()

    fun indexAt(point: Offset): Int? {
        val info = grid.layoutInfo
        // Las posiciones de los elementos no cuentan el margen superior del contenido.
        val y = point.y + info.viewportStartOffset
        return info.visibleItemsInfo.firstOrNull { item ->
            point.x >= item.offset.x && point.x < item.offset.x + item.size.width &&
                y >= item.offset.y && y < item.offset.y + item.size.height
        }?.index
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var held = true
        if (selection().isEmpty()) {
            awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        } else {
            val deadline = System.currentTimeMillis() + viewConfiguration.longPressTimeoutMillis
            while (true) {
                val left = deadline - System.currentTimeMillis()
                // Sin moverse hasta el plazo: cuenta como pulsación larga.
                val event = if (left <= 0) null else withTimeoutOrNull(left) { awaitPointerEvent() }
                if (event == null) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                val moved = change.position - down.position
                if (abs(moved.y) > slop && abs(moved.y) >= abs(moved.x)) return@awaitEachGesture
                if (abs(moved.x) > slop) {
                    held = false
                    break
                }
            }
        }
        val first = indexAt(down.position)
        val id = first?.let(idAt) ?: return@awaitEachGesture
        if (held && selection().isEmpty() && isVideo(id)) {
            // Solo vista previa mientras el dedo esté quieto. Soltar no abre ni marca nada.
            onHold(id)
            var moved = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                if ((change.position - down.position).getDistance() > slop * 2) {
                    moved = true
                    break
                }
            }
            onHold(null)
            if (!moved) return@awaitEachGesture
            held = false
        }
        start = first
        base = selection()
        onChange(base + id)
        if (held) onHold(id)
        // Desde aquí el gesto es de la selección: se atiende antes que el desplazamiento de la
        // cuadrícula y que el toque de la foto, y se les quita para que no actúen también.
        try {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                if ((change.position - down.position).getDistance() > slop * 2) onHold(null)
                val index = indexAt(change.position) ?: continue
                onChange(base + (min(start, index)..max(start, index)).mapNotNull(idAt))
            }
        } finally {
            onHold(null)
        }
    }
}

/**
 * Un vídeo que se reproduce en silencio dentro de su miniatura mientras se mantiene el dedo
 * encima. El reproductor vive solo lo que dura la vista previa.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPreview(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

/**
 * Pellizcar con dos dedos sobre una cuadrícula: [onPinch] recibe true al acercar (menos columnas)
 * y false al alejar. Un pellizco cuenta una sola vez aunque se siga moviendo.
 */
fun Modifier.pinchColumns(onPinch: (closer: Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        var zoom = 1f
        var fired = false
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                if (!fired && zoom > 1.25f) {
                    fired = true
                    onPinch(true)
                } else if (!fired && zoom < 0.8f) {
                    fired = true
                    onPinch(false)
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** Icono de cámara de fotos (el de Material). */
val CameraIcon: ImageVector = ImageVector.Builder("Camara", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(
        pathData = addPathNodes(
            "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9z" +
                "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9.2a2.8,2.8 0,1 0,0 5.6a2.8,2.8 0,1 0,0 -5.6z",
        ),
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.EvenOdd,
    )
}.build()

/** Tirador lateral para recorrer bibliotecas largas; al arrastrarlo enseña la fecha por la que va. */
@Composable
fun FastScroller(grid: LazyGridState, count: Int, label: (index: Int) -> String, modifier: Modifier = Modifier) {
    if (count < 40) return
    val scope = rememberCoroutineScope()
    val thumbPx = with(LocalDensity.current) { 52.dp.toPx() }
    var track by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val scrollFraction by remember(count) {
        derivedStateOf {
            val last = (count - grid.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
            (grid.firstVisibleItemIndex.toFloat() / last).coerceIn(0f, 1f)
        }
    }
    val shown = dragging || grid.isScrollInProgress
    val alphaState = animateFloatAsState(
        if (shown) 1f else 0f,
        tween(if (shown) 120 else 600, delayMillis = if (shown) 0 else 1200),
        label = "tirador",
    )
    val alpha by alphaState

    Box(modifier.onSizeChanged { track = it.height }) {
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, ((track - thumbPx) * (if (dragging) dragFraction else scrollFraction)).roundToInt()) }
                .graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dragging) {
                Text(
                    label((dragFraction * (count - 1)).roundToInt()),
                    style = LabelStyle,
                    color = Lumi.OnAccent,
                    modifier = Modifier.clip(CircleShape).background(Lumi.Accent).padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier
                    .then(
                        if (alpha < 0.05f) Modifier else Modifier.pointerInput(count, track) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragFraction = scrollFraction
                                    dragging = true
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, dy ->
                                change.consume()
                                dragFraction = (dragFraction + dy / (track - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                scope.launch { grid.scrollToItem((dragFraction * (count - 1)).roundToInt()) }
                            }
                        },
                    )
                    .padding(start = 14.dp, end = 4.dp)
                    .size(width = 8.dp, height = 52.dp)
                    .clip(CircleShape)
                    .background(if (dragging) Lumi.Accent else Lumi.Muted),
            )
        }
    }
}

/** Barra que sustituye a la de pestañas mientras hay fotos elegidas: cuántas son, cuánto pesan y qué se puede hacer con ellas. */
@Composable
fun SelectionBar(chosen: List<MediaItem>, state: UiState, vm: LumiViewModel, actions: Actions, onClear: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    // true = mover, false = copiar, null = cerrado
    var moving by remember { mutableStateOf<Boolean?>(null) }
    val haptic = LocalHapticFeedback.current
    val weight = remember(chosen) { chosen.sumOf { it.size } }
    val context = LocalContext.current
    val card = remember { cardVolume(context) }

    val targets = remember(state.albums) { state.albums.filter { isWritableAlbumPath(it.path) && !it.locked }.take(12) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Con otra app al lado, lo elegido se puede soltar en ella.
        if (canDragOut()) Box(Modifier.align(Alignment.CenterHorizontally)) { DragOutHandle(chosen) }
        // Álbumes a un toque: se elige uno y lo marcado se mueve allí, sin abrir ningún menú.
        if (targets.isNotEmpty()) {
            Row(
                Modifier.clip(CircleShape).background(Lumi.Surface.copy(alpha = 0.96f)).horizontalScroll(rememberScrollState()).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Mover a", style = SmallStyle, modifier = Modifier.padding(start = 8.dp, end = 2.dp))
                targets.forEach { album ->
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Lumi.Bg)
                            .clickable {
                                actions.write(chosen) { vm.moveTo(chosen, album.path, album.name) }
                                onClear()
                            }
                            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaThumb(album.cover, 160, Modifier.size(28.dp).clip(CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(album.name, style = LabelStyle, maxLines = 1)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Lumi.Surface).padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BarIcon(Icons.Filled.Close, "Cancelar", onClear)
                Text(
                    countText(chosen.size, "seleccionada", "seleccionadas"),
                    style = LabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Text(formatSize(weight), style = SmallStyle.copy(fontSize = 13.sp), modifier = Modifier.padding(end = 14.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                SelectAction(Icons.Filled.Share, "Enviar", Modifier.weight(1f)) { actions.share(chosen) }
                SelectAction(AlbumIcon, "Álbum", Modifier.weight(1f)) { moving = true }
                SelectAction(Icons.Filled.Lock, "Privada", Modifier.weight(1f)) {
                    vm.hideInVault(chosen) { stored -> actions.deleteForever(stored) }
                    onClear()
                }
                // Borrar va en otro color para que no se toque por error.
                SelectAction(Icons.Filled.Delete, "Borrar", Modifier.weight(1f), Lumi.Danger) { actions.trash(chosen, onClear) }
                Box(Modifier.weight(1f)) {
                    SelectAction(Icons.Filled.MoreVert, "Más", Modifier.fillMaxWidth()) { menu = true }
                    DropdownMenu(menu, { menu = false }, containerColor = Lumi.Surface) {
                        DropdownMenuItem({ Text(if (chosen.all { it.id in state.favorites }) "Quitar de favoritas" else "Marcar como favoritas") }, {
                            menu = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.toggleFavorite(chosen.map { it.id })
                            onClear()
                        })
                        if (card != null && chosen.any { !it.onCard }) {
                            DropdownMenuItem({ Text("Mover a la tarjeta SD") }, {
                                menu = false
                                vm.moveToCard(chosen, card) { copied -> actions.deleteForever(copied) }
                                onClear()
                            })
                        }
                        DropdownMenuItem({ Text("Copiar") }, {
                            menu = false
                            vm.clip = com.lumi.galeria.Clip(chosen, move = false)
                            vm.say("Listas para pegar. Abre un álbum y pulsa «Pegar».")
                            onClear()
                        })
                        DropdownMenuItem({ Text("Cortar") }, {
                            menu = false
                            vm.clip = com.lumi.galeria.Clip(chosen, move = true)
                            vm.say("Listas para pegar. Abre un álbum y pulsa «Pegar».")
                            onClear()
                        })
                        DropdownMenuItem({ Text("Copiar a un álbum") }, { menu = false; moving = false })
                        DropdownMenuItem({ Text("Comparar dos fotos") }, {
                            menu = false
                            val photos = chosen.filter { !it.isVideo }
                            if (photos.size == 2) vm.open(Screen.Compare(photos[0].id, photos[1].id))
                            else vm.say("Elige exactamente dos fotos para compararlas")
                            onClear()
                        })
                        DropdownMenuItem({ Text("Juntar en un PDF") }, {
                            menu = false
                            val photos = chosen.filter { !it.isVideo }
                            if (photos.isEmpty()) vm.say("Elige al menos una foto para el PDF")
                            else if (photos.size > 40) vm.say("Un PDF admite hasta 40 fotos")
                            // Las páginas van de la más antigua a la más reciente, como se hicieron.
                            else vm.open(Screen.Pdf(photos.sortedBy { it.date }.map { it.id }))
                            onClear()
                        })
                        DropdownMenuItem({ Text("Exportar: tamaño, formato, marca de agua…") }, {
                            menu = false
                            val photos = chosen.filter { !it.isVideo }
                            if (photos.isEmpty()) vm.say("Elige alguna foto para exportar") else vm.open(Screen.Export(photos.map { it.id }))
                            onClear()
                        })
                        DropdownMenuItem({ Text("Vídeo con música") }, {
                            menu = false
                            val photos = chosen.filter { !it.isVideo }
                            if (photos.size < 2) vm.say("Elige al menos dos fotos para el vídeo")
                            else vm.open(Screen.MemoryVideo("", photos.map { it.id }))
                            onClear()
                        })
                        DropdownMenuItem({ Text("Animar (GIF)") }, {
                            menu = false
                            val photos = chosen.filter { !it.isVideo }
                            if (photos.size in 2..40) vm.open(Screen.Animate(photos.map { it.id }))
                            else vm.say("Elige entre 2 y 40 fotos para animarlas")
                            onClear()
                        })
                        DropdownMenuItem({ Text("Hacer un collage") }, {
                            menu = false
                            val photos = chosen.filter { !it.isVideo }
                            if (photos.size in 2..6) vm.open(Screen.Collage(photos.map { it.id }))
                            else vm.say("Elige entre 2 y 6 fotos para el collage")
                            onClear()
                        })
                    }
                }
            }
        }
    }

    moving?.let { move ->
        AlbumPickerSheet(
            title = if (move) "Mover a…" else "Copiar a…",
            albums = state.albums,
            onPick = { path, name ->
                moving = null
                if (move) actions.write(chosen) { vm.moveTo(chosen, path, name) } else vm.copyTo(chosen, path, name)
                onClear()
            },
            onDismiss = { moving = null },
        )
    }
}

/** Una acción de la barra de selección: icono con su nombre debajo. */
@Composable
private fun SelectAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = Lumi.Ink, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Text(label, style = SmallStyle, color = tint, maxLines = 1)
    }
}

/** Icono de carpeta (el de Material). */
val AlbumIcon: ImageVector = ImageVector.Builder("Album", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(
        pathData = addPathNodes("M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z"),
        fill = SolidColor(Color.White),
    )
}.build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerSheet(title: String, albums: List<Album>, onPick: (path: String, name: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lumi.Surface) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = HeadingStyle.copy(fontSize = 20.sp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    placeholder = { Text("Álbum nuevo") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f),
                )
                PillButton("Crear", { onPick("Pictures/$clean/", clean) }, enabled = clean.isNotEmpty())
            }
            LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(albums.filter { isWritableAlbumPath(it.path) }, key = { it.bucketId }) { album ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(album.path, album.name) }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaThumb(album.cover, 160, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(album.name, style = LabelStyle.copy(fontSize = 15.sp))
                            Text(countText(album.count, "elemento", "elementos"), style = SmallStyle)
                        }
                    }
                }
            }
        }
    }
}

/** Icono de tarjeta de memoria (el de Material): marca los archivos que están en la tarjeta SD. */
val SdCardIcon: ImageVector = ImageVector.Builder("TarjetaSD", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(
        pathData = addPathNodes(
            "M18,2h-8L4.02,8 4,20c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z" +
                "M12,8h-2V4h2v4zM15,8h-2V4h2v4zM18,8h-2V4h2v4z",
        ),
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.EvenOdd,
    )
}.build()

/** El juego de iconos básico no trae el de pausa. */
@Composable
fun PauseGlyph(color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        repeat(2) { Box(Modifier.width(4.dp).height(14.dp).clip(RoundedCornerShape(1.dp)).background(color)) }
    }
}
