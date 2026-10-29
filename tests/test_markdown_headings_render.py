"""No markdown heading sits inside a raw HTML block, where GitHub prints it as text.

In GitHub's markdown, a line that opens or closes a block-level tag (`<details>`, `</details>`,
`<p>`, `<div>` and the rest of CommonMark's list) starts an HTML block, and that block runs to the
next blank line. Whatever is in it is passed through as HTML, so a `## ...` line there is shown as
the characters "## ..." instead of a heading, and gets no anchor. The README's "The microphone, and
what can be said about it" came straight after a `</details>` with no blank line between them, so
the section that introduces `tools/privacy.py` had no heading at all. Nothing else notices: the
text is all there and every link still resolves.

This walks the same `.md` files as test_markdown_links and fails on any ATX heading inside such a
block. Fenced code and HTML comments are skipped, since a heading shown there is an example or is
hidden on purpose.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_markdown_links import ROOT, markdown_files  # noqa: E402

# CommonMark's type 6 HTML block start tags (the list GitHub's renderer uses).
BLOCK_TAGS = {
    "address", "article", "aside", "base", "basefont", "blockquote", "body", "caption", "center",
    "col", "colgroup", "dd", "details", "dialog", "dir", "div", "dl", "dt", "fieldset",
    "figcaption", "figure", "footer", "form", "frame", "frameset", "h1", "h2", "h3", "h4", "h5",
    "h6", "head", "header", "hr", "html", "iframe", "legend", "li", "link", "main", "menu",
    "menuitem", "nav", "noframes", "ol", "optgroup", "option", "p", "param", "section", "source",
    "summary", "table", "tbody", "td", "tfoot", "th", "thead", "title", "tr", "track", "ul",
}
BLOCK_START = re.compile(r" {0,3}</?([A-Za-z][A-Za-z0-9]*)(?:[ \t>]|/>|$)")
COMMENT_START = re.compile(r" {0,3}<!--")
FENCE = re.compile(r"\s*(`{3,}|~{3,})")
HEADING = re.compile(r" {0,3}#{1,6}(?:[ \t]|$)")


def swallowed_headings(text):
    """Line numbers (from 1) of the ATX headings that an HTML block turns into plain text."""
    out = []
    fence = None      # the opening fence while inside a fenced code block
    comment = False   # inside an HTML comment, which ends at the line holding "-->"
    html = False      # inside an HTML block, which ends at the next blank line
    for number, line in enumerate(text.split("\n"), 1):
        if fence:
            if re.fullmatch(r"\s*" + re.escape(fence[0]) + "{%d,}\\s*" % len(fence), line):
                fence = None
            continue
        if comment:
            comment = "-->" not in line
            continue
        if html:
            if not line.strip():
                html = False
            elif HEADING.match(line):
                out.append(number)
            continue
        fenced = FENCE.match(line)
        if fenced:
            fence = fenced.group(1)
        elif COMMENT_START.match(line):
            comment = "-->" not in line.split("<!--", 1)[1]
        else:
            tag = BLOCK_START.match(line)
            html = bool(tag) and tag.group(1).lower() in BLOCK_TAGS
    return out


def swallowed(root):
    """'page:line' for every heading inside an HTML block, in file order."""
    root = Path(root)
    out = []
    for path in markdown_files(root):
        text = path.read_text(encoding="utf-8", errors="replace")
        page = path.relative_to(root).as_posix()
        out.extend(f"{page}:{number}" for number in swallowed_headings(text))
    return out


class HeadingsRenderTest(unittest.TestCase):
    def test_no_heading_is_inside_an_html_block(self):
        found = swallowed(ROOT)
        self.assertEqual([], found, "these headings show as plain text: put a blank line between "
                                    "each one and the HTML line above it:\n" + "\n".join(found))

    def test_a_heading_right_after_a_closing_tag_is_reported(self):
        # The checker has to be able to fail, or the test above proves nothing.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "note.md").write_text(
                "<details>\n<summary>More</summary>\n\nText.\n\n</details>\n## Next\n\nBody.\n",
                encoding="utf-8")
            (root / "fixed.md").write_text(
                "<details>\n<summary>More</summary>\n\nText.\n\n</details>\n\n## Next\n\nBody.\n",
                encoding="utf-8")
            self.assertEqual(["note.md:7"], swallowed(root))

    def test_the_block_runs_to_the_next_blank_line(self):
        text = "Intro.\n<div>\nraw\n### Inside\n\n### Outside\n"
        self.assertEqual([4], swallowed_headings(text))

    def test_code_comments_and_inline_html_are_not_html_blocks(self):
        text = "\n".join([
            "```markdown",
            "</details>",
            "## Shown as an example",
            "```",
            "<!--",
            "<details>",
            "## Hidden on purpose",
            "-->",
            '<a id="anchor"></a>',
            "## A heading after inline HTML",
        ]) + "\n"
        self.assertEqual([], swallowed_headings(text))


if __name__ == "__main__":
    unittest.main()
