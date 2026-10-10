package com.lumi.galeria.data

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de cierres inesperados, solo en el teléfono. Si Lumi se cierra por un fallo, se guarda aquí un resumen sin
 * nada personal (versión, modelo, Android y las líneas del fallo, sin su mensaje, que podría llevar nombres de
 * archivos). La próxima vez que se abre, el usuario decide si lo envía por correo. Lumi no tiene internet: no se manda nada solo.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "ultimo_fallo.txt")

    /** Engancha el registro al cierre inesperado. Se llama una vez al arrancar la app. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(app).writeText(describe(app, thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun describe(context: Context, thread: Thread, error: Throwable): String {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val version = info?.versionName ?: "?"
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() } ?: 0L
        val out = StringBuilder()
        out.appendLine("Lumi Gallery $version ($code)")
        out.appendLine("Fecha: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
        out.appendLine("Móvil: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        out.appendLine("Hilo: ${thread.name}")
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 4) {
            out.appendLine((if (depth == 0) "Fallo: " else "Causado por: ") + cause.javaClass.name)
            cause.stackTrace.take(18).forEach { out.appendLine("    en ${it.className}.${it.methodName} (${it.fileName}:${it.lineNumber})") }
            cause = cause.cause
            depth++
        }
        return out.toString()
    }

    /** El resumen del último cierre inesperado, si lo hubo y aún no se ha atendido. */
    fun pending(context: Context): String? = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
