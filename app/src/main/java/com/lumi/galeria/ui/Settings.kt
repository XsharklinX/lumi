package com.lumi.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.AppLanguage
import com.lumi.galeria.BuildConfig
import com.lumi.galeria.LockMethod
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.ReviewKind
import com.lumi.galeria.Screen
import com.lumi.galeria.UiState
import com.lumi.galeria.countText
import com.lumi.galeria.data.normalize
import com.lumi.galeria.formatSize
import com.lumi.galeria.tr

/** Ventana de elección abierta en Ajustes. */
private enum class Picker { THEME, COLOR, LANGUAGE, ORDER, LOCK, DECOY, FORGET_FACES, CAMERA, RECENTS, MEMORY_HOUR, TEXT_SIZE }

private const val CONTACT = "contactosharklin@gmail.com"
private const val POLICY = "https://xsharklinx.github.io/lumi/privacidad.html"

/**
 * Ajustes en seis grupos, cada uno en una tarjeta con sus filas: Aspecto, Fotos y álbumes,
 * Privacidad y seguridad, Almacenamiento, Análisis de las fotos y Acerca de. Cada fila dice cómo
 * está ahora; las que tienen varias opciones abren una ventana pequeña para elegir.
 */
@Composable
fun SettingsScreen(state: UiState, vm: LumiViewModel, actions: Actions) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    val recoverable = remember(state.items, state.stackByBest) {
        ReviewKind.entries.filter { it.counts }.flatMap { state.reviewItems(it) }.distinctBy { it.id }.sumOf { it.size }
    }
    var query by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var cameraIcon by remember { mutableStateOf(com.lumi.galeria.cameraIconOn(context)) }
    // Aviso de recuerdos por la mañana: viene apagado; en Android 13 o posterior hace falta permiso.
    var memoryOn by remember { mutableStateOf(com.lumi.galeria.widget.Daily.memoryNotify(context).first) }
    var memoryHour by remember { mutableStateOf(com.lumi.galeria.widget.Daily.memoryNotify(context).second) }
    fun setMemory(on: Boolean, hour: Int) {
        memoryOn = on
        memoryHour = hour
        com.lumi.galeria.widget.Daily.saveMemoryNotify(context, on, hour)
    }
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) setMemory(true, memoryHour) else vm.say("Sin permiso de avisos no se puede avisar de los recuerdos")
    }
    // Cuánto ocupa la biblioteca y cómo se reparte.
    val usage = remember(state.items) {
        var photos = 0L
        var videos = 0L
        var shots = 0L
        state.items.forEach { item ->
            when {
                item.isVideo -> videos += item.size
                item.isScreenshot -> shots += item.size
                else -> photos += item.size
            }
        }
        longArrayOf(photos, videos, shots)
    }

    fun openVault() = vm.openVault(actions.unlock)

    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding().imePadding()) {
        ScreenHeader("Ajustes", "", onBack = { vm.back() })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar un ajuste") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Lumi.Muted) },
            trailingIcon = {
                if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Borrar", Modifier.clip(CircleShape).clickable { query = "" }.padding(6.dp))
            },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        )
        CompositionLocalProvider(LocalSettingsQuery provides normalize(query.trim())) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (query.isBlank()) UsageCard(state.items.size, usage, recoverable) { vm.open(Screen.Space) }

                Group(
                    "Aspecto",
                    words = "tema claro oscuro sistema color lila coral verde azul ambar negro puro oled idioma language english español ingles",
                ) {
                    Item(
                        Glyph.Half, "Tema", value = state.theme.label, words = "claro oscuro sistema",
                        onClick = { picker = Picker.THEME },
                    )
                    Item(
                        Glyph.Dot(if (Lumi.dark) state.accent.dark else state.accent.light), "Color", value = state.accent.label,
                        words = "lila coral verde azul ambar acento", onClick = { picker = Picker.COLOR },
                    )
                    Item(
                        Glyph.Dot(Color.Black), "Negro puro", "Fondo negro del todo en el tema oscuro. Gasta menos batería en pantallas OLED.",
                        words = "oled", toggle = state.pureBlack, onClick = { vm.setPureBlack(!state.pureBlack) },
                    )
                    Item(
                        Glyph.Letters("Aa"), "Idioma", value = state.language.label, words = "language english español ingles",
                        onClick = { picker = Picker.LANGUAGE },
                    )
                }

                Group("Fotos y álbumes", words = "destacar estrella mejor foto dia orden recientes antiguas ocultos esconder carpetas sistema agitar deshacer") {
                    Item(
                        Glyph.Icon(Icons.Filled.Star), "Vibraciones", "Un toque suave al elegir, un tic en las ruedas y un golpe distinto al soltar y al borrar.",
                        words = "vibracion vibrar haptico tactil tic", toggle = vm.hapticsOn, onClick = { vm.chooseHaptics(!vm.hapticsOn) },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Refresh), "Agitar para deshacer", "Justo después de borrar o mover algo, agita el móvil para deshacerlo.",
                        words = "agitar deshacer sacudir", toggle = vm.shakeUndo, onClick = { vm.chooseShakeUndo(!vm.shakeUndo) },
                    )
                    Item(
                        Glyph.Icon(Icons.AutoMirrored.Filled.List), "Orden de la pantalla Fotos",
                        value = if (state.oldestFirst) "Antiguas primero" else "Recientes primero", words = "recientes antiguas",
                        onClick = { picker = Picker.ORDER },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Star), "Destacar la mejor foto de cada día",
                        "En la vista por días, la más bonita de cada día sale en grande: favorita, nítida, con gente sonriendo.",
                        words = "estrella grande destacada diario", toggle = state.dayHighlights, onClick = { vm.setDayHighlights(!state.dayHighlights) },
                    )
                    Item(
                        Glyph.Icon(AlbumIcon), "Mostrar álbumes ocultos", "Los álbumes que ocultaste vuelven a verse, marcados como ocultos.",
                        words = "esconder", toggle = state.showHidden, onClick = { vm.setShowHidden(!state.showHidden) },
                    )
                    // Google Play no admite en una galería el permiso que esto necesita: solo va en la variante completa.
                    if (BuildConfig.HIDDEN_FOLDERS) {
                        Item(
                            Glyph.Icon(AlbumIcon), "Carpetas ocultas del sistema", "Fotos y vídeos que otras apps guardan donde las galerías no miran",
                            onClick = { vm.open(Screen.HiddenFolders) }, link = true,
                        )
                    }
                }

                Group("Privacidad y seguridad", words = "señuelo falso pedir huella al abrir bloqueo bloquear seguridad contraseña pin patron cara carpeta privada cifrada recientes multitarea dni tarjeta datos personales") {
                    Item(
                        Glyph.Icon(Icons.Filled.Lock), "Pedir huella al abrir Lumi", "Usa tu huella o el bloqueo del teléfono (PIN, patrón o contraseña).",
                        words = "bloqueo bloquear seguridad contraseña", toggle = state.appLock,
                        // Para activarlo o quitarlo hay que demostrar que eres tú.
                        onClick = { actions.unlock { vm.setAppLock(!state.appLock) } },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Face), "Cómo se desbloquea", value = state.lockMethod.label,
                        words = "pin huella cara patron contraseña", onClick = { picker = Picker.LOCK },
                    )
                    if (state.lockMethod == LockMethod.PIN) {
                        Item(Glyph.Icon(Icons.Filled.Edit), "Cambiar el PIN", words = "pin", onClick = { actions.unlock { vm.createPin() } }, link = true)
                        Item(
                            Glyph.Icon(Icons.Filled.Lock), "PIN señuelo",
                            "Si alguien te obliga a abrir la carpeta privada, escribe este PIN: se abre otra, con lo que tú hayas guardado en ella.",
                            value = if (state.hasDecoy) "Puesto" else "Sin poner", words = "señuelo falso disimular obligar",
                            onClick = { if (state.hasDecoy) picker = Picker.DECOY else actions.unlock { vm.createDecoyPin() } },
                        )
                    }
                    Item(
                        Glyph.Icon(Icons.Filled.Lock), "Carpeta privada", "Fotos cifradas que solo se abren con tu huella o tu bloqueo",
                        words = "cifrada oculta", onClick = { openVault() }, link = true,
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Search), "Fotos con datos personales",
                        if (state.sensitive.isEmpty()) "Documentos de identidad, tarjetas y contraseñas: Lumi te avisa si encuentra alguna"
                        else countText(state.sensitive.size, "foto con datos personales a la vista", "fotos con datos personales a la vista"),
                        words = "dni cedula pasaporte tarjeta banco contraseña clave datos personales", onClick = { vm.open(Screen.Sensitive) }, link = true,
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Lock), "Ocultar en apps recientes",
                        if (android.os.Build.VERSION.SDK_INT >= 33) "Al cambiar de app, Lumi se ve en blanco en vez de tus fotos."
                        else "Al cambiar de app, Lumi se ve en blanco. En esta versión de Android también impide hacer capturas dentro de Lumi.",
                        value = vm.recentsHide.label, words = "recientes multitarea miniatura cambiar de app", onClick = { picker = Picker.RECENTS },
                    )
                }

                Group("Accesibilidad", words = "letra grande tamaño texto contraste leer talkback vista") {
                    Item(
                        Glyph.Icon(Icons.Filled.Edit), "Tamaño de letra", value = textSizeName(vm.textScale),
                        words = "letra grande pequeña tamaño texto", onClick = { picker = Picker.TEXT_SIZE },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Info), "Contraste alto", "Textos secundarios y líneas más marcados",
                        words = "contraste leer ver", toggle = vm.highContrast, onClick = { vm.chooseHighContrast(!vm.highContrast) },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Face), "Con TalkBack", "Cada foto se lee con lo que sale en ella, quién, dónde y cuándo.",
                        words = "talkback lector pantalla ciego",
                    )
                }

                Group("Fondo y avisos", words = "fondo pantalla wallpaper cambiar solo recuerdo aviso notificacion mañana") {
                    val wall = remember(picker) { com.lumi.galeria.widget.Daily.wallSettings(context) }
                    Item(
                        Glyph.Icon(Icons.Filled.Refresh), "Fondo que cambia solo",
                        if (wall.on) tr(wall.source.label) + " · " + tr(if (wall.hours == 24) "Cada día" else if (wall.hours == 1) "Cada hora" else "Cada 3 horas")
                        else "Tus favoritas o un álbum de fondo de pantalla, cada día o cada hora",
                        words = "fondo pantalla wallpaper", onClick = { vm.open(Screen.AutoWallpaper) }, link = true,
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Star), "Recuerdo del día",
                        if (memoryOn) "Un aviso a las ${memoryHour}:00 cuando haya fotos de este día en otros años. Como mucho uno al día."
                        else "Un aviso por la mañana cuando haya fotos de este día en otros años",
                        words = "recuerdo aviso notificacion", toggle = memoryOn,
                        onClick = {
                            if (memoryOn) setMemory(false, memoryHour)
                            else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            else setMemory(true, memoryHour)
                        },
                    )
                    if (memoryOn) {
                        Item(Glyph.Icon(Icons.Filled.Edit), "Hora del aviso", value = "${memoryHour}:00", words = "hora", onClick = { picker = Picker.MEMORY_HOUR })
                    }
                }

                Group("Almacenamiento", words = "confirmar preguntar aviso permiso importar camara reflex liberar espacio repetidas videos grandes capturas papelera borrar eliminar 30 dias copia seguridad tarjeta usb") {
                    Item(
                        Glyph.Icon(SdCardIcon), "Liberar espacio",
                        if (recoverable > 0) "Puedes recuperar ${formatSize(recoverable)}" else "Repetidas, vídeos grandes y capturas antiguas",
                        words = "espacio memoria tarjeta", onClick = { vm.open(Screen.Space) }, link = true,
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Delete), "Usar la papelera",
                        if (state.useTrash) "Lo que borras se guarda 30 días y después se elimina solo, para siempre."
                        else "Lo que borras se elimina en el acto y no se puede recuperar.",
                        words = "borrar eliminar 30 dias", toggle = state.useTrash, onClick = { vm.setUseTrash(!state.useTrash) },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Check), "Borrar sin confirmación de Android",
                        when {
                            android.os.Build.VERSION.SDK_INT < 31 -> "Necesita Android 12 o posterior."
                            state.manageMedia -> "Lumi borra y mueve sin que Android pregunte cada vez. Toca para cambiarlo."
                            else -> "Android pregunta cada vez que borras o mueves. Toca y elige «Permitir» para que deje de hacerlo."
                        },
                        value = if (android.os.Build.VERSION.SDK_INT < 31) null else if (state.manageMedia) "Activado" else "Desactivado",
                        words = "permiso preguntar aviso confirmar gestion multimedia",
                        onClick = { com.lumi.galeria.openManageMedia(context) },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Delete), "Papelera",
                        if (state.trashed.isEmpty()) "Vacía" else "${countText(state.trashed.size, "elemento", "elementos")} · ${formatSize(state.trashed.sumOf { it.size })}",
                        onClick = { vm.open(Screen.Trash) }, link = true,
                    )
                    Item(
                        Glyph.Icon(SdCardIcon), "Importar de una cámara o memoria", "Copia lo nuevo de una cámara, una tarjeta o una memoria USB, sin duplicar",
                        words = "importar camara usb tarjeta pasar fotos reflex", onClick = { vm.open(Screen.Import) }, link = true,
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Refresh), "Copia de seguridad",
                        when {
                            state.backup.destination == null -> "A tarjeta SD, memoria USB o una carpeta"
                            state.backup.pending == 0 -> "Todo copiado en ${state.backup.destination}"
                            else -> "${countText(state.backup.pending, "archivo", "archivos")} sin copiar"
                        },
                        words = "tarjeta usb", onClick = { vm.open(Screen.Backup) }, link = true,
                    )
                }

                Group("Análisis de las fotos", words = "analisis buscar reconocer texto personas caras agrupar") {
                    Item(
                        Glyph.Icon(Icons.Filled.Face), "Buscar caras",
                        when {
                            !state.facesOn -> "En pausa. Las personas y sus nombres se quedan; actívalo para seguir donde iba."
                            state.facesLooked < state.facesTotal -> "Buscando caras: ${state.facesLooked} de ${state.facesTotal} fotos con gente"
                            else -> "Junta las fotos de cada persona. En el teléfono: ninguna cara sale de él."
                        },
                        words = "personas caras rostros gente agrupar pausar", toggle = state.facesOn, onClick = { vm.setFacesOn(!state.facesOn) },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Delete), "Borrar todo lo de las caras",
                        "Personas, nombres y grupos. No se puede deshacer.",
                        words = "borrar caras personas", onClick = { picker = Picker.FORGET_FACES },
                    )
                    if (state.facesOn && state.hiddenPeople > 0) {
                        Item(
                            Glyph.Icon(Icons.Filled.Face), "Personas ocultas", countText(state.hiddenPeople, "persona oculta", "personas ocultas"),
                            words = "personas ocultas", onClick = { vm.showHiddenPeople() }, value = "Mostrar",
                        )
                    }
                    Item(
                        Glyph.Icon(Icons.Filled.Search),
                        when {
                            state.photoCount == 0 -> "Sin fotos que analizar"
                            state.unindexed > 0 -> "Mirando qué hay en cada foto: ${state.photoCount - state.unindexed} de ${state.photoCount}"
                            state.deepPending > 0 -> "Leyendo el texto de las fotos: faltan ${state.deepPending}"
                            else -> "Todo analizado"
                        },
                        "Sirve para buscar, para los álbumes de cosas y viajes y para detectar repetidas. Se hace en el teléfono, despacio, y se detiene mientras tocas la pantalla.",
                        words = "analisis buscar reconocer texto",
                    )
                }

                Group("Cámara", words = "camara foto telefono lumi qr preguntar mejorar auto sonido selfie espejo original") {
                    Item(
                        Glyph.Icon(Icons.Filled.Face), "Al tocar la cámara", value = vm.cameraChoice.label,
                        words = "camara telefono lumi preguntar predeterminada", onClick = { picker = Picker.CAMERA },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Face), "Usar Lumi como cámara del teléfono",
                        "Toca, elige «Cámara Lumi» y «Siempre». Si se abre otra cámara sin preguntar, quita su opción predeterminada en los ajustes de Android.",
                        words = "predeterminada camara telefono sistema siempre", onClick = {
                            runCatching { context.startActivity(android.content.Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
                        }, link = true,
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Star), "Icono «Cámara Lumi»", "Un segundo icono en tus apps que abre la cámara al instante. Es la misma app.",
                        words = "icono cajon acceso directo", toggle = cameraIcon,
                        onClick = {
                            cameraIcon = !cameraIcon
                            com.lumi.galeria.setCameraIcon(context, cameraIcon)
                        },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Star), "Lumi Auto", "Aclara, da contraste, color y nitidez a cada foto de la Cámara Lumi al guardarla",
                        words = "mejorar aclarar oscura auto calidad", toggle = vm.lumiAuto,
                        onClick = { vm.setCameraOption(com.lumi.galeria.LumiViewModel.KEY_LUMI_AUTO, !vm.lumiAuto) },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Star), "Guardar también la original", "Además de la mejorada, la foto tal como sale del sensor",
                        words = "original copia sensor", toggle = vm.keepOriginal,
                        onClick = { vm.setCameraOption(com.lumi.galeria.LumiViewModel.KEY_KEEP_ORIGINAL, !vm.keepOriginal) },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Check), "Sonido al disparar", null,
                        words = "sonido disparo obturador", toggle = vm.shutterSound,
                        onClick = { vm.setCameraOption(com.lumi.galeria.LumiViewModel.KEY_SHUTTER_SOUND, !vm.shutterSound) },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Face), "Selfies como en un espejo", "Si no, salen como te ven los demás",
                        words = "selfie espejo frontal invertir", toggle = vm.mirrorSelfie,
                        onClick = { vm.setCameraOption(com.lumi.galeria.LumiViewModel.KEY_MIRROR, !vm.mirrorSelfie) },
                    )
                }

                Group("Acerca de", words = "acerca version lumi gallery privacidad politica contacto correo internet funciones recorrido") {
                    Item(
                        Glyph.Icon(Icons.Filled.Search), "Descubre Lumi", "${state.discovered.size} de ${com.lumi.galeria.data.FEATURES.size} funciones descubiertas: búscalas y pruébalas",
                        words = "descubrir funciones catalogo consejos probar", onClick = { vm.open(Screen.Discover) }, link = true,
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Info), "Estado de Lumi", "Cuánto ocupa, qué ha mirado, limpiar la caché y volver a analizar",
                        words = "estado espacio cache ocupa analizar reindexar fallo informe error", onClick = { vm.open(Screen.Status) }, link = true,
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Info), "Volver a ver los consejos", "Los «¿Sabías que…?» y los globos de ayuda de cada pantalla",
                        words = "consejos ayuda globos sabias", onClick = { vm.resetCoach(); vm.say("Verás de nuevo los consejos") },
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Info), "Todo lo que puede hacer Lumi", "El recorrido por sus funciones, otra vez",
                        words = "funciones recorrido ayuda novedades", onClick = { vm.open(Screen.Tour) }, link = true,
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Info), "Lumi Gallery $version",
                        "Sin anuncios, sin cuenta y sin permiso de internet: tus fotos no salen del teléfono.",
                        words = "version",
                    )
                    Item(
                        Glyph.Icon(Icons.AutoMirrored.Filled.ExitToApp), "Política de privacidad", "Se abre en el navegador",
                        words = "privacidad politica",
                        onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(POLICY))) } },
                        link = true,
                    )
                    Item(
                        Glyph.Icon(Icons.Filled.Email), "Contacto", CONTACT, words = "correo ayuda",
                        onClick = {
                            clipboard.setText(AnnotatedString(CONTACT))
                            vm.say("Correo copiado")
                        },
                    )
                }

                if (query.isNotBlank()) {
                    Text("Si no está aquí, borra lo escrito para ver todos los ajustes.", style = SmallStyle, modifier = Modifier.padding(8.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    when (picker) {
        Picker.THEME -> ChoiceDialog("Tema", ThemeMode.entries, state.theme, { it.label }, { vm.setTheme(it); picker = null }) { picker = null }
        Picker.LANGUAGE -> ChoiceDialog("Idioma", AppLanguage.entries, state.language, { it.label }, { vm.setLanguage(it); picker = null }) { picker = null }
        Picker.ORDER -> ChoiceDialog(
            "Orden de la pantalla Fotos", listOf(false, true), state.oldestFirst,
            { if (it) "Antiguas primero" else "Recientes primero" }, { vm.setOldestFirst(it); picker = null },
        ) { picker = null }
        Picker.LOCK -> ChoiceDialog("Cómo se desbloquea", LockMethod.entries, state.lockMethod, { it.label }, { method ->
            picker = null
            if (method != state.lockMethod) {
                // Cambiar de método exige pasar antes por el actual. Si el teléfono no tiene bloqueo,
                // no hay nada que comprobar todavía.
                if (method == LockMethod.PIN && !actions.canUnlock()) vm.createPin()
                else actions.unlock { if (method == LockMethod.PIN) vm.createPin() else vm.useSystemLock() }
            }
        }) { picker = null }
        Picker.DECOY -> ChoiceDialog("PIN señuelo", listOf(true, false), null, { if (it) "Cambiar el PIN señuelo" else "Quitar el PIN señuelo" }, { change ->
            picker = null
            actions.unlock { if (change) vm.createDecoyPin() else vm.removeDecoyPin() }
        }) { picker = null }
        Picker.TEXT_SIZE -> ChoiceDialog("Tamaño de letra", listOf(0.9f, 1f, 1.15f, 1.3f), vm.textScale, { textSizeName(it) }, { vm.chooseTextScale(it); picker = null }) { picker = null }
        Picker.RECENTS -> ChoiceDialog("Ocultar en apps recientes", com.lumi.galeria.RecentsHide.entries, vm.recentsHide, { it.label }, { vm.chooseRecentsHide(it); picker = null }) { picker = null }
        Picker.MEMORY_HOUR -> ChoiceDialog("Hora del aviso", listOf(7, 8, 9, 10, 12, 20), memoryHour, { "$it:00" }, { setMemory(true, it); picker = null }) { picker = null }
        Picker.CAMERA -> ChoiceDialog("Al tocar la cámara", com.lumi.galeria.CameraChoice.entries, vm.cameraChoice, { it.label }, { vm.chooseCamera(it); picker = null }) { picker = null }
        Picker.FORGET_FACES -> AlertDialog(
            onDismissRequest = { picker = null },
            containerColor = Lumi.Surface,
            title = { Text("¿Borrar todo lo de las caras?", style = HeadingStyle) },
            text = {
                Text(
                    "Se borran ${countText(state.people.size, "persona", "personas")}, sus nombres y los grupos. Las fotos no se tocan. Si solo quieres que deje de buscar, usa la pausa.",
                    style = SmallStyle.copy(fontSize = 15.sp, color = Lumi.Ink),
                )
            },
            confirmButton = {
                Text("Borrar", style = LabelStyle, color = Lumi.Danger, modifier = Modifier.clip(CircleShape).clickable { picker = null; vm.clearFaces() }.padding(12.dp))
            },
            dismissButton = {
                Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { picker = null }.padding(12.dp))
            },
        )
        Picker.COLOR -> AlertDialog(
            onDismissRequest = { picker = null },
            containerColor = Lumi.Surface,
            title = { Text("Color", style = HeadingStyle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccentColor.entries.forEach { option ->
                        val tone = if (Lumi.dark) option.dark else option.light
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.setAccent(option); picker = null }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(tone))
                            Text(option.label, style = LabelStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                            if (option == state.accent) Icon(Icons.Filled.Check, null, tint = Lumi.Accent)
                        }
                    }
                }
            },
            confirmButton = {},
        )
        null -> Unit
    }
}

/** El dibujo de la izquierda de cada fila. */
private sealed interface Glyph {
    data class Icon(val vector: ImageVector) : Glyph
    data class Dot(val color: Color) : Glyph
    data class Letters(val text: String) : Glyph
    data object Half : Glyph
}

/** Lo escrito en el buscador de ajustes, ya sin tildes ni mayúsculas. Vacío si no se busca nada. */
private val LocalSettingsQuery = compositionLocalOf { "" }

/** Si lo que se busca aparece en [words]. Sin búsqueda, todo se enseña. */
@Composable
private fun matches(words: String): Boolean {
    val query = LocalSettingsQuery.current
    // Se busca en español y, si la app está en inglés, también en lo que se ve en pantalla.
    return query.isEmpty() || normalize(words).contains(query) || normalize(tr(words)).contains(query)
}

/**
 * Un grupo de ajustes: título y una tarjeta con sus filas. [words] reúne lo que encuentra el
 * buscador en todo el grupo; si no hay nada, el grupo entero desaparece.
 */
@Composable
private fun Group(title: String, words: String, content: @Composable () -> Unit) {
    if (!matches("$title $words")) return
    val color = groupColor(title)
    Text(title, style = SmallStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = Lumi.Muted, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Lumi.Surface)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGroupColor provides color) { content() }
    }
}

/** El color de los iconos de cada sección de ajustes. */
private val LocalGroupColor = compositionLocalOf { Color(0xFF7C5CFF) }

/** Cada sección con su color, para encontrar las cosas de un vistazo. */
private fun groupColor(title: String): Color = when (title) {
    "Apariencia" -> Color(0xFF7C5CFF)
    "Fotos y álbumes" -> Color(0xFF3D7BF5)
    "Privacidad y seguridad" -> Color(0xFF2FA37A)
    "Almacenamiento" -> Color(0xFFE59A3B)
    "Análisis de las fotos" -> Color(0xFF3BA3B5)
    "Cámara" -> Color(0xFFE5709B)
    "Accesibilidad" -> Color(0xFF5B6B8C)
    "Fondo y avisos" -> Color(0xFFB0832F)
    "Acerca de" -> Color(0xFF8A8799)
    else -> listOf(Color(0xFF7C5CFF), Color(0xFF3D7BF5), Color(0xFF2FA37A), Color(0xFFE59A3B), Color(0xFFE5709B))[(title.hashCode() and 0x7fffffff) % 5]
}

/**
 * Una fila de ajustes. A la derecha lleva un interruptor ([toggle]), el valor actual ([value]) o,
 * si lleva a otra pantalla ([link]), una flecha. Al buscar, solo se ven las filas que coinciden.
 */
@Composable
private fun Item(
    glyph: Glyph,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    words: String = "",
    toggle: Boolean? = null,
    link: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    if (!matches("$title ${subtitle.orEmpty()} $words")) return
    val tone = LocalGroupColor.current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(press, 0.98f).clickable(press, androidx.compose.material3.ripple(), onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // El icono en blanco sobre un círculo del color de la sección.
        Box(Modifier.size(36.dp).clip(CircleShape).background(tone), contentAlignment = Alignment.Center) {
            when (glyph) {
                is Glyph.Icon -> Icon(glyph.vector, null, Modifier.size(19.dp), tint = Color.White)
                is Glyph.Dot -> Box(Modifier.size(16.dp).clip(CircleShape).background(glyph.color).border(2.dp, Color.White, CircleShape))
                is Glyph.Letters -> Text(glyph.text, style = LabelStyle.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Glyph.Half -> Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f))) {
                    Box(Modifier.size(width = 9.dp, height = 18.dp).background(Color.White))
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = LabelStyle.copy(fontSize = 15.sp))
            if (subtitle != null) Text(subtitle, style = SmallStyle)
        }
        when {
            toggle != null -> Switch(
                checked = toggle,
                onCheckedChange = { onClick?.invoke() },
                colors = SwitchDefaults.colors(checkedTrackColor = Lumi.Accent, checkedThumbColor = Lumi.OnAccent),
            )
            value != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = LabelStyle, color = Lumi.Muted)
                if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = Lumi.Muted)
            }
            link -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = Lumi.Muted)
        }
    }
    HorizontalDivider(Modifier.padding(start = 64.dp), color = Lumi.Bg, thickness = 1.dp)
}

/** Ventana pequeña para elegir una opción entre varias; la actual lleva una marca. */
@Composable
internal fun <T> ChoiceDialog(title: String, options: List<T>, current: T?, label: (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text(title, style = HeadingStyle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(option) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label(option), style = LabelStyle.copy(fontSize = 15.sp), color = if (option == current) Lumi.Accent else Lumi.Ink, modifier = Modifier.weight(1f))
                        if (option == current) Icon(Icons.Filled.Check, null, tint = Lumi.Accent)
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/** Cuánto ocupan las fotos, cómo se reparte y cuánto se puede recuperar. Un toque lleva a liberar espacio. */
@Composable
private fun UsageCard(count: Int, usage: LongArray, recoverable: Long, onClick: () -> Unit) {
    val total = usage.sum()
    val colors = listOf(Lumi.Accent, Lumi.Accent.copy(alpha = 0.5f), Lumi.Muted)
    val names = listOf("Fotos", "Vídeos", "Capturas")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Lumi.Surface).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatSize(rollingNumber(total.toDouble()).toLong()), style = TitleStyle.copy(fontSize = 26.sp), modifier = Modifier.weight(1f))
            Text(countText(count, "elemento", "elementos"), style = SmallStyle.copy(fontSize = 13.sp))
        }
        if (total > 0) {
            Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                usage.forEachIndexed { index, bytes ->
                    if (bytes > 0) Box(Modifier.weight(bytes.toFloat() / total).height(10.dp).background(colors[index]))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                usage.forEachIndexed { index, bytes ->
                    if (bytes > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(colors[index]))
                            Text("${names[index]} ${formatSize(bytes)}", style = SmallStyle)
                        }
                    }
                }
            }
        }
        Text(
            if (recoverable > 0) "Puedes recuperar ${formatSize(recoverable)}. Toca para ver cómo." else "Toca para revisar repetidas, vídeos grandes y capturas antiguas.",
            style = LabelStyle,
            color = Lumi.Accent,
        )
    }
}

private fun textSizeName(scale: Float): String = when {
    scale < 0.95f -> tr("Pequeña")
    scale < 1.05f -> tr("Normal")
    scale < 1.2f -> tr("Grande")
    else -> tr("Muy grande")
}
