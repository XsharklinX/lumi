package com.lumi.galeria.ui

import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.data.thingWords
import com.lumi.galeria.data.normalize
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.defaultMinSize
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    remember(query, filters) { Cascade.start = System.currentTimeMillis() }
    val idle = query.isBlank() && filters.isEmpty
    var savingSmart by remember { mutableStateOf(false) }

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

        // Las búsquedas fijadas: un toque las repite. Mantener pulsada una la quita.
        if (state.pinnedSearches.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Fijadas", style = SmallStyle)
                state.pinnedSearches.forEach { pin ->
                    val on = pin.query == query.trim() && pin.filters == filters
                    Text(
                        "📌 " + pin.name, style = LabelStyle, color = if (on) Lumi.OnAccent else Lumi.Ink,
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Surface)
                            .combinedClickable(onLongClick = { vm.unpinSearch(pin.id) }) { focus.clearFocus(); vm.searchQuery = pin.query; vm.searchFilters = pin.filters }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        FilterBar(state, found, filters, ::set)

        // Mientras se escribe: lo que de verdad hay en las fotos, con cuántas son.
        val suggestions by produceState(emptyList<Suggestion>(), query, state.index, state.places, state.albums) {
            value = if (query.trim().length < 2) emptyList() else withContext(Dispatchers.Default) { suggest(query, state) }
        }
        if (suggestions.isNotEmpty()) {
            SuggestionList(suggestions) { s ->
                focus.clearFocus()
                when (s.kind) {
                    SuggestKind.THING -> { vm.searchFilters = filters.copy(thing = s.value); vm.searchQuery = "" }
                    SuggestKind.PLACE -> { vm.searchFilters = filters.copy(place = s.value); vm.searchQuery = "" }
                    SuggestKind.ALBUM -> { vm.searchFilters = filters.copy(album = s.album); vm.searchQuery = "" }
                    SuggestKind.TEXT -> Unit
                }
            }
        }

        if (idle) {
            Explore(state, onQuery = { vm.searchQuery = it; focus.clearFocus() }, onClear = vm::clearSearches, onFilter = ::set)
        } else if (results.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val close = remember(query) { similarWords(query) }
                LumiArt(SearchLineIcon, ART_PALETTE[1], Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(28.dp)))
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
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
            )
            Row(Modifier.padding(start = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "✦ Guardar como álbum inteligente", style = LabelStyle, color = Lumi.Accent,
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp).clip(CircleShape).clickable { savingSmart = true }.padding(horizontal = 10.dp, vertical = 10.dp),
                )
                Text(
                    "📌 Fijar", style = LabelStyle, color = Lumi.Accent,
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp).clip(CircleShape).clickable { vm.pinSearch(query, filters) }.padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(scaledColumns(state.itemColumns)),
                state = grid,
                contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize().onGloballyPositioned { container[0] = it }.pinchColumns(vm::zoomItems),
            ) {
                items(results, key = { it.id }) { item ->
                    PhotoTile(
                        item = item,
                        px = 320,
                        favorite = item.id in state.favorites,
                        tone = state.index[item.id]?.color ?: 0,
                        motion = state.index[item.id]?.motion == true,
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
    if (savingSmart) {
        FolderNameDialog(
            "Álbum inteligente", com.lumi.galeria.data.suggestAlbumName(query, filters).take(30),
            onDismiss = { savingSmart = false },
            onDone = { name -> savingSmart = false; vm.saveSmartAlbum(name, query, filters) },
        )
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
        filters.people.forEach { name -> Chip(name, on = true) { set(filters.copy(people = filters.people - name)) } }
        filters.camera?.let { Chip(it, on = true) { set(filters.copy(camera = null)) } }
        filters.size?.let { Chip(it.label, on = true) { set(filters.copy(size = null)) } }
        filters.format?.let { Chip(it, on = true) { set(filters.copy(format = null)) } }
        filters.kinds.forEach { kind -> Chip(kind.label, on = true) { set(filters.copy(kinds = filters.kinds - kind)) } }

        // Los que abren una lista. Solo aparecen si tienen algo que ofrecer.
        if (filters.year == null && years.isNotEmpty()) ListChip("Cuándo", years) { set(filters.copy(year = it)) }
        if (filters.year != null && filters.month == null && found.months.size > 1) ListChip("Mes", found.months) { set(filters.copy(month = it)) }
        if (filters.place == null && places.isNotEmpty()) ListChip("Dónde", places) { set(filters.copy(place = it)) }
        if (filters.album == null && albums.size > 1) ListChip("Álbum", albums) { set(filters.copy(album = it)) }
        if (filters.thing == null && things.isNotEmpty()) ListChip("Qué hay", things) { set(filters.copy(thing = it)) }
        if (found.people.isNotEmpty()) ListChip(if (filters.people.isEmpty()) "Quién" else "Y con…", found.people) { set(filters.copy(people = filters.people + it)) }
        if (filters.camera == null && found.cameras.size > 1) ListChip("Cámara", found.cameras) { set(filters.copy(camera = it)) }
        if (filters.size == null && found.sizes.size > 1) ListChip("Tamaño", found.sizes) { set(filters.copy(size = it)) }
        if (filters.format == null && found.formats.size > 1) ListChip("Formato", found.formats) { set(filters.copy(format = it)) }

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

private enum class SuggestKind(val label: String) { THING("Cosa"), PLACE("Lugar"), ALBUM("Álbum"), TEXT("Escrito en la foto") }

/** Una sugerencia del buscador: qué es, cómo se llama, cuántas fotos hay y una de muestra. */
private class Suggestion(val kind: SuggestKind, val label: String, val count: Int, val cover: MediaItem?, val value: String, val album: Long = 0)

/**
 * Cosas, lugares y álbumes cuyo nombre empieza (o contiene) lo escrito, y cuántas fotos tienen
 * texto con esas letras. Primero lo que empieza igual y, a igualdad, lo que tiene más fotos.
 */
private fun suggest(query: String, state: UiState): List<Suggestion> {
    val q = normalize(query.trim())
    fun rank(name: String): Int {
        val n = normalize(name)
        return when {
            n.startsWith(q) -> 0
            n.contains(" $q") -> 1
            q.length >= 3 && n.contains(q) -> 2
            else -> -1
        }
    }
    val things = HashMap<String, Pair<Int, MediaItem>>()
    val places = HashMap<String, Pair<Int, MediaItem>>()
    var written = 0
    var writtenCover: MediaItem? = null
    for (item in state.items) {
        val entry = state.index[item.id]
        for (word in thingWords(entry)) {
            if (rank(word) < 0 && rank(tr(word)) < 0) continue
            val now = things[word]
            things[word] = (now?.first ?: 0) + 1 to (now?.second ?: item)
        }
        state.places[item.id]?.let { place ->
            if (rank(place) >= 0) {
                val now = places[place]
                places[place] = (now?.first ?: 0) + 1 to (now?.second ?: item)
            }
        }
        if (q.length >= 3 && entry?.plainText?.contains(q) == true) {
            written++
            if (writtenCover == null) writtenCover = item
        }
    }
    fun <K> best(map: Map<K, Pair<Int, MediaItem>>, name: (K) -> String, take: Int) =
        map.entries.sortedWith(compareBy<Map.Entry<K, Pair<Int, MediaItem>>> { minOf(rank(name(it.key)).let { r -> if (r < 0) 9 else r }, 9) }.thenByDescending { it.value.first }).take(take)
    val out = ArrayList<Suggestion>()
    best(things, { tr(it) }, 3).forEach { out += Suggestion(SuggestKind.THING, it.key, it.value.first, it.value.second, it.key) }
    best(places, { it }, 2).forEach { out += Suggestion(SuggestKind.PLACE, it.key, it.value.first, it.value.second, it.key) }
    state.albums.filter { !it.locked && rank(it.name) >= 0 }.sortedByDescending { it.count }.take(2).forEach {
        out += Suggestion(SuggestKind.ALBUM, it.name, it.count, it.cover, it.name, it.bucketId)
    }
    if (written > 0) out += Suggestion(SuggestKind.TEXT, "«${query.trim()}»", written, writtenCover, query.trim())
    return out
}

/** La lista de sugerencias, bajo los filtros. */
@Composable
private fun SuggestionList(suggestions: List<Suggestion>, onPick: (Suggestion) -> Unit) {
    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp).clip(RoundedCornerShape(18.dp)).background(Lumi.Surface)) {
        suggestions.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Lumi.Bg)) {
                    s.cover?.let { MediaThumb(it, 160, Modifier.fillMaxSize()) }
                }
                Column(Modifier.weight(1f)) {
                    Text(s.label.replaceFirstChar { it.uppercase() }, style = LabelStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr(s.kind.label) + " · " + countText(s.count, "foto", "fotos"), style = SmallStyle)
                }
            }
        }
    }
}
