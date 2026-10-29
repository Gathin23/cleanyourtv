"""The `tv.json` keys a list author is told to write, in the two pages that describe them.

A list PR adds only its own folder, with a `tv.json` beside `debloat.txt`: `tools/gen_tables.py`
writes both TV tables, the brand line and the counts from it, and `tests/test_examples.py` refuses
a folder whose `tv.json` is missing or wrong. The keys the loader accepts are `KEYS` plus the
optional `OPTIONAL`, and both pages that tell a contributor what to put in the file named only the
four required ones - while two folders on main already carry the optional `readme_os`, which is the
`README.md` table's OS cell when that page should read shorter than `examples/README.md` does
(`"Google TV launcher on Android TV 11"` in one table, `"Google TV 11"` in the other). A
contributor with that kind of set had no way to learn the key from the page that asks for the file.

The key list is read out of `tools/gen_tables.py` rather than copied into this test, so a key added
to the loader and left undocumented in both pages fails here; and the loader is run over a folder
written in the test, so a `gen_tables.py` that stops validating keys - or stops accepting the one
the pages now document - fails here instead of leaving the pages quietly wrong.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import re
import sys
import tempfile
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import gen_tables  # noqa: E402  (needs tools/ on the path first)

RECIPE = ROOT / "docs" / "workflows" / "tv-list.md"
CONTRIBUTING = ROOT / "CONTRIBUTING.md"
RECIPE_SECTION = "# Workflow: Add or update a TV list"
CONTRIBUTING_SECTION = "## 1. Add your TV model"
STEP = re.compile(r"^(\d+)\.\s+(.*)$")
HEADING = re.compile(r"^#{1,6} ")
# The step that asks for the file: it opens with "Add `examples/<folder>/tv.json`". A later step may
# name the file again (tv-list.md's step 5 does, to say the tables are generated from every one), and
# that is a sentence about the tables, not the instruction that asks for the file.
ASK_FOR_FILE = re.compile(r"^Add `examples/[^`]*tv\.json`")


def section(text, head):
    """The lines under a heading, up to the next heading of any level. Empty when the heading is gone.

    Both pages keep their recipe under one heading - `# Add or update a TV list` is the whole page,
    CONTRIBUTING's is section 1 of four - so the reader stops at whichever heading comes next.
    """
    out, inside = [], False
    for line in text.split("\n"):
        if HEADING.match(line):
            inside = line.strip() == head
            continue
        if inside:
            out.append(line)
    return out


def steps(lines):
    """[(number, text)] with a wrapped step joined onto its first line and fenced blocks dropped.

    The steps are the numbered checklist the page asks a contributor to follow; prose above them and
    a code fence inside one are not the instruction, which is why both are kept out of the text a
    key has to appear in.
    """
    out, fenced = [], False
    for line in lines:
        if line.strip().startswith("```"):
            fenced = not fenced
            continue
        if fenced:
            continue
        match = STEP.match(line)
        if match:
            out.append((int(match.group(1)), match.group(2).strip()))
        elif out and line.startswith(("   ", "\t")) and line.strip():
            out[-1] = (out[-1][0], f"{out[-1][1]} {line.strip()}")
    return out


def tv_json_step(path, head):
    """The one step of that section that asks for the file, or a failure naming what it read."""
    found = [(n, t) for n, t in steps(section(path.read_text(encoding="utf-8"), head))
             if ASK_FOR_FILE.match(t)]
    if len(found) != 1:
        raise AssertionError(f"{path.name}: expected one step asking for tv.json under {head!r}, "
                             f"read {[n for n, _ in found]}")
    return found[0][1]


def fenced_json(path, head):
    """Every fenced block under that heading that is a JSON object, parsed."""
    blocks, current, fenced = [], [], False
    for line in section(path.read_text(encoding="utf-8"), head):
        if line.strip().startswith("```"):
            if fenced and current:
                blocks.append("\n".join(current))
            fenced, current = not fenced, []
            continue
        if fenced:
            current.append(line)
    out = []
    for block in blocks:
        block = block.strip()
        if block.startswith("{"):
            out.append(json.loads(block))
    return out


class TvJsonKeysInTheRecipeTest(unittest.TestCase):
    def test_the_recipe_names_every_key_the_loader_accepts(self):
        step = tv_json_step(RECIPE, RECIPE_SECTION)
        for key in gen_tables.KEYS + gen_tables.OPTIONAL:
            with self.subTest(key=key):
                self.assertIn(f"`{key}`", step,
                              f"docs/workflows/tv-list.md asks for tv.json but never names {key!r}")

    def test_contributing_names_every_key_the_loader_accepts(self):
        step = tv_json_step(CONTRIBUTING, CONTRIBUTING_SECTION)
        for key in gen_tables.KEYS + gen_tables.OPTIONAL:
            with self.subTest(key=key):
                self.assertIn(f"`{key}`", step,
                              f"CONTRIBUTING.md asks for tv.json but never names {key!r}")

    def test_the_example_in_the_recipe_is_a_file_the_loader_accepts(self):
        blocks = fenced_json(RECIPE, RECIPE_SECTION)
        self.assertEqual(len(blocks), 1, f"expected one tv.json example, read {len(blocks)}")
        example = blocks[0]
        self.assertEqual(sorted(set(example) - set(gen_tables.KEYS) - set(gen_tables.OPTIONAL)), [],
                         "the recipe's example carries a key gen_tables.load would refuse")
        for key in gen_tables.KEYS:
            with self.subTest(key=key):
                self.assertIn(key, example, f"the recipe's example leaves out {key!r}")

    def test_the_optional_key_is_one_the_loader_reads(self):
        # The key the pages were missing, checked against the loader rather than against a sentence:
        # it is in OPTIONAL, every folder that carries it validates, and it reaches the README table.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "examples" / "example-tv"
            folder.mkdir(parents=True)
            data = {"tv": "Example TV (EX1)", "os": "Google TV launcher on Android TV 11",
                    "brand": "Example", "notes": "one line", "readme_os": "Google TV 11"}
            (folder / "tv.json").write_text(json.dumps(data))
            entries = gen_tables.load(root)
            rows = gen_tables.readme_rows(entries)
            self.assertIn("| Example TV (EX1) | Google TV 11 |", rows)
            self.assertIn("| `example-tv/` | Example TV (EX1) | Google TV launcher on Android TV 11 | one line |",
                          gen_tables.examples_rows(entries))

    def test_the_loader_still_refuses_a_key_it_does_not_read(self):
        # If gen_tables.py stopped validating keys, the two tests above would pin a list of proved
        # nothing: a page could name any key and the file would still be accepted.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "examples" / "example-tv"
            folder.mkdir(parents=True)
            (folder / "tv.json").write_text(json.dumps(
                {"tv": "Example TV (EX1)", "os": "Android TV 10", "brand": "Example",
                 "notes": "one line", "not_a_key": "x"}))
            with self.assertRaises(gen_tables.Problem) as caught:
                gen_tables.load(root)
            self.assertIn("not_a_key", str(caught.exception))

    def test_both_pages_are_the_ones_read(self):
        # A renamed heading would make section() empty and every key check pass by reading nothing.
        self.assertGreaterEqual(len(steps(section(RECIPE.read_text(encoding="utf-8"), RECIPE_SECTION))), 5)
        self.assertGreaterEqual(len(steps(section(CONTRIBUTING.read_text(encoding="utf-8"),
                                                  CONTRIBUTING_SECTION))), 5)


class StepReaderTest(unittest.TestCase):
    PAGE = ("## Add a list\n"
            "1. Create the folder.\n"
            "2. Add `examples/<folder>/tv.json` beside the list, like `examples/xiaomi-mi-tv-4a/tv.json`:\n"
            "   ```json\n"
            "   { \"tv\": \"X (Y)\", \"os\": \"Android TV 10\" }\n"
            "   ```\n"
            "   `tv` and `os` fill the row, `brand` the brand line,\n"
            "   and `notes` the Notes cell.\n"
            "3. Change nothing outside the folder.\n"
            "\n"
            "## Another section\n"
            "\n"
            "1. This one mentions `later_key`, which belongs to no tv.json.\n")

    def test_a_wrapped_step_is_joined(self):
        found = dict(steps(section(self.PAGE, "## Add a list")))
        self.assertIn("`tv` and `os` fill the row", found[2])
        self.assertIn("and `notes` the Notes cell.", found[2])

    def test_a_fenced_block_is_not_the_instruction(self):
        found = dict(steps(section(self.PAGE, "## Add a list")))
        self.assertNotIn("Android TV 10", found[2],
                         "a key only present in the example must not count as documented")

    def test_a_key_in_another_section_is_not_read(self):
        found = dict(steps(section(self.PAGE, "## Add a list")))
        self.assertNotIn("later_key", " ".join(found.values()))

    def test_the_tv_json_step_is_found_and_must_be_the_only_one(self):
        step = tv_json_step_from(self.PAGE)
        self.assertIn("`brand`", step)

    def test_two_steps_asking_for_the_file_are_refused(self):
        page = self.PAGE.replace("3. Change nothing outside the folder.",
                                 "3. Add `examples/<other>/tv.json` too.")
        with self.assertRaises(AssertionError):
            tv_json_step_from(page)


def tv_json_step_from(text):
    """tv_json_step() over a page written here, so its own rules are tested rather than assumed."""
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "page.md"
        path.write_text(text)
        return tv_json_step(path, "## Add a list")


if __name__ == "__main__":
    unittest.main()
