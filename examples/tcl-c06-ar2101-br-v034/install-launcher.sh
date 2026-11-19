#!/usr/bin/env bash
# Build the reference launcher (Docker, no local Android SDK needed), install it as Home,
# then push the Apple TV style theme. Run from anywhere.
# Usage: TV_HOST=192.168.0.6 examples/tcl-c06-ar2101-br-v034/install-launcher.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
cd "$ROOT"
: "${TV_HOST:?set TV_HOST to the TV IP, e.g. TV_HOST=192.168.0.6 $0}"
export TV_HOST

if [ ! -f launcher/app/build/outputs/apk/release/app-release.apk ]; then
  echo "=== building launcher in Docker ==="
  tools/docker-build.sh launcher assembleRelease
fi

echo "=== installing launcher as Home ==="
SKIP_BUILD=1 TV_HOST="$TV_HOST" launcher/build-and-install.sh

echo "=== pushing Apple TV theme ==="
TV_HOST="$TV_HOST" tools/theme.sh themes/apple-tv-br

echo "=== verifying Home ==="
tools/venv/bin/python tools/tv.py dumpsys activity activities | grep -E 'mFocusedApp|ResumedActivity' | head -5
