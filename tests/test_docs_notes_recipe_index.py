"""The docs recipe asks for the links that keep a new docs page findable.

`docs/workflows/docs-notes.md` is the page AGENTS.md's workflow list and CONTRIBUTING's routing table
send a contributor to for "a troubleshooting entry, a fix to PROMPT.md, or any other docs change", and
its own second bullet is what sends brand-specific knowledge into `docs/devices/`. The page said what
a note holds and that several tests pin facts inside one, but not the one thing
`tests/test_device_notes_index.py` refuses a note without: a link from a page a reader comes through -
the row of its folder in `examples/README.md` when the model has a list of its own, or that page's
"Notes for models that have no tested list yet" section when it has not. A note written from the page
as it read left the suite red: `docs/devices/<name>.md : notes nothing links to (add them to
examples/README.md, either to the row of their list or to the notes index)`.

It said nothing either about the page a docs change is when it is not a device note: a page added
below `docs/` is held to `tests/test_docs_pages_reachable.py`, which reported
`pages under docs/ that nothing links to: docs/hdmi-cec-notes.md` for a page written beside them, and
the recipe is where a contributor is told what a docs change needs.

Nothing here copies either rule: the section heading the index is read by, the row rule and the
repo-wide sweeps all come from `tests/test_device_notes_index.py` and `tests/test_docs_pages_reachable.py`,
and four tests below run those checkers over trees written here - a page nothing links has to be
reported, and the same page linked from a page a reader comes through has to be quiet - so a checker
that stops enforcing fails these pairs instead of letting the page pass silently.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_device_notes_index import (  # noqa: E402
    NOTE_INDEX_HEADING,
    index_notes,
    index_notes_with_their_own_list,
    unlinked_notes,
)
from test_docs_pages_reachable import unlinked_docs  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs" / "workflows" / "docs-notes.md"

BULLET = re.compile(r"^-\s+(.*)$")
CHECK = "tests/test_device_notes_index.py"


def bullets(text):
    """The page's top-level bullets, each joined across the lines it is wrapped over."""
    out, current = [], None
    for line in text.split("\n"):
        match = BULLET.match(line)
        if match:
            if current is not None:
                out.append(current)
            current = match.group(1).strip()
            continue
        if current is None:
            continue
        if not line.strip():
            out.append(current)
            current = None
        elif line[:1] in (" ", "\t"):
            current = f"{current} {line.strip()}"
        else:
            out.append(current)
            current = None
    if current is not None:
        out.append(current)
    return out


def index_bullets(text):
    """The bullets that send a new docs/devices note to examples/README.md."""
    return [b for b in bullets(text) if "docs/devices" in b and "examples/README.md" in b]


def page_text():
    return PAGE.read_text(encoding="utf-8")


class RecipeSendsANoteToTheIndexTest(unittest.TestCase):
    def test_one_bullet_sends_a_new_note_to_the_index(self):
        found = index_bullets(page_text())
        named_elsewhere = [b for b in bullets(page_text()) if "docs/devices" in b]
        self.assertEqual(
            len(found), 1,
            f"{PAGE.name} has {len(found)} bullets naming both docs/devices/ and examples/README.md: the "
            f"bullet that sends a note into docs/devices/ has to say where it is linked from, or a note "
            f"written from the page fails {CHECK}. Bullets naming docs/devices/ today: {named_elsewhere}",
        )

    def test_the_bullet_names_where_in_the_index(self):
        bullet = index_bullets(page_text())[0]
        self.assertIn(NOTE_INDEX_HEADING, bullet,
                      f"the bullet has to name the section a list-less note is filed under, the heading "
                      f"{CHECK} reads it by")
        self.assertIn("row", bullet.lower(),
                      "the bullet has to name the examples/README.md row a note with a list of its own goes in")

    def test_the_bullet_names_the_check(self):
        self.assertIn(CHECK, index_bullets(page_text())[0],
                      "the bullet has to name the check, so a reader who skips it knows which test failed")


class TheCheckerStillBitesTest(unittest.TestCase):
    def note_tree(self, index_body):
        """A tree with one device note and an examples/README.md whose notes section is `index_body`."""
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        (root / "docs" / "devices").mkdir(parents=True)
        (root / "docs" / "devices" / "lonely.md").write_text("# A device note\n", encoding="utf-8")
        (root / "examples").mkdir()
        (root / "examples" / "README.md").write_text(
            f"# Lists\n\n| Folder | Model |\n|---|---|\n\n## {NOTE_INDEX_HEADING}\n\n{index_body}\n",
            encoding="utf-8",
        )
        return root

    def test_a_note_nothing_links_is_reported(self):
        root = self.note_tree("")
        self.assertEqual(unlinked_notes(root), ["docs/devices/lonely.md"])

    def test_the_same_note_linked_from_the_index_is_quiet(self):
        root = self.note_tree("- [A note](../docs/devices/lonely.md), Android 9: what it shows.")
        self.assertEqual(unlinked_notes(root), [])
        self.assertEqual(index_notes(root), ["lonely.md"])

    def test_a_link_outside_the_index_section_is_still_a_link(self):
        root = self.note_tree("")
        (root / "examples" / "README.md").write_text(
            f"# Lists\n\nSee [the note](../docs/devices/lonely.md) from its row instead.\n\n"
            f"## {NOTE_INDEX_HEADING}\n\n",
            encoding="utf-8",
        )
        self.assertEqual(unlinked_notes(root), [])
        self.assertEqual(index_notes(root), [], "a link outside the section is not an index entry")

    def test_an_index_entry_that_holds_its_own_list_is_reported(self):
        # The page's second half: the section says its model has no list, so an entry that links
        # examples/<its own folder>/debloat.txt from there belongs in that folder's row instead - which
        # index_notes_with_their_own_list is the check for, and it is why the bullet names the move.
        root = self.note_tree("- [A note](../docs/devices/lonely.md), Android 9: a one-day list.")
        (root / "docs" / "devices" / "lonely.md").write_text(
            "# A device note\n\nA tested package list for this unit lives in "
            "[`examples/lonely/debloat.txt`](../../examples/lonely/debloat.txt).\n", encoding="utf-8")
        (root / "examples" / "lonely").mkdir()
        (root / "examples" / "lonely" / "debloat.txt").write_text("# a list\n", encoding="utf-8")
        self.assertEqual(unlinked_notes(root), [], "the index entry keeps it reachable")
        offenders = index_notes_with_their_own_list(root)
        self.assertEqual(1, len(offenders))
        self.assertIn("examples/lonely/debloat.txt", offenders[0])


class TheBulletReaderTest(unittest.TestCase):
    def test_a_wrapped_bullet_is_one_bullet(self):
        text = (f"- A note in `docs/devices/` is linked from `examples/README.md`, under the\n"
                f"  \"{NOTE_INDEX_HEADING}\" heading.\n")
        self.assertEqual(len(index_bullets(text)), 1)
        self.assertIn(NOTE_INDEX_HEADING, index_bullets(text)[0])

    def test_a_mention_outside_a_bullet_is_not_a_bullet(self):
        text = ("A note in `docs/devices/` is linked from `examples/README.md`, and that is all this line says.\n")
        self.assertEqual(index_bullets(text), [])

    def test_a_page_without_such_a_bullet_reads_as_empty(self):
        text = ("- Brand-specific knowledge goes in `examples/` or `docs/devices/`.\n"
                "- Docs and the README stay in step.\n")
        self.assertEqual([b for b in bullets(text) if "docs/devices" in b],
                         ["Brand-specific knowledge goes in `examples/` or `docs/devices/`."])
        self.assertEqual(index_bullets(text), [])


class ThePageCheckerStillBitesTest(unittest.TestCase):
    def tree(self, files):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        for name, text in files.items():
            (root / name).parent.mkdir(parents=True, exist_ok=True)
            (root / name).write_text(text, encoding="utf-8")
        return root

    def test_a_page_nothing_links_is_reported(self):
        root = self.tree({"README.md": "# readme\n", "docs/hdmi-cec.md": "# notes\n"})
        self.assertEqual(["docs/hdmi-cec.md"], unlinked_docs(root))

    def test_the_same_page_linked_from_a_page_is_quiet(self):
        root = self.tree({"CONTRIBUTING.md": "Read [the HDMI notes](docs/hdmi-cec.md).\n",
                          "docs/hdmi-cec.md": "# notes\n"})
        self.assertEqual([], unlinked_docs(root))


if __name__ == "__main__":
    unittest.main()
