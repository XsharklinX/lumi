@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.lumi.galeria.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Tasks
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
    FOUR_FIVE("4:5", 4f / 5f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_FOUR("3:4", 3f / 4f),
    THREE_TWO("3:2", 3f / 2f),
    WIDE("16:9", 16f / 9f),
    TALL("9:16", 9f / 16f),
}

private enum class Grab { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

internal val FULL = Rect(0f, 0f, 1f, 1f)

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
    // La foto tal como la abre el sistema; en un RAW, «bitmap» puede ser el revelado de más abajo.
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        baseBitmap = withContext(Dispatchers.IO) {
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
        bitmap = baseBitmap
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
    // Cuánto se nota el filtro, de 0 a 1.
    var lookStrength by remember { mutableFloatStateOf(1f) }
    // Efectos: espejo, bordes oscurecidos, negros lavados y giro de tono.
    var flip by remember { mutableStateOf(false) }
    var vignette by remember { mutableFloatStateOf(0f) }
    var fade by remember { mutableFloatStateOf(0f) }
    var hue by remember { mutableFloatStateOf(0f) }
    // Antes y después: null si no se compara; si no, por dónde va la barra, de 0 (izquierda) a 1.
    // A su izquierda se ve la foto original y a su derecha, con los cambios.
    var split by remember { mutableStateOf<Float?>(null) }
    var saveMenu by remember { mutableStateOf(false) }
    // Retoque local: borrar, zonas, caras, marco y revelado RAW.
    var retouch by remember { mutableStateOf(com.lumi.galeria.data.Retouch()) }
    var zoneIndex by remember { mutableIntStateOf(0) }
    var brush by remember { mutableFloatStateOf(0.03f) }
    var faceShapes by remember { mutableStateOf<List<com.lumi.galeria.data.FaceShape>?>(null) }
    var faceStatus by remember { mutableStateOf<String?>(null) }
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rawHeadroom by remember { mutableStateOf<Boolean?>(null) }
    var history by remember { mutableStateOf(emptyList<HistoryStep>()) }
    var historyAt by remember { mutableIntStateOf(0) }
    var historyOpen by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    val tone = remember(brightness, contrast, saturation, warmth, look, fade, hue, lookStrength) {
        toneMatrix(brightness, contrast, saturation, warmth, look, fade, hue, lookStrength)
    }
    val toned = brightness != 0f || contrast != 0f || saturation != 0f || warmth != 0f || look != Look.NONE || fade != 0f || hue != 0f

    val source = bitmap
    // Perspectiva: cuánto se enderezan las líneas que convergen, en vertical y en horizontal.
    var keyV by remember { mutableFloatStateOf(0f) }
    var keyH by remember { mutableFloatStateOf(0f) }
    var perspective by remember { mutableStateOf(false) }
    var framing by remember { mutableStateOf(false) }
    // Mientras se ajusta, se trabaja sobre una copia reducida: la foto completa solo al guardar.
    val preview = remember(source) {
        source?.let { s ->
            val longSide = maxOf(s.width, s.height)
            if (longSide <= 2048) s else Bitmap.createScaledBitmap(s, s.width * 2048 / longSide, s.height * 2048 / longSide, true)
        }
    }
    // Curvas, tonos por gama, sombras y luces, nitidez, ruido y grano: se calculan píxel a píxel.
    var pro by remember { mutableStateOf(com.lumi.galeria.data.Develop()) }
    // Primero el retoque local (borrar, zonas, caras), sobre la foto sin girar ni recortar.
    val worked by produceState<Bitmap?>(null, preview, retouch.erase, retouch.zones, retouch.faces, faceShapes) {
        val p = preview ?: return@produceState
        kotlinx.coroutines.delay(60)
        value = withContext(Dispatchers.Default) {
            var q = com.lumi.galeria.data.applyErase(p, retouch.erase)
            q = com.lumi.galeria.data.applyZones(q, retouch.zones)
            com.lumi.galeria.data.applyFaces(q, faceShapes.orEmpty(), retouch.faces)
        }
    }
    val shaped by produceState<Bitmap?>(null, worked, keyV, keyH, pro) {
        val p = worked ?: return@produceState
        // Un respiro: mientras se arrastra un control no se recalcula cada milímetro.
        kotlinx.coroutines.delay(60)
        value = withContext(Dispatchers.Default) {
            val straight = if (keyV == 0f && keyH == 0f) p else keystone(p, keyV, keyH)
            com.lumi.galeria.data.develop(straight, pro)
        }
    }
    val histogram = remember(preview) { preview?.let { com.lumi.galeria.data.histogram(it) } }
    val prefsLooks = remember { context.getSharedPreferences("editor", android.content.Context.MODE_PRIVATE) }
    var savedLooks by remember { mutableStateOf(readLooks(prefsLooks)) }
    var naming by remember { mutableStateOf(false) }
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

    val changed = quarter != 0 || angle != 0f || crop != FULL || toned || flip || vignette != 0f || keyV != 0f || keyH != 0f || !pro.isIdentity ||
        retouch != com.lumi.galeria.data.Retouch()

    /** Los ajustes de color de ahora, para guardarlos como filtro propio. */
    fun currentLook(): org.json.JSONObject = org.json.JSONObject().apply {
        put("brillo", brightness.toDouble()); put("contraste", contrast.toDouble()); put("color", saturation.toDouble())
        put("calidez", warmth.toDouble()); put("filtro", look.name); put("intensidad", lookStrength.toDouble())
        put("desvanecer", fade.toDouble()); put("tono", hue.toDouble()); put("vineta", vignette.toDouble())
        put("pro", developToJson(pro))
    }

    fun applyLook(values: org.json.JSONObject) {
        brightness = values.optDouble("brillo", 0.0).toFloat()
        contrast = values.optDouble("contraste", 0.0).toFloat()
        saturation = values.optDouble("color", 0.0).toFloat()
        warmth = values.optDouble("calidez", 0.0).toFloat()
        look = runCatching { Look.valueOf(values.optString("filtro", "NONE")) }.getOrDefault(Look.NONE)
        lookStrength = values.optDouble("intensidad", 1.0).toFloat()
        fade = values.optDouble("desvanecer", 0.0).toFloat()
        hue = values.optDouble("tono", 0.0).toFloat()
        vignette = values.optDouble("vineta", 0.0).toFloat()
        pro = values.optJSONObject("pro")?.let { developFromJson(it) } ?: com.lumi.galeria.data.Develop()
    }

    /** Todo lo que hay hecho ahora mismo, en texto: sirve para el historial y para retomar la edición. */
    fun snapshot(): String = currentLook().apply {
        put(
            "geo",
            org.json.JSONObject().put("q", quarter).put("a", angle.toDouble()).put("l", crop.left.toDouble()).put("t", crop.top.toDouble())
                .put("r", crop.right.toDouble()).put("b", crop.bottom.toDouble()).put("asp", aspect.name).put("flip", flip)
                .put("kv", keyV.toDouble()).put("kh", keyH.toDouble()),
        )
        put("retoque", com.lumi.galeria.data.retouchToJson(retouch))
    }.toString()

    fun restore(json: String) {
        val o = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return
        applyLook(o)
        val g = o.optJSONObject("geo")
        quarter = g?.optInt("q", 0) ?: 0
        angle = g?.optDouble("a", 0.0)?.toFloat() ?: 0f
        crop = if (g == null) FULL else Rect(g.optDouble("l", 0.0).toFloat(), g.optDouble("t", 0.0).toFloat(), g.optDouble("r", 1.0).toFloat(), g.optDouble("b", 1.0).toFloat())
        aspect = runCatching { Aspect.valueOf(g?.optString("asp") ?: "FREE") }.getOrDefault(Aspect.FREE)
        flip = g?.optBoolean("flip", false) ?: false
        keyV = g?.optDouble("kv", 0.0)?.toFloat() ?: 0f
        keyH = g?.optDouble("kh", 0.0)?.toFloat() ?: 0f
        retouch = com.lumi.galeria.data.retouchFromJson(o.optJSONObject("retoque"))
        zoneIndex = zoneIndex.coerceAtMost((retouch.zones.size - 1).coerceAtLeast(0))
    }

    // Al abrir: el historial empieza con la foto original y, si quedó una edición sin guardar, se retoma.
    LaunchedEffect(baseBitmap != null) {
        if (baseBitmap == null) return@LaunchedEffect
        val blank = snapshot()
        history = listOf(HistoryStep("Foto original", blank))
        historyAt = 0
        val draft = prefsLooks.getString("borrador_${item.id}", null)
        if (draft != null && draft != blank) {
            restore(draft)
            vm.say("Retomada tu edición anterior")
        }
        started = true
    }
    LaunchedEffect(tab) {
        when (tab) {
            7 -> vm.discover("editor_zonas")
            8 -> vm.discover("editor_borrar")
            9 -> vm.discover("editor_caras")
            10 -> vm.discover("editor_marcos")
            11 -> vm.discover("raw")
        }
    }
    LaunchedEffect(historyOpen) { if (historyOpen) vm.discover("editor_historial") }
    val stateKey = listOf(quarter, angle, crop, aspect, brightness, contrast, saturation, warmth, look, lookStrength, flip, vignette, fade, hue, keyV, keyH, pro, retouch)
    LaunchedEffect(stateKey, started) {
        if (!started) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        val now = snapshot()
        val at = history.getOrNull(historyAt)
        if (at?.json == now) return@LaunchedEffect
        val label = if (history.size == 1 && prefsLooks.getString("borrador_${item.id}", null) != null) "Edición retomada" else describeChange(at?.json, now)
        history = (history + HistoryStep(label, now)).let { if (it.size > 60) listOf(it.first()) + it.drop(2) else it }
        historyAt = history.lastIndex
        if (now == history.first().json) prefsLooks.edit().remove("borrador_${item.id}").apply() else saveDraft(prefsLooks, item.id, now)
    }

    // El RAW se abre aparte, en luz lineal, solo cuando hace falta.
    LaunchedEffect(item.id, tab, retouch.raw) {
        if (item.format == "DNG" && rawBitmap == null && baseBitmap != null && (tab == 11 || !retouch.raw.isZero)) {
            rawBitmap = withContext(Dispatchers.IO) { com.lumi.galeria.data.decodeRaw(context, item.uri, 3072) }
            rawHeadroom = rawBitmap?.let { com.lumi.galeria.data.hasRawHeadroom(it) } ?: false
        }
    }
    LaunchedEffect(baseBitmap, rawBitmap, retouch.raw) {
        val base = baseBitmap ?: return@LaunchedEffect
        val raw = rawBitmap
        if (retouch.raw.isZero || raw == null) {
            if (bitmap !== base) bitmap = base
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(80)
        bitmap = withContext(Dispatchers.Default) { runCatching { com.lumi.galeria.data.developRaw(raw, retouch.raw) }.getOrNull() } ?: base
    }
    // Las caras se buscan cuando se entra en esa pestaña, o si ya hay un retoque guardado.
    LaunchedEffect(tab, preview) {
        val p = preview ?: return@LaunchedEffect
        if ((tab == 9 || !retouch.faces.isIdentity) && faceShapes == null) {
            faceStatus = "Buscando caras…"
            val found = com.lumi.galeria.data.detectFaceShapes(p)
            if (found.isEmpty()) {
                faceStatus = "No se ve ninguna cara en esta foto (o Google Play aún está descargando el detector)."
            } else {
                faceShapes = found
                faceStatus = if (found.size == 1) "Una cara encontrada" else "${found.size} caras encontradas"
            }
        }
    }
    val framed by produceState<Bitmap?>(null, tab, shaped, quarter, angle, crop, tone, toned, flip, vignette, retouch.frame) {
        val s = shaped ?: return@produceState
        if (tab != 10) return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching { com.lumi.galeria.data.applyFrame(render(s, quarter, angle, crop, if (toned) tone else null, flip, vignette), retouch.frame) }.getOrNull()
        }
    }
    var copiedLook by remember { mutableStateOf(prefsLooks.getString("copiados", null)) }

    /** Propone un recorte centrado en lo principal de la foto, con un poco de aire alrededor. */
    fun autoFrame() {
        val bmp = preview ?: return
        framing = true
        scope.launch {
            val found = withContext(Dispatchers.Default) { runCatching { subjectBox(bmp) }.getOrNull() }
            framing = false
            if (found == null) {
                vm.say("No se encontró nada principal que encuadrar")
                return@launch
            }
            // De la foto original al marco tal como está girado.
            val r = when (quarter) {
                1 -> Rect(1 - found.bottom, found.left, 1 - found.top, found.right)
                2 -> Rect(1 - found.right, 1 - found.bottom, 1 - found.left, 1 - found.top)
                3 -> Rect(found.top, 1 - found.right, found.bottom, 1 - found.left)
                else -> found
            }.let { if (flip) Rect(1 - it.right, it.top, 1 - it.left, it.bottom) else it }
            var left = r.left - r.width * 0.15f
            var right = r.right + r.width * 0.15f
            var top = r.top - r.height * 0.15f
            var bottom = r.bottom + r.height * 0.15f
            // Con proporción fija se amplía el lado que se queda corto.
            aspect.ratio?.let { ratio ->
                val wantH = lockedHeight(right - left, ratio)
                if (wantH > bottom - top) {
                    val extra = (wantH - (bottom - top)) / 2
                    top -= extra
                    bottom += extra
                } else {
                    val extra = ((bottom - top) / lockedHeight(1f, ratio) - (right - left)) / 2
                    left -= extra
                    right += extra
                }
            }
            // Lo que se sale del marco se mete dentro.
            fun fit(a: Float, b: Float): Pair<Float, Float> {
                val size = minOf(b - a, 1f)
                val start = a.coerceIn(0f, 1f - size)
                return start to start + size
            }
            val (l, rr) = fit(left, right)
            val (t, bb) = fit(top, bottom)
            crop = Rect(l, t, rr, bb)
        }
    }

    fun save(replace: Boolean) {
        val bmp = source ?: return
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    var base = com.lumi.galeria.data.applyErase(bmp, retouch.erase)
                    base = com.lumi.galeria.data.applyZones(base, retouch.zones)
                    if (!retouch.faces.isIdentity) {
                        val shapes = faceShapes ?: com.lumi.galeria.data.detectFaceShapes(base)
                        base = com.lumi.galeria.data.applyFaces(base, shapes, retouch.faces)
                    }
                    val straight = if (keyV != 0f || keyH != 0f) keystone(base, keyV, keyH) else base
                    com.lumi.galeria.data.applyFrame(
                        render(com.lumi.galeria.data.develop(straight, pro), quarter, angle, crop, if (toned) tone else null, flip, vignette),
                        retouch.frame,
                    )
                }.getOrNull()
            }
            when {
                result == null -> vm.say("No hay memoria suficiente para esta foto")
                // Tocar el original necesita un permiso del sistema; si se niega, no se guarda nada.
                replace -> { prefsLooks.edit().remove("borrador_${item.id}").apply(); actions.write(listOf(item)) { vm.replaceEdit(item, result) } }
                else -> { prefsLooks.edit().remove("borrador_${item.id}").apply(); vm.saveEdit(item, result).join() }
            }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Editar", style = HeadingStyle, modifier = Modifier.weight(1f))
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                failed -> EmptyMessage("No se puede editar esta foto", "El formato no se deja abrir.")
                source != null && (tab == 7 || tab == 8) -> {
                    val zone = retouch.zones.getOrNull(zoneIndex)
                    val mode = if (tab == 8) PaintMode.ERASE else when (zone?.kind) {
                        com.lumi.galeria.data.ZoneKind.GRADIENT -> PaintMode.GRADIENT
                        com.lumi.galeria.data.ZoneKind.CIRCLE -> PaintMode.CIRCLE
                        else -> PaintMode.BRUSH
                    }
                    PaintStage(
                        image = worked ?: source, mode = mode, radius = brush,
                        shown = if (tab == 7 && zone?.kind == com.lumi.galeria.data.ZoneKind.BRUSH) zone.strokes else emptyList(),
                        zone = if (tab == 7) zone else null,
                        onStroke = { stroke ->
                            if (tab == 8) retouch = retouch.copy(erase = retouch.erase + stroke)
                            else if (zone != null && zone.kind == com.lumi.galeria.data.ZoneKind.BRUSH) {
                                retouch = retouch.copy(zones = retouch.zones.mapIndexed { i, z -> if (i == zoneIndex) z.copy(strokes = z.strokes + stroke) else z })
                            }
                        },
                        onLine = { a, b ->
                            if (zone != null && zone.kind != com.lumi.galeria.data.ZoneKind.BRUSH) {
                                retouch = retouch.copy(zones = retouch.zones.mapIndexed { i, z -> if (i == zoneIndex) z.copy(a = a, b = b) else z })
                            }
                        },
                    )
                }
                source != null && tab == 10 -> FrameStage(framed)
                source != null -> {
                    val image = remember(shaped, source) { (shaped ?: source).asImageBitmap() }
                    // Para el «antes»: sin curvas, tonos ni detalle.
                    val original = remember(preview) { preview?.asImageBitmap() }
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
                                        // Mientras se compara, arrastrar mueve la barra y no el recorte.
                                        if (split != null) {
                                            grab = Grab.NONE
                                            return@detectDragGestures
                                        }
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
                                    split?.let { at ->
                                        split = (at + drag.x / f.width).coerceIn(0f, 1f)
                                        return@detectDragGestures
                                    }
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
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                    colorFilter = if (toned) ColorFilter.colorMatrix(ColorMatrix(tone)) else null,
                                )
                            }
                        }
                        // La mitad "antes": la misma foto, sin luz ni color, hasta la barra.
                        val cut = split?.let { f.left + f.width * it }
                        if (cut != null) {
                            clipRect(f.left, f.top, cut, f.bottom) {
                                withTransform({
                                    rotate(quarter * 90f + angle, f.center)
                                    scale(coverScale(angle, frameW, frameH), f.center)
                                    if (flip) scale(-1f, 1f, f.center)
                                }) {
                                    drawImage(
                                        original ?: image,
                                        dstOffset = IntOffset((f.center.x - drawnW / 2).roundToInt(), (f.center.y - drawnH / 2).roundToInt()),
                                        dstSize = IntSize(drawnW.roundToInt(), drawnH.roundToInt()),
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                    )
                                }
                            }
                        }
                        val box = Rect(
                            f.left + crop.left * f.width, f.top + crop.top * f.height,
                            f.left + crop.right * f.width, f.top + crop.bottom * f.height,
                        )
                        if (vignette > 0f) {
                            // Al comparar, la viñeta solo oscurece el lado "después".
                            clipRect(maxOf(cut ?: box.left, box.left), box.top, box.right, box.bottom) {
                                drawRect(
                                    Brush.radialGradient(
                                        0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f * vignette),
                                        center = box.center, radius = hypot(box.width, box.height) / 2,
                                    ),
                                    box.topLeft, box.size,
                                )
                            }
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
                        // Lupa: la esquina que se arrastra, ampliada en un círculo, al lado contrario del dedo.
                        val corner = when (grab) {
                            Grab.TOP_LEFT -> box.topLeft
                            Grab.TOP_RIGHT -> box.topRight
                            Grab.BOTTOM_LEFT -> box.bottomLeft
                            Grab.BOTTOM_RIGHT -> box.bottomRight
                            else -> null
                        }
                        if (corner != null) {
                            val r = 54.dp.toPx()
                            val margin = r + 10.dp.toPx()
                            val at = Offset(if (corner.x < size.width / 2) size.width - margin else margin, margin)
                            clipPath(Path().apply { addOval(Rect(at, r)) }) {
                                drawRect(Color.Black, Offset(at.x - r, at.y - r), Size(2 * r, 2 * r))
                                withTransform({
                                    translate(at.x - corner.x, at.y - corner.y)
                                    scale(3f, 3f, corner)
                                }) {
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
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                                colorFilter = if (toned) ColorFilter.colorMatrix(ColorMatrix(tone)) else null,
                                            )
                                        }
                                    }
                                    drawRect(Color.White, box.topLeft, box.size, style = Stroke(0.6.dp.toPx()))
                                }
                            }
                            drawCircle(Color.White, r, at, style = Stroke(2.dp.toPx()))
                        }
                        if (cut != null) {
                            drawLine(Color.White, Offset(cut, f.top), Offset(cut, f.bottom), 2.dp.toPx())
                            drawCircle(Color.White, 13.dp.toPx(), Offset(cut, f.center.y))
                            drawCircle(Lumi.Accent, 5.dp.toPx(), Offset(cut, f.center.y))
                        }
                    }
                    if (split != null) {
                        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 36.dp, vertical = 6.dp)) {
                            Text("Antes", style = LabelStyle, color = Color.White, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp))
                            Spacer(Modifier.weight(1f))
                            Text("Después", style = LabelStyle, color = Color.White, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> {
                    Dial(angle) { angle = it }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        Aspect.entries.forEach { option -> ShapeChip(option, aspect == option) { applyAspect(option) } }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoChip(if (framing) "Buscando…" else "Encuadre auto", false) { if (!framing) autoFrame() }
                        PhotoChip("Perspectiva", perspective || keyV != 0f || keyH != 0f) { perspective = !perspective }
                        PhotoChip("Girar 90°", false) {
                            quarter = (quarter + 1) % 4
                            crop = FULL
                            aspect = Aspect.FREE
                        }
                        PhotoChip("Espejo", flip) { flip = !flip }
                    }
                    if (perspective) {
                        PhotoTuner("Vertical", keyV * 100, -100f..100f, "%.0f") { keyV = snap(it) / 100 }
                        PhotoTuner("Horizontal", keyH * 100, -100f..100f, "%.0f") { keyH = snap(it) / 100 }
                    }
                }
                1 -> {
                    PhotoTuner("Brillo", brightness * 100, -100f..100f, "%.0f") { brightness = snap(it) / 100 }
                    PhotoTuner("Contraste", contrast * 100, -100f..100f, "%.0f") { contrast = snap(it) / 100 }
                    PhotoTuner("Color", saturation * 100, -100f..100f, "%.0f") { saturation = snap(it) / 100 }
                    PhotoTuner("Calidez", warmth * 100, -100f..100f, "%.0f") { warmth = snap(it) / 100 }
                    PhotoTuner("Sombras", pro.shadows * 100, -100f..100f, "%.0f") { pro = pro.copy(shadows = snap(it) / 100) }
                    PhotoTuner("Luces", pro.highlights * 100, -100f..100f, "%.0f") { pro = pro.copy(highlights = snap(it) / 100) }
                }
                2 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Cada filtro, aplicado a una miniatura de la propia foto.
                    val thumb = remember(preview) {
                        preview?.let { s ->
                            val k = 160f / maxOf(s.width, s.height)
                            Bitmap.createScaledBitmap(s, (s.width * k).toInt().coerceAtLeast(1), (s.height * k).toInt().coerceAtLeast(1), true).asImageBitmap()
                        }
                    }
                    // Los filtros propios, primero. Mantener pulsado uno lo borra.
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoChip("Guardar mis ajustes como filtro", false) { naming = true }
                        savedLooks.forEach { saved ->
                            val savedMatrix = remember(saved) { toneFromValues(saved.values) }
                            Column(
                                Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(
                                    onClick = { applyLook(saved.values) },
                                    onLongClick = {
                                        savedLooks = savedLooks.filter { it !== saved }
                                        writeLooks(prefsLooks, savedLooks)
                                        vm.say("Filtro «${saved.name}» borrado")
                                    },
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (thumb != null) {
                                    Image(
                                        thumb, null, contentScale = ContentScale.Crop,
                                        colorFilter = ColorFilter.colorMatrix(ColorMatrix(savedMatrix)),
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Lumi.Accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                                    )
                                }
                                Text(saved.name, style = SmallStyle, color = Lumi.Accent, maxLines = 1)
                            }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Look.entries.forEach { option ->
                            val on = look == option
                            Column(
                                Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                    look = option
                                    lookStrength = 1f
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                val matrix = remember(option) { toneMatrix(0f, 0f, 0f, 0f, option, 0f, 0f) }
                                if (thumb != null) {
                                    Image(
                                        thumb, null,
                                        contentScale = ContentScale.Crop,
                                        colorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix)),
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                                            .then(if (on) Modifier.border(2.5.dp, Lumi.Accent, RoundedCornerShape(12.dp)) else Modifier),
                                    )
                                }
                                Text(option.label, style = SmallStyle, color = if (on) Lumi.Accent else Lumi.Ink)
                            }
                        }
                    }
                    if (look != Look.NONE) PhotoTuner("Intensidad", lookStrength * 100, 0f..100f, "%.0f") { lookStrength = it / 100 }
                }
                4 -> {
                    var channel by remember { mutableStateOf(com.lumi.galeria.data.Channel.ALL) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.lumi.galeria.data.Channel.entries.forEach { option -> PhotoChip(option.label, channel == option) { channel = option } }
                    }
                    CurveEditor(pro.curve(channel), channel, histogram) { next -> pro = pro.copy(curves = pro.curves + (channel to next)) }
                    Text("Arrastra la línea para cambiarla; toca dos veces un punto para quitarlo.", style = SmallStyle)
                }
                5 -> {
                    var band by remember { mutableStateOf(com.lumi.galeria.data.Band.BLUE) }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.lumi.galeria.data.Band.entries.forEach { option ->
                            val touched = pro.bands[option]?.isZero == false
                            Box(
                                Modifier.size(if (band == option) 34.dp else 28.dp).clip(CircleShape).background(Color(option.swatch))
                                    .border(2.dp, if (band == option) Lumi.Ink else if (touched) Lumi.Accent else Color.Transparent, CircleShape)
                                    .clickable { band = option },
                            )
                        }
                    }
                    val shift = pro.bands[band] ?: com.lumi.galeria.data.BandShift()
                    Text(band.label, style = LabelStyle)
                    PhotoTuner("Tono", shift.hue * 100, -100f..100f, "%.0f") { pro = pro.copy(bands = pro.bands + (band to shift.copy(hue = snap(it) / 100))) }
                    PhotoTuner("Saturación", shift.saturation * 100, -100f..100f, "%.0f") { pro = pro.copy(bands = pro.bands + (band to shift.copy(saturation = snap(it) / 100))) }
                    PhotoTuner("Luminosidad", shift.lightness * 100, -100f..100f, "%.0f") { pro = pro.copy(bands = pro.bands + (band to shift.copy(lightness = snap(it) / 100))) }
                }
                6 -> {
                    PhotoTuner("Nitidez", pro.sharpen * 100, 0f..100f, "%.0f") { pro = pro.copy(sharpen = it / 100) }
                    PhotoTuner("Quitar ruido", pro.denoise * 100, 0f..100f, "%.0f") { pro = pro.copy(denoise = it / 100) }
                    PhotoTuner("Grano", pro.grain * 100, 0f..100f, "%.0f") { pro = pro.copy(grain = it / 100) }
                }
                7 -> ZonesPanel(retouch, zoneIndex, { zoneIndex = it }, brush, { brush = it }) { retouch = it }
                8 -> ErasePanel(retouch, brush, { brush = it }) { retouch = it }
                9 -> FacesPanel(retouch, faceStatus) { retouch = it }
                10 -> FramePanel(retouch) { retouch = it }
                11 -> RawPanel(retouch, rawHeadroom) { retouch = it }
                else -> {
                    PhotoTuner("Viñeta", vignette * 100, 0f..100f, "%.0f") { vignette = it / 100 }
                    PhotoTuner("Desvanecer", fade * 100, 0f..100f, "%.0f") { fade = it / 100 }
                    PhotoTuner("Tono", hue * 100, -100f..100f, "%.0f") { hue = snap(it) / 100 }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Un toque corrige luz y color según lo clara u oscura que sea la foto; después se puede afinar a mano.
                PhotoChip("Auto", false) {
                    source?.let { bmp ->
                        val auto = autoLevels(bmp)
                        brightness = auto[0]
                        contrast = auto[1]
                        saturation = auto[2]
                        tab = 1
                    }
                }
                listOf("Recortar", "Luz y color", "Filtros", "Efectos", "Curvas", "Tonos", "Detalle").forEachIndexed { i, label -> PhotoChip(label, tab == i) { tab = i } }
                listOf(7 to "Zonas", 8 to "Borrar", 9 to "Caras", 10 to "Marco").forEach { (i, label) -> PhotoChip(label, tab == i) { tab = i } }
                if (item.format == "DNG") PhotoChip("Revelar RAW", tab == 11) { tab = 11 }
                PhotoChip("Historial · ${history.size}", false) { historyOpen = true }
                PhotoChip("Copiar ajustes", false) {
                    val values = currentLook().toString()
                    prefsLooks.edit().putString("copiados", values).apply()
                    copiedLook = values
                    vm.discover("copiar_ajustes")
                    vm.say("Ajustes copiados. Pégalos en otra foto o en varias a la vez desde la selección.")
                }
                if (copiedLook != null) PhotoChip("Pegar ajustes", false) {
                    runCatching { applyLook(org.json.JSONObject(copiedLook!!)) }
                    vm.say("Ajustes pegados")
                }
                PhotoChip("Antes y después", split != null) { split = if (split == null) 0.5f else null }
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
                        keyV = 0f
                        keyH = 0f
                        lookStrength = 1f
                        pro = com.lumi.galeria.data.Develop()
                        retouch = com.lumi.galeria.data.Retouch()
                        zoneIndex = 0
                    }.padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
            // Cancelar y guardar, abajo, al alcance del pulgar.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Cancelar", { vm.back() }, modifier = Modifier.weight(1f), primary = false)
                Box(Modifier.weight(2f)) {
                    PillButton(if (saving) "Guardando…" else "Guardar", { saveMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = changed && !saving && source != null)
                    DropdownMenu(saveMenu, { saveMenu = false }, containerColor = Lumi.Surface) {
                        DropdownMenuItem({ Text("Guardar una copia") }, { saveMenu = false; save(replace = false) })
                        DropdownMenuItem({ Text("Reemplazar la original") }, { saveMenu = false; save(replace = true) })
                    }
                }
            }
        }
    }

    if (historyOpen) {
        HistoryDialog(
            steps = history, current = historyAt,
            onPick = { i ->
                history.getOrNull(i)?.let { restore(it.json); historyAt = i }
                historyOpen = false
            },
            onDismiss = { historyOpen = false },
        )
    }
    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            containerColor = Lumi.Surface,
            title = { Text("Nombre del filtro", style = HeadingStyle) },
            text = { OutlinedTextField(name, { name = it.take(24) }, singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text("Mi filtro") }) },
            confirmButton = {
                Text(
                    "Guardar", style = LabelStyle, color = if (name.isBlank()) Lumi.Muted else Lumi.Accent,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = name.isNotBlank()) {
                        savedLooks = savedLooks.filter { it.name != name.trim() } + com.lumi.galeria.data.SavedLook(name.trim(), currentLook())
                        writeLooks(prefsLooks, savedLooks)
                        naming = false
                        vm.say("Filtro guardado. Lo tienes en «Filtros» para cualquier foto.")
                    }.padding(12.dp),
                )
            },
            dismissButton = {
                Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { naming = false }.padding(12.dp))
            },
        )
    }
}

/** La matriz de color de un filtro guardado, para enseñar su miniatura. */
internal fun toneFromValues(values: org.json.JSONObject): FloatArray = toneMatrix(
    values.optDouble("brillo", 0.0).toFloat(), values.optDouble("contraste", 0.0).toFloat(), values.optDouble("color", 0.0).toFloat(),
    values.optDouble("calidez", 0.0).toFloat(), runCatching { Look.valueOf(values.optString("filtro", "NONE")) }.getOrDefault(Look.NONE),
    values.optDouble("desvanecer", 0.0).toFloat(), values.optDouble("tono", 0.0).toFloat(), values.optDouble("intensidad", 1.0).toFloat(),
)

/** Aplica unos ajustes copiados a una foto entera, sin pasar por la pantalla del editor. */
internal fun renderWithLook(source: Bitmap, values: org.json.JSONObject): Bitmap {
    val pro = values.optJSONObject("pro")?.let { developFromJson(it) } ?: com.lumi.galeria.data.Develop()
    return render(com.lumi.galeria.data.develop(source, pro), 0, 0f, FULL, toneFromValues(values), false, values.optDouble("vineta", 0.0).toFloat())
}

/** Abre una foto para editarla, a lo sumo con 4096 puntos de lado largo. */
internal fun decodeEditable(context: android.content.Context, uri: android.net.Uri): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longSide = maxOf(info.size.width, info.size.height)
        if (longSide > MAX_SIDE) {
            val k = MAX_SIDE.toFloat() / longSide
            decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
        }
    }
}.getOrNull()

/** Guarda el borrador de una foto; solo se conservan los últimos veinte. */
private fun saveDraft(prefs: android.content.SharedPreferences, id: Long, json: String) {
    val order = prefs.getString("borradores", "").orEmpty().split(',').filter { it.isNotEmpty() && it != id.toString() } + id.toString()
    val edit = prefs.edit()
    order.dropLast(20).forEach { edit.remove("borrador_$it") }
    edit.putString("borradores", order.takeLast(20).joinToString(",")).putString("borrador_$id", json).apply()
}

/** Resume en pocas palabras qué cambió entre dos pasos del historial. */
private fun describeChange(prev: String?, next: String): String {
    val a = prev?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: return "Cambio"
    val b = runCatching { org.json.JSONObject(next) }.getOrNull() ?: return "Cambio"
    val parts = ArrayList<String>()
    fun differs(vararg keys: String) = keys.any { a.opt(it)?.toString() != b.opt(it)?.toString() }
    fun differsIn(parent: String, key: String) = a.optJSONObject(parent)?.opt(key)?.toString() != b.optJSONObject(parent)?.opt(key)?.toString()
    if (differs("geo")) parts += "Recorte y giro"
    if (differs("brillo", "contraste", "color", "calidez")) parts += "Luz y color"
    if (differs("filtro", "intensidad")) parts += "Filtro"
    if (differs("desvanecer", "tono", "vineta")) parts += "Efectos"
    if (differs("pro")) parts += "Curvas, tonos o detalle"
    if (differsIn("retoque", "erase")) parts += "Borrar"
    if (differsIn("retoque", "zonas")) parts += "Zonas"
    if (differsIn("retoque", "caras")) parts += "Caras"
    if (differsIn("retoque", "marco")) parts += "Marco"
    if (differsIn("retoque", "raw")) parts += "Revelado RAW"
    return parts.take(2).joinToString(" · ").ifEmpty { "Cambio" }
}

private fun readLooks(prefs: android.content.SharedPreferences): List<com.lumi.galeria.data.SavedLook> = runCatching {
    val array = org.json.JSONArray(prefs.getString("filtros", "[]"))
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        com.lumi.galeria.data.SavedLook(o.getString("nombre"), o.getJSONObject("valores"))
    }
}.getOrDefault(emptyList())

private fun writeLooks(prefs: android.content.SharedPreferences, looks: List<com.lumi.galeria.data.SavedLook>) {
    val array = org.json.JSONArray()
    looks.forEach { array.put(org.json.JSONObject().put("nombre", it.name).put("valores", it.values)) }
    prefs.edit().putString("filtros", array.toString()).apply()
}

private fun developToJson(d: com.lumi.galeria.data.Develop): org.json.JSONObject = org.json.JSONObject().apply {
    put("sombras", d.shadows.toDouble()); put("luces", d.highlights.toDouble())
    put("nitidez", d.sharpen.toDouble()); put("ruido", d.denoise.toDouble()); put("grano", d.grain.toDouble())
    put("curvas", org.json.JSONObject().apply {
        d.curves.forEach { (channel, curve) ->
            put(channel.name, org.json.JSONArray().apply { curve.points.forEach { put(it.first.toDouble()); put(it.second.toDouble()) } })
        }
    })
    put("gamas", org.json.JSONObject().apply {
        d.bands.forEach { (band, s) -> put(band.name, org.json.JSONArray().put(s.hue.toDouble()).put(s.saturation.toDouble()).put(s.lightness.toDouble())) }
    })
}

internal fun developFromJson(o: org.json.JSONObject): com.lumi.galeria.data.Develop {
    val curves = HashMap<com.lumi.galeria.data.Channel, com.lumi.galeria.data.Curve>()
    o.optJSONObject("curvas")?.let { c ->
        c.keys().forEach { key ->
            val channel = runCatching { com.lumi.galeria.data.Channel.valueOf(key) }.getOrNull() ?: return@forEach
            val a = c.getJSONArray(key)
            curves[channel] = com.lumi.galeria.data.Curve((0 until a.length() / 2).map { a.getDouble(it * 2).toFloat() to a.getDouble(it * 2 + 1).toFloat() })
        }
    }
    val bands = HashMap<com.lumi.galeria.data.Band, com.lumi.galeria.data.BandShift>()
    o.optJSONObject("gamas")?.let { g ->
        g.keys().forEach { key ->
            val band = runCatching { com.lumi.galeria.data.Band.valueOf(key) }.getOrNull() ?: return@forEach
            val a = g.getJSONArray(key)
            bands[band] = com.lumi.galeria.data.BandShift(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat())
        }
    }
    return com.lumi.galeria.data.Develop(
        curves, bands, o.optDouble("sombras", 0.0).toFloat(), o.optDouble("luces", 0.0).toFloat(),
        o.optDouble("nitidez", 0.0).toFloat(), o.optDouble("ruido", 0.0).toFloat(), o.optDouble("grano", 0.0).toFloat(),
    )
}

/**
 * La curva de un canal sobre el histograma de la foto. Arrastrar cerca de un punto lo mueve;
 * en otro sitio, crea uno nuevo en la línea. Dos toques sobre un punto lo quitan.
 */
@Composable
internal fun CurveEditor(curve: com.lumi.galeria.data.Curve, channel: com.lumi.galeria.data.Channel, histogram: FloatArray?, onChange: (com.lumi.galeria.data.Curve) -> Unit) {
    val line = when (channel) {
        com.lumi.galeria.data.Channel.ALL -> Lumi.Ink
        com.lumi.galeria.data.Channel.RED -> Color(0xFFFF5A5A)
        com.lumi.galeria.data.Channel.GREEN -> Color(0xFF5BC48A)
        com.lumi.galeria.data.Channel.BLUE -> Color(0xFF4F8DF7)
    }
    val current by rememberUpdatedState(curve)
    val change by rememberUpdatedState(onChange)
    val table = remember(curve) { curve.table() }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Lumi.Bg)
            .pointerInput(channel) {
                detectTapGestures(onDoubleTap = { tap ->
                    val pts = current.points.sortedBy { it.first }
                    val hit = pts.indexOfFirst { hypot(it.first * size.width - tap.x, (1 - it.second) * size.height - tap.y) < 28.dp.toPx() }
                    if (hit in 1 until pts.lastIndex) change(com.lumi.galeria.data.Curve(pts.filterIndexed { i, _ -> i != hit }))
                })
            }
            .pointerInput(channel) {
                var moving = -1
                detectDragGestures(
                    onDragStart = { start ->
                        val pts = current.points.sortedBy { it.first }
                        moving = pts.indexOfFirst { hypot(it.first * size.width - start.x, (1 - it.second) * size.height - start.y) < 28.dp.toPx() }
                        if (moving < 0) {
                            val x = (start.x / size.width).coerceIn(0.02f, 0.98f)
                            val next = (pts + (x to (1 - start.y / size.height).coerceIn(0f, 1f))).sortedBy { it.first }
                            moving = next.indexOfFirst { it.first == x }
                            change(com.lumi.galeria.data.Curve(next))
                        }
                    },
                    onDragEnd = { moving = -1 },
                ) { pointer, _ ->
                    pointer.consume()
                    val pts = current.points.sortedBy { it.first }.toMutableList()
                    if (moving !in pts.indices) return@detectDragGestures
                    val y = (1 - pointer.position.y / size.height).coerceIn(0f, 1f)
                    // Los extremos solo suben y bajan; los demás no pueden saltarse a sus vecinos.
                    val x = when (moving) {
                        0 -> 0f
                        pts.lastIndex -> 1f
                        else -> (pointer.position.x / size.width).coerceIn(pts[moving - 1].first + 0.03f, pts[moving + 1].first - 0.03f)
                    }
                    pts[moving] = x to y
                    change(com.lumi.galeria.data.Curve(pts))
                }
            },
    ) {
        histogram?.let { bins ->
            val bw = size.width / bins.size
            bins.forEachIndexed { i, v -> drawRect(Lumi.Line, Offset(i * bw, size.height * (1 - v)), Size(bw, size.height * v)) }
        }
        for (i in 1..3) {
            drawLine(Lumi.Line, Offset(size.width * i / 4, 0f), Offset(size.width * i / 4, size.height))
            drawLine(Lumi.Line, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4))
        }
        val path = Path()
        for (v in 0..255) {
            val x = v / 255f * size.width
            val y = (1 - table[v] / 255f) * size.height
            if (v == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(2.5.dp.toPx()))
        curve.points.forEach { (x, y) ->
            drawCircle(Color.White, 7.dp.toPx(), Offset(x * size.width, (1 - y) * size.height))
            drawCircle(line, 4.dp.toPx(), Offset(x * size.width, (1 - y) * size.height))
        }
    }
}

/**
 * Brillo, contraste y color que le convienen a [bitmap], en la misma escala que los controles
 * (de -1 a 1). Mira cómo se reparte la luz: si la foto no llega ni al negro ni al blanco, sube el
 * contraste hasta que llegue; si queda oscura o quemada en conjunto, la acerca al gris medio.
 */
internal fun autoLevels(bitmap: Bitmap): FloatArray {
    val histogram = IntArray(256)
    val steps = 64
    for (y in 0 until steps) for (x in 0 until steps) {
        val p = bitmap.getPixel((x * 2 + 1) * bitmap.width / (steps * 2), (y * 2 + 1) * bitmap.height / (steps * 2))
        histogram[((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000]++
    }
    val total = steps * steps
    // Se ignora el 1 % más oscuro y el 1 % más claro: un reflejo o una sombra no cuentan.
    var low = 0
    var seen = 0
    while (low < 255 && seen + histogram[low] < total / 100) seen += histogram[low++]
    var high = 255
    seen = 0
    while (high > low && seen + histogram[high] < total / 100) seen += histogram[high--]
    val mean = histogram.indices.sumOf { it * histogram[it] }.toFloat() / total

    val scale = (235f / (high - low).coerceAtLeast(1)).coerceIn(1f, 1.45f)
    // Así queda la media después de estirar el contraste alrededor del gris.
    val stretched = scale * (mean - 127.5f) + 127.5f
    val shift = ((122f - stretched) * 0.6f).coerceIn(-36f, 36f)
    return floatArrayOf(shift / 80f, (scale - 1f) / 0.6f, 0.12f)
}

/** Cerca del centro el control se queda en cero, para poder volver al valor original sin afinar. */
private fun snap(value: Float): Float = if (abs(value) < 4f) 0f else value

@Composable
internal fun PhotoTuner(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: String, onChange: (Float) -> Unit) {
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

internal enum class Look(val label: String) {
    NONE("Original"), VIVID("Vívido"), WARM("Cálido"), COOL("Frío"), FADED("Suave"), MONO("Blanco y negro"), SEPIA("Sepia"),
}

/** Matriz de color 4x5 que resume los ajustes y el filtro; sirve igual para la vista previa y para guardar. */
internal fun toneMatrix(brightness: Float, contrast: Float, saturation: Float, warmth: Float, look: Look, fade: Float, hue: Float, lookStrength: Float = 1f): FloatArray {
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
    // El filtro se arma aparte para poder suavizarlo con la intensidad.
    val lookMatrix = android.graphics.ColorMatrix()
    when (look) {
        Look.NONE -> Unit
        Look.VIVID -> {
            lookMatrix.postConcat(android.graphics.ColorMatrix().apply { setSaturation(1.35f) })
            lookMatrix.postConcat(levels(1.1f, -12f))
        }
        Look.WARM -> lookMatrix.postConcat(tint(1.12f, 0.88f))
        Look.COOL -> lookMatrix.postConcat(tint(0.9f, 1.1f))
        Look.FADED -> {
            lookMatrix.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0.7f) })
            lookMatrix.postConcat(levels(0.88f, 24f))
        }
        Look.MONO -> lookMatrix.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        Look.SEPIA -> {
            lookMatrix.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0f) })
            lookMatrix.postConcat(
                android.graphics.ColorMatrix(
                    floatArrayOf(1.07f, 0f, 0f, 0f, 0f, 0f, 0.95f, 0f, 0f, 0f, 0f, 0f, 0.78f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
                ),
            )
        }
    }
    if (look != Look.NONE) {
        val l = lookMatrix.array
        val identity = android.graphics.ColorMatrix().array
        matrix.postConcat(android.graphics.ColorMatrix(FloatArray(20) { identity[it] + (l[it] - identity[it]) * lookStrength }))
    }
    return matrix.array.copyOf()
}

@Composable
internal fun PhotoChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = LabelStyle,
        color = if (on) Lumi.OnAccent else Lumi.Ink,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (on) Lumi.Accent else Lumi.Surface)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
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
internal fun render(source: Bitmap, quarter: Int, angle: Float, crop: Rect, tone: FloatArray?, flip: Boolean, vignette: Float): Bitmap {
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

/**
 * Rueda graduada para enderezar: se arrastra de lado y cada raya es un grado. Al pasar por el
 * cero se queda en él, con un toque de vibración.
 */
@Composable
private fun Dial(angle: Float, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(angle)
    val haptic = LocalHapticFeedback.current
    val accent = Lumi.Accent
    val ink = Lumi.Muted
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(String.format(Locale.US, "%.1f°", angle), style = LabelStyle, color = if (angle == 0f) Lumi.Muted else Lumi.Accent)
        Canvas(
            Modifier.fillMaxWidth().height(40.dp).pointerInput(Unit) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    val next = (current - dx / 9.dp.toPx()).coerceIn(-45f, 45f)
                    val crossed = current != 0f && (current > 0f) != (next > 0f)
                    if (crossed || (current != 0f && abs(next) < 0.3f)) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(0f)
                    } else {
                        onChange(next)
                    }
                }
            },
        ) {
            val step = 9.dp.toPx()
            val cx = size.width / 2
            for (deg in -45..45) {
                val x = cx + (deg - current) * step
                if (x < 0f || x > size.width) continue
                val major = deg % 5 == 0
                val fade = (1f - abs(x - cx) / cx * 0.85f).coerceIn(0.1f, 1f)
                drawLine(
                    ink.copy(alpha = fade),
                    Offset(x, size.height - (if (major) 20.dp else 11.dp).toPx()),
                    Offset(x, size.height),
                    strokeWidth = (if (deg == 0) 2.dp else 1.dp).toPx(),
                )
            }
            drawLine(accent, Offset(cx, 2.dp.toPx()), Offset(cx, size.height), strokeWidth = 2.5.dp.toPx())
        }
    }
}

/** Proporción del recorte, dibujada con su forma. */
@Composable
private fun ShapeChip(option: Aspect, on: Boolean, onClick: () -> Unit) {
    val tint = if (on) Lumi.Accent else Lumi.Muted
    val ratio = option.ratio
    val w = when {
        ratio == null -> 20.dp
        ratio >= 1f -> 22.dp
        else -> 22.dp * ratio
    }
    val h = when {
        ratio == null -> 16.dp
        ratio >= 1f -> 22.dp / ratio
        else -> 22.dp
    }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) Lumi.Accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = w, height = h).border(1.8.dp, tint, RoundedCornerShape(3.dp)))
        }
        Text(option.label, style = SmallStyle, color = tint)
    }
}

/**
 * Corrige la perspectiva: [vertical] endereza las líneas que se juntan hacia arriba (o hacia
 * abajo, si es negativo), como los edificios fotografiados desde el suelo; [horizontal], las que
 * se juntan hacia un lado. Van de -1 a 1.
 */
private fun keystone(source: Bitmap, vertical: Float, horizontal: Float): Bitmap {
    val w = source.width.toFloat()
    val h = source.height.toFloat()
    val a = maxOf(vertical, 0f) * 0.3f
    val b = maxOf(-vertical, 0f) * 0.3f
    val c = maxOf(horizontal, 0f) * 0.3f
    val d = maxOf(-horizontal, 0f) * 0.3f
    // Puntos de la foto que pasan a ser las esquinas: el trapecio se estira hasta el rectángulo.
    val from = floatArrayOf(w * a, h * c, w - w * a, h * d, w - w * b, h - h * d, w * b, h - h * c)
    val to = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
    val matrix = Matrix().apply { setPolyToPoly(from, 0, to, 0, 4) }
    val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
}

/** Dónde está lo principal de la foto (persona, animal u objeto), en proporciones; null si nada destaca. */
private fun subjectBox(bitmap: Bitmap): Rect? {
    val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build())
    val mask = Tasks.await(segmenter.process(InputImage.fromBitmap(bitmap, 0))).foregroundConfidenceMask ?: return null
    mask.rewind()
    var left = bitmap.width
    var top = bitmap.height
    var right = -1
    var bottom = -1
    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
        if (mask.get() > 0.5f) {
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
    }
    if (right < left || bottom < top) return null
    return Rect(left.toFloat() / bitmap.width, top.toFloat() / bitmap.height, (right + 1f) / bitmap.width, (bottom + 1f) / bitmap.height)
}
