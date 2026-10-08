package com.lumi.galeria.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import com.lumi.galeria.AppLanguage
import com.lumi.galeria.Lang
import com.lumi.galeria.MainActivity
import com.lumi.galeria.MemoryWidget
import com.lumi.galeria.PhotoWidget
import com.lumi.galeria.R
import com.lumi.galeria.countText
import com.lumi.galeria.data.readShowcaseOut
import com.lumi.galeria.tr
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** Qué fotos enseña un widget de fotos. */
enum class WidgetKind(val label: String) {
    FAVORITES("Favoritas"), ALBUM("Un álbum"), PERSON("Una persona"), MEMORY("Recuerdos"), RECENT("Recientes"),
}

/** Lo que eligió el usuario al poner el widget. [value] es la carpeta del álbum o el grupo de la persona. */
class WidgetConfig(val kind: WidgetKind, val value: String = "", val label: String = "", val hours: Int = 3)

/** Una persona con nombre y sus fotos, tal como la deja escrita la app para los widgets. */
class WidgetPerson(val group: Long, val name: String, val photos: List<Long>)

/**
 * Los widgets de fotos de Lumi. Se pintan aquí, fuera de la app, solo con lo que guarda Android
 * (las fotos) y lo que la app deja escrito (favoritas, personas y lo que no se luce).
 */
object Widgets {
    const val ACTION_NEXT = "com.lumi.galeria.widget.OTRA_FOTO"
    const val ACTION_OPEN = "com.lumi.galeria.widget.ABRIR_FOTO"
    const val EXTRA_PHOTO = "foto"
    private const val PEOPLE_FILE = "widget_personas.json"

    private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun config(context: Context, id: Int): WidgetConfig {
        val p = prefs(context)
        val kind = runCatching { WidgetKind.valueOf(p.getString("kind_$id", null) ?: "FAVORITES") }.getOrDefault(WidgetKind.FAVORITES)
        return WidgetConfig(kind, p.getString("value_$id", "").orEmpty(), p.getString("label_$id", "").orEmpty(), p.getInt("hours_$id", 3))
    }

    fun saveConfig(context: Context, id: Int, config: WidgetConfig) {
        prefs(context).edit()
            .putString("kind_$id", config.kind.name).putString("value_$id", config.value).putString("label_$id", config.label)
            .putInt("hours_$id", config.hours).remove("shown_$id").remove("since_$id")
            .apply()
    }

    fun forget(context: Context, ids: IntArray) {
        val edit = prefs(context).edit()
        for (id in ids) listOf("kind_", "value_", "label_", "hours_", "shown_", "since_").forEach { edit.remove(it + id) }
        edit.apply()
    }

    /** Vuelve a pintar todos los widgets de Lumi: cambió algo en la app (favoritas, fotos, personas). */
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        for (cls in listOf(PhotoWidget::class.java, MemoryWidget::class.java, com.lumi.galeria.MosaicWidget::class.java)) {
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, cls)) }.getOrNull() ?: continue
            if (ids.isEmpty()) continue
            context.sendBroadcast(
                Intent(context, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }

    /** Las personas con nombre, para elegir una en el widget y para saber qué fotos son suyas. */
    fun writePeople(context: Context, people: List<WidgetPerson>) {
        val array = JSONArray()
        people.forEach { person ->
            array.put(JSONObject().put("g", person.group).put("n", person.name).put("f", JSONArray(person.photos)))
        }
        runCatching { java.io.File(context.filesDir, PEOPLE_FILE).writeText(array.toString()) }
    }

    fun readPeople(context: Context): List<WidgetPerson> = runCatching {
        val array = JSONArray(java.io.File(context.filesDir, PEOPLE_FILE).readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val f = o.getJSONArray("f")
            WidgetPerson(o.getLong("g"), o.getString("n"), (0 until f.length()).map { f.getLong(it) })
        }
    }.getOrDefault(emptyList())

    private class Photo(val id: Long, val path: String)

    /** Las fotos del teléfono, de la más reciente a la más antigua, sin lo que no se luce. */
    private fun photos(context: Context, limit: Int = 4000): List<Photo> {
        val out = readShowcaseOut(context)
        val list = ArrayList<Photo>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH),
                null, null, "${MediaStore.Images.Media.DATE_TAKEN} DESC",
            )?.use { c ->
                while (c.moveToNext() && list.size < limit) {
                    val id = c.getLong(0)
                    val path = c.getString(1).orEmpty()
                    if (id in out || path.contains("Screenshot", ignoreCase = true) || path.startsWith("Documents/")) continue
                    list += Photo(id, path)
                }
            }
        }
        return list
    }

    /** Lo de estos días en otros años: el año más cercano que tenga fotos. */
    private fun memory(context: Context, alive: Set<Long>): Pair<List<Long>, Int>? = runCatching {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        for (years in 1..15) {
            val day = today.minusYears(years.toLong())
            val from = day.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
            val ids = ArrayList<Long>()
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?",
                arrayOf(from.toString(), to.toString()), null,
            )?.use { c -> while (c.moveToNext()) c.getLong(0).takeIf { it in alive }?.let { ids += it } }
            if (ids.isNotEmpty()) return@runCatching ids to years
        }
        null
    }.getOrNull()

    private class Choice(val ids: List<Long>, val title: String?, val subtitle: String?)

    private fun candidates(context: Context, config: WidgetConfig): Choice {
        val all = photos(context)
        val alive = all.mapTo(HashSet()) { it.id }
        fun recent() = Choice(all.take(60).map { it.id }, null, null)
        return when (config.kind) {
            WidgetKind.FAVORITES -> {
                val favorites = context.getSharedPreferences("lumi", Context.MODE_PRIVATE).getStringSet("favorites", emptySet()).orEmpty()
                    .mapNotNull { it.toLongOrNull() }.filter { it in alive }
                if (favorites.isNotEmpty()) Choice(favorites, null, null) else recent()
            }
            WidgetKind.ALBUM -> Choice(all.filter { it.path == config.value }.take(400).map { it.id }, config.label, null)
            WidgetKind.PERSON -> {
                val person = readPeople(context).firstOrNull { it.group.toString() == config.value }
                Choice(person?.photos.orEmpty().filter { it in alive }, person?.name ?: config.label, null)
            }
            WidgetKind.MEMORY -> memory(context, alive)?.let { (ids, years) ->
                Choice(ids, if (years == 1) tr("Hace un año") else tr("Hace $years años"), countText(ids.size, "foto de estos días", "fotos de estos días"))
            } ?: Choice(all.take(40).map { it.id }, tr("Tus fotos"), tr("Hoy no hay recuerdos de otros años"))
            WidgetKind.RECENT -> recent()
        }
    }

    /** Recorta [source] al tamaño del widget y le redondea las esquinas como las de Android. */
    /** Las fotos que valen para un widget con [config], de la más reciente a la más antigua. */
    fun candidateIds(context: Context, config: WidgetConfig): List<Long> = candidates(context, config).ids

    /** Un cuadrado de [side] con la foto recortada y las esquinas redondeadas. */
    fun squareRounded(source: Bitmap, side: Int, radius: Float): Bitmap = fit(source, side, side, radius)

    private fun fit(source: Bitmap, width: Int, height: Int, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate((width - source.width * scale) / 2f, (height - source.height * scale) / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        }
        Canvas(out).drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        return out
    }

    /**
     * Pinta el widget [id]. Con [advance] cambia de foto aunque no toque. Lo que se enseña se
     * recuerda, para no cambiar de foto cada vez que Android pide repintar.
     */
    fun render(context: Context, manager: AppWidgetManager, id: Int, memoryWidget: Boolean, advance: Boolean) {
        val lumi = context.getSharedPreferences("lumi", Context.MODE_PRIVATE)
        Lang.apply(runCatching { AppLanguage.valueOf(lumi.getString("language", null) ?: "SYSTEM") }.getOrDefault(AppLanguage.SYSTEM))
        val config = if (memoryWidget) WidgetConfig(WidgetKind.MEMORY, hours = 3) else config(context, id)
        val choice = candidates(context, config)
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val shown = p.getLong("shown_$id", -1L)
        val since = p.getLong("since_$id", 0L)
        val due = advance || shown !in choice.ids || now - since >= config.hours * 3_600_000L - 300_000L
        val photo = if (!due) shown else (choice.ids.filter { it != shown }.randomOrNull() ?: choice.ids.firstOrNull())
        if (photo != null && photo != shown) p.edit().putLong("shown_$id", photo).putLong("since_$id", now).apply()

        val views = RemoteViews(context.packageName, R.layout.widget_photo)
        val density = context.resources.displayMetrics.density
        val options = manager.getAppWidgetOptions(id)
        // En vertical, el ancho es el mínimo y el alto el máximo que da el lanzador.
        val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 160
        val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 160
        var w = (wDp * density).toInt()
        var h = (hDp * density).toInt()
        val k = minOf(1f, 900f / maxOf(w, h))
        w = (w * k).toInt().coerceAtLeast(64)
        h = (h * k).toInt().coerceAtLeast(64)

        val uri = photo?.let { ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it) }
        val bitmap = uri?.let {
            runCatching { context.contentResolver.loadThumbnail(it, Size(maxOf(w, h), maxOf(w, h)), null) }.getOrNull()
        }?.let { fit(it, w, h, 22f * density * k) }

        if (bitmap != null) {
            views.setImageViewBitmap(R.id.photo, bitmap)
            views.setViewVisibility(R.id.photo, View.VISIBLE)
            views.setViewVisibility(R.id.empty, View.GONE)
        } else {
            views.setViewVisibility(R.id.photo, View.GONE)
            views.setViewVisibility(R.id.empty, View.VISIBLE)
            views.setTextViewText(
                R.id.empty,
                when (config.kind) {
                    WidgetKind.FAVORITES -> tr("Marca fotos con el corazón para verlas aquí")
                    WidgetKind.PERSON -> tr("Abre Lumi para ver las fotos de esta persona")
                    else -> tr("Aún no hay fotos aquí")
                },
            )
        }
        val title = choice.title
        views.setViewVisibility(R.id.shade, if (title != null && bitmap != null) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.title, if (title != null) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.title, title.orEmpty())
        views.setViewVisibility(R.id.subtitle, if (choice.subtitle != null) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.subtitle, choice.subtitle.orEmpty())

        // Tocar la foto la abre en Lumi; la flecha pone otra.
        val open = Intent(context, MainActivity::class.java).setAction(ACTION_OPEN)
            .putExtra(EXTRA_PHOTO, photo ?: -1L)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        views.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(context, 4000 + id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val provider = if (memoryWidget) MemoryWidget::class.java else PhotoWidget::class.java
        val next = Intent(context, provider).setAction(ACTION_NEXT).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        views.setOnClickPendingIntent(R.id.next, PendingIntent.getBroadcast(context, 5000 + id, next, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        views.setViewVisibility(R.id.next, if (choice.ids.size > 1) View.VISIBLE else View.GONE)
        manager.updateAppWidget(id, views)
    }

    /** La foto que abre el widget, con su dirección. */
    fun photoUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
}
