"""Every wallpaper is credited in its theme's CREDITS.md, and credited to the picture that is there.

`docs/DOS-AND-DONTS.md` states the rule for a theme's pictures ("Use your own pictures, or ones with a
stated free licence (CC0, CC BY), credited in `CREDITS.md`", [#1114](https://github.com/Gathin23/cleanyourtv/pull/1114),
[#1260](https://github.com/Gathin23/cleanyourtv/issues/1260)), `themes/CREATE_A_THEME.md` step 2 sends an
author to `themes/sweden/CREDITS.md` for the format, and five of the nine theme rows in
`themes/README.md` end by pointing a reader at one. Nothing read the six files: `tests/test_theme_wallpapers.py`
judges every picture's shape, weight and suffix, `tests/test_scenes_captions.py` every name under the
buttons, and the file that says where a picture came from and under which licence was read by no test at
all. So a theme PR could add a ninth photo to a collection and leave `CREDITS.md` at eight - a picture
that is redistributed with no credit, which is the one condition CC BY and CC BY-SA grant - or rename
`004.jpg` and leave a line crediting a file that is no longer in the tree, and the suite stayed green.
The two files are hand-kept per theme, and they can drift apart: `wallpapers/captions.txt` writes the
photographer and licence the TV shows under the buttons, `CREDITS.md` the licence the repo redistributes
under, so a picture swapped between two names shows one in the room and states the other in the file.

What is judged, for every theme that carries a CREDITS.md:

- every picture the launcher draws from that folder is named in the file, so no picture ships uncredited;
- every picture name the file states is a file in that folder, so a rename cannot leave a stale credit;
- a picture's own credit line states the same licence as the line `captions.txt` gives it, where both
  state one (read through one spelling, so `CC0 1.0` and `CC0` are the same licence and `CC BY-SA 3.0`
  and `CC BY-SA 4.0` are not);
- the file states a licence at all: a list of photographers with no licence is not the credit the page
  asks for.

Not judged: a theme with pictures and no CREDITS.md. Six are that way today - `aurora/`, `dussehra/` and
`glass/` ship one wallpaper drawn for the theme, and `blocky/`, `scenes/` and `ruska/` draw every picture
from code in this tree (`tools/blocky_wallpapers.py`, `tools/scenes_wallpapers.py`,
`themes/ruska/make_wallpaper.py`) - so asking for the file of all of them would be a new rule rather than
the one the page states. What is judged is a file that exists and then goes out of step with the folder
beside it, and the walk is over every theme folder, so a seventh file is read without this page changing.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
THEMES = ROOT / "themes"
ROTATION_JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "WallpaperRotation.java"

# A picture's file name as the six credits files write it. A title inside a Wikimedia URL is a page on
# someone else's site, not a file here, so a name is only read from a bullet or from backticks.
PICTURE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]*\.(?:jpe?g|png|webp)", re.IGNORECASE)
BULLET = re.compile(r"^[-*]\s+`?(" + PICTURE.pattern + r")`?\s*:", re.IGNORECASE)
BACKTICKED = re.compile(r"`([^`]+)`")
LICENCE = re.compile(r"CC0(?:\s*1\.0)?|CC[\s_-]*BY(?:[\s_-]*SA)?[\s_-]+\d\.\d|public domain", re.IGNORECASE)


def shown_suffixes():
    """The suffixes WallpaperRotation.images() accepts: the pictures the launcher would draw."""
    found = re.findall(r'name\.endsWith\("(\.[a-z]+)"\)', ROTATION_JAVA.read_text(encoding="utf-8"))
    assert found, 'no endsWith(".ext") in WallpaperRotation.java'
    return tuple(dict.fromkeys(found))


def themes():
    """Every theme folder, by path, in a stable order."""
    if not THEMES.is_dir():
        return []
    return sorted(p for p in THEMES.iterdir() if p.is_dir())


def pictures(folder):
    """Every picture the launcher draws from this theme, by path.

    The two places `tests/test_theme_wallpapers.py` walks: the one picture at the theme's root
    (`wallpaper.jpg`, the name `Config.WALLPAPER_NAME` opens) and every picture in `wallpapers/`.
    `screenshot.jpg` is Home's own capture of the theme, not a wallpaper, and is not credited here.
    """
    suffixes = shown_suffixes()
    out = [p for p in sorted(folder.glob("wallpaper.*")) if p.is_file() and p.suffix.lower() in suffixes]
    collection = folder / "wallpapers"
    if collection.is_dir():
        out.extend(p for p in sorted(collection.iterdir()) if p.is_file() and p.suffix.lower() in suffixes)
    return out


def captions(folder):
    """{picture name: line} from the theme's wallpapers/captions.txt, as WallpaperCaptions.parse reads it."""
    path = folder / "wallpapers" / "captions.txt"
    if not path.is_file():
        return {}
    out = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        tab = line.find("\t")
        if tab <= 0:
            continue
        name = line[:tab].strip()
        if name not in out:
            out[name] = line[tab + 1:].strip()
    return out


def licence(text):
    """The one licence this text states, in one written form, or None.

    The six files and the caption lines spell the same four licences differently ("CC0 1.0" and "CC0",
    "CC BY-SA 4.0" and "CC BY SA 4.0"), so they are compared through this: a version is kept, because
    CC BY-SA 3.0 and CC BY-SA 4.0 are different licences, and ShareAlike is kept apart from plain BY.
    """
    found = LICENCE.search(text)
    if not found:
        return None
    said = re.sub(r"[\s_-]+", " ", found.group(0).strip().lower())
    if said.startswith("cc0"):
        return "CC0"
    if said.startswith("public domain"):
        return "public domain"
    words = said.split()
    version = words[-1]
    return f"CC BY-SA {version}" if "sa" in words else f"CC BY {version}"


def named(text):
    """The picture names a credits file states, in file order, without repeats.

    Two shapes the six files use: a bullet that opens with the name (`- 002.jpg: "Jack-O-Lantern..." by
    ...`) and a name in backticks (`- `wallpaper.jpg`: ...`). A name in a sentence is not read here - it
    is checked against the folder by the forward rule instead - so `themes/retro/CREDITS.md`, which
    credits its three pictures in prose, is reported by neither.
    """
    out = []
    for line in text.splitlines():
        stripped = line.strip()
        bullet = BULLET.match(stripped)
        if bullet:
            out.append(bullet.group(1))
        for found in BACKTICKED.findall(stripped):
            if "/" not in found and PICTURE.fullmatch(found):
                out.append(found)
    return list(dict.fromkeys(out))


def credit_line(text, name):
    """The first line of the credits file that names this picture, or None when it names it nowhere."""
    for line in text.splitlines():
        if name in line:
            return line
    return None


def problems(folder):
    """Every way this theme's CREDITS.md and the pictures beside it disagree, one sentence each."""
    credits = folder / "CREDITS.md"
    if not credits.is_file():
        return []
    text = credits.read_text(encoding="utf-8")
    out = []
    for path in pictures(folder):
        if path.name not in text:
            out.append(f"themes/{folder.name}/{path.name} is shipped and CREDITS.md does not name it")
    for name in named(text):
        if not any((where / name).is_file() for where in (folder, folder / "wallpapers")):
            out.append(f"themes/{folder.name}/CREDITS.md credits {name}, and there is no such picture")
    if licence(text) is None:
        out.append(f"themes/{folder.name}/CREDITS.md states no licence "
                   f"(CC0, CC BY, CC BY-SA or public domain)")
    for name, caption in captions(folder).items():
        on_the_tv, credited = licence(caption), licence(credit_line(text, name) or "")
        if on_the_tv and credited and on_the_tv != credited:
            out.append(f"themes/{folder.name}/wallpapers/captions.txt gives {name} {on_the_tv} and "
                       f"CREDITS.md gives it {credited}")
    return out


class ThemeCreditsTest(unittest.TestCase):
    def test_every_picture_a_theme_ships_is_credited(self):
        found = []
        for folder in themes():
            found.extend(problems(folder))
        self.assertEqual([], found, "\n" + "\n".join(found))

    def test_the_walk_reads_the_files_and_the_pictures_the_tree_carries(self):
        # A walk that stops reading finds no problems either, so what it walked is checked here.
        credited = [f for f in themes() if (f / "CREDITS.md").is_file()]
        self.assertGreaterEqual(len(credited), 6, "fewer than six themes carry a CREDITS.md")
        counted = sum(len(pictures(f)) for f in credited)
        self.assertGreaterEqual(counted, 31, f"the walk read {counted} pictures, not the 31 credited today")
        compared = 0
        for folder in credited:
            text = (folder / "CREDITS.md").read_text(encoding="utf-8")
            for name, caption in captions(folder).items():
                if licence(caption) and licence(credit_line(text, name) or ""):
                    compared += 1
        self.assertGreaterEqual(compared, 20,
                                f"only {compared} pictures had their caption's licence and the credit's compared")
        for name in ("halloween", "nature-calm", "storm", "sweden", "retro", "cinematic"):
            with self.subTest(theme=name):
                self.assertTrue((THEMES / name / "CREDITS.md").is_file(),
                                f"themes/{name}/CREDITS.md is gone: the walk reads nothing")

    def test_a_theme_with_pictures_and_no_credits_file_is_left_alone(self):
        # Documented in this file's docstring: the code-drawn themes are not a CREDITS.md gap.
        for name in ("scenes", "blocky", "ruska"):
            with self.subTest(theme=name):
                self.assertEqual([], problems(THEMES / name))


class TheLicenceSpelling(unittest.TestCase):
    """licence(): one written form for the four licences the pages allow, read out of a sentence."""

    def test_cc0_is_one_licence_however_it_is_written(self):
        self.assertEqual("CC0", licence("released under CC0 1.0 Universal (Public Domain Dedication)"))
        self.assertEqual("CC0", licence("Alabama Extension, CC0"))
        self.assertEqual("CC0", licence("released CC0"))

    def test_a_version_is_kept(self):
        self.assertEqual("CC BY-SA 3.0", licence("Mathias Krumbholz, CC BY-SA 3.0"))
        self.assertEqual("CC BY-SA 4.0", licence("CC BY-SA 4.0"))
        self.assertNotEqual(licence("CC BY-SA 3.0"), licence("CC BY-SA 4.0"))

    def test_share_alike_is_kept_apart_from_plain_by(self):
        self.assertEqual("CC BY 4.0", licence("Rollingskyphoto, CC BY 4.0"))
        self.assertNotEqual(licence("CC BY 4.0"), licence("CC BY-SA 4.0"))

    def test_a_hyphen_and_a_space_mean_the_same_licence(self):
        self.assertEqual(licence("CC BY-SA 4.0"), licence("CC BY SA 4.0"))

    def test_public_domain_is_its_own_answer(self):
        self.assertEqual("public domain", licence("the USDA Forest Service, public domain (a work of the "
                                                  "US federal government)"))

    def test_a_sentence_that_states_no_licence_has_none(self):
        self.assertIsNone(licence("created by the theme author, all rights reserved"))
        self.assertIsNone(licence(""))


class TheNamesRead(unittest.TestCase):
    """named(): the two shapes a credits file uses, and the names that are not files here."""

    def test_a_bullet_that_opens_with_a_name_is_read(self):
        text = '- 002.jpg: "Jack-O-Lantern at night.jpg" by Alabama Extension, CC0 1.0\n'
        self.assertEqual(["002.jpg"], named(text))

    def test_a_backticked_name_is_read(self):
        self.assertEqual(["wallpaper.jpg"], named("- `wallpaper.jpg`: a dark gradient, CC0\n"))

    def test_a_title_in_a_wikimedia_url_is_not_a_file_here(self):
        text = "  https://commons.wikimedia.org/wiki/File:Jack-O-Lantern_at_night.jpg\n"
        self.assertEqual([], named(text))

    def test_a_quoted_title_is_not_a_file_here(self):
        text = '- 003.jpg: "Lit by the Moon SQF A1 -1619-Edit (52321356482).jpg" by the USDA Forest Service\n'
        self.assertEqual(["003.jpg"], named(text))

    def test_a_backticked_path_is_not_a_name_in_this_folder(self):
        self.assertEqual([], named("see `themes/dussehra/screenshot.jpg` for the tiles\n"))

    def test_a_name_stated_twice_is_read_once(self):
        text = "- `001.jpg`: CC0\n- 001.jpg: CC0\n"
        self.assertEqual(["001.jpg"], named(text))

    def test_a_prose_credit_is_read_as_no_name(self):
        text = "All three pictures (6am_4pm.jpg, 4pm_9pm.jpg, 9pm_6am.jpg) were created by the author\n"
        self.assertEqual([], named(text))


class TheProblemsFound(unittest.TestCase):
    """problems(): the four rules, over a theme written here, one shape per test."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.folder = self.root / "themes" / "one"
        (self.folder / "wallpapers").mkdir(parents=True)

    def picture(self, name):
        path = self.folder / "wallpapers" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"\xff\xd8")
        return path

    def credits(self, text):
        (self.folder / "CREDITS.md").write_text(text, encoding="utf-8")

    def caption(self, text):
        (self.folder / "wallpapers" / "captions.txt").write_text(text, encoding="utf-8")

    def test_a_credited_collection_is_quiet(self):
        self.picture("001.jpg")
        self.picture("002.jpg")
        self.caption("001.jpg\tDusk · Someone, CC BY 4.0\n002.jpg\tDawn · Another, CC0\n")
        self.credits('- 001.jpg: "Dusk" by Someone, CC BY 4.0\n  https://example.invalid/Dusk.jpg\n'
                     '- 002.jpg: "Dawn" by Another, CC0 1.0\n')
        self.assertEqual([], problems(self.folder))

    def test_a_picture_the_file_does_not_name_is_reported(self):
        self.picture("001.jpg")
        self.picture("002.jpg")
        self.credits('- 001.jpg: "Dusk" by Someone, CC BY 4.0\n')
        self.assertEqual(["themes/one/002.jpg is shipped and CREDITS.md does not name it"], problems(self.folder))

    def test_a_credit_for_a_picture_that_is_gone_is_reported(self):
        self.picture("001.jpg")
        self.credits('- 001.jpg: "Dusk" by Someone, CC BY 4.0\n- 004.jpg: "Old name" by Someone, CC BY 4.0\n')
        self.assertEqual(["themes/one/CREDITS.md credits 004.jpg, and there is no such picture"],
                         problems(self.folder))

    def test_a_file_that_states_no_licence_is_reported(self):
        self.picture("001.jpg")
        self.credits('- 001.jpg: "Dusk" by Someone\n')
        self.assertEqual(["themes/one/CREDITS.md states no licence (CC0, CC BY, CC BY-SA or public domain)"],
                         problems(self.folder))

    def test_a_caption_and_a_credit_that_state_different_licences_are_reported(self):
        self.picture("001.jpg")
        self.caption("001.jpg\tDusk · Someone, CC BY-SA 4.0\n")
        self.credits('- 001.jpg: "Dusk" by Someone, CC BY 4.0\n')
        self.assertEqual(["themes/one/wallpapers/captions.txt gives 001.jpg CC BY-SA 4.0 and CREDITS.md "
                          "gives it CC BY 4.0"], problems(self.folder))

    def test_a_caption_with_no_licence_is_not_compared(self):
        # Not every collection shows a licence (retro's captions are empty), and that is not a failure.
        self.picture("001.jpg")
        self.caption("001.jpg\tDusk\n")
        self.credits('- 001.jpg: "Dusk" by Someone, CC0\n')
        self.assertEqual([], problems(self.folder))

    def test_a_prose_credit_still_credits_the_pictures(self):
        # themes/retro/CREDITS.md's shape: the names in a sentence, the licence at its end.
        self.picture("6am.jpg")
        self.picture("9pm.jpg")
        self.credits("All two pictures (6am.jpg, 9pm.jpg) were created by the author, released under CC0 1.0.\n")
        self.assertEqual([], problems(self.folder))

    def test_a_single_picture_at_the_root_is_credited_too(self):
        # themes/cinematic/CREDITS.md's shape: the one wallpaper at the theme's root.
        (self.folder / "wallpaper.jpg").write_bytes(b"\xff\xd8")
        (self.folder / "screenshot.jpg").write_bytes(b"\xff\xd8")
        self.credits("- `wallpaper.jpg`: an original gradient, CC0 1.0\n")
        self.assertEqual([], problems(self.folder))

    def test_a_screenshot_is_not_asked_for_a_credit(self):
        self.picture("001.jpg")
        (self.folder / "screenshot.jpg").write_bytes(b"\xff\xd8")
        self.credits('- 001.jpg: "Dusk" by Someone, CC0\n')
        self.assertEqual([], problems(self.folder))

    def test_a_theme_with_no_credits_file_is_quiet(self):
        self.picture("001.jpg")
        self.assertEqual([], problems(self.folder))


if __name__ == "__main__":
    unittest.main()
