# Seguridad de Lumi: revisión antes de producción

Lumi no tiene servidor, cuentas ni permiso de INTERNET. Todo vive en el teléfono. Por eso la mayor parte de las
listas de seguridad para webs y apps con base de datos (claves de API, RLS, cookies, rate limiting, XSS, cabeceras
HTTP, HTTPS) no aplican. Esta es la revisión de lo que sí aplica.

## Hecho

| Punto | Qué se hizo |
| --- | --- |
| Copia de seguridad de Android | `allowBackup="false"`. Antes podía subir a la cuenta de Google del usuario el índice de personas, las notas y los ajustes. |
| Fuerza bruta del PIN | Tras 5 fallos seguidos hay esperas de 30 s, 1 min, 5 min y 15 min. Se guardan en las preferencias, cerrar la app no las salta. La comparación del hash es de tiempo constante. |
| Archivos internos expuestos | `MainActivity` (exportada) ya no abre un `file://` que apunte a la carpeta privada de Lumi (índices, carpeta cifrada, ajustes). |
| Secretos en git | Comprobado: `*.jks` y `keystore.properties` están en `.gitignore` y no aparecen en ningún commit. |
| Dependencias | Subidas dentro de la misma versión mayor: WorkManager 2.10.5, CameraX 1.4.2, ExifInterface 1.4.2, escáner de documentos 16.0.0 (ya no es beta). |
| Arranque | El borrado de archivos temporales de la carpeta privada ya no ocurre en el hilo principal. |
| Cierres inesperados | Registro local sin datos personales (`CrashLog`). Se ofrece enviar el informe por correo; nunca se envía solo. |

## Ya estaba bien

- Carpeta privada cifrada, con PIN señuelo, y con captura de pantalla bloqueada (`FLAG_SECURE`).
- PIN guardado con PBKDF2-HMAC-SHA256 (60 000 vueltas) y sal propia, nunca en claro.
- Sin llamadas a `Log` que dejen rutas o nombres en el registro del teléfono.
- El proveedor de pegatinas es de solo lectura y rechaza rutas con `..`.
- Los modelos de ML Kit los descarga Google Play; la app no necesita internet.

## Pendiente y por qué no se hizo ahora

Estas subidas son de versión mayor o cambian la herramienta de compilación. Hacerlas justo antes de publicar es en sí
un riesgo; conviene hacerlas en una rama, con las pruebas de `playstore/QA-pruebas.md`, o justo después del primer
lanzamiento.

| Pieza | Ahora | Última estable (2026-10) | Notas |
| --- | --- | --- | --- |
| Kotlin | 2.0.21 | 2.4.x | `play-services-tflite-java` 16.5.0 ya exige Kotlin 2.2: por eso se queda en 16.4.0. |
| Android Gradle Plugin | 8.7.2 | 9.4.x | Cambio grande (AGP 9). |
| Gradle | 8.11.1 | 9.8.x | Va con AGP. |
| Compose BOM | 2024.12.01 | 2026.09.00 | Sube Compose de la 1.7 a la 1.12. |
| Media3 | 1.4.1 | 1.11.x | El editor de vídeo usa efectos de la 1.4.1. Subir exige repasar `VideoEdit.kt`. |
| CameraX | 1.4.2 | 1.6.x | Estable nueva, cambios menores. |
| activity-compose, core-ktx, lifecycle | 1.9.3 / 1.15.0 / 2.8.7 | 1.13 / 1.19 / 2.11 | Piden `compileSdk` más alto en algunas. |

Para mirar versiones nuevas sin tocar nada: `./gradlew.bat :app:dependencies` y comparar con
`https://maven.google.com`. Conviene activar Dependabot en el repositorio.

## A vigilar cuando haya pago

Para el pago único se usará Google Play Billing: Lumi no ve la tarjeta. Creo que no exige permiso de INTERNET en la
app, pero hay que confirmarlo al integrarlo y, si hiciera falta, volver a publicar la política de privacidad
(`docs/privacidad.html`, `docs/privacy.html`, `PRIVACY.md`).

## Componentes exportados

| Componente | Por qué es exportado | Riesgo |
| --- | --- | --- |
| `MainActivity` | Es la pantalla de inicio y abre fotos desde otras apps | Bajo. Solo muestra datos; abrir la carpeta privada sigue pidiendo huella o PIN. |
| `CameraActivity` y `CameraLauncher` | Otras apps piden hacer una foto | Bajo. |
| `CameraWidgetSetupActivity`, `WidgetSetupActivity` | Android las abre al poner un widget | Bajo. |
| `StickerProvider` | WhatsApp lee las pegatinas | Bajo. Solo lectura y protegido con el permiso `com.whatsapp.sticker.READ`. |
