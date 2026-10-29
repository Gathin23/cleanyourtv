#!/usr/bin/env bash
# Build the launcher APK and install it on the TV as the default home screen.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
TOOLS="$(cd ../tools && pwd)"   # absolute, so the venv's Python doesn't warn about "../" in its prefix
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
# No venv yet: say how to make one (as tools/disable.sh does) instead of failing on the Windows path (#1142).
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
# Below minSdk, `pm install` refuses the APK with INSTALL_FAILED_OLDER_SDK - and only after gradle has
# spent a minute building and b64push has pushed the APK to the TV (#576). Ask the TV first. The
# number comes from the build file, so it follows the launcher instead of a hard-coded copy here.
MIN_SDK=$(sed -n 's/^[[:space:]]*minSdk[[:space:]][[:space:]]*\([0-9]\{1,3\}\)[[:space:]]*$/\1/p' app/build.gradle 2>/dev/null | head -n 1 || true)
TV_SDK=$("$PY" -I "$TOOLS/tv.py" 'getprop ro.build.version.sdk' 2>/dev/null | tr -dc '0-9' || true)
if [ -n "$MIN_SDK" ] && [ -n "$TV_SDK" ] && [ "$TV_SDK" -lt "$MIN_SDK" ]; then
  echo "ERROR: this TV runs SDK $TV_SDK and the launcher is built with minSdk $MIN_SDK (app/build.gradle)," >&2
  echo "so pm install would answer INSTALL_FAILED_OLDER_SDK. Nothing was built, pushed or disabled." >&2
  echo "The debloat half of the repo still applies to that set, from its own home screen; the launcher" >&2
  echo "half would need your own build with a lower minSdk (docs/TROUBLESHOOTING.md)." >&2
  exit 1
fi
# macOS: java_home or Homebrew; Linux: the java on PATH.
# `/usr/libexec/java_home -v 17` means "17 or newer", so on a Mac whose only JDK is newer it prints
# that one and exits 0, and the Homebrew fallback below never runs. Gradle 8.9 then stops with
# "Unsupported class file major version" (68 for JDK 24) before it reads a line of this project.
# Homebrew's openjdk@17 is keg-only and is never registered where java_home would find it, so check
# the version it answers with and fall through when it is not a 17. `java -version` goes to a
# variable rather than through `grep -q`, whose early exit can SIGPIPE the JVM under `set -o pipefail`.
# A JRE (Temurin ships one) answers as a 17 but has no jlink, which the Android Gradle plugin needs,
# so skip it too and fall through to Homebrew (#1255).
if [ -z "${JAVA_HOME:-}" ]; then
  JH="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  if [ -n "$JH" ]; then
    case "$("$JH/bin/java" -version 2>&1 || true)" in *'version "17.'*) ;; *) JH="" ;; esac
  fi
  if [ -n "$JH" ] && [ ! -x "$JH/bin/jlink" ]; then JH=""; fi
  if [ -z "$JH" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then JH=/opt/homebrew/opt/openjdk@17; fi
  if [ -z "$JH" ]; then JH="$(java -XshowSettings:properties -version 2>&1 | awk '/java.home/ {print $3}')"; fi
  JAVA_HOME="$JH"
fi
export JAVA_HOME
# Android Studio's default SDK location on macOS, then on Linux, then Homebrew's command-line tools.
export ANDROID_HOME="${ANDROID_HOME:-$(for d in ~/Library/Android/sdk ~/Android/Sdk; do [ -d "$d" ] && echo "$d" && break; done)}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"
# local.properties is a Java properties file, where a backslash is an escape. Git Bash on Windows hands us
# C:\Users\...\Sdk, which Gradle then reads as a broken path ("The filename, directory name, or volume label
# syntax is incorrect"), and since the file is only written when missing, build-and-install.ps1 kept failing
# on it too. Forward slashes work everywhere.
[ -f local.properties ] || echo "sdk.dir=$(printf '%s' "$ANDROID_HOME" | tr '\\' '/')" > local.properties
./gradlew assembleRelease -q --console=plain
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
HOME_QUERY='cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
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
  *INSTALL_FAILED_OLDER_SDK*)
    # The pre-flight above missed it (a TV whose getprop did not answer, or an APK built elsewhere):
    # name the real cause instead of leaving the raw Failure line to be read as an ADB problem.
    echo "ERROR: pm install refused the APK as too new for this set (INSTALL_FAILED_OLDER_SDK): the TV runs an older SDK than the launcher's minSdk ${MIN_SDK:-in app/build.gradle}." >&2
    echo "The stock launcher was left untouched. Read both numbers and see docs/TROUBLESHOOTING.md." >&2
    exit 1 ;;
  *) echo "ERROR: pm install failed; the stock launcher was left untouched." >&2; exit 1 ;;
esac
# Replacing the launcher while it is Home lets Android restart it mid-update, and on a TCL G03
# (Android 11) that instance came up with mOccludesParent=false: Home then left the previous app or
# HDMI picture on screen (#1101). `am start` below would only reuse it, so stop it first; the start
# then creates a fresh one. A force stop also takes the key helpers off the enabled list, so a helper
# that was on is written back (cleared first: Android binds again only when the list changes).
"$PY" -I ../tools/tv.py 's=$(settings get secure enabled_accessibility_services); a=$(settings get secure accessibility_enabled); am force-stop com.gathin.tvhome; case "$s" in *com.gathin.tvhome*) settings put secure enabled_accessibility_services ""; settings put secure enabled_accessibility_services "$s"; settings put secure accessibility_enabled "$a" ;; esac' >/dev/null
# Start the launcher on its own, before the stock Home is touched. In the batch below a start that
# fails only prints an "Error:" line, and the stock launcher would be disabled anyway, leaving the TV
# with no usable Home (#239). "Status: ok" from `am start -W` means the activity really came up;
# the HOME resolution further down only names a component, it does not prove the activity starts.
START_OUT=$("$PY" -I ../tools/tv.py 'am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity')
echo "$START_OUT"
# Both halves matter: some builds answer "Status: ok" for a task that was merely brought to the
# front, and an activity that cannot start still prints "Error: Activity not started".
if ! printf '%s' "$START_OUT" | grep -qF 'Status: ok' || printf '%s' "$START_OUT" | grep -qi '^Error'; then
  echo "ERROR: the new launcher did not start (see above); $STOCK_LAUNCHER was left enabled." >&2
  exit 1
fi
printf '%s\n' \
  "pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted" \
  "cmd package compile -m speed -f com.gathin.tvhome" \
  "$DISABLE_STOCK" \
  "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity" \
  | "$PY" -I ../tools/tv.py --batch

# Home's owner, or an empty string when the TV cannot be reached (pipefail would otherwise make a
# lost connection abort the script with no explanation).
query_home() {
  "$PY" -I ../tools/tv.py "$HOME_QUERY" 2>/dev/null | tail -n 1 | tr -d '\r' || true
}

# Put the stock launcher back and check that it took. A TV whose new Home does not start can only be
# rescued over ADB, so the commands go into a local file as well: when it is the connection that
# broke, the terminal that ran this script may never come back.
rollback_home() {
  if [ "$STOCK_LAUNCHER" = none ]; then
    echo "ERROR: Home is not the new launcher, and no stock launcher was disabled; nothing to roll back." >&2
    return 1
  fi
  "$PY" -I ../tools/tv.py "pm enable --user 0 $STOCK_LAUNCHER" >/dev/null 2>&1 || true
  # set-home-activity needs pkg/activity on Android 9, so resolve the component on the TV first.
  "$PY" -I ../tools/tv.py "cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $STOCK_LAUNCHER | tail -n 1)" >/dev/null 2>&1 || true
  BACK=$(query_home)
  case "$BACK" in
    "$STOCK_LAUNCHER"/*) echo "$STOCK_LAUNCHER owns Home again." >&2; return 0 ;;
  esac
  REC="${HOME:-$PWD}/cleanyourtv-handover-$(printf '%s' "$TV_HOST" | tr -c 'A-Za-z0-9._-' '_').txt"
  {
    echo "# cleanyourtv: the launcher handover on $TV_HOST did not finish. From the repo root:"
    echo "TV_HOST=$TV_HOST tools/tv.py 'pm enable --user 0 $STOCK_LAUNCHER'"
    echo "TV_HOST=$TV_HOST tools/tv.py 'cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $STOCK_LAUNCHER | tail -n 1)'"
    echo "# then check: TV_HOST=$TV_HOST tools/tv.py '$HOME_QUERY'"
  } > "$REC" 2>/dev/null || REC=""
  echo "ERROR: recovery is incomplete; Home still resolves to '${BACK:-nothing}'." >&2
  if [ -n "$REC" ]; then echo "The commands that finish it are in $REC; the TV still needs ADB for them." >&2
  else echo "Re-run once the TV answers: pm enable --user 0 $STOCK_LAUNCHER, then set its Home activity." >&2; fi
  return 1
}

# Order matters: the new launcher is started before the stock one is disabled, and the home
# preference is set after. Android drops the preference whenever the set of HOME apps changes, and
# on Google TV 14 the next candidate is the setup wizard's RecoveryActivity, which crash-loops
# system_server (see examples/xiaomi-tv-a-pro-2026). So check where Home ended up, and put the
# stock launcher back if it is not ours.
HOME_NOW=$(query_home)
case "$HOME_NOW" in
  com.gathin.tvhome/*) echo "Home now resolves to $HOME_NOW" ;;
  *)
    if [ -z "$HOME_NOW" ]; then
      echo "ERROR: the TV stopped answering while Home was being switched, so the outcome is unknown." >&2
    else
      echo "ERROR: Home resolves to $HOME_NOW, not to the new launcher." >&2
    fi
    rollback_home || true
    if [ -n "$HOME_NOW" ]; then
      echo "On Google TV this usually means com.google.android.tungsten.setupwraith also claims Home: add it to" >&2
      echo "your debloat list (see README, Troubleshooting), apply the list, then run this script again." >&2
    fi
    exit 1 ;;
esac

# Optional accessibility services. Off unless asked for, and Android switches them off again on
# every reinstall, so pass the flag each time you run this script.
#   INPUT_KEY_SERVICE=1   the remote's Input button (KEYCODE_TV or TV_INPUT) opens the input switcher from any
#                         app; Google TV needs this once the stock launcher is gone (InputKeyService).
#                         It also handles input_key_keycodes and app_keys (config.xml or a theme; a YouTube button)
#   HOME_KEY_SERVICE=1    remotes whose Home button sends KEYCODE_ALL_APPS instead of HOME, e.g. Xiaomi
#                         sticks: the key is routed to the launcher (HomeKeyService)
#   RESTORE_KEY_SERVICES=1  grants WRITE_SECURE_SETTINGS, so that with restore_key_services (config.xml or a
#                         theme) Home puts these helpers back after a force stop of the launcher
# TCL firmware (TclAppBoot) refuses to bind an app's accessibility service while the app is not running unless it
# holds the AUTO_START app-op, so grant it first; other TVs do not know the op and ignore it. Builds name it
# AUTO_START (G08, AU02) or APP_AUTO_START (G10), so set both. App-ops reach the disk lazily: without
# `appops write-settings` the next reboot drops the grant, TclAppBoot refuses the service again and Home falls back
# to the stock launcher (TCL 55Q6C, Google TV 14, UTT2.250416.001 AU02). A service that is
# already listed was refused while the APK was replaced, and binds again only once the list changes, so clear it,
# switch accessibility off and write it back (as tools/theme.sh does).
enable_service() {   # appends to enabled_accessibility_services without dropping other services
  svc=com.gathin.tvhome/.$1
  "$PY" -I ../tools/tv.py "cmd appops set com.gathin.tvhome AUTO_START allow 2>/dev/null; cmd appops set com.gathin.tvhome APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$1*) settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$s\" ;; null|'') settings put secure enabled_accessibility_services $svc ;; *) settings put secure enabled_accessibility_services \"\$s:$svc\" ;; esac; settings put secure accessibility_enabled 1; echo $1 enabled"
}
if [ "${INPUT_KEY_SERVICE:-0}" = 1 ]; then enable_service InputKeyService; fi
if [ "${HOME_KEY_SERVICE:-0}" = 1 ]; then enable_service HomeKeyService; fi
if [ "${RESTORE_KEY_SERVICES:-0}" = 1 ]; then
  "$PY" -I ../tools/tv.py "pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS && echo restore-granted"
fi
echo "Installed. Press Home on the remote to see it."
