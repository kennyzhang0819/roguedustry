import os
import numpy as np
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "sprites", "mutators")
SIZE = 64


def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


LIGHT, LAVENDER, GRAY, GRAY2, SLATE = hexc("eff7ff"), hexc("d3d1ff"), hexc("5a5959"), hexc("72727a"), hexc("595a66")
RED, DRED, PEACH, NAVY, BLUE = hexc("f25555"), hexc("a23f4c"), hexc("ffdfbc"), hexc("35387e"), hexc("47558f")
PURPLE, DPURPLE, DARK, YELLOW = hexc("924e94"), hexc("513678"), hexc("373232"), hexc("ffd37f")


def kernel(r, cut):
    return [(dx, dy) for dx in range(-r, r + 1) for dy in range(-r, r + 1) if abs(dx) + abs(dy) <= 2 * r - cut]


def shift_or(mask, k):
    pad = max(max(abs(dx), abs(dy)) for dx, dy in k)
    p = np.pad(mask, pad)
    out = np.zeros_like(mask)
    for dx, dy in k:
        out |= p[pad + dy:pad + dy + SIZE, pad + dx:pad + dx + SIZE]
    return out


def dilate(mask, r, cut):
    return shift_or(mask, kernel(r, cut))


def erode(mask, r, cut):
    return ~dilate(~mask, r, cut)


def soften(mask, r=2, cut=1):
    opened = dilate(erode(mask, r, cut), r, cut)
    return erode(dilate(opened, r, cut), r, cut)


def blank():
    return np.zeros((SIZE, SIZE), dtype=bool)


def poly(points):
    img = Image.new("1", (SIZE, SIZE), 0)
    ImageDraw.Draw(img).polygon(points, fill=1)
    return soften(np.array(img, dtype=bool))


def stroke(points, r=4, cut=3):
    img = Image.new("1", (SIZE, SIZE), 0)
    ImageDraw.Draw(img).line(points, fill=1, width=1)
    return dilate(np.array(img, dtype=bool), r, cut)


def dot(x, y, r, cut=None):
    m = blank()
    m[y, x] = True
    return dilate(m, r, r if cut is None else cut)


def octagon(x0, y0, x1, y1, cut):
    return poly([(x0 + cut, y0), (x1 - cut, y0), (x1, y0 + cut), (x1, y1 - cut), (x1 - cut, y1), (x0 + cut, y1), (x0, y1 - cut), (x0, y0 + cut)])


def icon(name, layers):
    img = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    for mask, color in layers:
        if color is None:
            img[mask] = (0, 0, 0, 0)
        else:
            img[mask] = (*color, 255)
    Image.fromarray(img, "RGBA").save(os.path.join(OUT, name + ".png"))


def right_chevron(x, y, s):
    return [(x, y), (x + s, y + s), (x, y + 2 * s)]


def right_half():
    return poly([(32, 0), (64, 0), (64, 64), (32, 64)])


def speed_freaks():
    return [
        (stroke([(8, 32), (22, 32)], 6, 4), PEACH),
        (stroke(right_chevron(10, 10, 22), 6, 4), DRED),
        (stroke(right_chevron(32, 10, 22), 6, 4), RED),
    ]


def life_leech():
    drop = poly([(32, 2), (56, 26), (56, 44), (42, 62), (22, 62), (8, 44), (8, 26)])
    return [
        (drop, RED),
        (drop & right_half(), DRED),
        (stroke([(20, 30), (20, 44), (28, 52)], 4, 3), PEACH),
    ]


def long_range():
    ring = octagon(4, 4, 60, 60, 18) & ~octagon(16, 16, 48, 48, 11)
    ticks = stroke([(32, 5), (32, 20)], 4, 3) | stroke([(32, 44), (32, 59)], 4, 3) | stroke([(5, 32), (20, 32)], 4, 3) | stroke([(44, 32), (59, 32)], 4, 3)
    return [
        (ring, SLATE),
        (ticks, LIGHT),
        (dot(32, 32, 8, 5), RED),
    ]


def just_die():
    skull = octagon(6, 16, 58, 52, 14) | octagon(16, 40, 48, 62, 7)
    holes = octagon(15, 26, 29, 40, 5) | octagon(35, 26, 49, 40, 5) | stroke([(28, 54), (28, 62)], 1, 1) | stroke([(36, 54), (36, 62)], 1, 1)
    return [
        (skull & ~holes, LIGHT),
        (stroke([(16, 20), (32, 4), (48, 20)], 6, 4), RED),
    ]


def self_destruction():
    body = octagon(2, 16, 50, 62, 16)
    return [
        (body, SLATE),
        (body & poly([(2, 40), (24, 62), (2, 62)]), GRAY),
        (stroke([(14, 34), (20, 28)], 4, 3), LIGHT),
        (stroke([(32, 30), (46, 16)], 5, 3), DARK),
        (dot(51, 12, 10, 7), RED),
        (dot(51, 12, 4, 3), YELLOW),
    ]


def mutually_assured():
    disc = octagon(0, 0, 63, 63, 19)
    blades = poly([(32, 30), (12, 10), (52, 10)]) | poly([(26, 38), (8, 56), (26, 56)]) | poly([(38, 38), (56, 56), (38, 56)])
    return [
        (disc, PEACH),
        (blades, DARK),
        (dot(32, 32, 9, 6), RED),
    ]


def walking_infested():
    legs = stroke([(4, 16), (16, 28), (48, 28), (60, 16)], 5, 3) | stroke([(4, 60), (16, 48), (48, 48), (60, 60)], 5, 3)
    body = octagon(12, 12, 52, 60, 14)
    return [
        (legs, DRED),
        (body, RED),
        (body & right_half(), DRED),
        (octagon(18, 24, 30, 36, 4), PEACH),
        (octagon(34, 24, 46, 36, 4), PEACH),
    ]


def alien_incubation():
    back = octagon(34, 18, 62, 62, 11)
    egg = octagon(6, 2, 44, 62, 15)
    crack = stroke([(16, 24), (26, 34), (18, 42), (30, 54)], 3, 2)
    return [
        (back, DPURPLE),
        (egg, PURPLE),
        (crack & egg, LAVENDER),
    ]


def barrier():
    outer = poly([(4, 2), (60, 2), (60, 36), (32, 64), (4, 36)])
    inner = poly([(14, 12), (50, 12), (50, 32), (32, 50), (14, 32)])
    return [
        (outer, NAVY),
        (inner, BLUE),
        (inner & right_half(), LIGHT),
    ]


def avenger():
    head = octagon(4, 14, 60, 62, 18)
    return [
        (stroke([(16, 20), (32, 6), (48, 20)], 6, 4), DRED),
        (head, SLATE),
        (head & poly([(0, 40), (24, 64), (0, 64)]), GRAY),
        (stroke([(14, 28), (24, 38)], 4, 3) | stroke([(50, 28), (40, 38)], 4, 3), RED),
        (stroke([(22, 52), (42, 52)], 3, 2), DARK),
    ]


def inspiration():
    return [
        (octagon(10, 10, 54, 54, 5), PEACH),
        (poly([(32, 0), (64, 32), (32, 64), (0, 32)]), YELLOW),
        (octagon(20, 20, 44, 44, 8), LIGHT),
    ]


def evasive_maneuvers():
    return [
        (stroke([(6, 14), (26, 14)], 5, 3) | stroke([(6, 50), (26, 50)], 5, 3), DRED),
        (stroke([(6, 32), (30, 32)], 5, 3), PEACH),
        (stroke(right_chevron(26, 6, 26), 8, 5), RED),
    ]


def transmutation():
    arrow = stroke([(32, 56), (32, 16)], 8, 6) | stroke([(10, 28), (32, 6), (54, 28)], 7, 5)
    return [
        (octagon(10, 46, 54, 64, 7), DARK),
        (arrow, PURPLE),
        (arrow & right_half(), DPURPLE),
    ]


def aggressive_deployment():
    def down(y):
        return stroke([(10, y), (32, y + 22), (54, y)], 5, 3)
    return [
        (stroke([(6, 58), (58, 58)], 5, 3), GRAY2),
        (down(6), DRED),
        (down(24), RED),
    ]


for name, fn in [
    ("speed-freaks", speed_freaks), ("life-leech", life_leech), ("long-range", long_range), ("just-die", just_die),
    ("self-destruction", self_destruction), ("mutually-assured-destruction", mutually_assured),
    ("walking-infested", walking_infested), ("alien-incubation", alien_incubation), ("barrier", barrier),
    ("avenger", avenger), ("inspiration", inspiration), ("evasive-maneuvers", evasive_maneuvers),
    ("transmutation", transmutation), ("aggressive-deployment", aggressive_deployment),
]:
    icon(name, fn())

print("Icons written to", os.path.abspath(OUT))
