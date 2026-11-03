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

Two rules the checkers gained since are missing from it in the same way, and a folder built from the page
as it reads now still fails both. Its row is the only place the add-ons table's Permissions column is
covered, and the page never asks for that column, so an author who follows it writes a row without one -
or, filling it in from the words the page gives for the add-on's own README, writes "start at boot" where
the manifest says `RECEIVE_BOOT_COMPLETED`:

    AssertionError: True is not false : recipe-check asks for FOREGROUND_SERVICE,
    FOREGROUND_SERVICE_SPECIAL_USE, RECEIVE_BOOT_COMPLETED and its row says 'None'

And the folder's `.gitignore` - the one every add-on carries, the fix for the `sdk.dir=/home/...` that
Android Studio writes into `local.properties` - is nowhere in the recipe, so the folder that follows it
puts that path in the commit:

    AssertionError: addons/recipe-check/local.properties is not ignored;
    addons/recipe-check/.gitignore carries: (no .gitignore)

These tests read every requirement out of the checkers themselves (`tests/test_addons.py`,
`.github/workflows/addons.yml`, `tests/test_addon_local_properties_ignored.py` and
`tests/test_addon_permissions_index.py`), so an assertion about the page can only pass while the checker
really enforces what it asks for: if one stops, the pair fails and the page has to be re-read rather than
silently kept in step.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs/workflows/addon.md"
ADDON_TESTS = ROOT / "tests/test_addons.py"
ADDON_WORKFLOW = ROOT / ".github/workflows/addons.yml"
LOCAL_PROPERTIES_TESTS = ROOT / "tests/test_addon_local_properties_ignored.py"
PERMISSIONS_TESTS = ROOT / "tests/test_addon_permissions_index.py"


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

    def test_the_recipe_asks_for_the_local_properties_ignore(self):
        ignore = steps_naming(self.text, ".gitignore")
        self.assertEqual(len(ignore), 1,
                         f"expected one step about the folder's .gitignore, found {len(ignore)}")
        self.assertIn("local.properties", ignore[0],
                      "the recipe never asks for the folder's .gitignore, and\n"
                      "tests/test_addon_local_properties_ignored.py fails every add-on whose local.properties\n"
                      "git would commit: name the file Android Studio writes, beside the other three lines")

    def test_the_recipe_gives_the_permissions_in_the_manifest_names(self):
        section = steps_naming(self.text, "Permissions section")
        self.assertEqual(len(section), 1,
                         f"expected one step about the add-on's README, found {len(section)}")
        self.assertIn("AndroidManifest.xml", section[0],
                      "the step that asks for the Permissions section does not ask for the names the app's\n"
                      "AndroidManifest.xml uses, which is what tests/test_addon_permissions_index.py matches")

    def test_the_recipe_says_the_row_carries_the_permissions_column(self):
        rows = steps_naming(self.text, "addons/README.md")
        self.assertEqual(len(rows), 1, f"expected one step about the row in addons/README.md, found {len(rows)}")
        self.assertIn("permissions", rows[0].lower(),
                      "the step that adds the row does not ask for the Permissions column, which\n"
                      "tests/test_addon_permissions_index.py holds to the manifest for every add-on; a row\n"
                      "without it is not read as this add-on's row at all")


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

    def test_the_local_properties_check_still_asks_git_about_the_file(self):
        text = LOCAL_PROPERTIES_TESTS.read_text(encoding="utf-8")
        self.assertIn("check-ignore", text,
                      "tests/test_addon_local_properties_ignored.py no longer asks git whether the file is\n"
                      "ignored; the recipe's .gitignore step can go")
        self.assertIn('LOCAL_PROPERTIES = "local.properties"', text,
                      "tests/test_addon_local_properties_ignored.py no longer names local.properties; the\n"
                      "recipe's .gitignore step can go")

    def test_the_permissions_check_still_holds_the_row_and_the_section(self):
        text = PERMISSIONS_TESTS.read_text(encoding="utf-8")
        for reader in ("manifest_permissions", "permissions_cell", "permissions_section"):
            self.assertIn(reader, text,
                          f"tests/test_addon_permissions_index.py no longer reads {reader}; the recipe's\n"
                          f"permission steps can go")


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
