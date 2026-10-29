#!/usr/bin/env bash
# Make the news screensaver the TV's screensaver, and set its feeds, its seconds per headline and how long the TV
# waits before it starts. Only what you pass is changed: without a feeds file and without feeds.txt in this folder
# the feeds on the TV stay as they are. Install the app first with build-and-install.sh.
#
# Usage: TV_HOST=192.168.1.50 addons/news-screensaver/setup.sh                        # feeds.txt from this folder
#        TV_HOST=192.168.1.50 addons/news-screensaver/setup.sh ~/my-feeds.txt         # any feeds file
#        TV_HOST=192.168.1.50 SECONDS_PER_HEADLINE=20 addons/news-screensaver/setup.sh    # 20 s per headline
#        TV_HOST=192.168.1.50 IDLE_MINUTES=10 addons/news-screensaver/setup.sh        # start it after 10 idle minutes
#        TV_HOST=192.168.1.50 START=1 addons/news-screensaver/setup.sh                # and show it now
#        TV_HOST=192.168.1.50 addons/news-screensaver/setup.sh --default-feeds        # back to the built-in feed
#        TV_HOST=192.168.1.50 addons/news-screensaver/setup.sh --off                  # previous screensaver back
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
ARG="${1:-}"
FEEDS=
case "$ARG" in
  --off|--default-feeds) ;;
  "") [ -f "$HERE/feeds.txt" ] && FEEDS="$HERE/feeds.txt" ;;
  /*|?:*) FEEDS="$ARG" ;;
  *) FEEDS="$(pwd -W 2>/dev/null || pwd)/$ARG" ;;
esac
cd "$(dirname "$0")/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.newsdream
DREAM=$PKG/.NewsDream
URI=content://$PKG.files
TMP=/data/local/tmp/cyt-news
# The screensaver the TV had before the first run, so --off can put it back.
BEFORE=/data/local/tmp/cyt-news-before
IDLE_MINUTES="${IDLE_MINUTES:-0}"
SECONDS_PER_HEADLINE="${SECONDS_PER_HEADLINE:-}"
tv() { "$PY" -I tv.py "$@"; }

[[ "$IDLE_MINUTES" =~ ^[0-9]+$ ]] && [ "$IDLE_MINUTES" -le 1440 ] || { echo "IDLE_MINUTES takes 1 to 1440" >&2; exit 1; }
[ -z "$SECONDS_PER_HEADLINE" ] || { [[ "$SECONDS_PER_HEADLINE" =~ ^[0-9]+$ ]] && [ "$SECONDS_PER_HEADLINE" -ge 5 ] \
  && [ "$SECONDS_PER_HEADLINE" -le 600 ]; } || { echo "SECONDS_PER_HEADLINE takes 5 to 600" >&2; exit 1; }
tv "pm path $PKG; true" | grep -q package: \
  || { echo "the news screensaver is not on the TV: run addons/news-screensaver/build-and-install.sh first" >&2; exit 1; }

if [ "$ARG" = --off ]; then
  tv "if [ -f $BEFORE ]; then { read c; read e; } < $BEFORE; if [ \"\$c\" = null ]; then settings delete secure screensaver_components >/dev/null; else settings put secure screensaver_components \"\$c\"; fi; [ \"\$e\" = null ] || settings put secure screensaver_enabled \"\$e\"; rm -f $BEFORE; fi; echo screensaver: \$(settings get secure screensaver_components)"
  exit 0
fi

if [ "$ARG" = --default-feeds ]; then
  tv "content delete --uri $URI/feeds.txt" >/dev/null
  echo "feeds: the built-in default"
elif [ -n "$FEEDS" ]; then
  [ -f "$FEEDS" ] || { echo "no feeds file $FEEDS (copy feeds.example.txt to feeds.txt and edit it)" >&2; exit 1; }
  CLEAN="$(mktemp)"
  trap 'rm -f "$CLEAN"' EXIT
  N=0
  URLS=" "
  trim() { printf '%s' "$1" | tr -d '\r' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'; }
  while IFS= read -r LINE || [ -n "$LINE" ]; do
    LINE="$(trim "$LINE")"
    case "$LINE" in ''|'#'*) continue ;; esac
    # "Name | https://..." or just the address; the last bar splits them.
    NAME=; URL="$LINE"
    case "$LINE" in *'|'*) NAME="$(trim "${LINE%|*}")"; URL="$(trim "${LINE##*|}")" ;; esac
    [[ "$URL" =~ ^[Hh][Tt][Tt][Pp][Ss]://[^[:space:]/]+[^[:space:]]*$ ]] && [ "${#URL}" -le 500 ] \
      || { echo "not an https:// feed address: $LINE" >&2; exit 1; }
    [ "${#NAME}" -le 40 ] || { echo "name over 40 characters: $NAME" >&2; exit 1; }
    case "$URLS" in *" $URL "*) continue ;; esac
    URLS="$URLS$URL "
    if [ -n "$NAME" ]; then printf '%s | %s\n' "$NAME" "$URL"; else printf '%s\n' "$URL"; fi >> "$CLEAN"
    N=$((N + 1))
  done < "$FEEDS"
  [ "$N" -le 10 ] || { echo "$N feeds; the screensaver takes at most 10" >&2; exit 1; }
  [ "$N" -gt 0 ] || echo "warning: no feeds in $FEEDS: the screensaver will say so" >&2
  # Git Bash: the venv's Windows Python needs a Windows path for the temp file.
  "$PY" -I b64push.py "$(cygpath -w "$CLEAN" 2>/dev/null || printf '%s' "$CLEAN")" "$TMP-feeds.txt" | grep -q md5 || { echo "sending the feeds failed" >&2; exit 1; }
  tv "content write --uri $URI/feeds.txt < $TMP-feeds.txt; rm -f $TMP-feeds.txt" >/dev/null
  echo "feeds: $N from $FEEDS"
fi

[ -z "$SECONDS_PER_HEADLINE" ] || tv "printf '%s' $SECONDS_PER_HEADLINE > $TMP-seconds.txt; content write --uri $URI/seconds.txt < $TMP-seconds.txt; rm -f $TMP-seconds.txt" >/dev/null
tv "[ -f $BEFORE ] || { settings get secure screensaver_components; settings get secure screensaver_enabled; } > $BEFORE; settings put secure screensaver_components $DREAM; settings put secure screensaver_enabled 1" >/dev/null
[ "$IDLE_MINUTES" -eq 0 ] || tv "settings put system screen_off_timeout $((IDLE_MINUTES * 60000))" >/dev/null
echo "On the TV now:"
tv "f=\$(content read --uri $URI/feeds.txt 2>/dev/null); s=\$(content read --uri $URI/seconds.txt 2>/dev/null); echo \"\${f:-(the built-in default feed)}\" | sed 's/^/  feed: /'; echo \"  seconds per headline: \${s:-15}\"; echo \"  screen_off_timeout: \$(( \$(settings get system screen_off_timeout) / 60000 )) min\""
echo "Screensaver: News. Undo with: addons/news-screensaver/setup.sh --off"
[ -z "${START:-}" ] || tv "am start -n com.android.systemui/.Somnambulator >/dev/null; sleep 3; dumpsys dreams | grep mCurrentDreamName"
