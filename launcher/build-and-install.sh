#!/usr/bin/env bash
# Build the launcher APK and install it on the TV as the default home screen.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
# macOS: java_home or Homebrew; Linux: the java on PATH.
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || { [ -d /opt/homebrew/opt/openjdk@17 ] && echo /opt/homebrew/opt/openjdk@17; } || java -XshowSettings:properties -version 2>&1 | awk '/java.home/ {print $3}')}"
# Android Studio's default SDK location on macOS, then on Linux, then Homebrew's command-line tools.
export ANDROID_HOME="${ANDROID_HOME:-$(for d in ~/Library/Android/sdk ~/Android/Sdk; do [ -d "$d" ] && echo "$d" && break; done)}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleRelease -q --console=plain
TOOLS="$(cd ../tools && pwd)"   # absolute, so the venv's Python doesn't warn about "../" in its prefix
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
# Which app owns Home differs by build (com.google.android.tvlauncher on Android TV,
# com.google.android.apps.tv.launcherx on newer Google TV), so ask the TV instead of guessing.
# Only a launcher we recognise is accepted: the answer can also be Android's chooser ("android"),
# the setup wizard, or another third-party launcher, none of which should be disabled by accident.
# Pass STOCK_LAUNCHER=<package> to name it yourself, or STOCK_LAUNCHER=none to disable nothing.
KNOWN_STOCK_LAUNCHERS="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher com.mitv.tvhome"
if [ -z "${STOCK_LAUNCHER:-}" ]; then
  HOME_PKG=$("$PY" -I ../tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' \
    | tail -n 1 | tr -d '\r' | cut -d/ -f1) || {
    echo "ERROR: could not ask the TV which launcher owns Home. Check TV_HOST and the debugging prompt on the TV," >&2
    echo "or pass STOCK_LAUNCHER=<package> yourself." >&2; exit 1; }
  case " $KNOWN_STOCK_LAUNCHERS " in
    *" $HOME_PKG "*) STOCK_LAUNCHER=$HOME_PKG ;;
    *)
      if [ "$HOME_PKG" = com.gathin.tvhome ]; then
        STOCK_LAUNCHER=none; echo "Home already belongs to this launcher; nothing will be disabled."
      elif [ "$HOME_PKG" = android ]; then
        # Android's chooser: two or more launchers tie and no default is set, which is where a TV lands
        # after tools/restore.sh followed by a re-applied list. Take the one known stock launcher among
        # the HOME claimants, if there is exactly one.
        CLAIMANTS=$("$PY" -I ../tools/tv.py 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' \
          | tr -d '\r' | grep -o '[A-Za-z0-9_.]*/' | tr -d / | sort -u)
        FOUND=""; N=0
        for k in $KNOWN_STOCK_LAUNCHERS; do
          if printf '%s\n' "$CLAIMANTS" | grep -qx "$k"; then FOUND=$k; N=$((N + 1)); fi
        done
        if [ "$N" -eq 1 ]; then
          STOCK_LAUNCHER=$FOUND; echo "Home is Android's chooser; the stock launcher among the candidates is $STOCK_LAUNCHER."
        else
          echo "ERROR: Home is Android's launcher chooser and $N known stock launchers claim Home: pick a default on the TV first," >&2
          echo "or run again with STOCK_LAUNCHER=<package to disable> or STOCK_LAUNCHER=none." >&2; exit 1
        fi
      else
        echo "ERROR: Home currently resolves to '${HOME_PKG:-nothing}', which is not a stock launcher this script knows." >&2
        echo "  android                                  = Android's launcher chooser (two launchers tie); pick a default on the TV first" >&2
        echo "  com.google.android.tungsten.setupwraith  = Google TV's setup wizard; see README, Troubleshooting" >&2
        echo "  anything else                            = another launcher is the default" >&2
        echo "Run again with STOCK_LAUNCHER=<package to disable> or STOCK_LAUNCHER=none." >&2; exit 1
      fi ;;
  esac
fi
echo "Stock launcher: $STOCK_LAUNCHER"
if [ "$STOCK_LAUNCHER" = none ]; then DISABLE_STOCK="echo stock launcher left alone"
else DISABLE_STOCK="pm disable-user --user 0 $STOCK_LAUNCHER"; fi
APK=app/build/outputs/apk/release/app-release.apk
"$PY" -I ../tools/b64push.py "$APK" /data/local/tmp/tvhome.apk
# Install on its own: in the batch below a failed install (e.g. "not enough space" on a full /data)
# would not stop the stock launcher from being disabled.
OUT=$("$PY" -I ../tools/tv.py 'pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk')
echo "$OUT"
case "$OUT" in
  *Success*) ;;
  *) echo "ERROR: pm install failed; the stock launcher was left untouched." >&2; exit 1 ;;
esac
printf '%s\n' \
  "pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted" \
  "cmd package compile -m speed -f com.gathin.tvhome" \
  "am start -W -n com.gathin.tvhome/.WebHomeActivity | grep -E '^(Status|Error)'" \
  "$DISABLE_STOCK" \
  "cmd package set-home-activity --user 0 com.gathin.tvhome/.WebHomeActivity" \
  | "$PY" -I ../tools/tv.py --batch
# Order matters: the new launcher is started before the stock one is disabled, and the home
# preference is set after. Android drops the preference whenever the set of HOME apps changes, and
# on Google TV 14 the next candidate is the setup wizard's RecoveryActivity, which crash-loops
# system_server (see examples/xiaomi-tv-a-pro-2026). So check where Home ended up, and put the
# stock launcher back if it is not ours.
HOME_NOW=$("$PY" -I ../tools/tv.py 'cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN' | tail -n 1 | tr -d '\r')
case "$HOME_NOW" in
  com.gathin.tvhome/*) echo "Home now resolves to $HOME_NOW" ;;
  *)
    [ "$STOCK_LAUNCHER" = none ] || "$PY" -I ../tools/tv.py "pm enable --user 0 $STOCK_LAUNCHER" >/dev/null || true
    echo "ERROR: Home resolves to ${HOME_NOW:-nothing}, not to the new launcher; $STOCK_LAUNCHER was re-enabled." >&2
    echo "On Google TV this usually means com.google.android.tungsten.setupwraith also claims Home: add it to" >&2
    echo "your debloat list (see README, Troubleshooting), apply the list, then run this script again." >&2
    exit 1 ;;
esac

# Optional accessibility services. Off unless asked for, and Android switches them off again on
# every reinstall, so pass the flag each time you run this script.
#   INPUT_KEY_SERVICE=1   the remote's Input button (KEYCODE_TV) opens the input switcher from any
#                         app; Google TV needs this once the stock launcher is gone (InputKeyService)
#   HOME_KEY_SERVICE=1    remotes whose Home button sends KEYCODE_ALL_APPS instead of HOME, e.g. Xiaomi
#                         sticks: the key is routed to the launcher (HomeKeyService)
enable_service() {   # appends to enabled_accessibility_services without dropping other services
  svc=com.gathin.tvhome/.$1
  "$PY" -I ../tools/tv.py "s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$1*) ;; null|'') settings put secure enabled_accessibility_services $svc ;; *) settings put secure enabled_accessibility_services \"\$s:$svc\" ;; esac; settings put secure accessibility_enabled 1; echo $1 enabled"
}
if [ "${INPUT_KEY_SERVICE:-0}" = 1 ]; then enable_service InputKeyService; fi
if [ "${HOME_KEY_SERVICE:-0}" = 1 ]; then enable_service HomeKeyService; fi
echo "Installed. Press Home on the remote to see it."
