package com.lumi.galeria.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.AppLanguage
import com.lumi.galeria.CameraActivity
import com.lumi.galeria.Lang
import com.lumi.galeria.R
import com.lumi.galeria.data.readShowcaseOut
import com.lumi.galeria.tr
import com.lumi.galeria.ui.AccentColor
import com.lumi.galeria.ui.HeadingStyle
import com.lumi.galeria.ui.LabelStyle
import com.lumi.galeria.ui.Lumi
import com.lumi.galeria.ui.LumiTheme
import com.lumi.galeria.ui.ScreenHeader
import com.lumi.galeria.ui.SmallStyle
import com.lumi.galeria.ui.Text
import com.lumi.galeria.ui.ThemeMode
import android.content.ContentUris
import android.provider.MediaStore
import android.util.Size

/** Qué abre el widget de cámara. */
enum class CameraWidgetKind(val label: String, val hint: String, val mode: String, val front: Boolean, val icon: Int) {
    PHOTO("Cámara", "Abre la Cámara Lumi para hacer fotos", "PHOTO", false, R.drawable.ic_shortcut_camera),
    SELFIE("Selfie", "Ya con la cámara delantera", "PHOTO", true, R.drawable.ic_widget_selfie),
    VIDEO("Vídeo", "Lista para grabar", "VIDEO", false, R.drawable.ic_widget_video),
    QR("QR", "Para leer un código al momento", "QR", false, R.drawable.ic_shortcut_qr),
}

object CameraWidgets {
    private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun kind(context: Context, id: Int): CameraWidgetKind =
        runCatching { CameraWidgetKind.valueOf(prefs(context).getString("cam_$id", null) ?: "PHOTO") }.getOrDefault(CameraWidgetKind.PHOTO)

    fun save(context: Context, id: Int, kind: CameraWidgetKind) = prefs(context).edit().putString("cam_$id", kind.name).apply()

    fun forget(context: Context, ids: IntArray) {
        val edit = prefs(context).edit()
        ids.forEach { edit.remove("cam_$it").remove("mosaico_$it") }
        edit.apply()
    }

    /** Un botón del tamaño de un icono que abre la Cámara Lumi directamente, en el modo elegido. */
    fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val lumi = context.getSharedPreferences("lumi", Context.MODE_PRIVATE)
        Lang.apply(runCatching { AppLanguage.valueOf(lumi.getString("language", null) ?: "SYSTEM") }.getOrDefault(AppLanguage.SYSTEM))
        val kind = kind(context, id)
        val views = RemoteViews(context.packageName, R.layout.widget_camera)
        views.setImageViewResource(R.id.icon, kind.icon)
        views.setTextViewText(R.id.label, tr(kind.label))
        val open = Intent(context, CameraActivity::class.java)
            .putExtra(CameraActivity.EXTRA_MODE, kind.mode).putExtra(CameraActivity.EXTRA_FRONT, kind.front)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        views.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(context, 8000 + id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        manager.updateAppWidget(id, views)
    }

    private val TILES = intArrayOf(R.id.tile0, R.id.tile1, R.id.tile2, R.id.tile3, R.id.tile4, R.id.tile5)

    /**
     * El mosaico: 4 fotos (o 6 si cabe) de lo elegido al ponerlo. Cada vez que toca cambia una sola,
     * para que se note poco a poco; tocar una la abre en Lumi.
     */
    fun renderMosaic(context: Context, manager: AppWidgetManager, id: Int, advance: Boolean) {
        val config = Widgets.config(context, id)
        val pool = Widgets.candidateIds(context, config)
        val options = manager.getAppWidgetOptions(id)
        val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
        val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 180
        val count = if (wDp >= 250) 6 else 4
        val columns = count / 2
        val p = prefs(context)
        val out = readShowcaseOut(context)
        var shown = p.getString("mosaico_$id", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { it in pool && it !in out }.toMutableList()
        while (shown.size < count) {
            val next = pool.filter { it !in shown }.randomOrNull() ?: break
            shown += next
        }
        val now = System.currentTimeMillis()
        val since = p.getLong("mosaico_since_$id", 0L)
        if ((advance || now - since >= config.hours * 3_600_000L - 300_000L) && shown.isNotEmpty()) {
            pool.filter { it !in shown }.randomOrNull()?.let { fresh -> shown[shown.indices.random()] = fresh }
            p.edit().putLong("mosaico_since_$id", now).apply()
        }
        p.edit().putString("mosaico_$id", shown.joinToString(",")).apply()

        val views = RemoteViews(context.packageName, R.layout.widget_mosaic)
        val density = context.resources.displayMetrics.density
        val side = ((minOf(wDp / columns, hDp / 2) * density).toInt()).coerceIn(96, 420)
        TILES.forEachIndexed { i, tile ->
            val column = i % 3
            if (column >= columns) {
                views.setViewVisibility(tile, View.GONE)
                return@forEachIndexed
            }
            val index = (i / 3) * columns + column
            val photo = shown.getOrNull(index)
            views.setViewVisibility(tile, View.VISIBLE)
            if (photo == null) {
                views.setImageViewResource(tile, R.drawable.widget_tile_empty)
                return@forEachIndexed
            }
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photo)
            val bitmap = runCatching { context.contentResolver.loadThumbnail(uri, Size(side, side), null) }.getOrNull()
            if (bitmap != null) views.setImageViewBitmap(tile, Widgets.squareRounded(bitmap, side, 10f * density)) else views.setImageViewResource(tile, R.drawable.widget_tile_empty)
            val open = Intent(context, com.lumi.galeria.MainActivity::class.java).setAction(Widgets.ACTION_OPEN).putExtra(Widgets.EXTRA_PHOTO, photo)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(tile, PendingIntent.getActivity(context, 9000 + id * 8 + i, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        manager.updateAppWidget(id, views)
    }
}

/** Al poner el widget de cámara: qué abre. */
class CameraWidgetSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val prefs = getSharedPreferences("lumi", Context.MODE_PRIVATE)
        Lang.apply(runCatching { AppLanguage.valueOf(prefs.getString("language", null) ?: "SYSTEM") }.getOrDefault(AppLanguage.SYSTEM))
        val theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
        val accent = runCatching { AccentColor.valueOf(prefs.getString("accent", null) ?: "LILAC") }.getOrDefault(AccentColor.LILAC)
        setContent {
            LumiTheme(theme, accent, prefs.getBoolean("pureBlack", false)) {
                Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
                    ScreenHeader("Widget de cámara", "Elige qué abre al tocarlo", onBack = { finish() })
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CameraWidgetKind.entries.forEach { kind ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Lumi.Surface).clickable {
                                    CameraWidgets.save(this@CameraWidgetSetupActivity, id, kind)
                                    CameraWidgets.render(applicationContext, AppWidgetManager.getInstance(this@CameraWidgetSetupActivity), id)
                                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                    finish()
                                }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(Modifier.size(48.dp)) {
                                    androidx.compose.foundation.Image(painterResource(kind.icon), null, Modifier.fillMaxSize())
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(kind.label, style = HeadingStyle.copy(fontSize = 16.sp))
                                    Text(kind.hint, style = SmallStyle)
                                }
                            }
                        }
                        Text("Puedes poner varios, uno de cada.", style = SmallStyle, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

