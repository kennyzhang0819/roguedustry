import os
import numpy as np
from PIL import Image, ImageDraw

SPRITES = os.path.join(os.path.dirname(__file__), "..", "sprites")
SIZE = 64


def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


RED, MUTED_RED, BRICK, DARK = hexc("f25555"), hexc("bb5359"), hexc("954f4c"), hexc("373232")
GRAY, GRAY_L, GRAY_P = hexc("5a5959"), hexc("7a7a7f"), hexc("51515f")
SLATE, SLATE_D, SLATE_L = hexc("7274a0"), hexc("4a4665"), hexc("a0a1b8")
PEACH, LIGHT, OCHRE, COPPER = hexc("ffdfbc"), hexc("eff7ff"), hexc("ca9a5a"), hexc("a46f48")
PURPLE, DPURPLE, VIOLET, PLUM = hexc("924e94"), hexc("513678"), hexc("a27ce5"), hexc("4a3972")

#curses are drawn with the reds and then shown in purple, the curse color
CURSE_TONES = {RED: VIOLET, MUTED_RED: PURPLE, BRICK: DPURPLE}

X = np.arange(SIZE)[None, :]
Y = np.arange(SIZE)[:, None]


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


def peak(x, top, half, bottom):
    return poly([(x - half, bottom), (x - half, top + half), (x, top), (x + half, top + half), (x + half, bottom)])


def pod(x, top, half, bottom):
    return poly([(x - half, top), (x + half, top), (x + half, bottom - half), (x, bottom), (x - half, bottom - half)])


def speed_freaks():
    arrow = poly([(20, 6), (34, 6), (60, 32), (34, 58), (20, 58), (40, 38), (40, 26)])
    return [
        (poly([(0, 12), (24, 12), (30, 18), (24, 24), (0, 24)]) | poly([(6, 28), (30, 28), (34, 32), (30, 36), (6, 36)])
         | poly([(0, 40), (22, 40), (28, 46), (22, 52), (0, 52)]), GRAY),
        (arrow & (Y < 32), RED),
        (arrow & (Y >= 32), MUTED_RED),
        (poly([(36, 20), (48, 32), (36, 44), (36, 36), (40, 32), (36, 28)]), PEACH),
    ]


def life_leech():
    drop = poly([(32, 6), (52, 26), (52, 46), (40, 58), (24, 58), (12, 46), (12, 26)])
    return [
        (drop & (X < 32), RED),
        (drop & (X >= 32), MUTED_RED),
        (diamond(22, 44, 5), PEACH),
        (poly([(0, 2), (12, 2), (24, 14), (24, 30), (18, 36), (18, 20)]), GRAY_L),
        (poly([(64, 2), (52, 2), (40, 14), (40, 30), (46, 36), (46, 20)]), GRAY),
    ]


def long_range():
    barrel = poly([(12, 40), (46, 6), (56, 16), (22, 50)])
    return [
        (barrel & (X + Y < 56), GRAY_L),
        (barrel & (X + Y >= 56), GRAY),
        (poly([(0, 34), (10, 34), (30, 54), (30, 64), (0, 64)]), DARK),
        (diamond(18, 46, 13), GRAY),
        (diamond(52, 10, 11) | diamond(60, 4, 5), RED),
        (diamond(52, 10, 4), PEACH),
    ]


def just_die():
    stone = poly([(6, 64), (6, 24), (22, 8), (38, 24), (38, 64)])
    return [
        (poly([(28, 60), (28, 36), (46, 18), (46, 4), (58, 16), (58, 40), (40, 58), (38, 60)]), RED),
        (stone & (X < 22), GRAY_L),
        (stone & (X >= 22), GRAY),
        (poly([(19, 22), (25, 22), (25, 28), (31, 28), (31, 34), (25, 34), (25, 46), (19, 46), (19, 34), (13, 34), (13, 28), (19, 28)]), DARK),
    ]


def self_destruction():
    return [
        (diamond(32, 34, 24) | diamond(46, 48, 13) | diamond(22, 18, 12), RED),
        (diamond(32, 34, 9), DARK),
        (poly([(2, 10), (10, 2), (24, 2), (24, 8), (10, 22), (2, 22)]), GRAY),
        (poly([(50, 24), (58, 16), (64, 16), (64, 42), (58, 42), (50, 34)]), GRAY),
        (poly([(2, 50), (14, 38), (22, 46), (22, 56), (14, 64), (2, 64)]), GRAY),
    ]


def mutually_assured():
    cap = poly([(4, 26), (4, 14), (16, 2), (46, 2), (60, 16), (60, 26), (54, 32), (10, 32)])
    return [
        (poly([(0, 64), (0, 54), (8, 46), (20, 46), (20, 64)]) | poly([(44, 64), (44, 50), (50, 44), (64, 44), (64, 64)]), GRAY),
        (poly([(22, 32), (40, 32), (40, 48), (52, 60), (52, 64), (12, 64), (12, 60), (22, 50)]), RED),
        (cap & (Y < 22), PEACH),
        (cap & (Y >= 22), RED),
        (poly([(28, 34), (34, 34), (34, 56), (28, 56)]), MUTED_RED),
    ]


def alien_incubation():
    egg = poly([(10, 58), (10, 26), (30, 6), (50, 26), (50, 58)])
    return [
        (poly([(0, 64), (0, 56), (6, 50), (58, 50), (64, 56), (64, 64)]), GRAY),
        (egg & (X < 30), PEACH),
        (egg & (X >= 30), LIGHT),
        (poly([(10, 34), (20, 24), (30, 34), (40, 24), (50, 34), (50, 42), (40, 32), (30, 42), (20, 32), (10, 42)]), RED),
        (poly([(40, 16), (52, 4), (60, 4), (64, 8), (64, 16), (56, 16), (48, 24)]), RED),
    ]


def barrier():
    shield = poly([(8, 14), (16, 6), (42, 6), (50, 14), (50, 38), (29, 59), (8, 38)])
    return [
        (poly([(34, 38), (44, 28), (49, 33), (56, 26), (64, 34), (64, 64), (34, 64)]), RED),
        (poly([(18, 20), (26, 12), (52, 12), (60, 20), (60, 44), (39, 65), (18, 44)]), SLATE_D),
        (shield & (X < 29), SLATE),
        (shield & (X >= 29), SLATE_L),
        (poly([(22, 20), (29, 13), (36, 20), (36, 34), (29, 41), (22, 34)]), RED),
    ]


def avenger():
    big = peak(46, 2, 14, 64)
    return [
        (poly([(0, 64), (0, 48), (8, 40), (22, 40), (22, 64)]), GRAY),
        (peak(22, 24, 10, 64), MUTED_RED),
        (big & (X < 46), RED),
        (big & (X >= 46), BRICK),
        (poly([(40, 30), (46, 24), (52, 30), (46, 36)]), PEACH),
    ]


def inspiration():
    crown = poly([(4, 58), (4, 18), (18, 32), (26, 24), (26, 14), (32, 8), (38, 14), (38, 24), (46, 32), (60, 18), (60, 58)])
    return [
        (crown & (X < 32), RED),
        (crown & (X >= 32), MUTED_RED),
        (poly([(4, 44), (60, 44), (60, 58), (4, 58)]), PEACH),
        (diamond(18, 51, 4) | diamond(46, 51, 4), DARK),
        (diamond(32, 51, 6), RED),
    ]


def evasive_maneuvers():
    return [
        (poly([(0, 52), (12, 40), (20, 48), (8, 60), (0, 60)]), GRAY),
        (poly([(10, 40), (26, 24), (36, 34), (20, 50)]), MUTED_RED),
        (diamond(44, 20, 18), RED),
        (poly([(44, 8), (56, 20), (44, 32), (44, 24), (48, 20), (44, 16)]), PEACH),
    ]


def transmutation():
    big = poly([(28, 30), (28, 16), (42, 2), (56, 16), (64, 16), (64, 44), (46, 62), (28, 44)])
    return [
        (diamond(16, 48, 14), GRAY),
        (diamond(10, 54, 6), GRAY_L),
        (big & (X < 44), RED),
        (big & (X >= 44), MUTED_RED),
        (poly([(36, 30), (44, 22), (52, 30), (52, 36), (44, 28), (36, 36)]), PEACH),
    ]


def aggressive_deployment():
    return [
        (poly([(0, 64), (0, 50), (10, 50), (18, 58), (46, 58), (54, 50), (64, 50), (64, 64)]), GRAY),
        (pod(10, 0, 8, 34), MUTED_RED),
        (pod(32, 0, 11, 50) & (X < 32), RED),
        (pod(32, 0, 11, 50) & (X >= 32), MUTED_RED),
        (pod(54, 10, 8, 40), RED),
        (poly([(28, 6), (36, 6), (36, 16), (28, 16)]), PEACH),
    ]


def hungry_mechs():
    body = poly([(2, 22), (18, 6), (42, 6), (58, 22), (44, 36), (58, 50), (44, 64), (18, 64), (2, 48)])
    return [
        (body & (X < 30), RED),
        (body & (X >= 30), MUTED_RED),
        (diamond(58, 36, 9), GRAY_L),
        (diamond(62, 36, 4), GRAY),
        (diamond(24, 24, 5), PEACH),
    ]


def mech_virus():
    turret = poly([(0, 64), (0, 40), (8, 32), (30, 32), (38, 40), (38, 64)])
    blob = poly([(26, 36), (26, 12), (38, 0), (52, 0), (64, 12), (64, 36), (50, 50), (40, 50)])
    return [
        (turret & (X < 19), GRAY_L),
        (turret & (X >= 19), GRAY),
        (poly([(12, 34), (30, 16), (38, 24), (20, 42)]), DARK),
        (blob & (X < 45), RED),
        (blob & (X >= 45), MUTED_RED),
        (diamond(30, 46, 7) | diamond(56, 56, 6), RED),
        (diamond(48, 22, 5), PEACH),
    ]


def overshields():
    dome = poly([(0, 48), (0, 26), (24, 2), (40, 2), (64, 26), (64, 48), (54, 48), (54, 30), (36, 12), (28, 12), (10, 30), (10, 48)])
    unit = peak(32, 22, 13, 64)
    return [
        (poly([(6, 56), (6, 34), (26, 14), (38, 14), (58, 34), (58, 56), (50, 56), (50, 38), (34, 22), (30, 22), (14, 38), (14, 56)]), SLATE_D),
        (dome & (X < 32), SLATE_L),
        (dome & (X >= 32), SLATE),
        (unit & (X < 32), RED),
        (unit & (X >= 32), MUTED_RED),
        (diamond(32, 44, 5), PEACH),
    ]


def armored_swarms():
    left, right, mid = peak(11, 24, 10, 64), peak(53, 30, 10, 64), peak(32, 6, 14, 64)
    return [
        (left & (Y >= 40), BRICK),
        (left & (Y < 40), GRAY),
        (right & (Y >= 46), BRICK),
        (right & (Y < 46), GRAY),
        (mid & (Y >= 28) & (X < 32), RED),
        (mid & (Y >= 28) & (X >= 32), MUTED_RED),
        (mid & (Y < 28) & (X < 32), GRAY_L),
        (mid & (Y < 28) & (X >= 32), GRAY),
        (poly([(18, 26), (46, 26), (46, 30), (18, 30)]), DARK),
        (diamond(32, 44, 4), PEACH),
    ]


def leadership():
    leader = peak(44, 0, 18, 64)
    return [
        (poly([(0, 40), (16, 24), (26, 24), (26, 34), (12, 48), (0, 48)]), SLATE_L),
        (pod(10, 46, 8, 64), GRAY_L),
        (pod(26, 38, 8, 64), GRAY),
        (leader & (X < 44), RED),
        (leader & (X >= 44), BRICK),
        (poly([(36, 20), (44, 12), (52, 20), (52, 26), (44, 18), (36, 26)]), PEACH),
    ]


def shield_towers():
    ring = poly([(0, 40), (0, 24), (22, 2), (42, 2), (64, 24), (64, 40), (54, 40), (54, 28), (38, 12), (26, 12), (10, 28), (10, 40)])
    tower = poly([(16, 64), (16, 36), (24, 28), (40, 28), (48, 36), (48, 64)])
    return [
        (ring & (X < 32), RED),
        (ring & (X >= 32), MUTED_RED),
        (tower & (X < 32), GRAY_L),
        (tower & (X >= 32), GRAY),
        (poly([(24, 44), (32, 36), (40, 44), (40, 52), (32, 60), (24, 52)]), RED),
        (diamond(32, 48, 3), PEACH),
    ]


def ambush():
    left = poly([(0, 6), (12, 6), (30, 24), (30, 30), (12, 48), (0, 48)])
    right = poly([(56, 20), (64, 20), (64, 62), (54, 62), (36, 44), (36, 40), (54, 22)])
    return [
        (poly([(0, 64), (0, 54), (10, 54), (20, 64)]), GRAY),
        (left & (Y < 27), RED),
        (left & (Y >= 27), MUTED_RED),
        (right & (X < 52), MUTED_RED),
        (right & (X >= 52), BRICK),
        (diamond(33, 36, 4), PEACH),
    ]


def living_metal():
    block = poly([(2, 10), (8, 4), (30, 4), (30, 26), (60, 26), (60, 58), (54, 64), (2, 64)])
    return [
        (block & (X < 30), GRAY_L),
        (block & (X >= 30), GRAY),
        (poly([(30, 4), (50, 4), (64, 18), (64, 26), (52, 26), (40, 38), (30, 28)]), PURPLE),
        (poly([(30, 28), (40, 38), (34, 44), (24, 34)]), VIOLET),
        (poly([(12, 40), (24, 40), (24, 52), (12, 52)]), DARK),
    ]


def grace_period():
    top = poly([(8, 8), (56, 8), (56, 12), (36, 32), (28, 32), (8, 12)])
    bottom = poly([(28, 32), (36, 32), (56, 52), (56, 56), (8, 56), (8, 52)])
    return [
        (top & (X < 32), VIOLET),
        (top & (X >= 32), PURPLE),
        (bottom & (X < 32), VIOLET),
        (bottom & (X >= 32), PURPLE),
        (poly([(14, 56), (26, 44), (34, 44), (46, 56)]) | poly([(22, 12), (42, 12), (34, 20), (30, 20)]), PEACH),
        (poly([(0, 0), (52, 0), (60, 8), (0, 8)]) | poly([(4, 56), (64, 56), (64, 64), (12, 64)]), DPURPLE),
    ]


def prefab():
    block = poly([(2, 30), (8, 24), (40, 24), (46, 30), (46, 64), (2, 64)])
    return [
        (block & (X < 26), PURPLE),
        (block & (X >= 26), DPURPLE),
        (poly([(12, 36), (36, 36), (36, 54), (12, 54)]), VIOLET),
        (poly([(42, 0), (58, 0), (48, 10), (60, 10), (34, 36), (34, 28), (40, 22), (30, 22), (30, 12)]), PEACH),
    ]


def war_factory():
    hall = poly([(0, 64), (0, 22), (14, 8), (14, 22), (28, 8), (28, 22), (42, 8), (42, 22), (56, 22), (56, 64)])
    return [
        (hall & (X < 28), PURPLE),
        (hall & (X >= 28), DPURPLE),
        (poly([(16, 40), (36, 40), (36, 64), (16, 64)]), PLUM),
        (poly([(40, 36), (52, 48), (64, 36), (64, 48), (52, 60), (40, 48)]), PEACH),
    ]


def recycle():
    return [
        (diamond(12, 54, 10) | diamond(28, 60, 7), GRAY),
        (poly([(8, 44), (8, 20), (24, 4), (40, 4), (56, 20), (56, 30), (46, 30), (46, 24), (36, 14), (28, 14), (18, 24), (18, 44)]), PURPLE),
        (poly([(40, 30), (62, 30), (51, 41)]), DPURPLE),
        (diamond(50, 52, 11), VIOLET),
        (diamond(50, 52, 4), PEACH),
    ]


def field_commander():
    ship = poly([(32, 0), (46, 14), (46, 30), (60, 44), (60, 58), (52, 58), (46, 52), (40, 58), (24, 58), (18, 52), (12, 58), (4, 58), (4, 44), (18, 30), (18, 14)])
    return [
        (ship & (X < 32), PURPLE),
        (ship & (X >= 32), DPURPLE),
        (diamond(32, 22, 7), PEACH),
        (poly([(24, 40), (32, 32), (40, 40), (40, 48), (32, 40), (24, 48)]), VIOLET),
    ]


def ore_magnet():
    u = poly([(4, 2), (20, 2), (20, 28), (28, 36), (36, 36), (44, 28), (44, 2), (58, 2), (58, 36), (42, 52), (22, 52), (4, 34)])
    return [
        (u & (X < 32), PURPLE),
        (u & (X >= 32), DPURPLE),
        (u & (Y < 12), LIGHT),
        (poly([(4, 64), (4, 54), (14, 44), (24, 54), (24, 64)]) | poly([(40, 64), (40, 52), (51, 41), (62, 52), (62, 64)]), COPPER),
        (poly([(22, 64), (22, 40), (31, 31), (40, 40), (40, 64)]), OCHRE),
    ]


def scrap_rebirth():
    return [
        (poly([(0, 64), (0, 36), (6, 30), (10, 30), (26, 46), (26, 64)]), GRAY_L),
        (poly([(34, 64), (34, 46), (50, 30), (58, 30), (64, 36), (64, 64)]), GRAY),
        (diamond(30, 24, 11), PURPLE),
        (diamond(44, 10, 8), VIOLET),
        (diamond(18, 10, 6), PEACH),
    ]


def last_stand():
    core = poly([(14, 40), (20, 34), (44, 34), (50, 40), (50, 64), (14, 64)])
    return [
        (poly([(20, 32), (2, 14), (2, 2), (14, 2), (32, 20)]), VIOLET),
        (poly([(40, 26), (54, 12), (62, 12), (62, 20), (48, 34)]), VIOLET),
        (peak(32, 0, 6, 36), PEACH),
        (core & (X < 32), PURPLE),
        (core & (X >= 32), DPURPLE),
        (poly([(24, 46), (40, 46), (40, 56), (24, 56)]), PLUM),
    ]


def absolution():
    return [
        (diamond(26, 26, 22) & (X + Y < 50), RED),
        (diamond(36, 36, 22) & (X + Y > 62), MUTED_RED),
        (poly([(0, 52), (52, 0), (62, 0), (0, 62)]), VIOLET),
        (poly([(4, 54), (54, 4), (58, 4), (4, 58)]), LIGHT),
    ]


def magnet():
    u = poly([(6, 6), (22, 6), (22, 34), (28, 40), (36, 40), (42, 34), (42, 6), (58, 6), (58, 42), (42, 58), (22, 58), (6, 42)])
    return [
        (u & (X < 32), RED),
        (u & (X >= 32), MUTED_RED),
        (u & (Y < 16) & (X < 32), LIGHT),
        (u & (Y < 16) & (X >= 32), SLATE_L),
        (diamond(32, 14, 5), PEACH),
    ]


if __name__ == "__main__":
    for name, fn in [
        ("living-metal", living_metal), ("grace-period", grace_period), ("prefab", prefab),
        ("war-factory", war_factory), ("recycle", recycle), ("field-commander", field_commander),
        ("ore-magnet", ore_magnet), ("scrap-rebirth", scrap_rebirth), ("last-stand", last_stand), ("absolution", absolution),
    ]:
        icon("relics", name, fn())

    for name, fn in [
        ("speed-freaks", speed_freaks), ("life-leech", life_leech), ("long-range", long_range), ("just-die", just_die),
        ("self-destruction", self_destruction), ("mutually-assured-destruction", mutually_assured),
        ("alien-incubation", alien_incubation), ("barrier", barrier),
        ("avenger", avenger), ("inspiration", inspiration), ("evasive-maneuvers", evasive_maneuvers),
        ("transmutation", transmutation), ("aggressive-deployment", aggressive_deployment),
        ("hungry-mechs", hungry_mechs), ("mech-virus", mech_virus), ("overshields", overshields), ("armored-swarms", armored_swarms),
        ("leadership", leadership), ("shield-towers", shield_towers), ("ambush", ambush),
    ]:
        icon("curses", name, [(mask, CURSE_TONES.get(color, color)) for mask, color in fn()])

    icon("pickups", "magnet", magnet())

    print("Icons written to", os.path.abspath(SPRITES))
