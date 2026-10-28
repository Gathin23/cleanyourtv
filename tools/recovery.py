#!/usr/bin/env python3
"""The state a debloat list is about to change, so that undo means undo (#239).

    recovery.py --save <debloat list>                read the TV and write the record
    recovery.py --commands <list> [stock launcher]   print a batch for tools/tv.py --batch that puts
                                                     the recorded states back

`tools/restore.sh` used to enable every package in the list, including ones the owner had already
disabled, and reset Home and the animation scales to fixed values instead of to what was there
before. Both scripts now go through this record: `disable.sh` writes it before the first command
reaches the TV, and `restore.sh` replays it.

Records live in ~/.cleanyourtv/, one per TV and list. The first one wins: a retry after a
half-applied list must not record the broken state as the original. The TV is the address it was
reached at, so a set that takes a new address gets a new record: save() warns when a record under
another address still holds, as enabled, a package it just found disabled (#2139). A later run, and check.py --fix,
only add the names the list gained since then, with their state before this list disabled them, and
record a package that was absent then and is installed and enabled now as enabled.
"""
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import sys
import time

# Every name from the TV or from the list ends up inside a command run by the TV's shell, so nothing
# that could carry shell syntax is allowed through.
PACKAGE_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")
HOME_RE = re.compile(r"[A-Za-z_][\w.]*/[\w.$]+")
SCALE_RE = re.compile(r"-?\d+(\.\d+)?")
SETTINGS = ("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
# This project's own launcher. When it already owned Home before debloating while the stock launcher
# was on, restoring Home to it would leave the stock launcher's Home unused, so restore goes back to the
# stock one instead. When the stock launcher was already off then, this launcher keeps Home (#1714).
CUSTOM_LAUNCHER = "com.gathin.tvhome"
# The stock launchers launcher/build-and-install.sh knows (its KNOWN_STOCK_LAUNCHERS). save() records
# the state of each one installed, listed or not, so restore turns one back on only when it was on
# before cleanup (#1714). tests/test_restore_stock_launcher.py keeps the two lists in step.
STOCK_LAUNCHERS = ("com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
                   "com.google.android.leanbacklauncher", "com.mitv.tvhome")
# What answers HOME without being a launcher, the cases disable.sh, disable.ps1 and check.py skip too:
# the setup wizards and the settings apps (on Google TV, setupwraith's .RecoveryActivity takes Home and
# system_server crash-loops on it), a vendor's FallbackHome, the placeholder a TCL or a Xiaomi shows
# when no launcher is enabled, and a factory-test app. tests/test_recovery_home_not_a_launcher.py
# keeps the copies in step.
NOT_LAUNCHERS = {"com.google.android.tungsten.setupwraith", "com.android.tv.settings",
                 "com.android.settings", "com.android.provision"}
FALLBACK_HOME_RE = re.compile(r"/\S*FallbackHome$")
FACTORY_HOME_RE = re.compile(r"factorytest|factory_test|factorymode", re.I)
VERSION = 1
# Core Android packages no list may disable: with one of these off, Android itself can stop booting
# (#884: an original Mi TV Stick that lost its permission controller, package installer, ext services,
# module metadata and mainline overlays never came back). The content providers in CORE_OK are the
# exception, since tested lists turn them off and no TV feature uses them. Ext services and ext shared
# also ship under AOSP's bare names, android.ext.services and android.ext.shared (#1711: an OBE R3
# Ultra, a CVTE SMART_TV and a MIBOX4 list them that way). disable.sh and disable.ps1
# carry the same two patterns (tests/test_core_packages.py keeps the three in step).
# The TV middleware too: com.cltv.mal (a Haier H50H5F looped with it off, #1578), com.mediatek.tis, tv.service, tv.agent.
CORE_RE = re.compile(r"^(android|android\.ext\.(services|shared)|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*|com\.cltv\.mal|com\.mediatek\.(tis|tv\.service|tv\.agent))$")
CORE_OK = re.compile(r"^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$")


def core_packages(names):
    """The names in a list that must never be disabled, in list order."""
    return [n for n in names if CORE_RE.match(n) and not CORE_OK.match(n)]


def read_list(path):
    """Package names from a debloat list, in file order.

    A BOM and CRLF from a Windows editor are dropped, as is a trailing `# note`; anything that is not
    a bare package name stops the run before the TV is touched.
    """
    # A list that cannot be read is the same mistake as a bad name further down it - a typo, or a
    # folder a git pull moved - and it is answered the same way: one line naming the path, before any
    # of the plan is printed and so before tools/tv.py --batch has a command to send. Left to
    # Path.read_text it was a 17-line traceback through pathlib: that is what `tools/restore.sh
    # <typo>` printed for the undo, while `tools/disable.sh <typo>` already said "cannot read the
    # list: <path>". OSError covers a missing file, a directory and a path with a file in the middle.
    try:
        text = Path(path).read_text(encoding="utf-8-sig", errors="replace")
    except OSError as e:
        raise SystemExit(f"could not read {path} ({e.strerror or e}). Nothing was sent to the TV: "
                         f"pass the debloat list, e.g. examples/<your tv>/debloat.txt")
    names = []
    for number, line in enumerate(text.splitlines(), 1):
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        name = line.split()[0]
        if not PACKAGE_RE.fullmatch(name):
            raise SystemExit(f"{path}:{number}: not a package name: {name!r}")
        names.append(name)
    if not names:
        raise SystemExit(f"{path}: no package names in this list")
    return names


def split_host_port(endpoint):
    """host:port or [ipv6]:port as (host, port or None), the way tools/tv.py reads TV_HOST."""
    if endpoint.startswith("["):
        end = endpoint.find("]")
        if end != -1:
            rest = endpoint[end + 1:]
            return endpoint[1:end], (rest[1:] if rest.startswith(":") else None)
    elif endpoint.count(":") == 1:
        host, port = endpoint.split(":", 1)
        return host, port
    return endpoint, None


def lan_address(host):
    """The TV's IP address when TV_HOST names a TV on the network, else None.

    A loopback address (a relay, tools/relay.md) and a host name keep TV_HOST exactly as typed as the
    record's name, as they always have; TV_PORT is not part of that name.
    """
    name, _ = split_host_port(host or "")
    try:
        return None if ipaddress.ip_address(name).is_loopback else name
    except ValueError:
        return None


def list_digest(list_path):
    """The eight hex characters in a record's name that identify the list it was taken for."""
    return hashlib.sha1(str(Path(list_path).resolve()).encode()).hexdigest()[:8]


def record_name(list_path, tag):
    digest = list_digest(list_path)
    tag = re.sub(r"[^A-Za-z0-9.-]", "_", tag or "no-host")
    return Path.home() / ".cleanyourtv" / f"recovery-{tag}-{digest}.json"


def records_under_other_addresses(list_path, path):
    """The other records taken for this same list, as (tag, path), the file this run writes left out.

    A record is one file per TV and list, and on the network the TV is the address it was reached at,
    so a set that takes a new DHCP lease leaves its record behind under the old address: the next run
    finds no record for the new one and writes a fresh one out of a TV that is already cleaned (#2139).
    """
    folder = path.parent
    if not folder.is_dir():
        return []
    digest = list_digest(list_path)
    suffix = f"-{digest}.json"
    found = []
    for other in sorted(folder.glob(f"recovery-*{suffix}")):
        if other.name == path.name:
            continue
        found.append((other.name[len("recovery-"):-len(suffix)], other))
    return found


def cleaned_by_another_record(list_path, path, disabled):
    """The record for this list, under another address, that reads like this TV before an earlier run.

    A package this run finds disabled, which that record holds as enabled before its own cleanup, is
    the shape of the same TV at a new address: the earlier run disabled it, and the record it left is
    the only place that still says it was on. A second TV cleaned with the same list is the other
    reading, and the warning says so rather than being sure here. A record that cannot be read is left
    to the run that owns it: this is one more line of output, not a reason to stop.
    """
    found = []
    for tag, other in records_under_other_addresses(list_path, path):
        try:
            with open(other) as handle:
                record = json.load(handle)
        except (OSError, ValueError):
            continue
        recorded = record.get("packages") if isinstance(record, dict) else None
        if not isinstance(recorded, dict):
            continue
        names = sorted(name for name, state in recorded.items()
                       if state == "enabled" and name in disabled)
        if names:
            found.append((tag, other, names))
    return found


def record_path(list_path, host):
    """One record per TV and list. The list is identified by its absolute path, so a copy of the
    same file somewhere else gets its own record. On the network a TV is its IP address:
    TV_HOST=192.168.1.50, TV_HOST=192.168.1.50:5555 and TV_PORT=5555 reach the same TV, so they
    share one record, named without the port."""
    return record_name(list_path, lan_address(host) or host)


def find_record(list_path, host):
    """The record file for this TV and list: record_path's name, or the name older versions gave a
    record on the network, TV_HOST exactly as typed with its port (recovery-192.168.1.50_5555-...).

    Records under both names cannot be told apart from here: they may be this TV before and after
    cleanup, or two TVs that had this address in turn. So nothing is chosen for the owner, and the
    run stops before anything reaches the TV.
    """
    path = record_path(list_path, host)
    address = lan_address(host)
    if not address:
        return path
    port = split_host_port(host)[1] or os.environ.get("TV_PORT") or "5555"
    typed = f"[{address}]:{port}" if ":" in address else f"{address}:{port}"
    names = dict.fromkeys((path, record_name(list_path, typed), record_name(list_path, host)))
    found = [name for name in names if name.exists()]
    if not found:
        return path
    if len(found) > 1:
        raise SystemExit("this TV and list have two recovery records, under the name older versions "
                         "gave it and the one\nit gets now:\n" + "".join(f"  {name}\n" for name in found)
                         + "They may be this TV before and after cleanup, or two TVs that had this address "
                         "in turn.\nMove the one that is not this TV's state from before cleanup aside "
                         "deliberately, then run this again.\nNothing was changed on the TV.")
    if found[0] != path:
        sys.stderr.write(f"using {found[0]}, the name an older version gave this TV's record.\n")
    return found[0]


def is_chooser(home):
    """Whether a Home component is Android's chooser rather than a launcher.

    When Home has no default, HOME resolves to android/com.android.internal.app.ResolverActivity
    (#85). Its package, "android", is not a package name a record can hold, and that is the test
    here: a launcher always has one.
    """
    return bool(home) and not PACKAGE_RE.fullmatch(home.split("/", 1)[0])


def not_a_launcher(home):
    """Whether a Home component answers HOME without being a launcher to put back.

    Home on one of these is a TV that is already broken: setupwraith's recovery screen, a black
    placeholder, the factory test screen. Replayed as set-home-activity, it would pin Home there even
    with the stock launcher enabled again.
    """
    if not home:
        return False
    package = home.split("/", 1)[0]
    return (package in NOT_LAUNCHERS or bool(FALLBACK_HOME_RE.search(home))
            or bool(FACTORY_HOME_RE.search(package)))


def load_record(path):
    try:
        with open(path) as f:
            record = json.load(f)
    except (OSError, ValueError) as e:
        raise SystemExit(f"{path}: not a usable recovery record ({e}).\n"
                         "It is left where it is: move it aside deliberately, a retry must not "
                         "replace the state recorded before the first change.")
    if record.get("version") != VERSION or not isinstance(record.get("packages"), dict):
        raise SystemExit(f"{path}: unknown recovery record format; move it aside deliberately.")
    # Records written before save() left the chooser out hold it as Home, and "android" among the
    # packages with it. Everything else in them is the TV before cleanup, and moving the file aside
    # would lose that: restore would then enable every listed package, and a new disable.sh run would
    # record the TV after cleanup as the original. So the chooser and that one entry are dropped here,
    # which reads the record the way save() writes it now. Any other bad name is still refused below,
    # and extend_record() (from save() and check.py --fix) writes the record back this way only when the
    # list has gained names or a package recorded absent has since arrived.
    home = record.get("home")
    if isinstance(home, str) and HOME_RE.fullmatch(home) and is_chooser(home):
        record["packages"].pop(home.split("/", 1)[0], None)
        record["home"] = None
        sys.stderr.write(f"{path}: the recorded Home is Android's chooser ({home}), not a launcher; "
                         "reading it as no Home, so undo resolves the stock launcher.\n")
    # Older records can also hold a Home that is no launcher, which restore would pin Home to. Only the
    # Home is dropped: the package's entry is its state before cleanup, and the list may name it.
    elif isinstance(home, str) and HOME_RE.fullmatch(home) and not_a_launcher(home):
        record["home"] = None
        sys.stderr.write(f"{path}: the recorded Home is {home}, which is not a launcher; reading it "
                         "as no Home, so undo resolves the stock launcher.\n")
    # The record is replayed on the TV's shell, so it gets the same checks as the list and the TV's
    # answers did when it was written: a hand-edited or damaged file stops here, before any command.
    for name, state in record["packages"].items():
        if not PACKAGE_RE.fullmatch(name):
            raise SystemExit(f"{path}: not a usable recovery record (not a package name: {name!r}); "
                             "move it aside deliberately.")
        if state not in ("absent", "disabled", "enabled"):
            raise SystemExit(f"{path}: not a usable recovery record (unknown state for {name}: "
                             f"{state!r}); move it aside deliberately.")
    home = record.get("home")
    if home is not None and not (isinstance(home, str) and HOME_RE.fullmatch(home)):
        raise SystemExit(f"{path}: not a usable recovery record (not a Home component: {home!r}); "
                         "move it aside deliberately.")
    if not isinstance(record.get("settings", {}), dict):
        raise SystemExit(f"{path}: not a usable recovery record (settings is not a mapping); "
                         "move it aside deliberately.")
    return record


def write_record(path, record):
    # Written next to its final name first: a crash halfway through must not leave a truncated record
    # that then blocks every retry as "already recorded".
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "w") as f:
        json.dump(record, f, indent=2, sort_keys=True)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, path)


def shell(dev, command):
    return (dev.shell(command, read_timeout_s=120.0) or "").strip()


def package_names(answer):
    return {line.split(":", 1)[1].strip() for line in answer.splitlines()
            if line.startswith("package:")}


def query_packages(dev):
    """The packages installed for user 0, and the ones disabled for user 0.

    Every command these tools send acts on user 0: `pm disable-user --user 0`, and `pm enable`,
    which means user 0 when no user is given. Without --user, Android 14's pm answers for every user
    on the TV and merges the answers (AOSP's android11-release still answered for user 0 alone), so
    on a TV with a second user a package that only the other user has disabled shows under -d, and
    one installed only for the other user shows as installed. The Chromecast with Google TV 4K this
    was tried on has such a user, a dormant profile with the id 10. Recorded from the merged answers,
    a package the owner had on for user 0 would be "disabled", and restore would leave it off for
    good. check.py reads the same two sets, so its drift report and its read-back after --fix are
    about user 0 too.
    """
    scope = " --user 0"
    installed = package_names(shell(dev, "pm list packages --user 0"))
    if not installed:
        # A pm that does not take --user prints an error or its usage instead of package lines, and
        # only that falls back to the plain queries. An empty -d --user 0 is no reason to: a TV
        # nothing was disabled on yet answers exactly that, and the merged -d would bring the other
        # user's disabled packages into the record on the very first run.
        scope = ""
        installed = package_names(shell(dev, "pm list packages"))
    if not installed:
        raise SystemExit("the TV returned no packages, so nothing was recorded and nothing will be "
                         "disabled. Check the connection (tools/tv.py --probe) and try again.")
    disabled = package_names(shell(dev, f"pm list packages -d{scope}"))
    return installed, disabled


def query_home(dev):
    answer = shell(dev, "cmd package resolve-activity --brief -a android.intent.action.MAIN -c "
                        "android.intent.category.HOME")
    candidate = answer.splitlines()[-1].strip() if answer else ""
    # Only a component counts: "No activity found" when nothing handles HOME, or the shell's error on
    # firmware older than `cmd`, comes back as None. Android's chooser does pass, though: when Home has
    # no default the last line is android/com.android.internal.app.ResolverActivity (#85). check.py
    # reports that as the Home it found, and save() keeps it out of the record, because the chooser is
    # no launcher to put back and must never be written into a set-home-activity command.
    return candidate if HOME_RE.fullmatch(candidate) else None


def connect():
    # Imported here so --commands works on a machine where adb_shell is not installed: printing the
    # restore batch needs no TV. tv.py itself exits with a readable message when TV_HOST is unset.
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    try:
        import tv
    except ImportError as e:
        raise SystemExit(f"cannot load tools/tv.py ({e}).\nrun: python3 -m venv tools/venv && "
                         "tools/venv/bin/pip install -r tools/requirements.txt")
    return tv.connect()


def package_state(name, installed, disabled):
    return ("absent" if name not in installed
            else "disabled" if name in disabled
            else "enabled")


def extend_record(path, record, names, installed, disabled):
    """Bring an existing record up to date before the list is applied to the TV again.

    save() runs this for disable.sh, and check.py --fix runs it before it disables anything, because
    restore puts back only what the record holds. The list may have been edited since the record was
    taken. Entries already in the record are never overwritten (a retry must not record the broken
    state as the original), but names it does not hold yet have not been touched by this list, so
    their state now is their state before cleanup. So is the state of a package recorded as absent
    that is installed and enabled now: an update or the Play Store put it there after the record was
    taken. Without them, restore would leave those packages disabled for good. One that is installed
    but disabled stays absent, as nobody can say what disabled it, and restore leaves both alone.
    The file is written only when something changed. Returns the names added and the ones that
    arrived.
    """
    states = record["packages"]
    arrived = [name for name in names if states.get(name) == "absent"
               and package_state(name, installed, disabled) == "enabled"]
    added = [name for name in names if name not in states]
    for name in added:
        states[name] = package_state(name, installed, disabled)
    for name in arrived:
        states[name] = "enabled"
    if not added and not arrived:
        return added, arrived
    write_record(path, record)
    if added:
        print(f"added {len(added)} names the list gained since then to the recovery record: "
              f"{', '.join(added)}")
    if arrived:
        print(f"recorded {len(arrived)} as enabled, absent when the record was taken and installed "
              f"since: {', '.join(arrived)}")
    print("every other entry keeps the state from before the first run.")
    return added, arrived


def save(list_path, host):
    path = find_record(list_path, host)
    if path.exists():
        record = load_record(path)
        packages = read_list(list_path)
        print(f"keeping the recovery record from {record.get('created')}: {path}")
        # Only a name the record does not hold, or holds as absent, can change it (extend_record), and
        # only then is the TV asked.
        if all(record["packages"].get(name, "absent") != "absent" for name in packages):
            print("nothing was recorded again: it holds the state from before the first run.")
            return 0
        dev = connect()
        try:
            installed, disabled = query_packages(dev)
        finally:
            dev.close()
        added, arrived = extend_record(path, record, packages, installed, disabled)
        if not added and not arrived:
            print("nothing was recorded again: it holds the state from before the first run.")
        return 0

    packages = read_list(list_path)
    dev = connect()
    try:
        installed, disabled = query_packages(dev)
        home = query_home(dev)
        scales = {key: shell(dev, f"settings get global {key}") for key in SETTINGS}
    finally:
        dev.close()

    # Android's chooser, android/com.android.internal.app.ResolverActivity, answers HOME when Home has
    # no default. Recorded, it would put "android" among the packages, which is not a package name a
    # record can hold. A Home whose package the record cannot hold is left out instead, and restore
    # then resolves the stock launcher, as it does when nothing answered HOME.
    chooser = home if is_chooser(home) else None
    if chooser:
        home = None

    # The launcher that owns Home may not be in the list, but it is what undo has to put back.
    home_package = home.split("/", 1)[0] if home else None
    targets = packages + ([home_package] if home_package and home_package not in packages else [])
    # So may an installed stock launcher: undo needs its state from before, so that it turns it back
    # on only when it was on (#1714). One the factory had disabled (examples/verizon-stream-tv), or
    # that launcher/build-and-install.sh had disabled before this list, is recorded as disabled and
    # stays off.
    targets += [name for name in STOCK_LAUNCHERS if name in installed and name not in targets]
    # A Home that is no launcher (setupwraith's recovery screen, a FallbackHome, a factory test) is not
    # recorded as Home either: restore would pin Home to it with the stock launcher enabled again, so
    # it resolves the stock launcher instead. Its package keeps the entry above: that state is real.
    not_launcher = home if not_a_launcher(home) else None
    if not_launcher:
        home = None
    record = {
        "version": VERSION,
        "created": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "host": host,
        "list": str(Path(list_path).resolve()),
        "home": home,
        "settings": scales,
        "packages": {name: package_state(name, installed, disabled) for name in targets},
    }
    write_record(path, record)

    already = sum(1 for state in record["packages"].values() if state == "disabled")
    print(f"recorded {len(record['packages'])} packages on {host} "
          f"({already} already disabled, left alone by undo) -> {path}")
    if chooser:
        print(f"HOME before: {chooser}, Android's chooser (Home has no default), so no Home was "
              "recorded")
    elif not_launcher:
        print(f"HOME before: {not_launcher}, which is not a launcher (a setup wizard, a settings "
              "app, a FallbackHome or a factory test), so no Home was recorded")
    else:
        print(f"HOME before: {home or 'no component answered'}")
    print("undo with tools/restore.sh <same list>; keep this file, it is the only copy.")
    # A fresh record is written whenever this address has none, which is also what a TV that took a
    # new DHCP lease looks like: the record from before its cleanup is still under the old address,
    # and nothing in it reaches undo from here (#2139). Say so while the owner is still at the TV.
    for tag, other, names in cleaned_by_another_record(list_path, path, disabled):
        shown = ", ".join(names[:5]) + (", ..." if len(names) > 5 else "")
        sys.stderr.write(
            f"warning: a new record was written for this list, and one for the same list is under "
            f"{tag}:\n"
            f"  {other}\n"
            f"{len(names)} of the packages disabled now are recorded there as enabled before an "
            f"earlier cleanup ({shown}).\n"
            "If this is that TV on a new address (a new DHCP lease, or the Wi-Fi forget and re-join "
            "the setupwraith step asks for), undo run from here leaves those off. Run tools/restore.sh "
            "at the old address first, or move that record to "
            f"~/.cleanyourtv/{path.name} once you are sure it is this TV.\n"
            "If this is a second set cleaned with the same list, nothing is wrong: both records "
            "stay.\n")
    return 0


def restore_commands(list_path, stock, host):
    """The batch that puts the recorded states back, one command per line."""
    packages = read_list(list_path)
    if stock and not PACKAGE_RE.fullmatch(stock):
        raise SystemExit(f"not a package name: {stock!r}")
    path = find_record(list_path, host)
    commands = []

    if not path.exists():
        sys.stderr.write(
            f"no recovery record for this TV and list ({path}).\n"
            "Enabling every listed package and resetting Home and the animation scales to their\n"
            "defaults. Packages that were already disabled before cleanup will be enabled too:\n"
            "check the list before running this.\n")
        for name in packages:
            commands.append(f"pm enable {name}")
        if stock:
            commands.append(f"pm enable {stock}")
        commands.append(f"cmd package set-home-activity --user 0 $(cmd package resolve-activity "
                        f"--brief -a android.intent.action.MAIN -c android.intent.category.HOME "
                        f"{stock or 'com.google.android.tvlauncher'} | tail -n 1)")
        commands.extend(f"settings put global {key} 1.0" for key in SETTINGS)
        commands.append("pm list packages -d")
        return commands

    record = load_record(path)
    states = record["packages"]
    re_enable = [name for name, state in states.items() if state == "enabled"]
    for name in re_enable:
        commands.append(f"pm enable {name}")
    untouched = [(name, state) for name, state in states.items() if state != "enabled"]
    for name, state in untouched:
        sys.stderr.write(f"  leaving {name} alone ({state} when the record was taken)\n")

    # The stock launcher comes back only when the record holds it as enabled, which re_enable above
    # already covers (#1714): restore enables what was on before cleanup and nothing else. One the
    # record holds as disabled was off before this list (the factory, or launcher/build-and-install.sh
    # before the list), and one it does not hold at all (a record taken before save() noted the stock
    # launchers) cannot be said to have been on, so neither is turned on here.
    fallback = stock or "com.google.android.tvlauncher"
    fallback_state = states.get(fallback)

    home = record.get("home")
    if home and home.split("/", 1)[0] != CUSTOM_LAUNCHER:
        commands.append(f"cmd package set-home-activity --user 0 {home}")
    elif home and fallback_state != "enabled":
        # This project's launcher owned Home and the stock launcher was not on: Home stays where it
        # was. Its package is in the record as enabled, so it was enabled above.
        commands.append(f"cmd package set-home-activity --user 0 {home}")
        sys.stderr.write(f"  Home goes back to {home}, which owned it when the record was taken: "
                         f"{fallback} was {fallback_state or 'not in the record'} then, so it stays as "
                         f"it is. To have it back: tools/tv.py 'pm enable {fallback}', then make it "
                         f"Home.\n")
    elif not home and fallback_state in ("disabled", "absent"):
        # No Home was recorded and the stock launcher was off or missing: there is nothing to resolve.
        sys.stderr.write(f"  leaving Home alone: none was recorded, and {fallback} was {fallback_state} "
                         f"when the record was taken\n")
    else:
        # Nothing handled HOME before cleanup (fresh TV, chooser only), only something that is no
        # launcher did, or this project's launcher did while the stock launcher was on, so there is no
        # stock component to replay; ask the TV to resolve the stock launcher the way restore.sh
        # always did.
        commands.append(f"cmd package set-home-activity --user 0 $(cmd package resolve-activity "
                        f"--brief -a android.intent.action.MAIN -c android.intent.category.HOME "
                        f"{fallback} | tail -n 1)")
    for key in SETTINGS:
        value = str(record.get("settings", {}).get(key, "")).strip()
        if value == "null":
            commands.append(f"settings delete global {key}")
        elif SCALE_RE.fullmatch(value):
            commands.append(f"settings put global {key} {value}")
        else:
            sys.stderr.write(f"  skipping {key}: the record holds {value!r}, not a number\n")
    # disable.sh and check.py --fix add names the list gained to the record before disabling them, so
    # a name still missing here was added after the last of those runs and never disabled by it:
    # leave it alone.
    edited = [name for name in packages if name not in states]
    if edited:
        sys.stderr.write(f"not in the record, leaving alone: {', '.join(edited)}\n")
    commands.append("pm list packages -d")

    sys.stderr.write(f"restoring the state recorded on {record.get('created')}: "
                     f"re-enabling {len(re_enable)}, leaving {len(untouched)} as they were.\n")
    return commands


def main():
    args = sys.argv[1:]
    host = os.environ.get("TV_HOST", "")
    if len(args) == 2 and args[0] == "--save":
        return save(args[1], host)
    if len(args) in (2, 3) and args[0] == "--commands":
        if not host:
            sys.stderr.write("Set TV_HOST to your TV's IP address, e.g. TV_HOST=192.168.1.50\n")
        stock = args[2] if len(args) == 3 else "com.google.android.tvlauncher"
        for command in restore_commands(args[1], stock, host):
            print(command)
        return 0
    sys.stderr.write("usage: recovery.py --save <debloat list>\n"
                     "       recovery.py --commands <debloat list> [stock launcher package]\n")
    return 2


if __name__ == "__main__":
    sys.exit(main())
