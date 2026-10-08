package com.lumi.galeria.ui

import com.lumi.galeria.data.storageSpace
import com.lumi.galeria.data.cardVolume
import androidx.compose.ui.platform.LocalContext
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.style.TextOverflow
import com.lumi.galeria.Source
import com.lumi.galeria.dayTitle
import com.lumi.galeria.formatDuration
import com.lumi.galeria.data.albumName
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.ReviewKind
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.formatSize

@Composable
fun SpaceScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    val context = LocalContext.current
    val groups = remember(state.items, state.stackByBest, state.blurry, state.duplicateGroups) {
        ReviewKind.entries.map { it to state.reviewItems(it) }.filter { it.second.isNotEmpty() }
    }
    val storage = remember(state.items) {
        runCatching { StatFs(Environment.getExternalStorageDirectory().path) }.getOrNull()
    }

    Box(Modifier.fillMaxSize().background(Lumi.Bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader("Espacio", "", onBack = { vm.back() })
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val card = remember { cardVolume(context) }
                if (card != null) CardSection(state, vm, actions, card)
                else if (storage != null) StorageBar(storage.totalBytes, storage.availableBytes)
                if (groups.isEmpty()) {
                    Column(Modifier.padding(horizontal = 6.dp, vertical = 18.dp)) {
                        Text("Todo en orden", style = TitleStyle.copy(fontSize = 34.sp))
                        Text("No hay copias, fotos parecidas, vídeos grandes ni capturas antiguas que revisar.", style = SmallStyle.copy(fontSize = 14.sp))
                    }
                } else {
                    Text("Para revisar", style = HeadingStyle, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
                    groups.forEach { (kind, items) ->
                        SpaceRow(kind.title, items, formatSize(items.sumOf { it.size })) { vm.open(Screen.Review(kind)) }
                    }
                    Text(
                        "Nada viene marcado: tú eliges qué quitar. Lo que quites pasa 30 días en la papelera.",
                        style = SmallStyle,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                val dark = remember(state.items, state.index) {
                    state.items.filter { item ->
                        !item.isVideo && !item.isScreenshot && !item.isGif && !item.name.contains("_mejorada") &&
                            state.index[item.id]?.let { it.color != 0 && com.lumi.galeria.data.isDark(it.color) } == true
                    }
                }
                if (dark.isNotEmpty()) SpaceRow("Se pueden mejorar", dark, "Lumi Auto") { vm.open(Screen.Enhance) }
                val sensitive = remember(state.items, state.sensitive) { state.items.filter { it.id in state.sensitive } }
                if (sensitive.isNotEmpty()) SpaceRow("Fotos con datos personales", sensitive, "Proteger") { vm.open(Screen.Sensitive) }
                SpaceRow("Repaso rápido", state.items.filter { it.isScreenshot }.take(3), "Deslizar") { vm.open(Screen.SwipeReview) }
                SpaceRow(
                    "Papelera",
                    state.trashed,
                    if (state.trashed.isEmpty()) "vacía" else formatSize(state.trashed.sumOf { it.size }),
                ) { vm.open(Screen.Trash) }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/**
 * Con tarjeta de memoria: cómo están el teléfono y la tarjeta, y qué conviene pasar a la tarjeta
 * para liberar el teléfono. Lo antiguo es lo que menos se mira a diario.
 */
@Composable
private fun CardSection(state: UiState, vm: LumiViewModel, actions: Actions, card: String) {
    val context = LocalContext.current
    val space = remember(state.items) { storageSpace(context) }
    val now = remember { System.currentTimeMillis() }
    val oldVideos = remember(state.items) { state.items.filter { it.isVideo && !it.onCard && it.date < now - 182L * 86_400_000 } }
    val oldPhotos = remember(state.items) { state.items.filter { !it.isVideo && !it.onCard && it.date < now - 365L * 86_400_000 } }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (space != null) {
            UsageLine("Teléfono", space.phoneTotal, space.phoneFree)
            UsageLine("Tarjeta SD", space.cardTotal, space.cardFree)
        }
        Text("Pasar a la tarjeta", style = HeadingStyle.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 4.dp))
        listOf(
            Triple("Vídeos de hace más de 6 meses", oldVideos, countText(oldVideos.size, "vídeo", "vídeos")),
            Triple("Fotos de hace más de un año", oldPhotos, countText(oldPhotos.size, "foto", "fotos")),
        ).filter { it.second.isNotEmpty() }.forEach { (title, list, count) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = LabelStyle)
                    Text(count + " · " + formatSize(list.sumOf { it.size }), style = SmallStyle)
                }
                ChipButton("Mover") { vm.moveToCard(list, card) { copied -> actions.deleteForever(copied) } }
            }
        }
        Text(
            "Se copian a la tarjeta y después Android te pide permiso para borrarlas del teléfono. Siguen viéndose igual en Lumi.",
            style = SmallStyle,
        )
    }
}

@Composable
private fun UsageLine(label: String, total: Long, free: Long) {
    val used = (total - free).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = LabelStyle, modifier = Modifier.weight(1f))
            Text("${formatSize(free)} libres de ${formatSize(total)}", style = SmallStyle)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Lumi.Bg)) {
            val part = if (total > 0) used.toFloat() / total else 0f
            Box(Modifier.fillMaxWidth(part).height(8.dp).background(if (part > 0.9f) Lumi.Danger else Lumi.Accent))
        }
    }
}

/** Cuánto ocupa el teléfono y cuánto queda libre. */
@Composable
private fun StorageBar(total: Long, free: Long) {
    val used = (total - free).coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatSize(free), style = TitleStyle.copy(fontSize = 30.sp), modifier = Modifier.weight(1f))
            Text("${formatSize(free)} libres de ${formatSize(total)}", style = SmallStyle)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Lumi.Bg)) {
            Box(Modifier.fillMaxWidth(if (total > 0) used.toFloat() / total else 0f).height(10.dp).background(Lumi.Accent))
        }
    }
}

@Composable
private fun SpaceRow(title: String, items: List<MediaItem>, trailing: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Lumi.Surface)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tres miniaturas montadas una sobre otra.
        Box(Modifier.width(92.dp).height(44.dp)) {
            items.take(3).forEachIndexed { i, item ->
                MediaThumb(
                    item, 160,
                    Modifier
                        .offset(x = (24 * i).dp)
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, Lumi.Surface, RoundedCornerShape(12.dp)),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = HeadingStyle.copy(fontSize = 15.sp))
            Text(countText(items.size, "elemento", "elementos"), style = SmallStyle)
        }
        Text(trailing, style = LabelStyle, color = Lumi.Accent)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickGrid(
    items: List<MediaItem>,
    selection: Set<Long>,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
    caption: (MediaItem) -> String? = { null },
    urgent: (MediaItem) -> Boolean = { false },
    onOpen: ((MediaItem) -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        items(items, key = { it.id }) { item ->
            PhotoTile(
                item = item,
                px = 320,
                selected = item.id in selection,
                caption = caption(item),
                urgent = urgent(item),
                modifier = Modifier.aspectRatio(1f).combinedClickable(onLongClick = onOpen?.let { { it(item) } }) { onToggle(item.id) },
            )
        }
    }
}

/**
 * Revisar antes de borrar. Nada empieza marcado: se elige foto a foto o con los botones de
 * arriba. En copias y fotos parecidas se ve cada grupo junto, con la que se propone conservar
 * delante, y se pueden comparar de cerca antes de decidir.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReviewScreen(screen: Screen.Review, state: UiState, vm: LumiViewModel, actions: Actions) {
    val kind = screen.kind
    val grouped = kind == ReviewKind.REPEATED || kind == ReviewKind.DUPLICATES
    val raw = remember(state.stackByBest, state.duplicateGroups, kind) { state.reviewGroups(kind) }
    // Las copias se comprueban imagen contra imagen antes de enseñarlas: si la huella se equivoca,
    // la pareja no sale.
    val groups by produceState<List<List<MediaItem>>?>(if (kind == ReviewKind.DUPLICATES) null else raw, raw) {
        value = if (kind == ReviewKind.DUPLICATES) vm.verifiedDuplicates(raw) else raw
    }
    val flat = remember(state.items, state.blurry, kind) { if (grouped) emptyList() else state.reviewItems(kind) }
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    val all = if (grouped) groups.orEmpty().flatten() else flat
    val chosen = remember(all, selection) { all.filter { it.id in selection } }

    fun toggle(id: Long) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun openViewer(items: List<MediaItem>, item: MediaItem) = vm.open(Screen.Viewer(Source.Ids(items.mapTo(HashSet()) { it.id }), item.id))

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(kind.title, kind.hint, onBack = { vm.back() })
        when {
            grouped && groups == null -> EmptyMessage("Comprobando…", "Lumi está comparando cada copia con su original.", Modifier.weight(1f))
            all.isEmpty() -> EmptyMessage("Nada que revisar", "Aquí no queda nada.", Modifier.weight(1f), icon = ShrinkIcon)
            else -> {
                // Atajos para elegir muchas de golpe, y para soltarlas todas.
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (grouped) countText(groups.orEmpty().size, "grupo", "grupos") else countText(all.size, "elemento", "elementos"),
                        style = SmallStyle, modifier = Modifier.weight(1f),
                    )
                    if (selection.isNotEmpty()) {
                        ChipButton("Quitar selección") { selection = emptySet() }
                    }
                    ChipButton(if (grouped) "Elegir las copias" else "Elegir todo") {
                        // En grupos se elige todo menos la que se propone conservar.
                        selection = if (grouped) groups.orEmpty().flatMap { it.drop(1) }.mapTo(HashSet()) { it.id } else all.mapTo(HashSet()) { it.id }
                    }
                }
                when {
                    grouped -> LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(groups.orEmpty(), key = { it.first().id }) { group ->
                            ReviewGroup(
                                group = group,
                                exact = kind == ReviewKind.DUPLICATES,
                                selection = selection,
                                onToggle = ::toggle,
                                onOpen = { openViewer(group, it) },
                                onCompare = { other -> vm.open(Screen.Compare(group.first().id, other.id)) },
                            )
                        }
                    }
                    kind == ReviewKind.BIG_VIDEOS -> LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(flat, key = { it.id }) { video ->
                            VideoRow(video, video.id in selection, onToggle = { toggle(video.id) }, onOpen = { openViewer(flat, video) })
                        }
                    }
                    else -> PickGrid(
                        items = flat,
                        selection = selection,
                        onToggle = ::toggle,
                        modifier = Modifier.weight(1f),
                        caption = { formatSize(it.size) },
                        onOpen = { openViewer(flat, it) },
                    )
                }
                Text(
                    "Toca para elegir. Mantén pulsado para verla en grande.",
                    style = SmallStyle, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                )
                PillButton(
                    if (chosen.isEmpty()) "Elige lo que quieras quitar"
                    else "Mover ${chosen.size} a la papelera · ${formatSize(chosen.sumOf { it.size })}",
                    onClick = { actions.trash(chosen) { selection = emptySet() } },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    enabled = chosen.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun ChipButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = LabelStyle,
        color = Lumi.Accent,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/**
 * Un grupo de copias o de fotos parecidas. La primera es la que se propone conservar (la de más
 * resolución, o la más nítida). Debajo de cada una, sus datos para poder decidir.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReviewGroup(
    group: List<MediaItem>,
    exact: Boolean,
    selection: Set<Long>,
    onToggle: (Long) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onCompare: (MediaItem) -> Unit,
) {
    val keeper = group.first()
    // Mismo tamaño en bytes: es el mismo archivo guardado dos veces.
    val identical = exact && group.all { it.size == keeper.size }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        identical -> "Archivo idéntico"
                        exact -> "Misma imagen"
                        else -> "Fotos parecidas"
                    },
                    style = HeadingStyle.copy(fontSize = 15.sp),
                )
                Text(dayTitle(keeper.date) + " · " + countText(group.size, "foto", "fotos"), style = SmallStyle)
            }
            if (!keeper.isVideo) {
                ChipButton("Comparar") { onCompare(group.firstOrNull { it.id in selection && it.id != keeper.id } ?: group[1]) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            group.forEachIndexed { index, item ->
                Column(Modifier.width(118.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    PhotoTile(
                        item = item,
                        px = 320,
                        selected = item.id in selection,
                        corner = 12.dp,
                        modifier = Modifier.size(118.dp).combinedClickable(onLongClick = { onOpen(item) }) { onToggle(item.id) },
                    )
                    Text(
                        if (index == 0) (if (exact) "Se propone conservar" else "La más nítida") else item.bucket.ifEmpty { item.name },
                        style = SmallStyle,
                        color = if (index == 0) Lumi.Accent else Lumi.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        (if (item.width > 0) "${item.width} × ${item.height} · " else "") + formatSize(item.size),
                        style = SmallStyle.copy(fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
            }
        }
        if (group.all { it.id in selection }) {
            Text("Has elegido todas las de este grupo: no quedará ninguna.", style = SmallStyle, color = Lumi.Danger)
        }
    }
}

/** Un vídeo grande: miniatura, cuánto dura, cuándo se hizo y, sobre todo, cuánto pesa. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoRow(video: MediaItem, selected: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Lumi.Accent.copy(alpha = 0.18f) else Lumi.Surface)
            .combinedClickable(onLongClick = onOpen, onClick = onToggle)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhotoTile(video, 320, Modifier.size(72.dp), selected = selected, corner = 12.dp)
        Column(Modifier.weight(1f)) {
            Text(video.name, style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dayTitle(video.date) + " · " + formatDuration(video.duration), style = SmallStyle, maxLines = 1)
            Text(albumName(video.bucket), style = SmallStyle, maxLines = 1)
        }
        Text(formatSize(video.size), style = HeadingStyle.copy(fontSize = 16.sp), color = Lumi.Accent)
    }
}

@Composable
fun TrashScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    val items = state.trashed
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    val chosen = if (selection.isEmpty()) items else items.filter { it.id in selection }
    val now = System.currentTimeMillis() / 1000

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(
            "Papelera",
            if (items.isEmpty()) "" else "${countText(items.size, "elemento", "elementos")} · ${formatSize(items.sumOf { it.size })}",
            onBack = { vm.back() },
        )
        if (items.isEmpty()) {
            EmptyMessage("La papelera está vacía", "Lo que borres se guarda aquí 30 días por si te arrepientes.", Modifier.weight(1f), icon = TrashIcon)
        } else {
            Text(
                "Cada elemento se borra solo, para siempre, cuando se cumplen sus 30 días. En rojo, los que se van en tres días o menos.",
                style = SmallStyle,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            PickGrid(
                items = items,
                selection = selection,
                onToggle = { id -> selection = if (id in selection) selection - id else selection + id },
                modifier = Modifier.weight(1f),
                caption = { item ->
                    val days = ((item.expires - now) / 86_400).coerceAtLeast(0)
                    when {
                        item.expires <= 0 -> null
                        days == 0L -> "Hoy"
                        days == 1L -> "Mañana"
                        else -> "$days días"
                    }
                },
                urgent = { item -> item.expires > 0 && item.expires - now <= 3 * 86_400 },
            )
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    if (selection.isEmpty()) "Restaurar todo" else "Restaurar ${selection.size}",
                    onClick = { actions.restore(chosen); selection = emptySet() },
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    if (selection.isEmpty()) "Vaciar" else "Borrar ${selection.size}",
                    onClick = { actions.deleteForever(chosen); selection = emptySet() },
                    modifier = Modifier.weight(1f),
                    primary = false,
                    danger = true,
                )
            }
        }
    }
}

/**
 * Fotos oscuras o apagadas que Lumi Auto puede mejorar. Nada viene elegido; cada mejora se guarda
 * como copia junto a la original, que no se toca. Manteniendo pulsada una se ve el antes y el después.
 */
@Composable
fun EnhanceScreen(state: UiState, vm: LumiViewModel) {
    val dark = remember(state.items, state.index) {
        state.items.filter { item ->
            !item.isVideo && !item.isScreenshot && !item.isGif && !item.name.contains("_mejorada") &&
                state.index[item.id]?.let { it.color != 0 && com.lumi.galeria.data.isDark(it.color) } == true
        }
    }
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var preview by remember { mutableStateOf<MediaItem?>(null) }
    val chosen = remember(dark, selection) { dark.filter { it.id in selection } }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Se pueden mejorar", "Fotos oscuras o apagadas", onBack = { vm.back() })
        if (dark.isEmpty()) {
            EmptyMessage("Nada que mejorar", "No hay fotos oscuras o apagadas, o aún se están revisando.", Modifier.weight(1f), icon = SparkIcon)
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(countText(dark.size, "foto", "fotos"), style = SmallStyle, modifier = Modifier.weight(1f))
            if (selection.isNotEmpty()) ChipButton("Quitar selección") { selection = emptySet() }
            ChipButton("Elegir todas") { selection = dark.mapTo(HashSet()) { it.id } }
        }
        PickGrid(
            items = dark,
            selection = selection,
            onToggle = { id -> selection = if (id in selection) selection - id else selection + id },
            modifier = Modifier.weight(1f),
            onOpen = { preview = it },
        )
        Text(
            "Toca para elegir. Mantén pulsada para ver el antes y el después. La original no se toca: la mejorada se guarda como copia.",
            style = SmallStyle, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )
        val progress = vm.making
        PillButton(
            when {
                progress != null -> "Mejorando… $progress %"
                chosen.isEmpty() -> "Elige las que quieras mejorar"
                else -> "Mejorar " + countText(chosen.size, "foto", "fotos")
            },
            onClick = { vm.enhancePhotos(chosen) },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            enabled = chosen.isNotEmpty() && progress == null,
        )
    }
    preview?.let { item -> BeforeAfter(item) { preview = null } }
}

@Composable
private fun BeforeAfter(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pair by produceState<Pair<android.graphics.Bitmap, android.graphics.Bitmap>?>(null, item.id) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val before = context.contentResolver.loadThumbnail(item.uri, android.util.Size(1024, 1024), null)
                val copy = before.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                before to com.lumi.galeria.data.lumiAuto(copy)
            }.getOrNull()
        }
    }
    var showAfter by remember { mutableStateOf(true) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text(if (showAfter) "Después" else "Antes", style = HeadingStyle) },
        text = {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Lumi.Bg).clickable { showAfter = !showAfter }, contentAlignment = Alignment.Center) {
                val shown = pair?.let { if (showAfter) it.second else it.first }
                if (shown == null) Text("Preparando…", style = SmallStyle)
                else androidx.compose.foundation.Image(
                    shown.asImageBitmap(), null, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                )
            }
        },
        confirmButton = {
            Text(if (showAfter) "Ver antes" else "Ver después", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable { showAfter = !showAfter }.padding(12.dp))
        },
        dismissButton = { Text("Cerrar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(12.dp)) },
    )
}
