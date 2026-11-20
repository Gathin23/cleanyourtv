import math, sys
from PIL import Image, ImageDraw, ImageFont

FONT = sys.argv[1] if len(sys.argv) > 1 else ""
OUT = sys.argv[2] if len(sys.argv) > 2 else ""
SS = 4

def font(px, weight):
    f = ImageFont.truetype(FONT, px)
    try: f.set_variation_by_axes([weight])
    except Exception: pass
    return f

def gradient(w, h, top, bottom):
    im = Image.new("RGB", (w, h)); px = im.load()
    for y in range(h):
        for x in range(w):
            t = min(1.0, 0.35 * x / w + 0.65 * y / h)
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(top, bottom))
    return im

def sparkle(d, cx, cy, r, color, k=0.28):
    pts = []
    for i in range(8):
        a = math.pi / 4 * i - math.pi / 2
        rr = r if i % 2 == 0 else r * k
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=color)

# Glyphs draw inside a square (x, y, s).
def glyph_sparkle(d, x, y, s, fg, accent):
    sw = s * 0.075; w, h = s * 0.80, s * 0.56
    l, t = x + (s - w) / 2, y + s * 0.14
    d.rounded_rectangle([l, t, l + w, t + h], radius=s * 0.11, outline=fg, width=round(sw))
    cx = x + s / 2
    d.rounded_rectangle([cx - s * .17, t + h + s * .09, cx + s * .17, t + h + s * .09 + sw], radius=sw / 2, fill=fg)
    sparkle(d, cx + s * .02, t + h * .5, s * .17, accent)
    sparkle(d, cx + s * .20, t + h * .27, s * .065, accent)
    sparkle(d, cx - s * .20, t + h * .70, s * .045, fg)

def glyph_antenna(d, x, y, s, fg, accent):
    sw = s * 0.07; w, h = s * 0.78, s * 0.52
    l, t = x + (s - w) / 2, y + s * 0.30
    cx = x + s / 2
    for dx in (-1, 1):
        d.line([cx + dx * s * .05, t, cx + dx * s * .20, t - s * .19], fill=fg, width=round(sw * .8))
        r = s * .045; ex, ey = cx + dx * s * .20, t - s * .19
        d.ellipse([ex - r, ey - r, ex + r, ey + r], fill=fg)
    d.rounded_rectangle([l, t, l + w, t + h], radius=s * .13, fill=fg)
    p = s * .075
    d.rounded_rectangle([l + p, t + p, l + w - p, t + h - p], radius=s * .07, fill=accent)
    sparkle(d, cx, t + h / 2, s * .14, fg)
    for dx in (-1, 1):
        d.rounded_rectangle([cx + dx * s * .2 - s * .06, t + h + s * .02, cx + dx * s * .2 + s * .06, t + h + s * .07], radius=s * .025, fill=fg)

def glyph_check(d, x, y, s, fg, accent):
    sw = s * 0.08; w, h = s * 0.80, s * 0.58
    l, t = x + (s - w) / 2, y + s * 0.13
    d.rounded_rectangle([l, t, l + w, t + h], radius=s * .12, outline=fg, width=round(sw))
    cx = x + s / 2
    d.rounded_rectangle([cx - s * .17, t + h + s * .08, cx + s * .17, t + h + s * .08 + sw], radius=sw / 2, fill=fg)
    pts = [(cx - s * .17, t + h * .52), (cx - s * .04, t + h * .70), (cx + s * .19, t + h * .30)]
    d.line(pts, fill=accent, width=round(s * .085), joint="curve")
    for px_, py_ in (pts[0], pts[2]):
        r = s * .0425; d.ellipse([px_ - r, py_ - r, px_ + r, py_ + r], fill=accent)

def glyph_play(d, x, y, s, fg, accent):
    # Rounded screen filled with accent, a play-like broom sweep: three bars of decreasing length.
    w, h = s * 0.80, s * 0.58
    l, t = x + (s - w) / 2, y + s * 0.13
    d.rounded_rectangle([l, t, l + w, t + h], radius=s * .14, fill=fg)
    bars = [(0.18, 0.62), (0.40, 0.48), (0.62, 0.34)]
    for fy, fw in bars:
        yy = t + h * fy; bh = h * 0.12
        d.rounded_rectangle([l + w * .14, yy, l + w * (.14 + fw), yy + bh], radius=bh / 2, fill=accent)
    sparkle(d, l + w * .80, t + h * .62, s * .10, accent)
    cx = x + s / 2; sw = s * .075
    d.rounded_rectangle([cx - s * .17, t + h + s * .08, cx + s * .17, t + h + s * .08 + sw], radius=sw / 2, fill=fg)

STYLES = {
    "A": dict(top=(11, 31, 58), bottom=(14, 92, 110), fg=(245, 248, 250), accent=(94, 234, 212), word=((245, 248, 250), (94, 234, 212)), glyph=glyph_sparkle, name="A  Gece mavisi, yıldız"),
    "B": dict(top=(21, 24, 30), bottom=(34, 38, 46), fg=(255, 255, 255), accent=(207, 255, 207), word=((255, 255, 255), (207, 255, 207)), glyph=glyph_antenna, name="B  Proje renkleri, antenli TV"),
    "C": dict(top=(248, 250, 252), bottom=(226, 232, 240), fg=(21, 24, 30), accent=(22, 163, 74), word=((21, 24, 30), (22, 163, 74)), glyph=glyph_check, name="C  Açık zemin, onay işareti"),
    "D": dict(top=(60, 6, 12), bottom=(18, 2, 6), fg=(255, 244, 240), accent=(255, 92, 92), word=((255, 244, 240), (255, 120, 110)), glyph=glyph_play, name="D  Ana ekran kırmızısı, süpürge çizgileri"),
}

def icon(st, size):
    S = size * SS; im = gradient(S, S, st["top"], st["bottom"]); d = ImageDraw.Draw(im)
    st["glyph"](d, S * .12, S * .10, S * .76, st["fg"], st["accent"] if st["glyph"] is not glyph_antenna else st["accent"])
    return im.resize((size, size), Image.LANCZOS)

def banner(st, w, h):
    W, H = w * SS, h * SS; im = gradient(W, H, st["top"], st["bottom"]); d = ImageDraw.Draw(im)
    g = H * .62; st["glyph"](d, W * .055, (H - g) / 2, g, st["fg"], st["accent"])
    f = font(round(H * .215), 600); a, b = "Clean", "YourTV"
    tx = W * .055 + g + W * .035; avail = W * .95 - tx
    wa, wb = d.textlength(a, font=f), d.textlength(b, font=f)
    if wa + wb > avail:
        f = font(round(H * .215 * avail / (wa + wb)), 600); wa = d.textlength(a, font=f)
    asc, desc = f.getmetrics(); ty = (H - (asc + desc)) / 2 + desc * .25
    d.text((tx, ty), a, font=f, fill=st["word"][0]); d.text((tx + wa, ty), b, font=f, fill=st["word"][1])
    return im.resize((w, h), Image.LANCZOS)

if __name__ == "__main__":
    for k, st in STYLES.items():
        icon(st, 192).save(f"{OUT}/{k}-ic_launcher.png"); banner(st, 320, 180).save(f"{OUT}/{k}-banner.png")
        icon(st, 512).save(f"{OUT}/{k}-icon-512.png"); banner(st, 960, 540).save(f"{OUT}/{k}-banner-960.png")

    # comparison sheet
    cw, ch = 960, 540; pad = 40; label = 70
    sheet = Image.new("RGB", (2 * (cw + 360) + 3 * pad, 2 * (ch + label) + 3 * pad), (240, 240, 240))
    d = ImageDraw.Draw(sheet); lf = font(40, 600)
    for i, (k, st) in enumerate(STYLES.items()):
        col, row = i % 2, i // 2
        x = pad + col * (cw + 360 + pad); y = pad + row * (ch + label + pad)
        d.text((x, y), st["name"], font=lf, fill=(20, 20, 20))
        sheet.paste(Image.open(f"{OUT}/{k}-banner-960.png"), (x, y + label))
        ic = Image.open(f"{OUT}/{k}-icon-512.png").resize((320, 320), Image.LANCZOS)
        sheet.paste(ic, (x + cw + 40, y + label + (ch - 320) // 2))
    sheet.save(f"{OUT}/secenekler.png")
