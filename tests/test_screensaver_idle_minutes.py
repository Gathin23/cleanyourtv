"""IDLE_MINUTES with a leading zero (08, 09) is a number of minutes, not a bash octal error."""
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SCRIPTS = ["addons/photo-screensaver/sync.sh", "addons/news-screensaver/setup.sh"]


@unittest.skipIf(os.name == "nt", "the screensaver scripts require a POSIX shell")
class IdleMinutesTest(unittest.TestCase):
    def run_script(self, script):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        addon = root / script
        addon.parent.mkdir(parents=True)
        addon.write_text((REPO / script).read_text())
        (addon.parent / "pictures").mkdir()
        tools = root / "tools"
        (tools / "venv/bin").mkdir(parents=True)
        (tools / "venv/bin/python").symlink_to(sys.executable)
        log = root / "tv.log"
        # Every command goes to a log; `pm path` finds the app.
        (tools / "tv.py").write_text(
            "import sys\n"
            f"open({str(log)!r}, 'a').write(sys.argv[1] + '\\n')\n"
            "print('package:/fake.apk')\n")
        result = subprocess.run(["bash", str(addon)], cwd=root, capture_output=True, text=True,
                                env={**os.environ, "TV_HOST": "fake", "IDLE_MINUTES": "08"})
        return result, log.read_text() if log.exists() else ""

    def test_leading_zero_is_read_as_decimal(self):
        for script in SCRIPTS:
            with self.subTest(script=script):
                result, commands = self.run_script(script)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("screen_off_timeout 480000", commands)


if __name__ == "__main__":
    unittest.main()
