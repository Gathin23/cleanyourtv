"""No folder's README numbers two units the same.

A folder's `README.md` grows one numbered section per set reported on it, and the number is how the
rest of the page and the folder's other files refer to that set: "codes from the fifth unit", "the
trade the third unit did not measure". Two sections with the same number make every one of those
references ambiguous.

`examples/tcl-g08-us-google-tv` had exactly that. `8d20d8a3` (2026-11-14 21:39) added "## Fifth unit:
a 98-inch set on AU14"; `f4293591` (2026-11-14 23:34, a different contributor) added "## Fifth unit:
TCL 55Q77K with Google TV's home kept" and, in the same commit, wrote "(README.md, "Fifth unit")" into
the list header and "(codes from the fifth unit)" into the second unit's button table - so three
references pointed at a label that named two sets. Both sections described real, separate sets, and
the folder's own `tv.json` carried both as "fifth unit" too, so the collision reached the generated
table.

Only the README's `#` headings are read. A list header under `examples/` uses the same words as prose
rather than as a table of contents, and `examples/jvc-uk-4k-sadang` labels both a second set of the
same model ("same fingerprint") and the Logik, a second model, as "Second unit" - the latter being
what its `tv.json` calls "second unit tested". A heading guard would have to call that a defect; this
one leaves the list headers to the folder and guards the page that has a contents list.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"

ORDINALS = (
    "First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth", "Ninth", "Tenth",
    "Eleventh", "Twelfth", "Thirteenth", "Fourteenth", "Fifteenth",
)
# "## Fifth unit: a 98-inch set on AU14", "### Second unit", at any depth.
HEADING = re.compile(r"^#{1,6}\s+(" + "|".join(ORDINALS) + r")\s+unit\b", re.IGNORECASE)

# Headings the tree carried when this test was written: 43 in 24 folders. A floor well below that
# catches a glob or a regex that stopped finding anything without pinning the number.
SOME_HEADINGS = 20


def headings():
    """(fine name, line number, ordinal as written) for every numbered unit section in examples/."""
    found = []
    for readme in sorted(EXAMPLES.glob("*/README.md")):
        text = readme.read_text(encoding="utf-8")
        for n, line in enumerate(text.splitlines(), 1):
            match = HEADING.match(line)
            if match:
                found.append((readme.relative_to(ROOT), n, match.group(1)))
    return found


class UnitNumbers(unittest.TestCase):
    def test_a_folder_numbers_each_unit_once(self):
        seen = {}
        repeats = []
        for readme, line, ordinal in headings():
            first = seen.setdefault((readme, ordinal.lower()), line)
            if first != line:
                repeats.append(
                    f"{readme}:{line} is the {ordinal} unit a second time; line {first} already is"
                )
        self.assertEqual([], repeats)

    def test_the_headings_are_still_there_to_read(self):
        self.assertGreaterEqual(len(headings()), SOME_HEADINGS)


if __name__ == "__main__":
    unittest.main()
