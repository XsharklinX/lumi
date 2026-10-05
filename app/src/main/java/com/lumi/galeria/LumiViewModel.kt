package com.lumi.galeria

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.galeria.data.Album
import com.lumi.galeria.data.AlbumSort
import com.lumi.galeria.data.ItemSort
import com.lumi.galeria.data.renameItem
import com.lumi.galeria.data.replaceOriginal
import com.lumi.galeria.data.sortItems
import com.lumi.galeria.data.Analyzer
import com.lumi.galeria.data.AutoAlbum
import com.lumi.galeria.data.Backup
import com.lumi.galeria.data.FILES_URI
import com.lumi.galeria.data.HiddenFolder
import com.lumi.galeria.data.findDuplicates
import com.lumi.galeria.data.saveNewPhoto
import com.lumi.galeria.data.savePng
import com.lumi.galeria.data.CONCEPTS
import com.lumi.galeria.data.Semantic
import com.lumi.galeria.data.semanticPhrase
import com.lumi.galeria.data.toEnglish
import com.lumi.galeria.data.scanHidden
import com.lumi.galeria.data.trimVideo
import com.lumi.galeria.data.IndexEntry
import com.lumi.galeria.data.Indexer
import com.lumi.galeria.data.MediaItem
import com.lumi.galeria.data.Memory
import com.lumi.galeria.data.PhotoStack
import com.lumi.galeria.data.Quiet
import com.lumi.galeria.data.Topic
import com.lumi.galeria.data.topPlaces
import com.lumi.galeria.data.topThings
import com.lumi.galeria.data.VaultItem
import com.lumi.galeria.data.buildAlbums
import com.lumi.galeria.data.buildMemory
import com.lumi.galeria.data.buildThings
import com.lumi.galeria.data.buildTrips
import com.lumi.galeria.data.copyItems
import com.lumi.galeria.data.externalItem
import com.lumi.galeria.data.loadLibrary
import com.lumi.galeria.data.mediaIdOf
import com.lumi.galeria.data.moveItems
import com.lumi.galeria.data.saveEdited
import com.lumi.galeria.data.saveVideoFrame
import com.lumi.galeria.data.search
import com.lumi.galeria.ui.ThemeMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Level(val columns: Int, val label: String, val thumb: Int) {
    YEAR(6, "Año", 160),
    MONTH(4, "Mes", 320),
    DAY(2, "Día", 512),
}

sealed interface Cell {
    val key: String

    data class Header(override val key: String, val title: String, val count: Int) : Cell

    data class Photo(val item: MediaItem, val stackSize: Int) : Cell {
        override val key get() = "p${item.id}"
    }

    /** Tarjeta de recuerdos que encabeza la cuadrícula cuando hay fotos de estos días en otros años. */
    data class Recall(val memory: Memory) : Cell {
        override val key get() = "recuerdo"
    }
}

sealed interface Source {
    data object Timeline : Source
    data object Favorites : Source
    data class Stack(val bestId: Long) : Source
    data class Album(val bucketId: Long) : Source

    /** Álbum que Lumi arma solo: cosas, viajes o el recuerdo del día. */
    data class Auto(val key: String) : Source
    data class Ids(val ids: Set<Long>) : Source
    data class Search(val query: String) : Source

    /** Una carpeta que el sistema oculta a las galerías. */
    data class Hidden(val path: String) : Source

    /** Un archivo que llega de otra app o que se abre desde la carpeta privada. */
    data class External(val uri: Uri, val isVideo: Boolean) : Source
}

enum class ReviewKind(val title: String, val hint: String, val preselected: Boolean) {
    REPEATED("Repetidas", "Se conserva la mejor toma de cada pila.", true),
    BIG_VIDEOS("Vídeos grandes", "Vídeos de más de 50 MB, del más pesado al más ligero.", true),
    OLD_SCREENSHOTS("Capturas antiguas", "Capturas de pantalla de hace más de 30 días.", true),
    BLURRY("Quizá borrosas", "Pueden estar movidas o desenfocadas. Marca solo las que quieras quitar.", false),
    DUPLICATES("Duplicadas", "El mismo archivo guardado más de una vez. De cada grupo se conserva una copia.", true),
}

sealed interface Screen {
    data object Timeline : Screen
    data object Albums : Screen
    data object Search : Screen
    data object Space : Screen
    data object Favorites : Screen
    data object Trash : Screen
    data object Vault : Screen
    data object Backup : Screen
    data object Settings : Screen
    data class Album(val bucketId: Long) : Screen

    /** Elegir qué fotos van al álbum recién creado. */
    data class NewAlbum(val name: String) : Screen
    data class Items(val title: String, val source: Source) : Screen

    /** [origin] es el hueco de la miniatura desde el que crece la foto al abrirse. */
    data class Viewer(val source: Source, val startId: Long, val origin: Rect? = null) : Screen
    data class StackView(val bestId: Long) : Screen
    data class Review(val kind: ReviewKind) : Screen
    data class Editor(val id: Long) : Screen
    data class Markup(val id: Long) : Screen
    data class Trim(val id: Long) : Screen
    data class Collage(val ids: List<Long>) : Screen
    data class Compare(val first: Long, val second: Long) : Screen
    data class Cutout(val id: Long) : Screen
    data object HiddenFolders : Screen
}

/** Qué se enseña en la pantalla Fotos. */
enum class TileFilter(val label: String) {
    ALL("Todo"), PHOTOS("Fotos"), VIDEOS("Vídeos"), SCREENSHOTS("Capturas"), FAVORITES("Favoritas"), GIFS("GIF"),
}

enum class LockMethod(val label: String) { SYSTEM("Bloqueo del teléfono"), PIN("PIN propio de Lumi") }

/** Qué se le está pidiendo al usuario en el teclado del PIN. */
enum class PinStep { ASK, CREATE, REPEAT }

/** Otra app ha pedido una foto y Lumi hace de selector. */
data class PickMode(val multiple: Boolean, val images: Boolean, val videos: Boolean)

data class BackupState(
    val destination: String? = null,
    val pending: Int = 0,
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val last: Long = 0,
)

data class UiState(
    val hasPermission: Boolean = false,
    /** Android 14: el usuario solo dio acceso a algunas fotos. */
    val partial: Boolean = false,
    val loading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val trashed: List<MediaItem> = emptyList(),
    val favorites: Set<Long> = emptySet(),
    val stackByBest: Map<Long, PhotoStack> = emptyMap(),
    /** Lo que se ve en la cuadrícula: de cada pila solo aparece la mejor toma. */
    val tiles: List<MediaItem> = emptyList(),
    val cells: Map<Level, List<Cell>> = emptyMap(),
    val albums: List<Album> = emptyList(),
    val autoAlbums: List<AutoAlbum> = emptyList(),
    val memory: Memory? = null,
    val blurry: List<MediaItem> = emptyList(),
    /** Lo que se sabe de cada foto: texto, cosas reconocidas y lugar. */
    val index: Map<Long, IndexEntry> = emptyMap(),
    /** Fotos (sin vídeos) que hay en total, y cuántas faltan por pasar cada vuelta del análisis. */
    val topThings: List<Topic> = emptyList(),
    val topPlaces: List<Topic> = emptyList(),
    val photoCount: Int = 0,
    val unindexed: Int = 0,
    val deepPending: Int = 0,
    /** Pedir la huella al abrir la app, y si ahora mismo está bloqueada. */
    val appLock: Boolean = false,
    val locked: Boolean = false,
    val lockMethod: LockMethod = LockMethod.SYSTEM,
    /** Teclado del PIN en pantalla, o null si no se está pidiendo. */
    val pinStep: PinStep? = null,
    val pinError: String? = null,
    /** Los álbumes con candado están abiertos hasta que se salga de la app. */
    val albumsUnlocked: Boolean = false,
    /** Fotos de los álbumes con candado; vacía mientras sigan cerrados. */
    val lockedItems: List<MediaItem> = emptyList(),
    val showHidden: Boolean = false,
    val itemSort: ItemSort = ItemSort.NEWEST,
    val albumSort: AlbumSort = AlbumSort.RECENT,
    /** La cuadrícula principal empieza por las fotos más antiguas. */
    val oldestFirst: Boolean = false,
    val filter: TileFilter = TileFilter.ALL,
    /** Lo borrado pasa por la papelera; si no, se elimina para siempre en el acto. */
    val useTrash: Boolean = true,
    /** Lo último que se mandó a la papelera, mientras aún se ofrece deshacerlo. */
    val undo: List<MediaItem> = emptyList(),
    /** Copias que sobran: de cada grupo de repetidas, todas menos una. */
    val duplicates: List<MediaItem> = emptyList(),
    /** Carpetas ocultas del sistema; null si aún no se han buscado. */
    val hiddenFolders: List<HiddenFolder>? = null,
    val scanningHidden: Boolean = false,
    /** El vídeo está en una ventana flotante: solo debe verse el vídeo. */
    val pip: Boolean = false,
    /** Cuántos elementos faltan por pasar por el modelo de búsqueda por significado. */
    val semanticPending: Int = 0,
    /** Lo que devolvió la última búsqueda, en su orden; es lo que recorre el visor al abrir un resultado. */
    val searchResults: List<MediaItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val level: Level = Level.MONTH,
    val pick: PickMode? = null,
    val message: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val vaultOpen: Boolean = false,
    val vaultItems: List<VaultItem> = emptyList(),
    val backup: BackupState = BackupState(),
) {
    fun itemsFor(source: Source): List<MediaItem> = when (source) {
        Source.Timeline -> tiles
        Source.Favorites -> sortItems(items.filter { it.id in favorites }, itemSort)
        is Source.Stack -> stackByBest[source.bestId]?.members.orEmpty()
        is Source.Album -> sortItems((items + lockedItems).filter { it.bucketId == source.bucketId }, itemSort)
        is Source.Auto -> when (source.key) {
            "recuerdo" -> memory?.items.orEmpty()
            else -> sortItems(autoAlbums.firstOrNull { it.key == source.key }?.items.orEmpty(), itemSort)
        }
        is Source.Ids -> sortItems(items.filter { it.id in source.ids }, itemSort)
        is Source.Search -> searchResults
        is Source.Hidden -> hiddenFolders?.firstOrNull { it.path == source.path }?.items.orEmpty()
        is Source.External -> {
            // Si el archivo está en la biblioteca se abre dentro de ella, para poder pasar a las demás fotos.
            val id = mediaIdOf(source.uri)
            if (id != null && items.any { it.id == id }) items else listOf(externalItem(source.uri, source.isVideo))
        }
    }

    fun reviewItems(kind: ReviewKind): List<MediaItem> = when (kind) {
        ReviewKind.REPEATED -> stackByBest.values.flatMap { s -> s.members.filter { it.id != s.best.id } }
        ReviewKind.BIG_VIDEOS -> items.filter { it.isVideo && it.size >= 50L * 1024 * 1024 }.sortedByDescending { it.size }
        ReviewKind.OLD_SCREENSHOTS -> {
            val limit = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
            items.filter { it.isScreenshot && it.date < limit }
        }
        ReviewKind.BLURRY -> blurry
        ReviewKind.DUPLICATES -> duplicates
    }
}

class LumiViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("lumi", Context.MODE_PRIVATE)
    private val analyzer = Analyzer(app)
    private val indexer = Indexer(app)
    private val semantic = Semantic(app)
    private val backup = Backup(app)
    private val vault get() = LumiApp.vault

    private val _state = MutableStateFlow(
        UiState(
            favorites = readIds(KEY_FAVORITES),
            theme = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            lockMethod = if (prefs.getString(KEY_PIN_HASH, null) != null && prefs.getBoolean(KEY_USE_PIN, false)) LockMethod.PIN else LockMethod.SYSTEM,
            showHidden = prefs.getBoolean(KEY_SHOW_HIDDEN, false),
            itemSort = runCatching { ItemSort.valueOf(prefs.getString(KEY_ITEM_SORT, null) ?: "NEWEST") }.getOrDefault(ItemSort.NEWEST),
            albumSort = runCatching { AlbumSort.valueOf(prefs.getString(KEY_ALBUM_SORT, null) ?: "RECENT") }.getOrDefault(AlbumSort.RECENT),
            oldestFirst = prefs.getBoolean(KEY_OLDEST_FIRST, false),
            recentSearches = prefs.getString(KEY_RECENT, "").orEmpty().split('\n').filter { it.isNotBlank() },
            useTrash = prefs.getBoolean(KEY_USE_TRASH, true),
            appLock = prefs.getBoolean(KEY_APP_LOCK, false),
            locked = prefs.getBoolean(KEY_APP_LOCK, false),
        ),
    )
    val state = _state.asStateFlow()

    val backStack = mutableStateListOf<Screen>(Screen.Timeline)

    /** Fotos de pilas que el usuario decidió conservar todas: no se vuelven a apilar. */
    private var kept = readIds(KEY_KEPT)
    private var stacks: List<PhotoStack> = emptyList()
    private var lockedAlbums = readIds(KEY_LOCKED_ALBUMS)
    private var hiddenAlbums = readIds(KEY_HIDDEN_ALBUMS)
    private var pinnedAlbums = readIds(KEY_PINNED_ALBUMS)
    /** Álbum -> foto elegida como portada. */
    private var covers: Map<Long, Long> = prefs.getStringSet(KEY_COVERS, emptySet()).orEmpty().mapNotNull { pair ->
        val (album, item) = pair.split(':').mapNotNull { it.toLongOrNull() }.takeIf { it.size == 2 } ?: return@mapNotNull null
        album to item
    }.toMap()
    private var undoJob: Job? = null
    private var lastLibrary: Pair<List<MediaItem>, List<MediaItem>>? = null
    private var afterPin: (() -> Unit)? = null
    private var firstPin = ""
    private var canReadPlace = false
    private var loadJob: Job? = null
    private var messageJob: Job? = null
    private var backupJob: Job? = null
    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload()
    }

    fun onPermission(granted: Boolean, partial: Boolean, place: Boolean) {
        if (!granted) {
            _state.update { it.copy(hasPermission = false, partial = false) }
            return
        }
        if (!observing) {
            observing = true
            getApplication<Application>().contentResolver.registerContentObserver(FILES_URI, true, observer)
        }
        val changed = !_state.value.hasPermission || _state.value.partial != partial || canReadPlace != place
        canReadPlace = place
        _state.update { it.copy(hasPermission = true, partial = partial) }
        if (changed) reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // El sistema avisa varias veces seguidas por cada cambio; se espera a que termine.
            delay(250)
            val library = withContext(Dispatchers.IO) { loadLibrary(getApplication()) }
            publish(library.active, library.trashed)
            // A partir de aquí todo es análisis: va en un hilo aparte, despacio y sin estorbar.
            val found = withContext(Quiet.dispatcher) { analyzer.findStacks(library.active, kept) }
            if (found.size != stacks.size || found.zip(stacks).any { (a, b) -> a.best.id != b.best.id || a.members.size != b.members.size }) {
                stacks = found
                publish(library.active, library.trashed)
            }
            // Primero lo rápido (cosas, lugar y huella), después el modelo de búsqueda y, al final,
            // lo más lento: leer el texto de cada foto.
            withContext(Quiet.dispatcher) {
                indexer.scan(library.active, canReadPlace, deep = false) { publishIndex(library.active) }
            }
            publishIndex(library.active)
            withContext(Quiet.dispatcher) { semantic.scan(library.active) { publishIndex(library.active) } }
            publishIndex(library.active)
            withContext(Quiet.dispatcher) {
                indexer.scan(library.active, canReadPlace, deep = true) { publishIndex(library.active) }
            }
            publishIndex(library.active)
        }
    }

    /** Lo que se puede enseñar: fuera lo de álbumes con candado y, salvo que se pida, lo oculto. */
    private fun visible(all: List<MediaItem>): List<MediaItem> {
        val current = _state.value
        if (lockedAlbums.isEmpty() && (hiddenAlbums.isEmpty() || current.showHidden)) return all
        return all.filter { it.bucketId !in lockedAlbums && (current.showHidden || it.bucketId !in hiddenAlbums) }
    }

    /** Vuelve a calcular lo que se ve sin releer la biblioteca: cambió un ajuste, no las fotos. */
    private fun refresh() {
        val (all, trashed) = lastLibrary ?: return
        viewModelScope.launch { publish(all, trashed) }
    }

    /** La biblioteca cambió: se rehace todo lo que se ve. */
    private suspend fun publish(all: List<MediaItem>, trashed: List<MediaItem>) {
        lastLibrary = all to trashed
        val settings = _state.value
        val items = visible(all)
        val next = withContext(Dispatchers.Default) {
            val alive = items.mapTo(HashSet()) { it.id }
            val valid = stacks.mapNotNull { s ->
                val members = s.members.filter { it.id in alive }
                if (members.size < 2) null else PhotoStack(members, if (s.best.id in alive) s.best else members.first())
            }
            val byBest = valid.associateBy { it.best.id }
            val hidden = HashSet<Long>()
            valid.forEach { s -> s.members.forEach { if (it.id != s.best.id) hidden += it.id } }
            val unstacked = items.filter { it.id !in hidden }
            val tiles = when (settings.filter) {
                TileFilter.ALL -> unstacked
                TileFilter.PHOTOS -> unstacked.filter { !it.isVideo && !it.isScreenshot }
                TileFilter.VIDEOS -> unstacked.filter { it.isVideo }
                TileFilter.SCREENSHOTS -> unstacked.filter { it.isScreenshot }
                TileFilter.FAVORITES -> unstacked.filter { it.id in settings.favorites }
                TileFilter.GIFS -> unstacked.filter { it.isGif }
            }
            // El recuerdo del día solo encabeza la vista completa, no las filtradas.
            val memory = if (settings.filter == TileFilter.ALL) buildMemory(unstacked) else null
            UiState(
                items = items,
                trashed = trashed,
                stackByBest = byBest,
                tiles = tiles,
                cells = (if (settings.oldestFirst) tiles.asReversed() else tiles).let { ordered ->
                    Level.entries.associateWith { buildCells(ordered, byBest, it, memory) }
                },
                albums = buildAlbums(all, lockedAlbums, hiddenAlbums, settings.albumSort, pinnedAlbums, covers),
                lockedItems = if (settings.albumsUnlocked) all.filter { it.bucketId in lockedAlbums } else emptyList(),
                memory = memory,
                backup = _state.value.backup.copy(
                    destination = backup.destinationName(),
                    pending = backup.pending(items).size,
                    last = backup.lastRun,
                ),
            )
        }
        _state.update {
            it.copy(
                loading = false,
                items = next.items,
                trashed = next.trashed,
                stackByBest = next.stackByBest,
                tiles = next.tiles,
                cells = next.cells,
                albums = next.albums,
                lockedItems = next.lockedItems,
                memory = next.memory,
                backup = if (it.backup.running) it.backup else next.backup,
            )
        }
        publishIndex(all)
    }

    /** El análisis avanzó: solo cambia lo que depende de él. La cuadrícula no se toca. */
    private suspend fun publishIndex(all: List<MediaItem>) {
        val items = visible(all)
        val next = withContext(Dispatchers.Default) {
            val index = indexer.snapshot()
            val photos = items.size
            UiState(
                // Los álbumes de cosas salen del modelo nuevo; mientras no haya mirado nada, de la lista antigua.
                autoAlbums = buildTrips(items, index) + semantic.albums(items, CONCEPTS).ifEmpty { buildThings(items, index) },
                blurry = indexer.blurry(items),
                duplicates = findDuplicates(items, index).flatMap { it.drop(1) },
                semanticPending = semantic.pending(items),
                index = index,
                // Con el modelo nuevo los temas salen mucho más finos; la lista antigua queda de reserva.
                topThings = semantic.topics(items, CONCEPTS).ifEmpty { topThings(items, index) },
                topPlaces = topPlaces(items, index),
                photoCount = photos,
                unindexed = items.count { it.id !in index },
                deepPending = items.count { index[it.id]?.deep != true },
            )
        }
        _state.update {
            it.copy(
                autoAlbums = next.autoAlbums,
                blurry = next.blurry,
                duplicates = next.duplicates,
                semanticPending = next.semanticPending,
                index = next.index,
                topThings = next.topThings,
                topPlaces = next.topPlaces,
                photoCount = next.photoCount,
                unindexed = next.unindexed,
                deepPending = next.deepPending,
            )
        }
    }

    fun setLevel(level: Level) = _state.update { it.copy(level = level) }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.update { it.copy(theme = mode) }
    }

    // --- Bloqueo de la app ---

    fun setAppLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK, enabled).apply()
        _state.update { it.copy(appLock = enabled, locked = false) }
    }

    fun unlockApp() = _state.update { it.copy(locked = false) }

    /** El usuario sale de la app: se cierra todo lo que estaba abierto con contraseña. */
    fun onLeave() {
        lockVault()
        val wasOpen = _state.value.albumsUnlocked
        _state.update { it.copy(locked = it.appLock, albumsUnlocked = false, pinStep = null) }
        if (wasOpen) {
            backStack.removeAll { it is Screen.Album || it is Screen.Viewer }
            if (backStack.isEmpty()) backStack.add(Screen.Timeline)
            refresh()
        }
    }

    // --- PIN propio ---

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
        val bytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    val usesPin: Boolean get() = _state.value.lockMethod == LockMethod.PIN

    /** Enseña el teclado y, si el PIN es correcto, ejecuta [onOk]. */
    fun askPin(onOk: () -> Unit) {
        afterPin = onOk
        _state.update { it.copy(pinStep = PinStep.ASK, pinError = null) }
    }

    /** Empieza a crear un PIN nuevo; al terminar, Lumi pasa a pedir ese PIN. */
    fun createPin() {
        firstPin = ""
        _state.update { it.copy(pinStep = PinStep.CREATE, pinError = null) }
    }

    fun cancelPin() {
        afterPin = null
        _state.update { it.copy(pinStep = null, pinError = null) }
    }

    /** Se confirma un PIN con huella en lugar de teclearlo. */
    fun pinBypassed() {
        val done = afterPin
        cancelPin()
        done?.invoke()
    }

    fun pinEntered(pin: String) {
        when (_state.value.pinStep) {
            PinStep.ASK -> {
                val salt = android.util.Base64.decode(prefs.getString(KEY_PIN_SALT, "") ?: "", android.util.Base64.NO_WRAP)
                if (hash(pin, salt) == prefs.getString(KEY_PIN_HASH, null)) pinBypassed()
                else _state.update { it.copy(pinError = "Ese no es el PIN") }
            }
            PinStep.CREATE -> {
                firstPin = pin
                _state.update { it.copy(pinStep = PinStep.REPEAT, pinError = null) }
            }
            PinStep.REPEAT -> if (pin == firstPin) {
                val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                prefs.edit()
                    .putString(KEY_PIN_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
                    .putString(KEY_PIN_HASH, hash(pin, salt))
                    .putBoolean(KEY_USE_PIN, true)
                    .apply()
                _state.update { it.copy(lockMethod = LockMethod.PIN, pinStep = null, pinError = null) }
                say("PIN guardado")
            } else {
                firstPin = ""
                _state.update { it.copy(pinStep = PinStep.CREATE, pinError = "No coinciden. Empieza otra vez.") }
            }
            null -> Unit
        }
    }

    fun useSystemLock() {
        prefs.edit().putBoolean(KEY_USE_PIN, false).apply()
        _state.update { it.copy(lockMethod = LockMethod.SYSTEM) }
    }

    // --- Álbumes con candado, ocultos y orden ---

    fun unlockAlbums() {
        _state.update { it.copy(albumsUnlocked = true) }
        refresh()
    }

    fun setAlbumLocked(bucketId: Long, locked: Boolean) {
        lockedAlbums = if (locked) lockedAlbums + bucketId else lockedAlbums - bucketId
        writeIds(KEY_LOCKED_ALBUMS, lockedAlbums)
        // Poner un candado lo cierra en el acto: quien esté dentro sale y tiene que volver a abrirlo.
        if (locked) _state.update { it.copy(albumsUnlocked = false) }
        say(if (locked) "Álbum bloqueado" else "Candado quitado")
        refresh()
    }

    /** Un álbum con candado solo está abierto mientras se está dentro de él. */
    private fun relockIfLeft() {
        if (!_state.value.albumsUnlocked) return
        if (backStack.none { it is Screen.Album && it.bucketId in lockedAlbums }) {
            _state.update { it.copy(albumsUnlocked = false) }
            refresh()
        }
    }

    fun setAlbumHidden(bucketId: Long, hidden: Boolean) {
        hiddenAlbums = if (hidden) hiddenAlbums + bucketId else hiddenAlbums - bucketId
        writeIds(KEY_HIDDEN_ALBUMS, hiddenAlbums)
        refresh()
    }

    fun setShowHidden(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply()
        _state.update { it.copy(showHidden = show) }
        refresh()
    }

    fun setItemSort(sort: ItemSort) {
        prefs.edit().putString(KEY_ITEM_SORT, sort.name).apply()
        _state.update { it.copy(itemSort = sort) }
    }

    fun setAlbumSort(sort: AlbumSort) {
        prefs.edit().putString(KEY_ALBUM_SORT, sort.name).apply()
        _state.update { it.copy(albumSort = sort) }
        refresh()
    }

    fun setUseTrash(use: Boolean) {
        prefs.edit().putBoolean(KEY_USE_TRASH, use).apply()
        _state.update { it.copy(useTrash = use) }
    }

    fun setOldestFirst(oldest: Boolean) {
        prefs.edit().putBoolean(KEY_OLDEST_FIRST, oldest).apply()
        _state.update { it.copy(oldestFirst = oldest) }
        refresh()
    }

    // --- Más acciones sobre archivos ---

    /** Solo después de que el sistema haya concedido el permiso de escritura sobre [original]. */
    fun replaceEdit(original: MediaItem, bitmap: Bitmap) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { replaceOriginal(getApplication(), original, bitmap) }
        say(if (ok) "Foto original reemplazada" else "No se pudo guardar")
        if (ok) {
            back()
            reload()
        }
    }

    /** Guarda como foto el instante [positionMs] de un vídeo. */
    fun saveFrame(item: MediaItem, positionMs: Long) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { saveVideoFrame(getApplication(), item, positionMs) }
        say(if (ok) "Fotograma guardado como foto" else "No se pudo sacar el fotograma")
        if (ok) reload()
    }

    fun rename(item: MediaItem, name: String) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { renameItem(getApplication(), item, name) }
        say(if (ok) "Nombre cambiado" else "No se pudo cambiar el nombre")
        if (ok) reload()
    }

    fun toggleFavorite(ids: Collection<Long>) {
        val current = _state.value.favorites
        val next = if (current.containsAll(ids)) current - ids.toSet() else current + ids
        writeIds(KEY_FAVORITES, next)
        _state.update { it.copy(favorites = next) }
        if (_state.value.filter == TileFilter.FAVORITES) refresh()
    }

    // --- Búsqueda ---

    /** Resultados de [query], con el modelo de significado si ya ha mirado alguna foto. */
    suspend fun runSearch(query: String): List<MediaItem> {
        val now = _state.value
        val found = withContext(Dispatchers.Default) {
            val phrase = semanticPhrase(query)
            val scores = if (phrase.isBlank() || semantic.size == 0) null else semantic.scores(phrase, toEnglish(phrase))
            search(query, now.items, now.index, now.favorites, scores)
        }
        _state.update { it.copy(searchResults = found) }
        return found
    }

    fun rememberSearch(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        val next = (listOf(clean) + _state.value.recentSearches.filter { !it.equals(clean, ignoreCase = true) }).take(8)
        prefs.edit().putString(KEY_RECENT, next.joinToString("\n")).apply()
        _state.update { it.copy(recentSearches = next) }
    }

    fun clearSearches() {
        prefs.edit().remove(KEY_RECENT).apply()
        _state.update { it.copy(recentSearches = emptyList()) }
    }

    /** Lee el texto de una foto y lo entrega a [onDone] ya en el hilo de la pantalla. */
    fun readText(item: MediaItem, onDone: (String) -> Unit) = viewModelScope.launch {
        say("Leyendo el texto…")
        val text = withContext(Dispatchers.IO) { indexer.readTextNow(item) }
        if (text.isBlank()) say("No se ve texto en esta foto") else onDone(text)
    }

    // --- Filtro de la pantalla Fotos, deshacer y ventana flotante ---

    fun setFilter(filter: TileFilter) {
        _state.update { it.copy(filter = filter) }
        refresh()
    }

    /** Tras mandar [items] a la papelera, ofrece deshacerlo durante unos segundos. */
    fun offerUndo(items: List<MediaItem>) {
        undoJob?.cancel()
        _state.update { it.copy(undo = items) }
        undoJob = viewModelScope.launch {
            delay(7000)
            _state.update { it.copy(undo = emptyList()) }
        }
    }

    fun clearUndo() {
        undoJob?.cancel()
        _state.update { it.copy(undo = emptyList()) }
    }

    fun setPip(active: Boolean) = _state.update { it.copy(pip = active) }

    // --- Más gestión de álbumes ---

    fun setAlbumPinned(bucketId: Long, pinned: Boolean) {
        pinnedAlbums = if (pinned) pinnedAlbums + bucketId else pinnedAlbums - bucketId
        writeIds(KEY_PINNED_ALBUMS, pinnedAlbums)
        refresh()
    }

    fun setAlbumCover(item: MediaItem) {
        covers = covers + (item.bucketId to item.id)
        prefs.edit().putStringSet(KEY_COVERS, covers.mapTo(HashSet()) { "${it.key}:${it.value}" }).apply()
        say("Portada del álbum cambiada")
        refresh()
    }

    // --- Carpetas ocultas del sistema ---

    fun scanHiddenFolders() = viewModelScope.launch {
        _state.update { it.copy(scanningHidden = true) }
        val found = withContext(Dispatchers.IO) { scanHidden() }
        _state.update { it.copy(hiddenFolders = found, scanningHidden = false) }
    }

    // --- Creaciones: collage, dibujo y recorte de vídeo ---

    fun saveArt(bitmap: Bitmap, name: String, path: String, done: String) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { saveNewPhoto(getApplication(), bitmap, name, path) }
        say(if (ok) done else "No se pudo guardar")
        if (ok) {
            back()
            reload()
        }
    }

    /** Guarda el recorte sin fondo como PNG, que es el formato que conserva la transparencia. */
    fun saveCutout(bitmap: Bitmap, name: String) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { savePng(getApplication(), bitmap, name) }
        say(if (ok) "Recorte guardado en el álbum Lumi" else "No se pudo guardar")
        if (ok) {
            back()
            reload()
        }
    }

    fun trim(item: MediaItem, startMs: Long, endMs: Long) = viewModelScope.launch {
        say("Recortando el vídeo…")
        val ok = withContext(Dispatchers.IO) { trimVideo(getApplication(), item, startMs, endMs) }
        say(if (ok) "Recorte guardado como vídeo nuevo" else "Este vídeo no se deja recortar")
        if (ok) {
            back()
            reload()
        }
    }

    fun keepStack(stack: PhotoStack) {
        kept = kept + stack.members.map { it.id }
        writeIds(KEY_KEPT, kept)
        reload()
    }

    // --- Archivos ---

    fun copyTo(items: List<MediaItem>, path: String, album: String) = viewModelScope.launch {
        val done = withContext(Dispatchers.IO) { copyItems(getApplication(), items, path) }
        say(if (done == 0) "No se pudo copiar" else "${countText(done, "copiada", "copiadas")} a $album")
        reload()
    }

    /** Solo después de que el sistema haya concedido el permiso de escritura sobre [items]. */
    fun moveTo(items: List<MediaItem>, path: String, album: String) = viewModelScope.launch {
        val done = withContext(Dispatchers.IO) { moveItems(getApplication(), items, path) }
        say(if (done == 0) "No se pudo mover" else "${countText(done, "movida", "movidas")} a $album")
        reload()
    }

    fun saveEdit(original: MediaItem, bitmap: Bitmap) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { saveEdited(getApplication(), original, bitmap) }
        say(if (ok) "Copia guardada junto a la original" else "No se pudo guardar")
        if (ok) {
            back()
            reload()
        }
    }

    fun say(text: String) {
        messageJob?.cancel()
        _state.update { it.copy(message = text) }
        messageJob = viewModelScope.launch {
            delay(2800)
            _state.update { it.copy(message = null) }
        }
    }

    // --- Carpeta privada ---

    fun unlockVault() = viewModelScope.launch {
        val items = withContext(Dispatchers.IO) { vault.list() }
        _state.update { it.copy(vaultOpen = true, vaultItems = items) }
    }

    fun lockVault() {
        if (!_state.value.vaultOpen) return
        _state.update { it.copy(vaultOpen = false, vaultItems = emptyList()) }
        backStack.removeAll { it == Screen.Vault || (it is Screen.Viewer && it.source is Source.External) }
        if (backStack.isEmpty()) backStack.add(Screen.Timeline)
        viewModelScope.launch(Dispatchers.IO) { vault.clearOpened() }
    }

    /** Cifra [items] y entrega a [onStored] los que ya están a salvo, para pedir que se borren los originales. */
    fun hideInVault(items: List<MediaItem>, onStored: (List<MediaItem>) -> Unit) = viewModelScope.launch {
        say("Guardando en la carpeta privada…")
        val stored = withContext(Dispatchers.IO) { items.filter { vault.add(it) } }
        if (_state.value.vaultOpen) unlockVault()
        if (stored.isEmpty()) say("No se pudo guardar") else onStored(stored)
    }

    fun openFromVault(item: VaultItem) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) { vault.open(item) }
        if (file == null) say("No se pudo abrir") else open(Screen.Viewer(Source.External(Uri.fromFile(file), item.isVideo), -1))
    }

    fun restoreFromVault(items: List<VaultItem>) = viewModelScope.launch {
        val done = withContext(Dispatchers.IO) { items.count { vault.restore(it) } }
        say("${countText(done, "devuelta", "devueltas")} a la galería")
        unlockVault()
        reload()
    }

    fun deleteFromVault(items: List<VaultItem>) = viewModelScope.launch {
        withContext(Dispatchers.IO) { items.forEach { vault.delete(it) } }
        unlockVault()
    }

    // --- Copia de seguridad ---

    fun setBackupDestination(uri: Uri) {
        backup.destination = uri
        _state.update {
            it.copy(backup = BackupState(destination = backup.destinationName(), pending = backup.pending(it.items).size))
        }
    }

    fun runBackup() {
        if (backupJob?.isActive == true) return
        val items = _state.value.items
        backupJob = viewModelScope.launch {
            _state.update { it.copy(backup = it.backup.copy(running = true, done = 0, total = it.backup.pending)) }
            val copied = withContext(Dispatchers.IO) {
                backup.run(items) { done, total -> _state.update { it.copy(backup = it.backup.copy(done = done, total = total)) } }
            }
            _state.update {
                it.copy(backup = it.backup.copy(running = false, pending = backup.pending(items).size, last = backup.lastRun))
            }
            say(if (copied == 0) "No había nada nuevo que copiar" else "${countText(copied, "archivo copiado", "archivos copiados")}")
        }
    }

    // --- Otras apps ---

    fun openExternal(uri: Uri, isVideo: Boolean) {
        backStack.removeAll { it is Screen.Viewer || it is Screen.Editor }
        backStack.add(Screen.Viewer(Source.External(uri, isVideo), mediaIdOf(uri) ?: -1))
    }

    fun setPick(mode: PickMode?) {
        _state.update { it.copy(pick = mode) }
        if (mode != null) switchTab(Screen.Timeline)
    }

    // --- Navegación ---

    fun open(screen: Screen) {
        backStack.add(screen)
    }

    fun replaceTop(screen: Screen) {
        backStack[backStack.lastIndex] = screen
    }

    fun switchTab(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
        relockIfLeft()
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        relockIfLeft()
        return true
    }

    override fun onCleared() {
        if (observing) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    private fun readIds(key: String): Set<Long> =
        prefs.getStringSet(key, emptySet()).orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }

    private fun writeIds(key: String, ids: Set<Long>) =
        prefs.edit().putStringSet(key, ids.mapTo(HashSet()) { it.toString() }).apply()

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_KEPT = "kept"
        const val KEY_THEME = "theme"
        const val KEY_APP_LOCK = "appLock"
        const val KEY_USE_PIN = "usePin"
        const val KEY_PIN_HASH = "pinHash"
        const val KEY_PIN_SALT = "pinSalt"
        const val KEY_LOCKED_ALBUMS = "lockedAlbums"
        const val KEY_HIDDEN_ALBUMS = "hiddenAlbums"
        const val KEY_PINNED_ALBUMS = "pinnedAlbums"
        const val KEY_COVERS = "albumCovers"
        const val KEY_SHOW_HIDDEN = "showHidden"
        const val KEY_ITEM_SORT = "itemSort"
        const val KEY_ALBUM_SORT = "albumSort"
        const val KEY_OLDEST_FIRST = "oldestFirst"
        const val KEY_USE_TRASH = "useTrash"
        const val KEY_RECENT = "recentSearches"
    }
}

private fun buildCells(tiles: List<MediaItem>, stackByBest: Map<Long, PhotoStack>, level: Level, memory: Memory?): List<Cell> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val out = ArrayList<Cell>(tiles.size + 64)
    if (memory != null) out += Cell.Recall(memory)
    var groupKey = Long.MIN_VALUE
    var headerIndex = -1
    var count = 0

    fun closeGroup() {
        if (headerIndex >= 0) out[headerIndex] = (out[headerIndex] as Cell.Header).copy(count = count)
    }

    for (tile in tiles) {
        val day = Instant.ofEpochMilli(tile.date).atZone(zone).toLocalDate()
        val key = when (level) {
            Level.YEAR -> day.year.toLong()
            Level.MONTH -> day.year * 12L + day.monthValue
            Level.DAY -> day.toEpochDay()
        }
        if (key != groupKey) {
            closeGroup()
            groupKey = key
            headerIndex = out.size
            count = 0
            out += Cell.Header("h${level.name}$key", groupTitle(day, level, today), 0)
        }
        out += Cell.Photo(tile, stackByBest[tile.id]?.members?.size ?: 0)
        count++
    }
    closeGroup()
    return out
}
