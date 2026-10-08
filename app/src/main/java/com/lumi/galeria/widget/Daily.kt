package com.lumi.galeria.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lumi.galeria.AppLanguage
import com.lumi.galeria.Lang
import com.lumi.galeria.MainActivity
import com.lumi.galeria.MemoryWidget
import com.lumi.galeria.R
import com.lumi.galeria.countText
import com.lumi.galeria.data.readShowcaseOut
import com.lumi.galeria.tr
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** De dónde salen las fotos del fondo que cambia solo. */
enum class WallSource(val label: String) { FAVORITES("Favoritas"), ALBUM("Un álbum"), MEMORY("Recuerdos") }

/** Dónde se pone: inicio, pantalla de bloqueo o las dos. */
enum class WallTarget(val label: String) { HOME("Inicio"), LOCK("Bloqueo"), BOTH("Los dos") }

/** Lo que eligió el usuario para el fondo que cambia solo. */
class WallSettings(val on: Boolean, val source: WallSource, val album: String, val albumName: String, val hours: Int, val target: WallTarget)

/**
 * Lo que Lumi hace sola cada cierto tiempo, sin nada abierto en segundo plano: Android la despierta
 * a su hora. Cambiar el fondo de pantalla y avisar de un recuerdo por la mañana.
 */
object Daily {
    private const val WALL = "fondo_que_cambia"
    private const val NOTIFY = "aviso_de_recuerdos"
    private const val CHANNEL = "recuerdos"

    private fun prefs(context: Context) = context.getSharedPreferences("lumi", Context.MODE_PRIVATE)

    fun wallSettings(context: Context): WallSettings {
        val p = prefs(context)
        return WallSettings(
            on = p.getBoolean("wallOn", false),
            source = runCatching { WallSource.valueOf(p.getString("wallSource", null) ?: "FAVORITES") }.getOrDefault(WallSource.FAVORITES),
            album = p.getString("wallAlbum", "").orEmpty(),
            albumName = p.getString("wallAlbumName", "").orEmpty(),
            hours = p.getInt("wallHours", 24),
            target = runCatching { WallTarget.valueOf(p.getString("wallTarget", null) ?: "HOME") }.getOrDefault(WallTarget.HOME),
        )
    }

    fun saveWall(context: Context, s: WallSettings) {
        prefs(context).edit().putBoolean("wallOn", s.on).putString("wallSource", s.source.name).putString("wallAlbum", s.album)
            .putString("wallAlbumName", s.albumName).putInt("wallHours", s.hours).putString("wallTarget", s.target.name).apply()
        val work = WorkManager.getInstance(context)
        if (!s.on) {
            work.cancelUniqueWork(WALL)
            return
        }
        work.enqueueUniquePeriodicWork(
            WALL, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WallpaperWorker>(s.hours.toLong(), TimeUnit.HOURS).build(),
        )
        // El primero ya, para ver que funciona.
        work.enqueue(androidx.work.OneTimeWorkRequestBuilder<WallpaperWorker>().build())
    }

    fun memoryNotify(context: Context): Pair<Boolean, Int> = prefs(context).let { it.getBoolean("memoryNotify", false) to it.getInt("memoryHour", 9) }

    fun saveMemoryNotify(context: Context, on: Boolean, hour: Int) {
        prefs(context).edit().putBoolean("memoryNotify", on).putInt("memoryHour", hour).apply()
        val work = WorkManager.getInstance(context)
        if (!on) {
            work.cancelUniqueWork(NOTIFY)
            return
        }
        // Una vez al día, empezando a la hora elegida.
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(hour, 0)
        if (!first.isAfter(now)) first = first.plusDays(1)
        val delay = java.time.Duration.between(now, first).toMinutes()
        work.enqueueUniquePeriodicWork(
            NOTIFY, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<MemoryNotifyWorker>(24, TimeUnit.HOURS).setInitialDelay(delay, TimeUnit.MINUTES).build(),
        )
    }

    /** Fotos del teléfono que valen para lucirse, de la más reciente a la más antigua. */
    internal fun showcase(context: Context, path: String? = null, from: Long = 0, to: Long = Long.MAX_VALUE): List<Long> {
        val out = readShowcaseOut(context)
        val ids = ArrayList<Long>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH),
                "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?",
                arrayOf(from.toString(), to.toString()), "${MediaStore.Images.Media.DATE_TAKEN} DESC",
            )?.use { c ->
                while (c.moveToNext() && ids.size < 5000) {
                    val id = c.getLong(0)
                    val p = c.getString(1).orEmpty()
                    if (id in out || p.contains("Screenshot", ignoreCase = true) || p.startsWith("Documents/")) continue
                    if (path != null && p != path) continue
                    ids += id
                }
            }
        }
        return ids
    }

    /** El año más cercano con fotos de estos días: sus fotos y cuántos años hace. */
    internal fun memoryToday(context: Context): Pair<List<Long>, Int>? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        for (years in 1..15) {
            val day = today.minusYears(years.toLong())
            val ids = showcase(context, null, day.atStartOfDay(zone).toInstant().toEpochMilli(), day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            if (ids.isNotEmpty()) return ids to years
        }
        return null
    }

    internal fun uri(id: Long) = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    internal fun applyLanguage(context: Context) {
        Lang.apply(runCatching { AppLanguage.valueOf(prefs(context).getString("language", null) ?: "SYSTEM") }.getOrDefault(AppLanguage.SYSTEM))
    }

    internal fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Recuerdo del día"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    internal const val CHANNEL_ID = CHANNEL
}

/** Pone de fondo una foto de las elegidas, recortada a la pantalla. */
class WallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val s = Daily.wallSettings(context)
        if (!s.on) return Result.success()
        val ids = when (s.source) {
            WallSource.FAVORITES -> {
                val favorites = context.getSharedPreferences("lumi", Context.MODE_PRIVATE).getStringSet("favorites", emptySet()).orEmpty()
                    .mapNotNullTo(HashSet()) { it.toLongOrNull() }
                Daily.showcase(context).filter { it in favorites }
            }
            WallSource.ALBUM -> Daily.showcase(context, s.album)
            WallSource.MEMORY -> Daily.memoryToday(context)?.first ?: Daily.showcase(context).take(60)
        }
        if (ids.isEmpty()) return Result.success()
        val prefs = context.getSharedPreferences("lumi", Context.MODE_PRIVATE)
        val last = prefs.getLong("wallLast", -1L)
        val pick = ids.filter { it != last }.randomOrNull() ?: ids.first()
        val metrics = context.resources.displayMetrics
        val w = minOf(metrics.widthPixels, metrics.heightPixels)
        val h = maxOf(metrics.widthPixels, metrics.heightPixels)
        val bitmap = runCatching {
            val source = context.contentResolver.openInputStream(Daily.uri(pick))?.use { input ->
                val bytes = input.readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@use null
                // Girada como se ve en la galería.
                val exif = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                val degrees = exif.rotationDegrees
                if (degrees == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
            } ?: return Result.success()
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val scale = maxOf(w.toFloat() / source.width, h.toFloat() / source.height)
            val m = Matrix().apply {
                postScale(scale, scale)
                postTranslate((w - source.width * scale) / 2f, (h - source.height * scale) / 2f)
            }
            Canvas(out).drawBitmap(source, m, Paint(Paint.FILTER_BITMAP_FLAG))
            out
        }.getOrNull() ?: return Result.success()
        runCatching {
            val manager = WallpaperManager.getInstance(context)
            val which = when (s.target) {
                WallTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                WallTarget.LOCK -> WallpaperManager.FLAG_LOCK
                WallTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            manager.setBitmap(bitmap, null, true, which)
            prefs.edit().putLong("wallLast", pick).apply()
        }
        return Result.success()
    }
}

/** Por la mañana, si hay fotos de este día en otros años, un aviso con una de ellas. Uno al día como mucho. */
class MemoryNotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Daily.memoryNotify(context).first) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val prefs = context.getSharedPreferences("lumi", Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay()
        if (prefs.getLong("memoryNotified", -1L) == today) return Result.success()
        val (ids, years) = Daily.memoryToday(context) ?: return Result.success()
        Daily.applyLanguage(context)
        Daily.channel(context)
        val photo = ids.random()
        val picture = runCatching { context.contentResolver.loadThumbnail(Daily.uri(photo), Size(720, 720), null) }.getOrNull()
        val title = if (years == 1) tr("Hace un año, hoy") else tr("Hace $years años, hoy")
        val text = countText(ids.size, "foto de este día. Toca para verla.", "fotos de este día. Toca para verlas.")
        val open = Intent(context, MainActivity::class.java).setAction(MemoryWidget.ACTION_MEMORY)
            .putExtra(MemoryWidget.EXTRA_TITLE, if (years == 1) tr("Hace un año") else tr("Hace $years años"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val notification = NotificationCompat.Builder(context, Daily.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lumi)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 7000, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .apply {
                if (picture != null) {
                    setLargeIcon(picture)
                    setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?))
                }
            }
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(7000, notification) }
        prefs.edit().putLong("memoryNotified", today).apply()
        return Result.success()
    }
}
