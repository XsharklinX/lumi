package com.lumi.galeria.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import com.lumi.galeria.tr
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.data.MediaItem

/** Cómo se enseñan las carpetas en Álbumes. */
enum class AlbumView(val label: String) { GRID("Cuadrícula"), LIST("Lista"), STRIP("Con fotos") }

/** Acceso pequeño de la cabecera de Álbumes: Favoritas y Privada. */
@Composable
fun QuickAccess(title: String, subtitle: String, icon: ImageVector, cover: MediaItem?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(Lumi.Surface).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Lumi.Bg), contentAlignment = Alignment.Center) {
            if (cover != null) MediaThumb(cover, 160, Modifier.fillMaxSize())
            Box(
                Modifier.align(if (cover != null) Alignment.BottomEnd else Alignment.Center).padding(if (cover != null) 2.dp else 0.dp)
                    .size(if (cover != null) 16.dp else 24.dp).clip(CircleShape).background(if (cover != null) Color.Black.copy(alpha = 0.55f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(if (cover != null) 10.dp else 20.dp), tint = if (cover != null) Color(0xFFFF4D6D) else Lumi.Accent)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = HeadingStyle.copy(fontSize = 14.sp), maxLines = 1)
            Text(subtitle, style = SmallStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Botón "Nuevo": dentro de Álbumes ya se sabe que es un álbum. */
@Composable
fun NewButton(onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Lumi.Accent).clickable(onClick = onClick).padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = Lumi.OnAccent)
        Text("Nuevo", style = LabelStyle, color = Lumi.OnAccent)
    }
}

/** Importar de una cámara, una tarjeta o una memoria USB. */
@Composable
fun ImportButton(onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Lumi.Surface).clickable(onClick = onClick).padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(SdCardIcon, null, Modifier.size(16.dp), tint = Lumi.Accent)
        Text("Importar", style = LabelStyle)
    }
}

/** Dibujo pequeño de cada vista: cuatro cuadros, tres rayas o una raya con cuadritos. */
@Composable
private fun ViewGlyph(view: AlbumView, ink: Color) {
    when (view) {
        AlbumView.GRID -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(2) { Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.5.dp)).background(ink)) } } }
        }
        AlbumView.LIST -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { Box(Modifier.size(3.dp).background(ink)); Box(Modifier.size(width = 9.dp, height = 3.dp).background(ink)) } }
        }
        AlbumView.STRIP -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(width = 14.dp, height = 2.dp).background(ink))
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) { repeat(4) { Box(Modifier.size(width = 2.75.dp, height = 8.dp).background(ink)) } }
        }
    }
}

/** Un solo botón con la vista actual; al tocarlo se elige otra. */
@Composable
fun ViewSwitch(current: AlbumView, onPick: (AlbumView) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Lumi.Surface).clickable { open = true }
                .semantics { contentDescription = tr("Cambiar la vista") },
            contentAlignment = Alignment.Center,
        ) { ViewGlyph(current, Lumi.Ink) }
        DropdownMenu(open, { open = false }, containerColor = Lumi.Surface) {
            AlbumView.entries.forEach { view ->
                DropdownMenuItem(
                    text = { Text(view.label, color = if (view == current) Lumi.Accent else Lumi.Ink) },
                    leadingIcon = { ViewGlyph(view, if (view == current) Lumi.Accent else Lumi.Muted) },
                    onClick = {
                        open = false
                        onPick(view)
                    },
                )
            }
        }
    }
}

/** Álbum en la cuadrícula: portada cuadrada con el nombre encima. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumTile(name: String, subtitle: String, cover: MediaItem?, locked: Boolean, onLongClick: (() -> Unit)?, card: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
            .background(if (locked) Lumi.Accent.copy(alpha = 0.22f) else Lumi.Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (!locked && cover != null) MediaThumb(cover, 512, Modifier.fillMaxSize())
        if (locked) {
            // Con candado no se enseña nada de lo que hay dentro.
            Box(Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Lumi.Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, "Bloqueado", Modifier.size(28.dp), tint = Lumi.OnAccent)
            }
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(name, style = HeadingStyle.copy(fontSize = 15.sp), color = if (locked) Lumi.Ink else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (card) Icon(SdCardIcon, "En la tarjeta de memoria", Modifier.size(14.dp), tint = if (locked) Lumi.Muted else Color.White)
            }
            Text(subtitle, style = SmallStyle, color = if (locked) Lumi.Muted else Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

/** Álbum con sus últimas fotos a la vista: se reconoce por lo que tiene dentro, no solo por el nombre. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumStrip(name: String, subtitle: String, recent: List<MediaItem>, locked: Boolean, onLongClick: (() -> Unit)?, card: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(name, style = HeadingStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (card) Icon(SdCardIcon, "En la tarjeta de memoria", Modifier.size(14.dp), tint = Lumi.Muted)
                }
                Text(subtitle, style = SmallStyle, maxLines = 1)
            }
            if (locked) Icon(Icons.Filled.Lock, "Bloqueado", Modifier.size(20.dp), tint = Lumi.Accent)
        }
        if (!locked) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 4) {
                    val item = recent.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Lumi.Bg)) {
                        if (item != null) MediaThumb(item, 320, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** Álbum que arma Lumi (viajes, cosas), en la fila que se desliza de lado. */
@Composable
fun LumiAlbumCard(title: String, subtitle: String, cover: MediaItem, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MediaThumb(cover, 320, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)))
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(title, style = HeadingStyle.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = SmallStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
