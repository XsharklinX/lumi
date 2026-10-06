package com.lumi.galeria

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

/**
 * Widget de recuerdos: una foto de estos mismos días en otros años, con cuántos años hace. Cambia
 * cada pocas horas. Si hoy no hay ninguno, enseña una foto reciente y lo dice.
 */
class MemoryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // El widget no pasa por la pantalla de la app: aquí se decide el idioma.
        val prefs = context.getSharedPreferences("lumi", Context.MODE_PRIVATE)
        Lang.apply(runCatching { AppLanguage.valueOf(prefs.getString("language", null) ?: "SYSTEM") }.getOrDefault(AppLanguage.SYSTEM))
        val memory = findMemory(context)
        val bitmap = memory?.let { runCatching { context.contentResolver.loadThumbnail(it.uri, Size(720, 720), null) }.getOrNull() }
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_memory)
            if (bitmap != null) views.setImageViewBitmap(R.id.photo, bitmap)
            val title = when {
                memory == null -> "Lumi"
                memory.years == 1 -> tr("Hace un año")
                memory.years > 1 -> tr("Hace ${memory.years} años")
                else -> tr("Tus fotos")
            }
            views.setTextViewText(R.id.title, title)
            views.setTextViewText(
                R.id.subtitle,
                when {
                    memory == null -> tr("Aún no hay fotos")
                    memory.years > 0 -> countText(memory.count, "foto de estos días", "fotos de estos días")
                    else -> tr("Hoy no hay recuerdos de otros años")
                },
            )
            views.setViewVisibility(R.id.subtitle, View.VISIBLE)
            val open = Intent(context, MainActivity::class.java).setAction(if (memory != null && memory.years > 0) ACTION_MEMORY else Intent.ACTION_MAIN)
                .putExtra(EXTRA_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(context, 1000 + id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
        }
    }

    private class Found(val uri: android.net.Uri, val years: Int, val count: Int)

    /** El año más cercano con fotos de estos días (un día antes o después también vale). */
    private fun findMemory(context: Context): Found? = runCatching {
        val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        for (years in 1..15) {
            val day = today.minusYears(years.toLong())
            val from = day.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
            val ids = ArrayList<Long>()
            context.contentResolver.query(
                images, arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?",
                arrayOf(from.toString(), to.toString()), null,
            )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
            if (ids.isNotEmpty()) return@runCatching Found(ContentUris.withAppendedId(images, ids.random()), years, ids.size)
        }
        // Sin recuerdos: una de las fotos más recientes.
        context.contentResolver.query(images, arrayOf(MediaStore.Images.Media._ID), null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use { c ->
            val recent = ArrayList<Long>()
            while (c.moveToNext() && recent.size < 30) recent += c.getLong(0)
            recent.randomOrNull()?.let { Found(ContentUris.withAppendedId(images, it), 0, recent.size) }
        }
    }.getOrNull()

    companion object {
        const val ACTION_MEMORY = "com.lumi.galeria.RECUERDO"
        const val EXTRA_TITLE = "titulo"
    }
}
