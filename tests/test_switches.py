"""Every theme key (a switch) has a screenshot of it on a real TV, listed in docs/switches.md.

The site's Switches tab reads the same table, so a key added with its screenshot shows up there too.

A row may show more than one picture - `vpn_pill` carries the pill up and the pill at rest,
`icon_only_vpn` the lock open and the lock closed - and the page's rules (a JPEG or PNG, 16:9, no
wider than 1920 px, under 300 KB) are about every picture in the table, not one per row. The reader
used to take the last `<img>` of a row (`.*<img` is greedy), so the picture before it in a two-picture
row was read by nothing: `switches/vpn_pill.jpg` and `switches/icon_only_vpn.jpg` could have been
missing, 4:3, over 300 KB or a WebP named .jpg and the suite stayed green.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_themes import MAX_SCREENSHOT, jpeg_size  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
THEMES_README = ROOT / "themes" / "README.md"
SWITCHES = DOCS / "switches.md"

# A row of the Screenshots table: `| `key` | the pictures |`.
ROW_RE = re.compile(r"^\|\s*`([a-z_]+)`\s*\|(.*)$")
IMG_RE = re.compile(r"<img\b[^>]*>")
SRC_RE = re.compile(r'\bsrc="([^"]*)"')

# The page's limits (docs/switches.md, "Adding a key"): a listed picture is a JPEG or PNG, 16:9, no
# wider than this and under MAX_SCREENSHOT. tests/test_switches_page_rules.py reads this number back
# out of the file, so the page cannot drift from the checker.
MAX_WIDTH = 1920

# Keys from before screenshots were required. This list only shrinks: once a key has a row in
# docs/switches.md, take it off here.
SCREENSHOT_WANTED = {
    "accent_color", "advanced_effects", "always_show_names", "ambient_glow", "animations",
    "app_banners", "app_glow", "app_keys", "app_labels", "app_library", "app_library_selection",
    "background_color", "boot_to_live_tv", "cinematic_art", "cinematic_dots", "cinematic_home",
    "cinematic_scrim", "cinematic_white_focus", "coocaa_sources", "dock_apps", "dock_even_padding",
    "dock_glass", "dock_opacity", "dock_row", "dock_title", "fade_rows", "festival_until", "fill_dock",
    "focus_overshoot", "glow_style", "greeting", "greeting_name", "handle_view_inputs", "hidden_apps",
    "icon_only_buttons", "icon_only_inputs", "input_action", "input_key_keycodes", "input_sources",
    "input_uri_scheme", "key_filter", "launch_actions", "launcher_settings_button", "library_apps",
    "library_button", "live_tv_action", "lunar_utc_offset", "live_tv_input_id", "live_tv_package", "module_cards",
    "race_calendar", "reduced_motion", "reorder_button", "restore_key_services", "rows_below_dock",
    "settings_fallback_components", "settings_key_keycodes", "settings_key_scancodes", "shamsi_date",
    "show_home_settings_shortcut", "show_inputs_pill", "show_network_pill", "show_stats",
    "source_panel_action", "source_panel_extra", "source_panel_service", "stock_home_redirect",
    "tile_menu", "top_menu", "vpn_app", "wallpaper", "wallpaper_captions", "wallpaper_dim",
    "wallpaper_moods", "wallpaper_order", "wallpaper_true_color", "wifi_label_width",
    "wifi_status_color",
}


def theme_keys():
    """Every key in the Keys table of themes/README.md, as the site reads it."""
    keys, on = [], False
    for line in THEMES_README.read_text(encoding="utf-8").split("\n"):
        if re.match(r"^##\s", line):
            on = bool(re.match(r"^##\s+Keys\s*$", line))
        if not on or not re.match(r"^\|\s*`[a-z_]+`", line):
            continue
        for key in re.findall(r"`([a-z_]+)`", line.split("|")[1]):
            if key not in keys:
                keys.append(key)
    return keys


def rows(text):
    """(key, the pictures in that row in the order the page shows them) for every table row.

    An `<img>` with no src, or with `src=""`, keeps its place as an empty string: a picture the page
    cannot draw is a row to fix, not a row to skip.
    """
    found = []
    for line in text.split("\n"):
        match = ROW_RE.match(line)
        if not match:
            continue
        pictures = []
        for tag in IMG_RE.findall(match.group(2)):
            src = SRC_RE.search(tag)
            pictures.append(src.group(1) if src else "")
        found.append((match.group(1), pictures))
    return found


def screenshot_rows(text=None):
    """key -> every picture its row shows, in page order. A key named in two rows keeps both."""
    table = {}
    for key, pictures in rows(SWITCHES.read_text(encoding="utf-8") if text is None else text):
        table.setdefault(key, []).extend(pictures)
    return table


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", data[16:24])


def picture_problem(docs, src):
    """Why the picture `src`, read below the folder `docs`, breaks the rules the page states, or None."""
    if not src:
        return "the <img> in this row has no src, so nothing is drawn there"
    path = (Path(docs) / src).resolve()
    if not path.is_file():
        return f"{src} does not exist"
    data = path.read_bytes()
    if len(data) > MAX_SCREENSHOT:
        return f"{src} is over 300 KB"
    size = jpeg_size(data) or png_size(data)
    if size is None:
        return f"{src} is not a JPEG or PNG"
    width, height = size
    if width * 9 != height * 16:
        return f"{src} is {width}x{height}, not 16:9"
    if width > MAX_WIDTH:
        return f"{src} is {width} px wide; the page says no wider than {MAX_WIDTH}, and 1280x720 is enough"
    return None


def problems(docs, table):
    """(key, src, why) for every picture in `table` that breaks the rules, in page order."""
    return [(key, src, why)
            for key, pictures in table.items()
            for src in pictures
            for why in [picture_problem(docs, src)]
            if why]


class SwitchScreenshotTest(unittest.TestCase):
    def test_every_key_has_a_screenshot(self):
        rows_by_key = screenshot_rows()
        for key in theme_keys():
            with self.subTest(key=key):
                if key in SCREENSHOT_WANTED:
                    self.assertNotIn(key, rows_by_key,
                                     "it has a screenshot now: take it off SCREENSHOT_WANTED")
                    continue
                self.assertTrue(rows_by_key.get(key),
                                "no screenshot: add one to docs/switches.md (see its Adding a key section)")

    def test_every_picture_in_a_row_exists_and_fits_the_rules(self):
        bad = problems(DOCS, screenshot_rows())
        self.assertEqual([], [f"{key}: {why}" for key, src, why in bad],
                         "a picture in docs/switches.md that breaks the rules its Adding a key section states")

    def test_rows_and_wanted_list_name_real_keys(self):
        keys = set(theme_keys())
        self.assertEqual(set(screenshot_rows()) - keys, set(),
                         "docs/switches.md lists a key themes/README.md does not")
        self.assertEqual(SCREENSHOT_WANTED - keys, set(),
                         "SCREENSHOT_WANTED names a key themes/README.md does not")

    def test_the_page_shows_every_picture_it_is_checked_for(self):
        # A reader that finds no row, or one picture per row, would pass the checks above by reading
        # nothing: the page holds a row per key that has its screenshot, and a few rows hold two.
        table = screenshot_rows()
        self.assertGreaterEqual(len(table), 30, "the Screenshots table was not read")
        self.assertGreaterEqual(sum(len(p) for p in table.values()), len(table),
                                "the pictures in the Screenshots table were not read")


class TheReader(unittest.TestCase):
    """The row reader, over pages written here rather than the one in the tree."""

    def test_a_row_with_two_pictures_gives_both_in_page_order(self):
        page = "\n".join([
            "| Key | Screenshot |",
            "|---|---|",
            '| `vpn_pill` | <img src="switches/vpn_pill.jpg" width="320"> <img src="switches/vpn_pill_rest.jpg" width="320"> |',
        ])
        self.assertEqual({"vpn_pill": ["switches/vpn_pill.jpg", "switches/vpn_pill_rest.jpg"]},
                         screenshot_rows(page))

    def test_a_row_with_one_picture_and_a_row_with_none(self):
        page = "\n".join([
            '| `dock_band` | <img src="switches/dock_band.jpg" width="320" alt="dock_band"> |',
            "| `tile_menu` | the menu is described in prose |",
        ])
        self.assertEqual({"dock_band": ["switches/dock_band.jpg"], "tile_menu": []},
                         screenshot_rows(page))

    def test_an_img_without_a_src_keeps_its_place(self):
        page = '| `dock_band` | <img src="switches/dock_band.jpg"> <img alt="no source"> |'
        self.assertEqual({"dock_band": ["switches/dock_band.jpg", ""]}, screenshot_rows(page))

    def test_an_empty_src_is_not_a_picture(self):
        page = '| `dock_band` | <img src=""> |'
        self.assertEqual({"dock_band": [""]}, screenshot_rows(page))
        self.assertIn("no src", problems(DOCS, screenshot_rows(page))[0][2])

    def test_a_key_named_in_two_rows_keeps_both_rows_pictures(self):
        page = "\n".join([
            '| `dock_band` | <img src="switches/dock_band.jpg"> |',
            '| `dock_band` | <img src="switches/single_row.jpg"> |',
        ])
        self.assertEqual({"dock_band": ["switches/dock_band.jpg", "switches/single_row.jpg"]},
                         screenshot_rows(page))

    def test_a_line_that_is_not_a_row_is_not_read(self):
        # The header, the separator and the prose around the table.
        page = "\n".join([
            "| Key | Screenshot |",
            "|---|---|",
            "A new key needs a screenshot.",
            "| `dock_band` | <img src=\"switches/dock_band.jpg\"> |",
            "`tests/test_switches.py` checks it.",
        ])
        self.assertEqual(["dock_band"], list(screenshot_rows(page)))


class ThePictureRules(unittest.TestCase):
    """The rules, over pictures written here rather than the ones in the tree."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.docs = Path(self.tmp.name)
        self.addCleanup(self.tmp.cleanup)

    def jpeg(self, name, width, height, size=None):
        segment = b"\xff\xc0" + struct.pack(">H", 11) + b"\x08" + struct.pack(">HH", height, width) \
            + b"\x01\x01\x11\x00"
        data = b"\xff\xd8" + segment + b"\xff\xd9"
        path = self.docs / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data + b"\x00" * max(0, (size or 0) - len(data)))
        return path

    def png(self, name, width, height):
        data = b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" \
            + struct.pack(">II", width, height) + b"\x08\x02\x00\x00\x00"
        path = self.docs / name
        path.write_bytes(data)
        return path

    def test_a_1280x720_jpeg_and_png_pass(self):
        self.jpeg("switches/a.jpg", 1280, 720)
        self.png("switches/b.png", 1280, 720)
        self.assertEqual([], problems(self.docs, {"a": ["switches/a.jpg"], "b": ["switches/b.png"]}))

    def test_a_picture_that_is_not_16_by_9_is_reported(self):
        self.jpeg("switches/a.jpg", 640, 480)
        bad = problems(self.docs, {"a": ["switches/a.jpg", "switches/a.jpg"]})
        self.assertEqual(2, len(bad), "both pictures are judged, not one per key")
        self.assertIn("not 16:9", bad[0][2])

    def test_a_picture_over_300_kb_is_reported(self):
        self.jpeg("switches/a.jpg", 1280, 720, size=MAX_SCREENSHOT + 1)
        self.assertIn("over 300 KB", problems(self.docs, {"a": ["switches/a.jpg"]})[0][2])

    def test_a_picture_wider_than_1920_is_reported(self):
        self.jpeg("switches/a.jpg", 3840, 2160)
        self.assertIn("wider than 1920", problems(self.docs, {"a": ["switches/a.jpg"]})[0][2])

    def test_a_text_file_named_jpg_is_reported(self):
        (self.docs / "switches").mkdir()
        (self.docs / "switches" / "a.jpg").write_text("iVBORw0KGgo", encoding="utf-8")
        self.assertIn("not a JPEG or PNG", problems(self.docs, {"a": ["switches/a.jpg"]})[0][2])

    def test_a_picture_that_is_not_there_is_reported(self):
        self.assertIn("does not exist",
                      problems(self.docs, {"a": ["switches/nowhere.jpg"]})[0][2])


if __name__ == "__main__":
    unittest.main()
