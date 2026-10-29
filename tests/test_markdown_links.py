"""Every relative link in the repository's markdown resolves to something that exists.

A page moved between folders keeps its old relative path and nothing opens it for you: this note
came from `examples/tcl-g09-google-tv/`, moved to `docs/devices/`, and its companion link stayed
`../examples/tcl-g09-google-tv` - which from `docs/devices/` is `docs/examples/`, a folder that has
never existed. CI runs the unit tests and the shell helpers, so a link like that is only found by
the reader who clicks it, and only when they are on the page that has it. This walks every `.md`
file once and fails on the ones that lead nowhere.

What counts: markdown links and images, reference definitions and `href`/`src` in raw HTML. What
does not: `http(s)`, `mailto:`, `data:`, a bare `#anchor` and the insides of fenced code blocks,
where a path is an example rather than a link. Anchors are not checked: a heading's slug is
GitHub's, not ours, and guessing it here would produce failures nobody can act on.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import re
import tempfile
import unittest
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


class RepositoryLinksTest(unittest.TestCase):
    def test_every_relative_link_resolves(self):
        broken = broken_links(ROOT)
        message = "\n".join(f"{page} -> {target}" for page, target in broken)
        self.assertEqual([], broken, "relative links that lead nowhere:\n" + message)

    def test_a_destination_that_does_not_exist_is_reported(self):
        # The checker has to be able to fail, or the test above proves nothing.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "sub").mkdir()
            (root / "sub" / "note.md").write_text(
                "[list](../examples/moved)\n[picture](shot.png)\n[ok](../target.md)\n",
                encoding="utf-8")
            (root / "target.md").write_text("# Target\n", encoding="utf-8")
            self.assertEqual([("sub/note.md", "../examples/moved"), ("sub/note.md", "shot.png")],
                             broken_links(root))

    def test_links_that_leave_the_repository_are_not_checked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "note.md").write_text(
                "\n".join([
                    "[issue](https://github.com/Gathin23/cleanyourtv/issues/795)",
                    "[mail](mailto:someone@example.com)",
                    "[here](#what-this-page-covers)",
                    "[back](note.md#what-this-page-covers)",
                ]) + "\n",
                encoding="utf-8")
            self.assertEqual([], broken_links(root))

    def test_code_blocks_and_build_folders_are_skipped(self):
        # A path shown in a command is an example; site-packages inside the CI venv is not ours.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "note.md").write_text(
                "```bash\ntools/venv/bin/python -I tools/tv.py 'pm list packages'\n```\n",
                encoding="utf-8")
            (root / "venv" / "lib").mkdir(parents=True)
            (root / "venv" / "lib" / "installed.md").write_text(
                "[gone](nothing-here.md)\n", encoding="utf-8")
            self.assertEqual([], broken_links(root))

    def test_an_encoded_space_in_a_destination_is_decoded(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "shot one.png").write_bytes(b"\x89PNG")
            (root / "note.md").write_text("[shot](shot%20one.png)\n", encoding="utf-8")
            self.assertEqual([], broken_links(root))


if __name__ == "__main__":
    unittest.main()
