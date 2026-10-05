"""Genera el icono de 512 px y el gráfico de 1024x500 que pide Google Play.

Uso: python playstore/make_graphics.py   (necesita Pillow)
Dibuja el mismo icono "Horizonte" que lleva la app: un sol sobre una loma, en violeta.
"""
import os

from PIL import Image, ImageDraw, ImageFont

here = os.path.dirname(os.path.abspath(__file__))
fonts = os.path.join(here, "..", "app", "src", "main", "res", "font")
VIOLET = (91, 61, 245)
LILAC = (185, 168, 255)
WHITE = (255, 255, 255)


def bezier(p0, p1, p2, p3, steps=40):
    out = []
    for i in range(steps + 1):
        t = i / steps
        a = (1 - t) ** 3
        b = 3 * (1 - t) ** 2 * t
        c = 3 * (1 - t) * t ** 2
        d = t ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return out


def horizon(size, scale=1.0):
    """El dibujo del icono en un lienzo transparente de [size] px. Coordenadas del original: 108x108."""
    big = size * 4  # se dibuja grande y se reduce para que los bordes salgan suaves
    im = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    k = big / 108 * scale
    off = (big - 108 * k) / 2

    def pt(x, y):
        return (off + x * k, off + y * k)

    hill = bezier(pt(20, 78), pt(32, 60), pt(44, 59), pt(54, 70)) + bezier(pt(54, 70), pt(62, 79), pt(72, 62), pt(88, 58))
    hill += [pt(88, 90), pt(20, 90)]
    d.polygon(hill, fill=LILAC)
    cx, cy = pt(60, 41)
    r = 11 * k
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=WHITE)
    return im.resize((size, size), Image.LANCZOS)


def font(name, size, weight=None):
    f = ImageFont.truetype(os.path.join(fonts, name), size)
    if weight:
        try:
            f.set_variation_by_axes([weight])
        except Exception:
            pass
    return f


# Icono de la ficha: cuadrado completo, sin esquinas redondeadas (Google Play las recorta él).
icon = Image.new("RGBA", (512, 512), VIOLET + (255,))
icon.alpha_composite(horizon(512, scale=1.18))
icon.convert("RGB").save(os.path.join(here, "icono-512.png"))

# Gráfico de funciones: 1024x500, sin transparencia.
banner = Image.new("RGBA", (1024, 500), VIOLET + (255,))
banner.alpha_composite(horizon(420, scale=1.25), (40, 40))
draw = ImageDraw.Draw(banner)
draw.text((462, 158), "Lumi Gallery", font=font("schibsted_grotesk.ttf", 78, 900), fill=WHITE)
draw.text((466, 262), "Tus fotos, bonitas y a mano.", font=font("figtree.ttf", 38, 600), fill=(233, 228, 255))
draw.text((466, 316), "Sin anuncios. Sin cuenta. Sin internet.", font=font("figtree.ttf", 30, 500), fill=LILAC)
banner.convert("RGB").save(os.path.join(here, "grafico-1024x500.png"))
print("ok")
