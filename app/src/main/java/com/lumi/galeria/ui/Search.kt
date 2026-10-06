package com.lumi.galeria.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.formatCount
import com.lumi.galeria.tr
import com.lumi.galeria.data.Filters
import com.lumi.galeria.data.Found
import com.lumi.galeria.data.Option
import com.lumi.galeria.data.Topic
import com.lumi.galeria.data.approximated
import com.lumi.galeria.data.similarWords

/**
 * Buscar es, sobre todo, tocar: los filtros de arriba se combinan y el resultado cambia al
 * momento. Escribir es opcional y sirve para nombres, álbumes y texto que aparece en las fotos.
 */
@Composable
fun SearchScreen(state: UiState, vm: LumiViewModel, link: GridLink) {
    val query = vm.searchQuery
    val filters = vm.searchFilters
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val grid = rememberLazyGridState()
    val container = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // La búsqueda recorre toda la biblioteca: se hace aparte para que tocar y escribir no den tirones.
    val found by produceState(Found(emptyList()), query, filters, state.items, state.index, state.favorites) {
        value = vm.runSearch(query, filters)
    }
    val results = found.results
    val idle = query.isBlank() && filters.isEmpty

    SideEffect {
        link.boundsOf = { id -> tileBounds(grid, container[0], id) }
        link.reveal = { id ->
            val index = results.indexOfFirst { it.id == id }
            if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.index == index }) grid.scrollToItem(maxOf(index - 6, 0))
        }
    }

    fun set(next: Filters) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        focus.clearFocus()
        vm.searchFilters = next
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().imePadding()) {
        Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Volver",
                Modifier.clip(CircleShape).clickable { vm.back() }.padding(10.dp).size(24.dp),
                tint = Lumi.Ink,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { vm.searchQuery = it },
                placeholder = { Text("Nombre, álbum o texto de la foto") },
                trailingIcon = {
                    if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Borrar", Modifier.clip(CircleShape).clickable { vm.searchQuery = "" }.padding(6.dp))
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.rememberSearch(query) }),
                modifier = Modifier.weight(1f),
            )
        }

        FilterBar(state, found, filters, ::set)

        if (idle) {
            Explore(state, onQuery = { vm.searchQuery = it; focus.clearFocus() }, onClear = vm::clearSearches, onFilter = ::set)
        } else if (results.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val close = remember(query) { similarWords(query) }
                Text(if (query.isBlank()) "Nada con estos filtros" else "Nada con «${query.trim()}»", style = HeadingStyle.copy(fontSize = 20.sp))
                Text(
                    if (filters.isEmpty) "Prueba con una sola palabra, o usa los filtros de arriba." else "Prueba a quitar algún filtro de arriba.",
                    style = SmallStyle.copy(fontSize = 14.sp),
                )
                if (close.isNotEmpty()) {
                    Text("Quizá querías decir", style = SmallStyle.copy(fontSize = 14.sp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        close.forEach { word -> Chip(word, on = false) { vm.searchQuery = tr(word) } }
                    }
                }
                Progress(state)
            }
        } else {
            val loose = remember(query) { approximated(query) }
            Text(
                countText(results.size, "resultado", "resultados") +
                    if (loose.isEmpty()) "" else ". Lumi no distingue «${loose.joinToString(", ")}»: te enseña lo más parecido que reconoce.",
                style = SmallStyle,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = grid,
                contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize().onGloballyPositioned { container[0] = it },
            ) {
                items(results, key = { it.id }) { item ->
                    PhotoTile(
                        item = item,
                        px = 320,
                        favorite = item.id in state.favorites,
                        tone = state.index[item.id]?.color ?: 0,
                        modifier = Modifier.aspectRatio(1f).clickable {
                            focus.clearFocus()
                            vm.rememberSearch(query)
                            vm.open(Screen.Viewer(Source.Search(query), item.id, link.boundsOf(item.id)))
                        },
                    )
                }
            }
        }
    }
}

/**
 * La fila de filtros. Primero lo que ya está elegido (un toque lo quita), después los que abren
 * una lista y, al final, los tipos de archivo.
 */
@Composable
private fun FilterBar(state: UiState, found: Found, filters: Filters, set: (Filters) -> Unit) {
    val years = found.years
    val places = found.places
    val things = found.things
    val albums = found.albums
    val kinds = found.kinds

    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Lo elegido, siempre delante.
        filters.year?.let { year ->
            val month = filters.month?.let { m -> found.months.firstOrNull { it.value == m }?.label }
            Chip(if (month != null) "$month de $year" else year.toString(), on = true) { set(filters.copy(year = null, month = null)) }
        }
        filters.place?.let { Chip(it, on = true) { set(filters.copy(place = null)) } }
        filters.album?.let { id ->
            val name = state.albums.firstOrNull { it.bucketId == id }?.name ?: found.albums.firstOrNull { it.value == id }?.label.orEmpty()
            Chip(name.ifEmpty { "Álbum" }, on = true) { set(filters.copy(album = null)) }
        }
        filters.thing?.let { Chip(it.replaceFirstChar(Char::uppercase), on = true) { set(filters.copy(thing = null)) } }
        filters.kinds.forEach { kind -> Chip(kind.label, on = true) { set(filters.copy(kinds = filters.kinds - kind)) } }

        // Los que abren una lista. Solo aparecen si tienen algo que ofrecer.
        if (filters.year == null && years.isNotEmpty()) ListChip("Cuándo", years) { set(filters.copy(year = it)) }
        if (filters.year != null && filters.month == null && found.months.size > 1) ListChip("Mes", found.months) { set(filters.copy(month = it)) }
        if (filters.place == null && places.isNotEmpty()) ListChip("Dónde", places) { set(filters.copy(place = it)) }
        if (filters.album == null && albums.size > 1) ListChip("Álbum", albums) { set(filters.copy(album = it)) }
        if (filters.thing == null && things.isNotEmpty()) ListChip("Qué hay", things) { set(filters.copy(thing = it)) }

        kinds.filter { it.value !in filters.kinds && it.count != 0 }.forEach { option ->
            Chip(option.label, on = false) { set(filters.copy(kinds = filters.kinds + option.value)) }
        }
    }
}

/** Sin nada elegido: por dónde empezar. */
@Composable
private fun Explore(state: UiState, onQuery: (String) -> Unit, onClear: () -> Unit, onFilter: (Filters) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Progress(state)
                if (state.recentSearches.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recientes", style = SmallStyle)
                        state.recentSearches.forEach { past -> Chip(past, on = false) { onQuery(past) } }
                        Text(
                            "Borrar",
                            style = LabelStyle,
                            color = Lumi.Accent,
                            modifier = Modifier.clip(CircleShape).clickable(onClick = onClear).padding(horizontal = 10.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
        if (state.topPlaces.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("Lugares", style = HeadingStyle, modifier = Modifier.padding(top = 10.dp)) }
            items(state.topPlaces, key = { "l" + it.word }) { TopicCard(it) { onFilter(Filters(place = it.word)) } }
        }
        if (state.topThings.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("Cosas", style = HeadingStyle, modifier = Modifier.padding(top = 10.dp)) }
            items(state.topThings, key = { "c" + it.word }) { TopicCard(it) { onFilter(Filters(thing = it.word)) } }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Toca los filtros de arriba y combínalos. Si prefieres escribir, vale el nombre de un álbum o de un archivo y cualquier texto que aparezca en la foto.",
                style = SmallStyle,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** Cuánto ha mirado ya Lumi; mientras no termine, la búsqueda no lo ve todo. */
@Composable
private fun Progress(state: UiState) {
    val text = when {
        state.photoCount == 0 -> return
        state.unindexed > 0 -> "Lumi ha mirado ${state.photoCount - state.unindexed} de ${countText(state.photoCount, "foto", "fotos")}. Ya puedes buscar en esas."
        state.deepPending > 0 -> "Falta leer el texto de ${countText(state.deepPending, "foto", "fotos")}. Buscar por cosas, lugares y fechas ya funciona."
        else -> return
    }
    Text(text, style = SmallStyle, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Lumi.Surface).padding(12.dp).fillMaxWidth())
}

/** Filtro de un toque. Encendido lleva una equis: tocarlo lo quita. */
@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (on) Lumi.Accent else Lumi.Surface)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = if (on) 8.dp else 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = LabelStyle, color = if (on) Lumi.OnAccent else Lumi.Ink, maxLines = 1)
        if (on) Icon(Icons.Filled.Close, "Quitar", Modifier.size(15.dp), tint = Lumi.OnAccent)
    }
}

/** Filtro que abre una lista de opciones, cada una con las fotos que tiene. */
@Composable
private fun <T> ListChip(label: String, options: List<Option<T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(CircleShape).background(Lumi.Surface).clickable { open = true }.padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = LabelStyle, maxLines = 1)
            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp), tint = Lumi.Muted)
        }
        DropdownMenu(open, { open = false }, containerColor = Lumi.Surface, modifier = Modifier.heightIn(max = 380.dp)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(option.label, style = LabelStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Text(formatCount(option.count), style = SmallStyle)
                        }
                    },
                    onClick = {
                        open = false
                        onPick(option.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun TopicCard(topic: Topic, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        MediaThumb(topic.cover, 320, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(topic.word.replaceFirstChar { it.uppercase() }, style = LabelStyle.copy(fontSize = 14.sp), color = Color.White, maxLines = 1)
            Text("${topic.count}", style = SmallStyle, color = Color.White.copy(alpha = 0.85f))
        }
    }
}
