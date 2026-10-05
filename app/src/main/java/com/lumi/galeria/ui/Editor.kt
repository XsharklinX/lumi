package com.lumi.galeria.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lado mayor con el que se edita y se guarda; por encima, la memoria no alcanza en muchos teléfonos. */
private const val MAX_SIDE = 4096

private enum class Aspect(val label: String, val ratio: Float?) {
    FREE("Libre", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_FOUR("3:4", 3f / 4f),
    THREE_TWO("3:2", 3f / 2f),
    WIDE("16:9", 16f / 9f),
    TALL("9:16", 9f / 16f),
}

private enum class Grab { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private val FULL = Rect(0f, 0f, 1f, 1f)

/** Ampliación mínima para que una imagen girada [degrees] siga cubriendo su marco de [w] x [h]. */
private fun coverScale(degrees: Float, w: Float, h: Float): Float {
    val a = Math.toRadians(abs(degrees).toDouble())
    return (cos(a) + maxOf(w / h, h / w) * sin(a)).toFloat()
}

@Composable
fun EditorScreen(screen: Screen.Editor, state: UiState, vm: LumiViewModel, actions: Actions) {
    val item = state.items.firstOrNull { it.id == screen.id }
    if (item == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longSide = maxOf(info.size.width, info.size.height)
                    if (longSide > MAX_SIDE) {
                        val k = MAX_SIDE.toFloat() / longSide
                        decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                    }
                }
            }.getOrNull()
        }
        failed = bitmap == null
    }

    // Giros de 90°, enderezado fino y recorte (en proporciones del marco ya girado).
    var quarter by remember { mutableIntStateOf(0) }
    var angle by remember { mutableFloatStateOf(0f) }
    var crop by remember { mutableStateOf(FULL) }
    var aspect by remember { mutableStateOf(Aspect.FREE) }
    // Luz y color: cada ajuste va de -1 a 1 y 0 deja la foto como está.
    var tab by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var look by remember { mutableStateOf(Look.NONE) }
    // Efectos: espejo, bordes oscurecidos, negros lavados y giro de tono.
    var flip by remember { mutableStateOf(false) }
    var vignette by remember { mutableFloatStateOf(0f) }
    var fade by remember { mutableFloatStateOf(0f) }
    var hue by remember { mutableFloatStateOf(0f) }
    // Mientras se mantiene pulsado "Ver original" se enseña la foto sin luz, color ni efectos.
    var comparing by remember { mutableStateOf(false) }
    var saveMenu by remember { mutableStateOf(false) }
    val tone = remember(brightness, contrast, saturation, warmth, look, fade, hue) {
        toneMatrix(brightness, contrast, saturation, warmth, look, fade, hue)
    }
    val toned = brightness != 0f || contrast != 0f || saturation != 0f || warmth != 0f || look != Look.NONE || fade != 0f || hue != 0f

    val source = bitmap
    val frameW = if (source == null) 1f else if (quarter % 2 == 0) source.width.toFloat() else source.height.toFloat()
    val frameH = if (source == null) 1f else if (quarter % 2 == 0) source.height.toFloat() else source.width.toFloat()

    /** Alto del recorte, en proporción del marco, que corresponde a un ancho [w] con la proporción elegida. */
    fun lockedHeight(w: Float, ratio: Float) = w * frameW / (frameH * ratio)

    fun applyAspect(next: Aspect) {
        aspect = next
        val ratio = next.ratio ?: return
        var w = 1f
        var h = lockedHeight(w, ratio)
        if (h > 1f) {
            w /= h
            h = 1f
        }
        crop = Rect((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2)
    }

    val changed = quarter != 0 || angle != 0f || crop != FULL || toned || flip || vignette != 0f

    fun save(replace: Boolean) {
        val bmp = source ?: return
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { render(bmp, quarter, angle, crop, if (toned) tone else null, flip, vignette) }.getOrNull()
            }
            when {
                result == null -> vm.say("No hay memoria suficiente para esta foto")
                // Tocar el original necesita un permiso del sistema; si se niega, no se guarda nada.
                replace -> actions.write(listOf(item)) { vm.replaceEdit(item, result) }
                else -> vm.saveEdit(item, result).join()
            }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false)
            Text("Editar", style = HeadingStyle, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            Box {
                PillButton(if (saving) "Guardando…" else "Guardar", { saveMenu = true }, enabled = changed && !saving && source != null)
                DropdownMenu(saveMenu, { saveMenu = false }, containerColor = Lumi.Surface) {
                    DropdownMenuItem({ Text("Guardar una copia") }, { saveMenu = false; save(replace = false) })
                    DropdownMenuItem({ Text("Reemplazar la original") }, { saveMenu = false; save(replace = true) })
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                failed -> EmptyMessage("No se puede editar esta foto", "El formato no se deja abrir.")
                source != null -> {
                    val image = remember(source) { source.asImageBitmap() }
                    var view by remember { mutableStateOf(IntSize.Zero) }
                    var grab by remember { mutableStateOf(Grab.NONE) }

                    fun frame(): Rect {
                        val pad = 28.dp.value * context.resources.displayMetrics.density
                        val k = minOf((view.width - 2 * pad) / frameW, (view.height - 2 * pad) / frameH)
                        val w = frameW * k
                        val h = frameH * k
                        return Rect(Offset((view.width - w) / 2, (view.height - h) / 2), Size(w, h))
                    }

                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { view = it }
                            .pointerInput(quarter, aspect) {
                                detectDragGestures(
                                    onDragStart = { point ->
                                        val f = frame()
                                        val box = Rect(
                                            f.left + crop.left * f.width, f.top + crop.top * f.height,
                                            f.left + crop.right * f.width, f.top + crop.bottom * f.height,
                                        )
                                        val reach = 36.dp.toPx()
                                        fun near(corner: Offset) = hypot(point.x - corner.x, point.y - corner.y) < reach
                                        grab = when {
                                            near(box.topLeft) -> Grab.TOP_LEFT
                                            near(box.topRight) -> Grab.TOP_RIGHT
                                            near(box.bottomLeft) -> Grab.BOTTOM_LEFT
                                            near(box.bottomRight) -> Grab.BOTTOM_RIGHT
                                            box.contains(point) -> Grab.MOVE
                                            else -> Grab.NONE
                                        }
                                    },
                                    onDragEnd = { grab = Grab.NONE },
                                    onDragCancel = { grab = Grab.NONE },
                                ) { change, drag ->
                                    change.consume()
                                    val f = frame()
                                    crop = dragCrop(crop, grab, drag.x / f.width, drag.y / f.height, aspect.ratio?.let { lockedHeight(1f, it) })
                                }
                            },
                    ) {
                        val f = frame()
                        val k = f.width / frameW
                        val drawnW = source.width * k
                        val drawnH = source.height * k
                        clipRect(f.left, f.top, f.right, f.bottom) {
                            withTransform({
                                rotate(quarter * 90f + angle, f.center)
                                scale(coverScale(angle, frameW, frameH), f.center)
                                if (flip) scale(-1f, 1f, f.center)
                            }) {
                                drawImage(
                                    image,
                                    dstOffset = IntOffset((f.center.x - drawnW / 2).roundToInt(), (f.center.y - drawnH / 2).roundToInt()),
                                    dstSize = IntSize(drawnW.roundToInt(), drawnH.roundToInt()),
                                    colorFilter = if (toned && !comparing) ColorFilter.colorMatrix(ColorMatrix(tone)) else null,
                                )
                            }
                        }
                        val box = Rect(
                            f.left + crop.left * f.width, f.top + crop.top * f.height,
                            f.left + crop.right * f.width, f.top + crop.bottom * f.height,
                        )
                        if (vignette > 0f && !comparing) {
                            drawRect(
                                Brush.radialGradient(
                                    0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f * vignette),
                                    center = box.center, radius = hypot(box.width, box.height) / 2,
                                ),
                                box.topLeft, box.size,
                            )
                        }
                        val shade = Color.Black.copy(alpha = 0.6f)
                        drawRect(shade, f.topLeft, Size(f.width, box.top - f.top))
                        drawRect(shade, Offset(f.left, box.bottom), Size(f.width, f.bottom - box.bottom))
                        drawRect(shade, Offset(f.left, box.top), Size(box.left - f.left, box.height))
                        drawRect(shade, Offset(box.right, box.top), Size(f.right - box.right, box.height))
                        // Regla de los tercios.
                        val line = Color.White.copy(alpha = 0.35f)
                        for (i in 1..2) {
                            drawLine(line, Offset(box.left + box.width * i / 3, box.top), Offset(box.left + box.width * i / 3, box.bottom))
                            drawLine(line, Offset(box.left, box.top + box.height * i / 3), Offset(box.right, box.top + box.height * i / 3))
                        }
                        drawRect(Color.White, box.topLeft, box.size, style = Stroke(1.5.dp.toPx()))
                        listOf(box.topLeft, box.topRight, box.bottomLeft, box.bottomRight).forEach {
                            drawCircle(Lumi.Accent, 8.dp.toPx(), it)
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> {
                    Tuner("Enderezar", angle, -30f..30f, "%.1f°") { angle = if (abs(it) < 0.4f) 0f else it }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Girar 90°", false) {
                            quarter = (quarter + 1) % 4
                            crop = FULL
                            aspect = Aspect.FREE
                        }
                        Chip("Espejo", flip) { flip = !flip }
                        Aspect.entries.forEach { option -> Chip(option.label, aspect == option) { applyAspect(option) } }
                    }
                }
                1 -> {
                    Tuner("Brillo", brightness * 100, -100f..100f, "%.0f") { brightness = snap(it) / 100 }
                    Tuner("Contraste", contrast * 100, -100f..100f, "%.0f") { contrast = snap(it) / 100 }
                    Tuner("Color", saturation * 100, -100f..100f, "%.0f") { saturation = snap(it) / 100 }
                    Tuner("Calidez", warmth * 100, -100f..100f, "%.0f") { warmth = snap(it) / 100 }
                }
                2 -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Look.entries.forEach { option -> Chip(option.label, look == option) { look = option } }
                }
                else -> {
                    Tuner("Viñeta", vignette * 100, 0f..100f, "%.0f") { vignette = it / 100 }
                    Tuner("Desvanecer", fade * 100, 0f..100f, "%.0f") { fade = it / 100 }
                    Tuner("Tono", hue * 100, -100f..100f, "%.0f") { hue = snap(it) / 100 }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Recortar", "Luz y color", "Filtros", "Efectos").forEachIndexed { i, label -> Chip(label, tab == i) { tab = i } }
                Text(
                    "Ver original",
                    style = LabelStyle,
                    color = if (comparing) Lumi.OnAccent else Lumi.Accent,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (comparing) Lumi.Accent else Color.Transparent)
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                comparing = true
                                tryAwaitRelease()
                                comparing = false
                            })
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
                Text(
                    "Deshacer todo",
                    style = LabelStyle,
                    color = if (changed) Lumi.Accent else Lumi.Muted,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = changed) {
                        quarter = 0
                        angle = 0f
                        crop = FULL
                        aspect = Aspect.FREE
                        brightness = 0f
                        contrast = 0f
                        saturation = 0f
                        warmth = 0f
                        look = Look.NONE
                        flip = false
                        vignette = 0f
                        fade = 0f
                        hue = 0f
                    }.padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
        }
    }
}

/** Cerca del centro el control se queda en cero, para poder volver al valor original sin afinar. */
private fun snap(value: Float): Float = if (abs(value) < 4f) 0f else value

@Composable
private fun Tuner(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = LabelStyle, modifier = Modifier.width(78.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent, inactiveTrackColor = Lumi.Line),
            modifier = Modifier.weight(1f).height(34.dp),
        )
        Text(String.format(Locale("es", "ES"), format, value), style = SmallStyle, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
}

private enum class Look(val label: String) {
    NONE("Original"), VIVID("Vívido"), WARM("Cálido"), COOL("Frío"), FADED("Suave"), MONO("Blanco y negro"), SEPIA("Sepia"),
}

/** Matriz de color 4x5 que resume los ajustes y el filtro; sirve igual para la vista previa y para guardar. */
private fun toneMatrix(brightness: Float, contrast: Float, saturation: Float, warmth: Float, look: Look, fade: Float, hue: Float): FloatArray {
    fun levels(scale: Float, shift: Float) = android.graphics.ColorMatrix(
        floatArrayOf(scale, 0f, 0f, 0f, shift, 0f, scale, 0f, 0f, shift, 0f, 0f, scale, 0f, shift, 0f, 0f, 0f, 1f, 0f),
    )

    fun tint(red: Float, blue: Float) = android.graphics.ColorMatrix(
        floatArrayOf(red, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, blue, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
    )

    val matrix = android.graphics.ColorMatrix()
    matrix.setSaturation(1f + saturation)
    val scale = 1f + contrast * 0.6f
    // El contraste gira alrededor del gris medio; el brillo desplaza todo.
    matrix.postConcat(levels(scale, (0.5f - 0.5f * scale) * 255f + brightness * 80f))
    matrix.postConcat(tint(1f + warmth * 0.16f, 1f - warmth * 0.16f))
    // Desvanecer: los negros suben y la imagen pierde contraste, como una foto antigua.
    if (fade > 0f) matrix.postConcat(levels(1f - 0.3f * fade, 50f * fade))
    if (hue != 0f) {
        // Giro de tono conservando la luminosidad (hasta medio círculo de color a cada lado).
        val angle = Math.toRadians(hue * 90.0)
        val c = kotlin.math.cos(angle).toFloat()
        val n = kotlin.math.sin(angle).toFloat()
        val r = 0.213f
        val g = 0.715f
        val b = 0.072f
        matrix.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    r + c * (1 - r) - n * r, g - c * g - n * g, b - c * b + n * (1 - b), 0f, 0f,
                    r - c * r + n * 0.143f, g + c * (1 - g) + n * 0.140f, b - c * b - n * 0.283f, 0f, 0f,
                    r - c * r - n * (1 - r), g - c * g + n * g, b + c * (1 - b) + n * b, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    when (look) {
        Look.NONE -> Unit
        Look.VIVID -> {
            matrix.postConcat(android.graphics.ColorMatrix().apply { setSaturation(1.35f) })
            matrix.postConcat(levels(1.1f, -12f))
        }
        Look.WARM -> matrix.postConcat(tint(1.12f, 0.88f))
        Look.COOL -> matrix.postConcat(tint(0.9f, 1.1f))
        Look.FADED -> {
            matrix.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0.7f) })
            matrix.postConcat(levels(0.88f, 24f))
        }
        Look.MONO -> matrix.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        Look.SEPIA -> {
            matrix.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0f) })
            matrix.postConcat(
                android.graphics.ColorMatrix(
                    floatArrayOf(1.07f, 0f, 0f, 0f, 0f, 0f, 0.95f, 0f, 0f, 0f, 0f, 0f, 0.78f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
                ),
            )
        }
    }
    return matrix.array.copyOf()
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = LabelStyle,
        color = if (on) Lumi.OnAccent else Lumi.Ink,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) Lumi.Accent else Lumi.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

private const val MIN_CROP = 0.12f

/**
 * Nuevo recorte tras mover [grab] una distancia ([dx], [dy]) en proporción del marco.
 * [heightPerWidth] fija la proporción: alto del recorte por cada unidad de ancho.
 */
private fun dragCrop(crop: Rect, grab: Grab, dx: Float, dy: Float, heightPerWidth: Float?): Rect {
    if (grab == Grab.NONE) return crop
    if (grab == Grab.MOVE) {
        val x = (crop.left + dx).coerceIn(0f, 1f - crop.width)
        val y = (crop.top + dy).coerceIn(0f, 1f - crop.height)
        return Rect(x, y, x + crop.width, y + crop.height)
    }
    val left = grab == Grab.TOP_LEFT || grab == Grab.BOTTOM_LEFT
    val top = grab == Grab.TOP_LEFT || grab == Grab.TOP_RIGHT
    // La esquina contraria no se mueve.
    val anchorX = if (left) crop.right else crop.left
    val anchorY = if (top) crop.bottom else crop.top
    val roomX = if (left) anchorX else 1f - anchorX
    val roomY = if (top) anchorY else 1f - anchorY
    var w = (crop.width + if (left) -dx else dx).coerceIn(MIN_CROP, roomX)
    var h = (crop.height + if (top) -dy else dy).coerceIn(MIN_CROP, roomY)
    if (heightPerWidth != null) {
        w = minOf(w, roomY / heightPerWidth)
        h = w * heightPerWidth
    }
    val x0 = if (left) anchorX - w else anchorX
    val y0 = if (top) anchorY - h else anchorY
    return Rect(x0, y0, x0 + w, y0 + h)
}

/** Aplica espejo, giro, enderezado, recorte, color y viñeta sobre la imagen a tamaño real. */
private fun render(source: Bitmap, quarter: Int, angle: Float, crop: Rect, tone: FloatArray?, flip: Boolean, vignette: Float): Bitmap {
    val base = if (quarter == 0 && !flip) source else {
        val turn = Matrix().apply {
            if (flip) postScale(-1f, 1f)
            postRotate(90f * quarter)
        }
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, turn, true)
    }
    val w = base.width.toFloat()
    val h = base.height.toFloat()
    val out = Bitmap.createBitmap(
        (crop.width * w).roundToInt().coerceAtLeast(1),
        (crop.height * h).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    android.graphics.Canvas(out).apply {
        save()
        translate(-crop.left * w, -crop.top * h)
        rotate(angle, w / 2, h / 2)
        val cover = coverScale(angle, w, h)
        scale(cover, cover, w / 2, h / 2)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (tone != null) paint.colorFilter = android.graphics.ColorMatrixColorFilter(tone)
        drawBitmap(base, 0f, 0f, paint)
        restore()
        if (vignette > 0f) {
            val dark = android.graphics.Color.argb((0.8f * vignette * 255).toInt(), 0, 0, 0)
            val shade = Paint().apply {
                shader = android.graphics.RadialGradient(
                    out.width / 2f, out.height / 2f, hypot(out.width.toFloat(), out.height.toFloat()) / 2,
                    intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, dark),
                    floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP,
                )
            }
            drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), shade)
        }
    }
    if (base !== source) base.recycle()
    return out
}
