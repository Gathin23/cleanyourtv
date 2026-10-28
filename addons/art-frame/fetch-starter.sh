#!/usr/bin/env bash
# Download the starter paintings listed in app/src/main/assets/starter/starter.json into that folder, so the
# app shows art offline from its first start. All are public domain (CC0) from the Art Institute of Chicago and
# the Cleveland Museum of Art, fetched from the museums' own servers. Files already there are kept.
# Each picture is made to fit 3840x2160 at JPEG quality 82 with sips (macOS) or Pillow when either is present;
# otherwise it is kept as downloaded and the app fits it on import.
#
# Usage: addons/art-frame/fetch-starter.sh        (build-and-install.sh runs it for you)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DIR="$HERE/app/src/main/assets/starter"
PY=python3
command -v "$PY" >/dev/null || PY=python
"$PY" -I - "$DIR" <<'EOF'
import json, os, shutil, subprocess, sys, tempfile, urllib.request

folder = sys.argv[1]
items = json.load(open(os.path.join(folder, "starter.json"), encoding="utf-8"))["artworks"]
headers = {"User-Agent": "art-frame (cleanyourtv add-on)", "AIC-User-Agent": "art-frame (cleanyourtv add-on)"}

def fit(src, dst):
    if shutil.which("sips"):
        out = subprocess.run(["sips", "-g", "pixelWidth", "-g", "pixelHeight", src], capture_output=True, text=True).stdout
        w, h = [int(l.split()[-1]) for l in out.splitlines()[1:3]]
        side = ["--resampleHeight", "2160"] if h * 3840 > w * 2160 else ["--resampleWidth", "3840"]
        if w > 3840 or h > 2160:
            subprocess.run(["sips", *side, "-s", "format", "jpeg", "-s", "formatOptions", "82", src, "--out", dst],
                           check=True, capture_output=True)
            return
    else:
        try:
            from PIL import Image
            with Image.open(src) as im:
                im.thumbnail((3840, 2160))
                im.convert("RGB").save(dst, "JPEG", quality=82)
            return
        except ImportError:
            pass
    shutil.copyfile(src, dst)

for item in items:
    dst = os.path.join(folder, item["file"])
    if os.path.isfile(dst) and os.path.getsize(dst) > 0:
        print("have", item["file"])
        continue
    req = urllib.request.Request(item["download"], headers=headers)
    with urllib.request.urlopen(req, timeout=180) as r, tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
        shutil.copyfileobj(r, tmp)
    try:
        fit(tmp.name, dst)
    finally:
        os.unlink(tmp.name)
    print("got ", item["file"], os.path.getsize(dst), "bytes")
EOF
