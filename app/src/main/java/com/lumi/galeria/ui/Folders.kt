@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.lumi.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.MoreVert
import com.lumi.galeria.AlbumFolder
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.Album
import com.lumi.galeria.formatCount

/** Una carpeta de álbumes: las portadas de sus dos primeros álbumes, montadas una sobre otra. */
@Composable
fun FolderTile(folder: AlbumFolder, albums: List<Album>, onLongClick: () -> Unit, onClick: () -> Unit) {
    val covers = albums.filter { !it.locked }.take(2).map { it.cover }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Lumi.Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        covers.getOrNull(1)?.let {
            MediaThumb(it, 320, Modifier.padding(14.dp).fillMaxSize().rotate(-6f).offset(x = (-6).dp).clip(RoundedCornerShape(16.dp)))
        }
        covers.getOrNull(0)?.let {
            MediaThumb(it, 512, Modifier.padding(14.dp).fillMaxSize().rotate(4f).offset(x = 6.dp).clip(RoundedCornerShape(16.dp)))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(FolderIcon, null, Modifier.size(15.dp), tint = Color.White)
                Text(
                    folder.name, style = HeadingStyle.copy(fontSize = 15.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(countText(albums.size, "álbum", "álbumes"), style = SmallStyle, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

/** Elegir en qué carpeta meter un álbum, o crear una nueva. */
@Composable
fun FolderPickerSheet(vm: LumiViewModel, album: Album, onDismiss: () -> Unit) {
    var naming by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lumi.Surface) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Meter «${album.name}» en…", style = HeadingStyle.copy(fontSize = 20.sp))
            vm.albumFolders.forEach { folder ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lumi.Bg).clickable {
                        vm.putInFolder(folder.id, album.bucketId)
                        onDismiss()
                    }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(FolderIcon, null, Modifier.size(20.dp), tint = Lumi.Accent)
                    Text(folder.name, style = LabelStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Text(countText(folder.albums.size, "álbum", "álbumes"), style = SmallStyle)
                }
            }
            PillButton("Carpeta nueva", onClick = { naming = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), primary = vm.albumFolders.isEmpty())
            Text("Las fotos no se mueven: solo cambia cómo se ordenan los álbumes en Lumi.", style = SmallStyle)
        }
    }
    if (naming) {
        FolderNameDialog("Carpeta nueva", "", onDismiss = { naming = false }) { name ->
            naming = false
            vm.createFolder(name, album.bucketId)
            onDismiss()
        }
    }
}

@Composable
fun FolderNameDialog(title: String, start: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text(title, style = HeadingStyle) },
        text = { OutlinedTextField(text, { text = it.take(30) }, singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text("Por ejemplo: Viajes") }) },
        confirmButton = {
            Text(
                "Guardar", style = LabelStyle, color = if (text.isBlank()) Lumi.Muted else Lumi.Accent,
                modifier = Modifier.clip(CircleShape).clickable(enabled = text.isNotBlank()) { onDone(text.trim()) }.padding(12.dp),
            )
        },
        dismissButton = { Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(12.dp)) },
    )
}

/** Lo que hay dentro de una carpeta de álbumes. */
@Composable
fun AlbumFolderScreen(screen: Screen.AlbumFolder, state: UiState, vm: LumiViewModel, actions: Actions) {
    val folder = vm.albumFolders.firstOrNull { it.id == screen.id }
    if (folder == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.back() }
        return
    }
    val albums = folder.albums.mapNotNull { id -> state.albums.firstOrNull { it.bucketId == id } }
    var pressed by remember { mutableStateOf<Long?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    fun openAlbum(album: Album) {
        if (!album.locked) vm.open(Screen.Album(album.bucketId))
        else actions.unlock {
            vm.unlockAlbums()
            vm.open(Screen.Album(album.bucketId))
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(folder.name, countText(albums.size, "álbum", "álbumes"), onBack = { vm.back() }) {
            Box {
                BarIcon(androidx.compose.material.icons.Icons.Filled.MoreVert, "Más", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = Lumi.Surface) {
                    DropdownMenuItem({ Text("Cambiar el nombre") }, { menu = false; renaming = true })
                    DropdownMenuItem({ Text("Deshacer la carpeta") }, {
                        menu = false
                        vm.deleteFolder(folder.id)
                        vm.back()
                    })
                }
            }
        }
        if (albums.isEmpty()) {
            EmptyMessage("Carpeta vacía", "Mantén pulsado un álbum y elige «Meter en una carpeta».", Modifier.weight(1f), icon = FolderIcon)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(scaledColumns(2)),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(albums, key = { it.bucketId }) { album ->
                    Box {
                        AlbumTile(album.name, if (album.locked) "Con candado" else formatCount(album.count), album.cover, album.locked, { pressed = album.bucketId }, card = album.onCard > 0) { openAlbum(album) }
                        DropdownMenu(pressed == album.bucketId, { pressed = null }, containerColor = Lumi.Surface) {
                            DropdownMenuItem({ Text("Sacar de la carpeta") }, {
                                pressed = null
                                vm.takeOutOfFolder(album.bucketId)
                            })
                        }
                    }
                }
            }
        }
    }
    if (renaming) {
        FolderNameDialog("Cambiar el nombre", folder.name, onDismiss = { renaming = false }) { name ->
            renaming = false
            vm.renameFolder(folder.id, name)
        }
    }
}
