package com.lumi.galeria.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Thumb
import com.lumi.galeria.UiState
import com.lumi.galeria.dayTitle

/** Lo que dura cada foto en pantalla. */
private const val STORY_MS = 4500

/**
 * Un recuerdo a pantalla completa, como las historias: las fotos pasan solas con un acercamiento
 * lento y barras arriba. Tocar a la derecha avanza, a la izquierda vuelve; mantener pulsado pausa.
 */
@Composable
fun StoryScreen(screen: Screen.StoryView, state: UiState, vm: LumiViewModel) {
    val items = remember(screen.ids) {
        val byId = state.items.associateBy { it.id }
        screen.ids.mapNotNull { byId[it] }
    }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val shown = remember { intArrayOf(-1) }

    fun next() {
        if (index < items.lastIndex) index++ else vm.back()
    }

    fun previous() {
        if (index > 0) index-- else shown[0] = -1
    }

    LaunchedEffect(index, paused) {
        // Foto nueva: la barra empieza de cero. Al volver de una pausa sigue donde iba.
        if (shown[0] != index) {
            progress.snapTo(0f)
            shown[0] = index
        }
        if (paused) return@LaunchedEffect
        progress.animateTo(1f, tween(((1f - progress.value) * STORY_MS).toInt().coerceAtLeast(1), easing = LinearEasing))
        next()
    }
    BackHandler { vm.back() }

    val item = items[index.coerceIn(0, items.lastIndex)]
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(items.size) {
                detectTapGestures(
                    onPress = {
                        paused = true
                        tryAwaitRelease()
                        paused = false
                    },
                    onTap = { point -> if (point.x < size.width * 0.3f) previous() else next() },
                )
            },
    ) {
        AsyncImage(
            Thumb(item.uri, 1440, item.modified), null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // Acercamiento lento mientras se ve.
                val zoom = 1f + 0.08f * progress.value
                scaleX = zoom
                scaleY = zoom
            },
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.25f to Color.Transparent, 0.65f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Con muchas fotos, una barra por foto no cabe: se agrupan en como mucho 30 tramos.
            val segments = minOf(items.size, 30)
            val current = index * segments / items.size
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until segments) {
                    val fill = when {
                        i < current -> 1f
                        i > current -> 0f
                        else -> progress.value
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f))) {
                        Box(Modifier.fillMaxWidth(fill).height(3.dp).background(Color.White))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1} / ${items.size}", style = SmallStyle, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f).padding(start = 4.dp))
                Text(
                    "Ver todas",
                    style = LabelStyle,
                    color = Color.White,
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).pointerInput(Unit) {
                        detectTapGestures { vm.replaceTop(screen.grid) }
                    }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
                BarIcon(Icons.Filled.Close, "Cerrar", { vm.back() }, Color.White)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(20.dp)) {
            Text(screen.title, style = TitleStyle.copy(fontSize = 30.sp), color = Color.White)
            Text(dayTitle(item.date), style = SmallStyle.copy(fontSize = 14.sp), color = Color.White.copy(alpha = 0.85f))
        }
    }
}
