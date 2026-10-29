"""Checks every TV list in examples/ and the two tables that index them.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"
PACKAGE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")


def folders():
    return sorted(p for p in EXAMPLES.iterdir() if p.is_dir())


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
        for folder in folders():
            path = folder / "debloat.txt"
            if not path.is_file():
                continue
            with self.subTest(folder=folder.name):
                lines = path.read_text(encoding="utf-8").split("\n")
                self.assertTrue(lines and lines[0].startswith("#"),
                                "first line should be a comment naming the TV, Android version and build")
                packages = []
                for n, line in enumerate(lines, 1):
                    body = line.split("#", 1)[0].strip()
                    if not body:
                        continue
                    self.assertRegex(body, PACKAGE, f"line {n}: not a package name: {line!r}")
                    packages.append(body)
                self.assertTrue(packages, "no packages: notes without a list belong in docs/devices/")
                dupes = sorted({p for p in packages if packages.count(p) > 1})
                self.assertFalse(dupes, f"listed twice: {dupes}")

    def test_examples_table_matches_folders(self):
        rows = table_rows(EXAMPLES / "README.md", "| Folder |")
        listed = []
        for row in rows:
            m = re.match(r"\|\s*`([^`/]+)/`\s*\|", row)
            self.assertIsNotNone(m, f"row does not start with a `folder/` cell: {row[:60]}")
            listed.append(m.group(1))
        names = [f.name for f in folders()]
        self.assertEqual(sorted(listed), sorted(names),
                         "examples/README.md rows and examples/ folders differ")
        self.assertEqual(len(listed), len(set(listed)), "a folder is listed twice")

    def test_readme_table_has_one_row_per_list(self):
        readme_rows = table_rows(ROOT / "README.md", "| TV | OS |")
        example_rows = table_rows(EXAMPLES / "README.md", "| Folder |")
        self.assertEqual(len(readme_rows), len(example_rows),
                         "README.md TV table and examples/README.md have a different number of rows")
        readme_tvs = sorted(r.split("|")[1].strip() for r in readme_rows)
        example_tvs = sorted(r.split("|")[2].strip() for r in example_rows)
        self.assertEqual(readme_tvs, example_tvs, "the TV column differs between the two tables")

    @unittest.skipUnless(os.environ.get("CLEANYOURTV_STRICT"), "counts are fixed up on merge; strict on main only")
    def test_readme_counts(self):
        text = (ROOT / "README.md").read_text(encoding="utf-8")
        n = len(folders())
        self.assertIn(f"{n} TV models", text, "stat strip alt text is stale (regenerate docs/stats.png too)")


if __name__ == "__main__":
    unittest.main()
