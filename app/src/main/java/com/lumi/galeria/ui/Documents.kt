@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lumi.galeria.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.PdfThumb
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.Doc
import com.lumi.galeria.data.DocKind
import com.lumi.galeria.data.normalize
import com.lumi.galeria.dayTitle
import com.lumi.galeria.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun Chip3(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, style = LabelStyle, color = if (on) Lumi.OnAccent else Lumi.Ink,
        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Surface).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Lo que se puede hacer para añadir documentos: escanear, una tarjeta, traer un PDF y la firma. */
@Composable
private fun AddMenu(vm: LumiViewModel, expanded: Boolean, onDismiss: () -> Unit) {
    val scan = rememberDocumentScanner(vm)
    val scanCard = rememberDocumentScanner(vm, card = true)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.importPdfs(uris) }
    DropdownMenu(expanded, onDismiss, containerColor = Lumi.Surface) {
        DropdownMenuItem({ Text("Escanear un documento") }, { onDismiss(); scan() })
        DropdownMenuItem({ Text("Escanear un DNI o una tarjeta") }, { onDismiss(); scanCard() })
        DropdownMenuItem({ Text("Traer un PDF del teléfono") }, { onDismiss(); pick.launch(arrayOf("application/pdf")) })
        DropdownMenuItem({ Text("Nuevo PDF con fotos") }, { onDismiss(); vm.open(Screen.PdfEdit("")) })
        DropdownMenuItem({ Text("Mi firma") }, { onDismiss(); vm.open(Screen.Signature(initials = false)) })
        DropdownMenuItem({ Text("Mis iniciales") }, { onDismiss(); vm.open(Screen.Signature(initials = true)) })
    }
}

/**
 * Pestaña Documentos: los PDF de Lumi con la primera página de miniatura, ordenados por tipo y
 * con un buscador que busca dentro de su texto.
 */
@Composable
fun DocumentsScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    LaunchedEffect(Unit) { vm.loadDocs() }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<DocKind?>(null) }
    var menu by remember { mutableStateOf(false) }
    val docs = vm.docs
    val words = remember(query) { normalize(query).split(' ').filter { it.length >= 2 } }
    val shown = remember(docs, words, kind) {
        docs.filter { doc ->
            (kind == null || doc.kind == kind) && words.all { w -> normalize(doc.name + " " + doc.text).contains(w) }
        }
    }
    val counts = remember(docs) { docs.groupingBy { it.kind }.eachCount() }
    val scan = rememberDocumentScanner(vm)

    Box(Modifier.fillMaxSize().background(Lumi.Bg)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ScreenHeader("Documentos", if (docs.isEmpty()) "" else countText(docs.size, "documento", "documentos")) {
                Box {
                    BarIcon(Icons.Filled.Add, "Añadir", { menu = true })
                    AddMenu(vm, menu) { menu = false }
                }
            }
            if (docs.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Tus papeles, en orden", style = TitleStyle.copy(fontSize = 26.sp))
                    Text(
                        "Escanea facturas, recibos, contratos o apuntes. Lumi lee el texto, propone un nombre y los ordena por tipo. Se puede buscar dentro de cada uno.",
                        style = SmallStyle.copy(fontSize = 15.sp),
                    )
                    PillButton("Escanear un documento", onClick = scan, modifier = Modifier.fillMaxWidth())
                    PillButton("Más opciones", onClick = { menu = true }, modifier = Modifier.fillMaxWidth(), primary = false)
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar dentro de los documentos") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Lumi.Muted) },
                    trailingIcon = { if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Borrar", Modifier.clip(CircleShape).clickable { query = "" }.padding(6.dp)) },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip3("Todos", kind == null) { kind = null }
                    DocKind.entries.filter { (counts[it] ?: 0) > 0 }.forEach { option ->
                        Chip3("${option.label} · ${counts[option]}", kind == option) { kind = if (kind == option) null else option }
                    }
                }
                if (vm.docsReading) Text("Leyendo documentos nuevos…", style = SmallStyle, modifier = Modifier.padding(horizontal = 16.dp))
                if (shown.isEmpty()) {
                    EmptyMessage("Nada con eso", "Prueba con otra palabra o quita el tipo elegido.", Modifier.weight(1f), icon = SearchLineIcon)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(scaledColumns(2)),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 130.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        itemsIndexed(shown, key = { _, it -> it.id }) { _, doc -> DocCard(doc) { vm.open(Screen.DocView(doc.uri.toString())) } }
                    }
                }
            }
        }
        if (!LocalWide.current) {
            Dock(Screen.Documents, vm::switchTab, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp), onCamera = actions.camera, onScan = scan)
        }
    }
}

@Composable
private fun DocCard(doc: Doc, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.72f).shadow(4.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color.White),
        ) {
            AsyncImage(PdfThumb(doc.uri, doc.date, 0, 400), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (doc.pages > 1) {
                Text(
                    "${doc.pages}", style = SmallStyle, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Text(doc.name, style = LabelStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(doc.kind.label + " · " + dayTitle(doc.date), style = SmallStyle, maxLines = 1)
    }
}

/** Un PDF de arriba abajo, página a página, con lo que se puede hacer con él. */
@Composable
fun DocViewScreen(screen: Screen.DocView, vm: LumiViewModel) {
    val context = LocalContext.current
    val uri = remember(screen.uri) { Uri.parse(screen.uri) }
    val doc = vm.docs.firstOrNull { it.uri == uri }
    val pages by produceState(doc?.pages ?: 0, uri) { value = withContext(Dispatchers.IO) { com.lumi.galeria.data.pageCount(context, uri) } }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val name = doc?.name ?: "Documento"

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(name, if (pages > 0) countText(pages, "página", "páginas") + (doc?.let { " · " + formatSize(it.size) } ?: "") else "", onBack = { vm.back() }) {
            BarIcon(Icons.Filled.Share, "Enviar", {
                runCatching {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null),
                    )
                }
            })
            Box {
                BarIcon(Icons.Filled.MoreVert, "Más", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = Lumi.Surface) {
                    DropdownMenuItem({ Text("Editar páginas") }, { menu = false; vm.open(Screen.PdfEdit(screen.uri)) })
                    DropdownMenuItem({ Text("Firmar") }, { menu = false; vm.open(Screen.SignDoc(screen.uri, pdf = true)) })
                    DropdownMenuItem({ Text("Cambiar el nombre") }, { menu = false; renaming = true })
                    DropdownMenuItem({ Text("Abrir con otra app") }, {
                        menu = false
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    })
                    if (doc != null) DropdownMenuItem({ Text("Borrar", color = Lumi.Danger) }, { menu = false; deleting = true })
                }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip3("Editar páginas", false) { vm.open(Screen.PdfEdit(screen.uri)) }
            Chip3("Firmar", false) { vm.open(Screen.SignDoc(screen.uri, pdf = true)) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items((0 until pages).toList()) { i ->
                AsyncImage(
                    PdfThumb(uri, doc?.date ?: 0, i, 1240), null,
                    Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(Color.White),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
    if (renaming && doc != null) {
        var text by remember { mutableStateOf(doc.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = Lumi.Surface,
            title = { Text("Nombre del documento", style = HeadingStyle) },
            text = { OutlinedTextField(text, { text = it.take(80) }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
            confirmButton = {
                Text("Guardar", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable(enabled = text.isNotBlank()) { renaming = false; vm.renameDoc(doc, text) }.padding(12.dp))
            },
            dismissButton = { Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { renaming = false }.padding(12.dp)) },
        )
    }
    if (deleting && doc != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = Lumi.Surface,
            title = { Text("¿Borrar este documento?", style = HeadingStyle) },
            text = { Text("Se borra del teléfono y no va a la papelera.", style = SmallStyle.copy(fontSize = 15.sp, color = Lumi.Ink)) },
            confirmButton = { Text("Borrar", style = LabelStyle, color = Lumi.Danger, modifier = Modifier.clip(CircleShape).clickable { deleting = false; vm.deleteDoc(doc) }.padding(12.dp)) },
            dismissButton = { Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { deleting = false }.padding(12.dp)) },
        )
    }
}

/** Guardar un escaneo: la primera página, el nombre que propone Lumi (se puede cambiar) y su tipo. */
@Composable
fun SaveDocScreen(vm: LumiViewModel) {
    val doc = vm.pendingDoc
    if (doc == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    var name by remember { mutableStateOf(doc.name) }
    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding().imePadding()) {
        ScreenHeader("Guardar escaneo", countText(doc.pages.size, "página", "páginas"), onBack = { vm.pendingDoc = null; vm.back() })
        Row(Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            doc.pages.forEach { page ->
                Image(
                    page.bitmap.asImageBitmap(), null,
                    Modifier.fillMaxHeight().aspectRatio(page.bitmap.width.toFloat() / page.bitmap.height).shadow(4.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                )
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nombre") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            Text("Tipo: ${doc.kind.label}. El texto va dentro del PDF: se puede buscar y copiar en cualquier lector.", style = SmallStyle)
            val sample = doc.pages.first().lines.take(3).joinToString(" · ") { it.text }
            if (sample.isNotBlank()) Text("Lumi ha leído: $sample", style = SmallStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            vm.making?.let { LinearProgressIndicator(color = Lumi.Accent, trackColor = Lumi.Line, modifier = Modifier.fillMaxWidth().clip(CircleShape)) }
            PillButton("Guardar PDF", onClick = { vm.savePendingDoc(name) }, modifier = Modifier.fillMaxWidth(), enabled = vm.making == null)
        }
    }
}

/** DNI o tarjeta: las dos caras a tamaño real en una página, con una marca opcional. */
@Composable
fun IdCardScreen(vm: LumiViewModel) {
    val cards = vm.pendingCard
    if (cards.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    var mark by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(if (com.lumi.galeria.Lang.english) "ID card" else "DNI") }
    val page by produceState<Bitmap?>(null, cards, mark) {
        kotlinx.coroutines.delay(150)
        value = withContext(Dispatchers.Default) { com.lumi.galeria.data.idCardPage(cards[0], cards.getOrNull(1), mark.trim()) }
    }
    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding().imePadding()) {
        ScreenHeader("DNI y tarjetas", "Las dos caras, a tamaño real", onBack = { vm.pendingCard = emptyList(); vm.back() })
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            page?.let {
                Image(
                    it.asImageBitmap(), null, Modifier.aspectRatio(it.width.toFloat() / it.height).shadow(6.dp, RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                )
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nombre") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                mark, { mark = it.take(50) }, label = { Text("Marca de agua (opcional)") }, placeholder = { Text("Solo para alquiler de piso · 2025") },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            Text("La marca cruza la página para que la copia no sirva para otra cosa.", style = SmallStyle)
            PillButton("Guardar PDF", onClick = { page?.let { vm.saveCard(it, name.ifBlank { "DNI" }) } }, modifier = Modifier.fillMaxWidth(), enabled = page != null && vm.making == null)
        }
    }
}

/**
 * Editar un PDF: ver sus páginas, cambiarlas de orden, girarlas, quitarlas y añadir más (escaneando,
 * desde fotos o uniendo otro PDF). Con [Screen.PdfEdit.uri] vacío se empieza un PDF nuevo.
 */
@Composable
fun PdfEditScreen(screen: Screen.PdfEdit, vm: LumiViewModel) {
    val context = LocalContext.current
    val source = remember(screen.uri) { screen.uri.takeIf { it.isNotEmpty() }?.let(Uri::parse) }
    val pages = remember { mutableStateListOf<LumiViewModel.EditPage>() }
    var loaded by remember { mutableStateOf(source == null) }
    LaunchedEffect(source) {
        if (source != null && pages.isEmpty()) {
            val n = withContext(Dispatchers.IO) { com.lumi.galeria.data.pageCount(context, source) }
            pages.addAll((0 until n).map { LumiViewModel.EditPage(source, it) })
            loaded = true
        }
    }
    // Lo que llega del escáner mientras se edita.
    LaunchedEffect(vm.pagesForEdit) {
        if (vm.pagesForEdit.isNotEmpty()) {
            pages.addAll(vm.pagesForEdit.map { LumiViewModel.EditPage(it, -1) })
            vm.pagesForEdit = emptyList()
        }
    }
    var selected by remember { mutableIntStateOf(-1) }
    var adding by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val scan = rememberDocumentScanner(vm)
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(40)) { uris -> pages.addAll(uris.map { LumiViewModel.EditPage(it, -1) }) }
    val merge = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val n = com.lumi.galeria.data.pageCount(context, uri)
            pages.addAll((0 until n).map { LumiViewModel.EditPage(uri, it) })
        }
    }
    val doc = vm.docs.firstOrNull { it.uri == source }
    val title = doc?.name ?: if (source == null) "PDF nuevo" else "Documento"

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(title, countText(pages.size, "página", "páginas"), onBack = { vm.back() }) {
            Box {
                BarIcon(Icons.Filled.Add, "Añadir páginas", { adding = true })
                DropdownMenu(adding, { adding = false }, containerColor = Lumi.Surface) {
                    DropdownMenuItem({ Text("Escanear páginas") }, { adding = false; scan() })
                    DropdownMenuItem({ Text("Desde fotos") }, { adding = false; photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    DropdownMenuItem({ Text("Unir otro PDF") }, { adding = false; merge.launch(arrayOf("application/pdf")) })
                }
            }
        }
        Text(
            if (selected < 0) "Toca una página para moverla, girarla o quitarla." else "Página ${selected + 1} elegida",
            style = SmallStyle, modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (!loaded) {
            Box(Modifier.weight(1f))
        } else if (pages.isEmpty()) {
            EmptyMessage("Sin páginas", "Añade páginas escaneando, desde tus fotos o uniendo otro PDF con el botón +.", Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(scaledColumns(3)), Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(pages, key = { i, p -> "${p.source}:${p.index}:$i" }) { i, page ->
                    val on = i == selected
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp)).background(Color.White)
                                .border(if (on) 3.dp else 0.dp, if (on) Lumi.Accent else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable { selected = if (on) -1 else i },
                        ) {
                            AsyncImage(
                                if (page.index >= 0) PdfThumb(page.source, 0, page.index, 360) else page.source, null,
                                Modifier.fillMaxSize().padding(3.dp).graphicsRotate(page.quarter * 90f), contentScale = ContentScale.Fit,
                            )
                        }
                        Text("${i + 1}", style = SmallStyle)
                    }
                }
            }
        }
        if (selected in pages.indices) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip3("← Antes", false) {
                    if (selected > 0) {
                        pages.add(selected - 1, pages.removeAt(selected))
                        selected--
                    }
                }
                Chip3("Después →", false) {
                    if (selected < pages.lastIndex) {
                        pages.add(selected + 1, pages.removeAt(selected))
                        selected++
                    }
                }
                Chip3("Girar", false) { pages[selected] = pages[selected].copy(quarter = (pages[selected].quarter + 1) % 4) }
                Chip3("Quitar", false) {
                    pages.removeAt(selected)
                    selected = -1
                }
            }
        }
        vm.making?.let { LinearProgressIndicator(progress = { it / 100f }, color = Lumi.Accent, trackColor = Lumi.Line, modifier = Modifier.fillMaxWidth().padding(12.dp).clip(CircleShape)) }
        PillButton(
            "Guardar PDF",
            onClick = { if (source != null) vm.savePdfEdit(source, title, pages.toList()) else naming = true },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            enabled = pages.isNotEmpty() && vm.making == null,
        )
    }
    if (naming) {
        var text by remember { mutableStateOf(if (com.lumi.galeria.Lang.english) "Document" else "Documento") }
        AlertDialog(
            onDismissRequest = { naming = false },
            containerColor = Lumi.Surface,
            title = { Text("Nombre del documento", style = HeadingStyle) },
            text = { OutlinedTextField(text, { text = it.take(80) }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
            confirmButton = {
                Text("Guardar", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable(enabled = text.isNotBlank()) {
                    naming = false
                    vm.savePdfEdit(null, text, pages.toList())
                }.padding(12.dp))
            },
            dismissButton = { Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { naming = false }.padding(12.dp)) },
        )
    }
}

private fun Modifier.graphicsRotate(degrees: Float): Modifier = this.rotate(degrees)

/** Dibujar la firma (o las iniciales) una vez. Se guarda dentro de Lumi y sirve para todo. */
@Composable
fun SignatureScreen(screen: Screen.Signature, vm: LumiViewModel) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val ink = Color(0xFF1F3A93)
    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(if (screen.initials) "Mis iniciales" else "Mi firma", "Dibújala con el dedo", onBack = { vm.back() })
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(20.dp)).background(Color.White)
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDragEnd = {
                            if (current.size > 1) strokes += current
                            current = emptyList()
                        },
                    ) { change, _ ->
                        change.consume()
                        current = current + change.position
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(Color(0xFFDDDDDD), Offset(size.width * 0.08f, size.height * 0.7f), Offset(size.width * 0.92f, size.height * 0.7f), 2f)
                (strokes + listOf(current)).forEach { points ->
                    if (points.size < 2) return@forEach
                    val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                    drawPath(path, ink, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (strokes.isEmpty() && current.isEmpty()) {
                Text("Firma aquí", style = HeadingStyle, color = Color(0xFFBBBBBB), modifier = Modifier.align(Alignment.Center))
            }
        }
        Text("Se guarda solo dentro de Lumi: ninguna otra app puede verla.", style = SmallStyle, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Borrar", onClick = { strokes.clear() }, modifier = Modifier.weight(1f), primary = false)
            PillButton(
                "Guardar",
                onClick = {
                    signatureBitmap(strokes.toList(), ink)?.let { vm.saveSignature(it, screen.initials) }
                    vm.back()
                },
                modifier = Modifier.weight(1f),
                enabled = strokes.isNotEmpty(),
            )
        }
    }
}

/** La firma recortada a lo que ocupa, sobre fondo transparente. */
private fun signatureBitmap(strokes: List<List<Offset>>, ink: Color): Bitmap? {
    val all = strokes.flatten()
    if (all.isEmpty()) return null
    val pad = 16f
    val left = all.minOf { it.x } - pad
    val top = all.minOf { it.y } - pad
    val w = (all.maxOf { it.x } + pad - left).toInt().coerceAtLeast(2)
    val h = (all.maxOf { it.y } + pad - top).toInt().coerceAtLeast(2)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(255, (ink.red * 255).toInt(), (ink.green * 255).toInt(), (ink.blue * 255).toInt())
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { points ->
        val path = android.graphics.Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x - left, p.y - top) else path.lineTo(p.x - left, p.y - top) }
        canvas.drawPath(path, paint)
    }
    return out
}

/** Algo puesto encima de una página al firmar: la firma, las iniciales o un texto. Posición y ancho, de 0 a 1. */
private class Stamp(val page: Int, val bitmap: Bitmap?, val text: String?) {
    var x by mutableFloatStateOf(0.55f)
    var y by mutableFloatStateOf(0.78f)
    var width by mutableFloatStateOf(if (bitmap != null) 0.32f else 0.25f)
}

private fun drawStamp(canvas: android.graphics.Canvas, stamp: Stamp, w: Int) {
    val sw = stamp.width * w
    if (stamp.bitmap != null) {
        val sh = sw * stamp.bitmap.height / stamp.bitmap.width
        canvas.drawBitmap(stamp.bitmap, null, android.graphics.RectF(stamp.x * w - sw / 2, stamp.y * w - sh / 2, stamp.x * w + sw / 2, stamp.y * w + sh / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    } else if (stamp.text != null) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(31, 58, 147)
            textAlign = Paint.Align.CENTER
            textSize = 40f
        }
        paint.textSize = 40f * sw / paint.measureText(stamp.text).coerceAtLeast(1f)
        canvas.drawText(stamp.text, stamp.x * w, stamp.y * w + paint.textSize / 3, paint)
    }
}

/**
 * Firmar un PDF o una foto: se pone la firma, las iniciales, la fecha o un texto, se mueven con
 * el dedo y se agrandan con dos dedos. Se guarda como copia nueva.
 */
@Composable
fun SignDocScreen(screen: Screen.SignDoc, state: UiState, vm: LumiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = remember(screen.uri) { Uri.parse(screen.uri) }
    val item = state.items.firstOrNull { it.id == screen.photoId }
    val pageCount by produceState(if (screen.pdf) 0 else 1, uri) {
        if (screen.pdf) value = withContext(Dispatchers.IO) { com.lumi.galeria.data.pageCount(context, uri) }
    }
    val signature by produceState<Bitmap?>(null) { value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(vm.signatureFile(false).path) } }
    val initials by produceState<Bitmap?>(null) { value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(vm.signatureFile(true).path) } }
    val stamps = remember { mutableStateListOf<Stamp>() }
    var selected by remember { mutableStateOf<Stamp?>(null) }
    var writing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val pager = rememberPagerState { pageCount.coerceAtLeast(1) }

    fun pageBitmap(index: Int, width: Int): Bitmap? =
        if (screen.pdf) com.lumi.galeria.data.renderPage(context, uri, index, width) else com.lumi.galeria.data.decodeForPdf(context, uri, 2400)

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false)
            Text("Firmar", style = HeadingStyle, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            PillButton(if (saving) "Guardando…" else "Guardar copia", onClick = {
                saving = true
                scope.launch {
                    val done = withContext(Dispatchers.Default) {
                        (0 until pageCount.coerceAtLeast(1)).mapNotNull { i ->
                            val base = withContext(Dispatchers.IO) { pageBitmap(i, 1654) } ?: return@mapNotNull null
                            val out = base.copy(Bitmap.Config.ARGB_8888, true)
                            val canvas = android.graphics.Canvas(out)
                            stamps.filter { it.page == i }.forEach { drawStamp(canvas, it, out.width) }
                            out
                        }
                    }
                    saving = false
                    if (done.isEmpty()) return@launch
                    if (screen.pdf) {
                        val name = vm.docs.firstOrNull { it.uri == uri }?.name ?: "Documento"
                        vm.saveSignedPdf(name + if (com.lumi.galeria.Lang.english) " (signed)" else " (firmado)", done)
                    } else if (item != null) {
                        vm.savePhotoNextTo(item, done.first(), "_firmada", "Copia firmada guardada junto a la original")
                        vm.back()
                    }
                }
            }, enabled = stamps.isNotEmpty() && !saving && vm.making == null)
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = selected == null) { index ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                val bitmap by produceState<Bitmap?>(null, index) { value = withContext(Dispatchers.IO) { pageBitmap(index, 1240) } }
                val b = bitmap ?: return@BoxWithConstraints
                val aspect = b.width.toFloat() / b.height
                val fitW = if (maxWidth / maxHeight > aspect) maxHeight * aspect else maxWidth
                val fitH = fitW / aspect
                Box(Modifier.size(fitW, fitH).shadow(4.dp).background(Color.White)) {
                    Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                    var box by remember { mutableStateOf(IntSize.Zero) }
                    Canvas(
                        Modifier.fillMaxSize().onSizeChanged { box = it }.pointerInput(index) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val w = box.width.toFloat()
                                val hit = stamps.lastOrNull { s -> s.page == index && kotlin.math.abs(down.position.x / w - s.x) < s.width / 2 + 0.04f && kotlin.math.abs(down.position.y / w - s.y) < 0.08f }
                                selected = hit
                                if (hit == null) return@awaitEachGesture
                                do {
                                    val event = awaitPointerEvent()
                                    val pan = event.calculatePan()
                                    hit.x = (hit.x + pan.x / w).coerceIn(0f, 1f)
                                    hit.y = (hit.y + pan.y / w).coerceIn(0f, box.height / w)
                                    if (event.changes.count { it.pressed } >= 2) hit.width = (hit.width * event.calculateZoom()).coerceIn(0.05f, 0.9f)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    ) {
                        val native = drawContext.canvas.nativeCanvas
                        stamps.filter { it.page == index }.forEach { s ->
                            drawStamp(native, s, size.width.toInt())
                            if (s === selected) {
                                drawRect(Color(0xFFB9A8FF), Offset((s.x - s.width / 2) * size.width - 6, (s.y - 0.06f) * size.width), androidx.compose.ui.geometry.Size(s.width * size.width + 12, 0.12f * size.width), style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
        if (pageCount > 1) Text("Página ${pager.currentPage + 1} de $pageCount", style = SmallStyle, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip3("Mi firma", false) {
                val s = signature
                if (s == null) vm.open(Screen.Signature(initials = false)) else stamps += Stamp(pager.currentPage, s, null).also { selected = it }
            }
            Chip3("Iniciales", false) {
                val s = initials
                if (s == null) vm.open(Screen.Signature(initials = true)) else stamps += Stamp(pager.currentPage, s, null).apply { width = 0.14f }.also { selected = it }
            }
            Chip3("Fecha", false) { stamps += Stamp(pager.currentPage, null, com.lumi.galeria.data.todayText()).also { selected = it } }
            Chip3("Texto", false) { writing = true }
            selected?.let { s -> Chip3("Quitar", false) { stamps.remove(s); selected = null } }
        }
        Text("Arrastra para colocar; con dos dedos, cambia el tamaño.", style = SmallStyle, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
    }
    if (writing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { writing = false },
            containerColor = Lumi.Surface,
            title = { Text("Escribe el texto", style = HeadingStyle) },
            text = { OutlinedTextField(text, { text = it.take(60) }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
            confirmButton = {
                Text("Poner", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable(enabled = text.isNotBlank()) {
                    writing = false
                    stamps += Stamp(pager.currentPage, null, text.trim()).also { selected = it }
                }.padding(12.dp))
            },
            dismissButton = { Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { writing = false }.padding(12.dp)) },
        )
    }
}
