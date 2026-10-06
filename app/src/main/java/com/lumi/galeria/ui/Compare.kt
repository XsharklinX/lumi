package com.lumi.galeria.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.formatSize
import com.lumi.galeria.timeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Comparar dos fotos ----------

/**
 * Dos fotos, una encima de otra, que se amplían y se mueven a la vez: así se ve en cuál salió
 * mejor el detalle que importa antes de decidir cuál se queda.
 */
@Composable
fun CompareScreen(screen: Screen.Compare, state: UiState, vm: LumiViewModel, actions: Actions) {
    val first = state.items.firstOrNull { it.id == screen.first }
    val second = state.items.firstOrNull { it.id == screen.second }
    // Al borrar una de las dos ya no hay nada que comparar.
    if (first == null || second == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Comparar", "Pellizca en cualquiera: las dos se mueven juntas", onBack = { vm.back() })
        Column(
            Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 8f)
                        // El desplazamiento se limita a lo que la ampliación deja fuera de la vista.
                        val reachX = size.width * (next - 1) / 2
                        val reachY = size.height / 2f * (next - 1) / 2
                        offset = if (next == 1f) Offset.Zero else Offset(
                            (offset.x + pan.x).coerceIn(-reachX, reachX),
                            (offset.y + pan.y).coerceIn(-reachY, reachY),
                        )
                        scale = next
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 3f
                        }
                    })
                },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(first, second).forEach { item ->
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black).clipToBounds()) {
                        AsyncImage(
                            ImageRequest.Builder(LocalContext.current).data(item.uri).size(3072).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        )
                    }
                    Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${timeText(item.date)} · ${item.width} × ${item.height} · ${formatSize(item.size)}",
                            style = SmallStyle,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (item.id in state.favorites) "Favorita ✓" else "Favorita",
                            style = LabelStyle, color = Lumi.Accent,
                            modifier = Modifier.clip(CircleShape).clickable { vm.toggleFavorite(listOf(item.id)) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                        Text(
                            "Borrar esta",
                            style = LabelStyle, color = Lumi.Danger,
                            modifier = Modifier.clip(CircleShape).clickable { actions.trash(listOf(item)) {} }.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------- Quitar el fondo ----------
