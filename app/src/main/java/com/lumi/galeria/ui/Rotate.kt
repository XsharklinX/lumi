package com.lumi.galeria.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Girar una foto viendo cómo queda antes de guardar. Se gira a la izquierda o a la derecha y se
 * puede dar la vuelta; después se guarda como copia o en lugar de la original.
 */
@Composable
fun RotateScreen(screen: Screen.Rotate, state: UiState, vm: LumiViewModel, actions: Actions) {
    val item = state.items.firstOrNull { it.id == screen.id && !it.isVideo }
    if (item == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longSide = maxOf(info.size.width, info.size.height)
                    if (longSide > 4096) {
                        val k = 4096f / longSide
                        decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                    }
                }
            }.getOrNull()
        }
    }
    // Cuartos de vuelta a la derecha; puede ser negativo o pasar de 4, solo cuenta el resto.
    var turns by remember { mutableIntStateOf(0) }
    var mirrored by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val angle by animateFloatAsState(turns * 90f, tween(220), label = "giro")
    val quarter = ((turns % 4) + 4) % 4
    val changed = quarter != 0 || mirrored

    fun render(source: Bitmap): Bitmap {
        val matrix = Matrix()
        if (mirrored) matrix.postScale(-1f, 1f)
        matrix.postRotate(quarter * 90f)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun save(replace: Boolean) {
        val source = bitmap ?: return
        saving = true
        scope.launch {
            val done = withContext(Dispatchers.Default) { runCatching { render(source) }.getOrNull() }
            saving = false
            when {
                done == null -> vm.say("No hay memoria suficiente para esta foto")
                replace -> actions.write(listOf(item)) { vm.replaceEdit(item, done) }
                else -> vm.saveEdit(item, done)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Cancelar", { vm.back() }, primary = false)
            Text("Girar", style = HeadingStyle, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            val source = bitmap
            if (source != null) {
                val image = remember(source) { source.asImageBitmap() }
                // Tumbada, la foto tiene que caber con el ancho y el alto cambiados.
                val sideways = quarter % 2 == 1
                val fitStraight = minOf(maxWidth.value / source.width, maxHeight.value / source.height)
                val fitTurned = minOf(maxWidth.value / source.height, maxHeight.value / source.width)
                val shrink = if (sideways) fitTurned / fitStraight else 1f
                val scale by animateFloatAsState(shrink, tween(220), label = "tamaño")
                Image(
                    image, null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        rotationZ = angle
                        scaleX = scale * if (mirrored) -1f else 1f
                        scaleY = scale
                    },
                )
            } else {
                Text("Abriendo la foto…", style = SmallStyle)
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("↺ Izquierda", { turns-- }, Modifier.weight(1f), primary = false)
                PillButton("Derecha ↻", { turns++ }, Modifier.weight(1f), primary = false)
                PillButton("Espejo", { mirrored = !mirrored }, Modifier.weight(1f), primary = mirrored)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (saving) "Guardando…" else "Guardar copia", { save(replace = false) }, Modifier.weight(1f), primary = false, enabled = changed && !saving && bitmap != null)
                PillButton("Reemplazar la original", { save(replace = true) }, Modifier.weight(1f), enabled = changed && !saving && bitmap != null)
            }
        }
    }
}
