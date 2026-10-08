import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma de subida a Google Play. El almacén y sus claves viven fuera del repositorio
// (keystore.properties y el .jks están en .gitignore); sin ellos se firma con la clave de pruebas.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use(::load) }

android {
    namespace = "com.lumi.galeria"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lumigallery.app"
        // Android 11: la papelera del sistema (createTrashRequest) existe desde aquí.
        minSdk = 30
        targetSdk = 36
        versionCode = 1100
        versionName = "1.1.0"
        // Los modelos de reconocimiento traen código nativo por arquitectura. En Google Play cada
        // teléfono descarga solo la suya; x86_64 es para el emulador.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        create("upload") {
            if (keystoreFile.exists()) {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (keystoreFile.exists()) "upload" else "debug")
        }
    }

    // Dos variantes de la misma app. "play" es la que se sube a la tienda; "full" añade lo que
    // Google Play no admite en una galería: ver las carpetas ocultas del sistema, que exige el
    // permiso de acceso a todos los archivos.
    flavorDimensions += "canal"
    productFlavors {
        create("play") {
            dimension = "canal"
            buildConfigField("boolean", "HIDDEN_FOLDERS", "false")
        }
        create("full") {
            dimension = "canal"
            buildConfigField("boolean", "HIDDEN_FOLDERS", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // El modelo de caras se lee directamente del APK: no puede ir comprimido.
    androidResources {
        noCompress += "tflite"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    // Pantalla de arranque con el logo animado, igual en todas las versiones de Android.
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Cambiar el fondo y avisar de recuerdos a su hora, sin nada abierto en segundo plano.
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // Guardar las fotos de la cámara en HEIC con el codificador del propio teléfono.
    implementation("androidx.heifwriter:heifwriter:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Para que los GIF y las imágenes animadas se muevan.
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // Volver a comprimir vídeos para que ocupen menos.
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
    // Instala el perfil de arranque (src/main/baseline-prof.txt) también cuando la app no llega por Google Play.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Leer texto, reconocer cosas y separar el fondo. Los modelos no van dentro de la app: los
    // descarga Google Play en el teléfono y el análisis se hace igualmente en el dispositivo.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.android.gms:play-services-mlkit-image-labeling:16.0.8")
    // Escáner de documentos: encuentra la hoja, la endereza y la limpia. Lo instala Google Play.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    // Personas: encontrar las caras (ML Kit) y sacar la huella de cada una con un modelo pequeño
    // (assets/caras.tflite). El motor que lo ejecuta lo pone Google Play, no va dentro de la app.
    implementation("com.google.android.gms:play-services-mlkit-face-detection:17.1.0")
    implementation("com.google.android.gms:play-services-tflite-java:16.4.0")
    // Cámara Lumi: CameraX (vista previa, fotos, vídeo, modos del móvil) y el puente con ML Kit para
    // leer QR y texto en vivo. El lector de códigos lo descarga Google Play, como los demás.
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")
    implementation("androidx.camera:camera-extensions:1.4.1")
    implementation("androidx.camera:camera-mlkit-vision:1.4.1")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
}
