"""A list header that says a factory-disabled package is "not in this list" is true about that list.

Many headers name the packages the TV already had disabled before the list ran and say they are left
out of the list, so restore.sh does not turn them on. The claim matters for undo: with no recovery
record, tools/recovery.py enables every package the list names, factory-disabled ones too. The Sony
BRAVIA 4K VH2 header said that of com.sony.dtv.promos, b2b.vendorprotocol, seeds.iot and
com.sonypicturescore while the groups added later for the UC and Asia-Pacific units list all four.

The reader takes a comment sentence that starts with "Already disabled/off", "Disabled from/at the
factory" or "Disabled before this debloat" and says "not in this list" or "not listed", in the same
sentence or at the start of the next one. It reads from the claim onwards, up to four comment lines,
and stops at a blank comment line or a bullet. Every package named there must be absent from the
list's active lines, parsed the way tools/disable.sh reads them.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_example_readme_package_fates import list_packages  # noqa: E402  (the disable.sh parse)

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"

START = re.compile(r"(?i)\b(?:already\s+(?:disabled|off)"
                   r"|disabled\s+(?:from|at|out\s+of)\s+the\s+(?:factory|box)"
                   r"|disabled\s+before\s+(?:this|the)\s+(?:debloat|list))")
NOT_LISTED = re.compile(r"(?i)\bnot\s+(?:in\s+this\s+list|listed)\b")
# "Not in this list, so ..." opening the sentence after the claim.
OPENS_NOT_LISTED = re.compile(r"(?i)^\(?\s*not\s+(?:in\s+this\s+list|listed)\b")
# A full stop inside a package name is never followed by a space, so this ends sentences only.
SENTENCE_END = re.compile(r"(?<=[.!?])\s+")
BULLET = re.compile(r"^#\s*(?:[-*]|\d+\.)\s")
# Deliberately loose: anything dotted is compared with the list, so restore.sh simply never matches.
TOKEN = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+")
SPAN = 4


def claims(text):
    """(line number, claim text) for every "already disabled, not in this list" claim in a list."""
    lines = text.split("\n")
    found = []
    for index, line in enumerate(lines):
        if not line.startswith("#"):
            continue
        start = START.search(line)
        if not start:
            continue
        segment = [line[start.start():]]
        for following in lines[index + 1:index + SPAN]:
            if not following.startswith("#") or not following.strip("# \t") or BULLET.match(following):
                break
            segment.append(following.lstrip("#").strip())
        sentences = SENTENCE_END.split(" ".join(segment))
        if NOT_LISTED.search(sentences[0]):
            found.append((index + 1, sentences[0]))
        elif len(sentences) > 1 and OPENS_NOT_LISTED.match(sentences[1]):
            found.append((index + 1, f"{sentences[0]} {sentences[1]}"))
    return found


def contradictions(path):
    """(line, package) for each package a claim calls unlisted that the list disables anyway."""
    active = list_packages(path)
    return [(number, name) for number, claim in claims(path.read_text(encoding="utf-8"))
            for name in TOKEN.findall(claim) if name in active]


def lists():
    return sorted(EXAMPLES.glob("*/debloat*.txt"))


class ReaderTest(unittest.TestCase):
    def test_a_claim_followed_by_not_in_this_list_is_read(self):
        text = ("# Kept: a.b.c\n"
                "# Already disabled at the factory: com.x.promos, com.x.iot,\n"
                "# com.y. Not in this list, so restore.sh leaves them as they are.\n"
                "#\n"
                "# Disabled from the factory, so not listed (restore.sh would turn them ON): com.z.app.\n")
        found = claims(text)
        self.assertEqual([number for number, _ in found], [2, 5])
        self.assertEqual(TOKEN.findall(found[0][1]), ["com.x.promos", "com.x.iot", "com.y", "restore.sh"])
        self.assertIn("com.z.app", found[1][1])
        self.assertNotIn("a.b.c", found[0][1])

    def test_a_listed_or_unrelated_claim_is_not_read(self):
        text = ("# Already off on this TV before the list: com.x.promos, com.x.home\n"
                "# (both listed below; restore leaves them disabled because the recovery record saw them).\n"
                "# Already disabled at the factory on this set: com.x.music. Left ON by choice here\n"
                "# (deliberately not in this list): com.x.voice.\n")
        self.assertEqual(claims(text), [])

    def test_a_blank_comment_or_a_bullet_ends_the_claim(self):
        text = ("# Already disabled at the factory: com.x.promos\n"
                "#\n"
                "# Not in this list: com.x.other\n"
                "# Disabled before this debloat: com.x.one\n"
                "#  - not listed: com.x.two\n")
        self.assertEqual(claims(text), [])


class FactoryOffNotListedTest(unittest.TestCase):
    def test_the_sweep_reads_the_headers(self):
        count = sum(len(claims(path.read_text(encoding="utf-8"))) for path in lists())
        self.assertGreaterEqual(count, 10, "the reader no longer finds the factory-disabled claims")

    def test_the_vh2_header_is_still_read(self):
        path = EXAMPLES / "sony-bravia-4k-vh2" / "debloat.txt"
        self.assertTrue(claims(path.read_text(encoding="utf-8")))

    def test_no_package_called_unlisted_is_on_an_active_line(self):
        found = []
        for path in lists():
            for number, name in contradictions(path):
                found.append(f"{path.relative_to(ROOT)}:{number}: {name}")
        self.assertEqual(found, [], "the header says these are not in the list, but the list disables "
                                    "them, so restore.sh with no recovery record turns them on")


if __name__ == "__main__":
    unittest.main()
