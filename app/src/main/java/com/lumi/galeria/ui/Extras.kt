@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.lumi.galeria.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.Thumb
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.Sensitive
import com.lumi.galeria.widget.Daily
import com.lumi.galeria.widget.WallSettings
import com.lumi.galeria.widget.WallSource
import com.lumi.galeria.widget.WallTarget
import kotlinx.coroutines.delay

/**
 * Fotos con datos personales: documentos de identidad, tarjetas y contraseñas. Lumi las encuentra
 * por el texto que leyó en ellas y propone pasarlas a la carpeta privada. No viene nada elegido.
 */
@Composable
fun SensitiveScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    val byKind = remember(state.items, state.sensitive) {
        val alive = state.items.associateBy { it.id }
        Sensitive.entries.associateWith { kind -> state.sensitive.filterValues { it == kind }.keys.mapNotNull { alive[it] }.sortedByDescending { it.date } }
            .filterValues { it.isNotEmpty() }
    }
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    val all = byKind.values.flatten()
    val chosen = all.filter { it.id in selection }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Fotos con datos personales", "Mejor bajo llave", onBack = { vm.back() })
        if (all.isEmpty()) {
            EmptyMessage(
                if (state.deepPending > 0) "Aún revisando" else "Nada a la vista",
                if (state.deepPending > 0) "Lumi sigue leyendo el texto de tus fotos. Vuelve dentro de un rato." else "No hay fotos con documentos de identidad, tarjetas ni contraseñas.",
                Modifier.weight(1f),
                icon = LockLineIcon,
            )
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(countText(all.size, "foto", "fotos"), style = SmallStyle, modifier = Modifier.weight(1f))
            if (selection.isNotEmpty()) SmallChip("Quitar selección") { selection = emptySet() }
            SmallChip("Elegir todas") { selection = all.mapTo(HashSet()) { it.id } }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            byKind.forEach { (kind, items) ->
                item(span = { GridItemSpan(3) }, key = kind.name) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp)) {
                        Text(kind.label, style = HeadingStyle.copy(fontSize = 16.sp))
                        Text(com.lumi.galeria.tr(kind.hint) + " · " + countText(items.size, "foto", "fotos"), style = SmallStyle)
                    }
                }
                items(items, key = { it.id }) { item ->
                    PhotoTile(
                        item = item, px = 320, selected = item.id in selection, caption = null, urgent = false,
                        modifier = Modifier.aspectRatio(1f).combinedClickable(
                            onLongClick = { vm.open(Screen.Viewer(Source.Ids(all.mapTo(HashSet()) { it.id }), item.id)) },
                        ) { selection = if (item.id in selection) selection - item.id else selection + item.id },
                    )
                }
            }
        }
        Text(
            "Toca para elegir; mantén pulsada para verla. Se cifran en la carpeta privada y después Android te pide permiso para quitarlas de la galería.",
            style = SmallStyle, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )
        PillButton(
            if (chosen.isEmpty()) "Elige las que quieras proteger" else "Pasar " + countText(chosen.size, "foto", "fotos") + " a la carpeta privada",
            onClick = {
                if (!actions.canUnlock()) {
                    vm.say("Pon un bloqueo de pantalla o un PIN de Lumi para usar la carpeta privada")
                } else {
                    vm.hideInVault(chosen) { stored -> actions.deleteForever(stored) }
                    selection = emptySet()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            enabled = chosen.isNotEmpty(),
        )
    }
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Text(
        label, style = LabelStyle, color = Lumi.Accent,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * Modo enseñar: solo se ven [Screen.Show.ids], a pantalla completa. Android fija la pantalla para
 * que no se pueda salir de Lumi, y para salir de aquí hace falta la huella o el PIN.
 */
@Composable
fun ShowScreen(screen: Screen.Show, state: UiState, vm: LumiViewModel, actions: Actions) {
    val context = LocalContext.current
    val activity = context as? Activity
    val photos = remember(screen.ids, state.items) {
        val byId = state.items.associateBy { it.id }
        screen.ids.mapNotNull { byId[it] }
    }
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val pager = rememberPagerState { photos.size }
    var chrome by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        // Fijar la pantalla: Android pide confirmarlo la primera vez.
        runCatching { activity?.startLockTask() }
        onDispose { runCatching { activity?.stopLockTask() } }
    }
    LaunchedEffect(chrome, pager.currentPage) {
        if (chrome) {
            delay(3500)
            chrome = false
        }
    }
    fun leave() {
        if (actions.canUnlock()) actions.unlock {
            runCatching { activity?.stopLockTask() }
            vm.back()
        } else {
            runCatching { activity?.stopLockTask() }
            vm.back()
        }
    }
    BackHandler { leave() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val item = photos[page]
            AsyncImage(
                Thumb(item.uri, 2048, item.modified), null,
                Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { chrome = !chrome },
                contentScale = ContentScale.Fit,
            )
        }
        if (chrome) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Enseñando " + countText(photos.size, "foto", "fotos") + " · ${pager.currentPage + 1} de ${photos.size}",
                    style = LabelStyle, color = Color.White,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Text(
                    "Salir", style = LabelStyle, color = Color.White,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable { leave() }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Text(
                if (actions.canUnlock()) "Para salir hace falta tu huella o tu PIN" else "Solo se pueden ver estas fotos",
                style = SmallStyle, color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(18.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** El fondo de pantalla que cambia solo: de dónde salen las fotos, cada cuánto y dónde se pone. */
@Composable
fun AutoWallpaperScreen(state: UiState, vm: LumiViewModel) {
    val context = LocalContext.current
    val start = remember { Daily.wallSettings(context) }
    var source by remember { mutableStateOf(start.source) }
    var album by remember { mutableStateOf(start.album) }
    var albumName by remember { mutableStateOf(start.albumName) }
    var hours by remember { mutableStateOf(start.hours) }
    var target by remember { mutableStateOf(start.target) }
    var on by remember { mutableStateOf(start.on) }
    val albums = state.albums.filter { !it.locked && it.count >= 3 && !it.cover.isScreenshot }

    fun save(enable: Boolean) {
        on = enable
        Daily.saveWall(context, WallSettings(enable, source, album, albumName, hours, target))
        vm.say(if (enable) "Listo: el fondo cambiará solo" else "El fondo ya no cambiará solo")
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Fondo que cambia solo", if (on) "Activado" else "Desactivado", onBack = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Qué fotos", style = LabelStyle)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallSource.entries.forEach { option -> OptionChip(option.label, source == option) { source = option } }
            }
            if (source == WallSource.ALBUM) {
                albums.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (album == a.path) Lumi.Accent.copy(alpha = 0.18f) else Lumi.Surface)
                            .clickable { album = a.path; albumName = a.name }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MediaThumb(a.cover, 160, Modifier.padding(0.dp).clip(RoundedCornerShape(10.dp)).aspectRatio(1f).fillMaxWidth(0.14f))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = LabelStyle)
                            Text(countText(a.count, "elemento", "elementos"), style = SmallStyle)
                        }
                    }
                }
            }
            Text("Cada cuánto", style = LabelStyle, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Cada hora", 3 to "Cada 3 horas", 24 to "Cada día").forEach { (h, text) -> OptionChip(text, hours == h) { hours = h } }
            }
            Text("Dónde", style = LabelStyle, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallTarget.entries.forEach { option -> OptionChip(option.label, target == option) { target = option } }
            }
            Text(
                "Cada foto se recorta a la pantalla. Nunca salen documentos ni capturas. Lo hace Android a su hora, sin dejar nada abierto en segundo plano, " +
                    "así que puede retrasarse unos minutos.",
                style = SmallStyle, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (on) PillButton("Desactivar", onClick = { save(false) }, modifier = Modifier.weight(1f), primary = false)
            PillButton(
                if (on) "Guardar" else "Activar",
                onClick = { save(true) },
                modifier = Modifier.weight(1f),
                enabled = source != WallSource.ALBUM || album.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun OptionChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, style = LabelStyle, color = if (on) Lumi.OnAccent else Lumi.Ink,
        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
