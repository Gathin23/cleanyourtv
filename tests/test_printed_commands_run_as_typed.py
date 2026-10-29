"""The commands the tools print as the next step run as printed, in the form their docs use.

git has the tools' .py files as 100644, so a printed `tools/privacy.py --write privacy.txt`,
`tools/relay.py 192.168.1.50` or `tools/trace_report.py traces/before` stopped at "permission denied"
(exit 126). Started with python3 instead, privacy.py cannot load tools/tv.py without the venv's
adb_shell. The README and the tools' own guides run them with tools/venv's Python, so the tools print
`tools/venv/bin/python -I tools/<x>.py`, and the shell scripts they print are ones git keeps executable.

The TV is faked or never reached, so these need no TV and no adb_shell.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
VENV_PYTHON = "tools/venv/bin/python"


def load(name):
    spec = importlib.util.spec_from_file_location(f"cyt_printed_{name}", ROOT / "tools" / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def problems(command):
    """Why COMMAND, typed in the repository root, would not run as printed; [] when it would."""
    words = shlex.split(command.replace("<tv ip>", "192.0.2.1"), comments=True)
    while words and re.fullmatch(r"[A-Z_]+=\S*", words[0]):
        words.pop(0)
    if not words:
        return [f"{command!r}: no command"]
    program = words[0]
    if program == VENV_PYTHON:
        if words[1:2] != ["-I"] or len(words) < 3 or not (ROOT / words[2]).is_file():
            return [f"{command!r}: not `{VENV_PYTHON} -I tools/<x>.py`"]
        return []
    if program.endswith(".py"):
        return [f"{command!r}: starts {program} itself; git has it as 100644 (permission denied), and "
                f"without the venv it cannot load tools/tv.py. Print `{VENV_PYTHON} -I {program}`"]
    path = ROOT / program
    if not program.startswith("tools/") or not path.is_file():
        return [f"{command!r}: {program} is not a script in tools/"]
    if os.name != "nt" and not os.access(path, os.X_OK):
        return [f"{command!r}: {program} is not executable"]
    return []


class PrivacyNextStepTest(unittest.TestCase):
    def report_block(self):
        privacy = load("privacy")
        out = io.StringIO()
        with mock.patch.dict(os.environ, {"TV_HOST": "192.0.2.1"}), contextlib.redirect_stdout(out):
            privacy.print_report({"installed": [], "disabled": [], "entries": [], "skipped": 0})
        lines = out.getvalue().splitlines()
        start = lines.index("What to do with it") + 1
        end = lines.index("", start)
        return [line.strip() for line in lines[start:end]]

    def test_the_write_step_is_the_readme_form(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        found = re.search(r"`(TV_HOST=\S+ tools/venv/bin/python -I tools/privacy\.py)", readme)
        self.assertIsNotNone(found, "README.md no longer shows how to start tools/privacy.py")
        expected = re.sub(r"^TV_HOST=\S+", "TV_HOST=<tv ip>", found.group(1)) + " --write privacy.txt"
        write = [line for line in self.report_block() if "--write" in line]
        self.assertEqual(len(write), 1, write)
        self.assertTrue(write[0].startswith(expected + " "), f"{write[0]!r} is not {expected!r}")

    def test_every_step_runs_as_printed_with_the_same_tv(self):
        block = self.report_block()
        self.assertEqual(len(block), 3, block)
        for line in block:
            with self.subTest(line=line):
                self.assertEqual(problems(line), [])
                self.assertTrue(line.startswith("TV_HOST=<tv ip> "), line)


class RelayExampleTest(unittest.TestCase):
    def example(self, *args, **env):
        base = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
        try:
            result = subprocess.run([sys.executable, str(ROOT / "tools" / "relay.py"), *args],
                                    env={**base, **env}, capture_output=True, text=True, timeout=10)
        except subprocess.TimeoutExpired:
            self.fail(f"relay.py {' '.join(args)} started a relay instead of refusing")
        self.assertEqual(result.returncode, 2, result.stderr)
        found = re.search(r"e\.g\.\s+(.+)$", result.stderr, re.M)
        self.assertIsNotNone(found, result.stderr)
        return found.group(1).strip()

    def test_the_examples_are_the_guides_form(self):
        guide = (ROOT / "tools" / "relay.md").read_text(encoding="utf-8")
        found = re.search(r"^(\S+ -I tools/relay\.py) 192\.168\.1\.50$", guide, re.M)
        self.assertIsNotNone(found, "tools/relay.md no longer shows how to start the relay")
        for args, env in (((), {}),
                          (("192.0.2.1", "--port", "0"), {"TV_HOST": "127.0.0.1", "TV_PORT": "15555"})):
            with self.subTest(args=args, env=env):
                command = self.example(*args, **env)
                self.assertEqual(problems(command), [])
                self.assertTrue(command.startswith(found.group(1) + " 192.168.1.50"), command)


class FakeDevice:
    def shell(self, command, read_timeout_s=None):
        return ""

    def close(self):
        pass


class TraceNextStepTest(unittest.TestCase):
    def test_the_report_step_runs_as_printed(self):
        trace = load("trace")

        def record_one(dev, setup, steps, package, path, run_shell=None):
            Path(path).write_bytes(b"trace")
            return 5

        fakes = {"tv": types.SimpleNamespace(connect=FakeDevice), "bench": types.ModuleType("bench")}
        out = io.StringIO()
        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict(sys.modules, fakes), \
                mock.patch.object(sys, "path", list(sys.path)), \
                mock.patch.object(trace, "record_one", record_one), contextlib.redirect_stdout(out):
            folder = os.path.join(tmp, "before")
            trace.main(["nav", "--runs", "1", "--out", folder])
        found = re.search(r"^next: (.+?)   \(see tools/trace\.md\)$", out.getvalue(), re.M)
        self.assertIsNotNone(found, out.getvalue())
        command = found.group(1)
        self.assertEqual(command, f"{VENV_PYTHON} -I tools/trace_report.py {folder}")
        self.assertEqual(problems(command.replace(folder, "traces/before")), [])


if __name__ == "__main__":
    unittest.main()
