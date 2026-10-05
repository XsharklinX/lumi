package com.lumi.galeria.data

import android.os.Process
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay

/**
 * El análisis de fotos (repetidas, texto, cosas) es trabajo pesado que no corre prisa.
 * Se hace en un solo hilo de baja prioridad y se detiene mientras el usuario toca la pantalla,
 * para que nunca le quite fluidez a la app.
 */
object Quiet {
    @Volatile
    private var busyUntil = 0L

    /** Un hilo, con la prioridad más baja que da el sistema. */
    val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "lumi-analisis")
    }.asCoroutineDispatcher()

    /** Lo llama la actividad con cada toque en la pantalla. */
    fun touched() {
        busyUntil = System.currentTimeMillis() + 2500
    }

    /** Espera a que el usuario lleve un rato sin tocar nada. */
    suspend fun whenIdle() {
        while (System.currentTimeMillis() < busyUntil) delay(300)
    }
}
