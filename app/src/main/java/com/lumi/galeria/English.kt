package com.lumi.galeria

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumi.galeria.data.WORDS

/** En qué idioma habla la app. */
enum class AppLanguage(val label: String) { SYSTEM("El del teléfono"), SPANISH("Español"), ENGLISH("English") }

/**
 * Los textos de la app están escritos en español dentro del código. Cuando toca enseñarlos en
 * inglés se traducen justo antes de pintarlos, con [tr]: así un mismo texto vale para los dos
 * idiomas y añadir otro es escribir otra tabla como [EN].
 */
object Lang {
    var english by mutableStateOf(false)
        private set

    fun apply(choice: AppLanguage) {
        english = when (choice) {
            AppLanguage.SPANISH -> false
            AppLanguage.ENGLISH -> true
            // Con el teléfono en cualquier idioma que no sea español, inglés.
            AppLanguage.SYSTEM -> android.content.res.Resources.getSystem().configuration.locales[0].language != "es"
        }
    }
}

private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

/** [text] en el idioma de la app. Lo que no tiene traducción se deja como está. */
fun tr(text: String): String {
    if (!Lang.english || text.isEmpty()) return text
    cache[text]?.let { return it }
    val out = translate(text)
    if (cache.size < 4000) cache[text] = out
    return out
}

private fun translate(text: String): String {
    EN[text]?.let { return it }
    for ((pattern, build) in PATTERNS) {
        val match = pattern.matchEntire(text) ?: continue
        return build(match.groupValues)
    }
    // Varias piezas unidas con un punto medio: cada una por su cuenta.
    if (" · " in text) return text.split(" · ").joinToString(" · ") { translate(it) }
    // Una cosa reconocida en la foto ("perro"), que el modelo ya nombra en inglés.
    thing(text)?.let { return it }
    // Varias cosas seguidas ("perro, playa").
    if (", " in text) {
        val parts = text.split(", ").map { thing(it) ?: return text }
        return parts.joinToString(", ")
    }
    return text
}

/** Palabra en español de cada cosa que reconoce el modelo -> su nombre en inglés. */
private val THINGS: Map<String, String> by lazy {
    HashMap<String, String>().apply { WORDS.forEach { (label, words) -> words.firstOrNull()?.let { putIfAbsent(it, label.lowercase()) } } }
}

private fun thing(text: String): String? {
    val found = THINGS[text.lowercase()] ?: return null
    return if (text.first().isUpperCase()) found.replaceFirstChar { it.uppercase() } else found
}

private fun rule(pattern: String, build: (List<String>) -> String) = Regex(pattern) to build

/** Textos con un dato dentro (un número, un nombre, un tamaño). */
private val PATTERNS: List<Pair<Regex, (List<String>) -> String>> = listOf(
    rule("Hace (\\d+) años") { "${it[1]} years ago" },
    rule("Lo mejor de (.+)") { "Best of ${it[1].replaceFirstChar(Char::uppercase)}" },
    rule("Mover (\\d+) aquí") { "Move ${it[1]} here" },
    rule("Mover (\\d+) a la papelera") { "Move ${it[1]} to the trash" },
    rule("Devolver (\\d+) a la galería") { "Return ${it[1]} to the gallery" },
    rule("Copiando (\\d+) de (\\d+)") { "Copying ${it[1]} of ${it[2]}" },
    rule("(.+) sin copiar") { "${it[1]} not backed up" },
    rule("(.+ (?:copied|moved)) a (.+)") { "${it[1]} to ${translate(it[2])}" },
    rule("(.+ returned) a la galería") { "${it[1]} to the gallery" },
    rule("Última copia: (.+)") { "Last backup: ${it[1]}" },
    rule("Nada con «(.*)»") { "Nothing for “${it[1]}”" },
    rule("(.+)\\. Lumi no distingue «(.+)»: te enseña lo más parecido que reconoce\\.") {
        "${it[1]}. Lumi can't tell “${it[2]}” apart: it shows the closest thing it recognizes."
    },
    rule("Lumi ha mirado (\\d+) de (.+)\\. Ya puedes buscar en esas\\.") { "Lumi has looked at ${it[1]} of ${it[2]}. You can already search those." },
    rule("Falta leer el texto de (.+)\\. Buscar por cosas, lugares y fechas ya funciona\\.") {
        "Text still has to be read in ${it[1]}. Searching by things, places and dates already works."
    },
    rule("Puedes recuperar (.+)\\. Toca para ver cómo\\.") { "You can free up ${it[1]}. Tap to see how." },
    rule("Puedes recuperar (.+)") { "You can free up ${it[1]}" },
    rule("Todo copiado en (.+)") { "Everything backed up to ${it[1]}" },
    rule("Mirando qué hay en cada foto: (\\d+) de (\\d+)") { "Looking at each photo: ${it[1]} of ${it[2]}" },
    rule("Leyendo el texto de las fotos: faltan (\\d+)") { "Reading text in photos: ${it[1]} to go" },
    rule("(Fotos|Vídeos|Capturas) (\\d.*)") { "${translate(it[1])} ${it[2]}" },
    rule("(.+) libres de (.+)") { "${it[1]} free of ${it[2]}" },
    rule("Restaurar (\\d+)") { "Restore ${it[1]}" },
    rule("Borrar (\\d+)") { "Delete ${it[1]}" },
    rule("(\\d+) días") { "${it[1]} days" },
    rule("De (\\d.*) a (\\d.*)") { "From ${it[1]} to ${it[2]}" },
    rule("El recorte dura (.+) de (.+)") { "The clip lasts ${it[1]} of ${it[2]}" },
    rule("Brillo (\\d+) %") { "Brightness ${it[1]}%" },
    rule("Volumen (\\d+) %") { "Volume ${it[1]}%" },
    rule("Pila de (\\d+) parecidas") { "Stack of ${it[1]} similar" },
    rule("Velocidad (.+)×") { "Speed ${it[1]}×" },
    rule("Cerca de (.+)") { "Near ${it[1]}" },
    rule("Conservar las (\\d+)") { "Keep all ${it[1]}" },
    rule("Ahora pesa (.+)\\. Se guarda una copia más ligera; el original sigue ahí hasta que tú lo borres\\.") {
        "It is ${it[1]} now. A lighter copy is saved; the original stays until you delete it."
    },
    rule("Convirtiendo… (\\d+) %\\. No salgas de esta pantalla hasta que termine\\.") { "Converting… ${it[1]}%. Stay on this screen until it finishes." },
    rule("unos (.+)") { "about ${it[1]}" },
    // "Agosto de 2025" o "mayo de 2025": una sola palabra, el mes, delante del año. Va la última
    // para no confundirse con frases como "12 de 2000".
    rule("(\\p{L}+) de (\\d{4})") {
        val month = it[1].replaceFirstChar(Char::uppercase)
        "${EN[month] ?: it[1]} ${it[2]}"
    },
)

/** Textos fijos. La clave es el texto en español tal como está en el código. */
private val EN: Map<String, String> = hashMapOf(
    // Fechas, cantidades y palabras sueltas
    "Hoy" to "Today", "Ayer" to "Yesterday", "hoy" to "today", "ayer" to "yesterday", "Mañana" to "Tomorrow",
    "Año" to "Year", "Mes" to "Month", "Día" to "Day",
    "Enero" to "January", "Febrero" to "February", "Marzo" to "March", "Abril" to "April", "Mayo" to "May", "Junio" to "June",
    "Julio" to "July", "Agosto" to "August", "Septiembre" to "September", "Octubre" to "October", "Noviembre" to "November", "Diciembre" to "December",
    "foto" to "photo", "fotos" to "photos", "elemento" to "item", "elementos" to "items", "resultado" to "result", "resultados" to "results",
    "archivo" to "file", "archivos" to "files", "carpeta" to "folder", "carpetas" to "folders", "elegida" to "chosen", "elegidas" to "chosen", "seleccionada" to "selected", "seleccionadas" to "selected",
    "página" to "page", "páginas" to "pages", "copiada" to "copied", "copiadas" to "copied", "movida" to "moved", "movidas" to "moved",
    "devuelta" to "returned", "devueltas" to "returned", "archivo copiado" to "file copied", "archivos copiados" to "files copied",
    "enviada a la papelera" to "sent to the trash", "enviadas a la papelera" to "sent to the trash",
    "foto de estos días" to "photo from these days", "fotos de estos días" to "photos from these days",
    "foto parecida" to "similar photo", "fotos parecidas" to "similar photos", "oculto" to "hidden", "vacía" to "empty", "Vacía" to "Empty",

    // Navegación y acciones comunes
    "Fotos" to "Photos", "Álbumes" to "Albums", "Buscar" to "Search", "Ajustes" to "Settings", "Volver" to "Back", "Guardar" to "Save",
    "Cancelar" to "Cancel", "Aceptar" to "OK", "Cerrar" to "Close", "Borrar" to "Delete", "Enviar" to "Share", "Editar" to "Edit", "Más" to "More",
    "Copiar" to "Copy", "Quitar" to "Remove", "Crear" to "Create", "Poner" to "Add", "Deshacer" to "Undo", "Ordenar" to "Sort", "Favorita" to "Favorite",
    "Favorita ✓" to "Favorite ✓", "Álbum" to "Album", "Privada" to "Private", "Listo" to "Done", "Comparar" to "Compare", "Nombre" to "Name",
    "Tamaño" to "Size", "Todo" to "All", "Vídeos" to "Videos", "Capturas" to "Screenshots", "Favoritas" to "Favorites", "Con texto" to "With text",
    "Más recientes" to "Newest", "Más antiguas" to "Oldest", "Cantidad de fotos" to "Number of photos",
    "Cámara" to "Camera", "Descargas" to "Downloads", "Vídeos de WhatsApp" to "WhatsApp videos", "Sin carpeta" to "No folder",

    // Bienvenida, permiso y bloqueo
    "Tus fotos se quedan en tu teléfono" to "Your photos stay on your phone",
    "Lumi necesita verlas para enseñártelas, ordenarlas y dejarte buscarlas. Nada más." to "Lumi needs to see them to show, sort and search them for you. Nothing else.",
    "Sin permiso de internet: nada sale de aquí" to "No internet permission: nothing leaves this phone",
    "Sin cuenta, sin anuncios y sin suscripción" to "No account, no ads and no subscription",
    "Candado con huella o PIN, si lo quieres" to "Fingerprint or PIN lock, if you want it",
    "Dar acceso a mis fotos" to "Give access to my photos",
    "Si ya lo rechazaste, ábrelo desde los ajustes" to "If you already said no, open it from settings",
    "Lumi está bloqueada" to "Lumi is locked",
    "Usa tu huella o el bloqueo del teléfono para ver tus fotos." to "Use your fingerprint or your phone lock to see your photos.",
    "Desbloquear" to "Unlock", "Usa tu huella o tu cara" to "Use your fingerprint or face",
    "Usa tu huella, tu cara o el bloqueo del teléfono" to "Use your fingerprint, face or phone lock", "Usar el PIN" to "Use PIN",
    "Bloqueo del teléfono" to "Phone lock", "PIN propio de Lumi" to "Lumi's own PIN", "Ese no es el PIN" to "That is not the PIN",
    "PIN guardado" to "PIN saved", "No coinciden. Empieza otra vez." to "They don't match. Start again.",
    "Escribe tu PIN" to "Enter your PIN", "Elige un PIN" to "Choose a PIN", "Escríbelo otra vez" to "Enter it again",
    "El PIN de Lumi, no el del teléfono" to "Lumi's PIN, not your phone's", "De 4 a 8 cifras" to "4 to 8 digits", "Huella" to "Fingerprint",
    "Pon un bloqueo de pantalla en el teléfono, o usa un PIN propio de Lumi desde Ajustes" to "Set a screen lock on your phone, or use Lumi's own PIN from Settings",
    "Para bloquear álbumes, pon un bloqueo en el teléfono o crea un PIN de Lumi en Ajustes" to "To lock albums, set a lock on your phone or create a Lumi PIN in Settings",

    // Pantalla Fotos
    "Aún no hay fotos" to "No photos yet", "Las fotos y los vídeos de tu teléfono aparecerán aquí." to "Your phone's photos and videos will show up here.",
    "Nada con este filtro" to "Nothing with this filter", "Toca «Todo» para volver a ver la biblioteca completa." to "Tap “All” to see the whole library again.",
    "Aquí solo se pueden elegir fotos" to "Only photos can be picked here", "Aquí solo se pueden elegir vídeos" to "Only videos can be picked here",
    "Elige fotos" to "Pick photos", "Elige una foto" to "Pick a photo", "Hacer una foto" to "Take a photo",
    "Lumi solo ve las fotos que elegiste." to "Lumi only sees the photos you chose.", "Elegir más" to "Choose more", "Ver todas" to "See all",
    "Toca las fotos que quieras enviar" to "Tap the photos you want to send", "Toca una foto para elegirla" to "Tap a photo to pick it",
    "Hace un año" to "A year ago", "Mes anterior" to "Previous month", "Mes siguiente" to "Next month", "Viaje" to "Trip",
    "No se encontró una app de cámara" to "No camera app found", "Este teléfono no permite ventanas flotantes" to "This phone does not allow floating windows",

    // Selección
    "Mover a" to "Move to", "Marcar como favoritas" to "Add to favorites", "Copiar a un álbum" to "Copy to an album",
    "Comparar dos fotos" to "Compare two photos", "Elige exactamente dos fotos para compararlas" to "Pick exactly two photos to compare them",
    "Juntar en un PDF" to "Combine into a PDF", "Elige al menos una foto para el PDF" to "Pick at least one photo for the PDF",
    "Un PDF admite hasta 40 fotos" to "A PDF takes up to 40 photos", "Hacer un collage" to "Make a collage",
    "Elige entre 2 y 6 fotos para el collage" to "Pick between 2 and 6 photos for the collage", "Mover a…" to "Move to…", "Copiar a…" to "Copy to…",
    "No se pudo copiar" to "Could not copy", "No se pudo mover" to "Could not move", "No se pudo guardar" to "Could not save", "No se pudo abrir" to "Could not open",

    // Álbumes
    "Nuevo álbum" to "New album", "Álbum nuevo" to "New album", "Con tu huella" to "With your fingerprint", "Tus carpetas" to "Your folders",
    "Mantén pulsada una para fijarla arriba, bloquearla u ocultarla." to "Press and hold one to pin, lock or hide it.",
    "Con candado" to "Locked", "Hechos por Lumi" to "Made by Lumi", "Bloqueado" to "Locked",
    "Toca y pon la contraseña para verlo" to "Tap and unlock to see it",
    "Después eliges qué fotos van dentro. Cuando exista, mantenlo pulsado para bloquearlo." to "Next you choose which photos go inside. Once it exists, press and hold it to lock it.",
    "Elegir fotos" to "Choose photos", "Toca las fotos que van en este álbum" to "Tap the photos that go in this album",
    "Este álbum está vacío" to "This album is empty", "Sus fotos se han movido o borrado." to "Its photos were moved or deleted.",
    "Opciones del álbum" to "Album options", "Poner candado" to "Lock", "Quitar el candado" to "Remove the lock", "Bloquear con candado" to "Lock",
    "Dejar de fijar" to "Unpin", "Fijar arriba" to "Pin to top", "Dejar de ocultar" to "Unhide", "Ocultar álbum" to "Hide album",
    "Cambiar el nombre" to "Rename", "Android no deja cambiar el nombre de esta carpeta" to "Android does not allow renaming this folder",
    "Borrar el álbum" to "Delete album", "Álbum oculto. Se puede volver a ver desde Ajustes." to "Album hidden. You can show it again from Settings.",
    "Álbum bloqueado" to "Album locked", "Candado quitado" to "Lock removed", "Portada del álbum cambiada" to "Album cover changed",
    "Aún no tienes favoritas" to "No favorites yet", "Toca el corazón al ver una foto y la tendrás siempre aquí." to "Tap the heart on a photo and it will always be here.",
    "Aquí ya no hay fotos" to "No photos here any more", "Se han movido o borrado." to "They were moved or deleted.",
    "Comida" to "Food", "Mascotas" to "Pets", "Playa y mar" to "Beach and sea", "Montaña y nieve" to "Mountains and snow", "Naturaleza" to "Nature",
    "Atardeceres" to "Sunsets", "Ciudad" to "City", "Gente" to "People", "Coches y motos" to "Cars and bikes", "Celebraciones" to "Celebrations", "Papeles" to "Papers",

    // Buscador
    "Nombre, álbum o texto de la foto" to "Name, album or text in the photo", "Nada con estos filtros" to "Nothing with these filters",
    "Prueba con una sola palabra, o usa los filtros de arriba." to "Try a single word, or use the filters above.",
    "Prueba a quitar algún filtro de arriba." to "Try removing one of the filters above.", "Quizá querías decir" to "Did you mean",
    "Cuándo" to "When", "Dónde" to "Where", "Qué hay" to "What's in it", "Recientes" to "Recent", "Lugares" to "Places", "Cosas" to "Things",
    "Toca los filtros de arriba y combínalos. Si prefieres escribir, vale el nombre de un álbum o de un archivo y cualquier texto que aparezca en la foto." to
        "Tap the filters above and combine them. If you prefer to type, an album or file name works, and so does any text that appears in a photo.",

    // Visor y reproductor
    "Reproducir" to "Play", "Velocidad 1×" to "Speed 1×", "Sin sonido" to "Muted", "Con sonido" to "Sound on", "Repetir" to "Repeat",
    "Fotograma" to "Save frame", "Girar" to "Rotate", "Flotante" to "Pop-out", "Bloquear toques" to "Lock screen",
    "Pantalla bloqueada. Mantén pulsado para desbloquear." to "Screen locked. Press and hold to unlock.",
    "Texto de la foto" to "Text in the photo", "Copiar todo" to "Copy all", "Texto copiado" to "Text copied", "Detalles" to "Details",
    "Fecha" to "Date", "Archivo" to "File", "Resolución" to "Resolution", "Duración" to "Duration", "Carpeta" to "Folder",
    "Guardada en" to "Stored on", "Tarjeta de memoria" to "Memory card", "Memoria del teléfono" to "Phone storage", "Lugar" to "Place",
    "Lumi ve" to "Lumi sees", "Disparo" to "Shutter", "Flash" to "Flash", "Disparado" to "Fired",
    "Presentación" to "Slideshow", "Pasa las fotos solas cada tres segundos; un toque la detiene" to "Moves to the next photo every three seconds; a tap stops it",
    "Dibujar o escribir encima" to "Draw or write on it", "Se guarda en una copia" to "Saved as a copy",
    "Copiar el texto de la foto" to "Copy the text in the photo", "Lee lo que hay escrito para pegarlo donde quieras" to "Reads what is written so you can paste it anywhere",
    "Quitar el fondo" to "Remove background", "Deja solo a la persona u objeto principal" to "Keeps only the main person or object",
    "Reducir el peso" to "Reduce file size", "Guarda una copia que ocupa menos; el original no se toca" to "Saves a copy that takes less space; the original is untouched",
    "Usar como portada del álbum" to "Use as album cover", "Enviar sin ubicación" to "Share without location",
    "Se manda una copia sin el lugar donde se hizo" to "Sends a copy without the place where it was taken", "Usar como…" to "Use as…", "Usar como" to "Use as",
    "Fondo de pantalla, foto de contacto y otros" to "Wallpaper, contact photo and more", "Mover a un álbum" to "Move to an album",
    "Mover a la carpeta privada" to "Move to the private folder", "Se guarda cifrada y desaparece de la galería" to "Stored encrypted and removed from the gallery",
    "Leyendo el texto…" to "Reading the text…", "No se ve texto en esta foto" to "No text found in this photo",
    "Nombre cambiado" to "Renamed", "No se pudo cambiar el nombre" to "Could not rename",
    "Fotograma guardado como foto" to "Frame saved as a photo", "No se pudo sacar el fotograma" to "Could not grab the frame",
    "En la tarjeta de memoria" to "On the memory card",
    "Pila" to "Stack", "Mejor toma" to "Best shot", "Elegida por nitidez. Toca otra si prefieres quedarte con ella." to "Picked for sharpness. Tap another one if you would rather keep it.",
    "Has elegido otra toma. Las demás irán a la papelera, donde siguen 30 días." to "You picked another shot. The rest go to the trash, where they stay 30 days.",
    "Quedarme con la mejor" to "Keep the best", "Quedarme con esta" to "Keep this one",

    // Vídeos más ligeros
    "Alta" to "High", "Media" to "Medium", "Ligera" to "Light", "1080p. En el móvil se ve casi igual" to "1080p. Looks almost the same on a phone",
    "720p. Buena para enviar" to "720p. Good for sharing", "480p. La que menos ocupa" to "480p. The smallest",
    "Este vídeo ya ocupa poco: convertirlo no ahorraría espacio." to "This video is already small: converting it would not save space.",
    "Un vídeo largo puede tardar varios minutos y calentar el teléfono." to "A long video can take several minutes and warm up the phone.",
    "Guardar una copia más ligera" to "Save a lighter copy",
    "Copia más ligera guardada. El original sigue en su sitio." to "Lighter copy saved. The original is still where it was.",
    "Este vídeo no se deja convertir" to "This video cannot be converted",

    // Editor y herramientas
    "Libre" to "Free", "No hay memoria suficiente para esta foto" to "Not enough memory for this photo", "Guardando…" to "Saving…",
    "Guardar una copia" to "Save a copy", "Reemplazar la original" to "Replace the original", "No se puede editar esta foto" to "This photo cannot be edited",
    "El formato no se deja abrir." to "The format cannot be opened.", "Antes" to "Before", "Después" to "After", "Enderezar" to "Straighten",
    "Girar 90°" to "Rotate 90°", "Espejo" to "Mirror", "Brillo" to "Brightness", "Contraste" to "Contrast", "Color" to "Color", "Calidez" to "Warmth",
    "Viñeta" to "Vignette", "Desvanecer" to "Fade", "Tono" to "Hue", "Recortar" to "Crop", "Luz y color" to "Light and color", "Filtros" to "Filters",
    "Efectos" to "Effects", "Antes y después" to "Before and after", "Deshacer todo" to "Reset all", "Vívido" to "Vivid", "Cálido" to "Warm",
    "Frío" to "Cool", "Suave" to "Soft", "Blanco y negro" to "Black and white",
    "Foto original reemplazada" to "Original photo replaced", "Copia guardada junto a la original" to "Copy saved next to the original",
    "Pellizca en cualquiera: las dos se mueven juntas" to "Pinch on either one: both move together", "Borrar esta" to "Delete this one",
    "Transparente" to "Transparent", "Blanco" to "White", "Negro" to "Black",
    "Google Play está descargando el recortador. Con conexión a internet tarda alrededor de un minuto; vuelve a intentarlo después." to
        "Google Play is downloading the cut-out tool. With an internet connection it takes about a minute; try again afterwards.",
    "No se pudo separar nada del fondo en esta foto." to "Nothing could be separated from the background in this photo.",
    "Separando del fondo…" to "Separating from the background…", "Recorte guardado en el álbum Lumi" to "Cut-out saved in the Lumi album",
    "Collage guardado en el álbum Lumi" to "Collage saved in the Lumi album", "No hay memoria suficiente para este collage" to "Not enough memory for this collage",
    "Separación" to "Spacing", "Fondo oscuro" to "Dark background", "Dibujar" to "Draw", "Guardar copia" to "Save copy",
    "Copia guardada con tus dibujos" to "Copy saved with your drawings", "No se puede abrir esta foto" to "This photo cannot be opened",
    "El formato no se deja editar." to "The format cannot be edited.", "Texto" to "Text", "Fino" to "Thin", "Medio" to "Medium", "Grueso" to "Thick",
    "Arrastra para colocar el texto. Toca «Texto» para añadir otro." to "Drag to place the text. Tap “Text” to add another.",
    "Escribe el texto" to "Type the text", "Este vídeo es demasiado corto para recortarlo" to "This video is too short to trim",
    "Recortar vídeo" to "Trim video", "Guardar recorte" to "Save clip", "Recortando el vídeo…" to "Trimming the video…",
    "Recorte guardado como vídeo nuevo" to "Clip saved as a new video", "Este vídeo no se deja recortar" to "This video cannot be trimmed",
    "Se guarda como vídeo nuevo, sin perder calidad. El principio puede quedar un instante antes de donde lo marcaste." to
        "Saved as a new video with no quality loss. The start may fall a moment before the point you marked.",
    "No se pudo crear el PDF" to "Could not create the PDF", "PDF guardado en Documentos, carpeta Lumi" to "PDF saved in Documents, Lumi folder",
    "PDF nuevo" to "New PDF", "Toca una página y muévela con las flechas para cambiar el orden." to "Tap a page and move it with the arrows to change the order.",
    "← Antes" to "← Move left", "Después →" to "Move right →", "Hoja A4" to "A4 page", "Tamaño de la foto" to "Photo size", "Creando…" to "Creating…",
    "Guardar y enviar" to "Save and share",

    // Carpeta privada, copia y carpetas ocultas
    "Carpeta privada" to "Private folder", "Aquí no hay nada todavía" to "Nothing here yet",
    "Elige fotos en la galería y usa «Mover a la carpeta privada». Se guardan cifradas y dejan de verse en la galería, la búsqueda y los recuerdos." to
        "Pick photos in the gallery and use “Move to the private folder”. They are stored encrypted and no longer show in the gallery, search or memories.",
    "Mantén pulsada una foto para sacarla de aquí o borrarla. La carpeta se cierra al salir de Lumi." to
        "Press and hold a photo to take it out or delete it. The folder closes when you leave Lumi.",
    "Guardando en la carpeta privada…" to "Saving to the private folder…", "Copia" to "Backup", "Tus fotos en un segundo sitio" to "Your photos in a second place",
    "Destino" to "Destination", "Sin elegir" to "Not set",
    "Puede ser una tarjeta SD, una memoria USB conectada al teléfono o cualquier carpeta. Para llevarlas al PC, copia a una memoria USB o a una carpeta y pásala por cable." to
        "It can be an SD card, a USB drive plugged into the phone or any folder. To take them to a PC, back up to a USB drive or a folder and transfer it by cable.",
    "Elegir carpeta" to "Choose folder", "Cambiar carpeta" to "Change folder", "Todo está copiado" to "Everything is backed up",
    "Aún no se ha hecho ninguna copia." to "No backup has been made yet.",
    "Se ordenan en carpetas por año y mes dentro de «Lumi». Solo se copia lo nuevo." to "They are sorted into year and month folders inside “Lumi”. Only new files are copied.",
    "Copiando…" to "Copying…", "Copiar ahora" to "Back up now", "Carpeta elegida" to "Folder chosen", "No se pudo preparar la copia" to "Could not prepare the copy",
    "No había nada nuevo que copiar" to "Nothing new to back up",
    "Carpetas ocultas" to "Hidden folders", "Hace falta un permiso especial" to "A special permission is needed",
    "Algunas apps guardan fotos y vídeos en carpetas que Android esconde a las galerías. Para verlas, Lumi necesita el acceso a todos los archivos, que se concede en los ajustes del sistema. Sigue sin salir nada del teléfono." to
        "Some apps keep photos and videos in folders that Android hides from galleries. To see them, Lumi needs access to all files, granted in the system settings. Still nothing leaves the phone.",
    "Abrir los ajustes del permiso" to "Open the permission settings", "Ya lo he concedido" to "I have granted it", "Buscando…" to "Searching…",
    "Lumi está recorriendo las carpetas del teléfono." to "Lumi is going through the phone's folders.", "No hay nada escondido" to "Nothing is hidden",
    "Ninguna carpeta oculta tiene fotos ni vídeos." to "No hidden folder has photos or videos.",
    "Se pueden ver, pero no borrar ni mover desde aquí." to "They can be viewed, but not deleted or moved from here.",

    // Espacio y papelera
    "Repetidas" to "Similar photos", "Se conserva la mejor toma de cada pila." to "The best shot of each stack is kept.",
    "Vídeos grandes" to "Large videos", "Vídeos de más de 50 MB, del más pesado al más ligero." to "Videos over 50 MB, heaviest first.",
    "Capturas antiguas" to "Old screenshots", "Capturas de pantalla de hace más de 30 días." to "Screenshots older than 30 days.",
    "Quizá borrosas" to "Maybe blurry", "Pueden estar movidas o desenfocadas. Marca solo las que quieras quitar." to "They may be shaky or out of focus. Mark only the ones you want to remove.",
    "Duplicadas" to "Duplicates", "El mismo archivo guardado más de una vez. De cada grupo se conserva una copia." to "The same file saved more than once. One copy of each group is kept.",
    "Espacio" to "Storage", "Todo en orden" to "All tidy", "No hay repetidas, vídeos grandes ni capturas antiguas que revisar." to "No similar photos, large videos or old screenshots to review.",
    "que puedes recuperar si quieres" to "that you can free up if you want", "Ves todo antes de borrar. Lo que quites pasa 30 días en la papelera." to "You see everything before deleting. What you remove stays 30 days in the trash.",
    "Toca las que quieras quitar" to "Tap the ones you want to remove", "Papelera" to "Trash", "La papelera está vacía" to "The trash is empty",
    "Lo que borres se guarda aquí 30 días por si te arrepientes." to "What you delete is kept here for 30 days in case you change your mind.",
    "Cada elemento se borra solo, para siempre, cuando se cumplen sus 30 días. En rojo, los que se van en tres días o menos." to
        "Each item is deleted for good once its 30 days are up. In red, the ones that go in three days or less.",
    "Restaurar todo" to "Restore all", "Vaciar" to "Empty",

    // Ajustes
    "Buscar un ajuste" to "Search settings", "Aspecto" to "Appearance", "Tema" to "Theme", "Sistema" to "System", "Claro" to "Light", "Oscuro" to "Dark",
    "Lila" to "Lilac", "Coral" to "Coral", "Verde" to "Green", "Azul" to "Blue", "Ámbar" to "Amber", "Negro puro" to "Pure black",
    "Fondo negro del todo en el tema oscuro. Gasta menos batería en pantallas OLED." to "Fully black background in the dark theme. Uses less battery on OLED screens.",
    "Idioma" to "Language", "El del teléfono" to "Same as phone", "Privacidad" to "Privacy", "Pedir huella al abrir Lumi" to "Ask for fingerprint when opening Lumi",
    "Usa tu huella o el bloqueo del teléfono (PIN, patrón o contraseña)." to "Uses your fingerprint or phone lock (PIN, pattern or password).",
    "Cómo se desbloquea" to "How it unlocks", "Con lo que uses en el teléfono: huella, cara, PIN, patrón o contraseña." to "With whatever you use on the phone: fingerprint, face, PIN, pattern or password.",
    "Con un PIN solo para Lumi, distinto del teléfono. La huella sigue valiendo como atajo." to "With a PIN just for Lumi, different from the phone's. Your fingerprint still works as a shortcut.",
    "Cambiar el PIN" to "Change PIN", "Mostrar álbumes ocultos" to "Show hidden albums",
    "Los álbumes que ocultaste vuelven a verse, marcados como ocultos." to "The albums you hid show again, marked as hidden.",
    "Fotos cifradas que solo se abren con tu huella o tu bloqueo" to "Encrypted photos that only open with your fingerprint or lock",
    "Carpetas ocultas del sistema" to "Hidden system folders", "Fotos y vídeos que otras apps guardan donde las galerías no miran" to "Photos and videos other apps keep where galleries do not look",
    "Tus fotos" to "Your photos", "Orden de la pantalla Fotos" to "Order of the Photos screen", "Recientes primero" to "Newest first", "Antiguas primero" to "Oldest first",
    "Liberar espacio" to "Free up space", "Repetidas, vídeos grandes y capturas antiguas" to "Similar photos, large videos and old screenshots",
    "Usar la papelera" to "Use the trash", "Lo que borras se guarda 30 días y después se elimina solo, para siempre." to "What you delete is kept 30 days and then removed for good.",
    "Lo que borras se elimina en el acto y no se puede recuperar." to "What you delete is removed at once and cannot be recovered.",
    "Copia de seguridad" to "Backup", "A tarjeta SD, memoria USB o una carpeta" to "To an SD card, USB drive or folder",
    "Análisis de las fotos" to "Photo analysis", "Sin fotos que analizar" to "No photos to analyze", "Todo analizado" to "All photos analyzed",
    "Sirve para buscar, para los álbumes de cosas y viajes y para detectar repetidas. Se hace en el teléfono, despacio, y se detiene mientras tocas la pantalla." to
        "Used for search, for the albums of things and trips, and to spot similar photos. It runs on your phone, slowly, and pauses while you touch the screen.",
    "Acerca de" to "About", "Sin anuncios, sin cuenta y sin permiso de internet: tus fotos no salen del teléfono." to "No ads, no account and no internet permission: your photos never leave the phone.",
    "Si no está aquí, borra lo escrito para ver todos los ajustes." to "If it is not here, clear what you typed to see every setting.",
    "Toca para revisar repetidas, vídeos grandes y capturas antiguas." to "Tap to review similar photos, large videos and old screenshots.",
)
