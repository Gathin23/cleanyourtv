#!/usr/bin/env python3
"""What a firmware update undid, compared with the list that was applied (#359).

    check.py <debloat list> [launcher package]        report the drift; exit 1 when there is any
    check.py --fix <debloat list> [launcher package]  re-disable what came back and put the launcher
                                                      back on Home

A firmware update re-enables listed packages, hands Home back to the stock launcher and adds new
preloads, and nothing in this repo notices: nobody runs disable.sh again, and restore.sh would undo
the cleanup instead of redoing it. This command compares the TV with the list and exits 1 when the
TV drifted, so it fits a cron job; --fix applies the list again. It never writes a recovery record of
its own (a record of the TV after cleanup would make undo leave the whole list disabled), but when
this TV and list have one, it first adds what disable.sh would: the names the list gained since then,
and a package the record calls absent that is installed and enabled now. Undo then still means undo.

It compares the TV with the list, not the record: the record holds what the TV looked like *before*
cleanup, which is what restore needs, while the target here is the list itself.

--fix runs the same safety checks as disable.sh before it disables anything (the Wi-Fi network
setupwraith owns, the packages under the HDMI/AV inputs), because a firmware update is exactly the
moment those come back into play: the update can turn setupwraith back on for a TV whose network it
owns, and a fix that skipped the checks would take that TV off Wi-Fi with no way back in.

Exit codes: 0 in sync (or fixed), 1 drift, an incomplete fix, or packages a safety check left on,
2 the TV or the list could not be read, which includes ADB timing out or failing partway (after which
a --fix may be partial, and the next check says what is left), a run with no TV_HOST set or no ADB
client to read the TV with, or the command line asked for something this script does not do, which
includes naming a launcher the TV does not have. That last one matters: README.md tells an owner to
run this from a weekly job and hear about it on exit 1, so a mistyped flag must not come back as
drift, nor a mistyped launcher as "nothing drifted". A recovery record --fix cannot read or update is
2 as well, with nothing changed on the TV.
"""
import os
import re
import sys

# The command line is judged before the list is read and before the TV is touched. `check.py --help`
# used to be taken as a list named `--help` and died in a FileNotFoundError traceback - exit 1, the
# code a weekly job reads as "the TV drifted" - and a flag typed where the launcher package goes
# reached set-home-activity as if it were a package name.
USAGE = ("usage: check.py <debloat list> [launcher package]        report the drift; exit 1 when there is any\n"
         "       check.py --fix <debloat list> [launcher package]  re-disable what came back and put the\n"
         "                                                        launcher back on Home\n"
         "       check.py --help                                   this text\n")

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import recovery  # noqa: E402  (needs the tools/ directory on the path first)

# Same three special cases as disable.sh: these answer HOME but they are not launchers, so Home
# landing on one of them is the crash loop the Home guard exists to prevent, not a working Home.
NOT_LAUNCHERS = {"com.google.android.tungsten.setupwraith", "com.android.tv.settings",
                 "com.android.settings", "com.android.provision"}
# A vendor's own fallback is a placeholder the firmware shows when no launcher is enabled, not a home
# the viewer can use: TCL sets register com.tcl.keycustomfunctionservice/.FallbackHome (and
# com.android.tv.settings/.system.FallbackHome). disable.sh already refuses to count one as a
# launcher (#435); --fix must not move Home onto one either, or the next boot lands in the placeholder.
FALLBACK_HOME_RE = re.compile(r"/\S*FallbackHome$")
# A factory-test app is not one either: com.duokan.factorytest/.FactoryLauncher answers HOME on a
# Xiaomi Mi TV 4A, and Home on it is the factory test screen (disable.sh skips these too).
FACTORY_HOME_RE = re.compile(r"factorytest|factory_test|factorymode", re.I)
# The first-run wizard's app. Android deletes a Wi-Fi network saved by it the moment it is disabled.
SETUPWRAITH = "com.google.android.tungsten.setupwraith"
INPUT_PERMISSION = "android.permission.TV_INPUT_HARDWARE"
# Only the package's own "requested permissions" block counts: a vendor app in a shared UID would
# otherwise match through its shared user's permissions, the same reason disable.sh scopes its grep.
REQUESTED_RE = re.compile(r"requested permissions:(.*?)"
                          r"(?:\n\s*(?:install|runtime) permissions:|\n\s*User \d+:|$)", re.S)
# Lollipop's dumpsys package prints no "requested permissions:" block, only the package's own
# "grantedPermissions:" (#1753): the fallback disable.sh reads when the requested block is absent.
GRANTED_RE = re.compile(r"grantedPermissions:(.*?)"
                        r"(?:\n\s*(?:install|runtime) permissions:|\n\s*User \d+:|$)", re.S)
HOME_QUERY = ("cmd package resolve-activity --brief -a android.intent.action.MAIN "
              "-c android.intent.category.HOME")
CLAIMS_QUERY = ("cmd package query-activities --brief -a android.intent.action.MAIN "
                "-c android.intent.category.HOME")


def home_of(component):
    return component.split("/", 1)[0] if component else None


def home_activities(dev):
    """Every HOME activity the TV can start, as package/activity, in the TV's order."""
    found = []
    for line in recovery.shell(dev, CLAIMS_QUERY).splitlines():
        candidate = line.strip()
        if recovery.HOME_RE.fullmatch(candidate):
            found.append(candidate)
    return found


def launcher_to_use(dev, listed, preferred=None):
    """The HOME activity --fix should switch to: asked for, or the only launcher the list keeps.

    After a firmware update both the stock launcher and the new one answer HOME. Every candidate
    that the list disables is a launcher this run is about to take Home away from, so what is left
    is the one the owner installed. A vendor FallbackHome answers HOME too and is not one of them.
    """
    if preferred:
        if not recovery.PACKAGE_RE.fullmatch(preferred):
            raise SystemExit(f"not a package name: {preferred!r}")
        # A package can register a FallbackHome next to its real Home; only the real one will do.
        match = [c for c in home_activities(dev)
                 if home_of(c) == preferred and not FALLBACK_HOME_RE.search(c)]
        if not match:
            raise SystemExit(f"{preferred} has no HOME activity on this TV: is the launcher "
                             f"installed? (launcher/build-and-install.sh)")
        return match[0]
    candidates = [c for c in home_activities(dev)
                  if home_of(c) not in listed and home_of(c) not in NOT_LAUNCHERS
                  and not FALLBACK_HOME_RE.search(c) and not FACTORY_HOME_RE.search(home_of(c))]
    if not candidates:
        return None
    own = [c for c in candidates if home_of(c) == recovery.CUSTOM_LAUNCHER]
    return own[0] if own else candidates[0]


def expected_launcher(installed, preferred=None):
    """The package Home should be on: the one asked for, else this repo's when it is installed.

    None when neither is installed: debloat without a launcher, where the stock launcher on Home is
    the intended state and only a Home on a listed package (or no Home at all) is drift.
    """
    wanted = preferred or recovery.CUSTOM_LAUNCHER
    return wanted if wanted in installed else None


def inspect(dev, listed, preferred=None):
    installed, disabled = recovery.query_packages(dev)
    home = recovery.query_home(dev)
    back_on = [name for name in listed if name in installed and name not in disabled]
    gone = [name for name in listed if name not in installed]
    # Home on a package the list disables is drift, and so is Home anywhere but the launcher we
    # expect: build-and-install.sh disables the stock launcher instead of naming it in the list, so
    # an update that turns it back on and hands it Home matches no line of the list at all (#434).
    expected = expected_launcher(installed, preferred)
    home_drifted = (home is None or home_of(home) in set(listed)
                    or (expected is not None and home_of(home) != expected))
    return installed, disabled, home, back_on, gone, home_drifted, expected


def report(list_path, listed, home, back_on, gone, home_drifted, expected=None):
    print(f"checking {list_path} ({len(listed)} packages) on {os.environ.get('TV_HOST', '?')}")
    if back_on:
        print(f"  back on ({len(back_on)}): {', '.join(back_on)}")
    if home_drifted:
        if not home:
            why = ""
        elif home_of(home) in set(listed):
            why = ", which this list disables"
        else:
            why = f", not {expected}"
        print(f"  Home is {home or 'nothing (no component answers HOME)'}{why}")
    if gone:
        # Not drift: the update may have removed an app for good, and there is nothing to disable.
        print(f"  not installed any more ({len(gone)}): {', '.join(gone)}")
    drift = len(back_on) + (1 if home_drifted else 0)
    if not drift:
        print(f"nothing drifted: {len(listed) - len(gone)} listed packages are still disabled and "
              f"Home is {home}")
        return 0
    print(f"drift: {drift} of {len(listed)}; re-apply the list with --fix, or run "
          f"tools/disable.sh and the install script by hand")
    return 1


def setupwraith_owns_wifi(dev):
    """Whether the saved Wi-Fi networks belong to setupwraith; None when the TV could not say."""
    owners = re.findall(r"cname=(\S*)", recovery.shell(dev, "dumpsys wifi"))
    if not owners:
        return None
    return any("setupwraith" in owner for owner in owners)


def asks_for_input_hardware(dev, name):
    """Whether the package itself asks for TV_INPUT_HARDWARE, i.e. it drives the HDMI/AV inputs.

    True or False from a dump that can be read; None when the dump shows neither a requested nor a
    granted permission block, so the check could not run — Android 5.x prints neither (#1753)."""
    dump = recovery.shell(dev, f"dumpsys package {name}")
    block = REQUESTED_RE.search(dump)
    if block:
        return INPUT_PERMISSION in block.group(1)
    granted = GRANTED_RE.search(dump)
    if granted:
        return INPUT_PERMISSION in granted.group(1)
    if "Service Resolver Table:" in dump:
        # Lollipop's per-package dump has no permission block but lists what serves the input
        # service: the decisive check the requested-permission grep stands in for.
        return "android.media.tv.TvInputService" in dump
    return None


def handler_apps(dev):
    """The apps the TV opens its inputs with (VIEW on a passthrough channel) and the ones it opens
    Settings with, as two sets of package names, the same query disable.sh makes."""
    q = "cmd package query-activities --brief"
    inputs = recovery.shell(dev, f"{q} -a android.intent.action.VIEW -t vnd.android.cursor.item/channel")
    settings = recovery.shell(dev, f"{q} -a android.settings.SETTINGS")
    names = lambda out: set(re.findall(r"^\s*([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/", out, re.M))
    return names(inputs), names(settings)


def safety_checks(dev, names):
    """The names disable.sh would leave enabled, each with the reason, as a list of (name, why).

    These are the checks disable.sh runs, in the same order, repeated here for the same reason they
    exist there: each one is a package an owner cannot put back from the couch. FORCE_SETUPWRAITH=1,
    TVINPUT_ALLOW=1 and HANDLERS_ALLOW=1 mean what they mean in disable.sh, so a TV on Ethernet or an
    owner who wants the inputs off does not have to remember more switches.
    """
    skipped = []
    if SETUPWRAITH in names and not os.environ.get("FORCE_SETUPWRAITH"):
        owns = setupwraith_owns_wifi(dev)
        if owns is not False:
            # Be safe when the TV could not say: a wrong guess here is the TV off the network.
            why = ("the saved Wi-Fi network belongs to it, so disabling it deletes that network and "
                   "takes the TV off Wi-Fi: on the TV, forget the network and join it again in "
                   "Settings > Network & Internet, then run --fix again (or set "
                   "FORCE_SETUPWRAITH=1 if it uses Ethernet)" if owns else
                   "could not read which app owns the saved Wi-Fi networks (dumpsys wifi failed or "
                   "showed none); set FORCE_SETUPWRAITH=1 if the TV uses Ethernet")
            skipped.append((SETUPWRAITH, why))
    if not os.environ.get("TVINPUT_ALLOW"):
        for name in names:
            if any(name == skipped_name for skipped_name, _ in skipped):
                continue
            hw = asks_for_input_hardware(dev, name)
            if hw is None:
                # Fail closed, the same as disable.sh: a dump this firmware cannot be read from
                # keeps the package on (#1753), and the owner is told what it costs.
                skipped.append((name, "could not check whether it drives the HDMI/AV inputs (the "
                                      "TV's dumpsys shows neither permission block, as Android 5.x "
                                      "prints neither): it stays enabled; set TVINPUT_ALLOW=1 to "
                                      "disable it once every input still works after a reboot"))
            elif hw:
                skipped.append((name, "it asks for android.permission.TV_INPUT_HARDWARE, so it is "
                                      "the software layer under the HDMI/AV/tuner inputs: with it "
                                      "disabled those ports stay black and only a full restore and "
                                      "a cold boot bring them back (set TVINPUT_ALLOW=1 to disable "
                                      "it)"))
    if not os.environ.get("HANDLERS_ALLOW") and any(n not in {k for k, _ in skipped} for n in names):
        inputs, settings = handler_apps(dev)
        if not settings and not inputs:
            # A working query always has some app answering android.settings.SETTINGS; an empty
            # answer means the firmware has no `cmd`, and the guard below would pass in silence.
            print("  could not check which listed packages open the TV's inputs and Settings: the "
                  "query answered with nothing (this firmware may have no 'cmd'); open an HDMI "
                  "input and Settings from the launcher after --fix")
        for name in names:
            if any(name == skipped_name for skipped_name, _ in skipped):
                continue
            if name in inputs:
                skipped.append((name, "the TV opens its HDMI/AV inputs and channels with it, so with "
                                      "it disabled the launcher's input tiles do nothing (set "
                                      "HANDLERS_ALLOW=1 to disable it)"))
            elif name in settings:
                skipped.append((name, "the TV opens Settings with it, so with it disabled Settings "
                                      "does not open (set HANDLERS_ALLOW=1 to disable it)"))
    return skipped


def fix(dev, listed, back_on, home_drifted, preferred):
    failed = False
    # Home first: on Google TV a disabled stock launcher with nothing to replace it is the crash
    # loop the Home guard exists to prevent, so a Home that cannot be fixed stops the run before
    # anything is disabled.
    if home_drifted:
        target = launcher_to_use(dev, listed, preferred)
        if not target:
            print("Home: nothing to switch to: the launcher is not installed, or nothing but the "
                  "packages this list disables answers HOME. Run launcher/build-and-install.sh, "
                  "then check again; nothing was changed on the TV.")
            return 1
        recovery.shell(dev, f"cmd package set-home-activity --user 0 {target}")
        home = recovery.query_home(dev)
        if home != target:
            # Stop here, as the comment above says: Home is still on what it was, which may be a
            # package this list disables, and disabling it now leaves the TV with setupwraith's
            # RecoveryActivity or a placeholder for Home (#1573).
            print(f"Home: asked for {target} but the TV still resolves {home or 'nothing'}; "
                  f"nothing was disabled. Reboot it and check again")
            return 1
        print(f"Home: set to {target}")
    left_on = []
    if back_on:
        left_on = safety_checks(dev, back_on)
        for name, why in left_on:
            print(f"leaving {name} on: {why}")
        todo = [name for name in back_on if name not in {n for n, _ in left_on}]
        for name in todo:
            recovery.shell(dev, f"pm disable-user --user 0 {name}")
        # Read the TV back instead of trusting the command's silence: a package the firmware pins
        # stays enabled and the run has to say so.
        _, disabled = recovery.query_packages(dev)
        still_on = [name for name in todo if name not in disabled]
        if still_on:
            failed = True
            print(f"still enabled after --fix: {', '.join(still_on)} (the firmware may pin them; "
                  f"re-run the list by hand after the next reboot)")
        elif todo:
            print(f"disabled {len(todo)} package(s) the update had turned back on")
        if left_on:
            # Not a failure of the fix, but not a clean run either: the list asks for something this
            # TV cannot give up safely, and the next check reports the same drift until the owner
            # clears the obstacle (the Wi-Fi network) or accepts it (TVINPUT_ALLOW=1).
            print(f"left {len(left_on)} package(s) on that this list disables; the TV is not fully "
                  f"back to the list until they are dealt with")
    return 1 if failed or left_on else 0


def main():
    args = sys.argv[1:]
    if any(a in ("-h", "--help") for a in args):
        sys.stdout.write(USAGE)
        return 0
    # Only --fix is a flag; anything else starting with a dash is refused here, where it is a
    # mistyped command, rather than passed on as a path (a traceback, exit 1) or as the launcher
    # package (a set-home-activity for something that cannot own Home).
    unknown = next((a for a in args if a.startswith("-") and a != "--fix"), None)
    if unknown:
        sys.stderr.write(f"check.py: no such flag: {unknown}\n{USAGE}")
        return 2
    wanted = "--fix" in args
    args = [a for a in args if a != "--fix"]
    if not 1 <= len(args) <= 2:
        sys.stderr.write(USAGE)
        return 2
    list_path = args[0]
    preferred = args[1] if len(args) == 2 else None
    # The launcher package is judged here as well, not only in launcher_to_use, which runs under
    # --fix and only once Home has drifted. A second argument that is no installed package turns the
    # Home check off (expected_launcher has nothing to compare Home with), so a typo there, or the
    # package/activity form the report prints Home in, came back as "nothing drifted", exit 0.
    if preferred and not recovery.PACKAGE_RE.fullmatch(preferred):
        if "/" in preferred and recovery.PACKAGE_RE.fullmatch(home_of(preferred)):
            why = f"{preferred} is a Home activity, not a package: pass {home_of(preferred)}"
        else:
            why = f"not a package name: {preferred!r}"
        sys.stderr.write(f"check.py: {why}\n{USAGE}")
        return 2
    # read_list validates every name before anything is sent to the TV: its entries end up in the
    # TV's shell, and a list edited by hand since then is not to be trusted.
    try:
        listed = recovery.read_list(list_path)
    except OSError as e:
        # A path typed wrong or a list that moved after a git pull is a command-line mistake, not a
        # TV that drifted: exit 2, not the 1 a weekly job reads as drift.
        sys.stderr.write(f"could not read {list_path} ({e.strerror or e}). Nothing was checked or changed: "
                         f"pass the debloat list this TV was cleaned with, e.g. "
                         f"examples/<your tv>/debloat.txt.\n")
        return 2
    except SystemExit as e:
        # The same for a line that is not a package name and for a list with every line commented
        # out: read_list stops with a message, which Python turns into exit 1.
        sys.stderr.write(f"{e}. Nothing was checked or changed.\n")
        return 2
    # The launcher argument has to be a Home the TV can stay on: not one this list disables (--fix would
    # set Home to it and then disable it), and none of what answers HOME without being a launcher. With
    # `check.py --fix <list> com.google.android.tungsten.setupwraith`, --fix set Home to setupwraith's
    # RecoveryActivity and called it fixed (#1573).
    if preferred and (preferred in listed or preferred in NOT_LAUNCHERS
                      or FACTORY_HOME_RE.search(preferred)):
        why = ("this list disables it" if preferred in listed else
               "it answers Home without being a launcher (a setup wizard, a settings app or a "
               "factory test)")
        sys.stderr.write(f"check.py: {preferred} cannot be the launcher Home is on: {why}. Pass the "
                         f"launcher Home should stay on, or no launcher at all. Nothing was checked "
                         f"or changed.\n")
        return 2
    core = recovery.core_packages(listed)
    if core:
        # The same refusal as disable.sh: --fix would disable these again (#884).
        sys.stderr.write(f"refusing {list_path}: it disables core Android or TV middleware packages, and without them "
                         f"the TV may not boot again (#884): {', '.join(core)}. Take these lines out "
                         f"of the list and run it again. Nothing was checked or changed.\n")
        return 2

    # adb_shell raises its own errors as plain Exception subclasses, not OSError:
    # TcpTimeoutException when nobody accepts the "Allow USB debugging?" prompt in time (after the
    # authorisations were revoked, or for a key the TV has not seen) or when the TV goes to standby
    # partway, and AdbTimeoutError and DeviceAuthError. Left alone they end the run with a traceback
    # and exit 1, which a cron job reads as drift, so every Exception here is exit 2, the TV could
    # not be read. The two shapes that stop the run before any connection are SystemExit rather than
    # Exception, so they need the clause below: tv.py stops with its own message when TV_HOST is
    # unset, and recovery.connect() stops with one when adb_shell is not installed. Both mean the TV
    # was never asked, and both used to answer 1 - "the TV drifted" for a job that never ran.
    probe = "tools/venv/bin/python -I tools/tv.py --probe"
    try:
        dev = recovery.connect()
    except SystemExit as e:
        # A string code is one of those two messages: print it and answer 2. Nothing else in tv.py
        # or recovery.py exits with a string, so a numeric one (or none) keeps its own meaning.
        if not isinstance(e.code, str):
            raise
        sys.stderr.write(f"{e.code}\nNothing was checked or changed.\n")
        return 2
    except (OSError, ConnectionError) as e:
        sys.stderr.write(f"could not reach the TV ({e}). Check it is on and TV_HOST is right "
                         f"({probe}).\n")
        return 2
    except Exception as e:
        # Usually the handshake: the port answered, but nobody accepted the TV's prompt in time.
        sys.stderr.write(f"could not read the TV ({type(e).__name__}: {e}). If it shows "
                         f"\"Allow USB debugging?\", accept it and run this again; otherwise check "
                         f"it is on and TV_HOST is right ({probe}).\n")
        return 2
    fixing = False
    try:
        installed, disabled, home, back_on, gone, home_drifted, expected = inspect(dev, listed,
                                                                                   preferred)
        if preferred and preferred not in installed:
            # The other half of the launcher check before the list was read: only the TV can say
            # whether the package is there.
            sys.stderr.write(f"{preferred} is not installed on this TV, so Home cannot be checked "
                             f"against it; there is no report and nothing was changed. Check the "
                             f"package name of the launcher Home should be on, and run this again.\n")
            return 2
        code = report(list_path, listed, home, back_on, gone, home_drifted, expected)
        if code and wanted:
            # restore.sh puts back only what the recovery record holds, so before anything is disabled,
            # a record this TV and list already have gets the entries a second disable.sh run would add
            # (recovery.extend_record). Without a record nothing is written: one taken now would hold
            # the TV after cleanup. A record that cannot be used stops the run before anything reaches
            # the TV, as it stops disable.sh.
            path = recovery.find_record(list_path, os.environ.get("TV_HOST", ""))
            if path.exists():
                try:
                    recovery.extend_record(path, recovery.load_record(path), listed, installed, disabled)
                except OSError as e:
                    sys.stderr.write(f"could not update the recovery record ({e}), so nothing was "
                                     f"changed on the TV: without it, restore.sh would leave what "
                                     f"--fix disables off.\n")
                    return 2
            fixing = True
            code = fix(dev, listed, back_on, home_drifted, preferred)
    except SystemExit as e:
        # recovery.query_packages stops with its own message when the TV answers no package at all:
        # the TV could not be read, so exit 2, not the 1 Python gives that message. find_record and
        # load_record do the same for a recovery record --fix cannot use. Under --fix the exits keep
        # their code, as above.
        if fixing:
            raise
        sys.stderr.write(f"{e}\n")
        return 2
    except Exception as e:
        why = f"{type(e).__name__}: {e}"
        if fixing:
            # The commands before the one that failed have run on the TV, that one may or may not
            # have, and the rest have not. Nothing was read back, so only a fresh check can say
            # where the fix stopped.
            sys.stderr.write(f"lost the TV during --fix ({why}), so the fix may be partial. Once "
                             f"the TV answers ({probe}), run check.py again to see what is left.\n")
        else:
            # No report from half the queries: what was read so far could pass for a TV in sync.
            sys.stderr.write(f"could not read the TV ({why}), so there is no report and nothing "
                             f"was changed. Check it is on and still on the network ({probe}), "
                             f"then run this again.\n")
        return 2
    finally:
        dev.close()
    return code


if __name__ == "__main__":
    sys.exit(main())
