package com.lumi.galeria

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Widget de atajos: Cámara Lumi, leer un QR, escanear un documento y buscar, a un toque. */
class ShortcutWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val prefs = context.getSharedPreferences("lumi", Context.MODE_PRIVATE)
        Lang.apply(runCatching { AppLanguage.valueOf(prefs.getString("language", null) ?: "SYSTEM") }.getOrDefault(AppLanguage.SYSTEM))
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_shortcuts)
            listOf(
                Triple(R.id.go_camera, R.id.label_camera, "camara_lumi" to "Cámara"),
                Triple(R.id.go_qr, R.id.label_qr, "qr" to "QR"),
                Triple(R.id.go_scan, R.id.label_scan, "escanear" to "Escanear"),
                Triple(R.id.go_search, R.id.label_search, "buscar" to "Buscar"),
            ).forEachIndexed { i, (button, label, target) ->
                views.setTextViewText(label, tr(target.second))
                val intent = when (target.first) {
                    "camara_lumi" -> Intent(context, CameraActivity::class.java).putExtra(CameraActivity.EXTRA_MODE, "PHOTO")
                    "qr" -> Intent(context, CameraActivity::class.java).putExtra(CameraActivity.EXTRA_MODE, "QR")
                    else -> Intent(context, MainActivity::class.java).setAction(SHORTCUT_PREFIX + target.first)
                }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                views.setOnClickPendingIntent(button, PendingIntent.getActivity(context, 6000 + id * 8 + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            manager.updateAppWidget(id, views)
        }
    }
}

/** Las acciones de los atajos (icono y widget) empiezan así. */
const val SHORTCUT_PREFIX = "com.lumi.galeria.ATAJO_"
