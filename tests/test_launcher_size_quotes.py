"""The launcher's size is stated fourteen times, and tools/readme_stats.py is the one place that draws it.

The size of the release APK is on the front page in three places per page - the press card's alt text
(`... · 572 KB launcher`), the Tools table's `launcher/build-and-install.sh` row ("Builds a shrunk release
(about 572 KB)") and the launcher section's opening sentence ("**about 572 KB**") - in each of the four
front pages, and once more in docs/launcher.md. The number the card is drawn with is the `("572 KB",
"LAUNCHER")` entry of STATS in tools/readme_stats.py, which `gen_tables.py --font` hands to
tools/readme_stats.py, and the number the APK may not pass is the CI gate's 589,824 B.

A size bump is a hand edit in six files at once: Round AK's 561 -> 572 KB (1a7cee7d, "the launcher is about
572 KB after Round AK (585,489 B)") changed README.md, README.de.md, README.nl.md, docs/launcher.md,
docs/workflows/translation.md and tools/readme_stats.py in one commit. Nothing reads those places against
each other or against STATS: `tools/gen_tables.py --check` compares the lines render() writes, and the
counts() rewrite covers the three numbers before the size ("N TV models · B brands · L languages · ") but
never the KB next to them, so a page left at the old size is green in CI and tells a reader a weight the
card beside it does not.

Three readers, one per place, no word table: a translation writes its own words ("about", "etwa",
"ongeveer", "близько") and its own unit (KB, КБ), so a page is read by the number in front of a size unit,
while a package name that ends in one (TCL-LA-RT41KB-S1, a table cell in README.nl.md) is not a size and a
size in a row for another script (a screenshot "under 300 KB") is not the launcher's.

docs/workflows/translation.md's size-gate step is the sixth file of that commit and repeats the number in
its own sentence ("`README.md` quotes the launcher at about 572 KB"), so it is read too - by the sentence
that calls it a quote, because the same page states the 576 KB gate and a scan of every size there would
take that one. `.github/maintainer/MAINTAINER.md` carries the number a seventh time and is the
maintainer's file, so it is not read.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

PAGES = ("README.md", "README.de.md", "README.nl.md", "README.uk.md")
GUIDE = "docs/launcher.md"
STATS = "tools/readme_stats.py"
GATE_RECIPE = "docs/workflows/translation.md"
BUILD_ROW = "launcher/build-and-install.sh"
PLACES = ("press card's alt text", f"{BUILD_ROW} row", "launcher section")

# A size: a number in front of KB or КБ, not the tail of a package name or a build number.
SIZE = re.compile(r"(?<![\w-])(\d+)\s?(?:KB|КБ)")
STATS_SIZE = re.compile(r'\("(\d+) KB", "LAUNCHER"\)')
BOLD_SIZE = re.compile(r"\*\*[^*\n]{0,40}?(\d+)\s?(?:KB|КБ)[^*\n]{0,8}?\*\*")
QUOTED_SIZE = re.compile(r"quotes[^\n]{0,80}?(\d+)\s?(?:KB|КБ)")


def read(name, root=ROOT):
    return (Path(root) / name).read_text(encoding="utf-8")


def card_size(text):
    """The size the press card's alt text states, or None when the page shows no card or no size in it."""
    for line in text.split("\n"):
        if "docs/card" in line and 'alt="' in line:
            alt = line.split('alt="', 1)[1].split('"', 1)[0]
            match = SIZE.search(alt)
            return int(match.group(1)) if match else None
    return None


def row_size(text):
    """The size the Tools table's row for launcher/build-and-install.sh states, or None."""
    for line in text.split("\n"):
        if line.startswith("|") and BUILD_ROW in line:
            match = SIZE.search(line)
            return int(match.group(1)) if match else None
    return None


def section_size(text):
    """The bolded size in the launcher section, or None. Every bolded size on the page is judged, so a
    second one cannot slip past: (first, count)."""
    found = [int(m.group(1)) for m in BOLD_SIZE.finditer(text)]
    return (found[0] if found else None), len(found)


def stats_size(text):
    """The size tools/readme_stats.py draws the card with, or None when STATS no longer states it."""
    match = STATS_SIZE.search(text)
    return int(match.group(1)) if match else None


def quoted_size(text):
    """The size docs/workflows/translation.md says README.md quotes, or None when it no longer says one.

    The same step states the 589,824 B (576 KB) gate, so every size on the page is not the launcher's: the
    one read is the number in the sentence that calls it a quote.
    """
    match = QUOTED_SIZE.search(text)
    return int(match.group(1)) if match else None


def problems(pages, size):
    """One line per page that does not state SIZE in all three places, or states another size there."""
    out = []
    for name, text in pages.items():
        card, row = card_size(text), row_size(text)
        bold, count = section_size(text)
        for place, value in zip(PLACES, (card, row, bold)):
            if value is None:
                out.append(f"{name} states no launcher size in its {place}")
            elif value != size:
                out.append(f"{name} states {value} KB in its {place} and tools/readme_stats.py draws "
                           f"the card with {size} KB")
        if count > 1:
            out.append(f"{name} bolds {count} sizes, so the launcher section's is not the only one")
    return out


def guide_problems(text, size):
    """What docs/launcher.md gets wrong about the size."""
    bold, count = section_size(text)
    if bold is None:
        return [f"{GUIDE} states no launcher size in bold"]
    if bold != size:
        return [f"{GUIDE} states {bold} KB and tools/readme_stats.py draws the card with {size} KB"]
    if count > 1:
        return [f"{GUIDE} bolds {count} sizes, so the launcher's is not the only one"]
    return []


def pages_with_a_card(root=ROOT):
    """Every front page that shows the press card, by name."""
    out = []
    for path in sorted(Path(root).glob("README*.md")):
        if card_size(read(path.name, root)) is not None:
            out.append(path.name)
    return out


class LauncherSizeQuotesTest(unittest.TestCase):
    def test_the_four_front_pages_state_the_size_their_card_is_drawn_with(self):
        size = stats_size(read(STATS))
        self.assertIsNotNone(size, f"{STATS} no longer states the launcher's size in STATS")
        found = problems({name: read(name) for name in PAGES}, size)
        self.assertFalse(found, "\n".join(found))

    def test_the_launcher_guide_states_the_same_size(self):
        size = stats_size(read(STATS))
        self.assertIsNotNone(size, f"{STATS} no longer states the launcher's size in STATS")
        found = guide_problems(read(GUIDE), size)
        self.assertFalse(found, "\n".join(found))

    def test_the_size_gate_recipe_quotes_the_same_size(self):
        size = stats_size(read(STATS))
        self.assertIsNotNone(size, f"{STATS} no longer states the launcher's size in STATS")
        quoted = quoted_size(read(GATE_RECIPE))
        self.assertIsNotNone(quoted, f"{GATE_RECIPE} no longer states the size it says README.md quotes")
        self.assertEqual(quoted, size,
                         f"{GATE_RECIPE} says README.md quotes {quoted} KB and tools/readme_stats.py draws "
                         f"the card with {size} KB")

    def test_the_readers_read_the_pages_that_show_the_card(self):
        self.assertEqual(pages_with_a_card(), sorted(PAGES),
                         "README*.md and the four front pages this file reads disagree on which pages "
                         "show the press card")
        for name in PAGES:
            text = read(name)
            self.assertIsNotNone(card_size(text), f"{name}: no card alt text size read")
            self.assertIsNotNone(row_size(text), f"{name}: no {BUILD_ROW} row size read")
            bold, count = section_size(text)
            self.assertEqual(count, 1, f"{name}: {count} bolded sizes read, expected one")


class ReaderTest(unittest.TestCase):
    """The three readers over pages written here, one shape each."""

    def test_the_readers_read_a_page_written_as_the_front_pages_write_it(self):
        page = ("<img src=\"docs/card-halloween.jpg\" alt=\"12 TV models · 3 brands · 4 languages · 572 KB "
                "launcher. Featured in ...\">\n\n"
                f"| [`{BUILD_ROW}`]({BUILD_ROW}) | Builds a shrunk release (about 572 KB), installs it. |\n\n"
                "A small native home screen: **about 572 KB**, plain Java.\n")
        self.assertEqual((card_size(page), row_size(page), section_size(page)), (572, 572, (572, 1)))
        self.assertFalse(problems({"page.md": page}, 572))

    def test_a_card_that_states_another_size_is_reported(self):
        page = ("<img src=\"docs/card.jpg\" alt=\"12 TV models · 3 brands · 4 languages · 561 KB launcher\">\n"
                f"| [`{BUILD_ROW}`]({BUILD_ROW}) | Builds a shrunk release (about 572 KB). |\n"
                "A small native home screen: **about 572 KB**, plain Java.\n")
        self.assertEqual(card_size(page), 561)
        found = problems({"page.md": page}, 572)
        self.assertEqual(len(found), 1, found)
        self.assertIn("page.md states 561 KB in its press card's alt text", found[0])

    def test_a_build_row_that_states_another_size_is_reported(self):
        page = ("<img src=\"docs/card.jpg\" alt=\"12 TV models · 4 languages · 572 KB launcher\">\n"
                f"| [`{BUILD_ROW}`]({BUILD_ROW}) | Builds a shrunk release (about 580 KB). |\n"
                "A small native home screen: **about 572 KB**, plain Java.\n")
        self.assertEqual([p for p in problems({"page.md": page}, 572) if "row" in p],
                         [f"page.md states 580 KB in its {BUILD_ROW} row and tools/readme_stats.py draws "
                          "the card with 572 KB"])

    def test_a_launcher_section_that_states_another_size_is_reported(self):
        page = ("<img src=\"docs/card.jpg\" alt=\"12 TV models · 4 languages · 572 KB launcher\">\n"
                f"| [`{BUILD_ROW}`]({BUILD_ROW}) | Builds a shrunk release (about 572 KB). |\n"
                "A small native home screen: **about 576 KB**, plain Java.\n")
        self.assertEqual([p for p in problems({"page.md": page}, 572) if "section" in p],
                         ["page.md states 576 KB in its launcher section and tools/readme_stats.py draws "
                          "the card with 572 KB"])

    def test_a_page_with_no_card_or_row_is_reported_rather_than_skipped(self):
        found = problems({"page.md": "A page about the launcher with no size at all.\n"}, 572)
        self.assertEqual(len(found), 3, found)
        self.assertIn(f"page.md states no launcher size in its press card's alt text", found[0])

    def test_a_build_row_for_another_script_is_not_read(self):
        page = (f"| [`tools/theme.sh`](tools/theme.sh) | Pushes a theme. Screenshots stay under 300 KB. |\n"
                f"| [`{BUILD_ROW}`]({BUILD_ROW}) | Builds a shrunk release (about 572 KB). |\n")
        self.assertEqual(row_size(page), 572)

    def test_a_package_name_that_ends_in_a_size_is_not_a_size(self):
        # README.nl.md carries TCL-LA-RT41KB-S1 in a TV cell; a bolded size is what the section reader takes.
        page = "| TCL 32S6500S | persist.tcl.client_type TCL-LA-RT41KB-S1 |\n"
        self.assertEqual(section_size(page), (None, 0))
        self.assertIsNone(SIZE.search("TCL-LA-RT41KB-S1"))

    def test_a_bold_phrase_with_no_size_is_not_read(self):
        page = "**The dock**, **about it**, **Рада бачити**\n"
        self.assertEqual(section_size(page), (None, 0))

    def test_two_bolded_sizes_are_reported(self):
        page = "**about 572 KB** and later **about 300 KB**\n"
        self.assertEqual(section_size(page), (572, 2))
        self.assertTrue(any("bolds 2 sizes" in p for p in problems({"page.md": page}, 572)))

    def test_the_ukrainian_unit_is_read(self):
        page = "**близько 572 КБ**, простий Java\n"
        self.assertEqual(section_size(page), (572, 1))

    def test_the_stats_reader_reports_a_tool_that_states_no_size(self):
        self.assertEqual(stats_size('STATS = [("1", "TV MODELS"), ("1", "BRANDS")]\n'), None)
        self.assertEqual(stats_size('STATS = [("1", "TV MODELS"), ("9 KB", "LAUNCHER")]\n'), 9)

    def test_the_gate_recipe_reader_takes_the_quoted_size_and_not_the_gate(self):
        text = ("   stay under 589,824 B (576 KB): `launcher/app/build/outputs/apk/release/app-release.apk`,\n"
                "   on every pull request. `README.md` quotes the launcher at about 572 KB, so a full\n"
                "   language can be the whole budget.\n")
        self.assertEqual(SIZE.findall(text), ["576", "572"])
        self.assertEqual(quoted_size(text), 572)

    def test_a_recipe_with_no_quote_sentence_is_reported(self):
        text = "   stay under 589,824 B (576 KB), measured on every pull request.\n"
        self.assertIsNone(quoted_size(text))


if __name__ == "__main__":
    unittest.main()
