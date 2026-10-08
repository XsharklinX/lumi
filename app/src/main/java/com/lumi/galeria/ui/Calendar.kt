package com.lumi.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.UiState
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.groupTitle
import com.lumi.galeria.Level
import com.lumi.galeria.monthTitle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Calendario para saltar a una fecha. Los días con fotos enseñan una; tocar uno lleva la
 * cuadrícula a ese día. [start] es el mes que se abre: el que se estaba viendo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSheet(tiles: List<MediaItem>, start: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // Día -> su primera foto. Recorre toda la biblioteca, así que se calcula aparte.
    val days by produceState(emptyMap<LocalDate, MediaItem>(), tiles) {
        value = withContext(Dispatchers.Default) {
            val zone = ZoneId.systemDefault()
            val found = HashMap<LocalDate, MediaItem>()
            for (item in tiles) found.putIfAbsent(Instant.ofEpochMilli(item.date).atZone(zone).toLocalDate(), item)
            found
        }
    }
    val counts by produceState(emptyMap<LocalDate, Int>(), tiles) {
        value = withContext(Dispatchers.Default) {
            val zone = ZoneId.systemDefault()
            HashMap<LocalDate, Int>().apply { tiles.forEach { merge(Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate(), 1, Int::plus) } }
        }
    }
    var month by remember { mutableStateOf(YearMonth.from(start)) }
    val years = remember(days) { days.keys.mapTo(sortedSetOf()) { it.year }.toList() }
    val today = remember { LocalDate.now() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lumi.Surface) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Mes anterior",
                    Modifier.clip(CircleShape).clickable { month = month.minusMonths(1) }.padding(10.dp).size(22.dp), tint = Lumi.Ink,
                )
                Text(monthTitle(month), style = HeadingStyle.copy(fontSize = 19.sp), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, "Mes siguiente",
                    Modifier.clip(CircleShape).clickable { month = month.plusMonths(1) }.padding(10.dp).size(22.dp), tint = Lumi.Ink,
                )
            }
            Row {
                (if (com.lumi.galeria.Lang.english) listOf("M", "T", "W", "T", "F", "S", "S") else listOf("L", "M", "X", "J", "V", "S", "D")).forEach { letter ->
                    Text(letter, style = SmallStyle, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            // La semana empieza en lunes: los huecos de delante son los días del mes anterior.
            val blanks = month.atDay(1).dayOfWeek.value - 1
            val slots = blanks + month.lengthOfMonth()
            for (week in 0 until (slots + 6) / 7) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (column in 0 until 7) {
                        val number = week * 7 + column - blanks + 1
                        val day = if (number in 1..month.lengthOfMonth()) month.atDay(number) else null
                        val photo = day?.let { days[it] }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .then(if (day != null && photo != null) Modifier.clickable { onPick(day) } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (photo != null) {
                                MediaThumb(photo, 160, Modifier.fillMaxSize())
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                            }
                            // Cuántas fotos tiene el día, en la esquina.
                            if (photo != null && (counts[day] ?: 0) > 1) {
                                Text(
                                    "${counts[day]}",
                                    style = SmallStyle.copy(fontSize = 9.sp),
                                    color = Color.White,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
                                )
                            }
                            if (day != null) {
                                Text(
                                    number.toString(),
                                    style = LabelStyle,
                                    color = when {
                                        photo != null -> Color.White
                                        day == today -> Lumi.Accent
                                        else -> Lumi.Muted
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Text("Toca un día para ver lo que hiciste o guardaste ese día.", style = SmallStyle)
            if (years.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    years.asReversed().forEach { year ->
                        val on = year == month.year
                        Text(
                            year.toString(),
                            style = LabelStyle,
                            color = if (on) Lumi.OnAccent else Lumi.Ink,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (on) Lumi.Accent else Lumi.Bg)
                                .clickable {
                                    // Al cambiar de año se va al mes más reciente que tenga fotos.
                                    val last = days.keys.filter { it.year == year }.maxOrNull()
                                    month = if (last != null) YearMonth.from(last) else month.withYear(year)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Algo para volver a mirar: unas fotos con título, y a dónde lleva tocarlo. */
class Story(val title: String, val cover: MediaItem, val count: Int, val open: Screen, val ids: List<Long>)

/**
 * Lo que se propone arriba de Fotos: estos mismos días en otros años, el último viaje y las
 * favoritas del último mes que tenga varias. Sin avisos: está ahí por si apetece.
 */
fun storiesOf(state: UiState): List<Story> {
    val out = ArrayList<Story>()
    state.memory?.let { out += Story(it.title, it.cover, it.items.size, Screen.Items(it.title, Source.Auto("recuerdo")), it.items.map { m -> m.id }) }
    state.autoAlbums.firstOrNull { it.isTrip }?.let { trip ->
        out += Story(trip.title, trip.cover, trip.items.size, Screen.Items(trip.title, Source.Auto(trip.key)), trip.items.map { it.id })
    }
    // De diciembre a febrero, el resumen del año que acaba o acaba de terminar.
    val today = LocalDate.now()
    if (today.monthValue == 12 || today.monthValue <= 2) {
        val year = if (today.monthValue == 12) today.year else today.year - 1
        val summary = summarize(state, year)
        if (summary.highlights.size >= 8) {
            val title = if (com.lumi.galeria.Lang.english) "Your $year" else "Tu $year"
            out += Story(title, summary.highlights.last(), summary.highlights.size, Screen.YearReview(year), summary.highlights.map { it.id })
        }
    }
    if (state.favorites.size >= 3) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val current = YearMonth.from(today)
        val byMonth = HashMap<YearMonth, ArrayList<MediaItem>>()
        for (item in state.items) {
            if (item.id !in state.favorites) continue
            val month = YearMonth.from(Instant.ofEpochMilli(item.date).atZone(zone))
            if (month != current) byMonth.getOrPut(month) { ArrayList() } += item
        }
        byMonth.entries.filter { it.value.size >= 3 }.maxByOrNull { it.key }?.let { (month, items) ->
            val name = groupTitle(month.atDay(1), Level.MONTH, today)
            val title = if (com.lumi.galeria.Lang.english) "Best of $name" else "Lo mejor de " + name.replaceFirstChar { it.lowercase() }
            out += Story(title, state.coverOf(items), items.size, Screen.Items(title, Source.Ids(items.mapTo(HashSet()) { it.id })), items.map { it.id })
        }
    }
    return out
}

/** La fila de recuerdos. Con uno solo ocupa todo el ancho; con varios, van uno al lado del otro. */
@Composable
fun StoryRow(stories: List<Story>, onOpen: (Story) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stories.forEach { story ->
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(if (stories.size == 1) 2.4f else if (stories.size == 2) 1.15f else 0.74f)
                    .clip(RoundedCornerShape(if (stories.size == 1) 24.dp else 18.dp))
                    .clickable { onOpen(story) },
            ) {
                MediaThumb(story.cover, if (stories.size == 1) 1024 else 512, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
                Column(Modifier.align(Alignment.BottomStart).padding(if (stories.size == 1) 16.dp else 10.dp)) {
                    Text(
                        story.title,
                        style = if (stories.size == 1) TitleStyle.copy(fontSize = 24.sp) else HeadingStyle.copy(fontSize = 14.sp, lineHeight = 17.sp),
                        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text("${story.count}", style = SmallStyle, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

/** La mejor portada para [items]: nítida, con caras, nunca un documento ni una captura. */
fun UiState.coverOf(items: List<MediaItem>): MediaItem =
    com.lumi.galeria.data.pickCover(items, favorites, index, faceScore, showcaseOut)
