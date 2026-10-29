"""README: the Launcher settings section names every row the settings screen has.

The rows are read from the screen, not from a list kept here. `activity_launcher_settings.xml` carries
each row, and the three rows whose label the code writes (`wallpaperChoice`, `animationsChoice`,
`advancedChoice`) take the string `LauncherSettingsActivity.refresh()` sets on them. So a row added to
the screen without a README line fails in CI, while rewording a README line stays free as long as the
row's own label - the text the TV shows - is still there. The numbers next to the rows (4 to 7, 0 to 3,
the four dock opacities) come from the same two files.
"""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LAYOUT = ROOT / "launcher/app/src/main/res/layout/activity_launcher_settings.xml"
ACTIVITY = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/LauncherSettingsActivity.java"
STRINGS = ROOT / "launcher/app/src/main/res/values/strings.xml"
README = ROOT / "README.md"

ANDROID = "{http://schemas.android.com/apk/res/android}"
SUMMARY = "Launcher settings and the default home"


def attr(view, name):
    return view.get(ANDROID + name) or ""


def english_strings(text):
    """name -> value for every <string> in res/values/strings.xml (the English text Android shows)."""
    out = {}
    for match in re.finditer(r"<string name=\"([a-z_0-9]+)\"[^>]*>(.*?)</string>", text, re.S):
        out[match.group(1)] = match.group(2).strip()
    return out


def normalise(text):
    """One line, so a label the README wraps across two lines still matches."""
    return re.sub(r"\s+", " ", text.replace("**", "").replace("\\", "")).strip()


def row_label(name, strings):
    """A resource name as the row's label: 'animations_state' ("Animations: %1$s") is 'Animations'."""
    value = strings.get(name)
    if value is None:
        return None
    value = re.sub(r"%\d\$s", "", value)
    return value.strip().rstrip(":").strip() or None


def row_labels(layout_text, activity_text, strings):
    """Every label the settings screen shows, in screen order, as [(id, label)].

    A row is a view with the settings-row style, or a group label (a 20sp caption naming the
    numbered choices under it). A settings row with no android:text takes the string its own
    `<id>.setText(getString(R.string.<name>` uses in the activity.
    """
    rows = []
    for view in ET.fromstring(layout_text).iter():
        text = attr(view, "text")
        style = view.get("style") or ""
        if text.startswith("@string/"):
            # A row that carries its own label (a settings row), or the 20sp caption of a group of
            # numbered choices. The hints under a row are 14sp and the title is 34sp, so neither is a row.
            if not (style.endswith("SettingsRow") or attr(view, "textSize") == "20sp"):
                continue
            name = text[len("@string/"):]
        elif style.endswith("SettingsRow"):
            # A row whose label the code writes: `<id>.setText(getString(R.string.<name>` in the activity.
            view_id = attr(view, "id").split("/")[-1]
            found = re.search(r"\b%s\.setText\(\s*getString\(R\.string\.([a-z_0-9]+)"
                              % re.escape(view_id), activity_text) if view_id else None
            if not found:
                continue
            name = found.group(1)
        else:
            continue
        label = row_label(name, strings)
        if label:
            rows.append((attr(view, "id").split("/")[-1] or name, label))
    return rows


def missing_rows(section, rows):
    """The (id, label) rows the section does not name."""
    seen = normalise(section).lower()
    return [(row_id, label) for row_id, label in rows if label.lower() not in seen]


def numbered(label, layout_text):
    """The numbers under a label row, in the order the screen shows them (columns, rows below dock).

    The choices sit in a horizontal row of their own, right after the caption, so only that one sibling
    is read: the next caption on the page belongs to the next setting.
    """
    root = ET.fromstring(layout_text)
    for parent in root.iter():
        children = list(parent)
        for index, view in enumerate(children):
            if attr(view, "text") != "@string/" + label:
                continue
            numbers = []
            for sibling in children[index + 1:]:
                found = [child_text for child_text in
                         (attr(child, "text") for child in sibling.iter()) if child_text.isdigit()]
                if found:
                    numbers.extend(found)
                elif attr(sibling, "text"):
                    break       # the next caption: the choices above are the whole row
            return numbers
    return []


def detail_section(readme_text, summary):
    """The text inside one <details> block, or '' - so a row named three sections further down
    does not count as the settings page being documented."""
    start = readme_text.find("<summary><b>%s</b></summary>" % summary)
    if start < 0:
        return ""
    end = readme_text.find("</details>", start)
    return readme_text[start:end if end > 0 else len(readme_text)]


class ScreenRows(unittest.TestCase):
    """The README against the settings screen it describes."""

    def setUp(self):
        self.layout_text = LAYOUT.read_text(encoding="utf-8")
        self.activity_text = ACTIVITY.read_text(encoding="utf-8")
        self.strings = english_strings(STRINGS.read_text(encoding="utf-8"))
        self.rows = row_labels(self.layout_text, self.activity_text, self.strings)
        self.section = detail_section(README.read_text(encoding="utf-8"), SUMMARY)

    def test_screen_rows_are_read(self):
        """The screen has its rows, and each one resolved to the label the TV shows."""
        self.assertGreaterEqual(len(self.rows), 9, self.rows)
        labels = [label for _, label in self.rows]
        self.assertEqual(len(labels), len(set(labels)), labels)
        for row_id, label in self.rows:
            self.assertTrue(label.strip(), row_id)

    def test_readme_names_every_row(self):
        self.assertTrue(self.section, "README has no '%s' section" % SUMMARY)
        self.assertEqual(missing_rows(self.section, self.rows), [])

    def test_readme_names_the_numbers_under_each_row(self):
        columns = numbered("columns_label", self.layout_text)
        under_dock = numbered("rows_below_dock_label", self.layout_text)
        self.assertEqual(columns, ["4", "5", "6", "7"])
        self.assertEqual(under_dock, ["0", "1", "2", "3"])
        opacities = re.search(r"DOCK_OPACITIES\s*=\s*\{([0-9,\s]+)\}", self.activity_text).group(1)
        opacities = [value.strip() for value in opacities.split(",") if value.strip()]
        section = normalise(self.section)
        self.assertIn("%s, %s, %s or %s" % tuple(columns), section)
        self.assertIn("%s to %s" % (under_dock[0], under_dock[-1]), section)
        self.assertIn("%s%%, %s%%, %s%% or %s%%" % tuple(opacities), section)

    def test_a_row_the_readme_leaves_out_is_reported(self):
        rows = [("reorderChoice", "Reorder apps"), ("walls", "Wallpaper")]
        self.assertEqual(missing_rows("- **Reorder apps** only with the button.", rows),
                         [("walls", "Wallpaper")])

    def test_a_row_named_in_another_section_does_not_count(self):
        readme = ("<details>\n<summary><b>%s</b></summary>\n\nNothing here.\n\n</details>\n\n"
                  "<details>\n<summary><b>Elsewhere</b></summary>\n\n- **Wallpaper** this is not the "
                  "settings page.\n\n</details>\n" % SUMMARY)
        section = detail_section(readme, SUMMARY)
        self.assertIn("Nothing here", section)
        self.assertNotIn("Elsewhere", section)
        self.assertEqual(missing_rows(section, [("wallpaperChoice", "Wallpaper")]),
                         [("wallpaperChoice", "Wallpaper")])

    def test_a_placeholder_is_not_part_of_the_label(self):
        strings = {"animations_state": "Animations: %1$s", "wallpaper_label": "Wallpaper"}
        self.assertEqual(row_label("animations_state", strings), "Animations")
        self.assertEqual(missing_rows("**Animations** on or off", [("a", "Animations")]), [])
        self.assertEqual(missing_rows("**Animations: %s** on or off", [("a", "Animations")]), [])

    def test_a_label_wrapped_across_lines_still_matches(self):
        section = "**Follow theme (reset on-TV\n  choices)** clears the choices."
        self.assertEqual(missing_rows(section, [("f", "Follow theme (reset on-TV choices)")]), [])
        self.assertEqual(missing_rows(normalise(section),
                                       [("f", "Follow theme (reset on-TV choices)")]), [])

    def test_a_screen_with_no_rows_reads_as_no_rows(self):
        layout = ('<ScrollView xmlns:android="http://schemas.android.com/apk/res/android">'
                  '<TextView android:text="Hi" android:textSize="34sp" /></ScrollView>')
        self.assertEqual(row_labels(layout, "", {}), [])
        self.assertEqual(numbered("columns_label", layout), [])


if __name__ == "__main__":
    unittest.main()
