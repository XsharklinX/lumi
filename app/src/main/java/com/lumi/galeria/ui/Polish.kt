package com.lumi.galeria.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * El aviso con «Deshacer». Entra con un muelle y un anillo se va vaciando durante los siete segundos que
 * dura la posibilidad de deshacer: se ve cuánto queda sin leer ni contar. Zona táctil de al menos 48 dp.
 */
@Composable
fun UndoPill(text: String, key: Any, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val enter = remember(key) { Animatable(0f) }
    val left = remember(key) { Animatable(1f) }
    val still = reducedMotion()
    LaunchedEffect(key) { enter.animateTo(1f, if (still) tween(1) else spring(dampingRatio = 0.6f, stiffness = 420f)) }
    LaunchedEffect(key) { left.animateTo(0f, tween(7000, easing = LinearEasing)) }
    val accent = Lumi.Accent
    val track = Lumi.Bg.copy(alpha = 0.25f)
    Row(
        modifier
            .graphicsLayer {
                translationY = (1f - enter.value) * 60.dp.toPx()
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .background(Lumi.Ink)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp).semantics { contentDescription = "Quedan unos segundos para deshacer" }) {
            val w = 3.dp.toPx()
            val box = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), box, style = Stroke(w))
            drawArc(accent, -90f, 360f * left.value, false, Offset(w / 2, w / 2), box, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(text, style = LabelStyle, color = Lumi.Bg, modifier = Modifier.padding(start = 10.dp))
        Box(
            Modifier.defaultMinSize(minHeight = 48.dp).clip(CircleShape).clickable(onClick = onUndo).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Deshacer", style = LabelStyle, color = Lumi.Accent) }
    }
}

/** Cuándo se abrió la pantalla actual: las miniaturas que aparecen justo después entran en cascada. */
object Cascade {
    @Volatile var start = 0L
}

/**
 * Entrada en olas cortas: cada miniatura sale de borrosa y algo pequeña a nítida, con un retraso que depende
 * de su foto. Solo en el primer instante de una pantalla, para que al desplazarse no se repita; se apaga con
 * «reducir animaciones».
 */
@Composable
fun Modifier.cascadeIn(seed: Long): Modifier {
    val still = reducedMotion()
    val play = remember { !still && System.currentTimeMillis() - Cascade.start < 900 }
    if (!play) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((seed % 8) * 45)
        p.animateTo(1f, tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val v = p.value
    return this
        .graphicsLayer {
            alpha = v
            val s = 0.9f + 0.1f * v
            scaleX = s
            scaleY = s
        }
        .then(if (v < 1f && android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(((1f - v) * 10f).dp) else Modifier)
}
