package com.lumi.galeria.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.lumi.galeria.data.albumName
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.UiState
import com.lumi.galeria.VaultThumb
import com.lumi.galeria.countText
import com.lumi.galeria.dayTitle
import com.lumi.galeria.formatSize

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(state: UiState, vm: LumiViewModel) {
    if (!state.vaultOpen) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val items = state.vaultItems
    var selection by remember { mutableStateOf(emptySet<String>()) }
    BackHandler(selection.isNotEmpty()) { selection = emptySet() }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(
            "Carpeta privada",
            if (items.isEmpty()) "" else "${countText(items.size, "elemento", "elementos")} · ${formatSize(items.sumOf { it.size })}",
            onBack = { vm.back() },
        )
        if (items.isEmpty()) {
            EmptyMessage(
                "Aquí no hay nada todavía",
                "Elige fotos en la galería y usa «Mover a la carpeta privada». Se guardan cifradas y dejan de verse en la galería, la búsqueda y los recuerdos.",
                Modifier.weight(1f),
                icon = LockLineIcon,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(scaledColumns(3)),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Por álbumes, con el que tiene lo más reciente primero.
                val groups = items.groupBy { albumName(it.path.trimEnd('/').substringAfterLast('/')) }
                    .entries.sortedByDescending { (_, list) -> list.maxOf { it.date } }
                groups.forEach { (name, list) ->
                item(key = "t-$name", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                        Text(name, style = HeadingStyle, modifier = Modifier.weight(1f))
                        Text(countText(list.size, "elemento", "elementos"), style = SmallStyle)
                    }
                }
                items(list.sortedByDescending { it.date }, key = { it.id }) { item ->
                    val selected = item.id in selection
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Lumi.Surface)
                            .combinedClickable(
                                onClick = {
                                    if (selection.isEmpty()) vm.openFromVault(item)
                                    else selection = if (selected) selection - item.id else selection + item.id
                                },
                                onLongClick = { selection = selection + item.id },
                            ),
                    ) {
                        AsyncImage(VaultThumb(item.id), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (item.isVideo) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.align(Alignment.BottomStart).padding(6.dp).size(18.dp), tint = Color.White)
                        }
                        if (selection.isNotEmpty()) {
                            if (selected) Box(Modifier.fillMaxSize().background(Lumi.Accent.copy(alpha = 0.28f)))
                            Box(
                                Modifier.padding(6.dp).size(22.dp).clip(CircleShape)
                                    .background(if (selected) Lumi.Accent else Color.Black.copy(alpha = 0.35f))
                                    .border(1.5.dp, if (selected) Lumi.Accent else Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Lumi.OnAccent)
                            }
                        }
                    }
                }
                }
            }
        }
        if (selection.isEmpty()) {
            Text(
                "Mantén pulsada una foto para sacarla de aquí o borrarla. La carpeta se cierra al salir de Lumi.",
                style = SmallStyle,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            val chosen = items.filter { it.id in selection }
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    "Devolver ${chosen.size} a la galería",
                    onClick = { vm.restoreFromVault(chosen); selection = emptySet() },
                    modifier = Modifier.weight(1.6f),
                )
                PillButton(
                    "Borrar",
                    onClick = { vm.deleteFromVault(chosen); selection = emptySet() },
                    modifier = Modifier.weight(1f),
                    primary = false,
                    danger = true,
                )
            }
        }
    }
}

@Composable
fun BackupScreen(state: UiState, vm: LumiViewModel) {
    val context = LocalContext.current
    val backup = state.backup
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Sin esto, el permiso sobre la carpeta se pierde al reiniciar el teléfono.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            vm.setBackupDestination(uri)
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Copia", "Tus fotos en un segundo sitio", onBack = { vm.back() })
        Column(Modifier.padding(horizontal = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Lumi.Surface).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Destino", style = SmallStyle)
                Text(backup.destination ?: "Sin elegir", style = HeadingStyle.copy(fontSize = 20.sp))
                Text(
                    "Puede ser una tarjeta SD, una memoria USB conectada al teléfono o cualquier carpeta. Para llevarlas al PC, copia a una memoria USB o a una carpeta y pásala por cable.",
                    style = SmallStyle,
                )
                PillButton(
                    if (backup.destination == null) "Elegir carpeta" else "Cambiar carpeta",
                    onClick = { picker.launch(null) },
                    modifier = Modifier.padding(top = 6.dp),
                    primary = backup.destination == null,
                    enabled = !backup.running,
                )
            }
            if (backup.destination != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Lumi.Surface).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        when {
                            backup.running -> "Copiando ${backup.done} de ${backup.total}"
                            backup.pending == 0 -> "Todo está copiado"
                            else -> "${countText(backup.pending, "archivo", "archivos")} sin copiar"
                        },
                        style = HeadingStyle.copy(fontSize = 20.sp),
                    )
                    if (backup.running) {
                        LinearProgressIndicator(
                            progress = { if (backup.total > 0) backup.done.toFloat() / backup.total else 0f },
                            color = Lumi.Accent,
                            trackColor = Lumi.Line,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        )
                    }
                    Text(
                        if (backup.last > 0) "Última copia: ${dayTitle(backup.last).replaceFirstChar { it.lowercase() }}"
                        else "Aún no se ha hecho ninguna copia.",
                        style = SmallStyle,
                    )
                    Text("Se ordenan en carpetas por año y mes dentro de «Lumi». Solo se copia lo nuevo.", style = SmallStyle)
                }
            }
        }
        PillButton(
            if (backup.running) "Copiando…" else "Copiar ahora",
            onClick = { vm.runBackup() },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            enabled = backup.destination != null && !backup.running && backup.pending > 0,
        )
    }
}
