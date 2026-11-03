"""Every fenced code block in the repository's markdown is closed.

GitHub runs an unclosed fence to the end of the page: from there on every line is code, so the
headings below it stop being headings, the links stop being links and the bold text shows its
asterisks. Nothing in this suite read the fences, and the two sweeps that would have noticed look
straight past them: tests/test_markdown_links.py drops fenced blocks before it hunts for links (a
path inside a code sample is an example, not a link) and the table sweep skips them too - so the
links an unclosed block swallows are the ones nothing else checks, and the suite stays green while
the page reads as one monospace wall.

`addons/README.md` is the page this was written for. `tools/manage.py`'s three commands were added
in 5726f765 with their closing fence, and the merge commit 57ee4bd6 that brought PR #1216 in kept
the block but not the fence: everything from `## Rules` down - the rules a contributor reads before
opening a pull request, and the "Adding one" section below them - has rendered as code since.

What is read: every `.md` page, through tests/test_markdown_links.py's own markdown_files(), so the
walk and its skip list (the git data, the tools venv, node_modules, build output) live in one place.
A fence opens on a line holding three or more backticks or tildes, with the language after it
(`bash`, `bat`, `text`), and an info string that holds no backtick of its own - which is why a line
of inline code that starts with three backticks is not a fence. It closes on a line of the same character, at least as long, with nothing
after it: a shorter run, or another language's opening line, stays inside the block exactly as
GitHub keeps it. Indentation is not part of the test - a fence inside a list item opens and closes
at the item's own indent, and every page here matches its own two ends - so a block that is opened
in a list and closed at the margin counts as closed.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import unittest
from pathlib import Path

# The walk is the markdown sweep's, used here rather than copied: a folder added to its skip list
# is skipped for both readers at once.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_markdown_links import markdown_files

ROOT = Path(__file__).resolve().parents[1]

# A fence line: any indent, three or more of one quote character, then the rest of the line.
FENCE = re.compile(r"^[ \t]*(`{3,}|~{3,})(.*)$")


def blocks(text):
    """(open line, close line or None, info string) for each fenced block in one page, in order."""
    found = []
    opening = None
    for number, line in enumerate(text.split("\n"), 1):
        match = FENCE.match(line)
        if not match:
            continue
        ticks, rest = match.group(1), match.group(2)
        if opening is None:
            # A backtick fence's info string may not hold a backtick, or the line is inline code.
            if ticks[0] == "`" and "`" in rest:
                continue
            opening = (number, ticks[0], len(ticks), rest.strip())
            continue
        # The closing fence: the same character, no shorter, and nothing after it.
        if ticks[0] == opening[1] and len(ticks) >= opening[2] and rest.strip() == "":
            found.append((opening[0], number, opening[3]))
            opening = None
    if opening is not None:
        found.append((opening[0], None, opening[3]))
    return found


def unclosed(text):
    """The line numbers of the fences that nothing closes, in the order they were opened."""
    return [open_at for open_at, close_at, _ in blocks(text) if close_at is None]


class TheRepository(unittest.TestCase):
    def test_every_page_closes_its_fences(self):
        """An unclosed fence turns the rest of a page into code, headings and links included."""
        broken = []
        for page in markdown_files(ROOT):
            for line in unclosed(page.read_text(encoding="utf-8", errors="replace")):
                broken.append(f"{page.relative_to(ROOT).as_posix()}:{line}")
        self.assertEqual(broken, [], "a fence nothing closes; everything below it renders as code")

    def test_the_walk_reads_the_tree(self):
        """The sweep cannot pass by reading nothing: pin what it walks and what it finds."""
        pages = list(markdown_files(ROOT))
        self.assertGreaterEqual(len(pages), 300, "the markdown walk found too few pages")
        fences = 0
        for page in pages:
            fences += len(blocks(page.read_text(encoding="utf-8", errors="replace")))
        self.assertGreaterEqual(fences, 600, "the fence reader found too few blocks")


class TheReader(unittest.TestCase):
    def test_a_closed_block(self):
        self.assertEqual(blocks("```bash\nls\n```\n"), [(1, 3, "bash")])

    def test_a_block_nothing_closes(self):
        self.assertEqual(unclosed("text\n```bash\nls\nmore text\n"), [2])

    def test_a_shorter_run_does_not_close(self):
        self.assertEqual(unclosed("````\ntext\n```\n"), [1])

    def test_a_longer_run_closes(self):
        self.assertEqual(blocks("```\n```\n"), [(1, 2, "")])
        self.assertEqual(blocks("```\nx\n`````\n"), [(1, 3, "")])

    def test_another_language_inside_a_block_does_not_close_it(self):
        self.assertEqual(blocks("```bash\nprose\n```python\nx = 1\n```\n"), [(1, 5, "bash")])

    def test_tildes_do_not_mix_with_backticks(self):
        self.assertEqual(unclosed("~~~\nx\n```\n"), [1])
        self.assertEqual(blocks("~~~\nx\n~~~\n"), [(1, 3, "")])

    def test_three_backticks_with_a_backtick_after_them_are_inline_code(self):
        self.assertEqual(blocks("```a `b` c\n"), [])

    def test_a_fence_inside_a_list_item(self):
        text = "- a step:\n\n  ```bash\n  ls\n  ```\n\n- the next one\n"
        self.assertEqual(blocks(text), [(3, 5, "bash")])
        self.assertEqual(unclosed(text), [])

    def test_a_fence_opened_in_a_list_and_closed_at_the_margin(self):
        self.assertEqual(blocks("  ```\n  ls\n```\n"), [(1, 3, "")])

    def test_two_blocks_and_the_prose_between_them(self):
        text = "```\none\n```\nprose\n```text\ntwo\n```\n"
        self.assertEqual(blocks(text), [(1, 3, ""), (5, 7, "text")])
        self.assertEqual(unclosed(text), [])

    def test_a_block_that_runs_to_the_end_of_the_page(self):
        self.assertEqual(blocks("intro\n```properties\nkey=value\n"), [(2, None, "properties")])
