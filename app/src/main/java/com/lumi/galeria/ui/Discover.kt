package com.lumi.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.data.FEATURES
import com.lumi.galeria.data.Feature
import com.lumi.galeria.data.FeatureTopic
import com.lumi.galeria.data.Go

/** Lleva a donde se prueba una función y la da por descubierta. */
fun runGo(feature: Feature, vm: LumiViewModel, actions: Actions) {
    vm.discover(feature.id)
    when (feature.go) {
        Go.NONE -> vm.switchTab(Screen.Timeline)
        Go.SEARCH -> vm.open(Screen.Search)
        Go.PEOPLE -> vm.open(Screen.People)
        Go.MERGE_PEOPLE -> vm.open(Screen.MergePeople)
        Go.CAMERA, Go.CAMERA_QR, Go.CAMERA_PRO, Go.CAMERA_PRIVATE, Go.CAMERA_NIGHT, Go.CAMERA_SMART, Go.CAMERA_TIMELAPSE -> vm.open(Screen.Camera)
        Go.PHONE_CAMERA, Go.SETTINGS, Go.EXPORT_HINT -> vm.open(Screen.Settings)
        Go.DOCUMENTS, Go.SCAN -> vm.open(Screen.Documents)
        Go.ID_CARD -> vm.open(Screen.IdCard)
        Go.VAULT -> vm.open(Screen.Vault)
        Go.SPACE -> vm.open(Screen.Space)
        Go.TOUR -> vm.open(Screen.Tour)
        Go.ENHANCE -> vm.open(Screen.Enhance)
        Go.SENSITIVE -> vm.open(Screen.Sensitive)
        Go.AUTO_WALLPAPER -> vm.open(Screen.AutoWallpaper)
        Go.YEAR -> vm.open(Screen.YearReview(java.time.LocalDate.now().year))
        Go.SWIPE -> vm.open(Screen.SwipeReview)
        Go.DATES -> vm.open(Screen.Dates)
        Go.STICKERS -> vm.open(Screen.Stickers)
        Go.ALBUMS -> vm.switchTab(Screen.Albums)
        Go.CALENDAR -> vm.switchTab(Screen.Timeline)
        Go.STATUS -> vm.open(Screen.Status)
    }
}

/** Todo lo que sabe hacer Lumi, por temas, con buscador, «Probar» y cuántas funciones llevas descubiertas. */
@Composable
fun DiscoverScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = FEATURES.filter { f ->
        q.isEmpty() || (f.title + " " + f.text + " " + f.words + " " + f.topic.label).lowercase().contains(q)
    }
    val done = FEATURES.count { it.id in state.discovered }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Descubre Lumi", "$done de ${FEATURES.size} descubiertas", onBack = { vm.back() })
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Lumi.Surface)) {
                Box(Modifier.fillMaxWidth(done / FEATURES.size.toFloat().coerceAtLeast(1f)).fillMaxHeight().clip(CircleShape).background(Lumi.Accent))
            }
            OutlinedTextField(
                query, { query = it }, placeholder = { Text("Buscar una función") }, singleLine = true, shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (shown.isEmpty()) {
            EmptyMessage("Nada con ese nombre", "Prueba con otra palabra, por ejemplo «fecha» o «vídeo».", Modifier.weight(1f), icon = SearchLineIcon)
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureTopic.entries.forEach { topic ->
                    val group = shown.filter { it.topic == topic }
                    if (group.isEmpty()) return@forEach
                    item(key = "t_${topic.name}") { Text(topic.label, style = HeadingStyle, modifier = Modifier.padding(start = 6.dp, top = 8.dp)) }
                    items(group, key = { it.id }) { f -> FeatureRow(f, f.id in state.discovered) { runGo(f, vm, actions) } }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(f: Feature, seen: Boolean, onTry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Lumi.Surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.title, style = LabelStyle)
                if (seen) Text("✓", style = LabelStyle, color = Lumi.Accent)
            }
            Text(f.text, style = SmallStyle.copy(fontSize = 13.sp))
        }
        Text(
            if (seen) "Otra vez" else "Probar", style = LabelStyle, color = if (seen) Lumi.Accent else Lumi.OnAccent,
            modifier = Modifier.clip(CircleShape).background(if (seen) Lumi.Accent.copy(alpha = 0.14f) else Lumi.Accent)
                .clickable(onClick = onTry).padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/** «¿Sabías que…?»: una tarjeta discreta arriba de Fotos con una función que aún no has descubierto. Se cierra con un toque. */
@Composable
fun TipOfDayCard(vm: LumiViewModel, actions: Actions, modifier: Modifier = Modifier) {
    // Cambia al cerrar la tarjeta y al volver a ver los consejos.
    val tick = vm.tipTick
    val state = LocalState.current
    val tip = remember(tick, state.discovered.size) { vm.tipOfDay() } ?: return
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Accent.copy(alpha = 0.12f))
            .clickable { vm.dismissTip(); runGo(tip, vm, actions) }.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("¿Sabías que…? ${tip.title}", style = LabelStyle.copy(fontSize = 13.sp), color = Lumi.Accent)
            Text(tip.text, style = SmallStyle.copy(fontSize = 13.sp, color = Lumi.Ink), maxLines = 3)
        }
        Text(
            "✕", style = LabelStyle, color = Lumi.Muted,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clip(CircleShape).clickable(onClickLabel = "Cerrar consejo") { vm.dismissTip() }.padding(12.dp),
        )
    }
}

private fun coachFor(screen: Screen, viewer: Boolean): Pair<String, String>? = when {
    viewer -> "visor" to "Desliza hacia arriba para ver su información, su nota y sus etiquetas. Pellizca o toca dos veces para acercar."
    screen == Screen.Timeline -> "fotos" to "Mantén pulsada una foto y arrastra el dedo para elegir varias de golpe. Pellizca para cambiar el zoom."
    screen == Screen.Albums -> "albumes" to "Mantén pulsado un álbum para fijarlo arriba, bloquearlo, ocultarlo o meterlo en una carpeta."
    screen == Screen.Search -> "buscar" to "Prueba «Lucía en la playa» o «tickets»: Lumi entiende personas, sitios y cosas. Guarda la búsqueda como álbum inteligente."
    else -> null
}

/** El globo de «primera vez»: la primera vez que se entra a una pantalla, dice qué se puede hacer en ella. Una sola vez. */
@Composable
fun CoachOverlay(vm: LumiViewModel, screen: Screen, viewer: Boolean, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val tick = vm.coachTick
    val coach = coachFor(screen, viewer) ?: return
    if (vm.coachSeen(coach.first)) return
    var ready by remember(coach.first, tick) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(coach.first, tick) {
        kotlinx.coroutines.delay(900)
        ready = true
    }
    androidx.compose.animation.AnimatedVisibility(ready, modifier, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 }, exit = androidx.compose.animation.fadeOut()) {
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Lumi.Ink).padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(coach.second, style = SmallStyle.copy(fontSize = 13.sp, color = Lumi.Bg), modifier = Modifier.weight(1f))
            Text(
                "Entendido", style = LabelStyle, color = Lumi.Bg,
                modifier = Modifier.clip(CircleShape).clickable { vm.markCoach(coach.first) }.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
