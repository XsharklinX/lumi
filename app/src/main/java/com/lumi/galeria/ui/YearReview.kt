package com.lumi.galeria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.Lang
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.data.thingWords
import com.lumi.galeria.dayTitle
import com.lumi.galeria.formatCount
import com.lumi.galeria.monthTitle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un grupo de fotos con nombre: un sitio, una cosa, un mes, una cámara. */
class YearGroup(val name: String, val items: List<MediaItem>)

/** Lo que se cuenta de un año. Todo sale de lo que ya sabe Lumi; nada se sube a ningún sitio. */
class YearSummary(
    val year: Int,
    val photos: Int,
    val videos: Int,
    val favorites: Int,
    val days: Map<LocalDate, List<MediaItem>>,
    val months: List<YearGroup>,
    val bestMonth: YearGroup?,
    val bestDay: Pair<LocalDate, List<MediaItem>>?,
    val places: List<YearGroup>,
    val trips: Int,
    val things: List<YearGroup>,
    val cameras: List<YearGroup>,
    /** Lo mejor del año, en orden: las favoritas y, de cada mes, las fotos más nítidas. */
    val highlights: List<MediaItem>,
)

/** Años con fotos, del más reciente al más antiguo. */
fun yearsWithPhotos(items: List<MediaItem>): List<Int> {
    val zone = ZoneId.systemDefault()
    return items.mapTo(HashSet()) { Instant.ofEpochMilli(it.date).atZone(zone).year }.sortedDescending()
}

/** El año que tiene sentido resumir: el pasado hasta que el actual vaya por la mitad. */
fun defaultReviewYear(items: List<MediaItem>): Int? {
    val years = yearsWithPhotos(items)
    if (years.isEmpty()) return null
    val now = LocalDate.now()
    return if (now.monthValue >= 7 || now.year - 1 !in years) years.first() else now.year - 1
}

fun summarize(state: UiState, year: Int): YearSummary {
    val zone = ZoneId.systemDefault()
    val all = state.items.filter { Instant.ofEpochMilli(it.date).atZone(zone).year == year }
    // Las capturas de pantalla no son recuerdos: no cuentan en el resumen.
    val shots = all.filter { !it.isScreenshot }
    val days = shots.groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
    val months = shots.groupBy { YearMonth.from(Instant.ofEpochMilli(it.date).atZone(zone)) }.entries
        .sortedBy { it.key }.map { YearGroup(monthTitle(it.key), it.value) }
    val places = shots.mapNotNull { item -> state.places[item.id]?.let { it to item } }
        .groupBy({ it.first }, { it.second }).entries.sortedByDescending { it.value.size }
        .take(6).map { YearGroup(it.key, it.value) }
    val things = HashMap<String, ArrayList<MediaItem>>()
    for (item in shots) thingWords(state.index[item.id]).forEach { things.getOrPut(it) { ArrayList() } += item }
    val cameras = shots.mapNotNull { item -> state.index[item.id]?.camera?.takeIf { it.isNotEmpty() }?.let { it to item } }
        .groupBy({ it.first }, { it.second }).entries.sortedByDescending { it.value.size }.take(3).map { YearGroup(it.key, it.value) }
    val trips = state.autoAlbums.count { album -> album.isTrip && album.items.any { Instant.ofEpochMilli(it.date).atZone(zone).year == year } }

    val favorites = shots.filter { it.id in state.favorites }
    val picks = LinkedHashSet<MediaItem>(favorites.take(24))
    for (month in months) {
        month.items.filter { !it.isVideo }
            .sortedByDescending { state.index[it.id]?.blur ?: 0f }
            .take(3).forEach { picks += it }
    }
    return YearSummary(
        year = year,
        photos = shots.count { !it.isVideo },
        videos = shots.count { it.isVideo },
        favorites = favorites.size,
        days = days,
        months = months,
        bestMonth = months.maxByOrNull { it.items.size },
        bestDay = days.entries.maxByOrNull { it.value.size }?.let { it.key to it.value },
        places = places,
        trips = trips,
        things = things.entries.sortedByDescending { it.value.size }.take(8).map { YearGroup(it.key.replaceFirstChar(Char::uppercase), it.value) },
        cameras = cameras,
        highlights = picks.sortedBy { it.date }.take(40),
    )
}

/**
 * Tu año en fotos: un calendario que se ilumina los días con más fotos, el mes y el día con más,
 * los sitios, lo que más fotografiaste y con qué. Cada dato abre esas fotos.
 */
@Composable
fun YearReviewScreen(screen: Screen.YearReview, state: UiState, vm: LumiViewModel) {
    val years = remember(state.items) { yearsWithPhotos(state.items) }
    var year by androidx.compose.runtime.remember { mutableIntStateOf(screen.year) }
    val summary by produceState<YearSummary?>(null, year, state.items, state.index, state.places) {
        value = withContext(Dispatchers.Default) { summarize(state, year) }
    }
    val title = if (Lang.english) "Your $year" else "Tu $year"

    fun openGroup(name: String, items: List<MediaItem>) {
        vm.open(Screen.Items(name, Source.Ids(items.mapTo(HashSet()) { it.id })))
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader(title, "Tu año en fotos", onBack = { vm.back() })
        if (years.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                years.forEach { y ->
                    val on = y == year
                    Text(
                        y.toString(), style = LabelStyle, color = if (on) Lumi.OnAccent else Lumi.Ink,
                        modifier = Modifier.clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Surface).clickable { year = y }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        val s = summary
        if (s == null || s.year != year) {
            Box(Modifier.weight(1f))
            return@Column
        }
        if (s.photos + s.videos == 0) {
            EmptyMessage("Nada en $year", "No hay fotos de ese año, sin contar capturas de pantalla.", Modifier.weight(1f))
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Portada: la mejor foto del año y lo que se hizo en números.
            val cover = s.highlights.lastOrNull { it.id in state.favorites } ?: s.highlights.firstOrNull() ?: s.days.values.first().first()
            Box(Modifier.fillMaxWidth().aspectRatio(1.1f).clip(RoundedCornerShape(26.dp)).clickable {
                if (s.highlights.isNotEmpty()) vm.open(Screen.StoryView(title, s.highlights.map { it.id }, Screen.Items(title, Source.Ids(s.highlights.mapTo(HashSet()) { it.id }))))
            }) {
                MediaThumb(cover, 1024, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = TitleStyle.copy(fontSize = 40.sp), color = Color.White)
                    Text(
                        listOfNotNull(
                            countText(s.photos, "foto", "fotos"),
                            if (s.videos > 0) countText(s.videos, "vídeo", "vídeos") else null,
                            if (s.trips > 0) countText(s.trips, "viaje", "viajes") else null,
                        ).joinToString(" · "),
                        style = LabelStyle.copy(fontSize = 15.sp), color = Color.White.copy(alpha = 0.9f),
                    )
                    if (s.highlights.isNotEmpty()) {
                        Row(
                            Modifier.padding(top = 8.dp).clip(CircleShape).background(Color.White).padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp), tint = Color.Black)
                            Text("Ver lo mejor del año", style = LabelStyle, color = Color.Black)
                        }
                    }
                }
            }

            if (s.highlights.size >= 2) {
                PillButton(
                    "Hacer un vídeo con música",
                    onClick = { vm.open(Screen.MemoryVideo(title, s.highlights.map { it.id })) },
                    modifier = Modifier.fillMaxWidth(),
                    primary = false,
                )
            }
            Card {
                Text("Tu año, día a día", style = HeadingStyle.copy(fontSize = 17.sp))
                Text("Cada cuadro es un día; cuanto más claro, más fotos. Toca uno para verlas.", style = SmallStyle)
                Heatmap(s) { day -> s.days[day]?.let { openGroup(dayTitle(day), it) } }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                s.bestMonth?.let { month ->
                    StatCard(month.name.substringBefore(' '), "tu mes con más fotos · ${formatCount(month.items.size)}", state.coverOf(month.items), Modifier.weight(1f)) {
                        openGroup(month.name, month.items)
                    }
                }
                s.bestDay?.let { (day, items) ->
                    StatCard(dayTitle(day), "tu día con más fotos · ${formatCount(items.size)}", state.coverOf(items), Modifier.weight(1f)) {
                        openGroup(dayTitle(day), items)
                    }
                }
            }

            if (s.places.isNotEmpty()) {
                Card {
                    Text("Dónde estuviste", style = HeadingStyle.copy(fontSize = 17.sp))
                    s.places.forEach { place -> GroupRow(place) { openGroup(place.name, place.items) } }
                }
            }
            if (s.things.isNotEmpty()) {
                Card {
                    Text("Lo que más fotografiaste", style = HeadingStyle.copy(fontSize = 17.sp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        s.things.forEach { thing -> Tile(thing) { openGroup(thing.name, thing.items) } }
                    }
                }
            }
            Card {
                Text("Mes a mes", style = HeadingStyle.copy(fontSize = 17.sp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.months.forEach { month -> Tile(YearGroup(month.name.substringBefore(' '), month.items)) { openGroup(month.name, month.items) } }
                }
            }
            if (s.cameras.isNotEmpty()) {
                Card {
                    Text("Con qué las hiciste", style = HeadingStyle.copy(fontSize = 17.sp))
                    val total = s.cameras.sumOf { it.items.size }.coerceAtLeast(1)
                    s.cameras.forEach { camera ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { openGroup(camera.name, camera.items) }.padding(vertical = 4.dp)) {
                            Row {
                                Text(camera.name, style = LabelStyle, modifier = Modifier.weight(1f))
                                Text("${camera.items.size * 100 / total} %", style = LabelStyle, color = Lumi.Accent)
                            }
                            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Lumi.Bg)) {
                                Box(Modifier.fillMaxWidth(camera.items.size.toFloat() / total).height(6.dp).background(Lumi.Accent))
                            }
                        }
                    }
                }
            }
            if (s.favorites > 0) {
                Text(
                    "Marcaste ${countText(s.favorites, "favorita", "favoritas")} este año.",
                    style = SmallStyle.copy(fontSize = 14.sp), modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Lumi.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Una semana por columna y un día por fila, como el calendario de actividad de un año. */
@Composable
private fun Heatmap(summary: YearSummary, onDay: (LocalDate) -> Unit) {
    val first = LocalDate.of(summary.year, 1, 1)
    // La primera columna empieza en lunes.
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val weeks = ((LocalDate.of(summary.year, 12, 31).toEpochDay() - start.toEpochDay()) / 7 + 1).toInt()
    val most = summary.days.values.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
    val counts = remember(summary) { summary.days.mapValues { it.value.size } }
    val accent = Lumi.Accent
    val empty = Lumi.Bg
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(weeks / 7f)
            .pointerInput(summary) {
                detectTapGestures { point ->
                    val cell = size.width.toFloat() / weeks
                    val week = (point.x / cell).toInt()
                    val weekday = (point.y / cell).toInt()
                    val day = start.plusDays(week * 7L + weekday)
                    if (day.year == summary.year && day in summary.days) onDay(day)
                }
            },
    ) {
        val cell = size.width / weeks
        val gap = cell * 0.18f
        for (week in 0 until weeks) for (weekday in 0 until 7) {
            val day = start.plusDays(week * 7L + weekday)
            if (day.year != summary.year) continue
            val n = counts[day] ?: 0
            // Raíz cuadrada: un día con muchísimas fotos no deja a los demás casi apagados.
            val level = if (n == 0) 0f else 0.25f + 0.75f * kotlin.math.sqrt(n.toFloat() / most)
            drawRoundRect(
                color = if (n == 0) empty else accent.copy(alpha = level),
                topLeft = Offset(week * cell + gap / 2, weekday * cell + gap / 2),
                size = Size(cell - gap, cell - gap),
                cornerRadius = CornerRadius(cell * 0.2f),
            )
        }
    }
}

@Composable
private fun StatCard(big: String, small: String, cover: MediaItem, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(0.9f).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        MediaThumb(cover, 512, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(big.replaceFirstChar { it.uppercase() }, style = TitleStyle.copy(fontSize = 20.sp, lineHeight = 23.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(small, style = SmallStyle, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GroupRow(group: YearGroup, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediaThumb(group.items.first(), 160, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
        Text(group.name, style = LabelStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatCount(group.items.size), style = LabelStyle, color = Lumi.Muted)
    }
}

@Composable
private fun Tile(group: YearGroup, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MediaThumb(group.items.first(), 256, Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)))
        Text(group.name.replaceFirstChar { it.uppercase() }, style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatCount(group.items.size), style = SmallStyle)
    }
}
