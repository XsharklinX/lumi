package com.lumi.galeria

import com.lumi.galeria.data.restoreName
import com.lumi.galeria.data.restorePaths
import com.lumi.galeria.data.copyToCard
import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.lumi.galeria.data.Filters
import com.lumi.galeria.data.Found
import com.lumi.galeria.data.find
import com.lumi.galeria.data.placeNames
import com.lumi.galeria.data.readSnapshot
import com.lumi.galeria.data.saveSnapshot
import com.lumi.galeria.ui.AccentColor
import com.lumi.galeria.ui.AlbumView
import com.lumi.galeria.data.HiddenFolder
import com.lumi.galeria.data.findDuplicates
import com.lumi.galeria.data.saveScanned
import com.lumi.galeria.data.sameImage
import com.lumi.galeria.data.saveNewPhoto
import com.lumi.galeria.data.savePng
import com.lumi.galeria.data.scanHidden
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

/** Columnas que admite la vista de día. */
val DayColumns = 2..5

/** Fotos que esperan a pegarse en otro álbum. [move]: se cortaron, no se copiaron. */
data class Clip(val items: List<MediaItem>, val move: Boolean)

enum class Level(val columns: Int, val label: String, val thumb: Int) {
    YEAR(6, "Año", 160),
    MONTH(4, "Mes", 320),
    DAY(2, "Día", 512),
}

sealed interface Cell {
    val key: String

    data class Header(override val key: String, val title: String, val count: Int) : Cell

    /** [star]: la mejor foto de su día, que se ve el doble de grande. */
    data class Photo(val item: MediaItem, val stackSize: Int, val star: Boolean = false) : Cell {
        override val key get() = "p${item.id}"
    }

    /** Tarjeta de recuerdos que encabeza la cuadrícula cuando hay fotos de estos días en otros años. */
    data class Recall(val memory: Memory?) : Cell {
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

/** [counts]: entra en «puedes recuperar», porque sobra sin ninguna duda. */
enum class ReviewKind(val title: String, val hint: String, val counts: Boolean) {
    DUPLICATES("Copias exactas", "La misma imagen guardada más de una vez. Lumi compara cada copia con la original antes de enseñarla.", true),
    REPEATED("Fotos parecidas", "Fotos casi iguales hechas seguidas. No son copias: mira cada grupo y quédate con las que quieras.", false),
    BIG_VIDEOS("Vídeos grandes", "Vídeos de más de 50 MB, del más pesado al más ligero.", false),
    OLD_SCREENSHOTS("Capturas antiguas", "Capturas de pantalla de hace más de 30 días.", false),
    BLURRY("Quizá borrosas", "Pueden estar movidas o desenfocadas. Míralas antes de quitarlas.", false),
    CLOSED_EYES("Ojos cerrados", "Fotos en las que alguien parpadea. Puede que haya otra igual con los ojos abiertos.", false),
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
    data class Viewer(val source: Source, val startId: Long, val origin: Rect? = null, val slideshow: Boolean = false) : Screen
    data class StackView(val bestId: Long) : Screen
    data class Review(val kind: ReviewKind) : Screen
    data class Editor(val id: Long) : Screen
    data class Markup(val id: Long) : Screen
    data class Collage(val ids: List<Long>) : Screen

    /** Juntar estas fotos en un PDF. */
    data class Pdf(val ids: List<Long>) : Screen

    /** Convertir un tramo de este vídeo en GIF. */
    data class Gif(val id: Long) : Screen

    /** Poner esta foto de fondo de pantalla. */
    data class Wallpaper(val id: Long) : Screen

    /** Girar esta foto viendo cómo queda. */
    data class Rotate(val id: Long) : Screen

    /** Ordenar los álbumes a mano. */
    data object AlbumOrder : Screen

    /** Un recuerdo a pantalla completa, como las historias. [grid] es la cuadrícula con todas sus fotos. */
    data class StoryView(val title: String, val ids: List<Long>, val grid: Screen) : Screen

    /** Repaso rápido deslizando, para limpiar. */
    data object SwipeReview : Screen

    /** Importar de una cámara, una tarjeta o una memoria USB. */
    data object Import : Screen

    /** Todas las personas que ha agrupado Lumi. */
    data object People : Screen

    /** Las fotos de una persona. [key] es su nombre o, si aún no tiene, la marca de su grupo. */
    data class Person(val key: String) : Screen

    /** Resumen de un año en fotos. */
    data class YearReview(val year: Int) : Screen

    /** Editar un vídeo: recortar, velocidad, encuadre, filtros y sonido. */
    data class VideoEditor(val id: Long) : Screen

    /** Convertir fotos seguidas en un GIF. */
    data class Animate(val ids: List<Long>) : Screen

    /** Sacar copias de varias fotos con otro tamaño, formato o marca de agua. */
    data class Export(val ids: List<Long>) : Screen

    /** Montar un vídeo con música a partir de un grupo de fotos. */
    data class MemoryVideo(val title: String, val ids: List<Long>) : Screen

    /** Las capturas de pantalla ordenadas por tipo. */
    data object Screenshots : Screen

    /** Desenfocar el fondo de una foto, como el modo retrato. */
    data class Portrait(val id: Long) : Screen
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
enum class PinStep { ASK, CREATE, REPEAT, DECOY_CREATE, DECOY_REPEAT }

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

/** Cómo va la importación desde una cámara o una memoria. */
data class ImportJob(
    /** Nombre de la carpeta o del aparato elegido. */
    val source: String = "",
    val scanning: Boolean = false,
    val found: Int = 0,
    val files: List<com.lumi.galeria.data.Importable> = emptyList(),
    val copying: Boolean = false,
    val finished: Boolean = false,
    val album: String = "",
    val done: Int = 0,
    val failed: Int = 0,
    val total: Int = 0,
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
    /** Ciudad cercana a cada foto que guarda su lugar. */
    val places: Map<Long, String> = emptyMap(),
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
    val albumView: AlbumView = AlbumView.LIST,
    /** Columnas de las cuadrículas de álbumes, favoritas y buscador; se cambian pellizcando. */
    val itemColumns: Int = 3,
    /** Vídeo -> por dónde se dejó de ver, en milisegundos. */
    val watched: Map<Long, Long> = emptyMap(),
    /** Lo que se decidió conservar en el repaso rápido: no vuelve a salir. */
    val reviewed: Set<Long> = emptySet(),
    /** La cuadrícula principal empieza por las fotos más antiguas. */
    val oldestFirst: Boolean = false,
    val filter: TileFilter = TileFilter.ALL,
    /** Lo borrado pasa por la papelera; si no, se elimina para siempre en el acto. */
    val useTrash: Boolean = true,
    /** Lo último que se mandó a la papelera, mientras aún se ofrece deshacerlo. */
    val undo: List<MediaItem> = emptyList(),
    /** Copias que sobran: de cada grupo de repetidas, todas menos una. */
    val duplicates: List<MediaItem> = emptyList(),
    /** Grupos de archivos repetidos; en cada uno, primero el que se propone conservar. */
    val duplicateGroups: List<List<MediaItem>> = emptyList(),
    /** Carpetas ocultas del sistema; null si aún no se han buscado. */
    val hiddenFolders: List<HiddenFolder>? = null,
    val scanningHidden: Boolean = false,
    /** El vídeo está en una ventana flotante: solo debe verse el vídeo. */
    val pip: Boolean = false,
    /** Lo que devolvió la última búsqueda, en su orden; es lo que recorre el visor al abrir un resultado. */
    val searchResults: List<MediaItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val level: Level = Level.MONTH,
    /** Columnas de la vista de día; se cambian pellizcando. */
    val dayColumns: Int = 3,
    val pick: PickMode? = null,
    val message: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.LILAC,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val pureBlack: Boolean = false,
    /** Fotos en las que alguien sale con los ojos cerrados. */
    val closedEyes: Set<Long> = emptySet(),
    /** La vista por días destaca la mejor foto de cada día. */
    val dayHighlights: Boolean = true,
    /** Personas que salen en las fotos, agrupadas por su cara. */
    val people: List<com.lumi.galeria.data.Person> = emptyList(),
    /** Agrupar caras está activado. */
    val facesOn: Boolean = true,
    /** Fotos ya miradas en busca de caras, de cuántas. */
    val facesLooked: Int = 0,
    val facesTotal: Int = 0,
    /** Personas ocultas por el usuario. */
    val hiddenPeople: Int = 0,
    val vaultOpen: Boolean = false,
    /** Lo abierto es la carpeta del PIN señuelo. La pantalla no lo distingue: solo cambia de dónde salen las fotos. */
    val vaultDecoy: Boolean = false,
    /** Hay un PIN señuelo guardado. */
    val hasDecoy: Boolean = false,
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
        ReviewKind.CLOSED_EYES -> items.filter { it.id in closedEyes }
    }

    /** Copias y parecidas agrupadas; en cada grupo, primero la que se propone conservar. */
    fun reviewGroups(kind: ReviewKind): List<List<MediaItem>> = when (kind) {
        ReviewKind.REPEATED -> stackByBest.values.map { s -> listOf(s.best) + s.members.filter { it.id != s.best.id } }
        ReviewKind.DUPLICATES -> duplicateGroups
        else -> emptyList()
    }
}

class LumiViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("lumi", Context.MODE_PRIVATE)
    private val resume = app.getSharedPreferences("seguir", Context.MODE_PRIVATE)
    private val analyzer = Analyzer(app)
    private val indexer = Indexer(app)
    private val faceFinder = com.lumi.galeria.data.FaceFinder(app)
    private var named = com.lumi.galeria.data.PeopleNames.read(app)
    /** "foto:nombre": esa foto no es de esa persona aunque lo parezca. */
    private var notThem = prefs.getStringSet(KEY_NOT_THEM, emptySet()).orEmpty().toSet()
    private val backup = Backup(app)
    /** La carpeta privada abierta ahora: la de verdad o la del PIN señuelo. */
    private val vault get() = if (_state.value.vaultDecoy) LumiApp.decoy else LumiApp.vault

    private val _state = MutableStateFlow(
        UiState(
            favorites = readIds(KEY_FAVORITES),
            theme = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            lockMethod = if (prefs.getString(KEY_PIN_HASH, null) != null && prefs.getBoolean(KEY_USE_PIN, false)) LockMethod.PIN else LockMethod.SYSTEM,
            showHidden = prefs.getBoolean(KEY_SHOW_HIDDEN, false),
            itemSort = runCatching { ItemSort.valueOf(prefs.getString(KEY_ITEM_SORT, null) ?: "NEWEST") }.getOrDefault(ItemSort.NEWEST),
            albumSort = runCatching { AlbumSort.valueOf(prefs.getString(KEY_ALBUM_SORT, null) ?: "RECENT") }.getOrDefault(AlbumSort.RECENT),
            oldestFirst = prefs.getBoolean(KEY_OLDEST_FIRST, false),
            accent = runCatching { AccentColor.valueOf(prefs.getString(KEY_ACCENT, null) ?: "LILAC") }.getOrDefault(AccentColor.LILAC),
            pureBlack = prefs.getBoolean(KEY_BLACK, false),
            reviewed = readIds(KEY_REVIEWED),
            itemColumns = prefs.getInt(KEY_ITEM_COLUMNS, 3).coerceIn(DayColumns),
            watched = resume.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? Long)?.let { id to it } } }.toMap(),
            albumView = runCatching { AlbumView.valueOf(prefs.getString(KEY_ALBUM_VIEW, null) ?: "LIST") }.getOrDefault(AlbumView.LIST),
            language = runCatching { AppLanguage.valueOf(prefs.getString(KEY_LANGUAGE, null) ?: "SYSTEM") }.getOrDefault(AppLanguage.SYSTEM),
            recentSearches = prefs.getString(KEY_RECENT, "").orEmpty().split('\n').filter { it.isNotBlank() },
            useTrash = prefs.getBoolean(KEY_USE_TRASH, true),
            dayColumns = prefs.getInt(KEY_DAY_COLUMNS, 3).coerceIn(DayColumns),
            appLock = prefs.getBoolean(KEY_APP_LOCK, false),
            hasDecoy = prefs.getString(KEY_DECOY_HASH, null) != null,
            facesOn = prefs.getBoolean(KEY_FACES, true),
            dayHighlights = prefs.getBoolean(KEY_HIGHLIGHTS, true),
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
    private var albumOrder = prefs.getString(KEY_ALBUM_ORDER, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
    /** Álbum -> foto elegida como portada. */
    private var covers: Map<Long, Long> = prefs.getStringSet(KEY_COVERS, emptySet()).orEmpty().mapNotNull { pair ->
        val (album, item) = pair.split(':').mapNotNull { it.toLongOrNull() }.takeIf { it.size == 2 } ?: return@mapNotNull null
        album to item
    }.toMap()
    private var undoJob: Job? = null
    private var lastLibrary: Pair<List<MediaItem>, List<MediaItem>>? = null
    private var afterPin: (() -> Unit)? = null
    /** Qué hacer si en lugar del PIN se escribe el señuelo; null si aquí el señuelo no vale. */
    private var afterDecoy: (() -> Unit)? = null
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
            if (lastLibrary == null) {
                // Recién abierta: se pinta lo que había la última vez y, mientras, se lee lo que hay ahora.
                val before = withContext(Dispatchers.IO) { readSnapshot(getApplication()) }
                if (!before.isNullOrEmpty()) publish(before, emptyList(), withIndex = false)
            } else {
                // El sistema avisa varias veces seguidas por cada cambio; se espera a que termine.
                delay(250)
            }
            val library = withContext(Dispatchers.IO) { loadLibrary(getApplication()) }
            publish(library.active, library.trashed)
            viewModelScope.launch(Dispatchers.IO) { saveSnapshot(getApplication(), library.active) }
            // A partir de aquí todo es análisis: va en un hilo aparte, despacio y sin estorbar.
            val found = withContext(Quiet.dispatcher) { analyzer.findStacks(library.active, kept) }
            if (found.size != stacks.size || found.zip(stacks).any { (a, b) -> a.best.id != b.best.id || a.members.size != b.members.size }) {
                stacks = found
                publish(library.active, library.trashed)
            }
            // Primero lo rápido (cosas, lugar y huella) y después lo lento: leer el texto de cada foto.
            withContext(Quiet.dispatcher) {
                indexer.scan(library.active, canReadPlace, deep = false) { publishIndex(library.active) }
            }
            publishIndex(library.active)
            // Después, las caras: antes que el texto, que es lo más lento y lo que menos se echa en falta.
            if (_state.value.facesOn) {
                publishPeople()
                withContext(Quiet.dispatcher) { faceFinder.scan(visible(library.active)) { publishPeople() } }
                publishPeople()
            }
            withContext(Quiet.dispatcher) {
                indexer.scan(library.active, canReadPlace, deep = true) { publishIndex(library.active) }
            }
            publishIndex(library.active)
            // Ya se sabe lo nítida que es cada foto: la estrella de cada día puede cambiar.
            if (_state.value.dayHighlights) refresh()
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
    private suspend fun publish(all: List<MediaItem>, trashed: List<MediaItem>, withIndex: Boolean = true) {
        lastLibrary = all to trashed
        val settings = _state.value
        val items = visible(all)
        val next = withContext(Dispatchers.Default) {
            val alive = items.mapTo(HashSet()) { it.id }
            val blinks = settings.closedEyes
            val faces = faceScores
            val valid = stacks.mapNotNull { s ->
                val members = s.members.filter { it.id in alive }
                if (members.size < 2) return@mapNotNull null
                var best = if (s.best.id in alive) s.best else members.first()
                // Si en la más nítida alguien parpadea, mejor otra en la que todos tengan los ojos abiertos y sonrían.
                if (best.id in blinks || faces.isNotEmpty()) {
                    val open = members.filter { it.id !in blinks }
                    if (open.isNotEmpty()) {
                        val happiest = open.maxBy { faces[it.id] ?: -1f }
                        if (best.id in blinks || (faces[happiest.id] ?: -1f) > (faces[best.id] ?: -1f) + 0.35f) best = happiest
                    }
                }
                PhotoStack(members, best)
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
                    val stars: ((List<MediaItem>) -> MediaItem?)? = if (!settings.dayHighlights) null else { day ->
                        // Solo en días con varias fotos: con dos o tres, destacar una no aporta nada.
                        if (day.size < 4) null else day.maxBy { beauty(it, settings.favorites, settings.index, faces) }.takeIf { beauty(it, settings.favorites, settings.index, faces) > -5f }
                    }
                    Level.entries.associateWith { buildCells(ordered, byBest, it, memory, settings.filter == TileFilter.ALL && ordered.isNotEmpty(), if (it == Level.DAY) stars else null) }
                },
                albums = buildAlbums(all, lockedAlbums, hiddenAlbums, settings.albumSort, pinnedAlbums, covers, albumOrder),
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
        if (withIndex) publishIndex(all)
    }

    /** El análisis avanzó: solo cambia lo que depende de él. La cuadrícula no se toca. */
    private suspend fun publishIndex(all: List<MediaItem>) {
        val items = visible(all)
        val next = withContext(Dispatchers.Default) {
            val index = indexer.snapshot()
            val photos = items.size
            val names = placeNames(items, index)
            UiState(
                autoAlbums = buildTrips(items, index) + buildThings(items, index),
                blurry = indexer.blurry(items),
                duplicateGroups = findDuplicates(items, index),
                index = index,
                topThings = topThings(items, index),
                topPlaces = topPlaces(items, names),
                places = names,
                photoCount = photos,
                // Cuenta también las que esperan a que llegue el reconocimiento de Google Play.
                unindexed = items.count { index[it.id]?.pending != false },
                deepPending = items.count { index[it.id]?.deep != true },
            )
        }
        _state.update {
            it.copy(
                autoAlbums = next.autoAlbums,
                blurry = next.blurry,
                duplicateGroups = next.duplicateGroups,
                duplicates = next.duplicateGroups.flatMap { it.drop(1) },
                index = next.index,
                topThings = next.topThings,
                topPlaces = next.topPlaces,
                places = next.places,
                photoCount = next.photoCount,
                unindexed = next.unindexed,
                deepPending = next.deepPending,
            )
        }
    }

    /** Foto -> cómo de bien salen las caras: sonrisas suman, ojos cerrados restan. Solo fotos con caras. */
    private var faceScores: Map<Long, Float> = emptyMap()

    /** Vuelve a juntar las caras en personas con lo que se sabe ahora. */
    private suspend fun publishPeople() {
        val all = lastLibrary?.first ?: return
        val items = visible(all)
        var blinks: Set<Long> = emptySet()
        val next = withContext(Dispatchers.Default) {
            val alive = items.mapTo(HashSet()) { it.id }
            val faces = faceFinder.snapshot().filter { it.photo in alive }
            val byPhoto = faces.groupBy { it.photo }
            blinks = byPhoto.filterValues { list -> list.any { it.blinking } }.keys
            faceScores = byPhoto.mapValues { (_, list) ->
                list.sumOf { f -> ((if (f.smile >= 0f) f.smile else 0.3f) - (if (f.blinking) 1.5f else 0f)).toDouble() }.toFloat() / list.size
            }
            com.lumi.galeria.data.groupPeople(faces, named, notThem)
        }
        val blinksChanged = blinks != _state.value.closedEyes
        _state.update { it.copy(closedEyes = blinks) }
        // La mejor toma de cada pila y la foto estrella de cada día dependen de las caras.
        if (blinksChanged) refresh()
        val photos = items.count { !it.isVideo && !it.isScreenshot && !it.isGif }
        _state.update {
            it.copy(people = next, facesLooked = faceFinder.lookedAt().coerceAtMost(photos), facesTotal = photos, hiddenPeople = named.filter { n -> n.hidden }.map { n -> n.name }.distinct().size)
        }
    }

    private fun peopleChanged() {
        com.lumi.galeria.data.PeopleNames.write(getApplication(), named)
        viewModelScope.launch { publishPeople() }
    }

    /**
     * Pone nombre a [person]. Si ya tenía, se cambia en todos sus grupos. Si otra persona ya se
     * llama así, las dos pasan a ser la misma: así se juntan dos grupos de alguien.
     */
    fun namePerson(person: com.lumi.galeria.data.Person, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        named = if (person.name != null) {
            named.map { if (it.name == person.name) com.lumi.galeria.data.Named(clean, it.centroid, it.hidden) else it }
        } else {
            named + com.lumi.galeria.data.Named(clean, person.centroid)
        }
        if (person.name != null && person.name != clean) {
            notThem = notThem.map { if (it.endsWith(":" + person.name)) it.substringBefore(':') + ":" + clean else it }.toSet()
            prefs.edit().putStringSet(KEY_NOT_THEM, notThem).apply()
        }
        peopleChanged()
        val top = backStack.lastOrNull()
        if (top is Screen.Person && top.key == person.key) replaceTop(Screen.Person(clean))
    }

    /** Deja de enseñar a [person]. Se puede deshacer desde Ajustes. */
    fun hidePerson(person: com.lumi.galeria.data.Person) {
        val name = person.name ?: ("~" + person.key)
        named = if (person.name != null) named.map { if (it.name == name) com.lumi.galeria.data.Named(it.name, it.centroid, true) else it }
        else named + com.lumi.galeria.data.Named(name, person.centroid, hidden = true)
        peopleChanged()
        if (backStack.lastOrNull() is Screen.Person) back()
        say("Persona oculta. Puedes volver a verla desde Ajustes.")
    }

    fun showHiddenPeople() {
        // Las ocultas sin nombre se olvidan del todo; las que tenían nombre vuelven con él.
        named = named.filter { !(it.hidden && it.name.startsWith("~")) }.map { com.lumi.galeria.data.Named(it.name, it.centroid, false) }
        peopleChanged()
    }

    /** Estas fotos no son de [person]. */
    fun notThisPerson(person: com.lumi.galeria.data.Person, photos: List<MediaItem>) {
        val name = person.name ?: return
        notThem = notThem + photos.map { "${it.id}:$name" }
        prefs.edit().putStringSet(KEY_NOT_THEM, notThem).apply()
        peopleChanged()
        say("Quitadas de $name")
    }

    /** Activa o quita agrupar caras. Al quitarlo se borra todo lo que se sabía de las caras. */
    fun setFacesOn(on: Boolean) {
        prefs.edit().putBoolean(KEY_FACES, on).apply()
        _state.update { it.copy(facesOn = on, people = if (on) it.people else emptyList(), facesLooked = if (on) it.facesLooked else 0) }
        if (on) reload() else viewModelScope.launch(Dispatchers.IO) { faceFinder.clear() }
    }

    fun setDayHighlights(on: Boolean) {
        prefs.edit().putBoolean(KEY_HIGHLIGHTS, on).apply()
        _state.update { it.copy(dayHighlights = on) }
        refresh()
    }

    /**
     * Lo bonita que es una foto para destacarla: favorita, gente sonriendo con los ojos abiertos,
     * nítida. Las capturas y los vídeos no compiten.
     */
    private fun beauty(item: MediaItem, favorites: Set<Long>, index: Map<Long, IndexEntry>, faces: Map<Long, Float>): Float {
        if (item.isScreenshot || item.isVideo || item.isGif) return -10f
        var score = 0f
        if (item.id in favorites) score += 3f
        faces[item.id]?.let { score += 1f + it * 1.5f }
        val blur = index[item.id]?.blur ?: -1f
        if (blur > 0f) score += (kotlin.math.ln(blur + 1f) / 3f).coerceAtMost(2f)
        if (item.width.toLong() * item.height >= 6_000_000) score += 0.3f
        return score
    }

    fun setLevel(level: Level) = _state.update { it.copy(level = level) }

    fun setDayColumns(columns: Int) {
        val next = columns.coerceIn(DayColumns)
        prefs.edit().putInt(KEY_DAY_COLUMNS, next).apply()
        _state.update { it.copy(dayColumns = next) }
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.update { it.copy(theme = mode) }
    }

    fun setAccent(color: AccentColor) {
        prefs.edit().putString(KEY_ACCENT, color.name).apply()
        _state.update { it.copy(accent = color) }
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
        _state.update { it.copy(language = language) }
    }

    /** Dónde retomar [video]; 0 si se empieza por el principio. */
    fun resumePosition(video: MediaItem): Long {
        val at = _state.value.watched[video.id] ?: return 0L
        return if (at >= 5000 && at < video.duration - 10_000) at else 0L
    }

    /**
     * Apunta por dónde va [video]. Solo los de más de un minuto: en uno corto no merece la pena.
     * Al principio o casi al final se olvida, porque ya no hay nada que retomar.
     */
    fun saveResume(video: MediaItem, positionMs: Long) {
        if (video.duration < 60_000) return
        val keep = positionMs >= 5000 && positionMs < video.duration - 10_000
        val current = _state.value.watched
        if (keep && current[video.id] == positionMs || !keep && video.id !in current) return
        val key = video.id.toString()
        resume.edit().apply { if (keep) putLong(key, positionMs) else remove(key) }.apply()
        _state.update { it.copy(watched = if (keep) it.watched + (video.id to positionMs) else it.watched - video.id) }
    }

    fun setAlbumView(view: AlbumView) {
        prefs.edit().putString(KEY_ALBUM_VIEW, view.name).apply()
        _state.update { it.copy(albumView = view) }
    }

    fun setPureBlack(on: Boolean) {
        prefs.edit().putBoolean(KEY_BLACK, on).apply()
        _state.update { it.copy(pureBlack = on) }
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
        afterDecoy = null
        _state.update { it.copy(pinStep = PinStep.ASK, pinError = null) }
    }

    /** Empieza a crear un PIN nuevo; al terminar, Lumi pasa a pedir ese PIN. */
    fun createPin() {
        firstPin = ""
        _state.update { it.copy(pinStep = PinStep.CREATE, pinError = null) }
    }

    fun cancelPin() {
        afterPin = null
        afterDecoy = null
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
                val decoy = afterDecoy
                when {
                    hash(pin, salt) == prefs.getString(KEY_PIN_HASH, null) -> pinBypassed()
                    // El señuelo solo abre la carpeta privada; en cualquier otro sitio es un PIN equivocado.
                    decoy != null && isDecoy(pin) -> {
                        cancelPin()
                        decoy()
                    }
                    else -> _state.update { it.copy(pinError = "Ese no es el PIN") }
                }
            }
            PinStep.DECOY_CREATE -> {
                val salt = android.util.Base64.decode(prefs.getString(KEY_PIN_SALT, "") ?: "", android.util.Base64.NO_WRAP)
                if (hash(pin, salt) == prefs.getString(KEY_PIN_HASH, null)) {
                    _state.update { it.copy(pinError = "Tiene que ser distinto de tu PIN") }
                } else {
                    firstPin = pin
                    _state.update { it.copy(pinStep = PinStep.DECOY_REPEAT, pinError = null) }
                }
            }
            PinStep.DECOY_REPEAT -> if (pin == firstPin) {
                val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                prefs.edit()
                    .putString(KEY_DECOY_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
                    .putString(KEY_DECOY_HASH, hash(pin, salt))
                    .apply()
                _state.update { it.copy(pinStep = null, pinError = null, hasDecoy = true) }
                say("PIN señuelo guardado")
            } else {
                firstPin = ""
                _state.update { it.copy(pinStep = PinStep.DECOY_CREATE, pinError = "No coinciden. Empieza otra vez.") }
            }
            PinStep.CREATE -> {
                firstPin = pin
                _state.update { it.copy(pinStep = PinStep.REPEAT, pinError = null) }
            }
            PinStep.REPEAT -> if (pin == firstPin && isDecoy(pin)) {
                firstPin = ""
                _state.update { it.copy(pinStep = PinStep.CREATE, pinError = "Ese es tu PIN señuelo. Elige otro.") }
            } else if (pin == firstPin) {
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

    private fun isDecoy(pin: String): Boolean {
        val stored = prefs.getString(KEY_DECOY_HASH, null) ?: return false
        val salt = android.util.Base64.decode(prefs.getString(KEY_DECOY_SALT, "") ?: "", android.util.Base64.NO_WRAP)
        return hash(pin, salt) == stored
    }

    /** Empieza a crear el PIN señuelo, que abre una segunda carpeta privada. */
    fun createDecoyPin() {
        firstPin = ""
        _state.update { it.copy(pinStep = PinStep.DECOY_CREATE, pinError = null) }
    }

    /** Quita el PIN señuelo. Lo guardado en su carpeta sigue ahí por si se vuelve a poner. */
    fun removeDecoyPin() {
        prefs.edit().remove(KEY_DECOY_HASH).remove(KEY_DECOY_SALT).apply()
        _state.update { it.copy(hasDecoy = false) }
        say("PIN señuelo quitado")
    }

    /**
     * Abre la carpeta privada. Con PIN propio se pide aquí y, si se escribe el señuelo, se abre la
     * otra carpeta. Con el bloqueo del teléfono lo comprueba [system] y no hay señuelo.
     */
    fun openVault(system: (() -> Unit) -> Unit) {
        if (_state.value.vaultOpen) {
            open(Screen.Vault)
            return
        }
        if (!usesPin) {
            system { unlockVault(); open(Screen.Vault) }
            return
        }
        afterPin = { unlockVault(); open(Screen.Vault) }
        afterDecoy = if (_state.value.hasDecoy) ({ unlockVault(decoy = true); open(Screen.Vault) }) else null
        _state.update { it.copy(pinStep = PinStep.ASK, pinError = null) }
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

    fun markReviewed(id: Long) {
        val next = _state.value.reviewed + id
        writeIds(KEY_REVIEWED, next)
        _state.update { it.copy(reviewed = next) }
    }

    fun forgetReviewed(id: Long) {
        val next = _state.value.reviewed - id
        writeIds(KEY_REVIEWED, next)
        _state.update { it.copy(reviewed = next) }
    }

    fun setAlbumOrder(order: List<Long>) {
        albumOrder = order
        prefs.edit().putString(KEY_ALBUM_ORDER, order.joinToString(",")).apply()
        refresh()
    }

    /** Pellizco en una cuadrícula de álbum o del buscador: menos columnas al acercar, más al alejar. */
    fun zoomItems(closer: Boolean) {
        val next = (_state.value.itemColumns + if (closer) -1 else 1).coerceIn(DayColumns)
        prefs.edit().putInt(KEY_ITEM_COLUMNS, next).apply()
        _state.update { it.copy(itemColumns = next) }
    }

    /**
     * Pasa [items] a la tarjeta [volume]. Primero los copia; después [onCopied] recibe los que se
     * copiaron bien, para pedir al sistema permiso para borrarlos del teléfono.
     */
    fun moveToCard(items: List<MediaItem>, volume: String, onCopied: (List<MediaItem>) -> Unit) = viewModelScope.launch {
        val pending = items.filter { !it.onCard }
        if (pending.isEmpty()) {
            say("Ya están en la tarjeta")
            return@launch
        }
        say("Copiando a la tarjeta…")
        val copied = withContext(Dispatchers.IO) {
            copyToCard(getApplication(), pending, volume) { n ->
                if (n % 25 == 0) viewModelScope.launch { say("Copiando a la tarjeta: $n de ${pending.size}") }
            }
        }
        if (copied.isEmpty()) {
            say("No se pudo copiar a la tarjeta")
        } else {
            say("Copiadas a la tarjeta. Ahora confirma que se borren del teléfono.")
            onCopied(copied)
        }
        reload()
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

    /** Guarda como foto el instante [positionMs] del vídeo de una foto en movimiento. */
    fun saveMotionFrame(item: MediaItem, positionMs: Long) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val file = com.lumi.galeria.data.motionVideoFile(context, item) ?: return@withContext false
            val frame = runCatching {
                android.media.MediaMetadataRetriever().run {
                    try {
                        setDataSource(file.path)
                        getFrameAtTime(positionMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                    } finally {
                        release()
                    }
                }
            }.getOrNull() ?: return@withContext false
            val path = if (com.lumi.galeria.data.isWritableAlbumPath(item.path)) item.path else "Pictures/Lumi/"
            saveNewPhoto(context, frame, item.name.substringBeforeLast('.') + "_instante_" + positionMs, path)
        }
        say(if (ok) "Instante guardado como foto nueva" else "No se pudo guardar ese instante")
        if (ok) reload()
    }

    /** Guarda el vídeo de una foto en movimiento como vídeo aparte. */
    fun saveMotionVideo(item: MediaItem) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { com.lumi.galeria.data.saveMotionVideo(getApplication(), item) }
        say(if (ok) "Vídeo guardado junto a la foto" else "No se pudo guardar el vídeo")
        if (ok) reload()
    }

    /** Guarda lo que devolvió el escáner: cada página como foto y, además, todo junto en un PDF. */
    fun saveScan(pages: List<android.net.Uri>, pdf: android.net.Uri?) = viewModelScope.launch {
        val saved = withContext(Dispatchers.IO) { saveScanned(getApplication(), pages, pdf) }
        say(if (saved == 0) "No se pudo guardar el documento" else "Documento guardado en el álbum Documentos y como PDF")
        if (saved > 0) reload()
    }

    fun rename(item: MediaItem, name: String) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { renameItem(getApplication(), item, name) }
        if (ok) offerAction("Nombre cambiado") { restoreName(getApplication(), item) } else say("No se pudo cambiar el nombre")
        if (ok) reload()
    }

    fun toggleFavorite(ids: Collection<Long>) {
        val current = _state.value.favorites
        val next = if (current.containsAll(ids)) current - ids.toSet() else current + ids
        writeIds(KEY_FAVORITES, next)
        _state.update { it.copy(favorites = next) }
        if (_state.value.filter == TileFilter.FAVORITES) refresh()
    }

    // --- Importar de una cámara o una memoria ---

    var importState by mutableStateOf(ImportJob())
        private set
    private var importJob: Job? = null

    fun scanForImport(tree: Uri) {
        if (importState.copying) return
        importJob?.cancel()
        importJob = viewModelScope.launch {
            val context = getApplication<Application>()
            // Sin esto, Android retira el permiso sobre la carpeta en cuanto se sale de la pantalla.
            runCatching { context.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = withContext(Dispatchers.IO) { com.lumi.galeria.data.importSourceName(context, tree) }
            importState = ImportJob(source = name, scanning = true)
            val library = _state.value.items + _state.value.lockedItems
            val files = withContext(Dispatchers.IO) {
                com.lumi.galeria.data.scanImport(context, tree, library) { n -> importState = importState.copy(found = n) }
            }
            importState = ImportJob(source = name, files = files)
        }
    }

    /** Copia [files] al álbum [album]. Sigue aunque se salga de la pantalla. */
    fun runImport(files: List<com.lumi.galeria.data.Importable>, album: String) {
        if (importState.copying || files.isEmpty()) return
        importJob = viewModelScope.launch {
            importState = importState.copy(copying = true, finished = false, album = album, done = 0, failed = 0, total = files.size)
            var done = 0
            var failed = 0
            withContext(Dispatchers.IO) {
                for (file in files) {
                    if (com.lumi.galeria.data.importOne(getApplication(), file, album)) done++ else failed++
                    importState = importState.copy(done = done, failed = failed)
                }
            }
            importState = importState.copy(copying = false, finished = true)
            if (backStack.lastOrNull() != Screen.Import) say("${countText(done, "foto importada", "fotos importadas")} a $album")
            reload()
        }
    }

    fun clearImport() {
        if (importState.copying) return
        importState = ImportJob()
    }

    // --- Búsqueda ---

    /** Fotos copiadas o cortadas, a la espera de pegarse en un álbum. */
    var clip by mutableStateOf<Clip?>(null)

    /** Lo escrito y lo elegido en el buscador. Viven aquí para no perderse al abrir una foto y volver. */
    var searchQuery by mutableStateOf("")
    var searchFilters by mutableStateOf(Filters())

    /** Abre el buscador en blanco. */
    fun openSearch() {
        searchQuery = ""
        searchFilters = Filters()
        open(Screen.Search)
    }

    /** Resultados de [query] con [filters], y las opciones que quedan por elegir. */
    suspend fun runSearch(query: String, filters: Filters): Found {
        val now = _state.value
        val found = withContext(Dispatchers.Default) {
            val people = HashMap<Long, MutableList<String>>()
            now.people.forEach { person -> person.name?.let { name -> person.photos.forEach { people.getOrPut(it) { ArrayList() } += name } } }
            find(query, filters, now.items, now.index, now.favorites, now.places, people)
        }
        _state.update { it.copy(searchResults = found.results) }
        return found
    }

    /** Pares ya comparados: "original:copia:fechas" -> son la misma imagen. */
    private val compared = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Quita de cada grupo de copias las que, comparadas imagen contra imagen, no son iguales a
     * la original. La huella rápida puede equivocarse; esto no deja pasar el error.
     */
    suspend fun verifiedDuplicates(groups: List<List<MediaItem>>): List<List<MediaItem>> = withContext(Dispatchers.IO) {
        groups.mapNotNull { group ->
            val keep = group.first()
            val same = group.drop(1).filter { other ->
                compared.getOrPut("${keep.id}:${other.id}:${keep.modified}:${other.modified}") { sameImage(getApplication(), keep, other) }
            }
            if (same.isEmpty()) null else listOf(keep) + same
        }
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
    /** Lee las líneas de texto de una foto con su sitio, para poder tocarlas encima de ella. */
    fun readTextPage(item: MediaItem, onDone: (com.lumi.galeria.data.TextPage) -> Unit) = viewModelScope.launch {
        say("Leyendo el texto…")
        val page = withContext(Dispatchers.IO) { indexer.readTextPage(item) }
        if (page == null || page.lines.isEmpty()) say("No se ve texto en esta foto") else onDone(page)
    }

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

    /** Lo que dice la barra de «Deshacer» de mover, copiar o renombrar; null si no hay nada que deshacer. */
    var undoText by mutableStateOf<String?>(null)
        private set
    private var undoRun: (() -> Unit)? = null

    /** Enseña [text] con un «Deshacer» durante unos segundos; si se pulsa, se ejecuta [undo]. */
    private fun offerAction(text: String, undo: () -> Unit) {
        clearUndo()
        undoRun = undo
        undoText = text
        undoJob = viewModelScope.launch {
            delay(7000)
            undoText = null
            undoRun = null
        }
    }

    fun runUndo() {
        val run = undoRun ?: return
        clearUndo()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { run() }
            say("Deshecho")
            reload()
        }
    }

    /** Tras mandar [items] a la papelera, ofrece deshacerlo durante unos segundos. */
    fun offerUndo(items: List<MediaItem>) {
        undoJob?.cancel()
        undoText = null
        undoRun = null
        _state.update { it.copy(undo = items) }
        undoJob = viewModelScope.launch {
            delay(7000)
            _state.update { it.copy(undo = emptyList()) }
        }
    }

    fun clearUndo() {
        undoJob?.cancel()
        undoText = null
        undoRun = null
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

    /** Por dónde va lo que se está creando (GIF, exportar, vídeo de recuerdos), de 0 a 100; null si nada. */
    var making by mutableStateOf<Int?>(null)
        private set

    fun animate(photos: List<MediaItem>, width: Int, delayCs: Int, bounce: Boolean) {
        if (making != null) return
        viewModelScope.launch {
            making = 0
            val ok = runCatching {
                withContext(Dispatchers.Default) { com.lumi.galeria.data.makeBurstGif(getApplication(), photos, width, delayCs, bounce) { making = it } }
            }.getOrDefault(false)
            making = null
            say(if (ok) "GIF guardado en el álbum Lumi" else "No se pudo crear el GIF")
            if (ok) {
                if (backStack.lastOrNull() is Screen.Animate) back()
                reload()
            }
        }
    }

    /** Exporta [photos] y, al terminar, entrega a [then] las copias creadas. */
    fun exportPhotos(photos: List<MediaItem>, options: com.lumi.galeria.data.ExportOptions, then: (List<Uri>) -> Unit) {
        if (making != null) return
        viewModelScope.launch {
            making = 0
            val made = ArrayList<Uri>()
            withContext(Dispatchers.IO) {
                photos.forEachIndexed { i, photo ->
                    com.lumi.galeria.data.exportPhoto(getApplication(), photo, options)?.let { made += it }
                    making = (i + 1) * 100 / photos.size
                }
            }
            making = null
            when {
                made.isEmpty() -> say("No se pudo exportar")
                made.size < photos.size -> say("${made.size} de ${photos.size} exportadas a «Lumi Exportadas»")
                else -> say("${countText(made.size, "foto exportada", "fotos exportadas")} a «Lumi Exportadas»")
            }
            if (made.isNotEmpty()) {
                if (backStack.lastOrNull() is Screen.Export) back()
                then(made)
                reload()
            }
        }
    }

    fun memoryVideo(photos: List<MediaItem>, music: Uri?, title: String, pace: com.lumi.galeria.data.MemoryPace) {
        if (making != null) return
        viewModelScope.launch {
            making = 0
            val ok = runCatching {
                com.lumi.galeria.data.makeMemoryVideo(getApplication(), photos, music, title, pace) { making = it }
            }.getOrDefault(false)
            making = null
            say(if (ok) "Vídeo guardado en el álbum Lumi" else "No se pudo montar el vídeo")
            if (ok) {
                if (backStack.lastOrNull() is Screen.MemoryVideo) back()
                reload()
            }
        }
    }

    /** Guarda [bitmap] como foto nueva junto a [item], sin salir de la pantalla. */
    fun savePhotoNextTo(item: MediaItem, bitmap: Bitmap, suffix: String, done: String) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            saveNewPhoto(getApplication(), bitmap, item.name.substringBeforeLast('.') + suffix, if (com.lumi.galeria.data.isWritableAlbumPath(item.path)) item.path else "Pictures/Lumi/")
        }
        say(if (ok) done else "No se pudo guardar")
        if (ok) reload()
    }

    /** Por dónde va el guardado del vídeo editado, de 0 a 100; null si no se está guardando. */
    var videoExport by mutableStateOf<Int?>(null)
        private set

    fun exportVideo(item: MediaItem, edit: com.lumi.galeria.data.VideoEdit) {
        if (videoExport != null) return
        viewModelScope.launch {
            videoExport = 0
            val ok = runCatching {
                com.lumi.galeria.data.exportVideo(getApplication(), item, edit) { videoExport = it }
            }.getOrDefault(false)
            videoExport = null
            say(if (ok) "Vídeo nuevo guardado junto al original" else "Este vídeo no se pudo editar")
            if (ok) {
                if (backStack.lastOrNull() is Screen.VideoEditor) back()
                reload()
            }
        }
    }

    fun keepStack(stack: PhotoStack) {
        kept = kept + stack.members.map { it.id }
        writeIds(KEY_KEPT, kept)
        reload()
    }

    // --- Archivos ---

    fun copyTo(items: List<MediaItem>, path: String, album: String) = viewModelScope.launch {
        val created = withContext(Dispatchers.IO) { copyItems(getApplication(), items, path) }
        if (created.isEmpty()) say("No se pudo copiar")
        else offerAction("${countText(created.size, "copiada", "copiadas")} a $album") {
            // Las copias las creó Lumi: se pueden borrar sin pedir permiso.
            created.forEach { runCatching { getApplication<Application>().contentResolver.delete(it, null, null) } }
        }
        reload()
    }

    /** Solo después de que el sistema haya concedido el permiso de escritura sobre [items]. */
    fun moveTo(items: List<MediaItem>, path: String, album: String) = viewModelScope.launch {
        val done = withContext(Dispatchers.IO) { moveItems(getApplication(), items, path) }
        if (done == 0) say("No se pudo mover")
        else offerAction("${countText(done, "movida", "movidas")} a $album") { restorePaths(getApplication(), items) }
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

    fun unlockVault(decoy: Boolean = _state.value.vaultDecoy) = viewModelScope.launch {
        val items = withContext(Dispatchers.IO) { (if (decoy) LumiApp.decoy else LumiApp.vault).list() }
        _state.update { it.copy(vaultOpen = true, vaultDecoy = decoy, vaultItems = items) }
    }

    fun lockVault() {
        if (!_state.value.vaultOpen) return
        _state.update { it.copy(vaultOpen = false, vaultDecoy = false, vaultItems = emptyList()) }
        backStack.removeAll { it == Screen.Vault || (it is Screen.Viewer && it.source is Source.External) }
        if (backStack.isEmpty()) backStack.add(Screen.Timeline)
        viewModelScope.launch(Dispatchers.IO) {
            LumiApp.vault.clearOpened()
            LumiApp.decoy.clearOpened()
        }
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
        const val KEY_DECOY_HASH = "decoyHash"
        const val KEY_FACES = "faces"
        const val KEY_HIGHLIGHTS = "dayHighlights"
        const val KEY_NOT_THEM = "notThem"
        const val KEY_DECOY_SALT = "decoySalt"
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
        const val KEY_DAY_COLUMNS = "dayColumns"
        const val KEY_ACCENT = "accent"
        const val KEY_BLACK = "pureBlack"
        const val KEY_ALBUM_VIEW = "albumView"
        const val KEY_ALBUM_ORDER = "albumOrder"
        const val KEY_REVIEWED = "reviewedKept"
        const val KEY_ITEM_COLUMNS = "itemColumns"
        const val KEY_LANGUAGE = "language"
    }
}

private fun buildCells(
    tiles: List<MediaItem>,
    stackByBest: Map<Long, PhotoStack>,
    level: Level,
    memory: Memory?,
    stories: Boolean,
    /** Elige la foto estrella de un día; null para no destacar ninguna. */
    star: ((List<MediaItem>) -> MediaItem?)? = null,
): List<Cell> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val out = ArrayList<Cell>(tiles.size + 64)
    // La fila de recuerdos encabeza la vista completa; qué enseña lo decide la pantalla.
    if (stories) out += Cell.Recall(memory)
    var groupKey = Long.MIN_VALUE
    var headerIndex = -1
    var count = 0

    fun closeGroup() {
        if (headerIndex < 0) return
        out[headerIndex] = (out[headerIndex] as Cell.Header).copy(count = count)
        // La estrella del día pasa delante de las demás.
        val choose = star ?: return
        val photos = out.subList(headerIndex + 1, out.size)
        val best = choose(photos.map { (it as Cell.Photo).item }) ?: return
        val at = photos.indexOfFirst { (it as Cell.Photo).item.id == best.id }
        if (at < 0) return
        val cell = (photos.removeAt(at) as Cell.Photo).copy(star = true)
        photos.add(0, cell)
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
