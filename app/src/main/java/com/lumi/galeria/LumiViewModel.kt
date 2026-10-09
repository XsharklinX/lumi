package com.lumi.galeria

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.sync.withLock
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

    /** Las fotos de una persona. [key] es la marca de su grupo («g12»). */
    data class Person(val key: String) : Screen

    /** «¿Son la misma persona?», de pareja en pareja. */
    data object MergePeople : Screen

    /** «¿Es Lucía?»: las caras dudosas de una persona, una a una. */
    data class Doubts(val key: String) : Screen

    /** Un grupo de personas (Familia, Amigos…) como álbum. */
    data class Circle(val name: String) : Screen

    /** Cómo ha crecido: su cara a lo largo del tiempo, en un GIF. */
    data class Growing(val key: String) : Screen

    /** Pestaña Documentos: escaneos y PDF. */
    data object Documents : Screen

    /** Un PDF, página a página. */
    data class DocView(val uri: String) : Screen

    /** Guardar un escaneo recién hecho: nombre propuesto y tipo. */
    data object SaveDoc : Screen

    /** Las dos caras de un DNI o una tarjeta en una página. */
    data object IdCard : Screen

    /** Editar las páginas de un PDF ([uri] vacío: uno nuevo). */
    data class PdfEdit(val uri: String) : Screen

    /** Dibujar la firma o las iniciales. */
    data class Signature(val initials: Boolean) : Screen

    /** Firmar un PDF ([pdf]) o una foto. */
    data class SignDoc(val uri: String, val pdf: Boolean, val photoId: Long = -1) : Screen

    /** La Cámara Lumi. */
    data object Camera : Screen

    /** Todo lo que puede hacer Lumi. */
    data object Tour : Screen

    /** Fotos oscuras o apagadas que se pueden mejorar con Lumi Auto. */
    data object Enhance : Screen

    /** Fotos con datos personales que conviene pasar a la carpeta privada. */
    data object Sensitive : Screen

    /** Dejar el móvil a alguien para que vea solo [ids]. */
    data class Show(val ids: List<Long>) : Screen

    /** El fondo de pantalla que cambia solo. */
    data object AutoWallpaper : Screen

    /** Todos los ajustes de la Cámara Lumi. */
    data object CameraSettings : Screen

    /** Una carpeta de álbumes hecha en Lumi. */
    data class AlbumFolder(val id: Long) : Screen

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

/** Cómo se ordenan las personas (las fijadas y las que tienen nombre van siempre antes). */
enum class PeopleOrder(val label: String) { COUNT("Más fotos"), NAME("Nombre"), RECENT("Recientes") }

/** Cuándo se ve Lumi en blanco en la vista de apps recientes. */
/** Una carpeta de álbumes hecha en Lumi: un nombre y los álbumes que agrupa. */
class AlbumFolder(val id: Long, val name: String, val albums: List<Long>)

enum class RecentsHide(val label: String) { NEVER("Nunca"), PRIVATE("Con lo privado abierto"), ALWAYS("Siempre") }

/** Qué cámara abre el botón de cámara. */
enum class CameraChoice(val label: String) { ASK("Preguntar cada vez"), PHONE("La del teléfono"), LUMI("Cámara Lumi") }

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
    /** Lo que no se luce (capturas, papeles, datos personales): fuera de recuerdos, portadas y widgets. */
    val showcaseOut: Set<Long> = emptySet(),
    /** Fotos con datos personales: documentos de identidad, tarjetas y contraseñas. */
    val sensitive: Map<Long, com.lumi.galeria.data.Sensitive> = emptyMap(),
    /** Foto -> cómo de bien salen las caras. */
    val faceScore: Map<Long, Float> = emptyMap(),
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
    /** Android deja a Lumi borrar y mover sin pedir confirmación cada vez («Gestión de contenido multimedia»). */
    val manageMedia: Boolean = false,
    /** La vista por días destaca la mejor foto de cada día. */
    val dayHighlights: Boolean = true,
    /** Personas que salen en las fotos, agrupadas por su cara. */
    val people: List<com.lumi.galeria.data.Person> = emptyList(),
    /** Buscar caras está activado (si no, está en pausa; lo encontrado se queda). */
    val facesOn: Boolean = true,
    /** Minutos que faltan, más o menos, para mirar todas las fotos con gente; 0 si no se sabe. */
    val facesEta: Int = 0,
    val peopleOrder: PeopleOrder = PeopleOrder.COUNT,
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
    /** Foto -> nombres de las personas que salen en ella. Se calcula solo si se pide (TalkBack). */
    val namesByPhoto: Map<Long, List<String>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        HashMap<Long, MutableList<String>>().apply {
            people.forEach { person -> person.name?.let { name -> person.photos.forEach { getOrPut(it) { ArrayList() } += name } } }
        }
    }

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
            peopleOrder = runCatching { PeopleOrder.valueOf(prefs.getString(KEY_PEOPLE_ORDER, null) ?: "COUNT") }.getOrDefault(PeopleOrder.COUNT),
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
            // Después, las caras: primero las fotos en las que el análisis ya vio gente (de la más
            // reciente a la más antigua) y, tras leer el texto, el resto.
            val shown = visible(library.active)
            withContext(Dispatchers.IO) { faceFinder.prune(shown) }
            publishPeople()
            val (withPeople, rest) = withContext(Dispatchers.Default) { faceFinder.pending(shown, _state.value.index) }
            if (_state.value.facesOn && withPeople.isNotEmpty()) {
                startFaceClock(shown)
                withContext(Quiet.dispatcher) { faceFinder.scan(withPeople, { _state.value.facesOn }) { publishPeople() } }
                publishPeople()
            }
            withContext(Quiet.dispatcher) {
                indexer.scan(library.active, canReadPlace, deep = true) { publishIndex(library.active) }
            }
            publishIndex(library.active)
            if (_state.value.facesOn && rest.isNotEmpty()) {
                withContext(Quiet.dispatcher) { faceFinder.scan(rest, { _state.value.facesOn }) { publishPeople() } }
                publishPeople()
            }
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
            val out = settings.showcaseOut
            val memory = if (settings.filter != TileFilter.ALL) null else buildMemory(unstacked.filter { it.id !in out })?.let { m ->
                Memory(m.title, m.items, com.lumi.galeria.data.pickCover(m.items, settings.favorites, settings.index, faces, out))
            }
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
                albums = buildAlbums(all, lockedAlbums, hiddenAlbums, settings.albumSort, pinnedAlbums, covers, albumOrder) { list ->
                    com.lumi.galeria.data.pickCover(list, settings.favorites, settings.index, faces, out)
                },
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
        openPending()
        if (withIndex) publishIndex(all)
    }

    /** El análisis avanzó: solo cambia lo que depende de él. La cuadrícula no se toca. */
    private suspend fun publishIndex(all: List<MediaItem>) {
        val items = visible(all)
        val next = withContext(Dispatchers.Default) {
            val index = indexer.snapshot()
            val photos = items.size
            val names = placeNames(items, index)
            val out = HashSet<Long>()
            val sensitive = HashMap<Long, com.lumi.galeria.data.Sensitive>()
            for (item in items) {
                val entry = index[item.id]
                if (com.lumi.galeria.data.keepOutOfShowcase(item, entry)) out += item.id
                if (!item.isVideo) com.lumi.galeria.data.sensitiveKind(entry)?.let { sensitive[item.id] = it }
            }
            val favorites = _state.value.favorites
            val faces = faceScores
            UiState(
                autoAlbums = (buildTrips(items, index) + buildThings(items, index)).map { album ->
                    // Papeles y fotos con texto sí llevan uno de ellos de portada: es lo que son.
                    if (album.key == "cosa:documentos" || album.key == "cosa:texto") album
                    else album.withCover(com.lumi.galeria.data.pickCover(album.items, favorites, index, faces, out))
                },
                showcaseOut = out,
                sensitive = sensitive,
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
        val outChanged = next.showcaseOut != _state.value.showcaseOut
        showcaseOut = next.showcaseOut
        _state.update {
            it.copy(
                showcaseOut = next.showcaseOut,
                sensitive = next.sensitive,
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
        if (outChanged) {
            // Cambian el recuerdo del día y las portadas; los widgets, el fondo y los avisos lo leen de un archivo.
            refresh()
            withContext(Dispatchers.IO) { com.lumi.galeria.data.writeShowcaseOut(getApplication(), next.showcaseOut) }
            com.lumi.galeria.widget.Widgets.refreshAll(getApplication())
        }
    }

    /** Copia de [UiState.showcaseOut] a mano para lo que se calcula fuera del estado. */
    private var showcaseOut: Set<Long> = emptySet()

    /** Foto -> cómo de bien salen las caras: sonrisas suman, ojos cerrados restan. Solo fotos con caras. */
    private var faceScores: Map<Long, Float> = emptyMap()

    /** Cuándo empezó la búsqueda de caras en curso y cuántas fotos con gente había ya miradas, para estimar lo que falta. */
    private var faceStart = 0L
    private var faceStartLooked = 0

    /** Vuelve a leer las personas con lo que se sabe ahora. Es rápido: no se agrupa nada de nuevo. */
    private suspend fun publishPeople() {
        val all = lastLibrary?.first ?: return
        val items = visible(all)
        val index = _state.value.index
        val order = _state.value.peopleOrder
        var blinks: Set<Long> = emptySet()
        var looked = 0
        var total = 0
        var hidden = 0
        val next = withContext(Dispatchers.Default) {
            val alive = items.mapTo(HashSet()) { it.id }
            val faces = faceFinder.snapshot().filter { it.photo in alive }
            val byPhoto = faces.groupBy { it.photo }
            blinks = byPhoto.filterValues { list -> list.any { it.blinking } }.keys
            faceScores = byPhoto.mapValues { (_, list) ->
                list.sumOf { f -> ((if (f.smile >= 0f) f.smile else 0.3f) - (if (f.blinking) 1.5f else 0f)).toDouble() }.toFloat() / list.size
            }
            looked = faceFinder.lookedWithPeople(items, index)
            total = faceFinder.withPeople(items, index)
            hidden = faceFinder.hiddenCount()
            val dates = items.associate { it.id to it.date }
            sortPeople(faceFinder.people(alive, avoid = showcaseOut), order, dates)
        }
        val blinksChanged = blinks != _state.value.closedEyes
        // Lo que falta, al ritmo de esta búsqueda.
        val eta = if (faceStart > 0 && looked > faceStartLooked && total > looked) {
            val perPhoto = (System.currentTimeMillis() - faceStart) / (looked - faceStartLooked).toFloat()
            ((total - looked) * perPhoto / 60_000f).toInt() + 1
        } else 0
        _state.update {
            it.copy(closedEyes = blinks, people = next, facesLooked = looked, facesTotal = total, hiddenPeople = hidden, facesEta = eta, faceScore = faceScores)
        }
        // Las personas con nombre, para elegirlas en el widget de fotos.
        withContext(Dispatchers.IO) {
            com.lumi.galeria.widget.Widgets.writePeople(
                getApplication(),
                next.mapNotNull { p -> p.name?.let { name -> com.lumi.galeria.widget.WidgetPerson(p.group.id, name, p.photos.filter { it !in showcaseOut }) } },
            )
        }
        // La mejor toma de cada pila y la foto estrella de cada día dependen de las caras.
        if (blinksChanged) refresh()
    }

    private fun sortPeople(people: List<com.lumi.galeria.data.Person>, order: PeopleOrder, dates: Map<Long, Long>): List<com.lumi.galeria.data.Person> {
        val byOrder = when (order) {
            PeopleOrder.COUNT -> compareByDescending<com.lumi.galeria.data.Person> { it.photos.size }
            PeopleOrder.NAME -> compareBy<com.lumi.galeria.data.Person> { com.lumi.galeria.data.normalize(it.name ?: "~") }.thenByDescending { it.photos.size }
            PeopleOrder.RECENT -> compareByDescending<com.lumi.galeria.data.Person> { p -> p.photos.maxOfOrNull { dates[it] ?: 0L } ?: 0L }
        }
        // Las fijadas arriba y, después, las que tienen nombre.
        return people.sortedWith(compareByDescending<com.lumi.galeria.data.Person> { it.pinned }.thenByDescending { it.name != null }.then(byOrder))
    }

    /** Tras un cambio del usuario: se guarda (ya lo hizo [faceFinder]) y se vuelve a enseñar. */
    private fun peopleChanged() {
        viewModelScope.launch { publishPeople() }
    }

    fun facesOf(photo: Long): List<com.lumi.galeria.data.Face> = faceFinder.facesOf(photo)

    /**
     * Pone nombre a [person]. Si otra persona ya se llama así, las dos pasan a ser la misma: así
     * se junta a alguien que salía en dos grupos.
     */
    fun namePerson(person: com.lumi.galeria.data.Person, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        val twin = _state.value.people.firstOrNull { it.key != person.key && it.name.equals(clean, ignoreCase = true) }
        val top = backStack.lastOrNull()
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                if (twin != null) {
                    faceFinder.merge(twin.group.id, person.group.id)
                } else {
                    faceFinder.rename(person.group.id, clean)
                }
            }
            publishPeople()
            if (twin != null) {
                say("Juntada con ${twin.name}")
                if (top is Screen.Person && top.key == person.key) replaceTop(Screen.Person(twin.key))
            }
        }
    }

    /** Deja de enseñar a [person]. Se puede deshacer desde Ajustes. */
    fun hidePerson(person: com.lumi.galeria.data.Person) {
        faceFinder.setHidden(person.group.id, true)
        peopleChanged()
        if (backStack.lastOrNull() is Screen.Person) back()
        say("Persona oculta. Puedes volver a verla desde Ajustes.")
    }

    fun showHiddenPeople() {
        faceFinder.showAllHidden()
        peopleChanged()
    }

    /** Estas fotos no son de [person]. */
    fun notThisPerson(person: com.lumi.galeria.data.Person, photos: List<MediaItem>) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { faceFinder.rejectPhotos(person.group.id, photos.map { it.id }) }
            publishPeople()
            say("Quitadas de ${person.name ?: "esta persona"}")
        }
    }

    /** Respuesta a «¿Son la misma persona?». */
    fun answerMerge(a: com.lumi.galeria.data.Person, b: com.lumi.galeria.data.Person, same: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                if (same) {
                    // Se queda la que tiene nombre; si ninguna, la que tiene más fotos.
                    val (into, from) = if (b.name != null && a.name == null || (a.name == null && b.photos.size > a.photos.size)) b to a else a to b
                    faceFinder.merge(into.group.id, from.group.id)
                } else {
                    faceFinder.keepApart(a.group.id, b.group.id)
                }
            }
            publishPeople()
        }
    }

    /** Respuesta a «¿Es Lucía?». */
    fun answerDoubt(doubt: com.lumi.galeria.data.Doubt, yes: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                if (yes) faceFinder.confirm(doubt.face, doubt.person.group.id) else faceFinder.reject(doubt.face, doubt.person.group.id)
            }
            publishPeople()
        }
    }

    suspend fun mergeSuggestions(): List<Pair<com.lumi.galeria.data.Person, com.lumi.galeria.data.Person>> =
        withContext(Dispatchers.Default) { faceFinder.mergeSuggestions(_state.value.people) }

    suspend fun doubtsOf(person: com.lumi.galeria.data.Person): List<com.lumi.galeria.data.Doubt> =
        withContext(Dispatchers.Default) { faceFinder.doubts(person) }

    /** La cara [face] de una foto es de [person]. */
    fun faceIs(face: com.lumi.galeria.data.Face, person: com.lumi.galeria.data.Person) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { faceFinder.confirm(face, person.group.id) }
            publishPeople()
            say("Es ${person.name ?: "esa persona"}")
        }
    }

    /** La cara [face] es de alguien nuevo, que se llama [name]. */
    fun faceIsNew(face: com.lumi.galeria.data.Face, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        val twin = _state.value.people.firstOrNull { it.name.equals(clean, ignoreCase = true) }
        if (twin != null) return faceIs(face, twin)
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val current = _state.value.people.firstOrNull { it.group.id == face.group }
                // Si su grupo no tiene nombre, el nombre es para todo el grupo; si lo tiene, es otra persona.
                if (current != null && current.name == null) faceFinder.rename(face.group, clean) else faceFinder.startPerson(face, clean)
            }
            publishPeople()
        }
    }

    fun setPersonPinned(person: com.lumi.galeria.data.Person, pinned: Boolean) {
        faceFinder.setPinned(person.group.id, pinned)
        peopleChanged()
    }

    fun setPersonCover(person: com.lumi.galeria.data.Person, face: com.lumi.galeria.data.Face) {
        faceFinder.setCover(person.group.id, face.key)
        peopleChanged()
        say("Portada cambiada")
    }

    fun setPeopleOrder(order: PeopleOrder) {
        prefs.edit().putString(KEY_PEOPLE_ORDER, order.name).apply()
        _state.update { it.copy(peopleOrder = order) }
        peopleChanged()
    }

    /** Pausa o sigue la búsqueda de caras. Pausar no borra nada: las personas siguen ahí. */
    fun setFacesOn(on: Boolean) {
        prefs.edit().putBoolean(KEY_FACES, on).apply()
        _state.update { it.copy(facesOn = on) }
        if (on) reload()
    }

    /** Borra todo lo que se sabe de las caras, nombres incluidos. Solo tras confirmarlo. */
    fun clearFaces() = viewModelScope.launch {
        withContext(Dispatchers.IO) { faceFinder.clear() }
        circles = emptyMap()
        writeCircles()
        _state.update { it.copy(people = emptyList(), facesLooked = 0, hiddenPeople = 0, closedEyes = emptySet()) }
        say("Borrado todo lo de las caras")
    }

    // --- Grupos de personas: Familia, Amigos… ---

    /** Nombre del grupo -> marcas de las personas que lo forman. */
    var circles by mutableStateOf(readCircles())
        private set

    private fun readCircles(): Map<String, Set<String>> = runCatching {
        val o = org.json.JSONObject(prefs.getString(KEY_CIRCLES, "{}") ?: "{}")
        o.keys().asSequence().associateWith { key ->
            val a = o.getJSONArray(key)
            (0 until a.length()).mapTo(LinkedHashSet()) { a.getString(it) }
        }
    }.getOrDefault(emptyMap())

    private fun writeCircles() {
        val o = org.json.JSONObject()
        circles.forEach { (name, members) -> o.put(name, org.json.JSONArray(members.toList())) }
        prefs.edit().putString(KEY_CIRCLES, o.toString()).apply()
    }

    fun saveCircle(name: String, members: Set<String>, old: String? = null) {
        val clean = name.trim().take(30)
        if (clean.isEmpty() || members.isEmpty()) return
        circles = (if (old != null) circles - old else circles) + (clean to members)
        writeCircles()
    }

    fun deleteCircle(name: String) {
        circles = circles - name
        writeCircles()
    }

    /**
     * Fotogramas de «Cómo ha crecido»: una cara por época (año, o mes si hay pocos años), con la
     * cara siempre en el mismo sitio y la fecha escrita abajo.
     */
    suspend fun growingFrames(person: com.lumi.galeria.data.Person, side: Int = 480): List<Bitmap> = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val byId = _state.value.items.associateBy { it.id }
        val zone = java.time.ZoneId.systemDefault()
        val faces = person.faces.filter { byId[it.photo] != null && it.right - it.left >= 0.06f }
        if (faces.isEmpty()) return@withContext emptyList()
        fun date(face: com.lumi.galeria.data.Face) = java.time.Instant.ofEpochMilli(byId.getValue(face.photo).date).atZone(zone)
        val years = faces.map { date(it).year }.toSet()
        val byYear = years.size >= 3
        val periods = faces.groupBy { if (byYear) date(it).year * 100 else date(it).year * 100 + date(it).monthValue }.toSortedMap()
        periods.values.take(48).mapNotNull { list ->
            // De cada época, la cara más grande con los ojos abiertos.
            val face = list.maxBy { (it.right - it.left) * (if (it.blinking) 0.3f else 1f) * (0.6f + it.smile.coerceAtLeast(0f)) }
            val item = byId.getValue(face.photo)
            val source = sharpThumb(context, item.uri, 1000) ?: return@mapNotNull null
            val w = source.width
            val h = source.height
            val cx = (face.left + face.right) / 2 * w
            val cy = (face.top + face.bottom) / 2 * h
            val size = maxOf((face.right - face.left) * w, (face.bottom - face.top) * h) * 2.2f
            val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            canvas.drawColor(android.graphics.Color.BLACK)
            val k = side / size
            val matrix = android.graphics.Matrix().apply {
                postTranslate(-cx, -cy + size * 0.06f)
                postScale(k, k)
                postTranslate(side / 2f, side / 2f)
            }
            canvas.drawBitmap(source, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG))
            source.recycle()
            val d = date(face)
            val label = if (byYear) d.year.toString() else monthTitle(java.time.YearMonth.of(d.year, d.monthValue))
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = side * 0.07f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setShadowLayer(side * 0.02f, 0f, 0f, android.graphics.Color.argb(200, 0, 0, 0))
            }
            canvas.drawText(label, side * 0.05f, side * 0.94f, paint)
            out
        }
    }

    fun saveGrowing(person: com.lumi.galeria.data.Person, frames: List<Bitmap>, delayCs: Int) {
        if (making != null) return
        viewModelScope.launch {
            making = 0
            val name = com.lumi.galeria.data.safeFolder(person.name ?: "persona").replace(' ', '_') + "_crece.gif"
            val ok = runCatching {
                withContext(Dispatchers.Default) { com.lumi.galeria.data.saveFramesGif(getApplication(), frames, delayCs, name) { making = it } }
            }.getOrDefault(false)
            making = null
            say(if (ok) "GIF guardado en el álbum Lumi" else "No se pudo crear el GIF")
            if (ok) reload()
        }
    }

    /** Lo que se sabe de cuánto falta por mirar, para enseñarlo mientras se buscan caras. */
    private fun startFaceClock(items: List<MediaItem>) {
        faceStart = System.currentTimeMillis()
        faceStartLooked = faceFinder.lookedWithPeople(items, _state.value.index)
    }

    fun setManageMedia(granted: Boolean) {
        if (_state.value.manageMedia != granted) _state.update { it.copy(manageMedia = granted) }
    }

    /** Se ofrece una sola vez, tras el primer borrado, quitar el aviso del sistema. */
    var offerManageMedia by mutableStateOf(false)

    fun afterTrash() {
        if (android.os.Build.VERSION.SDK_INT < 31 || _state.value.manageMedia || prefs.getBoolean(KEY_MANAGE_ASKED, false)) return
        prefs.edit().putBoolean(KEY_MANAGE_ASKED, true).apply()
        offerManageMedia = true
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
        if (item.isScreenshot || item.isVideo || item.isGif || item.id in showcaseOut) return -10f
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


    // --- Documentos ---

    /** Los PDF de Lumi, con su tipo y su texto. */
    var docs by mutableStateOf<List<com.lumi.galeria.data.Doc>>(emptyList())
        private set

    /** Se están leyendo PDF que aún no se conocían. */
    var docsReading by mutableStateOf(false)
        private set
    private var docsJob: Job? = null

    fun loadDocs() {
        docsJob?.cancel()
        docsJob = viewModelScope.launch {
            val context = getApplication<Application>()
            // Primero lo que ya se sabe, al instante; después se lee lo nuevo.
            docs = withContext(Dispatchers.IO) { com.lumi.galeria.data.loadDocs(context, readMissing = false) }
            if (docs.any { it.pages == 0 }) {
                docsReading = true
                docs = withContext(Dispatchers.IO) { com.lumi.galeria.data.loadDocs(context, readMissing = true) }
                docsReading = false
            }
        }
    }

    /** Un escaneo leído y listo para guardar: las páginas con su texto y un nombre propuesto. */
    class PendingDoc(val pages: List<com.lumi.galeria.data.DocPage>, val name: String, val kind: com.lumi.galeria.data.DocKind)

    var pendingDoc by mutableStateOf<PendingDoc?>(null)

    /** Páginas que llegan de fuera para añadirse a un PDF que se está editando. */
    var pagesForEdit by mutableStateOf<List<Uri>>(emptyList())

    /** Lo que devolvió el escáner: se lee el texto de cada página y se propone un nombre antes de guardar. */
    fun saveScan(pages: List<Uri>, @Suppress("UNUSED_PARAMETER") pdf: Uri?) {
        if (pages.isEmpty()) return
        // Si se está editando un PDF, las páginas escaneadas van a él.
        if (backStack.lastOrNull() is Screen.PdfEdit) {
            pagesForEdit = pages
            return
        }
        viewModelScope.launch {
            say("Leyendo el documento…")
            val context = getApplication<Application>()
            val made = withContext(Dispatchers.IO) {
                pages.mapNotNull { uri -> com.lumi.galeria.data.decodeForPdf(context, uri)?.let { com.lumi.galeria.data.DocPage(it, com.lumi.galeria.data.readLines(it)) } }
            }
            if (made.isEmpty()) {
                say("No se pudo leer el escaneo")
                return@launch
            }
            val lines = made.flatMap { it.lines }
            val kind = com.lumi.galeria.data.docKind(lines.joinToString("\n") { it.text })
            pendingDoc = PendingDoc(made, com.lumi.galeria.data.suggestDocName(made.first().lines, kind, Lang.english), kind)
            open(Screen.SaveDoc)
        }
    }

    fun savePendingDoc(name: String) {
        val doc = pendingDoc ?: return
        if (making != null) return
        viewModelScope.launch {
            making = 0
            val context = getApplication<Application>()
            val uri = withContext(Dispatchers.IO) {
                val file = java.io.File(context.cacheDir, "documento.pdf")
                com.lumi.galeria.data.writePdf(file, doc.pages)
                com.lumi.galeria.data.publishPdf(context, file, name.ifBlank { doc.name }).also { uri ->
                    file.delete()
                    if (uri != null) com.lumi.galeria.data.rememberDoc(context, uri, doc.pages.flatMap { it.lines }.joinToString("\n") { it.text }, doc.kind, doc.pages.size)
                }
            }
            making = null
            if (uri == null) {
                say("No se pudo guardar el PDF")
                return@launch
            }
            pendingDoc = null
            say("Guardado en Documentos")
            if (backStack.lastOrNull() == Screen.SaveDoc) back()
            loadDocs()
        }
    }

    /** Las dos caras de una tarjeta, escaneadas. */
    var pendingCard by mutableStateOf<List<Bitmap>>(emptyList())

    fun scanCard(pages: List<Uri>) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            pendingCard = withContext(Dispatchers.IO) { pages.take(2).mapNotNull { com.lumi.galeria.data.decodeForPdf(context, it, 1600) } }
            if (pendingCard.isEmpty()) say("No se pudo leer la tarjeta") else open(Screen.IdCard)
        }
    }

    fun saveCard(page: Bitmap, name: String) {
        if (making != null) return
        viewModelScope.launch {
            making = 0
            val context = getApplication<Application>()
            val uri = withContext(Dispatchers.IO) {
                val file = java.io.File(context.cacheDir, "tarjeta.pdf")
                com.lumi.galeria.data.writePdf(file, listOf(com.lumi.galeria.data.DocPage(page, emptyList())))
                com.lumi.galeria.data.publishPdf(context, file, name).also { uri ->
                    file.delete()
                    if (uri != null) com.lumi.galeria.data.rememberDoc(context, uri, name, com.lumi.galeria.data.DocKind.IDENTITY, 1)
                }
            }
            making = null
            say(if (uri != null) "Guardado en Documentos" else "No se pudo guardar")
            if (uri != null) {
                pendingCard = emptyList()
                if (backStack.lastOrNull() == Screen.IdCard) back()
                loadDocs()
            }
        }
    }

    fun importPdfs(uris: List<Uri>) = viewModelScope.launch {
        val context = getApplication<Application>()
        val done = withContext(Dispatchers.IO) { uris.count { com.lumi.galeria.data.importPdf(context, it) != null } }
        say(if (done == 0) "No se pudo traer el PDF" else countText(done, "PDF añadido a Documentos", "PDF añadidos a Documentos"))
        loadDocs()
    }

    fun renameDoc(doc: com.lumi.galeria.data.Doc, name: String) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { com.lumi.galeria.data.renamePdf(getApplication(), doc.uri, name) }
        say(if (ok) "Nombre cambiado" else "No se pudo cambiar el nombre")
        loadDocs()
    }

    fun deleteDoc(doc: com.lumi.galeria.data.Doc) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { com.lumi.galeria.data.deletePdf(getApplication(), doc.uri) }
        say(if (ok) "Documento borrado" else "No se pudo borrar")
        if (ok && backStack.lastOrNull() is Screen.DocView) back()
        loadDocs()
    }

    /** Una página al editar un PDF: de un PDF ([index] ≥ 0) o una imagen ([index] = -1), girada [quarter] cuartos. */
    data class EditPage(val source: Uri, val index: Int, val quarter: Int = 0)

    /**
     * Guarda el PDF editado. Cada página se vuelve a dibujar y a leer, para que el texto siga
     * dentro. Con [target], sustituye ese PDF; si no, crea uno nuevo llamado [name].
     */
    fun savePdfEdit(target: Uri?, name: String, pages: List<EditPage>) {
        if (making != null || pages.isEmpty()) return
        viewModelScope.launch {
            making = 0
            val context = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) {
                val made = pages.mapIndexedNotNull { i, page ->
                    val bmp = (if (page.index >= 0) com.lumi.galeria.data.renderPage(context, page.source, page.index, 1240) else com.lumi.galeria.data.decodeForPdf(context, page.source))
                        ?.let { com.lumi.galeria.data.turned(it, page.quarter) } ?: return@mapIndexedNotNull null
                    making = (i + 1) * 90 / pages.size
                    com.lumi.galeria.data.DocPage(bmp, com.lumi.galeria.data.readLines(bmp))
                }
                if (made.isEmpty()) return@withContext false
                val file = java.io.File(context.cacheDir, "editado.pdf")
                com.lumi.galeria.data.writePdf(file, made)
                val text = made.flatMap { it.lines }.joinToString("\n") { it.text }
                val uri = if (target != null && com.lumi.galeria.data.replacePdf(context, target, file)) target else com.lumi.galeria.data.publishPdf(context, file, name)
                file.delete()
                if (uri != null) com.lumi.galeria.data.rememberDoc(context, uri, text, com.lumi.galeria.data.docKind(text), made.size)
                uri != null
            }
            making = null
            say(if (ok) "PDF guardado" else "No se pudo guardar el PDF")
            if (ok) {
                if (backStack.lastOrNull() is Screen.PdfEdit) back()
                loadDocs()
            }
        }
    }

    /** Guarda páginas ya firmadas como un PDF nuevo. */
    fun saveSignedPdf(name: String, pages: List<Bitmap>) {
        if (making != null || pages.isEmpty()) return
        viewModelScope.launch {
            making = 0
            val context = getApplication<Application>()
            val uri = withContext(Dispatchers.IO) {
                val made = pages.mapIndexed { i, bmp ->
                    making = (i + 1) * 90 / pages.size
                    com.lumi.galeria.data.DocPage(bmp, com.lumi.galeria.data.readLines(bmp))
                }
                val file = java.io.File(context.cacheDir, "firmado.pdf")
                com.lumi.galeria.data.writePdf(file, made)
                val text = made.flatMap { it.lines }.joinToString("\n") { it.text }
                com.lumi.galeria.data.publishPdf(context, file, name).also { uri ->
                    file.delete()
                    if (uri != null) com.lumi.galeria.data.rememberDoc(context, uri, text, com.lumi.galeria.data.docKind(text), made.size)
                }
            }
            making = null
            say(if (uri != null) "Documento firmado guardado en Documentos" else "No se pudo guardar")
            if (uri != null) {
                if (backStack.lastOrNull() is Screen.SignDoc) back()
                loadDocs()
            }
        }
    }

    /** Dónde se guarda la firma (o las iniciales). Solo dentro de Lumi: ninguna otra app la ve. */
    fun signatureFile(initials: Boolean): java.io.File =
        java.io.File(getApplication<Application>().filesDir, if (initials) "iniciales.png" else "firma.png")

    fun saveSignature(bitmap: Bitmap, initials: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { signatureFile(initials).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        withContext(Dispatchers.Main) { say(if (initials) "Iniciales guardadas" else "Firma guardada") }
    }

    // --- Ajustes de la Cámara Lumi ---

    /** Lumi Auto: aclarar, contraste, color y nitidez al guardar cada foto. */
    var lumiAuto by mutableStateOf(prefs.getBoolean(KEY_LUMI_AUTO, true))
        private set
    /** Guardar además la foto tal como sale del sensor. */
    var keepOriginal by mutableStateOf(prefs.getBoolean(KEY_KEEP_ORIGINAL, false))
        private set
    var shutterSound by mutableStateOf(prefs.getBoolean(KEY_SHUTTER_SOUND, true))
        private set
    /** Los selfies salen como en un espejo (como se ven en la pantalla). */
    var mirrorSelfie by mutableStateOf(prefs.getBoolean(KEY_MIRROR, false))
        private set

    fun setCameraOption(key: String, on: Boolean) {
        prefs.edit().putBoolean(key, on).apply()
        when (key) {
            KEY_LUMI_AUTO -> lumiAuto = on
            KEY_KEEP_ORIGINAL -> keepOriginal = on
            KEY_SHUTTER_SOUND -> shutterSound = on
            KEY_MIRROR -> mirrorSelfie = on
        }
    }

    /** Lo que hacen las teclas de volumen mientras está abierta la Cámara Lumi ([up]: la de subir); null fuera de ella. */
    var volumeKey: ((up: Boolean) -> Unit)? = null

    /** Ajustes de la Cámara Lumi. */
    val camPrefs = com.lumi.galeria.data.CameraPrefs(app)

    /** Fotos de la cámara que aún se están procesando y guardando, por detrás. */
    var shotsPending by mutableIntStateOf(0)
        private set

    /** Una foto detrás de otra: procesarlas a la vez llenaría la memoria. */
    private val shotQueue = kotlinx.coroutines.sync.Mutex()

    /**
     * Guarda una foto de la Cámara Lumi que está en [file]: si hay algo que hacerle (Lumi Auto,
     * filtro, horizonte, espejo) se procesa; si no, se guarda tal cual, con toda su resolución.
     * Con [private], va cifrada a la carpeta privada en vez de a la galería.
     */
    fun saveShot(file: java.io.File, edit: com.lumi.galeria.data.ShotEdit, album: String, private: Boolean, onSaved: (Uri?) -> Unit) {
        shotsPending++
        viewModelScope.launch {
            val context = getApplication<Application>()
            val name = "IMG_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US).format(java.util.Date())
            val needs = edit.auto || edit.look != com.lumi.galeria.data.CameraLook.NONE || edit.mirror || kotlin.math.abs(edit.tilt) in 0.6f..8f ||
                edit.crop > 0f || (edit.heic && !private) || edit.portrait
            val uri = shotQueue.withLock { withContext(Dispatchers.Default) {
                val processed = if (needs) com.lumi.galeria.data.decodeShot(context, Uri.fromFile(file))?.let {
                    runCatching { com.lumi.galeria.data.applyShotEdit(it, edit) }.getOrNull()
                } else null
                if (private) {
                    val toStore = if (processed != null) {
                        java.io.File(context.cacheDir, "privada_${System.nanoTime()}.jpg").also { out -> out.outputStream().use { processed.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
                    } else file
                    val ok = LumiApp.vault.addFile(toStore, "$name.jpg", isVideo = false)
                    if (toStore !== file) toStore.delete()
                    return@withContext if (ok) Uri.EMPTY else null
                }
                val saved = if (processed != null) com.lumi.galeria.data.saveProcessed(context, processed, name, album, file, edit.heic)
                else saveFileToAlbum(context, file, name, album)
                if (keepOriginal && processed != null) saveFileToAlbum(context, file, name + "_original", album)
                saved
            } }
            shotsPending--
            file.delete()
            if (private) say(if (uri != null) "Guardada cifrada en la carpeta privada" else "No se pudo guardar")
            else if (uri == null) say("No se pudo guardar la foto")
            onSaved(uri?.takeIf { it != Uri.EMPTY })
            reload()
        }
    }

    /** Para otra app: procesa la foto (Lumi Auto, recorte…) y la deja en un JPEG, sin guardarla en la galería. */
    fun processShotToFile(file: java.io.File, edit: com.lumi.galeria.data.ShotEdit, onReady: (java.io.File?) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val out = shotQueue.withLock {
                withContext(Dispatchers.Default) {
                    runCatching {
                        val bmp = com.lumi.galeria.data.decodeShot(context, Uri.fromFile(file)) ?: return@runCatching file
                        val done = com.lumi.galeria.data.applyShotEdit(bmp, edit)
                        java.io.File(context.cacheDir, "entrega_${System.nanoTime()}.jpg").also { f -> f.outputStream().use { done.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
                    }.getOrNull()
                }
            }
            if (out != null && out != file) file.delete()
            onReady(out)
        }
    }

    fun processBitmapToFile(bitmap: Bitmap, edit: com.lumi.galeria.data.ShotEdit, onReady: (java.io.File?) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val file = withContext(Dispatchers.IO) {
                java.io.File(context.cacheDir, "montada_${System.nanoTime()}.jpg").also { out -> out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 97, it) } }
            }
            processShotToFile(file, edit, onReady)
        }
    }

    /** La cámara a solas (sin la galería): se lee la última copia guardada de la biblioteca, sin analizar nada. */
    fun loadQuick() {
        if (lastLibrary != null || _state.value.items.isNotEmpty()) return
        viewModelScope.launch {
            val before = withContext(Dispatchers.IO) { readSnapshot(getApplication()) }
            if (!before.isNullOrEmpty()) publish(before, emptyList(), withIndex = false)
        }
    }

    /** Guarda una foto ya montada (noche, HDR, ráfaga) con lo demás que toque. */
    fun saveBitmapShot(bitmap: Bitmap, edit: com.lumi.galeria.data.ShotEdit, album: String, private: Boolean, onSaved: (Uri?) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val file = withContext(Dispatchers.IO) {
                java.io.File(context.cacheDir, "montada_${System.currentTimeMillis()}.jpg").also { out -> out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 97, it) } }
            }
            saveShot(file, edit, album, private, onSaved)
        }
    }

    private fun saveFileToAlbum(context: Context, file: java.io.File, name: String, album: String): Uri? = runCatching {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, if (com.lumi.galeria.data.isWritableAlbumPath(album)) album else "DCIM/Camera/")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        resolver.openOutputStream(target)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(target, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        target
    }.getOrNull()

    // --- Historial de códigos QR ---

    class QrEntry(val type: String, val value: String, val time: Long)

    var qrHistory by mutableStateOf(readQrHistory())
        private set

    private fun readQrHistory(): List<QrEntry> = runCatching {
        val a = org.json.JSONArray(prefs.getString(KEY_QR_HISTORY, "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> QrEntry(o.getString("t"), o.getString("v"), o.getLong("m")) } }
    }.getOrDefault(emptyList())

    fun rememberQr(type: String, value: String) {
        if (value.isBlank()) return
        qrHistory = (listOf(QrEntry(type, value, System.currentTimeMillis())) + qrHistory.filter { it.value != value }).take(60)
        val a = org.json.JSONArray()
        qrHistory.forEach { a.put(org.json.JSONObject().put("t", it.type).put("v", it.value).put("m", it.time)) }
        prefs.edit().putString(KEY_QR_HISTORY, a.toString()).apply()
    }

    fun clearQrHistory() {
        qrHistory = emptyList()
        prefs.edit().remove(KEY_QR_HISTORY).apply()
    }

    // --- Mejorar fotos que ya existen ---

    /** Aplica Lumi Auto a [photos] y guarda cada una como copia junto a la original. */
    fun enhancePhotos(photos: List<MediaItem>) {
        if (making != null || photos.isEmpty()) return
        viewModelScope.launch {
            making = 0
            val context = getApplication<Application>()
            var done = 0
            withContext(Dispatchers.Default) {
                photos.forEachIndexed { i, item ->
                    val bmp = com.lumi.galeria.data.decodeShot(context, item.uri)
                    if (bmp != null) {
                        val better = runCatching { com.lumi.galeria.data.lumiAuto(bmp) }.getOrNull()
                        if (better != null && saveNewPhoto(context, better, item.name.substringBeforeLast('.') + "_mejorada", item.path)) done++
                    }
                    making = (i + 1) * 100 / photos.size
                }
            }
            making = null
            say(countText(done, "foto mejorada", "fotos mejoradas") + if (Lang.english) " (saved as copies)" else " (guardadas como copias)")
            if (done > 0) {
                if (backStack.lastOrNull() == Screen.Enhance) back()
                reload()
            }
        }
    }

    // --- Recorrido de lo que hace Lumi ---

    /** El recorrido ya se vio (o se saltó): no vuelve a salir solo. */
    val tourSeen: Boolean get() = prefs.getBoolean(KEY_TOUR, false)

    fun tourDone() = prefs.edit().putBoolean(KEY_TOUR, true).apply()

    // --- Acabado: abrir fotos desde fuera, ocultar en recientes, atajos de cámara ---

    /** Modo con el que se abre la Cámara Lumi la próxima vez (QR, documento…); null, el de siempre. */
    var cameraStart: String? = null

    /** Foto que pidió abrir un widget (o la cámara) antes de que la galería estuviera cargada. */
    private var pendingPhoto: Long? = null
    private var pendingEdit = false

    /** Abre [id] en el visor, pasando de una a otra como en Fotos; con [edit], en el editor. */
    fun openPhoto(id: Long, edit: Boolean = false) {
        if (id < 0) return
        pendingPhoto = id
        pendingEdit = edit
        if (_state.value.items.isNotEmpty()) openPending()
    }

    private fun openPending() {
        val id = pendingPhoto ?: return
        val items = _state.value.items
        if (items.isEmpty()) return
        pendingPhoto = null
        if (items.any { it.id == id }) {
            switchTab(Screen.Timeline)
            open(Screen.Viewer(Source.Timeline, id))
            if (pendingEdit) open(Screen.Editor(id))
        } else {
            openExternal(com.lumi.galeria.widget.Widgets.photoUri(id), false)
        }
    }

    /** Cuándo se ve Lumi en blanco en la vista de apps recientes. */
    var recentsHide by mutableStateOf(runCatching { RecentsHide.valueOf(prefs.getString(KEY_RECENTS, null) ?: "ALWAYS") }.getOrDefault(RecentsHide.ALWAYS))
        private set

    fun chooseRecentsHide(choice: RecentsHide) {
        recentsHide = choice
        prefs.edit().putString(KEY_RECENTS, choice.name).apply()
    }

    // --- Cámara ---

    /** Qué cámara abre el botón de cámara. */
    var cameraChoice by mutableStateOf(runCatching { CameraChoice.valueOf(prefs.getString(KEY_CAMERA, null) ?: "ASK") }.getOrDefault(CameraChoice.ASK))
        private set

    /** Se está preguntando qué cámara usar. */
    var askCamera by mutableStateOf(false)

    fun chooseCamera(choice: CameraChoice) {
        cameraChoice = choice
        prefs.edit().putString(KEY_CAMERA, choice.name).apply()
    }

    /** Álbum donde guarda la Cámara Lumi (carpeta relativa, p. ej. «DCIM/Camera/»). */
    var cameraAlbum by mutableStateOf(prefs.getString(KEY_CAMERA_ALBUM, "DCIM/Camera/") ?: "DCIM/Camera/")
        private set

    fun chooseCameraAlbum(path: String) {
        cameraAlbum = path
        prefs.edit().putString(KEY_CAMERA_ALBUM, path).apply()
    }

    /** Guarda en la carpeta privada una foto hecha con la Cámara Lumi (modo Privada), sin pasar por la galería. */
    fun savePrivatePhoto(file: java.io.File) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { LumiApp.vault.addFile(file, "Privada_" + System.currentTimeMillis() / 1000 + ".jpg", isVideo = false) }
        file.delete()
        say(if (ok) "Guardada cifrada en la carpeta privada" else "No se pudo guardar")
        if (_state.value.vaultOpen && !_state.value.vaultDecoy) unlockVault()
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
        // El widget de favoritas se entera al momento.
        com.lumi.galeria.widget.Widgets.refreshAll(getApplication())
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

    // --- Accesibilidad ---

    /** Tamaño de letra de Lumi (1 = el del teléfono). */
    var textScale by mutableStateOf(prefs.getFloat(KEY_TEXT_SCALE, 1f))
        private set
    var highContrast by mutableStateOf(prefs.getBoolean(KEY_CONTRAST, false))
        private set

    fun chooseTextScale(scale: Float) {
        textScale = scale
        prefs.edit().putFloat(KEY_TEXT_SCALE, scale).apply()
    }

    fun chooseHighContrast(on: Boolean) {
        highContrast = on
        prefs.edit().putBoolean(KEY_CONTRAST, on).apply()
    }

    // --- Carpetas de álbumes (solo en Lumi: las fotos no se mueven) ---

    var albumFolders by mutableStateOf(readFolders())
        private set

    private fun readFolders(): List<AlbumFolder> = runCatching {
        val a = org.json.JSONArray(prefs.getString(KEY_FOLDERS, "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val ids = o.getJSONArray("a")
            AlbumFolder(o.getLong("id"), o.getString("n"), (0 until ids.length()).map { ids.getLong(it) })
        }
    }.getOrDefault(emptyList())

    private fun saveFolders(next: List<AlbumFolder>) {
        albumFolders = next
        val a = org.json.JSONArray()
        next.forEach { f -> a.put(org.json.JSONObject().put("id", f.id).put("n", f.name).put("a", org.json.JSONArray(f.albums))) }
        prefs.edit().putString(KEY_FOLDERS, a.toString()).apply()
    }

    fun createFolder(name: String, first: Long?) {
        val id = System.currentTimeMillis()
        val cleaned = albumFolders.map { f -> AlbumFolder(f.id, f.name, f.albums.filter { it != first }) }
        saveFolders(cleaned + AlbumFolder(id, name, listOfNotNull(first)))
        say("Carpeta «$name» creada")
    }

    /** Un álbum solo puede estar en una carpeta: si estaba en otra, se cambia. */
    fun putInFolder(folderId: Long, bucketId: Long) {
        saveFolders(albumFolders.map { f ->
            val rest = f.albums.filter { it != bucketId }
            AlbumFolder(f.id, f.name, if (f.id == folderId) rest + bucketId else rest)
        })
    }

    fun takeOutOfFolder(bucketId: Long) = saveFolders(albumFolders.map { f -> AlbumFolder(f.id, f.name, f.albums.filter { it != bucketId }) })

    fun renameFolder(id: Long, name: String) = saveFolders(albumFolders.map { f -> if (f.id == id) AlbumFolder(f.id, name, f.albums) else f })

    /** Deshacer una carpeta: sus álbumes vuelven a la lista. */
    fun deleteFolder(id: Long) = saveFolders(albumFolders.filter { it.id != id })

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

    /** Bytes recién liberados, para celebrarlo; null si no hay nada que celebrar. */
    var celebration by mutableStateOf<Long?>(null)

    fun celebrate(bytes: Long) {
        if (bytes > 0) celebration = bytes
    }

    /** Agitar el móvil justo después de borrar o mover algo pregunta si deshacerlo. */
    var shakeUndo by mutableStateOf(prefs.getBoolean("shakeUndo", true))
        private set

    fun chooseShakeUndo(on: Boolean) {
        shakeUndo = on
        prefs.edit().putBoolean("shakeUndo", on).apply()
    }

    fun clearMessage() {
        messageJob?.cancel()
        _state.update { it.copy(message = null) }
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

    companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_KEPT = "kept"
        const val KEY_THEME = "theme"
        const val KEY_APP_LOCK = "appLock"
        const val KEY_USE_PIN = "usePin"
        const val KEY_PIN_HASH = "pinHash"
        const val KEY_PIN_SALT = "pinSalt"
        const val KEY_DECOY_HASH = "decoyHash"
        const val KEY_FACES = "faces"
        const val KEY_CAMERA = "cameraChoice"
        // Con cada actualización grande cambia, para que el recorrido salga una vez con lo nuevo.
        const val KEY_TOUR = "tourSeen_1.1"
        const val KEY_RECENTS = "recentsHide"
        const val KEY_LUMI_AUTO = "lumiAuto"
        const val KEY_KEEP_ORIGINAL = "keepOriginal"
        const val KEY_SHUTTER_SOUND = "shutterSound"
        const val KEY_MIRROR = "mirrorSelfie"
        const val KEY_QR_HISTORY = "qrHistory"
        const val KEY_CAMERA_ALBUM = "cameraAlbum"
        const val KEY_MANAGE_ASKED = "manageAsked"
        const val KEY_HIGHLIGHTS = "dayHighlights"
        const val KEY_PEOPLE_ORDER = "peopleOrder"
        const val KEY_CIRCLES = "peopleCircles"
        const val KEY_DECOY_SALT = "decoySalt"
        const val KEY_LOCKED_ALBUMS = "lockedAlbums"
        const val KEY_HIDDEN_ALBUMS = "hiddenAlbums"
        const val KEY_PINNED_ALBUMS = "pinnedAlbums"
        const val KEY_FOLDERS = "albumFolders"
        const val KEY_TEXT_SCALE = "textScale"
        const val KEY_CONTRAST = "highContrast"
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
