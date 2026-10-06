package com.lumi.galeria.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.formatDuration
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun decode(context: android.content.Context, item: MediaItem, maxSide: Int): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
        val longSide = maxOf(info.size.width, info.size.height)
        if (longSide > maxSide) {
            val k = maxSide.toFloat() / longSide
            decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
        }
    }
}.getOrNull()

// ---------- Carpetas ocultas del sistema ----------

@Composable
fun HiddenFoldersScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    var allowed by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    LaunchedEffect(allowed) { if (allowed && state.hiddenFolders == null) vm.scanHiddenFolders() }
    val folders = state.hiddenFolders

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Carpetas ocultas", if (folders == null) "" else countText(folders.size, "carpeta", "carpetas"), onBack = { vm.back() })
        when {
            !allowed -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Hace falta un permiso especial", style = HeadingStyle.copy(fontSize = 20.sp))
                Text(
                    "Algunas apps guardan fotos y vídeos en carpetas que Android esconde a las galerías. Para verlas, Lumi necesita " +
                        "el acceso a todos los archivos, que se concede en los ajustes del sistema. Sigue sin salir nada del teléfono.",
                    style = SmallStyle.copy(fontSize = 14.sp),
                )
                PillButton("Abrir los ajustes del permiso", actions.allFiles, Modifier.fillMaxWidth())
                PillButton("Ya lo he concedido", { allowed = Environment.isExternalStorageManager() }, Modifier.fillMaxWidth(), primary = false)
            }
            state.scanningHidden || folders == null -> EmptyMessage("Buscando…", "Lumi está recorriendo las carpetas del teléfono.")
            folders.isEmpty() -> EmptyMessage("No hay nada escondido", "Ninguna carpeta oculta tiene fotos ni vídeos.")
            else -> LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        "Se pueden ver, pero no borrar ni mover desde aquí.",
                        style = SmallStyle,
                        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    )
                }
                items(folders, key = { it.path }) { folder ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Lumi.Surface)
                            .clickable { vm.open(Screen.Items(folder.name, Source.Hidden(folder.path))) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaThumb(folder.items.first(), 160, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.name, style = HeadingStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(folder.path.substringAfter("/0/"), style = SmallStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${folder.items.size}", style = LabelStyle, color = Lumi.Accent)
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
}
