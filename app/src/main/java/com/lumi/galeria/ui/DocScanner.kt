package com.lumi.galeria.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.lumi.galeria.LumiViewModel

/**
 * El escáner de documentos de Google: encuentra la hoja solo, la endereza, quita sombras y deja
 * retocar cada página. Se le puede hacer la foto en el momento o elegir una de la galería. Todo
 * pasa en el teléfono; el escáner lo instala Google Play la primera vez.
 * Devuelve la función que lo abre.
 */
@Composable
fun rememberDocumentScanner(vm: LumiViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data) ?: return@rememberLauncherForActivityResult
        vm.saveScan(scan.pages?.map { it.imageUri }.orEmpty(), scan.pdf?.uri)
    }
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(30)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build(),
        )
    }
    return {
        val activity = context as Activity
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { vm.say("El escáner aún no está listo: Google Play lo está descargando. Prueba otra vez en un minuto.") }
    }
}
