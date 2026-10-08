package com.lumi.galeria.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Los dos colores de cada dibujo, para que cada página tenga el suyo. */
class ArtColors(val a: Color, val b: Color)

val ART_PALETTE = listOf(
    ArtColors(Color(0xFF7C5CFF), Color(0xFFB9A8FF)),
    ArtColors(Color(0xFF3D7BF5), Color(0xFF8FC1FF)),
    ArtColors(Color(0xFFE5709B), Color(0xFFFFB3C8)),
    ArtColors(Color(0xFFE59A3B), Color(0xFFFFD08A)),
    ArtColors(Color(0xFF2FA37A), Color(0xFF8FE0BF)),
    ArtColors(Color(0xFF5B6B8C), Color(0xFFB8C4DE)),
    ArtColors(Color(0xFF7C5CFF), Color(0xFFFFD43B)),
    ArtColors(Color(0xFF1C7A49), Color(0xFF7FD8A6)),
    ArtColors(Color(0xFF3BA3B5), Color(0xFF9EE3EE)),
    ArtColors(Color(0xFFB0832F), Color(0xFFFFE08A)),
)

/** Una foto dibujada: sol y loma, como el logo. Nada de fotos de verdad. */
private fun DrawScope.card(center: Offset, w: Float, h: Float, degrees: Float, colors: ArtColors, light: Float) {
    rotate(degrees, center) {
        val topLeft = Offset(center.x - w / 2, center.y - h / 2)
        val radius = CornerRadius(w * 0.14f)
        drawRoundRect(Color.Black.copy(alpha = 0.25f), topLeft + Offset(0f, h * 0.04f), Size(w, h), radius)
        drawRoundRect(Brush.linearGradient(listOf(colors.a, colors.b), topLeft, topLeft + Offset(w, h)), topLeft, Size(w, h), radius)
        // Un brillo por arriba, como una foto con luz.
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f * light), Color.Transparent), topLeft.y, topLeft.y + h * 0.5f), topLeft, Size(w, h), radius)
        drawCircle(Color.White.copy(alpha = 0.9f), w * 0.11f, Offset(topLeft.x + w * 0.66f, topLeft.y + h * 0.32f))
        val hill = Path().apply {
            moveTo(topLeft.x, topLeft.y + h * 0.78f)
            cubicTo(topLeft.x + w * 0.2f, topLeft.y + h * 0.52f, topLeft.x + w * 0.42f, topLeft.y + h * 0.54f, topLeft.x + w * 0.56f, topLeft.y + h * 0.7f)
            cubicTo(topLeft.x + w * 0.68f, topLeft.y + h * 0.8f, topLeft.x + w * 0.82f, topLeft.y + h * 0.6f, topLeft.x + w, topLeft.y + h * 0.56f)
            lineTo(topLeft.x + w, topLeft.y + h)
            lineTo(topLeft.x, topLeft.y + h)
            close()
        }
        drawPath(hill, Color.White.copy(alpha = 0.35f))
    }
}

/**
 * Ilustración de Lumi: tres fotos dibujadas en abanico que flotan sobre un fondo de luz, y en medio
 * el símbolo de lo que se cuenta. Igual para todo el mundo: no enseña ninguna foto del teléfono.
 */
@Composable
fun LumiArt(icon: ImageVector, colors: ArtColors, modifier: Modifier = Modifier) {
    val bg = Lumi.Bg
    val motion = rememberInfiniteTransition(label = "dibujo")
    val float by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse), label = "flota")
    val drift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "luz")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(colors.a.copy(alpha = 0.32f), bg), 0f, h))
            // Dos manchas de luz que se mueven despacio.
            val r1 = w * 0.55f
            drawCircle(Brush.radialGradient(listOf(colors.b.copy(alpha = 0.45f), Color.Transparent), Offset(w * (0.15f + 0.1f * drift), h * 0.25f), r1), r1, Offset(w * (0.15f + 0.1f * drift), h * 0.25f))
            val r2 = w * 0.45f
            drawCircle(Brush.radialGradient(listOf(colors.a.copy(alpha = 0.4f), Color.Transparent), Offset(w * (0.88f - 0.1f * drift), h * 0.7f), r2), r2, Offset(w * (0.88f - 0.1f * drift), h * 0.7f))
            // Puntos de brillo.
            listOf(0.12f to 0.62f, 0.86f to 0.2f, 0.78f to 0.86f, 0.24f to 0.12f).forEachIndexed { i, (x, y) ->
                drawCircle(Color.White.copy(alpha = 0.25f + 0.25f * ((drift + i * 0.3f) % 1f)), w * 0.008f + 1.5f, Offset(w * x, h * y))
            }
            val cw = minOf(w * 0.3f, h * 0.42f)
            val ch = cw * 1.25f
            val cx = w / 2
            val cy = h * 0.52f
            card(Offset(cx - cw * 0.72f, cy + ch * 0.04f), cw, ch, -13f, ArtColors(colors.a, colors.a.copy(alpha = 0.7f)), 0.6f)
            card(Offset(cx + cw * 0.72f, cy + ch * 0.02f), cw, ch, 11f, ArtColors(colors.b.copy(alpha = 0.85f), colors.a), 0.6f)
            translate(0f, -h * 0.025f * float) {
                card(Offset(cx, cy - ch * 0.04f), cw * 1.06f, ch * 1.06f, 0f, colors, 1f)
            }
        }
        // El símbolo de la página, en una chapa encima de la foto del centro.
        Box(
            Modifier.size(64.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Lumi.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(30.dp), tint = colors.a)
        }
    }
}
