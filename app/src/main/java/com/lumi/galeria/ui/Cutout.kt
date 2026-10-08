package com.lumi.galeria.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Qué queda detrás de la persona u objeto. */
private enum class Behind(val label: String) {
    CLEAR("Transparente"), WHITE("Blanco"), BLACK("Negro"), BLUR("Desenfocado"), MONO("Fondo en gris"), ACCENT("Color"),
}

/** Cómo de marcado es el borde entre lo que se queda y lo que se quita. */
private enum class Edge(val label: String, val low: Float, val high: Float) {
    SOFT("Suave", 0.2f, 0.8f), NORMAL("Normal", 0.38f, 0.62f), SHARP("Nítido", 0.48f, 0.52f),
}

/** La foto y, para cada píxel, la seguridad de que es parte de lo principal (de 0 a 1). */
private class Segmented(val photo: Bitmap, val confidence: FloatArray)

/**
 * Quitar el fondo. El reconocedor dice, píxel a píxel, cuánto cree que algo es parte de lo
 * principal; con eso se hace un borde suave en vez de uno dentado. Detrás se puede dejar nada,
 * un color, la misma foto desenfocada (efecto retrato) o en gris (solo lo principal en color).
 */
@Composable
fun CutoutScreen(screen: Screen.Cutout, state: UiState, vm: LumiViewModel) {
    val item = state.items.firstOrNull { it.id == screen.id }
    if (item == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    var segmented by remember { mutableStateOf<Segmented?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var behind by remember { mutableStateOf(Behind.CLEAR) }
    var edge by remember { mutableStateOf(Edge.NORMAL) }
    var trim by remember { mutableStateOf(false) }
    val accent = Lumi.Accent

    LaunchedEffect(item.id) {
        val result = segment(context, item)
        segmented = result.getOrNull()
        if (segmented == null) problem = segmentProblem(result)
    }

    val result by produceState<Bitmap?>(null, segmented, behind, edge, trim) {
        val source = segmented ?: return@produceState
        value = withContext(Dispatchers.Default) { runCatching { compose(source, behind, edge, trim, accent) }.getOrNull() }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false)
            Text("Quitar el fondo", style = HeadingStyle, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            PillButton(
                "Guardar",
                onClick = {
                    val done = result ?: return@PillButton
                    val name = item.name.substringBeforeLast('.') + "_sin_fondo"
                    // Solo lo transparente necesita PNG; con algo detrás vale una foto normal.
                    if (behind == Behind.CLEAR) vm.saveCutout(done, name)
                    else vm.saveArt(done, name, item.path, "Recorte guardado junto a la foto original")
                },
                enabled = result != null,
            )
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(20.dp)).background(Lumi.Surface),
            contentAlignment = Alignment.Center,
        ) {
            // Damero detrás: así se ve qué partes quedan transparentes.
            if (behind == Behind.CLEAR && result != null) Checkerboard()
            val shown = result
            when {
                shown != null -> Image(shown.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                problem != null -> Text(problem!!, style = SmallStyle.copy(fontSize = 15.sp, color = Lumi.Ink), modifier = Modifier.padding(24.dp))
                else -> Text("Separando del fondo…", style = HeadingStyle)
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Detrás", style = SmallStyle)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Behind.entries.forEach { option -> Choice(option.label, behind == option) { behind = option } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Borde", style = SmallStyle, modifier = Modifier.padding(end = 4.dp))
                Edge.entries.forEach { option -> Choice(option.label, edge == option) { edge = option } }
            }
            Choice("Ajustar al recorte", trim) { trim = !trim }
        }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = LabelStyle,
        color = if (on) Lumi.OnAccent else Lumi.Ink,
        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Bg).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun Checkerboard() {
    val light = Color(0xFFE6E6E6)
    val dark = Color(0xFFC9C9C9)
    Canvas(Modifier.fillMaxSize()) {
        val cell = 12.dp.toPx()
        drawRect(light)
        var y = 0
        while (y * cell < size.height) {
            var x = y % 2
            while (x * cell < size.width) {
                drawRect(dark, Offset(x * cell, y * cell), Size(cell, cell))
                x += 2
            }
            y++
        }
    }
}

/** Mezcla la foto con lo que va detrás, píxel a píxel, según la seguridad del reconocedor. */
private fun compose(source: Segmented, behind: Behind, edge: Edge, trim: Boolean, accent: Color): Bitmap {
    val photo = source.photo
    val w = photo.width
    val h = photo.height
    val px = IntArray(w * h)
    photo.getPixels(px, 0, w, 0, 0, w, h)
    // Detrás: el desenfoque es la foto muy reducida y vuelta a ampliar.
    val back: IntArray? = when (behind) {
        Behind.BLUR -> {
            val tiny = Bitmap.createScaledBitmap(photo, (w / 28).coerceAtLeast(2), (h / 28).coerceAtLeast(2), true)
            val blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
            tiny.recycle()
            IntArray(w * h).also { blurred.getPixels(it, 0, w, 0, 0, w, h); blurred.recycle() }
        }
        Behind.MONO -> IntArray(w * h) { i ->
            val p = px[i]
            val v = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        else -> null
    }
    val flat = when (behind) {
        Behind.WHITE -> 0xFFFFFFFF.toInt()
        Behind.BLACK -> 0xFF000000.toInt()
        Behind.ACCENT -> (0xFF shl 24) or ((accent.red * 255).toInt() shl 16) or ((accent.green * 255).toInt() shl 8) or (accent.blue * 255).toInt()
        else -> 0
    }
    var left = w
    var top = h
    var right = -1
    var bottom = -1
    val out = IntArray(w * h)
    for (i in px.indices) {
        // Paso suave entre "fondo seguro" y "principal seguro".
        val t = ((source.confidence[i] - edge.low) / (edge.high - edge.low)).coerceIn(0f, 1f)
        val a = t * t * (3 - 2 * t)
        if (a > 0.1f) {
            val x = i % w
            val y = i / w
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        val p = px[i]
        out[i] = if (behind == Behind.CLEAR) {
            ((a * 255).toInt() shl 24) or (p and 0xFFFFFF)
        } else {
            val b = back?.get(i) ?: flat
            fun mix(shift: Int) = ((p shr shift and 0xFF) * a + (b shr shift and 0xFF) * (1 - a)).toInt()
            (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }
    val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    full.setPixels(out, 0, w, 0, 0, w, h)
    if (!trim || right < left || bottom < top) return full
    // Ajustar al recorte: solo lo principal, con un pequeño margen.
    val pad = (maxOf(right - left, bottom - top) * 0.04f).toInt()
    val x0 = (left - pad).coerceAtLeast(0)
    val y0 = (top - pad).coerceAtLeast(0)
    val x1 = (right + pad).coerceAtMost(w - 1)
    val y1 = (bottom + pad).coerceAtMost(h - 1)
    return Bitmap.createBitmap(full, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
}

/** Separa lo principal del fondo. Tarda un poco: se hace fuera del hilo de la pantalla. */
private suspend fun segment(context: android.content.Context, item: com.lumi.galeria.data.MediaItem): Result<Segmented> = withContext(Dispatchers.IO) {
    runCatching {
        val photo = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longSide = maxOf(info.size.width, info.size.height)
            if (longSide > 2048) {
                val k = 2048f / longSide
                decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
            }
        }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
        val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build())
        val mask = Tasks.await(segmenter.process(InputImage.fromBitmap(photo, 0))).foregroundConfidenceMask!!
        val values = FloatArray(photo.width * photo.height)
        mask.rewind()
        mask.get(values)
        Segmented(photo, values)
    }
}

/** Por qué no se pudo separar: el recortador no viene dentro de Lumi, lo baja Google Play la primera vez. */
private fun segmentProblem(result: Result<Segmented>): String =
    if (result.exceptionOrNull()?.message.orEmpty().contains("module", ignoreCase = true)) {
        "Google Play está descargando el recortador. Con conexión a internet tarda alrededor de un minuto; vuelve a intentarlo después."
    } else {
        "No se pudo separar nada del fondo en esta foto."
    }

/**
 * Modo retrato en cualquier foto: la persona o lo principal queda nítido y el fondo se
 * desenfoca, con la intensidad que se elija. También se puede dejar el fondo en gris u oscurecerlo.
 */
@Composable
fun PortraitScreen(screen: Screen.Portrait, state: UiState, vm: LumiViewModel) {
    val item = state.items.firstOrNull { it.id == screen.id }
    if (item == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    var segmented by remember { mutableStateOf<Segmented?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var strength by remember { mutableStateOf(0.6f) }
    var gray by remember { mutableStateOf(false) }
    var dim by remember { mutableStateOf(false) }
    var compare by remember { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        val result = segment(context, item)
        segmented = result.getOrNull()
        if (segmented == null) problem = segmentProblem(result)
    }
    val result by produceState<Bitmap?>(null, segmented, strength, gray, dim) {
        val source = segmented ?: return@produceState
        kotlinx.coroutines.delay(80)
        value = withContext(Dispatchers.Default) { runCatching { portrait(source, strength, gray, dim) }.getOrNull() }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false)
            Text("Retrato", style = HeadingStyle, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            PillButton(
                "Guardar",
                onClick = {
                    val done = result ?: return@PillButton
                    vm.saveArt(done, item.name.substringBeforeLast('.') + "_retrato", item.path, "Retrato guardado junto a la foto original")
                },
                enabled = result != null,
            )
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(20.dp)).background(Lumi.Surface)
                .pointerInput(Unit) {
                    // Mantener el dedo enseña la original.
                    detectTapGestures(onPress = {
                        compare = true
                        tryAwaitRelease()
                        compare = false
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            val shown = if (compare) segmented?.photo else result
            when {
                shown != null -> Image(shown.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                problem != null -> Text(problem!!, style = SmallStyle.copy(fontSize = 15.sp, color = Lumi.Ink), modifier = Modifier.padding(24.dp))
                else -> Text("Buscando a quién enfocar…", style = HeadingStyle)
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Desenfoque", style = LabelStyle)
                androidx.compose.material3.Slider(
                    strength, { strength = it }, Modifier.weight(1f), valueRange = 0.1f..1f,
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Fondo en blanco y negro", gray) { gray = !gray }
                Choice("Oscurecer el fondo", dim) { dim = !dim }
            }
            Text("Mantén el dedo en la foto para ver la original.", style = SmallStyle)
        }
    }
}

/** Fondo desenfocado (y, si se pide, gris u oscurecido) detrás de lo principal, con un borde suave. */
private fun portrait(source: Segmented, strength: Float, gray: Boolean, dim: Boolean): Bitmap {
    val photo = source.photo
    val w = photo.width
    val h = photo.height
    // Desenfoque por pasos: reducir mucho y volver a ampliar en dos saltos queda suave, sin cuadros.
    val factor = 6f + strength * 34f
    val tiny = Bitmap.createScaledBitmap(photo, (w / factor).toInt().coerceAtLeast(2), (h / factor).toInt().coerceAtLeast(2), true)
    val mid = Bitmap.createScaledBitmap(tiny, (w / 4).coerceAtLeast(2), (h / 4).coerceAtLeast(2), true)
    val blurred = Bitmap.createScaledBitmap(mid, w, h, true)
    tiny.recycle()
    mid.recycle()
    val back = IntArray(w * h)
    blurred.getPixels(back, 0, w, 0, 0, w, h)
    blurred.recycle()
    val px = IntArray(w * h)
    photo.getPixels(px, 0, w, 0, 0, w, h)
    val out = IntArray(w * h)
    val darken = if (dim) 0.7f else 1f
    for (i in px.indices) {
        val t = ((source.confidence[i] - 0.2f) / 0.6f).coerceIn(0f, 1f)
        val a = t * t * (3 - 2 * t)
        var b = back[i]
        if (gray) {
            val v = ((b shr 16 and 0xFF) * 299 + (b shr 8 and 0xFF) * 587 + (b and 0xFF) * 114) / 1000
            b = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val p = px[i]
        fun mix(shift: Int) = ((p shr shift and 0xFF) * a + (b shr shift and 0xFF) * darken * (1 - a)).toInt().coerceIn(0, 255)
        out[i] = (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}
