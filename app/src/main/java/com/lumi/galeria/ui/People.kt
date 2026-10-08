@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lumi.galeria.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumi.galeria.FaceThumb
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.PeopleOrder
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.Thumb
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.Face
import com.lumi.galeria.data.Person
import com.lumi.galeria.formatCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged

/** Una cara recortada de su foto, en un círculo. */
@Composable
fun FaceCircle(face: Face, state: UiState, size: Dp, modifier: Modifier = Modifier) {
    val item = remember(state.items, face.photo) { state.items.firstOrNull { it.id == face.photo } }
    Box(modifier.size(size).clip(CircleShape).background(Lumi.Surface)) {
        if (item != null) {
            AsyncImage(
                FaceThumb(item.uri, item.modified, face.left, face.top, face.right, face.bottom), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        }
    }
}

/** La cara de una persona, en un círculo. Las fijadas llevan un aro del color de la app. */
@Composable
fun FaceAvatar(person: Person, state: UiState, size: Dp, modifier: Modifier = Modifier) {
    FaceCircle(
        person.cover, state, size,
        modifier.then(if (person.pinned) Modifier.border(2.dp, Lumi.Accent, CircleShape) else Modifier),
    )
}

private val UiState.facesBusy: Boolean get() = facesOn && facesTotal > 0 && facesLooked < facesTotal

/** Fila de personas para la pantalla de álbumes. Mientras busca, un hueco lo dice; nunca desaparece sin más. */
@Composable
fun PeopleRow(state: UiState, vm: LumiViewModel) {
    if (state.people.isEmpty() && !state.facesBusy) return
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
            if (state.facesBusy) {
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.open(Screen.People) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(68.dp).clip(CircleShape).background(Lumi.Surface), contentAlignment = Alignment.Center) {
                        Text("${state.facesLooked * 100 / state.facesTotal.coerceAtLeast(1)} %", style = LabelStyle, color = Lumi.Accent)
                    }
                    Text("Buscando…", style = LabelStyle.copy(fontSize = 12.sp), color = Lumi.Muted)
                }
            }
        }
    }
}

/** Todas las personas, con su cara, su nombre y cuántas fotos tiene. Arriba, sus grupos y cómo ordenarlas. */
@Composable
fun PeopleScreen(state: UiState, vm: LumiViewModel) {
    var creating by remember { mutableStateOf(false) }
    val suggestions by produceState(0, state.people) { value = vm.mergeSuggestions().size }
    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Personas", if (state.people.isEmpty()) "" else countText(state.people.size, "persona", "personas"), onBack = { vm.back() })
        if (state.facesBusy) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Buscando caras: ${formatCount(state.facesLooked)} de ${formatCount(state.facesTotal)} fotos con gente" +
                        if (state.facesEta > 0) " · unos ${state.facesEta} min" else "",
                    style = SmallStyle,
                )
                LinearProgressIndicator(
                    progress = { state.facesLooked.toFloat() / state.facesTotal }, color = Lumi.Accent, trackColor = Lumi.Line,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                )
            }
        } else if (!state.facesOn) {
            Text("Búsqueda de caras en pausa. Se sigue desde Ajustes.", style = SmallStyle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (state.people.isEmpty()) {
            EmptyMessage(
                if (state.facesBusy) "Buscando caras…" else "Aún no hay personas",
                "Lumi junta las caras que se repiten en al menos tres fotos. Se hace en el teléfono, empezando por las fotos más recientes, y se detiene mientras lo usas.",
                icon = PersonIcon,
            )
            return@Column
        }
        if (suggestions > 0) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Lumi.Accent.copy(alpha = 0.15f)).clickable { vm.open(Screen.MergePeople) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("¿Son la misma persona?", style = LabelStyle.copy(fontSize = 15.sp))
                    Text(countText(suggestions, "pareja por revisar", "parejas por revisar"), style = SmallStyle)
                }
                Text("Revisar", style = LabelStyle, color = Lumi.Accent)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeopleOrder.entries.forEach { option -> Pick2(option.label, state.peopleOrder == option) { vm.setPeopleOrder(option) } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.circles.keys.forEach { name -> Pick2(name, false) { vm.open(Screen.Circle(name)) } }
            Pick2("+ Grupo (Familia, Amigos…)", false) { creating = true }
        }
        LazyVerticalGrid(
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
    if (creating) CircleDialog(state, vm, null) { creating = false }
}

@Composable
private fun Pick2(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, style = LabelStyle, color = if (on) Lumi.OnAccent else Lumi.Ink,
        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Surface).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Crear o cambiar un grupo de personas: nombre y quién está. */
@Composable
private fun CircleDialog(state: UiState, vm: LumiViewModel, editing: String?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(editing.orEmpty()) }
    var members by remember { mutableStateOf(editing?.let { vm.circles[it] }.orEmpty()) }
    val choices = state.people.filter { it.name != null } + state.people.filter { it.name == null }.take(12)
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Lumi.Surface,
        title = { Text(if (editing == null) "Nuevo grupo" else "Cambiar el grupo", style = HeadingStyle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text("Familia, Amigos, Trabajo…") })
                Text("Quién está", style = SmallStyle)
                Column(Modifier.height(260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    choices.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { person ->
                                val on = person.key in members
                                Column(
                                    Modifier.width(62.dp).clip(RoundedCornerShape(12.dp))
                                        .clickable { members = if (on) members - person.key else members + person.key }.padding(2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    FaceAvatar(person, state, 50.dp, if (on) Modifier.border(3.dp, Lumi.Accent, CircleShape) else Modifier)
                                    Text(person.name ?: "Sin nombre", style = SmallStyle.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (on) Lumi.Accent else Lumi.Muted)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "Guardar", style = LabelStyle, color = if (name.isBlank() || members.isEmpty()) Lumi.Muted else Lumi.Accent,
                modifier = Modifier.clip(CircleShape).clickable(enabled = name.isNotBlank() && members.isNotEmpty()) {
                    vm.saveCircle(name, members, editing)
                    onDone()
                }.padding(12.dp),
            )
        },
        dismissButton = {
            Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDone).padding(12.dp))
        },
    )
}

/** Un grupo de personas como álbum que se llena solo. Se puede pedir que salgan al menos dos juntas. */
@Composable
fun CircleScreen(screen: Screen.Circle, state: UiState, vm: LumiViewModel, actions: Actions, link: GridLink) {
    val members = vm.circles[screen.name]
    if (members == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    var together by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val people = state.people.filter { it.key in members }
    val ids = remember(people, together) {
        val count = HashMap<Long, Int>()
        people.forEach { p -> p.photos.forEach { count.merge(it, 1, Int::plus) } }
        count.filterValues { !together || it >= 2 }.keys.toHashSet()
    }
    val source = Source.Ids(ids)
    val items = remember(state.items, state.itemSort, ids) { state.itemsFor(source) }
    ItemsScreen(
        title = screen.name,
        emptyTitle = if (together) "Nunca salen juntas" else "Aún sin fotos",
        emptyText = if (together) "Quita «Al menos dos juntas» para ver las de cada una." else "Las fotos aparecerán cuando Lumi encuentre sus caras.",
        items = items, source = source, state = state, vm = vm, actions = actions, link = link,
        extra = {
            Box {
                BarIcon(Icons.Filled.MoreVert, "Opciones", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = Lumi.Surface) {
                    DropdownMenuItem({ Text("Cambiar el grupo") }, { menu = false; editing = true })
                    DropdownMenuItem({ Text("Vídeo con música") }, { menu = false; vm.open(Screen.MemoryVideo(screen.name, items.map { it.id })) })
                    DropdownMenuItem({ Text("Borrar el grupo") }, { menu = false; vm.deleteCircle(screen.name); vm.back() })
                }
            }
        },
        hero = {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { person ->
                        Column(Modifier.width(60.dp).clickable { vm.open(Screen.Person(person.key)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            FaceAvatar(person, state, 52.dp)
                            Text(person.name ?: "Sin nombre", style = SmallStyle.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pick2("Todas sus fotos", !together) { together = false }
                    Pick2("Al menos dos juntas", together) { together = true }
                }
            }
        },
    )
    if (editing) CircleDialog(state, vm, screen.name) { editing = false }
}

/** Las fotos de una persona, con su cara arriba y lo que se puede hacer con ella. */
@Composable
fun PersonScreen(screen: Screen.Person, state: UiState, vm: LumiViewModel, actions: Actions, link: GridLink) {
    val person = state.people.firstOrNull { it.key == screen.key }
    if (person == null) {
        // Puede que aún se esté recalculando; si no aparece, se vuelve atrás.
        LaunchedEffect(state.people) {
            delay(1500)
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
    var covers by remember { mutableStateOf(false) }
    val doubts by produceState(0, person) { value = vm.doubtsOf(person).size }
    val title = person.name ?: "Sin nombre"

    ItemsScreen(
        title = title,
        emptyTitle = "No quedan fotos",
        emptyText = "Se han movido o borrado.",
        items = items, source = source, state = state, vm = vm, actions = actions, link = link,
        extra = {
            Box {
                BarIcon(Icons.Filled.MoreVert, "Opciones", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = Lumi.Surface) {
                    DropdownMenuItem({ Text(if (person.name == null) "Ponerle nombre" else "Cambiar el nombre") }, { menu = false; naming = true })
                    DropdownMenuItem({ Text("Elegir la cara de portada") }, { menu = false; covers = true })
                    DropdownMenuItem({ Text(if (person.pinned) "Quitar de arriba" else "Fijar arriba") }, { menu = false; vm.setPersonPinned(person, !person.pinned) })
                    if (state.people.any { it.name != null && it.key != person.key }) {
                        DropdownMenuItem({ Text("Fotos con otra persona") }, { menu = false; together = true })
                    }
                    DropdownMenuItem({ Text("Cómo ha crecido") }, { menu = false; vm.open(Screen.Growing(person.key)) })
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
                FaceAvatar(person, state, 120.dp, Modifier.clickable { covers = true })
                Text(title, style = TitleStyle.copy(fontSize = 28.sp), color = if (person.name == null) Lumi.Muted else Lumi.Ink)
                Text(countText(items.size, "foto", "fotos"), style = SmallStyle)
                if (person.name != null) {
                    Text("Mantén pulsada una foto para decir que no es ${person.name}.", style = SmallStyle, modifier = Modifier.padding(horizontal = 24.dp))
                }
                if (person.name == null) {
                    PillButton("Ponerle nombre", onClick = { naming = true }, modifier = Modifier.padding(top = 6.dp))
                } else if (doubts > 0) {
                    Row(
                        Modifier.padding(top = 6.dp).clip(CircleShape).background(Lumi.Accent.copy(alpha = 0.15f))
                            .clickable { vm.open(Screen.Doubts(person.key)) }.padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text("¿Es ${person.name}? ${countText(doubts, "cara por revisar", "caras por revisar")}", style = LabelStyle, color = Lumi.Accent)
                    }
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

    if (naming) NameDialog(person.name.orEmpty(), state.people.mapNotNull { it.name }.distinct().filter { it != person.name }, { naming = false }) { vm.namePerson(person, it) }
    if (covers) {
        ModalBottomSheet(onDismissRequest = { covers = false }, containerColor = Lumi.Surface) {
            Text("Cara de portada", style = HeadingStyle, modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
            // Las caras más grandes primero: son las que mejor se ven en un círculo.
            val faces = remember(person) { person.faces.sortedByDescending { (it.right - it.left) * (it.bottom - it.top) }.take(60) }
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(faces, key = { it.key }) { face ->
                    FaceCircle(
                        face, state, 60.dp,
                        Modifier.clickable { covers = false; vm.setPersonCover(person, face) }
                            .then(if (face.key == person.cover.key) Modifier.border(3.dp, Lumi.Accent, CircleShape) else Modifier),
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
        }
    }
    if (together) {
        AlertDialog(
            onDismissRequest = { together = false },
            containerColor = Lumi.Surface,
            title = { Text("Con quién", style = HeadingStyle) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

/** Poner o cambiar un nombre. Elegir uno que ya existe junta a las dos personas. */
@Composable
fun NameDialog(current: String, others: List<String>, onDismiss: () -> Unit, onName: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text("¿Quién es?", style = HeadingStyle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it.take(40) }, singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text("Nombre") })
                val matches = others.filter { text.isBlank() || it.contains(text.trim(), ignoreCase = true) }.take(8)
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
                    onDismiss()
                    onName(text)
                }.padding(12.dp),
            )
        },
        dismissButton = {
            Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(12.dp))
        },
    )
}

/**
 * «¿Son la misma persona?», en tarjetas que se deslizan: a la derecha, sí (se juntan); a la
 * izquierda, no (no se vuelve a preguntar). También con los botones de abajo.
 */
@Composable
fun MergePeopleScreen(state: UiState, vm: LumiViewModel) {
    val pairs by produceState<List<Pair<Person, Person>>?>(null) { value = vm.mergeSuggestions() }
    var at by remember { mutableIntStateOf(0) }
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    val list = pairs
    val pair = list?.getOrNull(at)

    fun answer(same: Boolean) {
        val (a, b) = pair ?: return
        scope.launch {
            offset.animateTo(if (same) width * 1.3f else -width * 1.3f, androidx.compose.animation.core.tween(220))
            vm.answerMerge(a, b, same)
            at++
            offset.snapTo(0f)
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("¿Son la misma persona?", list?.let { if (at < it.size) "Quedan ${it.size - at}" else "" }.orEmpty(), onBack = { vm.back() })
        if (list != null && pair == null) {
            EmptyMessage("Todo revisado", "No quedan personas que se parezcan tanto como para preguntar.")
            return@Column
        }
        if (pair == null) return@Column
        val (a, b) = pair
        Text(
            "Desliza a la derecha si son la misma persona y a la izquierda si no.",
            style = SmallStyle, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp).onSizeChanged { width = it.width }, contentAlignment = Alignment.Center) {
            // La siguiente pareja asoma detrás.
            list.getOrNull(at + 1)?.let {
                Box(Modifier.fillMaxWidth(0.92f).fillMaxSize(0.92f).clip(RoundedCornerShape(28.dp)).background(Lumi.Surface.copy(alpha = 0.5f)))
            }
            val tilt = offset.value / width.coerceAtLeast(1)
            Column(
                Modifier.fillMaxSize()
                    .graphicsLayerCompat(offset.value, tilt * 12f)
                    .clip(RoundedCornerShape(28.dp)).background(Lumi.Surface)
                    .pointerInput(pair) {
                        detectDragGestures(
                            onDragEnd = {
                                when {
                                    offset.value > size.width * 0.28f -> answer(true)
                                    offset.value < -size.width * 0.28f -> answer(false)
                                    else -> scope.launch { offset.animateTo(0f) }
                                }
                            },
                        ) { change, drag ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + drag.x) }
                        }
                    }
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf(a, b).forEachIndexed { i, person ->
                        if (i == 1) Text("=", style = TitleStyle.copy(fontSize = 30.sp), color = Lumi.Accent)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FaceAvatar(person, state, 104.dp)
                            Text(person.name ?: "Sin nombre", style = LabelStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(countText(person.photos.size, "foto", "fotos"), style = SmallStyle)
                        }
                    }
                }
                // Unas cuantas caras de cada una, para comparar mejor.
                listOf(a, b).forEach { person ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        person.faces.shuffled(java.util.Random(person.group.id)).take(5).forEach { FaceCircle(it, state, 44.dp) }
                    }
                }
                // Lo que pasará al soltar, según hacia dónde se arrastre.
                Text(
                    when {
                        tilt > 0.12f -> "Sí: juntar"
                        tilt < -0.12f -> "No son la misma"
                        else -> "Juntarlas mejora la búsqueda y los recuerdos."
                    },
                    style = LabelStyle, color = if (tilt > 0.12f) Lumi.Accent else if (tilt < -0.12f) Lumi.Danger else Lumi.Muted,
                )
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("No", onClick = { answer(false) }, modifier = Modifier.weight(1f), primary = false)
            PillButton("Sí, juntar", onClick = { answer(true) }, modifier = Modifier.weight(1f))
        }
    }
}

private fun Modifier.graphicsLayerCompat(x: Float, degrees: Float): Modifier = this.graphicsLayer {
    translationX = x
    rotationZ = degrees
}

/** «¿Es Lucía?»: cada cara dudosa sobre su foto, recuadrada. */
@Composable
fun DoubtsScreen(screen: Screen.Doubts, state: UiState, vm: LumiViewModel) {
    val person = state.people.firstOrNull { it.key == screen.key }
    if (person == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val doubts by produceState<List<com.lumi.galeria.data.Doubt>?>(null, person.key) { value = vm.doubtsOf(person) }
    var at by remember { mutableIntStateOf(0) }
    val name = person.name ?: "esta persona"
    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        val list = doubts
        ScreenHeader("¿Es $name?", list?.let { if (at < it.size) "Quedan ${it.size - at}" else "" }.orEmpty(), onBack = { vm.back() })
        if (list == null) return@Column
        val doubt = list.getOrNull(at)
        if (doubt == null) {
            EmptyMessage("Todo revisado", "Gracias: $name queda más afinada.")
            return@Column
        }
        val item = state.items.firstOrNull { it.id == doubt.face.photo }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black)) {
            if (item != null) {
                AsyncImage(Thumb(item.uri, 1024, item.modified), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                // La cara, recuadrada sobre la foto ajustada a la caja.
                val aspect = item.shownWidth.toFloat() / item.shownHeight.coerceAtLeast(1)
                Canvas(Modifier.fillMaxSize()) {
                    val fw = minOf(size.width, size.height * aspect)
                    val fh = fw / aspect
                    val ox = (size.width - fw) / 2
                    val oy = (size.height - fh) / 2
                    val f = doubt.face
                    drawRoundRect(
                        Color(0xFFB9A8FF), Offset(ox + f.left * fw - 6, oy + f.top * fh - 6),
                        Size((f.right - f.left) * fw + 12, (f.bottom - f.top) * fh + 12),
                        androidx.compose.ui.geometry.CornerRadius(14f), style = Stroke(3.dp.toPx()),
                    )
                }
            }
            FaceCircle(doubt.face, state, 84.dp, Modifier.align(Alignment.BottomEnd).padding(12.dp).border(3.dp, Color.White, CircleShape))
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(if (doubt.inside) "No es $name" else "No", onClick = { vm.answerDoubt(doubt, yes = false); at++ }, modifier = Modifier.weight(1f), primary = false)
            PillButton("Sí es $name", onClick = { vm.answerDoubt(doubt, yes = true); at++ }, modifier = Modifier.weight(1f))
        }
    }
}

/** Cómo ha crecido: su cara a lo largo del tiempo, siempre en el mismo sitio, en un GIF. */
@Composable
fun GrowingScreen(screen: Screen.Growing, state: UiState, vm: LumiViewModel) {
    val person = state.people.firstOrNull { it.key == screen.key }
    if (person == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val frames by produceState<List<Bitmap>?>(null, person.key) { value = vm.growingFrames(person) }
    var speed by remember { mutableIntStateOf(1) }
    val delays = listOf(80, 45, 25)
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(frames, speed) {
        val list = frames ?: return@LaunchedEffect
        while (list.size > 1) {
            delay(delays[speed] * 10L)
            shown = (shown + 1) % list.size
        }
    }
    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Cómo ha crecido", person.name ?: "", onBack = { vm.back() })
        val list = frames
        when {
            list == null -> EmptyMessage("Preparando sus caras…", "Lumi busca una foto de cada época y pone la cara siempre en el mismo sitio.", Modifier.weight(1f))
            list.size < 2 -> EmptyMessage("Hace falta más tiempo", "Esta persona sale en fotos de muy pocas fechas distintas.", Modifier.weight(1f))
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Image(
                        list[shown % list.size].asImageBitmap(), null,
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    )
                }
                if (vm.making != null) {
                    LinearProgressIndicator(
                        progress = { (vm.making ?: 0) / 100f }, color = Lumi.Accent, trackColor = Lumi.Line,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(6.dp).clip(CircleShape),
                    )
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(countText(list.size, "época", "épocas"), style = SmallStyle)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Lento", "Normal", "Rápido").forEachIndexed { i, label -> Pick2(label, speed == i) { speed = i } }
                    }
                    PillButton(
                        "Guardar GIF",
                        onClick = { vm.saveGrowing(person, list, delays[speed]) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = vm.making == null,
                    )
                }
            }
        }
    }
}
