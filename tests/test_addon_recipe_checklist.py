"""docs/workflows/addon.md is the recipe an add-on author is sent to, and its last step promised that
`tests/test_addons.py` checks "all of this" - while the page never asked for the file that check refuses a
folder without, `addons/<name>/screenshot.jpg`, nor that the folder's row in addons/README.md shows it.

A folder built exactly from the page as it read (its own Gradle project, a README with a Permissions
heading, one row linking the folder) already fails the suite:

    AssertionError: no screenshot.jpg: add one of the add-on on a real TV, see addons/README.md

and once a picture is in place the row is still refused:

    AssertionError: 'x/screenshot.jpg' not found in '| [x](x/) | ... | wanted |': the addons/README.md row
    does not show the screenshot

The same page left out that a new folder carries its own Gradle wrapper, which the add-on workflow refuses
to build ("addons/x has no gradlew"), so a folder copied from a template without `gradlew` never builds.

These tests read both requirements out of the checkers themselves (`tests/test_addons.py` and
`.github/workflows/addons.yml`), so an assertion about the page can only pass while the checker really
enforces what it asks for: if either stops, the pair fails and the page has to be re-read rather than
silently kept in step.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs/workflows/addon.md"
ADDON_TESTS = ROOT / "tests/test_addons.py"
ADDON_WORKFLOW = ROOT / ".github/workflows/addons.yml"


def steps(text):
    """[(number, text)] for the numbered steps of a recipe, wrapped continuation lines joined in."""
    found = []
    for line in text.split("\n"):
        m = re.match(r"^(\d+)\.\s+(.*)$", line)
        if m:
            found.append([int(m.group(1)), m.group(2)])
        elif found and line.startswith("   ") and line.strip():
            found[-1][1] = found[-1][1] + " " + line.strip()
    return [(number, body) for number, body in found]


def steps_naming(text, needle):
    """The numbered steps whose text names `needle`."""
    return [body for _, body in steps(text) if needle in body]


class RecipeTest(unittest.TestCase):
    def setUp(self):
        self.text = PAGE.read_text(encoding="utf-8")

    def test_the_recipe_has_steps(self):
        self.assertGreaterEqual(len(steps(self.text)), 5, "docs/workflows/addon.md lost its numbered steps")

    def test_the_recipe_asks_for_the_screenshot_file(self):
        self.assertTrue(steps_naming(self.text, "screenshot.jpg"),
                        "the recipe never asks for addons/<name>/screenshot.jpg, which tests/test_addons.py\n"
                        "fails a folder without: add the step, with the JPEG 16:9 / under 300 KB rule, or the\n"
                        "page sends an author into a red suite")

    def test_the_recipe_says_the_row_shows_the_screenshot(self):
        rows = steps_naming(self.text, "addons/README.md")
        self.assertEqual(len(rows), 1, f"expected one step about the row in addons/README.md, found {len(rows)}")
        self.assertIn("screenshot", rows[0].lower(),
                      "the step that adds the row in addons/README.md does not say the row shows the add-on's\n"
                      "screenshot, which tests/test_addons.py requires of the row")

    def test_the_recipe_asks_for_the_gradle_wrapper(self):
        project = steps_naming(self.text, "settings.gradle")
        self.assertEqual(len(project), 1,
                         f"expected one step creating the Gradle project, found {len(project)}")
        self.assertIn("gradlew", project[0],
                      "the step that creates addons/<name>/ does not ask for its own gradlew wrapper, and\n"
                      ".github/workflows/addons.yml refuses to build a folder without one")


class CheckerTest(unittest.TestCase):
    """The requirements the page is held to are the ones the checkers still enforce."""

    def test_addon_tests_still_refuse_a_folder_without_a_screenshot(self):
        text = ADDON_TESTS.read_text(encoding="utf-8")
        self.assertIn('"screenshot.jpg"', text,
                      "tests/test_addons.py no longer names screenshot.jpg; the recipe's screenshot step can go")
        self.assertIn("{folder.name}/screenshot.jpg", text,
                      "tests/test_addons.py no longer requires the row to show the screenshot; the row step's\n"
                      "wording can go")

    def test_the_addon_workflow_still_refuses_a_folder_without_its_wrapper(self):
        text = ADDON_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("addons/$a/gradlew", text,
                      ".github/workflows/addons.yml no longer refuses a folder without gradlew; the recipe's\n"
                      "wrapper step can go")


class StepReaderTest(unittest.TestCase):
    def test_a_wrapped_step_is_read_as_one_step(self):
        text = "1. Create a folder\n   with a wrapper.\n2. Add a row.\n"
        self.assertEqual(steps(text), [(1, "Create a folder with a wrapper."), (2, "Add a row.")])

    def test_a_mention_outside_the_steps_is_not_a_step(self):
        text = "An intro that names addons/README.md.\n\n1. Create a folder.\n"
        self.assertEqual(steps_naming(text, "addons/README.md"), [])

    def test_a_step_names_only_what_it_carries(self):
        text = "1. Create a folder with gradlew.\n2. Add a row showing the screenshot.\n"
        self.assertEqual(len(steps_naming(text, "gradlew")), 1)
        self.assertEqual(steps_naming(text, "screenshot"), ["Add a row showing the screenshot."])


if __name__ == "__main__":
    unittest.main()
