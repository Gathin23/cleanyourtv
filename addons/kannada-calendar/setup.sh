#!/usr/bin/env bash
# Give the Kannada Calendar add-on your calendar link. The add-on downloads the calendar itself from it, so
# the link stays on the TV and nothing is saved in this repository. Install the app first (README.md, Setup).
#
# Usage: TV_HOST=192.168.1.50 addons/kannada-calendar/setup.sh '<the link from the Bangalore Press website>'
#        TV_HOST=192.168.1.50 addons/kannada-calendar/setup.sh --remove     # forget the link and the saved copy
#
# The link is the "add to Google Calendar" one on the publisher's site; the older feed form it hands out is converted
# by the app. Open the add-on's card afterwards: it downloads the calendar the first time it is opened.
set -euo pipefail
ARG="${1:-}"
[ -n "$ARG" ] || { sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 1; }
cd "$(dirname "$0")/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 '<link>'}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.gagantk.kannadacalendar
DIR=/sdcard/Android/data/$PKG/files
tv() { "$PY" -I tv.py "$@"; }

tv "pm path $PKG; true" | grep -q package: \
  || { echo "the add-on is not on the TV: build and sideload it first (addons/kannada-calendar/README.md)" >&2; exit 1; }

if [ "$ARG" = --remove ]; then
  tv "rm -f $DIR/feed.txt; pm clear $PKG" >/dev/null
  echo "link and saved calendar removed"
  exit 0
fi

# The link goes inside single quotes on the TV's shell, so only the characters a web address uses are let through.
case "$ARG" in
  *[!A-Za-z0-9:/._%?=\&~+@-]*) echo "that does not look like a web address (a space or a quote in it?); copy the link again" >&2; exit 1 ;;
esac
case "$ARG" in
  http://*|https://*|webcal://*) ;;
  *) echo "the link has to start with http://, https:// or webcal://" >&2; exit 1 ;;
esac
tv "mkdir -p $DIR && printf '%s\\n' '$ARG' > $DIR/feed.txt" >/dev/null
echo "link saved on the TV; open the Kannada Calendar card and it downloads the calendar"
