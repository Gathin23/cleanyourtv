"""The front page and its translations link each other.

A translation is only useful if a reader can reach it. README.md is the page everyone lands on, and
each translation opens with a line that offers the other languages, so a language added to one of
them and not to the rest leaves readers of that language without a way through. Nothing read those
links before: README.vi.md was linked from the two other translations and from nowhere on
README.md.

Every translation is found by its file name, so a fourth one is covered without touching this test.
Nothing here reads the prose, so translating or rewording any page stays free.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
FRONT = "README.md"
TRANSLATION = re.compile(r"^README\.[A-Za-z][A-Za-z-]*\.md$")
# `[text](target)` and `href="target"`, the two shapes the language rows use.
LINK = re.compile(r"\]\(([^)\s]+)\)|href=\"([^\"]+)\"")


def translations():
    return sorted(p.name for p in ROOT.iterdir() if p.is_file() and TRANSLATION.match(p.name))


def links(text):
    """The local files a page links to, without anchors or query strings."""
    found = set()
    for inline, href in LINK.findall(text):
        target = (inline or href).split("#")[0].split("?")[0].strip()
        for prefix in ("https://github.com/Gathin23/cleanyourtv/blob/main/", "blob/main/"):
            if target.startswith(prefix):
                target = target[len(prefix):]
        if target.startswith("./"):
            target = target[2:]
        if target and "/" not in target and not target.startswith(("http", "mailto", "data:")):
            found.add(target)
    return found


def missing(needed, text):
    """The names in `needed` a page does not link to."""
    found = links(text)
    return [name for name in needed if name not in found]


class ReadmeTranslationsTest(unittest.TestCase):
    def test_every_translation_is_found(self):
        # A regex that matches nothing would make the checks below pass for any set of pages.
        self.assertGreaterEqual(len(translations()), 3, "no README.<language>.md files were found")
        self.assertIn("README.vi.md", translations())

    def test_the_front_page_links_every_translation(self):
        text = (ROOT / FRONT).read_text(encoding="utf-8")
        self.assertEqual([], missing(translations(), text),
                         "README.md does not link every translation its readers can open")

    def test_every_translation_links_the_front_page_and_the_others(self):
        every = [FRONT] + translations()
        for name in translations():
            with self.subTest(page=name):
                text = (ROOT / name).read_text(encoding="utf-8")
                needed = [other for other in every if other != name]
                self.assertEqual([], missing(needed, text), f"{name} does not link {needed}")


class LinkReaderTest(unittest.TestCase):
    """The reader above has to see the shapes the pages really use, and nothing else."""

    def test_markdown_and_html_links_are_both_read(self):
        text = "[中文](README.zh-CN.md) · <a href=\"README.ja.md\">日本語</a>"
        self.assertEqual({"README.zh-CN.md", "README.ja.md"}, links(text))

    def test_an_anchor_and_a_leading_dot_slash_are_dropped(self):
        self.assertEqual({"README.vi.md"}, links("[vi](./README.vi.md#start)"))

    def test_an_absolute_repository_link_counts(self):
        text = "[ja](https://github.com/Gathin23/cleanyourtv/blob/main/README.ja.md)"
        self.assertEqual({"README.ja.md"}, links(text))

    def test_external_links_are_not_files(self):
        self.assertEqual(set(), links("[site](https://cleanyour.tv) [mail](mailto:a@b) [#top](#top)"))

    def test_a_missing_language_is_reported(self):
        text = "[English](README.md) · [日本語](README.ja.md)"
        self.assertEqual(["README.vi.md"], missing([FRONT, "README.ja.md", "README.vi.md"], text))

    def test_a_page_that_links_nothing_reports_everything(self):
        self.assertEqual([FRONT, "README.vi.md"], missing([FRONT, "README.vi.md"], "no links here"))


if __name__ == "__main__":
    unittest.main()
