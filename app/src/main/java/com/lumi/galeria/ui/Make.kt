package com.lumi.galeria.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.data.SHRINK_OPTIONS
import com.lumi.galeria.data.ShrinkOption
import com.lumi.galeria.data.makeGif
import com.lumi.galeria.data.savePdf
import com.lumi.galeria.data.shrinkVideo
import com.lumi.galeria.data.shrunkSize
import com.lumi.galeria.formatDuration
import com.lumi.galeria.formatSize
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Varias fotos en un solo PDF: se ordenan las páginas, se elige el tamaño y se guarda. */
@Composable
fun PdfScreen(screen: Screen.Pdf, state: UiState, vm: LumiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf(screen.ids.mapNotNull { id -> state.items.firstOrNull { it.id == id && !it.isVideo } }) }
    var chosen by remember { mutableIntStateOf(0) }
    var a4 by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    if (pages.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }

    fun move(by: Int) {
        val to = chosen + by
        if (to !in pages.indices) return
        pages = pages.toMutableList().apply { add(to, removeAt(chosen)) }
        chosen = to
    }

    fun save(send: Boolean) {
        saving = true
        scope.launch {
            val uri = withContext(Dispatchers.IO) { savePdf(context, pages, a4, "Lumi ${LocalDate.now()} ${System.currentTimeMillis() % 10000}") }
            saving = false
            if (uri == null) {
                vm.say("No se pudo crear el PDF")
                return@launch
            }
            vm.say("PDF guardado en Documentos, carpeta Lumi")
            if (send) {
                val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.clipData = ClipData.newRawUri(null, uri)
                runCatching { context.startActivity(Intent.createChooser(intent, null)) }
            }
            vm.back()
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("PDF nuevo", countText(pages.size, "página", "páginas"), onBack = { vm.back() })
        Text(
            "Toca una página y muévela con las flechas para cambiar el orden.",
            style = SmallStyle, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        LazyRow(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                val on = index == chosen
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Una hoja en blanco con la foto dentro, como quedará en el PDF.
                    Box(
                        Modifier
                            .width(if (on) 190.dp else 150.dp)
                            .aspectRatio(if (a4) 0.707f else (item.width.toFloat() / item.height.coerceAtLeast(1)).coerceIn(0.4f, 2.2f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .then(if (on) Modifier.border(3.dp, Lumi.Accent, RoundedCornerShape(6.dp)) else Modifier)
                            .clickable { chosen = index }
                            .padding(if (a4) 8.dp else 0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        coil.compose.AsyncImage(
                            model = com.lumi.galeria.Thumb(item.uri, 512, item.modified),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text("${index + 1}", style = LabelStyle, color = if (on) Lumi.Accent else Lumi.Muted)
                }
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("← Antes", { move(-1) }, Modifier.weight(1f), primary = false, enabled = chosen > 0)
                PillButton("Después →", { move(1) }, Modifier.weight(1f), primary = false, enabled = chosen < pages.lastIndex)
                PillButton(
                    "Quitar", {
                        pages = pages.filterIndexed { index, _ -> index != chosen }
                        chosen = chosen.coerceAtMost(pages.lastIndex).coerceAtLeast(0)
                    },
                    Modifier.weight(1f), primary = false, danger = true, enabled = pages.size > 1,
                )
            }
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(Lumi.Surface).padding(4.dp)) {
                listOf(true to "Hoja A4", false to "Tamaño de la foto").forEach { (value, label) ->
                    val on = a4 == value
                    Text(
                        label,
                        style = LabelStyle,
                        color = if (on) Lumi.OnAccent else Lumi.Ink,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(CircleShape).background(if (on) Lumi.Accent else Color.Transparent).clickable { a4 = value }.padding(vertical = 10.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (saving) "Creando…" else "Guardar", { save(send = false) }, Modifier.weight(1f), primary = false, enabled = !saving)
                PillButton("Guardar y enviar", { save(send = true) }, Modifier.weight(1f), enabled = !saving)
            }
        }
    }
}

/**
 * Pasar un vídeo a una copia que ocupa menos. Enseña cuánto pesa ahora y cuánto pesará con cada
 * calidad; mientras convierte, el avance. El original no se toca.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShrinkSheet(item: MediaItem, vm: LumiViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Solo se ofrecen las calidades con las que de verdad se gana espacio.
    val options = remember(item.id) { SHRINK_OPTIONS.filter { shrunkSize(item, it) < item.size * 0.8 } }
    var option by remember { mutableStateOf<ShrinkOption?>(options.firstOrNull()) }
    var progress by remember { mutableIntStateOf(-1) }
    var job by remember { mutableStateOf<Job?>(null) }
    val running = progress >= 0

    ModalBottomSheet(onDismissRequest = { job?.cancel(); onDismiss() }, containerColor = Lumi.Surface) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Reducir el peso", style = HeadingStyle.copy(fontSize = 20.sp))
            Text("Ahora pesa ${formatSize(item.size)}. Se guarda una copia más ligera; el original sigue ahí hasta que tú lo borres.", style = SmallStyle.copy(fontSize = 14.sp))
            if (options.isEmpty()) {
                Text("Este vídeo ya ocupa poco: convertirlo no ahorraría espacio.", style = LabelStyle)
            } else if (running) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    color = Lumi.Accent, trackColor = Lumi.Bg,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                )
                Text("Convirtiendo… $progress %. No salgas de esta pantalla hasta que termine.", style = SmallStyle.copy(fontSize = 14.sp))
                PillButton("Cancelar", { job?.cancel(); progress = -1 }, Modifier.fillMaxWidth(), primary = false)
            } else {
                options.forEach { choice ->
                    val on = choice == option
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) Lumi.Accent.copy(alpha = 0.18f) else Lumi.Bg).clickable { option = choice }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(choice.label, style = HeadingStyle.copy(fontSize = 15.sp))
                            Text(choice.hint, style = SmallStyle)
                        }
                        Text("unos ${formatSize(shrunkSize(item, choice))}", style = LabelStyle, color = if (on) Lumi.Accent else Lumi.Ink)
                    }
                }
                Text("Un vídeo largo puede tardar varios minutos y calentar el teléfono.", style = SmallStyle)
                PillButton(
                    "Guardar una copia más ligera",
                    {
                        val wanted = option ?: return@PillButton
                        progress = 0
                        job = scope.launch {
                            val ok = runCatching { shrinkVideo(context, item, wanted) { progress = it } }.getOrDefault(false)
                            progress = -1
                            vm.say(if (ok) "Copia más ligera guardada. El original sigue en su sitio." else "Este vídeo no se deja convertir")
                            if (ok) {
                                vm.reload()
                                onDismiss()
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Duración máxima de un GIF: más largo pesa demasiado para mandarlo. */
private const val GIF_MAX_MS = 8000L

/**
 * Convertir unos segundos de un vídeo en GIF. Se elige el tramo (hasta 8 segundos) y el tamaño; el
 * fotograma de arriba es el del punto que se está moviendo.
 */
@Composable
fun GifScreen(screen: Screen.Gif, state: UiState, vm: LumiViewModel) {
    val item = state.items.firstOrNull { it.id == screen.id && it.isVideo }
    if (item == null || item.duration < 1000) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val total = item.duration.toFloat()
    var range by remember { mutableStateOf(0f..(minOf(3000f, total) / total)) }
    var focus by remember { mutableStateOf(0f) }
    var big by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(-1) }
    var job by remember { mutableStateOf<Job?>(null) }
    val startMs = (range.start * total).toLong()
    val endMs = (range.endInclusive * total).toLong()
    val frame by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, (focus * total / 300).toLong()) {
        value = withContext(Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            val shot = runCatching {
                retriever.setDataSource(context, item.uri)
                retriever.getScaledFrameAtTime((focus * total).toLong() * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720, 720)
            }.getOrNull()
            runCatching { retriever.release() }
            shot ?: value
        }
    }
    val width = if (big) 480 else 320
    // Calculado a ojo con GIF reales: unos 18 KB por fotograma a 320 px.
    val guess = (endMs - startMs) / 100 * (if (big) 40_000L else 18_000L)

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Hacer un GIF", "De ${formatDuration(startMs)} a ${formatDuration(endMs)}", onBack = { job?.cancel(); vm.back() })
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            val shot = frame
            if (shot != null) {
                androidx.compose.foundation.Image(
                    shot.asImageBitmap(), null, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                )
            } else {
                MediaThumb(item, 1024, Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.RangeSlider(
                value = range,
                onValueChange = { next ->
                    val maxSpan = GIF_MAX_MS / total
                    // Si el tramo pasa de 8 segundos, el otro extremo se mueve con el dedo.
                    focus = if (next.start != range.start) next.start else next.endInclusive
                    range = when {
                        next.endInclusive - next.start <= maxSpan -> next
                        next.start != range.start -> next.start..(next.start + maxSpan)
                        else -> (next.endInclusive - maxSpan)..next.endInclusive
                    }
                },
                enabled = progress < 0,
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Lumi.Accent, activeTrackColor = Lumi.Accent, inactiveTrackColor = Lumi.Line),
            )
            Text("Hasta 8 segundos. Se ve a 10 fotogramas por segundo y se repite sin parar.", style = SmallStyle)
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(Lumi.Surface).padding(4.dp)) {
                listOf(false to "Pequeño", true to "Mediano").forEach { (value, label) ->
                    val on = big == value
                    Text(
                        label,
                        style = LabelStyle,
                        color = if (on) Lumi.OnAccent else Lumi.Ink,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(CircleShape).background(if (on) Lumi.Accent else Color.Transparent)
                            .clickable(enabled = progress < 0) { big = value }.padding(vertical = 10.dp),
                    )
                }
            }
            if (progress >= 0) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    color = Lumi.Accent, trackColor = Lumi.Surface,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                )
                PillButton("Cancelar", { job?.cancel(); progress = -1 }, Modifier.fillMaxWidth(), primary = false)
            } else {
                PillButton(
                    "Guardar GIF · unos ${formatSize(guess)}",
                    {
                        progress = 0
                        job = scope.launch {
                            val ok = withContext(Dispatchers.Default) { makeGif(context, item, startMs, endMs, width) { progress = it } }
                            progress = -1
                            vm.say(if (ok) "GIF guardado en el álbum Lumi" else "No se pudo hacer el GIF")
                            if (ok) {
                                vm.reload()
                                vm.back()
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = endMs - startMs >= 500,
                )
            }
        }
    }
}
