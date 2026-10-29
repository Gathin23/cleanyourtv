"""tools/theme.sh reads a Windows drive-letter path (C:/Users/... or C:\\Users\\...) as absolute.

Under Git Bash, coding agents and people often pass the theme folder as C:/Users/.../themes/aurora, which the
Windows Python opens as typed. theme.sh only took /... as absolute, so it joined C:/... onto the caller's folder
and stopped with "no config.json in C:/Users/me/cleanyourtv/C:/Users/me/themes/aurora". A drive-letter path now
goes through the same branch as /c/Users/...: cd and pwd -W resolve it, or it is kept as typed.

Linux and macOS have no drives, so a folder named Q: stands in for one. Nothing named Q: is in the caller's
folder, so cd fails there and theme.sh keeps the path as typed; tools/Q:/mytheme is where that path then
points once theme.sh has moved into tools/. Joined to the caller's folder, as before, it points nowhere.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_theme_paths as paths  # noqa: E402  (imported as a module so its tests do not run twice)


@unittest.skipIf(os.name == "nt", "Windows has no folder named Q:; the stand-in for a drive needs Linux or macOS")
class ThemeDriveLetterPathTest(unittest.TestCase):
    # The copy of theme.sh, the fake tools/venv/bin/python and the bash run that test_theme_paths.py uses.
    prepare = paths.ThemePathsTest.prepare
    run_theme = paths.ThemePathsTest.run_theme

    def test_a_drive_letter_path_is_kept_as_typed(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            tools = self.prepare(base / "repo")
            caller = base / "caller"
            caller.mkdir()
            theme = tools / "Q:" / "mytheme"
            theme.mkdir(parents=True)
            (theme / "config.json").write_text(json.dumps({"columns": 5}))
            result, capture = self.run_theme(tools, "Q:/mytheme", cwd=caller)
            self.assertEqual(result.returncode, 0, result.stderr)
            push = next(line for line in capture.read_text().splitlines() if line.startswith("tv.py --push "))
            self.assertEqual(push.split()[2], "Q:/mytheme/config.json", push)

    def test_a_missing_drive_letter_folder_names_the_path_given(self):
        for missing in ("Q:/no-such-theme", "Q:\\no-such-theme"):
            with self.subTest(path=missing), tempfile.TemporaryDirectory() as tmp:
                base = Path(tmp)
                tools = self.prepare(base / "repo")
                result, capture = self.run_theme(tools, missing, cwd=base)
                self.assertEqual(result.returncode, 1, result.stderr)
                self.assertEqual(result.stderr, f"no config.json in {missing}\n")
                self.assertFalse(capture.exists(), "a missing theme must not reach the TV transport")


if __name__ == "__main__":
    unittest.main()
