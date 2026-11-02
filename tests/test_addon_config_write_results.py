"""Add-on setup scripts must reject Android provider diagnostics, even with exit status 0."""
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


@unittest.skipIf(os.name == "nt", "add-on shell scripts need a POSIX shell")
class AddonConfigWriteResultsTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        tools = self.root / "tools"
        (tools / "venv/bin").mkdir(parents=True)
        (tools / "venv/bin/python").symlink_to(sys.executable)
        (tools / "tv.py").write_text('''\
import os, pathlib, resource, shutil, subprocess, sys
if sys.argv[1] == "--push":
    shutil.copyfile(sys.argv[2], sys.argv[3])
else:
    limit_stage = os.environ.get("STAGE_FAIL") == "1" and "-seconds.txt" in sys.argv[1] and "printf '%s'" in sys.argv[1]
    def limit_file_size():
        resource.setrlimit(resource.RLIMIT_FSIZE, (0, 0))
    result = subprocess.run(["sh", "-c", sys.argv[1]], capture_output=True, text=True,
                            env={**os.environ, "PATH": os.environ["FAKE_ROOT"] + "/bin:" + os.environ["PATH"]},
                            preexec_fn=limit_file_size if limit_stage else None)
    sys.stdout.write(result.stdout)
    sys.stderr.write(result.stderr)
    # The real tv.py also does not pass through the remote shell's exit status.
''')
        (tools / "b64push.py").write_text('''\
import pathlib, shutil, sys
source, dest = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
if source.exists(): shutil.copyfile(source, dest)
print("md5 MATCH")
''')
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for name, body in {
            "pm": "#!/bin/sh\ncase \"$1\" in path) echo package:/fake/app.apk ;; *) echo Success ;; esac\n",
            "content": '''#!/bin/sh
case "$*" in *seconds.txt*) echo write >> "$FAKE_ROOT/seconds-writes" ;; esac
case "$CONTENT_MODE" in
  error) echo 'Could not find provider' >&2; exit 0 ;;
  picture_error) case "$*" in *pictures*) echo 'Could not find provider' >&2; exit 0 ;; esac ;;
esac
exit 0
''',
            "cp": "#!/bin/sh\n[ \"${CP_FAIL:-0}\" = 1 ] && exit 1\nexec /bin/cp \"$@\"\n",
            "settings": "#!/bin/sh\ncase \"$*\" in *screen_off_timeout*) echo 600000 ;; *screensaver_components*) echo null ;; *screensaver_enabled*) echo 1 ;; esac\n",
            "logcat": "#!/bin/sh\ncase \"$1\" in -d) printf 'mqtt connected\\nfrigate ok\\n' ;; esac\n",
            "appops": "#!/bin/sh\nexit 0\n",
            "cmd": "#!/bin/sh\nexit 0\n",
            "am": "#!/bin/sh\nexit 0\n",
            "sleep": "#!/bin/sh\nexit 0\n",
        }.items():
            path = bin_dir / name
            path.write_text(body)
            path.chmod(0o755)

    def script(self, addon, filename, replacements):
        folder = self.root / "addons" / addon
        folder.mkdir(parents=True)
        text = (ROOT / "addons" / addon / filename).read_text()
        for before, after in replacements.items():
            text = text.replace(before, after)
        path = folder / filename
        path.write_text(text)
        (folder / "gradlew").write_text("#!/bin/sh\nexit 0\n")
        (folder / "gradlew").chmod(0o755)
        return path

    def run_script(self, path, mode, *args, cp_fail=False, stage_fail=False, **settings):
        return subprocess.run(
            ["bash", str(path), *map(str, args)], cwd=self.root,
            env={**os.environ, "TV_HOST": "fake", "FAKE_ROOT": str(self.root),
                 "CONTENT_MODE": mode, "CP_FAIL": "1" if cp_fail else "0",
                 "STAGE_FAIL": "1" if stage_fail else "0", **settings},
            capture_output=True, text=True,
        )

    def frigate(self, mode, cp_fail=True):
        stage = self.root / "frigate-stage"
        path = self.script("frigate-glance", "install.sh", {
            "STAGE=/data/local/tmp/fg-config.json": f"STAGE={stage}",
            "d=/sdcard/Android/data/$PKG/files": f"d={self.root}/appfiles",
        })
        config = self.root / "frigate.json"
        config.write_text('{"mqtt":"mqtt://user:pass@example.invalid:1883","frigateUrl":"http://example.invalid"}')
        return self.run_script(path, mode, config, cp_fail=cp_fail), stage

    def news(self, mode):
        stage = self.root / "news-stage"
        path = self.script("news-screensaver", "setup.sh", {
            "TMP=/data/local/tmp/cyt-news": f"TMP={stage}",
            "BEFORE=/data/local/tmp/cyt-news-before": f"BEFORE={self.root}/news-before",
        })
        feeds = self.root / "feeds.txt"
        feeds.write_text("https://example.invalid/feed.xml\n")
        return self.run_script(path, mode, feeds), stage

    def photos(self, mode, with_image=False, cp_fail=False):
        stage = self.root / "photo-stage"
        path = self.script("photo-screensaver", "sync.sh", {
            "TMP=/data/local/tmp/cyt-screensaver": f"TMP={stage}",
            "BEFORE=/data/local/tmp/cyt-screensaver-before": f"BEFORE={self.root}/photo-before",
            "DIR=/sdcard/Android/data/$PKG/files/pictures": f"DIR={self.root}/tv-pictures",
        })
        pictures = self.root / "pictures"
        pictures.mkdir(exist_ok=True)
        if with_image:
            (pictures / "photo.jpg").write_bytes(b"test image")
        return self.run_script(path, mode, pictures, cp_fail=cp_fail), stage

    def test_frigate_rejects_zero_exit_provider_error(self):
        result, stage = self.frigate("error")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("config pushed", result.stdout)
        self.assertIn("Could not find provider", result.stderr)
        self.assertFalse(stage.exists())

    def test_frigate_accepts_silent_provider_write(self):
        result, stage = self.frigate("success")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("config pushed", result.stdout)
        self.assertFalse(stage.exists())

    def test_frigate_keeps_direct_copy_fallback(self):
        result, stage = self.frigate("error", cp_fail=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("config pushed", result.stdout)
        self.assertTrue((self.root / "appfiles/config.json").exists())
        self.assertFalse(stage.exists())

    def test_news_rejects_zero_exit_provider_error_before_enabling(self):
        result, stage = self.news("error")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("Screensaver: News", result.stdout)
        self.assertFalse(Path(str(stage) + "-feeds.txt").exists())

    def test_news_accepts_silent_provider_write(self):
        result, _ = self.news("success")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Screensaver: News", result.stdout)

    def test_news_rejects_failed_seconds_stage_before_provider_write(self):
        stage = self.root / "news-stage"
        path = self.script("news-screensaver", "setup.sh", {
            "TMP=/data/local/tmp/cyt-news": f"TMP={stage}",
            "BEFORE=/data/local/tmp/cyt-news-before": f"BEFORE={self.root}/news-before",
        })
        result = self.run_script(path, "success", stage_fail=True, SECONDS_PER_HEADLINE="120")
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue("could not stage" in result.stderr or "could not write" in result.stderr)
        self.assertNotIn("Screensaver: News", result.stdout)
        self.assertFalse((self.root / "seconds-writes").exists())

    def test_photos_rejects_zero_exit_seconds_error(self):
        result, stage = self.photos("error")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("Screensaver: Photos", result.stdout)
        self.assertFalse(Path(str(stage) + "-seconds.txt").exists())

    def test_photos_rejects_failed_seconds_stage_before_provider_write(self):
        stage = self.root / "photo-stage"
        path = self.script("photo-screensaver", "sync.sh", {
            "TMP=/data/local/tmp/cyt-screensaver": f"TMP={stage}",
            "BEFORE=/data/local/tmp/cyt-screensaver-before": f"BEFORE={self.root}/photo-before",
            "DIR=/sdcard/Android/data/$PKG/files/pictures": f"DIR={self.root}/tv-pictures",
        })
        pictures = self.root / "pictures"
        pictures.mkdir()
        result = self.run_script(path, "success", pictures, stage_fail=True, SECONDS_PER_PICTURE="120")
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue("could not stage" in result.stderr or "could not write" in result.stderr)
        self.assertNotIn("Screensaver: Photos", result.stdout)
        self.assertFalse((self.root / "seconds-writes").exists())

    def test_photos_fallback_copies_when_provider_prints_error(self):
        result, _ = self.photos("picture_error", with_image=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((self.root / "tv-pictures/photo.jpg").exists())
        self.assertIn("Screensaver: Photos", result.stdout)

    def test_photos_stops_when_provider_and_copy_both_fail(self):
        result, stage = self.photos("picture_error", with_image=True, cp_fail=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("Screensaver: Photos", result.stdout)
        self.assertFalse(Path(str(stage) + "-photo.jpg").exists())


if __name__ == "__main__":
    unittest.main()
