package com.lumi.galeria.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Hundirse al pulsar: el elemento se encoge un poco mientras el dedo está encima y vuelve con un
 * rebote corto. Se usa con el mismo [source] que el clickable.
 */
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.95f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> launch { scale.animateTo(pressed, tween(90)) }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f)) }
            }
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Una vibración muy corta y discreta para lo importante (favorita, borrar, guardar). */
@Composable
fun rememberTick(): (Int) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind -> view.performHapticFeedback(kind) } }
}

/** «2×» del vídeo: tres flechas dibujadas que se encienden una tras otra mientras se mantiene el dedo. */
@Composable
fun FastPill(modifier: Modifier = Modifier) {
    val beat = rememberInfiniteTransition(label = "rapido")
    val phase by beat.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "flechas")
    Row(
        modifier.clip(CircleShape).background(Color(0xCC0E0E13)).padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("2×", style = HeadingStyle.copy(fontSize = 16.sp), color = Color.White)
        Canvas(Modifier.size(width = 30.dp, height = 14.dp)) {
            val w = size.width / 3
            for (i in 0 until 3) {
                // Cada flecha se ilumina cuando le toca y se apaga despacio.
                val d = ((phase - i + 3f) % 3f)
                val alpha = (1f - d / 1.6f).coerceIn(0.25f, 1f)
                val path = Path().apply {
                    moveTo(i * w + w * 0.15f, 0f)
                    lineTo(i * w + w * 0.85f, size.height / 2)
                    lineTo(i * w + w * 0.15f, size.height)
                    close()
                }
                drawPath(path, Color.White.copy(alpha = alpha))
            }
        }
    }
}

/**
 * La onda al saltar en un vídeo: sale del lado tocado, con flechas que corren y el salto acumulado
 * («+30 s» tras tocar tres veces).
 */
@Composable
fun SeekRipple(forward: Boolean, seconds: Long, modifier: Modifier = Modifier) {
    val beat = rememberInfiniteTransition(label = "salto")
    val phase by beat.animateFloat(0f, 3f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "flechas")
    val shape = if (forward) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50) else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    Box(
        modifier.fillMaxHeight().fillMaxWidth(0.42f).clip(shape).background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(width = 42.dp, height = 18.dp)) {
                val w = size.width / 3
                for (i in 0 until 3) {
                    val slot = if (forward) i else 2 - i
                    val d = ((phase - i + 3f) % 3f)
                    val alpha = (1f - d / 1.5f).coerceIn(0.25f, 1f)
                    val x0 = slot * w
                    val path = Path().apply {
                        if (forward) {
                            moveTo(x0 + w * 0.15f, 0f); lineTo(x0 + w * 0.85f, size.height / 2); lineTo(x0 + w * 0.15f, size.height)
                        } else {
                            moveTo(x0 + w * 0.85f, 0f); lineTo(x0 + w * 0.15f, size.height / 2); lineTo(x0 + w * 0.85f, size.height)
                        }
                        close()
                    }
                    drawPath(path, Color.White.copy(alpha = alpha))
                }
            }
            Text((if (forward) "+" else "−") + "$seconds s", style = HeadingStyle.copy(fontSize = 15.sp), color = Color.White, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Chispas que salen del corazón al marcar una favorita. Se dispara cambiando [key]. */
@Composable
fun HeartBurst(key: Int, color: Color, modifier: Modifier = Modifier) {
    val burst = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (key == 0) return@LaunchedEffect
        burst.snapTo(0f)
        burst.animateTo(1f, tween(520))
    }
    if (burst.value >= 1f) return
    Canvas(modifier.size(72.dp)) {
        val t = burst.value
        val center = Offset(size.width / 2, size.height / 2)
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0 - 90.0)
            val r = size.minDimension * (0.18f + 0.34f * t)
            val p = Offset(center.x + (cos(angle) * r).toFloat(), center.y + (sin(angle) * r).toFloat())
            drawCircle(if (i % 2 == 0) color else Color.White, radius = 3.2.dp.toPx() * (1f - t), center = p, alpha = 1f - t)
        }
    }
}

/** Un número que sube rodando hasta [value] la primera vez y cada vez que cambia. */
@Composable
fun rollingNumber(value: Double, millis: Int = 700): Double {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(value) { shown.animateTo(value.toFloat(), tween(millis)) }
    return shown.value.toDouble()
}

/** Huecos con un brillo que pasa, mientras la galería se lee por primera vez. */
@Composable
fun SkeletonGrid(columns: Int, modifier: Modifier = Modifier) {
    val beat = rememberInfiniteTransition(label = "brillo")
    val x by beat.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x")
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val brush = Brush.linearGradient(
            listOf(Lumi.Surface, Lumi.Line.copy(alpha = 0.9f), Lumi.Surface),
            start = Offset(width * x - width * 0.5f, 0f), end = Offset(width * x, width * 0.3f),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(9) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(columns) { Box(Modifier.weight(1f).aspectRatio(1f).background(brush)) }
                }
            }
        }
    }
}

/** Qué clase de aviso es, para su icono y su color. */
enum class ToastKind { DONE, WARNING, INFO }

/** Lo que dice un aviso: si empieza por «No …» o habla de error, es un aviso; si no, algo hecho. */
fun toastKindOf(text: String): ToastKind {
    val t = text.lowercase()
    return when {
        t.startsWith("no ") || t.startsWith("couldn") || t.startsWith("can't") || t.contains("error") || t.contains("no se pudo") -> ToastKind.WARNING
        t.startsWith("un momento") || t.startsWith("desbloquea") || t.startsWith("pon ") || t.startsWith("elige") -> ToastKind.INFO
        else -> ToastKind.DONE
    }
}

/**
 * El aviso como tarjeta: icono, mensaje y, si hay, una acción. Entra desde abajo con un rebote y se
 * puede quitar deslizándolo de lado.
 */
@Composable
fun ToastCard(message: String, icons: Triple<ImageVector, ImageVector, ImageVector>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val enter = remember(message) { Animatable(0f) }
    LaunchedEffect(message) { enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f)) }
    var dragged by remember(message) { mutableStateOf(0f) }
    val back by animateFloatAsState(dragged, spring(), label = "arrastre")
    val kind = toastKindOf(message)
    val (icon, tint) = when (kind) {
        ToastKind.DONE -> icons.first to Color(0xFF2FA37A)
        ToastKind.WARNING -> icons.second to Color(0xFFE5A23B)
        ToastKind.INFO -> icons.third to Lumi.Accent
    }
    Row(
        modifier
            .graphicsLayer {
                translationY = (1f - enter.value) * 80.dp.toPx()
                alpha = enter.value.coerceIn(0f, 1f) * (1f - (abs(back) / 600f)).coerceIn(0f, 1f)
                translationX = back
            }
            .pointerInput(message) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (abs(dragged) > 160f) onDismiss() else dragged = 0f },
                ) { change, dx ->
                    change.consume()
                    dragged += dx
                }
            }
            .clip(RoundedCornerShape(18.dp)).background(Lumi.Surface)
            .padding(start = 12.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(16.dp), tint = Color.White)
        }
        Text(message, style = LabelStyle.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Dónde tocó el dedo por última vez (en píxeles de la ventana), para que la pantalla nueva crezca desde ahí. */
object TapOrigin {
    @Volatile var x = 0f
    @Volatile var y = 0f
    @Volatile var at = 0L

    /** El último toque, si fue hace muy poco (es el que abrió la pantalla). */
    fun recent(): Offset? = if (System.currentTimeMillis() - at < 700) Offset(x, y) else null
}

/** La barra de abajo se retira al bajar por una lista y vuelve al subir. */
object DockState {
    var hidden by mutableStateOf(false)
    /** La última pestaña en la que estuvo la pastilla, para deslizarla desde ahí. */
    var lastTab: Any? = null
}

