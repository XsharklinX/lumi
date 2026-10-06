package com.lumi.galeria.ui

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.timeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cómo queda la foto en una de las dos pantallas: ampliación, desplazamiento y retoques. */
private data class Framing(val zoom: Float = 1f, val pan: Offset = Offset.Zero, val dim: Float = 0f, val blur: Boolean = false)

/**
 * Poner una foto de fondo viendo la pantalla tal como quedará: la foto ocupa todo, con un reloj y
 * unos iconos de muestra encima. Se amplía con dos dedos y se arrastra; se puede oscurecer o
 * desenfocar para que se lean los iconos. Inicio y bloqueo guardan cada uno su encuadre.
 */
@Composable
fun WallpaperScreen(screen: Screen.Wallpaper, state: UiState, vm: LumiViewModel) {
    val item = state.items.firstOrNull { it.id == screen.id && !it.isVideo }
    if (item == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val metrics = context.resources.displayMetrics
    val bitmap by produceState<Bitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    // Lo justo para ampliar sin que se note: el doble del lado largo de la pantalla.
                    val need = maxOf(metrics.widthPixels, metrics.heightPixels) * 2f
                    val k = need / maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                    if (k < 1f) decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                }
            }.getOrNull()
        }
    }
    var lock by remember { mutableStateOf(false) }
    var home by remember { mutableStateOf(Framing()) }
    var locked by remember { mutableStateOf(Framing()) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    val framing = if (lock) locked else home

    fun update(next: Framing) {
        if (lock) locked = next else home = next
    }

    /** Escala con la que la foto cubre la pantalla sin dejar huecos. */
    fun cover(source: Bitmap) = maxOf(area.width.toFloat() / source.width, area.height.toFloat() / source.height)

    fun clamp(pan: Offset, zoom: Float, source: Bitmap): Offset {
        val s = cover(source) * zoom
        val maxX = (source.width * s - area.width) / 2
        val maxY = (source.height * s - area.height) / 2
        return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
    }

    /** La parte de la foto que se ve, con sus retoques, del tamaño de la pantalla. */
    fun render(source: Bitmap, f: Framing): Bitmap {
        val s = cover(source) * f.zoom
        val w = area.width / s
        val h = area.height / s
        val cx = source.width / 2f - f.pan.x / s
        val cy = source.height / 2f - f.pan.y / s
        val outW = metrics.widthPixels.coerceAtLeast(1)
        val outH = (outW * area.height.toFloat() / area.width).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val from = android.graphics.Rect((cx - w / 2).toInt(), (cy - h / 2).toInt(), (cx + w / 2).toInt(), (cy + h / 2).toInt())
        if (f.blur) {
            // Desenfoque: la imagen muy reducida y vuelta a ampliar.
            val tiny = Bitmap.createBitmap(outW / 24 + 1, outH / 24 + 1, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(tiny).drawBitmap(source, from, RectF(0f, 0f, tiny.width.toFloat(), tiny.height.toFloat()), paint)
            canvas.drawBitmap(tiny, null, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), paint)
            tiny.recycle()
        } else {
            canvas.drawBitmap(source, from, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), paint)
        }
        if (f.dim > 0f) canvas.drawColor(android.graphics.Color.argb((f.dim * 255).toInt(), 0, 0, 0))
        return out
    }

    fun apply(both: Boolean) {
        val source = bitmap ?: return
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) {
                runCatching {
                    val manager = WallpaperManager.getInstance(context)
                    if (both) {
                        manager.setBitmap(render(source, home), null, true, WallpaperManager.FLAG_SYSTEM)
                        manager.setBitmap(render(source, locked), null, true, WallpaperManager.FLAG_LOCK)
                    } else {
                        manager.setBitmap(render(source, framing), null, true, if (lock) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM)
                    }
                }.isSuccess
            }
            saving = false
            vm.say(if (ok) "Fondo de pantalla cambiado" else "No se pudo poner de fondo")
            if (ok) vm.back()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { area = it }) {
        val source = bitmap
        if (source != null && area.width > 0) {
            val image = remember(source) { source.asImageBitmap() }
            val s = cover(source) * framing.zoom
            Image(
                image, null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        // Se dibuja del tamaño de la foto y se lleva a su sitio.
                        translationX = framing.pan.x
                        translationY = framing.pan.y
                        scaleX = s
                        scaleY = s
                    }
                    .requiredWidth(with(androidx.compose.ui.platform.LocalDensity.current) { source.width.toDp() })
                    .requiredHeight(with(androidx.compose.ui.platform.LocalDensity.current) { source.height.toDp() })
                    .then(if (framing.blur && Build.VERSION.SDK_INT >= 31) Modifier.blur(4.dp) else Modifier),
            )
            if (framing.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = framing.dim)))
            // Ampliar con dos dedos y arrastrar.
            Box(
                Modifier.fillMaxSize().pointerInput(source, lock) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val now = if (lock) locked else home
                        val nextZoom = (now.zoom * zoom).coerceIn(1f, 4f)
                        update(now.copy(zoom = nextZoom, pan = clamp(now.pan + pan, nextZoom, source)))
                    }
                },
            )
        }

        // Lo que pinta el teléfono encima, de muestra.
        if (lock) {
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(timeText(System.currentTimeMillis()), style = TitleStyle.copy(fontSize = 72.sp), color = Color.White)
            }
        } else {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 40.dp)) {
                Text(timeText(System.currentTimeMillis()), style = TitleStyle.copy(fontSize = 44.sp), color = Color.White)
                Spacer(Modifier.height(140.dp))
                // Iconos de muestra: así se ve si se leen sobre la foto.
                val colors = listOf(0xFF4F8DF7, 0xFFF7B84F, 0xFF5BC48A, 0xFFE8667A, 0xFF9A7BF2, 0xFFF2F2F2, 0xFF47C1D9, 0xFFF28E4B)
                LazyVerticalGrid(GridCells.Fixed(4), userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    items(colors.size) { i -> Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(colors[i]))) }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(10.dp)
                .clip(RoundedCornerShape(26.dp)).background(Color(0xE61C1B24)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xFF0E0E13)).padding(4.dp)) {
                listOf(false to "Inicio", true to "Bloqueo").forEach { (value, label) ->
                    val on = lock == value
                    Text(
                        label,
                        style = LabelStyle,
                        color = if (on) Lumi.OnAccent else Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(CircleShape).background(if (on) Lumi.Accent else Color.Transparent)
                            .clickable { lock = value }.padding(vertical = 9.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Oscurecer", style = LabelStyle, color = Color.White, modifier = Modifier.width(92.dp))
                Slider(
                    value = framing.dim,
                    onValueChange = { update(framing.copy(dim = it)) },
                    valueRange = 0f..0.6f,
                    colors = SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Desenfocar",
                    style = LabelStyle,
                    color = if (framing.blur) Lumi.OnAccent else Color.White,
                    modifier = Modifier.clip(CircleShape).background(if (framing.blur) Lumi.Accent else Color.White.copy(alpha = 0.12f))
                        .clickable { update(framing.copy(blur = !framing.blur)) }.padding(horizontal = 14.dp, vertical = 9.dp),
                )
                Text("Pellizca para ampliar y arrastra", style = SmallStyle, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Cancelar", { vm.back() }, Modifier.weight(0.8f), primary = false)
                PillButton(if (saving) "Poniendo…" else if (lock) "Poner en bloqueo" else "Poner en inicio", { apply(both = false) }, Modifier.weight(1.2f), primary = false, enabled = !saving && bitmap != null)
                PillButton("En las dos", { apply(both = true) }, Modifier.weight(1f), enabled = !saving && bitmap != null)
            }
        }
    }
}
