"""Checks that every config.xml key an examples/ page names is one config.xml defines.

A list header or a folder README tells the owner what to set in
launcher/app/src/main/res/values/config.xml for that TV ("put 240 in settings_key_scancodes in
config.xml"). The launcher looks its settings up by name, so an owner who adds a key it never asks
for builds and installs a launcher that behaves as before. examples/portronics-beem-470/debloat.txt
said to set `settings_component` for its Settings pill; no such key has ever existed in launcher/,
and the one that does that job is the `settings_fallback_components` array that SystemSettings.java
walks.

The check reads every snake_case name on a line of an examples/ .txt or .md file that mentions
config.xml, and asks that it be a resource name in config.xml. examples/README.md is left out: its
table is generated from each folder's tv.json by tools/gen_tables.py.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_theme_keys_config import CONFIG_XML  # noqa: E402


ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"
GENERATED = EXAMPLES / "README.md"

XML_COMMENT = re.compile(r"<!--.*?-->", re.S)
# <bool name="fill_dock">, <string-array name="dock_apps">, <integer-array name="settings_key_scancodes">
RESOURCE = re.compile(r'<[a-z]+(?:-array)?\s+name="([a-z][a-z0-9_]*)"')
# A snake_case word on its own: `app_keys`, settings_key_scancodes. Not a piece of a package name,
# a path or an UPPER_CASE variable (com.foo_bar, tools/gen_tables.py, HOME_KEY_SERVICE=1).
SNAKE = re.compile(r"(?<![A-Za-z0-9_./-])[a-z][a-z0-9]*(?:_[a-z0-9]+)+(?![A-Za-z0-9_/-])")


def resource_names(config_text):
    """Every resource name config.xml declares, comments aside."""
    return set(RESOURCE.findall(XML_COMMENT.sub("", config_text)))


def named_keys(text):
    """(line number, name) for each snake_case name on a line that mentions config.xml."""
    found = []
    for number, line in enumerate(text.split("\n"), 1):
        if "config.xml" not in line:
            continue
        found.extend((number, name) for name in SNAKE.findall(line))
    return found


def pages():
    return sorted(path for path in EXAMPLES.rglob("*")
                  if path.is_file() and path.suffix in (".txt", ".md") and path != GENERATED)


class ExampleConfigKeysTest(unittest.TestCase):
    def test_every_named_config_key_exists(self):
        names = resource_names(CONFIG_XML.read_text(encoding="utf-8"))
        self.assertIn("settings_fallback_components", names, "config.xml could not be read")
        read = 0
        for page in pages():
            relative = page.relative_to(ROOT).as_posix()
            for number, name in named_keys(page.read_text(encoding="utf-8")):
                read += 1
                if name not in names:
                    with self.subTest(line=f"{relative}:{number}", key=name):
                        self.fail(f"{relative}:{number} names `{name}` with config.xml, and "
                                  f"config.xml has no such key: the launcher would never read it")
        # A sweep that read nothing would pass for any page.
        self.assertGreaterEqual(read, 30, "far fewer config.xml keys named in examples/ than before")


class ReaderTest(unittest.TestCase):
    """The readers above, against text written here."""

    def test_resources_of_every_kind_are_read(self):
        text = ('<resources>\n'
                '    <bool name="fill_dock">true</bool>\n'
                '    <string name="live_tv_package" translatable="false"></string>\n'
                '    <string-array name="settings_fallback_components" translatable="false">\n'
                '    </string-array>\n'
                '    <integer-array name="settings_key_scancodes">\n'
                '    </integer-array>\n'
                '</resources>\n')
        self.assertEqual(resource_names(text), {"fill_dock", "live_tv_package",
                                                "settings_fallback_components",
                                                "settings_key_scancodes"})

    def test_a_name_inside_an_xml_comment_is_not_a_resource(self):
        text = '<!-- <bool name="old_switch">false</bool> -->\n<bool name="fill_dock">true</bool>\n'
        self.assertEqual(resource_names(text), {"fill_dock"})

    def test_names_only_from_lines_that_mention_config_xml(self):
        text = ("# settings put secure user_setup_complete 1\n"
                "# put 240 in settings_key_scancodes in launcher config.xml before building\n")
        self.assertEqual(named_keys(text), [(2, "settings_key_scancodes")])

    def test_backticked_names_are_read(self):
        self.assertEqual(named_keys("- `config.xml`: `stock_home_redirect` `com.x.launcherx`\n"),
                         [(1, "stock_home_redirect")])

    def test_packages_paths_and_variables_are_not_keys(self):
        line = ("# com.foo_bar.app, tools/gen_tables.py and HOME_KEY_SERVICE=1 with config.xml, "
                "launcher/app/src/main/res/values/config.xml\n")
        self.assertEqual(named_keys(line), [])

    def test_the_old_portronics_line_is_caught(self):
        line = "#   settings_component=com.htc.htcsettings/com.htc.activity.HomeActivity in config.xml.\n"
        self.assertEqual(named_keys(line), [(1, "settings_component")])


if __name__ == "__main__":
    unittest.main()
