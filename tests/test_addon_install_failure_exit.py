"""An add-on installer whose `pm install` fails must stop with an error, not print "Installed."."""
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SCRIPTS = [f"addons/{name}/build-and-install.sh" for name in (
    "haunted", "koi-pond", "news-screensaver", "photo-screensaver", "sleep-timer", "tv-static", "wake-app")]


@unittest.skipIf(os.name == "nt", "the installers require a POSIX shell")
class InstallFailureTest(unittest.TestCase):
    def run_script(self, script):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        addon = root / script
        addon.parent.mkdir(parents=True)
        addon.write_text((REPO / script).read_text())
        gradlew = addon.parent / "gradlew"
        gradlew.write_text("#!/bin/sh\nexit 0\n")
        gradlew.chmod(0o755)
        apk = addon.parent / "app/build/outputs/apk/release/app-release.apk"
        apk.parent.mkdir(parents=True)
        apk.write_bytes(b"apk")
        tools = root / "tools"
        (tools / "venv/bin").mkdir(parents=True)
        (tools / "venv/bin/python").symlink_to(sys.executable)
        (tools / "b64push.py").write_text("")
        log = root / "tv.log"
        (tools / "tv.py").write_text(
            "import sys\n"
            f"open({str(log)!r}, 'a').write(sys.argv[1] + '\\n')\n"
            "if sys.argv[1].startswith('pm install'):\n"
            "    print('Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]')\n")
        result = subprocess.run(["bash", str(addon)], cwd=root, capture_output=True, text=True,
                                env={**os.environ, "TV_HOST": "fake"})
        return result, log.read_text()

    def test_failed_install_stops_with_an_error(self):
        for script in SCRIPTS:
            with self.subTest(script=script):
                result, commands = self.run_script(script)
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn("Installed.", result.stdout)
                self.assertIn("INSTALL_FAILED_INSUFFICIENT_STORAGE", result.stdout + result.stderr)
                self.assertNotIn("settings put", commands)
                self.assertNotIn("appops", commands)


if __name__ == "__main__":
    unittest.main()
