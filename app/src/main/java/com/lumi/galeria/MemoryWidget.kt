package com.lumi.galeria

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.lumi.galeria.widget.Widgets

/**
 * Widget de recuerdos: una foto de estos mismos días en otros años, con cuántos años hace. Cambia
 * cada pocas horas o al tocar la flecha. Si hoy no hay ninguno, enseña una foto reciente y lo dice.
 */
class MemoryWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Widgets.ACTION_NEXT) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) inBackground { Widgets.render(context, AppWidgetManager.getInstance(context), id, memoryWidget = true, advance = true) }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        inBackground { ids.forEach { Widgets.render(context, manager, it, memoryWidget = true, advance = false) } }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        inBackground { Widgets.render(context, manager, id, memoryWidget = true, advance = false) }
    }

    override fun onDeleted(context: Context, ids: IntArray) = Widgets.forget(context, ids)

    companion object {
        const val ACTION_MEMORY = "com.lumi.galeria.RECUERDO"
        const val EXTRA_TITLE = "titulo"
    }
}
