#!/usr/bin/env bash
# Disable every package listed in a debloat file on the TV (one package per line, # comments allowed).
# Packages are disabled for user 0, never uninstalled. Undo with tools/restore.sh <same file>.
#
# Usage: TV_HOST=192.168.1.50 tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt
set -euo pipefail
LIST="${1:?usage: disable.sh <debloat list>}"
# Resolve the list against the caller's directory before moving into the repo. Git Bash's pwd says
# /c/Users/..., which the Windows Python cannot open with MSYS_NO_PATHCONV=1 set; pwd -W gives
# C:/Users/..., the same path (and so the same record) as the PowerShell scripts.
LIST_DIR="$(cd "$(dirname "$LIST")" && { pwd -W 2>/dev/null || pwd; })"
LIST="$LIST_DIR/$(basename "$LIST")"
cd "$(dirname "$0")/.."
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }
# Lists come from contributors, and the package name is pasted into a shell command on the TV.
# Refuse the whole list unless every entry is a bare package name (#165).
# A list read from a directory that is not there dies in the `cd` above; one whose file is not
# readable only made awk complain, so say it in the script's own words, next to the empty-list
# message below rather than as a stray awk line.
[ -r "$LIST" ] || { echo "cannot read the list: $LIST" >&2; exit 1; }
# Normalise first: Notepad on Windows saves a BOM and CRLF, and without this the first "# TV" line
# arrives as a package name and every entry keeps a trailing \r (#208).
# `|| true`: an empty result is not a failure of this pipeline. Under pipefail the grep exits 1 when
# the list holds no package line at all, and `set -e` then ended the run with no output and status 1,
# so a list whose entries are all commented out (what CONTRIBUTING asks for while one is untested)
# read exactly like a run that died halfway.
PKGS=$(LC_ALL=C awk 'NR==1{sub(/^\357\273\277/,"")} {sub(/\r$/,""); print}' "$LIST" \
  | grep -vE '^\s*(#|$)' | sed -E 's/\s+#.*$//' | awk '{print $1}' || true)
BAD=$(printf '%s\n' "$PKGS" | grep -vE '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' || true)
if [ -n "$BAD" ]; then
  { echo "refusing $LIST: not a package name:"; printf '%s\n' "$BAD" | sed 's/^/  /'; } >&2
  exit 1
fi
# Nothing to do, and nothing to lose by saying so before the TV is asked anything: no record is
# written and no query is sent, so this cannot be mistaken for a list that ran. It is the first
# refusal after the shape check because it is the one a contributor is most likely to hit: a list
# whose entries are all commented out is what CONTRIBUTING asks for while an entry is untested.
if [ -z "$PKGS" ]; then
  echo "refusing $LIST: no package names in it (every line is a comment or blank), so nothing was" >&2
  echo "sent to the TV and no recovery record was written." >&2
  exit 1
fi
# Core Android packages: with one of these disabled, Android itself can stop booting. An original Mi TV
# Stick never came back after a list took the permission controller, package installer, ext services,
# module metadata and the mainline overlays (#884). No switch overrides this. The content providers
# below CORE_OK are the exception: tested lists turn them off and no TV feature uses them.
# Keep CORE_RE and CORE_OK the same in disable.ps1 and recovery.py (tests/test_core_packages.py).
CORE_RE='^(android|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*)$'
CORE_OK='^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$'
CORE=$(printf '%s\n' "$PKGS" | grep -E "$CORE_RE" | grep -vE "$CORE_OK" || true)
if [ -n "$CORE" ]; then
  { echo "refusing $LIST: it disables core Android packages, and without them the TV may not boot again (#884):"
    printf '%s\n' "$CORE" | sed 's/^/  /'
    echo "Take these lines out of the list (or comment them) and run it again. Nothing was changed on the TV."; } >&2
  exit 1
fi
# A Wi-Fi network saved during the first-run wizard belongs to setupwraith, and Android deletes it the
# moment setupwraith is disabled: the TV drops off Wi-Fi, and ADB and the phone remote go with it.
# Skip it until the network has been forgotten and re-joined in Settings (it then belongs to
# android.uid.system). FORCE_SETUPWRAITH=1 disables it anyway, e.g. on Ethernet.
SETUP=com.google.android.tungsten.setupwraith
if printf '%s\n' "$PKGS" | grep -x "$SETUP" >/dev/null && [ -z "${FORCE_SETUPWRAITH:-}" ]; then
  # Be safe: if we cannot read the owners (tv.py failed, timed out, or showed no network at all),
  # do not disable setupwraith, so the TV cannot lose the network it is on.
  WIFI="$("$PY" -I tools/tv.py "dumpsys wifi")" || WIFI=""
  OWNERS="$(printf '%s\n' "$WIFI" | grep -o 'cname=[^ ]*' || true)"
  if [ -z "$OWNERS" ]; then
    echo "Skipping $SETUP: could not check which app owns the saved Wi-Fi networks (dumpsys wifi failed" >&2
    echo "or showed none). Try again, or set FORCE_SETUPWRAITH=1 if the TV uses Ethernet." >&2
    PKGS="$(printf '%s\n' "$PKGS" | grep -vx "$SETUP" || true)"
  # no grep -q here: with pipefail its early exit would SIGPIPE the grep before it and fail open
  elif printf '%s\n' "$OWNERS" | grep setupwraith >/dev/null; then
    echo "Skipping $SETUP: the saved Wi-Fi network belongs to it, and disabling it would delete that" >&2
    echo "network and take the TV off Wi-Fi. On the TV, forget the network and join it again in" >&2
    echo "Settings > Network & Internet, then run this again (or set FORCE_SETUPWRAITH=1 on Ethernet)." >&2
    PKGS="$(printf '%s\n' "$PKGS" | grep -vx "$SETUP" || true)"
  fi
fi

# On firmware with ro.adb.secure=0, port 5555 gives a shell to any device on the network without the
# "Allow" prompt. Read it now and say so once the list is done; a failed read only skips the note.
ADB_SECURE="$("$PY" -I tools/tv.py "getprop ro.adb.secure" 2>/dev/null | tr -d '[:space:]')" || ADB_SECURE=""

# Home guard (#239): a list that disables the app owning Home leaves the TV with nothing to start the
# next time Home is pressed, and on Google TV the setup wizard can take the category instead and
# crash-loop system_server (docs/TROUBLESHOOTING.md). Skip the owner while nothing else on the TV can
# answer HOME; FORCE_HOME=1 disables it anyway, for a TV reachable another way.
HOME_QUERY='cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
CLAIM_QUERY='cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
# Launchers the install script knows as stock Home; the last resort when the TV cannot say who answers HOME.
KNOWN_LAUNCHERS="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher com.mitv.tvhome"
PKG_RE='^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$'
drop_pkg() { PKGS="$(printf '%s\n' "$PKGS" | grep -vxF "$1" || true)"; }
# Every package with a usable HOME activity the TV can start now (disabled packages do not answer, so
# this is "what could take Home if the list went through"); fails when the TV answers nothing.
# The setup wizard and the settings apps answer HOME too, but they are not launchers: on Google TV,
# setupwraith taking Home is the crash loop this guard exists to prevent. They don't count.
# A vendor's own fallback does not count either: TCL Android TV sets register
# com.tcl.keycustomfunctionservice/.FallbackHome (and com.android.tv.settings/.system.FallbackHome),
# a placeholder the firmware shows when no launcher is enabled, not a home the viewer can use.
# Nor does a factory-test app: on a Xiaomi Mi TV 4A com.duokan.factorytest/.FactoryLauncher answers
# HOME, and handing Home to it leaves the viewer on the factory test screen.
home_launchers() {
  local raw claims
  raw=$("$PY" -I tools/tv.py "$CLAIM_QUERY" 2>/dev/null) || return 1
  claims=$(printf '%s\n' "$raw" | tr -d '\r' | grep -oE '^ *[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' | sed -E 's/^ +//' | sort -u || true)
  [ -n "$claims" ] || return 1
  printf '%s\n' "$claims" | grep -vE '/[^ ]*FallbackHome$' | cut -d/ -f1 | sort -u \
    | grep -viE 'factorytest|factory_test|factorymode' \
    | grep -vxF -e com.google.android.tungsten.setupwraith -e com.android.tv.settings -e com.android.settings -e com.android.provision || true
}
HOME_PKG=""
HOME_CHOOSER=""
if [ -z "${FORCE_HOME:-}" ] && [ -n "$PKGS" ]; then
  HOME_PKG=$("$PY" -I tools/tv.py "$HOME_QUERY" 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1) || HOME_PKG=""
  # "android" is the chooser: the TV did answer, Home just has no default. Remember it for the message
  # below, which runs after HOME_PKG is cleared.
  [ "$HOME_PKG" = "android" ] && HOME_CHOOSER=1
  # `cmd` arrived with Android 7 (Nougat): on an older set the query answers nothing, or the shell's own
  # "cmd: not found", and the guard below was skipped without a word - which is how a list disabled the
  # only launcher on a KitKat TV that had no replacement to hand Home over to (#795). Ask the framework
  # instead, which has printed the package holding Home since 4.x:
  #   mHomeProcess: ProcessRecord{52f4a5c8 2f4b:com.example.launcher/u0a56}
  # Only a package-shaped answer counts; on a set where Home has not started yet the line says null.
  # grep -c, not -q: under pipefail an early exit could SIGPIPE printf and fail open (see above).
  HOME_SHAPE=$(printf '%s\n' "$HOME_PKG" | grep -cE "$PKG_RE" || true)
  if [ "${HOME_SHAPE:-0}" -eq 0 ]; then
    HOME_PKG=$("$PY" -I tools/tv.py 'dumpsys activity' 2>/dev/null | tr -d '\r' \
      | sed -nE 's/.*mHomeProcess:.*[: ]([A-Za-z0-9_.]+)\/[A-Za-z0-9_.]+.*/\1/p' | head -n 1 || true)
    HOME_SHAPE=$(printf '%s\n' "$HOME_PKG" | grep -cE "$PKG_RE" || true)
    # Diagnostic text is not an owner; use the same fail-closed path as an empty answer.
    [ "${HOME_SHAPE:-0}" -gt 0 ] || HOME_PKG=""
  fi
  if [ -n "$HOME_PKG" ]; then
    # The extra query is worth it only when the list holds Home's own package, which is how a list
    # that leaves the TV without a launcher looks before anything is disabled.
    # grep without -q: an early exit could SIGPIPE printf and fail open under pipefail (see above).
    if printf '%s\n' "$PKGS" | grep -xF "$HOME_PKG" >/dev/null; then
      # Nothing left to answer Home once the list is applied, or the TV could not say who does: either
      # way the owner is the only Home this run knows about, so it stays enabled.
      LAUNCHERS=$(home_launchers || true)
      OTHERS=$(printf '%s\n' "$LAUNCHERS" | sed '/^$/d' | grep -vxF "$HOME_PKG" | grep -vxF "$PKGS" || true)
      if [ -z "$OTHERS" ]; then
        echo "Skipping $HOME_PKG: it owns Home, and no other app that can answer Home is installed and" >&2
        echo "staying enabled. Make the new launcher Home first (launcher/build-and-install.sh), then run" >&2
        echo "this list again to disable the old one; or set FORCE_HOME=1 if you can still reach the TV." >&2
        drop_pkg "$HOME_PKG"
      fi
    fi
  else
    # No single owner: Android's chooser ("android", launchers tie), or neither query named one.
    # Skipping the guard here would let a list take every launcher, so fail closed: judge by the HOME
    # claimants, and if the TV cannot list those either, keep every known stock launcher.
    if LAUNCHERS=$(home_launchers); then
      LISTED=$(printf '%s\n' "$LAUNCHERS" | sed '/^$/d' | grep -xF "$PKGS" || true)
      OTHERS=$(printf '%s\n' "$LAUNCHERS" | sed '/^$/d' | grep -vxF "$PKGS" || true)
      if [ -n "$LISTED" ] && [ -z "$OTHERS" ]; then
        for p in $LISTED; do
          echo "Skipping $p: Home has no default, and once the list is applied no other app could" >&2
          echo "answer Home. Make the new launcher Home first, or set FORCE_HOME=1." >&2
          drop_pkg "$p"
        done
      fi
    else
      for p in $KNOWN_LAUNCHERS; do
        printf '%s\n' "$PKGS" | grep -xF "$p" >/dev/null || continue
        echo "Skipping $p: could not ask the TV which apps answer Home, and it is a stock launcher." >&2
        echo "Run again once the TV answers, or set FORCE_HOME=1 if you can still reach the TV." >&2
        drop_pkg "$p"
      done
    fi
  fi
fi
# Neither query named Home, so the guard above could only go by the apps that answer Home. Say so: a
# silent fallback looks exactly like a check that passed, and on a set too old for the launcher there
# is nothing to lose by keeping every listed package (#795).
if [ -n "$PKGS" ] && [ -z "$HOME_PKG" ] && [ -z "${FORCE_HOME:-}" ]; then
  if [ -n "$HOME_CHOOSER" ]; then
    echo "Home has no default on this TV (resolve-activity answered \"android\", the chooser), so the Home" >&2
    echo 'guard went by the apps that answer Home instead.' >&2
  else
    echo "Could not tell which app owns Home on this TV, so the Home guard went by the apps that answer" >&2
    echo 'Home instead: neither "cmd package resolve-activity" (added in Android 7) nor "dumpsys activity"' >&2
    echo 'named one.' >&2
  fi
  echo 'Check by hand that the list does not hold your launcher before running it. The replacement' >&2
  echo 'launcher needs Android 8.0 (minSdk 26), so on an older set the stock launcher is the only Home' >&2
  echo 'there is: disabled, it leaves nothing to start, and a set with no recovery has no way back (#795).' >&2
fi

# Only the package's own "requested permissions" block counts: a vendor app in a shared UID would otherwise
# match through its shared user's permissions and be skipped by mistake.
# A package that asks for android.permission.TV_INPUT_HARDWARE is usually the software layer under the
# HDMI, AV and tuner inputs: routing, the HDCP handshake and scaling live in it. Disable one and the port
# still notices the cable but shows "No Signal" for good; re-enabling the pieces in batches does not bring
# the picture back, a full restore and a cold boot do (#321, OnePlus TV on Android 11). Keep those
# packages; TVINPUT_ALLOW=1 disables them anyway.
# The permission alone is a loose test and does produce false positives: on a Vestel shandao set the
# MediaTek USB media player asks for it and implements no input at all. The decisive check is which
# packages serve the service, `pm query-services --components -a android.media.tv.TvInputService`, but it
# stays out of this guard on purpose: the command is missing on the older firmware this repo also covers,
# and a guard that fails open costs someone their HDMI ports. So the skip still goes by the permission,
# and the message tells the owner how to tell a false positive from the real thing.
if [ -n "$PKGS" ] && [ -z "${TVINPUT_ALLOW:-}" ]; then
  # One round trip: the loop runs on the TV, where `dumpsys package` prints the permissions a package
  # asks for. Every name was checked as a bare package name above, so none of them can add shell syntax
  # here. </dev/null so the client cannot read this script's stdin (that is the piped batch below);
  # tr -d '\r' because adb can end lines with CRLF, which would leave a CR on every name.
  NAMES="$(printf '%s ' $PKGS)"
  # Trailing `; true`: the loop's status is the last package's `grep -q`, which is nonzero whenever
  # the last listed package is not an input driver — the common case — and would look like a failed
  # check. Only a real failure (tv.py cannot reach the TV) should reach the warning below.
  if TV_HW="$("$PY" -I tools/tv.py \
      "for p in $NAMES; do dumpsys package \$p | sed -n -E '/requested permissions:/,/(install|runtime) permissions:|User [0-9]+:/p' | grep -q TV_INPUT_HARDWARE && echo \$p; done; true" \
      </dev/null 2>/dev/null | tr -d '\r')"; then
    for p in $TV_HW; do
      # grep without -q: with pipefail an early exit could SIGPIPE printf and fail the run (see above).
      printf '%s\n' "$PKGS" | grep -xF "$p" >/dev/null || continue
      echo "Skipping $p: it asks for android.permission.TV_INPUT_HARDWARE, so it is the software layer" >&2
      echo "under the HDMI/AV/tuner inputs. With it disabled those inputs still notice the cable but stay" >&2
      echo "black (\"No Signal\"), and re-enabling the pieces in batches does not restore the picture: a" >&2
      echo "full restore plus a cold boot does (#321)." >&2
      echo "Asking for the permission is not proof, though: a preloaded app can request it and drive no" >&2
      echo "input at all. What settles it is which packages actually serve the input service:" >&2
      echo "  pm query-services --components -a android.media.tv.TvInputService" >&2
      echo "If $p is absent from that list it is a false positive on your set, as a MediaTek USB media" >&2
      echo "player turned out to be (examples/cecotec-shandao). Check every input after a reboot before" >&2
      echo "trusting it, then set TVINPUT_ALLOW=1 to disable it." >&2
      PKGS="$(printf '%s\n' "$PKGS" | grep -vxF "$p" || true)"
    done
  else
    # Do not skip anything on a guess, but say the check did not run: a black input is easy to blame on
    # the cable, and the ports are the one thing the owner cannot test from here.
    echo "Could not check which listed packages drive the HDMI/AV inputs (tv.py failed). Check every" >&2
    echo "input after this run: a port that notices the cable and stays black means one of them was" >&2
    echo "disabled, and only a full restore and a cold boot bring it back (#321)." >&2
  fi
fi

# The launcher switches to an HDMI input with ACTION_VIEW on a passthrough channel URI (type
# vnd.android.cursor.item/channel), and its Settings entry opens android.settings.SETTINGS. Whichever
# app answers those on this TV (Live TV, a vendor input app, the Settings app) has to stay: disabled,
# the input tiles do nothing and Settings does not open, which on a set with no Settings button on the
# remote leaves no way to turn ADB back on (a Sony KDL-43W800G lost its inputs, a Sony W800C its
# Settings). Ask the TV which apps answer instead of going by name; HANDLERS_ALLOW=1 disables them anyway.
if [ -n "$PKGS" ] && [ -z "${HANDLERS_ALLOW:-}" ]; then
  Q='cmd package query-activities --brief'
  # The two lists are split at the "==" line; `; true` so a firmware without a match still answers.
  if HANDLERS="$("$PY" -I tools/tv.py \
      "$Q -a android.intent.action.VIEW -t vnd.android.cursor.item/channel 2>/dev/null; echo ==; $Q -a android.settings.SETTINGS 2>/dev/null; true" \
      </dev/null 2>/dev/null | tr -d '\r')"; then
    NAME_RE='s#^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+)/.*#\1#p'
    INPUT_APPS="$(printf '%s\n' "$HANDLERS" | sed '/^==$/,$d' | sed -n -E "$NAME_RE" | sort -u)"
    SETTINGS_APPS="$(printf '%s\n' "$HANDLERS" | sed '1,/^==$/d' | sed -n -E "$NAME_RE" | sort -u)"
    for p in $INPUT_APPS; do
      printf '%s\n' "$PKGS" | grep -xF "$p" >/dev/null || continue
      echo "Skipping $p: the TV opens its HDMI/AV inputs and channels with it, so with it disabled the" >&2
      echo "launcher's input tiles do nothing. Set HANDLERS_ALLOW=1 to disable it anyway." >&2
      PKGS="$(printf '%s\n' "$PKGS" | grep -vxF "$p" || true)"
    done
    for p in $SETTINGS_APPS; do
      printf '%s\n' "$PKGS" | grep -xF "$p" >/dev/null || continue
      echo "Skipping $p: the TV opens Settings with it, so with it disabled Settings does not open (and" >&2
      echo "with it the way back to Developer options). Set HANDLERS_ALLOW=1 to disable it anyway." >&2
      PKGS="$(printf '%s\n' "$PKGS" | grep -vxF "$p" || true)"
    done
  else
    echo "Could not check which listed packages open the TV's inputs and Settings (tv.py failed). After" >&2
    echo "this run, open an HDMI input and Settings from the launcher before you put the remote down." >&2
  fi
fi

# The state undo needs goes to disk before the first command reaches the TV, and a failed save stops
# here with the TV untouched: a record written afterwards would describe a TV that is already broken,
# and a retry must not overwrite the one taken before the first change (#239).
"$PY" -I tools/recovery.py --save "$LIST"

{
  [ -n "$PKGS" ] && printf '%s\n' "$PKGS" | sed 's/^/pm disable-user --user 0 /'
  echo "pm list packages -d"
} | "$PY" -I tools/tv.py --batch

if [ "$ADB_SECURE" = "0" ]; then
  echo "Warning: this TV reports ro.adb.secure=0, so network ADB on port 5555 accepts any device on" >&2
  echo "your network without the \"Allow\" prompt. When you are done, turn USB debugging and" >&2
  echo "Network/ADB debugging off on the TV and reboot it (README: \"Isn't ADB access a security hole?\")." >&2
fi
