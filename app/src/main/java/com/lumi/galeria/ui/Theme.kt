package com.lumi.galeria.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lumi.galeria.R

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Oscuro") }

/** Colores de la app. Cambian con el tema; el visor de fotos es siempre oscuro y no los usa. */
object Lumi {
    var dark by mutableStateOf(true)
        private set

    val Bg get() = if (dark) Color(0xFF0E0E13) else Color(0xFFF6F5FA)
    val Surface get() = if (dark) Color(0xFF1C1B24) else Color(0xFFE9E6F3)
    val Line get() = if (dark) Color(0xFF2A2933) else Color(0xFFD9D6E6)
    val Ink get() = if (dark) Color(0xFFF4F3F8) else Color(0xFF15131F)
    val Muted get() = if (dark) Color(0xFFA19FB2) else Color(0xFF5D5A6E)
    val Accent get() = if (dark) Color(0xFFB9A8FF) else Color(0xFF5B3DF5)
    val OnAccent get() = if (dark) Color(0xFF1A1040) else Color(0xFFFFFFFF)
    val Danger get() = if (dark) Color(0xFFFF8A80) else Color(0xFFC23B2E)

    internal fun apply(isDark: Boolean) {
        dark = isDark
    }
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

val Display = variable(R.font.schibsted_grotesk, 700, 900)
val Body = variable(R.font.figtree, 400, 500, 600, 700)

private class Styles(val dark: Boolean) {
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = (-0.6).sp, color = Lumi.Ink)
    val heading = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Lumi.Ink)
    val label = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Lumi.Ink)
    val small = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Lumi.Muted)
}

private var styles = Styles(true)

/** Los estilos solo se rehacen al cambiar de tema; así cada texto no fabrica el suyo al pintarse. */
private fun current(): Styles {
    val dark = Lumi.dark
    if (styles.dark != dark) styles = Styles(dark)
    return styles
}

val TitleStyle get() = current().title
val HeadingStyle get() = current().heading
val LabelStyle get() = current().label
val SmallStyle get() = current().small

@Composable
fun LumiTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    Lumi.apply(dark)
    val scheme = if (dark) {
        darkColorScheme(
            primary = Lumi.Accent, onPrimary = Lumi.OnAccent, background = Lumi.Bg, onBackground = Lumi.Ink,
            surface = Lumi.Surface, onSurface = Lumi.Ink, surfaceContainerLow = Lumi.Surface, onSurfaceVariant = Lumi.Muted,
        )
    } else {
        lightColorScheme(
            primary = Lumi.Accent, onPrimary = Lumi.OnAccent, background = Lumi.Bg, onBackground = Lumi.Ink,
            surface = Lumi.Surface, onSurface = Lumi.Ink, surfaceContainerLow = Lumi.Surface, onSurfaceVariant = Lumi.Muted,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        ProvideTextStyle(TextStyle(fontFamily = Body, fontSize = 14.sp, color = Lumi.Ink), content)
    }
}
