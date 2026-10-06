package com.lumi.galeria.data

/** Tipos de captura de pantalla, en el orden en que se enseñan. */
enum class ShotKind(val label: String) {
    CHATS("Conversaciones"), SHOPPING("Compras y recibos"), MAPS("Mapas y direcciones"), TICKETS("Entradas y reservas"),
    SOCIAL("Redes sociales"), MEMES("Memes e imágenes"), OTHER("Otras"),
}

private fun words(vararg w: String) = w.toList()

private val TICKET = words(
    "billete", "boarding", "tarjeta de embarque", "embarque", "entrada", "ticket", "reserva", "booking", "localizador", "vuelo", "flight",
    "puerta", "gate", "asiento", "seat", "check-in", "renfe", "ryanair", "iberia", "vueling", "codigo de reserva", "confirmation",
)
private val SHOP = words(
    "€", "$", "total", "pedido", "order", "carrito", "cart", "precio", "price", "iva", "factura", "invoice", "recibo", "receipt",
    "pago", "payment", "envio", "shipping", "amazon", "aliexpress", "shein", "comprar", "buy now", "bizum", "transferencia", "importe",
)
private val MAP = words(
    "google maps", "waze", "ruta", "route", "indicaciones", "directions", "llegada", "km", "como llegar", "ubicacion", "location", "calle",
)
private val CHAT = words(
    "whatsapp", "telegram", "messenger", "mensaje", "message", "escribiendo", "typing", "en linea", "online", "ult. vez", "last seen",
    "escribe un mensaje", "type a message", "visto", "seen", "signal",
)
private val SOCIAL_APPS = words("instagram", "tiktok", "twitter", "facebook", "reddit", "youtube", "threads", "pinterest", "snapchat", "linkedin")
private val SOCIAL = words("me gusta", "likes", "seguir", "follow", "comentarios", "comments", "reels", "compartir", "share", "seguidores", "followers", "responder", "reply")

/** Las horas de los mensajes ("12:34"): en una conversación hay muchas. */
private val CLOCK = Regex("\\b\\d{1,2}:\\d{2}\\b")

/**
 * De qué va una captura, según el texto que se lee en ella y la app de la que viene (muchos
 * móviles la ponen en el nombre del archivo: «Screenshot_..._WhatsApp.jpg»).
 */
fun shotKind(item: MediaItem, entry: IndexEntry?): ShotKind {
    val from = normalize(item.name + " " + item.path)
    val text = entry?.plainText.orEmpty()
    fun has(list: List<String>, inText: Boolean = true) = list.any { from.contains(it) || (inText && text.contains(it)) }
    return when {
        has(TICKET) -> ShotKind.TICKETS
        has(CHAT, inText = false) || CLOCK.findAll(text).count() >= 4 && has(CHAT) -> ShotKind.CHATS
        text.length > 30 && SHOP.count { text.contains(it) } >= 2 -> ShotKind.SHOPPING
        has(MAP, inText = false) || MAP.count { text.contains(it) } >= 2 -> ShotKind.MAPS
        has(SOCIAL_APPS, inText = false) || SOCIAL.count { text.contains(it) } >= 2 -> ShotKind.SOCIAL
        CLOCK.findAll(text).count() >= 4 -> ShotKind.CHATS
        text.length in 1..140 -> ShotKind.MEMES
        text.isEmpty() && entry?.deep == true -> ShotKind.MEMES
        else -> ShotKind.OTHER
    }
}

/** Las capturas repartidas por tipo; solo los tipos que tienen alguna. */
fun sortShots(items: List<MediaItem>, index: Map<Long, IndexEntry>): Map<ShotKind, List<MediaItem>> =
    items.filter { it.isScreenshot }.groupBy { shotKind(it, index[it.id]) }.toSortedMap(compareBy { it.ordinal })
