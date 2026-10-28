#!/usr/bin/env bash
# Automated ZTE ZXV10 B866F Anti-Tamper & Watchdog Mitigation Setup
#
# Usage:
#   TV_HOST=<tv ip> examples/zte-b866f/setup-zte-mitigation.sh
#
# Prerequisite:
#   1. CleanYourTV (`com.gathin.tvhome`) is already installed (`STOCK_LAUNCHER=none launcher/build-and-install.sh`).
#   2. Magisk root (`su` or `/debug_ramdisk/su`) is granted to `com.android.shell`.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TV_PY="$REPO_ROOT/tools/tv.py"

echo "[1/4] Locating installed com.gathin.tvhome APK and Magisk su binary on device..."
APK_PATH="$("$TV_PY" "pm path com.gathin.tvhome | head -n 1 | cut -d: -f2 | tr -d '\r'")"
if [ -z "$APK_PATH" ]; then
  echo "Error: com.gathin.tvhome is not installed on the TV." >&2
  echo "Run 'STOCK_LAUNCHER=none launcher/build-and-install.sh' first." >&2
  exit 1
fi
echo "      Found com.gathin.tvhome at: $APK_PATH"

echo "[2/4] Disabling ZTE ControlDevice factory-reset watchdog components..."
"$TV_PY" '
SU_BIN=$(command -v su || echo /debug_ramdisk/su)
"$SU_BIN" -c "
  pm disable com.control.device/.receiver.ZteKeyInterceptorReceiver
  pm disable com.control.device/.recevier.ZteKeyInterceptorReceiver
"
'

echo "[3/4] Installing systemless Magisk module (/data/adb/modules/cleanyourtv) for FLAG_SYSTEM..."
"$TV_PY" "
SU_BIN=\$(command -v su || echo /debug_ramdisk/su)
\"\$SU_BIN\" -c '
  mkdir -p /data/adb/modules/cleanyourtv/system/app/CleanYourTV
  cp \"$APK_PATH\" /data/adb/modules/cleanyourtv/system/app/CleanYourTV/CleanYourTV.apk
  chmod 0644 /data/adb/modules/cleanyourtv/system/app/CleanYourTV/CleanYourTV.apk
  cat > /data/adb/modules/cleanyourtv/module.prop <<\"EOF\"
id=cleanyourtv
name=CleanYourTV System Launcher (ZTE B866F Mitigation)
version=1.0
versionCode=1
author=CleanYourTV
description=Mounts CleanYourTV as /system/app/CleanYourTV with FLAG_SYSTEM and disables ZTE ControlDevice factory-reset watchdog on ZTE ZXV10 B866F.
EOF
  chmod 0644 /data/adb/modules/cleanyourtv/module.prop
'
"

echo "[4/4] Installing boot persistence script (/data/adb/service.d/99-cleanyourtv-tweaks.sh) and setting HOME..."
"$TV_PY" '
SU_BIN=$(command -v su || echo /debug_ramdisk/su)
"$SU_BIN" -c "
  mkdir -p /data/adb/service.d
  cat > /data/adb/service.d/99-cleanyourtv-tweaks.sh <<\"EOF\"
#!/system/bin/sh
until [ \"\$(getprop sys.boot_completed)\" = \"1\" ]; do
  sleep 1
done
pm disable com.control.device/.receiver.ZteKeyInterceptorReceiver >/dev/null 2>&1
pm disable com.control.device/.recevier.ZteKeyInterceptorReceiver >/dev/null 2>&1
settings put secure tv_user_setup_complete 0 >/dev/null 2>&1
cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity >/dev/null 2>&1
sleep 45
pm disable com.control.device/.receiver.ZteKeyInterceptorReceiver >/dev/null 2>&1
pm disable com.control.device/.recevier.ZteKeyInterceptorReceiver >/dev/null 2>&1
settings put secure tv_user_setup_complete 0 >/dev/null 2>&1
cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity >/dev/null 2>&1
EOF
  chmod 0755 /data/adb/service.d/99-cleanyourtv-tweaks.sh
  cp /data/adb/service.d/99-cleanyourtv-tweaks.sh /data/adb/modules/cleanyourtv/service.sh
  chmod 0755 /data/adb/modules/cleanyourtv/service.sh
  settings put secure tv_user_setup_complete 0
  cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
"
'

echo "Done! ZTE B866F watchdog mitigation and systemless launcher module are active."
