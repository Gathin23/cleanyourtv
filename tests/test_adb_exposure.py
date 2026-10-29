"""tools/disable.sh says so when the TV's network ADB accepts any device (ro.adb.secure=0).

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
LIST = "# My TV\ncom.example.one\ncom.example.two\n"


class AdbSecureWarningTest(unittest.TestCase):
    def run_disable(self, getprop):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            tools = repo / "tools"
            tools.mkdir(parents=True)
            shutil.copy2(ROOT / "tools" / "disable.sh", tools / "disable.sh")
            client = tools / "venv" / "bin" / "python"
            client.parent.mkdir(parents=True)
            # Fake tv.py client: answers getprop as given, takes the record, records --batch input.
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
            result = subprocess.run(["bash", str(tools / "disable.sh"), "debloat.txt"], cwd=repo,
                                    env=env, capture_output=True, text=True,
                                    stdin=subprocess.DEVNULL, timeout=60)
            self.assertEqual(result.returncode, 0, result.stderr)
            # The note never changes what is sent.
            self.assertEqual(capture.read_text().splitlines(), [
                "pm disable-user --user 0 com.example.one",
                "pm disable-user --user 0 com.example.two",
                "pm list packages -d",
            ])
            return result.stderr

    def test_warns_when_adb_is_not_secure(self):
        stderr = self.run_disable('echo 0')
        self.assertIn("ro.adb.secure=0", stderr)
        self.assertIn("Isn't ADB access a security hole?", stderr)

    def test_warns_with_crlf_output(self):
        stderr = self.run_disable(r"printf '0\r\n'")
        self.assertIn("ro.adb.secure=0", stderr)

    def test_quiet_when_adb_is_secure(self):
        self.assertNotIn("ro.adb.secure", self.run_disable('echo 1'))

    def test_quiet_when_the_property_is_missing(self):
        self.assertNotIn("ro.adb.secure", self.run_disable('echo'))

    def test_quiet_when_the_read_fails(self):
        # A failed read skips the note; it does not stop the list or guess.
        self.assertNotIn("ro.adb.secure", self.run_disable('echo 0; exit 1'))


if __name__ == "__main__":
    unittest.main()
