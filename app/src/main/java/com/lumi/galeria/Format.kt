package com.lumi.galeria

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "ES")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM", ES)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", ES)

private fun String.capitalized() = replaceFirstChar { it.titlecase(ES) }

fun groupTitle(day: LocalDate, level: Level, today: LocalDate): String = when (level) {
    Level.YEAR -> day.year.toString()
    Level.MONTH -> MONTH_FORMAT.format(day).capitalized() + if (day.year != today.year) " ${day.year}" else ""
    Level.DAY -> dayTitle(day, today)
}

fun dayTitle(day: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    day == today -> "Hoy"
    day == today.minusDays(1) -> "Ayer"
    else -> DAY_FORMAT.format(day).capitalized() + if (day.year != today.year) " de ${day.year}" else ""
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
    if (n == 1) "1 $one" else String.format(ES, "%,d %s", n, many)
