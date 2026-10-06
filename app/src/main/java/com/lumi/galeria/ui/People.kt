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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumi.galeria.FaceThumb
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.Person
import com.lumi.galeria.formatCount

/** La cara de una persona, en un círculo. */
@Composable
fun FaceAvatar(person: Person, state: UiState, size: Dp, modifier: Modifier = Modifier) {
    val face = person.cover
    val item = state.items.firstOrNull { it.id == face.photo }
    Box(modifier.size(size).clip(CircleShape).background(Lumi.Surface)) {
        if (item != null) {
            AsyncImage(
                FaceThumb(item.uri, item.modified, face.left, face.top, face.right, face.bottom), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        }
    }
}

/** Fila de personas para la pantalla de álbumes. No aparece hasta que haya alguna. */
@Composable
fun PeopleRow(state: UiState, vm: LumiViewModel) {
    if (!state.facesOn || state.people.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Personas", style = HeadingStyle.copy(fontSize = 20.sp), modifier = Modifier.weight(1f))
            Text(
                "Ver todas", style = LabelStyle, color = Lumi.Accent,
                modifier = Modifier.clip(CircleShape).clickable { vm.open(Screen.People) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            state.people.take(15).forEach { person ->
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.open(Screen.Person(person.key)) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FaceAvatar(person, state, 68.dp)
                    Text(
                        person.name ?: "Añadir nombre", style = LabelStyle.copy(fontSize = 12.sp),
                        color = if (person.name == null) Lumi.Muted else Lumi.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Todas las personas, con su cara, su nombre y cuántas fotos tiene. */
@Composable
fun PeopleScreen(state: UiState, vm: LumiViewModel) {
    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Personas", if (state.people.isEmpty()) "" else countText(state.people.size, "persona", "personas"), onBack = { vm.back() })
        val scanning = state.facesOn && state.facesTotal > 0 && state.facesLooked < state.facesTotal
        if (scanning) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Buscando caras: ${formatCount(state.facesLooked)} de ${formatCount(state.facesTotal)} fotos", style = SmallStyle)
                LinearProgressIndicator(
                    progress = { state.facesLooked.toFloat() / state.facesTotal }, color = Lumi.Accent, trackColor = Lumi.Line,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                )
            }
        }
        when {
            !state.facesOn -> EmptyMessage("Agrupar caras está apagado", "Actívalo en Ajustes, en «Análisis de las fotos». Se hace en el teléfono y nada sale de él.")
            state.people.isEmpty() -> EmptyMessage(
                if (scanning) "Buscando caras…" else "Aún no hay personas",
                "Lumi junta las caras que se repiten en al menos tres fotos. Se hace en el teléfono, despacio, y se detiene mientras lo usas.",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(scaledColumns(3)),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.people, key = { it.key }) { person ->
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { vm.open(Screen.Person(person.key)) }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FaceAvatar(person, state, 92.dp)
                        Text(
                            person.name ?: "Añadir nombre", style = LabelStyle,
                            color = if (person.name == null) Lumi.Muted else Lumi.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(formatCount(person.photos.size), style = SmallStyle)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Ponles nombre para buscarlas escribiéndolo. Si alguien sale dos veces, dale el mismo nombre a las dos y se juntan.",
                        style = SmallStyle, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

/** Las fotos de una persona, con su cara arriba y lo que se puede hacer con ella. */
@Composable
fun PersonScreen(screen: Screen.Person, state: UiState, vm: LumiViewModel, actions: Actions, link: GridLink) {
    val person = state.people.firstOrNull { it.key == screen.key }
    if (person == null) {
        // Puede que aún se esté recalculando; si no aparece, se vuelve atrás.
        LaunchedEffect(state.people) {
            kotlinx.coroutines.delay(1500)
            if (state.people.none { it.key == screen.key }) vm.back()
        }
        return
    }
    val ids = remember(person) { person.photos.toHashSet() }
    val source = Source.Ids(ids)
    val items = remember(state.items, state.itemSort, ids) { state.itemsFor(source) }
    var naming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var together by remember { mutableStateOf(false) }
    val title = person.name ?: "Sin nombre"

    ItemsScreen(
        title = title,
        emptyTitle = "No quedan fotos",
        emptyText = "Se han movido o borrado.",
        items = items, source = source, state = state, vm = vm, actions = actions, link = link,
        extra = {
            Box {
                BarIcon(androidx.compose.material.icons.Icons.Filled.MoreVert, "Opciones", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = Lumi.Surface) {
                    DropdownMenuItem({ Text(if (person.name == null) "Ponerle nombre" else "Cambiar el nombre") }, { menu = false; naming = true })
                    if (state.people.any { it.name != null && it.key != person.key }) {
                        DropdownMenuItem({ Text("Fotos con otra persona") }, { menu = false; together = true })
                    }
                    DropdownMenuItem({ Text("Vídeo con música") }, { menu = false; vm.open(Screen.MemoryVideo(title, items.map { it.id })) })
                    DropdownMenuItem({ Text("Ocultar a esta persona") }, { menu = false; vm.hidePerson(person) })
                }
            }
        },
        hero = {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FaceAvatar(person, state, 120.dp)
                Text(title, style = TitleStyle.copy(fontSize = 28.sp), color = if (person.name == null) Lumi.Muted else Lumi.Ink)
                Text(countText(items.size, "foto", "fotos"), style = SmallStyle)
                if (person.name == null) {
                    PillButton("Ponerle nombre", onClick = { naming = true }, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        selectionExtra = if (person.name == null) null else { chosen, clear ->
            Text(
                "No es ${person.name}", style = LabelStyle, color = Lumi.Bg,
                modifier = Modifier.clip(CircleShape).background(Lumi.Ink).clickable {
                    vm.notThisPerson(person, chosen)
                    clear()
                }.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        },
    )

    if (naming) {
        var text by remember { mutableStateOf(person.name.orEmpty()) }
        val others = state.people.mapNotNull { it.name }.distinct().filter { it != person.name }
        AlertDialog(
            onDismissRequest = { naming = false },
            containerColor = Lumi.Surface,
            title = { Text("¿Quién es?", style = HeadingStyle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(text, { text = it.take(40) }, singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text("Nombre") })
                    // Elegir un nombre que ya existe junta a las dos personas.
                    val matches = others.filter { text.isBlank() || it.contains(text.trim(), ignoreCase = true) }.take(6)
                    if (matches.isNotEmpty()) {
                        Text("¿Es la misma que…?", style = SmallStyle)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            matches.forEach { name ->
                                Text(
                                    name, style = LabelStyle,
                                    modifier = Modifier.clip(CircleShape).background(Lumi.Bg).clickable { text = name }.padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Text(
                    "Guardar", style = LabelStyle, color = if (text.isBlank()) Lumi.Muted else Lumi.Accent,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = text.isNotBlank()) {
                        naming = false
                        vm.namePerson(person, text)
                    }.padding(12.dp),
                )
            },
            dismissButton = {
                Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { naming = false }.padding(12.dp))
            },
        )
    }
    if (together) {
        AlertDialog(
            onDismissRequest = { together = false },
            containerColor = Lumi.Surface,
            title = { Text("Con quién", style = HeadingStyle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.people.filter { it.name != null && it.key != person.key }.forEach { other ->
                        val both = person.photos.toSet().intersect(other.photos.toSet())
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = both.isNotEmpty()) {
                                together = false
                                vm.open(Screen.Items(if (com.lumi.galeria.Lang.english) "$title and ${other.name}" else "$title y ${other.name}", Source.Ids(both)))
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FaceAvatar(other, state, 40.dp)
                            Text(other.name.orEmpty(), style = LabelStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                            Text(if (both.isEmpty()) "Ninguna juntos" else formatCount(both.size), style = SmallStyle)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}
