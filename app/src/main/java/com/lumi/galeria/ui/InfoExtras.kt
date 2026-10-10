@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lumi.galeria.ui

import android.graphics.Bitmap
import android.util.Size as AndroidSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.UiState
import com.lumi.galeria.dayTitle
import com.lumi.galeria.timeText
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.data.colorHistogram
import com.lumi.galeria.data.isRaw
import com.lumi.galeria.data.rawTwin
import com.lumi.galeria.data.readShotInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun Pill(label: String, strong: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(
        label, style = LabelStyle.copy(fontSize = 12.sp), color = if (strong) Lumi.OnAccent else Lumi.Ink,
        modifier = Modifier.clip(CircleShape).background(if (strong) Lumi.Accent else Lumi.Bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

/**
 * Lo que se añade al panel de información de una foto: su nota y etiquetas, los datos de la toma
 * con el histograma, si es RAW (y tiene su JPEG), la fecha y si está marcada para borrarse sola.
 */
@Composable
fun InfoExtras(
    item: MediaItem,
    state: UiState,
    onNote: () -> Unit,
    onDate: () -> Unit,
    onTemporary: () -> Unit,
    onClearTemporary: () -> Unit,
    onTag: (String) -> Unit,
    onTwin: () -> Unit,
    onShown: () -> Unit = {},
) {
    if (item.isExternal) return
    val context = LocalContext.current
    val note = state.notes[item.id]
    val twin = remember(item.id, state.items.size) { rawTwin(item, state.items) }
    val temporaryAt = state.temporary[item.id]
    androidx.compose.runtime.LaunchedEffect(item.id) { onShown() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (item.isRaw || twin != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.isRaw) Pill("RAW", strong = true)
                if (twin != null) Pill(if (item.isRaw) "Tiene su JPEG: compararlos" else "Tiene su RAW: compararlos", onClick = onTwin)
            }
        }

        // Nota y etiquetas.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lumi.Bg).clickable(onClick = onNote).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nota y etiquetas", style = LabelStyle, modifier = Modifier.weight(1f))
                Text(if (note == null) "Añadir" else "Editar", style = LabelStyle, color = Lumi.Accent)
            }
            if (note == null) {
                Text("Escribe algo de esta foto o ponle #etiquetas: luego las encuentras en el buscador.", style = SmallStyle)
            } else {
                if (note.text.isNotBlank()) Text(note.text, style = LabelStyle.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
                if (note.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    note.tags.forEach { tag -> TagChip(tag) { onTag(tag) } }
                }
            }
        }

        // Datos de la toma: lo que anotó la cámara y cómo se reparte la luz.
        if (!item.isVideo) {
            val shot by produceState<com.lumi.galeria.data.ShotInfo?>(null, item.id) { value = withContext(Dispatchers.IO) { readShotInfo(context, item) } }
            val histogram by produceState<Array<FloatArray>?>(null, item.id) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val bmp: Bitmap = context.contentResolver.loadThumbnail(item.uri, AndroidSize(320, 320), null)
                        colorHistogram(bmp)
                    }.getOrNull()
                }
            }
            if (shot != null || histogram != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Datos de la toma", style = LabelStyle)
                    histogram?.let { Histogram3(it) }
                    shot?.let { s ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (s.camera.isNotEmpty()) Pill(s.camera)
                            if (s.lens.isNotEmpty()) Pill(s.lens)
                            s.aperture?.let { Pill(it) }
                            s.shutter?.let { Pill(it) }
                            s.iso?.let { Pill("ISO $it") }
                            s.focal?.let { Pill(it) }
                            if (s.flash) Pill("Con flash")
                        }
                    }
                }
            }
        }

        // Fecha y borrado automático.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lumi.Bg).clickable(onClick = onDate).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Fecha", style = LabelStyle)
                Text(dayTitle(item.date) + " · " + timeText(item.date), style = SmallStyle)
            }
            Text("Cambiar", style = LabelStyle, color = Lumi.Accent)
        }
        if (temporaryAt != null) {
            val days = ((temporaryAt - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lumi.Bg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Foto temporal", style = LabelStyle)
                    Text(if (days <= 0L) "Se manda a la papelera hoy" else if (days == 1L) "Se manda a la papelera mañana" else "Se manda a la papelera dentro de $days días", style = SmallStyle)
                }
                Text("Conservar", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable(onClick = onClearTemporary).padding(8.dp))
            }
        } else {
            Text(
                "Borrar sola…", style = LabelStyle, color = Lumi.Accent,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onTemporary).padding(vertical = 4.dp),
            )
        }
    }
}

/** El histograma de color: rojo, verde y azul sumados sobre fondo oscuro, del negro al blanco. */
@Composable
fun Histogram3(bins: Array<FloatArray>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF14141A))) {
        val colors = listOf(Color(0xFFFF4D4D), Color(0xFF3DDC84), Color(0xFF4D8DFF))
        bins.forEachIndexed { channel, values ->
            val w = size.width / (values.size - 1)
            val path = Path().apply {
                moveTo(0f, size.height)
                values.forEachIndexed { i, v -> lineTo(i * w, size.height - v * (size.height - 6f)) }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, colors[channel].copy(alpha = 0.55f), blendMode = BlendMode.Plus)
            val line = Path().apply { values.forEachIndexed { i, v -> val y = size.height - v * (size.height - 6f); if (i == 0) moveTo(0f, y) else lineTo(i * w, y) } }
            drawPath(line, colors[channel].copy(alpha = 0.8f), style = Stroke(1.2.dp.toPx()))
        }
        // Referencias: sombras, medios tonos y luces.
        for (i in 1..3) drawLine(Color.White.copy(alpha = 0.08f), Offset(size.width * i / 4, 0f), Offset(size.width * i / 4, size.height))
    }
}
