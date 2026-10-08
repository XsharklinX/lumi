package com.lumi.galeria.ui

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import kotlinx.coroutines.launch

/** Una página del recorrido: qué es, tres o cuatro cosas que se pueden hacer y adónde lleva «Probar». */
private class TourPage(
    val icon: ImageVector,
    val title: String,
    val text: String,
    val points: List<String>,
    val tryLabel: String? = null,
    val tryIt: ((LumiViewModel, Context) -> Unit)? = null,
)

private val PAGES = listOf(
    TourPage(
        SparkIcon, "Te damos la bienvenida a Lumi",
        "Una galería completa que funciona sin internet: tus fotos no salen del teléfono.",
        listOf("Sin anuncios, sin cuenta y sin suscripción", "Todo se analiza en el propio teléfono", "Desliza para ver lo que puedes hacer"),
    ),
    TourPage(
        SearchLineIcon, "Encuentra cualquier foto",
        "Escribe lo que buscas como lo dirías.",
        listOf("Cosas, sitios y fechas: «playa en 2024»", "Personas: «Lucía y Marcos»", "El texto de tickets, carteles y capturas"),
        "Probar el buscador", { vm, _ -> vm.openSearch() },
    ),
    TourPage(
        PersonIcon, "Personas",
        "Las caras de cada persona, juntas y con su nombre.",
        listOf("«¿Son la misma persona?» deslizando", "Grupos como Familia o Amigos", "«Cómo ha crecido» a lo largo de los años"),
        "Ver personas", { vm, _ -> vm.open(Screen.People) },
    ),
    TourPage(
        CameraLineIcon, "Cámara Lumi",
        "Fotos mejores que las de tu móvil, y sin esperar entre una y otra.",
        listOf("Lumi Auto, Noche y HDR juntan varias fotos", "Retrato, Pro, vídeo 4K y timelapse", "Dispara sola cuando todos sonríen", "QR y documentos en la misma rueda"),
        "Abrir la cámara", { vm, _ -> vm.open(Screen.Camera) },
    ),
    TourPage(
        FlipCameraIcon, "Tu cámara de siempre",
        "La Cámara Lumi puede ser la cámara de tu teléfono.",
        listOf("Elígela como cámara y toca «Siempre»", "Las demás apps también le piden fotos", "Desde la pantalla de bloqueo, sin enseñar tu galería", "Widget e icono propio para abrirla al instante"),
        "Usar como cámara", { _, context -> runCatching { context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) } },
    ),
    TourPage(
        PictureIcon, "Widgets y recuerdos",
        "Tus fotos en la pantalla de inicio, y recuerdos que se arman solos.",
        listOf("Widgets de foto, mosaico, recuerdos, cámara y atajos", "Un fondo de pantalla que cambia solo", "Un aviso por la mañana de este día en otros años", "Vídeos con música y «Tu año en fotos»"),
    ),
    TourPage(
        PenIcon, "Edita y crea",
        "Sin otras apps: todo el editor está dentro y siempre guarda una copia.",
        listOf("Curvas, tonos, retrato y quitar el fondo", "Dibujar, texto y pegatinas", "Editor de vídeo, GIF y collage", "Mejora en lote las fotos oscuras"),
    ),
    TourPage(
        ScanIcon, "Tus documentos",
        "Escanea, guarda y firma, todo ordenado en su pestaña.",
        listOf("Un escáner que endereza cada hoja", "PDF con texto que se puede buscar", "Nombre automático, DNI en una hoja y firma"),
        "Ir a Documentos", { vm, _ -> vm.switchTab(Screen.Documents) },
    ),
    TourPage(
        LockLineIcon, "Privacidad de verdad",
        "Lo tuyo se queda tuyo, también si alguien coge tu teléfono.",
        listOf("Carpeta privada cifrada y PIN señuelo", "Encuentra tu DNI, tarjetas y contraseñas", "Modo enseñar: presta el móvil sin miedo", "Lumi en blanco en las apps recientes"),
    ),
    TourPage(
        ShrinkIcon, "Espacio y orden",
        "Lumi te ayuda a limpiar, pero nunca borra nada que no elijas.",
        listOf("Copias, parecidas y ojos cerrados", "Borrar sin que Android pregunte cada vez", "Carpetas para agrupar tus álbumes", "Tarjeta SD, USB y copia de seguridad"),
        "Liberar espacio", { vm, _ -> vm.open(Screen.Space) },
    ),
    TourPage(
        TrophyIcon, "Hecha para todo el mundo",
        "Y este recorrido se vuelve a ver cuando quieras desde Ajustes.",
        listOf("Letra más grande y contraste alto", "TalkBack lee lo que sale en cada foto", "En español y en inglés"),
    ),
)

/**
 * Lo que puede hacer Lumi, en páginas cortas que se pasan deslizando. Sale al empezar (y otra vez
 * tras una actualización grande) y se vuelve a ver desde Ajustes. Cada página lleva a probarlo.
 */
@Composable
fun TourScreen(state: UiState, vm: LumiViewModel) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun close() {
        vm.tourDone()
        vm.back()
    }
    BackHandler { close() }

    Box(Modifier.fillMaxSize().background(Lumi.Bg)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
            val page = PAGES[index]
            // Al llegar a una página, el título y cada punto entran uno detrás de otro.
            val enter = remember { Animatable(0f) }
            LaunchedEffect(pager.currentPage == index) {
                if (pager.currentPage == index) {
                    enter.snapTo(0f)
                    enter.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                }
            }
            fun step(i: Int): Float = ((enter.value * (page.points.size + 2)) - i).coerceIn(0f, 1f)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Arriba, un dibujo propio de cada página: nunca fotos del teléfono, que pueden ser personales.
                LumiArt(page.icon, ART_PALETTE[index % ART_PALETTE.size], Modifier.fillMaxWidth().aspectRatio(1.3f))
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        page.title, style = TitleStyle.copy(fontSize = 30.sp, lineHeight = 34.sp),
                        modifier = Modifier.alpha(step(0)).offset(y = ((1f - step(0)) * 14).dp),
                    )
                    Text(page.text, style = SmallStyle.copy(fontSize = 16.sp, lineHeight = 22.sp, color = Lumi.Ink), modifier = Modifier.alpha(step(0)))
                    Spacer(Modifier.height(2.dp))
                    page.points.forEachIndexed { i, point ->
                        val a = step(i + 1)
                        Row(
                            Modifier.alpha(a).offset(x = ((1f - a) * 18).dp),
                            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(Lumi.Accent))
                            Text(point, style = LabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                    page.tryLabel?.let { label ->
                        PillButton(label, onClick = {
                            vm.tourDone()
                            vm.back()
                            page.tryIt?.invoke(vm, context)
                        }, modifier = Modifier.padding(top = 8.dp), primary = false)
                    }
                    Spacer(Modifier.height(150.dp))
                }
            }
        }
        // Abajo: puntos de página, saltar y seguir.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Lumi.Bg)).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PAGES.indices.forEach { i ->
                    Box(Modifier.height(7.dp).width(if (i == pager.currentPage) 22.dp else 7.dp).clip(CircleShape).background(if (i == pager.currentPage) Lumi.Accent else Lumi.Line))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Saltar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { close() }.padding(12.dp))
                Spacer(Modifier.weight(1f))
                val last = pager.currentPage == PAGES.lastIndex
                PillButton(if (last) "Empezar" else "Siguiente", onClick = {
                    if (last) close() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                })
            }
        }
    }
}
