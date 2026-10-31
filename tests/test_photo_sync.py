"""Run the real photo sync script against local recording transfer boundaries, never a TV."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
BOUNDARY = r'''
import hashlib, json, os, shutil, sys
from pathlib import Path

pictures = Path(os.environ["FAKE_PICTURES"])
stage = Path(os.environ["FAKE_STAGE"])
uri = "content://com.gathin.photodream.files/pictures/"
if Path(__file__).name == "b64push.py":
    source, target = sys.argv[1:]
    shutil.copyfile(source, stage / Path(target).name)
    with open(os.environ["FAKE_SENDS"], "a") as f:
        f.write(json.dumps(Path(source).name) + "\n")
    print("md5: recording transfer boundary")
else:
    command = " ".join(sys.argv[1:])
    if command.startswith("pm path "):
        print("package:/data/app/fake/base.apk")
    elif command.startswith("cd "):
        for p in sorted(pictures.iterdir()):
            print(hashlib.md5(p.read_bytes()).hexdigest() + "  " + p.name)
    elif "content write --user 0 --uri " + uri in command:
        name = command.split(uri, 1)[1].split()[0]
        source = stage / ("cyt-screensaver-" + name)
        shutil.copyfile(source, pictures / name)
        source.unlink()
        print("__PHOTO_IMAGE_OK__")
    elif "content write --user 0 --uri content://com.gathin.photodream.files/seconds.txt" in command:
        print("__PHOTO_WRITE_OK__")
    elif "content delete --uri " + uri in command:
        target = pictures / command.split(uri, 1)[1].split()[0]
        if target.exists(): target.unlink()
    elif command.startswith("ls "):
        print(len(list(pictures.iterdir())))
    elif any(word in command for word in ("settings ", "seconds.txt", "am ", "dumpsys ")):
        pass  # Settings, screensaver and launch operations are intercepted, never executed.
    else:
        raise AssertionError("unexpected TV command: " + command)
'''


@unittest.skipUnless(os.name == "posix" and shutil.which("bash"),
                     "sync.sh is a POSIX script; it needs bash on a POSIX system")
class PhotoSyncTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        addon = self.base / "repo" / "addons" / "photo-screensaver"
        addon.mkdir(parents=True)
        self.script = addon / "sync.sh"
        shutil.copy2(ROOT / "addons" / "photo-screensaver" / "sync.sh", self.script)
        tools = self.base / "repo" / "tools"
        python = tools / "venv" / "bin" / "python"
        python.parent.mkdir(parents=True)
        python.symlink_to(sys.executable)
        for name in ("tv.py", "b64push.py"):
            (tools / name).write_text(BOUNDARY)
        self.local, self.device, self.stage = (self.base / name for name in ("local", "device", "stage"))
        for folder in (self.local, self.device, self.stage): folder.mkdir()
        self.sends = self.base / "sends.jsonl"

    def sync(self):
        self.sends.write_text("")
        env = {k: v for k, v in os.environ.items()
               if k not in ("START", "IDLE_MINUTES", "SECONDS_PER_PICTURE")}
        env.update(TV_HOST="192.0.2.1", FAKE_PICTURES=str(self.device),
                   FAKE_STAGE=str(self.stage), FAKE_SENDS=str(self.sends))
        result = subprocess.run(["bash", self.script.as_posix(), self.local.as_posix()], env=env,
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return [json.loads(line) for line in self.sends.read_text().splitlines()]

    def test_renamed_picture_is_sent_and_unchanged_picture_is_skipped(self):
        original = self.local / "trip-2023-12-02.jpg"
        data = b"\x00\xffphoto bytes"
        original.write_bytes(data)
        self.assertEqual(self.sync(), [original.name])
        self.assertEqual(self.sync(), [])

        renamed = original.with_name("trip.2023.12.02.jpg")
        original.rename(renamed)
        self.assertEqual(self.sync(), [renamed.name])
        self.assertFalse((self.device / original.name).exists())
        self.assertEqual((self.device / renamed.name).read_bytes(), data)
        self.assertEqual(self.sync(), [])
        self.assertEqual((self.device / renamed.name).read_bytes(), data)

    def test_changed_picture_is_sent_again(self):
        picture = self.local / "trip.2023.12.02.jpg"
        picture.write_bytes(b"first picture")
        self.assertEqual(self.sync(), [picture.name])
        picture.write_bytes(b"changed picture")
        self.assertEqual(self.sync(), [picture.name])
        self.assertEqual((self.device / picture.name).read_bytes(), b"changed picture")
        self.assertEqual(self.sync(), [])


if __name__ == "__main__":
    unittest.main()
