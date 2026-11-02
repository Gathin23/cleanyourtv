"""The image uploader must not say 'sent' when Android rejects the provider write."""
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "addons/startup-branding/push-image.sh"


@unittest.skipIf(os.name == "nt", "push-image.sh requires a POSIX shell")
class PushImageTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        addon = self.root / "addons/startup-branding"
        addon.mkdir(parents=True)
        tools = self.root / "tools"
        (tools / "venv/bin").mkdir(parents=True)
        (tools / "venv/bin/python").symlink_to(sys.executable)
        self.stage = self.root / "staged-image"
        script = SCRIPT.read_text().replace("TMP=/data/local/tmp/startup-branding-picture", f"TMP={self.stage}")
        (addon / "push-image.sh").write_text(script)
        (tools / "b64push.py").write_text("import shutil, sys\nshutil.copyfile(sys.argv[1], sys.argv[2])\n")
        (tools / "tv.py").write_text('''\
import os, subprocess, sys
root = os.environ["FAKE_ROOT"]
result = subprocess.run(["sh", "-c", sys.argv[1]], capture_output=True, text=True,
                        env={**os.environ, "PATH": root + "/bin:" + os.environ["PATH"]})
sys.stdout.write(result.stdout)
sys.stderr.write(result.stderr)
''')
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for name, body in {
            "pm": "#!/bin/sh\necho package:/fake/startup-branding.apk\n",
            "content": '''#!/bin/sh
case "$CONTENT_MODE" in
  success) exit 0 ;;
  error) echo 'Could not find provider' >&2; exit 0 ;;
  nonzero) exit 1 ;;
esac
''',
        }.items():
            path = bin_dir / name
            path.write_text(body)
            path.chmod(0o755)
        (self.root / "logo.png").write_bytes(b"test image")

    def run_push(self, mode):
        return subprocess.run(
            ["bash", str(self.root / "addons/startup-branding/push-image.sh"), "logo.png"],
            cwd=self.root,
            env={**os.environ, "TV_HOST": "fake", "FAKE_ROOT": str(self.root), "CONTENT_MODE": mode},
            capture_output=True, text=True,
        )

    def test_zero_exit_provider_error_is_not_reported_as_sent(self):
        result = self.run_push("error")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Could not find provider", result.stderr)
        self.assertNotIn("sent logo.png", result.stdout)
        self.assertFalse(self.stage.exists())

    def test_nonzero_provider_error_is_not_reported_as_sent(self):
        result = self.run_push("nonzero")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("sent logo.png", result.stdout)
        self.assertFalse(self.stage.exists())

    def test_silent_success_is_reported_as_sent(self):
        result = self.run_push("success")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("sent logo.png", result.stdout)
        self.assertFalse(self.stage.exists())


if __name__ == "__main__":
    unittest.main()
