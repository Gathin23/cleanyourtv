"""The README's Tools table lists every tool that ships in tools/.

AGENTS.md asks a new tool to get its row "where the others are", and that table is where a reader of
the front page finds a tool at all. Two tools were missing from it: tools/bench.py, the benchmark
that AGENTS.md and tools/trace.md already send contributors to for key latency, jank and cold start
numbers, and tools/adb-closed.py, which the closing step in the README quoted inline and nowhere
else. Nobody notices a missing row, so this test reads the table the way a reader does.

Three kinds of file are not expected in the table, and none is a judgement call about the tool:

  * README_STATS, README_FEATURED and README_CARD draw the README's card image in CI, not something
    a reader runs; the card's alt text is what points at them.
  * SCENES_WALLPAPERS draws the wallpaper of themes/scenes and is linked from themes/README.md,
    which is the only place a theme author needs it.
  * GEN_TABLES writes the README's TV tables and brand line after a merge, a maintainer's step; the
    markers around those tables name it, and so do CONTRIBUTING.md and MAINTAINER.md.

A PowerShell twin (.ps1) is listed by the row of its .sh script, as the table's theme.sh row already
serves theme.ps1. Its suffix is trimmed before the lookup, so a twin without its script is reported.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS_DIR = ROOT / "tools"
README = ROOT / "README.md"

TABLE_HEADER = "| Tool | What it does |"
TOOL_PATH = re.compile(r"tools/([\w.-]+\.(?:py|sh|ps1))")

README_STATS = "readme_stats.py"        # the three that draw docs/card.jpg
README_FEATURED = "readme_featured.py"
README_CARD = "readme_card.py"
SCENES_WALLPAPERS = "scenes_wallpapers.py"   # themes/scenes/wallpapers, linked from themes/README.md
GEN_TABLES = "gen_tables.py"            # the TV tables, from examples/*/tv.json
NOT_IN_TABLE = {README_STATS, README_FEATURED, README_CARD, SCENES_WALLPAPERS, GEN_TABLES}


def table_rows(text):
    """The Tools table's body rows, as lines. An empty list when the table is not there."""
    lines = text.splitlines()
    start = None
    for i, line in enumerate(lines):
        if line.strip() == TABLE_HEADER:
            start = i
            break
    if start is None:
        return []
    rows = []
    for line in lines[start + 2:]:      # skip the header and its |---|---| separator
        if not line.startswith("|"):
            break
        rows.append(line)
    return rows


def first_cell(row):
    """What the table's first cell says, without the pipes."""
    return row.strip().strip("|").split("|")[0].strip()


def listed(rows):
    """The tool file names the table names, wherever in the first cell they appear."""
    names = set()
    for row in rows:
        names.update(TOOL_PATH.findall(first_cell(row)))
    return names


def unlisted(files, named, skip=NOT_IN_TABLE):
    """The files in tools/ that no row names, so a reader cannot find them."""
    missing = []
    for name in sorted(files):
        if name in named or name in skip:
            continue
        if name.endswith(".ps1") and name[:-len(".ps1")] + ".sh" in named:
            continue
        missing.append(name)
    return missing


def tool_files():
    return {p.name for p in TOOLS_DIR.iterdir() if p.suffix in {".py", ".sh", ".ps1"}}


SAMPLE = """\
## Doing it by hand

| Tool | What it does |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Shell on the TV. |
| [`tools/disable.sh`](tools/disable.sh) | Apply a list. |
| [`tools/bench.py`](tools/bench.py) | Key latency. |

The scripts never leave the TV without a Home app.
"""


class ToolsTableTest(unittest.TestCase):
    def setUp(self):
        self.rows = table_rows(README.read_text(encoding="utf-8"))

    def test_the_readme_has_a_tools_table_to_read(self):
        # Without this, a renamed heading would report every tool as missing (or none as checked).
        self.assertGreaterEqual(len(self.rows), 8, "the README's Tools table was not found")

    def test_every_tool_has_a_row(self):
        missing = unlisted(tool_files(), listed(self.rows))
        self.assertEqual(missing, [], "tools a reader cannot find from the README's Tools table: "
                                      + ", ".join(f"tools/{name}" for name in missing))

    def test_no_row_names_a_tool_that_is_not_there(self):
        for name in sorted(listed(self.rows)):
            self.assertTrue((TOOLS_DIR / name).exists(), f"the table links tools/{name}, which is gone")


class CheckerTest(unittest.TestCase):
    """The checks above have to be able to fail, on a table small enough to read."""

    def setUp(self):
        self.rows = table_rows(SAMPLE)

    def test_the_sample_table_is_read(self):
        self.assertEqual(len(self.rows), 3)
        self.assertEqual(listed(self.rows), {"tv.py", "disable.sh", "bench.py"})

    def test_a_file_with_no_row_is_reported(self):
        self.assertEqual(unlisted({"tv.py", "adb-closed.py"}, listed(self.rows)), ["adb-closed.py"])

    def test_a_listed_file_is_not_reported(self):
        self.assertEqual(unlisted({"tv.py", "bench.py"}, listed(self.rows)), [])

    def test_a_twin_is_covered_by_its_script(self):
        self.assertEqual(unlisted({"disable.ps1"}, listed(self.rows)), [])
        self.assertEqual(unlisted({"theme.ps1"}, listed(self.rows)), ["theme.ps1"])

    def test_a_card_renderer_or_the_wallpaper_script_is_not_reported(self):
        self.assertEqual(unlisted({README_CARD, SCENES_WALLPAPERS}, listed(self.rows)), [])

    def test_a_table_that_is_not_there_reads_as_empty(self):
        self.assertEqual(table_rows("## Tools\n\nNone yet.\n"), [])
        self.assertEqual(unlisted({"tv.py"}, listed([])), ["tv.py"])

    def test_the_row_parsing_keeps_a_second_link(self):
        row = "| [`tools/check.py`](tools/check.py), [`recovery.py`](tools/recovery.py) | x |"
        self.assertEqual(listed([row]), {"check.py", "recovery.py"})
        self.assertEqual(first_cell(row),
                         "[`tools/check.py`](tools/check.py), [`recovery.py`](tools/recovery.py)")


if __name__ == "__main__":
    unittest.main()
