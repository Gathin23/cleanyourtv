"""Checks that an example folder's README says how many packages its debloat.txt disables.

A folder README answers "what is in that file?" with a number, and that number is the only place a
reader learns the size of the list before opening it. It states it in one of three ways:

    | `debloat.txt` | The 30 packages that were disabled, grouped and commented |
    The [debloat list](debloat.txt) disables 71 packages: everything the owner does not use
    1. **Debloated 23 packages** - ad services, app stores, background junk

Nothing tied any of them to the file, so adding packages to a list leaves the README describing a list
that no longer exists. Only the table row was read at first; the prose forms are read now, because a
count restated in a sentence drifts the same way - two folders' lists had shrunk under theirs.

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
# `The [debloat list](debloat.txt) disables 71 packages: everything the owner does not use` - the
# number has to stay in the same sentence as the link, so the gap is short.
PROSE_LINK = re.compile(r"\[[^\]]*\]\([^)]*debloat\.txt[^)]*\)[^\n]{0,80}?(\d+)\s+packages\b")
# `1. **Debloated 23 packages** - ad services, app stores, background junk`
PROSE_STEP = re.compile(r"\*\*[Dd]ebloated (\d+) packages\*\*")
PROSE = (
    (PROSE_LINK, "the sentence about the debloat list"),
    (PROSE_STEP, "the step that says what was done"),
)
ROW = "the debloat.txt row"


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


def claims(readme_text):
    """Every package count the README states for its debloat.txt, as (where it says it, number).

    A line that is the table row is not searched for the prose forms as well: a row that names the
    file and repeats the number in a link would otherwise be the same claim read twice.
    """
    found = []
    for line in readme_text.split("\n"):
        row = LIST_ROW.match(line)
        if row:
            count = COUNT.search(row.group(1))
            if count:
                found.append((ROW, int(count.group(1))))
                continue
        for pattern, where in PROSE:
            for match in pattern.finditer(line):
                found.append((where, int(match.group(1))))
    return found


def folders():
    return sorted(p for p in EXAMPLES.iterdir() if p.is_dir() and (p / "debloat.txt").is_file())


class ExampleListCountTest(unittest.TestCase):
    def test_readme_count_matches_the_list(self):
        checked = 0
        for folder in folders():
            readme = folder / "README.md"
            if not readme.is_file():
                continue
            stated = claims(readme.read_text(encoding="utf-8"))
            if not stated:
                continue
            checked += 1
            count = len(packages(folder / "debloat.txt"))
            for where, claim in stated:
                with self.subTest(folder=folder.name, where=where):
                    self.assertEqual(
                        claim, count,
                        f"examples/{folder.name}/README.md says {claim} packages in {where} and "
                        f"its debloat.txt carries {count}",
                    )
        # The rule is only worth having while folders still state a count; a sweep that read
        # nothing must not pass.
        self.assertGreaterEqual(checked, 8, "no example README states a package count any more")

    def test_a_readme_states_its_count_in_prose(self):
        # The prose forms are what this check grew for. A sweep in which no README states its count
        # that way would leave them unexercised against the tree, and the next stale one unnoticed.
        prose = [
            folder.name
            for folder in folders()
            if (folder / "README.md").is_file()
            and any(
                where != ROW
                for where, _ in claims((folder / "README.md").read_text(encoding="utf-8"))
            )
        ]
        self.assertTrue(prose, "no example README states its package count in prose any more")


class CountReaderTest(unittest.TestCase):
    """The readers above, against documents written here."""

    def test_row_with_a_trailing_description(self):
        text = "| `debloat.txt` | The 12 packages that were disabled, grouped and commented |\n"
        self.assertEqual(claims(text), [(ROW, 12)])

    def test_row_without_a_number(self):
        text = "| `debloat.txt` | The packages that were disabled, grouped and commented |\n"
        self.assertEqual(claims(text), [])

    def test_row_for_another_file_is_ignored(self):
        text = (
            "| File | What it is |\n"
            "|---|---|\n"
            "| `config.json` | The 4 apps in the dock |\n"
            "| `README.md` | 9 packages explained |\n"
        )
        self.assertEqual(claims(text), [])

    def test_number_after_prose_in_the_row_is_found(self):
        text = "| `debloat.txt` | Disabled here: the 45 packages, grouped and commented |\n"
        self.assertEqual(claims(text), [(ROW, 45)])

    def test_a_sentence_about_the_list_is_read(self):
        text = (
            "The [debloat list](debloat.txt) disables 71 packages: everything the owner does\n"
            "not use, since they install their own apps from Play Store afterwards.\n"
        )
        self.assertEqual(claims(text), [("the sentence about the debloat list", 71)])

    def test_a_step_that_says_what_was_done_is_read(self):
        text = "1. **Debloated 23 packages** - ad services, app stores, background junk\n"
        self.assertEqual(claims(text), [("the step that says what was done", 23)])

    def test_a_batch_size_is_not_a_claim_about_the_list(self):
        # "The first 35 packages were disabled and survived a reboot" is a session's account of the
        # order it went in, not the size of the file; reading it would fail a correct README.
        text = (
            "**How much testing is behind it:** one set. The first 35 packages were disabled and\n"
            "survived a reboot; the rest went in afterwards, so 37 packages\n"
        )
        self.assertEqual(claims(text), [])

    def test_a_table_row_is_not_read_twice_as_prose(self):
        text = "| `debloat.txt` | The 12 packages: see [the list](debloat.txt) for the 12 packages |\n"
        self.assertEqual(claims(text), [(ROW, 12)])

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
        self.assertEqual(claims(text), [(ROW, 6)])


if __name__ == "__main__":
    unittest.main()
