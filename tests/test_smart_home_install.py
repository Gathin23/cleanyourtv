"""The Smart Home installer must not claim a failed provider write succeeded."""
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
INSTALL = ROOT / "addons/smart-home/install.sh"


@unittest.skipIf(os.name == "nt", "install.sh requires a POSIX shell")
class SmartHomeInstallTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        addon = self.root / "addons/smart-home"
        addon.mkdir(parents=True)
        tools = self.root / "tools"
        (tools / "venv/bin").mkdir(parents=True)
        (tools / "venv/bin/python").symlink_to(sys.executable)
        shutil.copy2(INSTALL, addon / "install.sh")
        script = (addon / "install.sh").read_text().replace(
            "STAGE=/data/local/tmp/smart-home-config.json",
            f"STAGE={self.root}/smart-home-config.json",
        )
        (addon / "install.sh").write_text(script)
        (addon / "gradlew").write_text("#!/bin/sh\nexit 0\n")
        (addon / "gradlew").chmod(0o755)
        (tools / "b64push.py").write_text("pass\n")
        (tools / "tv.py").write_text('''\
import os
import pathlib
import shutil
import subprocess
import sys

root = pathlib.Path(os.environ["FAKE_ROOT"])
if sys.argv[1] == "--push":
    shutil.copyfile(sys.argv[2], root / "smart-home-config.json")
else:
    command = sys.argv[1]
    path = os.environ["PATH"]
    result = subprocess.run(["sh", "-c", command], capture_output=True, text=True,
                            env={**os.environ, "PATH": str(root / "bin") + os.pathsep + path})
    sys.stdout.write(result.stdout)
    sys.stderr.write(result.stderr)
    # tv.py passes through shell text, but not the remote shell's exit status.
''')
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for name, body in {
            "pm": '#!/bin/sh\necho Success\n',
            "am": '#!/bin/sh\ntouch "$FAKE_ROOT/app-running"\nexit 0\n',
            "sleep": '#!/bin/sh\nexit 0\n',
            "content": '''#!/bin/sh
count_file="$FAKE_ROOT/count"
count=$(cat "$count_file" 2>/dev/null || echo 0)
count=$((count + 1))
echo "$count" > "$count_file"
case "$CONTENT_MODE" in
  success) exit 0 ;;
  provider_requires_launch) if [ -f "$FAKE_ROOT/app-running" ]; then exit 0; fi; echo 'Could not find provider' >&2; exit 0 ;;
  error_then_success) if [ "$count" -lt 3 ]; then echo 'Could not find provider' >&2; fi; exit 0 ;;
  error) echo 'Could not find provider' >&2; exit 0 ;;
  nonzero) echo 'write failed' >&2; exit 1 ;;
esac
''',
        }.items():
            path = bin_dir / name
            path.write_text(body)
            path.chmod(0o755)
        self.config = self.root / "config.json"
        self.config.write_text('{"url":"http://example.invalid","token":"test"}')

    def install(self, mode, with_config=True):
        env = {**os.environ, "TV_HOST": "fake", "FAKE_ROOT": str(self.root), "CONTENT_MODE": mode}
        args = ["bash", str(self.root / "addons/smart-home/install.sh")]
        if with_config:
            args.append(str(self.config))
        return subprocess.run(args, cwd=self.root, env=env, capture_output=True, text=True)

    def attempts(self):
        return int((self.root / "count").read_text())

    def test_zero_exit_with_provider_error_fails_after_retries(self):
        result = self.install("error")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.attempts(), 3)
        self.assertIn("Could not find provider", result.stderr)
        self.assertNotIn("config pushed", result.stdout)
        self.assertNotIn("Installed.", result.stdout)
        self.assertFalse((self.root / "smart-home-config.json").exists())

    def test_provider_available_on_third_attempt_succeeds(self):
        result = self.install("error_then_success")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.attempts(), 3)
        self.assertIn("config pushed", result.stdout)
        self.assertFalse((self.root / "smart-home-config.json").exists())

    def test_provider_that_requires_running_app_receives_config(self):
        result = self.install("provider_requires_launch")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.attempts(), 1)
        self.assertIn("config pushed", result.stdout)

    def test_silent_success_needs_one_attempt(self):
        result = self.install("success")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.attempts(), 1)
        self.assertIn("config pushed", result.stdout)

    def test_nonzero_write_does_not_claim_success(self):
        result = self.install("nonzero")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.attempts(), 3)
        self.assertNotIn("Installed.", result.stdout)

    def test_install_without_config_does_not_write_it(self):
        result = self.install("error", with_config=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.root / "count").exists())
        self.assertIn("Installed.", result.stdout)


if __name__ == "__main__":
    unittest.main()
