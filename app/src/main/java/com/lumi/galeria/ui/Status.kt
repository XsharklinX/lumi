package com.lumi.galeria.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.CrashLog
import com.lumi.galeria.formatCount
import com.lumi.galeria.formatSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun sizeOf(file: File): Long = if (file.isFile) file.length() else file.listFiles()?.sumOf { sizeOf(it) } ?: 0L

private class Usage(val cache: Long, val index: Long, val stickers: Long, val vault: Long, val other: Long)

private fun measure(context: Context): Usage {
    val files = context.filesDir
    val vault = files.listFiles()?.filter { it.name.startsWith("vault") }?.sumOf { sizeOf(it) } ?: 0L
    val stickers = sizeOf(File(files, "pegatinas"))
    val databases = context.getDatabasePath("analisis.db").parentFile?.let { sizeOf(it) } ?: 0L
    val total = sizeOf(files)
    return Usage(sizeOf(context.cacheDir), databases, stickers, vault, (total - vault - stickers).coerceAtLeast(0L))
}

/** Borra lo que Lumi puede volver a hacer sola: miniaturas guardadas y copias temporales de envíos. */
private fun clearCache(context: Context) {
    runCatching { context.imageLoader.memoryCache?.clear() }
    runCatching { context.imageLoader.diskCache?.clear() }
    context.cacheDir.listFiles()?.forEach { f -> if (!f.name.startsWith("vault")) runCatching { f.deleteRecursively() } }
}

/** Abre el correo con el informe ya escrito. Es el usuario quien lo envía. */
fun sendCrashReport(context: Context, report: String) {
    val subject = "Informe de fallo de Lumi"
    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:contactosharklin@gmail.com"))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, report)
    val ok = runCatching { context.startActivity(mail) }.isSuccess
    if (!ok) {
        val plain = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf("contactosharklin@gmail.com"))
            .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, report)
        runCatching { context.startActivity(Intent.createChooser(plain, null)) }
    }
}

/** Al abrir Lumi después de un cierre inesperado: ofrece enviar el informe, mostrando exactamente qué lleva. */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.pending(context)) }
    val text = report ?: return
    AlertDialog(
        onDismissRequest = { },
        containerColor = Lumi.Surface,
        title = { Text("Lumi se cerró la última vez", style = HeadingStyle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Si quieres, envía este informe y lo arreglamos. Lleva la versión, el modelo del móvil y las líneas del fallo; no lleva fotos, nombres ni datos tuyos. No se envía nada solo.",
                    style = SmallStyle.copy(fontSize = 14.sp, color = Lumi.Ink),
                )
                Text(
                    text, style = SmallStyle.copy(fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp).clip(RoundedCornerShape(12.dp)).background(Lumi.Bg).padding(10.dp).verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            Text("Enviar informe", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.defaultMinSize(minHeight = 48.dp).clip(CircleShape).clickable {
                sendCrashReport(context, text)
                CrashLog.clear(context)
                report = null
            }.padding(12.dp))
        },
        dismissButton = {
            Text("Descartar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.defaultMinSize(minHeight = 48.dp).clip(CircleShape).clickable {
                CrashLog.clear(context)
                report = null
            }.padding(12.dp))
        },
    )
}

@Composable
private fun StatusCard(title: String, rows: List<Pair<String, String>>, content: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = HeadingStyle.copy(fontSize = 17.sp))
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(label, style = SmallStyle.copy(fontSize = 14.sp, color = Lumi.Ink), modifier = Modifier.weight(1f))
                Text(value, style = LabelStyle, color = Lumi.Muted)
            }
        }
        content()
    }
}

/** Cuánto ocupa Lumi, qué ha mirado ya, y los dos botones para cuando algo se ve raro. */
@Composable
fun StatusScreen(state: UiState, vm: LumiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val usage by produceState<Usage?>(null, refresh) { value = withContext(Dispatchers.IO) { measure(context) } }
    var confirmReindex by remember { mutableStateOf(false) }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    val crash = remember(refresh) { CrashLog.pending(context) }
    LaunchedEffect(Unit) { vm.discover("estado") }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Estado de Lumi", "Versión $version", onBack = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusCard(
                "Lo que Lumi ha mirado",
                listOf(
                    "Fotos y vídeos" to formatCount(state.items.size),
                    "Fotos ya analizadas" to "${formatCount((state.photoCount - state.unindexed).coerceAtLeast(0))} de ${formatCount(state.photoCount)}",
                    "Fotos con gente" to if (state.facesTotal == 0) "—" else "${formatCount(state.facesLooked)} de ${formatCount(state.facesTotal)}",
                    "Personas encontradas" to formatCount(state.people.size),
                    "Etiquetas y notas" to formatCount(state.notes.size),
                ),
            )
            val u = usage
            StatusCard(
                "Lo que ocupa en el teléfono",
                if (u == null) listOf("Midiendo…" to "") else listOf(
                    "Miniaturas y copias temporales" to formatSize(u.cache),
                    "Índice y caras" to formatSize(u.index),
                    "Pegatinas" to formatSize(u.stickers),
                    "Carpeta privada" to formatSize(u.vault),
                    "Otros datos de Lumi" to formatSize(u.other),
                ),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Limpiar la caché", {
                        scope.launch {
                            withContext(Dispatchers.IO) { clearCache(context) }
                            vm.say("Caché limpia")
                            refresh++
                        }
                    }, modifier = Modifier.weight(1f), primary = false)
                }
                Text("La caché se vuelve a llenar sola: no se pierde ninguna foto, solo habrá que esperar un momento a ver las miniaturas.", style = SmallStyle)
            }
            StatusCard("Si algo se ve raro", emptyList()) {
                Text("Volver a analizar borra lo que Lumi sabe de tus fotos (cosas, texto, lugares) y lo lee otra vez, despacio y en segundo plano. Tus fotos, las personas y la carpeta privada no se tocan.", style = SmallStyle)
                PillButton("Volver a analizar las fotos", { confirmReindex = true }, modifier = Modifier.fillMaxWidth(), primary = false)
            }
            if (crash != null) {
                StatusCard("Último cierre inesperado", emptyList()) {
                    Text(crash, style = SmallStyle.copy(fontSize = 11.sp), modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp).verticalScroll(rememberScrollState()))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Enviar informe", { sendCrashReport(context, crash); CrashLog.clear(context); refresh++ }, modifier = Modifier.weight(1f))
                        PillButton("Descartar", { CrashLog.clear(context); refresh++ }, modifier = Modifier.weight(1f), primary = false)
                    }
                }
            }
        }
    }
    if (confirmReindex) {
        AlertDialog(
            onDismissRequest = { confirmReindex = false },
            containerColor = Lumi.Surface,
            title = { Text("¿Volver a analizar?", style = HeadingStyle) },
            text = { Text("Mientras termina, el buscador irá encontrando cosas poco a poco. Gasta algo de batería.", style = SmallStyle.copy(fontSize = 14.sp, color = Lumi.Ink)) },
            confirmButton = {
                Text("Volver a analizar", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.defaultMinSize(minHeight = 48.dp).clip(CircleShape).clickable { confirmReindex = false; vm.reindex() }.padding(12.dp))
            },
            dismissButton = {
                Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.defaultMinSize(minHeight = 48.dp).clip(CircleShape).clickable { confirmReindex = false }.padding(12.dp))
            },
        )
    }
}
