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

/** Color de botones y marcas. Cada uno tiene un tono para el tema oscuro y otro, más intenso, para el claro. */
enum class AccentColor(val label: String, val dark: Color, val light: Color) {
    LILAC("Lila", Color(0xFFB9A8FF), Color(0xFF5B3DF5)),
    CORAL("Coral", Color(0xFFFFB199), Color(0xFFC2452D)),
    GREEN("Verde", Color(0xFF8FD9B6), Color(0xFF157A4C)),
    BLUE("Azul", Color(0xFF8EC5FF), Color(0xFF1E63D0)),
    AMBER("Ámbar", Color(0xFFFFD479), Color(0xFF8A5A00)),
}

/** Colores de la app. Cambian con el tema; el visor de fotos es siempre oscuro y no los usa. */
object Lumi {
    var dark by mutableStateOf(true)
        private set
    private var accent by mutableStateOf(AccentColor.LILAC)
    /** Fondo negro del todo en el tema oscuro, para pantallas OLED. */
    private var black by mutableStateOf(false)

    val Bg get() = if (!dark) Color(0xFFF6F5FA) else if (black) Color.Black else Color(0xFF0E0E13)
    val Surface get() = if (!dark) Color(0xFFE9E6F3) else if (black) Color(0xFF16161B) else Color(0xFF1C1B24)
    val Line get() = if (dark) Color(0xFF2A2933) else Color(0xFFD9D6E6)
    val Ink get() = if (dark) Color(0xFFF4F3F8) else Color(0xFF15131F)
    val Muted get() = if (dark) Color(0xFFA19FB2) else Color(0xFF5D5A6E)
    val Accent get() = if (dark) accent.dark else accent.light
    val OnAccent get() = if (dark) Color(0xFF17131F) else Color(0xFFFFFFFF)
    val Danger get() = if (dark) Color(0xFFFF8A80) else Color(0xFFC23B2E)

    internal fun apply(isDark: Boolean, color: AccentColor, pureBlack: Boolean) {
        dark = isDark
        accent = color
        black = pureBlack
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
fun LumiTheme(mode: ThemeMode, accent: AccentColor, pureBlack: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    Lumi.apply(dark, accent, pureBlack)
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
