"""The Settings long-press menu is written down where a reader is sent for it.

Clicking Settings opens the TV's own settings app. Holding OK opens a menu instead, and on a
China-market set the row an owner needs is the one Xiaomi's own settings app carries: the ADB switch
under Account & Security, which Android's settings app has no page for. MainActivity.showSettingsMenu()
offers that app as "Native settings" whenever one of the packages in `settings_fallback_components` is
installed (Xiaomi China, TCL/FFALCON, Fire OS), yet the README block a reader is sent to - "Launcher
settings and the default home" - described the launcher's own settings page and not this menu at all.
The row was written down in one device note only, and that note sent the reader to "the pull request
that added it": merged, so there is nothing there to read, while the notes for other China sets answer
a closed ADB port with "re-enable it in Account & Security" without saying which app that is.

The row names are read out of the menu code and out of the string resources, so renaming a row moves
the requirement with it - nothing here says what a row is called or how the sentence reads.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import html
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN_ACTIVITY = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "MainActivity.java"
STRINGS = ROOT / "launcher" / "app" / "src" / "main" / "res" / "values" / "strings.xml"
README = ROOT / "README.md"
VENDOR_NOTE = ROOT / "docs" / "devices" / "xiaomi-mitv-mteq0" / "xiaomi-mitv-mteq0.md"

METHOD = "private void showSettingsMenu()"
BLOCK = "<summary><b>Launcher settings and the default home</b></summary>"
# addSettingsMenuRow(rows, popup, R.string.<name>, ...), however the call is wrapped.
ROW = re.compile(r"addSettingsMenuRow\(\s*rows,\s*popup,\s*R\.string\.([a-z_]+)")


def method_body(text, name=METHOD):
    """The lines of the method whose declaration starts with `name`, up to its closing brace."""
    lines = text.split("\n")
    start = next((i for i, line in enumerate(lines) if line.strip().startswith(name)), None)
    if start is None:
        return ""
    for i in range(start + 1, len(lines)):
        if lines[i] == "    }":          # the method's own closing brace, not a nested block
            return "\n".join(lines[start + 1:i])
    return "\n".join(lines[start + 1:])


def without_comments(body):
    """The body with `//` comments dropped, so a row that was commented out is not a row."""
    return "\n".join(line.split("//", 1)[0] for line in body.split("\n"))


def menu_rows(body):
    """The string resources the menu adds a row for, in the order the menu adds them."""
    rows = []
    for line in without_comments(body).split("\n"):
        rows.extend(ROW.findall(line))
    return rows


def string_text(name, xml):
    """What R.string.<name> says in the default (English) strings.xml."""
    match = re.search(r'<string\s+name="%s"[^>]*>(.*?)</string>' % re.escape(name), xml, re.S)
    if match is None:
        raise AssertionError(
            f"the Settings menu names a row R.string.{name} that values/strings.xml does not define")
    return html.unescape(match.group(1)).strip()


def settings_block(text):
    """The README block that answers how to reach the launcher's and the TV's settings."""
    start = text.find(BLOCK)
    if start < 0:
        return ""
    end = text.find("</details>", start)
    return text[start:end]


class SettingsMenuDocumentedTest(unittest.TestCase):
    def setUp(self):
        self.body = method_body(MAIN_ACTIVITY.read_text(encoding="utf-8"))
        self.xml = STRINGS.read_text(encoding="utf-8")
        self.block = settings_block(README.read_text(encoding="utf-8"))

    def test_the_menu_still_offers_the_vendor_settings_app(self):
        # The subject of this file: without this row there is no route to Account & Security on a set
        # whose Settings action resolves to the Android app.
        self.assertIn("native_settings", menu_rows(self.body),
                      "showSettingsMenu() no longer adds a Native settings row: this page and the note are stale")

    def test_every_menu_row_is_named_in_the_readme_block(self):
        rows = menu_rows(self.body)
        self.assertGreaterEqual(len(rows), 4, "read no rows out of showSettingsMenu(): the reader is broken")
        self.assertTrue(self.block, f"README.md has no {BLOCK} block")
        for name in rows:
            with self.subTest(row=name):
                text = string_text(name, self.xml)
                self.assertIn(text, self.block,
                              f"the Settings menu's {text!r} row (R.string.{name}) is not in the README block")

    def test_the_vendor_note_names_the_row_and_reads_on_its_own(self):
        note = VENDOR_NOTE.read_text(encoding="utf-8")
        text = string_text("native_settings", self.xml)
        self.assertIn(text, note, f"the note no longer names the {text!r} row")
        self.assertNotIn("pull request", note.lower(),
                         "the note sends a reader to a pull request; say what the merged menu does instead")


class MenuReaderTest(unittest.TestCase):
    """The reader above, against code written here, so a broken one fails instead of passing quietly."""

    def test_a_wrapped_call_is_one_row(self):
        body = """        if (moduleCards) addSettingsMenuRow(rows, popup, R.string.module_cards, () -> {
            openSomething();
        });"""
        self.assertEqual(menu_rows(body), ["module_cards"])

    def test_a_commented_out_row_is_not_a_row(self):
        body = """        // addSettingsMenuRow(rows, popup, R.string.native_settings, this::openVendor);
        addSettingsMenuRow(rows, popup, R.string.reorder, this::toggleReorder);"""
        self.assertEqual(menu_rows(body), ["reorder"])

    def test_a_label_that_is_not_a_string_resource_is_not_a_row(self):
        body = """        addSettingsMenuRow(rows, popup, getString(R.string.reorder), this::toggleReorder);
        addSettingsMenuRow(rows, popup, labels.get(0), this::toggleReorder);"""
        self.assertEqual(menu_rows(body), [])

    def test_the_real_menu_is_read(self):
        # Six rows today: launcher settings, native settings, module cards, dock edit, reorder, restart.
        body = method_body(MAIN_ACTIVITY.read_text(encoding="utf-8"))
        self.assertEqual(menu_rows(body),
                         ["launcher_settings", "native_settings", "module_cards", "dock_edit", "reorder",
                          "power_menu"])

    def test_a_row_with_no_string_is_reported(self):
        with self.assertRaisesRegex(AssertionError, r"R\.string\.not_a_string"):
            string_text("not_a_string", STRINGS.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
