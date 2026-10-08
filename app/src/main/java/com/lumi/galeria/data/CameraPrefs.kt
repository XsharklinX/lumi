package com.lumi.galeria.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Formato de la foto: lo que se ve en el visor es exactamente lo que se guarda. */
enum class ShotRatio(val label: String) { SQUARE("1:1"), FOUR_THREE("4:3"), SIXTEEN_NINE("16:9"), FULL("Completa") }

enum class VideoQuality(val label: String, val bitsPerSecond: Long) { FHD("1080p", 17_000_000), UHD("4K", 48_000_000) }

/** Qué hacen las teclas de volumen dentro de la Cámara Lumi. */
enum class VolumeKeys(val label: String) { SHUTTER("Disparar"), ZOOM("Acercar y alejar"), RECORD("Grabar vídeo") }

enum class PhotoSize(val label: String) { MAX("Máxima"), BALANCED("Equilibrada (12 MP)") }

enum class PhotoFormat(val label: String) { JPEG("JPEG"), HEIC("HEIC (ocupa la mitad)") }

/**
 * Los ajustes de la Cámara Lumi, guardados en el teléfono. Cada cambio se guarda en el momento y
 * se ve en pantalla, porque cada valor es un estado de Compose.
 */
class CameraPrefs(context: Context) {
    private val p = context.getSharedPreferences("camara", Context.MODE_PRIVATE)

    private inline fun <reified T : Enum<T>> enumOf(key: String, default: T): T =
        runCatching { enumValueOf<T>(p.getString(key, null) ?: default.name) }.getOrDefault(default)

    var rememberSettings by mutableStateOf(p.getBoolean("recordar", true))
        private set
    var lastMode by mutableStateOf(p.getString("modo", null))
        private set
    var flash by mutableStateOf(p.getString("flash", null))
        private set
    var ratio by mutableStateOf(enumOf("formato", ShotRatio.FOUR_THREE))
        private set
    var timer by mutableStateOf(p.getInt("temporizador", 0))
        private set
    var guide by mutableStateOf(p.getString("guias", null))
        private set
    var size by mutableStateOf(enumOf("tamano", PhotoSize.MAX))
        private set
    var format by mutableStateOf(enumOf("tipo", PhotoFormat.JPEG))
        private set
    var video by mutableStateOf(enumOf("video", VideoQuality.FHD))
        private set
    var fps by mutableStateOf(p.getInt("fps", 30))
        private set
    var stabilize by mutableStateOf(p.getBoolean("estabilizar", true))
        private set
    var volume by mutableStateOf(enumOf("volumen", VolumeKeys.SHUTTER))
        private set
    var swipeFlip by mutableStateOf(p.getBoolean("deslizarGira", true))
        private set
    var doubleTapFlip by mutableStateOf(p.getBoolean("dobleToqueGira", false))
        private set

    fun setRemember(on: Boolean) { rememberSettings = on; p.edit().putBoolean("recordar", on).apply() }
    fun chooseSize(v: PhotoSize) { size = v; p.edit().putString("tamano", v.name).apply() }
    fun chooseFormat(v: PhotoFormat) { format = v; p.edit().putString("tipo", v.name).apply() }
    fun chooseVideo(v: VideoQuality) { video = v; p.edit().putString("video", v.name).apply() }
    fun chooseFps(v: Int) { fps = v; p.edit().putInt("fps", v).apply() }
    fun chooseStabilize(on: Boolean) { stabilize = on; p.edit().putBoolean("estabilizar", on).apply() }
    fun chooseVolume(v: VolumeKeys) { volume = v; p.edit().putString("volumen", v.name).apply() }
    fun chooseSwipeFlip(on: Boolean) { swipeFlip = on; p.edit().putBoolean("deslizarGira", on).apply() }
    fun chooseDoubleTapFlip(on: Boolean) { doubleTapFlip = on; p.edit().putBoolean("dobleToqueGira", on).apply() }
    fun chooseGuide(name: String) { guide = name; p.edit().putString("guias", name).apply() }

    /** Lo que se recuerda de una vez para otra, si así se quiere: modo, flash, formato y temporizador. */
    fun remember(mode: String, flash: String, ratio: ShotRatio, timer: Int) {
        if (!rememberSettings) {
            // El formato se recuerda siempre: cambiarlo cada vez sería un fastidio.
            this.ratio = ratio
            p.edit().putString("formato", ratio.name).apply()
            return
        }
        lastMode = mode
        this.flash = flash
        this.ratio = ratio
        this.timer = timer
        p.edit().putString("modo", mode).putString("flash", flash).putString("formato", ratio.name).putInt("temporizador", timer).apply()
    }

    /** Los consejos de una línea salen solo las dos primeras veces. */
    fun shouldTip(key: String): Boolean = p.getInt("consejo_$key", 0) < 2

    fun tipShown(key: String) {
        p.edit().putInt("consejo_$key", p.getInt("consejo_$key", 0) + 1).apply()
    }
}
