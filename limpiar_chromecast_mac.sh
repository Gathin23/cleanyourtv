#!/bin/bash
# ==============================================================
# Script de Optimización y Debloat para Chromecast con Google TV HD
# Compatible con macOS (Terminal)
# ==============================================================

set -e
echo "=========================================================="
echo "  Optimizador para Chromecast con Google TV HD (macOS)"
echo "=========================================================="

# 1. Comprobar / Descargar ADB para macOS
if command -v adb >/dev/null 2>&1; then
    ADB="adb"
    echo "✓ ADB encontrado en el sistema"
else
    echo "→ Descargando herramientas ADB oficiales para macOS..."
    mkdir -p /tmp/chromecast_tools
    cd /tmp/chromecast_tools
    curl -sSL "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip" -o platform-tools.zip
    unzip -q -o platform-tools.zip
    ADB="/tmp/chromecast_tools/platform-tools/adb"
    chmod +x "$ADB"
    echo "✓ ADB descargado correctamente"
fi

# 2. Datos de conexión
echo ""
echo "En el Chromecast entra a:"
echo "Ajustes > Sistema > Opciones para desarrolladores > Depuración inalámbrica"
echo ""
read -p "Introduce la IP y Puerto que muestra la tele (ej: 192.168.1.134:37953): " TV_ADDR

# 3. Emparejamiento si es necesario
echo ""
read -p "¿Necesitas emparejar con código (sale en 'Vincular dispositivo con código')? (s/n): " PAIR_REQ
if [ "$PAIR_REQ" = "s" ] || [ "$PAIR_REQ" = "S" ]; then
    read -p "Introduce IP:PUERTO de vinculación: " PAIR_ADDR
    read -p "Introduce el Código de 6 dígitos: " PAIR_CODE
    echo "Emparejando..."
    $ADB pair "$PAIR_ADDR" "$PAIR_CODE"
fi

echo "Conectando al Chromecast en $TV_ADDR..."
$ADB connect "$TV_ADDR"

echo "Verificando dispositivo..."
$ADB -s "$TV_ADDR" shell "getprop ro.product.model"

# 4. Descargar e instalar Projectivy Launcher
echo ""
echo "→ Descargando Projectivy Launcher..."
curl -sSL -L "https://github.com/spocky/miproja1/releases/download/4.72/ProjectivyLauncher-4.72-c97-xda-release.apk" -o /tmp/ProjectivyLauncher.apk

echo "→ Instalando Projectivy Launcher en el Chromecast..."
$ADB -s "$TV_ADDR" push /tmp/ProjectivyLauncher.apk /data/local/tmp/ProjectivyLauncher.apk
$ADB -s "$TV_ADDR" shell "pm install -r -g /data/local/tmp/ProjectivyLauncher.apk"

# 5. Aplicar Debloat específico para Chromecast Google TV
echo ""
echo "→ Deshabilitando telemetría, publicidad y servicios pesados..."

PKGS=(
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
    "com.google.android.apps.tv.launcherx"
)

for pkg in "${PKGS[@]}"; do
    echo "Deshabilitando: $pkg"
    $ADB -s "$TV_ADDR" shell "pm disable-user --user 0 $pkg" 2>/dev/null || true
done

# 6. Activar DNS Privado AdGuard (Filtro anti-anuncios)
echo ""
echo "→ Configurando AdGuard DNS anti-publicidad..."
$ADB -s "$TV_ADDR" shell "settings put global private_dns_specifier dns.adguard-dns.com"
$ADB -s "$TV_ADDR" shell "settings put global private_dns_mode hostname"

# 7. Animaciones 0.5x
echo "→ Acelerando animaciones a 0.5x..."
$ADB -s "$TV_ADDR" shell "settings put global window_animation_scale 0.5"
$ADB -s "$TV_ADDR" shell "settings put global transition_animation_scale 0.5"
$ADB -s "$TV_ADDR" shell "settings put global animator_duration_scale 0.5"

# 8. Fijar Projectivy como Home
echo "→ Fijando pantalla de inicio..."
$ADB -s "$TV_ADDR" shell "cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity" 2>/dev/null || true
$ADB -s "$TV_ADDR" shell "input keyevent KEYCODE_HOME"

echo ""
echo "=========================================================="
echo "  ¡Completado con éxito!"
echo "  El Chromecast ya tiene Projectivy Launcher, AdGuard DNS"
echo "  y el debloat aplicado."
echo "=========================================================="
