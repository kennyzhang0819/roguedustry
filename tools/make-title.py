import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "libs", "mindustry-src", "core", "assets-raw", "sprites")

OUTLINE = (68, 68, 68, 255)
WHITE = (255, 255, 255, 255)
GRAY = (154, 154, 154, 255)
YELLOW = (255, 211, 127, 255)
ORANGE = (212, 129, 107, 255)
UNIT_OUTLINE = (64, 64, 73, 255)
RED = (230, 84, 84, 255)

FACE_H, STROKE, DROP, EDGE, GAP = 80, 18, 14, 6, 12


def need(path):
    if not os.path.exists(path):
        sys.exit("Missing " + path + ". Run ./tools/fetch-source.sh first.")
    return path


def letter(ch):
    s, h = STROKE, FACE_H
    mid = (h - s) // 2
    if ch == "I":
        w = s
        return w, [[(0, 0), (w, 0), (w, h), (0, h)]], []
    if ch == "S":
        w, c = 62, 12
        return w, [
            [(c, 0), (w, 0), (w, s), (s, s), (s, mid), (w - c, mid), (w, mid + c), (w, h - c), (w - c, h), (0, h), (0, h - s), (w - s, h - s), (w - s, mid + s), (c, mid + s), (0, mid + s - c), (0, c)],
        ], []
    if ch == "U":
        w, c = 62, 14
        return w, [[(0, 0), (s, 0), (s, h - s), (w - s, h - s), (w - s, 0), (w, 0), (w, h - c), (w - c, h), (c, h), (0, h - c)]], []
    if ch == "O":
        w, c = 62, 14
        return w, [[(c, 0), (w - c, 0), (w, c), (w, h - c), (w - c, h), (c, h), (0, h - c), (0, c)]], [[(s, s), (w - s, s), (w - s, h - s), (s, h - s)]]
    if ch == "R":
        w, c = 62, 14
        leg = mid + s
        return w, [
            [(0, 0), (w - c, 0), (w, c), (w, leg - 4), (w - 10, leg + 6), (w, h), (w - s - 8, h), (s + 8, leg), (s, leg), (s, h), (0, h)],
        ], [[(s, s), (w - s, s), (w - s, mid), (s, mid)]]
    if ch == "V":
        w = 66
        return w, [[(0, 0), (s, 0), (s, 40), (w // 2, 40 + (w // 2 - s)), (w - s, 40), (w - s, 0), (w, 0), (w, 47), (w // 2, h), (0, 47)]], []
    raise ValueError(ch)


def word(text, face, side):
    widths = [letter(ch)[0] for ch in text]
    width = sum(widths) + GAP * (len(text) - 1) + 2 * EDGE
    height = FACE_H + DROP + 2 * EDGE
    mask = Image.new("L", (width, height), 0)
    draw = ImageDraw.Draw(mask)
    x = EDGE
    for ch in text:
        w, solid, holes = letter(ch)
        for poly in solid:
            draw.polygon([(x + px, EDGE + py) for px, py in poly], fill=255)
        for poly in holes:
            draw.polygon([(x + px, EDGE + py) for px, py in poly], fill=0)
        x += w + GAP
    f = np.array(mask) > 0
    shifted = np.zeros_like(f)
    for d in range(1, DROP + 1):
        shifted[d:] |= f[:-d]
    g = shifted & ~f
    body = f | g
    out = dilate(body, EDGE) & ~body
    img = np.zeros((height, width, 4), np.uint8)
    img[out] = OUTLINE
    img[g] = side
    img[f] = face
    return Image.fromarray(img, "RGBA")


def dilate(m, r):
    out = m.copy()
    h, w = m.shape
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dy * dy > r * r + r:
                continue
            ys, ye = max(0, dy), h + min(0, dy)
            xs, xe = max(0, dx), w + min(0, dx)
            out[ys:ye, xs:xe] |= m[ys - dy:ye - dy, xs - dx:xe - dx]
    return out


def title():
    logo = Image.open(need(os.path.join(SRC, "ui", "logo.png"))).convert("RGBA")
    sub = word("SURVIVORS", YELLOW, ORANGE)
    width = max(logo.width, sub.width)
    overlap = EDGE + 2
    out = Image.new("RGBA", (width, logo.height + sub.height - overlap), (0, 0, 0, 0))
    out.alpha_composite(sub, ((width - sub.width) // 2, logo.height - overlap))
    out.alpha_composite(logo, ((width - logo.width) // 2, 0))
    path = os.path.join(ROOT, "sprites", "ui", "title.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out.save(path)
    print("Wrote " + path)


def sprite(rel):
    return Image.open(need(os.path.join(SRC, rel))).convert("RGBA")


def outlined(img, r, color=UNIT_OUTLINE):
    pad = r + 1
    a = np.array(img)
    big = np.zeros((a.shape[0] + 2 * pad, a.shape[1] + 2 * pad, 4), np.uint8)
    big[pad:-pad, pad:-pad] = a
    m = big[:, :, 3] > 40
    ring = dilate(m, r) & ~m
    big[ring] = color
    return Image.fromarray(big, "RGBA")


def tint(img, color):
    a = np.array(img).astype(np.float32)
    for i in range(3):
        a[:, :, i] *= color[i] / 255.0
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def scaled(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def stamp(canvas, img, cx, cy, angle=0.0):
    if angle:
        img = img.rotate(angle, resample=Image.NEAREST, expand=True)
    canvas.alpha_composite(img, (int(cx - img.width / 2), int(cy - img.height / 2)))


def mech(name, weapon, wx, wy, under):
    body = sprite("units/" + name + ".png")
    size = body.width + 2 * wx
    c = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mid = size / 2
    leg = sprite("units/" + name + "-leg.png")
    stamp(c, leg, mid, mid)
    stamp(c, leg.transpose(Image.FLIP_LEFT_RIGHT), mid, mid)
    stamp(c, sprite("units/" + name + "-base.png"), mid, mid)
    gun = sprite("units/weapons/" + weapon + ".png")
    if under:
        stamp(c, gun, mid + wx, mid - wy)
        stamp(c, gun.transpose(Image.FLIP_LEFT_RIGHT), mid - wx, mid - wy)
    stamp(c, body, mid, mid)
    cell = os.path.join(SRC, "units", name + "-cell.png")
    if os.path.exists(cell):
        stamp(c, tint(sprite("units/" + name + "-cell.png"), YELLOW), mid, mid)
    if not under:
        stamp(c, gun, mid + wx, mid - wy)
        stamp(c, gun.transpose(Image.FLIP_LEFT_RIGHT), mid - wx, mid - wy)
    return c


def facing(img, x, y, tx, ty):
    return img.rotate(-math.degrees(math.atan2(tx - x, -(ty - y))), resample=Image.NEAREST, expand=True)


HEROES = {
    "reign": lambda: mech("reign", "reign-weapon", 86, 4, True),
    "scepter": lambda: mech("scepter", "scepter-weapon", 64, 4, True),
    "nova": lambda: mech("nova", "heal-weapon", 18, 0, True),
    "meltdown": lambda: sprite("blocks/turrets/meltdown.png"),
    "duo": lambda: sprite("blocks/turrets/duo/duo-preview.png"),
    "flare": lambda: sprite("units/flare.png"),
}


def turret_full(rel, base):
    b = sprite("blocks/turrets/bases/" + base + ".png")
    top = sprite("blocks/turrets/" + rel)
    c = Image.new("RGBA", (max(b.width, top.width), max(b.height, top.height)), (0, 0, 0, 0))
    stamp(c, b, c.width / 2, c.height / 2)
    stamp(c, top, c.width / 2, c.height / 2)
    return c


def font(size):
    path = os.path.join(ROOT, "libs", "fonts", "Cormorant.ttf")
    if not os.path.exists(path):
        sys.exit("Missing " + path + ". Download Cormorant from https://github.com/google/fonts/raw/main/ofl/cormorant/Cormorant%5Bwght%5D.ttf")
    f = ImageFont.truetype(path, size)
    f.set_variation_by_name("Bold")
    return f


def blur(img, r):
    return img.filter(ImageFilter.GaussianBlur(r))


def background(size, rng):
    y, x = np.mgrid[0:size, 0:size].astype(np.float32) / size
    d = np.sqrt((x - 0.5) ** 2 + (y - 0.42) ** 2)
    base = np.clip(1.0 - d * 1.3, 0, 1)[..., None]
    img = (np.array([42, 6, 12]) + base * np.array([96, 18, 30])).astype(np.float32)
    grime = blur(Image.fromarray((rng.random((size // 8, size // 8)) * 255).astype(np.uint8)).resize((size, size), Image.BICUBIC), size / 60)
    img *= (0.82 + 0.3 * np.array(grime, np.float32)[..., None] / 255.0)

    moon = Image.new("L", (size, size), 0)
    mx, my, mr = size * 0.5, size * 0.47, size * 0.4
    ImageDraw.Draw(moon).ellipse((mx - mr, my - mr, mx + mr, my + mr), fill=255)
    glow = np.array(blur(moon, size / 18), np.float32)[..., None] / 255.0
    edge = np.array(blur(moon, 1.5), np.float32)[..., None] / 255.0
    t = np.clip((y - (my - mr) / size) / (2 * mr / size), 0, 1)[..., None]
    moon_col = np.array([190, 70, 150]) * (1 - t) + np.array([150, 28, 72]) * t
    moon_col = moon_col * (0.85 + 0.2 * np.array(grime, np.float32)[..., None] / 255.0)
    img = img * (1 - edge) + moon_col * edge
    img += glow * (1 - edge) * np.array([70, 10, 40])

    fade = np.clip((y - 0.62) / 0.38, 0, 1)[..., None] ** 1.4
    img = img * (1 - 0.75 * fade)
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).convert("RGBA")

    d = ImageDraw.Draw(out)
    for bx, by, k in ((0.86, 0.17, 1.0), (0.79, 0.36, 0.7), (0.93, 0.3, 0.55), (0.12, 0.24, 0.6)):
        bat(d, bx * size, by * size, k * size / 22)
    for _ in range(140):
        sx, sy = rng.random() * size, rng.random() * size
        a = int(40 + rng.random() * 110)
        r = 0.6 + rng.random() * 1.3
        d.ellipse((sx - r, sy - r, sx + r, sy + r), fill=(235, 200, 210, a))
    return out


def bat(d, x, y, k):
    pts = [(-1.0, -0.15), (-0.7, -0.35), (-0.45, -0.1), (-0.2, -0.25), (0, -0.05), (0.2, -0.25), (0.45, -0.1), (0.7, -0.35), (1.0, -0.15), (0.6, 0.05), (0.3, 0.0), (0, 0.2), (-0.3, 0.0), (-0.6, 0.05)]
    d.polygon([(x + px * k, y + py * k) for px, py in pts], fill=(70, 12, 24, 230))


def gold_text(size, text, px, width, tall=1.18, track=-0.045):
    f = font(px)
    advance = [f.getlength(ch) + px * track for ch in text]
    w = int(sum(advance) + px * 0.2)
    box = f.getbbox(text)
    h = box[3] - box[1]
    pad = px // 4
    mask = Image.new("L", (w + 2 * pad, h + 2 * pad), 0)
    d = ImageDraw.Draw(mask)
    x = pad
    for ch, adv in zip(text, advance):
        d.text((x, pad - box[1]), ch, font=f, fill=255)
        x += adv
    mask = mask.crop(mask.getbbox())
    mask = mask.resize((int(width), int(mask.height * tall * width / mask.width)), Image.LANCZOS)
    full = Image.new("L", (mask.width + 2 * pad, mask.height + 2 * pad), 0)
    full.paste(mask, (pad, pad))
    mask = full.filter(ImageFilter.MaxFilter(3))
    m = np.array(mask, np.float32) / 255.0
    hh, ww = m.shape
    t = np.linspace(0, 1, hh)[:, None]
    stops = np.array([[255, 238, 130], [255, 208, 36], [242, 164, 12], [255, 216, 64], [222, 142, 12]], np.float32)
    pos = np.array([0.0, 0.3, 0.55, 0.72, 1.0])
    col = np.stack([np.interp(t[:, 0], pos, stops[:, i]) for i in range(3)], -1)[:, None, :].repeat(ww, 1)
    grain = np.random.default_rng(3).random((hh, ww))[..., None]
    col *= 0.9 + 0.16 * grain

    soft = np.array(blur(mask, max(1.5, px / 70)), np.float32) / 255.0
    gy, gx = np.gradient(soft)
    light = (gx * 1.0 + gy * 0.7) * px / 4
    rim = np.clip(-light, 0, 1)[..., None]
    shine = np.clip(light, 0, 1)[..., None]
    rim = np.clip(rim * 1.8 - 0.55, 0, 1) * m[..., None]
    col = col * (1 - rim) + np.array([214, 44, 20]) * rim
    col = col * (1 - shine * 0.4) + np.array([255, 246, 190]) * shine * 0.4

    face = np.dstack([np.clip(col, 0, 255), m * 255]).astype(np.uint8)
    layer = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    layer.paste(Image.new("RGBA", mask.size, (90, 14, 6, 255)), (0, 0), mask.filter(ImageFilter.MaxFilter(3)))
    layer.alpha_composite(Image.fromarray(face, "RGBA"))
    return layer


def hero_layer(size, name):
    art = HEROES[name]()
    art = art.crop(art.getbbox())
    target = size * {"reign": 0.8, "scepter": 0.76, "nova": 0.56, "meltdown": 0.6, "duo": 0.5, "flare": 0.56}[name]
    f = target / max(art.width, art.height)
    k = int(math.ceil(f))
    art = outlined(scaled(art, k), k * 2)
    f = target / max(art.width, art.height)
    return art.resize((int(art.width * f), int(art.height * f)), Image.LANCZOS)


def icon_for(name, size=512, seed=7):
    rng = np.random.default_rng(seed)
    c = background(size, rng)
    art = hero_layer(size, name)
    cx, cy = size / 2, size * 0.7
    pad = size // 10
    alpha = Image.new("L", (art.width + 2 * pad, art.height + 2 * pad), 0)
    alpha.paste(art.split()[3], (pad, pad))
    glow = Image.new("RGBA", alpha.size, (255, 70, 40, 0))
    glow.putalpha(alpha.filter(ImageFilter.MaxFilter(15)))
    glow = blur(glow, size / 40)
    stamp(c, glow, cx, cy)
    stamp(c, glow, cx, cy)
    stamp(c, art, cx, cy)

    for text, y, px, w in (("MINDUSTRY", 0.12, 110, 0.86), ("SURVIVORS", 0.3, 120, 0.94)):
        t = gold_text(size, text, px, size * w)
        shadow = Image.new("RGBA", t.size, (16, 0, 4, 0))
        shadow.putalpha(blur(t.split()[3], 2.5).point(lambda v: min(235, int(v * 1.2))))
        stamp(c, shadow, size / 2 + 5, size * y + 7)
        hal = Image.new("RGBA", t.size, (255, 120, 40, 0))
        hal.putalpha(blur(t.split()[3], 10).point(lambda v: int(v * 0.5)))
        stamp(c, hal, size / 2, size * y)
        stamp(c, t, size / 2, size * y)
    return c


def icon(name="duo"):
    out = icon_for(name)
    path = os.path.join(ROOT, "icon.png")
    out.save(path)
    print("Wrote " + path)


def compare(folder):
    names = list(HEROES)
    sheet = Image.new("RGBA", (512 * len(names), 512 + 140), (20, 20, 20, 255))
    for i, n in enumerate(names):
        im = icon_for(n)
        sheet.alpha_composite(im, (512 * i, 0))
        for j, s in enumerate((64, 96, 128)):
            sheet.alpha_composite(im.resize((s, s), Image.LANCZOS), (512 * i + 10 + j * 140, 512 + 6))
    path = os.path.join(folder, "thumbnails.png")
    sheet.save(path)
    print("Wrote " + path)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "compare":
        compare(sys.argv[2])
    else:
        title()
        icon(sys.argv[1] if len(sys.argv) > 1 else "duo")
