package com.lumi.galeria

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.lumi.galeria.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Para pintar fuera del hilo principal: leer fotos y recortarlas lleva su tiempo. */
internal val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Hace [work] fuera del hilo principal sin que Android dé el aviso por terminado antes de tiempo. */
internal fun android.content.BroadcastReceiver.inBackground(work: () -> Unit) {
    val pending = goAsync()
    widgetScope.launch {
        try {
            runCatching(work)
        } finally {
            pending.finish()
        }
    }
}

/**
 * Widget de fotos: favoritas, un álbum, una persona o recuerdos, lo que se eligió al ponerlo. Cambia
 * cada hora, cada tres o cada día, o al tocar la flecha; tocar la foto la abre en Lumi.
 */
class PhotoWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Widgets.ACTION_NEXT) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) inBackground { Widgets.render(context, AppWidgetManager.getInstance(context), id, memoryWidget = false, advance = true) }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        inBackground { ids.forEach { Widgets.render(context, manager, it, memoryWidget = false, advance = false) } }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        // Cambió de tamaño: la foto se vuelve a recortar a la medida nueva.
        inBackground { Widgets.render(context, manager, id, memoryWidget = false, advance = false) }
    }

    override fun onDeleted(context: Context, ids: IntArray) = Widgets.forget(context, ids)
}
