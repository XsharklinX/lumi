package com.lumi.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.Topic
import com.lumi.galeria.data.approximated
import com.lumi.galeria.data.search
import com.lumi.galeria.data.similarWords
import com.lumi.galeria.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(state: UiState, vm: LumiViewModel, link: GridLink) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val field = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    val container = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // La búsqueda recorre toda la biblioteca: se hace aparte para que escribir no dé tirones.
    val results by produceState(emptyList<MediaItem>(), query, state.items, state.index, state.favorites) {
        value = vm.runSearch(query)
    }
    val things = state.topThings
    val places = state.topPlaces

    // Se entra a escribir: el teclado sale solo la primera vez.
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { field.requestFocus() } }
    SideEffect {
        link.boundsOf = { id -> tileBounds(grid, container[0], id) }
        link.reveal = { id ->
            val index = results.indexOfFirst { it.id == id }
            if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.index == index }) grid.scrollToItem(maxOf(index - 6, 0))
        }
    }

    fun pick(words: String) {
        query = words
        focus.clearFocus()
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
                onValueChange = { query = it },
                placeholder = { Text("Busca en tus fotos") },
                trailingIcon = {
                    if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Borrar", Modifier.clip(CircleShape).clickable { query = "" }.padding(6.dp))
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.rememberSearch(query) }),
                modifier = Modifier.weight(1f).focusRequester(field),
            )
        }

        if (query.isBlank()) {
            // Sin escribir nada ya hay por dónde empezar: lo que Lumi ha encontrado en las fotos.
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
                                state.recentSearches.forEach { past -> Pill(past) { pick(past) } }
                                Text(
                                    "Borrar",
                                    style = LabelStyle,
                                    color = Lumi.Accent,
                                    modifier = Modifier.clip(CircleShape).clickable { vm.clearSearches() }.padding(horizontal = 10.dp, vertical = 9.dp),
                                )
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Vídeos" to "videos", "Favoritas" to "favoritas", "Capturas" to "capturas", "Con texto" to "texto", "Hoy" to "hoy")
                                .forEach { (label, words) -> Pill(label) { pick(words) } }
                        }
                    }
                }
                if (places.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("Lugares", style = HeadingStyle, modifier = Modifier.padding(top = 10.dp)) }
                    items(places, key = { "l" + it.word }) { TopicCard(it) { pick(it.word) } }
                }
                if (things.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("Cosas", style = HeadingStyle, modifier = Modifier.padding(top = 10.dp)) }
                    items(things, key = { "c" + it.word }) { TopicCard(it) { pick(it.word) } }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "También puedes escribir un mes o un año, el nombre de un álbum o algo que esté escrito en la foto.",
                        style = SmallStyle,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        } else if (results.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val close = remember(query) { similarWords(query) }
                Text("Nada con esas palabras", style = HeadingStyle.copy(fontSize = 20.sp))
                if (close.isNotEmpty()) {
                    Text("Quizá querías decir", style = SmallStyle.copy(fontSize = 14.sp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        close.forEach { word -> Pill(word) { pick(word) } }
                    }
                }
                Text(
                    "Lumi reconoce unas cuatrocientas cosas (perro, playa, comida, coche, montaña…) y lee el texto de las fotos. " +
                        "Prueba con una sola palabra o con una más general.",
                    style = SmallStyle.copy(fontSize = 14.sp),
                )
                Progress(state)
            }
        } else {
            // Con el modelo nuevo ya no hace falta aproximar: entiende la palabra tal cual.
            val loose = remember(query, state.semanticPending) { if (state.semanticPending < state.photoCount) emptyList() else approximated(query) }
            Text(
                countText(results.size, "resultado", "resultados") +
                    if (loose.isEmpty()) "" else ". Lumi no distingue «${loose.joinToString(", ")}»: te enseña lo más parecido que reconoce.",
                style = SmallStyle,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
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

/** Cuánto ha mirado ya Lumi; mientras no termine, la búsqueda no lo ve todo. */
@Composable
private fun Progress(state: UiState) {
    val text = when {
        state.photoCount == 0 -> return
        state.semanticPending > 0 && state.unindexed == 0 ->
            "Lumi está aprendiendo a reconocer tus fotos: lleva ${state.photoCount - state.semanticPending} de ${state.photoCount}. " +
                "Con las que ya ha visto puedes buscar con tus palabras; el resto irá apareciendo."
        state.unindexed > 0 -> "Lumi ha mirado ${state.photoCount - state.unindexed} de ${countText(state.photoCount, "foto", "fotos")}. Ya puedes buscar en esas."
        state.deepPending > 0 -> "Falta leer el texto de ${countText(state.deepPending, "foto", "fotos")}. Buscar por cosas, lugares y fechas ya funciona."
        else -> return
    }
    Text(text, style = SmallStyle, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Lumi.Surface).padding(12.dp).fillMaxWidth())
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = LabelStyle,
        modifier = Modifier.clip(CircleShape).background(Lumi.Surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )
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
