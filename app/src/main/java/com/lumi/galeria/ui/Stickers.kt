package com.lumi.galeria.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.data.Sticker
import com.lumi.galeria.data.StickerPack
import com.lumi.galeria.data.StickerStore
import com.lumi.galeria.data.buildSticker
import com.lumi.galeria.data.trimTransparent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val EMOJIS = listOf("😀", "😂", "😍", "😎", "🥳", "😢", "😡", "👍", "❤️", "🔥", "🎉", "🐶", "🐱", "🙏", "😴", "🤔")

@Composable
private fun StickerCheckers() {
    val light = Color(0xFFE6E6E6)
    val dark = Color(0xFFC9C9C9)
    Canvas(Modifier.fillMaxSize()) {
        val cell = 12.dp.toPx()
        drawRect(light)
        var y = 0
        while (y * cell < size.height) {
            var x = y % 2
            while (x * cell < size.width) {
                drawRect(dark, Offset(x * cell, y * cell), Size(cell, cell))
                x += 2
            }
            y++
        }
    }
}

/** Tu paquete de pegatinas para WhatsApp. Se hacen desde cualquier foto y se añaden con un toque. */
@Composable
fun StickersScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    val context = LocalContext.current
    val store = remember { StickerStore(context) }
    var stickers by remember { mutableStateOf(store.list()) }
    var chosen by remember { mutableStateOf<Sticker?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.discover("pegatinas") }

    fun addToWhatsApp() {
        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", StickerPack.ID)
            putExtra("sticker_pack_authority", StickerPack.authority(context))
            putExtra("sticker_pack_name", StickerPack.NAME)
        }
        // Si hay WhatsApp normal y Business, se prueba el primero que responda.
        val ok = runCatching { context.startActivity(intent) }.isSuccess
        if (!ok) problem = "No se pudo abrir WhatsApp. Comprueba que lo tienes instalado y actualizado."
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Pegatinas", "Para WhatsApp", onBack = { vm.back() })
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(3) }) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(StickerPack.NAME, style = HeadingStyle)
                    Text(
                        if (stickers.size < StickerPack.MIN) "Necesitas al menos ${StickerPack.MIN} pegatinas para añadir el paquete a WhatsApp. Abre una foto, pulsa ⋯ y elige «Crear pegatina»."
                        else "${stickers.size} de ${StickerPack.MAX}. Pulsa una pegatina para cambiar su emoji o quitarla.",
                        style = SmallStyle.copy(fontSize = 13.sp),
                    )
                    PillButton("Añadir a WhatsApp", { addToWhatsApp() }, modifier = Modifier.fillMaxWidth(), enabled = stickers.size >= StickerPack.MIN)
                    problem?.let { Text(it, style = SmallStyle.copy(color = Lumi.Accent)) }
                    Text("Las pegatinas se guardan solo en Lumi; WhatsApp las lee desde aquí, sin conexión.", style = SmallStyle)
                }
            }
            items(stickers, key = { it.file }) { sticker ->
                val bmp by produceState<Bitmap?>(null, sticker.file) {
                    value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(java.io.File(store.dir(), sticker.file).path) }
                }
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Lumi.Surface).clickable { chosen = sticker }, contentAlignment = Alignment.Center) {
                    bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit) }
                    Text(sticker.emoji, style = LabelStyle, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                }
            }
            item {
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Lumi.Accent.copy(alpha = 0.12f)).clickable { vm.switchTab(Screen.Timeline) },
                    contentAlignment = Alignment.Center,
                ) { Text("Elige una foto\ny ⋯ → Crear pegatina", style = SmallStyle.copy(color = Lumi.Accent), modifier = Modifier.padding(8.dp)) }
            }
        }
    }
    chosen?.let { sticker ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { chosen = null },
            containerColor = Lumi.Surface,
            title = { Text("Pegatina", style = HeadingStyle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Emoji con el que WhatsApp la sugiere", style = SmallStyle)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EMOJIS.forEach { e ->
                            Text(e, style = HeadingStyle, modifier = Modifier.clip(CircleShape).background(if (e == sticker.emoji) Lumi.Accent.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { store.setEmoji(sticker, e); stickers = store.list(); chosen = sticker.copy(emoji = e) }.padding(8.dp))
                        }
                    }
                }
            },
            confirmButton = { Text("Listo", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable { chosen = null }.padding(12.dp)) },
            dismissButton = {
                Text("Quitar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { store.remove(sticker); stickers = store.list(); chosen = null }.padding(12.dp))
            },
        )
    }
}

/** Convertir una foto en pegatina: se separa lo principal del fondo y se le pone el borde blanco. */
@Composable
fun StickerMakerScreen(screen: Screen.StickerMaker, state: UiState, vm: LumiViewModel, actions: Actions) {
    val item = state.items.firstOrNull { it.id == screen.id }
    if (item == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    val store = remember { StickerStore(context) }
    var segmented by remember { mutableStateOf<Segmented?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var outline by remember { mutableStateOf(true) }
    var emoji by remember { mutableStateOf("😀") }

    LaunchedEffect(item.id) {
        val result = segment(context, item)
        segmented = result.getOrNull()
        if (segmented == null) problem = segmentProblem(result)
    }
    val sticker by produceState<Bitmap?>(null, segmented, outline) {
        val source = segmented ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                val photo = source.photo
                val w = photo.width
                val h = photo.height
                val px = IntArray(w * h)
                photo.getPixels(px, 0, w, 0, 0, w, h)
                for (i in px.indices) {
                    val t = ((source.confidence[i] - 0.3f) / 0.4f).coerceIn(0f, 1f)
                    val a = t * t * (3 - 2 * t)
                    px[i] = ((a * 255).toInt() shl 24) or (px[i] and 0xFFFFFF)
                }
                val cut = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(px, 0, w, 0, 0, w, h) }
                buildSticker(trimTransparent(cut), outline)
            }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false)
            Text("Crear pegatina", style = HeadingStyle, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            PillButton(
                "Añadir",
                onClick = {
                    val done = sticker ?: return@PillButton
                    if (store.add(done, emoji)) {
                        vm.say("Pegatina añadida a tu paquete")
                        vm.discover("pegatinas")
                        vm.back()
                        vm.open(Screen.Stickers)
                    } else vm.say("El paquete está lleno: quita alguna pegatina antes")
                },
                enabled = sticker != null,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(20.dp)).background(Lumi.Surface), contentAlignment = Alignment.Center) {
            val shown = sticker
            if (shown != null) StickerCheckers()
            when {
                shown != null -> Image(shown.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                problem != null -> Text(problem!!, style = SmallStyle.copy(fontSize = 15.sp, color = Lumi.Ink), modifier = Modifier.padding(24.dp))
                else -> Text("Separando del fondo…", style = HeadingStyle)
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (outline) "Con borde blanco" else "Sin borde", style = LabelStyle, color = if (outline) Lumi.OnAccent else Lumi.Ink,
                modifier = Modifier.clip(CircleShape).background(if (outline) Lumi.Accent else Lumi.Surface).clickable { outline = !outline }.padding(horizontal = 14.dp, vertical = 9.dp),
            )
            Text("Emoji", style = SmallStyle)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EMOJIS.forEach { e ->
                    Text(e, style = HeadingStyle, modifier = Modifier.clip(CircleShape).background(if (e == emoji) Lumi.Accent.copy(alpha = 0.2f) else Color.Transparent).clickable { emoji = e }.padding(8.dp))
                }
            }
        }
    }
}
