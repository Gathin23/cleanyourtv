#!/usr/bin/env bash
# Push a theme (a folder with config.json, and optionally a wallpaper.jpg or a wallpapers/ collection) to the
# launcher on the TV and restart Home so it picks it up. The launcher reads the files from its external files
# directory and overlays config.json on its built-in config.xml; see themes/README.md for the keys.
#
# Usage: TV_HOST=192.168.1.50 tools/theme.sh themes/aurora
#        TV_HOST=192.168.1.50 tools/theme.sh --reset        # remove the overlay, back to config.xml
set -euo pipefail
# Resolve the theme folder against the caller's directory before moving into tools/.
# Git Bash's pwd says /c/Users/..., which the Windows Python cannot open; pwd -W gives C:/Users/... (as in disable.sh).
# An absolute argument needs the same: /c/Users/... passed through as typed reached the JSON check below, which
# could not open it and called the file "not valid JSON". A folder that does not exist keeps the path as typed,
# so the "no config.json" message names what the caller gave.
THEME_ARG="${1:-}"
case "$THEME_ARG" in
  --reset|"") ;;
  /*|[A-Za-z]:[\\/]*) THEME_ARG="$(cd "$THEME_ARG" 2>/dev/null && { pwd -W 2>/dev/null || pwd; } || printf '%s' "$THEME_ARG")" ;;
  *) THEME_ARG="$(pwd -W 2>/dev/null || pwd)/$THEME_ARG" ;;
esac
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 themes/aurora}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
# A unique marker lets the launcher clear on-TV layout/motion choices after every theme push,
# including a push of identical config.json bytes and --reset.
REVISION="$("$PY" -I -c 'import uuid; print(uuid.uuid4().hex)')"
tv() { "$PY" -I tv.py "$@"; }
# One shared recovery path verifies the helpers after Home restarts.


# Some firmware (TCL G07, Android 11) locks the shell out of Android/data. Files are staged in
# /data/local/tmp and copied in, or written through the launcher's ThemeProvider when that is refused.
# The provider has to be tried first: on Android 14 the shell can write into Android/data, so cp "succeeds",
# but the file it creates belongs to the shell (uid 2000) and the launcher then gets EACCES opening it, which
# silently ignores the theme whenever the file did not exist yet - a fresh install, or after a reinstall.
# A provider write creates the file as the launcher, and it cannot truncate a file the shell owns either, so
# remove our copy first. cp stays as the fallback for firmware without the provider.
URI=content://$PKG.theme
TMP=/data/local/tmp/cyt-theme
# A file's staging path; wallpapers/a.jpg is staged as $TMP-wallpapers-a.jpg.
stage() { echo "$TMP-${1//\//-}"; }
# Android 8 and 9 have no `content write`: they print the usage and "[ERROR] Unsupported operation: write" but
# exit 0, so the exit status alone would skip cp and leave the file deleted. Treat that message as a failure.
put() { echo "rm -f $DIR/$1 2>/dev/null; { o=\$(content write --uri $URI/$1 < $(stage "$1") 2>&1) && case \"\$o\" in *'Unsupported operation'*) false ;; esac; } || cp $(stage "$1") $DIR/$1 2>/dev/null; rm -f $(stage "$1")"; }
del() { echo "rm -f $DIR/$1 2>/dev/null; content delete --uri $URI/$1 2>/dev/null"; }

# The collection a theme can push. A push empties it before writing the theme's own pictures, and --reset has to
# empty it too: the launcher's wallpaper picker (LauncherSettingsActivity.showWallpaperPicker) lists that folder
# itself, and an on-TV pick of one of its pictures is read back from it (WallpaperRotation.chosen), so a reset
# that left the pictures behind still shows the theme it just removed. Both paths use this one string.
CLEAR_WALLPAPERS="mkdir -p $DIR/wallpapers 2>/dev/null; rm -f $DIR/wallpapers/*.[jJ][pP][gG] $DIR/wallpapers/*.[jJ][pP][eE][gG] $DIR/wallpapers/*.[pP][nN][gG] $DIR/wallpapers/*.[wW][eE][bB][pP] 2>/dev/null; content delete --uri $URI/wallpapers 2>/dev/null"

if [ "${1:-}" = --reset ]; then
  tv "mkdir -p $DIR 2>/dev/null; $(del config.json); $(del wallpaper.jpg); $(del wallpapers/captions.txt); $CLEAR_WALLPAPERS; printf '%s' '$REVISION' > $TMP-theme_revision; $(put theme_revision)" >/dev/null
  "$PY" -I theme_restart.py
  echo reset
  exit 0
fi
THEME="${THEME_ARG:?usage: theme.sh <theme folder> | --reset}"
[ -f "$THEME/config.json" ] || { echo "no config.json in $THEME" >&2; exit 1; }
# What the launcher makes of the file: Config.read() answers "no overlay" for anything that is not a JSON
# object, so an array, a number or a string is a theme the TV drops - and it replaces the theme the set had,
# which puts the built-in config.xml back. Reading the shape here, before anything is pushed, keeps a run
# that ends "is on the TV" from meaning the opposite.
theme_json_kind() {
  "$PY" -I -c '
import json, sys
try:
    with open(sys.argv[1], encoding="utf-8-sig") as handle:
        theme = json.load(handle)
except (OSError, ValueError):
    print("invalid")
else:
    print("object" if isinstance(theme, dict) else "not an object")
' "$1" 2>/dev/null
}
case "$(theme_json_kind "$THEME/config.json")" in
  object) ;;
  "not an object") echo "$THEME/config.json is not a JSON object of theme keys, so the launcher would ignore" \
    "it and the TV would go back to its built-in config (themes/README.md). Nothing was sent to the TV." >&2
    exit 1 ;;
  *) echo "$THEME/config.json is not valid JSON" >&2; exit 1 ;;
esac
# Every collection name is checked before anything changes on the TV: a name refused halfway through the push
# left the new config.json and part of the collection behind. b64push.py refuses a path with '..' in it, so
# such a name (sunset..jpg) is refused here too. The names that pass have no spaces, so NAMES splits on them.
NAMES=""
if [ -d "$THEME/wallpapers" ]; then
  for IMAGE in "$THEME"/wallpapers/*; do
    [ -f "$IMAGE" ] || continue
    case "$IMAGE" in
      *.[jJ][pP][gG]|*.[jJ][pP][eE][gG]|*.[pP][nN][gG]|*.[wW][eE][bB][pP])
        NAME=$(basename "$IMAGE")
        [[ "$NAME" =~ ^[A-Za-z0-9_.-]+$ && "$NAME" != *..* ]] || { echo "unsupported wallpaper filename: $NAME" >&2; exit 1; }
        NAMES="$NAMES $NAME" ;;
    esac
  done
fi

tv "mkdir -p $DIR" >/dev/null 2>&1 || true
tv --push "$THEME/config.json" "$TMP-config.json" >/dev/null
tv "$(put config.json)"
if [ -f "$THEME/wallpaper.jpg" ]; then
  "$PY" -I b64push.py "$THEME/wallpaper.jpg" "$TMP-wallpaper.jpg"
  tv "$(put wallpaper.jpg)"
else
  tv "$(del wallpaper.jpg)" >/dev/null
fi
tv "$CLEAR_WALLPAPERS" >/dev/null
for NAME in $NAMES; do
  "$PY" -I b64push.py "$THEME/wallpapers/$NAME" "$(stage "wallpapers/$NAME")"
  tv "$(put "wallpapers/$NAME")" >/dev/null
done
# The collection's names (wallpaper_captions); a theme without them leaves no stale names behind.
if [ -f "$THEME/wallpapers/captions.txt" ]; then
  "$PY" -I b64push.py "$THEME/wallpapers/captions.txt" "$(stage wallpapers/captions.txt)"
  tv "$(put wallpapers/captions.txt)" >/dev/null
else
  tv "$(del wallpapers/captions.txt)" >/dev/null
fi
tv "printf '%s' '$REVISION' > $TMP-theme_revision; $(put theme_revision)" >/dev/null
"$PY" -I theme_restart.py
echo applied
echo "Theme $(basename "$THEME") is on the TV. Undo with: tools/theme.sh --reset"
