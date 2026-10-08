@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lumi.galeria.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.R
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Todo lo que se dibuja se guarda en unidades de la foto: 1 es el ancho de la imagen. Así se ve
// igual en pantalla que en la copia guardada, sea cual sea su resolución.

private enum class Tool(val label: String) {
    PEN("Pincel"), MARKER("Rotulador"), ARROW("Flecha"), LINE("Línea"), OVAL("Círculo"), BOX("Rectángulo"),
    TEXT("Texto"), STICKER("Pegatina"), MOVE("Mover"),
}

private enum class Lettering(val label: String) { PLAIN("Sencillo"), BOX("Con fondo"), OUTLINE("Contorno") }

private enum class Face(val label: String) { BOLD("Normal"), TITLE("Titular"), SERIF("Clásica"), MONO("Máquina") }

/** Algo dibujado. Se puede mover ([shift]), agrandar ([scale]) y girar ([turn], en grados) alrededor de su centro. */
private sealed class Mark {
    var shift by mutableStateOf(Offset.Zero)
    var scale by mutableFloatStateOf(1f)
    var turn by mutableFloatStateOf(0f)

    /** Rectángulo que ocupa sin mover ni girar. */
    abstract fun bounds(): Rect

    fun center(): Offset = bounds().center + shift
}

private class Ink(val points: List<Offset>, val color: Color, val width: Float, val marker: Boolean) : Mark() {
    override fun bounds() = Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y }).inflate(width / 2)
}

private class Shape(val tool: Tool, val start: Offset, val end: Offset, val color: Color, val width: Float) : Mark() {
    override fun bounds() = Rect(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y)).inflate(width * 2)
}

private class Label(val text: String, val color: Color, val style: Lettering, val face: Face, val at: Offset, val size: Float) : Mark() {
    override fun bounds(): Rect {
        val w = size * 0.56f * text.length.coerceAtLeast(1)
        return Rect(at.x - w / 2, at.y - size * 0.8f, at.x + w / 2, at.y + size * 0.3f)
    }
}

private class Sticker(val emoji: String, val at: Offset, val size: Float) : Mark() {
    override fun bounds() = Rect(at.x - size / 2, at.y - size / 2, at.x + size / 2, at.y + size / 2)
}

/** Lo que se puede deshacer: añadir, borrar o mover/girar/agrandar algo. */
private sealed interface Step {
    class Add(val mark: Mark) : Step
    class Remove(val mark: Mark, val index: Int) : Step
    class Move(val mark: Mark, val from: Triple<Offset, Float, Float>, val to: Triple<Offset, Float, Float>) : Step
}

private val PALETTE = listOf(
    Color.White, Color(0xFF111111), Color(0xFFFF5A5A), Color(0xFFFF9F43), Color(0xFFFFD43B),
    Color(0xFF5BC48A), Color(0xFF4F8DF7), Color(0xFFB98CFF), Color(0xFFFF7AC6),
)

private val STICKERS = listOf(
    "😀", "😂", "😍", "🥰", "😎", "🤩", "😮", "😢", "😡", "🥳", "🤔", "😴",
    "❤️", "💔", "✨", "🔥", "⭐", "💯", "👍", "👎", "👏", "🙌", "👀", "💪",
    "🎉", "🎂", "🎁", "🌸", "🌈", "☀️", "🌙", "❄️", "🐶", "🐱", "🍕", "☕",
    "✈️", "🏖️", "⛰️", "🏰", "📍", "➡️", "⬅️", "⬆️", "⬇️", "✅", "❌", "❓",
)

/**
 * Dibujar sobre una foto: pincel, rotulador, flechas, líneas, círculos y rectángulos rectos, texto
 * con estilos y pegatinas. Con «Mover» se toca cualquier cosa para moverla, y con dos dedos se
 * agranda y se gira. Todo se deshace y se rehace paso a paso. Se guarda siempre como copia.
 */
@Composable
fun MarkupScreen(screen: Screen.Markup, state: UiState, vm: LumiViewModel) {
    val item = state.items.firstOrNull { it.id == screen.id }
    if (item == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        bitmap = withContext(Dispatchers.IO) { decode(context, item, 3072) }
        failed = bitmap == null
    }
    val title = remember { runCatching { ResourcesCompat.getFont(context, R.font.schibsted_grotesk) }.getOrNull() }

    val marks = remember { mutableStateListOf<Mark>() }
    val done = remember { mutableStateListOf<Step>() }
    val undone = remember { mutableStateListOf<Step>() }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(PALETTE[4]) }
    var thickness by remember { mutableFloatStateOf(0.35f) }
    var selected by remember { mutableStateOf<Mark?>(null) }
    // Lo que se está dibujando con el dedo, antes de soltarlo.
    var live by remember { mutableStateOf<Mark?>(null) }
    var writing by remember { mutableStateOf(false) }
    var stickers by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(IntSize.Zero) }
    val source = bitmap
    val strokeWidth = 0.004f + thickness * 0.03f

    fun record(step: Step) {
        done += step
        undone.clear()
    }

    fun add(mark: Mark) {
        marks += mark
        record(Step.Add(mark))
    }

    fun undo() {
        val step = done.removeLastOrNull() ?: return
        when (step) {
            is Step.Add -> marks.remove(step.mark)
            is Step.Remove -> marks.add(step.index.coerceAtMost(marks.size), step.mark)
            is Step.Move -> step.mark.apply { shift = step.from.first; scale = step.from.second; turn = step.from.third }
        }
        if (selected != null && selected !in marks) selected = null
        undone += step
    }

    fun redo() {
        val step = undone.removeLastOrNull() ?: return
        when (step) {
            is Step.Add -> marks += step.mark
            is Step.Remove -> marks.remove(step.mark)
            is Step.Move -> step.mark.apply { shift = step.to.first; scale = step.to.second; turn = step.to.third }
        }
        done += step
    }

    /** Hueco de la pantalla que ocupa la foto, ajustada sin deformar. */
    fun area(): Rect {
        val b = source ?: return Rect.Zero
        if (view.width == 0) return Rect.Zero
        val k = minOf(view.width.toFloat() / b.width, view.height.toFloat() / b.height)
        val w = b.width * k
        val h = b.height * k
        return Rect(Offset((view.width - w) / 2, (view.height - h) / 2), Size(w, h))
    }

    fun toPhoto(point: Offset): Offset {
        val a = area()
        return Offset((point.x - a.left) / a.width, (point.y - a.top) / a.width)
    }

    /** Lo de más arriba que hay bajo el dedo, con un margen generoso para que sea fácil acertar. */
    fun hit(point: Offset): Mark? = marks.lastOrNull { mark ->
        val b = mark.bounds()
        val c = mark.center()
        val dx = point.x - c.x
        val dy = point.y - c.y
        // Se deshace el giro para comparar con el rectángulo sin girar.
        val r = Math.toRadians(-mark.turn.toDouble())
        val x = (dx * cos(r) - dy * sin(r)).toFloat() / mark.scale
        val y = (dx * sin(r) + dy * cos(r)).toFloat() / mark.scale
        kotlin.math.abs(x) <= b.width / 2 + 0.03f && kotlin.math.abs(y) <= b.height / 2 + 0.03f
    }

    BackHandler(selected != null) { selected = null }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false)
            Spacer(Modifier.weight(1f))
            RoundIcon(UndoIcon, "Deshacer", done.isNotEmpty()) { undo() }
            RoundIcon(RedoIcon, "Rehacer", undone.isNotEmpty()) { redo() }
            Spacer(Modifier.width(8.dp))
            PillButton(
                if (saving) "Guardando…" else "Guardar copia",
                onClick = {
                    val base = source ?: return@PillButton
                    saving = true
                    selected = null
                    val drawn = marks.toList()
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            runCatching {
                                val out = base.copy(Bitmap.Config.ARGB_8888, true)
                                val canvas = android.graphics.Canvas(out)
                                drawn.forEach { render(canvas, it, out.width.toFloat(), title) }
                                out
                            }.getOrNull()
                        }
                        if (result != null) vm.saveArt(result, item.name.substringBeforeLast('.') + "_dibujo", item.path, "Copia guardada con tus dibujos").join()
                        else vm.say("No hay memoria suficiente para esta foto")
                        saving = false
                    }
                },
                enabled = marks.isNotEmpty() && !saving,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                failed -> EmptyMessage("No se puede abrir esta foto", "El formato no se deja editar.")
                source != null -> {
                    val image = remember(source) { source.asImageBitmap() }
                    val accent = Lumi.Accent
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { view = it }
                            .pointerInput(tool, color, strokeWidth) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val a = area()
                                    if (a.width <= 0) return@awaitEachGesture
                                    val start = toPhoto(down.position)
                                    if (tool == Tool.MOVE) {
                                        // Tocar algo lo elige; arrastrar lo mueve; con dos dedos se agranda y se gira.
                                        val target = hit(start)
                                        selected = target
                                        if (target == null) return@awaitEachGesture
                                        val before = Triple(target.shift, target.scale, target.turn)
                                        do {
                                            val event = awaitPointerEvent()
                                            val pan = event.calculatePan()
                                            target.shift += Offset(pan.x / a.width, pan.y / a.width)
                                            if (event.changes.count { it.pressed } >= 2) {
                                                target.scale = (target.scale * event.calculateZoom()).coerceIn(0.2f, 8f)
                                                target.turn += event.calculateRotation()
                                            }
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        } while (event.changes.any { it.pressed })
                                        val after = Triple(target.shift, target.scale, target.turn)
                                        if (after != before) record(Step.Move(target, before, after))
                                        return@awaitEachGesture
                                    }
                                    if (tool == Tool.TEXT || tool == Tool.STICKER) return@awaitEachGesture
                                    val points = arrayListOf(start)
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.positionChanged()) {
                                            change.consume()
                                            val p = toPhoto(change.position)
                                            when (tool) {
                                                Tool.PEN, Tool.MARKER -> {
                                                    points += p
                                                    live = Ink(points.toList(), color, if (tool == Tool.MARKER) strokeWidth * 3f else strokeWidth, tool == Tool.MARKER)
                                                }
                                                else -> live = Shape(tool, start, p, color, strokeWidth)
                                            }
                                        }
                                    } while (change.pressed)
                                    live?.let { mark ->
                                        val b = mark.bounds()
                                        if (b.width > 0.01f || b.height > 0.01f) add(mark)
                                    }
                                    live = null
                                }
                            },
                    ) {
                        val a = area()
                        drawImage(
                            image,
                            dstOffset = IntOffset(a.left.roundToInt(), a.top.roundToInt()),
                            dstSize = IntSize(a.width.roundToInt(), a.height.roundToInt()),
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                        )
                        val canvas = drawContext.canvas.nativeCanvas
                        canvas.save()
                        canvas.clipRect(a.left, a.top, a.right, a.bottom)
                        canvas.translate(a.left, a.top)
                        marks.forEach { render(canvas, it, a.width, title) }
                        live?.let { render(canvas, it, a.width, title) }
                        canvas.restore()
                        // Marco de lo elegido, girado con él.
                        selected?.let { mark ->
                            val b = mark.bounds()
                            val c = mark.center()
                            val half = Size(b.width * mark.scale / 2 * a.width + 12f, b.height * mark.scale / 2 * a.width + 12f)
                            val center = Offset(a.left + c.x * a.width, a.top + c.y * a.width)
                            drawContext.canvas.save()
                            drawContext.canvas.translate(center.x, center.y)
                            drawContext.canvas.rotate(mark.turn)
                            drawRoundRect(
                                accent, Offset(-half.width, -half.height), Size(half.width * 2, half.height * 2),
                                androidx.compose.ui.geometry.CornerRadius(12f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
                            )
                            drawContext.canvas.restore()
                        }
                    }
                }
            }
        }

        Column(Modifier.background(Lumi.Bg).padding(top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tool == Tool.MOVE) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (selected == null) "Toca algo para moverlo. Con dos dedos se agranda y se gira." else "Arrastra para moverlo; con dos dedos, agrándalo o gíralo.",
                        style = SmallStyle, modifier = Modifier.weight(1f),
                    )
                    selected?.let { mark ->
                        RoundIcon(Icons.Filled.Delete, "Borrar", true) {
                            val index = marks.indexOf(mark)
                            marks.remove(mark)
                            record(Step.Remove(mark, index))
                            selected = null
                        }
                    }
                }
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PALETTE.forEach { option ->
                        Box(
                            Modifier.size(if (option == color) 30.dp else 24.dp).clip(CircleShape).background(option)
                                .border(2.dp, if (option == color) Lumi.Accent else Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable { color = option },
                        )
                    }
                }
                if (tool != Tool.STICKER && tool != Tool.TEXT) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Grosor", style = SmallStyle)
                        Slider(
                            thickness, { thickness = it }, Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                        )
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size((4 + thickness * 20).dp).clip(CircleShape).background(color))
                        }
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Tool.entries.forEach { option ->
                    ToolButton(option, option == tool) {
                        when (option) {
                            Tool.TEXT -> writing = true
                            Tool.STICKER -> stickers = true
                            else -> {
                                tool = option
                                if (option != Tool.MOVE) selected = null
                            }
                        }
                    }
                }
            }
        }
    }

    if (writing) {
        TextDialog(color, onDone = { text, style, face ->
            writing = false
            val b = source ?: return@TextDialog
            val label = Label(text, color, style, face, Offset(0.5f, b.height.toFloat() / b.width / 2), 0.075f)
            add(label)
            // Recién puesto queda elegido, para colocarlo donde haga falta.
            tool = Tool.MOVE
            selected = label
        }) { writing = false }
    }
    if (stickers) {
        ModalBottomSheet(onDismissRequest = { stickers = false }, containerColor = Lumi.Surface) {
            Text("Pegatinas", style = HeadingStyle, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
            LazyVerticalGrid(GridCells.Fixed(6), Modifier.fillMaxWidth().height(320.dp).padding(horizontal = 12.dp)) {
                items(STICKERS) { emoji ->
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).clickable {
                            stickers = false
                            val b = source ?: return@clickable
                            val sticker = Sticker(emoji, Offset(0.5f, b.height.toFloat() / b.width / 2), 0.18f)
                            add(sticker)
                            tool = Tool.MOVE
                            selected = sticker
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 30.sp) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Pinta [mark] en [canvas], cuyo origen es la esquina de la foto y [w] su ancho en píxeles. */
private fun render(canvas: android.graphics.Canvas, mark: Mark, w: Float, title: Typeface?) {
    val c = mark.bounds().center
    canvas.save()
    canvas.translate(mark.shift.x * w, mark.shift.y * w)
    canvas.rotate(mark.turn, c.x * w, c.y * w)
    canvas.scale(mark.scale, mark.scale, c.x * w, c.y * w)
    when (mark) {
        is Ink -> {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mark.color.toArgb()
                if (mark.marker) alpha = 110
                style = Paint.Style.STROKE
                strokeWidth = mark.width * w
                strokeCap = if (mark.marker) Paint.Cap.SQUARE else Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = android.graphics.Path()
            mark.points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.x * w, p.y * w)
                else {
                    // Curvas suaves entre puntos en vez de una línea quebrada.
                    val prev = mark.points[i - 1]
                    path.quadTo(prev.x * w, prev.y * w, (prev.x + p.x) / 2 * w, (prev.y + p.y) / 2 * w)
                }
            }
            canvas.drawPath(path, paint)
        }
        is Shape -> {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mark.color.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = mark.width * w
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val s = Offset(mark.start.x * w, mark.start.y * w)
            val e = Offset(mark.end.x * w, mark.end.y * w)
            when (mark.tool) {
                Tool.OVAL -> canvas.drawOval(RectF(minOf(s.x, e.x), minOf(s.y, e.y), maxOf(s.x, e.x), maxOf(s.y, e.y)), paint)
                Tool.BOX -> canvas.drawRoundRect(RectF(minOf(s.x, e.x), minOf(s.y, e.y), maxOf(s.x, e.x), maxOf(s.y, e.y)), paint.strokeWidth, paint.strokeWidth, paint)
                else -> {
                    canvas.drawLine(s.x, s.y, e.x, e.y, paint)
                    if (mark.tool == Tool.ARROW) {
                        val angle = atan2(e.y - s.y, e.x - s.x)
                        val head = maxOf(paint.strokeWidth * 4f, hypot(e.x - s.x, e.y - s.y) * 0.12f).coerceAtMost(hypot(e.x - s.x, e.y - s.y) * 0.5f)
                        for (side in listOf(-1, 1)) {
                            val a = angle + Math.PI.toFloat() + side * 0.5f
                            canvas.drawLine(e.x, e.y, e.x + cos(a) * head, e.y + sin(a) * head, paint)
                        }
                    }
                }
            }
        }
        is Label -> {
            val size = mark.size * w
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                textAlign = Paint.Align.CENTER
                typeface = when (mark.face) {
                    Face.BOLD -> Typeface.DEFAULT_BOLD
                    Face.TITLE -> Typeface.create(title ?: Typeface.DEFAULT, Typeface.BOLD)
                    Face.SERIF -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    Face.MONO -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
            }
            val x = mark.at.x * w
            val y = mark.at.y * w
            val ink = mark.color.toArgb()
            // Un color claro va sobre fondo oscuro y al revés, para que siempre se lea.
            val light = mark.color.red * 0.3f + mark.color.green * 0.59f + mark.color.blue * 0.11f > 0.6f
            when (mark.style) {
                Lettering.PLAIN -> {
                    paint.color = ink
                    paint.setShadowLayer(size * 0.12f, 0f, 0f, android.graphics.Color.argb(160, 0, 0, 0))
                    canvas.drawText(mark.text, x, y, paint)
                }
                Lettering.BOX -> {
                    val width = paint.measureText(mark.text)
                    val box = RectF(x - width / 2 - size * 0.35f, y - size * 0.95f, x + width / 2 + size * 0.35f, y + size * 0.35f)
                    canvas.drawRoundRect(box, size * 0.3f, size * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink })
                    paint.color = if (light) android.graphics.Color.rgb(20, 20, 20) else android.graphics.Color.WHITE
                    canvas.drawText(mark.text, x, y, paint)
                }
                Lettering.OUTLINE -> {
                    val edge = Paint(paint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = size * 0.14f
                        strokeJoin = Paint.Join.ROUND
                        color = if (light) android.graphics.Color.rgb(20, 20, 20) else android.graphics.Color.WHITE
                    }
                    canvas.drawText(mark.text, x, y, edge)
                    paint.color = ink
                    canvas.drawText(mark.text, x, y, paint)
                }
            }
        }
        is Sticker -> {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = mark.size * w
                textAlign = Paint.Align.CENTER
            }
            val metrics = paint.fontMetrics
            canvas.drawText(mark.emoji, mark.at.x * w, mark.at.y * w - (metrics.ascent + metrics.descent) / 2, paint)
        }
    }
    canvas.restore()
}

@Composable
private fun TextDialog(color: Color, onDone: (String, Lettering, Face) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(Lettering.BOX) }
    var face by remember { mutableStateOf(Face.BOLD) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text("Escribe el texto", style = HeadingStyle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text, { text = it.take(80) }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Lettering.entries.forEach { option -> Choice(option.label, option == style) { style = option } }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Face.entries.forEach { option -> Choice(option.label, option == face) { face = option } }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                    Text("Con el color elegido. Después se mueve, se agranda y se gira.", style = SmallStyle)
                }
            }
        },
        confirmButton = {
            Text(
                "Poner", style = LabelStyle, color = if (text.isBlank()) Lumi.Muted else Lumi.Accent,
                modifier = Modifier.clip(CircleShape).clickable(enabled = text.isNotBlank()) { onDone(text.trim(), style, face) }.padding(12.dp),
            )
        },
        dismissButton = {
            Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(12.dp))
        },
    )
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, style = LabelStyle, color = if (on) Lumi.OnAccent else Lumi.Ink,
        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Bg).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f)) }
}

/** Botón de herramienta: un dibujito de lo que hace y su nombre debajo. */
@Composable
private fun ToolButton(tool: Tool, on: Boolean, onClick: () -> Unit) {
    val ink = if (on) Lumi.OnAccent else Lumi.Ink
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Surface), contentAlignment = Alignment.Center) {
            when (tool) {
                Tool.TEXT -> Text("Aa", style = LabelStyle.copy(fontSize = 15.sp), color = ink)
                Tool.STICKER -> Text("☺", fontSize = 20.sp, color = ink)
                else -> Canvas(Modifier.size(22.dp)) {
                    val s = size.width
                    val line = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                    when (tool) {
                        Tool.PEN -> {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(s * 0.1f, s * 0.75f)
                                cubicTo(s * 0.3f, s * 0.2f, s * 0.55f, s * 0.95f, s * 0.9f, s * 0.25f)
                            }
                            drawPath(path, ink, style = line)
                        }
                        Tool.MARKER -> drawLine(ink.copy(alpha = 0.55f), Offset(s * 0.1f, s * 0.6f), Offset(s * 0.9f, s * 0.4f), s * 0.28f, StrokeCap.Square)
                        Tool.ARROW -> {
                            drawLine(ink, Offset(s * 0.12f, s * 0.88f), Offset(s * 0.88f, s * 0.12f), line.width, StrokeCap.Round)
                            drawLine(ink, Offset(s * 0.88f, s * 0.12f), Offset(s * 0.5f, s * 0.14f), line.width, StrokeCap.Round)
                            drawLine(ink, Offset(s * 0.88f, s * 0.12f), Offset(s * 0.86f, s * 0.5f), line.width, StrokeCap.Round)
                        }
                        Tool.LINE -> drawLine(ink, Offset(s * 0.12f, s * 0.88f), Offset(s * 0.88f, s * 0.12f), line.width, StrokeCap.Round)
                        Tool.OVAL -> drawOval(ink, Offset(s * 0.08f, s * 0.2f), Size(s * 0.84f, s * 0.6f), style = line)
                        Tool.BOX -> drawRoundRect(ink, Offset(s * 0.1f, s * 0.2f), Size(s * 0.8f, s * 0.6f), androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = line)
                        Tool.MOVE -> {
                            drawLine(ink, Offset(s * 0.5f, s * 0.05f), Offset(s * 0.5f, s * 0.95f), line.width, StrokeCap.Round)
                            drawLine(ink, Offset(s * 0.05f, s * 0.5f), Offset(s * 0.95f, s * 0.5f), line.width, StrokeCap.Round)
                            for ((tip, a, b) in listOf(
                                Triple(Offset(s * 0.5f, s * 0.05f), Offset(s * 0.35f, s * 0.2f), Offset(s * 0.65f, s * 0.2f)),
                                Triple(Offset(s * 0.5f, s * 0.95f), Offset(s * 0.35f, s * 0.8f), Offset(s * 0.65f, s * 0.8f)),
                                Triple(Offset(s * 0.05f, s * 0.5f), Offset(s * 0.2f, s * 0.35f), Offset(s * 0.2f, s * 0.65f)),
                                Triple(Offset(s * 0.95f, s * 0.5f), Offset(s * 0.8f, s * 0.35f), Offset(s * 0.8f, s * 0.65f)),
                            )) {
                                drawLine(ink, tip, a, line.width, StrokeCap.Round)
                                drawLine(ink, tip, b, line.width, StrokeCap.Round)
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
        Text(tool.label, style = SmallStyle.copy(fontSize = 11.sp), color = if (on) Lumi.Accent else Lumi.Muted, maxLines = 1)
    }
}
