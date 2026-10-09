@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.camera.camera2.interop.ExperimentalCamera2Interop::class,
    androidx.camera.core.ExperimentalGetImage::class,
)

package com.lumi.galeria.ui

import android.Manifest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.SwipeDown
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.MediaActionSound
import android.media.ToneGenerator
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.OrientationEventListener
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lumi.galeria.LumiViewModel
import com.lumi.galeria.Screen
import com.lumi.galeria.Source
import com.lumi.galeria.Thumb
import com.lumi.galeria.UiState
import com.lumi.galeria.data.CameraLook
import com.lumi.galeria.data.NightStack
import com.lumi.galeria.data.PhotoFormat
import com.lumi.galeria.data.PhotoSize
import com.lumi.galeria.data.ShotEdit
import com.lumi.galeria.data.ShotRatio
import com.lumi.galeria.data.VideoQuality
import com.lumi.galeria.data.VolumeKeys
import com.lumi.galeria.data.findContacts
import com.lumi.galeria.data.isWritableAlbumPath
import com.lumi.galeria.data.mediaIdOf
import com.lumi.galeria.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Desde dónde se abre la cámara. [request]: otra app pide una foto o un vídeo. [secure]: con el móvil
 * bloqueado, solo se ve lo hecho ahora. [front]: empezar con la cámara delantera (selfie).
 */
class CameraHost(
    val request: CaptureAsk? = null,
    val secure: Boolean = false,
    val front: Boolean = false,
    val exit: (() -> Unit)? = null,
    /** La cámara a solas, sin la galería detrás: lo escaneado se guarda desde la galería, así que no hay Documento. */
    val standalone: Boolean = false,
)

/** Otra app pidió una foto ([video] = false) o un vídeo; se le entrega con [onPhoto] o [onVideo]. */
class CaptureAsk(val video: Boolean, val onPhoto: (java.io.File) -> Unit, val onVideo: (java.io.File) -> Unit)

/** Una foto o un vídeo de la biblioteca a partir de su dirección, para borrarlo o abrirlo. */
fun itemForUri(uri: Uri): com.lumi.galeria.data.MediaItem? {
    val id = mediaIdOf(uri) ?: return null
    val video = uri.path.orEmpty().contains("/video/")
    return com.lumi.galeria.data.MediaItem(
        id = id, isVideo = video, name = "", date = 0, modified = 0, size = 0, width = 0, height = 0, duration = 0,
        bucketId = 0, bucket = "", path = "", expires = 0,
    )
}

/** Lo que la cámara propone según lo que ve. Nunca cambia de modo sola. */
private enum class Suggest(val text: String) {
    QR("Código QR · Ver"), DOCUMENT("Documento · Escanear"), NIGHT("Poca luz · Probar Noche"), HDR("Contraluz · Probar HDR"),
}

/** Cómo se ve el disparador. */
private enum class ShutterLook { PHOTO, PRIVATE, SMART, SMART_ARMED, VIDEO, RECORDING, BUSY }

/** Cómo es la luz de lo que se ve: media, cuánto está muy oscuro y cuánto quemado (de 0 a 1). */
private class Scene(val mean: Float, val dark: Float, val bright: Float)

/** Lo que la cámara puede hacer a mano, según el teléfono. */
private class ProCaps(val manual: Boolean, val iso: Range<Int>?, val exposure: Range<Long>?, val minFocus: Float)

private val PAPER = setOf("Paper", "Receipt", "Menu", "Newspaper", "Poster", "Whiteboard", "Passport", "Document")

private val ISO_STEPS = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
private val SHUTTER_STEPS = listOf(
    1_000_000_000L / 8000, 1_000_000_000L / 4000, 1_000_000_000L / 2000, 1_000_000_000L / 1000, 1_000_000_000L / 500, 1_000_000_000L / 250,
    1_000_000_000L / 125, 1_000_000_000L / 60, 1_000_000_000L / 30, 1_000_000_000L / 15, 1_000_000_000L / 8, 1_000_000_000L / 4,
    1_000_000_000L / 2, 1_000_000_000L,
)

private fun shutterText(ns: Long): String = if (ns >= 1_000_000_000L) "${ns / 1_000_000_000L} s" else "1/${(1_000_000_000L / ns)}"

/**
 * Mira lo que entra por la cámara sin guardar nada: luz de la escena, histograma y bordes
 * enfocados para el modo Pro, códigos QR y si parece un documento. Todo en el teléfono.
 */
private class SceneAnalyzer(
    private val main: Executor,
    codes: Boolean,
    labels: Boolean,
    private val pro: Boolean,
    private val onScene: (Scene) -> Unit,
    private val onCodes: (List<Barcode>) -> Unit,
    private val onLabels: (List<String>) -> Unit,
    private val onHisto: (IntArray) -> Unit,
    private val onPeaking: (Bitmap?) -> Unit,
) : ImageAnalysis.Analyzer {
    private val scanner = if (codes) BarcodeScanning.getClient() else null
    private val labeler = if (labels) ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) else null
    private var frame = 0
    private var lastLabels = 0L

    /** Resaltar lo enfocado: solo cuando se enfoca a mano. */
    @Volatile var peaking = false

    override fun analyze(image: ImageProxy) {
        frame++
        val plane = image.planes[0]
        val buffer = plane.buffer
        val stride = plane.rowStride
        val w = image.width
        val h = image.height
        val step = maxOf(4, w / 96)
        var sum = 0L
        var n = 0
        var dark = 0
        var bright = 0
        val histo = if (pro) IntArray(64) else null
        var y = 0
        while (y < h) {
            val row = y * stride
            var x = 0
            while (x < w) {
                val v = buffer.get(row + x).toInt() and 0xFF
                sum += v
                n++
                if (v < 35) dark++
                if (v > 235) bright++
                histo?.let { it[v shr 2]++ }
                x += step
            }
            y += step
        }
        if (n > 0) {
            val scene = Scene(sum.toFloat() / n, dark.toFloat() / n, bright.toFloat() / n)
            main.execute {
                onScene(scene)
                histo?.let(onHisto)
            }
        }
        if (pro && frame % 2 == 0) {
            val map = if (peaking) peakingMap(buffer, stride, w, h, image.imageInfo.rotationDegrees) else null
            main.execute { onPeaking(map) }
        }
        val media = image.image
        val rotation = image.imageInfo.rotationDegrees
        val now = SystemClock.elapsedRealtime()
        if (media != null && labeler != null && now - lastLabels > 1800) {
            lastLabels = now
            labeler.process(InputImage.fromMediaImage(media, rotation))
                .addOnSuccessListener { found -> main.execute { onLabels(found.filter { it.confidence >= 0.7f }.map { it.text }) } }
                .addOnCompleteListener { image.close() }
            return
        }
        if (media != null && scanner != null && frame % 3 == 0) {
            scanner.process(InputImage.fromMediaImage(media, rotation))
                .addOnSuccessListener { found -> main.execute { onCodes(found) } }
                .addOnCompleteListener { image.close() }
            return
        }
        image.close()
    }

    /** Los bordes nítidos, pintados en color sobre transparente y ya girados como se ven. */
    private fun peakingMap(buffer: java.nio.ByteBuffer, stride: Int, w: Int, h: Int, rotation: Int): Bitmap {
        val s = maxOf(2, w / 180)
        val pw = w / s - 2
        val ph = h / s - 2
        val px = IntArray(pw * ph)
        fun at(x: Int, y: Int) = buffer.get(y * stride + x).toInt() and 0xFF
        for (j in 0 until ph) for (i in 0 until pw) {
            val x = (i + 1) * s
            val y = (j + 1) * s
            val g = abs(at(x + s, y) - at(x - s, y)) + abs(at(x, y + s) - at(x, y - s))
            px[j * pw + i] = if (g > 70) 0xFFFF3DA8.toInt() else 0
        }
        val raw = Bitmap.createBitmap(px, pw, ph, Bitmap.Config.ARGB_8888)
        if (rotation == 0) return raw
        return Bitmap.createBitmap(raw, 0, 0, pw, ph, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, false)
    }

    fun close() {
        scanner?.close()
        labeler?.close()
    }
}

/**
 * La Cámara Lumi. El visor enseña justo lo que se guarda; las fotos se procesan por detrás, así que
 * se puede disparar sin esperar; con poca luz o a contraluz, Noche y HDR mezclan varias fotos.
 */
@SuppressLint("MissingPermission")
@Composable
fun CameraScreen(state: UiState, vm: LumiViewModel, actions: Actions, host: CameraHost = CameraHost()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val prefs = vm.camPrefs
    val main = remember { ContextCompat.getMainExecutor(context) }

    var canCamera by remember { mutableStateOf(granted(context, Manifest.permission.CAMERA)) }
    var canAudio by remember { mutableStateOf(granted(context, Manifest.permission.RECORD_AUDIO)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        canCamera = granted(context, Manifest.permission.CAMERA)
        canAudio = granted(context, Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { if (!canCamera) ask.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }

    if (!canCamera) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LumiArt(CameraLineIcon, ART_PALETTE[6], Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp)))
            Text("La Cámara Lumi necesita la cámara", style = TitleStyle.copy(fontSize = 24.sp), color = Color.White)
            Text("Solo para hacer fotos y vídeos, leer QR y texto. Nada sale del teléfono.", style = SmallStyle.copy(fontSize = 15.sp), color = Color.White.copy(alpha = 0.8f))
            PillButton("Permitir", onClick = { ask.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) })
            Text("Volver", style = LabelStyle, color = Color.White, modifier = Modifier.clip(CircleShape).clickable { vm.back() }.padding(12.dp))
        }
        return
    }

    // En vertical siempre, como cualquier cámara: al girar el móvil giran los iconos, no la pantalla.
    DisposableEffect(Unit) {
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity?.requestedOrientation = before ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    val controller = remember {
        LifecycleCameraController(context).apply {
            // Toda la calidad del sensor: tarda un poco más, pero las fotos se guardan por detrás.
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            isTapToFocusEnabled = false
            isPinchToZoomEnabled = false
        }
    }
    LaunchedEffect(owner) { controller.bindToLifecycle(owner) }
    // Al salir de la cámara se suelta, aunque la app siga abierta.
    DisposableEffect(controller) { onDispose { controller.unbind() } }

    fun startMode(): CamMode {
        host.request?.let { return if (it.video) CamMode.VIDEO else CamMode.PHOTO }
        vm.cameraStart?.let { start ->
            vm.cameraStart = null
            runCatching { CamMode.valueOf(start) }.getOrNull()?.let { return it }
        }
        val last = prefs.lastMode?.takeIf { prefs.rememberSettings }?.let { runCatching { CamMode.valueOf(it) }.getOrNull() }
        if (host.secure && last in setOf(CamMode.PRIVATE, CamMode.TRACE)) return CamMode.PHOTO
        return if (last == null || last == CamMode.DOCUMENT || last == CamMode.PRIVATE || last == CamMode.TIMELAPSE) CamMode.PHOTO else last
    }

    var mode by remember { mutableStateOf(startMode()) }
    var front by remember { mutableStateOf(host.front) }
    var flash by remember { mutableStateOf(prefs.flash?.takeIf { prefs.rememberSettings }?.let { runCatching { Flash.valueOf(it) }.getOrNull() } ?: Flash.AUTO) }
    var timer by remember { mutableIntStateOf(if (prefs.rememberSettings) prefs.timer else 0) }
    var ratio by remember { mutableStateOf(prefs.ratio) }
    val guide = runCatching { Guide.valueOf(prefs.guide ?: "NONE") }.getOrDefault(Guide.NONE)
    var look by remember { mutableStateOf(CameraLook.NONE) }
    var showLooks by remember { mutableStateOf(false) }
    // Disparo y estado.
    var inFlight by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var tip by remember { mutableStateOf<String?>(null) }
    // Vídeo.
    var recording by remember { mutableStateOf<Recording?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recordStart by remember { mutableLongStateOf(0L) }
    var pausedAt by remember { mutableLongStateOf(0L) }
    var pausedTotal by remember { mutableLongStateOf(0L) }
    var pendingRecord by remember { mutableStateOf(false) }
    // Lo último hecho.
    var lastShot by remember { mutableStateOf<Uri?>(null) }
    var lastPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var quickCard by remember { mutableStateOf(false) }
    // Lo hecho desde que se abrió la cámara: lo único que se ve con el móvil bloqueado.
    val session = remember { androidx.compose.runtime.mutableStateListOf<Uri>() }
    val sessionPicked = remember { androidx.compose.runtime.mutableStateListOf<Uri>() }
    var sessionViewer by remember { mutableStateOf<Uri?>(null) }
    // Para otras apps: la foto hecha, esperando «Usar» o «Repetir».
    var review by remember { mutableStateOf<java.io.File?>(null) }
    // Timelapse.
    var lapse by remember { mutableStateOf<com.lumi.galeria.data.TimelapseFrames?>(null) }
    var lapseEvery by remember { mutableIntStateOf(2) }
    var lapseJob by remember { mutableStateOf<Job?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var strip by remember { mutableStateOf(false) }
    var pickAlbum by remember { mutableStateOf(false) }
    var traceAlpha by remember { mutableFloatStateOf(0.35f) }
    var liveLines by remember { mutableStateOf<List<LiveLine>>(emptyList()) }
    var pickedLine by remember { mutableStateOf<LiveLine?>(null) }
    val flashOverlay = remember { Animatable(0f) }
    // Láminas de diafragma al disparar: de 0 (abiertas) a 1 (cerradas).
    val iris = remember { Animatable(0f) }
    // El nombre del modo nuevo, grande un instante en el centro.
    var modeName by remember { mutableStateOf<String?>(null) }
    var screenLight by remember { mutableStateOf(false) }
    // Enfoque y luz.
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var locked by remember { mutableStateOf(false) }
    var ev by remember { mutableIntStateOf(0) }
    var touchSun by remember { mutableIntStateOf(0) }
    // Zoom.
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var dial by remember { mutableStateOf(false) }
    var bigZoom by remember { mutableIntStateOf(0) }
    var showBigZoom by remember { mutableStateOf(false) }
    var zoomJob by remember { mutableStateOf<Job?>(null) }
    // Pro.
    var wb by remember { mutableStateOf(WhiteBalance.AUTO) }
    var aeLock by remember { mutableStateOf(false) }
    var iso by remember { mutableStateOf<Int?>(null) }
    var shutterNs by remember { mutableStateOf<Long?>(null) }
    var focusDist by remember { mutableStateOf<Float?>(null) }
    var proCaps by remember { mutableStateOf<ProCaps?>(null) }
    var histo by remember { mutableStateOf<IntArray?>(null) }
    var peaking by remember { mutableStateOf<ImageBitmap?>(null) }
    // QR.
    var qr by remember { mutableStateOf<Barcode?>(null) }
    var qrCandidate by remember { mutableStateOf<String?>(null) }
    var qrSeen by remember { mutableIntStateOf(0) }
    var torch by remember { mutableStateOf(false) }
    var qrHistory by remember { mutableStateOf(false) }
    var photoCodes by remember { mutableStateOf<List<Barcode>>(emptyList()) }
    // Sugerencias de escena.
    var suggest by remember { mutableStateOf<Suggest?>(null) }
    var photoQr by remember { mutableStateOf<Barcode?>(null) }
    val quietUntil = remember { mutableStateMapOf<Suggest, Long>() }
    var darkSince by remember { mutableLongStateOf(0L) }
    var backlightSince by remember { mutableLongStateOf(0L) }
    var paperSeen by remember { mutableLongStateOf(0L) }
    // Disparo inteligente.
    var smartArmed by remember { mutableStateOf(false) }
    var smartFaces by remember { mutableIntStateOf(0) }
    var smartHappy by remember { mutableIntStateOf(0) }
    var steady by remember { mutableStateOf(false) }
    var steadySince by remember { mutableLongStateOf(0L) }
    // Transiciones: la última imagen, desenfocada, mientras cambia la cámara.
    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    val frozenAlpha = remember { Animatable(0f) }
    val flipTurn = remember { Animatable(0f) }
    var bindCount by remember { mutableIntStateOf(0) }
    var lastFront by remember { mutableStateOf(front) }
    // Dónde están el visor, la miniatura y la pantalla, para que la foto vuele a su sitio.
    var rootAt by remember { mutableStateOf(Offset.Zero) }
    var vfRect by remember { mutableStateOf(Rect.Zero) }
    var thumbRect by remember { mutableStateOf(Rect.Zero) }
    var fly by remember { mutableStateOf<ImageBitmap?>(null) }
    val flyAt = remember { Animatable(0f) }

    val scanDocument = rememberDocumentScanner(vm)
    val pickForQr = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val found = readCodes(context, uri)
            if (found.isEmpty()) vm.say("No se ve ningún código en esta foto") else photoCodes = found
        }
    }
    val sound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    val beep = remember { runCatching { ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 60) }.getOrNull() }
    val analysisThread = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            sound.release()
            beep?.release()
            analysisThread.shutdown()
        }
    }
    fun haptic(kind: Int = HapticFeedbackConstants.CLOCK_TICK) {
        activity?.window?.decorView?.performHapticFeedback(kind)
    }
    fun say(text: String) {
        toast = text
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1400)
            toast = null
        }
    }

    // Modos del móvil (Retrato, Noche, HDR): los que ofrece a otras apps.
    val extensions by produceState<Pair<ExtensionsManager, Set<Int>>?>(null) {
        value = runCatching {
            val provider = withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
            val manager = withContext(Dispatchers.IO) { ExtensionsManager.getInstanceAsync(context, provider).get() }
            val back = CameraSelector.DEFAULT_BACK_CAMERA
            manager to listOf(ExtensionMode.BOKEH, ExtensionMode.NIGHT, ExtensionMode.HDR).filter { manager.isExtensionAvailable(back, it) }.toSet()
        }.getOrNull()
    }
    // Retrato siempre: si el móvil no trae el suyo, Retrato Lumi desenfoca el fondo al guardar.
    val modes = when {
        host.request?.video == true -> listOf(CamMode.VIDEO)
        host.request != null -> listOf(CamMode.PHOTO, CamMode.PORTRAIT, CamMode.NIGHT, CamMode.HDR)
        host.secure -> CamMode.entries.filter { it !in setOf(CamMode.PRIVATE, CamMode.TRACE, CamMode.DOCUMENT) }
        else -> CamMode.entries.toList()
    }
    fun phoneMode(m: CamMode): Int? = when (m) {
        CamMode.PORTRAIT -> ExtensionMode.BOKEH
        CamMode.NIGHT -> ExtensionMode.NIGHT
        CamMode.HDR -> ExtensionMode.HDR
        else -> null
    }?.takeIf { extensions?.second?.contains(it) == true }

    // Nivel del móvil, si está quieto y hacia dónde está girado (para girar los iconos).
    var roll by remember { mutableFloatStateOf(0f) }
    var turn by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        var lastX = 0f
        var lastY = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                roll = Math.toDegrees(kotlin.math.atan2(x.toDouble(), y.toDouble())).toFloat()
                val moving = abs(x - lastX) + abs(y - lastY) > 0.25f
                lastX = x
                lastY = y
                if (moving) steadySince = 0L else if (steadySince == 0L) steadySince = System.currentTimeMillis()
                steady = steadySince != 0L && System.currentTimeMillis() - steadySince > 500
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        val orientation = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                val snapped = when (degrees) {
                    in 45 until 135 -> 90
                    in 135 until 225 -> 180
                    in 225 until 315 -> 270
                    else -> 0
                }
                if (snapped != turn) turn = snapped
            }
        }
        orientation.enable()
        onDispose {
            sensors.unregisterListener(listener)
            orientation.disable()
        }
    }
    // Los iconos giran por el camino corto.
    var iconTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(turn) {
        val wanted = -turn.toFloat()
        var delta = (wanted - iconTarget) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        iconTarget += delta
    }
    val iconAngle by animateFloatAsState(iconTarget, tween(300), label = "giro")
    val tilt = ((roll + 360f) % 90f).let { if (it > 45f) it - 90f else it }

    // Zoom: se sigue lo que dice la cámara.
    DisposableEffect(controller) {
        val observer = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { z ->
            zoom = z.zoomRatio
            minZoom = z.minZoomRatio
            maxZoom = z.maxZoomRatio
        }
        controller.zoomState.observeForever(observer)
        onDispose { controller.zoomState.removeObserver(observer) }
    }
    fun zoomTo(target: Float, animate: Boolean = true) {
        val goal = target.coerceIn(minZoom, maxZoom)
        zoomJob?.cancel()
        if (!animate) {
            controller.setZoomRatio(goal)
            return
        }
        zoomJob = scope.launch {
            val anim = Animatable(zoom)
            anim.animateTo(goal, tween(240)) { controller.setZoomRatio(value) }
        }
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    // Cuando la imagen nueva ya llega, la congelada se desvanece.
    DisposableEffect(previewView) {
        val view = previewView
        val observer = androidx.lifecycle.Observer<PreviewView.StreamState> { stream ->
            if (stream == PreviewView.StreamState.STREAMING && frozen != null) scope.launch {
                delay(60)
                frozenAlpha.animateTo(0f, tween(260))
                frozen = null
            }
        }
        view?.previewStreamState?.observeForever(observer)
        onDispose { view?.previewStreamState?.removeObserver(observer) }
    }

    fun cropFor(r: ShotRatio, w: Float, h: Float): Float = when (r) {
        ShotRatio.SQUARE -> 1f
        ShotRatio.FULL -> (maxOf(w, h) / minOf(w, h)).takeIf { it > 16f / 9f + 0.02f } ?: 0f
        else -> 0f
    }

    // Cada modo, con lo que necesita de la cámara.
    var analyzer by remember { mutableStateOf<SceneAnalyzer?>(null) }
    LaunchedEffect(mode, front, extensions, ratio, prefs.size, prefs.video, prefs.fps) {
        // Antes de cambiar, la imagen actual se queda quieta y desenfocada.
        if (bindCount > 0) {
            previewView?.bitmap?.let { frozen = it.asImageBitmap() }
            frozenAlpha.snapTo(1f)
            if (front != lastFront) scope.launch {
                flipTurn.snapTo(0f)
                flipTurn.animateTo(180f, tween(380))
            }
            // Si la cámara tarda, no se queda congelada para siempre.
            scope.launch {
                delay(1500)
                if (frozen != null) {
                    frozenAlpha.animateTo(0f, tween(200))
                    frozen = null
                }
            }
        }
        lastFront = front
        val base = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val extension = phoneMode(mode)
        controller.cameraSelector = extensions?.first?.takeIf { extension != null && it.isExtensionAvailable(base, extension) }
            ?.getExtensionEnabledCameraSelector(base, extension!!) ?: base
        val wide = ratio == ShotRatio.SIXTEEN_NINE || ratio == ShotRatio.FULL
        val aspect = CameraController.OutputSize(if (wide) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3)
        controller.previewTargetSize = aspect
        controller.imageCaptureTargetSize = if (prefs.size == PhotoSize.BALANCED) {
            CameraController.OutputSize(if (wide) Size(2250, 4000) else Size(3000, 4000))
        } else aspect
        if (mode == CamMode.VIDEO) runCatching {
            // 60 fotogramas por segundo solo si el móvil los da.
            val ranges = controller.cameraInfo?.supportedFrameRateRanges.orEmpty()
            val fps = if (prefs.fps == 60 && ranges.any { it.upper >= 60 }) 60 else 30
            if (ranges.any { it.upper >= fps }) controller.setVideoCaptureTargetFrameRate(Range(fps, fps))
        }
        runCatching {
            controller.videoCaptureQualitySelector = QualitySelector.from(
                if (prefs.video == VideoQuality.UHD) Quality.UHD else Quality.FHD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.HD),
            )
        }
        controller.setEnabledUseCases(
            when {
                mode == CamMode.VIDEO -> CameraController.VIDEO_CAPTURE
                mode == CamMode.QR || mode == CamMode.TEXT -> CameraController.IMAGE_ANALYSIS
                extension != null -> CameraController.IMAGE_CAPTURE
                mode == CamMode.SMART || mode == CamMode.PHOTO || mode == CamMode.PRO -> CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS
                else -> CameraController.IMAGE_CAPTURE
            },
        )
        qr = null
        photoQr = null
        suggest = null
        liveLines = emptyList()
        smartArmed = false
        histo = null
        peaking = null
        controller.clearImageAnalysisAnalyzer()
        analyzer?.close()
        analyzer = null
        when (mode) {
            CamMode.QR -> {
                val scanner = BarcodeScanning.getClient()
                controller.setImageAnalysisAnalyzer(main, MlKitAnalyzer(listOf(scanner), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, main) { result ->
                    val found = result.getValue(scanner).orEmpty().firstOrNull() ?: return@MlKitAnalyzer
                    // Un código pequeño: se acerca solo para leerlo mejor.
                    val box = found.boundingBox
                    val viewWidth = previewView?.width ?: context.resources.displayMetrics.widthPixels
                    if (box != null && box.width() < viewWidth * 0.18f && zoom < 3f) controller.setZoomRatio((zoom * 1.6f).coerceAtMost(maxZoom))
                    // Solo se enseña cuando se ha leído igual dos veces: así no parpadea ni se confunde.
                    val raw = found.rawValue ?: return@MlKitAnalyzer
                    if (raw == qrCandidate) qrSeen++ else {
                        qrCandidate = raw
                        qrSeen = 1
                    }
                    if (qrSeen == 2 && qr?.rawValue != raw) {
                        qr = found
                        beep?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                        haptic(HapticFeedbackConstants.CONFIRM)
                        vm.rememberQr(codeType(found), raw)
                    }
                })
            }
            CamMode.TEXT -> {
                val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                controller.setImageAnalysisAnalyzer(main, MlKitAnalyzer(listOf(reader), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, main) { result ->
                    liveLines = result.getValue(reader)?.textBlocks.orEmpty().flatMap { block ->
                        block.lines.mapNotNull { line -> line.boundingBox?.let { LiveLine(line.text, it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) } }
                    }
                })
            }
            CamMode.SMART -> {
                val faces = FaceDetection.getClient(
                    FaceDetectorOptions.Builder().setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build(),
                )
                controller.setImageAnalysisAnalyzer(main, MlKitAnalyzer(listOf(faces), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, main) { result ->
                    val list = result.getValue(faces).orEmpty()
                    smartFaces = list.size
                    smartHappy = list.count { (it.smilingProbability ?: 0f) > 0.55f && (it.leftEyeOpenProbability ?: 0f) > 0.45f && (it.rightEyeOpenProbability ?: 0f) > 0.45f }
                })
            }
            CamMode.PHOTO, CamMode.PRO -> if (extension == null) {
                val photo = mode == CamMode.PHOTO
                val made = SceneAnalyzer(
                    main, codes = photo, labels = photo, pro = !photo,
                    onScene = { scene ->
                        val now = SystemClock.elapsedRealtime()
                        darkSince = if (scene.mean < 55f && !front) (if (darkSince == 0L) now else darkSince) else 0L
                        backlightSince = if (scene.bright > 0.1f && scene.dark > 0.22f) (if (backlightSince == 0L) now else backlightSince) else 0L
                    },
                    onCodes = { found -> photoQr = found.firstOrNull { it.rawValue != null } },
                    onLabels = { labels -> if (labels.any { it in PAPER }) paperSeen = SystemClock.elapsedRealtime() },
                    onHisto = { histo = it },
                    onPeaking = { map -> peaking = map?.asImageBitmap() },
                )
                analyzer = made
                controller.setImageAnalysisAnalyzer(analysisThread, made)
            }
            CamMode.DOCUMENT -> scanDocument()
            else -> Unit
        }
        bindCount++
    }
    // Lo que pueden los controles manuales, una vez la cámara está en marcha.
    LaunchedEffect(bindCount) {
        delay(400)
        proCaps = runCatching {
            val info = Camera2CameraInfo.from(controller.cameraInfo ?: return@runCatching null)
            val caps = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ProCaps(
                manual = caps?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true,
                iso = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
                exposure = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
                minFocus = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
            )
        }.getOrNull()
    }
    LaunchedEffect(analyzer, focusDist) { analyzer?.peaking = focusDist != null }
    DisposableEffect(Unit) { onDispose { analyzer?.close() } }

    // Sugerencias de escena: solo en Foto, tras verse un momento, y si no se descartaron hace poco.
    LaunchedEffect(mode) {
        while (mode == CamMode.PHOTO) {
            delay(500)
            val now = SystemClock.elapsedRealtime()
            fun quiet(s: Suggest) = (quietUntil[s] ?: 0L) > now
            suggest = when {
                photoQr != null && !quiet(Suggest.QR) -> Suggest.QR
                now - paperSeen < 2500 && !quiet(Suggest.DOCUMENT) -> Suggest.DOCUMENT
                darkSince != 0L && now - darkSince > 1500 && !quiet(Suggest.NIGHT) -> Suggest.NIGHT
                backlightSince != 0L && now - backlightSince > 1500 && !quiet(Suggest.HDR) -> Suggest.HDR
                else -> null
            }
        }
        suggest = null
    }

    LaunchedEffect(flash, front, bindCount) { controller.imageCaptureFlashMode = if (front) ImageCapture.FLASH_MODE_OFF else flash.mode }
    LaunchedEffect(torch, mode, bindCount) { controller.enableTorch(torch && mode == CamMode.QR) }
    LaunchedEffect(ev, bindCount) { runCatching { controller.cameraControl?.setExposureCompensationIndex(ev) } }
    // Pro y estabilización de vídeo: todo en una sola petición a la cámara.
    LaunchedEffect(mode, wb, aeLock, iso, shutterNs, focusDist, prefs.stabilize, bindCount) {
        val control = controller.cameraControl ?: return@LaunchedEffect
        val c2 = Camera2CameraControl.from(control)
        val builder = CaptureRequestOptions.Builder()
        var any = false
        if (mode == CamMode.PRO) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wb.mode)
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, aeLock)
            if (iso != null || shutterNs != null) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso ?: 400)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterNs ?: 16_666_666L)
            }
            focusDist?.let {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, it)
            }
            any = true
        }
        if (mode == CamMode.VIDEO && prefs.stabilize) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            any = true
        }
        runCatching { if (any) c2.captureRequestOptions = builder.build() else c2.clearCaptureRequestOptions() }
    }

    // Se recuerda lo elegido para la próxima vez.
    LaunchedEffect(mode, flash, ratio, timer) { prefs.remember(mode.name, flash.name, ratio, timer) }

    // Consejos de una línea, solo las primeras veces.
    LaunchedEffect(mode) {
        val text = when (mode) {
            CamMode.PHOTO -> "Mantén el disparador para hacer una ráfaga"
            CamMode.NIGHT -> if (phoneMode(mode) != null) null else "Apoya el móvil: junta seis fotos en una"
            CamMode.HDR -> if (phoneMode(mode) != null) null else "Junta una foto oscura, una normal y una clara"
            CamMode.SMART -> "Pulsa y se disparará cuando todos sonrían"
            CamMode.PRIVATE -> "Va cifrada a la carpeta privada, nunca a la galería"
            CamMode.TRACE -> "La última foto del álbum sale encima, transparente"
            CamMode.TIMELAPSE -> "Apoya el móvil: hará una foto cada pocos segundos"
            CamMode.PORTRAIT -> if (phoneMode(mode) != null) null else "Retrato Lumi: el fondo se desenfoca al guardar"
            CamMode.QR -> "Apunta a un código: se lee solo"
            CamMode.TEXT -> "Toca una línea para copiarla"
            CamMode.PRO -> "Toca A para volver a automático"
            else -> null
        } ?: return@LaunchedEffect
        val key = "modo_" + mode.name
        if (!prefs.shouldTip(key)) return@LaunchedEffect
        delay(600)
        prefs.tipShown(key)
        tip = text
        delay(3200)
        if (tip == text) tip = null
    }

    val albumPath = vm.cameraAlbum
    val albumName = state.albums.firstOrNull { it.path == albumPath }?.name ?: albumPath.trimEnd('/').substringAfterLast('/').let { if (it == "Camera") "Cámara" else it }
    val traceItem = remember(state.items, albumPath, mode) { if (mode == CamMode.TRACE) state.items.firstOrNull { it.path == albumPath && !it.isVideo } else null }
    val recent = remember(state.items) { state.items.filter { it.path == albumPath || it.path.startsWith("DCIM/") }.take(20) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned { rootAt = it.boundsInRoot().topLeft },
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val hOverW = when (ratio) {
            ShotRatio.SQUARE -> 1f
            ShotRatio.FOUR_THREE -> 4f / 3f
            ShotRatio.SIXTEEN_NINE -> 16f / 9f
            ShotRatio.FULL -> screenH / screenW
        }
        val vfHeight = screenW * hOverW
        val vfTop = when (ratio) {
            ShotRatio.FULL -> 0.dp
            ShotRatio.SIXTEEN_NINE -> ((screenH - vfHeight) / 2).coerceAtLeast(0.dp)
            else -> statusTop + 96.dp
        }
        val crop = with(density) { cropFor(ratio, screenW.toPx(), screenH.toPx()) }

        fun edit(phoneProcessed: Boolean) = ShotEdit(
            // Lo que ya procesa el móvil (sus modos) no se vuelve a tocar.
            auto = vm.lumiAuto && !phoneProcessed,
            look = look,
            tilt = if (mode == CamMode.TRACE || front) 0f else tilt,
            mirror = front && vm.mirrorSelfie,
            crop = crop,
            heic = prefs.format == PhotoFormat.HEIC && host.request == null,
            portrait = mode == CamMode.PORTRAIT && phoneMode(mode) == null,
        )

        /** Destello, sonido y vibración: la foto se hizo. */
        fun feedback() {
            scope.launch {
                if (prefs.iris) {
                    iris.snapTo(0f)
                    iris.animateTo(1f, tween(110))
                    iris.animateTo(0f, tween(190))
                } else {
                    flashOverlay.snapTo(0.8f)
                    flashOverlay.animateTo(0f, tween(240))
                }
            }
            if (vm.shutterSound) sound.play(MediaActionSound.SHUTTER_CLICK)
            haptic(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        /** La foto vuela del visor a la miniatura. */
        fun flyFrom(snapshot: Bitmap?) {
            val image = snapshot?.asImageBitmap() ?: return
            lastPreview = image
            fly = image
            scope.launch {
                flyAt.snapTo(0f)
                flyAt.animateTo(1f, tween(360))
                fly = null
            }
        }

        fun saved(uri: Uri?) {
            if (uri != null && uri != Uri.EMPTY) {
                lastShot = uri
                session.add(0, uri)
            }
        }

        /** Guarda la foto en la galería o, si otra app la pidió, la deja lista para «Usar». */
        fun keepFile(file: java.io.File, shotEdit: ShotEdit, private: Boolean) {
            if (host.request != null) vm.processShotToFile(file, shotEdit) { review = it }
            else vm.saveShot(file, shotEdit, albumPath, private) { saved(it) }
        }

        fun keepBitmap(bitmap: Bitmap, shotEdit: ShotEdit) {
            if (host.request != null) vm.processBitmapToFile(bitmap, shotEdit) { review = it }
            else vm.saveBitmapShot(bitmap, shotEdit, albumPath, false) { saved(it) }
        }

        /** Una foto normal: no se espera a que se guarde; se puede seguir disparando. */
        fun takeOne() {
            if (inFlight >= 3) return
            inFlight++
            val snapshot = previewView?.bitmap
            feedback()
            val file = java.io.File(context.cacheDir, "toma_${System.nanoTime()}.jpg")
            val shotEdit = edit(phoneMode(mode) != null)
            val private = mode == CamMode.PRIVATE
            controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), main, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    inFlight--
                    flyFrom(snapshot)
                    keepFile(file, shotEdit, private)
                }
                override fun onError(exception: ImageCaptureException) {
                    inFlight--
                    vm.say("No se pudo hacer la foto")
                }
            })
        }

        /** Noche Lumi: varias fotos seguidas, alineadas y sumadas. */
        suspend fun takeNight() {
            val frames = 6
            working = true
            status = "No te muevas"
            progress = 0f
            val snapshot = previewView?.bitmap
            val first = captureBitmap(controller, context, 2560)
            if (first == null) {
                working = false; status = null; progress = null
                vm.say("No se pudo hacer la foto")
                return
            }
            val stack = NightStack(first)
            first.recycle()
            for (i in 2..frames) {
                progress = (i - 1f) / frames
                val next = captureBitmap(controller, context, 2560) ?: continue
                withContext(Dispatchers.Default) { stack.next(next) }
                next.recycle()
            }
            progress = 1f
            feedback()
            status = "Juntando las fotos…"
            val result = withContext(Dispatchers.Default) { runCatching { stack.result() }.getOrNull() }
            working = false; status = null; progress = null
            if (result == null) {
                vm.say("No se pudo montar la foto de noche")
                return
            }
            flyFrom(snapshot)
            keepBitmap(result, edit(true))
        }

        /** HDR Lumi: una oscura, una normal y una clara, mezcladas. */
        suspend fun takeHdr() {
            val control = controller.cameraControl
            val exposure = controller.cameraInfo?.exposureState
            if (control == null || exposure == null || !exposure.isExposureCompensationSupported) {
                takeOne()
                return
            }
            working = true
            status = "No te muevas"
            progress = 0f
            val snapshot = previewView?.bitmap
            val range = exposure.exposureCompensationRange
            val stepEv = exposure.exposureCompensationStep.toFloat().coerceAtLeast(0.01f)
            val delta = (2f / stepEv).toInt().coerceAtMost(minOf(-range.lower, range.upper))
            val shots = ArrayList<Bitmap>()
            for ((n, index) in listOf(-delta, 0, delta).withIndex()) {
                progress = n / 3f
                withContext(Dispatchers.IO) { runCatching { control.setExposureCompensationIndex(index).get(1, TimeUnit.SECONDS) } }
                delay(350)
                captureBitmap(controller, context, 2560)?.let { shots += it }
            }
            runCatching { control.setExposureCompensationIndex(ev) }
            progress = 1f
            feedback()
            if (shots.size < 2) {
                working = false; status = null; progress = null
                vm.say("No se pudo hacer el HDR")
                return
            }
            status = "Juntando las fotos…"
            val merged = withContext(Dispatchers.Default) { runCatching { com.lumi.galeria.data.hdrMerge(shots) }.getOrNull() }
            shots.forEach { it.recycle() }
            working = false; status = null; progress = null
            if (merged == null) {
                vm.say("No se pudo hacer el HDR")
                return
            }
            flyFrom(snapshot)
            keepBitmap(merged, edit(true))
        }

        /** Ráfaga mientras se mantiene el disparador: se guarda solo la mejor (nítida y sin ojos cerrados). */
        var bursting by remember { mutableStateOf(false) }
        suspend fun takeBurst() {
            bursting = true
            working = true
            val snapshot = previewView?.bitmap
            val faces = FaceDetection.getClient(FaceDetectorOptions.Builder().setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build())
            var best: Bitmap? = null
            var bestScore = -1f
            var count = 0
            while (bursting && count < 15) {
                val shot = captureBitmap(controller, context, 3000) ?: break
                count++
                status = tr("Ráfaga") + " · $count"
                haptic()
                val score = withContext(Dispatchers.Default) {
                    val sharp = com.lumi.galeria.data.burstSharpness(shot)
                    val found = runCatching {
                        com.google.android.gms.tasks.Tasks.await(faces.process(InputImage.fromBitmap(Bitmap.createScaledBitmap(shot, 640, (640f * shot.height / shot.width).toInt(), true), 0)))
                    }.getOrDefault(emptyList())
                    val blink = found.any { (it.leftEyeOpenProbability ?: 1f) < 0.3f && (it.rightEyeOpenProbability ?: 1f) < 0.3f }
                    val smile = found.sumOf { (it.smilingProbability ?: 0f).toDouble() }.toFloat()
                    ln(sharp + 1f) + smile - if (blink) 4f else 0f
                }
                if (score > bestScore) {
                    best?.recycle()
                    best = shot
                    bestScore = score
                } else {
                    shot.recycle()
                }
            }
            bursting = false
            working = false
            status = null
            faces.close()
            val chosen = best ?: return
            feedback()
            say(if (count > 1) tr("Guardada la mejor de $count") else tr("Foto guardada"))
            flyFrom(snapshot)
            keepBitmap(chosen, edit(false))
        }

        /** Selfie con poca luz: la pantalla se ilumina en cálido justo al disparar. */
        suspend fun withScreenLight(block: suspend () -> Unit) {
            val window = activity?.window
            val useLight = front && flash != Flash.OFF
            if (useLight) {
                screenLight = true
                window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
                delay(450)
            }
            try {
                block()
            } finally {
                if (useLight) {
                    screenLight = false
                    window?.attributes = window?.attributes?.apply { screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                }
            }
        }

        fun capture() {
            scope.launch {
                withScreenLight {
                    when {
                        mode == CamMode.NIGHT && phoneMode(mode) == null -> takeNight()
                        mode == CamMode.HDR && phoneMode(mode) == null -> takeHdr()
                        else -> takeOne()
                    }
                }
            }
        }

        fun elapsed(now: Long): Long = now - recordStart - pausedTotal - if (paused) now - pausedAt else 0L

        fun startRecording() {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (isWritableAlbumPath(albumPath)) albumPath else "DCIM/Camera/")
            }
            val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)).setContentValues(values).build()
            // Para otra app, el vídeo va a un archivo aparte y se le entrega al parar.
            val askFile = host.request?.takeIf { it.video }?.let { java.io.File(context.cacheDir, "pedido_${System.nanoTime()}.mp4") }
            recordStart = System.currentTimeMillis()
            pausedTotal = 0L
            paused = false
            if (vm.shutterSound) sound.play(MediaActionSound.START_VIDEO_RECORDING)
            haptic(HapticFeedbackConstants.LONG_PRESS)
            recording = runCatching {
                val listener = androidx.core.util.Consumer<VideoRecordEvent> { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        if (vm.shutterSound) sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                        if (askFile != null) {
                            if (!event.hasError() || askFile.length() > 0) host.request?.onVideo?.invoke(askFile) else vm.say("No se pudo guardar el vídeo")
                        } else if (!event.hasError()) {
                            saved(event.outputResults.outputUri)
                            lastPreview = null
                            if (!host.secure) vm.reload()
                        } else {
                            vm.say("No se pudo guardar el vídeo")
                        }
                    }
                }
                if (askFile != null) controller.startRecording(androidx.camera.video.FileOutputOptions.Builder(askFile).build(), AudioConfig.create(canAudio), main, listener)
                else controller.startRecording(output, AudioConfig.create(canAudio), main, listener)
            }.getOrNull()
        }

        fun stopRecording() {
            recording?.stop()
            recording = null
            paused = false
        }

        fun shutter() {
            if (working) return
            when (mode) {
                CamMode.VIDEO -> if (recording != null) stopRecording() else startRecording()
                CamMode.QR, CamMode.TEXT -> Unit
                CamMode.DOCUMENT -> scanDocument()
                CamMode.SMART -> smartArmed = !smartArmed
                CamMode.TIMELAPSE -> if (lapse != null) {
                    // Parar y montar el vídeo acelerado.
                    lapseJob?.cancel()
                    val frames = lapse!!
                    lapse = null
                    scope.launch {
                        working = true
                        status = tr("Montando el vídeo…")
                        val uri = com.lumi.galeria.data.makeTimelapse(context, frames) { p -> status = tr("Montando el vídeo…") + " $p %" }
                        working = false
                        status = null
                        if (uri == null) vm.say("Hacen falta al menos dos fotos para el timelapse") else {
                            saved(uri)
                            lastPreview = null
                            say(tr("Timelapse guardado"))
                            if (!host.secure) vm.reload()
                        }
                    }
                } else {
                    val frames = com.lumi.galeria.data.TimelapseFrames(context)
                    lapse = frames
                    haptic(HapticFeedbackConstants.LONG_PRESS)
                    lapseJob = scope.launch {
                        while (true) {
                            val start = SystemClock.elapsedRealtime()
                            captureBitmap(controller, context, 1920)?.let { shot ->
                                withContext(Dispatchers.IO) { frames.add(shot) }
                                shot.recycle()
                            }
                            status = "● " + com.lumi.galeria.countText(frames.count, "foto", "fotos") + " · " + String.format(Locale.US, "%.1f s", frames.count / 30f).replace('.', ',')
                            delay((lapseEvery * 1000L - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(50))
                        }
                    }
                }
                else -> if (timer > 0) {
                    if (progress != null) return
                    scope.launch {
                        working = true
                        val total = timer * 1000L
                        val start = SystemClock.elapsedRealtime()
                        while (true) {
                            val gone = SystemClock.elapsedRealtime() - start
                            if (gone >= total) break
                            progress = gone.toFloat() / total
                            status = "${((total - gone) / 1000) + 1}"
                            delay(50)
                        }
                        progress = null
                        status = null
                        working = false
                        capture()
                    }
                } else {
                    capture()
                }
            }
        }

        fun switchMode(next: CamMode) {
            if (next == mode || recording != null || working || lapse != null) return
            haptic()
            focusAt = null
            locked = false
            if (next != CamMode.PRO) {
                iso = null
                shutterNs = null
                focusDist = null
            }
            mode = next
        }

        fun flipCamera() {
            if (recording != null || working) return
            haptic()
            front = !front
        }

        // Disparo inteligente: cuando todos sonríen con los ojos abiertos y el móvil está quieto.
        LaunchedEffect(smartArmed, smartFaces, smartHappy, steady) {
            if (smartArmed && smartFaces > 0 && smartHappy == smartFaces && steady && !working) {
                smartArmed = false
                capture()
            }
        }

        // Las teclas de volumen, según lo elegido en los ajustes.
        DisposableEffect(Unit) {
            vm.volumeKey = { up ->
                when (vm.camPrefs.volume) {
                    VolumeKeys.SHUTTER -> shutter()
                    VolumeKeys.ZOOM -> zoomTo(zoom * if (up) 1.12f else 1f / 1.12f, animate = false)
                    VolumeKeys.RECORD -> if (mode == CamMode.VIDEO) shutter() else {
                        pendingRecord = true
                        switchMode(CamMode.VIDEO)
                    }
                }
            }
            onDispose { vm.volumeKey = null }
        }
        LaunchedEffect(mode, bindCount) {
            if (pendingRecord && mode == CamMode.VIDEO) {
                pendingRecord = false
                delay(700)
                startRecording()
            }
        }

        fun openShot(uri: Uri) {
            val id = mediaIdOf(uri)
            if (id != null && state.items.any { it.id == id }) vm.open(Screen.Viewer(Source.Timeline, id))
            else vm.open(Screen.Viewer(Source.External(uri, false), id ?: -1))
        }

        fun focus(point: Offset, lock: Boolean) {
            val view = previewView ?: return
            val control = controller.cameraControl ?: return
            val metering = view.meteringPointFactory.createPoint(point.x, point.y)
            val action = FocusMeteringAction.Builder(metering, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB).apply {
                if (lock) disableAutoCancel() else setAutoCancelDuration(5, TimeUnit.SECONDS)
            }.build()
            control.cancelFocusAndMetering()
            control.startFocusAndMetering(action)
            focusAt = point
            locked = lock
            // Cada enfoque nuevo empieza con la luz que elija la cámara.
            if (mode != CamMode.PRO) ev = 0
            if (lock) haptic(HapticFeedbackConstants.LONG_PRESS)
        }
        LaunchedEffect(focusAt, locked, touchSun) {
            if (focusAt != null && !locked) {
                delay(3000)
                focusAt = null
            }
        }

        fun leave() {
            if (recording != null) stopRecording()
            // Un timelapse a medias se descarta.
            lapseJob?.cancel()
            lapse?.clear()
            lapse = null
            if (!vm.back()) host.exit?.invoke()
        }
        BackHandler { leave() }

        // --- El visor: exactamente el encuadre que se guarda ---
        Box(
            Modifier.offset(y = vfTop).fillMaxWidth().height(vfHeight).clipToBounds()
                .onGloballyPositioned { vfRect = it.boundsInRoot() },
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        // Con TextureView se le puede poner un filtro de color en directo.
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        this.controller = controller
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = this
                    }
                },
                update = { view ->
                    if (look == CameraLook.NONE) view.setLayerType(View.LAYER_TYPE_NONE, null)
                    else view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(look.matrix()) })
                },
                modifier = Modifier.fillMaxSize(),
            )
            frozen?.let { image ->
                Image(
                    image, null,
                    Modifier.fillMaxSize().alpha(frozenAlpha.value).blur(28.dp).graphicsLayer {
                        rotationY = flipTurn.value
                        cameraDistance = 12f * density.density
                    },
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f * frozenAlpha.value)))
            }
            // Gestos: tocar enfoca, mantener bloquea, pellizcar acerca, deslizar de lado cambia de modo
            // y hacia abajo (o dos toques) gira la cámara.
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(prefs.doubleTapFlip) {
                        detectTapGestures(
                            onTap = { point ->
                                if (locked) {
                                    controller.cameraControl?.cancelFocusAndMetering()
                                    locked = false
                                }
                                if (quickCard) quickCard = false else focus(point, lock = false)
                            },
                            onDoubleTap = if (prefs.doubleTapFlip) ({ _ -> flipCamera() }) else null,
                            onLongPress = { point -> focus(point, lock = true) },
                        )
                    }
                    .pointerInput(modes, mode, prefs.swipeFlip, recording, working) {
                        val swipe = 64.dp.toPx()
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var total = Offset.Zero
                            var pinched = false
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2) {
                                    pinched = true
                                    val change = event.calculateZoom()
                                    if (change != 1f) {
                                        controller.setZoomRatio((zoom * change).coerceIn(minZoom, maxZoom))
                                        bigZoom++
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (!pinched) {
                                    total += event.changes.first().positionChange()
                                }
                            } while (event.changes.any { it.pressed })
                            if (!pinched) {
                                if (abs(total.x) > swipe && abs(total.x) > abs(total.y) * 1.4f) {
                                    val i = modes.indexOf(mode)
                                    val next = modes.getOrNull(if (total.x < 0) i + 1 else i - 1)
                                    if (next != null) switchMode(next)
                                } else if (prefs.swipeFlip && total.y > swipe * 1.4f && abs(total.y) > abs(total.x) * 1.4f) {
                                    flipCamera()
                                }
                            }
                        }
                    },
            )
            traceItem?.let {
                AsyncImage(Thumb(it.uri, 1024, it.modified), null, Modifier.fillMaxSize().alpha(traceAlpha), contentScale = ContentScale.Crop)
            }
            peaking?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            GuideOverlay(guide, tilt, level = prefs.level && mode !in setOf(CamMode.QR, CamMode.TEXT, CamMode.DOCUMENT))
            focusAt?.let { at ->
                FocusRing(at, locked, density)
                val range = controller.cameraInfo?.exposureState?.exposureCompensationRange
                if (range != null && range.upper > range.lower && mode != CamMode.PRO) {
                    ExposureSun(at, ev, range.lower, range.upper, density) { next ->
                        ev = next
                        touchSun++
                    }
                }
            }
            if (mode == CamMode.TEXT) {
                liveLines.forEach { line ->
                    with(density) {
                        Box(
                            Modifier.offset(line.left.toDp(), line.top.toDp()).size((line.right - line.left).toDp(), (line.bottom - line.top).toDp())
                                .clip(RoundedCornerShape(4.dp)).background(Color(0x66FFD43B)).border(1.dp, Color(0xFFFFD43B), RoundedCornerShape(4.dp))
                                .clickable { pickedLine = line },
                        )
                    }
                }
            }
            if (mode == CamMode.QR) QrFrame(qr != null)
            if (mode == CamMode.PRO) histo?.let { Histogram(it, Modifier.align(Alignment.TopEnd).padding(10.dp).size(96.dp, 48.dp)) }
            if (flashOverlay.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashOverlay.value)))
            IrisOverlay(iris.value)
            androidx.compose.animation.AnimatedVisibility(
                modeName != null,
                enter = androidx.compose.animation.fadeIn(tween(120)) + androidx.compose.animation.scaleIn(tween(200), initialScale = 0.85f),
                exit = androidx.compose.animation.fadeOut(tween(400)) + androidx.compose.animation.scaleOut(tween(400), targetScale = 1.08f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(
                    modeName.orEmpty(), style = TitleStyle.copy(fontSize = 40.sp), color = Color.White,
                    modifier = Modifier.rotate(iconAngle).graphicsLayer { shadowElevation = 0f },
                )
            }
            if (showBigZoom) {
                Text(
                    String.format(Locale.US, "%.1f×", zoom).replace('.', ','), style = TitleStyle.copy(fontSize = 40.sp), color = Color.White,
                    modifier = Modifier.align(Alignment.Center).rotate(iconAngle),
                )
            }
        }
        // Al cambiar de modo, su nombre aparece en grande y se desvanece (no al abrir la cámara).
        var firstMode by remember { mutableStateOf(true) }
        LaunchedEffect(mode) {
            if (firstMode) {
                firstMode = false
                return@LaunchedEffect
            }
            modeName = mode.label
            delay(700)
            modeName = null
        }
        LaunchedEffect(bigZoom) {
            if (bigZoom > 0) {
                showBigZoom = true
                delay(700)
                showBigZoom = false
            }
        }
        if (screenLight) Box(Modifier.fillMaxSize().background(Color(0xFFFFF1DC)))

        // --- Arriba: cerrar y los ajustes de la foto, con iconos ---
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TopIcon(CloseLineIcon, "Cerrar", iconAngle) { leave() }
                when (mode) {
                    CamMode.QR -> {
                        TopIcon(TorchIcon, if (torch) "Apagar la linterna" else "Encender la linterna", iconAngle, on = torch) { torch = !torch }
                        TopIcon(PictureIcon, "Leer de una foto", iconAngle) { pickForQr.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        if (!host.secure) TopIcon(HistoryIcon, "Códigos leídos", iconAngle) { qrHistory = true }
                    }
                    CamMode.VIDEO -> {
                        Text(
                            prefs.video.label + " · " + (if (prefs.fps == 60) "60" else "30"),
                            style = LabelStyle, color = Color.White,
                            modifier = Modifier.rotate(iconAngle).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
                                .clickable(enabled = recording == null) { vm.open(Screen.CameraSettings) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        TopIcon(GearIcon, "Ajustes de la cámara", iconAngle) { if (recording == null) vm.open(Screen.CameraSettings) }
                    }
                    CamMode.TEXT, CamMode.DOCUMENT -> TopIcon(GearIcon, "Ajustes de la cámara", iconAngle) { vm.open(Screen.CameraSettings) }
                    else -> {
                        TopIcon(
                            when (flash) { Flash.OFF -> BoltOffIcon; Flash.AUTO -> FlashAutoIcon; else -> BoltIcon }, tr("Flash") + ": " + tr(flash.label), iconAngle,
                            on = flash == Flash.ON,
                        ) {
                            flash = Flash.entries[(flash.ordinal + 1) % Flash.entries.size]
                            say(when (flash) { Flash.AUTO -> "Flash automático"; Flash.ON -> "Flash encendido"; Flash.OFF -> "Flash apagado" })
                        }
                        TopIcon(TimerIcon, if (timer == 0) "Sin temporizador" else "Temporizador: $timer s", iconAngle, on = timer > 0, badge = if (timer > 0) "$timer" else null) {
                            timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 }
                            say(if (timer == 0) "Sin temporizador" else "Temporizador: $timer s")
                        }
                        TopIcon(SparkIcon, if (vm.lumiAuto) "Lumi Auto activado" else "Lumi Auto apagado", iconAngle, on = vm.lumiAuto) {
                            vm.setCameraOption(LumiViewModel.KEY_LUMI_AUTO, !vm.lumiAuto)
                            say(if (vm.lumiAuto) "Lumi Auto activado" else "Lumi Auto apagado")
                        }
                        TopIcon(PaletteIcon, "Filtros", iconAngle, on = look != CameraLook.NONE) { showLooks = !showLooks }
                        if (!host.secure) TopIcon(GearIcon, "Ajustes de la cámara", iconAngle) { vm.open(Screen.CameraSettings) }
                    }
                }
            }
            // Dónde se guarda y el formato, discretos debajo.
            if (mode !in setOf(CamMode.QR, CamMode.TEXT, CamMode.DOCUMENT)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (host.secure || host.request != null) {
                        Spacer(Modifier.width(1.dp))
                    } else if (mode != CamMode.PRIVATE) {
                        Row(
                            Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable(enabled = recording == null) { pickAlbum = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(FolderIcon, null, Modifier.size(15.dp), tint = Color.White)
                            Text(albumName, style = SmallStyle.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                        }
                    } else {
                        Row(
                            Modifier.clip(CircleShape).background(Lumi.Accent).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(LockLineIcon, null, Modifier.size(15.dp), tint = Lumi.OnAccent)
                            Text("Privada", style = SmallStyle.copy(fontWeight = FontWeight.Bold), color = Lumi.OnAccent)
                        }
                    }
                    if (mode != CamMode.VIDEO) {
                        Text(
                            ratio.label, style = SmallStyle.copy(fontWeight = FontWeight.Bold), color = Color.White,
                            modifier = Modifier.rotate(iconAngle).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
                                .clickable { ratio = ShotRatio.entries[(ratio.ordinal + 1) % ShotRatio.entries.size]; haptic() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (locked) {
                Row(
                    Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).background(Color(0xFFFFD43B))
                        .clickable { controller.cameraControl?.cancelFocusAndMetering(); locked = false; focusAt = null }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(LockLineIcon, null, Modifier.size(14.dp), tint = Color.Black)
                    Text("Enfoque y luz fijos", style = SmallStyle.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                }
            }
            if (recording != null) {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { delay(300); now = System.currentTimeMillis() } }
                val seconds = elapsed(now) / 1000
                val left = remember(prefs.video, prefs.fps, now / 10_000) {
                    val free = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(0L)
                    val bits = prefs.video.bitsPerSecond * (if (prefs.fps == 60) 3 else 2) / 2
                    if (bits > 0) free * 8 / bits / 60 else 0
                }
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        (if (paused) "❚❚ " else "● ") + "%d:%02d".format(seconds / 60, seconds % 60), style = LabelStyle, color = Color.White,
                        modifier = Modifier.clip(CircleShape).background(if (paused) Color.DarkGray else Color(0xFFD93025)).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Text(
                        tr("Quedan") + " " + (if (left >= 60) "${left / 60} h ${left % 60} min" else "$left min"), style = LabelStyle, color = Color.White,
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            toast?.let {
                Text(
                    it, style = LabelStyle, color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        // --- Abajo: avisos, zoom, modos y disparador ---
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 10.dp)
                .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag < -14) strip = true else if (drag > 14) strip = false } },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (mode == CamMode.QR) {
                qr?.let { code -> QrCard(code, Modifier.padding(horizontal = 14.dp), onClose = { qr = null; qrCandidate = null }) }
            }
            if (mode == CamMode.PHOTO && qr != null) QrCard(qr!!, Modifier.padding(horizontal = 14.dp), onClose = { qr = null })
            if (mode == CamMode.TEXT && liveLines.isNotEmpty()) TextCard(liveLines, Modifier.padding(horizontal = 14.dp))
            suggest?.takeIf { qr == null }?.let { s ->
                Row(
                    Modifier.clip(CircleShape).background(Color(0xFFFFD43B)).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.text, style = LabelStyle, color = Color.Black,
                        modifier = Modifier.clickable {
                            when (s) {
                                Suggest.QR -> qr = photoQr
                                Suggest.DOCUMENT -> scanDocument()
                                Suggest.NIGHT -> switchMode(CamMode.NIGHT)
                                Suggest.HDR -> switchMode(CamMode.HDR)
                            }
                            suggest = null
                        }.padding(vertical = 8.dp),
                    )
                    Icon(CloseLineIcon, tr("Descartar"), Modifier.clip(CircleShape).clickable {
                        quietUntil[s] = SystemClock.elapsedRealtime() + 30_000
                        suggest = null
                    }.padding(8.dp).size(14.dp), tint = Color.Black)
                }
            }
            (status ?: if (mode == CamMode.SMART && smartArmed) {
                if (smartFaces == 0) tr("Esperando caras…") else tr("$smartHappy de $smartFaces sonríen") + if (steady) "" else " · " + tr("no te muevas")
            } else null)?.let { text ->
                Text(text, style = LabelStyle, color = Color.White, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 7.dp))
            }
            tip?.let { text ->
                Text(text, style = SmallStyle, color = Color.White, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 7.dp))
            }
            if (mode == CamMode.PRO) {
                ProControls(proCaps, controller, ev, { ev = it }, wb, { wb = it }, aeLock, { aeLock = it }, iso, { iso = it }, shutterNs, { shutterNs = it }, focusDist, { focusDist = it })
            }
            if (mode == CamMode.TRACE) {
                Slider(traceAlpha, { traceAlpha = it }, Modifier.width(200.dp), valueRange = 0.1f..0.7f, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Lumi.Accent))
            }
            if (showLooks && mode !in setOf(CamMode.QR, CamMode.TEXT, CamMode.DOCUMENT, CamMode.VIDEO)) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CameraLook.entries.forEach { option ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { look = option }) {
                            Box(
                                Modifier.size(44.dp).rotate(iconAngle).clip(CircleShape).background(lookSwatch(option))
                                    .border(2.dp, if (option == look) Color(0xFFFFD43B) else Color.White.copy(alpha = 0.4f), CircleShape),
                            )
                            Text(option.label, style = SmallStyle.copy(fontSize = 11.sp), color = Color.White)
                        }
                    }
                }
            }
            if (mode == CamMode.TIMELAPSE && lapse == null && !working) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cada", style = LabelStyle, color = Color.White)
                    listOf(1, 2, 5, 10).forEach { s ->
                        Text(
                            "$s s", style = LabelStyle, color = if (s == lapseEvery) Color.Black else Color.White,
                            modifier = Modifier.clip(CircleShape).background(if (s == lapseEvery) Color(0xFFFFD43B) else Color.Black.copy(alpha = 0.5f))
                                .clickable { lapseEvery = s }.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (strip && host.request == null) {
                SessionStrip(
                    session = session, picked = sessionPicked,
                    onOpen = { uri -> if (host.secure) sessionViewer = uri else openShot(uri) },
                    onToggle = { uri -> if (uri in sessionPicked) sessionPicked.remove(uri) else sessionPicked.add(uri) },
                    onShare = {
                        if (host.secure) vm.say("Desbloquea el móvil para compartir") else actions.shareUris(sessionPicked.toList())
                    },
                    onDelete = {
                        val items = sessionPicked.mapNotNull { itemForUri(it) }
                        actions.trash(items) {
                            session.removeAll(sessionPicked)
                            if (lastShot in sessionPicked) {
                                lastShot = session.firstOrNull()
                                lastPreview = null
                            }
                            sessionPicked.clear()
                        }
                    },
                    onClear = { sessionPicked.clear() },
                )
            }
            if (quickCard) {
                val item = lastShot?.let { uri -> mediaIdOf(uri)?.let { id -> state.items.firstOrNull { it.id == id } } ?: itemForUri(uri) }
                QuickCard(
                    preview = lastPreview, uri = lastShot,
                    onEdit = {
                        when {
                            host.secure -> vm.say("Desbloquea el móvil para editar")
                            item != null -> vm.open(Screen.Editor(item.id))
                            else -> vm.say("Un momento: aún se está guardando")
                        }
                    },
                    onShare = { if (host.secure) vm.say("Desbloquea el móvil para compartir") else lastShot?.let { actions.shareUris(listOf(it)) } },
                    onDelete = {
                        if (item != null) actions.trash(listOf(item)) {
                            lastShot = null
                            lastPreview = null
                            quickCard = false
                        } else vm.say("Un momento: aún se está guardando")
                    },
                    onRename = { if (lastShot != null) showRename = true },
                    onClose = { quickCard = false },
                )
            }
            // Zoom: los objetivos del móvil; arrastrar de lado abre la rueda con todos los aumentos.
            if (mode != CamMode.DOCUMENT) {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).pointerInput(minZoom, maxZoom) {
                        var startZoom = 1f
                        var moved = 0f
                        var lastZoom = 1f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                startZoom = zoom
                                lastZoom = zoom
                                moved = 0f
                                dial = true
                                zoomJob?.cancel()
                            },
                            onDragEnd = { scope.launch { delay(1200); dial = false } },
                            onDragCancel = { dial = false },
                        ) { change, dx ->
                            change.consume()
                            moved += dx
                            val next = (startZoom * exp(-moved / 260f)).coerceIn(minZoom, maxZoom)
                            val step = if (next < 2f) 0.1f else 0.5f
                            if (kotlin.math.floor(next / step) != kotlin.math.floor(lastZoom / step)) haptic(HapticFeedbackConstants.CLOCK_TICK)
                            if ((next == minZoom || next == maxZoom) && next != lastZoom) haptic(HapticFeedbackConstants.REJECT)
                            lastZoom = next
                            controller.setZoomRatio(next)
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (dial) {
                        ZoomRuler(zoom, minZoom, maxZoom, Modifier.fillMaxWidth().height(44.dp))
                    } else {
                        val stops = listOfNotNull(minZoom.takeIf { it < 0.95f }, 1f, 2f.takeIf { maxZoom >= 2f }, 5f.takeIf { maxZoom >= 5f })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            stops.forEachIndexed { i, stop ->
                                val next = stops.getOrNull(i + 1) ?: Float.MAX_VALUE
                                val on = zoom >= stop - 0.05f && zoom < next - 0.05f
                                Box(
                                    Modifier.size(if (on) 40.dp else 32.dp).rotate(iconAngle).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                                        .clickable { zoomTo(stop) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (on) String.format(Locale.US, "%.1f×", zoom).replace(".0", "").replace('.', ',') else zoomLabel(stop),
                                        style = SmallStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = if (on) Color(0xFFFFD43B) else Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            ModeWheel(modes, mode, enabled = recording == null && !working) { switchMode(it) }
            // Miniatura, disparador y girar.
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (recording != null) {
                    RoundButton(if (paused) PlayLineIcon else PauseIcon, if (paused) "Seguir grabando" else "Pausar", iconAngle) {
                        val rec = recording ?: return@RoundButton
                        if (paused) {
                            rec.resume()
                            pausedTotal += System.currentTimeMillis() - pausedAt
                            paused = false
                        } else {
                            rec.pause()
                            pausedAt = System.currentTimeMillis()
                            paused = true
                        }
                    }
                } else {
                    Box(
                        Modifier.size(54.dp).rotate(iconAngle).onGloballyPositioned { thumbRect = it.boundsInRoot() }
                            .clip(RoundedCornerShape(14.dp)).background(Color.DarkGray)
                            .pointerInput(lastShot, mode) {
                                detectTapGestures(
                                    onTap = {
                                        when {
                                            host.request != null -> Unit
                                            mode == CamMode.PRIVATE -> vm.openVault(actions.unlock)
                                            host.secure -> lastShot?.let { sessionViewer = it } ?: run { strip = !strip }
                                            lastShot != null -> openShot(lastShot!!)
                                            else -> strip = !strip
                                        }
                                    },
                                    onLongPress = { if (lastShot != null && mode != CamMode.PRIVATE) { haptic(HapticFeedbackConstants.LONG_PRESS); quickCard = true } },
                                )
                            }
                            .semantics { contentDescription = tr("Última foto. Mantén pulsado para editar, compartir o borrar.") },
                    ) {
                        when {
                            mode == CamMode.PRIVATE -> Icon(LockLineIcon, null, Modifier.align(Alignment.Center).size(24.dp), tint = Color.White)
                            lastPreview != null -> Image(lastPreview!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            lastShot != null -> AsyncImage(lastShot, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            // Con el móvil bloqueado o para otra app, nada de la galería.
                            host.secure || host.request != null -> Unit
                            else -> recent.firstOrNull()?.let { AsyncImage(Thumb(it.uri, 256, it.modified), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        if (vm.shotsPending > 0) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp).clip(CircleShape).background(Lumi.Accent),
                                contentAlignment = Alignment.Center,
                            ) { Text("${vm.shotsPending}", style = SmallStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = Lumi.OnAccent) }
                        }
                    }
                }
                ShutterButton(
                    look = when {
                        recording != null || lapse != null -> ShutterLook.RECORDING
                        mode == CamMode.VIDEO -> ShutterLook.VIDEO
                        working -> ShutterLook.BUSY
                        mode == CamMode.PRIVATE -> ShutterLook.PRIVATE
                        mode == CamMode.SMART && smartArmed -> ShutterLook.SMART_ARMED
                        mode == CamMode.SMART -> ShutterLook.SMART
                        else -> ShutterLook.PHOTO
                    },
                    progress = progress,
                    onTap = { shutter() },
                    // Mantener el disparador en Foto: ráfaga que elige sola la mejor.
                    onLongPress = { if (mode == CamMode.PHOTO && !working) scope.launch { takeBurst() } },
                    onRelease = { bursting = false },
                )
                if (recording != null) {
                    // Una foto sin cortar el vídeo: la imagen que se está grabando.
                    RoundButton(CameraLineIcon, "Foto sin parar el vídeo", iconAngle) {
                        val frame = previewView?.bitmap ?: return@RoundButton
                        feedback()
                        flyFrom(frame)
                        if (host.request == null) vm.saveBitmapShot(frame, ShotEdit(auto = false), albumPath, false) { saved(it) }
                    }
                } else {
                    RoundButton(FlipCameraIcon, "Girar la cámara", iconAngle) { flipCamera() }
                }
            }
        }

        // La foto recién hecha vuela hasta la miniatura.
        fly?.let { image ->
            val p = flyAt.value
            val from = vfRect
            val to = thumbRect
            if (from != Rect.Zero && to != Rect.Zero) {
                val left = from.left + (to.left - from.left) * p - rootAt.x
                val top = from.top + (to.top - from.top) * p - rootAt.y
                val width = from.width + (to.width - from.width) * p
                val height = from.height + (to.height - from.height) * p
                with(density) {
                    Image(
                        image, null,
                        Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.size(width.toDp(), height.toDp())
                            .clip(RoundedCornerShape((14 * p).dp)).alpha(1f - p * 0.2f),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }

    review?.let { file ->
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillButton("Repetir", onClick = { file.delete(); review = null }, modifier = Modifier.weight(1f), primary = false)
                PillButton("Usar esta foto", onClick = { host.request?.onPhoto?.invoke(file) }, modifier = Modifier.weight(1f))
            }
        }
    }
    // Con el móvil bloqueado: las fotos de ahora, a pantalla completa, sin acceso a la galería.
    sessionViewer?.let { start ->
        val pager = androidx.compose.foundation.pager.rememberPagerState(session.indexOf(start).coerceAtLeast(0)) { session.size }
        BackHandler { sessionViewer = null }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                AsyncImage(session.getOrNull(page), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TopIcon(CloseLineIcon, "Cerrar", 0f) { sessionViewer = null }
                Spacer(Modifier.weight(1f))
                Text(
                    tr("Solo las fotos de ahora. Desbloquea para ver la galería."), style = SmallStyle, color = Color.White,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
    if (qrHistory) QrHistorySheet(vm) { qrHistory = false }
    if (photoCodes.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { photoCodes = emptyList() }, containerColor = Lumi.Bg) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                photoCodes.forEach { code ->
                    LaunchedEffect(code.rawValue) { vm.rememberQr(codeType(code), code.rawValue.orEmpty()) }
                    QrCard(code)
                }
            }
        }
    }
    if (pickAlbum) {
        AlbumPickerSheet(
            title = "Guardar las fotos en…",
            albums = state.albums,
            onPick = { path, _ ->
                pickAlbum = false
                vm.chooseCameraAlbum(path)
            },
            onDismiss = { pickAlbum = false },
        )
    }
    if (showRename) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = Lumi.Surface,
            title = { Text("Nombre de la foto", style = HeadingStyle) },
            text = { OutlinedTextField(text, { text = it.take(60) }, singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text("Por ejemplo: Ticket del taller") }) },
            confirmButton = {
                Text("Guardar", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable(enabled = text.isNotBlank()) {
                    val uri = lastShot
                    showRename = false
                    quickCard = false
                    if (uri != null) {
                        val ext = runCatching { context.contentResolver.getType(uri) }.getOrNull()?.let { if (it.contains("heif") || it.contains("heic")) ".heic" else ".jpg" } ?: ".jpg"
                        runCatching {
                            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, com.lumi.galeria.data.safeFolder(text) + ext) }, null, null)
                        }
                        vm.reload()
                    }
                }.padding(12.dp))
            },
            dismissButton = { Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { showRename = false }.padding(12.dp)) },
        )
    }
    pickedLine?.let { line ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { pickedLine = null },
            containerColor = Lumi.Surface,
            title = { Text(line.text, style = HeadingStyle, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    findContacts(line.text).forEach { contact ->
                        Text(contact.value, style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable { openContact(context, contact) }.padding(8.dp))
                    }
                }
            },
            confirmButton = {
                Text("Copiar", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable {
                    clipboard.setText(AnnotatedString(line.text))
                    pickedLine = null
                    vm.say("Texto copiado")
                }.padding(12.dp))
            },
            dismissButton = { Text("Cerrar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable { pickedLine = null }.padding(12.dp)) },
        )
    }
}

/** Un icono de la barra de arriba: redondo, del mismo tamaño que los demás y girando con el móvil. */
@Composable
private fun TopIcon(icon: ImageVector, description: String, angle: Float, on: Boolean = false, badge: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).rotate(angle).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClick)
            .semantics { contentDescription = tr(description) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (on) Color(0xFFFFD43B) else Color.White)
        if (badge != null) {
            Text(
                badge, style = SmallStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Black), color = if (on) Color(0xFFFFD43B) else Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 5.dp),
            )
        }
    }
}

/** Un botón redondo a los lados del disparador. */
@Composable
private fun RoundButton(icon: ImageVector, description: String, angle: Float, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).rotate(angle).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onClick)
            .semantics { contentDescription = tr(description) },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(24.dp), tint = Color.White) }
}

/**
 * El disparador: se encoge al pulsarlo, un anillo enseña el avance de Noche, HDR y el temporizador,
 * y en vídeo pasa de círculo rojo a cuadrado mientras graba.
 */
@Composable
private fun ShutterButton(look: ShutterLook, progress: Float?, onTap: () -> Unit, onLongPress: () -> Unit, onRelease: () -> Unit) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val recording = look == ShutterLook.RECORDING
    val inner by animateDpAsState(if (recording) 30.dp else 64.dp, spring(dampingRatio = 0.7f), label = "tamaño")
    val corner by animateDpAsState(if (recording) 8.dp else 32.dp, spring(dampingRatio = 0.7f), label = "esquinas")
    val color by animateColorAsState(
        when (look) {
            ShutterLook.VIDEO, ShutterLook.RECORDING -> Color(0xFFD93025)
            ShutterLook.PRIVATE -> Lumi.Accent
            ShutterLook.SMART_ARMED -> Color(0xFF5BC48A)
            ShutterLook.BUSY -> Color.White.copy(alpha = 0.55f)
            else -> Color.White
        },
        label = "color",
    )
    val shown by animateFloatAsState(progress ?: 0f, tween(120, easing = LinearEasing), label = "avance")
    Box(
        Modifier.size(80.dp).scale(press.value)
            .semantics { contentDescription = tr(if (look == ShutterLook.RECORDING) "Parar el vídeo" else if (look == ShutterLook.VIDEO) "Grabar" else "Hacer la foto") }
            .pointerInput(look) {
                detectTapGestures(
                    onPress = {
                        scope.launch { press.animateTo(0.88f, tween(90)) }
                        tryAwaitRelease()
                        scope.launch { press.animateTo(1f, spring(dampingRatio = 0.5f)) }
                        onRelease()
                    },
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            drawCircle(Color.White, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            if (progress != null) {
                drawArc(
                    Color(0xFFFFD43B), -90f, 360f * shown, false,
                    topLeft = Offset(stroke / 2, stroke / 2), size = GeoSize(size.width - stroke, size.height - stroke), style = Stroke(stroke),
                )
            }
        }
        Box(Modifier.size(inner).clip(RoundedCornerShape(corner)).background(color))
    }
}

/**
 * Los modos en una rueda: el elegido siempre en el centro. Se cambia tocando un nombre o deslizando
 * (aquí o sobre el visor), con una vibración suave en cada paso.
 */
@Composable
private fun ModeWheel(modes: List<CamMode>, selected: CamMode, enabled: Boolean, onSelect: (CamMode) -> Unit) {
    val centers = remember { mutableStateMapOf<CamMode, Float>() }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(40.dp).clipToBounds().pointerInput(selected, enabled) {
            if (!enabled) return@pointerInput
            var moved = 0f
            detectHorizontalDragGestures(onDragStart = { moved = 0f }) { change, dx ->
                change.consume()
                moved += dx
                val step = 56.dp.toPx()
                if (abs(moved) > step) {
                    val i = modes.indexOf(selected)
                    modes.getOrNull(if (moved < 0) i + 1 else i - 1)?.let(onSelect)
                    moved = 0f
                }
            }
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        val half = constraints.maxWidth / 2f
        val target = half - (centers[selected] ?: half)
        val shift by animateFloatAsState(target, spring(dampingRatio = 0.85f, stiffness = 380f), label = "rueda")
        Row(
            Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset { IntOffset(shift.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            modes.forEach { option ->
                val on = option == selected
                Text(
                    option.label, style = LabelStyle.copy(fontSize = 14.sp), color = if (on) Color.Black else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.onGloballyPositioned { centers[option] = it.positionInParent().x + it.size.width / 2f }
                        .clip(CircleShape).background(if (on) Color(0xFFFFD43B) else Color.Transparent)
                        .clickable(enabled = enabled) { onSelect(option) }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** La regla del zoom: todos los aumentos, con el actual en el centro. */
@Composable
private fun ZoomRuler(zoom: Float, min: Float, max: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val label = SmallStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Canvas(modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))) {
        val center = size.width / 2
        val perE = 110.dp.toPx()
        fun x(v: Float) = center + (ln(v) - ln(zoom)) * perE
        var v = min
        while (v <= max + 0.001f) {
            val px = x(v)
            if (px in 0f..size.width) {
                val whole = abs(v - v.roundToInt()) < 0.01f || abs(v - 0.5f) < 0.01f
                drawLine(Color.White.copy(alpha = if (whole) 0.9f else 0.4f), Offset(px, size.height * (if (whole) 0.18f else 0.3f)), Offset(px, size.height * 0.45f), 2f)
            }
            v += if (v < 2f) 0.1f else if (v < 5f) 0.25f else 1f
        }
        listOf(0.5f, 1f, 2f, 3f, 5f, 10f, 20f).filter { it in min..max }.forEach { mark ->
            val px = x(mark)
            if (px in 12f..size.width - 12f) {
                val text = if (mark < 1f) "0,5" else mark.toInt().toString()
                val layout = measurer.measure(text, label)
                drawText(layout, topLeft = Offset(px - layout.size.width / 2, size.height * 0.52f))
            }
        }
        drawLine(Color(0xFFFFD43B), Offset(center, 4f), Offset(center, size.height - 4f), 4f)
    }
}

/** El círculo de enfoque: blanco al enfocar, amarillo si está fijo. */
@Composable
private fun FocusRing(at: Offset, locked: Boolean, density: androidx.compose.ui.unit.Density) {
    val grow = remember(at) { Animatable(1.4f) }
    LaunchedEffect(at) { grow.animateTo(1f, spring(dampingRatio = 0.6f)) }
    with(density) {
        Box(
            Modifier.offset(at.x.toDp() - 34.dp, at.y.toDp() - 34.dp).size(68.dp).scale(grow.value)
                .border(2.dp, if (locked) Color(0xFFFFD43B) else Color.White, CircleShape),
        )
    }
}

/** El sol junto al enfoque: arrastrarlo hacia arriba aclara y hacia abajo oscurece. */
@Composable
private fun ExposureSun(at: Offset, ev: Int, low: Int, high: Int, density: androidx.compose.ui.unit.Density, onChange: (Int) -> Unit) {
    val track = 120.dp
    with(density) {
        val fraction = if (high > low) (ev - low).toFloat() / (high - low) else 0.5f
        val left = at.x.toDp() + 44.dp
        val top = at.y.toDp() - track / 2
        Box(
            Modifier.offset(left, top).size(36.dp, track).pointerInput(low, high) {
                var acc = 0f
                var start = ev
                detectVerticalDragGestures(onDragStart = { acc = 0f; start = ev }) { change, dy ->
                    change.consume()
                    acc += dy
                    onChange((start - (acc / 14.dp.toPx()).roundToInt()).coerceIn(low, high))
                }
            },
        ) {
            Box(Modifier.align(Alignment.Center).width(2.dp).height(track).background(Color(0xFFFFD43B).copy(alpha = 0.6f)))
            Text(
                "☀", color = Color(0xFFFFD43B), fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (track - 24.dp) * (1f - fraction)),
            )
        }
    }
}

/** Histograma de la luz, del negro (izquierda) al blanco (derecha). */
@Composable
private fun Histogram(values: IntArray, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(4.dp)) {
        val top = (values.maxOrNull() ?: 1).coerceAtLeast(1)
        val w = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = size.height * v / top
            drawRect(
                if (i >= values.size - 2) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.8f),
                topLeft = Offset(i * w, size.height - h), size = GeoSize(w, h),
            )
        }
    }
}

/** Tras la foto: editarla, compartirla, borrarla o ponerle nombre, sin salir de la cámara. */
@Composable
private fun QuickCard(preview: ImageBitmap?, uri: Uri?, onEdit: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xF21C1B24)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color.DarkGray)) {
            if (preview != null) Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if (uri != null) AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickAction(PenIcon, "Editar", Modifier.weight(1f), onEdit)
                QuickAction(OpenWithIcon, "Compartir", Modifier.weight(1f), onShare)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickAction(TrashIcon, "Borrar", Modifier.weight(1f), onDelete)
                QuickAction(TextIcon, "Nombre", Modifier.weight(1f), onRename)
            }
        }
        Icon(CloseLineIcon, tr("Cerrar"), Modifier.clip(CircleShape).clickable(onClick = onClose).padding(6.dp).size(18.dp), tint = Color.White)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = Color.White)
        Text(label, style = SmallStyle.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1)
    }
}

/** Los controles del modo Pro: luz, ISO, velocidad, enfoque a mano y color. Cada uno vuelve a automático con «A». */
@Composable
private fun ProControls(
    caps: ProCaps?, controller: LifecycleCameraController,
    ev: Int, onEv: (Int) -> Unit, wb: WhiteBalance, onWb: (WhiteBalance) -> Unit, aeLock: Boolean, onLock: (Boolean) -> Unit,
    iso: Int?, onIso: (Int?) -> Unit, shutterNs: Long?, onShutter: (Long?) -> Unit, focus: Float?, onFocus: (Float?) -> Unit,
) {
    val exposure = controller.cameraInfo?.exposureState
    val range = exposure?.exposureCompensationRange
    val step = exposure?.exposureCompensationStep?.toFloat() ?: 1f
    val manual = caps?.manual == true
    val isoSteps = ISO_STEPS.filter { caps?.iso?.contains(it) ?: false }
    val speeds = SHUTTER_STEPS.filter { caps?.exposure?.contains(it) ?: false }
    val yellow = Color(0xFFFFD43B)
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.72f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        @Composable
        fun ProRow(name: String, auto: Boolean, onAuto: () -> Unit, value: String, content: @Composable () -> Unit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = LabelStyle, color = Color.White, modifier = Modifier.width(48.dp))
                Text(
                    "A", style = LabelStyle, color = if (auto) Color.Black else Color.White,
                    modifier = Modifier.clip(CircleShape).background(if (auto) yellow else Color.White.copy(alpha = 0.15f)).clickable(onClick = onAuto)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
                Box(Modifier.weight(1f).padding(horizontal = 6.dp)) { content() }
                Text(value, style = SmallStyle, color = Color.White, modifier = Modifier.width(52.dp))
            }
        }
        val colors = SliderDefaults.colors(thumbColor = yellow, activeTrackColor = yellow)
        val manualExposure = iso != null || shutterNs != null
        if (range != null && range.upper > range.lower && !manualExposure) {
            ProRow("Luz", ev == 0, { onEv(0) }, String.format(Locale.US, "%+.1f", ev * step).replace('.', ',')) {
                DetentDial(
                    (range.lower..range.upper).map { String.format(Locale.US, "%+.1f", it * step).replace('.', ',').replace("+0,0", "0") },
                    ev - range.lower, { onEv(it + range.lower) }, Modifier.fillMaxWidth(),
                )
            }
        }
        if (manual && isoSteps.size >= 2) {
            val i = isoSteps.indexOf(iso ?: -1).let { if (it < 0) isoSteps.indexOfFirst { s -> s >= 400 }.coerceAtLeast(0) else it }
            ProRow("ISO", iso == null, { onIso(null) }, iso?.toString() ?: "Auto") {
                DetentDial(isoSteps.map { "$it" }, i, { onIso(isoSteps[it]) }, Modifier.fillMaxWidth())
            }
        }
        if (manual && speeds.size >= 2) {
            val i = speeds.indexOf(shutterNs ?: -1L).let { if (it < 0) speeds.indexOfFirst { s -> s >= 1_000_000_000L / 60 }.coerceAtLeast(0) else it }
            ProRow("Vel.", shutterNs == null, { onShutter(null) }, shutterNs?.let { shutterText(it) } ?: "Auto") {
                DetentDial(speeds.map { shutterText(it) }, i, { onShutter(speeds[it]) }, Modifier.fillMaxWidth())
            }
        }
        if (manual && (caps?.minFocus ?: 0f) > 0f) {
            val maxD = caps!!.minFocus
            ProRow("Foco", focus == null, { onFocus(null) }, focus?.let { if (it < 0.05f) "∞" else String.format(Locale.US, "%.1f m", 1f / it).replace('.', ',') } ?: "Auto") {
                Slider(focus ?: 0f, { onFocus(it) }, valueRange = 0f..maxD, colors = colors)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Color", style = LabelStyle, color = Color.White, modifier = Modifier.width(48.dp))
            WhiteBalance.entries.forEach { option ->
                Text(
                    option.label, style = SmallStyle.copy(fontWeight = FontWeight.Bold), color = if (option == wb) Color.Black else Color.White,
                    modifier = Modifier.clip(CircleShape).background(if (option == wb) yellow else Color.White.copy(alpha = 0.15f)).clickable { onWb(option) }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        if (!manualExposure) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bloquear la luz", style = LabelStyle, color = Color.White, modifier = Modifier.weight(1f))
                Switch(aeLock, onLock, colors = SwitchDefaults.colors(checkedTrackColor = yellow))
            }
        }
        if (focus != null) Text("Lo enfocado se marca en rosa.", style = SmallStyle, color = Color.White.copy(alpha = 0.8f))
        if (!manual && caps != null) Text("Este móvil no deja controlar ISO, velocidad ni enfoque a mano.", style = SmallStyle, color = Color.White.copy(alpha = 0.8f))
    }
}

/**
 * Todos los ajustes de la Cámara Lumi, en cuadrícula como el menú «Más» de las fotos: cada ajuste
 * es un círculo con su icono, lleno si está activado, con su nombre y lo elegido debajo.
 */
@Composable
fun CameraSettingsScreen(vm: LumiViewModel) {
    val prefs = vm.camPrefs
    var picking by remember { mutableStateOf<String?>(null) }
    val guideNow = runCatching { Guide.valueOf(prefs.guide ?: "NONE") }.getOrDefault(Guide.NONE)
    Column(Modifier.fillMaxSize().background(Lumi.Bg).navigationBarsPadding()) {
        ScreenHeader("Ajustes de la cámara", "Cámara Lumi", onBack = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsTitle("Fotos")
            SettingsGrid(
                listOf(
                    SettingTile(Icons.Outlined.AutoAwesome, "Lumi Auto", if (vm.lumiAuto) "Activado" else "Apagado", vm.lumiAuto) {
                        vm.setCameraOption(LumiViewModel.KEY_LUMI_AUTO, !vm.lumiAuto)
                    },
                    SettingTile(Icons.Outlined.PhotoSizeSelectLarge, "Tamaño", prefs.size.label.substringBefore(" ("), null) { picking = "size" },
                    SettingTile(Icons.Outlined.Image, "Formato", prefs.format.label.substringBefore(" ("), null) { picking = "format" },
                    SettingTile(Icons.Outlined.Collections, "Original", if (vm.keepOriginal) "Se guarda" else "No", vm.keepOriginal) {
                        vm.setCameraOption(LumiViewModel.KEY_KEEP_ORIGINAL, !vm.keepOriginal)
                    },
                    SettingTile(Icons.Outlined.Flip, "Espejo", if (vm.mirrorSelfie) "Selfies" else "No", vm.mirrorSelfie) {
                        vm.setCameraOption(LumiViewModel.KEY_MIRROR, !vm.mirrorSelfie)
                    },
                    SettingTile(Icons.Outlined.GridOn, "Guías", guideNow.label, guideNow != Guide.NONE) { picking = "guide" },
                    SettingTile(Icons.Outlined.Straighten, "Nivel", if (prefs.level) "Activado" else "Apagado", prefs.level) {
                        prefs.chooseLevel(!prefs.level)
                    },
                    SettingTile(Icons.Outlined.Camera, "Obturador", if (prefs.iris) "Diafragma" else "Destello", prefs.iris) {
                        prefs.chooseIris(!prefs.iris)
                    },
                    SettingTile(Icons.AutoMirrored.Outlined.VolumeUp, "Sonido", if (vm.shutterSound) "Activado" else "Apagado", vm.shutterSound) {
                        vm.setCameraOption(LumiViewModel.KEY_SHUTTER_SOUND, !vm.shutterSound)
                    },
                ),
            )
            SettingsTitle("Vídeo")
            SettingsGrid(
                listOf(
                    SettingTile(Icons.Outlined.HighQuality, "Calidad", prefs.video.label, prefs.video == VideoQuality.UHD) { picking = "video" },
                    SettingTile(Icons.Outlined.Speed, "Fotogramas", "${prefs.fps} fps", prefs.fps == 60) { picking = "fps" },
                    SettingTile(Icons.Outlined.Vibration, "Estabilizar", if (prefs.stabilize) "Activado" else "Apagado", prefs.stabilize) {
                        prefs.chooseStabilize(!prefs.stabilize)
                    },
                ),
            )
            SettingsTitle("Controles")
            SettingsGrid(
                listOf(
                    SettingTile(Icons.AutoMirrored.Outlined.VolumeDown, "Volumen", prefs.volume.label, null) { picking = "volume" },
                    SettingTile(Icons.Outlined.SwipeDown, "Deslizar", if (prefs.swipeFlip) "Gira" else "No", prefs.swipeFlip) {
                        prefs.chooseSwipeFlip(!prefs.swipeFlip)
                    },
                    SettingTile(Icons.Outlined.TouchApp, "Dos toques", if (prefs.doubleTapFlip) "Gira" else "No", prefs.doubleTapFlip) {
                        prefs.chooseDoubleTapFlip(!prefs.doubleTapFlip)
                    },
                    SettingTile(Icons.Outlined.Restore, "Recordar", if (prefs.rememberSettings) "Activado" else "Apagado", prefs.rememberSettings) {
                        prefs.setRemember(!prefs.rememberSettings)
                    },
                ),
            )
            Text(
                "Los círculos llenos están activados. Las fotos algo torcidas (hasta 8°) se enderezan solas. La resolución 4K y los 60 fotogramas solo se usan si el móvil los admite.",
                style = SmallStyle, modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
            )
        }
    }
    when (picking) {
        "size" -> ChoiceDialog("Tamaño de foto", PhotoSize.entries, prefs.size, { it.label }, { prefs.chooseSize(it); picking = null }) { picking = null }
        "format" -> ChoiceDialog("Formato", PhotoFormat.entries, prefs.format, { it.label }, { prefs.chooseFormat(it); picking = null }) { picking = null }
        "guide" -> ChoiceDialog("Guías", Guide.entries, guideNow, { it.label }, { prefs.chooseGuide(it.name); picking = null }) { picking = null }
        "video" -> ChoiceDialog("Calidad de vídeo", VideoQuality.entries, prefs.video, { it.label }, { prefs.chooseVideo(it); picking = null }) { picking = null }
        "fps" -> ChoiceDialog("Fotogramas por segundo", listOf(30, 60), prefs.fps, { "$it" }, { prefs.chooseFps(it); picking = null }) { picking = null }
        "volume" -> ChoiceDialog("Teclas de volumen", VolumeKeys.entries, prefs.volume, { it.label }, { prefs.chooseVolume(it); picking = null }) { picking = null }
    }
}

/** Un ajuste de la cuadrícula. [on] = null si no es de activar y desactivar. */
private class SettingTile(val icon: ImageVector, val label: String, val value: String, val on: Boolean?, val onClick: () -> Unit)

@Composable
private fun SettingsTitle(text: String) {
    Text(text, style = LabelStyle, color = Lumi.Accent, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp))
}

@Composable
private fun SettingsGrid(tiles: List<SettingTile>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Lumi.Surface).padding(vertical = 10.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.chunked(4).forEach { line ->
            Row(Modifier.fillMaxWidth()) {
                line.forEach { tile -> SettingTileView(tile, Modifier.weight(1f)) }
                repeat(4 - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SettingTileView(tile: SettingTile, modifier: Modifier) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val filled = tile.on == true
    val circle by animateColorAsState(if (filled) Lumi.Accent else Lumi.Accent.copy(alpha = 0.14f), label = "circulo")
    val tint by animateColorAsState(if (filled) Lumi.OnAccent else Lumi.Accent, label = "icono")
    Column(
        modifier.pressScale(source).clip(RoundedCornerShape(16.dp)).clickable(source, null, onClick = tile.onClick).padding(vertical = 6.dp)
            .semantics { contentDescription = tr(tile.label) + ": " + tr(tile.value) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
            Icon(tile.icon, null, Modifier.size(22.dp), tint = tint)
        }
        Text(tile.label, style = SmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = Lumi.Ink, maxLines = 1)
        Text(tile.value, style = SmallStyle.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Lo hecho en esta sesión de cámara: tocar abre; mantener elige varias para compartir o borrar. */
@Composable
private fun SessionStrip(
    session: List<Uri>, picked: List<Uri>,
    onOpen: (Uri) -> Unit, onToggle: (Uri) -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.78f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (session.isEmpty()) tr("Aún no has hecho nada en esta sesión") else tr("Esta sesión") + " · " + com.lumi.galeria.countText(session.size, "elemento", "elementos"),
                style = LabelStyle, color = Color.White, modifier = Modifier.weight(1f),
            )
            if (picked.isNotEmpty()) {
                Text("Compartir ${picked.size}", style = LabelStyle, color = Color.Black, modifier = Modifier.clip(CircleShape).background(Color(0xFFFFD43B)).clickable(onClick = onShare).padding(horizontal = 12.dp, vertical = 6.dp))
                Spacer(Modifier.width(6.dp))
                Text("Borrar ${picked.size}", style = LabelStyle, color = Color.White, modifier = Modifier.clip(CircleShape).background(Color(0xFFD93025)).clickable(onClick = onDelete).padding(horizontal = 12.dp, vertical = 6.dp))
                Spacer(Modifier.width(6.dp))
                Icon(CloseLineIcon, tr("Quitar selección"), Modifier.clip(CircleShape).clickable(onClick = onClear).padding(6.dp).size(16.dp), tint = Color.White)
            }
        }
        if (session.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(session, key = { it.toString() }) { uri ->
                    val on = uri in picked
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                            .border(if (on) 3.dp else 0.dp, if (on) Color(0xFFFFD43B) else Color.Transparent, RoundedCornerShape(10.dp))
                            .pointerInput(uri, picked.isEmpty()) {
                                detectTapGestures(
                                    onTap = { if (picked.isNotEmpty()) onToggle(uri) else onOpen(uri) },
                                    onLongPress = { onToggle(uri) },
                                )
                            },
                    ) {
                        AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (uri.path.orEmpty().contains("/video/")) Icon(PlayLineIcon, null, Modifier.align(Alignment.Center).size(22.dp), tint = Color.White)
                    }
                }
            }
            if (picked.isEmpty()) Text("Toca para verla; mantén pulsado para elegir varias.", style = SmallStyle, color = Color.White.copy(alpha = 0.8f))
        }
    }
}
