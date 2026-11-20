#!/usr/bin/env bash
# Restore this TV to the state recorded before the debloat.
# Re-enables the three batches in reverse order, puts Home back on the stock launcher,
# restores the animation scales, and removes the launcher theme overlay.
# Usage: TV_HOST=192.168.0.6 examples/tcl-c06-ar2101-br-v034/restore-all.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
cd "$ROOT"
: "${TV_HOST:?set TV_HOST to the TV IP, e.g. TV_HOST=192.168.0.6 $0}"
export TV_HOST
for b in batch3.txt batch2.txt batch1.txt; do
  echo "=== restore $b ==="
  TV_HOST="$TV_HOST" tools/restore.sh "examples/tcl-c06-ar2101-br-v034/$b"
done
echo "=== remove theme overlay ==="
TV_HOST="$TV_HOST" tools/theme.sh --reset || true
echo "=== disabled packages now (compare with backup/) ==="
tools/venv/bin/python tools/tv.py pm list packages -d
