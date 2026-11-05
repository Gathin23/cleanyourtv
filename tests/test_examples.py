"""Checks every TV list in examples/ and the two tables that index them.

The tables are generated from each folder's tv.json by tools/gen_tables.py. Here only the tv.json files
are checked; that the committed tables and counts are what the script writes is CI's "README tables" job,
on main only, kept apart so a list merge waiting for its regenerated tables doesn't turn these tests red.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import gen_tables  # noqa: E402  (needs tools/ on the path first)

EXAMPLES = ROOT / "examples"
PACKAGE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
# A line that is only a commented-out package, with or without a note after it: how a list keeps one
# ("# com.google.android.videos   # firmware re-enables it on every boot"). Prose that merely starts
# with a package name ("# com.x also answers HOME") is not one.
COMMENTED_OUT = re.compile(r"^\s*#\s*([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)\s*(?:#.*)?$")


def folders():
    return sorted(p for p in EXAMPLES.iterdir() if p.is_dir())


def list_files(examples):
    """Every list under examples: each folder's debloat.txt and a second list beside it, such as
    google-chromecast-gtv-4k/debloat-keep-cast.txt, which tools/disable.sh applies the same way."""
    return sorted(examples.glob("*/debloat*.txt"))


def commented_out_but_listed(lines):
    """(line number, package) for each package commented out below the first package line that an
    active line still names.

    tools/disable.sh strips everything from the first `#`, so the active line wins: the package is
    disabled whatever the commented-out entry says about keeping it (examples/tcl-43p615 disabled
    YouTube while noting that the owner keeps it). A header may repeat active entries on purpose, as a
    record of what was applied (examples/amazon-fire-tv-stick-4k-2023), so lines above the first
    package line are left out.
    """
    active = set()
    first = None
    for n, line in enumerate(lines, 1):
        body = line.split("#", 1)[0].strip()
        if body:
            active.add(body)
            first = first or n
    if first is None:
        return []
    found = []
    for n, line in enumerate(lines[first:], first + 1):
        match = COMMENTED_OUT.match(line)
        if match and match.group(1) in active:
            found.append((n, match.group(1)))
    return found


def format_problems(examples):
    """`list: what` for every way a list under examples breaks the list format, debloat.txt and a
    second list beside it alike."""
    out = []
    for path in list_files(examples):
        own = path.relative_to(examples).as_posix()
        lines = path.read_text(encoding="utf-8").split("\n")
        if not lines[0].startswith("#"):
            out.append(f"{own}: first line should be a comment naming the TV, Android version and build")
        packages = []
        for n, line in enumerate(lines, 1):
            body = line.split("#", 1)[0].strip()
            if not body:
                continue
            if not PACKAGE.search(body):
                out.append(f"{own}: line {n}: not a package name: {line!r}")
                continue
            packages.append(body)
        if not packages:
            out.append(f"{own}: no packages: notes without a list belong in docs/devices/")
        dupes = sorted({p for p in packages if packages.count(p) > 1})
        if dupes:
            out.append(f"{own}: listed twice: {dupes}")
        kept = [f"line {n}: {p}" for n, p in commented_out_but_listed(lines)]
        if kept:
            out.append(f"{own}: commented out to keep it, but an active line still disables it: {kept}")
    return out


def table_rows(path, header_prefix):
    """Rows of the first markdown table whose header starts with header_prefix."""
    lines = path.read_text(encoding="utf-8").split("\n")
    start = next(i for i, l in enumerate(lines) if l.startswith(header_prefix))
    rows = []
    for line in lines[start + 2:]:
        if not line.startswith("|"):
            break
        rows.append(line)
    return rows


class ExampleListsTest(unittest.TestCase):
    def test_every_folder_has_a_list(self):
        for folder in folders():
            with self.subTest(folder=folder.name):
                self.assertTrue((folder / "debloat.txt").is_file(), "no debloat.txt")

    def test_list_format(self):
        problems = format_problems(EXAMPLES)
        self.assertEqual([], problems, "\n".join(problems))

    def test_every_folder_has_a_valid_tv_json(self):
        try:
            gen_tables.load(ROOT)
        except gen_tables.Problem as e:
            self.fail(str(e))

    def test_readme_table_has_one_row_per_list(self):
        readme_rows = table_rows(ROOT / "README.md", "| TV | OS |")
        example_rows = table_rows(EXAMPLES / "README.md", "| Folder |")
        self.assertEqual(len(readme_rows), len(example_rows),
                         "README.md TV table and examples/README.md have a different number of rows")
        readme_tvs = sorted(r.split("|")[1].strip() for r in readme_rows)
        example_tvs = sorted(r.split("|")[2].strip() for r in example_rows)
        self.assertEqual(readme_tvs, example_tvs, "the TV column differs between the two tables")


class CommentedOutReaderTest(unittest.TestCase):
    def test_a_kept_entry_that_is_still_active_is_reported(self):
        lines = ["# TV", "com.a.b      # app", "com.c.d",
                 "# com.a.b    # came back enabled; the owner keeps it"]
        self.assertEqual(commented_out_but_listed(lines), [(4, "com.a.b")])

    def test_a_kept_entry_that_is_not_active_is_fine(self):
        lines = ["# TV", "com.c.d", "# com.a.b    # firmware re-enables it on every boot"]
        self.assertEqual(commented_out_but_listed(lines), [])

    def test_a_header_record_of_active_entries_is_fine(self):
        lines = ["# TV", "# Applied on this unit:", "# com.a.b", "", "com.a.b"]
        self.assertEqual(commented_out_but_listed(lines), [])

    def test_prose_that_starts_with_a_package_name_is_not_an_entry(self):
        lines = ["# TV", "com.a.b", "# com.a.b also answers HOME, so it goes last"]
        self.assertEqual(commented_out_but_listed(lines), [])


if __name__ == "__main__":
    unittest.main()
