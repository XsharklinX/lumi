package com.lumi.galeria.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import com.lumi.galeria.monthTitle
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.lumi.galeria.agoText
import com.lumi.galeria.formatCount
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
    // Carpetas de álbumes: a cuál se mete uno, y la que se mantuvo pulsada.
    var folderFor by remember { mutableStateOf<Album?>(null) }
    var pressedFolder by remember { mutableStateOf<Long?>(null) }
    var renamingFolder by remember { mutableStateOf<com.lumi.galeria.AlbumFolder?>(null) }
    var smartMenu by remember { mutableStateOf<Long?>(null) }
    var renamingSmart by remember { mutableStateOf<com.lumi.galeria.data.SmartAlbum?>(null) }

    /** Poner o quitar el candado siempre pasa por la contraseña. */
    fun toggleLock(bucketId: Long, locked: Boolean) {
        if (!actions.canUnlock()) vm.say("Para bloquear álbumes, pon un bloqueo en el teléfono o crea un PIN de Lumi en Ajustes")
        else actions.unlock { vm.setAlbumLocked(bucketId, !locked) }
    }

    val inFolders = vm.albumFolders.flatMapTo(HashSet()) { it.albums }
    // Al bajar por los álbumes el título grande se encoge.
    val albumsGrid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val albumsScrolled by remember { androidx.compose.runtime.derivedStateOf { albumsGrid.firstVisibleItemIndex > 0 || albumsGrid.firstVisibleItemScrollOffset > 40 } }
    val albumsCollapse by androidx.compose.animation.core.animateFloatAsState(if (albumsScrolled) 1f else 0f, androidx.compose.animation.core.tween(260), label = "titulo")
    val shown = state.albums.filter { (state.showHidden || !it.hidden) && it.bucketId !in inFolders }
    val pinned = shown.filter { it.pinned }
    val rest = shown.filter { !it.pinned }
    // Fecha de lo último que entró en cada carpeta.
    val latest = remember(state.items) { HashMap<Long, Long>().apply { state.items.forEach { merge(it.bucketId, it.date, ::maxOf) } } }
    // Las cuatro fotos más recientes de cada carpeta, para la vista "Con fotos".
    val recent = remember(state.items, state.albumView) {
        if (state.albumView != AlbumView.STRIP) emptyMap()
        else HashMap<Long, ArrayList<MediaItem>>().apply {
            state.items.forEach { item -> getOrPut(item.bucketId) { ArrayList() }.let { if (it.size < 4) it += item } }
        }
    }

    fun openAlbum(album: Album) {
        if (!album.locked) vm.open(Screen.Album(album.bucketId))
        else actions.unlock {
            vm.unlockAlbums()
            vm.open(Screen.Album(album.bucketId))
        }
    }

    val scanDocument = rememberDocumentScanner(vm)

    // Opciones de un álbum al mantenerlo pulsado.
    val menu: @Composable (Album) -> Unit = { album ->
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
                    { Text("Meter en una carpeta") },
                    {
                        pressed = null
                        folderFor = album
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

    Box(Modifier.fillMaxSize().background(Lumi.Bg)) {
        Column {
            // Lupa y tuerca en el mismo sitio que en Fotos.
            ScreenHeader("Álbumes", "", collapse = albumsCollapse) {
                BarIcon(Icons.Filled.Search, "Buscar", { vm.openSearch() })
                BarIcon(Icons.Filled.Settings, "Ajustes", { vm.open(Screen.Settings) })
            }
            // Favoritas y Privada, pequeños: son accesos, no álbumes que haya que ver en grande.
            Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAccess("Favoritas", countText(favorites.size, "foto", "fotos"), Icons.Filled.Favorite, favorites.firstOrNull(), Modifier.weight(1f)) {
                    vm.open(Screen.Favorites)
                }
                QuickAccess("Documentos", "Escaneos y PDF", Icons.Filled.Edit, null, Modifier.weight(1f)) { vm.switchTab(Screen.Documents) }
                QuickAccess("Privada", "Con tu huella", Icons.Filled.Lock, null, Modifier.weight(1f)) { vm.openVault(actions.unlock) }
            }
            Row(Modifier.padding(start = 12.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NewButton { newName = ""; creating = true }
                ImportButton { vm.open(Screen.Import) }
                ViewSwitch(state.albumView, vm::setAlbumView)
                Spacer(Modifier.weight(1f))
                // «A mano» abre la pantalla para colocarlos; se puede volver a ella cuando se quiera.
                SortMenu(AlbumSort.entries, state.albumSort, { it.label }) { sort ->
                    if (sort == AlbumSort.MANUAL) vm.open(Screen.AlbumOrder) else vm.setAlbumSort(sort)
                }
            }
            val view = state.albumView
            val columns = if (view == AlbumView.GRID) 2 else 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(scaledColumns(columns)),
                state = albumsGrid,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 130.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Lo que arma Lumi va en una fila que se desliza de lado, para no alargar la pantalla.
                if (state.people.isNotEmpty() || (state.facesOn && state.facesLooked < state.facesTotal)) {
                    item(key = "personas", span = { GridItemSpan(maxLineSpan) }) { PeopleRow(state, vm) }
                }
                if (state.smartAlbums.isNotEmpty()) {
                    item(key = "inteligentes", span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionTitle("✦ Álbumes inteligentes")
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                state.smartAlbums.forEach { smart ->
                                    val found = remember(smart, state.items, state.index, state.notes, state.places, state.people) { state.itemsFor(Source.Smart(smart.id)) }
                                    val cover = found.firstOrNull()
                                    Box {
                                        if (cover != null) {
                                            LumiAlbumCard(smart.name, formatCount(found.size) + if (found.size == 1) " foto" else " fotos", cover, onLongClick = { smartMenu = smart.id }) {
                                                vm.open(Screen.Items(smart.name, Source.Smart(smart.id)))
                                            }
                                        } else {
                                            Column(
                                                Modifier.width(132.dp).height(150.dp).clip(RoundedCornerShape(18.dp)).background(Lumi.Surface).clickable { smartMenu = smart.id }.padding(12.dp),
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Text(smart.name, style = HeadingStyle.copy(fontSize = 14.sp))
                                                Text("Aún sin fotos", style = SmallStyle)
                                            }
                                        }
                                        DropdownMenu(smartMenu == smart.id, { smartMenu = null }, containerColor = Lumi.Surface) {
                                            DropdownMenuItem({ Text("Cambiar el nombre") }, { smartMenu = null; renamingSmart = smart })
                                            DropdownMenuItem({ Text("Quitar este álbum") }, { smartMenu = null; vm.deleteSmartAlbum(smart.id) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val reviewYear = defaultReviewYear(state.items)
                if (trips.isNotEmpty() || things.isNotEmpty() || reviewYear != null || state.items.any { it.isScreenshot }) {
                    item(key = "lumi", span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionTitle("Hechos por Lumi")
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                state.items.firstOrNull { it.isScreenshot }?.let { shot ->
                                    LumiAlbumCard("Capturas ordenadas", "Chats, compras, mapas…", shot) { vm.open(Screen.Screenshots) }
                                }
                                if (reviewYear != null) {
                                    val cover = state.items.firstOrNull { it.id in state.favorites && java.time.Instant.ofEpochMilli(it.date).atZone(java.time.ZoneId.systemDefault()).year == reviewYear }
                                        ?: state.items.firstOrNull { !it.isScreenshot && java.time.Instant.ofEpochMilli(it.date).atZone(java.time.ZoneId.systemDefault()).year == reviewYear }
                                    if (cover != null) {
                                        LumiAlbumCard(if (com.lumi.galeria.Lang.english) "Your $reviewYear" else "Tu $reviewYear", "Tu año en fotos", cover) {
                                            vm.open(Screen.YearReview(reviewYear))
                                        }
                                    }
                                }
                                (trips + things).forEach { album ->
                                    LumiAlbumCard(
                                        album.title,
                                        formatCount(album.items.size) + if (album.subtitle.isNotEmpty()) " · ${album.subtitle}" else "",
                                        album.cover,
                                    ) { vm.open(Screen.Items(album.title, Source.Auto(album.key))) }
                                }
                            }
                        }
                    }
                }
                item(key = "t-carpetas", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle("Tus álbumes")
                        Text("Mantén pulsado uno para fijarlo arriba, bloquearlo, ocultarlo o meterlo en una carpeta.", style = SmallStyle, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
                    }
                }
                items(vm.albumFolders, key = { "carpeta-${it.id}" }) { folder ->
                    val inside = folder.albums.mapNotNull { id -> state.albums.firstOrNull { it.bucketId == id } }
                    Box {
                        FolderTile(folder, inside, onLongClick = { pressedFolder = folder.id }) { vm.open(Screen.AlbumFolder(folder.id)) }
                        DropdownMenu(pressedFolder == folder.id, { pressedFolder = null }, containerColor = Lumi.Surface) {
                            DropdownMenuItem({ Text("Cambiar el nombre") }, { pressedFolder = null; renamingFolder = folder })
                            DropdownMenuItem({ Text("Deshacer la carpeta") }, { pressedFolder = null; vm.deleteFolder(folder.id) })
                        }
                    }
                }
                items(pinned + rest, key = { it.bucketId }) { album ->
                    // Un álbum con candado no dice ni cuántas fotos guarda.
                    val subtitle = if (album.locked) "Con candado" else
                        formatCount(album.count) + (latest[album.bucketId]?.let { " · " + agoText(it) } ?: "") +
                            (if (album.pinned) " · fijado" else "") + (if (album.hidden) " · oculto" else "") +
                            if (album.onCard in 1 until album.count) " · parte en la tarjeta" else ""
                    Box {
                        when (view) {
                            AlbumView.GRID -> AlbumTile(album.name, subtitle, album.cover, album.locked, { pressed = album.bucketId }, card = album.onCard > 0) { openAlbum(album) }
                            AlbumView.LIST -> AlbumRow(album.name, subtitle, if (album.locked) null else album.cover, { pressed = album.bucketId }, card = album.onCard > 0) { openAlbum(album) }
                            AlbumView.STRIP -> AlbumStrip(album.name, subtitle, recent[album.bucketId].orEmpty(), album.locked, { pressed = album.bucketId }, card = album.onCard > 0) { openAlbum(album) }
                        }
                        menu(album)
                    }
                }
            }
        }
        if (!LocalWide.current) Dock(Screen.Albums, vm::switchTab, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp), onCamera = actions.camera, onScan = scanDocument)
    }

    folderFor?.let { album -> FolderPickerSheet(vm, album) { folderFor = null } }
    renamingSmart?.let { smart ->
        FolderNameDialog("Cambiar el nombre", smart.name, onDismiss = { renamingSmart = null }) { name ->
            renamingSmart = null
            vm.renameSmartAlbum(smart.id, name)
        }
    }
    renamingFolder?.let { folder ->
        FolderNameDialog("Cambiar el nombre", folder.name, onDismiss = { renamingFolder = null }) { name ->
            renamingFolder = null
            vm.renameFolder(folder.id, name)
        }
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

/** Barra para pegar aquí lo que se copió o cortó en otro sitio. */
@Composable
private fun PasteBar(clip: com.lumi.galeria.Clip, album: Album?, vm: LumiViewModel, actions: Actions) {
    // Lo que ya está en este álbum no se pega otra vez.
    val items = clip.items.filter { it.bucketId != album?.bucketId }
    Row(
        Modifier.padding(horizontal = 12.dp).clip(CircleShape).background(Lumi.Surface).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            album == null || items.isEmpty() -> Text("Ya están en este álbum", style = LabelStyle, modifier = Modifier.padding(horizontal = 14.dp))
            !isWritableAlbumPath(album.path) -> Text("Android no deja pegar en esta carpeta", style = LabelStyle, modifier = Modifier.padding(horizontal = 14.dp))
            else -> PillButton(if (clip.move) "Mover ${items.size} aquí" else "Pegar ${items.size} aquí", {
                if (clip.move) actions.write(items) { vm.moveTo(items, album.path, album.name) } else vm.copyTo(items, album.path, album.name)
                vm.clip = null
            })
        }
        BarIcon(Icons.Filled.Close, "Cancelar", { vm.clip = null })
    }
}

/** Un álbum en la lista compacta: miniatura, nombre y una línea con lo que hay dentro. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(name: String, subtitle: String, cover: MediaItem?, onLongClick: (() -> Unit)?, card: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Lumi.Surface), contentAlignment = Alignment.Center) {
            if (cover != null) MediaThumb(cover, 160, Modifier.fillMaxSize())
            else Icon(Icons.Filled.Lock, "Bloqueado", Modifier.size(24.dp), tint = Lumi.Accent)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(name, style = HeadingStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (card) Icon(SdCardIcon, "En la tarjeta de memoria", Modifier.size(14.dp), tint = Lumi.Muted)
            }
            Text(subtitle, style = SmallStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
        bottom = vm.clip?.let { clip -> { PasteBar(clip, album, vm, actions) } },
        hero = { AlbumHero(album?.cover ?: items.firstOrNull(), album?.name ?: "Álbum", items, source, vm, actions) },
    )
}

@Composable
fun FavoritesScreen(state: UiState, vm: LumiViewModel, actions: Actions, link: GridLink) {
    val items = remember(state.items, state.favorites, state.itemSort) { state.itemsFor(Source.Favorites) }
    ItemsScreen(
        title = "Favoritas",
        emptyTitle = "Aún no tienes favoritas",
        emptyText = "Toca el corazón al ver una foto y la tendrás siempre aquí.",
        emptyIcon = HeartIcon,
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
        hero = { AlbumHero(items.firstOrNull(), screen.title, items, screen.source, vm, actions) },
    )
}

/**
 * La portada de un álbum: su foto en grande con el nombre, cuántas fotos tiene y de qué fechas, y
 * botones para verlo en presentación o compartirlo.
 */
@Composable
private fun AlbumHero(cover: MediaItem?, title: String, items: List<MediaItem>, source: Source, vm: LumiViewModel, actions: Actions) {
    val dates = remember(items) {
        if (items.isEmpty()) "" else {
            val zone = java.time.ZoneId.systemDefault()
            val first = java.time.YearMonth.from(java.time.Instant.ofEpochMilli(items.minOf { it.date }).atZone(zone))
            val last = java.time.YearMonth.from(java.time.Instant.ofEpochMilli(items.maxOf { it.date }).atZone(zone))
            if (first == last) monthTitle(first) else "${monthTitle(first)} – ${monthTitle(last)}"
        }
    }
    val tone = coverTone(cover)
    val still = reducedMotion()
    val drift = androidx.compose.animation.core.rememberInfiniteTransition(label = "portada")
    val zoom by drift.animateFloat(
        1f, if (still) 1f else 1.08f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(10000, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "acercar",
    )
    Column(Modifier.background(Brush.verticalGradient(0f to tone.copy(alpha = 0.55f), 1f to Lumi.Bg))) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(0.dp))) {
            if (cover != null) MediaThumb(cover, 1024, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom })
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 0.8f to tone.copy(alpha = 0.6f), 1f to tone.copy(alpha = 0.75f))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
                Text(title, style = TitleStyle.copy(fontSize = 30.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(countText(items.size, "foto", "fotos") + if (dates.isNotEmpty()) " · $dates" else "", style = SmallStyle.copy(fontSize = 14.sp))
            }
        }
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroButton(SlidesIcon, "Presentación") {
                items.firstOrNull()?.let { vm.open(Screen.Viewer(source, it.id, null, slideshow = true)) }
            }
            // Las apps que reciben fotos no suelen aceptar cientos de golpe.
            HeroButton(Icons.Filled.Share, "Compartir") { actions.share(items.take(100)) }
            if (items.count { !it.isVideo } >= 2) HeroButton(MovieIcon, "Vídeo") { vm.open(Screen.MemoryVideo(title, items.map { it.id })) }
        }
    }
}

@Composable
private fun HeroButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Lumi.Surface).clickable(onClick = onClick).padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = Lumi.Accent)
        Text(label, style = LabelStyle)
    }
}

/** Cuadrícula sencilla de una lista de fotos, con selección y paso al visor. */
@Composable
internal fun ItemsScreen(
    title: String,
    emptyTitle: String,
    emptyText: String,
    /** Dibujo de la pantalla vacía. */
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector = PictureIcon,
    items: List<MediaItem>,
    source: Source,
    state: UiState,
    vm: LumiViewModel,
    actions: Actions,
    link: GridLink,
    extra: (@Composable () -> Unit)? = null,
    /** Barra de abajo cuando no hay nada elegido. */
    bottom: (@Composable () -> Unit)? = null,
    /** Portada grande arriba de las fotos; al bajar, se va con ellas. */
    hero: (@Composable () -> Unit)? = null,
    /** Algo más que hacer con lo elegido, encima de la barra de selección. */
    selectionExtra: (@Composable (chosen: List<MediaItem>, clear: () -> Unit) -> Unit)? = null,
) {
    // Con portada, la cuadrícula empieza una casilla más abajo.
    val offset = if (hero != null) 1 else 0
    val grid = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    val currentItems by rememberUpdatedState(items)
    val currentSelection by rememberUpdatedState(selection)
    var holding by remember { mutableStateOf<Long?>(null) }

    val container = remember { arrayOfNulls<LayoutCoordinates>(1) }

    BackHandler(selection.isNotEmpty()) { selection = emptySet() }
    SideEffect {
        link.boundsOf = { id -> tileBounds(grid, container[0], id) }
        link.reveal = { id ->
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.index == index + offset }) grid.scrollToItem(maxOf(index + offset - 6, 0))
        }
    }
    // Mientras se ve la portada, el título ya está en ella.
    val heroShown by remember { derivedStateOf { offset == 1 && grid.firstVisibleItemIndex == 0 } }

    Box(Modifier.fillMaxSize().background(Lumi.Bg)) {
        Column {
            ScreenHeader(if (heroShown) "" else title, if (heroShown) "" else countText(items.size, "elemento", "elementos"), onBack = { vm.back() }) {
                SortMenu(ItemSort.entries, state.itemSort, { it.label }, vm::setItemSort)
                extra?.invoke()
            }
            if (items.isEmpty()) {
                EmptyMessage(emptyTitle, emptyText, icon = emptyIcon)
            } else {
                Box {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(scaledColumns(state.itemColumns)),
                        state = grid,
                        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 130.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize().onGloballyPositioned { container[0] = it }.pinchColumns(vm::zoomItems).dragSelect(
                            grid = grid,
                            idAt = { currentItems.getOrNull(it - offset)?.id },
                            selection = { currentSelection },
                            onChange = { next ->
                                if (next.size != currentSelection.size) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selection = next
                            },
                            onHold = { holding = it },
                            isVideo = { id -> currentItems.any { it.id == id && it.isVideo } },
                        ),
                    ) {
                        if (hero != null) item(key = "portada", span = { GridItemSpan(maxLineSpan) }) { hero() }
                        items(items, key = { it.id }) { item ->
                            PhotoTile(
                                item = item,
                                px = 320,
                                favorite = source != Source.Favorites && item.id in state.favorites,
                                tone = state.index[item.id]?.color ?: 0,
                                motion = state.index[item.id]?.motion == true,
                                watched = state.watched[item.id]?.let { it.toFloat() / item.duration.coerceAtLeast(1) } ?: 0f,
                                preview = holding == item.id,
                                selected = if (selection.isEmpty()) null else item.id in selection,
                                modifier = Modifier.aspectRatio(1f).clickable {
                                    if (selection.isNotEmpty()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        label = { index -> currentItems.getOrNull(index - offset)?.let { dayTitle(it.date) }.orEmpty() },
                        modifier = Modifier.fillMaxSize().padding(bottom = 110.dp).navigationBarsPadding(),
                    )
                }
            }
        }
        if (selection.isEmpty() && bottom != null) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)) { bottom() }
        }
        if (selection.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chosen = items.filter { it.id in selection }
                selectionExtra?.invoke(chosen) { selection = emptySet() }
                SelectionBar(chosen, state, vm, actions, pool = items, onSelect = { selection = it }) { selection = emptySet() }
            }
        }
    }
}

/** El color de una portada (el medio que Lumi ya calculó al analizarla), suavizado para que se lea encima. */
@Composable
fun coverTone(cover: MediaItem?): Color {
    val state = LocalState.current
    val raw = cover?.let { state.index[it.id]?.color }?.takeIf { it ushr 24 == 0xFF }
    val base = raw?.let { Color(it) } ?: Lumi.Accent
    val target = androidx.compose.ui.graphics.lerp(base, Lumi.Bg, 0.35f)
    val shown by androidx.compose.animation.animateColorAsState(target, androidx.compose.animation.core.tween(600), label = "tono")
    return shown
}
