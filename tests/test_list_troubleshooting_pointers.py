"""A list header that sends the reader to a fix names the page the fix is on.

The README's "Gotchas we hit", later its "Troubleshooting" section, moved to docs/TROUBLESHOOTING.md on
19 October 2026 (c49561eb). Seven list headers still said "(README, Troubleshooting)", "(see README
gotchas)" or "See the README troubleshooting section", among them examples/xiaomi-mi-tv-4a, the list
CONTRIBUTING.md and docs/workflows/tv-list.md tell contributors to copy. The README has no such heading
any more, so the reader searches the front page and finds "When something goes wrong" with three
bullets. The pairing recipe and the keyboard fix are not among them, and the setupwraith bullet is a
summary that sends the reader on to docs/TROUBLESHOOTING.md.

The sweep reads every page and list under examples/, with a list's `#` comment wraps joined, and lets a
README section through only when the README still has a heading of that name. The table names the
bullet each of those seven headers points at now, and the bullet has to start that way in the doc.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"
README = (ROOT / "README.md").read_text(encoding="utf-8")
DOC = (ROOT / "docs" / "TROUBLESHOOTING.md").read_text(encoding="utf-8")

# "README, Troubleshooting", "README Troubleshooting", "the README troubleshooting section",
# "README's Troubleshooting", "README gotchas".
README_SECTION = re.compile(r"\bREADME(?:'s)?,?\s+(troubleshooting|gotchas)\b", re.I)
# A list wraps its header at about 105 columns, and a pointer can be cut anywhere in it.
COMMENT_WRAP = re.compile(r"\s*\n#\s*")
FENCE = re.compile(r"```.*?```", re.S)

# Folder -> the bullet in docs/TROUBLESHOOTING.md its header names, as `docs/TROUBLESHOOTING.md, "..."`.
POINTERS = {
    "google-tv-streamer-4k-kirkwood": "USB debugging never opens port 5555",
    "google-chromecast-gtv-4k": "USB debugging never opens port 5555",
    "tcl-qm6k-google-tv": "USB debugging never opens port 5555",
    "tcl-g05-google-tv": "The new launcher still doesn't own Home",
    "tcl-55s470g": "The new launcher still doesn't own Home",
    "xiaomi-mitv-moor2": "The new launcher still doesn't own Home",
    "xiaomi-mi-tv-4a": "No on-screen keyboard",
}


def joined(text):
    """A list's text with its `#` comment wraps joined into one line, as a reader reads it."""
    return COMMENT_WRAP.sub(" ", text)


def readme_headings():
    return [h.lower() for h in re.findall(r"^#+\s+(.+)$", FENCE.sub("", README), re.M)]


def pages(root):
    """(relative path, text) for every list and page under root, in a stable order."""
    for directory, subdirectories, files in os.walk(root):
        subdirectories.sort()
        for name in sorted(files):
            if name.endswith((".txt", ".md")):
                path = Path(directory) / name
                yield path.relative_to(ROOT), path.read_text(encoding="utf-8", errors="replace")


def missing_sections(root, headings):
    """(file, pointer) for every README section named under root that headings do not have."""
    found = []
    for path, text in pages(root):
        for match in README_SECTION.finditer(joined(text)):
            if not any(match.group(1).lower() in heading for heading in headings):
                found.append((str(path), match.group(0)))
    return found


class ListTroubleshootingPointersTest(unittest.TestCase):
    def test_the_pattern_reads_every_spelling_the_lists_used(self):
        for text in ("works on 5555 (README, Troubleshooting).", "installed (see README Troubleshooting).",
                     "See the README troubleshooting section.", "Set Gboard as the IME (see README gotchas).",
                     "the README's Troubleshooting has it"):
            with self.subTest(text=text):
                self.assertRegex(text, README_SECTION)
        for text in ('(docs/TROUBLESHOOTING.md, "No on-screen keyboard")', "Steps in the README FAQ.",
                     "see launcher Troubleshooting #3"):
            with self.subTest(text=text):
                self.assertNotRegex(text, README_SECTION)

    def test_a_pointer_cut_by_a_comment_wrap_is_still_read(self):
        text = "# installed, so no keyboard appears. Set Gboard as the IME (see README\n#   gotchas).\n"
        self.assertRegex(joined(text), README_SECTION)

    def test_no_page_in_examples_names_a_readme_section_the_readme_does_not_have(self):
        self.assertEqual(missing_sections(EXAMPLES, readme_headings()), [],
                         "the section moved to docs/TROUBLESHOOTING.md: name the bullet there")

    def test_each_header_names_the_bullet_the_fix_is_in(self):
        for folder, lead in POINTERS.items():
            with self.subTest(folder=folder):
                self.assertTrue(re.search(r"^- \*\*" + re.escape(lead), DOC, re.M),
                                f"no bullet in docs/TROUBLESHOOTING.md starts with {lead!r}")
                pointer = f'docs/TROUBLESHOOTING.md, "{lead}"'
                header = joined((EXAMPLES / folder / "debloat.txt").read_text(encoding="utf-8"))
                self.assertTrue(pointer in header, f"the header does not name {pointer}")


if __name__ == "__main__":
    unittest.main()
