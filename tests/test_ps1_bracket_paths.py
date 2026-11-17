"""disable.ps1, restore.ps1 and theme.ps1 take a list or theme path with brackets as the path it is.

Resolve-Path, Get-Content, Test-Path and Get-ChildItem read [ and ] as wildcard syntax unless they are given
-LiteralPath, so a list in a folder such as `tv [old]` was "not found" by disable.ps1 and restore.ps1, and
theme.ps1 called the config.json of a theme in such a folder "not valid JSON". theme.sh, disable.sh and
restore.sh take the same paths as typed.

tools/tv.py, b64push.py and theme_restart.py are stand-ins that record their calls. The cases need `pwsh` on
PATH and skip without it; CI has it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
PWSH = shutil.which("pwsh")

# Stands in for tv.py and b64push.py: records each call and the batch it was sent.
FAKE = r'''
import json
import os
import sys

argv = sys.argv[1:]
batch = sys.stdin.read() if "--batch" in argv else None
with open(os.environ["FAKE_LOG"], "a") as log:
    log.write(json.dumps({"tool": os.path.basename(__file__), "argv": argv, "batch": batch}) + "\n")
'''


class Ps1BracketPathsTest(unittest.TestCase):
    def setUp(self):
        if not PWSH:
            raise unittest.SkipTest("the PowerShell scripts need pwsh on PATH")
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        self.tools = self.repo / "tools"
        self.tools.mkdir(parents=True)
        for name in ("disable.ps1", "restore.ps1", "theme.ps1", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, self.tools / name)
        for name in ("tv.py", "b64push.py", "theme_restart.py"):
            (self.tools / name).write_text(FAKE)
        self.venv()
        self.home = self.base / "home"
        self.home.mkdir()
        self.log = self.base / "calls.jsonl"
        self.folder = self.base / "tv [old]"
        self.folder.mkdir()

    def venv(self):
        # The scripts ask for the Windows path; on Linux and macOS this shim runs the tests' interpreter.
        client = self.tools / "venv" / "Scripts" / "python.exe"
        client.parent.mkdir(parents=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)

    def run_script(self, script, *args):
        env = dict(os.environ, TV_HOST="192.0.2.1", HOME=str(self.home), USERPROFILE=str(self.home),
                   FAKE_LOG=str(self.log))
        return subprocess.run([PWSH, "-NoProfile", "-File", str(self.tools / script), "-TvHost", "192.0.2.1",
                               *args], cwd=self.repo, env=env, capture_output=True, text=True, timeout=180)

    def calls(self):
        if not self.log.exists():
            return []
        return [json.loads(line) for line in self.log.read_text().splitlines() if line]

    def test_restore_reads_a_list_in_a_folder_with_brackets(self):
        listed = self.folder / "debloat.txt"
        listed.write_text("# My TV\ncom.example.ads\n")
        result = self.run_script("restore.ps1", str(listed))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        batches = [c["batch"] for c in self.calls() if c["batch"]]
        self.assertTrue(any("pm enable com.example.ads" in b for b in batches), self.calls())

    def test_disable_reads_a_list_in_a_folder_with_brackets(self):
        # A core package stops the run before the TV is asked anything, after the list was read.
        listed = self.folder / "debloat.txt"
        listed.write_text("# My TV\ncom.android.systemui\n")
        result = self.run_script("disable.ps1", str(listed))
        output = result.stdout + result.stderr
        self.assertNotEqual(result.returncode, 0, output)
        self.assertIn("core Android", output)
        self.assertEqual(self.calls(), [])

    def test_theme_reads_a_theme_in_a_folder_with_brackets(self):
        theme = self.folder / "mytheme"
        (theme / "wallpapers").mkdir(parents=True)
        (theme / "config.json").write_text('{"columns": 5}\n')
        (theme / "wallpapers" / "a.jpg").write_bytes(b"jpeg")
        result = self.run_script("theme.ps1", str(theme))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        pushed = [c["argv"] for c in self.calls()]
        self.assertIn("--push", [a for argv in pushed for a in argv[:1]], pushed)
        self.assertTrue(any(c["tool"] == "b64push.py" and c["argv"][0].endswith("a.jpg") for c in self.calls()),
                        pushed)


class DockerBuildBracketPathTest(unittest.TestCase):
    """docker-build.ps1 run from a checkout whose path has brackets gets as far as docker."""

    def test_docker_build_runs_from_a_checkout_with_brackets(self):
        shell = PWSH or (shutil.which("powershell") if os.name == "nt" else None)
        if not shell:
            self.skipTest("docker-build.ps1 needs pwsh on PATH (or powershell on Windows)")
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            repo = base / "repo [x]"
            (repo / "tools").mkdir(parents=True)
            (repo / "launcher").mkdir()
            shutil.copy2(ROOT / "tools" / "docker-build.ps1", repo / "tools" / "docker-build.ps1")
            (repo / "launcher" / "settings.gradle").write_text("")
            bin_dir = base / "bin"
            bin_dir.mkdir()
            log = base / "docker.log"
            if os.name == "nt":
                (bin_dir / "docker.cmd").write_text(f'@echo %*>>"{log}"\r\n@exit /b 0\r\n')
            else:
                fake = bin_dir / "docker"
                fake.write_text(f'#!/bin/sh\necho "$@" >> "{log}"\nexit 0\n')
                fake.chmod(0o755)
            env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ["PATH"])
            result = subprocess.run([shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                                     str(repo / "tools" / "docker-build.ps1"), "-Project", "launcher"],
                                    cwd=base, env=env, capture_output=True, text=True, timeout=180)
            output = result.stdout + result.stderr
            self.assertEqual(result.returncode, 0, output)
            self.assertNotIn("null", output)
            self.assertTrue(log.exists() and "run" in log.read_text(), output)


if __name__ == "__main__":
    unittest.main()
