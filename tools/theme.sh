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
  /*) THEME_ARG="$(cd "$THEME_ARG" 2>/dev/null && { pwd -W 2>/dev/null || pwd; } || printf '%s' "$THEME_ARG")" ;;
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
# Restart Home so it rereads the theme. Android drops a force-stopped app's accessibility services from the
# enabled list, which would switch off InputKeyService/HomeKeyService, so put the list back if it had ours.
# Android removes them asynchronously while it handles the restart, so wait a second before writing the
# list back. Writing the same list back is not enough: the force-stop also turns accessibility_enabled off,
# and the service only binds again once the list changes. So restart first, then clear the list, write it
# back and switch accessibility on again as build-and-install does. Some TVs (TCL Smart TV Pro G03, Android 11)
# also keep the service in their "crashed" list after the force-stop and leave it unbound until accessibility
# itself goes off and on again, so switch it off with the list cleared and wait a second before writing back.
# By then Android has already emptied the list and switched accessibility off (Xiaomi MiTV-MOOR2, Android 11), so
# clearing it changed nothing and the services stayed crashed: write the list back first, so the clear is a change.
# When the launcher is already Home, it comes back through the Home intent: `am start -n` from the shell brings it
# back in an ordinary task, and the next Home press starts a second MainActivity next to it. Otherwise (a theme
# pushed before the handover) it is started by name as before, so the new look still shows.
RESTART="a=\$(settings get secure enabled_accessibility_services); am force-stop $PKG; case \"\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)\" in $PKG/*) am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null ;; *) am start -n $PKG/.MainActivity >/dev/null ;; esac; case \"\$a\" in *$PKG*) sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1; sleep 1; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1 ;; esac"

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
  tv "mkdir -p $DIR 2>/dev/null; $(del config.json); $(del wallpaper.jpg); $(del wallpapers/captions.txt); $CLEAR_WALLPAPERS; printf '%s' '$REVISION' > $TMP-theme_revision; $(put theme_revision); $RESTART; echo reset" | tail -n 1
  exit 0
fi
THEME="${THEME_ARG:?usage: theme.sh <theme folder> | --reset}"
[ -f "$THEME/config.json" ] || { echo "no config.json in $THEME" >&2; exit 1; }
"$PY" -I -c 'import json,sys; json.load(open(sys.argv[1], encoding="utf-8-sig"))' "$THEME/config.json" 2>/dev/null \
  || { echo "$THEME/config.json is not valid JSON" >&2; exit 1; }

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
if [ -d "$THEME/wallpapers" ]; then
  for IMAGE in "$THEME"/wallpapers/*; do
    [ -f "$IMAGE" ] || continue
    case "$IMAGE" in
      *.[jJ][pP][gG]|*.[jJ][pP][eE][gG]|*.[pP][nN][gG]|*.[wW][eE][bB][pP])
        NAME=$(basename "$IMAGE")
        [[ "$NAME" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "unsupported wallpaper filename: $NAME" >&2; exit 1; }
        "$PY" -I b64push.py "$IMAGE" "$(stage "wallpapers/$NAME")"
        tv "$(put "wallpapers/$NAME")" >/dev/null ;;
    esac
  done
fi
# The collection's names (wallpaper_captions); a theme without them leaves no stale names behind.
if [ -f "$THEME/wallpapers/captions.txt" ]; then
  "$PY" -I b64push.py "$THEME/wallpapers/captions.txt" "$(stage wallpapers/captions.txt)"
  tv "$(put wallpapers/captions.txt)" >/dev/null
else
  tv "$(del wallpapers/captions.txt)" >/dev/null
fi
tv "printf '%s' '$REVISION' > $TMP-theme_revision; $(put theme_revision)" >/dev/null
tv "$RESTART; echo applied" | tail -n 1
echo "Theme $(basename "$THEME") is on the TV. Undo with: tools/theme.sh --reset"
