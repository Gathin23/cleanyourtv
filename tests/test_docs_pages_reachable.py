"""Every page under docs/ is linked from a page a reader can reach.

docs/DOS-AND-DONTS.md is the maintainer's own list of the reasons a pull request most often came
back for changes or was closed, one example per line, and nothing in the repository named it - not
CONTRIBUTING.md, which is what a contributor reads first, not README.md, not AGENTS.md - so a
`grep -rn DOS-AND-DONTS` over the tree found the page itself and nothing else, and it was reachable
only by listing docs/ on GitHub. The page states the rule it broke: "No screenshots or reports in
docs/ that nothing links to". tests/test_device_notes_index.py holds the reports under docs/devices/
to that rule and no checker read the pages beside them.

This walks every page below docs/, folders included, and fails on one that no other page links to.
The destinations come from tests/test_device_notes_index.py's own parser, so a path inside a fenced
code block is an example rather than a link, an http(s) address is not a link into this checkout, and
`page.md#section` still points at the page.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import sys
import tempfile
import unittest
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_device_notes_index import EXTERNAL, destinations, markdown_files  # noqa: E402

# The page of reasons, and the page a contributor reads before anything else.
RULES_PAGE = "docs/DOS-AND-DONTS.md"
CONTRIBUTING = "CONTRIBUTING.md"


def relative(root, path):
    """`path` as a repository-relative posix string, symlinks resolved."""
    return os.path.relpath(Path(path).resolve(), Path(root).resolve()).replace(os.sep, "/")


def referrers(root):
    """{page: the pages that link to it}, for every markdown page in the tree, itself included."""
    root = Path(root).resolve()
    out = {}
    for page in markdown_files(root):
        for target in destinations(page.read_text(encoding="utf-8", errors="replace")):
            if target.startswith(EXTERNAL):
                continue
            # A link may carry an anchor of its own ("page.md#section"): the file is still the target.
            local = target.split("#")[0]
            if not local:
                continue
            resolved = relative(root, (page.parent / unquote(local)).resolve())
            out.setdefault(resolved, set()).add(relative(root, page))
    return out


def docs_pages(root):
    """The pages below docs/, repository-relative, in a stable order."""
    return [relative(root, page) for page in markdown_files(Path(root) / "docs")]


def unlinked_docs(root):
    """The pages below docs/ that no page other than themselves links to."""
    linked = referrers(root)
    return [rel for rel in docs_pages(root) if not (linked.get(rel, set()) - {rel})]


class DocsPagesReachableTest(unittest.TestCase):
    def test_every_page_under_docs_is_linked_from_a_page(self):
        unlinked = unlinked_docs(ROOT)
        self.assertEqual([], unlinked,
                         "pages under docs/ that nothing links to: " + ", ".join(unlinked))

    def test_contributing_links_the_page_of_reasons_a_pr_comes_back(self):
        # Pinned where a contributor starts rather than anywhere in the tree: CONTRIBUTING.md is the
        # page the reasons are a short form of, and its own text hands a reader to AGENTS.md beside it.
        self.assertTrue((ROOT / RULES_PAGE).is_file(), f"{RULES_PAGE} is gone")
        linked = referrers(ROOT).get(RULES_PAGE, set())
        self.assertIn(CONTRIBUTING, linked,
                      f"{CONTRIBUTING} does not link {RULES_PAGE}; linked from {sorted(linked)}")


class CheckerTest(unittest.TestCase):
    """The checker has to be able to fail, and to leave alone what is not a link."""

    def tree(self, files):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        for name, text in files.items():
            (root / name).parent.mkdir(parents=True, exist_ok=True)
            (root / name).write_text(text, encoding="utf-8")
        return root

    def test_a_page_nothing_links_to_is_reported(self):
        root = self.tree({"README.md": "# readme\n", "docs/lost.md": "# lost\n"})
        self.assertEqual(["docs/lost.md"], unlinked_docs(root))

    def test_a_page_linked_from_another_page_is_not_reported(self):
        root = self.tree({"README.md": "Read [the index](docs/index.md).\n",
                          "docs/index.md": "See [lost](lost.md).\n",
                          "docs/lost.md": "# lost\n"})
        self.assertEqual([], unlinked_docs(root))

    def test_a_page_in_a_folder_of_its_own_is_walked(self):
        root = self.tree({"docs/deep/lost.md": "# lost\n"})
        self.assertEqual(["docs/deep/lost.md"], unlinked_docs(root))

    def test_a_page_linked_only_from_itself_is_still_reported(self):
        root = self.tree({"docs/lost.md": "[itself](lost.md)\n"})
        self.assertEqual(["docs/lost.md"], unlinked_docs(root))

    def test_a_path_inside_a_code_block_is_not_a_link(self):
        root = self.tree({"README.md": "```\ndocs/lost.md\n```\n", "docs/lost.md": "# lost\n"})
        self.assertEqual(["docs/lost.md"], unlinked_docs(root))

    def test_an_address_of_the_page_elsewhere_is_not_a_link_into_the_tree(self):
        root = self.tree({
            "README.md": "https://github.com/Gathin23/cleanyourtv/blob/main/docs/lost.md\n",
            "docs/lost.md": "# lost\n",
        })
        self.assertEqual(["docs/lost.md"], unlinked_docs(root))

    def test_a_link_carrying_an_anchor_still_points_at_the_page(self):
        root = self.tree({"README.md": "Read [the index](docs/index.md).\n",
                          "docs/index.md": "[lost](lost.md#why-it-matters)\n",
                          "docs/lost.md": "# lost\n"})
        self.assertEqual([], unlinked_docs(root))

    def test_the_tree_is_read(self):
        # A walk that read nothing would pass every page above: the checkout is 93 pages below docs/.
        self.assertGreaterEqual(len(docs_pages(ROOT)), 80)
        self.assertGreaterEqual(sum(1 for _ in markdown_files(ROOT)), 180)


if __name__ == "__main__":
    unittest.main()
