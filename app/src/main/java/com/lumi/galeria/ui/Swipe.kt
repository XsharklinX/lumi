package com.lumi.galeria.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.dayTitle
import com.lumi.galeria.formatSize
import kotlinx.coroutines.launch

/** De dónde salen las fotos del repaso. */
private enum class Pile(val label: String) { SHOTS("Capturas"), WHATSAPP("WhatsApp"), DOWNLOADS("Descargas"), ALL("Todo") }

/** Una decisión del repaso: qué foto y si se borra. Se guardan en orden para poder deshacer. */
private class Decision(val item: MediaItem, val delete: Boolean)

/**
 * Repaso rápido: una foto cada vez. Deslizar a la izquierda la aparta para borrar; a la derecha,
 * se queda. Nada se borra hasta pulsar «Borrar», y entonces Android pide confirmarlo una sola vez
 * para todas. Lo que se conserva no vuelve a salir en otros repasos.
 */
@Composable
fun SwipeScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pile by remember { mutableStateOf(Pile.SHOTS) }
    var decisions by remember { mutableStateOf(emptyList<Decision>()) }
    val decided = decisions.mapTo(HashSet()) { it.item.id }
    val queue = remember(state.items, pile, state.reviewed) {
        state.items.asReversed().filter { item ->
            item.id !in state.reviewed && when (pile) {
                Pile.SHOTS -> item.isScreenshot
                Pile.WHATSAPP -> item.bucket.contains("whatsapp", ignoreCase = true) || item.path.contains("WhatsApp", ignoreCase = true)
                Pile.DOWNLOADS -> item.bucket.contains("download", ignoreCase = true)
                Pile.ALL -> true
            }
        }
    }
    val remaining = queue.filter { it.id !in decided }
    val toDelete = decisions.filter { it.delete }.map { it.item }
    val offset = remember { Animatable(0f) }
    var width by remember { mutableStateOf(1) }

    fun decide(delete: Boolean) {
        val item = remaining.firstOrNull() ?: return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        decisions = decisions + Decision(item, delete)
        if (!delete) vm.markReviewed(item.id)
        scope.launch { offset.snapTo(0f) }
    }

    fun fling(delete: Boolean) {
        scope.launch {
            offset.animateTo(if (delete) -width * 1.4f else width * 1.4f, tween(180))
            decide(delete)
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(
            "Repaso rápido",
            if (toDelete.isEmpty()) "Izquierda para borrar, derecha para quedarte con ella"
            else "Para borrar: ${countText(toDelete.size, "foto", "fotos")} · ${formatSize(toDelete.sumOf { it.size })}",
            onBack = { vm.back() },
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pile.entries.forEach { option ->
                val on = option == pile
                Text(
                    option.label,
                    style = LabelStyle,
                    color = if (on) Lumi.OnAccent else Lumi.Ink,
                    modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Surface).clickable { pile = option }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        Text(
            if (remaining.isEmpty()) "" else "Quedan ${remaining.size}",
            style = SmallStyle,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp).onSizeChanged { width = it.width.coerceAtLeast(1) }, contentAlignment = Alignment.Center) {
            val current = remaining.getOrNull(0)
            val next = remaining.getOrNull(1)
            if (current == null) {
                EmptyMessage("Repaso terminado", if (toDelete.isEmpty()) "No queda nada por revisar aquí." else "Pulsa «Borrar» para mandar a la papelera lo que apartaste.")
            } else {
                if (next != null) {
                    Card(next, Modifier.graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; translationY = 24f; alpha = 0.6f })
                }
                Card(
                    current,
                    Modifier
                        .graphicsLayer {
                            translationX = offset.value
                            rotationZ = offset.value / 40f
                        }
                        .pointerInput(current.id) {
                            detectDragGestures(
                                onDragEnd = {
                                    when {
                                        offset.value < -size.width * 0.3f -> fling(delete = true)
                                        offset.value > size.width * 0.3f -> fling(delete = false)
                                        else -> scope.launch { offset.animateTo(0f) }
                                    }
                                },
                            ) { change, drag ->
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + drag.x) }
                            }
                        },
                    // Se ve hacia dónde va mientras se arrastra.
                    label = when {
                        offset.value < -40f -> "Borrar"
                        offset.value > 40f -> "Conservar"
                        else -> null
                    },
                    danger = offset.value < 0f,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Round("✕", Lumi.Danger, enabled = remaining.isNotEmpty()) { fling(delete = true) }
            Round("↶", Lumi.Muted, small = true, enabled = decisions.isNotEmpty()) {
                // Deshacer la última: vuelve a salir y, si se había conservado, deja de estarlo.
                val last = decisions.last()
                decisions = decisions.dropLast(1)
                if (!last.delete) vm.forgetReviewed(last.item.id)
            }
            Round("♥", Color(0xFF7FD8A6), enabled = remaining.isNotEmpty()) { fling(delete = false) }
        }
        PillButton(
            if (toDelete.isEmpty()) "Nada apartado todavía" else "Borrar ${toDelete.size} · ${formatSize(toDelete.sumOf { it.size })}",
            {
                val chosen = toDelete
                actions.trash(chosen) { decisions = decisions.filter { !it.delete } }
            },
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            enabled = toDelete.isNotEmpty(),
        )
    }
}

@Composable
private fun Card(item: MediaItem, modifier: Modifier, label: String? = null, danger: Boolean = false) {
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(Lumi.Surface)) {
        MediaThumb(item, 1024, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(dayTitle(item.date), style = LabelStyle, color = Color.White)
            Text(formatSize(item.size), style = SmallStyle, color = Color.White.copy(alpha = 0.85f))
        }
        if (label != null) {
            val tint = if (danger) Lumi.Danger else Color(0xFF7FD8A6)
            Text(
                label,
                style = HeadingStyle.copy(fontSize = 20.sp),
                color = tint,
                modifier = Modifier.align(if (danger) Alignment.TopEnd else Alignment.TopStart).padding(18.dp)
                    .graphicsLayer { rotationZ = if (danger) 12f else -12f }
                    .border(3.dp, tint, RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Round(symbol: String, tint: Color, small: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 12.dp).size(if (small) 44.dp else 62.dp).clip(CircleShape).background(Lumi.Surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = HeadingStyle.copy(fontSize = if (small) 18.sp else 24.sp), color = if (enabled) tint else Lumi.Line)
    }
}
