#!/bin/bash
# Low-End Android TV System Optimization Script
# Enforces background process limits, AppOps restrictions, animation scaling, and AOT compilation.
# Usage: ./tools/optimize-low-end.sh <TV_IP>

set -e

TV_HOST="${1:-$TV_HOST}"
if [ -z "$TV_HOST" ]; then
    echo "Usage: $0 <TV_IP> (or set TV_HOST environment variable)"
    exit 1
fi

export TV_HOST
PY="python3"
if [ -f "tools/venv/bin/python" ]; then
    PY="tools/venv/bin/python"
fi

echo "==> Applying low-end TV optimizations to $TV_HOST..."

# 1. Background process limit (1 cached process)
echo "--> Enforcing background process limit = 1..."
$PY -I tools/tv.py "settings put global standard_background_process_limit 1" >/dev/null

# 2. Window animation scales
echo "--> Setting snappy UI animation scales (0.5x)..."
$PY -I tools/tv.py "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5" >/dev/null

# 3. Restrict Katniss hotword polling and Sling background execution
echo "--> Restricting background polling for Katniss and Sling..."
$PY -I tools/tv.py "cmd appops set com.google.android.katniss RUN_IN_BACKGROUND ignore; cmd appops set com.google.android.katniss RUN_ANY_IN_BACKGROUND ignore; cmd appops set com.sling RUN_IN_BACKGROUND ignore" >/dev/null

# 4. AOT native compile launcher and heavy streaming apps
PACKAGES=("com.gathin.tvhome" "com.sling" "org.smarttube.stable" "com.google.android.youtube.tv" "com.netflix.ninja" "com.amazon.amazonvideo.livingroom")
for PKG in "${PACKAGES[@]}"; do
    INSTALLED=$($PY -I tools/tv.py "pm list packages $PKG" 2>/dev/null || true)
    if [[ "$INSTALLED" == *"$PKG"* ]]; then
        echo "--> Compiling $PKG to native ARM machine code..."
        $PY -I tools/tv.py "cmd package compile -m speed -f $PKG" >/dev/null
    fi
done

echo "==> Low-end TV optimizations applied successfully!"
