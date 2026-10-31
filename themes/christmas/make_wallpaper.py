#!/usr/bin/env python3
"""Draws wallpaper.jpg: a snowy Christmas night, our own illustration, released CC0 1.0.

Usage: python3 make_wallpaper.py   (needs Pillow; writes wallpaper.jpg next to this file)
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

W, H, S = 1920, 1080, 2  # drawn at 2x, saved at 1920x1080
rnd = random.Random(24)


def s(v):
    return int(v * S)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# sky: deep blue to teal glow at the horizon
img = Image.new("RGB", (W * S, H * S))
d = ImageDraw.Draw(img)
top, hor = (5, 9, 28), (22, 48, 74)
for y in range(H * S):
    d.line([(0, y), (W * S, y)], fill=lerp(top, hor, min(1, y / (H * S * 0.68)) ** 1.4))

# stars
for _ in range(170):
    x, y, r = rnd.uniform(0, W), rnd.uniform(0, 560), rnd.choice([1, 1, 1.5, 2])
    c = rnd.randint(150, 235)
    d.ellipse([s(x - r), s(y - r), s(x + r), s(y + r)], fill=(c, c, 245))

# moon with halo
halo = Image.new("RGB", img.size, (0, 0, 0))
hd = ImageDraw.Draw(halo)
mx, my = 1480, 380
hd.ellipse([s(mx - 260), s(my - 260), s(mx + 260), s(my + 260)], fill=(40, 60, 85))
halo = halo.filter(ImageFilter.GaussianBlur(s(90)))
img = Image.blend(img, Image.composite(Image.new("RGB", img.size, (120, 150, 190)), img, halo.convert("L")), 0.45)
d = ImageDraw.Draw(img)
d.ellipse([s(mx - 105), s(my - 105), s(mx + 105), s(my + 105)], fill=(236, 240, 226))
for cx, cy, r in [(-35, -25, 22), (30, 20, 30), (-10, 50, 14)]:
    d.ellipse([s(mx + cx - r), s(my + cy - r), s(mx + cx + r), s(my + cy + r)], fill=(214, 222, 210))


def hill(base, amp, ph, col):
    pts = [(0, H * S)]
    for x in range(0, W * S + 8, 8):
        pts.append((x, s(base + amp * math.sin(x / S / 300 + ph) + amp * 0.4 * math.sin(x / S / 110 + ph * 2))))
    pts.append((W * S, H * S))
    d.polygon(pts, fill=col)


hill(700, 28, 1.0, (30, 52, 78))  # far hill
hill(780, 24, 3.0, (22, 38, 62))  # near hill


def fir(x, base, h, lit):
    w = h * 0.36
    d.rectangle([s(x - w * 0.06), s(base - h * 0.08), s(x + w * 0.06), s(base)], fill=(18, 14, 20))
    tiers = 5
    for i in range(tiers):
        ty = base - h * 0.06 - i * h * 0.17
        tw = w * (1 - i * 0.17)
        th = h * 0.30
        d.polygon([(s(x), s(ty - th)), (s(x - tw), s(ty)), (s(x + tw), s(ty))], fill=(11, 38, 40))
        # snow on the upper edge of each tier
        d.polygon([(s(x), s(ty - th)), (s(x - tw * 0.45), s(ty - th * 0.55)), (s(x + tw * 0.45), s(ty - th * 0.55))],
                  fill=(150, 170, 190))
    if lit:  # string lights
        cols = [(255, 90, 80), (255, 200, 80), (110, 200, 255), (140, 235, 130)]
        k = 0
        for i in range(tiers):
            ty = base - h * 0.06 - i * h * 0.17
            tw = w * (1 - i * 0.17)
            for j in range(5):
                t = (j + 0.5) / 5
                lx = x - tw * 0.85 + t * tw * 1.7
                ly = ty - h * 0.02 + math.sin(t * math.pi) * h * 0.035
                c = cols[k % 4]
                k += 1
                for rr, a in [(9, 0.18), (5, 0.4), (3, 1)]:
                    ov = Image.new("RGB", img.size)
                    ImageDraw.Draw(ov).ellipse([s(lx - rr), s(ly - rr), s(lx + rr), s(ly + rr)], fill=c)
                    mask = Image.new("L", img.size)
                    ImageDraw.Draw(mask).ellipse([s(lx - rr), s(ly - rr), s(lx + rr), s(ly + rr)], fill=int(255 * a))
                    img.paste(ov, mask=mask)
        sx, sy = x, base - h * 0.06 - (tiers - 1) * h * 0.17 - h * 0.30
        d.polygon([(s(sx + 14 * math.sin(a * 2 * math.pi / 5)), s(sy - 14 * math.cos(a * 2 * math.pi / 5 + 0))) for a in range(5)],
                  fill=(255, 215, 110))


# background firs (unlit, small), then the big lit ones
for x, b, h in [(1030, 735, 150), (1090, 745, 190), (1760, 765, 210), (1850, 770, 150), (1500, 730, 120)]:
    fir(x, b, h, False)

# cabin
cx0, cx1, cy = 1180, 1420, 790
d.rectangle([s(cx0), s(cy - 110), s(cx1), s(cy)], fill=(34, 24, 34))
d.polygon([(s(cx0 - 22), s(cy - 105)), (s(cx0 + 120), s(cy - 190)), (s(cx1 + 22), s(cy - 105))], fill=(26, 40, 62))
d.polygon([(s(cx0 - 22), s(cy - 105)), (s(cx0 + 120), s(cy - 190)), (s(cx1 + 22), s(cy - 105)),
           (s(cx1 + 5), s(cy - 112)), (s(cx0 + 120), s(cy - 178)), (s(cx0 - 5), s(cy - 112))], fill=(205, 220, 235))
d.rectangle([s(cx1 - 70), s(cy - 240), s(cx1 - 40), s(cy - 150)], fill=(40, 28, 38))
glow = Image.new("RGB", img.size)
gd = ImageDraw.Draw(glow)
for wx in (cx0 + 30, cx0 + 110):
    gd.rectangle([s(wx - 10), s(cy - 85), s(wx + 70), s(cy - 25)], fill=(255, 160, 50))
gd.rectangle([s(cx1 - 55), s(cy - 80), s(cx1 - 15), s(cy)], fill=(255, 160, 50))
img = Image.blend(img, Image.composite(Image.new("RGB", img.size, (255, 150, 60)), img, glow.filter(ImageFilter.GaussianBlur(s(34))).convert("L")), 0.35)
d = ImageDraw.Draw(img)
for wx in (cx0 + 30, cx0 + 110):
    d.rectangle([s(wx), s(cy - 75), s(wx + 60), s(cy - 35)], fill=(255, 200, 100))
    d.line([s(wx + 30), s(cy - 75), s(wx + 30), s(cy - 35)], fill=(150, 90, 40), width=s(3))
d.rectangle([s(cx1 - 50), s(cy - 72), s(cx1 - 20), s(cy)], fill=(255, 190, 90))
# snow on the ground under the cabin
d.ellipse([s(cx0 - 80), s(cy - 6), s(cx1 + 80), s(cy + 28)], fill=(40, 62, 92))

fir(1640, 800, 330, True)
fir(1020, 820, 260, True)
fir(1780, 840, 190, True)

# Santa's sleigh and reindeer, in the empty sky on the left
# flying sleigh in the empty left sky
def reindeer(x, y, k, nose=False):
    c = (112, 74, 66)
    def P(pts): return [(s(x + a * k), s(y + b * k)) for a, b in pts]
    d.ellipse(P([(-30, -12), (30, 14)]), fill=c)                      # body
    d.polygon(P([(18, -6), (34, -34), (44, -28), (30, 4)]), fill=c)   # neck
    d.ellipse(P([(32, -42), (56, -26)]), fill=c)                      # head
    d.polygon(P([(52, -34), (68, -30), (54, -26)]), fill=c)           # muzzle
    for lx, ly, ex, ey in [(-22, 8, -46, 26), (-10, 10, -30, 32), (14, 10, 40, 22), (24, 8, 52, 14)]:
        d.line(P([(lx, ly), (ex, ey)]), fill=c, width=s(4 * k))       # legs stretched in the gallop
    d.line(P([(-30, -4), (-40, -12)]), fill=c, width=s(4 * k))        # tail
    for bx in (0, 10):                                                # antlers
        d.line(P([(40 + bx * 0.3, -40), (36 + bx, -62), (28 + bx, -74)]), fill=c, width=s(3 * k))
        d.line(P([(36 + bx, -62), (46 + bx, -72)]), fill=c, width=s(3 * k))
    if nose:
        nx, ny = x + 67 * k, y - 28 * k
        m = Image.new("L", img.size); ImageDraw.Draw(m).ellipse([s(nx - 22), s(ny - 22), s(nx + 22), s(ny + 22)], fill=255)
        m = m.filter(ImageFilter.GaussianBlur(s(9)))
        img.paste(Image.new("RGB", img.size, (255, 60, 50)), mask=m.point(lambda v: int(v * 0.8)))
        d.ellipse([s(nx - 5), s(ny - 5), s(nx + 5), s(ny + 5)], fill=(255, 90, 80))

def sleigh(x, y, k):
    red, dk = (170, 28, 40), (14, 20, 40)
    def P(pts): return [(s(x + a * k), s(y + b * k)) for a, b in pts]
    d.polygon(P([(-70, -30), (60, -30), (74, -52), (62, -52), (50, -12), (-60, -6)]), fill=red)  # body
    d.polygon(P([(-70, -30), (-80, -50), (-72, -50), (-58, -30)]), fill=red)
    d.line(P([(-76, 12), (70, 12), (92, -8)]), fill=(225, 190, 90), width=s(5 * k))               # runner
    d.line(P([(-50, 10), (-50, -8)]), fill=(225, 190, 90), width=s(4 * k))
    d.line(P([(30, 10), (30, -10)]), fill=(225, 190, 90), width=s(4 * k))
    d.ellipse(P([(-96, -66), (-44, -22)]), fill=(120, 80, 40))                                    # gift sack
    d.ellipse(P([(-20, -62), (22, -26)]), fill=red)                                               # Santa
    d.ellipse(P([(-4, -88), (20, -62)]), fill=(240, 205, 180))                                    # face
    d.ellipse(P([(-2, -76), (20, -56)]), fill=(240, 240, 245))                                    # beard
    d.polygon(P([(-6, -82), (6, -104), (24, -92), (22, -80)]), fill=red)                          # hat
    d.ellipse(P([(18, -100), (30, -88)]), fill=(240, 240, 245))
    d.rectangle(P([(-8, -84), (22, -80)]), fill=(240, 240, 245))

K = 1.5
lines = [(560, 400), (730, 380), (900, 360)]
sleigh(300, 430, 1.5)
pts = [(380, 420)] + [(rx, ry - 5) for rx, ry in lines]
for a, b in zip(pts, pts[1:]):
    d.line([(s(a[0]), s(a[1])), (s(b[0] - 45), s(b[1] + 6))], fill=(170, 150, 120), width=s(2))
for i, (rx, ry) in enumerate(lines):
    reindeer(rx, ry, K, nose=(i == 2))

# foreground snow
hill(905, 18, 0.5, (52, 78, 110))
hill(965, 14, 2.2, (34, 52, 80))

# darken bottom and top-left so the clock and the tiles stay readable
shade = Image.new("L", img.size, 0)
sd = ImageDraw.Draw(shade)
for y in range(H * S):
    t = max(0, (y / S - 700) / 380)
    sd.line([(0, y), (W * S, y)], fill=int(190 * t ** 1.2))
tl = Image.new("L", img.size, 0)
ImageDraw.Draw(tl).ellipse([-s(300), -s(300), s(900), s(500)], fill=110)
shade = Image.composite(shade, shade, shade)
img = Image.composite(Image.new("RGB", img.size, (3, 6, 16)), img, shade)
img = Image.composite(Image.new("RGB", img.size, (3, 6, 16)), img, tl.filter(ImageFilter.GaussianBlur(s(160))))

img = img.resize((W, H), Image.LANCZOS)
d = ImageDraw.Draw(img, "RGBA")
for _ in range(220):  # falling snow
    x, y, r = rnd.uniform(0, W), rnd.uniform(0, 1000), rnd.choice([1, 1.5, 2, 2, 3])
    d.ellipse([x - r, y - r, x + r, y + r], fill=(240, 246, 255, rnd.randint(110, 220)))

img.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), "wallpaper.jpg"), quality=84, optimize=True)
