package com.lumi.galeria.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.dayTitle
import com.lumi.galeria.data.findDateFixes

/**
 * Fotos cuya fecha está mal: las de WhatsApp y las descargadas, que salen con la fecha del día en que
 * llegaron y no con la que dice su nombre; y las que no tienen fecha (escaneos). Se arreglan todas de
 * golpe o una a una; a las que no tienen fecha se les elige una en el calendario.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DatesScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    val fixes = remember(state.items) { findDateFixes(state.items) }
    val undated = remember(state.items) { state.items.filter { it.noDate && !it.isVideo && fixes.none { f -> f.item.id == it.id } } }
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var picking by remember { mutableStateOf(false) }
    val chosen = undated.filter { it.id in selection }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Fechas por revisar", "Cada foto en su día", onBack = { vm.back() })
        if (fixes.isEmpty() && undated.isEmpty()) {
            EmptyMessage("Todo en su día", "No hay fotos con la fecha equivocada ni fotos sin fecha.", Modifier.weight(1f), icon = CameraLineIcon)
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fixes.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Con otra fecha en el nombre", style = HeadingStyle)
                            Text(
                                "${countText(fixes.size, "foto", "fotos")} de WhatsApp o descargadas salen con la fecha en que llegaron. El nombre del archivo dice cuándo se hicieron de verdad.",
                                style = SmallStyle.copy(fontSize = 13.sp),
                            )
                            PillButton(
                                "Arreglar las ${fixes.size}",
                                onClick = { actions.write(fixes.map { it.item }) { vm.fixDates(fixes.map { it.item to it.proposed }) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(fixes.take(60), key = { it.item.id }) { fix ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lumi.Surface).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MediaThumb(fix.item, 160, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.open(Screen.Viewer(Source.Ids(setOf(fix.item.id)), fix.item.id)) })
                            Column(Modifier.weight(1f)) {
                                Text(fix.item.name, style = LabelStyle, maxLines = 1)
                                Text("${dayTitle(fix.item.date)} → ${dayTitle(fix.proposed)} · ${fix.origin}", style = SmallStyle, maxLines = 2)
                            }
                            Text(
                                "Arreglar", style = LabelStyle, color = Lumi.Accent,
                                modifier = Modifier.clip(CircleShape).clickable { actions.write(listOf(fix.item)) { vm.fixDates(listOf(fix.item to fix.proposed)) } }.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (fixes.size > 60) item { Text("Y ${fixes.size - 60} más: «Arreglar las ${fixes.size}» las cambia todas.", style = SmallStyle, modifier = Modifier.padding(8.dp)) }
                }
                if (undated.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Sin fecha", style = HeadingStyle)
                            Text(
                                "${countText(undated.size, "foto no dice", "fotos no dicen")} cuándo se hicieron (escaneos, por ejemplo). Elige las que quieras y ponles una fecha.",
                                style = SmallStyle.copy(fontSize = 13.sp),
                            )
                        }
                    }
                    item {
                        // Las fotos sin fecha, para elegirlas con un toque.
                        Box(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                items(undated, key = { it.id }) { item ->
                                    PhotoTile(
                                        item = item, px = 256, selected = item.id in selection,
                                        modifier = Modifier.aspectRatio(1f).combinedClickable(
                                            onLongClick = { vm.open(Screen.Viewer(Source.Ids(setOf(item.id)), item.id)) },
                                        ) { selection = if (item.id in selection) selection - item.id else selection + item.id },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("Elegir todas", { selection = undated.mapTo(HashSet()) { it.id } }, modifier = Modifier.weight(1f), primary = false)
                            PillButton(
                                if (chosen.isEmpty()) "Elige fotos" else "Poner fecha a ${chosen.size}",
                                { picking = true }, modifier = Modifier.weight(1f), enabled = chosen.isNotEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        DatePickDialog(System.currentTimeMillis(), onPick = { taken ->
            picking = false
            actions.write(chosen) { vm.fixDates(chosen.map { it to taken }) }
            selection = emptySet()
        }, onDismiss = { picking = false })
    }
}
