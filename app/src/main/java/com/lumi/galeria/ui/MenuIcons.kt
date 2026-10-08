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

/** Un destello de cuatro puntas, para lo nuevo. */
val SparkIcon = line("Destello") {
    moveTo(12f, 3f); lineTo(13.8f, 10.2f); lineTo(21f, 12f); lineTo(13.8f, 13.8f); lineTo(12f, 21f); lineTo(10.2f, 13.8f); lineTo(3f, 12f); lineTo(10.2f, 10.2f); close()
}

/** Una lupa. */
val SearchLineIcon = line("Buscar") {
    moveTo(17f, 10.5f)
    arcTo(6.5f, 6.5f, 0f, true, true, 16.99f, 10.4f)
    moveTo(15.2f, 15.2f); lineTo(20f, 20f)
}

/** Una cámara de fotos. */
val CameraLineIcon = line("Cámara") {
    moveTo(3f, 8f); lineTo(7f, 8f); lineTo(9f, 5f); lineTo(15f, 5f); lineTo(17f, 8f); lineTo(21f, 8f); lineTo(21f, 19f); lineTo(3f, 19f); close()
    moveTo(15.5f, 13f)
    arcTo(3.5f, 3.5f, 0f, true, true, 15.49f, 12.9f)
}

/** Un candado. */
val LockLineIcon = line("Candado") {
    moveTo(5f, 11f); lineTo(19f, 11f); lineTo(19f, 20f); lineTo(5f, 20f); close()
    moveTo(8f, 11f); lineTo(8f, 8f)
    arcTo(4f, 4f, 0f, false, true, 16f, 8f)
    lineTo(16f, 11f)
}

/** Una copa de premio. */
val TrophyIcon = line("Premio") {
    moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 9f)
    arcTo(5f, 5f, 0f, false, true, 7f, 9f)
    close()
    moveTo(12f, 14f); lineTo(12f, 18f)
    moveTo(8f, 20f); lineTo(16f, 20f)
    moveTo(7f, 6f); lineTo(4f, 6f); lineTo(4f, 8f)
    arcTo(3f, 3f, 0f, false, false, 7f, 11f)
    moveTo(17f, 6f); lineTo(20f, 6f); lineTo(20f, 8f)
    arcTo(3f, 3f, 0f, false, true, 17f, 11f)
}

/** Flash: un rayo. */
val BoltIcon = line("Flash") {
    moveTo(13f, 3f); lineTo(5f, 14f); lineTo(11f, 14f); lineTo(10f, 21f); lineTo(18f, 10f); lineTo(12f, 10f); close()
}

/** Flash apagado: el rayo tachado. */
val BoltOffIcon = line("Sin flash") {
    moveTo(13f, 3f); lineTo(9.5f, 7.8f)
    moveTo(7.6f, 10.4f); lineTo(5f, 14f); lineTo(11f, 14f); lineTo(10f, 21f); lineTo(14.6f, 14.7f)
    moveTo(16.4f, 12.2f); lineTo(18f, 10f); lineTo(13.4f, 10f)
    moveTo(4f, 4f); lineTo(20f, 20f)
}

/** Temporizador: un cronómetro. */
val TimerIcon = line("Temporizador") {
    moveTo(19f, 13f)
    arcTo(7f, 7f, 0f, true, true, 18.99f, 12.9f)
    moveTo(12f, 9f); lineTo(12f, 13f); lineTo(14.5f, 15f)
    moveTo(10f, 3f); lineTo(14f, 3f)
}

/** Ajustes: una rueda dentada sencilla. */
val GearIcon = line("Ajustes") {
    moveTo(15f, 12f)
    arcTo(3f, 3f, 0f, true, true, 14.99f, 11.9f)
    moveTo(12f, 2.5f); lineTo(12f, 5f); moveTo(12f, 19f); lineTo(12f, 21.5f)
    moveTo(2.5f, 12f); lineTo(5f, 12f); moveTo(19f, 12f); lineTo(21.5f, 12f)
    moveTo(5.3f, 5.3f); lineTo(7f, 7f); moveTo(17f, 17f); lineTo(18.7f, 18.7f)
    moveTo(5.3f, 18.7f); lineTo(7f, 17f); moveTo(17f, 7f); lineTo(18.7f, 5.3f)
}

/** Cerrar: una equis. */
val CloseLineIcon = line("Cerrar") {
    moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f)
}

/** Linterna. */
val TorchIcon = line("Linterna") {
    moveTo(7f, 3f); lineTo(17f, 3f); lineTo(17f, 7f); lineTo(14f, 11f); lineTo(14f, 21f); lineTo(10f, 21f); lineTo(10f, 11f); lineTo(7f, 7f); close()
    moveTo(12f, 14f); lineTo(12f, 16f)
}

/** Historial: un reloj con flecha. */
val HistoryIcon = line("Historial") {
    moveTo(4f, 12f)
    arcTo(8f, 8f, 0f, true, true, 6.3f, 17.7f)
    moveTo(4f, 12f); lineTo(2f, 10f); moveTo(4f, 12f); lineTo(6f, 10f)
    moveTo(12f, 8f); lineTo(12f, 12f); lineTo(15f, 14f)
}

/** Girar la cámara: dos flechas en círculo. */
val FlipCameraIcon = line("Girar la cámara") {
    moveTo(5f, 12f)
    arcTo(7f, 7f, 0f, false, true, 17.5f, 7.5f)
    moveTo(17.5f, 7.5f); lineTo(17.5f, 4f); moveTo(17.5f, 7.5f); lineTo(14f, 7.5f)
    moveTo(19f, 12f)
    arcTo(7f, 7f, 0f, false, true, 6.5f, 16.5f)
    moveTo(6.5f, 16.5f); lineTo(6.5f, 20f); moveTo(6.5f, 16.5f); lineTo(10f, 16.5f)
}

/** Pausa: dos barras. */
val PauseIcon = line("Pausa") {
    moveTo(9f, 6f); lineTo(9f, 18f); moveTo(15f, 6f); lineTo(15f, 18f)
}

/** Reproducir: un triángulo. */
val PlayLineIcon = line("Seguir") {
    moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
}

/** Un corazón, para las favoritas. */
val HeartIcon = line("Favoritas") {
    moveTo(12f, 20f)
    lineTo(4.5f, 12.5f)
    arcTo(4.2f, 4.2f, 0f, false, true, 12f, 7f)
    arcTo(4.2f, 4.2f, 0f, false, true, 19.5f, 12.5f)
    close()
}

/** Una papelera. */
val TrashIcon = line("Papelera") {
    moveTo(4f, 7f); lineTo(20f, 7f)
    moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
    moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f)
}

/** Una carpeta. */
val FolderIcon = line("Carpeta") {
    moveTo(3f, 6f); lineTo(9f, 6f); lineTo(11f, 8f); lineTo(21f, 8f); lineTo(21f, 19f); lineTo(3f, 19f); close()
}

/** Filtros: tres círculos que se cruzan. */
val PaletteIcon = line("Filtros") {
    moveTo(16f, 9f)
    arcTo(4f, 4f, 0f, true, true, 15.99f, 8.9f)
    moveTo(12f, 15f)
    arcTo(4f, 4f, 0f, true, true, 11.99f, 14.9f)
    moveTo(20f, 15f)
    arcTo(4f, 4f, 0f, true, true, 19.99f, 14.9f)
}
