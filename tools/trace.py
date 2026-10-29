#!/usr/bin/env python3
"""Record Perfetto traces on the TV around a launcher scenario, to see where its time goes.

tools/bench.py gives the headline numbers (key latency, jank, cold start, return to Home, PSS) with no
extra installs. This records the full timeline instead: every thread's slices, the launcher's own
lifecycle (onCreate, onResume, layout, draw) and the frame timeline, so a change can be traced to the
work it removes. tools/trace_report.py turns a folder of these traces into the numbers. Read
tools/trace.md first: it says which scenario to use and what can mislead.

Recording needs nothing beyond tools/requirements.txt; the perfetto client already ships on Android 9
and later. Set TV_HOST to the TV's IP (and TV_PORT if it is not 5555). Usage:

    tools/trace.py home --runs 5 --out traces/before
    tools/trace.py cold|coldsys|home|nav --runs N --out DIR [--package PKG] [--app COMPONENT]
    tools/trace.py custom --setup 'input keyevent KEYCODE_HOME' --setup 'sleep 2' \\
                          --step 'input keyevent KEYCODE_DPAD_RIGHT' --step 'sleep 1' --out DIR

--out DIR is the folder the traces go into; it is created if it is not there. A file where the folder
goes, a path through a file, and an empty --out are refused as argument errors (exit 2) before the TV is
asked anything, so a typo cannot read as a recording that failed. Started with a Python that lacks the
packages, it runs again under tools/venv's Python, or says how to make that venv, the way tools/bench.py
does.

Scenarios (setup runs before the recording starts, so it stays out of the trace):
    cold     another app in front, the launcher force-stopped, then started with `am start -W`
    coldsys  Home in front, the launcher force-stopped: Android starts it again by itself
    home     another app in front, then the HOME key: the everyday return to Home
    nav      Home in front, then 24 D-pad moves 0.35 s apart
    custom   your own --setup and --step shell commands; `sleep N` steps set the length
"""
import argparse
import base64
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import time

PACKAGE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$")
COMPONENT = re.compile(r"^[A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+$")
REMOTE = "/data/misc/perfetto-traces/cleanyourtv.pftrace"   # the folder perfetto may write to on Android 9 and 10
# Set on the run that starts again under tools/venv's Python, so a venv without the package answers with
# the line to run instead of starting a third process. tv.py carries the same marker for the same reason.
VENV_MARKER = "CLEANYOURTV_TRACE_PY_VENV"
NAV_KEYS = ("RIGHT RIGHT RIGHT LEFT LEFT LEFT DOWN RIGHT DOWN LEFT DOWN UP "
            "UP RIGHT UP LEFT DOWN DOWN RIGHT RIGHT UP UP UP LEFT").split()
START_MARGIN_S = 3    # recording starts in the background; give it time before the first step
END_MARGIN_S = 2

def _varint(value):
    """Unsigned protobuf integer; the config only uses non-negative values."""
    out = bytearray()
    while value > 127:
        out.append((value & 127) | 128)
        value >>= 7
    out.append(value)
    return bytes(out)


def _field(number, value):
    """The config's two wire types: unsigned integers and length-delimited strings/messages."""
    if isinstance(value, str):
        value = value.encode("utf-8")
    if isinstance(value, bytes):
        return _varint((number << 3) | 2) + _varint(len(value)) + value
    return _varint(number << 3) + _varint(value)


def perfetto_config(package, duration_ms):
    """Binary TraceConfig: Android P has no --txt parser; later versions accept binary too.

    Field numbers come from the Android P schema, including FtraceConfig and ProcessStatsConfig:
    https://android.googlesource.com/platform/external/perfetto/+/android-9.0.0_r1/protos/perfetto/config/
    """
    ftrace = b"".join(_field(1, event) for event in (
        "sched/sched_switch", "sched/sched_wakeup", "sched/sched_waking",
        "power/cpu_frequency", "power/cpu_idle", "task/task_newtask", "task/task_rename",
        "sched/sched_process_exit"))
    ftrace += b"".join(_field(2, category) for category in (
        "am", "wm", "view", "gfx", "input", "dalvik", "res", "binder_driver"))
    # FtraceConfig.atrace_apps=3, buffer_size_kb=10, drain_period_ms=11.
    ftrace += _field(3, package) + _field(10, 16384) + _field(11, 250)
    sources = (
        _field(1, "linux.ftrace") + _field(2, 0) + _field(100, ftrace),
        _field(1, "linux.process_stats") + _field(2, 1) + _field(103, _field(2, 1)),  # scan_all_processes_on_start
        _field(1, "android.surfaceflinger.frametimeline"),
        _field(1, "android.packages_list"),
    )
    # TraceConfig.buffers=1, data_sources=2, duration_ms=3. Each DataSource.config=1.
    # BufferConfig.size_kb=1 and fill_policy=4 (RING_BUFFER=1).
    buffers = b"".join(_field(1, _field(1, size) + _field(4, 1)) for size in (65536, 4096))
    return buffers + b"".join(_field(2, _field(1, source)) for source in sources) + _field(3, int(duration_ms))


def recording_ms(steps):
    """How long to record: the steps' sleeps plus a margin either side."""
    slept = sum(float(s.split()[1]) for s in steps if re.fullmatch(r"sleep \d+(\.\d+)?", s.strip()))
    return int((START_MARGIN_S + slept + END_MARGIN_S) * 1000)


def scenario(name, launcher_component, other_component, setup=(), steps=()):
    """(setup, steps) shell commands for a scenario. Setup runs before the recording starts."""
    package = launcher_component.split("/")[0]
    # Reuse the other activity across runs instead of growing its task history.
    other = f"am start --activity-reorder-to-front -n {other_component}" if other_component else None
    if name == "cold":
        return [other, "sleep 2", f"am force-stop {package}", "sleep 2"], \
               [f"am start -W -a android.intent.action.MAIN "
                f"-c android.intent.category.HOME -n {launcher_component}", "sleep 4"]
    if name == "coldsys":
        return ["input keyevent KEYCODE_HOME", "sleep 2"], [f"am force-stop {package}", "sleep 5"]
    if name == "home":
        return ["input keyevent KEYCODE_HOME", "sleep 2", other, "sleep 4"], ["input keyevent KEYCODE_HOME", "sleep 4"]
    if name == "nav":
        moves = []
        for key in NAV_KEYS:
            moves += [f"input keyevent KEYCODE_DPAD_{key}", "sleep 0.35"]
        return ["input keyevent KEYCODE_HOME", "sleep 2"], moves + ["sleep 1"]
    return list(setup), list(steps)


def traces_folder(value):
    """--out as the folder the traces go into, judged before anything is asked of the TV.

    os.makedirs() answers a file where the folder goes with FileExistsError, a path through a file with
    NotADirectoryError and an empty argument with FileNotFoundError: three tracebacks with exit 1, each
    thrown from main() after the TV has answered the Home query and the model was read, so the reader
    cannot tell their argument from a recording that failed. Refusing here makes it an argument error
    like --runs or --app, with the run never reaching the TV. A folder that does not exist yet is fine:
    makedirs() creates it, as the default traces/ always has.
    """
    if not value:
        raise argparse.ArgumentTypeError("--out is empty: name the folder for the traces, e.g. --out traces/before")
    path = Path(value)
    if path.exists() and not path.is_dir():
        raise argparse.ArgumentTypeError(f"{value} is a file, and --out is the folder the traces go into: "
                                         f"nothing could be recorded into it")
    for parent in path.parents:
        if parent.exists() and not parent.is_dir():
            raise argparse.ArgumentTypeError(f"{value} passes through the file {parent}, so the folder the "
                                             f"traces go into cannot be made")
    return value


def parse(argv):
    p = argparse.ArgumentParser(description="Record Perfetto traces on the TV around a launcher scenario. "
                                            "See tools/trace.md.")
    p.add_argument("scenario", choices=["cold", "coldsys", "home", "nav", "custom"])
    p.add_argument("--runs", type=int, default=5, help="how many traces to record (default 5)")
    p.add_argument("--out", type=traces_folder, default="traces", help="folder for the traces (default traces/)")
    p.add_argument("--package", default="com.gathin.tvhome", help="the launcher's package (default com.gathin.tvhome)")
    p.add_argument("--app", metavar="COMPONENT",
                   help="the other app for cold and home, as package/activity (default: found on the TV)")
    p.add_argument("--setup", action="append", default=[], metavar="CMD", help="custom: a setup command")
    p.add_argument("--step", action="append", default=[], metavar="CMD", help="custom: a recorded step")
    args = p.parse_args(argv)
    if not PACKAGE.match(args.package):
        p.error(f"not a package name: {args.package!r}")
    if args.app and not COMPONENT.match(args.app):
        p.error(f"--app needs package/activity, not {args.app!r}")
    if not 1 <= args.runs <= 50:
        p.error("--runs must be between 1 and 50")
    if args.scenario == "custom" and not args.step:
        p.error("custom needs at least one --step")
    if args.scenario != "custom" and (args.setup or args.step):
        p.error("--setup and --step are for the custom scenario")
    return args


def record_one(dev, setup, steps, package, out_path, run_shell=None):
    """Run the setup, record while the steps run, then pull the trace to out_path."""
    run_shell = run_shell or (lambda cmd: dev.shell(cmd, read_timeout_s=120.0))
    for cmd in setup:
        _run(run_shell, cmd)
    config = base64.b64encode(perfetto_config(package, recording_ms(steps))).decode()
    said = run_shell(f"rm -f {REMOTE}; echo {config} | base64 -d | perfetto -c - -o {REMOTE} --background 2>&1")
    if re.search(r"not found|inaccessible|[Ee]rror", said):
        raise RuntimeError("perfetto did not start on the TV (it ships with Android 9 and later): " + said.strip())
    # Newer --background clients print their PID. Watch /proc for it: SELinux refuses `kill -0` on perfetto's
    # process from the shell (TCL C755), which would look like a finished recording and pull an empty file.
    pid = next((w for w in said.split() if w.isdigit()), None)
    alive = f"[ -d /proc/{pid} ] && echo running" if pid else "pidof perfetto >/dev/null && echo running"
    time.sleep(START_MARGIN_S)
    for cmd in steps:
        _run(run_shell, cmd)
    time.sleep(END_MARGIN_S)
    for _ in range(60):   # the config's duration ends the recording; wait for the file to be written
        if run_shell(alive).strip() != "running":
            break
        time.sleep(1)
    dev.pull(REMOTE, out_path, transport_timeout_s=300.0, read_timeout_s=300.0)
    run_shell(f"rm -f {REMOTE}")
    size = os.path.getsize(out_path)
    if size == 0:
        os.remove(out_path)
        raise RuntimeError("perfetto wrote an empty trace; run with --runs 1 and see tools/trace.md")
    return size


def other_app(shell, package):
    """An app to leave Home for: the TV's Settings, else the first leanback app that isn't the launcher.

    Never the chooser: on a TCL C755 a plain LAUNCHER lookup resolves to android's ResolverActivity, a dialog.
    """
    candidates = shell("cmd package resolve-activity --brief -a android.settings.SETTINGS").splitlines()
    candidates += shell("cmd package query-activities --brief -a android.intent.action.MAIN "
                        "-c android.intent.category.LEANBACK_LAUNCHER").splitlines()
    for line in candidates:
        line = line.strip()
        if COMPONENT.match(line) and not line.startswith(("android/", package + "/")) and "Resolver" not in line:
            return line
    return None


def _run(run_shell, cmd):
    """A step: `sleep N` waits here (an adb shell sleep would block the connection), anything else runs on the TV."""
    m = re.fullmatch(r"sleep (\d+(?:\.\d+)?)", cmd.strip())
    if m:
        time.sleep(float(m.group(1)))
    else:
        run_shell(cmd)


def venv_python():
    """tools/venv's interpreter, which has adb_shell, or None."""
    here = Path(__file__).resolve().parent
    for candidate in (here / "venv" / "bin" / "python", here / "venv" / "Scripts" / "python.exe"):
        if candidate.is_file():
            return candidate
    return None


def client_python_or_exit():
    """Run again under tools/venv's Python when this one has no adb_shell, or say how to make that venv.

    tv.py, imported below for connect(), the shell and its timeouts, imports adb_shell, and the README
    installs that into tools/venv only. This file is started both ways: the pages print
    `tools/venv/bin/python -I tools/trace.py home ...`, and a reader who has just cloned the repository
    types `python3 tools/trace.py home`. That second run ended in a ModuleNotFoundError traceback out of
    `import tv`, naming neither tools/requirements.txt nor the venv sitting next to it - while bench.py
    re-execs itself the same way and tv.py, check.py, privacy.py and recovery.py each answer with the
    line to run. This is the third place the client is imported, so it answers like they do.
    """
    if importlib.util.find_spec("adb_shell") is not None:
        return
    venv = venv_python()
    # Once only, marked in the environment as tv.py does: a venv that lacks adb_shell as well gets the
    # line below rather than another run of itself. (tools/venv/bin/python is often a symlink to the very
    # interpreter that started this run, so comparing the two paths would skip the venv that has the
    # package while the one in hand does not.)
    if venv and not os.environ.get(VENV_MARKER):
        os.environ[VENV_MARKER] = "1"
        # -I as in the printed commands, so a module beside the checkout cannot stand in for adb_shell.
        os.execv(str(venv), [str(venv), "-I", os.path.abspath(__file__), *sys.argv[1:]])
    make = (r"python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt"
            if os.name == "nt" else
            "python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt")
    sys.exit("tools/trace.py needs the packages in tools/requirements.txt (No module named 'adb_shell').\n"
             f"run: {make}")


def main(argv=None):
    args = parse(sys.argv[1:] if argv is None else argv)
    # Imported only now, so --help and argument errors work without TV_HOST set.
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    # Only a run that started this file as a script may start it again: main(argv) is called by
    # tests/test_trace.py with a faked TV, and sys.argv there is the test runner's own command line
    # (`python3 -m unittest discover -s tests`), which execv would hand to trace.py's parser as
    # 'invalid choice: discover'. A caller that passes its arguments owns the interpreter.
    if argv is None:
        client_python_or_exit()
    import bench  # noqa: E402  the accessibility helpers bench.py already tested
    import tv  # noqa: E402

    try:
        dev = tv.connect()
    except OSError as e:
        sys.exit(f"cannot reach the TV at {tv.HOST}:{tv.PORT} ({e}). Is it on and awake, with network debugging on?")
    shell = lambda cmd: dev.shell(cmd, read_timeout_s=120.0)
    try:
        home = shell("cmd package resolve-activity --brief -a android.intent.action.MAIN "
                     f"-c android.intent.category.HOME {args.package}").strip().splitlines()
        launcher = home[-1].strip() if home and "/" in home[-1] else f"{args.package}/.MainActivity"
        other = args.app
        if args.scenario in ("cold", "home") and not other:
            other = other_app(shell, args.package)
            if not other:
                sys.exit("could not find another app to switch to; pass --app package/activity")
        setup, steps = scenario(args.scenario, launcher, other, args.setup, args.step)
        stops = any("force-stop" in c for c in setup + steps)
        # A force-stop can take the launcher's accessibility helpers off the list (tools/bench.py): read them
        # before the first one and put them back after the last run, never before (it would warm a cold start).
        helpers = bench.accessibility_services(_Shell(shell), args.package) if stops else []
        about = {"model": shell("getprop ro.product.model").strip(),
                 "android": shell("getprop ro.build.version.release").strip(),
                 "build": shell("getprop ro.build.display.id").strip()}
        os.makedirs(args.out, exist_ok=True)
        try:
            for run in range(1, args.runs + 1):
                path = os.path.join(args.out, f"{args.scenario}-{run}.pftrace")
                size = record_one(dev, setup, steps, args.package, path, shell)
                with open(path[:-len(".pftrace")] + ".json", "w") as f:
                    json.dump({"scenario": args.scenario, "run": run, "package": args.package,
                               "launcher": launcher, "other_app": other, "setup": setup, "steps": steps,
                               "tv": about, "recorded": time.strftime("%Y-%m-%dT%H:%M:%S")}, f, indent=1)
                print(f"{path}: {size} bytes")
        finally:
            if helpers:
                bench.restore_accessibility(_Shell(shell), args.package, helpers)
    finally:
        dev.close()
    print(f"next: tools/venv/bin/python -I tools/trace_report.py {args.out}   (see tools/trace.md)")


class _Shell:
    """What bench.py's accessibility helpers expect of a TV: a shell()."""

    def __init__(self, run):
        self.shell = lambda cmd, read_timeout_s=None: run(cmd)


if __name__ == "__main__":
    main()
