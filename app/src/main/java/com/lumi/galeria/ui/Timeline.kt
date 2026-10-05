package com.lumi.galeria.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.Cell
import com.lumi.galeria.Level
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.TileFilter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.data.Memory

@Composable
fun TimelineScreen(state: UiState, vm: LumiViewModel, actions: Actions, grid: LazyGridState, link: GridLink) {
    val level = state.level
    val cells = state.cells[level].orEmpty()
    val pick = state.pick
    val haptic = LocalHapticFeedback.current
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    // Foto que debe seguir a la vista al cambiar de nivel, para no perder el sitio.
    var anchor by remember { mutableStateOf<String?>(null) }
    val container = remember { arrayOfNulls<LayoutCoordinates>(1) }

    fun changeLevel(next: Level) {
        if (next == level) return
        val first = grid.firstVisibleItemIndex
        anchor = (first until minOf(first + 8, cells.size)).firstNotNullOfOrNull { (cells[it] as? Cell.Photo)?.key }
        vm.setLevel(next)
    }

    LaunchedEffect(level) {
        val key = anchor ?: return@LaunchedEffect
        val index = cells.indexOfFirst { it.key == key }
        if (index >= 0) grid.scrollToItem(if (index > 0 && cells[index - 1] is Cell.Header) index - 1 else index)
    }

    // El visor pregunta dónde está cada miniatura y avisa de qué foto enseña, para que al
    // cerrarlo la cuadrícula esté en ella.
    SideEffect {
        link.boundsOf = { id -> tileBounds(grid, container[0], "p$id") }
        link.reveal = { id ->
            val index = cells.indexOfFirst { it is Cell.Photo && it.item.id == id }
            if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                grid.scrollToItem(maxOf(index - level.columns * 2, 0))
            }
        }
    }

    BackHandler(selection.isNotEmpty()) { selection = emptySet() }

    val zoomIn by rememberUpdatedState<() -> Unit>({ changeLevel(Level.entries[minOf(level.ordinal + 1, Level.entries.lastIndex)]) })
    val zoomOut by rememberUpdatedState<() -> Unit>({ changeLevel(Level.entries[maxOf(level.ordinal - 1, 0)]) })
    val currentCells by rememberUpdatedState(cells)
    val currentSelection by rememberUpdatedState(selection)
    val canSelect by rememberUpdatedState(pick == null || pick.multiple)

    fun headerAt(index: Int): Cell.Header? {
        var i = minOf(index, cells.lastIndex)
        while (i >= 0 && cells[i] !is Cell.Header) i--
        return cells.getOrNull(i) as? Cell.Header
    }

    /** Marca (o desmarca) todas las fotos del grupo que encabeza la celda [headerIndex]. */
    fun toggleGroup(headerIndex: Int) {
        if (headerIndex < 0 || (pick != null && !pick.multiple)) return
        val ids = ArrayList<Long>()
        var i = headerIndex + 1
        while (i < cells.size && cells[i] !is Cell.Header) {
            (cells[i] as? Cell.Photo)?.let { ids += it.item.id }
            i++
        }
        selection = if (selection.containsAll(ids)) selection - ids.toSet() else selection + ids
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    var peek by remember { mutableStateOf<MediaItem?>(null) }

    val header by remember(cells) {
        derivedStateOf { headerAt(grid.firstVisibleItemIndex) ?: cells.firstOrNull { it is Cell.Header } as? Cell.Header }
    }
    val firstHeader = if (cells.firstOrNull() is Cell.Recall) 1 else 0
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barHeight = (if (state.partial && pick == null) 184.dp else 112.dp) + (if (pick == null) 46.dp else 0.dp)

    Box(Modifier.fillMaxSize().background(Lumi.Bg)) {
        when {
            state.loading -> Unit
            cells.isEmpty() -> if (state.filter == TileFilter.ALL) {
                EmptyMessage("Aún no hay fotos", "Las fotos y los vídeos de tu teléfono aparecerán aquí.")
            } else {
                EmptyMessage("Nada con este filtro", "Toca «Todo» para volver a ver la biblioteca completa.")
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(level.columns),
                state = grid,
                contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = top + barHeight, bottom = 130.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { container[0] = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            var zoom = 1f
                            var fired = false
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } >= 2) {
                                    zoom *= event.calculateZoom()
                                    event.changes.forEach { it.consume() }
                                    if (!fired && zoom > 1.25f) {
                                        fired = true
                                        zoomIn()
                                    } else if (!fired && zoom < 0.8f) {
                                        fired = true
                                        zoomOut()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .dragSelect(
                        grid = grid,
                        idAt = { index -> if (canSelect) (currentCells.getOrNull(index) as? Cell.Photo)?.item?.id else null },
                        selection = { currentSelection },
                        onChange = { next ->
                            if (next.size != currentSelection.size) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selection = next
                        },
                        onPeek = { id -> peek = id?.let { wanted -> (currentCells.firstOrNull { it is Cell.Photo && it.item.id == wanted } as? Cell.Photo)?.item } },
                    ),
            ) {
                itemsIndexed(
                    cells,
                    key = { _, cell -> cell.key },
                    span = { _, cell -> GridItemSpan(if (cell is Cell.Photo) 1 else maxLineSpan) },
                    contentType = { _, cell -> cell::class },
                ) { index, cell ->
                    when (cell) {
                        is Cell.Recall -> if (pick == null) {
                            RecallCard(cell.memory) { vm.open(Screen.Items(cell.memory.title, Source.Auto("recuerdo"))) }
                        }
                        // El primer grupo ya se lee en el título grande de arriba.
                        is Cell.Header -> if (index > firstHeader) {
                            // Tocar el título de un día o un mes marca todas sus fotos.
                            Row(
                                Modifier.fillMaxWidth().clickable { toggleGroup(index) }.padding(start = 14.dp, end = 14.dp, top = 22.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(cell.title, style = HeadingStyle, modifier = Modifier.weight(1f))
                                Text(countText(cell.count, "foto", "fotos"), style = SmallStyle)
                            }
                        }
                        is Cell.Photo -> {
                            val item = cell.item
                            PhotoTile(
                                item = item,
                                px = level.thumb,
                                stackSize = cell.stackSize,
                                favorite = item.id in state.favorites,
                                selected = if (selection.isEmpty()) null else item.id in selection,
                                badges = level != Level.YEAR,
                                corner = if (level == Level.DAY) 10.dp else 4.dp,
                                modifier = Modifier.aspectRatio(1f).clickable {
                                    when {
                                        selection.isNotEmpty() ->
                                            selection = if (item.id in selection) selection - item.id else selection + item.id
                                        pick != null -> when {
                                            item.isVideo && !pick.videos -> vm.say("Aquí solo se pueden elegir fotos")
                                            !item.isVideo && !pick.images -> vm.say("Aquí solo se pueden elegir vídeos")
                                            pick.multiple -> selection = setOf(item.id)
                                            else -> actions.pickResult(listOf(item))
                                        }
                                        cell.stackSize > 1 -> vm.open(Screen.StackView(item.id))
                                        else -> vm.open(Screen.Viewer(Source.Timeline, item.id, link.boundsOf(item.id)))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        FastScroller(
            grid = grid,
            count = cells.size,
            label = { headerAt(it)?.title.orEmpty() },
            modifier = Modifier.fillMaxSize().padding(top = top + barHeight, bottom = 110.dp).navigationBarsPadding(),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Lumi.Bg, 0.75f to Lumi.Bg.copy(alpha = 0.92f), 1f to Color.Transparent))
                .statusBarsPadding()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        pick == null -> header?.title ?: "Lumi"
                        pick.multiple -> "Elige fotos"
                        else -> "Elige una foto"
                    },
                    style = TitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    // El título grande es el del grupo que se está viendo: tocarlo lo marca entero.
                    modifier = Modifier.weight(1f).clickable(enabled = pick == null || pick.multiple) {
                        header?.let { toggleGroup(cells.indexOf(it)) }
                    },
                )
                if (pick == null) {
                    BarIcon(CameraIcon, "Hacer una foto", actions.camera)
                    BarIcon(Icons.Filled.Search, "Buscar", { vm.open(Screen.Search) })
                    BarIcon(Icons.Filled.Settings, "Ajustes", { vm.open(Screen.Settings) })
                }
            }
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pick == null) countText(header?.count ?: 0, "foto", "fotos") else header?.title.orEmpty(),
                    style = SmallStyle.copy(fontSize = 13.sp),
                    modifier = Modifier.weight(1f),
                )
                LevelSwitch(level, ::changeLevel)
            }
            if (pick == null) {
                Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TileFilter.entries.forEach { option ->
                        val on = state.filter == option
                        Text(
                            option.label,
                            style = LabelStyle,
                            color = if (on) Lumi.OnAccent else Lumi.Ink,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (on) Lumi.Accent else Lumi.Surface)
                                .clickable { vm.setFilter(option) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            if (state.partial && pick == null) {
                Row(
                    Modifier
                        .padding(top = 6.dp, end = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Lumi.Surface)
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Lumi solo ve las fotos que elegiste.", style = SmallStyle.copy(color = Lumi.Ink), modifier = Modifier.weight(1f))
                    Text(
                        "Elegir más", style = LabelStyle, color = Lumi.Accent,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = actions.requestAccess).padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                    Text(
                        "Ver todas", style = LabelStyle, color = Lumi.Accent,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = actions.openSettings).padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                }
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)) {
            when {
                pick != null && pick.multiple && selection.isNotEmpty() -> {
                    val chosen = state.tiles.filter { it.id in selection }
                    PillButton("Listo · ${countText(chosen.size, "elegida", "elegidas")}", { actions.pickResult(chosen) })
                }
                pick != null -> Text(
                    if (pick.multiple) "Toca las fotos que quieras enviar" else "Toca una foto para elegirla",
                    style = LabelStyle,
                    modifier = Modifier.clip(CircleShape).background(Lumi.Surface).padding(horizontal = 18.dp, vertical = 12.dp),
                )
                selection.isEmpty() -> Dock(Screen.Timeline, vm::switchTab)
                else -> SelectionBar(state.tiles.filter { it.id in selection }, state, vm, actions) { selection = emptySet() }
            }
        }
        peek?.let { PeekOverlay(it) }
    }
}

/** "Hace un año": las fotos de estos mismos días, sin avisos ni notificaciones. Está ahí si quieres mirarla. */
@Composable
private fun RecallCard(memory: Memory, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            .aspectRatio(2.4f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        MediaThumb(memory.items.first(), 1024, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(memory.title, style = TitleStyle.copy(fontSize = 24.sp), color = Color.White)
            Text(countText(memory.items.size, "foto de estos días", "fotos de estos días"), style = SmallStyle, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun LevelSwitch(level: Level, onChange: (Level) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Lumi.Surface).padding(3.dp)) {
        Level.entries.forEach { option ->
            val on = option == level
            Text(
                option.label,
                style = LabelStyle,
                color = if (on) Lumi.OnAccent else Lumi.Muted,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) Lumi.Accent else Color.Transparent)
                    .clickable { onChange(option) }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            )
        }
    }
}
