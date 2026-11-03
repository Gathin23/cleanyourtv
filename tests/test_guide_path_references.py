"""The pages and sections a tool or a list header names are the ones that exist.

A list header and a tool's own message are what send a reader somewhere after a failed run, and
neither is a markdown page: `examples/*/debloat.txt` and the tools are read as plain text, so
tests/test_markdown_links.py - which walks `*.md` - never opens them. `tools/bench.py` named
`tools/perf-ab.md` for a guide that lives at `docs/workflows/perf-ab.md` (it moved when the recipes
were split out of the tools' pages), and nothing failed: the sentence around it describes the same
method either way, so the wrong path was only there for the reader who went looking for the file.
The `#section` a header or a message gives is the same kind of reference - the five Xiaomi lists send
a reader to their entry in docs/TROUBLESHOOTING.md and tools/tv.py sends one to Connecting.

What is read: `examples/**/*.txt` and `tools/*.py`, `tools/*.sh`, `tools/*.ps1`, and every
`path/to/page.md` (with an optional `#section`) they name. A bare file name is left alone, since a
message may name a guide without its folder, and a URL is not one of our paths. A path is resolved
from the folder of the file that names it first and then from the repository root, the two forms
these files use: a list header writes `docs/TROUBLESHOOTING.md#...` for the root, and a tool writes
`docs/workflows/perf-ab.md` from `tools/`. The section a page must give is read by
tests/test_markdown_links.py's own `anchors()`, so GitHub's slug rule lives in one place.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import unittest
from pathlib import Path

# The anchor rule is the markdown sweep's, used here rather than copied: a heading that changes its
# slug changes it for both readers at once.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_markdown_links import anchors

ROOT = Path(__file__).resolve().parents[1]

# The files that name a page without being one.
GLOBS = ("examples/**/*.txt", "tools/*.py", "tools/*.sh", "tools/*.ps1")

# A path with a folder in it and a page at the end, e.g. "docs/workflows/perf-ab.md". The last
# character before ".md" is not a dot, so a sentence's own full stop is not part of the path, and a
# URL never matches: the "example.com/docs/x.md" inside one is preceded by "/" or ".".
PAGE = re.compile(r"(?<![\w/.-])((?:[A-Za-z0-9_.-]+/)+[A-Za-z0-9_-]+\.md)(?:#([A-Za-z0-9_-]+))?(?![\w-])")


def text_files(root=ROOT):
    """Every file that names a page without being one, in a stable order."""
    found = []
    for pattern in GLOBS:
        found.extend(root.glob(pattern))
    return sorted(path for path in found if path.is_file())


def references(text):
    """(page, section or None) for each page this text names, in the order it names them."""
    return [(match.group(1), match.group(2)) for match in PAGE.finditer(text)]


def resolved(where, page, root=ROOT):
    """The file PAGE names, read from WHERE's folder first and then from the repository root."""
    for candidate in (where.parent / page, root / page):
        if candidate.resolve().is_file():
            return candidate.resolve()
    return None


def reference_problems(root=ROOT):
    """(file, line, page, why) for each page or section a file names that is not there."""
    problems = []
    for path in text_files(root):
        text = path.read_text(encoding="utf-8", errors="replace")
        for number, line in enumerate(text.splitlines(), 1):
            for page, section in references(line):
                target = resolved(path, page, root)
                name = page + ("#" + section if section else "")
                if target is None:
                    problems.append((path.relative_to(root).as_posix(), number, name, "no such page"))
                elif section and section not in anchors(target):
                    problems.append((path.relative_to(root).as_posix(), number, name,
                                     "the page gives no such section"))
    return problems


class RepositoryReferencesTest(unittest.TestCase):
    """Every page and section a list header or a tool names is one the tree holds."""

    def test_every_page_and_section_named_is_there(self):
        problems = reference_problems()
        message = "\n".join(f"{where}:{number}: {name} ({why})" for where, number, name, why in problems)
        self.assertEqual([], problems, "pages and sections that are not there:\n" + message)

    def test_the_sweep_reads_the_references_it_was_written_for(self):
        # A walk that read nothing would pass, and so would one whose globs went stale.
        read = []
        for path in text_files():
            for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
                for page, section in references(line):
                    read.append(page + ("#" + section if section else ""))
        self.assertGreaterEqual(len(read), 150, "the sweep read %d references" % len(read))
        self.assertIn("tools/native-adb.md", read, "a tool naming a guide beside it is not read")
        self.assertIn("docs/TROUBLESHOOTING.md#connecting", read,
                      "a tool's message naming a section is not read")


class ReaderTest(unittest.TestCase):
    """The rule itself, on a tree written here: a page or section that is not there is named."""

    def setUp(self):
        import tempfile
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(self._remove)

    def _remove(self):
        import shutil
        shutil.rmtree(self.root, ignore_errors=True)

    def write(self, where, text):
        path = self.root / where
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def page(self, where="docs/workflows/perf-ab.md", text="# Perf A/B\n\n## Choosing a scenario\n"):
        return self.write(where, text)

    def test_a_page_that_is_not_there_is_named(self):
        self.page()
        self.write("tools/bench.py", "# the same rule as --out (tools/perf-ab.md's three tools)\n")
        self.assertEqual([("tools/bench.py", 1, "tools/perf-ab.md", "no such page")],
                         reference_problems(self.root))

    def test_a_section_the_page_does_not_give_is_named(self):
        self.page()
        self.write("examples/one/debloat.txt", "# see docs/workflows/perf-ab.md#measuring-twice\n")
        self.assertEqual([("examples/one/debloat.txt", 1, "docs/workflows/perf-ab.md#measuring-twice",
                           "the page gives no such section")], reference_problems(self.root))

    def test_a_section_the_page_gives_is_left_alone(self):
        self.page()
        self.write("tools/bench.py", '# see docs/workflows/perf-ab.md#choosing-a-scenario\n')
        self.write("examples/one/debloat.txt", "# see docs/workflows/perf-ab.md\n")
        self.assertEqual([], reference_problems(self.root))

    def test_the_sentence_full_stop_is_not_part_of_the_path(self):
        # The form example READMEs use: "a G05 set belongs with examples/tcl-65c735."
        self.page("examples/tcl-65c735/README.md", "# TCL 65C735\n")
        self.write("examples/one/README.md", "A G05 set belongs with examples/tcl-65c735.\n")
        self.assertEqual([], reference_problems(self.root))

    def test_a_path_is_read_from_the_naming_file_and_from_the_root(self):
        self.page()
        self.page("tools/trace.md", "# Trace\n")
        # tools/*.py names a page beside it and the root's page in the root's words.
        self.write("tools/bench.py", '# tools/trace.md, and docs/workflows/perf-ab.md\n')
        self.assertEqual([], reference_problems(self.root))

    def test_a_bare_file_name_is_left_alone(self):
        # tools/disable.ps1 says "TROUBLESHOOTING.md" with no folder, and that is not a path.
        self.write("tools/disable.sh", "# See TROUBLESHOOTING.md for the routes.\n")
        self.assertEqual([], reference_problems(self.root))

    def test_a_url_is_not_one_of_our_paths(self):
        self.write("examples/sber/debloat.txt",
                   "# https://developers.sber.ru/docs/ru/va/native/testing/adb and "
                   "https://example.com/docs/x.md\n")
        self.assertEqual([], references("# https://example.com/docs/x.md\n"))
        self.assertEqual([], reference_problems(self.root))

    def test_a_list_header_and_a_tool_are_both_read(self):
        self.page()
        self.write("examples/one/debloat.txt", "# see docs/workflows/nothing-here.md\n")
        self.write("tools/other.py", '# see docs/workflows/also-missing.md\n')
        self.assertEqual(["examples/one/debloat.txt", "tools/other.py"],
                         [where for where, _, _, _ in reference_problems(self.root)])


if __name__ == "__main__":
    unittest.main()
