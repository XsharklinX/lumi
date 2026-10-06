package com.lumi.galeria.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Iconos de línea del menú «Más», dibujados a mano en una rejilla de 24 x 24. */
private fun line(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }.build()

/** Una foto: marco con montañas. */
val PictureIcon = line("Foto") {
    moveTo(5f, 5f); lineTo(19f, 5f); lineTo(19f, 19f); lineTo(5f, 19f); close()
    moveTo(7f, 16f); lineTo(10.5f, 11.5f); lineTo(13f, 14.5f); lineTo(15f, 12.5f); lineTo(17f, 16f)
}

/** Girar: flecha que da la vuelta. */
val RotateIcon = line("Girar") {
    moveTo(12f, 5f)
    arcTo(7f, 7f, 0f, true, true, 5f, 12f)
    moveTo(12f, 5f); lineTo(15f, 3f)
    moveTo(12f, 5f); lineTo(14.5f, 8f)
}

/** Texto: una T. */
val TextIcon = line("Texto") {
    moveTo(6f, 6f); lineTo(18f, 6f)
    moveTo(12f, 6f); lineTo(12f, 19f)
}

/** Una persona recortada: cabeza y hombros. */
val PersonIcon = line("Persona") {
    moveTo(15.5f, 8.5f)
    arcTo(3.5f, 3.5f, 0f, true, true, 15.49f, 8.4f)
    moveTo(5f, 20f)
    arcTo(7f, 6f, 0f, false, true, 19f, 20f)
}

/** Copiar: dos hojas. */
val CopyIcon = line("Copiar") {
    moveTo(9f, 9f); lineTo(19f, 9f); lineTo(19f, 19f); lineTo(9f, 19f); close()
    moveTo(5f, 15f); lineTo(5f, 5f); lineTo(15f, 5f)
}

/** El teléfono tumbado. */
val LandscapeIcon = line("Horizontal") {
    moveTo(3f, 8f); lineTo(21f, 8f); lineTo(21f, 16f); lineTo(3f, 16f); close()
    moveTo(18f, 11f); lineTo(18f, 13f)
}

/** Un documento con renglones. */
val ScanIcon = line("Documento") {
    moveTo(6f, 3f); lineTo(14f, 3f); lineTo(18f, 7f); lineTo(18f, 21f); lineTo(6f, 21f); close()
    moveTo(9f, 12f); lineTo(15f, 12f)
    moveTo(9f, 16f); lineTo(15f, 16f)
}

/** Hacer más pequeño: dos flechas hacia dentro. */
val ShrinkIcon = line("Reducir") {
    moveTo(4f, 4f); lineTo(10f, 10f); moveTo(10f, 5.5f); lineTo(10f, 10f); lineTo(5.5f, 10f)
    moveTo(20f, 20f); lineTo(14f, 14f); moveTo(14f, 18.5f); lineTo(14f, 14f); lineTo(18.5f, 14f)
}

/** Dibujar: un lápiz. */
val PenIcon = line("Dibujar") {
    moveTo(4f, 20f); lineTo(5f, 15f); lineTo(15f, 5f); lineTo(19f, 9f); lineTo(9f, 19f); close()
    moveTo(13f, 7f); lineTo(17f, 11f)
}

/** Presentación: un triángulo de reproducir dentro de un marco. */
val SlidesIcon = line("Presentación") {
    moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 17f); lineTo(3f, 17f); close()
    moveTo(10f, 8.5f); lineTo(14.5f, 11f); lineTo(10f, 13.5f); close()
    moveTo(8f, 21f); lineTo(16f, 21f)
}

/** Información: una i dentro de un círculo. */
val InfoIcon = line("Información") {
    moveTo(21f, 12f)
    arcTo(9f, 9f, 0f, true, true, 20.99f, 11.9f)
    moveTo(12f, 11f); lineTo(12f, 16.5f)
    moveTo(12f, 7.6f); lineTo(12f, 7.7f)
}

/** Lugar tachado: enviar sin ubicación. */
val NoPlaceIcon = line("Sin ubicación") {
    moveTo(12f, 21f)
    curveTo(12f, 21f, 5f, 14.5f, 5f, 9.5f)
    arcTo(7f, 7f, 0f, false, true, 19f, 9.5f)
    curveTo(19f, 14.5f, 12f, 21f, 12f, 21f)
    moveTo(4f, 4f); lineTo(20f, 20f)
}

/** Otra app: una flecha que sale de un cuadro. */
val OpenWithIcon = line("Otra app") {
    moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f)
    moveTo(20f, 4f); lineTo(11f, 13f)
    moveTo(18f, 14f); lineTo(18f, 20f); lineTo(4f, 20f); lineTo(4f, 6f); lineTo(10f, 6f)
}

/** Portada: un marco con una estrella. */
val CoverIcon = line("Portada") {
    moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
    moveTo(12f, 8f); lineTo(13.2f, 10.8f); lineTo(16f, 11f); lineTo(13.8f, 12.8f); lineTo(14.5f, 15.6f)
    lineTo(12f, 14f); lineTo(9.5f, 15.6f); lineTo(10.2f, 12.8f); lineTo(8f, 11f); lineTo(10.8f, 10.8f); close()
}

/** Deshacer: flecha curva hacia la izquierda. */
val UndoIcon = line("Deshacer") {
    moveTo(9f, 7f); lineTo(5f, 11f); lineTo(9f, 15f)
    moveTo(5f, 11f); lineTo(14f, 11f)
    arcTo(5f, 5f, 0f, false, true, 14f, 21f)
    lineTo(11f, 21f)
}

/** Rehacer: flecha curva hacia la derecha. */
val RedoIcon = line("Rehacer") {
    moveTo(15f, 7f); lineTo(19f, 11f); lineTo(15f, 15f)
    moveTo(19f, 11f); lineTo(10f, 11f)
    arcTo(5f, 5f, 0f, false, false, 10f, 21f)
    lineTo(13f, 21f)
}

/** Vídeo: un fotograma de película con su triángulo de reproducir. */
val MovieIcon = line("Vídeo") {
    moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 18f); lineTo(4f, 18f); close()
    moveTo(10f, 9.5f); lineTo(15f, 12f); lineTo(10f, 14.5f); close()
}
