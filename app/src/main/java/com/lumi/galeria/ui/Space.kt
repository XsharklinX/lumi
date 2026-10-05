package com.lumi.galeria.ui

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.ReviewKind
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.formatSize

@Composable
fun SpaceScreen(state: UiState, vm: LumiViewModel) {
    val groups = remember(state.items, state.stackByBest, state.blurry) {
        ReviewKind.entries.map { it to state.reviewItems(it) }.filter { it.second.isNotEmpty() }
    }
    // Las "quizá borrosas" no cuentan en el total: es una sospecha, no algo que sobre seguro.
    val recoverable = remember(groups) {
        groups.filter { it.first.preselected }.flatMap { it.second }.distinctBy { it.id }.sumOf { it.size }
    }
    val storage = remember(state.items) {
        runCatching { StatFs(Environment.getExternalStorageDirectory().path) }.getOrNull()
    }

    Box(Modifier.fillMaxSize().background(Lumi.Bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                "Espacio",
                if (storage != null) "${formatSize(storage.availableBytes)} libres de ${formatSize(storage.totalBytes)}" else "",
                onBack = { vm.back() },
            )
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (groups.isEmpty()) {
                    Column(Modifier.padding(horizontal = 6.dp, vertical = 18.dp)) {
                        Text("Todo en orden", style = TitleStyle.copy(fontSize = 40.sp))
                        Text("No hay repetidas, vídeos grandes ni capturas antiguas que revisar.", style = SmallStyle.copy(fontSize = 14.sp))
                    }
                } else {
                    Column(Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
                        Text(formatSize(recoverable), style = TitleStyle.copy(fontSize = 54.sp, letterSpacing = (-1.5).sp))
                        Text("que puedes recuperar si quieres", style = SmallStyle.copy(fontSize = 14.sp))
                    }
                    groups.forEach { (kind, items) ->
                        SpaceRow(kind.title, items, formatSize(items.sumOf { it.size })) { vm.open(Screen.Review(kind)) }
                    }
                    Text(
                        "Ves todo antes de borrar. Lo que quites pasa 30 días en la papelera.",
                        style = SmallStyle,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                SpaceRow(
                    "Papelera",
                    state.trashed,
                    if (state.trashed.isEmpty()) "vacía" else formatSize(state.trashed.sumOf { it.size }),
                ) { vm.open(Screen.Trash) }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SpaceRow(title: String, items: List<MediaItem>, trailing: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Lumi.Surface)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tres miniaturas montadas una sobre otra.
        Box(Modifier.width(92.dp).height(44.dp)) {
            items.take(3).forEachIndexed { i, item ->
                MediaThumb(
                    item, 160,
                    Modifier
                        .offset(x = (24 * i).dp)
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, Lumi.Surface, RoundedCornerShape(12.dp)),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = HeadingStyle.copy(fontSize = 15.sp))
            Text(countText(items.size, "elemento", "elementos"), style = SmallStyle)
        }
        Text(trailing, style = LabelStyle, color = Lumi.Accent)
    }
}

@Composable
private fun PickGrid(
    items: List<MediaItem>,
    selection: Set<Long>,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
    caption: (MediaItem) -> String? = { null },
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        items(items, key = { it.id }) { item ->
            PhotoTile(
                item = item,
                px = 320,
                selected = item.id in selection,
                caption = caption(item),
                modifier = Modifier.aspectRatio(1f).clickable { onToggle(item.id) },
            )
        }
    }
}

@Composable
fun ReviewScreen(screen: Screen.Review, state: UiState, vm: LumiViewModel, actions: Actions) {
    val items = remember(state.items, state.stackByBest, state.blurry, screen.kind) { state.reviewItems(screen.kind) }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    // Lo seguro empieza marcado y se desmarca lo que se quiere conservar; lo dudoso, al revés.
    var excluded by remember { mutableStateOf(emptySet<Long>()) }
    val chosen = items.filter { (it.id !in excluded) == screen.kind.preselected }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(screen.kind.title, screen.kind.hint, onBack = { vm.back() })
        PickGrid(
            items = items,
            selection = chosen.mapTo(HashSet()) { it.id },
            onToggle = { id -> excluded = if (id in excluded) excluded - id else excluded + id },
            modifier = Modifier.weight(1f),
            caption = { formatSize(it.size) },
        )
        PillButton(
            if (chosen.isEmpty()) "Toca las que quieras quitar"
            else "Mover ${chosen.size} a la papelera · ${formatSize(chosen.sumOf { it.size })}",
            onClick = { actions.trash(chosen) {} },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            enabled = chosen.isNotEmpty(),
        )
    }
}

@Composable
fun TrashScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    val items = state.trashed
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    val chosen = if (selection.isEmpty()) items else items.filter { it.id in selection }
    val now = System.currentTimeMillis() / 1000

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(
            "Papelera",
            if (items.isEmpty()) "" else "${countText(items.size, "elemento", "elementos")} · ${formatSize(items.sumOf { it.size })}",
            onBack = { vm.back() },
        )
        if (items.isEmpty()) {
            EmptyMessage("La papelera está vacía", "Lo que borres se guarda aquí 30 días por si te arrepientes.", Modifier.weight(1f))
        } else {
            Text(
                "Cada elemento se borra solo, para siempre, cuando se cumplen sus 30 días. El número indica los días que le quedan.",
                style = SmallStyle,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            PickGrid(
                items = items,
                selection = selection,
                onToggle = { id -> selection = if (id in selection) selection - id else selection + id },
                modifier = Modifier.weight(1f),
                caption = { item ->
                    val days = ((item.expires - now) / 86_400).coerceAtLeast(0)
                    if (item.expires > 0) "$days d" else null
                },
            )
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    if (selection.isEmpty()) "Restaurar todo" else "Restaurar ${selection.size}",
                    onClick = { actions.restore(chosen); selection = emptySet() },
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    if (selection.isEmpty()) "Vaciar" else "Borrar ${selection.size}",
                    onClick = { actions.deleteForever(chosen); selection = emptySet() },
                    modifier = Modifier.weight(1f),
                    primary = false,
                    danger = true,
                )
            }
        }
    }
}
