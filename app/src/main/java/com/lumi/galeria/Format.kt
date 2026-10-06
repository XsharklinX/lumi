package com.lumi.galeria

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH = Locale("es", "ES")
/** Idioma en el que se escriben fechas y números: el de la app. */
private val ES: Locale get() = if (Lang.english) Locale.US else SPANISH
private val DAY_ES = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)
private val DAY_EN = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
private val MONTH_ES = DateTimeFormatter.ofPattern("MMMM", SPANISH)
private val MONTH_EN = DateTimeFormatter.ofPattern("MMMM", Locale.US)
private val DAY_FORMAT: DateTimeFormatter get() = if (Lang.english) DAY_EN else DAY_ES
private val MONTH_FORMAT: DateTimeFormatter get() = if (Lang.english) MONTH_EN else MONTH_ES
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", SPANISH)

private fun String.capitalized() = replaceFirstChar { it.titlecase(ES) }

fun groupTitle(day: LocalDate, level: Level, today: LocalDate): String = when (level) {
    Level.YEAR -> day.year.toString()
    Level.MONTH -> MONTH_FORMAT.format(day).capitalized() + if (day.year != today.year) " ${day.year}" else ""
    Level.DAY -> dayTitle(day, today)
}

fun dayTitle(day: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    day == today -> tr("Hoy")
    day == today.minusDays(1) -> tr("Ayer")
    else -> DAY_FORMAT.format(day).capitalized() + if (day.year == today.year) "" else if (Lang.english) ", ${day.year}" else " de ${day.year}"
}

fun dayTitle(millis: Long): String =
    dayTitle(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())

fun timeText(millis: Long): String =
    TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024 -> String.format(ES, "%.1f GB", mb / 1024)
        mb >= 10 -> String.format(ES, "%.0f MB", mb)
        mb >= 0.1 -> String.format(ES, "%.1f MB", mb)
        else -> String.format(ES, "%.0f KB", bytes / 1024.0)
    }
}

fun formatDuration(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) String.format(ES, "%d:%02d:%02d", h, m, s) else String.format(ES, "%d:%02d", m, s)
}

fun countText(n: Int, one: String, many: String): String =
    if (n == 1) "1 ${tr(one)}" else String.format(ES, "%,d %s", n, tr(many))

/** Hace cuánto fue [millis], en pocas palabras: "hoy", "ayer", "hace 5 días" o el mes. */
fun agoText(millis: Long): String {
    val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> tr("hoy")
        days == 1L -> tr("ayer")
        days < 30 -> if (Lang.english) "$days days ago" else "hace $days días"
        else -> MONTH_FORMAT.format(day) + (if (Lang.english) " " else " de ") + day.year
    }
}

/** "Agosto de 2025". */
fun monthTitle(month: java.time.YearMonth): String =
    MONTH_FORMAT.format(month.atDay(1)).capitalized() + (if (Lang.english) " " else " de ") + month.year

/** Un número con su separador de miles, en el idioma de la app. */
fun formatCount(n: Int): String = String.format(ES, "%,d", n)
