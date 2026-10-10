package com.lumi.galeria.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView

/**
 * Todas las vibraciones de Lumi pasan por aquí, para que suenen como una sola familia y se puedan apagar
 * desde Ajustes: un toque suave al elegir, un tic en cada muesca, un golpe seco al soltar en un álbum y
 * otro distinto al borrar.
 */
object Haptics {
    @Volatile var enabled = true

    /** Elegir algo o pasar por una muesca. */
    const val TICK = HapticFeedbackConstants.CLOCK_TICK

    /** Pulsación larga: empieza una selección o un arrastre. */
    const val HOLD = HapticFeedbackConstants.LONG_PRESS

    /** Soltar en su sitio: un álbum, una posición. */
    const val DROP = HapticFeedbackConstants.CONFIRM

    /** Borrar o llegar al final de algo. */
    const val DELETE = HapticFeedbackConstants.REJECT

    fun perform(view: View, kind: Int) {
        if (enabled) view.performHapticFeedback(kind)
    }
}

/** El sistema de vibración de Compose, pasando por [Haptics]: así todos los `performHapticFeedback` obedecen al interruptor. */
@Composable
fun rememberLumiHaptics(): HapticFeedback {
    val view = LocalView.current
    return remember(view) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                val kind = when (hapticFeedbackType) {
                    HapticFeedbackType.LongPress -> Haptics.HOLD
                    HapticFeedbackType.TextHandleMove -> Haptics.TICK
                    else -> Haptics.TICK
                }
                Haptics.perform(view, kind)
            }
        }
    }
}
