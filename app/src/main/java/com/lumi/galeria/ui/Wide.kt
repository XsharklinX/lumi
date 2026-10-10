package com.lumi.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.formatCount

/**
 * Pantalla ancha (tableta, plegable abierto, Chromebook): a la izquierda va un panel fijo con
 * las secciones y los álbumes, y las cuadrículas ganan columnas. El dock de abajo sobra.
 */
val LocalWide = compositionLocalOf { false }

/** Por cuánto se multiplican las columnas de las cuadrículas según el ancho disponible. */
val LocalColumnScale = compositionLocalOf { 1 }

/** Ancho a partir del cual se usan dos paneles. */
val WIDE_FROM: Dp = 720.dp

/** Ancho del panel lateral. */
val SIDE_PANE: Dp = 300.dp

/** Columnas pensadas para un teléfono, ajustadas al ancho que hay de verdad. */
@Composable
fun scaledColumns(columns: Int): Int = columns * LocalColumnScale.current

/** Pantallas que se ven a pantalla completa también en una tableta. */
fun isImmersive(screen: Screen): Boolean = when (screen) {
    is Screen.Viewer, is Screen.Editor, is Screen.Markup, is Screen.Cutout, is Screen.Wallpaper, is Screen.Rotate,
    is Screen.StoryView, is Screen.Collage, is Screen.Compare, is Screen.Gif, is Screen.Pdf,
    Screen.SwipeReview, is Screen.VideoEditor, is Screen.Animate, is Screen.Portrait, is Screen.MemoryVideo,
    is Screen.MonthStory, is Screen.StickerMaker, Screen.Camera, is Screen.SignDoc, is Screen.Signature, Screen.Tour, is Screen.Show, Screen.CameraSettings -> true
    else -> false
}

@Composable
fun SidePane(state: UiState, vm: LumiViewModel, actions: Actions, current: Screen, modifier: Modifier = Modifier) {
    fun go(screen: Screen) {
        when (screen) {
            Screen.Timeline, Screen.Albums -> vm.switchTab(screen)
            else -> {
                // Lo que no es una pestaña se abre encima de Fotos, para que «atrás» vuelva a ella.
                vm.switchTab(Screen.Timeline)
                vm.open(screen)
            }
        }
    }
    Column(modifier.width(SIDE_PANE).fillMaxHeight().background(Lumi.Surface.copy(alpha = 0.5f)).statusBarsPadding().navigationBarsPadding()) {
        Text("Lumi", style = TitleStyle.copy(fontSize = 28.sp), modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 12.dp))
        PaneRow(Icons.Filled.Home, "Fotos", current == Screen.Timeline) { go(Screen.Timeline) }
        PaneRow(Icons.Filled.Search, "Buscar", current == Screen.Search) { vm.switchTab(Screen.Timeline); vm.openSearch() }
        PaneRow(Icons.Filled.Edit, "Documentos", current == Screen.Documents) { vm.switchTab(Screen.Documents) }
        PaneRow(Icons.Filled.Favorite, "Favoritas", current == Screen.Favorites) { go(Screen.Favorites) }
        if (state.facesOn && state.people.isNotEmpty()) {
            PaneRow(Icons.Filled.Face, "Personas", current == Screen.People || current is Screen.Person) { go(Screen.People) }
        }
        PaneRow(SdCardIcon, "Liberar espacio", current == Screen.Space) { go(Screen.Space) }
        PaneRow(Icons.Filled.Settings, "Ajustes", current == Screen.Settings) { go(Screen.Settings) }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Álbumes", style = HeadingStyle.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
            Text(
                "Ver todos", style = LabelStyle, color = Lumi.Accent,
                modifier = Modifier.clip(CircleShape).clickable { go(Screen.Albums) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(state.albums.filter { !it.locked }, key = { it.bucketId }) { album ->
                val on = current is Screen.Album && current.bucketId == album.bucketId
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (on) Lumi.Accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable {
                            vm.switchTab(Screen.Albums)
                            vm.open(Screen.Album(album.bucketId))
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MediaThumb(album.cover, 160, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(album.name, style = LabelStyle.copy(fontSize = 15.sp), color = if (on) Lumi.Accent else Lumi.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatCount(album.count), style = SmallStyle)
                    }
                }
            }
            item {
                Box(Modifier.padding(top = 8.dp)) {
                    PaneRow(Icons.Filled.Lock, "Carpeta privada", current == Screen.Vault) { vm.openVault(actions.unlock) }
                }
            }
        }
    }
}

@Composable
private fun PaneRow(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(CircleShape)
            .background(if (on) Lumi.Accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (on) Lumi.Accent else Lumi.Muted)
        Text(label, style = LabelStyle.copy(fontSize = 15.sp), color = if (on) Lumi.Accent else Lumi.Ink)
    }
}
