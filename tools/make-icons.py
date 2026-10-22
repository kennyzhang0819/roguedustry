import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "sprites", "mutators")

RED, DARKRED, PEACH, WHITE, GRAY, DARK = (242, 85, 85), (168, 58, 58), (255, 220, 182), (239, 247, 255), (110, 110, 118), (60, 58, 62)
PURPLE, LILAC, BLUE, LIGHTBLUE, YELLOW = (142, 92, 160), (211, 170, 230), (110, 132, 220), (190, 205, 255), (255, 211, 127)


def octagon(d, x0, y0, x1, y1, c, cut=None):
    cut = cut if cut is not None else min(x1 - x0, y1 - y0) // 3
    d.polygon([(x0 + cut, y0), (x1 - cut, y0), (x1, y0 + cut), (x1, y1 - cut), (x1 - cut, y1), (x0 + cut, y1), (x0, y1 - cut), (x0, y0 + cut)], fill=c)


def chevron(d, x, y, size, thick, c, direction="right"):
    s, t = size, thick
    if direction == "right":
        d.polygon([(x, y), (x + t, y), (x + t + s, y + s), (x + t, y + 2 * s), (x, y + 2 * s), (x + s, y + s)], fill=c)
    elif direction == "up":
        d.polygon([(x, y + s), (x + s, y), (x + 2 * s, y + s), (x + 2 * s, y + s + t), (x + s, y + t), (x, y + s + t)], fill=c)
    else:
        d.polygon([(x, y), (x + s, y + s), (x + 2 * s, y), (x + 2 * s, y + t), (x + s, y + s + t), (x, y + t)], fill=c)


def slant(d, x, y, w, h, c):
    d.polygon([(x + h, y), (x + h + w, y), (x + w, y + h), (x, y + h)], fill=c)


def icon(name, draw_fn):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(img))
    img.save(os.path.join(OUT, name + ".png"))


def speed_freaks(d):
    chevron(d, 6, 10, 22, 12, DARKRED)
    chevron(d, 26, 10, 22, 12, RED)


def life_leech(d):
    d.polygon([(32, 4), (52, 24), (52, 44), (38, 58), (26, 58), (12, 44), (12, 24)], fill=RED)
    d.polygon([(32, 4), (52, 24), (52, 44), (38, 58), (32, 58)], fill=DARKRED)
    slant(d, 18, 32, 6, 10, PEACH)


def long_range(d):
    octagon(d, 6, 6, 58, 58, GRAY, 16)
    octagon(d, 14, 14, 50, 50, (0, 0, 0, 0), 11)
    for box in [(29, 0, 35, 16), (29, 48, 35, 64), (0, 29, 16, 35), (48, 29, 64, 35)]:
        d.rectangle(box, fill=WHITE)
    d.polygon([(32, 22), (42, 32), (32, 42), (22, 32)], fill=RED)


def just_die(d):
    chevron(d, 18, 2, 14, 7, RED, "up")
    octagon(d, 12, 20, 52, 50, WHITE, 10)
    d.rectangle((20, 50, 44, 60), fill=WHITE)
    for x in (24, 32, 40):
        d.rectangle((x - 2, 52, x, 60), fill=DARK)
    octagon(d, 18, 30, 29, 41, DARK, 3)
    octagon(d, 35, 30, 46, 41, DARK, 3)


def self_destruction(d):
    octagon(d, 6, 18, 46, 58, GRAY, 12)
    slant(d, 14, 28, 6, 10, WHITE)
    d.polygon([(38, 18), (46, 10), (52, 16), (44, 24)], fill=DARK)
    d.polygon([(54, 2), (62, 10), (54, 18), (46, 10)], fill=RED)
    d.polygon([(54, 7), (57, 10), (54, 13), (51, 10)], fill=YELLOW)


def mutually_assured(d):
    octagon(d, 2, 2, 62, 62, PEACH, 18)
    d.polygon([(32, 28), (14, 10), (50, 10)], fill=DARK)
    d.polygon([(30, 34), (12, 52), (30, 52)], fill=DARK)
    d.polygon([(34, 34), (52, 52), (34, 52)], fill=DARK)
    octagon(d, 24, 24, 40, 40, RED, 5)


def walking_infested(d):
    d.polygon([(2, 36), (12, 26), (18, 26), (8, 36)], fill=DARKRED)
    d.polygon([(62, 36), (52, 26), (46, 26), (56, 36)], fill=DARKRED)
    d.polygon([(4, 60), (14, 50), (20, 50), (10, 60)], fill=DARKRED)
    d.polygon([(60, 60), (50, 50), (44, 50), (54, 60)], fill=DARKRED)
    octagon(d, 14, 14, 50, 54, RED, 12)
    octagon(d, 20, 24, 28, 32, PEACH, 2)
    octagon(d, 36, 24, 44, 32, PEACH, 2)


def alien_incubation(d):
    octagon(d, 14, 4, 50, 60, PURPLE, 14)
    d.polygon([(32, 4), (50, 22), (50, 46), (36, 60), (32, 60)], fill=(116, 72, 134))
    slant(d, 20, 18, 6, 14, LILAC)
    octagon(d, 30, 40, 38, 48, LILAC, 2)


def barrier(d):
    d.polygon([(8, 6), (56, 6), (56, 36), (32, 60), (8, 36)], fill=BLUE)
    d.polygon([(16, 14), (48, 14), (48, 33), (32, 49), (16, 33)], fill=LIGHTBLUE)
    d.polygon([(32, 14), (48, 14), (48, 33), (32, 49)], fill=WHITE)


def avenger(d):
    octagon(d, 6, 10, 58, 58, GRAY, 14)
    d.polygon([(12, 24), (28, 40), (28, 46), (12, 30)], fill=RED)
    d.polygon([(52, 24), (36, 40), (36, 46), (52, 30)], fill=RED)
    d.rectangle((20, 46, 44, 51), fill=DARK)
    chevron(d, 18, 0, 14, 6, DARKRED, "up")


def inspiration(d):
    d.rectangle((14, 14, 50, 50), fill=PEACH)
    d.polygon([(32, 2), (62, 32), (32, 62), (2, 32)], fill=YELLOW)
    octagon(d, 22, 22, 42, 42, WHITE, 6)


def evasive_maneuvers(d):
    slant(d, 2, 12, 8, 10, DARKRED)
    slant(d, 2, 42, 8, 10, DARKRED)
    slant(d, 8, 27, 10, 10, RED)
    chevron(d, 26, 6, 26, 14, RED)


def transmutation(d):
    octagon(d, 16, 44, 48, 62, DARK, 6)
    d.polygon([(32, 2), (58, 28), (42, 28), (42, 48), (22, 48), (22, 28), (6, 28)], fill=PURPLE)
    d.polygon([(32, 2), (58, 28), (42, 28), (42, 48), (32, 48)], fill=(116, 72, 134))
    d.polygon([(32, 12), (38, 18), (32, 24), (26, 18)], fill=LILAC)


def eminent_domain(d):
    octagon(d, 22, 34, 62, 62, GRAY, 8)
    d.rectangle((30, 42, 54, 54), fill=DARK)
    d.rectangle((6, 2, 12, 62), fill=WHITE)
    d.polygon([(12, 4), (40, 4), (30, 14), (40, 24), (12, 24)], fill=RED)


def aggressive_deployment(d):
    chevron(d, 4, 6, 14, 8, DARKRED, "down")
    chevron(d, 4, 22, 14, 8, RED, "down")
    chevron(d, 32, 14, 14, 8, DARKRED, "down")
    chevron(d, 32, 30, 14, 8, RED, "down")
    d.rectangle((2, 54, 62, 60), fill=GRAY)


for name, fn in [
    ("speed-freaks", speed_freaks), ("life-leech", life_leech), ("long-range", long_range), ("just-die", just_die),
    ("self-destruction", self_destruction), ("mutually-assured-destruction", mutually_assured),
    ("walking-infested", walking_infested), ("alien-incubation", alien_incubation), ("barrier", barrier),
    ("avenger", avenger), ("inspiration", inspiration), ("evasive-maneuvers", evasive_maneuvers),
    ("transmutation", transmutation), ("eminent-domain", eminent_domain), ("aggressive-deployment", aggressive_deployment),
]:
    icon(name, fn)

print("Icons written to", os.path.abspath(OUT))
