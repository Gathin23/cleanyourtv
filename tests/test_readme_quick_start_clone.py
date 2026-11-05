"""The quick start's second step pasted PROMPT.md and never said to clone the repo first.

PROMPT.md is a prompt, not the repository. It sends the assistant to questions the repo answers:
`tools/tv.py` for the connection, `tools/disable.sh`, which writes the restore record and holds the
Home guard, `launcher/build-and-install.sh`, which builds the launcher and checks Home came back,
`examples/` for a tested list, `docs/` for the notes. Its own opening has one sentence about that -
"If you cloned this repo, mention that" - and nothing on any page said what to clone, so step 2 of
the quick start ("paste PROMPT.md into your assistant") was followed literally by a reader who had
only the web page. #1617 is that session on a TCL 50C755: the assistant answered that "the repo
tools you mention (tools/tv.py, tools/disable.sh, docs/, examples/) aren't in this folder" and said
it would write equivalent helper scripts itself, which is the one path the safety rules exist to
close - a hand-written disable loop has no restore record and no Home guard.

So every README clones first, in the same row that names the prompt, and say why on the line
under the table: the assistant reuses this repo's `tools/disable.sh` and `launcher/build-and-install.sh`
instead of writing its own. The paragraph names both scripts rather than describing them, so a reader
can see what the clone buys before they run anything.

PROMPT.md is the maintainer's file (AGENTS.md, and .github/maintainer/MAINTAINER.md holds it), so the
prompt-side half of #1617 - naming the repo inside the prompt itself - is left to them; this is the
installation half the reporter asked for, which is a README.

The owner/repo the clone URL must carry is read out of .github/ISSUE_TEMPLATE/config.yml instead of
being copied in, so a fork or a rename moves the URL with the page rather than leaving a dead command.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ISSUE_TEMPLATE = ROOT / ".github" / "ISSUE_TEMPLATE" / "config.yml"
PROMPT = ROOT / "PROMPT.md"
# `| first | second |`, the two-column shape the quick start uses.
ROW = re.compile(r"^\|\s*(.+?)\s*\|\s*(.+?)\s*\|\s*$")
SEPARATOR = re.compile(r"^[-: ]+$")
STEP_TWO = re.compile(r"^\**\s*2\.")


def pages():
    """The English README and every translation beside it."""
    return [ROOT / "README.md"] + sorted(ROOT.glob("README.*.md"))


def rows(text):
    """Table rows as (first cell, last cell), the separator row dropped."""
    found = []
    for line in text.splitlines():
        match = ROW.match(line.strip())
        if not match or SEPARATOR.match(match.group(1)):
            continue
        found.append((match.group(1), match.group(2)))
    return found


def prompt_rows(text):
    """The table rows that name PROMPT.md - the quick start's paste step is meant to be the only one."""
    return [(first, rest) for first, rest in rows(text) if "PROMPT.md" in rest]


def slug():
    """owner/repo, read from the issue template's links so a rename moves the clone URL too."""
    match = re.search(r"github\.com/([\w.-]+/[\w.-]+)/", ISSUE_TEMPLATE.read_text(encoding="utf-8"))
    return match.group(1) if match else ""


def clone_problems(text, owner_repo):
    """What is wrong with a page's paste step, as sentences; empty when the step clones first."""
    found = prompt_rows(text)
    if len(found) != 1:
        return [f"{len(found)} table rows name PROMPT.md: the quick start must have one paste step"]
    first, rest = found[0]
    problems = []
    if not STEP_TWO.match(first):
        problems.append(f"the row naming PROMPT.md reads {first!r}, not step 2")
    if "git clone" not in rest:
        problems.append("the row that pastes PROMPT.md does not clone the repo first")
    elif f"github.com/{owner_repo}" not in rest:
        problems.append(f"the clone command does not name github.com/{owner_repo}")
    elif rest.index("git clone") > rest.index("PROMPT.md"):
        problems.append("the clone command comes after PROMPT.md in the row")
    return problems


def note_after(text, needle):
    """The paragraph that follows the table row containing `needle`, wrapping joined, or ''."""
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.strip().startswith("|") and needle in line:
            rest = lines[index + 1:]
            break
    else:
        return ""
    while rest and (rest[0].strip().startswith("|") or not rest[0].strip()):
        rest = rest[1:]
    paragraph = []
    for line in rest:
        if not line.strip():
            break
        paragraph.append(line.strip())
    return " ".join(paragraph)


class TheCloneStep(unittest.TestCase):
    def test_the_slug_is_read_out_of_the_issue_template(self):
        self.assertTrue(ISSUE_TEMPLATE.is_file(), "no .github/ISSUE_TEMPLATE/config.yml to read the repo from")
        self.assertRegex(slug(), r"^[\w.-]+/[\w.-]+$",
                         "the issue template names no github.com/<owner>/<repo> URL")

    def test_seven_readmes_are_read(self):
        found = pages()
        self.assertEqual(7, len(found), f"expected README.md and six translations, read {[p.name for p in found]}")
        for page in found:
            self.assertGreaterEqual(len(rows(page.read_text(encoding="utf-8"))), 3,
                                    f"{page.name} parses as fewer than three table rows")

    def test_every_readme_clones_before_it_pastes_the_prompt(self):
        owner_repo = slug()
        for page in pages():
            with self.subTest(page=page.name):
                problems = clone_problems(page.read_text(encoding="utf-8"), owner_repo)
                self.assertEqual([], problems, f"{page.name}: {problems}")

    def test_the_line_under_the_table_says_what_the_clone_buys(self):
        """The two scripts the assistant would otherwise rewrite, named as paths so they are findable."""
        for page in pages():
            with self.subTest(page=page.name):
                note = note_after(page.read_text(encoding="utf-8"), "PROMPT.md")
                for path in ("tools/disable.sh", "launcher/build-and-install.sh"):
                    self.assertIn(path, note,
                                  f"{page.name}'s line under the quick start does not name {path}: {note!r}")

    def test_the_prompt_still_assumes_a_clone(self):
        """The page sends a reader to clone because the prompt's own opening asks about it."""
        self.assertIn("If you cloned this repo", PROMPT.read_text(encoding="utf-8"),
                      "PROMPT.md no longer mentions a clone, so the README's clone step has nothing behind it")


class TheReader(unittest.TestCase):
    """clone_problems() against pages written here, so a broken reader fails rather than the page."""

    GOOD = (
        "| Where | What to do |\n"
        "|---|---|\n"
        "| **1. On the TV** | USB debugging on. |\n"
        "| **2. On your computer** | Clone the repo (`git clone https://github.com/o/r`), then paste "
        "[`PROMPT.md`](PROMPT.md). |\n"
    )

    def test_a_page_that_clones_first_is_quiet(self):
        self.assertEqual([], clone_problems(self.GOOD, "o/r"))

    def test_a_row_without_the_clone_is_reported(self):
        text = self.GOOD.replace("Clone the repo (`git clone https://github.com/o/r`), then paste ",
                                 "Paste ")
        self.assertEqual(["the row that pastes PROMPT.md does not clone the repo first"],
                         clone_problems(text, "o/r"))

    def test_a_clone_after_the_prompt_is_reported(self):
        text = self.GOOD.replace(
            "Clone the repo (`git clone https://github.com/o/r`), then paste [`PROMPT.md`](PROMPT.md).",
            "Paste [`PROMPT.md`](PROMPT.md) from `git clone https://github.com/o/r`.")
        self.assertEqual(["the clone command comes after PROMPT.md in the row"], clone_problems(text, "o/r"))

    def test_another_repository_is_reported(self):
        text = self.GOOD.replace("github.com/o/r", "github.com/someone/fork")
        self.assertEqual(["the clone command does not name github.com/o/r"], clone_problems(text, "o/r"))

    def test_the_step_number_is_read(self):
        text = self.GOOD.replace("**2. On your computer**", "**4. Later**")
        self.assertEqual(["the row naming PROMPT.md reads '**4. Later**', not step 2"],
                         clone_problems(text, "o/r"))

    def test_two_rows_naming_the_prompt_are_reported(self):
        text = self.GOOD + "| **3. Later** | Paste [`PROMPT.md`](PROMPT.md) again. |\n"
        self.assertEqual(["2 table rows name PROMPT.md: the quick start must have one paste step"],
                         clone_problems(text, "o/r"))

    def test_a_clone_in_prose_is_not_the_step(self):
        """A page that clones in a paragraph and pastes in a row with no clone is still a page that pastes first."""
        text = "`git clone https://github.com/o/r`\n\n" + self.GOOD.replace(
            "Clone the repo (`git clone https://github.com/o/r`), then paste ", "Paste ")
        self.assertEqual(["the row that pastes PROMPT.md does not clone the repo first"],
                         clone_problems(text, "o/r"))

    def test_the_separator_row_is_not_a_step(self):
        self.assertEqual([], clone_problems(self.GOOD, "o/r"))
        self.assertEqual(3, len(rows(self.GOOD)), "the header and two steps are three rows; the separator is not one")


class TheNoteReader(unittest.TestCase):
    """note_after() finds the paragraph under the table, not a mention anywhere on the page."""

    TABLE = "| 1. On the TV | x |\n| 2. On your computer | paste [`PROMPT.md`](PROMPT.md) |\n"

    def test_a_wrapped_paragraph_is_joined(self):
        text = self.TABLE + "\nCloning matters: `tools/disable.sh`\nand `launcher/build-and-install.sh`.\n"
        note = note_after(text, "PROMPT.md")
        self.assertIn("`tools/disable.sh` and `launcher/build-and-install.sh`", note)

    def test_a_mention_further_down_the_page_is_not_the_note(self):
        text = self.TABLE + "\nSomething else.\n\n## Later\n\n`tools/disable.sh` is described here.\n"
        self.assertEqual("Something else.", note_after(text, "PROMPT.md"))
        self.assertNotIn("tools/disable.sh", note_after(text, "PROMPT.md"))

    def test_a_page_without_the_row_has_no_note(self):
        self.assertEqual("", note_after("| 1. On the TV | x |\n", "PROMPT.md"))


if __name__ == "__main__":
    unittest.main()
