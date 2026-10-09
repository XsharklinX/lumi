package com.lumi.galeria

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lumi.galeria.data.trashRequest
import com.lumi.galeria.ui.Actions
import com.lumi.galeria.ui.CameraHost
import com.lumi.galeria.ui.CaptureAsk
import com.lumi.galeria.ui.LabelStyle
import com.lumi.galeria.ui.Lumi
import com.lumi.galeria.ui.LumiTheme
import com.lumi.galeria.ui.Text
import com.lumi.galeria.widget.Widgets
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La Cámara Lumi a solas: la que abre el icono «Cámara Lumi», el widget de cámara, la pantalla de
 * bloqueo y las demás apps cuando piden una foto. Arranca sin cargar la galería, así que está
 * lista para disparar al momento. Lo que necesite la galería (ver una foto, editarla) se abre en Lumi.
 */
class CameraActivity : FragmentActivity() {
    private val vm: LumiViewModel by viewModels()
    private var afterTrash: (() -> Unit)? = null

    private val trashLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) afterTrash?.invoke()
        afterTrash = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val action = intent?.action.orEmpty()
        // Desde la pantalla de bloqueo: se ve encima, sin desbloquear, y nada de la galería.
        val secure = action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE || action == MediaStore.ACTION_IMAGE_CAPTURE_SECURE
        if (secure) setShowWhenLocked(true)
        val request = when (action) {
            MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.ACTION_IMAGE_CAPTURE_SECURE -> CaptureAsk(false, onPhoto = ::deliverPhoto, onVideo = {})
            MediaStore.ACTION_VIDEO_CAPTURE -> CaptureAsk(true, onPhoto = {}, onVideo = ::deliverVideo)
            else -> null
        }
        if (request == null) {
            vm.cameraStart = when {
                action == MediaStore.INTENT_ACTION_VIDEO_CAMERA -> "VIDEO"
                else -> intent?.getStringExtra(EXTRA_MODE)
            }
        }
        val host = CameraHost(
            request = request,
            secure = secure,
            front = intent?.getBooleanExtra(EXTRA_FRONT, false) == true,
            exit = { finish() },
            standalone = true,
        )
        vm.switchTab(Screen.Camera)
        val actions = Actions(
            trash = { items, onDone ->
                val real = items.filter { !it.isExternal }
                if (real.isNotEmpty()) runCatching {
                    afterTrash = onDone
                    trashLauncher.launch(IntentSenderRequest.Builder(trashRequest(this, real, true)).build())
                }
            },
            restore = {}, deleteForever = {}, write = { _, _ -> }, share = { items -> share(this, items.map { it.uri }, false, true) },
            shareWithoutLocation = {}, useAs = {}, requestAccess = {}, openSettings = {}, pickResult = {},
            // La carpeta privada se abre en Lumi, que pide la huella o el PIN.
            unlock = { openGallery(Intent(this, MainActivity::class.java).setAction(SHORTCUT_PREFIX + "privada")) },
            canUnlock = { true }, allFiles = {}, pip = {}, camera = {},
            shareUris = { uris -> share(this, uris, false, false) },
        )
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            Lang.apply(state.language)
            LumiTheme(state.theme, state.accent, state.pureBlack, vm.textScale, vm.highContrast) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                    when (val top = vm.backStack.lastOrNull()) {
                        Screen.Camera -> com.lumi.galeria.ui.CameraScreen(state, vm, actions, host)
                        Screen.CameraSettings -> com.lumi.galeria.ui.CameraSettingsScreen(vm)
                        // Lo escaneado se guarda aquí mismo, sin pasar por la galería.
                        Screen.SaveDoc -> com.lumi.galeria.ui.SaveDocScreen(vm)
                        Screen.IdCard -> com.lumi.galeria.ui.IdCardScreen(vm)
                        null -> LaunchedEffect(Unit) { finish() }
                        else -> LaunchedEffect(top) {
                            forward(top)
                            vm.back()
                        }
                    }
                    state.message?.let { message ->
                        Text(
                            message, style = LabelStyle, color = Lumi.Bg, textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 200.dp)
                                .clip(RoundedCornerShape(22.dp)).background(Lumi.Ink).padding(horizontal = 18.dp, vertical = 11.dp),
                        )
                    }
                }
            }
        }
        // Lo justo de la galería (álbumes y últimas fotos), leído de la copia guardada, cuando ya se puede disparar.
        if (!secure && request == null) lifecycleScope.launch {
            delay(1500)
            vm.loadQuick()
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val key = vm.volumeKey
        if (key != null && (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event?.repeatCount == 0 || vm.camPrefs.volume == com.lumi.galeria.data.VolumeKeys.ZOOM) key(keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Lo que pide la galería se abre en Lumi; la cámara se queda debajo para volver. */
    private fun forward(screen: Screen) {
        val intent = when (screen) {
            is Screen.Viewer -> when (val source = screen.source) {
                is Source.External -> Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .setDataAndType(source.uri, if (source.isVideo) "video/*" else "image/*")
                else -> Intent(this, MainActivity::class.java).setAction(Widgets.ACTION_OPEN).putExtra(Widgets.EXTRA_PHOTO, screen.startId)
            }
            is Screen.Editor -> Intent(this, MainActivity::class.java).setAction(ACTION_EDIT).putExtra(Widgets.EXTRA_PHOTO, screen.id)
            Screen.Vault -> Intent(this, MainActivity::class.java).setAction(SHORTCUT_PREFIX + "privada")
            else -> Intent(this, MainActivity::class.java)
        }
        openGallery(intent)
    }

    private fun openGallery(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
    }

    /** Dónde quiere la otra app la foto o el vídeo; null si no lo dice. */
    private fun output(): Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)
    else @Suppress("DEPRECATION") (intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT) as? Uri)

    /** La foto para otra app: en su archivo si lo dio; si no, una miniatura, que es lo que espera Android. */
    private fun deliverPhoto(file: File) {
        lifecycleScope.launch {
            val target = output()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (target != null) {
                        contentResolver.openOutputStream(target, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                        Intent().setData(target).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        val full = BitmapFactory.decodeFile(file.path)
                        val k = minOf(1f, 512f / maxOf(full.width, full.height))
                        Intent().putExtra("data", Bitmap.createScaledBitmap(full, (full.width * k).toInt(), (full.height * k).toInt(), true))
                    }
                }.getOrNull()
            }
            file.delete()
            if (result != null) setResult(Activity.RESULT_OK, result) else setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    /** El vídeo para otra app: en su archivo si lo dio; si no, en la galería, y se le pasa la dirección. */
    private fun deliverVideo(file: File) {
        lifecycleScope.launch {
            val target = output()
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    if (target != null) {
                        contentResolver.openOutputStream(target, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                        target
                    } else {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_" + System.currentTimeMillis() + ".mp4")
                            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/")
                        }
                        val made = contentResolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
                        contentResolver.openOutputStream(made)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                        made
                    }
                }.getOrNull()
            }
            file.delete()
            if (uri != null) setResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) else setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    companion object {
        /** Modo con el que abrir: PHOTO, VIDEO, QR… (desde el widget de cámara). */
        const val EXTRA_MODE = "modo"
        /** Empezar con la cámara delantera. */
        const val EXTRA_FRONT = "delantera"
    }
}

/** El segundo icono, «Cámara Lumi», en el cajón de apps. */
fun cameraIconOn(context: android.content.Context): Boolean =
    context.packageManager.getComponentEnabledSetting(android.content.ComponentName(context, "com.lumi.galeria.CameraLauncher")) ==
        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED

fun setCameraIcon(context: android.content.Context, on: Boolean) {
    context.packageManager.setComponentEnabledSetting(
        android.content.ComponentName(context, "com.lumi.galeria.CameraLauncher"),
        if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        android.content.pm.PackageManager.DONT_KILL_APP,
    )
}
