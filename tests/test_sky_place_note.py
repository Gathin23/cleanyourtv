"""docs/sky.md has to say where the sky's sunrise and sunset come from.

The page described the glow as following the sun, and named only `sky_intensity`, which a theme can set.
The place the sun is computed for lives in two string resources in `config.xml` instead, and nothing in
the repo mentioned them outside that file's own comment, so a theme author reading the page had no way to
know the glow peaks at a fixed 19:00 rather than at their own sunset. These tests pin the section that
says so to the code and the resource it describes: the two times against `Sky.java`'s reference constants,
the two resources against `config.xml`, and "a theme cannot set this" against `Config.java` never reading
the key and the Keys table never listing it. The page is reachable now too: `themes/README.md`'s backdrop
section, where `sky_intensity` is explained, links it.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKY_DOC = ROOT / "docs" / "sky.md"
THEMES_DOC = ROOT / "themes" / "README.md"
CONFIG_XML = ROOT / "launcher" / "app" / "src" / "main" / "res" / "values" / "config.xml"
SKY_JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "Sky.java"
SKY_VIEW_JAVA = SKY_JAVA.with_name("SkyView.java")
CONFIG_JAVA = SKY_JAVA.with_name("Config.java")

HEADING = "## Where the sun is"


def section(text, heading):
    """The lines under `heading`, up to the next heading of the same level, or None."""
    lines = text.splitlines()
    try:
        start = next(i for i, l in enumerate(lines) if l.strip() == heading)
    except StopIteration:
        return None
    level = len(heading) - len(heading.lstrip("#"))
    body = []
    for line in lines[start + 1:]:
        stripped = line.strip()
        if stripped.startswith("#") and len(stripped) - len(stripped.lstrip("#")) <= level:
            break
        body.append(line)
    return "\n".join(body)


def markdown_destinations(text, base):
    """Every relative destination a reader can click in `text`, resolved against `base`."""
    out = []
    for pattern in (r"\[[^\]]*\]\(([^)\s]+)\)", r'(?:href|src)="([^"]+)"'):
        for target in re.findall(pattern, text):
            if target.startswith(("http://", "https://", "mailto:", "#", "data:")):
                continue
            target = target.split("#")[0]
            if target:
                out.append((base / target).resolve())
    return out


def reference_times(java):
    """The reference day as HH:MM, read from the two constants the page quotes."""
    out = {}
    for name in ("REF_SUNRISE", "REF_SUNSET"):
        m = re.search(name + r"\s*=\s*(\d+)", java)
        if not m:
            raise AssertionError(f"Sky.java no longer defines {name}; the page's two times are unbacked")
        minutes = int(m.group(1))
        out[name] = f"{minutes // 60:02d}:{minutes % 60:02d}"
    return out


def keys_table(text):
    """The first cell of every body row of the ## Keys table: the keys a theme can set."""
    table = section(text, "## Keys")
    if table is None:
        raise AssertionError("themes/README.md has no ## Keys table")
    out = []
    for line in table.splitlines():
        stripped = line.strip()
        if not stripped.startswith("|") or re.match(r"^\|\s*-", stripped):
            continue
        first = stripped.strip("|").split("|")[0].strip().strip("`")
        if first and first != "Key":
            out.append(first)
    return out


class SkyPlaceSection(unittest.TestCase):
    def setUp(self):
        self.doc = SKY_DOC.read_text(encoding="utf-8")
        self.section = section(self.doc, HEADING)
        if self.section is None:
            self.fail(f"docs/sky.md has no {HEADING!r} section")
        self.java = SKY_JAVA.read_text(encoding="utf-8")
        self.config_xml = CONFIG_XML.read_text(encoding="utf-8")

    def test_names_both_coordinates_and_the_file_they_live_in(self):
        for needle in ("latitude", "longitude"):
            self.assertIn(needle, self.section)
        self.assertIn("launcher/app/src/main/res/values/config.xml", self.section,
                      "the page has to name the file a reader edits, not only its file name")

    def test_the_two_resources_exist_and_are_empty_by_default(self):
        for name in ("latitude", "longitude"):
            m = re.search(r'<string name="%s"([^>]*)>([^<]*)</string>' % name, self.config_xml)
            if m is None:
                self.fail(f"config.xml has no {name} string resource")
            self.assertIn('translatable="false"', m.group(1), f"{name} is a number, not a translation")
            self.assertEqual(m.group(2), "",
                             f"{name} is no longer empty by default; the page's fallback may be stale")

    def test_the_fixed_day_it_quotes_is_the_one_sky_java_draws(self):
        times = reference_times(self.java)
        self.assertIn(times["REF_SUNRISE"], self.section,
                      f"the page should give the reference sunrise, {times['REF_SUNRISE']}")
        self.assertIn(times["REF_SUNSET"], self.section,
                      f"the page should give the reference sunset, {times['REF_SUNSET']}")

    def test_skyview_is_what_reads_them(self):
        view = SKY_VIEW_JAVA.read_text(encoding="utf-8")
        for name in ("latitude", "longitude"):
            self.assertIn(f"R.string.{name}", view,
                          f"SkyView no longer reads R.string.{name}: the page points at the wrong knob")

    def test_it_says_a_theme_cannot_set_the_place(self):
        self.assertIn("theme", self.section.lower())
        table = keys_table(THEMES_DOC.read_text(encoding="utf-8"))
        self.assertNotIn("latitude", table, "latitude is a theme key now: the page has to say so")
        self.assertNotIn("longitude", table, "longitude is a theme key now: the page has to say so")
        config_java = CONFIG_JAVA.read_text(encoding="utf-8")
        for name in ("latitude", "longitude"):
            self.assertNotIn(f'"{name}"', config_java,
                             f"Config.java reads a {name} key now: a theme can set the place after all")

    def test_it_names_the_days_that_fall_back(self):
        for needle in ("polar night", "midnight sun"):
            self.assertIn(needle, self.section,
                          f"the fixed day also covers the {needle}, and the page should say so")

    def test_the_section_links_nothing_dead(self):
        for target in markdown_destinations(self.section, SKY_DOC.parent):
            self.assertTrue(target.exists(), f"{target} does not exist")


class ThePageIsReachable(unittest.TestCase):
    def test_the_backdrop_section_of_themes_readme_links_it(self):
        text = THEMES_DOC.read_text(encoding="utf-8")
        backdrop = section(text, "## Time-of-day backdrop")
        if backdrop is None:
            self.fail("themes/README.md has no backdrop section")
        self.assertIn(SKY_DOC.resolve(), markdown_destinations(backdrop, THEMES_DOC.parent),
                      "the page a theme author needs is not linked from the key it explains")


class TheCheckersThemselves(unittest.TestCase):
    """Every check above has to fail on the text it is there to catch."""

    def test_section_stops_at_the_next_heading_of_its_level(self):
        text = "## Where the sun is\nA\n### Deeper\nB\n## Cost\nC\n"
        self.assertEqual(section(text, "## Where the sun is"), "A\n### Deeper\nB")

    def test_section_is_none_when_the_heading_is_gone(self):
        self.assertIsNone(section("## Cost\nonly this\n", HEADING))

    def test_the_wrong_times_are_reported(self):
        times = reference_times(SKY_JAVA.read_text(encoding="utf-8"))
        old = "sunrise 06:00, sunset 20:00"
        self.assertNotIn(times["REF_SUNRISE"], old)
        self.assertNotIn(times["REF_SUNSET"], old)

    def test_keys_table_reads_the_first_cell_only(self):
        table = ("## Keys\n\n| Key | Type | Meaning |\n|---|---|---|\n"
                 "| `sky_intensity` | 0 to 100 | see `latitude` in config.xml |\n\n## Next\n")
        self.assertEqual(keys_table(table), ["sky_intensity"])

    def test_a_destination_that_does_not_exist_is_caught(self):
        doc = "see [the note](../docs/missing-page.md) and [tailscale](https://tailscale.com)"
        targets = markdown_destinations(doc, ROOT / "themes")
        self.assertEqual(len(targets), 1, "only the relative link is a destination to check")
        self.assertFalse(targets[0].exists())

    def test_the_reference_constants_are_required(self):
        with self.assertRaises(AssertionError):
            reference_times("final class Sky { }")


if __name__ == "__main__":
    unittest.main()
