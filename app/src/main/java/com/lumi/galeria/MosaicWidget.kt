package com.lumi.galeria

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.lumi.galeria.widget.CameraWidgets
import com.lumi.galeria.widget.Widgets

/** Widget mosaico: 4 o 6 fotos en cuadrícula que van cambiando de una en una. */
class MosaicWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        inBackground { ids.forEach { CameraWidgets.renderMosaic(context, manager, it, advance = false) } }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        inBackground { CameraWidgets.renderMosaic(context, manager, id, advance = false) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        Widgets.forget(context, ids)
        CameraWidgets.forget(context, ids)
    }
}

/** Widget de cámara: un botón del tamaño de un icono que abre la Cámara Lumi. */
class CameraWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { CameraWidgets.render(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) = CameraWidgets.forget(context, ids)
}
