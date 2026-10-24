"""Every theme key (a switch) has a screenshot of it on a real TV, listed in docs/switches.md.

The site's Switches tab reads the same table, so a key added with its screenshot shows up there too.
Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_themes import MAX_SCREENSHOT, jpeg_size  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
THEMES_README = ROOT / "themes" / "README.md"
SWITCHES = DOCS / "switches.md"

# Keys from before screenshots were required. This list only shrinks: once a key has a row in
# docs/switches.md, take it off here.
SCREENSHOT_WANTED = {
    "accent_color", "advanced_effects", "always_show_names", "ambient_glow", "animations",
    "app_banners", "app_glow", "app_keys", "app_labels", "app_library", "app_library_selection",
    "background_color", "boot_to_live_tv", "cinematic_art", "cinematic_dots", "cinematic_home",
    "cinematic_scrim", "cinematic_white_focus", "coocaa_sources", "dock_apps", "dock_even_padding",
    "dock_glass", "dock_opacity", "dock_row", "dock_title", "fade_rows", "festival_until", "fill_dock",
    "focus_anticipation", "focus_overshoot", "glass_depth_layers", "glow_style", "greeting",
    "greeting_name", "handle_view_inputs", "hidden_apps",
    "icon_only_buttons", "icon_only_inputs", "input_action", "input_key_keycodes", "input_sources",
    "input_uri_scheme", "key_filter", "launch_actions", "launcher_settings_button", "library_apps",
    "library_button", "live_tv_action", "live_tv_input_id", "live_tv_package", "module_cards",
    "race_calendar", "reduced_motion", "reorder_button", "restore_key_services", "rows_below_dock",
    "settings_fallback_components", "settings_key_keycodes", "shamsi_date",
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


def screenshot_rows():
    """key -> image path relative to docs/, from the Screenshots table of docs/switches.md."""
    rows = {}
    for line in SWITCHES.read_text(encoding="utf-8").split("\n"):
        m = re.match(r'^\|\s*`([a-z_]+)`\s*\|.*<img[^>]*\bsrc="([^"]+)"', line)
        if m:
            rows[m.group(1)] = m.group(2)
    return rows


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", data[16:24])


class SwitchScreenshotTest(unittest.TestCase):
    def test_every_key_has_a_screenshot(self):
        rows = screenshot_rows()
        for key in theme_keys():
            with self.subTest(key=key):
                if key in SCREENSHOT_WANTED:
                    self.assertNotIn(key, rows, "it has a screenshot now: take it off SCREENSHOT_WANTED")
                    continue
                self.assertIn(key, rows, "no screenshot: add one to docs/switches.md (see its Adding a key section)")

    def test_screenshots_exist_and_fit_the_rules(self):
        for key, src in screenshot_rows().items():
            with self.subTest(key=key):
                path = (DOCS / src).resolve()
                self.assertTrue(path.is_file(), f"{src} does not exist")
                data = path.read_bytes()
                self.assertLessEqual(len(data), MAX_SCREENSHOT, f"{src} is over 300 KB")
                size = jpeg_size(data) or png_size(data)
                self.assertIsNotNone(size, f"{src} is not a JPEG or PNG")
                width, height = size
                self.assertEqual(width * 9, height * 16, f"{src} is {width}x{height}, not 16:9")
                self.assertLessEqual(width, 1920, f"{src} is wider than 1920; 1280x720 is enough")

    def test_rows_and_wanted_list_name_real_keys(self):
        keys = set(theme_keys())
        self.assertEqual(set(screenshot_rows()) - keys, set(), "docs/switches.md lists a key themes/README.md does not")
        self.assertEqual(SCREENSHOT_WANTED - keys, set(), "SCREENSHOT_WANTED names a key themes/README.md does not")


if __name__ == "__main__":
    unittest.main()
