#!/usr/bin/env python3
"""Measure the launcher's key latency, jank and idle cost on a TV.

The launcher is the app that answers HOME. The script presses a fixed sequence of D-pad keys,
one at a time, and reads the frame timestamps that `dumpsys gfxinfo <pkg> framestats` reports:

  * response  = the first frame that FINISHED after the key was injected
  * movement  = the end of the continuous run of frames that started with it
  * jank      = janky frames / total frames, from the same dump

It also measures the cold start (`am start -W`), the return to Home (HOME key from another app),
the idle CPU of the whole device and the launcher's own PSS. Nothing on the TV is changed.

The TV is reached through tools/tv.py, the client this project already ships: it speaks ADB over
TCP with the adb_shell library, so no adb binary is needed. That matters on macOS, where the
platform tools are not installed by default.

Usage:
    TV_HOST=192.168.1.50 python tools/bench.py --keys 36 --series 3
    TV_HOST=192.168.1.50 python tools/bench.py --keys 36 --series 3 --out-dir my-results

The output is a summary plus the raw per-key numbers, so a run can be compared with another one
or re-analysed later.

Why the frame is matched by its completion time and not by its intended vsync: on a launcher
that moves the focus in a single frame (no animation), the frame that reacts to a key is born
from the vsync BEFORE the key and only finishes after it. Matching on the intended vsync drops
most of those keys and leaves a sample too small to say anything.
"""
import argparse
import atexit
import importlib.util
import json
import os
from pathlib import Path
import re
import statistics
import sys
import time

# The sequence the key probe uses: moves inside a row and between rows, including the edges.
DEFAULT_SEQUENCE = (22, 22, 22, 21, 21, 21, 20, 22, 22, 21, 21, 19)

# Android key codes: 19 is UP, 20 is DOWN, 21 is LEFT, 22 is RIGHT.
KEYCODE_NAMES = {19: "KEYCODE_DPAD_UP", 20: "KEYCODE_DPAD_DOWN", 21: "KEYCODE_DPAD_LEFT",
                 22: "KEYCODE_DPAD_RIGHT", 66: "KEYCODE_ENTER", 3: "KEYCODE_HOME",
                 4: "KEYCODE_BACK"}


class TV:
    """The TV, reached with tools/tv.py's client over one ADB connection.

    tools/tv.py is the client this project already ships: it speaks ADB over TCP with the
    adb_shell library, so no adb binary is needed. That matters on macOS, where the platform
    tools are not installed by default and the system blocks the ones that are.
    """

    def __init__(self, host, port=5555, binary=None):
        self.host = host
        self.port = port
        self.serial = f"{host}:{port}"
        self.binary = binary
        self.client = None
        self._other_app = None

    def _connect(self):
        """tools/tv.py's ADB connection, opened on first use and kept for the whole run.

        Starting tools/tv.py per command paid a Python start and a fresh ADB handshake each time,
        several per key; through a relay a full run took over 45 minutes.
        """
        if self.client is None:
            here = str(Path(__file__).resolve().parent)
            if here not in sys.path:
                sys.path.insert(0, here)
            # tv.py reads its target from the environment when imported, including CLI overrides here.
            os.environ["TV_HOST"], os.environ["TV_PORT"] = self.host, str(self.port)
            try:
                import tv as tv_client  # noqa: E402
            except ModuleNotFoundError as e:
                raise SystemExit(f"bench.py needs {e.name}: python3 -m venv tools/venv && "
                                 "tools/venv/bin/pip install -r tools/requirements.txt") from e
            tv_client.HOST, tv_client.PORT = self.host, int(self.port)
            self.client = tv_client.connect()
        return self.client

    def shell(self, command):
        """Run one shell command on the TV and return its output; bytes that aren't UTF-8 become U+FFFD.

        A dropped connection (the TV went to standby for a moment) is reopened once.
        """
        for attempt in (1, 2):
            try:
                out = self._connect().shell(command, read_timeout_s=120.0, decode=False)
                return out.decode("utf-8", "replace") if isinstance(out, bytes) else (out or "")
            except SystemExit:
                raise
            except Exception:
                self.close()
                if attempt == 2:
                    raise

    def close(self):
        if self.client is not None:
            try:
                self.client.close()
            except Exception:
                pass
            self.client = None

    def shell_ok(self, command):
        return self.shell(command).strip() == ""

    def home_component(self):
        """The component that answers HOME, e.g. com.gathin.tvhome/.MainActivity."""
        out = self.shell(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN "
            "-c android.intent.category.HOME")
        lines = [line.strip() for line in out.splitlines() if "/" in line]
        return lines[-1] if lines else None

    def focused_window(self):
        """The window that has the focus, e.g. "u0 br.eddy.tvhome/...MainActivity".

        The dump has several "mCurrentFocus"/"mFocusedApp" lines; only the one that names a
        window counts, and a popup (a menu left open) is a window of its own.
        """
        out = self.shell("dumpsys window")
        for line in out.splitlines():
            match = re.search(r"mCurrentFocus=Window\{[^}]*\s(\S+?)\}", line)
            if match:
                return match.group(1)
        return None

    def is_in_front(self, package):
        """Whether the launcher is the focused window.

        A launcher that is not in front renders nothing, so every key would measure zero frames
        and the run would be meaningless. A popup (a menu left open, the recents panel) counts as
        "not in front" too, because the launcher underneath is not drawing.
        """
        focused = self.focused_window() or ""
        return focused.startswith(f"{package}/")

    def package_of(self, component):
        return component.split("/", 1)[0]

    def version_of(self, package):
        out = self.shell(f"dumpsys package {package}")
        match = re.search(r"versionName=(\S+)", out)
        return match.group(1) if match else "?"

    def dumpsys(self, what):
        return self.shell(f"dumpsys {what}")


# Keys to read the input log for before deciding this TV doesn't log injected keys.
KEY_LOG_PROBES = 3


def venv_python():
    """tools/venv's interpreter, which has adb_shell, or None."""
    here = Path(__file__).resolve().parent
    for candidate in (here / "venv" / "bin" / "python", here / "venv" / "Scripts" / "python.exe"):
        if candidate.exists():
            return str(candidate)
    return None


def key_event_time(tv, keycode, binary=None):
    """The uptime (ms) at which the key went down, read from the input log.

    The log line is matched by the key's own name: the buffer holds the keys injected before it,
    and the first line of the buffer is not necessarily the key of this measurement.
    """
    # Some TVs never log injected keys (a TCL C755 on Google TV 14 doesn't), and reading the log costs
    # seconds there: its main buffer holds about 100 MB. After KEY_LOG_PROBES keys without a line, stop
    # reading it; measure_key then times the key from just before it was sent, as it always did when
    # the line was missing. Only the newest lines are read, filtered on the TV.
    state = vars(tv).setdefault("_key_log", {"seen": False, "misses": 0})
    if not state["seen"] and state["misses"] >= KEY_LOG_PROBES:
        return None
    out = tv.shell("logcat -d -b main -t 2000 | grep injectKeyEvent")
    name = KEYCODE_NAMES.get(keycode, f"keyCode={keycode}")
    for line in reversed(out.splitlines()):
        if "injectKeyEvent" in line and "action=ACTION_DOWN" in line and name in line:
            match = re.search(r"eventTime=(\d+)", line)
            if match:
                state["seen"] = True
                return int(match.group(1))
    state["misses"] += 1
    return None

def android_uptime(tv, package=None, binary=None):
    """The Android uptime in ms, the clock the frame timestamps use.

    `dumpsys gfxinfo` prints it as "Uptime: <ms> Realtime: <ms>". /proc/uptime is NOT the same
    clock: it counts the time the device spent suspended, so it runs hours ahead of the frames.
    """
    what = f"dumpsys gfxinfo {package}" if package else "dumpsys gfxinfo"
    out = tv.shell(what)
    match = re.search(r"^Uptime:\s*(\d+)", out, re.M)
    return int(match.group(1)) if match else None


def measure_key(tv, keycode, binary=None, package=None):
    """Inject one key and return (response_ms, movement_ms, frames, janky) or None."""
    package = package or tv.package_of(tv.home_component())
    # The reset zeroes the gfxinfo counters. Without it, the previous key's frame would still be
    # in the window and the "first frame after the key" would be the previous key's.
    reset_out = tv.shell(f"dumpsys gfxinfo {package} reset")
    t_before = None
    match = re.search(r"^Uptime:\s*(\d+)", reset_out, re.M)
    if match:
        t_before = int(match.group(1))
    tv.shell(f"input keyevent {keycode}")
    time.sleep(1.0)
    stats = tv.shell(f"dumpsys gfxinfo {package} framestats")
    t_after = None
    match = re.search(r"^Uptime:\s*(\d+)", stats, re.M)
    if match:
        t_after = int(match.group(1))
    t0 = key_event_time(tv, keycode, binary)
    # The log line is only trusted when it falls inside the window between the reset (before the
    # key) and the dump (after it). A line left over from an earlier key is outside the window
    # and would give a latency of seconds.
    if t0 is None or (t_before is not None and t0 < t_before) \
            or (t_after is not None and t0 > t_after):
        t0 = t_before
    if t0 is None:
        return None
    rows = frame_rows(stats)
    threshold = t0 * 1_000_000
    first = last = prev = None
    for flags, intended, completed in rows:
        if flags != 0 or completed < threshold:
            continue
        if first is None:
            first, last, prev = completed, completed, intended
            continue
        if (intended - prev) / 1e6 > 50:      # a gap: the movement is over
            break
        last, prev = completed, intended
    if first is None:
        return None
    # A key that moves nothing (the end of a row, the focus on the top bar) leaves the next
    # frame, caused by something else, to be measured as its answer. An Android TV answers in
    # 16 to 33 ms, so a first frame that only finishes more than 150 ms after the key is not the
    # answer to it: the key had no visual reaction and is left out of the numbers.
    if (first - threshold) / 1e6 > 150:
        return None
    frames = janky = 0
    for line in stats.splitlines():
        match = re.match(r"Total frames rendered:\s*(\d+)", line)
        if match:
            frames = int(match.group(1))
        match = re.match(r"Janky frames:\s*(\d+)", line)
        if match:
            janky = int(match.group(1))
    return ((first - threshold) / 1e6, (last - threshold) / 1e6, frames, janky)


def clear_log(tv, binary=None):
    tv.shell("logcat -c -b main")


def frame_rows(framestats):
    """The PROFILEDATA rows as (flags, intended_vsync_ns, completed_ns)."""
    rows = []
    inside = False
    columns = None
    for line in framestats.splitlines():
        if "---PROFILEDATA---" in line:
            inside = not inside
            columns = None
            continue
        if not inside:
            continue
        parts = [part.strip() for part in line.split(",")]
        if columns is None:
            # Android versions add fields; the header names stay the same.
            try:
                columns = [parts.index(name) for name in ("Flags", "IntendedVsync", "FrameCompleted")]
            except ValueError:
                pass
            continue
        try:
            rows.append(tuple(int(parts[index]) for index in columns))
        except (ValueError, IndexError):
            continue
    return rows


def other_app(tv):
    """An app to send the TV to, so the launcher really starts cold.

    The Settings app is found by the category it answers, not by a hardcoded activity: the
    activity name differs between manufacturers (com.android.tv.settings/.MainSettings on
    Google TV, others elsewhere), and a fixed name simply does not exist on some sets.
    """
    if tv._other_app:
        return tv._other_app
    out = tv.shell(
        "cmd package resolve-activity --brief -a android.intent.action.MAIN "
        "-c android.intent.category.LAUNCHER")
    lines = [line.strip() for line in out.splitlines() if "/" in line]
    tv._other_app = lines[-1] if lines else None
    return tv._other_app


def accessibility_services(tv, package):
    """The accessibility services enabled for a package, as the setting holds them."""
    out = tv.shell("settings get secure enabled_accessibility_services")
    return [part.strip() for part in out.split(":") if part.strip().startswith(package + "/")]


def _norm_component(component):
    """`pkg/pkg.Classe` and `pkg/.Classe` are the same component; normalise so it is not added twice."""
    if "/" not in component:
        return component
    package, cls = component.split("/", 1)
    if cls.startswith(package + "."):
        return package + "/" + cls[len(package):]
    return component


def restore_accessibility(tv, package, services):
    """Put back the accessibility services a force-stop took down.

    `am force-stop` kills the process the service lives in, and on some TVs it also **removes that
    package's services** from `enabled_accessibility_services` — they do not come back when the
    launcher starts again. Reported on a TCL 55C715 (#503) and reproduced on a TCL BeyondTV2
    (Android TV 9): the setting went from one service to empty after a single stop.

    The list is a colon-separated set shared by every app, so this **appends** what is missing
    instead of writing each service on its own: `settings put secure enabled_accessibility_services
    <one service>` replaces the whole list, which would wipe the launcher's other services and any
    other app's.
    """
    if not services:
        return
    current = tv.shell("settings get secure enabled_accessibility_services").strip()
    if current == "null":
        current = ""
    merged = [part for part in current.split(":") if part]
    known = {_norm_component(part) for part in merged}
    added = False
    for service in services:
        if _norm_component(service) not in known:
            known.add(_norm_component(service))
            merged.append(service)
            added = True
    if added:
        tv.shell("settings put secure enabled_accessibility_services " + ":".join(merged))
        tv.shell("settings put secure accessibility_enabled 1")


def cold_start(tv, component, repeats):
    """The `am start -W` WaitTime of the launcher, in ms.

    The accessibility services are put back **after** the measurement, not before. Writing that
    setting makes the system bind the service, which starts the launcher's process — the one thing a
    cold start must not have. Restoring first turned every "cold" start into a warm one.
    """
    values = []
    other = other_app(tv)
    if not other:
        print("Could not find another app to switch to; the cold start is not measured.")
        return values
    package = tv.package_of(component)
    # Read once, before the first stop: the force-stop clears the setting on these TVs, so after the
    # first run there would be nothing left to read.
    services = accessibility_services(tv, package)
    try:
        for _ in range(repeats):
            tv.shell(f"am start -W -n {other}")
            time.sleep(2)
            tv.shell(f"am force-stop {package}")
            time.sleep(3)
            # An explicit component alone creates a regular task. Start the HOME task,
            # so the next Home press cannot create a second launcher instance.
            out = tv.shell(f"am start -W -a android.intent.action.MAIN "
                           f"-c android.intent.category.HOME -n {component}")
            match = re.search(r"WaitTime:\s*(\d+)", out)
            if match:
                values.append(int(match.group(1)))
            time.sleep(3)
    finally:
        restore_accessibility(tv, package, services)
    return values


def return_to_home(tv, component, repeats):
    """How long the launcher takes to be back in front after the HOME key, in ms."""
    values = []
    other = other_app(tv)
    if not other:
        print("Could not find another app to switch to; the return to Home is not measured.")
        return values
    # AM/WM resume events identify the activity by its full class name, even when the
    # resolved component abbreviates it as pkg/.Class.
    class_name = component.split("/", 1)[1] if "/" in component else component
    if class_name.startswith("."):
        class_name = component.split("/", 1)[0] + class_name
    for _ in range(repeats):
        tv.shell(f"am start -W -n {other}")
        time.sleep(3)
        clear_log(tv)
        tv.shell("input keyevent 3")
        time.sleep(3)
        out = tv.shell("logcat -d -b main -b events -v epoch | grep -e injectKeyEvent "
                       "-e am_on_resume_called -e wm_on_resume_called")
        key_at = resume_at = None
        for line in out.splitlines():
            # The log indents its lines, so the timestamp is not at the start of the line.
            # Both timestamps have to come from the same clock: the log's own column is in
            # epoch seconds, while the eventTime inside the KeyEvent is in uptime
            # milliseconds. Mixing them gives a negative or absurd difference.
            stamp = re.search(r"^[\s]*(\d+\.\d+)\s", line)
            if "injectKeyEvent" in line and "KEYCODE_HOME" in line and key_at is None:
                if stamp:
                    key_at = float(stamp.group(1))
            # The LAST resume of the launcher after the key, not the first: the buffer can
            # still hold a resume from before the key was pressed.
            resumed = re.search(r"\b(?:am|wm)_on_resume_called:\s*\[[^,]*,\s*([^,]+),", line)
            if resumed and resumed.group(1).strip() == class_name and stamp and key_at is not None:
                if float(stamp.group(1)) >= key_at:
                    resume_at = float(stamp.group(1))
        if key_at is not None and resume_at is not None:
            values.append(int((resume_at - key_at) * 1000))
    return values


def launcher_pid(tv, package):
    """The pid of the launcher process, or None."""
    out = tv.shell(f"pidof {package}").strip()
    if not out:
        return None
    return out.split()[0]


def idle_cpu(tv, package, seconds):
    """The CPU of the whole device and of the launcher, over `seconds`, from /proc/stat."""
    def sample():
        return tv.shell("head -1 /proc/stat; cat /proc/[0-9]*/stat 2>/dev/null").splitlines()

    def parse(lines):
        for line in lines:
            if line.startswith("cpu "):
                fields = [int(x) for x in line.split()[1:]]
                return (sum(fields), fields[3] + fields[4])
        return None

    def process_ticks(lines, pid):
        """utime + stime of one pid, in clock ticks."""
        prefix = f"{pid} ("
        for line in lines:
            if line.startswith(prefix):
                parts = line[line.index(")") + 2:].split()
                if len(parts) > 12:
                    return int(parts[11]) + int(parts[12])
        return None

    # The ticks are in USER_HZ (100 on Android). The percentage is of ONE core, like `top` shows.
    ticks_per_second = 100
    before = sample()
    pid = launcher_pid(tv, package)
    before_ticks = process_ticks(before, pid) if pid else None
    before_cpu = parse(before)
    time.sleep(seconds)
    after = sample()
    after_cpu = parse(after)
    after_ticks = process_ticks(after, pid) if pid else None
    if not before_cpu or not after_cpu:
        return None, None
    total = after_cpu[0] - before_cpu[0]
    idle = after_cpu[1] - before_cpu[1]
    device = 0.0 if total == 0 else 100 * (1 - idle / total)
    launcher = None
    if before_ticks is not None and after_ticks is not None:
        launcher = 100 * (after_ticks - before_ticks) / (ticks_per_second * seconds)
    return device, launcher


def pss_of(tv, package):
    out = tv.shell(f"dumpsys meminfo {package}")
    match = re.search(r"^\s*TOTAL(?: PSS)?:\s+(\d+)\b", out, re.M)
    return int(match.group(1)) // 1024 if match else None


def stats(values):
    if not values:
        return {"p50": None, "p95": None, "p99": None, "max": None, "n": 0}
    ordered = sorted(values)
    def pick(fraction):
        index = min(len(ordered) - 1, int(len(ordered) * fraction + 0.999) - 1)
        return ordered[max(0, index)]
    return {"p50": int(statistics.median(ordered)), "p95": pick(0.95),
            "p99": pick(0.99), "max": ordered[-1], "n": len(ordered)}


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default=os.environ.get("TV_HOST"),
                        help="TV address (or set TV_HOST)")
    parser.add_argument("--port", type=int, default=int(os.environ.get("TV_PORT", "5555")))
    parser.add_argument("--keys", type=int, default=36, help="keys per series")
    parser.add_argument("--series", type=int, default=3, help="how many series")
    parser.add_argument("--cold", type=int, default=5, help="cold starts")
    parser.add_argument("--returns", type=int, default=10, help="returns to Home")
    parser.add_argument("--idle", type=int, default=60, help="idle seconds")
    parser.add_argument("--out-dir", default="bench-results", help="where to write the results")
    args = parser.parse_args()
    if not args.host:
        raise SystemExit("pass --host or set TV_HOST")
    venv = venv_python()
    if importlib.util.find_spec("adb_shell") is None and venv \
            and os.path.realpath(venv) != os.path.realpath(sys.executable):
        # Started with a Python that lacks adb_shell: run again with the project's venv, which has it.
        os.execv(venv, [venv, os.path.abspath(__file__), *sys.argv[1:]])

    tv = TV(args.host, args.port)
    atexit.register(tv.close)
    component = tv.home_component()
    if not component:
        raise SystemExit("no component answers HOME on this TV")
    package = tv.package_of(component)
    version = tv.version_of(package)
    os.makedirs(args.out_dir, exist_ok=True)

    print(f"Launcher: {component} ({version})")
    print(f"Keys: {args.series} x {args.keys}; writing to {args.out_dir}")

    samples = []
    for series in range(1, args.series + 1):
        print(f"Series {series}/{args.series}")
        tv.shell("input keyevent 3")
        time.sleep(3)
        # Start on the first grid row, not on the top bar: at the end of the top bar a Right
        # moves nothing and renders no frame, and that key cannot be measured.
        tv.shell("input keyevent 20")
        time.sleep(2)
        if not tv.is_in_front(package):
            print("The launcher is not the focused window; the keys would measure nothing.")
            print(f"Focused: {tv.focused_window()}")
            return 1
        for index in range(args.keys):
            keycode = DEFAULT_SEQUENCE[index % len(DEFAULT_SEQUENCE)]
            measured = measure_key(tv, keycode, package=package)
            if measured is None:
                samples.append({"series": series, "key": keycode, "measured": False})
                continue
            response, movement, frames, janky = measured
            samples.append({"series": series, "key": keycode, "measured": True,
                            "response_ms": round(response, 1),
                            "movement_ms": round(movement, 1),
                            "frames": frames, "janky": janky})

    measured = [s for s in samples if s.get("measured")]
    responses = [s["response_ms"] for s in measured]
    movements = [s["movement_ms"] for s in measured]
    frames = sum(s.get("frames", 0) for s in measured)
    janky = sum(s.get("janky", 0) for s in measured)

    print("Cold starts")
    colds = cold_start(tv, component, args.cold)
    print("Returns to Home")
    returns = return_to_home(tv, component, args.returns)
    print(f"Idle {args.idle}s")
    tv.shell("input keyevent 3")
    time.sleep(20)
    device_cpu, launcher_cpu = idle_cpu(tv, package, args.idle)
    pss = pss_of(tv, package)

    result = {
        "launcher": component, "version": version, "package": package,
        "host": args.host,
        "environment": {
            "android_release": tv.shell("getprop ro.build.version.release").strip(),
            "sdk": tv.shell("getprop ro.build.version.sdk").strip(),
            "model": tv.shell("getprop ro.product.model").strip(),
            "fingerprint": tv.shell("getprop ro.build.fingerprint").strip(),
        },
        "keys": {"series": args.series, "per_series": args.keys,
                 "measured": len(measured), "lost": len(samples) - len(measured)},
        "response_ms": stats(responses),
        "movement_ms": stats(movements),
        "jank_pct": round(100 * janky / frames, 2) if frames else None,
        "frames": frames, "janky_frames": janky,
        "cold_start_ms": stats(colds),
        "return_to_home_ms": stats(returns),
        "idle_cpu_pct": round(device_cpu, 1) if device_cpu is not None else None,
        "idle_launcher_cpu_pct": round(launcher_cpu, 1) if launcher_cpu is not None else None,
        "pss_mb": pss,
        "samples": samples,
    }
    path = os.path.join(args.out_dir, "result.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2)
    print(json.dumps({k: v for k, v in result.items() if k != "samples"}, indent=2))
    print(f"Written: {path}")


if __name__ == "__main__":
    main()
