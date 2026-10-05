package com.lumi.galeria.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.formatDuration
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun decode(context: android.content.Context, item: MediaItem, maxSide: Int): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
        val longSide = maxOf(info.size.width, info.size.height)
        if (longSide > maxSide) {
            val k = maxSide.toFloat() / longSide
            decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
        }
    }
}.getOrNull()

@Composable
private fun ToolChip(label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        style = LabelStyle,
        color = when {
            !enabled -> Lumi.Muted
            on -> Lumi.OnAccent
            else -> Lumi.Ink
        },
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) Lumi.Accent else Lumi.Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

// ---------- Collage ----------

private enum class Frame(val label: String, val ratio: Float) { SQUARE("1:1", 1f), PORTRAIT("4:5", 0.8f), STORY("9:16", 9f / 16f), WIDE("16:9", 16f / 9f) }

/** Dónde va cada foto, en proporción del lienzo, según cuántas haya. */
private fun layout(count: Int): List<Rect> = when (count) {
    2 -> listOf(Rect(0f, 0f, 0.5f, 1f), Rect(0.5f, 0f, 1f, 1f))
    3 -> listOf(Rect(0f, 0f, 0.5f, 1f), Rect(0.5f, 0f, 1f, 0.5f), Rect(0.5f, 0.5f, 1f, 1f))
    4 -> listOf(Rect(0f, 0f, 0.5f, 0.5f), Rect(0.5f, 0f, 1f, 0.5f), Rect(0f, 0.5f, 0.5f, 1f), Rect(0.5f, 0.5f, 1f, 1f))
    5 -> listOf(
        Rect(0f, 0f, 0.5f, 0.5f), Rect(0.5f, 0f, 1f, 0.5f),
        Rect(0f, 0.5f, 1f / 3, 1f), Rect(1f / 3, 0.5f, 2f / 3, 1f), Rect(2f / 3, 0.5f, 1f, 1f),
    )
    else -> List(6) { i -> Rect(i % 3 / 3f, i / 3 / 2f, (i % 3 + 1) / 3f, (i / 3 + 1) / 2f) }
}

@Composable
fun CollageScreen(screen: Screen.Collage, state: UiState, vm: LumiViewModel) {
    val items = remember(screen.ids, state.items) { screen.ids.mapNotNull { id -> state.items.firstOrNull { it.id == id && !it.isVideo } }.take(6) }
    if (items.size < 2) {
        LaunchedEffect(Unit) {
            vm.say("Elige entre 2 y 6 fotos para el collage")
            vm.back()
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var frame by remember { mutableStateOf(Frame.SQUARE) }
    var gap by remember { mutableStateOf(true) }
    var darkBack by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val cells = remember(items.size) { layout(items.size) }
    val back = if (darkBack) Color.Black else Color.White

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false)
            Text("Collage", style = HeadingStyle, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            PillButton(
                if (saving) "Guardando…" else "Guardar",
                onClick = {
                    saving = true
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            runCatching {
                                val width = 2160
                                val height = (width / frame.ratio).roundToInt()
                                val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(out)
                                canvas.drawColor(back.toArgb())
                                val pad = if (gap) 14f else 0f
                                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                                items.forEachIndexed { i, item ->
                                    val photo = decode(context, item, 1600) ?: return@forEachIndexed
                                    val cell = cells[i]
                                    val target = android.graphics.RectF(
                                        cell.left * width + pad, cell.top * height + pad, cell.right * width - pad, cell.bottom * height - pad,
                                    )
                                    // Se recorta por el centro para llenar el hueco sin deformar la foto.
                                    val scale = maxOf(target.width() / photo.width, target.height() / photo.height)
                                    val cropW = target.width() / scale
                                    val cropH = target.height() / scale
                                    val left = (photo.width - cropW) / 2
                                    val top = (photo.height - cropH) / 2
                                    canvas.drawBitmap(
                                        photo,
                                        android.graphics.Rect(left.toInt(), top.toInt(), (left + cropW).toInt(), (top + cropH).toInt()),
                                        target, paint,
                                    )
                                    photo.recycle()
                                }
                                out
                            }.getOrNull()
                        }
                        if (result != null) vm.saveArt(result, "Collage_${System.currentTimeMillis() / 1000}", "Pictures/Lumi/", "Collage guardado en el álbum Lumi").join()
                        else vm.say("No hay memoria suficiente para este collage")
                        saving = false
                    }
                },
                enabled = !saving,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.aspectRatio(frame.ratio).background(back)) {
                val w = maxWidth
                val h = maxHeight
                val pad = if (gap) 3.dp else 0.dp
                items.forEachIndexed { i, item ->
                    val cell = cells[i]
                    MediaThumb(
                        item, 512,
                        Modifier
                            .offset(x = w * cell.left, y = h * cell.top)
                            .size(width = w * cell.width, height = h * cell.height)
                            .padding(pad),
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Frame.entries.forEach { option -> ToolChip(option.label, frame == option) { frame = option } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolChip("Separación", gap) { gap = !gap }
                ToolChip("Fondo oscuro", darkBack) { darkBack = !darkBack }
            }
        }
    }
}

// ---------- Dibujar y escribir sobre la foto ----------

/** Posiciones y grosores van en proporción del ancho de la foto, para que valgan a cualquier tamaño. */
private class Stroke2(val points: List<Offset>, val color: Color, val width: Float)

private class Note(val text: String, val color: Color, val size: Float, position: Offset) {
    var position by mutableStateOf(position)
}

private val INKS = listOf(Color.White, Color.Black, Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E5BFF))

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

    // Trazos y textos, en el orden en que se hicieron, para poder deshacer de uno en uno.
    val marks = remember { mutableStateListOf<Any>() }
    val live = remember { mutableStateListOf<Offset>() }
    var ink by remember { mutableStateOf(INKS[2]) }
    var thick by remember { mutableIntStateOf(1) }
    var writing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(IntSize.Zero) }
    val widths = listOf(0.006f, 0.012f, 0.026f)
    val source = bitmap

    /** Hueco de la pantalla que ocupa la foto, ajustada sin deformar. */
    fun area(): Rect {
        val b = source ?: return Rect.Zero
        if (view.width == 0) return Rect.Zero
        val k = minOf(view.width.toFloat() / b.width, view.height.toFloat() / b.height)
        val w = b.width * k
        val h = b.height * k
        return Rect(Offset((view.width - w) / 2, (view.height - h) / 2), Size(w, h))
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false)
            Text("Dibujar", style = HeadingStyle, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            PillButton(
                if (saving) "Guardando…" else "Guardar copia",
                onClick = {
                    val base = source ?: return@PillButton
                    saving = true
                    val drawn = marks.toList()
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            runCatching {
                                val out = base.copy(Bitmap.Config.ARGB_8888, true)
                                val canvas = android.graphics.Canvas(out)
                                val w = out.width.toFloat()
                                drawn.forEach { mark ->
                                    when (mark) {
                                        is Stroke2 -> {
                                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                color = mark.color.toArgb()
                                                style = Paint.Style.STROKE
                                                strokeWidth = mark.width * w
                                                strokeCap = Paint.Cap.ROUND
                                                strokeJoin = Paint.Join.ROUND
                                            }
                                            val path = android.graphics.Path()
                                            mark.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x * w, p.y * w) else path.lineTo(p.x * w, p.y * w) }
                                            canvas.drawPath(path, paint)
                                        }
                                        is Note -> {
                                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                color = mark.color.toArgb()
                                                textSize = mark.size * w
                                                typeface = Typeface.DEFAULT_BOLD
                                                textAlign = Paint.Align.CENTER
                                                setShadowLayer(mark.size * w * 0.12f, 0f, 0f, android.graphics.Color.argb(160, 0, 0, 0))
                                            }
                                            canvas.drawText(mark.text, mark.position.x * w, mark.position.y * w, paint)
                                        }
                                    }
                                }
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
                    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER } }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { view = it }
                            .pointerInput(writing, ink, thick) {
                                detectDragGestures(
                                    onDragStart = { point ->
                                        val a = area()
                                        if (!writing && a.width > 0) {
                                            live.clear()
                                            live += Offset((point.x - a.left) / a.width, (point.y - a.top) / a.width)
                                        }
                                    },
                                    onDragEnd = {
                                        if (live.size > 1) marks += Stroke2(live.toList(), ink, widths[thick])
                                        live.clear()
                                    },
                                    onDragCancel = { live.clear() },
                                ) { change, drag ->
                                    change.consume()
                                    val a = area()
                                    if (a.width <= 0) return@detectDragGestures
                                    if (writing) {
                                        // En modo texto, arrastrar mueve el último texto escrito.
                                        (marks.lastOrNull { it is Note } as? Note)?.let { it.position += Offset(drag.x / a.width, drag.y / a.width) }
                                    } else {
                                        live += Offset((change.position.x - a.left) / a.width, (change.position.y - a.top) / a.width)
                                    }
                                }
                            },
                    ) {
                        val a = area()
                        drawImage(
                            image,
                            dstOffset = IntOffset(a.left.roundToInt(), a.top.roundToInt()),
                            dstSize = IntSize(a.width.roundToInt(), a.height.roundToInt()),
                        )

                        fun trace(points: List<Offset>, color: Color, width: Float) {
                            if (points.size < 2) return
                            val path = Path()
                            points.forEachIndexed { i, p ->
                                val x = a.left + p.x * a.width
                                val y = a.top + p.y * a.width
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color, style = Stroke(width * a.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }

                        marks.forEach { mark ->
                            when (mark) {
                                is Stroke2 -> trace(mark.points, mark.color, mark.width)
                                is Note -> {
                                    textPaint.color = mark.color.toArgb()
                                    textPaint.textSize = mark.size * a.width
                                    textPaint.setShadowLayer(mark.size * a.width * 0.12f, 0f, 0f, android.graphics.Color.argb(160, 0, 0, 0))
                                    drawContext.canvas.nativeCanvas.drawText(
                                        mark.text, a.left + mark.position.x * a.width, a.top + mark.position.y * a.width, textPaint,
                                    )
                                }
                            }
                        }
                        trace(live, ink, widths[thick])
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                INKS.forEach { color ->
                    Box(
                        Modifier
                            .size(if (color == ink) 34.dp else 28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(2.dp, if (color == ink) Lumi.Accent else Lumi.Line, CircleShape)
                            .clickable { ink = color },
                    )
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolChip("Dibujar", !writing) { writing = false }
                ToolChip("Texto", writing) {
                    typed = ""
                    dialog = true
                }
                ToolChip(listOf("Fino", "Medio", "Grueso")[thick], false) { thick = (thick + 1) % 3 }
                ToolChip("Deshacer", false, enabled = marks.isNotEmpty()) { marks.removeAt(marks.lastIndex) }
            }
            if (writing) Text("Arrastra para colocar el texto. Toca «Texto» para añadir otro.", style = SmallStyle)
        }
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            containerColor = Lumi.Surface,
            title = { Text("Escribe el texto", style = HeadingStyle) },
            text = { OutlinedTextField(typed, { typed = it.take(60) }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
            confirmButton = {
                Text(
                    "Poner", style = LabelStyle, color = if (typed.isBlank()) Lumi.Muted else Lumi.Accent,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = typed.isNotBlank()) {
                        val b = source
                        // Empieza centrado; después se arrastra adonde haga falta.
                        if (b != null) marks += Note(typed.trim(), ink, 0.07f, Offset(0.5f, b.height.toFloat() / b.width / 2))
                        writing = true
                        dialog = false
                    }.padding(12.dp),
                )
            },
            dismissButton = {
                Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { dialog = false }.padding(12.dp))
            },
        )
    }
}

// ---------- Recortar un vídeo ----------

@Composable
fun TrimScreen(screen: Screen.Trim, state: UiState, vm: LumiViewModel) {
    val item = state.items.firstOrNull { it.id == screen.id && it.isVideo }
    if (item == null || item.duration < 2000) {
        LaunchedEffect(Unit) {
            if (item != null) vm.say("Este vídeo es demasiado corto para recortarlo")
            vm.back()
        }
        return
    }
    val context = LocalContext.current
    var range by remember { mutableStateOf(0f..1f) }
    // El tirador que se movió por última vez decide qué fotograma se enseña.
    var focus by remember { mutableFloatStateOf(0f) }
    val startMs = (range.start * item.duration).toLong()
    val endMs = (range.endInclusive * item.duration).toLong()
    val frame by produceState<Bitmap?>(null, (focus * item.duration / 400).toLong()) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val shot = runCatching {
                retriever.setDataSource(context, item.uri)
                retriever.getScaledFrameAtTime((focus * item.duration).toLong() * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720, 720)
            }.getOrNull()
            runCatching { retriever.release() }
            shot ?: value
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false)
            Text("Recortar vídeo", style = HeadingStyle, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            PillButton("Guardar recorte", { vm.trim(item, startMs, endMs) }, enabled = endMs - startMs >= 1000 && (range.start > 0f || range.endInclusive < 1f))
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            val shot = frame
            if (shot != null) Image(shot.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else MediaThumb(item, 1024, Modifier.fillMaxSize())
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("De ${formatDuration(startMs)} a ${formatDuration(endMs)}", style = HeadingStyle.copy(fontSize = 20.sp))
            Text("El recorte dura ${formatDuration(endMs - startMs)} de ${formatDuration(item.duration)}", style = SmallStyle)
            RangeSlider(
                value = range,
                onValueChange = { next ->
                    focus = if (next.start != range.start) next.start else next.endInclusive
                    range = next
                },
                colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent, inactiveTrackColor = Lumi.Line),
            )
            Text(
                "Se guarda como vídeo nuevo, sin perder calidad. El principio puede quedar un instante antes de donde lo marcaste.",
                style = SmallStyle,
            )
        }
    }
}

// ---------- Carpetas ocultas del sistema ----------

@Composable
fun HiddenFoldersScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    var allowed by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    LaunchedEffect(allowed) { if (allowed && state.hiddenFolders == null) vm.scanHiddenFolders() }
    val folders = state.hiddenFolders

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Carpetas ocultas", if (folders == null) "" else countText(folders.size, "carpeta", "carpetas"), onBack = { vm.back() })
        when {
            !allowed -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Hace falta un permiso especial", style = HeadingStyle.copy(fontSize = 20.sp))
                Text(
                    "Algunas apps guardan fotos y vídeos en carpetas que Android esconde a las galerías. Para verlas, Lumi necesita " +
                        "el acceso a todos los archivos, que se concede en los ajustes del sistema. Sigue sin salir nada del teléfono.",
                    style = SmallStyle.copy(fontSize = 14.sp),
                )
                PillButton("Abrir los ajustes del permiso", actions.allFiles, Modifier.fillMaxWidth())
                PillButton("Ya lo he concedido", { allowed = Environment.isExternalStorageManager() }, Modifier.fillMaxWidth(), primary = false)
            }
            state.scanningHidden || folders == null -> EmptyMessage("Buscando…", "Lumi está recorriendo las carpetas del teléfono.")
            folders.isEmpty() -> EmptyMessage("No hay nada escondido", "Ninguna carpeta oculta tiene fotos ni vídeos.")
            else -> LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        "Se pueden ver, pero no borrar ni mover desde aquí.",
                        style = SmallStyle,
                        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    )
                }
                items(folders, key = { it.path }) { folder ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Lumi.Surface)
                            .clickable { vm.open(Screen.Items(folder.name, Source.Hidden(folder.path))) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaThumb(folder.items.first(), 160, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.name, style = HeadingStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(folder.path.substringAfter("/0/"), style = SmallStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${folder.items.size}", style = LabelStyle, color = Lumi.Accent)
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
}
