# Draws the fork's icon and banner (G3, emerald). Usage: python3 logo_final.py <path to outfit.ttf> <output folder>
import os, sys, importlib.util
from PIL import Image, ImageDraw
spec = importlib.util.spec_from_file_location("L", os.path.join(os.path.dirname(os.path.abspath(__file__)), "logo.py"))
L = importlib.util.module_from_spec(spec); spec.loader.exec_module(L)
OUT = sys.argv[2] if len(sys.argv) > 2 else "."
import os; os.makedirs(OUT, exist_ok=True)
G3 = dict(top=(21, 24, 30), bottom=(34, 38, 46), fg=(255, 255, 255), accent=(52, 211, 153),
          word=((255, 255, 255), (52, 211, 153)), glyph=L.glyph_antenna, star=(21, 24, 30), word_scale=.237)
L.icon(G3, 192).save(f"{OUT}/ic_launcher.png"); L.banner(G3, 320, 180).save(f"{OUT}/banner.png")
L.icon(G3, 512).save(f"{OUT}/icon-512.png"); L.banner(G3, 960, 540).save(f"{OUT}/banner-960.png")
