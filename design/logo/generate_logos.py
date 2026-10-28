"""
Logo textual de Semencraft III (versiones elegidas), monocromo blanco y negro.

  logo        SEMEN / --- III --- / CRAFT   (bloque cuadrado, el principal)
  horizontal  SEMENCRAFT con --- III --- debajo   (para espacios anchos)

Las letras salen de los contornos de Minecraft Ten (assets/font2.ttf), la misma
familia del logo original. SEMEN y CRAFT miden lo mismo de ancho: CRAFT se agranda
un poco, como en el original. Todo se aplana a un unico trazado con coordenadas
absolutas (sin transformaciones ni mascaras), asi cualquier programa lo abre igual.

Uso: python generate_logos.py
  - escribe los .svg en final/
  - la primera vez extrae los contornos con GlyphPaths.java (minecraft-ten-glyphs.json)
Para el PNG que usa el launcher: java RenderLogo.java final/semencraft-iii-logo-blanco.svg <salida.png> <ancho>
"""
import json
import os
import re
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "final")
FONT = os.path.join(HERE, "..", "..", "src", "main", "resources", "com", "semencraft",
                    "semencraftlauncherjavafx", "assets", "font2.ttf")
GLYPH_CACHE = os.path.join(HERE, "minecraft-ten-glyphs.json")
COLORS = {"blanco": "#ffffff", "negro": "#000000"}

CAP = 70.0             # altura de mayuscula de Minecraft Ten a tamano 100
WEIGHT = 0.02          # trazo extra (fraccion de la altura de letra)
TRACKING = 0.1         # espacio entre letras (fraccion de la altura de letra)
III_TRACKING = 0.32    # las I del numeral van un poco mas sueltas


def load_glyphs():
    if not os.path.exists(GLYPH_CACHE):
        out = subprocess.run(["java", os.path.join(HERE, "GlyphPaths.java"), FONT, "SEMNCRAFTI"],
                             check=True, capture_output=True, text=True).stdout
        with open(GLYPH_CACHE, "w", encoding="utf-8") as fh:
            fh.write(out)
    with open(GLYPH_CACHE, encoding="utf-8") as fh:
        glyphs = {g["ch"]: g for g in json.load(fh)}
    for g in glyphs.values():
        g["polys"] = parse_polys(g["d"])
    return glyphs


def parse_polys(d):
    """Los glifos de Minecraft Ten son solo lineas rectas: M ... L ... Z."""
    polys, current = [], []
    for cmd, args in re.findall(r"([MLZ])([^MLZ]*)", d):
        nums = [float(v) for v in args.split()]
        if cmd == "M":
            if current:
                polys.append(current)
            current = [(nums[0], nums[1])]
        elif cmd == "L":
            current.append((nums[0], nums[1]))
        else:
            polys.append(current)
            current = []
    if current:
        polys.append(current)
    return polys


G = load_glyphs()


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def word_width(text, height, tracking=TRACKING):
    units = sum(G[ch]["maxX"] - G[ch]["minX"] for ch in text) + (len(text) - 1) * tracking * CAP
    return units * height / CAP


def height_for_width(text, width, tracking=TRACKING):
    return width / word_width(text, 1.0, tracking)


def word_polys(text, x0, y0, height, tracking=TRACKING):
    """Poligonos absolutos de una palabra con su esquina superior izquierda en (x0, y0)."""
    k = height / CAP
    polys, x = [], 0.0
    for ch in text:
        g = G[ch]
        dx = x - g["minX"]
        for poly in g["polys"]:
            polys.append([(x0 + (px + dx) * k, y0 + (py + CAP) * k) for px, py in poly])
        x += (g["maxX"] - g["minX"]) + tracking * CAP
    return polys


def rect_poly(x, y, w, h):
    return [(x, y), (x + w, y), (x + w, y + h), (x, y + h)]


def rule_with_iii(x0, width, cy, letter_h):
    """Filete --- III --- del ancho dado, centrado verticalmente en cy."""
    row_h = 0.38 * letter_h
    iii_w = word_width("III", row_h, III_TRACKING)
    rule_h = 0.085 * letter_h
    space = 0.24 * letter_h
    cx = x0 + width / 2
    left_end = cx - iii_w / 2 - space
    right_start = cx + iii_w / 2 + space
    polys = [rect_poly(x0, cy - rule_h / 2, left_end - x0, rule_h)]
    polys += word_polys("III", cx - iii_w / 2, cy - row_h / 2, row_h, III_TRACKING)
    polys.append(rect_poly(right_start, cy - rule_h / 2, x0 + width - right_start, rule_h))
    return polys, row_h


def logo_block(h=84.0):
    """SEMEN / --- III --- / CRAFT con esquina superior izquierda en (0, 0)."""
    width = word_width("SEMEN", h)
    h_craft = height_for_width("CRAFT", width)
    gap = 0.2 * h
    rule, row_h = rule_with_iii(0, width, h + gap + 0.19 * h, h)
    y_craft = h + gap + row_h + gap
    polys = word_polys("SEMEN", 0, 0, h) + rule + word_polys("CRAFT", 0, y_craft, h_craft)
    return polys, width, y_craft + h_craft, WEIGHT * h


def logo_horizontal(h=70.0):
    """SEMENCRAFT con --- III --- debajo, esquina superior izquierda en (0, 0)."""
    width = word_width("SEMENCRAFT", h)
    gap = 0.24 * h
    rule, row_h = rule_with_iii(0, width, h + gap + 0.19 * h, h)
    return word_polys("SEMENCRAFT", 0, 0, h) + rule, width, h + gap + row_h, WEIGHT * h


def flat_svg(polys, width, height, stroke_w, color, title):
    margin = stroke_w
    d = "".join("M" + " L".join(f"{f(x + margin)} {f(y + margin)}" for x, y in poly) + "Z" for poly in polys)
    w, h = width + 2 * margin, height + 2 * margin
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {f(w)} {f(h)}" width="{f(w)}" height="{f(h)}">'
            f'<title>{title}</title>'
            f'<path fill="{color}" stroke="{color}" stroke-width="{f(stroke_w)}" stroke-linejoin="miter" d="{d}"/>'
            f'</svg>\n')


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    for key, maker, title in (("logo", logo_block, "Semencraft III"),
                              ("horizontal", logo_horizontal, "Semencraft III (horizontal)")):
        polys, width, height, stroke_w = maker()
        for color_name, hex_color in COLORS.items():
            name = f"semencraft-iii-{key}-{color_name}.svg"
            with open(os.path.join(OUT_DIR, name), "w", encoding="utf-8") as fh:
                fh.write(flat_svg(polys, width, height, stroke_w, hex_color, title))
            print("escrito final/" + name)
