package com.lumi.galeria.ui

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.data.Album
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.formatSize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Si en el teléfono están quitadas las animaciones: entonces nada se mueve solo. */
@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
}

// ---------- Celebración al liberar espacio ----------

/**
 * Confeti con los colores de Lumi y el espacio liberado subiendo hasta su cifra. Breve: se va
 * sola a los pocos segundos, o al tocarla.
 */
@Composable
fun Celebration(bytes: Long, onDone: () -> Unit) {
    val still = reducedMotion()
    val fall = remember { Animatable(0f) }
    val view = LocalView.current
    LaunchedEffect(bytes) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (!still) fall.animateTo(1f, tween(2600, easing = LinearEasing))
        delay(if (still) 2200 else 200)
        onDone()
    }
    val pieces = remember(bytes) {
        val random = java.util.Random(bytes)
        List(46) {
            floatArrayOf(random.nextFloat(), random.nextFloat() * 0.5f, 0.6f + random.nextFloat() * 0.8f, random.nextFloat() * 360f, random.nextInt(5).toFloat())
        }
    }
    val colors = listOf(Color(0xFFB9A8FF), Color(0xFF7FD8A6), Color(0xFFFFD43B), Color(0xFFFF8A80), Color(0xFF8EC5FF))
    val shown = rollingNumber(bytes.toDouble(), 1200)
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = onDone), contentAlignment = Alignment.Center) {
        if (!still) {
            Canvas(Modifier.fillMaxSize()) {
                val t = fall.value
                pieces.forEach { p ->
                    val x = p[0] * size.width + sin((t * 6f + p[3]).toDouble()).toFloat() * 24f
                    val y = (p[1] - 0.55f + t * p[2] * 1.4f) * size.height
                    if (y < -20f || y > size.height + 20f) return@forEach
                    rotate(p[3] + t * 720f * p[2], Offset(x, y)) {
                        drawRect(colors[p[4].toInt()], topLeft = Offset(x - 5f, y - 9f), size = Size(10f, 18f), alpha = (1f - t * 0.6f).coerceIn(0f, 1f))
                    }
                }
            }
        }
        Column(
            Modifier.shadow(16.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(Lumi.Surface).padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(formatSize(shown.toLong()), style = TitleStyle.copy(fontSize = 40.sp))
            Text("¡Has liberado espacio!", style = LabelStyle.copy(fontSize = 15.sp))
        }
    }
}

// ---------- Arrastrar fotos a un álbum ----------

/** Un arrastre en curso: qué fotos, dónde va el dedo y sobre qué álbum está. */
class AlbumDrag(val photos: List<MediaItem>, val albums: List<Album>, val onDrop: (Album) -> Unit) {
    var at by mutableStateOf(Offset.Zero)
    var over by mutableStateOf<Album?>(null)
    val places = mutableStateMapOf<Long, Rect>()
}

/** El arrastre que se está haciendo; lo pinta la pantalla principal por encima de todo. */
object AlbumDragState {
    var session by mutableStateOf<AlbumDrag?>(null)
}

/**
 * La pastilla «Arrastra a un álbum» de la barra de selección: al arrastrarla, las fotos elegidas
 * siguen al dedo y aparece una bandeja de álbumes a la derecha; al soltar sobre uno, van ahí.
 */
@Composable
fun AlbumDragHandle(photos: List<MediaItem>, albums: List<Album>, onDrop: (Album) -> Unit) {
    if (albums.isEmpty()) return
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Row(
        Modifier.onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .clip(CircleShape).background(Lumi.Surface.copy(alpha = 0.96f))
            .pointerInput(photos, albums) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val session = AlbumDrag(photos, albums, onDrop)
                        session.at = origin + offset
                        AlbumDragState.session = session
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    },
                    onDragEnd = {
                        val session = AlbumDragState.session
                        AlbumDragState.session = null
                        session?.over?.let { album ->
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            session.onDrop(album)
                        }
                    },
                    onDragCancel = { AlbumDragState.session = null },
                ) { change, amount ->
                    change.consume()
                    val session = AlbumDragState.session ?: return@detectDragGestures
                    session.at += amount
                    val over = session.albums.firstOrNull { session.places[it.bucketId]?.contains(session.at) == true }
                    if (over != session.over) {
                        session.over = over
                        if (over != null) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(34.dp)) {
            photos.take(2).reversed().forEachIndexed { i, item ->
                MediaThumb(item, 160, Modifier.size(28.dp).offset(x = (6 * i).dp, y = (6 * i).dp).rotate(if (i == 0) -8f else 4f).clip(RoundedCornerShape(7.dp)).border(1.5.dp, Lumi.Surface, RoundedCornerShape(7.dp)))
            }
        }
        Text("Arrastra a un álbum", style = LabelStyle)
    }
}

/** Lo que se ve mientras se arrastra: las fotos bajo el dedo y la bandeja de álbumes a la derecha. */
@Composable
fun AlbumDragOverlay(session: AlbumDrag) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
        Column(
            Modifier.align(Alignment.CenterEnd).statusBarsPadding().width(132.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)).background(Lumi.Surface).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Soltar en…", style = SmallStyle, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            session.albums.take(8).forEach { album ->
                val on = session.over?.bucketId == album.bucketId
                val grow by animateFloatAsState(if (on) 1.06f else 1f, label = "album")
                Row(
                    Modifier.fillMaxWidth().graphicsLayer { scaleX = grow; scaleY = grow }
                        .onGloballyPositioned { session.places[album.bucketId] = it.boundsInRoot() }
                        .clip(RoundedCornerShape(14.dp)).background(if (on) Lumi.Accent else Lumi.Bg).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MediaThumb(album.cover, 160, Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)))
                    Text(album.name, style = SmallStyle.copy(fontWeight = FontWeight.Bold), color = if (on) Lumi.OnAccent else Lumi.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Las fotos, apiladas, siguiendo al dedo.
        val density = LocalDensity.current
        Box(Modifier.offset { IntOffset((session.at.x - with(density) { 40.dp.toPx() }).roundToInt(), (session.at.y - with(density) { 40.dp.toPx() }).roundToInt()) }) {
            session.photos.take(3).reversed().forEachIndexed { i, item ->
                MediaThumb(item, 256, Modifier.size(80.dp).offset(x = (5 * i).dp, y = (5 * i).dp).rotate(-6f + 5f * i).shadow(10.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)))
            }
            Text(
                "${session.photos.size}", style = LabelStyle.copy(fontWeight = FontWeight.Bold), color = Lumi.OnAccent,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-8).dp).clip(CircleShape).background(Lumi.Accent).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

// ---------- Ruedas con muescas ----------

/**
 * Un dial que se gira con el dedo: los valores en una regla, el elegido en el centro, una muesca de
 * vibración en cada paso y un tope al llegar al final.
 */
@Composable
fun DetentDial(labels: List<String>, index: Int, onIndex: (Int) -> Unit, modifier: Modifier = Modifier, accent: Color = Color(0xFFFFD43B)) {
    if (labels.isEmpty()) return
    val view = LocalView.current
    val measurer = rememberTextMeasurer()
    val stepPx = with(LocalDensity.current) { 46.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val settle by animateFloatAsState(drag, tween(120), label = "dial")
    val current = index.coerceIn(0, labels.lastIndex)
    Box(
        modifier.height(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(labels.size, current) {
                var at = current
                detectHorizontalDragGestures(onDragEnd = { drag = 0f }, onDragCancel = { drag = 0f }) { change, dx ->
                    change.consume()
                    drag += dx
                    while (drag <= -stepPx) {
                        if (at < labels.lastIndex) { at++; onIndex(at); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                        else view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        drag += stepPx
                    }
                    while (drag >= stepPx) {
                        if (at > 0) { at--; onIndex(at); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                        else view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        drag -= stepPx
                    }
                }
            },
    ) {
        val style = SmallStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        val on = style.copy(fontSize = 14.sp, color = accent)
        Canvas(Modifier.fillMaxSize()) {
            val center = size.width / 2 + settle
            labels.forEachIndexed { i, label ->
                val x = center + (i - current) * stepPx
                if (x < -40f || x > size.width + 40f) return@forEachIndexed
                val chosen = i == current
                val layout = measurer.measure(label, if (chosen) on else style)
                val fade = (1f - abs(x - size.width / 2) / (size.width / 2)).coerceIn(0.25f, 1f)
                drawText(layout, topLeft = Offset(x - layout.size.width / 2, size.height / 2 - layout.size.height / 2), alpha = fade)
                drawLine(Color.White.copy(alpha = 0.35f * fade), Offset(x + stepPx / 2, size.height * 0.35f), Offset(x + stepPx / 2, size.height * 0.65f), 2f)
            }
            drawLine(accent, Offset(size.width / 2, 3f), Offset(size.width / 2, 8f), 4f)
        }
    }
}

// ---------- Diafragma de la cámara ----------

/** Láminas de diafragma que se cierran y vuelven a abrirse: [closed] va de 0 (abierto) a 1 (cerrado). */
@Composable
fun IrisOverlay(closed: Float, modifier: Modifier = Modifier) {
    if (closed <= 0.001f) return
    Canvas(modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2)
        val far = kotlin.math.hypot(size.width, size.height) / 2
        val r = far * (1f - closed)
        val turn = Math.toRadians((closed * 40f).toDouble())
        val hole = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            for (k in 0 until 6) {
                val a = turn + k * Math.PI / 3
                val p = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
                if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(hole, Color(0xFF0B0B0F))
        // Los bordes de las láminas: una línea de cada vértice hacia fuera, girada.
        for (k in 0 until 6) {
            val a = turn + k * Math.PI / 3
            val p = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
            val q = Offset(c.x + (cos(a + 0.9) * far).toFloat(), c.y + (sin(a + 0.9) * far).toFloat())
            drawLine(Color.White.copy(alpha = 0.12f), p, q, 2f)
        }
        drawCircle(Color.White.copy(alpha = 0.08f), r.coerceAtLeast(1f), c, style = Stroke(2f))
    }
}
