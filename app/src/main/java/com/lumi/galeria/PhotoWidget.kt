package com.lumi.galeria

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import android.widget.RemoteViews

/** Widget de inicio: enseña una de tus favoritas (o una foto reciente) y cambia cada hora. */
class PhotoWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val uri = pickPhoto(context)
        val bitmap = uri?.let { runCatching { context.contentResolver.loadThumbnail(it, Size(640, 640), null) }.getOrNull() }
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_photo)
            if (bitmap != null) views.setImageViewBitmap(R.id.photo, bitmap)
            val open = Intent(context, MainActivity::class.java).apply {
                if (uri != null) {
                    action = Intent.ACTION_VIEW
                    setDataAndType(uri, "image/*")
                }
            }
            views.setOnClickPendingIntent(
                R.id.photo,
                PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            manager.updateAppWidget(id, views)
        }
    }

    private fun pickPhoto(context: Context): Uri? {
        val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val favorites = context.getSharedPreferences("lumi", Context.MODE_PRIVATE)
            .getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
        val candidates = ArrayList<Long>()
        runCatching {
            context.contentResolver.query(
                images, arrayOf(MediaStore.Images.Media._ID), null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )?.use { c ->
                val alive = HashSet<Long>()
                while (c.moveToNext() && (alive.size < 2000)) alive += c.getLong(0)
                // Las favoritas pueden ser vídeos o haberse borrado: solo valen las fotos que siguen ahí.
                candidates += favorites.filter { it in alive }
                if (candidates.isEmpty()) candidates += alive.take(40)
            }
        }
        return candidates.randomOrNull()?.let { ContentUris.withAppendedId(images, it) }
    }
}
