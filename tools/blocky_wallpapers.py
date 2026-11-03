"""Draws the wallpapers of themes/blocky: 11 original pixel-art scenes (a meadow by day, at dusk and at night,
plains, desert, a snowy forest, jungle, ocean, a blossom grove, a cave and a lava cavern), as 1920x1080 PNG,
from code, so the collection is this repo's own work and can be changed or extended here.

    python3 tools/blocky_wallpapers.py <folder>     # writes 01-meadow-day.png ... 11-lava-cavern.png

Needs Pillow (tools/requirements-perf.txt). Every block is 40 px with a 4x4 shaded texture. The sky things (sun,
moon, clouds, stars) are drawn before the terrain, so hills and trees stand in front of them; nothing is drawn in
the top six block rows, where the clock and the top bar's pills sit, and the lower part of each scene fades to
near black so the dock, its name and the grid stay readable. The random seeds are fixed, so the same code always
draws the same pictures.

A new scene also needs its name in themes/blocky/wallpapers/captions.txt (`<file><TAB><name>`) and in the theme's
wallpaper_moods; tests/test_blocky_wallpapers.py fails until both have it.
"""
import os
import random
import sys

W, H, BS = 1920, 1080, 40
COLS, ROWS = W // BS, H // BS          # 48 x 27
SUB = BS // 4                            # texture pixel, 10 px
HEADER_ROWS = 6                          # rows 0-5: the clock, the date and the pills; kept plain sky
DARK_FROM, DARK_TO = 17, 25              # rows where terrain fades to near black (the dock sits around rows 17-23)
SKY_TOP_DIM = 0.6                        # the top of every sky darker, for the white clock and greeting
CLOUD_ROWS = (8, 9, 11)

GRASS, DIRT, STONE = (95, 159, 53), (121, 85, 58), (112, 112, 112)
OAK, OAK_LEAVES = (102, 76, 48), (58, 120, 40)
WHITE_CLOUD = (245, 245, 250)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Canvas:
    def __init__(self, seed):
        from PIL import Image, ImageDraw
        self.Image = Image
        self.rnd = random.Random(seed)
        self.img = Image.new("RGB", (W, H))
        self.d = ImageDraw.Draw(self.img)

    def sky(self, top, bottom):
        top = shade(top, SKY_TOP_DIM)
        for r in range(ROWS):   # banded per block row: pixel-art skies have steps, not smooth gradients
            self.d.rectangle([0, r * BS, W, (r + 1) * BS], fill=mix(top, bottom, r / (ROWS - 1)))

    def block(self, col, row, color, depth_fade=True, rough=0.10):
        """One textured block; rough is the spread of its 4x4 shades."""
        if depth_fade and row >= DARK_FROM:
            color = shade(color, max(0.10, 1 - (row - DARK_FROM + 1) / (DARK_TO - DARK_FROM + 1) * 0.9))
        x0, y0 = col * BS, row * BS
        for sy in range(4):
            for sx in range(4):
                f = 1 + self.rnd.choice((-rough, 0, 0, rough / 2, rough))
                self.d.rectangle([x0 + sx * SUB, y0 + sy * SUB, x0 + (sx + 1) * SUB - 1, y0 + (sy + 1) * SUB - 1],
                                 fill=shade(color, f))

    def glow(self, box, color, strength):
        """Blend a rectangle of the picture towards a colour: the stepped light around a sun or a torch."""
        box = [max(0, box[0]), max(0, box[1]), min(W, box[2]), min(H, box[3])]
        region = self.img.crop(box)
        self.img.paste(self.Image.blend(region, self.Image.new("RGB", region.size, color), strength), box[:2])

    def body(self, col, row, size, color, glow, strength):
        """A square sun or moon of size blocks at a block position, in three rings of stepped light."""
        x, y, s = col * BS, row * BS, size * BS
        for i in range(3, 0, -1):
            self.glow([x - i * BS, y - i * BS, x + s + i * BS, y + s + i * BS], glow, strength)
        self.d.rectangle([x, y, x + s - 1, y + s - 1], fill=color)

    def clouds(self, color, count):
        shadow = shade(color, 0.88)
        for _ in range(count):
            r = self.rnd.choice(CLOUD_ROWS)
            c = self.rnd.randrange(-4, COLS)
            length = self.rnd.randrange(4, 9)
            for i in range(length):
                x0, y0 = (c + i) * BS, r * BS
                self.d.rectangle([x0, y0, x0 + BS - 1, y0 + BS - SUB - 1], fill=color)
                self.d.rectangle([x0, y0 + BS - SUB, x0 + BS - 1, y0 + BS - 1], fill=shadow)
                if 0 < i < length - 2 and self.rnd.random() < 0.6:
                    self.d.rectangle([x0, y0 - BS, x0 + BS - 1, y0 - 1], fill=color)

    def specks(self, count, rows, colors):
        """Single texture pixels: stars, falling snow, petals, embers."""
        for _ in range(count):
            x = self.rnd.randrange(W // SUB) * SUB
            y = self.rnd.randrange(rows[0] * BS // SUB, rows[1] * BS // SUB) * SUB
            self.d.rectangle([x, y, x + SUB - 1, y + SUB - 1], fill=self.rnd.choice(colors))

    def heights(self, base, spread):
        h, out = base, []
        for _ in range(COLS):
            if self.rnd.random() < 2 / 3:
                h += self.rnd.choice((-1, 0, 0, 0, 1))
            h = max(base - spread, min(base + spread, h))
            out.append(h)
        return out

    def terrain(self, tops, top, fill, deep, fill_depth=3):
        for c, t in enumerate(tops):
            for r in range(t, ROWS):
                self.block(c, r, top if r == t else fill if r <= t + fill_depth else deep)

    def tree(self, c, ground, trunk, leaves, height=4, crown=2):
        height = min(height, ground - HEADER_ROWS - crown)   # the crown stays out of the header rows
        for i in range(1, height + 1):
            self.block(c, ground - i, trunk)
        top = ground - height
        for dy in range(-crown, 1):
            w = crown if dy > -crown else crown - 1
            for dx in range(-w, w + 1):
                self.block(c + dx, top + dy, leaves, rough=0.14)

    def spruce(self, c, ground, trunk, leaves, snow, height=6):
        height = min(height, ground - HEADER_ROWS - 1)   # the snowy tip stays out of the header rows
        for i in range(1, height + 1):
            self.block(c, ground - i, trunk)
        for level in range(height - 1):
            w = max(0, (level + 1) // 2 - (1 if level % 2 else 0))
            for dx in range(-w, w + 1):
                self.block(c + dx, ground - 2 - level,
                           snow if level >= height - 3 and dx in (-w, w) else leaves, rough=0.12)
        self.block(c, ground - height - 1, snow)


def day_sky(cv, top, bottom, clouds=WHITE_CLOUD, sun_col=34):
    cv.sky(top, bottom)
    cv.body(sun_col, 9, 3, (255, 248, 200), (255, 246, 210), 0.10)
    if clouds:
        cv.clouds(clouds, 6)


def meadow_day(cv):
    day_sky(cv, (64, 112, 184), (140, 186, 236))
    oaks(cv, 1.0)


def meadow_dusk(cv):
    cv.sky((54, 40, 92), (236, 128, 70))
    cv.body(34, 12, 3, (255, 170, 70), (255, 176, 96), 0.13)   # low, setting behind the hills
    cv.clouds((160, 140, 160), 6)
    oaks(cv, 0.62)
    cv.specks(14, (11, 15), [(230, 255, 120)])   # fireflies


def meadow_night(cv):
    cv.sky((8, 10, 30), (24, 34, 74))
    cv.specks(110, (HEADER_ROWS, 13), [(230, 230, 255), (180, 190, 230)])
    cv.body(35, 9, 2, (225, 228, 240), (200, 210, 255), 0.06)
    for x, y in ((1420, 380), (1450, 410), (1410, 420)):   # craters
        cv.d.rectangle([x, y, x + SUB - 1, y + SUB - 1], fill=(185, 188, 205))
    oaks(cv, 0.35)


def oaks(cv, light):
    tops = cv.heights(14, 2)
    cv.terrain(tops, shade(GRASS, light), shade(DIRT, light), shade(STONE, light))
    for c in (5, 16, 29, 41):
        cv.tree(c, tops[c], shade(OAK, light), shade(OAK_LEAVES, light), height=cv.rnd.randrange(3, 5))


def plains(cv):
    day_sky(cv, (70, 130, 200), (160, 205, 240))
    tops = cv.heights(14, 2)
    cv.terrain(tops, GRASS, DIRT, STONE)
    for c in (7, 23, 38):
        cv.tree(c, tops[c], OAK, OAK_LEAVES, height=4)
    for c in range(0, COLS, 3):   # flowers and tall grass on top of the grass
        if cv.rnd.random() < 0.5:
            cv.d.rectangle([c * BS + 15, (tops[c] - 1) * BS + 20, c * BS + 25, tops[c] * BS - 1],
                           fill=cv.rnd.choice([(230, 60, 50), (240, 210, 60), (70, 140, 50)]))


def desert(cv):
    day_sky(cv, (120, 160, 210), (230, 210, 170))
    tops = cv.heights(14, 2)
    cv.terrain(tops, (219, 207, 160), (210, 196, 146), (196, 168, 112))
    for c in (6, 19, 33, 44):   # cacti
        for i in range(1, cv.rnd.randrange(2, 4) + 1):
            cv.block(c, tops[c] - i, (76, 128, 46), rough=0.16)


def snowy_forest(cv):
    cv.sky((96, 120, 160), (190, 205, 225))
    cv.clouds((220, 225, 235), 7)   # overcast, no sun
    tops = cv.heights(14, 2)
    cv.terrain(tops, (240, 244, 250), DIRT, STONE)
    for c in (4, 12, 22, 31, 40):
        cv.spruce(c, tops[c], (76, 54, 36), (44, 78, 52), (235, 240, 245), height=cv.rnd.randrange(5, 8))
    cv.specks(80, (HEADER_ROWS, 16), [(240, 244, 250)])   # falling snow


def jungle(cv):
    day_sky(cv, (60, 120, 170), (150, 200, 210), sun_col=30)   # in the gap between the crowns at 26 and 37
    tops = cv.heights(14, 2)
    cv.terrain(tops, (70, 150, 40), DIRT, STONE)
    for c in (3, 14, 26, 37, 45):
        cv.tree(c, tops[c], (88, 68, 38), (40, 110, 30), height=cv.rnd.randrange(6, 9), crown=3)
    for c in range(COLS):   # hanging vines
        if cv.rnd.random() < 0.15:
            for i in range(cv.rnd.randrange(1, 4)):
                row = tops[c] - 8 + i
                if row >= HEADER_ROWS:
                    cv.d.rectangle([c * BS + 15, row * BS, c * BS + 24, (row + 1) * BS - 1], fill=(52, 120, 36))


def ocean(cv):
    day_sky(cv, (70, 130, 200), (160, 205, 240))
    for c in range(COLS):
        for r in range(13, ROWS):
            cv.block(c, r, (52, 104, 190) if r == 13 else (36, 76, 150), rough=0.08)
    for c in range(30, 37):   # a small sand island with a tree
        for r in range(12 if 32 <= c <= 34 else 13, 14):
            cv.block(c, r, (219, 207, 160))
    cv.tree(33, 12, OAK, (60, 140, 50), height=4)


def blossom_grove(cv):
    day_sky(cv, (110, 150, 210), (230, 200, 230))
    tops = cv.heights(14, 2)
    cv.terrain(tops, (110, 175, 70), DIRT, STONE)
    for c in (6, 18, 30, 42):
        cv.tree(c, tops[c], (60, 40, 50), (236, 160, 200), height=cv.rnd.randrange(4, 6), crown=3)
    cv.specks(60, (HEADER_ROWS, 15), [(246, 186, 214)])   # drifting petals


def cave(cv):
    ores = [(80, 200, 220), (60, 180, 90), (230, 190, 60), (200, 50, 50)]
    for c in range(COLS):
        for r in range(ROWS):
            cv.block(c, r, (52, 52, 56) if (c * 7 + r * 3) % 11 else (44, 44, 48), rough=0.16)
            if HEADER_ROWS <= r < DARK_FROM and cv.rnd.random() < 0.05:   # ore specks inside a stone block
                ore = cv.rnd.choice(ores)
                for _ in range(3):
                    x, y = c * BS + cv.rnd.randrange(4) * SUB, r * BS + cv.rnd.randrange(4) * SUB
                    cv.d.rectangle([x, y, x + SUB - 1, y + SUB - 1], fill=ore)
    for i in range(6, 0, -1):   # torch light
        cv.glow([380 - 30 - i * 20, 360 - 30 - i * 20, 380 + 30 + i * 20, 360 + 30 + i * 20], (255, 160, 60), 0.07)
    cv.d.rectangle([375, 350, 385, 410], fill=(110, 80, 40))
    cv.d.rectangle([370, 330, 390, 350], fill=(255, 200, 80))


def lava_cavern(cv):
    for c in range(COLS):
        for r in range(ROWS):
            cv.block(c, r, (90, 28, 28) if (c + r * 5) % 7 else (70, 20, 24), rough=0.18)
    for c in range(COLS):   # a lava band under the header, glowing
        for r in (6, 7):
            cv.block(c, r, (232, 110, 30) if r == 6 else (210, 80, 20), depth_fade=False, rough=0.12)
    cv.glow([0, 4 * BS, W, 10 * BS], (255, 120, 40), 0.08)
    for c in (8, 27, 40):   # lava falls from the band
        for r in range(8, cv.rnd.randrange(11, 15)):
            cv.block(c, r, (240, 120, 30), rough=0.12)
    cv.specks(70, (8, 16), [(255, 170, 60), (255, 120, 40)])   # embers


SCENES = [
    ("01-meadow-day", meadow_day), ("02-meadow-dusk", meadow_dusk),
    ("03-meadow-night", meadow_night), ("04-plains", plains), ("05-desert", desert),
    ("06-snowy-forest", snowy_forest), ("07-jungle", jungle), ("08-ocean", ocean),
    ("09-blossom-grove", blossom_grove), ("10-cave", cave), ("11-lava-cavern", lava_cavern),
]

USAGE = "usage: blocky_wallpapers.py <folder>     (the folder to write the 11 PNGs into; -h for the rest)"


def target(argv):
    """The folder to write into, from the command line; anything that is not one is refused here, as
    tools/scenes_wallpapers.py does: -h/--help prints this note and writes nothing, a leading dash is not a
    folder, and an empty word or a path that is a file is named instead of ending in a traceback."""
    if argv in (["-h"], ["--help"]):
        print((__doc__ or USAGE).strip())
        sys.exit(0)
    if len(argv) != 1:
        print(USAGE, file=sys.stderr)
        sys.exit(2)
    out = argv[0]
    if not out:
        print(f"blocky_wallpapers.py: the folder to write the scenes into was given as an empty argument.\n{USAGE}",
              file=sys.stderr)
        sys.exit(2)
    if out.startswith("-"):
        print(f"blocky_wallpapers.py takes one folder and no options, so {out} is not one.\n{USAGE}",
              file=sys.stderr)
        sys.exit(2)
    if os.path.exists(out) and not os.path.isdir(out):
        print(f"blocky_wallpapers.py: {out} is a file, and the scenes need a folder", file=sys.stderr)
        sys.exit(2)
    return out


def write(out):
    """The 11 PNGs into `out`; a missing Pillow, a folder it cannot create or a file it cannot write is named."""
    try:
        import PIL  # noqa: F401
    except ImportError:
        print("blocky_wallpapers.py needs Pillow: tools/venv/bin/pip install -r tools/requirements-perf.txt. "
              "Nothing was written.", file=sys.stderr)
        sys.exit(2)
    try:
        os.makedirs(out, exist_ok=True)
    except OSError as e:
        print(f"blocky_wallpapers.py: cannot create {out}: {e.strerror or e}. Nothing was written.", file=sys.stderr)
        sys.exit(2)
    for done, (name, draw) in enumerate(SCENES):
        path = os.path.join(out, f"{name}.png")
        cv = Canvas(done + 1)
        draw(cv)
        try:
            cv.img.save(path, optimize=True)
        except OSError as e:
            print(f"blocky_wallpapers.py: cannot write {path}: {e.strerror or e}", file=sys.stderr)
            print(f"stopped after {done} of {len(SCENES)} scenes, which are left in {out} as they are.",
                  file=sys.stderr)
            sys.exit(2)
    print(len(SCENES), "scenes written to", out)


if __name__ == "__main__":
    write(target(sys.argv[1:]))
