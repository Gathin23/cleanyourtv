"""Draws the wallpapers of themes/scenes: 14 dark illustrated landscapes, as SVG (1280x720) from code, so the
collection is this repo's own work and can be changed or extended here.

    python3 tools/scenes_wallpapers.py <folder>     # writes 01-dusk-peaks.svg ... 14-autumn-valley.svg

Every scene keeps its lower third dark, because the dock, the grid and the text sit there. The random seeds are
fixed, so the same code always draws the same SVGs.

themes/scenes/wallpapers/ holds them as 1920x1080 JPEG, each under 300 KB as themes/README.md asks: a headless
Chrome screenshot of the SVG (a 1280x720 window at device scale 1.5), blended 1.8% with Pillow grain
(Image.effect_noise, sigma 4) so the gradients do not band on an 8-bit panel, and saved at quality 86. The grain is
random, so a new render looks the same but is not byte-identical.

A new scene also needs its name in themes/scenes/wallpapers/captions.txt (`<file><TAB><name>`), which is what
wallpaper_captions shows under the top-right pills; tests/test_scenes_captions.py fails until it has one.
"""
import math
import os
import random
import sys

W, H = 1280, 720


# ---- building blocks ---------------------------------------------------------------------------

def grad(gid, stops, vertical=True):
    """A linear gradient, top to bottom or left to right; a stop is (offset, colour) or (offset, colour, opacity)."""
    x2, y2 = ("0", "1") if vertical else ("1", "0")
    s = "".join(f'<stop offset="{o}" stop-color="{c}"' + (f' stop-opacity="{a}"' if a is not None else "") + "/>"
                for o, c, a in [(st + (None,))[:3] for st in stops])
    return f'<linearGradient id="{gid}" x1="0" y1="0" x2="{x2}" y2="{y2}">{s}</linearGradient>'


def radial(gid, color, inner=1.0, mid=0.35):
    """A glow: the colour at the centre, fading to nothing at the edge."""
    return (f'<radialGradient id="{gid}"><stop offset="0" stop-color="{color}" stop-opacity="{inner}"/>'
            f'<stop offset=".4" stop-color="{color}" stop-opacity="{mid}"/>'
            f'<stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>')


BLURS = "".join(f'<filter id="b{n}" x="-20%" y="-50%" width="140%" height="200%">'
                f'<feGaussianBlur stdDeviation="{n}"/></filter>' for n in (2, 4, 8, 14, 24, 40))


def ridge(seed, base, amp, rough=1.0, step=8, freq=1.0):
    """A mountain line: summed sines, rougher with a higher rough."""
    rnd = random.Random(seed)
    phases = [rnd.uniform(0, 6.28) for _ in range(5)]
    pts = []
    for x in range(-step, W + 2 * step, step):
        y = base
        for k, ph in enumerate(phases):
            f = freq * (k + 1) ** 1.6 / 900.0
            y -= amp / (k + 1) ** rough * math.sin(x * f * 6.28 + ph)
        y += rnd.uniform(-1.5, 1.5) * rough
        pts.append(f"{x},{y:.1f}")
    return "M" + " L".join(pts) + f" L{W + 2 * step},{H} L{-step},{H} Z"


def hills(seed, base, amp, waves=2.0, step=10):
    """Smooth rolling hills or dunes."""
    rnd = random.Random(seed)
    ph = [rnd.uniform(0, 6.28) for _ in range(3)]
    pts = []
    for x in range(-step, W + 2 * step, step):
        y = base - amp * (0.65 * math.sin(x / W * waves * 6.28 + ph[0])
                          + 0.25 * math.sin(x / W * waves * 2.3 * 6.28 + ph[1])
                          + 0.10 * math.sin(x / W * waves * 5.1 * 6.28 + ph[2]))
        pts.append(f"{x},{y:.1f}")
    return "M" + " L".join(pts) + f" L{W + 2 * step},{H} L{-step},{H} Z"


def stars(seed, n, ymax, rmax=1.7, opacity=(0.35, 0.95)):
    """n stars above ymax, thicker towards the top."""
    rnd = random.Random(seed)
    return "".join(f'<circle cx="{rnd.uniform(0, W):.0f}" cy="{ymax * rnd.random() ** 1.4:.0f}" '
                   f'r="{rnd.choice([.5, .7, .9, 1.1, rmax])}" fill="#fff" opacity="{rnd.uniform(*opacity):.2f}"/>'
                   for _ in range(n))


def water(y, top, bottom, gid="water"):
    """The water from y down: its gradient and its shape."""
    return grad(gid, [(0, top), (1, bottom)]), f'<rect y="{y}" width="{W}" height="{H - y}" fill="url(#{gid})"/>'


def ripples(seed, y0, color, n=3, op=(0.06, 0.25)):
    """Thin light streaks on the water, a row every 8 px from y0 down."""
    rnd = random.Random(seed)
    return "".join(f'<rect x="{rnd.randint(0, W - 40)}" y="{y}" width="{rnd.randint(30, 170)}" height="1.3" '
                   f'fill="{color}" opacity="{rnd.uniform(*op):.2f}"/>' for y in range(y0, H, 8) for _ in range(n))


def svg(defs, body):
    """A whole 1280x720 picture, with the shared blur filters."""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">'
            f'<defs>{BLURS}{defs}</defs>{body}</svg>')


# ---- the scenes --------------------------------------------------------------------------------

def dusk_peaks():
    """Layered violet peaks against a dusk sky, the sun low behind them."""
    d = grad("sky", [(0, "#151a44"), (.38, "#4b3170"), (.6, "#c8607a"), (.72, "#f39a6b"), (.82, "#fbc98a")]) + \
        radial("sun", "#ffd59a")
    b = ('<rect width="1280" height="720" fill="url(#sky)"/><circle cx="905" cy="455" r="230" fill="url(#sun)"/>'
         f'<path d="{ridge(3, 470, 70, 1.1)}" fill="#a0628a" opacity=".75"/>'
         f'<path d="{ridge(7, 520, 85)}" fill="#6a3f74"/>'
         f'<path d="{ridge(11, 585, 70, 1.2)}" fill="#3b2650"/><path d="{ridge(19, 650, 55, 1.4)}" fill="#1d1430"/>')
    return svg(d, b)


def golden_coast():
    """The sun setting over the sea, its gold streak across the water."""
    gd, gb = water(430, "#5a4a78", "#0a1426")
    d = grad("sky", [(0, "#0d2547"), (.35, "#2f4f7f"), (.5, "#b9708a"), (.6, "#f6a36f")]) + \
        radial("sun", "#ffc884") + gd + grad("streak", [(0, "#ffcf98", .75), (1, "#ffcf98", 0)])
    b = ('<rect width="1280" height="432" fill="url(#sky)"/><circle cx="420" cy="430" r="190" fill="url(#sun)"/>' + gb +
         '<path d="M390,432 L450,432 L520,720 L320,720 Z" fill="url(#streak)" opacity=".7"/>' +
         ripples(5, 436, "#ffd2a6"))
    return svg(d, b)


def moon_lake():
    """A full moon and stars over a dark lake and a low ridge."""
    gd, gb = water(500, "#162b57", "#050a18")
    d = grad("sky", [(0, "#040816"), (.55, "#0e1c44"), (.68, "#1d3366")]) + radial("glow", "#9fb6f0", .45, .15) + gd + \
        grad("streak", [(0, "#dfe6ff", .55), (1, "#dfe6ff", 0)])
    b = ('<rect width="1280" height="720" fill="url(#sky)"/>' + stars(9, 260, 380) +
         '<circle cx="985" cy="170" r="220" fill="url(#glow)"/><circle cx="985" cy="170" r="34" fill="#f1ecdc"/>'
         f'<path d="{ridge(31, 470, 60, 1.2)}" fill="#0a1430"/>' + gb +
         '<path d="M975,502 L995,502 L1040,720 L930,720 Z" fill="url(#streak)" opacity=".55"/>' +
         f'<path d="{ridge(37, 520, 25, 1.5)}" fill="#050b1c"/>')
    return svg(d, b)


def aurora():
    """Green and violet aurora curtains over a mountain lake."""
    gd, gb = water(520, "#0b2a33", "#030a10")
    d = grad("sky", [(0, "#020611"), (.5, "#06182a"), (.75, "#0b2b3a")]) + gd + \
        grad("au1", [(0, "#3cf2b4", 0), (.4, "#3cf2b4", .85), (.7, "#40c8ff", .5), (1, "#8a5cff", 0)],
             vertical=False) + \
        grad("au2", [(0, "#8a5cff", 0), (.5, "#b06cff", .6), (1, "#3cf2b4", 0)], vertical=False)
    curtains = ('<path d="M-50,300 C200,140 420,330 640,190 S1000,120 1330,230" stroke="url(#au1)" '
                'stroke-width="70" fill="none" filter="url(#b24)" opacity=".9"/>'
                '<path d="M-50,250 C260,90 520,280 760,150 S1100,80 1330,170" stroke="url(#au2)" '
                'stroke-width="40" fill="none" filter="url(#b14)" opacity=".7"/>'
                '<path d="M100,330 C330,220 560,340 820,260 S1150,230 1330,300" stroke="url(#au1)" '
                'stroke-width="26" fill="none" filter="url(#b8)" opacity=".6"/>')
    b = ('<rect width="1280" height="720" fill="url(#sky)"/>' + stars(41, 220, 420, 1.4) + curtains +
         f'<path d="{ridge(43, 500, 80, 1.15)}" fill="#0c1a24"/>'
         f'<path d="{ridge(47, 520, 45, 1.3)}" fill="#07121a"/>' + gb +
         '<path d="M-50,560 C300,520 700,600 1330,540" stroke="#3cf2b4" stroke-width="30" fill="none" '
         'filter="url(#b24)" opacity=".18"/>' +
         ripples(49, 528, "#7fe9c9", 2, (0.04, 0.14)))
    return svg(d, b)


def dunes():
    """Desert dunes at sunset."""
    d = grad("sky", [(0, "#24163a"), (.35, "#6b3654"), (.55, "#c8664a"), (.68, "#f0a560")]) + \
        radial("sun", "#ffd28a") + grad("d1", [(0, "#c0703f"), (1, "#5a2c22")]) + \
        grad("d2", [(0, "#8f4a2e"), (1, "#3a1c18")]) + grad("d3", [(0, "#5c2c22"), (1, "#1c0e0e")])
    b = ('<rect width="1280" height="720" fill="url(#sky)"/><circle cx="760" cy="470" r="200" fill="url(#sun)"/>'
         '<circle cx="760" cy="468" r="38" fill="#ffe2b0" opacity=".9"/>'
         f'<path d="{hills(51, 470, 22, 1.6)}" fill="url(#d1)"/>'
         f'<path d="{hills(53, 540, 40, 1.2)}" fill="url(#d2)"/>'
         f'<path d="{hills(57, 620, 45, 0.9)}" fill="url(#d3)"/>'
         f'<path d="{hills(59, 690, 30, 1.4)}" fill="#120808"/>')
    return svg(d, b)


def tea_hills():
    """Tea-garden hills in the morning mist, rows of bushes in front."""
    d = grad("sky", [(0, "#1c2f3e"), (.45, "#5d8189"), (.7, "#c9c39a"), (.8, "#ecd3a0")]) + \
        radial("sun", "#fff0c2", .9, .3)

    def mist(y, op):
        return f'<rect x="-50" y="{y}" width="1380" height="70" fill="#e8efe6" opacity="{op}" filter="url(#b24)"/>'

    rnd = random.Random(81)
    rows = "".join(f'<path d="M-20,{620 + i * 16} Q640,{580 + i * 16 + rnd.uniform(-8, 8)} 1300,{630 + i * 16}" '
                   'stroke="#2f5a3d" stroke-width="2" fill="none" opacity=".45"/>' for i in range(8))
    b = ('<rect width="1280" height="720" fill="url(#sky)"/><circle cx="300" cy="390" r="240" fill="url(#sun)"/>'
         f'<path d="{hills(83, 440, 35, 1.3)}" fill="#7f9f8c"/>' + mist(420, .4) +
         f'<path d="{hills(85, 500, 45, 1.1)}" fill="#4f7a5f"/>' + mist(480, .3) +
         f'<path d="{hills(87, 570, 50, 0.9)}" fill="#2f5a41"/>' + mist(560, .18) +
         f'<path d="{hills(89, 640, 40, 1.2)}" fill="#18382a"/>' + rows +
         f'<path d="{hills(91, 700, 20, 1.6)}" fill="#0c1f17"/>')
    return svg(d, b)


def alpenglow():
    """Snow peaks lit pink after sunset."""
    d = grad("sky", [(0, "#141b38"), (.4, "#4a3c6e"), (.62, "#c87d8f"), (.75, "#f3b597")]) + \
        grad("snow", [(0, "#ffe9e4"), (.35, "#e8b8c0"), (1, "#5d4a72")])
    b = ('<rect width="1280" height="720" fill="url(#sky)"/>' + stars(93, 60, 160, 1.2, (0.2, 0.6)) +
         f'<path d="{ridge(95, 430, 130, 1.6, 6, 0.8)}" fill="url(#snow)"/>'
         f'<path d="{ridge(97, 505, 80, 1.5, 6, 1.0)}" fill="#3e3358" opacity=".95"/>'
         f'<path d="{ridge(99, 590, 60, 1.3)}" fill="#221c36"/>'
         f'<path d="{ridge(101, 660, 40, 1.5)}" fill="#100d1d"/>')
    return svg(d, b)


def city_lights():
    """A skyline at night, its lit windows mirrored in the water."""
    gd, gb = water(560, "#141a3a", "#04050c")
    d = grad("sky", [(0, "#050816"), (.55, "#151d46"), (.78, "#3a2c5c"), (.86, "#6a3f62")]) + gd
    rnd = random.Random(103)
    bld, win, refl = [], [], []
    x = -10
    while x < W + 10:
        bw = rnd.randint(28, 70)
        bh = rnd.randint(60, 260) * (1.25 if rnd.random() < .15 else 1)
        top = 560 - bh
        shade = rnd.choice(["#0b0f22", "#0e1328", "#0a0d1c"])
        bld.append(f'<rect x="{x}" y="{top:.0f}" width="{bw}" height="{bh:.0f}" fill="{shade}"/>')
        for wy in range(int(top) + 8, 552, 9):
            for wx in range(x + 5, x + bw - 5, 8):
                if rnd.random() < 0.28:
                    c = rnd.choice(["#ffd27a", "#ffe6b0", "#9fd0ff"])
                    win.append(f'<rect x="{wx}" y="{wy}" width="3" height="4" fill="{c}" '
                               f'opacity="{rnd.uniform(.4, .95):.2f}"/>')
                    if rnd.random() < 0.25:
                        refl.append(f'<rect x="{wx}" y="{560 + (560 - wy) * 0.6:.0f}" width="3" '
                                    f'height="{rnd.randint(6, 18)}" fill="{c}" opacity=".18"/>')
        x += bw + rnd.randint(0, 6)
    b = ('<rect width="1280" height="720" fill="url(#sky)"/>' + stars(105, 90, 260, 1.2, (0.2, 0.6)) +
         '<rect y="470" width="1280" height="90" fill="#8a4f7a" opacity=".25" filter="url(#b24)"/>' +
         "".join(bld) + "".join(win) + gb + "".join(refl) + ripples(107, 566, "#ffd9a0", 2, (0.04, 0.12)))
    return svg(d, b)


def monsoon():
    """Rain clouds over dark hills, a band of light on the horizon."""
    d = grad("sky", [(0, "#10181d"), (.45, "#26363d"), (.6, "#4a5d61"), (.68, "#c9925e")]) + \
        radial("glow", "#ffbf73", .7, .25)
    rnd = random.Random(121)
    clouds = "".join(f'<ellipse cx="{rnd.uniform(-100, 1380):.0f}" cy="{rnd.uniform(40, 330):.0f}" '
                     f'rx="{rnd.uniform(140, 320):.0f}" ry="{rnd.uniform(30, 70):.0f}" '
                     f'fill="{rnd.choice(["#1a262c", "#22323a", "#2e4148", "#3a4f55"])}" '
                     f'opacity="{rnd.uniform(.5, .9):.2f}" filter="url(#b14)"/>' for _ in range(26))
    rain = "".join(f'<path d="M{x:.0f},{y:.0f} l-6,26" stroke="#b8c8cc" stroke-width="1" '
                   f'opacity="{rnd.uniform(.05, .18):.2f}"/>'
                   for x, y in ((rnd.uniform(0, 1300), rnd.uniform(0, 600)) for _ in range(380)))
    b = ('<rect width="1280" height="720" fill="url(#sky)"/>'
         '<ellipse cx="900" cy="470" rx="420" ry="90" fill="url(#glow)"/>' + clouds +
         f'<path d="{hills(123, 500, 40, 1.4)}" fill="#1f2f2c"/><path d="{hills(125, 580, 50, 1.0)}" fill="#142220"/>'
         f'<path d="{hills(127, 660, 35, 1.5)}" fill="#0a1312"/>' + rain)
    return svg(d, b)


def above_clouds():
    """Sunrise over a sea of clouds, seen from a hilltop."""
    d = grad("sky", [(0, "#1a2140"), (.35, "#5a4a7c"), (.55, "#e08a7a"), (.64, "#ffc78e")]) + \
        radial("sun", "#ffd9a0", .95, .35) + \
        grad("sea", [(0, "#f2c3b8"), (.18, "#b98fa6"), (.5, "#4a3d5e"), (1, "#15111f")])
    rnd = random.Random(201)
    layers = [(452, "#ffd8c8", 16), (490, "#d7a9b4", 16), (540, "#9a7a98", 14), (600, "#5c4a6c", 12)]
    puffs = "".join(f'<ellipse cx="{rnd.uniform(-80, 1360):.0f}" cy="{y + rnd.uniform(-8, 8):.0f}" '
                    f'rx="{rnd.uniform(90, 220):.0f}" ry="{rnd.uniform(18, 34):.0f}" '
                    f'fill="{c}" opacity="{rnd.uniform(.55, .9):.2f}" filter="url(#b14)"/>'
                    for y, c, n in layers for _ in range(n))
    b = ('<rect width="1280" height="720" fill="url(#sky)"/>' + stars(203, 40, 150, 1.1, (0.2, 0.5)) +
         '<circle cx="560" cy="440" r="240" fill="url(#sun)"/>'
         '<circle cx="560" cy="446" r="30" fill="#fff0cf" opacity=".95"/>'
         f'<path d="{ridge(205, 445, 45, 1.3)}" fill="#4a3a63"/>'
         '<rect y="440" width="1280" height="280" fill="url(#sea)" filter="url(#b8)"/>' + puffs +
         f'<path d="{hills(207, 655, 45, 1.1)}" fill="#1a1426"/><path d="{hills(209, 705, 22, 1.8)}" fill="#0c0912"/>')
    return svg(d, b)


def mirror_lake():
    """Pink sunrise peaks, mirrored in a still lake."""
    d = grad("sky", [(0, "#151d3a"), (.22, "#3b3466"), (.4, "#8a587e"), (.5, "#d9837e"), (.57, "#f4b88c")]) + \
        radial("sun", "#ffe0a8", .9, .3) + \
        grad("peak", [(0, "#c98aa0"), (.5, "#6d4e78"), (1, "#2c2340")]) + \
        grad("deep", [(0, "#0c0a1a", .55), (.35, "#08071a", .8), (1, "#040309", .97)]) + \
        '<clipPath id="below"><rect y="410" width="1280" height="310"/></clipPath>'
    scene = ('<rect width="1280" height="410" fill="url(#sky)"/><circle cx="520" cy="392" r="230" fill="url(#sun)"/>'
             f'<path d="{ridge(211, 392, 120, 1.5, 6, 0.85)}" fill="url(#peak)"/>'
             f'<path d="{ridge(213, 412, 45, 1.4)}" fill="#1e1830"/>')
    b = (scene + '<g clip-path="url(#below)">'
         f'<g transform="translate(0,822) scale(1,-1)" opacity=".5" filter="url(#b4)">{scene}</g></g>'
         '<rect y="410" width="1280" height="310" fill="url(#deep)"/>' + ripples(215, 418, "#ffd9c0", 2, (0.05, 0.2)) +
         f'<path d="{hills(217, 702, 16, 2.2)}" fill="#09070f"/>')
    return svg(d, b)


def balloon(x, y, r, color, op=1.0):
    """A hot-air balloon: envelope, darker gores, a lit edge and a basket."""
    env = (f"M{x - r:.1f},{y:.1f} C{x - r:.1f},{y - 1.35 * r:.1f} {x + r:.1f},{y - 1.35 * r:.1f} {x + r:.1f},{y:.1f} "
           f"C{x + r:.1f},{y + .6 * r:.1f} {x + .35 * r:.1f},{y + 1.05 * r:.1f} {x + .22 * r:.1f},{y + 1.25 * r:.1f} "
           f"L{x - .22 * r:.1f},{y + 1.25 * r:.1f} "
           f"C{x - .35 * r:.1f},{y + 1.05 * r:.1f} {x - r:.1f},{y + .6 * r:.1f} {x - r:.1f},{y:.1f} Z")
    gores = "".join(f'<ellipse cx="{x:.1f}" cy="{y + .05 * r:.1f}" rx="{r * k:.1f}" ry="{1.12 * r:.1f}" fill="none" '
                    f'stroke="#000" stroke-opacity=".18" stroke-width="{max(1, r * .06):.1f}"/>' for k in (.35, .7))
    lit = f'<path d="{env}" fill="url(#lit)"/>'
    basket = (f'<path d="M{x - .2 * r:.1f},{y + 1.25 * r:.1f} L{x - .14 * r:.1f},{y + 1.55 * r:.1f} '
              f'M{x + .2 * r:.1f},{y + 1.25 * r:.1f} L{x + .14 * r:.1f},{y + 1.55 * r:.1f}" '
              f'stroke="#2a1a22" stroke-width="{max(.8, r * .04):.1f}"/>'
              f'<rect x="{x - .16 * r:.1f}" y="{y + 1.55 * r:.1f}" width="{.32 * r:.1f}" height="{.24 * r:.1f}" '
              f'rx="{.04 * r:.1f}" fill="#3a2418"/>')
    return f'<g opacity="{op}"><path d="{env}" fill="{color}"/>{gores}{lit}{basket}</g>'


def balloon_sunrise():
    """Hot-air balloons drifting over layered hills at dawn."""
    d = grad("sky", [(0, "#22264a"), (.32, "#7a5a8a"), (.56, "#f4a36f"), (.66, "#ffd79b")]) + \
        radial("sun", "#ffe2a6", .9, .3) + \
        '<radialGradient id="lit" cx=".3" cy=".25" r=".8"><stop offset="0" stop-color="#fff" stop-opacity=".38"/>' \
        '<stop offset=".6" stop-color="#fff" stop-opacity="0"/>' \
        '<stop offset="1" stop-color="#000" stop-opacity=".25"/></radialGradient>'
    balloons = (balloon(705, 250, 46, "#e8574a") + balloon(560, 330, 30, "#f2a33a") +
                balloon(840, 360, 22, "#3fa7a0", .92) + balloon(400, 200, 18, "#7a5cc8", .85) +
                balloon(980, 170, 14, "#e8c44a", .8) + balloon(300, 390, 12, "#d9547e", .75) +
                balloon(1120, 300, 10, "#5aa0e0", .7))
    b = ('<rect width="1280" height="720" fill="url(#sky)"/><circle cx="880" cy="450" r="230" fill="url(#sun)"/>'
         '<circle cx="880" cy="442" r="30" fill="#fff0cf" opacity=".9"/>' + balloons +
         f'<path d="{hills(221, 480, 25, 1.4)}" fill="#a0607a" opacity=".75"/>'
         '<rect x="-50" y="470" width="1380" height="50" fill="#ffd2b0" opacity=".25" filter="url(#b24)"/>'
         f'<path d="{hills(223, 540, 40, 1.1)}" fill="#6a3f66"/><path d="{hills(227, 610, 45, 0.9)}" fill="#3a2446"/>'
         f'<path d="{hills(229, 690, 30, 1.4)}" fill="#160d1c"/>')
    return svg(d, b)


def lighthouse_coast():
    """A lighthouse on a headland at blue hour, its beam across the sky."""
    gd, gb = water(470, "#2a3a66", "#050914")
    d = grad("sky", [(0, "#0b1430"), (.4, "#253a6b"), (.58, "#6b5a8a"), (.66, "#e6936e")]) + gd + \
        radial("lamp", "#fff1c2", 1, .35) + grad("beam", [(0, "#fff3cc", .45), (1, "#fff3cc", 0)], vertical=False) + \
        grad("beam2", [(0, "#fff3cc", 0), (1, "#fff3cc", .45)], vertical=False)
    lx, top = 760, 268    # tower centre and lamp height
    tower = (f'<path d="M{lx - 18},372 L{lx - 11},{top + 18} L{lx + 11},{top + 18} L{lx + 18},372 Z" fill="#d9dce6"/>'
             + "".join(f'<path d="M{lx - 18 + 2.2 * k},{372 - 26 * k} L{lx - 16.6 + 2.2 * k},{372 - 26 * k - 13} '
                       f'L{lx + 16.6 - 2.2 * k},{372 - 26 * k - 13} L{lx + 18 - 2.2 * k},{372 - 26 * k} Z" '
                       'fill="#b8463e"/>' for k in range(3)) +
             f'<rect x="{lx - 15}" y="{top + 12}" width="30" height="6" fill="#2a2e3e"/>'
             f'<rect x="{lx - 9}" y="{top - 4}" width="18" height="16" rx="2" fill="#fff1c2"/>'
             f'<path d="M{lx - 12},{top - 4} L{lx},{top - 16} L{lx + 12},{top - 4} Z" fill="#2a2e3e"/>')
    beams = (f'<path d="M{lx},{top + 4} L-80,{top - 150} L-80,{top + 60} Z" fill="url(#beam2)" '
             'filter="url(#b8)" opacity=".8"/>'
             f'<path d="M{lx},{top + 4} L1380,{top - 60} L1380,{top + 40} Z" fill="url(#beam)" '
             'filter="url(#b8)" opacity=".35"/>')
    headland = ("M380,720 C420,640 470,560 540,500 C600,452 650,418 700,396 C720,386 735,376 745,372 L790,372 "
                "C850,384 930,424 1010,462 C1090,498 1180,512 1300,516 L1300,720 Z")
    b = ('<rect width="1280" height="720" fill="url(#sky)"/>' + stars(231, 140, 320, 1.3) + beams +
         f'<path d="{hills(233, 468, 10, 2.2)}" fill="#141a33"/>' + gb + ripples(235, 476, "#ffd8b0", 2, (0.04, 0.16)) +
         f'<path d="{headland}" fill="#0b0d18"/>' + tower +
         f'<circle cx="{lx}" cy="{top + 4}" r="70" fill="url(#lamp)"/>' +
         f'<path d="{hills(237, 640, 26, 1.3)}" fill="#07080f"/>'
         f'<path d="{hills(239, 700, 16, 2.5)}" fill="#04050a"/>')
    return svg(d, b)


def autumn_valley():
    """An autumn valley at sunset, red trees below the mist."""
    d = grad("sky", [(0, "#24152a"), (.4, "#6b2f3c"), (.6, "#c45d45"), (.7, "#eda15f")]) + \
        radial("sun", "#ffc77a", .8, .3)
    rnd = random.Random(141)
    trees = "".join(f'<circle cx="{rnd.uniform(0, 1280):.0f}" cy="{y + rnd.uniform(-10, 40):.0f}" '
                    f'r="{rnd.uniform(5, 13):.1f}" fill="{rnd.choice(["#7a2a1e", "#9a3c1f", "#5c1f1a", "#b5522a"])}" '
                    'opacity=".85"/>' for y in (560, 600, 640) for _ in range(70))
    mist = '<rect x="-50" y="470" width="1380" height="60" fill="#f2b98a" opacity=".22" filter="url(#b24)"/>'
    b = ('<rect width="1280" height="720" fill="url(#sky)"/><circle cx="1000" cy="420" r="230" fill="url(#sun)"/>'
         f'<path d="{ridge(143, 450, 60, 1.0)}" fill="#8a4048" opacity=".7"/>' + mist +
         f'<path d="{hills(145, 520, 45, 1.2)}" fill="#5a2430"/>'
         f'<path d="{hills(147, 590, 45, 1.0)}" fill="#3a1820"/>' + trees +
         f'<path d="{hills(149, 680, 30, 1.4)}" fill="#160a0e"/>')
    return svg(d, b)


SCENES = [("01-dusk-peaks", dusk_peaks), ("02-golden-coast", golden_coast), ("03-moon-lake", moon_lake),
          ("04-aurora", aurora), ("05-dunes", dunes), ("06-above-the-clouds", above_clouds),
          ("07-mirror-lake", mirror_lake), ("08-tea-hills", tea_hills), ("09-alpenglow", alpenglow),
          ("10-city-lights", city_lights), ("11-balloon-sunrise", balloon_sunrise), ("12-monsoon", monsoon),
          ("13-lighthouse-coast", lighthouse_coast), ("14-autumn-valley", autumn_valley)]

# ---- the one argument ---------------------------------------------------------------------------

USAGE = "usage: scenes_wallpapers.py <folder>     (the folder to write the 14 SVGs into; -h for the rest)"


def target(argv):
    """The folder to write into, from the command line; anything that is not one is refused here.

    'scenes_wallpapers.py --help' used to take the option for the folder: it made a directory called
    '--help' in whatever directory the reader stood in and filled it with 14 SVGs, and said so with exit 0.
    So -h/--help now prints this script's own note and writes nothing, any other leading dash is refused
    ('--out x' would otherwise become a folder called '--out'), and a path that exists but is not a folder
    is named instead of ending in os.makedirs' FileExistsError traceback. An empty word is not a folder
    either: os.makedirs('') is a FileNotFoundError whose line names nothing the reader typed.
    """
    if argv in (["-h"], ["--help"]):
        print((__doc__ or USAGE).strip())
        sys.exit(0)
    if len(argv) != 1:
        print(USAGE, file=sys.stderr)
        sys.exit(2)
    out = argv[0]
    if not out:
        print("scenes_wallpapers.py: the folder to write the scenes into was given as an empty "
              f"argument.\n{USAGE}", file=sys.stderr)
        sys.exit(2)
    if out.startswith("-"):
        print(f"scenes_wallpapers.py takes one folder and no options, so {out} is not one.\n{USAGE}",
              file=sys.stderr)
        sys.exit(2)
    if os.path.exists(out) and not os.path.isdir(out):
        print(f"scenes_wallpapers.py: {out} is a file, and the scenes need a folder", file=sys.stderr)
        sys.exit(2)
    return out


def write(out):
    """The 14 SVGs into `out`, refusing a folder it cannot create or a file it cannot write.

    Neither used to be answered: a path through a file ('README.md/scenes') ended in os.makedirs'
    NotADirectoryError, and a folder sitting where one scene's name goes ended in IsADirectoryError
    from open(), each a traceback with exit 1 - the code a run that wrote nothing returns. Both are
    the reader's own argument or their own folder, so they are answered in the words the other
    refusals here use, before anything else is written; the second says how much did land, so a
    half-filled folder cannot be mistaken for the 14 the script promises.
    """
    try:
        os.makedirs(out, exist_ok=True)
    except OSError as e:
        print(f"scenes_wallpapers.py: cannot create {out}: {e.strerror or e}. Nothing was written.",
              file=sys.stderr)
        sys.exit(2)
    for done, (name, scene) in enumerate(SCENES):
        path = os.path.join(out, f"{name}.svg")
        try:
            with open(path, "w") as f:
                f.write(scene())
        except OSError as e:
            print(f"scenes_wallpapers.py: cannot write {path}: {e.strerror or e}", file=sys.stderr)
            print(f"stopped after {done} of {len(SCENES)} scenes, which are left in {out} as they "
                  "are.", file=sys.stderr)
            sys.exit(2)
    print(len(SCENES), "scenes written to", out)


if __name__ == "__main__":
    write(target(sys.argv[1:]))
