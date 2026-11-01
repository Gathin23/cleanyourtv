#!/usr/bin/env bash
# Push a theme (a folder with config.json, and optionally a wallpaper.jpg or a wallpapers/ collection) to the
# launcher on the TV and restart Home so it picks it up. The launcher reads the files from its external files
# directory and overlays config.json on its built-in config.xml; see themes/README.md for the keys.
#
# Usage: TV_HOST=192.168.1.50 tools/theme.sh themes/aurora
#        TV_HOST=192.168.1.50 tools/theme.sh --reset        # remove the overlay, back to config.xml
#
# One argument, and --reset takes none: a second word is refused rather than dropped (see below). Exit status:
# 0 the theme is on the TV, or the reset ran; 1 the theme could not be pushed, or Home did not restart after
# it; 2 the run stopped before the theme was up - the command line is not one theme folder or --reset (a word
# that starts with a dash is not a folder name, -h and --help included), TV_HOST is unset or empty, the checkout
# has no tools/venv, or the TV was not reached (tv.py's and theme_restart.py's own message says which).
set -euo pipefail
THEME_ARG="${1:-}"
# A theme folder or --reset, and nothing else. The first word alone decided everything the script did, so
# `theme.sh --reset themes/aurora` matched the reset branch on that word and threw the folder away: the theme
# the reader named was removed from the TV instead of pushed, and the run answered "reset" with exit 0 (the
# undo hint this script prints is what people copy, so appending the folder to it is an easy line to type).
# The same line the other way round, `theme.sh themes/aurora --reset`, pushed the theme and ignored the
# --reset the reader asked for. Both are answered here, before TV_HOST is required and before anything is
# sent: a command line this script cannot read should not reach a TV.
if [ -z "$THEME_ARG" ]; then
  echo "usage: theme.sh <theme folder> | --reset" >&2
  exit 2
fi
if [ "$#" -gt 1 ]; then
  shift
  echo "theme.sh takes one argument, a theme folder or --reset: $* would not have been used, and --reset" \
    "removes the theme from the TV while a folder pushes it, so the two cannot share a command line." \
    "Nothing was sent to the TV." >&2
  exit 2
fi
# A word that starts with a dash is an option to the reader who types it, and this script has one: --reset.
# Left to the path below, `--help` (or `--resett`, one letter too many) was resolved against the caller's
# directory and the run named a config.json nobody wrote - "no config.json in <cwd>/--help", exit 1, the status
# the header keeps for a theme that could not be pushed, so a run that sent nothing read as one that had. No
# folder a theme lives in starts with a dash, and this script sends nothing for a command line it cannot read:
# answer 2 with the usage, before TV_HOST is required and before anything is sent.
case "$THEME_ARG" in
  -*) if [ "$THEME_ARG" != "--reset" ]; then
        echo "theme.sh takes a theme folder or --reset, so $THEME_ARG is not a command line it can read." \
          "Nothing was sent to the TV." >&2
        echo "usage: theme.sh <theme folder> | --reset" >&2
        exit 2
      fi ;;
esac
# Resolve the theme folder against the caller's directory before moving into tools/.
# Git Bash's pwd says /c/Users/..., which the Windows Python cannot open; pwd -W gives C:/Users/... (as in disable.sh).
# An absolute argument needs the same: /c/Users/... passed through as typed reached the JSON check below, which
# could not open it and called the file "not valid JSON". A folder that does not exist keeps the path as typed,
# so the "no config.json" message names what the caller gave.
case "$THEME_ARG" in
  --reset|"") ;;
  /*|[A-Za-z]:[\\/]*) THEME_ARG="$(cd "$THEME_ARG" 2>/dev/null && { pwd -W 2>/dev/null || pwd; } || printf '%s' "$THEME_ARG")" ;;
  *) THEME_ARG="$(pwd -W 2>/dev/null || pwd)/$THEME_ARG" ;;
esac
cd "$(dirname "$0")"
# The address is the reader's to give, and a missing one is not a theme that could not be pushed: bash's own
# `${TV_HOST:?}` answered it with "tools/theme.sh: line N: TV_HOST: ..." and exit 1 - the status the header
# keeps for a push that ran and failed - so a run that never started read as one that had. Judged here, before
# the client is chosen, in this script's own words and with the status the header gives a run that stopped
# before the theme was up. An empty TV_HOST is the same mistake as an unset one.
if [ -z "${TV_HOST:-}" ]; then
  echo "set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 themes/aurora." \
    "Nothing was sent to the TV." >&2
  exit 2
fi
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
# The venv is on this computer, so a checkout without one is another run that stopped before the theme was up:
# it answered 1, the status for a push that ran and failed, while theme_restart.py's own missing-venv answer is 2.
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt." \
  "Nothing was sent to the TV." >&2; exit 2; }
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
# `content` also reports an exception from the provider as text - no provider in the launcher, or the launcher
# getting EACCES - so "Error while accessing provider" is a failure too, and cp runs instead of the file going.
# A folder is the shell's when the shell makes it (drwxrws--- shell ext_data_rw), and the provider, which runs as the
# launcher, then gets EACCES writing into it. Nothing makes wallpapers/ up front: the provider creates it with the
# first picture, and only the cp fallback, where the shell does the writing, runs mkdir.
put() { echo "rm -f $DIR/$1 2>/dev/null; { o=\$(content write --uri $URI/$1 < $(stage "$1") 2>&1) && case \"\$o\" in *'Unsupported operation'*|*'Error while accessing provider'*) false ;; esac; } || { mkdir -p $(dirname "$DIR/$1") 2>/dev/null; cp $(stage "$1") $DIR/$1 2>/dev/null; }; rm -f $(stage "$1")"; }
del() { echo "rm -f $DIR/$1 2>/dev/null; content delete --uri $URI/$1 2>/dev/null"; }

# The collection a theme can push. A push empties it before writing the theme's own pictures, and --reset has to
# empty it too: the launcher's wallpaper picker (LauncherSettingsActivity.showWallpaperPicker) lists that folder
# itself, and an on-TV pick of one of its pictures is read back from it (WallpaperRotation.chosen), so a reset
# that left the pictures behind still shows the theme it just removed. Both paths use this one string.
CLEAR_WALLPAPERS="rm -f $DIR/wallpapers/*.[jJ][pP][gG] $DIR/wallpapers/*.[jJ][pP][eE][gG] $DIR/wallpapers/*.[pP][nN][gG] $DIR/wallpapers/*.[wW][eE][bB][pP] 2>/dev/null; content delete --uri $URI/wallpapers 2>/dev/null"

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
