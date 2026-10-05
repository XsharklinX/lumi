package com.lumi.galeria.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.Album
import com.lumi.galeria.data.AlbumSort
import com.lumi.galeria.data.isWritableAlbumPath
import com.lumi.galeria.data.ItemSort
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.dayTitle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    val favorites = remember(state.items, state.favorites) { state.itemsFor(Source.Favorites) }
    val trips = state.autoAlbums.filter { it.isTrip }
    val things = state.autoAlbums.filter { !it.isTrip }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    // Álbum sobre el que se mantuvo el dedo; enseña sus opciones.
    var pressed by remember { mutableStateOf<Long?>(null) }
    var renaming by remember { mutableStateOf<Album?>(null) }
    var renameText by remember { mutableStateOf("") }

    /** Poner o quitar el candado siempre pasa por la contraseña. */
    fun toggleLock(bucketId: Long, locked: Boolean) {
        if (!actions.canUnlock()) vm.say("Para bloquear álbumes, pon un bloqueo en el teléfono o crea un PIN de Lumi en Ajustes")
        else actions.unlock { vm.setAlbumLocked(bucketId, !locked) }
    }

    Box(Modifier.fillMaxSize().background(Lumi.Bg)) {
        Column {
            // Lupa y tuerca en el mismo sitio que en Fotos.
            ScreenHeader("Álbumes", "") {
                BarIcon(Icons.Filled.Search, "Buscar", { vm.open(Screen.Search) })
                BarIcon(Icons.Filled.Settings, "Ajustes", { vm.open(Screen.Settings) })
            }
            Row(Modifier.padding(start = 12.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Nuevo álbum",
                    style = LabelStyle,
                    color = Lumi.OnAccent,
                    modifier = Modifier.clip(CircleShape).background(Lumi.Accent).clickable { newName = ""; creating = true }.padding(horizontal = 16.dp, vertical = 9.dp),
                )
                Spacer(Modifier.weight(1f))
                SortMenu(AlbumSort.entries, state.albumSort, { it.label }, vm::setAlbumSort)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 130.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Dos accesos fijos: favoritas y carpeta privada.
                item(key = "accesos", span = { GridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AlbumCard("Favoritas", countText(favorites.size, "foto", "fotos"), favorites.firstOrNull(), Icons.Filled.Favorite, Modifier.weight(1f)) {
                            vm.open(Screen.Favorites)
                        }
                        AlbumCard("Privada", "Con tu huella", null, Icons.Filled.Lock, Modifier.weight(1f)) {
                            if (state.vaultOpen) vm.open(Screen.Vault)
                            else actions.unlock {
                                vm.unlockVault()
                                vm.open(Screen.Vault)
                            }
                        }
                    }
                }

                if (trips.isNotEmpty()) {
                    item(key = "t-viajes", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Viajes") }
                    items(trips, key = { it.key }) { album ->
                        AlbumCard(album.title, "${album.subtitle} · ${album.items.size}", album.items.first(), null) {
                            vm.open(Screen.Items(album.title, Source.Auto(album.key)))
                        }
                    }
                }
                if (things.isNotEmpty()) {
                    item(key = "t-cosas", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Cosas") }
                    items(things, key = { it.key }) { album ->
                        AlbumCard(album.title, countText(album.items.size, "foto", "fotos"), album.items.first(), null) {
                            vm.open(Screen.Items(album.title, Source.Auto(album.key)))
                        }
                    }
                }
                item(key = "t-carpetas", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle("Carpetas")
                        Text("Mantén pulsado un álbum para bloquearlo u ocultarlo.", style = SmallStyle, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
                    }
                }
                items(state.albums.filter { state.showHidden || !it.hidden }, key = { it.bucketId }) { album ->
                    Box {
                        if (album.locked) {
                            LockedAlbumCard(
                                album.name,
                                onClick = {
                                    actions.unlock {
                                        vm.unlockAlbums()
                                        vm.open(Screen.Album(album.bucketId))
                                    }
                                },
                                onLongClick = { pressed = album.bucketId },
                            )
                        } else {
                            AlbumCard(
                                album.name,
                                countText(album.count, "elemento", "elementos") + (if (album.pinned) " · fijado" else "") + if (album.hidden) " · oculto" else "",
                                album.cover, null,
                                onLongClick = { pressed = album.bucketId },
                            ) { vm.open(Screen.Album(album.bucketId)) }
                        }
                        DropdownMenu(pressed == album.bucketId, { pressed = null }, containerColor = Lumi.Surface) {
                            DropdownMenuItem(
                                { Text(if (album.locked) "Quitar el candado" else "Bloquear con candado") },
                                {
                                    pressed = null
                                    toggleLock(album.bucketId, album.locked)
                                },
                            )
                            DropdownMenuItem(
                                { Text(if (album.pinned) "Dejar de fijar" else "Fijar arriba") },
                                {
                                    pressed = null
                                    vm.setAlbumPinned(album.bucketId, !album.pinned)
                                },
                            )
                            if (!album.locked) {
                                DropdownMenuItem(
                                    { Text(if (album.hidden) "Dejar de ocultar" else "Ocultar álbum") },
                                    {
                                        pressed = null
                                        vm.setAlbumHidden(album.bucketId, !album.hidden)
                                    },
                                )
                                DropdownMenuItem(
                                    { Text("Cambiar el nombre") },
                                    {
                                        pressed = null
                                        if (isWritableAlbumPath(album.path)) {
                                            renameText = album.name
                                            renaming = album
                                        } else {
                                            vm.say("Android no deja cambiar el nombre de esta carpeta")
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    { Text("Borrar el álbum", color = Lumi.Danger) },
                                    {
                                        pressed = null
                                        // Sus fotos van a la papelera, donde siguen 30 días.
                                        actions.trash(state.items.filter { it.bucketId == album.bucketId }) {}
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Dock(Screen.Albums, vm::switchTab, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp))
    }

    renaming?.let { album ->
        val clean = renameText.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = Lumi.Surface,
            title = { Text("Cambiar el nombre", style = HeadingStyle) },
            text = { OutlinedTextField(renameText, { renameText = it.take(40) }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
            confirmButton = {
                Text(
                    "Guardar", style = LabelStyle, color = if (clean.isEmpty()) Lumi.Muted else Lumi.Accent,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = clean.isNotEmpty()) {
                        renaming = null
                        // Cambiar el nombre de un álbum es llevar sus fotos a una carpeta con el nombre nuevo.
                        val parent = album.path.trimEnd('/').substringBeforeLast('/', "")
                        val path = if (parent.isEmpty()) "Pictures/$clean/" else "$parent/$clean/"
                        val photos = state.items.filter { it.bucketId == album.bucketId }
                        actions.write(photos) { vm.moveTo(photos, path, clean) }
                    }.padding(12.dp),
                )
            },
            dismissButton = {
                Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { renaming = null }.padding(12.dp))
            },
        )
    }

    if (creating) {
        val clean = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
        AlertDialog(
            onDismissRequest = { creating = false },
            containerColor = Lumi.Surface,
            title = { Text("Álbum nuevo", style = HeadingStyle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newName, { newName = it.take(40) }, placeholder = { Text("Nombre") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                    Text("Después eliges qué fotos van dentro. Cuando exista, mantenlo pulsado para bloquearlo.", style = SmallStyle)
                }
            },
            confirmButton = {
                Text(
                    "Elegir fotos", style = LabelStyle, color = if (clean.isEmpty()) Lumi.Muted else Lumi.Accent,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = clean.isNotEmpty()) {
                        creating = false
                        vm.open(Screen.NewAlbum(clean))
                    }.padding(12.dp),
                )
            },
            dismissButton = {
                Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { creating = false }.padding(12.dp))
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = HeadingStyle.copy(fontSize = 20.sp), modifier = Modifier.padding(start = 6.dp, top = 12.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    name: String,
    subtitle: String,
    cover: MediaItem?,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(22.dp)).background(Lumi.Surface),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) MediaThumb(cover, 512, Modifier.fillMaxSize())
            else if (icon != null) Icon(icon, null, Modifier.size(34.dp), tint = Lumi.Accent)
        }
        Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            Text(name, style = HeadingStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = SmallStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Álbum con candado. No enseña ni una foto ni cuántas hay: toda la tarjeta es una puerta cerrada
 * con el candado en el centro, y solo se abre con la contraseña.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LockedAlbumCard(name: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(22.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Lumi.Accent.copy(alpha = 0.55f), Lumi.Accent.copy(alpha = 0.16f)))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Lumi.Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, "Bloqueado", Modifier.size(32.dp), tint = Lumi.OnAccent)
                }
                Text("Bloqueado", style = LabelStyle, color = Lumi.Ink)
            }
        }
        Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            Text(name, style = HeadingStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Toca y pon la contraseña para verlo", style = SmallStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Tras ponerle nombre a un álbum nuevo, aquí se eligen las fotos que van dentro. */
@Composable
fun NewAlbumScreen(screen: Screen.NewAlbum, state: UiState, vm: LumiViewModel, actions: Actions) {
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    val chosen = state.tiles.filter { it.id in selection }
    val path = "Pictures/${screen.name}/"

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(screen.name, "Toca las fotos que van en este álbum", onBack = { vm.back() })
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.tiles, key = { it.id }) { item ->
                PhotoTile(
                    item = item,
                    px = 320,
                    selected = item.id in selection,
                    modifier = Modifier.aspectRatio(1f).clickable {
                        selection = if (item.id in selection) selection - item.id else selection + item.id
                    },
                )
            }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                if (chosen.isEmpty()) "Elige fotos" else "Mover ${chosen.size} aquí",
                onClick = {
                    // Mover fotos hechas con otra app necesita un permiso del sistema.
                    actions.write(chosen) { vm.moveTo(chosen, path, screen.name) }
                    vm.back()
                },
                modifier = Modifier.weight(1.4f),
                enabled = chosen.isNotEmpty(),
            )
            PillButton(
                "Copiar",
                onClick = {
                    vm.copyTo(chosen, path, screen.name)
                    vm.back()
                },
                modifier = Modifier.weight(1f),
                primary = false,
                enabled = chosen.isNotEmpty(),
            )
        }
    }
}

@Composable
fun AlbumScreen(screen: Screen.Album, state: UiState, vm: LumiViewModel, actions: Actions, link: GridLink) {
    val source = Source.Album(screen.bucketId)
    val items = remember(state.items, state.lockedItems, state.itemSort, screen.bucketId) { state.itemsFor(source) }
    val album = state.albums.firstOrNull { it.bucketId == screen.bucketId }
    // Si el álbum tiene candado y se cerró (por salir de la app), aquí no se queda nadie mirando.
    if (album != null && album.locked && !state.albumsUnlocked) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    var menu by remember { mutableStateOf(false) }
    ItemsScreen(
        title = album?.name ?: "Álbum",
        emptyTitle = "Este álbum está vacío",
        emptyText = "Sus fotos se han movido o borrado.",
        items = items, source = source, state = state, vm = vm, actions = actions, link = link,
        extra = {
            if (album != null) {
                Box {
                    BarIcon(Icons.Filled.MoreVert, "Opciones del álbum", { menu = true })
                    DropdownMenu(menu, { menu = false }, containerColor = Lumi.Surface) {
                        DropdownMenuItem(
                            { Text(if (album.locked) "Quitar el candado" else "Poner candado") },
                            {
                                menu = false
                                actions.unlock { vm.setAlbumLocked(album.bucketId, !album.locked) }
                            },
                        )
                        DropdownMenuItem(
                            { Text(if (album.hidden) "Dejar de ocultar" else "Ocultar álbum") },
                            {
                                menu = false
                                vm.setAlbumHidden(album.bucketId, !album.hidden)
                                if (!album.hidden && !state.showHidden) {
                                    vm.say("Álbum oculto. Se puede volver a ver desde Ajustes.")
                                    vm.back()
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun FavoritesScreen(state: UiState, vm: LumiViewModel, actions: Actions, link: GridLink) {
    val items = remember(state.items, state.favorites, state.itemSort) { state.itemsFor(Source.Favorites) }
    ItemsScreen(
        title = "Favoritas",
        emptyTitle = "Aún no tienes favoritas",
        emptyText = "Toca el corazón al ver una foto y la tendrás siempre aquí.",
        items = items, source = Source.Favorites, state = state, vm = vm, actions = actions, link = link,
    )
}

/** Viajes, cosas, recuerdos y grupos del mapa: cualquier lista de fotos con título. */
@Composable
fun AnyItemsScreen(screen: Screen.Items, state: UiState, vm: LumiViewModel, actions: Actions, link: GridLink) {
    val items = remember(state.items, state.autoAlbums, state.memory, state.itemSort, screen.source) { state.itemsFor(screen.source) }
    ItemsScreen(
        title = screen.title,
        emptyTitle = "Aquí ya no hay fotos",
        emptyText = "Se han movido o borrado.",
        items = items, source = screen.source, state = state, vm = vm, actions = actions, link = link,
    )
}

/** Cuadrícula sencilla de una lista de fotos, con selección y paso al visor. */
@Composable
private fun ItemsScreen(
    title: String,
    emptyTitle: String,
    emptyText: String,
    items: List<MediaItem>,
    source: Source,
    state: UiState,
    vm: LumiViewModel,
    actions: Actions,
    link: GridLink,
    extra: (@Composable () -> Unit)? = null,
) {
    val grid = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    val currentItems by rememberUpdatedState(items)
    val currentSelection by rememberUpdatedState(selection)

    val container = remember { arrayOfNulls<LayoutCoordinates>(1) }

    BackHandler(selection.isNotEmpty()) { selection = emptySet() }
    SideEffect {
        link.boundsOf = { id -> tileBounds(grid, container[0], id) }
        link.reveal = { id ->
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.index == index }) grid.scrollToItem(maxOf(index - 6, 0))
        }
    }

    Box(Modifier.fillMaxSize().background(Lumi.Bg)) {
        Column {
            ScreenHeader(title, countText(items.size, "elemento", "elementos"), onBack = { vm.back() }) {
                SortMenu(ItemSort.entries, state.itemSort, { it.label }, vm::setItemSort)
                extra?.invoke()
            }
            if (items.isEmpty()) {
                EmptyMessage(emptyTitle, emptyText)
            } else {
                Box {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = grid,
                        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 130.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize().onGloballyPositioned { container[0] = it }.dragSelect(
                            grid = grid,
                            idAt = { currentItems.getOrNull(it)?.id },
                            selection = { currentSelection },
                            onChange = { next ->
                                if (next.size != currentSelection.size) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selection = next
                            },
                        ),
                    ) {
                        items(items, key = { it.id }) { item ->
                            PhotoTile(
                                item = item,
                                px = 320,
                                favorite = source != Source.Favorites && item.id in state.favorites,
                                selected = if (selection.isEmpty()) null else item.id in selection,
                                modifier = Modifier.aspectRatio(1f).clickable {
                                    if (selection.isNotEmpty()) {
                                        selection = if (item.id in selection) selection - item.id else selection + item.id
                                    } else {
                                        vm.open(Screen.Viewer(source, item.id, link.boundsOf(item.id)))
                                    }
                                },
                            )
                        }
                    }
                    FastScroller(
                        grid, items.size,
                        label = { index -> currentItems.getOrNull(index)?.let { dayTitle(it.date) }.orEmpty() },
                        modifier = Modifier.fillMaxSize().padding(bottom = 110.dp).navigationBarsPadding(),
                    )
                }
            }
        }
        if (selection.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)) {
                SelectionBar(items.filter { it.id in selection }, state, vm, actions) { selection = emptySet() }
            }
        }
    }
}
