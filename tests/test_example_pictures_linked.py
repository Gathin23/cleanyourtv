"""A picture shipped in a list folder is a picture some page shows.

AGENTS.md's "Stray files" line is the rule: "No files that nothing links to: screenshots belong in the PR
description, not the tree." A contributor adding a list gets a folder of their own and a screenshot of their
set, and the screenshot is the one thing in that folder no test read: the list, the `tv.json`, the
`config.json` and the README are all judged, and a picture beside them was not.
`examples/tcl-43p615/home.jpg` sat there from 32595be6, the commit that added the list, with nothing showing
it - the folder has no README.md, `tv.json`'s notes are one table cell, and no page in the tree carried the
path. It is 129 KB that a reader never sees and a clone still downloads.

What counts as showing a picture: a relative link or image on any page in the tree, resolved from that
page's own folder, so a `docs/devices/` note that shows a set's Home screen with
`../../examples/<folder>/home.jpg` counts as much as the folder's own README. A raw `<img src>` counts,
which is how `addons/README.md` and `themes/README.md` write their screenshots. A path inside a fenced code
block does not, which is the line `tests/test_markdown_links.py` draws and this module reuses: there a path
is an example rather than a link, and a reader cannot click it. A link to the folder itself does not count
either, because it opens the folder's page and not the picture.

The rule has one limit worth stating rather than leaving to be read as stronger than it is: a picture in a
`wallpapers/` folder is named by no page, because the launcher reads the folder and picks one, so those are
not held to this. A case pins that.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import sys
import tempfile
import unittest
from pathlib import Path
from urllib.parse import unquote

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_markdown_links import markdown_files, targets  # noqa: E402  the tree's walk and link reader

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"
# The suffixes a picture is saved under; a wallpaper may be any of them, see `pictures()`.
PICTURE = (".jpg", ".jpeg", ".png", ".webp", ".gif")
EXTERNAL = ("http://", "https://", "mailto:", "data:", "#")


def list_folders(examples):
    """The list folders in `examples`, in a stable order."""
    examples = Path(examples)
    return sorted(p for p in examples.iterdir() if p.is_dir()) if examples.is_dir() else []


def pictures(examples):
    """Every picture below the list folders, outside a `wallpapers/` folder."""
    examples = Path(examples)
    found = []
    for folder in list_folders(examples):
        for path in sorted(folder.rglob("*")):
            if not path.is_file() or path.suffix.lower() not in PICTURE:
                continue
            # The launcher lists that folder and picks a picture out of it, so no page names these.
            if "wallpapers" in path.relative_to(folder).parts:
                continue
            found.append(path)
    return found


def destinations(page):
    """The files one page links to, as absolute paths.

    Read the way `tests/test_markdown_links.py` reads them - inline links and images, reference
    definitions, `href`/`src` in raw HTML, code blocks dropped - then resolved against the page's folder,
    so the answer is the file a reader lands on rather than the words in the page.
    """
    page = Path(page)
    found = set()
    for target in targets(page.read_text(encoding="utf-8", errors="replace")):
        if target.startswith(EXTERNAL):
            continue
        path = unquote(target.split("#")[0])
        if not path:
            continue
        try:
            found.add((page.parent / path).resolve())
        except OSError:
            continue
    return found


def shown(root, inside_examples=None):
    """The files the pages below `root` link to, as absolute paths.

    `inside_examples` narrows the walk to pages inside (True) or outside (False) `examples/`, which is how
    the tree cases tell the folder's own page from a note that shows its picture.
    """
    root = Path(root)
    examples = root / "examples"
    found = set()
    for page in markdown_files(root):
        if inside_examples is not None and ((examples in page.parents) is not inside_examples):
            continue
        found |= destinations(page)
    return found


def strays(root):
    """The pictures in `root`'s list folders that no page below `root` shows, as paths relative to it."""
    root = Path(root)
    seen = shown(root)
    return sorted(str(p.relative_to(root)) for p in pictures(root / "examples") if p.resolve() not in seen)


class TheTreeTest(unittest.TestCase):
    def test_every_picture_in_a_list_folder_is_shown_by_a_page(self):
        self.assertEqual(strays(ROOT), [],
                         "a picture in a list folder that no page shows: AGENTS.md's stray-files rule wants "
                         "screenshots in the PR description, so either show it on a page or take it out")

    def test_the_walk_still_reads_the_pictures_the_tree_ships(self):
        # A floor: a walk that read nothing would pass the case above while judging no picture at all.
        found = {str(p.relative_to(ROOT)) for p in pictures(EXAMPLES)}
        self.assertGreaterEqual(len(found), 3, f"pictures() read {sorted(found)}")
        for name in ("examples/tcl-q7c-google-tv/65q7d-pro-home.png",
                     "examples/tuvio-td43ufbcv5/home.png",
                     "examples/hisense-hismart-2k-atv4-hamamatsucho/set-top-box-tile.jpg"):
            self.assertIn(name, found)

    def test_a_note_outside_examples_shows_a_sets_picture(self):
        # The other half of the floor: a link from a page in another folder has to resolve into examples/,
        # which is how a `docs/devices/` note shows the Home screen of a set whose list has no README.
        found = sorted(str(p.relative_to(ROOT)) for p in shown(ROOT, inside_examples=False)
                       if p.is_file() and EXAMPLES in p.parents)
        self.assertNotEqual(found, [], "no picture below examples/ is shown from a page outside it")


def tree(files):
    """A throwaway checkout holding `files`: {relative path: text}, a picture's text being anything."""
    root = Path(tempfile.mkdtemp())
    for name, text in files.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    return root


class TheReaderTest(unittest.TestCase):
    def test_a_picture_the_folders_own_readme_shows_is_not_a_stray(self):
        root = tree({"examples/demo-set/debloat.txt": "com.example.app\n",
                     "examples/demo-set/home.jpg": "jpeg",
                     "examples/demo-set/README.md": "![Home](home.jpg)\n"})
        self.assertEqual(strays(root), [])

    def test_a_picture_a_note_in_another_folder_shows_is_not_a_stray(self):
        root = tree({"examples/demo-set/debloat.txt": "com.example.app\n",
                     "examples/demo-set/home.png": "png",
                     "docs/devices/demo-set.md": "![Home](../../examples/demo-set/home.png)\n"})
        self.assertEqual(strays(root), [])

    def test_a_raw_img_tag_counts(self):
        root = tree({"examples/demo-set/debloat.txt": "com.example.app\n",
                     "examples/demo-set/screenshot.jpg": "jpeg",
                     "examples/README.md": '<img src="demo-set/screenshot.jpg" width="240" alt="Home">\n'})
        self.assertEqual(strays(root), [])

    def test_a_link_with_an_escaped_space_resolves(self):
        root = tree({"examples/demo-set/debloat.txt": "com.example.app\n",
                     "examples/demo-set/my home.jpg": "jpeg",
                     "examples/demo-set/README.md": "![Home](my%20home.jpg)\n"})
        self.assertEqual(strays(root), [])

    def test_a_picture_no_page_shows_is_named_with_its_own_path(self):
        root = tree({"examples/demo-set/debloat.txt": "com.example.app\n",
                     "examples/demo-set/home.jpg": "jpeg",
                     "examples/demo-set/README.md": "A list for one set.\n"})
        self.assertEqual(strays(root), ["examples/demo-set/home.jpg"])

    def test_a_path_inside_a_code_block_is_not_a_link(self):
        # tests/test_markdown_links.py's rule: there a path is an example, and nothing can click it.
        root = tree({"examples/demo-set/debloat.txt": "com.example.app\n",
                     "examples/demo-set/home.jpg": "jpeg",
                     "examples/demo-set/README.md": "Taken on the TV:\n\n```text\nexamples/demo-set/home.jpg\n```\n"})
        self.assertEqual(strays(root), ["examples/demo-set/home.jpg"])

    def test_a_link_to_the_folder_does_not_show_its_pictures(self):
        root = tree({"examples/demo-set/debloat.txt": "com.example.app\n",
                     "examples/demo-set/home.jpg": "jpeg",
                     "examples/README.md": "| [`demo-set/`](demo-set/) | one set |\n"})
        self.assertEqual(strays(root), ["examples/demo-set/home.jpg"])

    def test_a_wallpaper_needs_no_page(self):
        root = tree({"examples/demo-set/theme/config.json": "{}\n",
                     "examples/demo-set/theme/wallpapers/one.jpg": "jpeg",
                     "examples/demo-set/theme/wallpapers/two.webp": "webp"})
        self.assertEqual(strays(root), [])

    def test_every_picture_suffix_is_read(self):
        files = {"examples/demo-set/debloat.txt": "com.example.app\n",
                 "examples/demo-set/README.md": "nothing here\n"}
        for name in ("a.jpg", "b.jpeg", "c.png", "d.webp", "e.gif"):
            files[f"examples/demo-set/{name}"] = "picture"
        self.assertEqual(strays(tree(files)),
                         [f"examples/demo-set/{name}" for name in ("a.jpg", "b.jpeg", "c.png", "d.webp", "e.gif")])

    def test_a_file_that_is_not_a_picture_is_left_alone(self):
        # The list, its tv.json, its config.json and its README are judged by their own tests.
        root = tree({"examples/demo-set/debloat.txt": "com.example.app\n",
                     "examples/demo-set/tv.json": "{}\n",
                     "examples/demo-set/config.json": "{}\n",
                     "examples/demo-set/README.md": "A list for one set.\n",
                     "examples/demo-set/notes.txt": "anything\n"})
        self.assertEqual(strays(root), [])

    def test_a_docs_picture_is_out_of_scope(self):
        # A page's own picture (a card, the social preview) is not a list folder's screenshot.
        root = tree({"examples/demo-set/debloat.txt": "com.example.app\n",
                     "docs/card.jpg": "jpeg"})
        self.assertEqual(strays(root), [])


if __name__ == "__main__":
    unittest.main()
