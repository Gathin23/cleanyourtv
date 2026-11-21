"""The wallpaper_moods object `tools/manage.py set` writes is one the launcher reads.

themes/README.md's Keys row for `wallpaper_moods` says the groups hold "file names from `wallpapers/`", read by
the time of day: `{"morning": [...], "evening": [...], "night": [...]}`. The launcher reads that object in three
steps, and each one drops what it does not recognise without a word:

    // Config.moods(JSONObject)
    for (String mood : WallpaperMood.ALL) {
        JSONArray names = o.optJSONArray(mood);
        if (names == null) continue;
        ...
        String name = names.optString(i, "").trim();
        if (ThemeFiles.collectionImage(name)) group.add(name);
    }

    // WallpaperMood.select()
    return group.isEmpty() ? all : group;

so a group named off the three (`dusk`, or `Morning` with a capital), a group that is not a list, an empty group,
a name `ThemeFiles.collectionImage()` refuses (a leading dot, a suffix the launcher does not read), and a name
the theme does not ship in `wallpapers/` (a picture renamed to `.jpeg` while the group still says `.jpg`) all end
as no group at that time of day: the whole collection rotates through instead. `set` printed the object as
written, and nothing on the TV said which group stopped working.

Measured on f63a1ebc with a theme folder holding `wallpapers/01.jpg`, before this refusal existed:

    manage.py set wallpaper_moods '{"noon": ["whatever.jpg"]}' --theme <folder>
    wallpaper_moods = {"noon": ["whatever.jpg"]} in <folder>/config.json

and the launcher's readers never looked at that group. The shipped themes are held to both halves already, by
tests/test_theme_wallpaper_moods.py; this file holds the folder `set` writes.

The rules are not copied in here: the group names come out of WallpaperMood.java's constants, the accepted names
out of ThemeFiles.java's Pattern and its `startsWith(".")`, and the key out of Config.java's own `optJSONObject`,
so a group the launcher adds or a suffix it stops reading fails this file instead of leaving a group quietly dead.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import re
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402

JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
MOOD_JAVA = JAVA / "WallpaperMood.java"
THEME_FILES_JAVA = JAVA / "ThemeFiles.java"
CONFIG_JAVA = JAVA / "Config.java"


def mood_names():
    """WallpaperMood.ALL, read out of the constants the launcher walks: the groups it reads, in its order."""
    text = MOOD_JAVA.read_text(encoding="utf-8")
    found = re.search(r'static final String MORNING = "([a-z]+)", EVENING = "([a-z]+)", NIGHT = "([a-z]+)"', text)
    assert found, "WallpaperMood.java no longer states MORNING, EVENING and NIGHT on one line"
    names = list(found.groups())
    assert len(names) == 3 and all(names), names
    return names


def accepted_picture_pattern():
    """ThemeFiles.IMAGE, read out of Pattern.compile(), with the dot rule beside it as its own sentence."""
    text = THEME_FILES_JAVA.read_text(encoding="utf-8")
    found = re.search(r'Pattern\.compile\("([^"]+)"', text)
    assert found, "ThemeFiles.java no longer compiles a Pattern for a collection image"
    assert '!name.startsWith(".")' in text, "ThemeFiles.collectionImage() no longer refuses a leading dot"
    # The Java literal escapes its dot for the regex engine too: "\\\\.(jpg|...)" in the source is \.(jpg|...) here.
    return found.group(1).replace("\\\\", "\\")


def moods_key():
    """The key Config.read() asks a theme's config.json for: the variable it assigns to wallpaperMoods."""
    found = re.search(r'JSONObject\s+moods\s*=\s*json\.optJSONObject\(\s*"([a-z0-9_]+)"\s*\)',
                      CONFIG_JAVA.read_text(encoding="utf-8"))
    assert found, "Config.java no longer reads wallpaper_moods with a named optJSONObject lookup"
    return found.group(1)


class SetTest(unittest.TestCase):
    """A theme folder of its own, with two pictures in wallpapers/ the groups can name."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.theme = Path(self.tmp.name) / "my-theme"
        (self.theme / "wallpapers").mkdir(parents=True)
        for name in ("01.jpg", "02.png", "03.jpeg"):
            (self.theme / "wallpapers" / name).write_bytes(b"picture")
        self.config = self.theme / "config.json"
        # cmd_set ends by pushing the theme with tools/theme.sh; the tests stop before the TV.
        patcher = mock.patch.object(manage, "run")
        self.push = patcher.start()
        self.push.return_value = 0
        self.addCleanup(patcher.stop)

    def set_key(self, value):
        """Runs set the way main() does; returns the refusal text, or None when the value was written."""
        try:
            with mock.patch("sys.stdout"):
                manage.cmd_set("wallpaper_moods", value, str(self.theme))
        except SystemExit as stop:
            return str(stop.code)
        return None

    def written(self):
        return json.loads(self.config.read_text(encoding="utf-8")) if self.config.is_file() else None

    def refused(self, value, *words):
        refusal = self.set_key(value)
        self.assertIsNotNone(refusal, f"{value!r} was written")
        for word in words:
            self.assertIn(word, refusal)
        self.assertIn("Nothing was written or pushed.", refusal)
        self.assertIsNone(self.written(), f"{value!r} was refused but the file changed")
        return refusal

    def test_takes_the_groups_the_shipped_themes_carry(self):
        value = {"morning": ["01.jpg"], "evening": ["02.png"], "night": ["03.jpeg"]}
        self.assertIsNone(self.set_key(json.dumps(value)))
        self.assertEqual(value, self.written()["wallpaper_moods"])
        self.push.assert_called_once()

    def test_refuses_a_group_the_launcher_does_not_read(self):
        # The reproduction: this shape was written on f63a1ebc and Config.moods() never looked at the group.
        self.refused('{"noon": ["01.jpg"]}', "noon", "morning, evening or night", "never looks at it")

    def test_refuses_a_group_named_in_another_case(self):
        # WallpaperMood.ALL holds lowercase names and optJSONArray() is exact, so `Morning` is a different group.
        self.refused('{"Morning": ["01.jpg"]}', "Morning", "not one the launcher reads")

    def test_refuses_a_group_that_is_not_a_list(self):
        self.refused('{"morning": "01.jpg"}', "morning", "not a list of picture names")

    def test_refuses_an_empty_group(self):
        self.refused('{"morning": []}', "morning", "empty", "whole collection")

    def test_refuses_a_name_that_is_not_a_string(self):
        for value in ('{"morning": [7]}', '{"morning": [null]}', '{"morning": [["01.jpg"]]}'):
            with self.subTest(value=value):
                self.refused(value, "not a picture name")

    def test_refuses_an_empty_name(self):
        for value in ('{"morning": [""]}', '{"morning": ["   "]}'):
            with self.subTest(value=value):
                self.refused(value, "not a picture name")

    def test_refuses_a_name_the_launcher_drops(self):
        for name in (".01.jpg", "01.gif", "01.jpg.bak2.zip", "01 01.jpg"):
            with self.subTest(name=name):
                self.refused(json.dumps({"morning": [name]}), "collectionImage() refuses")

    def test_refuses_a_picture_the_theme_does_not_ship(self):
        # The renamed-picture case: the group still names the old suffix while the folder holds the new one.
        self.refused('{"morning": ["04.jpg"]}', "does not ship", "every picture instead")

    def test_a_name_may_be_padded_and_is_kept_as_written(self):
        # moods() trims every name it reads, so the padding does not stop the picture being found.
        self.assertIsNone(self.set_key('{"morning": [" 01.jpg "]}'))
        self.assertEqual({"morning": [" 01.jpg "]}, self.written()["wallpaper_moods"])

    def test_a_theme_without_a_collection_can_name_no_picture(self):
        (self.theme / "wallpapers" / "01.jpg").unlink()
        (self.theme / "wallpapers" / "02.png").unlink()
        (self.theme / "wallpapers" / "03.jpeg").unlink()
        (self.theme / "wallpapers").rmdir()
        self.refused('{"morning": ["01.jpg"]}', "does not ship")

    def test_an_empty_object_still_clears_the_groups(self):
        # There is no unset command, and no groups is what the key being absent does: every picture rotates.
        self.assertIsNone(self.set_key("{}"))
        self.assertEqual({}, self.written()["wallpaper_moods"])


class RulesTest(unittest.TestCase):
    """The rules set applies are the launcher's, read out of its own files rather than copied."""

    def test_the_group_names_are_the_launcher_s(self):
        self.assertEqual(tuple(mood_names()), manage.WALLPAPER_MOODS)

    def test_the_picture_pattern_is_the_launcher_s(self):
        self.assertEqual(accepted_picture_pattern(), manage.MOOD_PICTURE.pattern)
        self.assertEqual(re.IGNORECASE, manage.MOOD_PICTURE.flags & re.IGNORECASE)

    def test_the_key_is_the_one_config_reads(self):
        self.assertEqual("wallpaper_moods", moods_key())
        self.assertIsNotNone(manage.find(moods_key()))

    def test_the_selection_falls_back_to_every_picture(self):
        # The sentence the refusals lean on: an empty group is the whole collection, not an empty screen.
        self.assertIn("group.isEmpty() ? all : group", MOOD_JAVA.read_text(encoding="utf-8"))


class ShippedThemesTest(unittest.TestCase):
    """The refusal does not turn away the moods this checkout already ships."""

    def test_every_shipped_theme_passes_the_rule(self):
        read = 0
        for path in sorted((ROOT / "themes").glob("*/config.json")):
            config = json.loads(path.read_text(encoding="utf-8"))
            if not isinstance(config, dict) or "wallpaper_moods" not in config:
                continue
            read += 1
            with self.subTest(theme=path.parent.name):
                self.assertIsNone(manage.moods_problem(config["wallpaper_moods"], path.parent))
        self.assertGreaterEqual(read, 5, "the sweep read the shipped themes that group their pictures")


if __name__ == "__main__":
    unittest.main()
