@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.lumi.galeria.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaActionSound
import android.media.ToneGenerator
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.Thumb
import com.lumi.galeria.UiState
import com.lumi.galeria.data.CameraLook
import com.lumi.galeria.data.NightStack
import com.lumi.galeria.data.ShotEdit
import com.lumi.galeria.data.findContacts
import com.lumi.galeria.data.isWritableAlbumPath
import com.lumi.galeria.data.mediaIdOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Modos de la Cámara Lumi, en el orden de la fila. Lector QR y Documento van pegados a Foto para
 * tenerlos a mano. Retrato usa el del móvil si lo ofrece; Noche y HDR, el del móvil o el de Lumi.
 */
enum class CamMode(val label: String) {
    TEXT("Texto"), DOCUMENT("Documento"), QR("QR"), PHOTO("Foto"), VIDEO("Vídeo"), TIMELAPSE("Timelapse"), PORTRAIT("Retrato"),
    NIGHT("Noche"), HDR("HDR"), PRO("Pro"), SMART("Inteligente"), PRIVATE("Privada"), TRACE("Calco"),
}

internal enum class Flash(val label: String, val mode: Int) { AUTO("Auto", ImageCapture.FLASH_MODE_AUTO), ON("Sí", ImageCapture.FLASH_MODE_ON), OFF("No", ImageCapture.FLASH_MODE_OFF) }

/** Guías de composición sobre el visor. */
internal enum class Guide(val label: String) { NONE("Sin guías"), THIRDS("Tercios"), SPIRAL("Espiral"), CENTER("Centro"), DIAGONAL("Diagonales") }

/** Color de la luz en el modo Pro. */
internal enum class WhiteBalance(val label: String, val mode: Int) {
    AUTO("Auto", CaptureRequest.CONTROL_AWB_MODE_AUTO), SUN("Sol", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUD("Nublado", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT), BULB("Bombilla", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT),
    TUBE("Fluorescente", CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT),
}

/** Una línea de texto vista en directo, con su caja en coordenadas de la pantalla. */
internal class LiveLine(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

internal fun granted(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Una foto a memoria (para ráfagas, noche y HDR), girada como se ve y a [side] como mucho. */
internal suspend fun captureBitmap(controller: LifecycleCameraController, context: Context, side: Int): Bitmap? =
    suspendCancellableCoroutine { cont ->
        controller.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bmp = runCatching {
                    val raw = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    val k = minOf(1f, side.toFloat() / maxOf(raw.width, raw.height))
                    val m = Matrix().apply {
                        postScale(k, k)
                        postRotate(rotation.toFloat())
                    }
                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, true) }
                }.getOrNull()
                image.close()
                if (cont.isActive) cont.resume(bmp)
            }

            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) cont.resume(null)
            }
        })
    }

internal fun zoomLabel(stop: Float): String = when {
    stop < 1f -> String.format(Locale.US, "%.1f", stop).replace('.', ',')
    else -> stop.toInt().toString()
}

/** Un color que recuerda a cada filtro, para su botón. */
internal fun lookSwatch(look: CameraLook): Color = when (look) {
    CameraLook.NONE -> Color(0xFF888888)
    CameraLook.WARM -> Color(0xFFE9A15B)
    CameraLook.COOL -> Color(0xFF6FA6E8)
    CameraLook.VIVID -> Color(0xFFE85A9A)
    CameraLook.MONO -> Color(0xFF444444)
    CameraLook.SEPIA -> Color(0xFFB08D57)
    CameraLook.SOFT -> Color(0xFFD9C8E8)
    CameraLook.DRAMA -> Color(0xFF2E3A4A)
}

/** Las guías de composición y el nivel (que se pone del color de la app cuando el móvil está recto). */
@Composable
internal fun GuideOverlay(guide: Guide, tilt: Float, level: Boolean = true) {
    val accent = Lumi.Accent
    Canvas(Modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = 0.38f)
        val w = size.width
        val h = size.height
        when (guide) {
            Guide.NONE -> Unit
            Guide.THIRDS -> for (i in 1..2) {
                drawLine(line, Offset(w * i / 3, 0f), Offset(w * i / 3, h), 2f)
                drawLine(line, Offset(0f, h * i / 3), Offset(w, h * i / 3), 2f)
            }
            Guide.CENTER -> {
                drawLine(line, Offset(w / 2 - 40, h / 2), Offset(w / 2 + 40, h / 2), 2f)
                drawLine(line, Offset(w / 2, h / 2 - 40), Offset(w / 2, h / 2 + 40), 2f)
                drawCircle(line, 90f, Offset(w / 2, h / 2), style = Stroke(2f))
            }
            Guide.DIAGONAL -> {
                drawLine(line, Offset(0f, 0f), Offset(w, h), 2f)
                drawLine(line, Offset(w, 0f), Offset(0f, h), 2f)
            }
            Guide.SPIRAL -> {
                // Espiral áurea aproximada con cuartos de círculo que se van encogiendo.
                var left = 0f
                var top = 0f
                var width = w
                var height = h
                val phi = 0.618f
                for (i in 0 until 7) {
                    when (i % 4) {
                        0 -> { val s = width * phi; drawArc(line, 180f, 90f, false, Offset(left, top), Size(s * 2, height * 2), style = Stroke(2f)); left += s; width -= s }
                        1 -> { val s = height * phi; drawArc(line, 270f, 90f, false, Offset(left - width, top), Size(width * 2, s * 2), style = Stroke(2f)); top += s; height -= s }
                        2 -> { val s = width * phi; drawArc(line, 0f, 90f, false, Offset(left + width - s * 2, top - height), Size(s * 2, height * 2), style = Stroke(2f)); width -= s }
                        else -> { val s = height * phi; drawArc(line, 90f, 90f, false, Offset(left, top + height - s * 2), Size(width * 2, s * 2), style = Stroke(2f)); height -= s }
                    }
                }
            }
        }
        // Nivel, si se pidió: dos trazos fijos a los lados y uno que gira con el móvil; al quedar
        // recto se juntan en una sola línea del color de la app.
        if (level) {
            val straight = kotlin.math.abs(tilt) < 1f
            val color = if (straight) accent else Color.White.copy(alpha = 0.8f)
            drawLine(color, Offset(w / 2 - 92, h / 2), Offset(w / 2 - 66, h / 2), 3f)
            drawLine(color, Offset(w / 2 + 66, h / 2), Offset(w / 2 + 92, h / 2), 3f)
            rotate(-tilt, Offset(w / 2, h / 2)) {
                drawLine(color, Offset(w / 2 - 56, h / 2), Offset(w / 2 + 56, h / 2), if (straight) 4f else 2.5f)
            }
        }
    }
}

/** Marco del lector QR, con las esquinas resaltadas; se pone verde al leer. */
@Composable
internal fun QrFrame(found: Boolean) {
    val color = if (found) Color(0xFF5BC48A) else Color.White
    Canvas(Modifier.fillMaxSize()) {
        val side = size.width * 0.66f
        val left = (size.width - side) / 2
        val top = size.height * 0.24f
        val corner = side * 0.14f
        val stroke = 5.dp.toPx()
        listOf(Offset(left, top) to Offset(1f, 1f), Offset(left + side, top) to Offset(-1f, 1f), Offset(left, top + side) to Offset(1f, -1f), Offset(left + side, top + side) to Offset(-1f, -1f))
            .forEach { (p, d) ->
                drawLine(color, p, Offset(p.x + corner * d.x, p.y), stroke)
                drawLine(color, p, Offset(p.x, p.y + corner * d.y), stroke)
            }
    }
}

/** Abre lo que se encontró en un texto: llamar, escribir o la web. */
internal fun openContact(context: Context, contact: com.lumi.galeria.data.Contact) {
    val intent = when (contact.kind) {
        com.lumi.galeria.data.ContactKind.PHONE -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contact.value.filter { it.isDigit() || it == '+' }))
        com.lumi.galeria.data.ContactKind.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + contact.value))
        com.lumi.galeria.data.ContactKind.WEB -> Intent(Intent.ACTION_VIEW, Uri.parse(if (contact.value.startsWith("http")) contact.value else "https://" + contact.value))
    }
    runCatching { context.startActivity(intent) }
}

@Composable
internal fun TextCard(lines: List<LiveLine>, modifier: Modifier) {
    val clipboard = LocalClipboardManager.current
    val all = lines.sortedBy { it.top }.joinToString("\n") { it.text }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Lumi.Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(com.lumi.galeria.countText(lines.size, "línea de texto", "líneas de texto"), style = LabelStyle.copy(fontSize = 15.sp))
        Text("Toca una línea para copiarla, llamar o abrir la web.", style = SmallStyle)
        Text("Copiar todo", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable { clipboard.setText(AnnotatedString(all)) }.padding(vertical = 4.dp))
    }
}

/** Qué es un código, en una palabra, para el historial. */
fun codeType(code: Barcode): String = when (code.valueType) {
    Barcode.TYPE_WIFI -> "Wifi"
    Barcode.TYPE_URL -> "Web"
    Barcode.TYPE_CONTACT_INFO -> "Contacto"
    Barcode.TYPE_CALENDAR_EVENT -> "Evento"
    Barcode.TYPE_PHONE -> "Teléfono"
    Barcode.TYPE_EMAIL -> "Correo"
    Barcode.TYPE_SMS -> "Mensaje"
    Barcode.TYPE_GEO -> "Ubicación"
    Barcode.TYPE_PRODUCT, Barcode.TYPE_ISBN -> "Producto"
    else -> "Texto"
}

private val SHORTENERS = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rb.gy", "shorturl.at", "ow.ly", "buff.ly", "tiny.cc", "rebrand.ly", "s.id")

/**
 * Lo que dice un QR o un código de barras y qué hacer con ello. Las webs enseñan a qué sitio
 * llevan y avisan si el enlace no es seguro o está acortado; el wifi deja ver la contraseña.
 */
@Composable
fun QrCard(code: Barcode, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showPassword by remember(code.rawValue) { mutableStateOf(false) }
    val raw = code.rawValue.orEmpty()

    fun start(intent: Intent) = runCatching { context.startActivity(intent) }.onFailure { android.widget.Toast.makeText(context, com.lumi.galeria.tr("No hay ninguna app para abrirlo"), android.widget.Toast.LENGTH_SHORT).show() }

    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Lumi.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                codeType(code), style = SmallStyle.copy(fontWeight = FontWeight.Bold), color = Lumi.OnAccent,
                modifier = Modifier.clip(CircleShape).background(Lumi.Accent).padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            if (onClose != null) Text("✕", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(6.dp))
        }
        when (code.valueType) {
            Barcode.TYPE_WIFI -> {
                val wifi = code.wifi
                Text(wifi?.ssid.orEmpty(), style = HeadingStyle.copy(fontSize = 20.sp))
                val security = when (wifi?.encryptionType) {
                    Barcode.WiFi.TYPE_WPA -> "WPA/WPA2"
                    Barcode.WiFi.TYPE_WEP -> "WEP (poco segura)"
                    else -> "Abierta, sin contraseña"
                }
                Text("Seguridad: $security", style = SmallStyle)
                if (!wifi?.password.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (showPassword) wifi?.password.orEmpty() else "••••••••", style = LabelStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                        Text(if (showPassword) "Ocultar" else "Ver", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable { showPassword = !showPassword }.padding(8.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Conectarse", onClick = {
                        if (wifi != null) runCatching {
                            val suggestion = android.net.wifi.WifiNetworkSuggestion.Builder().setSsid(wifi.ssid.orEmpty()).apply {
                                if (!wifi.password.isNullOrEmpty()) setWpa2Passphrase(wifi.password!!)
                            }.build()
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_WIFI_ADD_NETWORKS)
                                    .putParcelableArrayListExtra(android.provider.Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion)),
                            )
                        }
                    })
                    if (!wifi?.password.isNullOrEmpty()) PillButton("Copiar contraseña", onClick = { clipboard.setText(AnnotatedString(wifi?.password.orEmpty())) }, primary = false)
                }
            }
            Barcode.TYPE_URL -> {
                val url = code.url?.url ?: raw
                val parsed = Uri.parse(if (url.contains("://")) url else "https://$url")
                val host = parsed.host.orEmpty().removePrefix("www.")
                Text(host.ifEmpty { url }, style = HeadingStyle.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(url, style = SmallStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when {
                    parsed.scheme == "http" -> Warning("Conexión no segura (sin https): no escribas contraseñas ni datos de pago.")
                    host in SHORTENERS -> Warning("Enlace acortado: no se sabe a qué web lleva hasta abrirlo.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Abrir", onClick = { start(Intent(Intent.ACTION_VIEW, parsed)) })
                    PillButton("Copiar", onClick = { clipboard.setText(AnnotatedString(url)) }, primary = false)
                    PillButton("Compartir", onClick = { start(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null)) }, primary = false)
                }
            }
            Barcode.TYPE_CONTACT_INFO -> {
                val info = code.contactInfo
                Text(info?.name?.formattedName ?: "Contacto", style = HeadingStyle.copy(fontSize = 20.sp))
                listOfNotNull(info?.organization, info?.phones?.firstOrNull()?.number, info?.emails?.firstOrNull()?.address, info?.urls?.firstOrNull())
                    .forEach { Text(it, style = SmallStyle.copy(fontSize = 14.sp)) }
                PillButton("Guardar contacto", onClick = {
                    start(
                        Intent(android.provider.ContactsContract.Intents.Insert.ACTION).setType(android.provider.ContactsContract.RawContacts.CONTENT_TYPE)
                            .putExtra(android.provider.ContactsContract.Intents.Insert.NAME, info?.name?.formattedName)
                            .putExtra(android.provider.ContactsContract.Intents.Insert.COMPANY, info?.organization)
                            .putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, info?.phones?.firstOrNull()?.number)
                            .putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, info?.emails?.firstOrNull()?.address),
                    )
                })
            }
            Barcode.TYPE_CALENDAR_EVENT -> {
                val event = code.calendarEvent
                Text(event?.summary ?: "Evento", style = HeadingStyle.copy(fontSize = 20.sp))
                listOfNotNull(event?.start?.rawValue, event?.location, event?.description).forEach { Text(it, style = SmallStyle.copy(fontSize = 14.sp), maxLines = 3) }
                PillButton("Añadir al calendario", onClick = {
                    start(
                        Intent(Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
                            .putExtra(android.provider.CalendarContract.Events.TITLE, event?.summary)
                            .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, event?.location)
                            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, event?.description),
                    )
                })
            }
            Barcode.TYPE_PHONE -> {
                Text(code.phone?.number.orEmpty(), style = HeadingStyle.copy(fontSize = 20.sp))
                PillButton("Llamar", onClick = { start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + code.phone?.number))) })
            }
            Barcode.TYPE_EMAIL -> {
                Text(code.email?.address.orEmpty(), style = HeadingStyle.copy(fontSize = 20.sp))
                code.email?.subject?.takeIf { it.isNotBlank() }?.let { Text(it, style = SmallStyle) }
                PillButton("Escribir", onClick = {
                    start(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + code.email?.address)).putExtra(Intent.EXTRA_SUBJECT, code.email?.subject).putExtra(Intent.EXTRA_TEXT, code.email?.body))
                })
            }
            Barcode.TYPE_SMS -> {
                Text(code.sms?.phoneNumber.orEmpty(), style = HeadingStyle.copy(fontSize = 20.sp))
                code.sms?.message?.takeIf { it.isNotBlank() }?.let { Text(it, style = SmallStyle, maxLines = 3) }
                PillButton("Mandar mensaje", onClick = { start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + code.sms?.phoneNumber)).putExtra("sms_body", code.sms?.message)) })
            }
            Barcode.TYPE_GEO -> {
                Text("${code.geoPoint?.lat}, ${code.geoPoint?.lng}", style = HeadingStyle.copy(fontSize = 18.sp))
                PillButton("Abrir en el mapa", onClick = { start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${code.geoPoint?.lat},${code.geoPoint?.lng}"))) })
            }
            Barcode.TYPE_PRODUCT, Barcode.TYPE_ISBN -> {
                Text(raw, style = HeadingStyle.copy(fontSize = 22.sp))
                Text(if (code.valueType == Barcode.TYPE_ISBN) "Código de un libro (ISBN)" else "Código de producto", style = SmallStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Buscar el producto", onClick = { start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, raw)) })
                    PillButton("Copiar", onClick = { clipboard.setText(AnnotatedString(raw)) }, primary = false)
                }
            }
            else -> {
                Text(raw, style = LabelStyle.copy(fontSize = 16.sp), maxLines = 8, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Copiar", onClick = { clipboard.setText(AnnotatedString(raw)) })
                    PillButton("Compartir", onClick = { start(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, raw), null)) }, primary = false)
                }
            }
        }
    }
}

@Composable
private fun Warning(text: String) {
    Text("⚠ $text", style = SmallStyle.copy(fontSize = 13.sp), color = Color(0xFFE5A23B), modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x22E5A23B)).padding(8.dp))
}

/** Los últimos códigos leídos, para volver a ellos. */
@Composable
internal fun QrHistorySheet(vm: LumiViewModel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lumi.Surface) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Códigos leídos", style = HeadingStyle.copy(fontSize = 20.sp), modifier = Modifier.weight(1f))
                if (vm.qrHistory.isNotEmpty()) Text("Borrar", style = LabelStyle, color = Lumi.Danger, modifier = Modifier.clip(CircleShape).clickable { vm.clearQrHistory() }.padding(8.dp))
            }
            if (vm.qrHistory.isEmpty()) Text("Aún no has leído ningún código.", style = SmallStyle)
            Column(Modifier.height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                vm.qrHistory.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lumi.Bg).clickable {
                            if (entry.type == "Web") runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.value))) }
                            else {
                                clipboard.setText(AnnotatedString(entry.value))
                                vm.say("Copiado")
                            }
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(entry.type, style = SmallStyle.copy(fontWeight = FontWeight.Bold), color = Lumi.Accent, modifier = Modifier.width(72.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.value, style = LabelStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(com.lumi.galeria.dayTitle(entry.time) + " · " + com.lumi.galeria.timeText(entry.time), style = SmallStyle)
                        }
                    }
                }
            }
        }
    }
}

/** Lee los códigos QR o de barras de una foto ya hecha. */
suspend fun readCodes(context: Context, uri: Uri): List<Barcode> = withContext(Dispatchers.IO) {
    runCatching {
        val image = com.google.mlkit.vision.common.InputImage.fromFilePath(context, uri)
        com.google.android.gms.tasks.Tasks.await(BarcodeScanning.getClient().process(image))
    }.getOrDefault(emptyList())
}
