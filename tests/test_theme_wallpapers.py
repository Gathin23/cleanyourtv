"""Every picture a theme ships is the one the recipe asks for, and one the launcher draws.

Two pages a theme author reads state the numbers: `docs/workflows/theme.md` step 2 ("dark, 1920x1080,
under about 300 KB each") and `themes/CREATE_A_THEME.md` step 2 ("Resize each to 1920x1080 and keep it
under about 300 KB"). Nothing read them. `tests/test_themes.py` holds `screenshot.jpg` to a shape, a
size and 16:9; the wallpaper - the picture a 1 GB TV has to decode - was read by no test at all, so a
theme PR could ship a 4000x3000 phone photo or a 4 MB PNG and CI stayed green.

The other half is a picture that is pushed and never drawn, which no page says and no tool reports:
`WallpaperRotation.images()` takes only `entry.length() > 0`, at most 10 MB, and a name ending in
.jpg/.jpeg/.png/.webp (read out of the Java below, not copied), and `Config` opens one file at the
theme root and calls it `WALLPAPER_NAME`. A `wallpapers/sunset.gif`, a picture over the cap the
launcher sets, or a `wallpaper.png` beside a `wallpaper.jpg` goes through the push without a word and
never appears on Home - tools/theme.sh's own suffix list agrees with the launcher's, so a .gif in
`wallpapers/` is not even sent.

This walks `themes/*/` once and judges every picture in it: the numbers from the recipe's own line, the
suffixes, the byte cap and the single picture's name from the launcher's source. The rest of the file
runs the same readers over pictures written in a temporary directory, one shape per test, so a rule
that stops being read also stops being green.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import struct
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
THEMES = ROOT / "themes"
LAUNCHER = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
ROTATION_JAVA = LAUNCHER / "WallpaperRotation.java"
CONFIG_JAVA = LAUNCHER / "Config.java"
CAPTIONS_JAVA = LAUNCHER / "WallpaperCaptions.java"
THEME_RECIPE = ROOT / "docs" / "workflows" / "theme.md"


def recipe_line():
    """The line of theme.md's step 2 that states the picture's size and weight, or a loud failure.

    The numbers below are read out of that sentence rather than copied here: a page that asks for a
    different shape has to make this file fail, not drift away from it.
    """
    for line in THEME_RECIPE.read_text(encoding="utf-8").splitlines():
        if "KB" in line and re.search(r"\d+x\d+", line):
            return line
    raise AssertionError("no line in docs/workflows/theme.md states a picture's size and weight")


def recipe_shape():
    """(width, height) as theme.md writes them, e.g. (1920, 1080)."""
    found = re.search(r"(\d+)x(\d+)", recipe_line())
    assert found, "the recipe's picture line states no WxH"
    return int(found.group(1)), int(found.group(2))


def recipe_kilobytes():
    """The KB the recipe allows one picture, e.g. 300."""
    found = re.search(r"(\d+)\s*KB", recipe_line())
    assert found, "the recipe's picture line states no KB limit"
    return int(found.group(1))


def shown_suffixes():
    """The suffixes WallpaperRotation.images() accepts: the pictures the launcher would put on Home."""
    found = re.findall(r'name\.endsWith\("(\.[a-z]+)"\)', ROTATION_JAVA.read_text(encoding="utf-8"))
    assert found, "no endsWith(\".ext\") in WallpaperRotation.java"
    return tuple(dict.fromkeys(found))


def launcher_byte_cap():
    """WallpaperRotation's own limit on one picture, multiplied out of the Java's `10 * 1024 * 1024`."""
    found = re.search(r"entry\.length\(\)\s*<=\s*([\d\s*]+)", ROTATION_JAVA.read_text(encoding="utf-8"))
    assert found, "no length cap in WallpaperRotation.images()"
    cap = 1
    for factor in found.group(1).split("*"):
        cap *= int(factor.strip())
    return cap


def single_picture_name():
    """Config.WALLPAPER_NAME: the one file at a theme's root the launcher opens as its wallpaper."""
    found = re.search(r'WALLPAPER_NAME\s*=\s*"([^"]+)"', CONFIG_JAVA.read_text(encoding="utf-8"))
    assert found, "no WALLPAPER_NAME in Config.java"
    return found.group(1)


def captions_name():
    """WallpaperCaptions.FILE_NAME: the one non-picture file a collection may carry."""
    found = re.search(r'FILE_NAME\s*=\s*"([^"]+)"', CAPTIONS_JAVA.read_text(encoding="utf-8"))
    assert found, "no FILE_NAME in WallpaperCaptions.java"
    return found.group(1)


def jpeg_size(data):
    """(width, height) from the first SOF marker, or None if this is not a JPEG."""
    if data[:2] != b"\xff\xd8":
        return None
    i = 2
    while i + 4 <= len(data):
        if data[i] != 0xFF:
            return None
        marker = data[i + 1]
        length = struct.unpack(">H", data[i + 2:i + 4])[0]
        if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
            height, width = struct.unpack(">HH", data[i + 5:i + 9])
            return width, height
        i += 2 + length
    return None


def png_size(data):
    """(width, height) from the IHDR chunk, or None if this is not a PNG."""
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        return None
    width, height = struct.unpack(">II", data[16:24])
    return width, height


def webp_size(data):
    """(width, height) from a WebP's VP8X, VP8 or VP8L chunk, or None if this is not a WebP."""
    if len(data) < 30 or data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        return None
    chunk = data[12:16]
    if chunk == b"VP8X":
        width = 1 + int.from_bytes(data[24:27], "little")
        height = 1 + int.from_bytes(data[27:30], "little")
        return width, height
    if chunk == b"VP8 ":
        # A lossy frame header: the 3-byte start code, then 14 bits of width and 14 of height.
        if data[23:26] != b"\x9d\x01\x2a":
            return None
        width = int.from_bytes(data[26:28], "little") & 0x3FFF
        height = int.from_bytes(data[28:30], "little") & 0x3FFF
        return width, height
    if chunk == b"VP8L":
        if data[20] != 0x2F:
            return None
        bits = int.from_bytes(data[21:25], "little")
        return (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1
    return None


def picture_size(data):
    """(width, height) for a picture the launcher would show, or None when it is none of the three."""
    for reader in (jpeg_size, png_size, webp_size):
        size = reader(data)
        if size is not None:
            return size
    return None


def picture_problems(data):
    """Every way this picture breaks the rules, one sentence each; empty when it is fine.

    The two the launcher enforces come first (it skips an empty file and one over its cap without a
    word), then the picture's shape and weight as the recipe states them.
    """
    if not data:
        return ["is empty, and the launcher skips an empty picture"]
    if len(data) > launcher_byte_cap():
        return [f"is {len(data)} bytes, and the launcher skips a picture over {launcher_byte_cap()} bytes"]
    size = picture_size(data)
    if size is None:
        return [f"is not a JPEG, PNG or WebP: it starts with {data[:12]!r}"]
    width, height = size
    want_width, want_height = recipe_shape()
    problems = []
    if width * want_height != height * want_width:
        problems.append(f"is {width}x{height}, not {want_width}x{want_height}'s shape")
    if width > want_width:
        problems.append(f"is {width} px wide, and {want_width} is the most the recipe asks for")
    limit = recipe_kilobytes() * 1024
    if len(data) > limit:
        problems.append(f"is {len(data) // 1024} KB, and the recipe keeps a picture under "
                        f"{recipe_kilobytes()} KB")
    return problems


def theme_folders(root):
    """Every theme folder, by name, in a stable order."""
    themes = Path(root) / "themes"
    if not themes.is_dir():
        return []
    return sorted(p for p in themes.iterdir() if p.is_dir())


def pictures(root):
    """(path relative to the checkout, path) for every picture a theme ships as a wallpaper.

    A single picture at the theme's root is found by its `wallpaper.` prefix whatever its suffix, and
    every file in a `wallpapers/` folder is a candidate: one the launcher would not show is a failure
    here, not a file to skip.
    """
    out = []
    for folder in theme_folders(root):
        for path in sorted(folder.glob("wallpaper.*")):
            if path.is_file():
                out.append((f"themes/{folder.name}/{path.name}", path))
        collection = folder / "wallpapers"
        if collection.is_dir():
            for path in sorted(collection.iterdir()):
                if path.is_file():
                    out.append((f"themes/{folder.name}/wallpapers/{path.name}", path))
    return out


def problems(root):
    """Every picture problem in this checkout, as 'where: what', in file order."""
    out = []
    want = single_picture_name()
    shown = shown_suffixes()
    keep = captions_name()
    for relative, path in pictures(root):
        if path.name == keep:
            continue
        if "/wallpapers/" not in relative:
            # The launcher opens one name at a theme's root; anything else with a picture's prefix sits
            # beside the picture it pushed and is never drawn.
            if path.name != want:
                out.append(f"{relative}: the launcher opens {want} only, so this picture is never drawn")
                continue
        elif path.suffix.lower() not in shown:
            out.append(f"{relative}: the launcher shows only {' '.join(shown)} in a collection, so this "
                       f"picture is never drawn")
            continue
        for problem in picture_problems(path.read_bytes()):
            out.append(f"{relative}: {problem}")
    return out


class ThemesTest(unittest.TestCase):
    def test_every_picture_is_one_the_recipe_and_the_launcher_agree_on(self):
        found = problems(ROOT)
        self.assertEqual([], found, "\n" + "\n".join(found))

    def test_the_walk_sees_the_pictures_the_tree_carries(self):
        # A walk that stops reading finds no problems either, so the tree it walks is checked here.
        singles = [r for r, _ in pictures(ROOT) if "/wallpapers/" not in r]
        collection = [r for r, _ in pictures(ROOT) if "/wallpapers/" in r]
        self.assertGreaterEqual(len(singles), 1, "no theme's single wallpaper was read")
        self.assertGreaterEqual(len(collection), 1, "the collections were not read")
        self.assertGreaterEqual(len({r.split("/")[1] for r in collection}), 1,
                                "the collections of fewer than four themes were read")

    def test_the_rules_are_read_out_of_the_page_and_the_launcher(self):
        width, height = recipe_shape()
        self.assertEqual((1920, 1080), (width, height),
                         "docs/workflows/theme.md no longer asks for 1920x1080; the check above follows it")
        self.assertGreaterEqual(recipe_kilobytes(), 100, "the recipe states no KB a picture may weigh")
        self.assertEqual((".jpg", ".jpeg", ".png", ".webp"), shown_suffixes(),
                         "WallpaperRotation.java's suffixes changed: the collection check above follows them")
        self.assertEqual("wallpaper.jpg", single_picture_name(),
                         "Config.java's single picture was renamed: the root check above follows it")


class ThePictureSize(unittest.TestCase):
    """picture_size: the three formats a theme may ship, read from the header alone."""

    def test_a_jpeg_is_measured_from_its_sof_marker(self):
        data = b"\xff\xd8" + b"\xff\xe0" + struct.pack(">H", 4) + b"\x00\x00" \
            + b"\xff\xc0" + struct.pack(">H", 11) + b"\x08" + struct.pack(">HH", 720, 1280) \
            + b"\x03" + b"\x00" * 9
        self.assertEqual((1280, 720), picture_size(data))

    def test_a_png_is_measured_from_its_ihdr(self):
        data = b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" \
            + struct.pack(">II", 1280, 720) + b"\x08\x02\x00\x00\x00"
        self.assertEqual((1280, 720), picture_size(data))

    def test_a_little_webp_is_measured_from_its_vp8l_chunk(self):
        bits = (1280 - 1) | ((720 - 1) << 14)
        data = b"RIFF" + struct.pack("<I", 30) + b"WEBPVP8L" + struct.pack("<I", 10) \
            + b"\x2f" + bits.to_bytes(4, "little") + b"\x00" * 5
        self.assertEqual((1280, 720), picture_size(data))

    def test_a_lossy_webp_is_measured_from_its_vp8_chunk(self):
        data = b"RIFF" + struct.pack("<I", 30) + b"WEBPVP8 " + struct.pack("<I", 10) \
            + b"\x00\x00\x00" + b"\x9d\x01\x2a" + struct.pack("<HH", 1280, 720) + b"\x00" * 4
        self.assertEqual((1280, 720), picture_size(data))

    def test_an_extended_webp_is_measured_from_its_vp8x_chunk(self):
        data = b"RIFF" + struct.pack("<I", 30) + b"WEBPVP8X" + struct.pack("<I", 10) \
            + b"\x00\x00\x00\x00" + (1280 - 1).to_bytes(3, "little") + (720 - 1).to_bytes(3, "little") \
            + b"\x00" * 4
        self.assertEqual((1280, 720), picture_size(data))

    def test_text_named_jpg_has_no_size(self):
        self.assertIsNone(picture_size(b"iVBORw0KGgo" + b"A" * 40))

    def test_a_truncated_jpeg_has_no_size(self):
        self.assertIsNone(picture_size(b"\xff\xd8" + b"\xff\xc0"))


class TheRules(unittest.TestCase):
    """picture_problems: one rule per test, over pictures written here."""

    def jpeg(self, width, height, extra=0):
        data = b"\xff\xd8" + b"\xff\xe0" + struct.pack(">H", 4) + b"\x00\x00" \
            + b"\xff\xc0" + struct.pack(">H", 11) + b"\x08" + struct.pack(">HH", height, width) \
            + b"\x03" + b"\x00" * 9
        return data + b"\x00" * extra

    def test_a_1280x720_jpeg_of_90_kb_is_fine(self):
        self.assertEqual([], picture_problems(self.jpeg(1280, 720, 90 * 1024)))

    def test_a_smaller_16_9_picture_is_fine(self):
        self.assertEqual([], picture_problems(self.jpeg(1536, 864, 90 * 1024)),
                         "the launcher scales a smaller 16:9 picture; themes/nature-calm ships them")

    def test_a_4_3_picture_is_named(self):
        self.assertIn("is 1280x960, not 1920x1080's shape", picture_problems(self.jpeg(1280, 960)))

    def test_a_picture_wider_than_the_recipe_is_named(self):
        self.assertIn("is 2560 px wide, and 1920 is the most the recipe asks for",
                      picture_problems(self.jpeg(2560, 1440)))

    def test_a_picture_over_300_kb_is_named(self):
        self.assertIn("is 400 KB, and the recipe keeps a picture under 300 KB",
                      picture_problems(self.jpeg(1920, 1080, 400 * 1024)))

    def test_an_empty_file_is_named(self):
        self.assertEqual(["is empty, and the launcher skips an empty picture"], picture_problems(b""))

    def test_a_picture_over_the_launchers_own_cap_is_named(self):
        cap = launcher_byte_cap()
        data = self.jpeg(1920, 1080)
        data += b"\x00" * (cap + 1 - len(data))
        self.assertIn(f"is {cap + 1} bytes, and the launcher skips a picture over {cap} bytes",
                      picture_problems(data))

    def test_a_gif_is_not_a_picture_the_launcher_shows(self):
        data = b"GIF89a" + (1280).to_bytes(2, "little") + (720).to_bytes(2, "little") + b"\x00" * 10
        self.assertIn("is not a JPEG, PNG or WebP", picture_problems(data)[0])


class TheTree(unittest.TestCase):
    """problems(): a stand-in tree, so the walk itself is held to the rules."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "themes").mkdir()

    def write(self, relative, data=b"\xff\xd8"):
        path = self.root / "themes" / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path

    def jpeg(self, width, height):
        return (b"\xff\xd8" + b"\xff\xe0" + struct.pack(">H", 4) + b"\x00\x00"
                + b"\xff\xc0" + struct.pack(">H", 11) + b"\x08" + struct.pack(">HH", height, width)
                + b"\x03" + b"\x00" * 29)

    def test_a_theme_with_the_picture_it_names_is_quiet(self):
        self.write("one/wallpaper.jpg", self.jpeg(1920, 1080))
        self.write("two/wallpapers/001.jpg", self.jpeg(1280, 720))
        self.write("two/wallpapers/captions.txt", b"001.jpg\tDusk\n")
        self.assertEqual([], problems(self.root))

    def test_a_single_picture_under_another_name_is_never_drawn(self):
        self.write("one/wallpaper.jpg", self.jpeg(1920, 1080))
        self.write("one/wallpaper.png", self.jpeg(1920, 1080))
        self.assertEqual(["themes/one/wallpaper.png: the launcher opens wallpaper.jpg only, so this "
                          "picture is never drawn"], problems(self.root))

    def test_a_picture_the_launcher_would_skip_in_a_collection_is_named(self):
        self.write("one/wallpapers/sunset.gif", b"GIF89a" + b"\x00" * 20)
        self.assertEqual(["themes/one/wallpapers/sunset.gif: the launcher shows only .jpg .jpeg .png "
                          ".webp in a collection, so this picture is never drawn"], problems(self.root))

    def test_a_collection_picture_of_the_wrong_shape_is_named(self):
        self.write("one/wallpapers/001.jpg", self.jpeg(1280, 960))
        self.assertEqual(["themes/one/wallpapers/001.jpg: is 1280x960, not 1920x1080's shape"],
                         problems(self.root))

    def test_a_theme_with_no_picture_is_quiet(self):
        (self.root / "themes" / "default").mkdir()
        self.assertEqual([], problems(self.root))


if __name__ == "__main__":
    unittest.main()
