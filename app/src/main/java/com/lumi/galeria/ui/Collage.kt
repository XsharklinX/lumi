package com.lumi.galeria.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.R
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Formas del collage. Cada una coloca las fotos a su manera. */
private enum class Template(val label: String) {
    GRID("Cuadrícula"), MAGAZINE("Revista"), MOSAIC("Mosaico"), STRIP("Tira"), POLAROID("Polaroid"),
}

private enum class CollageSize(val label: String, val ratio: Float) { PORTRAIT("4:5", 0.8f), SQUARE("1:1", 1f), STORY("9:16", 9f / 16f), WIDE("16:9", 16f / 9f) }

private enum class Paper(val label: String, val color: Color, val ink: Color) {
    WHITE("Blanco", Color.White, Color(0xFF222222)),
    CREAM("Crema", Color(0xFFF4F1E8), Color(0xFF2B2620)),
    BLACK("Negro", Color(0xFF111111), Color.White),
    NIGHT("Noche", Color(0xFF1C1B2E), Color(0xFFE9E4FF)),
}

/** Un hueco del collage, en proporción del lienzo, y cuánto va girado (en las polaroid). */
private class Slot(val rect: Rect, val turn: Float = 0f)

/** Dónde va cada foto según la forma y cuántas hay. [titled] deja sitio abajo para el título en la revista. */
private fun slots(template: Template, n: Int): List<Slot> = when (template) {
    Template.GRID -> when (n) {
        2 -> listOf(Rect(0f, 0f, 0.5f, 1f), Rect(0.5f, 0f, 1f, 1f))
        3 -> listOf(Rect(0f, 0f, 0.5f, 1f), Rect(0.5f, 0f, 1f, 0.5f), Rect(0.5f, 0.5f, 1f, 1f))
        4 -> listOf(Rect(0f, 0f, 0.5f, 0.5f), Rect(0.5f, 0f, 1f, 0.5f), Rect(0f, 0.5f, 0.5f, 1f), Rect(0.5f, 0.5f, 1f, 1f))
        5 -> listOf(Rect(0f, 0f, 0.5f, 0.5f), Rect(0.5f, 0f, 1f, 0.5f), Rect(0f, 0.5f, 1f / 3, 1f), Rect(1f / 3, 0.5f, 2f / 3, 1f), Rect(2f / 3, 0.5f, 1f, 1f))
        else -> List(6) { i -> Rect(i % 3 / 3f, i / 3 / 2f, (i % 3 + 1) / 3f, (i / 3 + 1) / 2f) }
    }.map { Slot(it) }
    // Una foto grande a la izquierda, las demás en columna a la derecha, y abajo el título.
    Template.MAGAZINE -> {
        val rest = n - 1
        listOf(Slot(Rect(0f, 0f, 0.62f, 0.82f))) + List(rest) { i -> Slot(Rect(0.62f, 0.82f * i / rest, 1f, 0.82f * (i + 1) / rest)) }
    }
    // La primera ocupa la mitad de arriba; las demás, una fila debajo.
    Template.MOSAIC -> listOf(Slot(Rect(0f, 0f, 1f, 0.58f))) + List(n - 1) { i -> Slot(Rect(i.toFloat() / (n - 1), 0.58f, (i + 1f) / (n - 1), 1f)) }
    // Como las tiras de un fotomatón: una debajo de otra.
    Template.STRIP -> List(n) { i -> Slot(Rect(0f, i.toFloat() / n, 1f, (i + 1f) / n)) }
    // Polaroids un poco torcidas, repartidas por el papel.
    Template.POLAROID -> {
        val cols = if (n <= 4) 2 else 3
        val rows = ceil(n / cols.toFloat()).toInt()
        val turns = listOf(-6f, 4f, -3f, 7f, -5f, 3f)
        List(n) { i ->
            val c = i % cols
            val r = i / cols
            val cw = 1f / cols
            val ch = 1f / rows
            Slot(Rect(c * cw + cw * 0.07f, r * ch + ch * 0.06f, (c + 1) * cw - cw * 0.07f, (r + 1) * ch - ch * 0.06f), turns[i % turns.size])
        }
    }
}

/**
 * Collage con plantillas: cuadrícula, revista, mosaico, tira de fotomatón o polaroids. Se elige
 * el papel, si hay bordes y esquinas redondas, y un título. Tocar dos fotos las cambia de sitio.
 */
@Composable
fun CollageScreen(screen: Screen.Collage, state: UiState, vm: LumiViewModel) {
    val photos = remember(screen.ids, state.items) { screen.ids.mapNotNull { id -> state.items.firstOrNull { it.id == id && !it.isVideo } }.take(6) }
    if (photos.size < 2) {
        LaunchedEffect(Unit) {
            vm.say("Elige entre 2 y 6 fotos para el collage")
            vm.back()
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var template by remember { mutableStateOf(Template.GRID) }
    var frame by remember { mutableStateOf(CollageSize.PORTRAIT) }
    var paper by remember { mutableStateOf(Paper.WHITE) }
    var gap by remember { mutableStateOf(true) }
    var round by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var order by remember { mutableStateOf(photos.indices.toList()) }
    var picked by remember { mutableIntStateOf(-1) }
    var saving by remember { mutableStateOf(false) }
    val layout = remember(template, photos.size) { slots(template, photos.size) }
    // La tira tiene su propia forma: estrecha y alta.
    val ratio = if (template == Template.STRIP) 1f / (photos.size * 0.75f) else frame.ratio
    val polaroid = template == Template.POLAROID
    val hasTitle = title.isNotBlank()

    fun save() {
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val width = 2160
                    val height = (width / ratio).roundToInt()
                    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(out)
                    canvas.drawColor(paper.color.toArgb())
                    val pad = if (gap || polaroid) width * 0.012f else 0f
                    val corner = if (round) width * 0.025f else 0f
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    // En la revista el título va en su franja; en las demás, sobre una franja al pie.
                    val titleBand = if (hasTitle && template != Template.MAGAZINE) height * 0.1f else 0f
                    val usable = height - titleBand
                    layout.forEachIndexed { i, slot ->
                        val photo = decode(context, photos[order[i]], 1600) ?: return@forEachIndexed
                        val cell = RectF(slot.rect.left * width + pad, slot.rect.top * usable + pad, slot.rect.right * width - pad, slot.rect.bottom * usable - pad)
                        canvas.save()
                        canvas.rotate(slot.turn, cell.centerX(), cell.centerY())
                        val target = if (polaroid) {
                            // El marco blanco de la polaroid, más ancho abajo.
                            val white = Paint().apply { color = android.graphics.Color.WHITE; setShadowLayer(width * 0.01f, 0f, width * 0.004f, 0x55000000) }
                            canvas.drawRect(cell, white)
                            val m = cell.width() * 0.05f
                            RectF(cell.left + m, cell.top + m, cell.right - m, cell.bottom - m * 3.5f)
                        } else cell
                        // Se recorta por el centro para llenar el hueco sin deformar la foto.
                        val scale = maxOf(target.width() / photo.width, target.height() / photo.height)
                        val cropW = target.width() / scale
                        val cropH = target.height() / scale
                        val left = (photo.width - cropW) / 2
                        val top = (photo.height - cropH) / 2
                        if (corner > 0f) canvas.clipPath(android.graphics.Path().apply { addRoundRect(target, corner, corner, android.graphics.Path.Direction.CW) })
                        canvas.drawBitmap(photo, android.graphics.Rect(left.toInt(), top.toInt(), (left + cropW).toInt(), (top + cropH).toInt()), target, paint)
                        canvas.restore()
                        photo.recycle()
                    }
                    if (hasTitle) {
                        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = paper.ink.toArgb()
                            textSize = width * 0.06f
                            typeface = runCatching { ResourcesCompat.getFont(context, R.font.schibsted_grotesk) }.getOrNull()
                        }
                        val y = if (template == Template.MAGAZINE) height * 0.93f else height - titleBand * 0.35f
                        canvas.drawText(title.trim(), width * 0.05f, y, text)
                    }
                    out
                }.getOrNull()
            }
            if (result != null) vm.saveArt(result, "Collage_${System.currentTimeMillis() / 1000}", "Pictures/Lumi/", "Collage guardado en el álbum Lumi").join()
            else vm.say("No hay memoria suficiente para este collage")
            saving = false
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false)
            Text("Collage", style = HeadingStyle, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            PillButton(if (saving) "Guardando…" else "Guardar", { save() }, enabled = !saving)
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.aspectRatio(ratio).background(paper.color)) {
                val w = maxWidth
                val titleBand = if (hasTitle && template != Template.MAGAZINE) maxHeight * 0.1f else 0.dp
                val h = maxHeight - titleBand
                val pad = if (gap || polaroid) 3.dp else 0.dp
                layout.forEachIndexed { i, slot ->
                    val item = photos[order[i]]
                    Box(
                        Modifier
                            .offset(x = w * slot.rect.left, y = h * slot.rect.top)
                            .size(width = w * slot.rect.width, height = h * slot.rect.height)
                            .padding(pad)
                            .graphicsLayer { rotationZ = slot.turn }
                            .then(if (polaroid) Modifier.background(Color.White).padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp) else Modifier)
                            .clip(RoundedCornerShape(if (round) 10.dp else 0.dp))
                            .then(if (picked == i) Modifier.border(3.dp, Lumi.Accent) else Modifier)
                            // Tocar una foto y luego otra las cambia de sitio.
                            .clickable {
                                picked = if (picked < 0) i else {
                                    if (picked != i) order = order.toMutableList().apply { val a = this[picked]; this[picked] = this[i]; this[i] = a }
                                    -1
                                }
                            },
                    ) { MediaThumb(item, 512, Modifier.fillMaxSize()) }
                }
                if (hasTitle) {
                    Text(
                        title.trim(),
                        style = HeadingStyle.copy(fontSize = 18.sp, fontStyle = FontStyle.Normal),
                        color = paper.ink,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = w * 0.05f, bottom = if (template == Template.MAGAZINE) maxHeight * 0.05f else titleBand * 0.2f),
                    )
                }
            }
        }
        Text(
            if (picked >= 0) "Toca otra foto para cambiarlas de sitio" else "Toca dos fotos para cambiarlas de sitio",
            style = SmallStyle, modifier = Modifier.padding(start = 20.dp),
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Template.entries.forEach { option -> CollageChip(option.label, template == option) { template = option } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (template != Template.STRIP) CollageSize.entries.forEach { option -> CollageChip(option.label, frame == option) { frame = option } }
                Paper.entries.forEach { option ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(option.color)
                            .border(if (paper == option) 3.dp else 1.dp, if (paper == option) Lumi.Accent else Lumi.Line, CircleShape)
                            .clickable { paper = option },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CollageChip("Bordes", gap) { gap = !gap }
                CollageChip("Esquinas", round) { round = !round }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    placeholder = { Text("Título") },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CollageChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = LabelStyle,
        color = if (on) Lumi.OnAccent else Lumi.Ink,
        maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Surface).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
