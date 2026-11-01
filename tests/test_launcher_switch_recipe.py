"""docs/workflows/launcher-switch.md asks for everything `tests/test_switches.py` refuses a switch without.

The recipe is the page AGENTS.md's workflow list and CONTRIBUTING's routing table send a new switch author
to, and its numbered steps are the checklist they follow. Step 3 asked for a row in the Keys table of
`themes/README.md` and said nothing about `docs/switches.md`, while `tests/test_switches.py` fails a key in
that table with no screenshot row there - so a switch built by following the page exactly landed a red
suite. Reproduced on the base commit by adding a Keys row for a new key the way step 3 reads:

    AssertionError: 'demo_switch' not found in {'columns': ..., 'gamepad_passthrough': ...} :
    no screenshot: add one to docs/switches.md (see its Adding a key section)

Nothing is copied in here. The requirement is read out of the check that enforces it (the file it reads
and the message it fails with), and the picture's path and rules out of `docs/switches.md`, whose Adding a
key section owns them, so a checker that stops enforcing or a convention that moves fails this pair
instead of leaving the recipe stale.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
RECIPE = ROOT / "docs" / "workflows" / "launcher-switch.md"
SWITCHES = ROOT / "docs" / "switches.md"
CHECK = ROOT / "tests" / "test_switches.py"

# The picture's path as docs/switches.md's Adding a key section gives it.
PICTURE = re.compile(r"`(docs/switches/<key>\.\w+)`")
# And the two rules for it: the shape, and the file size ceiling.
SHAPE = re.compile(r"\b(\d+:\d+)\b")
CEILING = re.compile(r"under (\d+ ?KB)")


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


def the_keys_step():
    return [text for _, text in steps() if "themes/README.md" in text]


def picture_path():
    match = PICTURE.search(SWITCHES.read_text(encoding="utf-8"))
    return match.group(1) if match else None


def picture_shape():
    match = SHAPE.search(SWITCHES.read_text(encoding="utf-8"))
    return match.group(1) if match else None


def picture_ceiling():
    match = CEILING.search(SWITCHES.read_text(encoding="utf-8"))
    return match.group(1) if match else None


class LauncherSwitchRecipeTest(unittest.TestCase):
    def test_the_keys_step_asks_for_the_screenshot(self):
        found = the_keys_step()
        self.assertEqual(len(found), 1, f"expected one step naming themes/README.md, got {len(found)}")
        self.assertIn("docs/switches.md", found[0],
                      "the step that adds the Keys row must also ask for the screenshot row in docs/switches.md")

    def test_the_step_names_the_check_that_refuses_it(self):
        self.assertIn("tests/test_switches.py", the_keys_step()[0],
                      "the step should name the check, so a contributor knows why the row is not optional")

    def test_the_step_carries_the_picture_path(self):
        path = picture_path()
        self.assertIsNotNone(path, "docs/switches.md no longer gives the picture's path in `docs/switches/<key>.jpg`")
        self.assertIn(path, the_keys_step()[0], f"the step should give the path docs/switches.md gives, {path}")

    def test_the_step_carries_the_picture_rules(self):
        step = the_keys_step()[0]
        for name, value in (("shape", picture_shape()), ("size ceiling", picture_ceiling())):
            with self.subTest(rule=name):
                self.assertIsNotNone(value, f"docs/switches.md no longer states the {name}")
                self.assertIn(value, step, f"the step should repeat the {name} docs/switches.md states, {value!r}")


class TheCheckStillAsksTest(unittest.TestCase):
    """Pins the enforcer, not a wording: if it stops refusing a Keys row without a screenshot, the pair fails."""

    def test_it_reads_the_switches_page(self):
        source = CHECK.read_text(encoding="utf-8")
        self.assertIn('SWITCHES = DOCS / "switches.md"', source,
                      "tests/test_switches.py no longer reads docs/switches.md")
        self.assertIn("no screenshot: add one to docs/switches.md", source,
                      "tests/test_switches.py no longer refuses a key without a screenshot row")
        self.assertIn("themes", source, "tests/test_switches.py no longer takes its keys from themes/README.md")


class StepReaderTest(unittest.TestCase):
    """The reader above decides what counts as a step; a silent change there would hide a missing one."""

    def test_it_reads_every_step(self):
        found = steps()
        self.assertGreaterEqual(len(found), 9, "the recipe lost a step")
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
