"""addons/README.md's Tested on column, held to the test record in the add-on's own README.

The column is one cell and the add-on's own page says the same thing in its own words, so the two
drift: `addons/README.md` asks an add-on to "Say which one in the README and in the table above", and
nothing read the pair. Haunted's row said "Not yet tested on a real TV" while
`addons/haunted/README.md` recorded a Xiaomi Mi TV 4A (MiTV-MOOQ1), Android 10 run on 29 Oct 2026 -
crossfade, bats, ghost, glow, clock, 3,021 frames, 0.4% janky. The add-on's page was written first
(3b401f46, "Not yet tested on a real TV" in the row) and 61db2cba wrote the test record into the
README without touching the row, so the page a reader scans before installing said the opposite of
the page it links to, and the row that invites a real-TV report could not be told apart from one
that already has one.

A cell written in prose cannot be generated from prose, so this file holds the one shape that cannot
be true both ways: a row saying "Not yet tested on a real TV" for an add-on whose README records a
test. The other direction is left alone - a row naming a unit its page does not - because some pages
keep the device in the table only (koi-pond/, neon-dream/), and the table then carries the record
rather than contradicting it.
"""
import re
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ADDONS = ROOT / "addons"
INDEX = ADDONS / "README.md"
RULES = ADDONS / "README.md"

# The cell this file exists for, lowercased: the words the row uses when no set has run it yet.
NOT_YET = "not yet tested on a real tv"

# The rule the column is written under, as addons/README.md states it.
RULE_SENTENCE = "Say which one in the README and in the table above."


def addon_dirs(addons=None):
    addons = Path(addons) if addons else ADDONS
    return sorted(p for p in addons.iterdir() if p.is_dir()) if addons.is_dir() else []


def index_rows(index=None):
    """addons/README.md's add-on table as {folder: [cells]}, in page order.

    A row is the line that links the folder (`| [Name](folder/) | ... |`); the header and the
    separator hold no such link, so neither is read as one.
    """
    index = Path(index) if index else INDEX
    rows = {}
    if not index.is_file():
        return rows
    for line in index.read_text(encoding="utf-8").splitlines():
        link = re.search(r"\]\(([a-z0-9][a-z0-9-]*)/\)", line)
        if line.startswith("|") and link:
            rows.setdefault(link.group(1), [cell.strip() for cell in line.strip().strip("|").split("|")])
    return rows


def tested_on_cell(cells):
    """The row's Tested on cell - the fourth of Add-on, What it does, Author, Tested on - or ''."""
    return cells[3] if len(cells) > 3 else ""


def recorded_test(folder):
    """The unit an add-on's README records under "Tested on", or None.

    Both shapes the pages use: a `- Tested on: ...` bullet (haunted/, digital-rain/, photo-prints/,
    tatami/) and a `## Tested on` heading followed by the run (sources/, weather/, ...). The
    first non-empty line after the heading is the record; a heading with nothing under it is not.
    """
    readme = Path(folder) / "README.md"
    if not readme.is_file():
        return None
    lines = readme.read_text(encoding="utf-8", errors="ignore").splitlines()
    for i, line in enumerate(lines):
        bullet = re.match(r"\s*[-*]\s*\**Tested on\**:\s*(\S.*)$", line, re.I)
        if bullet:
            return bullet.group(1).strip()
        if re.match(r"#{1,6}\s*Tested on\s*$", line.strip(), re.I):
            for following in lines[i + 1:]:
                if following.startswith("#"):
                    break
                if following.strip():
                    return following.strip()
            return None
    return None


def untested_claims(addons=None):
    """[(folder, row cell, recorded line)] for every row that says untested over a recorded test."""
    addons = Path(addons) if addons else ADDONS
    claims = []
    for name, cells in index_rows(addons / "README.md").items():
        column = tested_on_cell(cells)
        if NOT_YET not in column.lower():
            continue
        recorded = recorded_test(addons / name)
        if recorded:
            claims.append((name, column, recorded))
    return claims


def stand_in_tree(directory, rows):
    """An addons/ tree from {folder: (tested cell, README text or None)}."""
    addons = Path(directory) / "addons"
    addons.mkdir(parents=True, exist_ok=True)
    table = ["# Add-ons", "", "| Add-on | What it does | Author | Tested on | Permissions | Screenshot |",
             "|---|---|---|---|---|---|"]
    for folder, (tested, readme) in rows.items():
        (addons / folder).mkdir(parents=True, exist_ok=True)
        table.append(f"| [{folder.title()}]({folder}/) | a thing | an author | {tested} | None | "
                     f'<img src="{folder}/screenshot.jpg" width="240" alt="{folder.title()} on a TV"> |')
        if readme is not None:
            (addons / folder / "README.md").write_text(readme, encoding="utf-8")
    (addons / "README.md").write_text("\n".join(table) + "\n", encoding="utf-8")
    return addons


class TestedOnColumnTest(unittest.TestCase):
    def test_no_row_says_untested_over_a_test_its_readme_records(self):
        rows = index_rows()
        self.assertGreaterEqual(len(rows), 30, "the sweep must read the page's whole table")
        for name, column, recorded in untested_claims():
            self.fail(f"addons/README.md's {name}/ row says {column!r}, and addons/{name}/README.md "
                      f"records a test on {recorded!r}: name that unit in the row")

    def test_every_addon_folder_has_a_row(self):
        rows = index_rows()
        for folder in addon_dirs():
            self.assertIn(folder.name, rows, "addons/README.md needs a row for this folder")

    def test_a_test_recorded_as_a_bullet_is_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            addons = stand_in_tree(tmp, {
                "first": ("Not yet tested on a real TV",
                          "# First\n\n- Package: `com.example.first`\n"
                          "- Tested on: Xiaomi Mi TV 4A (MiTV-MOOQ1), Android 10, 29 Oct 2026: it runs\n"),
            })
            claims = untested_claims(addons)
            self.assertEqual([c[0] for c in claims], ["first"], claims)
            self.assertIn("MiTV-MOOQ1", claims[0][2])

    def test_a_test_recorded_under_a_heading_is_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            addons = stand_in_tree(tmp, {
                "second": ("Not yet tested on a real TV",
                           "# Second\n\n## Tested on\n\nMOKA R8G (Android 14), over ADB: the card and the screen.\n"),
            })
            self.assertEqual([c[0] for c in untested_claims(addons)], ["second"])

    def test_a_row_that_names_a_tv_is_left_alone(self):
        with tempfile.TemporaryDirectory() as tmp:
            addons = stand_in_tree(tmp, {
                "third": ("MOKA R8G (Android 14)", "# Third\n\n- Tested on: MOKA R8G (Android 14)\n"),
                "fourth": ("TCL 43P69K (Google TV 12)", None),
            })
            self.assertEqual(untested_claims(addons), [], "a row naming a unit is not a claim")

    def test_an_untested_row_with_no_record_is_left_alone(self):
        with tempfile.TemporaryDirectory() as tmp:
            addons = stand_in_tree(tmp, {
                "fifth": ("Not yet tested on a real TV", "# Fifth\n\nNothing was run on a set yet.\n"),
                "sixth": ("Not yet tested on a real TV", "# Sixth\n\n## Tested on\n\n## Change the look\n"),
            })
            self.assertEqual(untested_claims(addons), [], "no recorded unit means nothing to contradict")

    def test_the_page_still_asks_for_the_unit_in_both_places(self):
        text = RULES.read_text(encoding="utf-8") if RULES.is_file() else ""
        self.assertIn(RULE_SENTENCE, text, "addons/README.md's rule is what this column is written under")


if __name__ == "__main__":
    unittest.main()
