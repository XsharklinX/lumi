package com.lumi.galeria.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.lumi.galeria.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hay otra app al lado (pantalla dividida, ventana libre) o la pantalla es ancha: tiene sentido arrastrar. */
@Composable
fun canDragOut(): Boolean {
    LocalConfiguration.current // Para volver a mirarlo al cambiar de tamaño o de modo de ventana.
    val activity = LocalContext.current as? Activity
    return LocalWide.current || activity?.isInMultiWindowMode == true
}

/** Miniatura con la que se arrastra; se prepara antes para que el dedo no espere. */
@Composable
fun rememberDragThumb(item: MediaItem?): Bitmap? {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(null, item?.id) {
        value = item?.let { withContext(Dispatchers.IO) { runCatching { context.contentResolver.loadThumbnail(it.uri, android.util.Size(240, 240), null) }.getOrNull() } }
    }
    return thumb
}

/**
 * Empieza a arrastrar [items] hacia otra app. El permiso de lectura viaja con lo que se suelta,
 * así la otra app puede abrir las fotos aunque no tenga acceso a la galería.
 */
fun startDragOut(view: View, context: Context, items: List<MediaItem>, thumb: Bitmap?) {
    if (items.isEmpty()) return
    val clip = ClipData.newUri(context.contentResolver, "Lumi", items[0].uri).apply {
        items.drop(1).forEach { addItem(ClipData.Item(it.uri)) }
    }
    val side = (96 * context.resources.displayMetrics.density).toInt()
    val shadow = object : View.DragShadowBuilder(view) {
        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(side, side)
            outShadowTouchPoint.set(side / 2, side / 2)
        }

        override fun onDrawShadow(canvas: Canvas) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val box = RectF(0f, 0f, side.toFloat(), side.toFloat())
            val radius = side * 0.16f
            if (thumb != null) {
                val path = android.graphics.Path().apply { addRoundRect(box, radius, radius, android.graphics.Path.Direction.CW) }
                canvas.save()
                canvas.clipPath(path)
                val k = maxOf(side.toFloat() / thumb.width, side.toFloat() / thumb.height)
                val w = thumb.width * k
                val h = thumb.height * k
                canvas.drawBitmap(thumb, null, RectF((side - w) / 2, (side - h) / 2, (side + w) / 2, (side + h) / 2), paint)
                canvas.restore()
            } else {
                paint.color = 0xFF2A2834.toInt()
                canvas.drawRoundRect(box, radius, radius, paint)
            }
            if (items.size > 1) {
                paint.color = 0xFF8E5BFF.toInt()
                val r = side * 0.17f
                canvas.drawCircle(side - r, r, r, paint)
                paint.color = android.graphics.Color.WHITE
                paint.textSize = r * 1.1f
                paint.textAlign = Paint.Align.CENTER
                paint.isFakeBoldText = true
                canvas.drawText(items.size.toString(), side - r, r + paint.textSize * 0.36f, paint)
            }
        }
    }
    view.startDragAndDrop(clip, shadow, null, View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
}

/** Píldora de la barra de selección: se arrastra desde aquí para soltar lo elegido en otra app. */
@Composable
fun DragOutHandle(items: List<MediaItem>) {
    val view = LocalView.current
    val context = LocalContext.current
    val thumb = rememberDragThumb(items.firstOrNull())
    Row(
        Modifier
            .clip(CircleShape)
            .background(Lumi.Accent)
            .pointerInput(items, thumb) {
                detectDragGestures(onDragStart = { startDragOut(view, context, items, thumb) }) { change, _ -> change.consume() }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(OpenWithIcon, null, Modifier.size(18.dp), tint = Lumi.OnAccent)
        Text(
            if (items.size == 1) "Arrastra la foto a otra app" else "Arrastra las ${items.size} a otra app",
            style = LabelStyle, color = Lumi.OnAccent,
        )
    }
}
