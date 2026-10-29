"""docs/workflows/showcase.md asks for the two count lines `tests/test_showcase_project_count.py` refuses a row without.

The recipe is the page AGENTS.md's workflow list and CONTRIBUTING's routing table send a new showcase
author to, and its numbered steps are the checklist they follow. Step 1 asked for the row, its section and
its brand index entry and stopped there, while the check that runs on every PR counts the rows and fails
when the page's `N projects so far.` line and README's spelled form are left behind - so a project added by
following the page exactly landed a red suite. Reproduced on the base commit by adding one row the way step
1 reads:

    AssertionError: "SHOWCASE.md says 81 projects and its tables hold 82 rows: bump the number (and
    README.md's spelled one) when you add a project"
    AssertionError: 81 != 82 : README.md says 'eighty-one projects' and SHOWCASE.md holds 82 rows

Nothing is copied in here. The phrase the page's count line must carry is read out of the check's own
stated_number(), and the two numbers are proved by running that check over pages written in the test, so a
checker that stops counting fails this pair instead of leaving the recipe stale.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
RECIPE = ROOT / "docs" / "workflows" / "showcase.md"
CHECK = ROOT / "tests" / "test_showcase_project_count.py"

spec = importlib.util.spec_from_file_location("cyt_showcase_count", CHECK)
if spec is None or spec.loader is None:  # pragma: no cover - the file is in the tree
    raise RuntimeError(f"cannot load {CHECK}")
count = importlib.util.module_from_spec(spec)
spec.loader.exec_module(count)

# One project row as the page lays it out: a "|" row whose first cell is a link.
ROW = "| [Project](https://example.com/p) | A fork of the launcher | TCL G08, Android TV 11 | [me](https://example.com) |"


def steps(body=None):
    """(number, text) for every numbered step, continuation lines joined into the step they belong to."""
    body = RECIPE.read_text(encoding="utf-8") if body is None else body
    out, current = [], None
    for line in body.split("\n"):
        head = re.match(r"^(\d+)\.\s+(.*)$", line)
        if head:
            current = [head.group(2)]
            out.append((int(head.group(1)), current))
        elif not line.strip():
            current = None
        elif current is not None and line[:1] in (" ", "\t"):
            current.append(line.strip())
        else:
            current = None
    return [(number, " ".join(parts)) for number, parts in out]


def the_count_step():
    """The step that sends the author to README.md, i.e. the one that carries the second number."""
    return [text for _, text in steps() if "README.md" in text]


def count_line_phrase():
    """The words the page's count line must carry, taken from the check that reads it."""
    match = re.search(r"projects so far", CHECK.read_text(encoding="utf-8"))
    return match.group(0) if match else None


def page(stated, rows):
    """A SHOWCASE.md holding `rows` project rows and stating `stated`, laid out as the page is."""
    return [f"{stated} projects so far.", "", "## Forks of the launcher", "",
            "| Project | What it is | Built on | By |", "|---|---|---|---|"] + [ROW] * rows


class ShowcaseRecipeCountTest(unittest.TestCase):
    def test_the_row_step_asks_for_both_counts(self):
        found = the_count_step()
        self.assertEqual(len(found), 1, f"expected one step naming README.md, got {len(found)}")
        self.assertIn("SHOWCASE.md", found[0],
                      "the step that asks for the count should name SHOWCASE.md's line as well as README.md's")

    def test_the_step_carries_the_count_line_the_check_reads(self):
        phrase = count_line_phrase()
        self.assertIsNotNone(phrase, "tests/test_showcase_project_count.py no longer reads a 'projects so far' line")
        self.assertIn(phrase, the_count_step()[0],
                      f"the step should quote the line the check reads, {phrase!r}")

    def test_the_step_names_the_check_that_refuses_it(self):
        self.assertIn("tests/test_showcase_project_count.py", the_count_step()[0],
                      "the step should name the check, so a contributor knows why the numbers are not optional")


class TheCheckStillAsksTest(unittest.TestCase):
    """Pins the enforcer, not a wording: if it stops counting, the pair fails instead of letting the recipe pass."""

    def test_a_row_without_the_bump_is_refused(self):
        _, counted, error = count.audit(page(50, 51))
        self.assertEqual(counted, 51)
        self.assertIsNotNone(error, "a page stating one project fewer than it holds was accepted")
        self.assertIn("bump the number", error)
        self.assertIn("README.md", error, "the refusal should still send the author to README's spelled number")

    def test_an_agreeing_page_is_quiet(self):
        stated, counted, error = count.audit(page(50, 50))
        self.assertIsNone(error, error)
        self.assertEqual((stated, counted), (50, 50))

    def test_readme_spelled_number_is_read(self):
        line = ("| **Built something on top?** | add it to [SHOWCASE.md](SHOWCASE.md), where seventy-five "
                "projects already live |")
        self.assertEqual(count.readme_spelled([line]), (75, "seventy-five"))
        self.assertEqual(count.readme_spelled(["add your project to SHOWCASE.md"]), (None, None),
                         "a line without a spelled number read as one")


class StepReaderTest(unittest.TestCase):
    """The reader above decides what counts as a step; a silent change there would hide a missing one."""

    def test_it_reads_every_step(self):
        found = steps()
        self.assertGreaterEqual(len(found), 4, "the recipe lost a step")
        self.assertEqual([n for n, _ in found], list(range(1, len(found) + 1)),
                         "the steps are not numbered 1..N")

    def test_a_wrapped_step_is_one_step(self):
        body = ("1. First step that wraps\n   onto a second line.\n2. Second.\n"
                "\nProse between the steps is not one.\n\n3. Third.\n")
        self.assertEqual(steps(body), [
            (1, "First step that wraps onto a second line."),
            (2, "Second."),
            (3, "Third."),
        ])

    def test_prose_between_steps_is_not_a_step(self):
        self.assertTrue(all(text.strip() for _, text in steps()), "a step came out empty")
        self.assertFalse(any("Then run the tests" in text for _, text in steps()),
                         "the closing sentence was read as a step, so a mention outside the steps counts")


if __name__ == "__main__":
    unittest.main()
