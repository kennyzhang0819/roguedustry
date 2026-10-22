import os
import numpy as np
from PIL import Image, ImageDraw

SPRITES = os.path.join(os.path.dirname(__file__), "..", "sprites")
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


def check_angles(points, closed):
    pairs = zip(points, points[1:] + (points[:1] if closed else []))
    for (x0, y0), (x1, y1) in pairs:
        dx, dy = abs(x1 - x0), abs(y1 - y0)
        if dx and dy and dx != dy:
            raise ValueError(f"edge {(x0, y0)} -> {(x1, y1)} is not straight or 45 degrees")


def poly(points):
    check_angles(points, True)
    img = Image.new("1", (SIZE, SIZE), 0)
    ImageDraw.Draw(img).polygon(points, fill=1)
    return soften(np.array(img, dtype=bool))


def stroke(points, r=4, cut=3):
    check_angles(points, False)
    img = Image.new("1", (SIZE, SIZE), 0)
    ImageDraw.Draw(img).line(points, fill=1, width=1)
    return dilate(np.array(img, dtype=bool), r, cut)


def dot(x, y, r, cut=None):
    m = blank()
    m[y, x] = True
    return dilate(m, r, r if cut is None else cut)


def octagon(x0, y0, x1, y1, cut):
    return poly([(x0 + cut, y0), (x1 - cut, y0), (x1, y0 + cut), (x1, y1 - cut), (x1 - cut, y1), (x0 + cut, y1), (x0, y1 - cut), (x0, y0 + cut)])


def icon(folder, name, layers):
    img = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    for mask, color in layers:
        if color is None:
            img[mask] = (0, 0, 0, 0)
        else:
            img[mask] = (*color, 255)
    os.makedirs(os.path.join(SPRITES, folder), exist_ok=True)
    Image.fromarray(img, "RGBA").save(os.path.join(SPRITES, folder, name + ".png"))


def diamond(cx, cy, r):
    return poly([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)])


def speed_freaks():
    return [
        (stroke([(6, 50), (26, 30)], 5, 3) | stroke([(6, 30), (16, 20)], 4, 3), PEACH),
        (stroke([(22, 8), (46, 32), (22, 56)], 6, 4), RED),
        (stroke([(38, 8), (58, 28)], 5, 3), DRED),
    ]


def life_leech():
    return [
        (stroke([(10, 8), (10, 38), (24, 52), (40, 52)], 6, 4), DRED),
        (stroke([(54, 56), (54, 26), (40, 12), (24, 12)], 6, 4), RED),
        (dot(32, 32, 6, 4), PEACH),
    ]


def long_range():
    return [
        (stroke([(10, 14), (10, 50)], 6, 4), SLATE),
        (stroke([(10, 32), (50, 32)], 6, 4), LIGHT),
        (diamond(52, 32, 10), RED),
    ]


def just_die():
    return [
        (stroke([(8, 58), (20, 46), (44, 46), (56, 58)], 5, 3), SLATE),
        (stroke([(32, 46), (32, 26)], 6, 4) | stroke([(32, 26), (16, 10)], 5, 3) | stroke([(32, 26), (48, 10)], 5, 3), RED),
    ]


def self_destruction():
    return [
        (stroke([(6, 6), (20, 20)], 6, 4) | stroke([(58, 6), (44, 20)], 6, 4) | stroke([(6, 58), (20, 44)], 6, 4) | stroke([(58, 58), (44, 44)], 6, 4), SLATE),
        (octagon(20, 20, 44, 44, 8), RED),
        (octagon(27, 27, 37, 37, 3), YELLOW),
    ]


def mutually_assured():
    return [
        (octagon(6, 6, 58, 58, 16), PEACH),
        (stroke([(14, 14), (50, 50)], 5, 3) | stroke([(50, 14), (14, 50)], 5, 3), DARK),
        (dot(32, 32, 9, 6), RED),
    ]


def walking_infested():
    return [
        (stroke([(22, 28), (22, 40), (10, 52)], 4, 3) | stroke([(42, 28), (42, 40), (54, 52)], 4, 3) | stroke([(32, 30), (32, 54)], 4, 3), DRED),
        (octagon(14, 4, 50, 34, 10), RED),
        (dot(10, 54, 6, 4) | dot(32, 56, 6, 4) | dot(54, 54, 6, 4), PEACH),
    ]


def alien_incubation():
    return [
        (octagon(30, 22, 60, 62, 11), DPURPLE),
        (octagon(4, 2, 38, 54, 13), PURPLE),
        (stroke([(14, 18), (20, 24), (14, 30)], 3, 2), LAVENDER),
    ]


def barrier():
    return [
        (stroke([(6, 44), (6, 26), (24, 8), (40, 8), (58, 26), (58, 44)], 6, 4), NAVY),
        (stroke([(18, 50), (18, 32), (28, 22), (36, 22), (46, 32), (46, 50)], 5, 3), BLUE),
        (dot(32, 44, 6, 4), LIGHT),
    ]


def avenger():
    return [
        (stroke([(8, 58), (32, 34), (56, 58)], 5, 3), DRED),
        (stroke([(14, 36), (32, 18), (50, 36)], 5, 3), RED),
        (stroke([(22, 14), (32, 4), (42, 14)], 4, 3), PEACH),
    ]


def inspiration():
    return [
        (stroke([(32, 4), (32, 60)], 5, 3) | stroke([(4, 32), (60, 32)], 5, 3), PEACH),
        (stroke([(14, 14), (50, 50)], 4, 3) | stroke([(50, 14), (14, 50)], 4, 3), YELLOW),
        (octagon(22, 22, 42, 42, 7), LIGHT),
    ]


def evasive_maneuvers():
    return [
        (stroke([(6, 50), (22, 50), (34, 38), (34, 26), (46, 14), (54, 14)], 5, 3), DRED),
        (stroke([(46, 6), (54, 14), (46, 22)], 4, 3), RED),
        (dot(10, 50, 6, 4), PEACH),
    ]


def transmutation():
    return [
        (stroke([(6, 58), (20, 58), (20, 44), (34, 44), (34, 30), (48, 30)], 5, 3), DPURPLE),
        (stroke([(40, 10), (54, 10), (54, 24)], 5, 3) | stroke([(34, 30), (54, 10)], 5, 3), PURPLE),
    ]


def aggressive_deployment():
    return [
        (stroke([(12, 4), (12, 40)], 5, 3) | stroke([(52, 4), (52, 40)], 5, 3), DRED),
        (stroke([(32, 4), (32, 50)], 6, 4), RED),
        (stroke([(4, 58), (60, 58)], 4, 3), GRAY2),
    ]


def core_link():
    return [
        (stroke([(10, 54), (30, 34)], 5, 3), PURPLE),
        (diamond(12, 52, 10), PEACH),
        (octagon(24, 6, 58, 40, 11), DPURPLE),
        (octagon(34, 16, 48, 30, 5), LAVENDER),
    ]


def living_metal():
    return [
        (octagon(4, 4, 60, 60, 14), DPURPLE),
        (stroke([(32, 14), (32, 50)], 6, 4) | stroke([(14, 32), (50, 32)], 6, 4), LIGHT),
    ]


def grace_period():
    return [
        (poly([(8, 8), (56, 8), (32, 32)]) | poly([(32, 32), (56, 56), (8, 56)]) | stroke([(32, 24), (32, 40)], 4, 3), PURPLE),
        (poly([(32, 36), (50, 54), (14, 54)]), PEACH),
        (stroke([(6, 5), (58, 5)], 4, 3) | stroke([(6, 59), (58, 59)], 4, 3), DPURPLE),
    ]


def prefab():
    return [
        (octagon(4, 14, 52, 60, 12), DPURPLE),
        (octagon(14, 24, 42, 50, 6), PURPLE),
        (stroke([(54, 4), (38, 20), (50, 20), (32, 38)], 5, 3), YELLOW),
    ]


def bigger_core():
    return [
        (stroke([(6, 20), (6, 6), (20, 6)], 5, 3) | stroke([(44, 6), (58, 6), (58, 20)], 5, 3)
         | stroke([(58, 44), (58, 58), (44, 58)], 5, 3) | stroke([(20, 58), (6, 58), (6, 44)], 5, 3), LAVENDER),
        (stroke([(10, 10), (22, 22)], 4, 3) | stroke([(54, 10), (42, 22)], 4, 3)
         | stroke([(54, 54), (42, 42)], 4, 3) | stroke([(10, 54), (22, 42)], 4, 3), DPURPLE),
        (octagon(16, 16, 48, 48, 10), PURPLE),
        (octagon(26, 26, 38, 38, 4), LIGHT),
    ]


def war_factory():
    return [
        (stroke([(8, 34), (8, 8), (26, 26), (26, 8), (44, 26), (44, 8), (56, 20), (56, 34)], 5, 3), PURPLE),
        (octagon(4, 32, 60, 60, 8), DPURPLE),
        (stroke([(20, 54), (32, 42), (44, 54)], 5, 3), PEACH),
    ]


def recycle():
    return [
        (stroke([(18, 8), (42, 8), (52, 18), (52, 34)], 5, 3) | stroke([(42, 28), (52, 38), (62, 28)], 5, 3), PURPLE),
        (stroke([(46, 56), (22, 56), (12, 46), (12, 30)], 5, 3) | stroke([(2, 36), (12, 26), (22, 36)], 5, 3), DPURPLE),
        (dot(32, 32, 7, 5), PEACH),
    ]


def field_commander():
    return [
        (stroke([(18, 44), (18, 60)], 4, 3) | stroke([(46, 44), (46, 60)], 4, 3), PEACH),
        (poly([(32, 4), (60, 32), (60, 50), (32, 22), (4, 50), (4, 32)]), PURPLE),
        (poly([(32, 30), (46, 44), (46, 54), (32, 40), (18, 54), (18, 44)]), DPURPLE),
    ]


def ore_magnet():
    return [
        (stroke([(14, 4), (14, 36), (26, 48), (38, 48), (50, 36), (50, 4)], 7, 4), PURPLE),
        (stroke([(14, 4), (14, 12)], 7, 4) | stroke([(50, 4), (50, 12)], 7, 4), LIGHT),
        (diamond(32, 22, 9), PEACH),
        (diamond(32, 22, 4), YELLOW),
    ]


def scrap_rebirth():
    return [
        (diamond(16, 48, 12) | diamond(48, 48, 12), DPURPLE),
        (stroke([(32, 60), (32, 18)], 5, 3) | stroke([(16, 28), (32, 12), (48, 28)], 5, 3), PEACH),
        (stroke([(12, 6), (52, 6)], 4, 3), PURPLE),
    ]


def last_stand():
    return [
        (stroke([(20, 6), (44, 6), (58, 20), (58, 44), (44, 58), (20, 58), (6, 44), (6, 20), (20, 6)], 4, 3), LAVENDER),
        (stroke([(14, 14), (50, 50)], 4, 3) | stroke([(50, 14), (14, 50)], 4, 3), DPURPLE),
        (octagon(20, 20, 44, 44, 8), PURPLE),
        (octagon(28, 28, 36, 36, 3), LIGHT),
    ]


def absolution():
    return [
        (octagon(8, 8, 56, 56, 14), DRED),
        (octagon(18, 18, 46, 46, 8), RED),
        (stroke([(8, 56), (56, 8)], 6, 4), None),
        (stroke([(12, 52), (52, 12)], 4, 3), LAVENDER),
    ]


if __name__ == "__main__":
    for name, fn in [
        ("core-link", core_link), ("living-metal", living_metal), ("grace-period", grace_period), ("prefab", prefab),
        ("bigger-core", bigger_core), ("war-factory", war_factory), ("recycle", recycle), ("field-commander", field_commander),
        ("ore-magnet", ore_magnet), ("scrap-rebirth", scrap_rebirth), ("last-stand", last_stand), ("absolution", absolution),
    ]:
        icon("relics", name, fn())

    for name, fn in [
        ("speed-freaks", speed_freaks), ("life-leech", life_leech), ("long-range", long_range), ("just-die", just_die),
        ("self-destruction", self_destruction), ("mutually-assured-destruction", mutually_assured),
        ("walking-infested", walking_infested), ("alien-incubation", alien_incubation), ("barrier", barrier),
        ("avenger", avenger), ("inspiration", inspiration), ("evasive-maneuvers", evasive_maneuvers),
        ("transmutation", transmutation), ("aggressive-deployment", aggressive_deployment),
    ]:
        icon("mutators", name, fn())

    print("Icons written to", os.path.abspath(SPRITES))
