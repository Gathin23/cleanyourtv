"""tools/b64push.py refuses a remote path that is a folder on the TV.

Before this, `b64push.py app.apk /data/local/tmp/apps` with apps a folder sent every chunk, checked
the md5, and then `mv -f` moved the copy into the folder as apps.part: the run printed MATCH and
exited 0, and the path the reader asked for was still the folder. Now the first command asks whether
the target is a folder and the run stops with 2 before a chunk is sent.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import shutil
import unittest

from test_b64push_atomic import FAKE_SHELL_TV, ROOT, B64PushTest as _Atomic, TempDirTest


@unittest.skipUnless(os.name == "posix" and shutil.which("md5sum") and shutil.which("base64"),
                     "the fake TV's shell is /bin/sh, and md5sum and base64 are needed")
class B64PushRemoteFolderTest(TempDirTest):
    """The stand-in TV from test_b64push_atomic.py: /bin/sh on a folder under the temp dir."""

    def setUp(self):
        super().setUp()
        self.tools = self.base / "tools"
        self.tools.mkdir()
        shutil.copy2(ROOT / "tools" / "b64push.py", self.tools / "b64push.py")
        (self.tools / "tv.py").write_text(FAKE_SHELL_TV)
        self.remote_dir = self.base / "remote"
        self.remote_dir.mkdir()
        self.log = self.base / "log.jsonl"

    push = _Atomic.push
    commands = _Atomic.commands
    local = _Atomic.local
    on_the_tv = _Atomic.on_the_tv

    def test_a_folder_at_the_target_is_refused_before_any_chunk(self):
        (self.remote_dir / "apps").mkdir()
        result = self.push(str(self.local(b"payload" * 100)), self.on_the_tv("apps"))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("is a folder on the TV", result.stderr)
        self.assertEqual(os.listdir(self.remote_dir / "apps"), [], "nothing was moved into the folder")
        self.assertFalse([c for c in self.commands() if c.startswith("echo '")], "no chunk was sent")
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["apps"])

    def test_a_file_at_the_target_is_still_replaced(self):
        (self.remote_dir / "app.apk").write_bytes(b"old")
        result = self.push(str(self.local(b"new" * 50)), self.on_the_tv("app.apk"))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.remote_dir / "app.apk").read_bytes(), b"new" * 50)


if __name__ == "__main__":
    unittest.main()
