"""The one reader for the README tables: rows of a Markdown table, and its cells as text.

The repo's pages double as its database: one script writes the TV tables from examples/*/tv.json,
the others read them back with this parser. README.md's Tools table, docs/switches.md's Key table,
addons/README.md's Add-on table and themes/README.md's Theme table are all read the same way,
so a malformed table has to fail in one place, not one per reader.

What `table_rows` answers, exactly (the schema every caller relies on):

- the first table whose header line starts with `header_start`; a page may hold several tables
  (docs/switches.md has one per section) and each caller names the one it wants;
- the `|---|---|` separator line under the header is skipped;
- a row is every later line that starts with `|`, split on `" | "` with the cells stripped;
- the table ends at the first line that does not start with `|` once rows have been read, so prose
  under the table is left alone and a second table in the same text is not this one.

Cell readers take one parsed cell. `plain` renders a cell as text (links to their words, code marks
dropped) - the form `set`, `check` and the tests compare against config keys. `img_src` and
`img_srcs` pull the screenshot paths out of a cell; `img_srcs` keeps every `<img src>` in order
because docs/switches.md gives some keys two pictures.

The per-page readers inside tests/ (test_theme_keys_table, test_tools_table, test_helper_table and
the like) look similar and are deliberately not this module: they assert the structure of one page -
sections, indented rows inside a numbered list, rows keyed by their first cell - which the general
reader above does not answer.
"""
import re


def table_rows(text, header_start):
    """Rows of the first Markdown table whose header starts with header_start, as lists of cell strings."""
    rows, inside = [], False
    for line in text.splitlines():
        if line.startswith(header_start):
            inside = True
            continue
        if inside:
            if not line.startswith("|"):
                if rows:
                    break
                continue
            if re.match(r"^\|[\s:|-]+\|$", line):
                continue
            rows.append([c.strip() for c in line.strip().strip("|").split(" | ")])
    return rows


def img_src(cell):
    m = re.search(r'<img src="([^"]+)"', cell or "")
    return m.group(1) if m else None


def img_srcs(cell):
    """Every <img src> in a cell, in the order the page draws them.

    docs/switches.md lets one row show more than one picture - `vpn_pill` carries the pill up and the pill at
    rest - and tests/test_switches.py already reads every one of them. A reader that stops at the first `<img
    src>` reports half of what the page gives the key, which is what `show` did while the page grew a second
    picture per row. A picture with no src keeps its place as an empty string, as that page's own reader has it.
    """
    out = []
    for tag in re.findall(r"<img\b[^>]*>", cell or ""):
        m = re.search(r'\bsrc="([^"]*)"', tag)
        out.append(m.group(1) if m else "")
    return out


def plain(cell):
    """A Markdown cell as text: links to their words, code marks dropped."""
    s = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", cell or "")
    return s.replace("`", "").strip()
