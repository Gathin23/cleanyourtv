#!/usr/bin/env bash
# Build Frigate Glance, install it on the TV, push its config and check it reaches MQTT and Frigate.
#   TV_HOST=<tv ip> addons/frigate-glance/install.sh <config.json>
# The config holds passwords: keep it outside the repo (backup/ is git-ignored). This script never prints it.
set -euo pipefail
CONFIG="${1:?usage: TV_HOST=<tv ip> $0 <config.json>}"
: "${TV_HOST:?set TV_HOST to the TV IP address}"
CONFIG="$(cd "$(dirname "$CONFIG")" && pwd)/$(basename "$CONFIG")"
# Git Bash on Windows: the venv's Python wants C:/... rather than /c/...
if command -v cygpath >/dev/null 2>&1; then CONFIG="$(cygpath -m "$CONFIG")"; fi
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.nickld.frigateglance
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

"$PY" -I - "$CONFIG" <<'EOF' || exit 1
import json, sys
try:
    c = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception as e:
    sys.exit(f"{sys.argv[1]} is not valid JSON: {e}")
missing = [k for k in ("mqtt", "frigateUrl") if not str(c.get(k, "")).strip()]
if missing:
    sys.exit("config needs " + ", ".join(missing))
from urllib.parse import urlsplit
try:
    u = urlsplit(c["mqtt"])
    ok = u.scheme == "mqtt" and bool(u.hostname) and not any(ch in c["mqtt"] for ch in " #?")
except ValueError:
    ok = False
if not ok:   # the value holds a password: never print it
    sys.exit('"mqtt" must look like mqtt://user:password@host:1883, special characters percent-encoded')
EOF

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/fg.apk
OUT=$(tv 'pm install -r /data/local/tmp/fg.apk; rm -f /data/local/tmp/fg.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# Display over other apps (TCL's Settings hide it) and TCL's auto-start, so the service reconnects after a reboot.
tv "appops set $PKG SYSTEM_ALERT_WINDOW allow; cmd appops set $PKG APP_AUTO_START allow 2>/dev/null; echo permissions granted"

# The config goes into the app's files folder, or through its provider where the shell may not write there.
# The log is cleared first: through the provider the service picks the config up, and logs its connection, at once.
tv "logcat -c" >/dev/null
STAGE=/data/local/tmp/fg-config.json
tv --push "$CONFIG" "$STAGE" >/dev/null
OUT=$(tv "d=/sdcard/Android/data/$PKG/files; ok=0; write_out=''; if mkdir -p \$d 2>/dev/null && cp $STAGE \$d/config.json 2>/dev/null; then ok=1; else write_out=\$(content write --user 0 --uri content://$PKG.config/config.json < $STAGE 2>&1); write_rc=\$?; if [ \"\$write_rc\" -eq 0 ] && [ -z \"\$write_out\" ]; then ok=1; fi; fi; if rm -f $STAGE && [ \"\$ok\" -eq 1 ]; then echo __FRIGATE_CONFIG_OK__; else printf '%s\n' \"\$write_out\"; fi")
case "$OUT" in
  __FRIGATE_CONFIG_OK__) echo "config pushed" ;;
  *) echo "$OUT" >&2; echo "ERROR: the config could not be written on the TV" >&2; exit 1 ;;
esac

# The key service, added to whatever accessibility services are already on (the launcher's, for one). A service
# that was already listed is unbound by the reinstall and binds again only once the list changes.
SVC=$PKG/.KeyService
tv "s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$SVC*) settings put secure enabled_accessibility_services \"\$s\"; settings put secure accessibility_enabled 1; sleep 1; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$s\" ;; null|'') settings put secure enabled_accessibility_services $SVC ;; *) settings put secure enabled_accessibility_services \"\$s:$SVC\" ;; esac; settings put secure accessibility_enabled 1; echo key service enabled"

# Start it, then wait up to 20 s for the connection lines the service logs.
tv "am start -n $PKG/.SettingsActivity >/dev/null" >/dev/null
LOG=""
for _ in $(seq 1 10); do
  sleep 2
  LOG=$(tv "logcat -d -s FrigateGlance:*" | tr -d '\r')
  case "$LOG" in *"mqtt connected"*|*"mqtt error"*) case "$LOG" in *"frigate ok"*|*"frigate error"*) break ;; esac ;; esac
done
mqtt=$(printf '%s\n' "$LOG" | grep -o 'mqtt connected\|mqtt error: .*' | tail -n 1 || true)
frigate=$(printf '%s\n' "$LOG" | grep -o 'frigate ok\|frigate error: .*' | tail -n 1 || true)
case "$mqtt" in "mqtt connected") echo "MQTT: ok" ;; "") echo "MQTT: no answer within 20 s" ;; *) echo "MQTT: ${mqtt#mqtt error: }" ;; esac
case "$frigate" in "frigate ok") echo "Frigate: ok" ;; "") echo "Frigate: no answer within 20 s" ;; *) echo "Frigate: ${frigate#frigate error: }" ;; esac
[ "$mqtt" = "mqtt connected" ] && [ "$frigate" = "frigate ok" ] || exit 1
tv "am broadcast -n $PKG/.TestCardReceiver >/dev/null; echo test card shown"
echo "Installed. A test card is on the TV now; real ones follow Frigate's alerts."
