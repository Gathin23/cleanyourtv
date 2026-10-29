"""tools/disable.sh and disable.ps1 say so when the TV's network ADB accepts any device.

That is `ro.adb.secure=0`, and also an empty value (#1720): on a debug (userdebug or eng) build, and on
Android 4.4, adbd reads a missing ro.adb.secure as 0 and gives a shell with no "Allow" prompt. Sets in
examples/ do exactly that (rca-rc50a21s-4ksm, a userdebug build; caxium-58-realtek-rtk2851p,
"ro.adb.secure unset"). A read that fails is not an empty value: it only skips the note.

Run from the repository root: python3 -m unittest discover -s tests -v
The PowerShell cases need `pwsh` on PATH and skip without it; CI's Ubuntu runner has it.
"""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
LIST = "# My TV\ncom.example.one\ncom.example.two\n"
PWSH = shutil.which("pwsh")


class AdbSecureWarningTest(unittest.TestCase):
    def entries(self):
        return ("sh", "ps1") if PWSH else ("sh",)

    def run_disable(self, getprop, entry="sh"):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            tools = repo / "tools"
            tools.mkdir(parents=True)
            shutil.copy2(ROOT / "tools" / "disable.sh", tools / "disable.sh")
            shutil.copy2(ROOT / "tools" / "disable.ps1", tools / "disable.ps1")
            # Fake tv.py client: answers getprop as given, takes the record, records --batch input.
            # disable.ps1 asks for the Windows path, which PowerShell resolves here too.
            for client in (tools / "venv" / "bin" / "python",
                           tools / "venv" / "Scripts" / "python.exe"):
                client.parent.mkdir(parents=True)
                client.write_text(
                    '#!/bin/sh\n'
                    'case "$*" in\n'
                    f'  *getprop*) {getprop} ;;\n'
                    '  *recovery.py*) exit 0 ;;\n'
                    '  *--batch*) cat > "$CAPTURE" ;;\n'
                    'esac\n')
                client.chmod(0o755)
            (repo / "debloat.txt").write_text(LIST)
            capture = Path(tmp) / "commands.txt"
            env = dict(os.environ, CAPTURE=str(capture))
            if entry == "sh":
                if os.name != "posix" or not shutil.which("bash"):
                    raise unittest.SkipTest("disable.sh is a POSIX script; it needs bash")
                argv = ["bash", str(tools / "disable.sh"), "debloat.txt"]
            else:
                argv = [PWSH, "-NoProfile", "-File", str(tools / "disable.ps1"),
                        "-TvHost", "192.168.1.50", "debloat.txt"]
            result = subprocess.run(argv, cwd=repo, env=env, capture_output=True, text=True,
                                    stdin=subprocess.DEVNULL, timeout=120)
            output = result.stdout + result.stderr
            self.assertEqual(result.returncode, 0, output)
            # The note never changes what is sent.
            self.assertEqual(capture.read_text().splitlines(), [
                "pm disable-user --user 0 com.example.one",
                "pm disable-user --user 0 com.example.two",
                "pm list packages -d",
            ])
            # Write-Warning can wrap a long line, so the PowerShell output is read with the line
            # breaks joined; the shell script's lines are joined the same way.
            return re.sub(r"\s+", " ", output)

    def test_warns_when_adb_is_not_secure(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                output = self.run_disable('echo 0', entry)
                self.assertIn("reports ro.adb.secure=0", output)
                self.assertIn("Isn't ADB access a security hole?", output)
                self.assertIn("Wireless debugging", output)

    def test_warns_with_crlf_output(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.assertIn("reports ro.adb.secure=0", self.run_disable(r"printf '0\r\n'", entry))

    def test_quiet_when_adb_is_secure(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.assertNotIn("ro.adb.secure", self.run_disable('echo 1', entry))

    def test_warns_when_the_property_is_empty(self):
        # #1720: an unset ro.adb.secure is no authorisation on a debug build or Android 4.4.
        for entry in self.entries():
            for getprop in ('echo', r"printf '\r\n'", "printf ''"):
                with self.subTest(entry=entry, getprop=getprop):
                    output = self.run_disable(getprop, entry)
                    self.assertIn("does not set ro.adb.secure", output)
                    self.assertIn("the same as ro.adb.secure=0", output)
                    self.assertIn("Isn't ADB access a security hole?", output)
                    self.assertNotIn("reports ro.adb.secure=0", output)

    def test_quiet_when_the_read_fails(self):
        # A failed read skips the note; it does not stop the list or guess, and it is not read as an
        # empty value either.
        for entry in self.entries():
            for getprop in ('echo 0; exit 1', 'exit 1'):
                with self.subTest(entry=entry, getprop=getprop):
                    self.assertNotIn("ro.adb.secure", self.run_disable(getprop, entry))


if __name__ == "__main__":
    unittest.main()
