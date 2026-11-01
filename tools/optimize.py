#!/usr/bin/env python3
"""Keep the apps used most compiled for a fast start, and show what slows the TV down after a boot.
    optimize.py                     compile the most-used apps that are not compiled yet, then the boot report
    optimize.py --dry-run           print what it would compile and the boot report; change nothing
    optimize.py --top 8             how many apps to take from the usage figures (default 5)
    optimize.py --refresh           also recompile apps already compiled, with the profile they have now
    optimize.py --measure           cold-start each app before and after (opens them on the TV)
    optimize.py --write <list>      write the apps that start at boot and were never used, as a debloat
                                    list with every line commented out, for tools/disable.sh
The apps: Netflix, Prime Video, YouTube and Disney+ Hotstar whenever they are installed, then the TV's
most-used apps by the usage figures Android keeps, up to --top more. Launchers (anything that answers
Home) are left out.

Why it helps: Android compiles an app ahead of time from the parts of it you use (speed-profile), but
only in the background, while the TV is idle. After an app updates, it runs from a quick verify until
that happens, and a TV that is on, or in standby with the network up, can wait days for it. Those are the
apps people open most, at their slowest. This compiles them now, one at a time.

Only speed-profile, never `speed`: compiling every method of an app the size of Netflix took a Xiaomi
MiTV-MOOQ0 (1.8 GB) off the air partway through and left the app uncompiled until it was compiled
again. A compile is also skipped while the TV has less than MIN_FREE_MB of memory available.
Undo: `tools/tv.py 'cmd package compile --reset <package>'` puts an app back to the state it was
installed in. An app update replaces the compiled code anyway.

The boot report: how long the boot took (from the TV's event log, while it still holds the boot), and
the installed apps that start themselves at every boot (BOOT_COMPLETED) with how much they were used.
Nothing is disabled here: --write hands the unused ones to the usual, reversible debloat flow.

Exit codes: 2 the TV was not reached (nothing was asked of it); 1 a compile failed, or --write could not
write its list; 0 otherwise.
"""
import argparse
import os
import re
import sys
import time

# Opened by name in this order when installed, before the usage figures are read.
FAVORITES = (
    "com.netflix.ninja",                    # Netflix
    "com.amazon.amazonvideo.livingroom",    # Prime Video
    "com.google.android.youtube.tv",        # YouTube
    "in.startv.hotstar",                    # Disney+ Hotstar / JioHotstar
)
# Compiled from a profile, or fully: nothing to gain from compiling again unless --refresh.
COMPILED = {"speed-profile", "speed", "everything", "everything-profile"}
MIN_FREE_MB = 350
PACKAGE_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")
USAGE_RE = re.compile(r'package=(\S+) totalTimeUsed="([0-9:]+)".*?appLaunchCount=(\d+)')
STATUS_RE = re.compile(r"status=([a-z-]+)")
BOOT_EVENTS = (("boot_progress_enable_screen", "screen on"), ("sf_stop_bootanim", "boot animation ends"))
HOME_QUERY = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"


def shell(dev, command, timeout=120.0):
    return (dev.shell(command, read_timeout_s=timeout) or "").strip()


def connect():
    # Imported here so the module can be imported (tests, --help) without adb_shell installed. The exits
    # follow privacy.py: tv.py's own sentence, then 2, as nothing was asked of the TV.
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    try:
        import tv
    except SystemExit as exit_request:
        if not exit_request.code:
            raise
        if isinstance(exit_request.code, str):
            sys.stderr.write(exit_request.code + "\n")
        sys.stderr.write("Nothing was asked of the TV.\n")
        raise SystemExit(2)
    except ImportError as e:
        sys.stderr.write(f"cannot load tools/tv.py ({e}).\n"
                         "run: python3 -m venv tools/venv && "
                         "tools/venv/bin/pip install -r tools/requirements.txt\n"
                         "Nothing was asked of the TV.\n")
        raise SystemExit(2)
    try:
        return tv.connect()
    except Exception as e:
        sys.stderr.write(f"cannot reach the TV at {os.environ.get('TV_HOST', 'TV_HOST')}: {e}\n"
                         "Check TV_HOST and that USB debugging is still on (tools/tv.py --probe).\n"
                         "Nothing was asked of the TV.\n")
        raise SystemExit(2)


def seconds(text):
    """totalTimeUsed as dumpsys prints it, "MM:SS" or "H:MM:SS", in seconds."""
    total = 0
    for part in text.split(":"):
        total = total * 60 + int(part or 0)
    return total


def parse_usage(dump):
    """{package: (seconds used, launches)} from `dumpsys usagestats`. The same package shows up in the
    daily, weekly, monthly and yearly stats; the largest figures are the longest interval's."""
    usage = {}
    for name, used, launches in USAGE_RE.findall(dump):
        if not PACKAGE_RE.fullmatch(name):
            continue
        old = usage.get(name, (0, 0))
        usage[name] = (max(old[0], seconds(used)), max(old[1], int(launches)))
    return usage


def packages(text):
    """Package names from `pm list packages` or `--brief` activity and receiver queries."""
    found = []
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("package:"):
            line = line[len("package:"):]
        name = line.split("/", 1)[0]
        if PACKAGE_RE.fullmatch(name) and name not in found:
            found.append(name)
    return found


def pick_apps(launchable, usage, homes, top):
    """FAVORITES that are installed, then the `top` most-used launchable apps, launchers left out."""
    allowed = [p for p in launchable if p not in homes]
    chosen = [p for p in FAVORITES if p in allowed]
    ranked = sorted((p for p in allowed if p not in chosen and usage.get(p, (0, 0)) != (0, 0)),
                    key=lambda p: usage[p], reverse=True)
    return chosen + ranked[:max(0, top)]


def dexopt_status(dump):
    """The compile status of the app's main APK from `dumpsys package <pkg>`, or "unknown"."""
    section = dump.split("Dexopt state:", 1)[-1] if "Dexopt state:" in dump else dump
    match = STATUS_RE.search(section)
    return match.group(1) if match else "unknown"


def needs_compile(status, refresh):
    return refresh or status not in COMPILED


def mem_available_mb(meminfo):
    match = re.search(r"MemAvailable:\s+(\d+) kB", meminfo)
    return int(match.group(1)) // 1024 if match else -1


def boot_times(events):
    """[(label, seconds since power-on)] from `logcat -b events`, for the events still in the log."""
    times = []
    for event, label in BOOT_EVENTS:
        match = re.search(rf"\b{event}: (\d+)", events)
        if match:
            times.append((label, int(match.group(1)) / 1000.0))
    return times


def boot_wakers(receivers, third_party, keep):
    """Installed (not system) apps that start at every boot, other than the ones kept fast."""
    return [p for p in packages(receivers) if p in third_party and p not in keep]


def launch_activity(dev, package):
    for category in ("android.intent.category.LEANBACK_LAUNCHER", "android.intent.category.LAUNCHER"):
        out = shell(dev, f"cmd package resolve-activity --brief -a android.intent.action.MAIN -c {category} {package}")
        last = out.splitlines()[-1].strip() if out else ""
        if last.startswith(package + "/"):
            return last
    return None


def cold_start_ms(dev, package):
    """First frame of a cold start in ms, or None. Force-stops the app, starts it, then goes Home."""
    activity = launch_activity(dev, package)
    if not activity:
        return None
    shell(dev, f"am force-stop {package}; input keyevent KEYCODE_HOME; sleep 3")
    out = shell(dev, f"am start -W -n {activity}", timeout=60.0)
    time.sleep(5)
    shell(dev, f"am force-stop {package}; input keyevent KEYCODE_HOME")
    match = re.search(r"TotalTime: (\d+)", out)
    return int(match.group(1)) if match and int(match.group(1)) > 0 else None


def compile_app(dev, package):
    """speed-profile, never speed (see the docstring). Returns (ok, seconds, message)."""
    start = time.monotonic()
    out = shell(dev, f"cmd package compile -m speed-profile -f {package}", timeout=900.0)
    ok = out.splitlines()[-1].strip() == "Success" if out else False
    return ok, time.monotonic() - start, out.splitlines()[-1].strip() if out else "no answer"


def write_list(path, wakers, usage):
    lines = ["# Apps that start at every boot and were never used, from tools/optimize.py --write.",
             "# Every line is commented out: remove the # in front of the ones you never open, then",
             f"#   TV_HOST=<tv ip> tools/disable.sh {path}",
             f"# Undo: TV_HOST=<tv ip> tools/restore.sh {path}", ""]
    lines += [f"# {p}" for p in wakers if usage.get(p, (0, 0)) == (0, 0)]
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def describe_usage(entry):
    used, launches = entry
    if (used, launches) == (0, 0):
        return "no use recorded"
    return f"{used // 3600}h {used % 3600 // 60:02d}m used, {launches} launches"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--top", type=int, default=5)
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--measure", action="store_true")
    parser.add_argument("--write", metavar="LIST")
    args = parser.parse_args(argv)

    dev = connect()
    usage = parse_usage(shell(dev, "dumpsys usagestats"))
    launchable = packages(shell(dev, "cmd package query-activities --brief -a android.intent.action.MAIN "
                                     "-c android.intent.category.LEANBACK_LAUNCHER") + "\n"
                          + shell(dev, "cmd package query-activities --brief -a android.intent.action.MAIN "
                                       "-c android.intent.category.LAUNCHER"))
    homes = set(packages(shell(dev, HOME_QUERY)))
    apps = pick_apps(launchable, usage, homes, args.top)

    failed = False
    print("Most-used apps")
    for package in apps:
        status = dexopt_status(shell(dev, f"dumpsys package {package}"))
        line = f"  {package:42} {status:15} {describe_usage(usage.get(package, (0, 0)))}"
        if not needs_compile(status, args.refresh):
            print(line + "  - compiled")
            continue
        if args.dry_run:
            print(line + "  - would compile (speed-profile)")
            continue
        free = mem_available_mb(shell(dev, "cat /proc/meminfo"))
        if 0 <= free < MIN_FREE_MB:
            print(line + f"  - skipped: {free} MB free, under {MIN_FREE_MB} MB")
            continue
        before = cold_start_ms(dev, package) if args.measure else None
        ok, took, message = compile_app(dev, package)
        if not ok:
            failed = True
            print(line + f"  - compile failed: {message}")
            continue
        after = cold_start_ms(dev, package) if args.measure else None
        timing = f", cold start {before} -> {after} ms" if before and after else ""
        print(line + f"  - compiled in {took:.0f} s{timing}")

    print("\nBoot")
    times = boot_times(shell(dev, "logcat -b events -d 2>/dev/null | grep -E 'boot_progress_enable_screen|sf_stop_bootanim'"))
    for label, at in times:
        print(f"  {label:20} {at:5.1f} s after power-on")
    if not times:
        print("  the boot is no longer in the TV's event log (it keeps the last few minutes after a boot)")
    third_party = set(packages(shell(dev, "pm list packages -3 -e --user 0")))
    wakers = boot_wakers(shell(dev, "cmd package query-receivers --brief -a android.intent.action.BOOT_COMPLETED"),
                         third_party, set(apps))
    if wakers:
        print("  Installed apps that start at every boot:")
        for package in wakers:
            print(f"    {package:42} {describe_usage(usage.get(package, (0, 0)))}")
        print("  Each one costs memory and CPU right after the boot. Disable the ones you never open with a")
        print("  debloat list (--write makes one), so the apps you use stay in memory and reopen at once.")
    if args.write:
        try:
            write_list(args.write, wakers, usage)
            print(f"  Wrote {args.write}: uncomment the apps to disable, then tools/disable.sh {args.write}")
        except OSError as e:
            print(f"  cannot write {args.write}: {e}", file=sys.stderr)
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
