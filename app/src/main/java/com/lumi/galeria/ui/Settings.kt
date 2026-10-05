package com.lumi.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.LockMethod
import com.lumi.galeria.BuildConfig
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.ReviewKind
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.formatSize

@Composable
fun SettingsScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    val recoverable = remember(state.items, state.stackByBest) {
        ReviewKind.entries.filter { it.preselected }.flatMap { state.reviewItems(it) }.distinctBy { it.id }.sumOf { it.size }
    }

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Ajustes", "", onBack = { vm.back() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Aspecto")
            Card {
                Text("Tema", style = HeadingStyle.copy(fontSize = 15.sp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val on = state.theme == mode
                        Text(
                            mode.label,
                            style = LabelStyle,
                            color = if (on) Lumi.OnAccent else Lumi.Ink,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (on) Lumi.Accent else Lumi.Bg)
                                .clickable { vm.setTheme(mode) }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            Section("Privacidad")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pedir huella al abrir Lumi", style = HeadingStyle.copy(fontSize = 15.sp))
                        Text("Usa tu huella o el bloqueo del teléfono (PIN, patrón o contraseña).", style = SmallStyle)
                    }
                    Switch(
                        checked = state.appLock,
                        // Para activarlo o quitarlo hay que demostrar que eres tú.
                        onCheckedChange = { wanted -> actions.unlock { vm.setAppLock(wanted) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Lumi.Accent, checkedThumbColor = Lumi.OnAccent),
                    )
                }
            }
            Card {
                Text("Cómo se desbloquea", style = HeadingStyle.copy(fontSize = 15.sp))
                Text(
                    if (state.lockMethod == LockMethod.SYSTEM) "Con lo que uses en el teléfono: huella, cara, PIN, patrón o contraseña."
                    else "Con un PIN solo para Lumi, distinto del teléfono. La huella sigue valiendo como atajo.",
                    style = SmallStyle,
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LockMethod.entries.forEach { method ->
                        val on = state.lockMethod == method
                        Text(
                            method.label,
                            style = LabelStyle,
                            color = if (on) Lumi.OnAccent else Lumi.Ink,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (on) Lumi.Accent else Lumi.Bg)
                                .clickable(enabled = !on) {
                                    // Cambiar de método exige pasar antes por el actual.
                                    // Si el teléfono no tiene bloqueo no hay nada que comprobar todavía.
                                    if (method == LockMethod.PIN && !actions.canUnlock()) vm.createPin()
                                    else actions.unlock { if (method == LockMethod.PIN) vm.createPin() else vm.useSystemLock() }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                if (state.lockMethod == LockMethod.PIN) {
                    Text(
                        "Cambiar el PIN",
                        style = LabelStyle,
                        color = Lumi.Accent,
                        modifier = Modifier.padding(top = 6.dp).clip(CircleShape).clickable { actions.unlock { vm.createPin() } }.padding(vertical = 8.dp),
                    )
                }
            }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mostrar álbumes ocultos", style = HeadingStyle.copy(fontSize = 15.sp))
                        Text("Los álbumes que ocultaste vuelven a verse, marcados como ocultos.", style = SmallStyle)
                    }
                    Switch(
                        checked = state.showHidden,
                        onCheckedChange = { vm.setShowHidden(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Lumi.Accent, checkedThumbColor = Lumi.OnAccent),
                    )
                }
            }
            LinkRow("Carpeta privada", "Fotos cifradas que solo se abren con tu huella o tu bloqueo") {
                if (state.vaultOpen) vm.open(Screen.Vault)
                else actions.unlock {
                    vm.unlockVault()
                    vm.open(Screen.Vault)
                }
            }

            // Google Play no admite en una galería el permiso que esto necesita: solo va en la variante completa.
            if (BuildConfig.HIDDEN_FOLDERS) {
                LinkRow("Carpetas ocultas del sistema", "Fotos y vídeos que otras apps guardan donde las galerías no miran") {
                    vm.open(Screen.HiddenFolders)
                }
            }

            Section("Tus fotos")
            Card {
                Text("Orden de la pantalla Fotos", style = HeadingStyle.copy(fontSize = 15.sp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "Recientes primero", true to "Antiguas primero").forEach { (oldest, label) ->
                        val on = state.oldestFirst == oldest
                        Text(
                            label,
                            style = LabelStyle,
                            color = if (on) Lumi.OnAccent else Lumi.Ink,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (on) Lumi.Accent else Lumi.Bg)
                                .clickable { vm.setOldestFirst(oldest) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            LinkRow(
                "Liberar espacio",
                if (recoverable > 0) "Puedes recuperar ${formatSize(recoverable)}" else "Repetidas, vídeos grandes y capturas antiguas",
            ) { vm.open(Screen.Space) }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Usar la papelera", style = HeadingStyle.copy(fontSize = 15.sp))
                        Text(
                            if (state.useTrash) "Lo que borras se guarda 30 días y después se elimina solo, para siempre."
                            else "Lo que borras se elimina en el acto y no se puede recuperar.",
                            style = SmallStyle,
                        )
                    }
                    Switch(
                        checked = state.useTrash,
                        onCheckedChange = { vm.setUseTrash(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Lumi.Accent, checkedThumbColor = Lumi.OnAccent),
                    )
                }
            }
            LinkRow(
                "Papelera",
                if (state.trashed.isEmpty()) "Vacía" else "${countText(state.trashed.size, "elemento", "elementos")} · ${formatSize(state.trashed.sumOf { it.size })}",
            ) { vm.open(Screen.Trash) }
            LinkRow(
                "Copia de seguridad",
                when {
                    state.backup.destination == null -> "A tarjeta SD, memoria USB o una carpeta"
                    state.backup.pending == 0 -> "Todo copiado en ${state.backup.destination}"
                    else -> "${countText(state.backup.pending, "archivo", "archivos")} sin copiar"
                },
            ) { vm.open(Screen.Backup) }

            Section("Análisis de las fotos")
            Card {
                Text(
                    when {
                        state.photoCount == 0 -> "Sin fotos que analizar"
                        state.unindexed > 0 -> "Mirando qué hay en cada foto: ${state.photoCount - state.unindexed} de ${state.photoCount}"
                        state.deepPending > 0 -> "Leyendo el texto de las fotos: faltan ${state.deepPending}"
                        else -> "Todo analizado"
                    },
                    style = HeadingStyle.copy(fontSize = 15.sp),
                )
                Text(
                    "Sirve para buscar, para los álbumes de cosas y viajes y para detectar repetidas. Se hace en el teléfono, despacio, y se detiene mientras tocas la pantalla.",
                    style = SmallStyle,
                )
            }

            Section("Acerca de")
            Card {
                Text("Lumi Gallery $version", style = HeadingStyle.copy(fontSize = 15.sp))
                Text("Sin anuncios, sin cuenta y sin permiso de internet: tus fotos no salen del teléfono.", style = SmallStyle)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = SmallStyle.copy(fontSize = 13.sp), color = Lumi.Accent, modifier = Modifier.padding(start = 8.dp, top = 14.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface).padding(16.dp)) { content() }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface).clickable(onClick = onClick).padding(16.dp),
    ) {
        Text(title, style = HeadingStyle.copy(fontSize = 15.sp))
        Text(subtitle, style = SmallStyle)
    }
}
