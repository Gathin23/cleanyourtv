#!/usr/bin/env bash
# Put a folder of photos on the TV for Photo Prints. Replaces the photos already there.
# Each photo is converted to a JPEG no larger than 1280 px on its long edge first (sips, macOS), so HEIC from an
# iPhone works and the TV decodes a 200 KB file instead of a 5 MB one; a print is about 400 px on the screen, so
# nothing shows the difference. The date the photo was taken goes into the file name, and the print shows its
# month and year. Nothing is uploaded anywhere else: the files go straight to the app's own folder on the TV.
#
# Uses the repo's tools/tv.py and tools/b64push.py (set up as in the main README: tools/venv).
# Usage: TV_HOST=192.168.1.50 addons/photo-prints/photos.sh ~/Pictures/TV
#        TV_HOST=192.168.1.50 addons/photo-prints/photos.sh --clear
set -euo pipefail
SRC="${1:-}"
case "$SRC" in --clear|"") ;; /*) ;; *) SRC="$(pwd)/$SRC" ;; esac
cd "$(dirname "$0")/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 ~/Pictures/TV}"
PY=venv/bin/python
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
DIR=/sdcard/Android/data/io.github.shivamsingh262.photoprints/files/photos
tv() { "$PY" -I tv.py "$@"; }

if [ "$SRC" = --clear ]; then
  tv "rm -rf $DIR; echo cleared"
  exit 0
fi
[ -d "${SRC:?usage: photos.sh <folder> | --clear}" ] || { echo "not a folder: $SRC" >&2; exit 1; }
command -v sips >/dev/null || { echo "sips not found: this script converts photos with macOS's sips" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
N=0
while IFS= read -r -d '' FILE; do
  N=$((N + 1))
  # The day the photo was taken, from a date in the file name (20231202_092230.jpg, IMG-20260110-WA0025.jpg),
  # else from when the camera says it was taken. None found: the print shows no date.
  YMD=""
  if [[ "$(basename "$FILE")" =~ (20[0-9]{2})(0[1-9]|1[0-2])([0-3][0-9]) ]]; then
    YMD="-${BASH_REMATCH[1]}${BASH_REMATCH[2]}${BASH_REMATCH[3]}"
  else
    TAKEN="$(sips -g creation "$FILE" 2>/dev/null | awk '/creation:/{print $2}')"
    if [[ "$TAKEN" =~ ^([0-9]{4}):([0-9]{2}):([0-9]{2}) ]]; then
      YMD="-${BASH_REMATCH[1]}${BASH_REMATCH[2]}${BASH_REMATCH[3]}"
    fi
  fi
  OUT="$WORK/$(printf 'photo-%04d' "$N")$YMD.jpg"
  # -Z keeps the aspect ratio; the EXIF orientation tag is kept, and the app turns the photo upright.
  if ! sips -s format jpeg -s formatOptions 85 -Z 1280 "$FILE" --out "$OUT" >/dev/null 2>&1; then
    echo "skipped (not a photo sips can read): $FILE" >&2
    N=$((N - 1))
  fi
done < <(find "$SRC" -maxdepth 1 -type f \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' \
  -o -iname '*.heic' -o -iname '*.webp' \) -print0 | sort -z)
[ "$N" -gt 0 ] || { echo "no photos (jpg, jpeg, png, heic, webp) in $SRC" >&2; exit 1; }

tv "rm -rf $DIR; mkdir -p $DIR" >/dev/null
I=0
for OUT in "$WORK"/photo-*.jpg; do
  I=$((I + 1))
  printf '\r%d/%d %s' "$I" "$N" "$(basename "$OUT")"
  "$PY" -I b64push.py "$OUT" "$DIR/$(basename "$OUT")" >/dev/null
done
echo
echo "$N photos on the TV. Photo Prints picks them up at its next sweep."
