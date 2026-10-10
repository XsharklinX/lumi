package com.lumi.galeria.data

/** Los temas del catálogo «Descubre Lumi». */
enum class FeatureTopic(val label: String) {
    SEARCH("Buscar"), PEOPLE("Personas"), CAMERA("Cámara"), PHOTOS("Fotos"), VIDEO("Vídeo"), DOCS("Documentos"),
    PRIVACY("Privacidad"), ORGANIZE("Orden"), EXTRA("Y además"),
}

/** Adónde lleva «Probar». Lo que no tiene sitio propio abre la pantalla más cercana. */
enum class Go {
    NONE, SEARCH, PEOPLE, MERGE_PEOPLE, CAMERA, CAMERA_QR, CAMERA_PRO, CAMERA_PRIVATE, CAMERA_NIGHT, CAMERA_SMART, CAMERA_TIMELAPSE,
    PHONE_CAMERA, DOCUMENTS, SCAN, ID_CARD, VAULT, SPACE, SETTINGS, TOUR, ENHANCE, SENSITIVE, AUTO_WALLPAPER, YEAR, SWIPE,
    DATES, STICKERS, ALBUMS, CALENDAR, EXPORT_HINT, STATUS,
}

/** Una cosa que sabe hacer Lumi: lo que se cuenta en «¿Sabías que…?» y en el catálogo. */
class Feature(
    val id: String,
    val topic: FeatureTopic,
    val title: String,
    /** Frase corta y clara de qué hace y cómo se usa. */
    val text: String,
    val go: Go = Go.NONE,
    /** Palabras extra para el buscador del catálogo. */
    val words: String = "",
)

/**
 * Todo lo que sabe hacer Lumi, para enseñarlo poco a poco. Cada una se marca como descubierta cuando
 * el usuario la prueba desde aquí (o la usa por su cuenta, en los sitios donde Lumi se da cuenta).
 */
val FEATURES: List<Feature> = listOf(
    // Buscar
    Feature("buscar_cosas", FeatureTopic.SEARCH, "Busca por lo que se ve", "Escribe «playa», «perro» o «tickets»: Lumi sabe qué sale en cada foto, sin conexión.", Go.SEARCH, words = "buscador encontrar"),
    Feature("buscar_texto", FeatureTopic.SEARCH, "Busca el texto de las fotos", "Facturas, carteles y capturas se encuentran por lo que dicen.", Go.SEARCH, words = "ocr leer"),
    Feature("buscar_personas", FeatureTopic.SEARCH, "Busca por personas", "Prueba «Lucía y Marcos en la playa» o «solo Lucía».", Go.SEARCH, words = "caras nombres"),
    Feature("album_inteligente", FeatureTopic.SEARCH, "Álbumes inteligentes", "Guarda cualquier búsqueda como álbum: se llena solo con las fotos nuevas que encajen.", Go.SEARCH, words = "guardar busqueda"),
    Feature("notas_etiquetas", FeatureTopic.SEARCH, "Notas y etiquetas", "Abre una foto, pulsa ⓘ y escribe una nota o #etiquetas. Luego las encuentras en el buscador.", Go.NONE, words = "tags apuntes"),
    // Personas
    Feature("personas", FeatureTopic.PEOPLE, "Personas", "Lumi junta las caras de cada persona. Ponle nombre una vez y ya está.", Go.PEOPLE, words = "caras"),
    Feature("misma_persona", FeatureTopic.PEOPLE, "¿Son la misma persona?", "Desliza como unas cartas para decir si dos caras son de la misma persona.", Go.MERGE_PEOPLE, words = "unir"),
    Feature("como_ha_crecido", FeatureTopic.PEOPLE, "Cómo ha crecido", "En el perfil de una persona, su cara a lo largo de los años.", Go.PEOPLE, words = "evolucion"),
    // Cámara
    Feature("camara_lumi", FeatureTopic.CAMERA, "Cámara Lumi", "Una cámara con Lumi Auto: aclara y da color a cada foto al guardarla.", Go.CAMERA, words = "foto"),
    Feature("camara_rafaga", FeatureTopic.CAMERA, "Ráfaga que elige sola", "Mantén pulsado el disparador: haz una ráfaga y Lumi guarda solo la mejor.", Go.CAMERA, words = "burst"),
    Feature("camara_noche", FeatureTopic.CAMERA, "Noche y HDR", "Con poca luz o contraluz, Lumi junta varias fotos en una.", Go.CAMERA_NIGHT, words = "oscuro"),
    Feature("camara_pro", FeatureTopic.CAMERA, "Modo Pro", "Controla ISO, velocidad, enfoque y color con ruedas de muescas.", Go.CAMERA_PRO, words = "manual iso"),
    Feature("camara_qr", FeatureTopic.CAMERA, "Lector de QR", "Lee QR y códigos de barras; avisa de enlaces dudosos y conecta al wifi.", Go.CAMERA_QR, words = "codigo"),
    Feature("camara_sonrisa", FeatureTopic.CAMERA, "Dispara cuando sonríen", "El modo Inteligente espera a que todos sonrían y el móvil esté quieto.", Go.CAMERA_SMART, words = "sonrisa"),
    Feature("camara_timelapse", FeatureTopic.CAMERA, "Timelapse", "Una foto cada pocos segundos y Lumi monta el vídeo acelerado.", Go.CAMERA_TIMELAPSE, words = "acelerado"),
    Feature("camara_privada", FeatureTopic.CAMERA, "Cámara privada", "Las fotos van cifradas a la carpeta privada, nunca a la galería.", Go.CAMERA_PRIVATE, words = "secreta"),
    Feature("camara_telefono", FeatureTopic.CAMERA, "Lumi como cámara del teléfono", "Elígela como cámara y toca «Siempre»: otras apps también la usarán.", Go.PHONE_CAMERA, words = "predeterminada"),
    Feature("camara_retrato", FeatureTopic.CAMERA, "Retrato en cualquier móvil", "El modo Retrato desenfoca el fondo aunque tu móvil no tenga modo retrato.", Go.CAMERA, words = "fondo desenfocado"),
    // Fotos
    Feature("editor_foto", FeatureTopic.PHOTOS, "Editor de fotos", "Recorte, luz, color, curvas, tonos y detalle. Siempre guarda una copia.", Go.NONE, words = "editar"),
    Feature("editor_zonas", FeatureTopic.PHOTOS, "Retocar solo una zona", "Con un degradado, un círculo o un pincel: oscurece un cielo o aclara una cara.", Go.NONE, words = "local pincel"),
    Feature("editor_borrar", FeatureTopic.PHOTOS, "Borrar cosas pequeñas", "Pinta encima de una mota, un cable o un papel y desaparece.", Go.NONE, words = "inpaint"),
    Feature("editor_caras", FeatureTopic.PHOTOS, "Retoque de caras", "Suaviza la piel, ilumina los ojos y quita los ojos rojos con un control.", Go.NONE, words = "piel ojos"),
    Feature("editor_marcos", FeatureTopic.PHOTOS, "Marcos para redes", "Un borde blanco, estilo polaroid o con el fondo desenfocado.", Go.NONE, words = "polaroid"),
    Feature("editor_historial", FeatureTopic.PHOTOS, "Historial de cambios", "Vuelve a cualquier paso de la edición y retómala más tarde.", Go.NONE, words = "deshacer"),
    Feature("copiar_ajustes", FeatureTopic.PHOTOS, "Copiar ajustes", "Copia los ajustes de una foto y pégalos en otras: editas una vez para todas.", Go.NONE, words = "lote pegar"),
    Feature("mejorar_lote", FeatureTopic.PHOTOS, "Mejorar fotos oscuras", "Lumi encuentra las fotos oscuras o apagadas y las mejora en lote.", Go.ENHANCE, words = "aclarar"),
    Feature("sin_fondo", FeatureTopic.PHOTOS, "Quitar el fondo", "En el menú de una foto: recorta a la persona o al objeto principal.", Go.NONE, words = "recorte"),
    Feature("pegatinas", FeatureTopic.PHOTOS, "Pegatinas para WhatsApp", "Recorta a una persona o mascota y guárdala como pegatina.", Go.STICKERS, words = "stickers"),
    Feature("datos_pro", FeatureTopic.PHOTOS, "Datos de la toma", "En la información de la foto: histograma, cámara, ISO, velocidad y apertura.", Go.NONE, words = "exif"),
    Feature("lupa", FeatureTopic.PHOTOS, "Lupa", "Mantén el dedo sobre una foto para ver una lupa que amplía lo que hay debajo.", Go.NONE, words = "ampliar"),
    Feature("raw", FeatureTopic.PHOTOS, "Fotos RAW", "Las fotos DNG se ven con su etiqueta, se comparan con su JPEG y se revelan en el editor.", Go.NONE, words = "dng"),
    Feature("foto_movimiento", FeatureTopic.PHOTOS, "Fotos en movimiento", "Mantén el dedo sobre una foto en movimiento y se mueve.", Go.NONE, words = "live"),
    // Vídeo
    Feature("editor_video", FeatureTopic.VIDEO, "Editor de vídeo", "Línea de tiempo con fotogramas: divide, quita trozos y reordena.", Go.NONE, words = "cortar"),
    Feature("unir_videos", FeatureTopic.VIDEO, "Unir vídeos y fotos", "Junta varios vídeos y fotos en uno, con transiciones.", Go.NONE, words = "juntar"),
    Feature("titulos_video", FeatureTopic.VIDEO, "Títulos en el vídeo", "Textos animados que aparecen y desaparecen cuando tú digas.", Go.NONE, words = "texto"),
    Feature("musica_video", FeatureTopic.VIDEO, "Música de fondo", "Pon una canción de tu móvil, mezclada con el sonido y con fundido al final.", Go.NONE, words = "audio"),
    Feature("formato_redes", FeatureTopic.VIDEO, "Formato para redes", "Vertical o cuadrado con el fondo desenfocado en vez de franjas negras.", Go.NONE, words = "historias"),
    Feature("velocidad_tramos", FeatureTopic.VIDEO, "Velocidad por tramos", "Cámara lenta solo en un momento, o acelera lo aburrido.", Go.NONE, words = "lenta"),
    Feature("video_doble", FeatureTopic.VIDEO, "Doble velocidad", "Mantén el dedo en un vídeo para verlo al doble de velocidad.", Go.NONE, words = "2x"),
    Feature("video_saltar", FeatureTopic.VIDEO, "Saltar en un vídeo", "Dos toques a un lado saltan 10 segundos; sigue tocando para sumar.", Go.NONE, words = "adelantar"),
    // Documentos
    Feature("escaner", FeatureTopic.DOCS, "Escáner de documentos", "Endereza y limpia cada hoja y la guarda en PDF con el texto dentro.", Go.SCAN, words = "pdf"),
    Feature("dni", FeatureTopic.DOCS, "DNI en una hoja", "Escanea las dos caras de un documento y salen en una sola página.", Go.ID_CARD, words = "tarjeta"),
    Feature("firma", FeatureTopic.DOCS, "Firmar documentos", "Dibuja tu firma una vez y ponla en cualquier PDF.", Go.DOCUMENTS, words = "firmar"),
    // Privacidad
    Feature("carpeta_privada", FeatureTopic.PRIVACY, "Carpeta privada", "Fotos cifradas que solo se abren con tu huella o tu PIN.", Go.VAULT, words = "cifrada"),
    Feature("pin_senuelo", FeatureTopic.PRIVACY, "PIN señuelo", "Si te obligan a abrir la carpeta privada, otro PIN abre una carpeta falsa.", Go.SETTINGS, words = "falso"),
    Feature("datos_personales", FeatureTopic.PRIVACY, "Tu DNI, bajo llave", "Lumi encuentra documentos, tarjetas y contraseñas en tus fotos para protegerlos.", Go.SENSITIVE, words = "tarjetas"),
    Feature("modo_ensenar", FeatureTopic.PRIVACY, "Modo enseñar", "Presta el móvil: solo se ven las fotos que elijas y no se puede salir.", Go.NONE, words = "prestar"),
    Feature("sin_ubicacion", FeatureTopic.PRIVACY, "Enviar sin ubicación", "En el menú de una foto: envíala sin los datos de dónde se hizo.", Go.NONE, words = "gps"),
    Feature("recientes", FeatureTopic.PRIVACY, "Lumi en blanco en recientes", "Al cambiar de app, Lumi no enseña tus fotos en la vista de apps recientes.", Go.SETTINGS, words = "multitarea"),
    // Orden
    Feature("carpetas_albumes", FeatureTopic.ORGANIZE, "Carpetas de álbumes", "Mantén pulsado un álbum para meterlo en una carpeta como Viajes o Familia.", Go.ALBUMS, words = "agrupar"),
    Feature("arrastrar_albumes", FeatureTopic.ORGANIZE, "Arrastrar a un álbum", "Elige fotos y arrástralas a una bandeja de álbumes.", Go.NONE, words = "mover"),
    Feature("corregir_fechas", FeatureTopic.ORGANIZE, "Corregir fechas", "Arregla la fecha de las fotos de WhatsApp o escaneadas.", Go.DATES, words = "whatsapp"),
    Feature("fotos_temporales", FeatureTopic.ORGANIZE, "Fotos temporales", "Marca una foto para que se borre sola: el ticket del parking, una talla.", Go.NONE, words = "caducar"),
    Feature("deshacer_agitando", FeatureTopic.ORGANIZE, "Agitar para deshacer", "Tras borrar o mover algo, agita el móvil y lo deshace.", Go.NONE, words = "sacudir"),
    Feature("repaso_rapido", FeatureTopic.ORGANIZE, "Repaso rápido", "Desliza para borrar o quedarte con cada foto, como unas cartas.", Go.SWIPE, words = "limpiar"),
    Feature("liberar_espacio", FeatureTopic.ORGANIZE, "Liberar espacio", "Copias, parecidas, vídeos grandes y capturas antiguas, con confeti al terminar.", Go.SPACE, words = "limpiar"),
    Feature("borrar_sin_preguntar", FeatureTopic.ORGANIZE, "Borrar sin que Android pregunte", "Concede la gestión multimedia y Lumi borra sin pedir permiso cada vez.", Go.SETTINGS, words = "permiso"),
    // Y además
    Feature("tu_mes", FeatureTopic.EXTRA, "Tu mes en fotos", "El primer día de cada mes, el resumen del anterior como una historia.", Go.NONE, words = "resumen"),
    Feature("tu_ano", FeatureTopic.EXTRA, "Tu año en fotos", "Tu año día a día, con sitios, personas y temas.", Go.YEAR, words = "resumen"),
    Feature("widgets", FeatureTopic.EXTRA, "Widgets", "Foto, mosaico, recuerdos, cámara y atajos en tu pantalla de inicio.", Go.NONE, words = "pantalla inicio"),
    Feature("fondo_cambia", FeatureTopic.EXTRA, "Fondo que cambia solo", "Tus favoritas o un álbum de fondo de pantalla, cada día.", Go.AUTO_WALLPAPER, words = "wallpaper"),
    Feature("calendario", FeatureTopic.EXTRA, "Calendario", "Salta a cualquier día tocando el calendario de la pantalla Fotos.", Go.CALENDAR, words = "fecha"),
    Feature("seleccion_inteligente", FeatureTopic.ORGANIZE, "Selección inteligente", "Elige una foto y toca «Las parecidas», «Las de este día», «Todas» o «Invertir».", Go.NONE, words = "seleccionar elegir atajos"),
    Feature("compartir_rapido", FeatureTopic.EXTRA, "Enviar en dos toques", "La hoja «Enviar» pone arriba tus apps de siempre y puede reducir las fotos para el chat.", Go.NONE, words = "compartir whatsapp reducir tamaño"),
    Feature("busquedas_fijadas", FeatureTopic.SEARCH, "Búsquedas fijadas", "En el buscador, pulsa «📌 Fijar» y la búsqueda queda como atajo arriba.", Go.SEARCH, words = "atajos chips guardar"),
    Feature("estado", FeatureTopic.EXTRA, "Estado de Lumi", "Cuánto ocupa, qué ha mirado, limpiar la caché y volver a analizar si algo se ve raro.", Go.STATUS, words = "espacio cache reindexar informe error"),
    Feature("recorrido", FeatureTopic.EXTRA, "Recorrido por Lumi", "El recorrido con todo lo que hace Lumi, cuando quieras.", Go.TOUR, words = "ayuda"),
)

fun featureOf(id: String): Feature? = FEATURES.firstOrNull { it.id == id }
