#!/usr/bin/env bash
# Make the photo screensaver the TV's screensaver and sync a folder of pictures and MP4 clips to it. Only new
# or changed files are sent and files no longer in the folder are removed from the TV, so a sync that broke
# off halfway is finished by running it again. Each picture stays up for SECONDS_PER_PICTURE (20 by default). Install the
# app first with build-and-install.sh.
#
# Usage: TV_HOST=192.168.1.50 addons/photo-screensaver/sync.sh                     # this folder's pictures/
#        TV_HOST=192.168.1.50 addons/photo-screensaver/sync.sh ~/Pictures/tv       # any folder
#        TV_HOST=192.168.1.50 SECONDS_PER_PICTURE=30 addons/photo-screensaver/sync.sh   # 30 s per picture
#        TV_HOST=192.168.1.50 IDLE_MINUTES=5 addons/photo-screensaver/sync.sh      # start it after 5 idle minutes
#        TV_HOST=192.168.1.50 START=1 addons/photo-screensaver/sync.sh             # and show it now
#        TV_HOST=192.168.1.50 addons/photo-screensaver/sync.sh --off               # previous screensaver back, pictures removed
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
FOLDER="${1:-$HERE/pictures}"
case "$FOLDER" in --off|/*|?:*) ;; *) FOLDER="$(pwd -W 2>/dev/null || pwd)/$FOLDER" ;; esac
cd "$(dirname "$0")/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.photodream
DREAM=$PKG/.PhotoDream
DIR=/sdcard/Android/data/$PKG/files/pictures
URI=content://$PKG.files/pictures
TMP=/data/local/tmp/cyt-screensaver
# The screensaver the TV had before the first run, so --off can put it back.
BEFORE=/data/local/tmp/cyt-screensaver-before
IDLE_MINUTES="${IDLE_MINUTES:-0}"
SECONDS_PER_PICTURE="${SECONDS_PER_PICTURE:-20}"
tv() { "$PY" -I tv.py "$@"; }
md5() { "$PY" -I -c 'import hashlib,sys; print(hashlib.md5(open(sys.argv[1],"rb").read()).hexdigest())' "$1"; }
checked_write() {
  local source="$1" uri="$2" seconds="${3:-}" out stage=""
  # tv.py loses the remote exit status, and Android's content write can print an error with exit 0.
  # Stage the timing value in this same remote shell and stop before the provider write if printf fails.
  [ -z "$seconds" ] || stage="if ! printf '%s' $seconds > $source; then rm -f $source; echo __PHOTO_STAGE_FAILED__; exit; fi; "
  out=$(tv "${stage}write_out=\$(content write --user 0 --uri $uri < $source 2>&1); write_rc=\$?; if rm -f $source && [ \"\$write_rc\" -eq 0 ] && [ -z \"\$write_out\" ]; then echo __PHOTO_WRITE_OK__; else printf '%s\n' \"\$write_out\"; fi")
  case "$out" in
    __PHOTO_WRITE_OK__) ;;
    __PHOTO_STAGE_FAILED__) echo "could not stage $source on the TV" >&2; return 1 ;;
    *) echo "$out" >&2; echo "could not write $uri on the TV" >&2; return 1 ;;
  esac
}

[[ "$IDLE_MINUTES" =~ ^[0-9]+$ ]] && [ "$IDLE_MINUTES" -le 1440 ] || { echo "IDLE_MINUTES takes 1 to 1440" >&2; exit 1; }
[[ "$SECONDS_PER_PICTURE" =~ ^[0-9]+$ ]] && [ "$SECONDS_PER_PICTURE" -ge 5 ] && [ "$SECONDS_PER_PICTURE" -le 3600 ] \
  || { echo "SECONDS_PER_PICTURE takes 5 to 3600" >&2; exit 1; }
tv "pm path $PKG; true" | grep -q package: \
  || { echo "the photo screensaver is not on the TV: run addons/photo-screensaver/build-and-install.sh first" >&2; exit 1; }

if [ "$FOLDER" = --off ]; then
  tv "if [ -f $BEFORE ]; then { read c; read e; } < $BEFORE; if [ \"\$c\" = null ]; then settings delete secure screensaver_components >/dev/null; else settings put secure screensaver_components \"\$c\"; fi; [ \"\$e\" = null ] || settings put secure screensaver_enabled \"\$e\"; rm -f $BEFORE; fi; rm -f $DIR/* 2>/dev/null; content delete --uri $URI 2>/dev/null; echo screensaver: \$(settings get secure screensaver_components)"
  exit 0
fi

[ -d "$FOLDER" ] || { echo "no folder $FOLDER. Create it and put your pictures (jpg, png, webp) and MP4 clips in it, or pass another folder." >&2; exit 1; }
FILES=()
for F in "$FOLDER"/*; do
  [ -f "$F" ] || continue
  NAME=$(basename "$F")
  case "$(printf '%s' "$NAME" | tr 'A-Z' 'a-z')" in *.jpg|*.jpeg|*.png|*.webp|*.mp4) ;; *) continue ;; esac
  [[ "$NAME" =~ ^[A-Za-z0-9_.-]+$ && "$NAME" != .* ]] || { echo "rename $NAME: use letters, digits, - _ and . only" >&2; exit 1; }
  SIZE=$(wc -c < "$F")
  case "$NAME" in
    *.mp4|*.MP4) [ "$SIZE" -le 1073741824 ] || { echo "$NAME is over 1 GB; the screensaver skips clips that big" >&2; exit 1; } ;;
    *) [ "$SIZE" -le 10485760 ] || { echo "$NAME is over 10 MB; the screensaver skips pictures that big" >&2; exit 1; } ;;
  esac
  FILES+=("$F")
done
[ "${#FILES[@]}" -le 100 ] || { echo "${#FILES[@]} files; the screensaver shows at most 100" >&2; exit 1; }
[ "${#FILES[@]}" -gt 0 ] || echo "warning: no pictures in $FOLDER: the screensaver stays black" >&2

# What the TV has now, by md5, so unchanged files are not sent again.
ON_TV="$(tv "cd $DIR 2>/dev/null && md5sum * 2>/dev/null; true" | tr -d '\r')"
while read -r SUM NAME; do
  [ -n "${NAME:-}" ] || continue
  KEEP=
  for F in ${FILES[@]+"${FILES[@]}"}; do [ "$(basename "$F")" = "$NAME" ] && KEEP=1; done
  if [ -z "$KEEP" ]; then
    tv "rm -f $DIR/$NAME 2>/dev/null; content delete --uri $URI/$NAME 2>/dev/null" >/dev/null
    echo "removed $NAME"
  fi
done <<< "$ON_TV"
SENT=0
for F in ${FILES[@]+"${FILES[@]}"}; do
  NAME=$(basename "$F")
  printf '%s\n' "$ON_TV" | grep -Fqx "$(md5 "$F")  $NAME" && continue
  echo "sending $NAME"
  "$PY" -I b64push.py "$F" "$TMP-$NAME" | grep md5 || { echo "sending $NAME failed; run the script again to resume" >&2; exit 1; }
  # Written through the app's FilesProvider so the app owns the file (on TCL G10 a copied one gives EACCES).
  OUT=$(tv "rm -f $DIR/$NAME 2>/dev/null; write_out=\$(content write --user 0 --uri $URI/$NAME < $TMP-$NAME 2>&1); write_rc=\$?; ok=0; if [ \"\$write_rc\" -eq 0 ] && [ -z \"\$write_out\" ]; then ok=1; elif mkdir -p $DIR && cp $TMP-$NAME $DIR/$NAME; then ok=1; fi; if rm -f $TMP-$NAME && [ \"\$ok\" -eq 1 ]; then echo __PHOTO_IMAGE_OK__; else printf '%s\n' \"\$write_out\"; fi")
  case "$OUT" in
    __PHOTO_IMAGE_OK__) ;;
    *) echo "$OUT" >&2; echo "sending $NAME failed; run the script again to resume" >&2; exit 1 ;;
  esac
  SENT=$((SENT + 1))
done

checked_write "$TMP-seconds.txt" "content://$PKG.files/seconds.txt" "$SECONDS_PER_PICTURE"
tv "[ -f $BEFORE ] || { settings get secure screensaver_components; settings get secure screensaver_enabled; } > $BEFORE; settings put secure screensaver_components $DREAM; settings put secure screensaver_enabled 1" >/dev/null
[ "$IDLE_MINUTES" -eq 0 ] || tv "settings put system screen_off_timeout $((IDLE_MINUTES * 60000))" >/dev/null
COUNT=$(tv "ls $DIR 2>/dev/null | wc -l" | tr -d '\r ')
echo "Screensaver: Photos with $COUNT file(s) on the TV, $SECONDS_PER_PICTURE s each ($SENT sent). Undo with: addons/photo-screensaver/sync.sh --off"
[ -z "${START:-}" ] || tv "am start -n com.android.systemui/.Somnambulator >/dev/null; sleep 3; dumpsys dreams | grep mCurrentDreamName"
