package com.lumi.galeria

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.util.Rational
import androidx.compose.foundation.layout.Row
import android.view.MotionEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lumi.galeria.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.data.Quiet
import com.lumi.galeria.data.copyWithoutLocation
import com.lumi.galeria.data.deleteRequest
import com.lumi.galeria.data.trashRequest
import com.lumi.galeria.data.writeRequest
import com.lumi.galeria.ui.Actions
import com.lumi.galeria.ui.AlbumScreen
import com.lumi.galeria.ui.AlbumsScreen
import com.lumi.galeria.ui.AnyItemsScreen
import com.lumi.galeria.ui.BackupScreen
import com.lumi.galeria.ui.EditorScreen
import com.lumi.galeria.ui.FavoritesScreen
import com.lumi.galeria.ui.GridLink
import com.lumi.galeria.ui.LabelStyle
import com.lumi.galeria.ui.Lumi
import com.lumi.galeria.ui.LumiTheme
import com.lumi.galeria.ui.CollageScreen
import com.lumi.galeria.ui.HiddenFoldersScreen
import com.lumi.galeria.ui.CompareScreen
import com.lumi.galeria.ui.CutoutScreen
import com.lumi.galeria.ui.MarkupScreen
import com.lumi.galeria.ui.NewAlbumScreen
import com.lumi.galeria.ui.PillButton
import com.lumi.galeria.ui.PinOverlay
import com.lumi.galeria.ui.ReviewScreen
import com.lumi.galeria.ui.SearchScreen
import com.lumi.galeria.ui.SettingsScreen
import com.lumi.galeria.ui.SmallStyle
import com.lumi.galeria.ui.SpaceScreen
import com.lumi.galeria.ui.StackScreen
import com.lumi.galeria.ui.TimelineScreen
import com.lumi.galeria.ui.TitleStyle
import com.lumi.galeria.ui.TrashScreen
import com.lumi.galeria.ui.VaultScreen
import com.lumi.galeria.ui.ViewerScreen

// La petición de huella del sistema necesita este tipo de actividad.
class MainActivity : FragmentActivity() {
    private val vm: LumiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        publishShortcuts()
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            Lang.apply(state.language)
            LumiTheme(state.theme, state.accent, state.pureBlack) {
                Box {
                    LumiRoot(vm, state)
                    // La huella sirve de atajo para no teclear el PIN propio.
                    PinOverlay(state, vm, onBiometric = if (canUseBiometrics()) ({ authenticate(biometricOnly = true) { vm.pinBypassed() } }) else null)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onPermission(hasMediaAccess(this), hasOnlyPartialAccess(this), granted(this, Manifest.permission.ACCESS_MEDIA_LOCATION))
    }

    override fun onStop() {
        super.onStop()
        // Al salir de la app se cierra lo privado; no al girar la pantalla.
        if (!isChangingConfigurations) vm.onLeave()
    }

    /** Deja el vídeo en una ventana pequeña que flota sobre lo demás. */
    fun floatVideo() {
        runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build()) }
            .onFailure { vm.say("Este teléfono no permite ventanas flotantes") }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.setPip(isInPictureInPictureMode)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Mientras el usuario toca la pantalla, el análisis de fotos espera.
        Quiet.touched()
        return super.dispatchTouchEvent(event)
    }

    /** Iconos de la barra de estado claros u oscuros según el fondo que tengan debajo. */
    fun styleBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(style, style)
    }

    fun hasPhoneLock(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun canUseBiometrics(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Pide al sistema que compruebe quién eres. Con [biometricOnly] solo vale huella o cara;
     * si no, también el PIN, patrón o contraseña del teléfono.
     */
    fun authenticate(biometricOnly: Boolean = false, onOk: () -> Unit) {
        val allowed = if (biometricOnly) BIOMETRIC_WEAK else BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            vm.say("Pon un bloqueo de pantalla en el teléfono, o usa un PIN propio de Lumi desde Ajustes")
            return
        }
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Lumi Gallery")
                .setSubtitle(tr(if (biometricOnly) "Usa tu huella o tu cara" else "Usa tu huella, tu cara o el bloqueo del teléfono"))
                .setAllowedAuthenticators(allowed)
                .apply { if (biometricOnly) setNegativeButtonText(tr("Usar el PIN")) }
                .build(),
        )
    }

    /**
     * Atajos al mantener pulsado el icono de Lumi en el inicio del teléfono. Se publican cada vez
     * que se abre la app, así siguen el idioma elegido.
     */
    private fun publishShortcuts() {
        val manager = getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
        Lang.apply(vm.state.value.language)
        fun shortcut(id: String, label: String, icon: Int) = android.content.pm.ShortcutInfo.Builder(this, id)
            .setShortLabel(tr(label))
            .setIcon(android.graphics.drawable.Icon.createWithResource(this, icon))
            .setIntent(Intent(this, MainActivity::class.java).setAction("$SHORTCUT$id"))
            .build()
        runCatching {
            manager.dynamicShortcuts = listOf(
                shortcut("buscar", "Buscar", R.drawable.ic_shortcut_search),
                shortcut("camara", "Hacer una foto", R.drawable.ic_shortcut_camera),
                shortcut("privada", "Carpeta privada", R.drawable.ic_shortcut_lock),
                shortcut("espacio", "Liberar espacio", R.drawable.ic_shortcut_space),
            )
        }
    }

    /** Otra app puede pedir que se abra un archivo o que el usuario elija uno. */
    private fun handle(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                val type = intent.type ?: runCatching { contentResolver.getType(uri) }.getOrNull().orEmpty()
                vm.openExternal(uri, type.startsWith("video"))
            }
            Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK -> {
                val type = intent.type.orEmpty()
                vm.setPick(
                    PickMode(
                        multiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false),
                        images = !type.startsWith("video"),
                        videos = !type.startsWith("image"),
                    ),
                )
            }
            "${SHORTCUT}buscar" -> {
                vm.setPick(null)
                vm.switchTab(Screen.Timeline)
                vm.openSearch()
            }
            "${SHORTCUT}camara" -> {
                vm.setPick(null)
                runCatching { startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
            }
            "${SHORTCUT}privada" -> {
                vm.setPick(null)
                vm.switchTab(Screen.Albums)
                vm.openVault { onOk -> authenticate(onOk = onOk) }
            }
            "${SHORTCUT}espacio" -> {
                vm.setPick(null)
                vm.switchTab(Screen.Timeline)
                vm.open(Screen.Space)
            }
            MemoryWidget.ACTION_MEMORY -> {
                vm.setPick(null)
                vm.switchTab(Screen.Timeline)
                vm.open(Screen.Items(intent.getStringExtra(MemoryWidget.EXTRA_TITLE) ?: tr("Recuerdos"), Source.Auto("recuerdo")))
            }
            else -> vm.setPick(null)
        }
    }
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
}

/** Vale con acceso completo o con el acceso parcial de Android 14 ("solo las fotos que elija"). */
private fun hasMediaAccess(context: Context): Boolean =
    mediaPermissions().any { it != Manifest.permission.ACCESS_MEDIA_LOCATION && granted(context, it) }

private fun hasOnlyPartialAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 34 &&
        granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) &&
        !granted(context, Manifest.permission.READ_MEDIA_IMAGES) &&
        !granted(context, Manifest.permission.READ_MEDIA_VIDEO)

/** Los lotes muy grandes no caben en una sola petición al sistema. */
private const val MAX_BATCH = 1000

@Composable
private fun LumiRoot(vm: LumiViewModel, state: UiState) {
    val context = LocalContext.current
    val timelineGrid = rememberLazyGridState()
    val link = remember { GridLink() }

    fun refreshAccess() = vm.onPermission(
        hasMediaAccess(context), hasOnlyPartialAccess(context), granted(context, Manifest.permission.ACCESS_MEDIA_LOCATION),
    )

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshAccess()
        vm.reload()
    }
    val afterRequest = remember { mutableStateOf<(() -> Unit)?>(null) }
    val senderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            afterRequest.value?.invoke()
            vm.reload()
        }
        afterRequest.value = null
    }

    val actions = remember {
        fun ask(items: List<MediaItem>, onDone: () -> Unit, request: (List<MediaItem>) -> IntentSender) {
            val real = items.filter { !it.isExternal }.take(MAX_BATCH)
            if (real.isEmpty()) return
            runCatching {
                afterRequest.value = onDone
                senderLauncher.launch(IntentSenderRequest.Builder(request(real)).build())
            }
        }
        Actions(
            trash = { items, onDone ->
                if (vm.state.value.useTrash) {
                    // Tras mandar algo a la papelera se ofrece deshacerlo unos segundos.
                    ask(items, { onDone(); vm.offerUndo(items.filter { !it.isExternal }) }) { trashRequest(context, it, true) }
                } else {
                    // Sin papelera: se borra para siempre y no hay nada que deshacer.
                    ask(items, onDone) { deleteRequest(context, it) }
                }
            },
            restore = { items -> ask(items, {}) { trashRequest(context, it, false) } },
            deleteForever = { items -> ask(items, {}) { deleteRequest(context, it) } },
            write = { items, onGranted -> ask(items, onGranted) { writeRequest(context, it) } },
            share = { items -> share(context, items.map { it.uri }, items.all { it.isVideo }, items.none { it.isVideo }) },
            shareUris = { uris -> share(context, uris, false, true) },
            shareWithoutLocation = { item ->
                val clean = copyWithoutLocation(context, item)
                if (clean != null) share(context, listOf(clean), false, true) else vm.say("No se pudo preparar la copia")
            },
            useAs = { item ->
                val intent = Intent(Intent.ACTION_ATTACH_DATA)
                    .setDataAndType(item.uri, "image/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(Intent.createChooser(intent, tr("Usar como"))) }
            },
            requestAccess = { permissionLauncher.launch(mediaPermissions()) },
            openSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            pickResult = { items ->
                val activity = context as Activity
                val result = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (items.size == 1) {
                    result.setData(items[0].uri)
                } else {
                    result.clipData = ClipData.newUri(context.contentResolver, "Lumi", items[0].uri).apply {
                        items.drop(1).forEach { addItem(ClipData.Item(it.uri)) }
                    }
                }
                activity.setResult(Activity.RESULT_OK, result)
                activity.finish()
            },
            unlock = { onOk -> if (vm.usesPin) vm.askPin(onOk) else (context as MainActivity).authenticate(onOk = onOk) },
            canUnlock = { vm.usesPin || (context as MainActivity).hasPhoneLock() },
            allFiles = {
                val app = Uri.fromParts("package", context.packageName, null)
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, app)) }
                    .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
            },
            pip = { (context as MainActivity).floatVideo() },
            camera = {
                runCatching { context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
                    .onFailure { vm.say("No se encontró una app de cámara") }
            },
        )
    }

    if (state.locked) {
        LockScreen { actions.unlock { vm.unlockApp() } }
        return
    }

    val top = vm.backStack.last()
    val viewer = top as? Screen.Viewer

    // Con el visor abierto la pantalla es oscura sea cual sea el tema.
    val darkBars = Lumi.dark || viewer != null
    LaunchedEffect(darkBars) { (context as MainActivity).styleBars(darkBars) }

    // Un archivo que llega de otra app se puede ver sin haber dado acceso a la galería.
    if (!state.hasPermission && viewer?.source !is Source.External) {
        PermissionScreen(onGrant = actions.requestAccess, onSettings = actions.openSettings)
        return
    }

    // El visor se pinta encima de la pantalla desde la que se abrió, que sigue viva debajo.
    val base = if (viewer == null) top else vm.backStack.lastOrNull { it !is Screen.Viewer } ?: Screen.Timeline

    BackHandler(vm.backStack.size > 1 || base != Screen.Timeline) {
        if (!vm.back()) vm.switchTab(Screen.Timeline)
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Lumi.Bg)) {
        // Tableta o plegable abierto: panel fijo a la izquierda y más columnas en las cuadrículas.
        val wide = maxWidth >= com.lumi.galeria.ui.WIDE_FROM && maxHeight >= 480.dp
        val pane = wide && !com.lumi.galeria.ui.isImmersive(base)
        val room = if (pane) maxWidth - com.lumi.galeria.ui.SIDE_PANE else maxWidth
        androidx.compose.runtime.CompositionLocalProvider(
            com.lumi.galeria.ui.LocalWide provides pane,
            com.lumi.galeria.ui.LocalColumnScale provides (room / 400.dp).toInt().coerceIn(1, 3),
        ) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                if (pane) com.lumi.galeria.ui.SidePane(state, vm, actions, base)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (state.hasPermission) {
                        // Al entrar en una pantalla llega desde la derecha; al volver, se va por la derecha. Entre
                        // Fotos y Álbumes se desliza hacia el lado de la pestaña.
                        val depth = vm.backStack.count { it !is Screen.Viewer }
                        AnimatedContent(
                            targetState = Shown(base, depth),
                            contentKey = { it.screen },
                            transitionSpec = {
                                val forward = when {
                                    targetState.depth != initialState.depth -> targetState.depth > initialState.depth
                                    else -> targetState.screen == Screen.Albums
                                }
                                val sign = if (forward) 1 else -1
                                (slideInHorizontally(tween(260)) { sign * it / 4 } + fadeIn(tween(260)))
                                    .togetherWith(slideOutHorizontally(tween(260)) { -sign * it / 6 } + fadeOut(tween(180)))
                            },
                            label = "pantallas",
                        ) { (shown, _) ->
                                when (shown) {
                                    Screen.Timeline -> TimelineScreen(state, vm, actions, timelineGrid, link)
                                    Screen.Albums -> AlbumsScreen(state, vm, actions)
                                    Screen.Search -> SearchScreen(state, vm, link)
                                    Screen.Space -> SpaceScreen(state, vm, actions)
                                    Screen.Favorites -> FavoritesScreen(state, vm, actions, link)
                                    Screen.Trash -> TrashScreen(state, vm, actions)
                                    Screen.Vault -> VaultScreen(state, vm)
                                    Screen.Backup -> BackupScreen(state, vm)
                                    Screen.Settings -> SettingsScreen(state, vm, actions)
                                    is Screen.Album -> AlbumScreen(shown, state, vm, actions, link)
                                    is Screen.NewAlbum -> NewAlbumScreen(shown, state, vm, actions)
                                    is Screen.Items -> AnyItemsScreen(shown, state, vm, actions, link)
                                    is Screen.StackView -> StackScreen(shown, state, vm, actions)
                                    is Screen.Review -> ReviewScreen(shown, state, vm, actions)
                                    is Screen.Editor -> EditorScreen(shown, state, vm, actions)
                                    is Screen.Markup -> MarkupScreen(shown, state, vm)
                                    is Screen.Collage -> CollageScreen(shown, state, vm)
                                    is Screen.Pdf -> com.lumi.galeria.ui.PdfScreen(shown, state, vm)
                                    is Screen.Gif -> com.lumi.galeria.ui.GifScreen(shown, state, vm)
                                    is Screen.Wallpaper -> com.lumi.galeria.ui.WallpaperScreen(shown, state, vm)
                                    is Screen.Rotate -> com.lumi.galeria.ui.RotateScreen(shown, state, vm, actions)
                                    Screen.AlbumOrder -> com.lumi.galeria.ui.AlbumOrderScreen(state, vm)
                                    is Screen.StoryView -> com.lumi.galeria.ui.StoryScreen(shown, state, vm)
                                    Screen.SwipeReview -> com.lumi.galeria.ui.SwipeScreen(state, vm, actions)
                                    Screen.Import -> com.lumi.galeria.ui.ImportScreen(state, vm)
                                    is Screen.YearReview -> com.lumi.galeria.ui.YearReviewScreen(shown, state, vm)
                                    is Screen.VideoEditor -> com.lumi.galeria.ui.VideoEditorScreen(shown, state, vm)
                                    Screen.People -> com.lumi.galeria.ui.PeopleScreen(state, vm)
                                    is Screen.Animate -> com.lumi.galeria.ui.AnimateScreen(shown, state, vm)
                                    is Screen.Export -> com.lumi.galeria.ui.ExportScreen(shown, state, vm, actions)
                                    is Screen.MemoryVideo -> com.lumi.galeria.ui.MemoryVideoScreen(shown, state, vm)
                                    Screen.Screenshots -> com.lumi.galeria.ui.ScreenshotsScreen(state, vm)
                                    is Screen.Portrait -> com.lumi.galeria.ui.PortraitScreen(shown, state, vm)
                                    is Screen.Person -> com.lumi.galeria.ui.PersonScreen(shown, state, vm, actions, link)
                                    is Screen.Compare -> CompareScreen(shown, state, vm, actions)
                                    is Screen.Cutout -> CutoutScreen(shown, state, vm)
                                    Screen.HiddenFolders -> HiddenFoldersScreen(state, vm, actions)
                                    is Screen.Viewer -> Unit
                                }
                        }
                    }
                }
            }
        }
        if (viewer != null) ViewerScreen(viewer, state, vm, actions, link)

        if (state.undo.isNotEmpty() && !state.pip) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 136.dp)
                    .clip(CircleShape)
                    .background(Lumi.Ink)
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(countText(state.undo.size, "enviada a la papelera", "enviadas a la papelera"), style = LabelStyle, color = Lumi.Bg)
                Text(
                    "Deshacer",
                    style = LabelStyle,
                    color = Lumi.Accent,
                    modifier = Modifier.clip(CircleShape).clickable {
                        actions.restore(state.undo)
                        vm.clearUndo()
                    }.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }

        vm.undoText?.takeIf { state.undo.isEmpty() && !state.pip }?.let { text ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 136.dp)
                    .clip(CircleShape)
                    .background(Lumi.Ink)
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text, style = LabelStyle, color = Lumi.Bg)
                Text(
                    "Deshacer",
                    style = LabelStyle,
                    color = Lumi.Accent,
                    modifier = Modifier.clip(CircleShape).clickable { vm.runUndo() }.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }

        state.message?.let { message ->
            Text(
                message,
                style = LabelStyle,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 84.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Lumi.Ink)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                color = Lumi.Bg,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun share(context: Context, uris: List<Uri>, allVideo: Boolean, allImages: Boolean) {
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }
    intent.setType(if (allVideo) "video/*" else if (allImages) "image/*" else "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // El selector necesita ver la dirección para poder ceder el permiso de lectura.
    intent.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

/** Con "pedir huella al abrir" activado, esto es lo único que se ve hasta desbloquear. */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().background(Lumi.Bg).safeDrawingPadding().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Lumi está bloqueada", style = TitleStyle.copy(fontSize = 28.sp), textAlign = TextAlign.Center)
        Text("Usa tu huella o el bloqueo del teléfono para ver tus fotos.", style = SmallStyle.copy(fontSize = 15.sp), textAlign = TextAlign.Center)
        PillButton("Desbloquear", onUnlock, Modifier.padding(top = 10.dp))
    }
}

/**
 * Lo primero que ve quien instala la app, antes del aviso de permisos de Android: una sola
 * pantalla que dice para qué se pide el acceso y qué no hace Lumi con las fotos.
 */
@Composable
private fun PermissionScreen(onGrant: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Lumi.Bg)
            .safeDrawingPadding()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        // Fotos de muestra que vienen con la app: todavía no se puede enseñar ninguna del teléfono.
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(R.drawable.welcome_1, R.drawable.welcome_2, R.drawable.welcome_3).forEachIndexed { index, picture ->
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(picture),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = if (index == 1) 18.dp else 0.dp)
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(20.dp)),
                )
            }
        }
        Text("Tus fotos se quedan en tu teléfono", style = TitleStyle.copy(fontSize = 32.sp, lineHeight = 35.sp))
        Text(
            "Lumi necesita verlas para enseñártelas, ordenarlas y dejarte buscarlas. Nada más.",
            style = SmallStyle.copy(fontSize = 16.sp, lineHeight = 23.sp),
        )
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                "Sin permiso de internet: nada sale de aquí",
                "Sin cuenta, sin anuncios y sin suscripción",
                "Candado con huella o PIN, si lo quieres",
            ).forEach { fact ->
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Lumi.Accent), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Filled.Check, null,
                            Modifier.size(14.dp), tint = Lumi.OnAccent,
                        )
                    }
                    Text(fact, style = LabelStyle.copy(fontSize = 15.sp))
                }
            }
        }
        PillButton("Dar acceso a mis fotos", onGrant, Modifier.fillMaxWidth().padding(top = 14.dp))
        Text(
            "Si ya lo rechazaste, ábrelo desde los ajustes",
            style = SmallStyle.copy(fontSize = 13.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable(onClick = onSettings).padding(10.dp),
        )
    }
}

/** Las acciones de los atajos del icono empiezan así. */
private const val SHORTCUT = "com.lumi.galeria.ATAJO_"

/** La pantalla que se ve y cuántas hay debajo: así se sabe si se entra o se vuelve. */
private data class Shown(val screen: Screen, val depth: Int)
