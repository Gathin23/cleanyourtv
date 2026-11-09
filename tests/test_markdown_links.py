"""Every relative link in the repository's markdown resolves to something that exists.

A page moved between folders keeps its old relative path and nothing opens it for you: this note
came from `examples/tcl-g09-google-tv/`, moved to `docs/devices/`, and its companion link stayed
`../examples/tcl-g09-google-tv` - which from `docs/devices/` is `docs/examples/`, a folder that has
never existed. CI runs the unit tests and the shell helpers, so a link like that is only found by
the reader who clicks it, and only when they are on the page that has it. This walks every `.md`
file once and fails on the ones that lead nowhere.

The same goes for the `#section` a link carries. `README.nl.md` keeps its sections in Dutch (`## Snel
aan de slag`) and its links in the English page's words (`#quick-start`), which only works because
each Dutch heading has an `<a id="quick-start"></a>` above it: a link whose anchor nothing on the
page gives does nothing at all when it is clicked - the browser stays exactly where it is, with
nothing to see. So the anchors every page gives are worked out here from its headings in GitHub's
words (lowercase, punctuation dropped, spaces to hyphens, a repeated heading numbered `-1`, `-2`)
and from its own `<a id>`/`<a name>` elements, and each `page.md#section` or `#section` is held to
them.

What counts: markdown links and images, reference definitions and `href`/`src` in raw HTML. What
does not: `http(s)`, `mailto:`, `data:` and the insides of fenced code blocks, where a path is an
example rather than a link. An anchor on a link that leaves the repository is not ours to judge.

A link to a folder carries its section in the README.md GitHub renders inside that folder, and that
shape was read by nothing: `dangling_anchors()` asked only whether a link's file gives the anchor, so
a destination that is a folder was skipped whole. `examples/README.md` carried one -
`examples/tcl-55c7k-uk/#compatibility-report-tcl-50c805-wi-fi`, the only `#section` in that table's
100 relative note links that sat on a folder rather than a file - and its fragment could be changed
to a heading the README does not have with every test still green. A folder link is now followed into
its README.md, which is where the heading has to be, and a folder with no README.md is reported
because nothing there can give the section.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import re
import tempfile
import unicodedata
import unittest
from functools import lru_cache
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]

# Not part of the documentation: the checkout's git data, the tools venv CI builds before this job
# runs (site-packages ships its own .md files), Gradle's output and a theme's node_modules.
SKIP = {".git", "venv", "node_modules", "build", ".gradle", "__pycache__"}

MARKDOWN_LINK = re.compile(r"\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
REFERENCE_DEF = re.compile(r"^\[[^\]]+\]:\s*(\S+)", re.M)
HTML_ATTR = re.compile(r"(?:href|src)=\"([^\"]+)\"")
FENCE = re.compile(r"```.*?```", re.S)
HEADING = re.compile(r"^#{1,6}\s+(.+?)\s*#*\s*$", re.M)
HEADING_LINK = re.compile(r"\[([^\]]*)\]\([^)]*\)")
# The anchor a page states by hand: README.nl.md puts one above each Dutch heading, in the English
# page's words, so the links that came across with the text still land on their section.
EXPLICIT_ANCHOR = re.compile(r"<a\s+(?:id|name)=\"([^\"]+)\"", re.I)
EXTERNAL = ("http://", "https://", "mailto:", "data:", "#", "<")


def markdown_files(root):
    """Every .md below root, outside the folders in SKIP, in a stable order."""
    root = Path(root)
    for dirpath, dirnames, filenames in os.walk(root):
        # Prune instead of filtering after the walk: a venv holds thousands of files we never read.
        dirnames[:] = sorted(name for name in dirnames if name not in SKIP)
        for name in sorted(filenames):
            if name.endswith(".md"):
                yield Path(dirpath) / name


def targets(text):
    """The link destinations in one page: inline links/images, reference definitions, HTML."""
    text = FENCE.sub("", text)
    return MARKDOWN_LINK.findall(text) + REFERENCE_DEF.findall(text) + HTML_ATTR.findall(text)


def slug(title):
    """The anchor GitHub gives a heading: lowercase, punctuation dropped, spaces to hyphens.

    A heading that is a link keeps its text, inline code keeps its words, and every letter with a
    diacritic is kept as it is - `## Vragen die mensen stellen` is `#vragen-die-mensen-stellen`
    and `## Điều khiển` keeps its `Đ`.
    """
    title = HEADING_LINK.sub(r"\1", title).replace("`", "").strip().lower()
    kept = []
    for char in title:
        if char in " -_" or char.isalnum() or unicodedata.category(char).startswith("M"):
            kept.append(char)
    return "".join(kept).replace(" ", "-")


@lru_cache(maxsize=None)
def anchors(path):
    """The anchors a page gives: its headings in GitHub's words, plus the ids it states by hand.

    A repeat is numbered the way GitHub numbers it (`## Same` three times is `same`, `same-1`,
    `same-2`), which is why the headings are walked in order rather than collected into a set.
    """
    text = FENCE.sub("", Path(path).read_text(encoding="utf-8", errors="replace"))
    seen = {}
    out = set()
    for title in HEADING.findall(text):
        base = slug(title)
        if not base:
            continue
        number = seen.get(base, 0)
        seen[base] = number + 1
        out.add(base if number == 0 else f"{base}-{number}")
    out.update(name.lower() for name in EXPLICIT_ANCHOR.findall(text))
    return frozenset(out)


def broken_links(root):
    """(page, destination) for every relative destination that does not exist, in file order."""
    root = Path(root)
    out = []
    for path in markdown_files(root):
        for target in targets(path.read_text(encoding="utf-8", errors="replace")):
            if target.startswith(EXTERNAL):
                continue
            # A link may carry an anchor of its own ("page.md#section"): the file is still the target.
            local = target.split("#")[0]
            if not local:
                continue
            if not (path.parent / unquote(local)).resolve().exists():
                out.append((path.relative_to(root).as_posix(), target))
    return out


def dangling_anchors(root):
    """(page, destination) for every anchor that the page a link names does not give.

    A destination whose file is not there is left to broken_links(): this asks only whether the
    section a reader is sent to is one the page has. A link that leaves the repository is not ours.
    A destination that is a folder is read as its README.md - the page GitHub shows there, and the
    one whose headings a `folder/#section` link lands on - and a folder with no README.md gives no
    section at all, so it is reported rather than skipped.
    """
    root = Path(root)
    out = []
    for path in markdown_files(root):
        for target in targets(path.read_text(encoding="utf-8", errors="replace")):
            if target.startswith(("http://", "https://", "mailto:", "data:", "<")):
                continue
            local, _, anchor = target.partition("#")
            anchor = unquote(anchor).strip()
            if not anchor:
                continue
            page = (path.parent / unquote(local)).resolve() if local else path
            if not page.exists():
                continue
            if page.is_dir():
                page = page / "README.md"
            if not page.is_file():
                out.append((path.relative_to(root).as_posix(), target))
                continue
            if anchor.lower() not in anchors(page):
                out.append((path.relative_to(root).as_posix(), target))
    return out


class RepositoryLinksTest(unittest.TestCase):
    def test_every_relative_link_resolves(self):
        broken = broken_links(ROOT)
        message = "\n".join(f"{page} -> {target}" for page, target in broken)
        self.assertEqual([], broken, "relative links that lead nowhere:\n" + message)

    def test_every_anchor_names_a_section_of_its_page(self):
        dangling = dangling_anchors(ROOT)
        message = "\n".join(f"{page} -> {target}" for page, target in dangling)
        self.assertEqual([], dangling, "anchors no section on the page gives:\n" + message)

    def test_the_dutch_front_page_gives_the_english_anchors_its_links_use(self):
        # README.nl.md is the page this rule was written for: its headings are Dutch and its links
        # are the English page's, so the ids it states by hand are the only thing that makes them
        # land. A section added there without its <a id> reads as a link that does nothing.
        page = ROOT / "README.nl.md"
        text = FENCE.sub("", page.read_text(encoding="utf-8"))
        used = {unquote(target.partition("#")[2]).strip().lower()
                for target in targets(text)
                if target.startswith("#") and len(target) > 1}
        self.assertGreaterEqual(len(used), 4, f"only {len(used)} in-page anchors read")
        self.assertEqual(set(), used - anchors(page), "links with no section to land on")
        self.assertGreaterEqual(len(EXPLICIT_ANCHOR.findall(text)), 10, "the ids the page states")

    def test_the_walk_reaches_the_pages_it_claims_to(self):
        # A walk that read nothing passes every check above; these are the counts it starts from.
        pages = list(markdown_files(ROOT))
        self.assertGreaterEqual(len(pages), 250, f"only {len(pages)} pages walked")
        anchors_checked = 0
        for path in pages:
            for target in targets(path.read_text(encoding="utf-8", errors="replace")):
                if "#" in target and not target.startswith(("http://", "https://", "mailto:", "data:", "<")):
                    anchors_checked += 1
        self.assertGreaterEqual(anchors_checked, 40, f"only {anchors_checked} in-repository anchors")


class AnchorTest(unittest.TestCase):
    def test_headings_become_the_anchors_github_gives_them(self):
        cases = {
            "Quick start": "quick-start",
            "Doing it by hand": "doing-it-by-hand",
            "Question marks, commas and (brackets)?": "question-marks-commas-and-brackets",
            "Isn't ADB access a security hole?": "isnt-adb-access-a-security-hole",
            "65U7E's buttons": "65u7es-buttons",
            "Na een firmware-update": "na-een-firmware-update",
            "Vragen die mensen stellen": "vragen-die-mensen-stellen",
            "Điều khiển từ xa": "điều-khiển-từ-xa",
            "简体中文": "简体中文",
            "The `--wait` flag": "the---wait-flag",
            "A [link](docs/x.md) in a heading": "a-link-in-a-heading",
            "*Emphasis* and **bold**": "emphasis-and-bold",
        }
        for title, want in cases.items():
            self.assertEqual(want, slug(title), title)

    def test_a_repeated_heading_is_numbered_the_way_github_numbers_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            page = Path(tmp) / "note.md"
            page.write_text("# Notes\n\n## Same\n\n## Same\n\n## Same\n", encoding="utf-8")
            self.assertEqual({"notes", "same", "same-1", "same-2"}, set(anchors(page)))

    def test_an_anchor_that_the_page_does_not_give_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "note.md").write_text(
                "## Quick start\n\n"
                "[own](#quick-start)\n"
                "[other](other.md#doing-it-by-hand)\n"
                "[not a section](#questions-people-ask)\n"
                "[heading in another language](other.md#snel-aan-de-slag)\n",
                encoding="utf-8")
            (root / "other.md").write_text(
                "## Doing it by hand\n\n## Snel aan de slag\n", encoding="utf-8")
            self.assertEqual(
                [("note.md", "#questions-people-ask")],
                dangling_anchors(root),
            )

    def test_an_anchor_stated_by_hand_counts_as_a_section(self):
        # The shape README.nl.md is built on: a Dutch heading, an English id above it, English links.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "nl.md").write_text(
                "<a id=\"quick-start\"></a>\n## Snel aan de slag\n\n"
                "<a name=\"after-a-firmware-update\"></a>\n## Na een firmware-update\n\n"
                "[begin](#quick-start) · [updates](#after-a-firmware-update)\n"
                "[dat is er niet](#questions-people-ask)\n",
                encoding="utf-8")
            self.assertEqual([("nl.md", "#questions-people-ask")], dangling_anchors(root))

    def test_a_section_that_is_a_details_summary_is_not_an_anchor(self):
        # The front pages' FAQ entries are <summary> lines rather than headings: a link that names
        # one has no section to land on unless the page gives the anchor some other way.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "faq.md").write_text(
                "## Questions people ask\n\n<details><summary><b>Is ADB a risk?</b></summary>\n</details>\n",
                encoding="utf-8")
            self.assertEqual([], dangling_anchors(root))
            (root / "nl.md").write_text(
                "## Veelgestelde vragen\n\n[Is ADB een risico?](#questions-people-ask)\n",
                encoding="utf-8")
            self.assertEqual([("nl.md", "#questions-people-ask")], dangling_anchors(root))

    def test_an_anchor_leaving_the_repository_is_not_checked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "note.md").write_text(
                "[issue](https://github.com/Gathin23/cleanyourtv/issues/795#issuecomment-1)\n"
                "[top](#)\n",
                encoding="utf-8")
            self.assertEqual([], dangling_anchors(root))

    def test_a_destination_with_no_file_is_left_to_the_link_check(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "note.md").write_text("[gone](moved.md#anything)\n", encoding="utf-8")
            self.assertEqual([], dangling_anchors(root))
            self.assertEqual([("note.md", "moved.md#anything")], broken_links(root))

    def test_code_blocks_are_skipped_for_anchors_too(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "note.md").write_text(
                "## What this page covers\n\n```markdown\n[see](#a-section-that-is-not-here)\n```\n",
                encoding="utf-8")
            self.assertEqual([], dangling_anchors(root))

    def test_an_encoded_anchor_is_decoded(self):
        # A browser percent-encodes a non-ASCII fragment: the heading is still the section.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "note.md").write_text(
                "## Điều khiển\n\n[từ xa](#%C4%90i%E1%BB%81u-khi%E1%BB%83n)\n",
                encoding="utf-8")
            self.assertEqual([], dangling_anchors(root))


class FolderLinkTest(unittest.TestCase):
    """A link to a folder carries the section its README.md gives.

    `examples/README.md` builds its rows from the folders' tv.json notes, and one note wrote
    `tcl-55c7k-uk/#compatibility-report-tcl-50c805-wi-fi`: a folder and the heading inside the
    README GitHub shows there. The anchor check asked only whether a *file* gives the anchor, so the
    destination was skipped and no heading was ever read - the shape is now judged like the rest.
    """

    def test_the_readme_inside_the_folder_gives_the_anchor(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "examples" / "tv").mkdir(parents=True)
            (root / "examples" / "tv" / "README.md").write_text(
                "# TCL C7K\n\n## Compatibility report: TCL 50C805, Wi-Fi\n", encoding="utf-8")
            (root / "examples" / "README.md").write_text(
                "| `tv/` | ... a [50C805 report](tv/#compatibility-report-tcl-50c805-wi-fi) records ... |\n",
                encoding="utf-8")
            self.assertEqual([], dangling_anchors(root))
            self.assertEqual([], broken_links(root))

    def test_a_heading_the_readme_does_not_give_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "examples" / "tv").mkdir(parents=True)
            (root / "examples" / "tv" / "README.md").write_text(
                "# TCL C7K\n\n## Compatibility report: TCL 50C805, Wi-Fi\n", encoding="utf-8")
            (root / "examples" / "README.md").write_text(
                "[report](tv/#compatibility-report-tcl-55c61k)\n", encoding="utf-8")
            self.assertEqual([], broken_links(root))
            self.assertEqual([("examples/README.md", "tv/#compatibility-report-tcl-55c61k")],
                             dangling_anchors(root))

    def test_a_folder_with_no_readme_gives_no_section(self):
        # Nothing there can give the heading, and the folder exists, so broken_links() is quiet:
        # only this check can say that the jump lands nowhere.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "examples" / "tv").mkdir(parents=True)
            (root / "examples" / "tv" / "debloat.txt").write_text("com.example.tv\n", encoding="utf-8")
            (root / "examples" / "README.md").write_text("[report](tv/#second-unit)\n", encoding="utf-8")
            self.assertEqual([], broken_links(root))
            self.assertEqual([("examples/README.md", "tv/#second-unit")], dangling_anchors(root))

    def test_a_folder_that_is_not_there_is_left_to_the_link_check(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "README.md").write_text("[gone](examples/moved/#second-unit)\n", encoding="utf-8")
            self.assertEqual([], dangling_anchors(root))
            self.assertEqual([("README.md", "examples/moved/#second-unit")], broken_links(root))


if __name__ == "__main__":
    unittest.main()
