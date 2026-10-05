"""Monta las capturas de la ficha de Google Play a partir de pantallazos de la app.

Uso:
  1. Guarda los pantallazos (PNG o JPG, en vertical) en playstore/capturas/originales/
     con estos nombres. Los que falten se saltan:
       fotos, buscar, albumes, visor, editor, privacidad, video, espacio
  2. python playstore/make_captures.py   (necesita Pillow)
  3. El resultado sale en playstore/capturas/ a 1080x1920, que es lo que acepta Google Play.

Cada imagen lleva una frase arriba y el pantallazo debajo, con las esquinas redondeadas.
"""
import os

from PIL import Image, ImageDraw, ImageFont

here = os.path.dirname(os.path.abspath(__file__))
src = os.path.join(here, "capturas", "originales")
out = os.path.join(here, "capturas")
fonts = os.path.join(here, "..", "app", "src", "main", "res", "font")
W, H = 1080, 1920
VIOLET = (91, 61, 245)
WHITE = (255, 255, 255)

# nombre del archivo -> (frase grande, frase pequeña)
SHOTS = [
    ("fotos", "Tus fotos, bonitas y a mano", "Pellizca para ir del año al día"),
    ("buscar", "Busca con tus palabras", "Todo se analiza en tu teléfono"),
    ("albumes", "Se ordena sola", "Viajes, cosas y tus carpetas"),
    ("visor", "Cada foto, a pantalla completa", "La app toma el color de la imagen"),
    ("editor", "Edita sin complicarte", "Recorta, ajusta, dibuja y quita el fondo"),
    ("privacidad", "Privada de verdad", "Candado con huella o PIN, y sin internet"),
    ("video", "Un reproductor completo", "Velocidad, ventana flotante y recorte"),
    ("espacio", "Libera espacio cuando quieras", "Duplicados, repetidas y vídeos grandes"),
]


def font(name, size, weight):
    f = ImageFont.truetype(os.path.join(fonts, name), size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def find(name):
    for ext in (".png", ".jpg", ".jpeg"):
        path = os.path.join(src, name + ext)
        if os.path.exists(path):
            return path
    return None


os.makedirs(src, exist_ok=True)
made = 0
for number, (name, title, subtitle) in enumerate(SHOTS, start=1):
    path = find(name)
    if not path:
        continue
    canvas = Image.new("RGB", (W, H), VIOLET)
    draw = ImageDraw.Draw(canvas)
    # La frase grande se encoge lo justo para no tocar los bordes.
    size = 68
    big = font("schibsted_grotesk.ttf", size, 900)
    while draw.textlength(title, font=big) > W - 120 and size > 40:
        size -= 2
        big = font("schibsted_grotesk.ttf", size, 900)
    small = font("figtree.ttf", 38, 500)
    draw.text((W / 2, 120), title, font=big, fill=WHITE, anchor="mm")
    draw.text((W / 2, 205), subtitle, font=small, fill=(225, 218, 255), anchor="mm")

    shot = Image.open(path).convert("RGB")
    # El pantallazo ocupa el resto, centrado, con margen a los lados y saliéndose por abajo.
    width = 860
    height = round(shot.height * width / shot.width)
    shot = shot.resize((width, height), Image.LANCZOS)
    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width, height + 80), radius=56, fill=255)
    frame = Image.new("RGB", (width + 24, height + 12), (20, 14, 60))
    frame_mask = Image.new("L", frame.size, 0)
    ImageDraw.Draw(frame_mask).rounded_rectangle((0, 0, frame.width, frame.height + 80), radius=66, fill=255)
    top = 290
    canvas.paste(frame, ((W - frame.width) // 2, top), frame_mask)
    canvas.paste(shot, ((W - width) // 2, top + 12), mask)
    canvas.save(os.path.join(out, f"{number:02d}-{name}.png"))
    made += 1

print(f"{made} capturas montadas en {out}" if made else f"No hay pantallazos en {src}")
