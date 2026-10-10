package com.lumi.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.dayTitle
import com.lumi.galeria.formatCount
import com.lumi.galeria.monthTitle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lo que se cuenta de un mes. */
class MonthSummary(
    val month: YearMonth,
    val photos: Int,
    val videos: Int,
    val days: Int,
    val best: List<MediaItem>,
    val bestDay: Pair<LocalDate, List<MediaItem>>?,
    val people: List<YearGroup>,
    val places: List<YearGroup>,
    val things: List<YearGroup>,
)

private fun monthOf(item: MediaItem, zone: ZoneId) = YearMonth.from(Instant.ofEpochMilli(item.date).atZone(zone))

fun summarizeMonth(state: UiState, month: YearMonth): MonthSummary {
    val zone = ZoneId.systemDefault()
    val shots = state.items.filter { !it.isScreenshot && monthOf(it, zone) == month }
    val days = shots.groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
    val names = state.namesByPhoto
    val people = HashMap<String, ArrayList<MediaItem>>()
    for (item in shots) names[item.id]?.forEach { people.getOrPut(it) { ArrayList() } += item }
    val places = shots.mapNotNull { item -> state.places[item.id]?.let { it to item } }
        .groupBy({ it.first }, { it.second }).entries.sortedByDescending { it.value.size }.take(4).map { YearGroup(it.key, it.value) }
    val things = HashMap<String, ArrayList<MediaItem>>()
    for (item in shots) com.lumi.galeria.data.thingWords(state.index[item.id]).forEach { things.getOrPut(it) { ArrayList() } += item }
    // Lo mejor: las favoritas y después las más nítidas.
    val best = (shots.filter { it.id in state.favorites && !it.isVideo } +
        shots.filter { !it.isVideo }.sortedByDescending { state.index[it.id]?.blur ?: 0f }).distinct().take(12).sortedBy { it.date }
    return MonthSummary(
        month = month,
        photos = shots.count { !it.isVideo },
        videos = shots.count { it.isVideo },
        days = days.size,
        best = best,
        bestDay = days.entries.maxByOrNull { it.value.size }?.let { it.key to it.value },
        people = people.entries.sortedByDescending { it.value.size }.take(6).map { YearGroup(it.key, it.value) },
        places = places,
        things = things.entries.sortedByDescending { it.value.size }.take(6).map { YearGroup(it.key.replaceFirstChar(Char::uppercase), it.value) },
    )
}

/** El mes anterior, si tiene fotos suficientes para contar algo. */
fun previousMonthWithStory(state: UiState, today: LocalDate = LocalDate.now()): YearMonth? {
    if (today.dayOfMonth > 10) return null
    val month = YearMonth.from(today).minusMonths(1)
    val zone = ZoneId.systemDefault()
    val n = state.items.count { !it.isScreenshot && monthOf(it, zone) == month }
    return month.takeIf { n >= 20 }
}

/** Tu mes en fotos: portada, cifras, lo mejor, las personas y los sitios. */
@Composable
fun MonthStoryScreen(screen: Screen.MonthStory, state: UiState, vm: LumiViewModel, actions: Actions) {
    val month = YearMonth.of(screen.year, screen.month)
    val summary by produceState<MonthSummary?>(null, month, state.items, state.index, state.places) {
        value = withContext(Dispatchers.Default) { summarizeMonth(state, month) }
    }
    LaunchedEffect(Unit) { vm.discover("tu_mes") }
    val title = monthTitle(month)

    fun openGroup(name: String, items: List<MediaItem>) = vm.open(Screen.Items(name, Source.Ids(items.mapTo(HashSet()) { it.id })))

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Tu mes en fotos", title, onBack = { vm.back() })
        val s = summary
        if (s == null) { Box(Modifier.weight(1f)); return@Column }
        if (s.photos + s.videos == 0) {
            EmptyMessage("Nada en $title", "No hay fotos de ese mes, sin contar capturas de pantalla.", Modifier.weight(1f))
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val cover = s.best.lastOrNull { it.id in state.favorites } ?: s.best.firstOrNull() ?: state.items.first { monthOf(it, ZoneId.systemDefault()) == month }
            Box(Modifier.fillMaxWidth().aspectRatio(1.1f).clip(RoundedCornerShape(26.dp)).clickable {
                if (s.best.isNotEmpty()) vm.open(Screen.StoryView(title, s.best.map { it.id }, Screen.Items(title, Source.Ids(s.best.mapTo(HashSet()) { it.id }))))
            }) {
                MediaThumb(cover, 1024, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = TitleStyle.copy(fontSize = 34.sp), color = Color.White)
                    Text(
                        listOfNotNull(
                            countText(s.photos, "foto", "fotos"),
                            if (s.videos > 0) countText(s.videos, "vídeo", "vídeos") else null,
                            countText(s.days, "día con fotos", "días con fotos"),
                        ).joinToString(" · "),
                        style = LabelStyle.copy(fontSize = 15.sp), color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
            if (s.best.size >= 2) {
                PillButton("Hacer un vídeo con música", { vm.open(Screen.MemoryVideo(title, s.best.map { it.id })) }, modifier = Modifier.fillMaxWidth(), primary = false)
            }
            s.bestDay?.let { (day, items) ->
                MonthCard {
                    Text("Tu mejor día", style = HeadingStyle.copy(fontSize = 17.sp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { openGroup(dayTitle(day), items) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MediaThumb(state.coverOf(items), 256, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(dayTitle(day), style = LabelStyle)
                            Text(countText(items.size, "foto", "fotos"), style = SmallStyle)
                        }
                    }
                }
            }
            if (s.best.isNotEmpty()) MonthCard {
                Text("Lo mejor del mes", style = HeadingStyle.copy(fontSize = 17.sp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.best.forEach { item ->
                        MediaThumb(item, 384, Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).clickable {
                            vm.open(Screen.Viewer(Source.Ids(s.best.mapTo(HashSet()) { it.id }), item.id))
                        })
                    }
                }
            }
            if (s.people.isNotEmpty()) MonthCard {
                Text("Con quién estuviste", style = HeadingStyle.copy(fontSize = 17.sp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.people.forEach { g -> MonthTile(g) { openGroup(g.name, g.items) } }
                }
            }
            if (s.places.isNotEmpty()) MonthCard {
                Text("Dónde estuviste", style = HeadingStyle.copy(fontSize = 17.sp))
                s.places.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { openGroup(g.name, g.items) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MediaThumb(g.items.first(), 160, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                        Text(g.name, style = LabelStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatCount(g.items.size), style = LabelStyle, color = Lumi.Muted)
                    }
                }
            }
            if (s.things.isNotEmpty()) MonthCard {
                Text("Lo que más fotografiaste", style = HeadingStyle.copy(fontSize = 17.sp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.things.forEach { g -> MonthTile(g) { openGroup(g.name, g.items) } }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MonthCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Lumi.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun MonthTile(group: YearGroup, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MediaThumb(group.items.first(), 256, Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)))
        Text(group.name.replaceFirstChar { it.uppercase() }, style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatCount(group.items.size), style = SmallStyle)
    }
}

/** La tarjeta de la cronología que invita a ver el mes anterior, los primeros días de cada mes. */
@Composable
fun MonthStoryCard(state: UiState, vm: LumiViewModel, modifier: Modifier = Modifier) {
    val month = remember(state.items.size) { previousMonthWithStory(state) } ?: return
    val key = "mes_${month.year}_${month.monthValue}"
    if (vm.coachSeen(key)) return
    val cover = remember(month, state.items.size) {
        val zone = ZoneId.systemDefault()
        state.items.firstOrNull { it.id in state.favorites && !it.isScreenshot && monthOf(it, zone) == month }
            ?: state.items.firstOrNull { !it.isVideo && !it.isScreenshot && monthOf(it, zone) == month }
    } ?: return
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface)
            .clickable { vm.open(Screen.MonthStory(month.year, month.monthValue)) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediaThumb(cover, 256, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
        Column(Modifier.weight(1f)) {
            Text("Tu ${monthTitle(month).substringBefore(' ').lowercase()} en fotos", style = LabelStyle)
            Text("Lo mejor, las personas y los sitios del mes pasado", style = SmallStyle, maxLines = 2)
        }
        Text(
            "Cerrar", style = SmallStyle, color = Lumi.Muted,
            modifier = Modifier.clip(CircleShape).clickable { vm.markCoach(key) }.padding(8.dp),
        )
    }
}
