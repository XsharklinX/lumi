package com.lumi.galeria.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.data.Frame
import com.lumi.galeria.data.FrameKind
import com.lumi.galeria.data.P
import com.lumi.galeria.data.Retouch
import com.lumi.galeria.data.Stroke
import com.lumi.galeria.data.Zone
import com.lumi.galeria.data.ZoneKind
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PaintMode { ERASE, GRADIENT, CIRCLE, BRUSH }

/**
 * La foto entera (sin girar ni recortar) para pintar encima: trazos para borrar o para un pincel de zona,
 * o una línea que marca el degradado o el círculo. Todo se guarda en proporciones de la foto (0 a 1).
 */
@Composable
fun PaintStage(
    image: Bitmap,
    mode: PaintMode,
    radius: Float,
    shown: List<Stroke>,
    zone: Zone?,
    onStroke: (Stroke) -> Unit,
    onLine: (P, P) -> Unit,
) {
    var view by remember { mutableStateOf(IntSize.Zero) }
    var live by remember { mutableStateOf<List<P>>(emptyList()) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val img = remember(image) { image.asImageBitmap() }
    val line = mode == PaintMode.GRADIENT || mode == PaintMode.CIRCLE

    fun fit(): Rect {
        val k = min(view.width / image.width.toFloat(), view.height / image.height.toFloat())
        val w = image.width * k
        val h = image.height * k
        return Rect(Offset((view.width - w) / 2, (view.height - h) / 2), Size(w, h))
    }

    fun norm(o: Offset): P {
        val f = fit()
        return P(((o.x - f.left) / f.width).coerceIn(0f, 1f), ((o.y - f.top) / f.height).coerceIn(0f, 1f))
    }

    val accent = Lumi.Accent
    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { view = it }
            .pointerInput(mode, radius) {
                detectTapGestures(onTap = { o -> if (!line) onStroke(Stroke(listOf(norm(o)), radius)) })
            }
            .pointerInput(mode, radius) {
                var start = P(0f, 0f)
                detectDragGestures(
                    onDragStart = { o ->
                        val p = norm(o)
                        finger = o
                        if (line) { start = p; onLine(p, p) } else live = listOf(p)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val p = norm(change.position)
                        finger = change.position
                        if (line) onLine(start, p) else live = live + p
                    },
                    onDragEnd = {
                        if (!line && live.isNotEmpty()) onStroke(Stroke(live, radius))
                        live = emptyList()
                        finger = null
                    },
                    onDragCancel = { live = emptyList(); finger = null },
                )
            },
    ) {
        val f = fit()
        drawImage(
            img, dstOffset = IntOffset(f.left.roundToInt(), f.top.roundToInt()), dstSize = IntSize(f.width.roundToInt(), f.height.roundToInt()),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        )
        val long = max(f.width, f.height)
        fun path(points: List<P>) = Path().apply {
            points.forEachIndexed { i, p ->
                val x = f.left + p.x * f.width
                val y = f.top + p.y * f.height
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            if (points.size == 1) lineTo(f.left + points[0].x * f.width + 0.1f, f.top + points[0].y * f.height)
        }
        // Lo que ya está pintado en la zona con pincel.
        shown.forEach { s ->
            drawPath(path(s.points), accent.copy(alpha = 0.38f), style = DrawStroke(s.radius * long * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (live.isNotEmpty()) {
            val tint = if (mode == PaintMode.ERASE) Color(0xFFFF4D4D) else accent
            drawPath(path(live), tint.copy(alpha = 0.5f), style = DrawStroke(radius * long * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (!line) finger?.let { drawCircle(Color.White, radius * long, it, style = DrawStroke(1.5.dp.toPx())) }
        if (zone != null && line) {
            val a = Offset(f.left + zone.a.x * f.width, f.top + zone.a.y * f.height)
            val b = Offset(f.left + zone.b.x * f.width, f.top + zone.b.y * f.height)
            if (mode == PaintMode.GRADIENT) {
                drawLine(Color.White, a, b, 2.dp.toPx())
                // Dos rayas perpendiculares: dónde empieza y dónde acaba el efecto.
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = hypot(dx, dy).coerceAtLeast(1f)
                val nx = -dy / len * long
                val ny = dx / len * long
                drawLine(accent, Offset(a.x - nx, a.y - ny), Offset(a.x + nx, a.y + ny), 2.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.6f), Offset(b.x - nx, b.y - ny), Offset(b.x + nx, b.y + ny), 1.5.dp.toPx())
            } else {
                drawCircle(Color.White, hypot(b.x - a.x, b.y - a.y), a, style = DrawStroke(2.dp.toPx()))
                drawCircle(accent, hypot(b.x - a.x, b.y - a.y) * 0.45f, a, style = DrawStroke(1.5.dp.toPx()))
            }
            drawCircle(accent, 9.dp.toPx(), a)
            drawCircle(Color.White, 7.dp.toPx(), b)
        }
    }
}

/** Vista del marco puesto, mientras se elige. */
@Composable
fun FrameStage(framed: Bitmap?) {
    if (framed == null) {
        Text("Poniendo el marco…", style = HeadingStyle)
        return
    }
    val img = remember(framed) { framed.asImageBitmap() }
    Canvas(Modifier.fillMaxSize().padding(16.dp)) {
        val k = min(size.width / framed.width, size.height / framed.height)
        val w = (framed.width * k).roundToInt()
        val h = (framed.height * k).roundToInt()
        drawImage(
            img, dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()), dstSize = IntSize(w, h),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        )
    }
}

@Composable
fun ErasePanel(r: Retouch, brush: Float, onBrush: (Float) -> Unit, onChange: (Retouch) -> Unit) {
    Text("Pinta sobre lo que quieras quitar: una mota, un cable, un papel. Rellena con lo que hay alrededor, así que va mejor con cosas pequeñas.", style = SmallStyle)
    PhotoTuner("Pincel", brush * 100, 1f..8f, "%.0f") { onBrush(it / 100) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoChip(if (r.erase.isEmpty()) "Nada pintado" else "Deshacer el último trazo", false) { if (r.erase.isNotEmpty()) onChange(r.copy(erase = r.erase.dropLast(1))) }
        if (r.erase.isNotEmpty()) PhotoChip("Quitar todo lo pintado", false) { onChange(r.copy(erase = emptyList())) }
    }
}

@Composable
fun ZonesPanel(r: Retouch, selected: Int, onSelect: (Int) -> Unit, brush: Float, onBrush: (Float) -> Unit, onChange: (Retouch) -> Unit) {
    val zone = r.zones.getOrNull(selected)
    fun setZone(next: Zone) = onChange(r.copy(zones = r.zones.mapIndexed { i, z -> if (i == selected) next else z }))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZoneKind.entries.forEach { kind ->
            PhotoChip("+ ${kind.label}", false) {
                val new = when (kind) {
                    ZoneKind.GRADIENT -> Zone(kind, P(0.5f, 0.05f), P(0.5f, 0.45f))
                    ZoneKind.CIRCLE -> Zone(kind, P(0.5f, 0.5f), P(0.78f, 0.5f))
                    ZoneKind.BRUSH -> Zone(kind)
                }
                onChange(r.copy(zones = r.zones + new))
                onSelect(r.zones.size)
            }
        }
        r.zones.forEachIndexed { i, z -> PhotoChip("${i + 1} · ${z.kind.label}", i == selected) { onSelect(i) } }
    }
    if (zone == null) {
        Text("Añade una zona: un degradado (para un cielo), un círculo (para una cara) o un pincel. Luego arrastra sobre la foto y ajusta luz, contraste, color y calidez solo ahí.", style = SmallStyle)
        return
    }
    Text(
        when (zone.kind) {
            ZoneKind.GRADIENT -> "Arrastra sobre la foto: el efecto es total en el punto de salida y se apaga hacia donde sueltas."
            ZoneKind.CIRCLE -> "Arrastra desde el centro hacia fuera para dar tamaño al círculo."
            ZoneKind.BRUSH -> "Pinta las partes que quieres ajustar."
        },
        style = SmallStyle,
    )
    if (zone.kind == ZoneKind.BRUSH) PhotoTuner("Pincel", brush * 100, 1f..12f, "%.0f") { onBrush(it / 100) }
    PhotoTuner("Luz", zone.light * 100, -100f..100f, "%.0f") { setZone(zone.copy(light = snapZero(it) / 100)) }
    PhotoTuner("Contraste", zone.contrast * 100, -100f..100f, "%.0f") { setZone(zone.copy(contrast = snapZero(it) / 100)) }
    PhotoTuner("Color", zone.color * 100, -100f..100f, "%.0f") { setZone(zone.copy(color = snapZero(it) / 100)) }
    PhotoTuner("Calidez", zone.warmth * 100, -100f..100f, "%.0f") { setZone(zone.copy(warmth = snapZero(it) / 100)) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (zone.kind == ZoneKind.BRUSH && zone.strokes.isNotEmpty()) PhotoChip("Borrar lo pintado", false) { setZone(zone.copy(strokes = emptyList())) }
        PhotoChip("Quitar esta zona", false) {
            onChange(r.copy(zones = r.zones.filterIndexed { i, _ -> i != selected }))
            onSelect((selected - 1).coerceAtLeast(0))
        }
    }
}

private fun snapZero(v: Float) = if (kotlin.math.abs(v) < 4f) 0f else v

@Composable
fun FacesPanel(r: Retouch, status: String?, onChange: (Retouch) -> Unit) {
    if (status != null) Text(status, style = SmallStyle.copy(fontSize = 14.sp, color = Lumi.Ink))
    PhotoTuner("Retoque", r.faces.amount * 100, 0f..100f, "%.0f") { onChange(r.copy(faces = r.faces.copy(amount = it / 100))) }
    Text("Suaviza la piel sin quitar los detalles y da luz a los ojos. Con un solo control.", style = SmallStyle)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoChip("Quitar ojos rojos", r.faces.redEye) { onChange(r.copy(faces = r.faces.copy(redEye = !r.faces.redEye))) }
    }
}

@Composable
fun FramePanel(r: Retouch, onChange: (Retouch) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FrameKind.entries.forEach { kind -> PhotoChip(kind.label, r.frame.kind == kind) { onChange(r.copy(frame = r.frame.copy(kind = kind))) } }
    }
    if (r.frame.kind != FrameKind.NONE) PhotoTuner("Grosor", r.frame.size * 100, 0f..100f, "%.0f") { onChange(r.copy(frame = r.frame.copy(size = it / 100))) }
    Text("El marco se pone al guardar, después del recorte. «Desenfocado» rellena con la misma foto difuminada, sin franjas negras.", style = SmallStyle)
}

@Composable
fun RawPanel(r: Retouch, headroom: Boolean?, onChange: (Retouch) -> Unit) {
    Text(
        when (headroom) {
            true -> "Este RAW conserva margen en luces y sombras: recupera lo que un JPEG ya habría perdido."
            false -> "Android entrega este RAW ya revelado a 8 bits, así que el margen es el de un JPEG: los ajustes valen, pero con menos recorrido."
            null -> "Abriendo el RAW…"
        },
        style = SmallStyle.copy(fontSize = 13.sp, color = Lumi.Ink),
    )
    PhotoTuner("Exposición", r.raw.ev * 100, -100f..100f, "%.0f") { onChange(r.copy(raw = r.raw.copy(ev = snapZero(it) / 100))) }
    PhotoTuner("Recuperar luces", -r.raw.highlights * 100, 0f..100f, "%.0f") { onChange(r.copy(raw = r.raw.copy(highlights = -it / 100))) }
    PhotoTuner("Abrir sombras", r.raw.shadows * 100, 0f..100f, "%.0f") { onChange(r.copy(raw = r.raw.copy(shadows = it / 100))) }
    PhotoTuner("Calidez", r.raw.warmth * 100, -100f..100f, "%.0f") { onChange(r.copy(raw = r.raw.copy(warmth = snapZero(it) / 100))) }
}

/** Un paso del historial de la edición. */
class HistoryStep(val label: String, val json: String)

@Composable
fun HistoryDialog(steps: List<HistoryStep>, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text("Historial de cambios", style = HeadingStyle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Toca un paso para volver a cómo estaba la foto entonces. Lo que hagas después se añade al final.", style = SmallStyle)
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(steps.reversed()) { reversedIndex, step ->
                        val i = steps.lastIndex - reversedIndex
                        val on = i == current
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) Lumi.Accent.copy(alpha = 0.16f) else Lumi.Bg)
                                .clickable { onPick(i) }.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i}", style = SmallStyle, modifier = Modifier.padding(end = 10.dp))
                            Text(step.label, style = LabelStyle, modifier = Modifier.weight(1f))
                            if (on) Text("Ahora", style = SmallStyle, color = Lumi.Accent)
                        }
                    }
                }
            }
        },
        confirmButton = { Text("Cerrar", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(12.dp)) },
    )
}
