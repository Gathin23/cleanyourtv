#!/bin/bash
# ==============================================================
# Script de Restauración para Chromecast con Google TV HD (macOS)
# ==============================================================

set -e
echo "=========================================================="
echo "  Restaurador para Chromecast con Google TV HD"
echo "=========================================================="

if command -v adb >/dev/null 2>&1; then
    ADB="adb"
else
    ADB="/tmp/chromecast_tools/platform-tools/adb"
fi

read -p "Introduce la IP y Puerto del Chromecast (ej: 192.168.1.134:37953): " TV_ADDR
$ADB connect "$TV_ADDR"

PKGS=(
    "com.google.android.apps.tv.launcherx"
    "com.google.android.feedback"
    "com.android.tv.feedbackconsent"
    "com.google.android.tv.remotecontrol.logging"
    "com.google.android.apps.tv.netoscope"
    "com.google.android.federatedcompute"
    "com.google.android.ondevicepersonalization.services"
    "com.google.android.adservices.api"
    "com.google.android.ext.adservices.api"
    "com.google.android.play.games"
    "com.google.android.videos"
    "com.android.printspooler"
    "com.google.android.apps.tv.dreamx"
    "com.google.android.backdrop"
)

for pkg in "${PKGS[@]}"; do
    echo "Rehabilitando: $pkg"
    $ADB -s "$TV_ADDR" shell "pm enable $pkg" 2>/dev/null || true
done

$ADB -s "$TV_ADDR" shell "settings put global private_dns_mode opportunistic"
$ADB -s "$TV_ADDR" shell "input keyevent KEYCODE_HOME"

echo "✓ Chromecast restaurado a su estado de fábrica original."
