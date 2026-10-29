"""themes/scenes is the repo's one rotating collection, and it shipped no names for its pictures.

themes/README.md documents wallpaper_captions as reading `wallpapers/captions.txt`
(`001.jpg<TAB>Dusk over the peaks`, one per line) and WallpaperPlayer asks WallpaperCaptions for the
name of the file it is showing, so a theme with pictures and no captions.txt shows nothing at all
when the key is turned on. themes/scenes/ had 14 pictures and no captions.txt (#992, TCL 65C935,
Google TV 11).

The 14 names are not copied into this test: the pictures come from the folder the launcher
enumerates and the scenes from the generator that draws them, so a fifteenth picture - or a renamed
one - fails here until it has a name. The rules a name has to keep (TAB separator, `#` comments,
MAX_LENGTH) are read out of WallpaperCaptions.java, so a name the launcher would silently drop is
a failure here instead of a caption that never appears.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
THEMES = ROOT / "themes"
SCENES = THEMES / "scenes"
CAPTIONS_JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "WallpaperCaptions.java"
ROTATION_JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "WallpaperRotation.java"
SCENES_PY = ROOT / "tools" / "scenes_wallpapers.py"


def max_length():
    """WallpaperCaptions.MAX_LENGTH: a longer name is cut by the launcher, so the file keeps under it."""
    found = re.search(r"MAX_LENGTH\s*=\s*(\d+)", CAPTIONS_JAVA.read_text(encoding="utf-8"))
    assert found, "no MAX_LENGTH in WallpaperCaptions.java"
    return int(found.group(1))


def extensions():
    """The suffixes WallpaperRotation.images() accepts: the files the launcher puts in a rotation."""
    found = re.findall(r'name\.endsWith\("(\.[a-z]+)"\)', ROTATION_JAVA.read_text(encoding="utf-8"))
    assert found, "no endsWith(\".ext\") in WallpaperRotation.java"
    return tuple(dict.fromkeys(found))


def pictures(folder):
    """The files in `folder` the launcher would show, by name: non-empty, one of the four suffixes."""
    exts = extensions()
    if not folder.is_dir():
        return []
    return sorted(p.name for p in folder.iterdir()
                  if p.is_file() and p.stat().st_size > 0 and p.suffix.lower() in exts)


def problems(shown, text, limit=None):
    """Every way this captions.txt disagrees with the collection, one sentence each; empty when it is right.

    The parse is WallpaperCaptions.parse: a blank line or a `#` line is skipped, the first TAB splits
    file from name, both sides are trimmed, and an empty side drops the line without a word.
    """
    limit = max_length() if limit is None else limit
    found, complaints = {}, []
    for number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        tab = line.find("\t")
        if tab <= 0:
            complaints.append(f"line {number} has no tab before a name, so the launcher skips it: {line!r}")
            continue
        file, name = line[:tab].strip(), line[tab + 1:].strip()
        if not name:
            complaints.append(f"line {number} names no picture: {line!r}")
            continue
        if len(name) > limit:
            complaints.append(f"line {number} is {len(name)} characters, over the launcher's {limit}: {name!r}")
        if file in found:
            complaints.append(f"line {number} gives {file} a second name, so its first one never shows")
        found[file] = name
    for file in sorted(set(found) - set(shown)):
        complaints.append(f"{file} has a name but is not in the folder")
    for file in sorted(set(shown) - set(found)):
        complaints.append(f"{file} has no name, so no caption shows while it is up")
    return complaints


def captions(folder):
    return folder / "wallpapers" / "captions.txt"


def read(folder):
    return captions(folder).read_text(encoding="utf-8")


def generator_scenes():
    """The scene names tools/scenes_wallpapers.py draws, in its own SCENES list."""
    text = SCENES_PY.read_text(encoding="utf-8")
    body = text.split("SCENES = [", 1)
    assert len(body) == 2, "no SCENES list in tools/scenes_wallpapers.py"
    return re.findall(r'\("([0-9][^"]*)"', body[1].split("]", 1)[0])


class ScenesCaptionsTest(unittest.TestCase):
    def test_every_picture_has_a_name(self):
        shown = pictures(SCENES / "wallpapers")
        self.assertTrue(shown, "the collection has no pictures to name")
        self.assertEqual([], problems(shown, read(SCENES)))

    def test_every_scene_the_generator_draws_is_named(self):
        scenes = generator_scenes()
        self.assertGreaterEqual(len(scenes), 10, "read no scene names out of tools/scenes_wallpapers.py")
        shown = pictures(SCENES / "wallpapers")
        complaints = problems(shown, read(SCENES))
        for name in scenes:
            with self.subTest(scene=name):
                self.assertNotIn(f"{name}.jpg has no name", complaints)
        # The generator writes SVGs and the folder holds JPEGs; a scene with no picture is a gap too.
        self.assertEqual([], [f"{name}.jpg" for name in scenes if f"{name}.jpg" not in shown])

    def test_the_file_stays_inside_the_launchers_rules(self):
        limit = max_length()
        for number, line in enumerate(read(SCENES).splitlines(), 1):
            if not line.strip() or line.strip().startswith("#"):
                continue
            with self.subTest(line=number):
                self.assertIn("\t", line, "a line without a tab is skipped by the launcher")
                raw = line.split("\t", 1)[1]
                self.assertTrue(raw.strip(), "an empty name drops the line without a word")
                self.assertEqual(raw, raw.strip(), "the launcher trims the name, so the file should not need it")
                self.assertNotIn("\t", raw, "a second tab stays inside the name")
                self.assertLessEqual(len(raw), limit)

    def test_no_theme_keeps_a_stale_captions_file(self):
        checked = 0
        for folder in sorted(p for p in THEMES.iterdir() if p.is_dir()):
            if not captions(folder).is_file():
                continue
            checked += 1
            with self.subTest(theme=folder.name):
                self.assertEqual([], problems(pictures(folder / "wallpapers"), read(folder)))
        self.assertGreaterEqual(checked, 1, "found no captions.txt at all")

    def test_a_theme_that_shows_captions_ships_them(self):
        checked = 0
        for folder in sorted(p for p in THEMES.iterdir() if p.is_dir()):
            config = folder / "config.json"
            if not config.is_file():
                continue
            checked += 1
            wanted = json.loads(config.read_text(encoding="utf-8"))
            if wanted.get("wallpaper_captions") is not True:
                continue
            with self.subTest(theme=folder.name):
                self.assertTrue(captions(folder).is_file(),
                                "wallpaper_captions is on but there is no wallpapers/captions.txt")
        self.assertGreaterEqual(checked, 5, "read no theme configs")


class CheckerTest(unittest.TestCase):
    """The checks above are only worth something if they can fail; these are the documents that must."""

    SHOWN = ["01-a.jpg", "02-b.jpg"]
    GOOD = "# a comment\n01-a.jpg\tDusk peaks\n\n02-b.jpg\tGolden coast\n"

    def test_a_right_file_reports_nothing(self):
        self.assertEqual([], problems(self.SHOWN, self.GOOD))

    def test_a_picture_without_a_line_is_reported(self):
        found = problems(self.SHOWN, "01-a.jpg\tDusk peaks\n")
        self.assertEqual(1, len(found), found)
        self.assertIn("02-b.jpg has no name", found[0])

    def test_a_name_for_a_picture_that_is_gone_is_reported(self):
        found = problems(self.SHOWN, self.GOOD + "03-c.jpg\tOld name\n")
        self.assertEqual([], [p for p in found if "01-a" in p or "02-b" in p])
        self.assertEqual(1, len(found), found)
        self.assertIn("03-c.jpg has a name but is not in the folder", found[0])

    def test_a_line_without_a_tab_is_reported(self):
        found = problems(self.SHOWN, "01-a.jpg Dusk peaks\n02-b.jpg\tGolden coast\n")
        self.assertEqual(2, len(found), found)   # the skipped line, and the picture left without a name
        self.assertIn("has no tab", found[0])

    def test_a_name_over_the_launchers_limit_is_reported(self):
        found = problems(self.SHOWN, "01-a.jpg\t" + "x" * 81 + "\n02-b.jpg\tGolden coast\n")
        self.assertEqual(1, len(found), found)
        self.assertIn("over the launcher's 80", found[0])

    def test_a_second_name_for_one_picture_is_reported(self):
        found = problems(self.SHOWN, self.GOOD + "01-a.jpg\tAnother name\n")
        self.assertEqual(1, len(found), found)
        self.assertIn("second name", found[0])


if __name__ == "__main__":
    unittest.main()
