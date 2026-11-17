"""Every picture a theme's wallpaper_moods names is one that theme ships, under a mood the launcher reads.

themes/README.md's Keys row for `wallpaper_moods` says the groups hold "file names from `wallpapers/`", and both
halves of that are read by the launcher in a way that drops what it does not recognise without a word:

- `Config.moods(JSONObject)` walks `WallpaperMood.ALL` and nothing else, so a group named `dusk` (or `Morning`)
  is not read at all;
- each name is kept only when `ThemeFiles.collectionImage()` accepts it, which is a name without a leading dot
  matching `[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)` - `9pm_6am.gif` is dropped here;
- `WallpaperMood.select()` then matches the kept names against the collection's pictures, and a group with none
  of them is answered with the whole collection instead (`return group.isEmpty() ? all : group`).

So a theme whose night group was renamed with the picture (a phone photo saved as `.jpeg`, a picture swapped for a
`.png`) pushes, prints "applied", and then shows a daytime picture at night - or every picture, in the collection's
own order - with nothing on the TV or in the suite saying which of the three groups stopped working.

Reproduced on 718f98c9 in a worktree with the edit a theme PR would carry: themes/retro/config.json's night group
renamed to `9pm_6am.jpeg` (the picture it names is still `9pm_6am.jpg`), and a `"dusk"` group added beside it. Main's
own suite there is `Ran 2251 tests ... OK (skipped=16)` under `tools/venv/bin/python` (`Ran 2235 ... OK (skipped=18)`
under the system `python3`, which skips two more); with only this file added to that same tree it fails, naming the
`9pm_6am.jpeg` group and the `dusk` one.

The rules are not copied in here: the mood names come out of WallpaperMood.java's constants, the accepted suffixes
out of ThemeFiles.java's Pattern, and the key out of Config.java's own `optJSONObject`, so a mood the launcher adds
or a suffix it stops reading fails this file instead of leaving a theme's group quietly dead.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import re
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
MOOD_JAVA = JAVA / "WallpaperMood.java"
THEME_FILES_JAVA = JAVA / "ThemeFiles.java"
CONFIG_JAVA = JAVA / "Config.java"
WALLPAPERS = "wallpapers"


def mood_names():
    """WallpaperMood.ALL, read out of the constants the launcher walks: the groups it reads, in its order."""
    text = MOOD_JAVA.read_text(encoding="utf-8")
    found = re.search(r'static final String MORNING = "([a-z]+)", EVENING = "([a-z]+)", NIGHT = "([a-z]+)"', text)
    assert found, "WallpaperMood.java no longer states MORNING, EVENING and NIGHT on one line"
    names = list(found.groups())
    assert len(names) == 3 and all(names), names
    return names


def collection_image():
    """ThemeFiles.collectionImage(): the launcher's own test for a name a mood may hold.

    The predicate has two halves on one line - a name that does not start with a dot, matched whole by the IMAGE
    Pattern - so both are read here: the Pattern's text out of Pattern.compile(), and the dot rule out of the
    startsWith() beside it. A name this refuses is one a theme must not write, because the launcher drops it.
    """
    text = THEME_FILES_JAVA.read_text(encoding="utf-8")
    found = re.search(r'Pattern\.compile\("([^"]+)"', text)
    assert found, "ThemeFiles.java no longer compiles a Pattern for a collection image"
    # The Java literal escapes its dot for the regex engine too: "\\.(jpg|...)" in the source is \.(jpg|...) here.
    pattern = re.compile(found.group(1).replace("\\\\", "\\"), re.IGNORECASE)
    assert '!name.startsWith(".")' in text, "ThemeFiles.collectionImage() no longer refuses a leading dot"
    return lambda name: bool(name) and not name.startswith(".") and pattern.fullmatch(name) is not None


def moods_key():
    """The key Config.read() asks the theme's config.json for: the variable it assigns to wallpaperMoods."""
    found = re.search(r'JSONObject\s+moods\s*=\s*json\.optJSONObject\(\s*"([a-z0-9_]+)"\s*\)',
                      CONFIG_JAVA.read_text(encoding="utf-8"))
    assert found, "Config.java no longer reads wallpaper_moods with a named optJSONObject lookup"
    return found.group(1)


def shipped_pictures(folder):
    """The pictures a theme's wallpapers/ folder holds, by name; empty when the folder is not there."""
    pictures = folder / WALLPAPERS
    if not pictures.is_dir():
        return set()
    return {p.name for p in pictures.iterdir() if p.is_file() and p.name != "captions.txt"}


def problems(moods, pictures, allowed, accepted, where):
    """Why a theme's wallpaper_moods would leave a group dead on the TV, in the words of the failure."""
    found = []
    for mood, names in moods.items():
        if mood not in allowed:
            found.append(f"{where}: wallpaper_moods group {mood!r} is not one the launcher reads "
                         f"({'/'.join(allowed)}), so it is dropped and its pictures never show")
            continue
        if not isinstance(names, list):
            found.append(f"{where}: wallpaper_moods group {mood!r} is not a list of file names, so the launcher "
                         f"drops the group")
            continue
        if not names:
            found.append(f"{where}: wallpaper_moods group {mood!r} is empty, so the launcher drops the group")
            continue
        for name in names:
            if not isinstance(name, str):
                found.append(f"{where}: wallpaper_moods group {mood!r} holds a value that is not a file name, so "
                             f"the launcher drops it")
                continue
            if not accepted(name.strip()):
                found.append(f"{where}: wallpaper_moods group {mood!r} names {name!r}, which ThemeFiles."
                             f"collectionImage() refuses, so the launcher drops it")
            elif name.strip() not in pictures:
                found.append(f"{where}: wallpaper_moods group {mood!r} names {name!r}, which this theme does not "
                             f"ship in {WALLPAPERS}/, so the group holds none of the collection's pictures and the "
                             f"launcher falls back to every picture")
    return found


def theme_moods(folder, key):
    """(moods, problem): the theme's config.json as an object and its wallpaper_moods, or why it has none to read."""
    path = folder / "config.json"
    if not path.is_file():
        return None, None
    try:
        config = json.loads(path.read_text(encoding="utf-8"))
    except ValueError as error:
        return None, f"{path} is not valid JSON ({error})"
    if not isinstance(config, dict):
        return None, f"{path} holds {type(config).__name__}, not a JSON object"
    moods = config.get(key)
    if moods is None:
        return None, None
    if not isinstance(moods, dict):
        return {}, f"{path}: {key} is {type(moods).__name__}, not an object of groups"
    return moods, None


def shipped_themes(root):
    """(folder, moods) for every theme folder that sets wallpaper_moods, in path order."""
    out = []
    for folder in sorted((root / "themes").iterdir()):
        if not folder.is_dir():
            continue
        moods, _problem = theme_moods(folder, moods_key())
        if moods:
            out.append((folder, moods))
    return out


class ShippedThemesTest(unittest.TestCase):
    """The themes in this checkout, held to the groups and names the launcher actually reads."""

    def test_the_rules_are_read_out_of_the_launcher(self):
        with self.subTest(part="mood names"):
            self.assertEqual(["morning", "evening", "night"], mood_names())
        with self.subTest(part="the key"):
            self.assertEqual("wallpaper_moods", moods_key())
        accepted = collection_image()
        with self.subTest(part="names the launcher keeps"):
            for name in ("001.jpg", "01-night.jpeg", "dusk.png", "wall.webp", "A_b-1.JPG"):
                self.assertTrue(accepted(name), name)
        with self.subTest(part="names the launcher drops"):
            for name in ("dusk.gif", ".hidden.jpg", "two words.jpg", "dusk", "sub/dusk.jpg", ""):
                self.assertFalse(accepted(name), name)

    def test_a_theme_ships_at_least_one_group(self):
        """A read guard: the walk below has something to read, so it cannot pass by finding no theme at all."""
        themes = shipped_themes(ROOT)
        self.assertTrue(themes, "no theme under themes/ sets wallpaper_moods any more, so this walk reads nothing")
        self.assertIn("retro", [folder.name for folder, _moods in themes],
                      "themes/retro's time-of-day groups are the ones this file was written against")

    def test_every_shipped_config_reads_as_groups(self):
        """A shipped config.json that will not parse, or whose groups are not an object, is named here."""
        key = moods_key()
        for folder in sorted((ROOT / "themes").iterdir()):
            if not folder.is_dir():
                continue
            with self.subTest(theme=folder.name):
                self.assertIsNone(theme_moods(folder, key)[1])

    def test_every_shipped_group_is_one_the_launcher_reads(self):
        allowed = mood_names()
        accepted = collection_image()
        for folder, moods in shipped_themes(ROOT):
            with self.subTest(theme=folder.name):
                self.assertEqual([], problems(moods, shipped_pictures(folder), allowed, accepted,
                                              f"themes/{folder.name}"))

    def test_every_shipped_group_names_only_pictures_that_are_there(self):
        """The names, one by one, so a renamed picture is named instead of reading as a group that still works."""
        accepted = collection_image()
        for folder, moods in shipped_themes(ROOT):
            pictures = shipped_pictures(folder)
            for mood, names in moods.items():
                if not isinstance(names, list):
                    continue
                for name in names:
                    if not isinstance(name, str):
                        continue
                    with self.subTest(theme=folder.name, mood=mood, name=name):
                        self.assertTrue(accepted(name.strip()), f"themes/{folder.name}: {name!r} is a name "
                                                                f"ThemeFiles.collectionImage() refuses")
                        self.assertIn(name.strip(), pictures, f"themes/{folder.name}: wallpaper_moods names "
                                                              f"{name!r}, which {WALLPAPERS}/ does not hold")


class TheReaderTest(unittest.TestCase):
    """The same readers over themes written in the test, one rule per case."""

    def theme(self, root, name, config, pictures=()):
        folder = Path(root) / "themes" / name
        folder.mkdir(parents=True)
        (folder / "config.json").write_text(json.dumps(config), encoding="utf-8")
        if pictures:
            (folder / WALLPAPERS).mkdir()
            for picture in pictures:
                (folder / WALLPAPERS / picture).write_bytes(b"\xff\xd8\xff")
        return folder

    def test_a_theme_whose_groups_name_pictures_it_ships_is_quiet(self):
        with tempfile.TemporaryDirectory() as root:
            folder = self.theme(root, "quiet", {"wallpaper_moods": {"night": ["03.jpg"], "morning": ["01.png"]}},
                                ["01.png", "02.jpg", "03.jpg"])
            self.assertEqual([], problems(theme_moods(folder, moods_key())[0], shipped_pictures(folder),
                                          mood_names(), collection_image(), "themes/quiet"))

    def test_a_group_the_launcher_does_not_read_is_named(self):
        with tempfile.TemporaryDirectory() as root:
            folder = self.theme(root, "dusk", {"wallpaper_moods": {"dusk": ["01.jpg"]}}, ["01.jpg"])
            found = problems(theme_moods(folder, moods_key())[0], shipped_pictures(folder), mood_names(),
                             collection_image(), "themes/dusk")
            self.assertEqual(1, len(found), found)
            self.assertIn("'dusk'", found[0])
            self.assertIn("morning/evening/night", found[0])

    def test_a_group_named_in_another_case_is_named(self):
        """`Morning` is not `morning`: Config walks the constants, so the capital is a different group."""
        with tempfile.TemporaryDirectory() as root:
            folder = self.theme(root, "caps", {"wallpaper_moods": {"Morning": ["01.jpg"]}}, ["01.jpg"])
            found = problems(theme_moods(folder, moods_key())[0], shipped_pictures(folder), mood_names(),
                             collection_image(), "themes/caps")
            self.assertEqual(1, len(found), found)
            self.assertIn("'Morning'", found[0])

    def test_a_renamed_picture_is_named(self):
        """The picture is there as 9pm_6am.jpg and the group says 9pm_6am.jpeg: the launcher falls back."""
        with tempfile.TemporaryDirectory() as root:
            folder = self.theme(root, "night", {"wallpaper_moods": {"night": ["9pm_6am.jpeg"]}}, ["9pm_6am.jpg"])
            found = problems(theme_moods(folder, moods_key())[0], shipped_pictures(folder), mood_names(),
                             collection_image(), "themes/night")
            self.assertEqual(1, len(found), found)
            self.assertIn("9pm_6am.jpeg", found[0])
            self.assertIn("falls back to every picture", found[0])

    def test_a_suffix_the_launcher_refuses_is_named(self):
        with tempfile.TemporaryDirectory() as root:
            folder = self.theme(root, "gif", {"wallpaper_moods": {"night": ["03.gif"]}}, ["03.gif"])
            found = problems(theme_moods(folder, moods_key())[0], shipped_pictures(folder), mood_names(),
                             collection_image(), "themes/gif")
            self.assertEqual(1, len(found), found)
            self.assertIn("collectionImage() refuses", found[0])

    def test_a_group_that_is_not_a_list_is_named(self):
        with tempfile.TemporaryDirectory() as root:
            folder = self.theme(root, "one", {"wallpaper_moods": {"night": "03.jpg"}}, ["03.jpg"])
            found = problems(theme_moods(folder, moods_key())[0], shipped_pictures(folder), mood_names(),
                             collection_image(), "themes/one")
            self.assertEqual(1, len(found), found)
            self.assertIn("not a list of file names", found[0])

    def test_an_empty_group_is_named(self):
        with tempfile.TemporaryDirectory() as root:
            folder = self.theme(root, "empty", {"wallpaper_moods": {"night": []}}, ["03.jpg"])
            found = problems(theme_moods(folder, moods_key())[0], shipped_pictures(folder), mood_names(),
                             collection_image(), "themes/empty")
            self.assertEqual(1, len(found), found)
            self.assertIn("is empty", found[0])

    def test_a_name_that_is_not_text_is_named(self):
        with tempfile.TemporaryDirectory() as root:
            folder = self.theme(root, "number", {"wallpaper_moods": {"night": [3]}}, ["03.jpg"])
            found = problems(theme_moods(folder, moods_key())[0], shipped_pictures(folder), mood_names(),
                             collection_image(), "themes/number")
            self.assertEqual(1, len(found), found)
            self.assertIn("not a file name", found[0])

    def test_a_theme_with_no_wallpapers_folder_cannot_ship_a_name(self):
        with tempfile.TemporaryDirectory() as root:
            folder = self.theme(root, "bare", {"wallpaper_moods": {"night": ["03.jpg"]}})
            found = problems(theme_moods(folder, moods_key())[0], shipped_pictures(folder), mood_names(),
                             collection_image(), "themes/bare")
            self.assertEqual(1, len(found), found)
            self.assertIn("does not ship in wallpapers/", found[0])

    def test_a_theme_without_groups_is_not_read(self):
        with tempfile.TemporaryDirectory() as root:
            folder = self.theme(root, "plain", {"wallpaper": True}, ["01.jpg"])
            moods, problem = theme_moods(folder, moods_key())
            self.assertIsNone(moods)
            self.assertIsNone(problem)
            self.assertEqual([], shipped_themes(Path(root)))

    def test_a_moods_value_that_is_not_an_object_is_named(self):
        with tempfile.TemporaryDirectory() as root:
            folder = self.theme(root, "list", {"wallpaper_moods": ["night"]}, ["01.jpg"])
            moods, problem = theme_moods(folder, moods_key())
            self.assertEqual({}, moods)
            self.assertIn("not an object of groups", problem)

    def test_a_config_that_is_not_json_is_named(self):
        with tempfile.TemporaryDirectory() as root:
            folder = Path(root) / "themes" / "broken"
            folder.mkdir(parents=True)
            (folder / "config.json").write_text("{\"wallpaper_moods\": ", encoding="utf-8")
            moods, problem = theme_moods(folder, moods_key())
            self.assertIsNone(moods)
            self.assertIn("is not valid JSON", problem)

    def test_a_folder_without_a_config_is_not_read(self):
        with tempfile.TemporaryDirectory() as root:
            folder = Path(root) / "themes" / "empty"
            folder.mkdir(parents=True)
            self.assertEqual((None, None), theme_moods(folder, moods_key()))

    def test_the_walk_reads_every_theme_folder_that_sets_groups(self):
        with tempfile.TemporaryDirectory() as root:
            self.theme(root, "a", {"wallpaper_moods": {"night": ["01.jpg"]}}, ["01.jpg"])
            self.theme(root, "b", {"wallpaper_moods": {"morning": ["02.jpg"]}}, ["02.jpg"])
            self.theme(root, "c", {"wallpaper": True}, ["03.jpg"])
            self.assertEqual(["a", "b"], [folder.name for folder, _moods in shipped_themes(Path(root))])


if __name__ == "__main__":
    unittest.main()
