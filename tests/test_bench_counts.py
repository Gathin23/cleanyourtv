"""tools/bench.py's five counts are what a run measures, and zero of one is refused before the TV is asked.

--keys, --series, --cold, --returns and --idle were read as whatever was typed, and each of them is a
count of a section of the result:

  * --series 0 and --keys 0 measured no keys at all and still exited 0 with a result.json whose
    response_ms, movement_ms and jank_pct are all null; --cold 0 and --returns 0 did the same to
    cold_start_ms and return_to_home_ms. In that file a null field is what docs/workflows/perf-ab.md
    tells a reader to leave out, and the page compares the six fields between two builds, so a typo
    read as "this TV does not report it" instead of "this run did not measure it".
  * --idle 0 was a ZeroDivisionError out of idle_cpu(), which divides the launcher's ticks by the
    seconds, and a negative --idle was a ValueError out of time.sleep: both after the key series, the
    cold starts, the returns and the 20 s settle, so the numbers of a full run were gone - and with
    exit 1 and a traceback, the code #2025 keeps for a finished run that could not be written.

The five are judged now where parse_args() returns, before the host is looked at, before the venv
re-exec of #1633's shape and before tv = TV(...), the way tools/trace.py's --runs and this script's
--out-dir already are. Nothing is lost: a TV that cannot do a cold start already reports null with
--cold 1.

The TV is faked, so this needs no TV and no adb_shell; a run with one of each still writes its
result.json, which is what keeps the checks from refusing a real run.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import io
import json
import os
import re
import sys
import tempfile
import unittest
from unittest import mock

from tools import bench


STRACE = (
    "cpu  1000 0 1000 8000 0 0 0 0 0 0\n"
    "1234 (com.gathin.tvhome) S 1 1234 1234 0 -1 4194560 100 0 0 0 50 60 0 0 20 0 1 0 100 1000 100 "
    "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0\n"
)


class FakeTV:
    """What main() asks the TV for, answered from strings: no connection, no adb_shell."""

    built = []

    def __init__(self, host, port=5555):
        FakeTV.built.append((host, port))
        self._other_app = None
        self.commands = []

    def close(self):
        pass

    def home_component(self):
        return "com.gathin.tvhome/.MainActivity"

    def package_of(self, component):
        return "com.gathin.tvhome"

    def version_of(self, package):
        return "0.4.0"

    def is_in_front(self, package):
        return True

    def focused_window(self):
        return "u0 com.gathin.tvhome/.MainActivity"

    def shell(self, command):
        self.commands.append(command)
        if command.startswith("pidof"):
            return "1234\n"
        if command.startswith("head -1 /proc/stat"):
            return STRACE
        if "meminfo" in command:
            return "  TOTAL PSS: 51200 KB\n"
        if command.startswith("am start -W"):
            return "WaitTime: 100\n"
        if "resolve-activity" in command:
            return "priority=0 com.gathin.tvhome/.MainActivity\n"
        return ""


def run(argv):
    """bench.main() with the TV faked and time.sleep stubbed out; (exit status, stdout, stderr).

    The sleeps are the run's pacing (3 s after Home, 2 s before the keys, the idle window), not what
    this file is about, so they are skipped; argv still reaches argparse as a reader's command line.
    """
    FakeTV.built = []
    # An empty TV_PORT reads as 5555 (tools/tv.py does the same), so the port this run keeps does not
    # depend on what the machine running the suite exported.
    environment = {"TV_HOST": "", "TV_PORT": ""}
    out, err = io.StringIO(), io.StringIO()
    status = 0
    with mock.patch.object(bench, "TV", FakeTV), \
            mock.patch.object(bench, "venv_python", lambda: None), \
            mock.patch.object(bench.time, "sleep", lambda seconds: None), \
            mock.patch.dict(os.environ, environment), \
            mock.patch.object(sys, "argv", ["bench.py"] + argv), \
            contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        try:
            bench.main()
        except SystemExit as e:
            status = e.code if isinstance(e.code, int) else 1
    return status, out.getvalue(), err.getvalue()


BASE = ["--host", "192.0.2.7", "--out-dir"]


class RefusedBeforeTheTVTest(unittest.TestCase):
    def refused(self, argv, option, value):
        with tempfile.TemporaryDirectory() as tmp:
            status, out, err = run(BASE + [tmp] + argv)
            self.assertEqual(status, 2, f"{argv} exited {status}, not 2:\n{err}")
            self.assertIn(option, err, f"{argv} does not name {option}:\n{err}")
            self.assertIn(f"not {value}", err, f"{argv} does not name the value it was given:\n{err}")
            self.assertNotIn("Traceback", err)
            self.assertEqual(out, "", f"{argv} printed on stdout:\n{out}")
            self.assertEqual(FakeTV.built, [], f"{argv} built a TV client before refusing")
            self.assertEqual(os.listdir(tmp), [], f"{argv} wrote {os.listdir(tmp)} before refusing")

    def test_a_zero_idle_is_refused(self):
        # idle_cpu() divides by the seconds: 0 was a ZeroDivisionError at the end of a full run.
        self.refused(["--idle", "0"], "--idle", "0")

    def test_a_negative_idle_is_refused(self):
        # time.sleep() answers a negative length with ValueError, after the same full run.
        for value in ("-1", "-60"):
            with self.subTest(idle=value):
                self.refused(["--idle", value], "--idle", value)

    def test_each_count_below_one_is_refused(self):
        # A zero count measured nothing in that section and still exited 0 with a result of nulls.
        for option in ("keys", "series", "cold", "returns"):
            for value in ("0", "-1", "-3"):
                with self.subTest(option=option, value=value):
                    self.refused([f"--{option}", value], f"--{option}", value)

    def test_the_refusal_comes_before_the_host_is_looked_at(self):
        # Without --host the run used to answer "pass --host or set TV_HOST"; a wrong count is the
        # more specific mistake and is answered first, from argparse, with its usage line.
        status, out, err = run(["--keys", "0", "--out-dir", "unused"])
        self.assertEqual(status, 2, err)
        self.assertIn("--keys", err)
        self.assertIn("usage: bench.py", err)
        self.assertEqual(FakeTV.built, [])


class ARunThatMeasuresTest(unittest.TestCase):
    def test_one_of_each_still_runs_and_writes_its_result(self):
        # The checks must not refuse a run a reader can actually make: the smallest one of each.
        with tempfile.TemporaryDirectory() as tmp:
            status, out, err = run(["--host", "192.0.2.7", "--out-dir", tmp,
                                    "--keys", "1", "--series", "1", "--cold", "1",
                                    "--returns", "1", "--idle", "1"])
            self.assertEqual(status, 0, f"a run with one of each exited {status}:\n{err}")
            self.assertEqual(FakeTV.built, [("192.0.2.7", "5555")], "the run never reached the TV")
            path = os.path.join(tmp, "result.json")
            self.assertTrue(os.path.exists(path), f"no result.json; the run printed:\n{out}")
            with open(path, encoding="utf-8") as f:
                result = json.load(f)
            self.assertEqual(result["keys"]["series"], 1)
            self.assertEqual(result["keys"]["per_series"], 1)
            self.assertIn("Written: " + path, out)


class SourceTest(unittest.TestCase):
    def setUp(self):
        with open(bench.__file__, encoding="utf-8") as f:
            self.source = f.read()

    def test_the_defaults_are_above_the_floor(self):
        # The five defaults are what the docstring's usage line runs with; a default moved to 0
        # would be refused by this very check. Read out of the source, so a changed default shows here.
        for option in ("keys", "series", "cold", "returns", "idle"):
            with self.subTest(option=option):
                match = re.search(rf'add_argument\("--{option}", type=int, default=(\d+)', self.source)
                self.assertIsNotNone(match, f"--{option} is not an int option any more")
                self.assertGreaterEqual(int(match.group(1)), 1, f"--{option}'s default is refused")

    def test_the_counts_are_judged_after_parse_args_and_before_the_tv(self):
        for marker in ("args = parser.parse_args()", "if args.idle < 1:", "tv = TV(args.host, args.port)"):
            self.assertTrue(marker in self.source, f"{marker!r} is gone from bench.py")
        parse = self.source.index("args = parser.parse_args()")
        check = self.source.index('if args.idle < 1:')
        tv = self.source.index("tv = TV(args.host, args.port)")
        self.assertLess(parse, check, "the count checks are above parse_args()")
        self.assertLess(check, tv, "the count checks are below tv = TV(): the TV is asked first")

    def test_all_five_counts_are_checked(self):
        for option in ("keys", "series", "cold", "returns"):
            self.assertTrue(f'("{option}", ' in self.source, f"--{option} is not in the loop")
        self.assertTrue('if args.idle < 1:' in self.source, "--idle is not checked")


if __name__ == "__main__":
    unittest.main()
