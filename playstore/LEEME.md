# Publicar Lumi Gallery en Google Play

Identificador de la app: `com.lumigallery.app`

## Qué hay en esta carpeta

| Archivo | Para qué |
|---|---|
| `icono-512.png` | Icono de la ficha (512 × 512). |
| `grafico-1024x500.png` | Gráfico de funciones, obligatorio. |
| `ficha.md` | Nombre, descripción breve y descripción completa, en español e inglés. |
| `../docs/` | Política de privacidad en español e inglés, lista para GitHub Pages. |
| `capturas/` | Capturas de teléfono. Se montan con `make_captures.py`. |
| `make_graphics.py`, `make_captures.py` | Regeneran el icono, el gráfico y las capturas. |

Lo que se sube está en `../Release/`: el archivo `LumiGallery-X-play.aab`.
Para generarlo de nuevo: `python compilar.py` desde la carpeta del proyecto.

## Antes de subir nada

1. **Guarda una copia de la firma.** Son dos archivos en la carpeta del proyecto que no están en
   git: `lumi-upload.jks` y `keystore.properties` (contiene la contraseña). Sin ellos no podrás
   subir actualizaciones. Cópialos a un sitio seguro fuera de este ordenador.
2. **Enlace de la política de privacidad.** Hay dos, y cualquiera vale para Google Play:
   - `https://github.com/XsharklinX/lumi/blob/main/PRIVACY.md` funciona en cuanto hagas el
     push, sin configurar nada, siempre que el repositorio sea público.
   - `https://xsharklinx.github.io/lumi/privacidad.html` es una página web propia. Para
     activarla: en GitHub, Settings → Pages → rama `main`, carpeta `/docs`. Tarda un minuto.
3. **Revisa las capturas.** Ya hay siete en `capturas/`, hechas con fotos de muestra.

## Pasos en Play Console

1. **Crear la app.** Nombre "Lumi Gallery", idioma español, tipo aplicación, gratuita.
2. **Firma de apps de Play.** Acepta que Google gestione la clave de firma. La que hay aquí es
   la clave de subida; si un día se pierde, Google permite cambiarla.
3. **Ficha de Play Store.** Copia los textos de `ficha.md` y sube el icono, el gráfico y las
   capturas. Categoría: Fotografía.
4. **Contenido de la app.** Hay que rellenar varios formularios:
   - *Política de privacidad*: la dirección de GitHub Pages del paso anterior.
   - *Anuncios*: no contiene anuncios.
   - *Acceso a la app*: todas las funciones están disponibles sin credenciales.
   - *Clasificación de contenido*: cuestionario de utilidad; sin violencia ni contenido sensible.
   - *Audiencia objetivo*: 13 años o más.
   - *Seguridad de los datos*: la app no recoge ni comparte ningún dato.
   - *Permisos de fotos y vídeos*: declara que la función principal de la app es una galería.
     Es el uso para el que Google admite el acceso amplio a fotos y vídeos.
5. **Subir el AAB.** En Pruebas, crea una versión y sube `LumiGallery-X-play.aab`.
6. **Prueba cerrada.** Si tu cuenta de desarrollador es personal y se creó después de noviembre
   de 2023, Google exige una prueba cerrada con al menos 12 personas durante 14 días antes de
   publicar en producción. Con una cuenta anterior puedes ir directo.

## Cosas de esta app que conviene saber al publicar

- **Dos variantes.** `play` es la que va a la tienda. `completa` añade "carpetas ocultas del
  sistema", que necesita el permiso de acceso a todos los archivos. Google Play no admite ese
  permiso en una galería, así que esa variante es solo para instalarla a mano.
- **Sin permiso de internet.** Se puede comprobar en la ficha, en "Permisos". Es el argumento
  principal de privacidad.
- **Quitar el fondo** usa un componente que Google Play descarga en el teléfono la primera vez.
  Está explicado en la política de privacidad.
- **Licencia del modelo de búsqueda.** El modelo que entiende las frases es MobileCLIP, de Apple.
  Su licencia va incluida en la app (`assets/clip/LICENSE.txt`). Conviene leerla antes de
  publicar para confirmar que cubre la distribución en una app comercial.
- **Número de versión.** Cada subida necesita un `versionCode` mayor que el anterior. Se cambia
  en `app/build.gradle.kts`.
- **Tamaño.** El AAB pesa unos 130 MB por los modelos de reconocimiento. Cada teléfono descarga
  solo su parte, por debajo del límite de 200 MB de Google Play.

## Capturas

Guarda pantallazos de la app en `capturas/originales/` con estos nombres y ejecuta
`python playstore/make_captures.py`:

`fotos`, `buscar`, `albumes`, `visor`, `editor`, `privacidad`, `video`, `espacio`

Cada uno sale a 1080 × 1920 con su frase encima. Usa fotos que no sean personales: las capturas
son públicas.
