package com.lumi.galeria.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.Corner
import com.lumi.galeria.data.ExportFormat
import com.lumi.galeria.data.ExportOptions
import com.lumi.galeria.data.MemoryPace
import com.lumi.galeria.data.estimateExport
import com.lumi.galeria.data.sortShots
import com.lumi.galeria.formatCount
import com.lumi.galeria.formatSize
import kotlinx.coroutines.delay

@Composable
private fun Pick(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, style = LabelStyle, color = if (on) Lumi.OnAccent else Lumi.Ink,
        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Surface).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun Progress(value: Int?, label: String) {
    if (value == null) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label $value %", style = LabelStyle)
        LinearProgressIndicator(
            progress = { value / 100f }, color = Lumi.Accent, trackColor = Lumi.Line,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
    }
}

// ---------- Animar una ráfaga ----------

/** Convierte fotos seguidas en un GIF. Se ve moverse en pantalla antes de guardarlo. */
@Composable
fun AnimateScreen(screen: Screen.Animate, state: UiState, vm: LumiViewModel) {
    val photos = remember(state.items) { state.items.filter { it.id in screen.ids && !it.isVideo }.sortedBy { it.date } }
    if (photos.size < 2) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    var speed by remember { mutableIntStateOf(1) }
    var bounce by remember { mutableStateOf(true) }
    var big by remember { mutableStateOf(false) }
    val delays = listOf(25, 12, 7)
    val order = remember(photos, bounce) { if (bounce) photos + photos.subList(1, photos.size - 1).asReversed() else photos }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(order, speed) {
        while (true) {
            delay(delays[speed] * 10L)
            frame = (frame + 1) % order.size
        }
    }
    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Animar", countText(photos.size, "foto", "fotos"), onBack = { vm.back() })
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black)) {
            // Todas cargadas a la vez y solo una visible: así no parpadea al pasar de una a otra.
            order.distinct().forEach { item ->
                val shown = order[frame % order.size].id == item.id
                MediaThumb(item, 1024, Modifier.fillMaxSize().alpha(if (shown) 1f else 0f))
            }
        }
        Progress(vm.making, "Creando el GIF…")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Lento", "Normal", "Rápido").forEachIndexed { i, label -> Pick(label, speed == i) { speed = i } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pick("Ida y vuelta", bounce) { bounce = !bounce }
                Pick(if (big) "Grande (720 px)" else "Ligero (480 px)", big) { big = !big }
            }
            PillButton(
                "Guardar GIF",
                onClick = { vm.animate(photos, if (big) 720 else 480, delays[speed], bounce) },
                modifier = Modifier.fillMaxWidth(),
                enabled = vm.making == null,
            )
        }
    }
}

// ---------- Exportar en lote ----------

/** Saca copias de varias fotos: de otro tamaño, en otro formato, con marca de agua y sin datos. */
@Composable
fun ExportScreen(screen: Screen.Export, state: UiState, vm: LumiViewModel, actions: Actions) {
    val photos = remember(state.items) { state.items.filter { it.id in screen.ids && !it.isVideo } }
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    var options by remember { mutableStateOf(ExportOptions()) }
    var share by remember { mutableStateOf(false) }
    val before = remember(photos) { photos.sumOf { it.size } }
    val after = remember(photos, options) { photos.sumOf { estimateExport(it, options) } }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding().imePadding()) {
        ScreenHeader("Exportar", countText(photos.size, "foto", "fotos"), onBack = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                photos.take(12).forEach { MediaThumb(it, 160, Modifier.size(54.dp).clip(RoundedCornerShape(10.dp))) }
            }
            Text("Tamaño (lado largo)", style = SmallStyle)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Original", 4096 to "4096 px", 2048 to "2048 px", 1280 to "1280 px", 800 to "800 px").forEach { (side, label) ->
                    Pick(label, options.side == side) { options = options.copy(side = side) }
                }
            }
            Text("Formato", style = SmallStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { format -> Pick(format.label, options.format == format) { options = options.copy(format = format) } }
            }
            if (options.format != ExportFormat.PNG) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Calidad", style = SmallStyle)
                    listOf(70 to "Ligera", 85 to "Buena", 95 to "Máxima").forEach { (q, label) -> Pick(label, options.quality == q) { options = options.copy(quality = q) } }
                }
            }
            Text("Marca de agua", style = SmallStyle)
            OutlinedTextField(
                options.mark, { options = options.copy(mark = it.take(40)) },
                singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text("Tu nombre o tu marca (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (options.mark.isNotBlank()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Corner.entries.forEach { corner -> Pick(corner.label, options.corner == corner) { options = options.copy(corner = corner) } }
                }
            }
            Text("Datos de la foto", style = SmallStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pick("Quitar ubicación", options.dropPlace) { options = options.copy(dropPlace = !options.dropPlace) }
                Pick("Quitar datos de cámara", options.dropCamera) { options = options.copy(dropCamera = !options.dropCamera) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pick("Guardar en «Lumi Exportadas»", !share) { share = false }
                Pick("Y compartir", share) { share = true }
            }
            Text("Las originales no se tocan. Las copias van al álbum «Lumi Exportadas».", style = SmallStyle)
        }
        Progress(vm.making, "Exportando…")
        PillButton(
            "Exportar · ${formatSize(before)} → unos ${formatSize(after)}",
            onClick = { vm.exportPhotos(photos, options) { uris -> if (share) actions.shareUris(uris) } },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            enabled = vm.making == null,
        )
    }
}

// ---------- Vídeo de recuerdos ----------

/** Un vídeo con las mejores fotos de un grupo, movimiento suave, título y la canción que se elija. */
@Composable
fun MemoryVideoScreen(screen: Screen.MemoryVideo, state: UiState, vm: LumiViewModel) {
    val all = remember(state.items) { state.items.filter { it.id in screen.ids } }
    val photos = remember(all, state.favorites, state.index) { com.lumi.galeria.data.pickForMemory(all, state.favorites, state.index) }
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) {
            vm.say("Aquí no hay fotos para hacer un vídeo")
            vm.back()
        }
        return
    }
    val context = LocalContext.current
    var title by remember { mutableStateOf(screen.title) }
    var pace by remember { mutableStateOf(MemoryPace.NORMAL) }
    var music by remember { mutableStateOf<Uri?>(null) }
    var musicName by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            music = uri
            musicName = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: "Canción elegida"
        }
    }
    val seconds = (photos.size * pace.seconds).toInt()
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(photos, pace) {
        while (true) {
            delay((pace.seconds * 1000).toLong())
            frame = (frame + 1) % photos.size
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding().imePadding()) {
        ScreenHeader("Vídeo de recuerdos", countText(photos.size, "foto", "fotos") + " · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", onBack = { vm.back() })
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black)) {
            MediaThumb(photos[frame % photos.size], 1024, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
            if (frame == 0 && title.isNotBlank()) {
                Text(title, style = TitleStyle.copy(fontSize = 28.sp), color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(18.dp))
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it.take(40) }, singleLine = true, shape = RoundedCornerShape(16.dp), label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryPace.entries.forEach { option -> Pick(option.label, pace == option) { pace = option } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pick(if (music == null) "♪ Elegir canción" else "♪ Cambiar canción", music != null) { pick.launch(arrayOf("audio/*")) }
                Text(musicName ?: "Sin música", style = SmallStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (music != null) Text("Quitar", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable { music = null; musicName = null }.padding(8.dp))
            }
            Progress(vm.making, "Montando el vídeo…")
            PillButton(
                "Crear vídeo",
                onClick = { vm.memoryVideo(photos, music, title.trim(), pace) },
                modifier = Modifier.fillMaxWidth(),
                enabled = vm.making == null,
            )
            Text("Se guarda en el álbum «Lumi». La canción es un archivo de tu teléfono.", style = SmallStyle)
        }
    }
}

// ---------- Capturas ordenadas ----------

/** Las capturas de pantalla repartidas por tipo, a partir del texto que se lee en ellas. */
@Composable
fun ScreenshotsScreen(state: UiState, vm: LumiViewModel) {
    val groups = remember(state.items, state.index) { sortShots(state.items, state.index) }
    val total = groups.values.sumOf { it.size }
    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Capturas", if (total == 0) "" else countText(total, "captura ordenada", "capturas ordenadas"), onBack = { vm.back() })
        if (groups.isEmpty()) {
            EmptyMessage("No hay capturas", "Las capturas de pantalla aparecerán aquí, ordenadas por tipo.", icon = PictureIcon)
            return@Column
        }
        if (state.deepPending > 0) {
            Text(
                "Lumi aún está leyendo el texto de algunas fotos: el orden mejorará cuando termine.",
                style = SmallStyle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(scaledColumns(2)),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(groups.entries.toList(), key = { it.key.name }) { (kind, list) ->
                Box(
                    Modifier.aspectRatio(1.2f).clip(RoundedCornerShape(20.dp)).clickable {
                        vm.open(Screen.Items(kind.label, Source.Ids(list.mapTo(HashSet()) { it.id })))
                    },
                ) {
                    MediaThumb(list.first(), 512, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
                    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                        Text(kind.label, style = HeadingStyle.copy(fontSize = 15.sp), color = Color.White)
                        Text(formatCount(list.size), style = SmallStyle, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}
