# Pruebas antes de publicar

Lo que sigue hay que hacerlo con móvil o emulador. No se puede comprobar desde el código.

## 1. Biblioteca enorme (50 000 fotos y 2 000 vídeos)

Preparar el móvil o emulador (carpeta `Pictures/Prueba`), a partir de unas pocas fotos reales para que ML Kit encuentre cosas:

```
adb shell mkdir -p /sdcard/Pictures/Prueba /sdcard/Movies/Prueba
adb push foto1.jpg foto2.jpg foto3.jpg /sdcard/Pictures/base/
adb shell 'i=0; for f in /sdcard/Pictures/base/*.jpg; do for n in $(seq 1 1000); do cp $f /sdcard/Pictures/Prueba/c${i}_$n.jpg; done; i=$((i+1)); done'
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/Prueba
```

Repetir con más copias hasta 50 000. Para los vídeos, lo mismo con 2-3 clips en `Movies/Prueba`.

Medir y apuntar:

| Qué | Cómo | Objetivo |
| --- | --- | --- |
| Arranque en frío | `adb shell am force-stop com.lumigallery.app` y `adb shell am start -W -n com.lumigallery.app/com.lumi.galeria.MainActivity` (mirar `TotalTime`) | Menos de 1,5 s con la biblioteca de prueba |
| Fluidez al desplazarse | `adb shell dumpsys gfxinfo com.lumigallery.app reset`, desplazar 30 s, `adb shell dumpsys gfxinfo com.lumigallery.app` | Menos del 5 % de fotogramas lentos |
| Memoria | `adb shell dumpsys meminfo com.lumigallery.app` tras desplazar rápido | Sin cierres; el total no debería pasar de unos 600 MB |
| Indexado en segundo plano | Dejar 1 h con la pantalla apagada y mirar «Estado de Lumi» | Avanza sin recalentar |
| Batería | `adb shell dumpsys batterystats --reset`, dejar 1 h, `adb shell dumpsys batterystats com.lumigallery.app` | Sin consumo anormal frente a la galería del sistema |

## 2. Matriz de dispositivos

Marcar cada casilla probando: galería, visor, editor de fotos, editor de vídeo, cámara, carpeta privada y ajustes.

| Caso | Android 11 | 12 | 13 | 14 | 15 | 16 |
| --- | --- | --- | --- | --- | --- | --- |
| Móvil pequeño (5") | | | | | | |
| Móvil grande (6,7") | | | | | | |
| Plegable abierto y cerrado | | | | | | |
| Tablet horizontal y vertical | | | | | | |

Condiciones a repetir en cada una: tema oscuro del sistema, fuente grande (200 %), idioma inglés, modo de ahorro de batería,
sin permiso de fotos / con acceso solo a las elegidas, TalkBack encendido.

## 3. Accesibilidad

- Recorrer con TalkBack las pantallas nuevas: Descubre Lumi, Estado de Lumi, Fechas por revisar, Pegatinas, Enviar, editores.
- Todos los botones se alcanzan con una zona de al menos 48 dp.
- Con «Quitar animaciones» del sistema, la cascada de miniaturas y los muelles se apagan.

## 4. Panel de salud de Google Play (Android vitals)

Nada que añadir a la app. En Play Console, en **Calidad > Android vitals**, la primera semana mirar cada día:

- **Tasa de cierres percibidos por el usuario** y **tasa de ANR**. Google marca como mal comportamiento por encima de ~1,09 % (cierres) y ~0,47 % (ANR) de las sesiones; por modelo de teléfono el umbral es mayor.
- **Tiempo de arranque** en frío y en caliente.
- **Reseñas y errores** agrupados por dispositivo: un modelo concreto que falla suele ser un fallo de ese fabricante.
- Los informes que envíen los usuarios a contactosharklin@gmail.com desde «Lumi se cerró la última vez».

## 5. Lo que no he podido probar yo

El editor de vídeo (fondo desenfocado, transiciones, textos, música con fundido), el revelado de RAW de 16 bits, las pegatinas
con WhatsApp, la lupa del visor y el vuelo de la foto hacia la papelera están escritos y compilan, pero hay que probarlos en un móvil.
