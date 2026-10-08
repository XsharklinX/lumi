package com.lumi.galeria.widget

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumi.galeria.AppLanguage
import com.lumi.galeria.Lang
import com.lumi.galeria.Thumb
import com.lumi.galeria.countText
import com.lumi.galeria.data.albumName
import com.lumi.galeria.ui.AccentColor
import com.lumi.galeria.ui.HeadingStyle
import com.lumi.galeria.ui.LabelStyle
import com.lumi.galeria.ui.Lumi
import com.lumi.galeria.ui.LumiTheme
import com.lumi.galeria.ui.PillButton
import com.lumi.galeria.ui.ScreenHeader
import com.lumi.galeria.ui.SmallStyle
import com.lumi.galeria.ui.Text
import com.lumi.galeria.ui.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un álbum del teléfono para el widget: su carpeta, cuántas fotos tiene y la última. */
private class PhoneAlbum(val path: String, val name: String, val count: Int, val latest: Long)

private fun phoneAlbums(context: Context): List<PhoneAlbum> {
    val byPath = LinkedHashMap<String, Triple<String, Int, Long>>()
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            null, null, "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(1) ?: continue
                if (path.contains("Screenshot", ignoreCase = true)) continue
                val now = byPath[path]
                byPath[path] = if (now == null) Triple(c.getString(2).orEmpty(), 1, c.getLong(0)) else now.copy(second = now.second + 1)
            }
        }
    }
    return byPath.map { (path, v) -> PhoneAlbum(path, albumName(v.first), v.second, v.third) }.filter { it.count >= 2 }.sortedByDescending { it.count }
}

/**
 * Se abre al poner el widget de fotos, y al mantenerlo pulsado para cambiarlo: qué fotos enseña
 * y cada cuánto cambia. Hasta que se pulsa «Poner el widget», el widget no se añade.
 */
class WidgetSetupActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Si se sale sin elegir, el widget no se pone.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val prefs = getSharedPreferences("lumi", Context.MODE_PRIVATE)
        Lang.apply(runCatching { AppLanguage.valueOf(prefs.getString("language", null) ?: "SYSTEM") }.getOrDefault(AppLanguage.SYSTEM))
        val theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
        val accent = runCatching { AccentColor.valueOf(prefs.getString("accent", null) ?: "LILAC") }.getOrDefault(AccentColor.LILAC)
        val black = prefs.getBoolean("pureBlack", false)
        val start = Widgets.config(this, widgetId)
        setContent {
            LumiTheme(theme, accent, black) {
                SetupScreen(start, onCancel = { finish() }) { config ->
                    Widgets.saveConfig(this, widgetId, config)
                    val manager = AppWidgetManager.getInstance(this)
                    val id = widgetId
                    val context = applicationContext
                    // Se pinta ya: con pantalla de ajustes, Android no lo pide la primera vez.
                    val mosaic = manager.getAppWidgetInfo(id)?.provider?.className == com.lumi.galeria.MosaicWidget::class.java.name
                    Thread {
                        runCatching {
                            if (mosaic) CameraWidgets.renderMosaic(context, manager, id, advance = false)
                            else Widgets.render(context, manager, id, memoryWidget = false, advance = true)
                        }
                    }.start()
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    finish()
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(start: WidgetConfig, onCancel: () -> Unit, onDone: (WidgetConfig) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var kind by remember { mutableStateOf(start.kind) }
    var picked by remember { mutableStateOf(start.value) }
    var label by remember { mutableStateOf(start.label) }
    var hours by remember { mutableStateOf(start.hours) }
    val albums by produceState(emptyList<PhoneAlbum>()) { value = withContext(Dispatchers.IO) { phoneAlbums(context) } }
    val people = remember { Widgets.readPeople(context) }
    val needsPick = (kind == WidgetKind.ALBUM || kind == WidgetKind.PERSON) && picked.isEmpty()

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Widget de fotos", "Elige qué quieres ver en la pantalla de inicio", onBack = onCancel)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Qué fotos", style = LabelStyle)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetKind.entries.forEach { option ->
                    Chip(option.label, option == kind) {
                        if (option != kind) {
                            kind = option
                            picked = ""
                            label = ""
                        }
                    }
                }
            }
            Text("Cambiar de foto", style = LabelStyle, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Cada hora", 3 to "Cada 3 horas", 24 to "Cada día").forEach { (h, text) -> Chip(text, hours == h) { hours = h } }
            }
            Text(
                when (kind) {
                    WidgetKind.FAVORITES -> "Las fotos que marcas con el corazón. Si aún no hay ninguna, las más recientes."
                    WidgetKind.ALBUM -> "Elige el álbum:"
                    WidgetKind.PERSON -> if (people.isEmpty()) "Pon nombre a alguien en Personas, dentro de Lumi, para poder elegirlo aquí." else "Elige a la persona:"
                    WidgetKind.MEMORY -> "Fotos de estos mismos días en otros años. Si hoy no hay, unas recientes."
                    WidgetKind.RECENT -> "Las últimas fotos que has hecho."
                },
                style = SmallStyle.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 6.dp),
            )
        }
        Box(Modifier.weight(1f).padding(top = 8.dp)) {
            when (kind) {
                WidgetKind.ALBUM -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                    items(albums, key = { it.path }) { album ->
                        PickRow(album.name, countText(album.count, "foto", "fotos"), ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, album.latest), picked == album.path) {
                            picked = album.path
                            label = album.name
                        }
                    }
                }
                WidgetKind.PERSON -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                    items(people, key = { it.group }) { person ->
                        PickRow(
                            person.name, countText(person.photos.size, "foto", "fotos"),
                            person.photos.firstOrNull()?.let { Widgets.photoUri(it) }, picked == person.group.toString(),
                        ) {
                            picked = person.group.toString()
                            label = person.name
                        }
                    }
                }
                else -> Text(
                    "Nunca salen documentos, capturas ni fotos de la carpeta privada. La flecha del widget pone otra foto, y tocar la foto la abre en Lumi.",
                    style = SmallStyle, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        PillButton(
            if (needsPick) (if (kind == WidgetKind.ALBUM) "Elige un álbum" else "Elige a una persona") else "Poner el widget",
            onClick = { onDone(WidgetConfig(kind, picked, label, hours)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            enabled = !needsPick,
        )
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, style = LabelStyle, color = if (on) Lumi.OnAccent else Lumi.Ink,
        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun PickRow(title: String, subtitle: String, photo: android.net.Uri?, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lumi.Surface)
            .then(if (on) Modifier.border(2.dp, Lumi.Accent, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Lumi.Bg)) {
            if (photo != null) AsyncImage(Thumb(photo, 256, 0), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = HeadingStyle.copy(fontSize = 15.sp))
            Text(subtitle, style = SmallStyle)
        }
        if (on) Box(Modifier.size(22.dp).clip(CircleShape).background(Lumi.Accent), contentAlignment = Alignment.Center) {
            Text("✓", style = LabelStyle, color = Lumi.OnAccent)
        }
    }
}
