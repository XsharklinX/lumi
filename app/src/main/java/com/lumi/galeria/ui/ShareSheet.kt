package com.lumi.galeria.ui

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.lumi.galeria.countText
import com.lumi.galeria.data.MediaItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class ShareTarget(val component: ComponentName, val label: String, val icon: Bitmap?)

/** Las apps a las que más se envía, primero. Cuenta cada envío desde esta hoja. */
private fun targetsFor(context: Context, video: Boolean): List<ShareTarget> {
    val prefs = context.getSharedPreferences("compartir", Context.MODE_PRIVATE)
    val probe = Intent(Intent.ACTION_SEND).setType(if (video) "video/*" else "image/*")
    return runCatching {
        context.packageManager.queryIntentActivities(probe, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .sortedWith(compareByDescending<android.content.pm.ResolveInfo> { prefs.getInt("n_" + it.activityInfo.packageName, 0) }
                .thenBy { it.loadLabel(context.packageManager).toString().lowercase() })
            .take(10)
            .map {
                ShareTarget(
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    it.loadLabel(context.packageManager).toString(),
                    runCatching { it.loadIcon(context.packageManager).toBitmap(96, 96) }.getOrNull(),
                )
            }
    }.getOrDefault(emptyList())
}

/** Una copia pequeña (1600 puntos de lado largo) para enviar por chat sin gastar datos. */
private fun shrinkForChat(context: Context, item: MediaItem, index: Int): Uri? {
    if (item.isVideo || item.isGif) return item.uri
    return runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val long = maxOf(info.size.width, info.size.height)
            if (long > 1600) {
                val k = 1600f / long
                decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
            }
        }
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "chat_${index}_${item.name.substringBeforeLast('.')}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }.getOrNull()
}

private fun send(context: Context, uris: List<Uri>, video: Boolean, only: ComponentName?) {
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    intent.setType(if (video) "video/*" else "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    if (only != null) {
        intent.component = only
        runCatching { context.startActivity(intent) }
    } else {
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
    }
}

/**
 * Enviar en dos toques: tus apps de siempre arriba, con un interruptor para reducir las fotos antes de mandarlas
 * por chat. «Más opciones» abre el selector normal de Android.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(items: List<MediaItem>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("compartir", Context.MODE_PRIVATE) }
    val video = items.all { it.isVideo }
    val targets = remember(video) { targetsFor(context, video) }
    var small by remember { mutableStateOf(prefs.getBoolean("reducir", false)) }
    var working by remember { mutableStateOf(false) }
    val photos = items.any { !it.isVideo && !it.isGif }

    fun go(component: ComponentName?) {
        if (working) return
        working = true
        scope.launch {
            val uris = withContext(Dispatchers.IO) {
                if (small && photos) items.mapIndexedNotNull { i, item -> shrinkForChat(context, item, i) } else items.map { it.uri }
            }
            if (component != null) prefs.edit().putInt("n_" + component.packageName, prefs.getInt("n_" + component.packageName, 0) + 1).apply()
            send(context, uris, video, component)
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lumi.Surface) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (items.size == 1) (if (video) "Enviar vídeo" else "Enviar foto") else "Enviar " + countText(items.size, "elemento", "elementos"),
                style = HeadingStyle,
            )
            if (photos) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lumi.Bg)
                        .clickable { small = !small; prefs.edit().putBoolean("reducir", small).apply() }
                        .defaultMinSize(minHeight = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Reducir para enviar por chat", style = LabelStyle)
                        Text("Copias de 1600 puntos: pesan mucho menos y se ven igual en el móvil.", style = SmallStyle)
                    }
                    Switch(small, { small = it; prefs.edit().putBoolean("reducir", it).apply() }, colors = SwitchDefaults.colors(checkedTrackColor = Lumi.Accent))
                }
            }
            if (targets.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    targets.forEach { t ->
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable { go(t.component) }.padding(vertical = 8.dp)
                                .semantics { contentDescription = t.label },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            t.icon?.let { Image(BitmapPainter(it.asImageBitmap()), null, Modifier.size(48.dp)) }
                            Text(t.label, style = SmallStyle.copy(fontSize = 12.sp), color = Lumi.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Text(
                if (working) "Preparando…" else "Más opciones",
                style = LabelStyle, color = Lumi.Accent,
                modifier = Modifier.clip(CircleShape).defaultMinSize(minHeight = 48.dp).clickable { go(null) }.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}
