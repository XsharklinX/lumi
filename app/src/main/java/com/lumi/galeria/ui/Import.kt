package com.lumi.galeria.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumi.galeria.DocThumb
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.Importable
import com.lumi.galeria.data.safeFolder
import com.lumi.galeria.dayTitle
import com.lumi.galeria.formatSize
import java.time.Instant
import java.time.ZoneId

/**
 * Importar de una cámara conectada por cable, una tarjeta o una memoria USB. Enseña lo que hay por
 * días, marca lo que ya está en el teléfono para no duplicarlo y copia lo elegido a un álbum.
 */
@Composable
fun ImportScreen(state: UiState, vm: LumiViewModel) {
    val job = vm.importState
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.scanForImport(uri)
    }
    // Al terminar de buscar se eligen solas las que faltan en el teléfono.
    var chosen by remember(job.files) { mutableStateOf(job.files.filterTo(HashSet()) { !it.known }.map { it.uri.toString() }.toSet()) }
    var album by remember(job.source) { mutableStateOf(job.source.ifBlank { "Importadas" }) }
    val days = remember(job.files) {
        val zone = ZoneId.systemDefault()
        job.files.groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }.entries.sortedByDescending { it.key }
    }
    val picked = job.files.filter { it.uri.toString() in chosen }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding().imePadding()) {
        ScreenHeader(
            "Importar",
            when {
                job.files.isNotEmpty() -> "${job.source} · " + countText(job.files.count { !it.known }, "nueva", "nuevas")
                else -> "Desde una cámara, una tarjeta o una memoria USB"
            },
            onBack = { vm.back() },
        )
        when {
            job.copying || job.finished -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (job.finished) countText(job.done, "copiada", "copiadas") + " a «${job.album}»" else "Copiando ${job.done} de ${job.total}",
                    style = TitleStyle.copy(fontSize = 24.sp),
                )
                LinearProgressIndicator(
                    progress = { if (job.total > 0) job.done.toFloat() / job.total else 0f },
                    color = Lumi.Accent,
                    trackColor = Lumi.Line,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                )
                Text(
                    when {
                        !job.finished -> "Puedes salir de esta pantalla: la copia sigue. No desconectes la cámara."
                        job.failed > 0 -> countText(job.failed, "archivo no se pudo copiar", "archivos no se pudieron copiar") + ". Los demás están en el teléfono."
                        else -> "Ya están en el teléfono. Lo que hay en la cámara no se ha tocado."
                    },
                    style = SmallStyle.copy(fontSize = 14.sp),
                )
                if (job.finished) {
                    val target = state.albums.firstOrNull { it.path.equals("DCIM/${safeFolder(job.album)}/", ignoreCase = true) }
                    if (target != null) PillButton("Ver el álbum", onClick = { vm.replaceTop(Screen.Album(target.bucketId)); vm.clearImport() })
                    PillButton("Importar de otro sitio", onClick = { vm.clearImport(); picker.launch(null) }, primary = target == null)
                }
            }
            job.scanning -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Buscando fotos y vídeos…", style = HeadingStyle)
                LinearProgressIndicator(color = Lumi.Accent, trackColor = Lumi.Line, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                if (job.found > 0) Text(countText(job.found, "encontrado", "encontrados"), style = SmallStyle)
            }
            job.files.isEmpty() -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Lumi.Surface).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(if (job.source.isNotEmpty()) "No hay fotos ni vídeos en «${job.source}»" else "Conecta lo que quieras importar", style = HeadingStyle.copy(fontSize = 20.sp))
                    Step("1", "Cámara: conéctala con su cable y elige en ella el modo PTP o MTP (a veces se llama «PC» o «transferencia»).")
                    Step("2", "Tarjeta o memoria USB: conéctala con un lector o un adaptador USB-C.")
                    Step("3", "Pulsa el botón y elige la cámara o la memoria. En las cámaras las fotos suelen estar en la carpeta DCIM.")
                    PillButton("Elegir de dónde importar", onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                Text("Lumi solo copia: nunca borra nada de la cámara ni de la memoria.", style = SmallStyle, modifier = Modifier.padding(horizontal = 8.dp))
            }
            else -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        countText(picked.size, "elegida", "elegidas") + " · " + formatSize(picked.sumOf { it.size }),
                        style = LabelStyle,
                        modifier = Modifier.weight(1f),
                    )
                    TextAction("Todo lo nuevo") { chosen = job.files.filter { !it.known }.map { it.uri.toString() }.toSet() }
                    TextAction("Ninguna") { chosen = emptySet() }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(scaledColumns(4)),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    days.forEach { (day, files) ->
                        val keys = files.map { it.uri.toString() }
                        val all = keys.all { it in chosen }
                        item(key = "d$day", span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    // Tocar el día elige o quita todas sus fotos.
                                    .clickable { chosen = if (all) chosen - keys.toSet() else chosen + keys }
                                    .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(dayTitle(files[0].date), style = HeadingStyle.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
                                val known = files.count { it.known }
                                Text(
                                    files.size.toString() + if (known > 0) " · " + countText(known, "ya la tienes", "ya las tienes") else "",
                                    style = SmallStyle,
                                )
                            }
                        }
                        items(files, key = { it.uri.toString() }) { file ->
                            ImportTile(file, file.uri.toString() in chosen) {
                                val key = file.uri.toString()
                                chosen = if (key in chosen) chosen - key else chosen + key
                            }
                        }
                    }
                }
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it.take(60) },
                        label = { Text("Álbum") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PillButton(
                        if (picked.isEmpty()) "Elige qué importar" else "Importar ${picked.size} a «${safeFolder(album)}»",
                        onClick = { vm.runImport(picked, safeFolder(album)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = picked.isNotEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Lumi.Accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(number, style = SmallStyle.copy(fontWeight = FontWeight.Bold), color = Lumi.Accent)
        }
        Text(text, style = SmallStyle.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(label, style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp))
}

@Composable
private fun ImportTile(file: Importable, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(Lumi.Surface).clickable(onClick = onClick)) {
        AsyncImage(DocThumb(file.uri), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (file.isVideo) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.align(Alignment.BottomStart).padding(4.dp).size(16.dp), tint = Color.White)
        }
        if (file.known && !selected) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Text("Ya la tienes", style = SmallStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
            }
        }
        if (selected) Box(Modifier.fillMaxSize().background(Lumi.Accent.copy(alpha = 0.22f)))
        Box(
            Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape)
                .background(if (selected) Lumi.Accent else Color.Black.copy(alpha = 0.3f))
                .border(1.5.dp, if (selected) Lumi.Accent else Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, Modifier.size(13.dp), tint = Lumi.OnAccent)
        }
    }
}
