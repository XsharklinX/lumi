package com.lumi.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.UiState
import com.lumi.galeria.data.AlbumSort
import kotlin.math.roundToInt

/** Alto de cada fila; arrastrar esta distancia mueve el álbum un puesto. */
private val ROW = 68.dp

/**
 * Ordenar los álbumes a mano: se mantiene pulsado uno y se arrastra a su sitio. Al pulsar «Listo»
 * el orden se guarda y Álbumes pasa a usarlo.
 */
@Composable
fun AlbumOrderScreen(state: UiState, vm: LumiViewModel) {
    val byId = remember(state.albums) { state.albums.associateBy { it.bucketId } }
    var order by remember { mutableStateOf(state.albums.map { it.bucketId }) }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { ROW.toPx() }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Ordenar álbumes", "Mantén pulsado uno y arrástralo a su sitio", onBack = { vm.back() })
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            items(order, key = { it }) { id ->
                val album = byId[id] ?: return@items
                val lifted = dragging == id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW)
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (lifted) offset else 0f
                            scaleX = if (lifted) 1.03f else 1f
                            scaleY = if (lifted) 1.03f else 1f
                        }
                        .then(if (lifted) Modifier.shadow(10.dp, RoundedCornerShape(16.dp)) else Modifier)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (lifted) Lumi.Surface else Lumi.Bg)
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragging = id
                                    offset = 0f
                                },
                                onDragEnd = { dragging = null; offset = 0f },
                                onDragCancel = { dragging = null; offset = 0f },
                            ) { change, amount ->
                                change.consume()
                                offset += amount.y
                                // Cada vez que pasa media fila de la vecina, cambia el puesto.
                                val from = order.indexOf(id)
                                val shift = (offset / rowPx).roundToInt()
                                val to = (from + shift).coerceIn(0, order.lastIndex)
                                if (to != from) {
                                    order = order.toMutableList().apply { add(to, removeAt(from)) }
                                    offset -= (to - from) * rowPx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Menu, "Arrastrar", Modifier.size(22.dp), tint = Lumi.Muted)
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Lumi.Surface), contentAlignment = Alignment.Center) {
                        if (album.locked) Icon(Icons.Filled.Lock, "Bloqueado", Modifier.size(20.dp), tint = Lumi.Accent)
                        else MediaThumb(album.cover, 160, Modifier.fillMaxSize())
                    }
                    Text(album.name, style = HeadingStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Cancelar", { vm.back() }, Modifier.weight(1f), primary = false)
            PillButton("Listo", {
                vm.setAlbumOrder(order)
                vm.setAlbumSort(AlbumSort.MANUAL)
                vm.back()
            }, Modifier.weight(1f))
        }
    }
}
