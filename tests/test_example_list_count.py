"""Checks that an example folder's README says how many packages its debloat.txt disables.

A folder README opens with a table whose `debloat.txt` row answers "what is in that file?" with
a number ("The 30 packages that were disabled, grouped and commented"). That number is the only
place a reader learns the size of the list before opening it, and nothing tied it to the file, so
adding packages to a list leaves the README describing a list that no longer exists.

The number is compared with the packages tools/disable.sh would actually send, parsed the way that
script parses a list (a trailing `# comment` is not part of the line, whole-line `#` comments and
blank lines are skipped, the first field is the package name). Nothing is copied in: the claim comes
out of the README and the count out of the list.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"
TRAILING_COMMENT = re.compile(r"\s+#.*$")
# `| `debloat.txt` | The 30 packages that were disabled, grouped and commented |`
LIST_ROW = re.compile(r"^\|\s*`debloat\.txt`\s*\|(.*)$")
COUNT = re.compile(r"(\d+)\s+packages\b")


def packages(list_path):
    """The package names disable.sh would send for this list."""
    text = list_path.read_text(encoding="utf-8")
    if text.startswith("\ufeff"):
        text = text[1:]
    names = []
    for line in text.split("\n"):
        line = TRAILING_COMMENT.sub("", line.rstrip("\r"))
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        names.append(line.split()[0])
    return names


def claimed_count(readme_text):
    """The number the README's debloat.txt row gives, or None if the row carries none."""
    for line in readme_text.split("\n"):
        row = LIST_ROW.match(line)
        if not row:
            continue
        found = COUNT.search(row.group(1))
        if found:
            return int(found.group(1))
    return None


def folders():
    return sorted(p for p in EXAMPLES.iterdir() if p.is_dir() and (p / "debloat.txt").is_file())


class ExampleListCountTest(unittest.TestCase):
    def test_readme_count_matches_the_list(self):
        checked = 0
        for folder in folders():
            readme = folder / "README.md"
            if not readme.is_file():
                continue
            claim = claimed_count(readme.read_text(encoding="utf-8"))
            if claim is None:
                continue
            checked += 1
            count = len(packages(folder / "debloat.txt"))
            with self.subTest(folder=folder.name):
                self.assertEqual(
                    claim, count,
                    f"examples/{folder.name}/README.md says {claim} packages and its debloat.txt "
                    f"carries {count}",
                )
        # The rule is only worth having while folders still state a count; a sweep that read
        # nothing must not pass.
        self.assertGreaterEqual(checked, 8, "no example README states a package count any more")


class CountReaderTest(unittest.TestCase):
    """The readers above, against documents written here."""

    def test_row_with_a_trailing_description(self):
        text = "| `debloat.txt` | The 12 packages that were disabled, grouped and commented |\n"
        self.assertEqual(claimed_count(text), 12)

    def test_row_without_a_number(self):
        text = "| `debloat.txt` | The packages that were disabled, grouped and commented |\n"
        self.assertIsNone(claimed_count(text))

    def test_row_for_another_file_is_ignored(self):
        text = (
            "| File | What it is |\n"
            "|---|---|\n"
            "| `config.json` | The 4 apps in the dock |\n"
            "| `README.md` | 9 packages explained |\n"
        )
        self.assertIsNone(claimed_count(text))

    def test_number_after_prose_in_the_row_is_found(self):
        text = "| `debloat.txt` | Disabled here: the 45 packages, grouped and commented |\n"
        self.assertEqual(claimed_count(text), 45)

    def test_comments_and_blank_lines_are_not_packages(self):
        import tempfile

        text = (
            "# a header\n"
            "\n"
            "com.example.one    # why\n"
            "   \n"
            "com.example.two\n"
            "# com.example.commented\n"
            "com.example.three\t# trailing tab comment\n"
        )
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "debloat.txt"
            path.write_text(text, encoding="utf-8")
            self.assertEqual(
                packages(path), ["com.example.one", "com.example.two", "com.example.three"]
            )

    def test_a_hash_inside_a_comment_is_not_a_second_comment(self):
        import tempfile

        text = "com.example.one  # see #884 and the note #12\ncom.example.two\n"
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "debloat.txt"
            path.write_text(text, encoding="utf-8")
            self.assertEqual(packages(path), ["com.example.one", "com.example.two"])

    def test_the_row_is_found_among_other_table_rows(self):
        text = (
            "# A folder README\n\n"
            "| File | What it is |\n"
            "|---|---|\n"
            "| `config.json` | Dock order and columns |\n"
            "| `debloat.txt` | The 6 packages that were disabled |\n"
            "| `README.md` | this page |\n"
        )
        self.assertEqual(claimed_count(text), 6)


if __name__ == "__main__":
    unittest.main()
