"""`recovery.py --commands` without TV_HOST cannot name the record, so it must not print a plan.

The record is one file per TV and list, and `record_path` names it after the address the TV was
reached at. A run with no TV_HOST therefore has no name to look under: it used to fall back to the
"no-host" tag `record_name` gives an empty tag, find nothing, and print the no-record fallback - the
batch that enables every listed package - while saying "no recovery record for this TV and list",
about a TV whose record may well be sitting right there. That fallback is the one plan produced
without looking (docs/restore.md: packages the owner had already disabled are enabled too), so it is
the one run that must say it did not look instead. Nothing reaches the TV either, so it is a run that
never asked the TV anything: exit 2, as tools/tv.py, check.py and privacy.py answer that shape.

`--commands` still asks the TV nothing and needs no ADB client; TV_HOST is only the record's name.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
RECOVERY = ROOT / "tools" / "recovery.py"
ADDRESS = "192.168.1.50"
# Stands in for tools/tv.py under restore.sh: keeps the batch it is sent, so a test can see that
# nothing was sent.
FAKE_TV = ('import sys\n'
           'open(__import__("os").environ["CAPTURE"], "w").write(sys.stdin.read())\n')


def digest(list_path):
    return hashlib.sha1(str(Path(list_path).resolve()).encode()).hexdigest()[:8]


def record_for(list_path, host):
    return {
        "version": 1,
        "created": "2026-10-30T10:00:00+0800",
        "host": host,
        "list": str(Path(list_path).resolve()),
        "home": "com.google.android.tvlauncher/.MainActivity",
        "settings": {"window_animation_scale": "0.5",
                     "transition_animation_scale": "null",
                     "animator_duration_scale": "1.0"},
        "packages": {"com.example.enabled": "enabled",
                     "com.example.already_off": "disabled"},
    }


class NoTVHostTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.home = self.base / "home"
        self.home.mkdir()
        self.list_path = self.base / "debloat.txt"
        self.list_path.write_text("# My TV\ncom.example.enabled\ncom.example.already_off\n")
        self.records = self.home / ".cleanyourtv"

    def write_record(self, host=ADDRESS):
        self.records.mkdir(exist_ok=True)
        path = self.records / f"recovery-{host}-{digest(self.list_path)}.json"
        path.write_text(json.dumps(record_for(self.list_path, host)))
        return path

    def env(self, host=None):
        env = {key: value for key, value in os.environ.items()
               if key not in ("TV_HOST", "TV_PORT")}
        env["HOME"] = str(self.home)
        if host is not None:
            env["TV_HOST"] = host
        return env

    def run_recovery(self, *args, host=None):
        return subprocess.run(
            [sys.executable, "-I", str(RECOVERY), *args],
            env=self.env(host), capture_output=True, text=True, timeout=120,
        )

    def test_a_run_without_tv_host_stops_instead_of_printing_the_fallback(self):
        """The case that matters: a record for this list is there, under an address not given."""
        self.write_record()
        result = self.run_recovery("--commands", str(self.list_path))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual(result.stdout, "", "a plan was printed for a record this run never read")
        self.assertIn("Set TV_HOST to your TV's IP address", result.stderr)
        self.assertIn("named after the address the TV was reached at", result.stderr)
        self.assertIn("Nothing was sent to the TV.", result.stderr)
        self.assertNotIn("no recovery record", result.stderr)
        self.assertNotIn("pm enable", result.stderr + result.stdout)

    def test_a_run_without_tv_host_writes_no_record_of_its_own(self):
        """Nothing in ~/.cleanyourtv/ is touched: a name is looked under, never made up."""
        self.write_record()
        before = {path.name: path.read_text() for path in self.records.iterdir()}
        result = self.run_recovery("--commands", str(self.list_path))
        self.assertEqual(result.returncode, 2, result.stderr)
        after = {path.name: path.read_text() for path in self.records.iterdir()}
        self.assertEqual(before, after)
        self.assertFalse([name for name in after if "no-host" in name],
                         f"a record under the no-host name appeared: {sorted(after)}")

    def test_a_run_without_tv_host_says_it_for_a_run_with_no_record_too(self):
        """The same answer whether or not a record exists: the run did not look either way."""
        result = self.run_recovery("--commands", str(self.list_path))
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertIn("Nothing was sent to the TV.", result.stderr)

    def test_the_address_names_the_record_and_the_plan_is_the_recorded_one(self):
        """The guard is about the missing address, not about printing a plan at all."""
        self.write_record()
        result = self.run_recovery("--commands", str(self.list_path), host=ADDRESS)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.splitlines(), [
            "pm enable com.example.enabled",
            "cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity",
            "settings put global window_animation_scale 0.5",
            "settings delete global transition_animation_scale",
            "settings put global animator_duration_scale 1.0",
            "pm list packages -d",
        ])
        self.assertIn("leaving com.example.already_off alone (disabled when the record was taken)",
                      result.stderr)

    def test_a_run_with_an_address_and_no_record_still_falls_back(self):
        """docs/restore.md's "Without a record": unchanged, and it still says what it enables."""
        result = self.run_recovery("--commands", str(self.list_path), host=ADDRESS)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("no recovery record for this TV and list", result.stderr)
        self.assertIn("Packages that were already disabled before cleanup will be enabled too",
                      result.stderr)
        self.assertIn("pm enable com.example.already_off", result.stdout)

    def test_restore_sh_sends_nothing_without_tv_host(self):
        """restore.sh is the caller that matters: no batch, no TV, and it stops."""
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("restore.sh is a POSIX script; it needs bash")
        repo = self.base / "repo"
        tools = repo / "tools"
        tools.mkdir(parents=True)
        for name in ("restore.sh", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, tools / name)
        (tools / "tv.py").write_text(FAKE_TV)
        client = tools / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)
        self.write_record()
        capture = self.base / "sent.txt"
        env = self.env()
        env["CAPTURE"] = str(capture)
        result = subprocess.run(
            ["bash", str(tools / "restore.sh"), str(self.list_path)],
            cwd=repo, env=env, capture_output=True, text=True,
            stdin=subprocess.DEVNULL, timeout=120,
        )
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Nothing was sent to the TV.", result.stderr)
        self.assertFalse(capture.exists() and capture.read_text().strip(),
                         f"a batch reached the TV: {capture.read_text() if capture.exists() else ''}")

    def test_the_usage_block_names_tv_host(self):
        """A read guard: the docstring that hands out the command says what it needs."""
        docstring = RECOVERY.read_text().split('"""')[1]
        self.assertIn("TV_HOST", docstring)
        self.assertIn("only the address that names the record", docstring)
        self.assertIn("so both need TV_HOST", docstring)


if __name__ == "__main__":
    unittest.main()
