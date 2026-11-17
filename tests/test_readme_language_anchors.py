"""An in-page link in a README the repository translates reaches a heading that page has.

README.nl.md was translated heading by heading, and the five `](#...)` links in it kept the English
anchors: `#quick-start`, `#doing-it-by-hand`, `#questions-people-ask` (twice) and
`#after-a-firmware-update`. Their sections are the Dutch "Snel aan de slag", "Zelf handmatig
uitvoeren", "Veelgestelde vragen" and "Na een firmware-update", so the slugs GitHub builds are
`snel-aan-de-slag`, `zelf-handmatig-uitvoeren`, `veelgestelde-vragen` and `na-een-firmware-update`,
and clicking one of those five links did nothing. Every other translated README links its own
headings, so nothing but a reader of the Dutch page would notice: no test read the anchors.

The pages read are the READMEs at the repository root that carry a language code (`README.<lang>.md`),
and the link has to be an in-page one (`#section`): a link into another file, an `http(s)` address and
the inside of a fenced code block are not the question here, and a heading's slug is GitHub's, not
ours - lowercase, markdown emphasis and code spans dropped, everything but letters, digits, spaces,
hyphens and underscores removed, spaces turned into hyphens, and `-1`, `-2` ... added to a repeat.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import tempfile
import unittest
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_device_notes_index import destinations  # noqa: E402

# A language code after the dot, so README.md (English) and files such as README-dev.md are not it.
TRANSLATED = re.compile(r"^README\.[A-Za-z]{2}(?:-[A-Za-z]{2,4})?\.md$")
HEADING = re.compile(r"^[ \t]{0,3}#{1,6}[ \t]+(.+?)[ \t]*#*[ \t]*$", re.M)
CODE_SPAN = re.compile(r"`([^`]*)`")
LINK_TEXT = re.compile(r"\[([^\]]*)\]\([^)]*\)")
HTML_TAG = re.compile(r"<[^>]+>")
EMPHASIS = re.compile(r"[*_~]+")
NOT_IN_SLUG = re.compile(r"[^\w \-]", re.UNICODE)


def slug(heading):
    """The anchor GitHub builds for one heading's text."""
    text = LINK_TEXT.sub(r"\1", heading)
    text = CODE_SPAN.sub(r"\1", text)
    text = HTML_TAG.sub("", text)
    text = EMPHASIS.sub("", text)
    text = text.strip().lower()
    text = NOT_IN_SLUG.sub("", text)
    return text.replace(" ", "-")


def anchors(markdown):
    """{the anchors a page carries}, a repeated heading getting the -1, -2 GitHub adds."""
    out = set()
    for heading in HEADING.findall(markdown):
        base = slug(heading)
        if not base:
            continue
        if base not in out:
            out.add(base)
            continue
        n = 1
        while f"{base}-{n}" in out:
            n += 1
        out.add(f"{base}-{n}")
    return out


def dead_anchors(markdown):
    """The in-page links of one page whose anchor no heading of that page builds."""
    carried = anchors(markdown)
    dead = []
    for target in destinations(markdown):
        if not target.startswith("#") or target == "#":
            continue
        fragment = unquote(target[1:])
        if fragment not in carried:
            dead.append(target)
    return dead


def translated_readmes(root):
    """The READMEs at the root of the checkout that carry a language code, in a stable order."""
    return [page for page in sorted(Path(root).glob("README.*.md"))
            if TRANSLATED.match(page.name)]


class TranslatedReadmeAnchorsTest(unittest.TestCase):
    def test_every_in_page_link_reaches_a_heading_the_page_has(self):
        dead = {}
        for page in translated_readmes(ROOT):
            broken = dead_anchors(page.read_text(encoding="utf-8", errors="replace"))
            if broken:
                dead[page.name] = broken
        self.assertEqual({}, dead,
                         "a translated README links an anchor its own headings do not build: "
                         + ", ".join(f"{name} {links}" for name, links in dead.items()))

    def test_the_readmes_are_read(self):
        # A glob that matched nothing would pass the check above: the checkout ships eleven of them.
        self.assertGreaterEqual(len(translated_readmes(ROOT)), 8,
                               "no translated READMEs read, so the check above proved nothing")


class CheckerTest(unittest.TestCase):
    """The checker has to be able to fail, and to leave alone what is not its question."""

    def test_a_link_to_a_heading_the_page_has_is_not_reported(self):
        self.assertEqual([], dead_anchors("# Snel aan de slag\n\nTerug naar [de start](#snel-aan-de-slag).\n"))

    def test_an_english_anchor_over_a_translated_heading_is_reported(self):
        self.assertEqual(["#quick-start"],
                         dead_anchors("# Snel aan de slag\n\nZie [de start](#quick-start).\n"))

    def test_a_link_into_another_file_is_not_this_question(self):
        self.assertEqual([], dead_anchors("# Snel aan de slag\n\nZie [de faq](faq.md#quick-start).\n"))

    def test_an_address_elsewhere_is_not_this_question(self):
        self.assertEqual([], dead_anchors("# Snel aan de slag\n\n"
                                          "Zie https://example.com/page#quick-start.\n"))

    def test_an_anchor_inside_a_code_block_is_an_example(self):
        self.assertEqual([], dead_anchors("# Snel aan de slag\n\n```\n[x](#quick-start)\n```\n"))

    def test_punctuation_and_accents_are_dropped_the_way_github_drops_them(self):
        page = "# Häufige Fragen?\n\n[Fragen](#häufige-fragen)\n"
        self.assertEqual([], dead_anchors(page))

    def test_a_code_span_in_a_heading_is_not_part_of_its_anchor(self):
        page = "## The `--fix` flag\n\n[fix](#the---fix-flag)\n"
        self.assertEqual([], dead_anchors(page))

    def test_a_repeated_heading_gets_the_suffix_github_adds(self):
        page = "# Notes\n\n## Notes\n\n[second](#notes-1)\n"
        self.assertEqual([], dead_anchors(page))

    def test_a_repeated_heading_without_the_suffix_is_reported(self):
        page = "# Notes\n\n## Notes\n\n[first](#notes-0)\n"
        self.assertEqual(["#notes-0"], dead_anchors(page))

    def test_only_the_readmes_with_a_language_code_are_read(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        for name in ("README.md", "README.nl.md", "README.ja.md", "README.pt-BR.md", "README-dev.md"):
            (root / name).write_text("# Kop\n\n[terug](#kop)\n", encoding="utf-8")
        self.assertEqual(["README.ja.md", "README.nl.md", "README.pt-BR.md"],
                         [page.name for page in translated_readmes(root)])

    def test_a_translated_readme_with_a_dead_anchor_is_found(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        (root / "README.nl.md").write_text("# Snel aan de slag\n\n[de start](#quick-start)\n",
                                           encoding="utf-8")
        broken = dead_anchors((root / "README.nl.md").read_text(encoding="utf-8"))
        self.assertEqual(["#quick-start"], broken)


if __name__ == "__main__":
    unittest.main()
