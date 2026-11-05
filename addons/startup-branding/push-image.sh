#!/usr/bin/env bash
# Send pictures (PNG, JPEG or WebP, up to 15 MB each) to Startup & Branding on the TV, then pick one in
# Custom Image. Install the add-on first with install.sh.
#   TV_HOST=<tv ip> addons/startup-branding/push-image.sh logo.png [more.jpg ...]
#   TV_HOST=<tv ip> addons/startup-branding/push-image.sh --delete logo.png
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
[ $# -ge 1 ] || { sed -n 2,5p "$0" >&2; exit 1; }
HERE="$(pwd -W 2>/dev/null || pwd)"
cd "$(dirname "$0")/../../tools"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.maxhayim.startupbranding
URI=content://$PKG.pictures
TMP=/data/local/tmp/startup-branding-picture
tv() { "$PY" -I tv.py "$@"; }
tv "pm path $PKG; true" | grep -q package: \
  || { echo "Startup & Branding is not on the TV: run addons/startup-branding/install.sh first" >&2; exit 1; }

if [ "$1" = "--delete" ]; then
  shift
  for NAME in "$@"; do
    [[ "$NAME" =~ ^[A-Za-z0-9._-]+\.(png|jpg|jpeg|webp|PNG|JPG|JPEG|WEBP)$ ]] || { echo "not a picture name: $NAME" >&2; exit 1; }
    tv "content delete --uri $URI/$NAME" >/dev/null && echo "deleted $NAME"
  done
  exit 0
fi

for F in "$@"; do
  case "$F" in /*|?:*) ;; *) F="$HERE/$F" ;; esac
  [ -f "$F" ] || { echo "no such file: $F" >&2; exit 1; }
  NAME=$(basename "$F")
  # The TV only takes plain names; anything else becomes _.
  SAFE=$(printf '%s' "$NAME" | tr -c 'A-Za-z0-9._-' '_')
  [[ "$SAFE" =~ \.(png|jpg|jpeg|webp|PNG|JPG|JPEG|WEBP)$ ]] || { echo "$NAME: PNG, JPEG or WebP only" >&2; exit 1; }
  SIZE=$(wc -c < "$F" | tr -d ' ')
  [ "$SIZE" -le 15728640 ] || { echo "$NAME is over 15 MB" >&2; exit 1; }
  "$PY" -I b64push.py "$F" "$TMP" >/dev/null || { echo "sending $NAME failed" >&2; exit 1; }
  tv "content write --uri $URI/$SAFE < $TMP; rm -f $TMP" >/dev/null
  echo "sent $SAFE"
done
echo "Pick it on the TV: Startup & Branding, Custom Image, Picture."
